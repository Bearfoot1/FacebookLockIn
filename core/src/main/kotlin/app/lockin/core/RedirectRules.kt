package app.lockin.core

/**
 * Rules for redirect apps: non-Facebook apps the owner chose to bounce to Marketplace
 * whenever they are opened. The Android layer stores the chosen package names.
 */
object RedirectRules {

    /** A well-known distracting app, shown with an on/off toggle. Some apps ship under several packages. */
    data class CommonApp(val name: String, val packages: List<String>)

    val commonApps: List<CommonApp> = listOf(
        CommonApp("Instagram", listOf("com.instagram.android")),
        CommonApp("TikTok", listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")),
        CommonApp("YouTube", listOf("com.google.android.youtube")),
        CommonApp("Snapchat", listOf("com.snapchat.android")),
        CommonApp("X (Twitter)", listOf("com.twitter.android")),
        CommonApp("Reddit", listOf("com.reddit.frontpage")),
        CommonApp("Threads", listOf("com.instagram.barcelona")),
        CommonApp("Pinterest", listOf("com.pinterest")),
        CommonApp("Twitch", listOf("tv.twitch.android.app")),
        CommonApp("Netflix", listOf("com.netflix.mediaclient")),
        CommonApp("Discord", listOf("com.discord")),
        CommonApp("Tumblr", listOf("com.tumblr")),
    )

    /**
     * Packages that can never be redirect apps: Facebook (handled by AppRules, and the
     * Marketplace target itself), browsers (the Marketplace fallback, and handled by
     * WebRules), and system screens the owner needs to get out of a lock.
     */
    private val neverRedirect: Set<String> = AppRules.facebookPackages + AppRules.browserPackages + setOf(
        "com.android.settings",
        "com.android.systemui",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
    )

    /** True when [packageName] may be chosen as a redirect app. */
    fun canRedirect(packageName: String): Boolean =
        packageName.isNotBlank() && packageName !in neverRedirect

    /** True when [packageName] is one of the [enabled] redirect apps and may be redirected. */
    fun shouldRedirect(packageName: String, enabled: Set<String>): Boolean =
        packageName in enabled && canRedirect(packageName)

    /** The common app that ships under [packageName], or null for any other app. */
    fun commonAppFor(packageName: String): CommonApp? =
        commonApps.firstOrNull { packageName in it.packages }

    /** Redirect apps that can be switched on at once without Pro. */
    const val FREE_REDIRECT_LIMIT = 5

    /**
     * The switched-on redirect apps as the owner sees them: one unit per common app
     * (TikTok's two packages are one app), one per custom package. Common apps come
     * first in list order, then custom packages sorted, so the order is stable.
     */
    fun redirectUnits(enabled: Set<String>): List<Set<String>> {
        val common = commonApps.filter { app -> app.packages.any { it in enabled } }.map { it.packages.toSet() }
        val custom = enabled.filter { commonAppFor(it) == null && canRedirect(it) }.sorted().map { setOf(it) }
        return common + custom
    }

    /** How many redirect apps are switched on. */
    fun redirectAppCount(enabled: Set<String>): Int = redirectUnits(enabled).size

    /** True when one more redirect app may be switched on. */
    fun canEnableAnother(enabled: Set<String>, pro: Boolean): Boolean =
        pro || redirectAppCount(enabled) < FREE_REDIRECT_LIMIT

    /**
     * The packages the lock actually redirects. With Pro, all of [enabled]. Without
     * (a lapsed subscription), only the first FREE_REDIRECT_LIMIT apps in the stable order.
     */
    fun activeRedirects(enabled: Set<String>, pro: Boolean): Set<String> {
        if (pro) return enabled
        return redirectUnits(enabled).take(FREE_REDIRECT_LIMIT).flatten().filter { it in enabled }.toSet()
    }
}
