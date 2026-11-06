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

    private val browserPackages: Set<String> = setOf(
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
    private val TAB_DESCRIPTION = Regex("^\\s*(.+?),\\s*tab\\s+\\d+\\s+of\\s+\\d+\\b.*$", RegexOption.IGNORE_CASE)

    /** The tab's label ("Marketplace") if [description] is a tab bar description, else null. */
    fun tabLabel(description: String): String? =
        TAB_DESCRIPTION.matchEntire(description)?.groupValues?.get(1)?.trim()

    /**
     * True when the package is a Facebook app and the screen is not Marketplace.
     * Messenger is always blocked, even when its texts look like Marketplace.
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
    ): Boolean {
        if (packageName == "com.facebook.orca") return true
        if (!isFacebookPackage(packageName)) return false
        if (selectedTab != null) return !isMarketplaceTab(selectedTab)
        if (lastSelectedTab != null) return !isMarketplaceTab(lastSelectedTab)
        return !isMarketplaceScreen(visibleTexts.filter { tabLabel(it) == null })
    }

    private fun isMarketplaceTab(label: String): Boolean = label.trim().equals(MARKETPLACE_TITLE, ignoreCase = true)
}
