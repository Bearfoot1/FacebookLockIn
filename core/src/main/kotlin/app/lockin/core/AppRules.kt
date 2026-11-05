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

    /**
     * True when the package is a Facebook app and the screen is not Marketplace.
     * Messenger is always blocked, even when its texts look like Marketplace.
     * Non-Facebook packages are never blocked by this rule.
     */
    fun isBlockedAppScreen(packageName: String, visibleTexts: List<String>): Boolean {
        if (packageName == "com.facebook.orca") return true
        if (!isFacebookPackage(packageName)) return false
        return !isMarketplaceScreen(visibleTexts)
    }
}
