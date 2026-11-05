package app.lockin.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AppRulesTest {

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
