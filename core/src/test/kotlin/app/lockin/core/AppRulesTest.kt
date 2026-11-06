package app.lockin.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AppRulesTest {

    // Real tab bar descriptions from the Facebook app (Galaxy S9+, Android 10, 2026-11-05).
    private val tabBar = listOf(
        "Home, tab 1 of 6", "Reels, tab 2 of 6", "Friends, tab 3 of 6",
        "Marketplace, tab 4 of 6, 1 new", "Notifications, tab 5 of 6", "Menu, tab 6 of 6",
    )

    @Test
    fun tabLabelParsesTabDescriptions() {
        assertEquals("Home", AppRules.tabLabel("Home, tab 1 of 6"))
        assertEquals("Marketplace", AppRules.tabLabel("Marketplace, tab 4 of 6, 1 new"))
        assertEquals(null, AppRules.tabLabel("Marketplace"))
        assertEquals(null, AppRules.tabLabel("What's on your mind?"))
    }

    @Test
    fun feedWithTabBarIsBlocked() {
        // The tab bar's "Marketplace, tab 4 of 6" text must not unlock the feed.
        assertTrue(AppRules.isBlockedAppScreen("com.facebook.katana", tabBar, selectedTab = "Home"))
        assertTrue(AppRules.isBlockedAppScreen("com.facebook.katana", tabBar + "Marketplace", selectedTab = "Reels"))
    }

    @Test
    fun marketplaceTabIsAllowed() {
        assertFalse(AppRules.isBlockedAppScreen("com.facebook.katana", tabBar, selectedTab = "Marketplace"))
    }

    // Real item page texts (no tab bar, no "Marketplace" title).
    private val itemPage = listOf("Close", "Product Image,1 of 1", "Lord of the Rings Cartoon VHS", "$5", "Message seller")

    @Test
    fun screenWithoutTabBarFollowsLastTab() {
        assertFalse(AppRules.isBlockedAppScreen("com.facebook.katana", itemPage, lastSelectedTab = "Marketplace"))
        assertTrue(AppRules.isBlockedAppScreen("com.facebook.katana", itemPage, lastSelectedTab = "Home"))
        assertTrue(AppRules.isBlockedAppScreen("com.facebook.katana", itemPage))
    }

    @Test
    fun visibleTabBarOverridesLastTab() {
        assertTrue(AppRules.isBlockedAppScreen("com.facebook.katana", tabBar, selectedTab = "Reels", lastSelectedTab = "Marketplace"))
    }

    @Test
    fun messengerBlockedEvenFromMarketplace() {
        assertTrue(AppRules.isBlockedAppScreen("com.facebook.orca", itemPage, lastSelectedTab = "Marketplace"))
    }

    @Test
    fun tabDescriptionsAloneDoNotCountAsMarketplace() {
        // No selected tab known: the tab bar text is ignored by the heuristic.
        assertTrue(AppRules.isBlockedAppScreen("com.facebook.katana", tabBar))
        assertFalse(AppRules.isBlockedAppScreen("com.facebook.katana", tabBar + "Marketplace"))
    }

    @Test
    fun facebookPackagesMatchSpec() {
        assertEquals(
            setOf("com.facebook.katana", "com.facebook.lite", "com.facebook.orca", "com.facebook.pages.app"),
            AppRules.facebookPackages,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["com.facebook.katana", "com.facebook.lite", "com.facebook.orca", "com.facebook.pages.app"])
    fun isFacebookPackageTrueForFacebookApps(pkg: String) {
        assertTrue(AppRules.isFacebookPackage(pkg))
    }

    @ParameterizedTest
    @ValueSource(strings = ["com.android.chrome", "com.facebook.katana.fake", "com.facebook", "", "com.instagram.android"])
    fun isFacebookPackageFalseForOthers(pkg: String) {
        assertFalse(AppRules.isFacebookPackage(pkg))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.microsoft.emmx",
            "org.mozilla.firefox", "org.mozilla.firefox_beta", "com.sec.android.app.sbrowser",
            "com.brave.browser", "com.opera.browser", "com.duckduckgo.mobile.android", "com.vivaldi.browser",
        ]
    )
    fun isBrowserPackageTrueForKnownBrowsers(pkg: String) {
        assertTrue(AppRules.isBrowserPackage(pkg))
    }

    @ParameterizedTest
    @ValueSource(strings = ["com.facebook.katana", "com.android.settings", "", "com.android.chrome.fake"])
    fun isBrowserPackageFalseForOthers(pkg: String) {
        assertFalse(AppRules.isBrowserPackage(pkg))
    }

    @Test
    fun marketplaceScreenExactTitle() {
        assertTrue(AppRules.isMarketplaceScreen(listOf("Marketplace")))
    }

    @Test
    fun marketplaceScreenIsCaseInsensitiveAndTrimmed() {
        assertTrue(AppRules.isMarketplaceScreen(listOf("  marketplace  ")))
        assertTrue(AppRules.isMarketplaceScreen(listOf("MARKETPLACE")))
    }

    @Test
    fun marketplaceScreenPrefixFollowedByNonLetter() {
        assertTrue(AppRules.isMarketplaceScreen(listOf("Marketplace (3)")))
        assertTrue(AppRules.isMarketplaceScreen(listOf("Marketplace - Buy and sell")))
        assertTrue(AppRules.isMarketplaceScreen(listOf("Marketplace 2")))
        assertTrue(AppRules.isMarketplaceScreen(listOf("Marketplace.")))
    }

    @Test
    fun marketplaceScreenRejectsLetterAfterPrefix() {
        assertFalse(AppRules.isMarketplaceScreen(listOf("Marketplaces")))
        assertFalse(AppRules.isMarketplaceScreen(listOf("Marketplacefake")))
    }

    @Test
    fun marketplaceScreenMatchesAnyTextInList() {
        assertTrue(AppRules.isMarketplaceScreen(listOf("Notifications", "Marketplace", "Home")))
    }

    @Test
    fun marketplaceScreenFalseForUnrelatedOrPartialText() {
        assertFalse(AppRules.isMarketplaceScreen(listOf("News Feed", "Reels", "Groups")))
        assertFalse(AppRules.isMarketplaceScreen(listOf("Buy and sell")))
        assertFalse(AppRules.isMarketplaceScreen(listOf("Market")))
        assertFalse(AppRules.isMarketplaceScreen(listOf("Go to Marketplace")))
        assertFalse(AppRules.isMarketplaceScreen(listOf("")))
    }

    @Test
    fun marketplaceScreenFalseForEmptyList() {
        assertFalse(AppRules.isMarketplaceScreen(emptyList()))
    }

    @Test
    fun blockedWhenFacebookAppNotMarketplace() {
        assertTrue(AppRules.isBlockedAppScreen("com.facebook.katana", listOf("News Feed")))
        assertTrue(AppRules.isBlockedAppScreen("com.facebook.lite", emptyList()))
        assertTrue(AppRules.isBlockedAppScreen("com.facebook.pages.app", listOf("Inbox")))
    }

    @Test
    fun notBlockedWhenFacebookAppShowsMarketplace() {
        assertFalse(AppRules.isBlockedAppScreen("com.facebook.katana", listOf("Marketplace", "Sell")))
        assertFalse(AppRules.isBlockedAppScreen("com.facebook.lite", listOf("Marketplace (2)")))
        assertFalse(AppRules.isBlockedAppScreen("com.facebook.pages.app", listOf("marketplace")))
    }

    @Test
    fun messengerAlwaysBlocked() {
        assertTrue(AppRules.isBlockedAppScreen("com.facebook.orca", listOf("Marketplace")))
        assertTrue(AppRules.isBlockedAppScreen("com.facebook.orca", listOf("Marketplace (1)", "Chats")))
        assertTrue(AppRules.isBlockedAppScreen("com.facebook.orca", emptyList()))
    }

    @Test
    fun nonFacebookPackagesNeverBlockedByThisRule() {
        assertFalse(AppRules.isBlockedAppScreen("com.android.chrome", listOf("News Feed")))
        assertFalse(AppRules.isBlockedAppScreen("com.example.app", emptyList()))
    }
}
