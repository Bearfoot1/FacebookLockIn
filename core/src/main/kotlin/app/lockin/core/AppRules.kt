package app.lockin.core

/**
 * Rules for the native Android apps. The Android layer passes the package name and
 * the visible text nodes of the current screen.
 */
object AppRules {

    val facebookPackages: Set<String> = setOf(
        "com.facebook.katana",
        "com.facebook.lite",
        "com.facebook.orca", // Messenger
        "com.facebook.pages.app",
    )

    /**
     * Third-party Facebook wrappers (m.facebook.com in a WebView, no URL bar or tab bar to
     * read). They can't be judged screen by screen, so they are always blocked. The
     * Android layer also treats any app that opens facebook.com links, but not every
     * link, as a wrapper.
     */
    val facebookWrapperPackages: Set<String> = setOf(
        "io.friendly",
        "com.pitchedapps.frost",
        "com.creativetrends.folio",
    )

    val browserPackages: Set<String> = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.microsoft.emmx",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "com.sec.android.app.sbrowser",
        "com.brave.browser",
        "com.opera.browser",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
    )

    private const val MARKETPLACE_TITLE = "Marketplace"
    const val MESSENGER_PACKAGE = "com.facebook.orca"

    fun isFacebookPackage(packageName: String): Boolean = packageName in facebookPackages

    fun isBrowserPackage(packageName: String): Boolean = packageName in browserPackages

    /**
     * Heuristic: a visible text (trimmed) either equals "Marketplace" or starts with
     * "Marketplace" followed by a non-letter (e.g. "Marketplace (3)"). Case-insensitive.
     * An empty list is never a Marketplace screen.
     *
     * Known limitation: any on-screen text that matches this shape counts, so the
     * check is only as strict as the caller's choice of texts to pass in.
     */
    fun isMarketplaceScreen(visibleTexts: List<String>): Boolean {
        return visibleTexts.any { raw ->
            val text = raw.trim()
            if (text.length < MARKETPLACE_TITLE.length) return@any false
            if (!text.regionMatches(0, MARKETPLACE_TITLE, 0, MARKETPLACE_TITLE.length, ignoreCase = true)) {
                return@any false
            }
            text.length == MARKETPLACE_TITLE.length || !text[MARKETPLACE_TITLE.length].isLetter()
        }
    }

    // Facebook's bottom tab bar labels each tab "Marketplace, tab 4 of 6" (sometimes
    // with a suffix such as ", 1 new"). Seen on the Facebook app, Android 10, 2026-11.
    // Other languages keep the shape with their own words ("Marketplace, pestaña 4 de 6",
    // "Marketplace, onglet 4 sur 6"), so any word stands in for "tab" and "of".
    private val TAB_DESCRIPTION = Regex("^\\s*(.+?),\\s*\\p{L}+\\s+\\d+\\s+\\p{L}+\\s+\\d+\\b.*$", RegexOption.IGNORE_CASE)

    // Fallback guard: anything shaped like a Marketplace tab entry ("Marketplace, ...") is
    // never taken as a Marketplace title, even in a format tabLabel does not recognise.
    private val MARKETPLACE_TAB_LIKE = Regex("^\\s*marketplace\\s*[,،、，]", RegexOption.IGNORE_CASE)

    /** The tab's label ("Marketplace") if [description] is a tab bar description, else null. */
    fun tabLabel(description: String): String? =
        TAB_DESCRIPTION.matchEntire(description)?.groupValues?.get(1)?.trim()

    private val TAB_COUNT = Regex(",\\s*\\p{L}+\\s+\\d+\\s+\\p{L}+\\s+(\\d+)\\b", RegexOption.IGNORE_CASE)

    /**
     * The selected tab of Facebook's main bottom bar, given every tab description on
     * screen and whether its node is selected. Other tab strips (Marketplace search has
     * "Recent, tab 1 of 2") are ignored: the main bar is the group, by tab count, that
     * contains a Marketplace tab. Null when no main bar is on screen.
     */
    fun selectedMainTab(tabs: List<Pair<String, Boolean>>): String? {
        val parsed = tabs.mapNotNull { (description, selected) ->
            val label = tabLabel(description) ?: return@mapNotNull null
            val count = TAB_COUNT.find(description)?.groupValues?.get(1) ?: return@mapNotNull null
            Triple(label, count, selected)
        }
        // The main bar shows all of its tabs: N distinct labels that all say "of N".
        // That rules out carousels and lists ("Marketplace, photo 1 of 10") now that any
        // language's wording is accepted.
        val mainCount = parsed.filter { isMarketplaceTab(it.first) }.map { it.second }.distinct().firstOrNull { count ->
            parsed.filter { it.second == count }.map { it.first.lowercase() }.distinct().size == count.toInt()
        } ?: return null
        return parsed.firstOrNull { it.second == mainCount && it.third }?.first
    }

    /**
     * True when the package is a Facebook app and the screen is not Marketplace.
     * Messenger is allowed unless [blockMessenger] is true (the owner's toggle, off by
     * default); when blocked it is blocked on every screen.
     * Non-Facebook packages are never blocked by this rule.
     *
     * [selectedTab] is the label of the selected bottom tab, when a tab bar is on screen.
     * It decides on its own: the tab bar always contains a "Marketplace, tab N of M"
     * text, so the text heuristic would allow every tab. Without a tab bar (item
     * pages, full-screen posts), [lastSelectedTab] decides: a screen opened from the
     * Marketplace tab is allowed (item pages have no tab bar and no "Marketplace" title),
     * one opened from any other tab is blocked. With neither known, the text heuristic
     * is used with tab descriptions removed.
     */
    fun isBlockedAppScreen(
        packageName: String,
        visibleTexts: List<String>,
        selectedTab: String? = null,
        lastSelectedTab: String? = null,
        blockMessenger: Boolean = false,
    ): Boolean {
        if (packageName == MESSENGER_PACKAGE) return blockMessenger
        if (!isFacebookPackage(packageName)) return false
        if (selectedTab != null) return !isMarketplaceTab(selectedTab)
        if (lastSelectedTab != null) return !isMarketplaceTab(lastSelectedTab)
        return !isMarketplaceScreen(visibleTexts.filter { tabLabel(it) == null && !MARKETPLACE_TAB_LIKE.containsMatchIn(it) })
    }

    private fun isMarketplaceTab(label: String): Boolean = label.trim().equals(MARKETPLACE_TITLE, ignoreCase = true)
}
