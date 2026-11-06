package app.lockin.android

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import app.lockin.core.AppRules
import app.lockin.core.QuoteRotator
import app.lockin.core.TipRotator
import app.lockin.core.WebRules

/**
 * Watches Facebook apps and browsers and shows the block overlay on locked screens.
 *
 * Facebook apps: the visible text of the foreground window goes to AppRules.
 * Browsers: the URL bar text goes to WebRules.
 *
 * The overlay is shown once per blocked key (the package for the Facebook app, the
 * package plus URL for browsers). Further events for the same blocked screen do not
 * re-show it or count it again.
 */
class LockAccessibilityService : AccessibilityService() {

    private val prefs: LockPrefs by lazy { LockPrefs(this) }
    private val quotes: QuoteRotator by lazy { QuoteRotator() }
    private val tips: TipRotator by lazy { TipRotator() }
    private var overlay: BlockOverlay? = null
    private var shownKey: String? = null
    private var lastContentEvaluation = 0L
    /** Label of the bottom tab last seen selected in the Facebook app; null after leaving it. */
    private var lastSelectedTab: String? = null

    private val lockListener = LockPrefs.Listener { enabled ->
        if (!enabled) hideOverlay()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs.registerListener(lockListener)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            handleEvent(event)
        } catch (e: RuntimeException) {
            // One bad event must not crash the service. The current overlay state is kept.
        }
    }

    override fun onInterrupt() {
        hideOverlay()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hideOverlay()
        prefs.unregisterListener(lockListener)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hideOverlay()
        prefs.unregisterListener(lockListener)
        super.onDestroy()
    }

    private fun handleEvent(event: AccessibilityEvent) {
        if (!prefs.enabled) {
            hideOverlay()
            return
        }
        val eventPackage = event.packageName?.toString()
        // Our own windows (the overlay and the settings screen) are never judged.
        if (eventPackage == packageName) return
        // Nothing is on screen and the event is not from a watched app: nothing to do.
        if (shownKey == null && !isWatched(eventPackage)) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val now = SystemClock.uptimeMillis()
            if (now - lastContentEvaluation < CONTENT_THROTTLE_MS) return
            lastContentEvaluation = now
        }

        val roots = frontAppRoots()
        if (roots.isEmpty()) return
        try {
            evaluate(roots)
        } finally {
            roots.forEach { recycleNode(it) }
        }
    }

    /**
     * Roots of every app window that belongs to the front app, top first.
     *
     * Not rootInActiveWindow: the block overlay takes focus (for the back key), so the
     * active window is the overlay itself and the screen under it would never be
     * checked again. Not only the topmost window either: popups (Chrome's tab tip
     * bubble, Samsung Internet's privacy dialog) are separate windows of the same
     * app that hide the URL bar and page from a single-window read.
     */
    @Suppress("DEPRECATION")
    private fun frontAppRoots(): List<AccessibilityNodeInfo> {
        val list = try {
            windows
        } catch (e: RuntimeException) {
            emptyList()
        }
        val roots = ArrayList<AccessibilityNodeInfo>()
        var frontPackage: String? = null
        // Windows are listed top first.
        for (window in list) {
            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                val root = window.root
                if (root != null) {
                    val pkg = root.packageName?.toString()
                    if (frontPackage == null) frontPackage = pkg
                    if (pkg != null && pkg == frontPackage) roots += root else recycleNode(root)
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) window.recycle()
        }
        if (roots.isEmpty()) rootInActiveWindow?.let { roots += it }
        return roots
    }

    private fun evaluate(roots: List<AccessibilityNodeInfo>) {
        val pkg = roots.first().packageName?.toString() ?: return
        when {
            pkg == packageName -> Unit
            // Keep the current state while the notification shade or quick settings are open.
            pkg == SYSTEM_UI_PACKAGE -> Unit
            AppRules.isFacebookPackage(pkg) -> evaluateFacebook(pkg, roots)
            AppRules.isBrowserPackage(pkg) -> evaluateBrowser(pkg, roots)
            // Launcher, other apps: the user has left Facebook and the browsers.
            // Forget the last tab so a link opened later does not inherit Marketplace.
            else -> {
                if (lastSelectedTab != null) Log.d(TAG, "left Facebook via $pkg: last tab cleared")
                lastSelectedTab = null
                hideOverlay()
            }
        }
    }

    private fun evaluateFacebook(pkg: String, roots: List<AccessibilityNodeInfo>) {
        // Messenger is always blocked, so its text is not needed.
        val screen = if (pkg == MESSENGER_PACKAGE) {
            ScreenInfo(emptyList(), null)
        } else {
            val parts = roots.map { readScreen(it) }
            ScreenInfo(parts.flatMap { it.texts }, parts.firstNotNullOfOrNull { it.selectedTab })
        }
        if (screen.selectedTab != null) lastSelectedTab = screen.selectedTab
        val blocked = AppRules.isBlockedAppScreen(pkg, screen.texts, screen.selectedTab, lastSelectedTab)
        // Tab labels and the decision only; screen text stays out of the log.
        Log.d(TAG, "$pkg tab=${screen.selectedTab} last=$lastSelectedTab blocked=$blocked")
        if (blocked) {
            showOverlay("app:$pkg")
        } else {
            hideOverlay()
        }
    }

    private fun evaluateBrowser(pkg: String, roots: List<AccessibilityNodeInfo>) {
        // If the URL bar cannot be read, the browser is treated as allowed (fail open).
        // A bare host ("m.facebook.com", how Samsung Internet shows every page) is
        // allowed too: the page cannot be told apart from Marketplace.
        val url = roots.firstNotNullOfOrNull { readUrlBar(pkg, it) }
        if (url != null && !WebRules.isHostOnlyDisplay(url) && WebRules.isLockedUrl(url)) {
            showOverlay("web:$pkg:$url")
        } else {
            hideOverlay()
        }
    }

    private class ScreenInfo(val texts: List<String>, val selectedTab: String?)

    /**
     * Collects text and content descriptions, and the label of the selected bottom tab.
     * Walks the whole tree (up to MAX_NODES): the tab bar comes late in tree order.
     */
    private fun readScreen(root: AccessibilityNodeInfo): ScreenInfo {
        val texts = ArrayList<String>()
        val tabs = ArrayList<Pair<String, Boolean>>()
        walkTree(root) { node ->
            for (raw in listOf(node.text, node.contentDescription)) {
                val text = raw?.toString()?.trim()
                if (text.isNullOrEmpty()) continue
                texts += text
                if (AppRules.tabLabel(text) != null) tabs += text to node.isSelected
            }
            false
        }
        return ScreenInfo(texts, AppRules.selectedMainTab(tabs))
    }

    /**
     * Visits up to MAX_NODES nodes, MAX_DEPTH levels deep. Child nodes are recycled after
     * use (needed on API 26-32). The root is owned by the caller. Stops when [visit] returns true.
     */
    private fun walkTree(root: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Boolean) {
        var visited = 0

        fun visitNode(node: AccessibilityNodeInfo, depth: Int): Boolean {
            visited++
            if (visit(node)) return true
            if (depth >= MAX_DEPTH) return false
            for (index in 0 until node.childCount) {
                if (visited >= MAX_NODES) return true
                val child = node.getChild(index) ?: continue
                try {
                    if (visitNode(child, depth + 1)) return true
                } finally {
                    recycleNode(child)
                }
            }
            return false
        }

        visitNode(root, 0)
    }

    private fun readUrlBar(pkg: String, root: AccessibilityNodeInfo): String? {
        for (id in urlBarViewIds(pkg)) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id) ?: continue
            try {
                for (node in nodes) {
                    val text = node.text?.toString()?.trim()
                    if (!text.isNullOrEmpty()) return toFullUrl(text)
                }
            } finally {
                for (node in nodes) recycleNode(node)
            }
        }
        return findEditableUrl(root)
    }

    /** Fallback for browsers without a known URL bar id: the first editable node that looks like a URL. */
    private fun findEditableUrl(root: AccessibilityNodeInfo): String? {
        var found: String? = null
        walkTree(root) { node ->
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && isEditable(node) && URL_SHAPE.matches(text)) {
                found = toFullUrl(text)
                true
            } else {
                false
            }
        }
        return found
    }

    private fun isEditable(node: AccessibilityNodeInfo): Boolean {
        if (Build.VERSION.SDK_INT >= 28 && node.isEditable) return true
        return node.className?.toString() == "android.widget.EditText"
    }

    private fun showOverlay(key: String) {
        if (key == shownKey) return
        hideOverlay()

        val (quoteIndex, quote) = quotes.next(prefs.lastQuote)
        val (tipIndex, tip) = tips.next(prefs.lastTip)
        val view = BlockOverlay(
            this, quote, tip,
            onGoBack = { performGlobalAction(GLOBAL_ACTION_BACK) },
            // Hide first so Marketplace is visible; the next event re-shows it if still blocked.
            onGoMarketplace = {
                hideOverlay()
                MarketplaceLauncher.open(this)
            },
        )
        if (!view.show()) return

        overlay = view
        shownKey = key
        prefs.recordBlock(quoteIndex, tipIndex)
    }

    private fun hideOverlay() {
        overlay?.dismiss()
        overlay = null
        shownKey = null
    }

    private fun isWatched(pkg: String?): Boolean =
        pkg != null && (AppRules.isFacebookPackage(pkg) || AppRules.isBrowserPackage(pkg))

    companion object {
        private const val MAX_NODES = 1500
        private const val TAG = "LockIn"
        private const val MAX_DEPTH = 30
        private const val CONTENT_THROTTLE_MS = 250L
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val MESSENGER_PACKAGE = "com.facebook.orca"

        private val KNOWN_URL_BAR_IDS = listOf(
            "com.android.chrome:id/url_bar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        )
        private val URL_BAR_SUFFIXES = listOf(
            "url_bar",
            "mozac_browser_toolbar_url_view",
            "location_bar_edit_text",
        )
        private val URL_SHAPE = Regex(
            "^(?:[a-z][a-z0-9+.-]*://)?[a-z0-9-]+(?:\\.[a-z0-9-]+)+(?:[/?#]\\S*)?$",
            RegexOption.IGNORE_CASE,
        )

        private fun urlBarViewIds(pkg: String): List<String> =
            (KNOWN_URL_BAR_IDS + URL_BAR_SUFFIXES.map { "$pkg:id/$it" }).distinct()

        /** Address bars often show no scheme, so "https://" is added when it is missing. */
        private fun toFullUrl(text: String): String =
            if (text.contains("://")) text else "https://$text"

        /** On API 26-32 nodes must be recycled. Recycling is a no-op on API 33 and later. */
        @Suppress("DEPRECATION")
        private fun recycleNode(node: AccessibilityNodeInfo) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                try {
                    node.recycle()
                } catch (e: IllegalStateException) {
                    // Already recycled.
                }
            }
        }
    }
}
