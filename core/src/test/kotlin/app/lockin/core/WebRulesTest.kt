package app.lockin.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class WebRulesTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://www.facebook.com/marketplace/",
            "https://www.facebook.com/marketplace",
            "https://www.facebook.com/marketplace/item/123456789/",
            "https://www.facebook.com/marketplace/category/vehicles",
            "https://www.facebook.com/marketplace/search/?query=couch",
            "https://www.facebook.com/marketplace/create/item",
            "https://m.facebook.com/marketplace/london/",
            "https://web.facebook.com/marketplace/item/42",
            "https://mbasic.facebook.com/marketplace/",
            "https://facebook.com/marketplace/",
            "http://www.facebook.com/marketplace/",
            "https://WWW.FACEBOOK.COM/Marketplace/",
            "https://www.facebook.com/MARKETPLACE/item/1",
            "https://www.facebook.com:443/marketplace/",
            "https://www.facebook.com/login",
            "https://www.facebook.com/login/",
            "https://www.facebook.com/login.php?next=x",
            "https://www.facebook.com/checkpoint/1501092823525282/",
            "https://www.facebook.com/recover/initiate/",
            "https://www.facebook.com/two_step_verification/authentication/",
            "https://www.facebook.com/help/",
            "https://www.facebook.com/policies/",
            "https://www.facebook.com/privacy/policy/",
            "https://www.facebook.com/cookies/",
            "https://www.facebook.com/LOGIN.PHP",
            "https://www.facebook.com/marketplace?ref=bookmark",
            "https://www.facebook.com/marketplace/?ref=bookmark",
            "/marketplace/item/1",
            "https://www.facebook.com/marketplace/search/?query=bike&exact=false",
            "https://www.facebook.com/marketplace/item/9?ref=search",
            "https://www.facebook.com/marketplace/you/selling",
            "https://www.facebook.com/marketplace#top",
            "https://evil.example@www.facebook.com/marketplace/",
            "facebook.com/marketplace/",
        ]
    )
    fun allowsMarketplaceLoginAndLegalUrls(url: String) {
        assertTrue(WebRules.isAllowedUrl(url), "expected allowed: $url")
        assertFalse(WebRules.isLockedUrl(url), "expected not locked: $url")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://www.facebook.com/",
            "https://www.facebook.com",
            "https://m.facebook.com/",
            "https://www.facebook.com/?ref=bookmark",
            "https://www.facebook.com/?q=marketplace",
            "/groups/x",
            "/",
            "https://www.facebook.com/home.php",
            "https://www.facebook.com/terms.php",
            "https://www.facebook.com/reel/1234567890",
            "https://www.facebook.com/reels/",
            "https://www.facebook.com/watch/?v=123",
            "https://www.facebook.com/groups/somegroup/",
            "https://www.facebook.com/messages/t/123",
            "https://www.facebook.com/messenger/",
            "https://www.facebook.com/search/top/?q=cats",
            "https://www.facebook.com/someprofile",
            "https://www.facebook.com/profile.php?id=100",
            "https://www.facebook.com/marketplacefake",
            "https://www.facebook.com/marketplacefake/item/1",
            "https://www.facebook.com/loginx",
            "https://www.facebook.com/helpful/",
            "https://www.facebook.com/login_fake.php",
            "https://www.facebook.com//reel/1",
            "https://www.facebook.com/%72eel/1",
            "https://www.facebook.com/",
            "http://www.facebook.com/groups/x",
            "https://m.facebook.com:8443/reel/1",
            "HTTPS://WWW.FACEBOOK.COM/GROUPS/X",
            "https://www.facebook.com/?",
            "facebook.com/",
            "facebook.com",
        ]
    )
    fun locksFeedReelsGroupsMessengerAndLookalikes(url: String) {
        assertFalse(WebRules.isAllowedUrl(url), "expected locked: $url")
        assertTrue(WebRules.isLockedUrl(url), "expected locked: $url")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://www.example.com/",
            "https://www.facebook.com.evil.test/login",
            "https://facebook.com.evil.test/",
            "https://facebook.com.evil.test/marketplace/",
            "https://notfacebook.com/",
            "https://notfacebook.com/marketplace/",
            "https://www.example.com/reel/1",
            "https://notfacebook.com/x",
            "https://example.org/groups/foo",
            "https://www.google.com/search?q=facebook",
            "https://facebookk.com/groups/",
            "not a url %%% at all",
            "http://[::1",
            "?ref=abc",
            "#section",
            "   ",
        ]
    )
    fun failsOpenForNonFacebookAndUnparseableUrls(url: String) {
        if (url.isBlank()) {
            // Blank input is treated as the Facebook home page and locked.
            assertFalse(WebRules.isAllowedUrl(url))
            return
        }
        assertTrue(WebRules.isAllowedUrl(url), "expected allowed: $url")
        assertFalse(WebRules.isLockedUrl(url), "expected not locked: $url")
    }

    @Test
    fun emptyStringIsLockedAsHome() {
        assertFalse(WebRules.isAllowedUrl(""))
        assertTrue(WebRules.isLockedUrl(""))
    }

    @Test
    fun slashIsHomeAndLocked() {
        assertFalse(WebRules.isAllowedUrl("/"))
        assertTrue(WebRules.isLockedUrl("/"))
    }

    @Test
    fun allowedPathPrefixesMatchSpec() {
        assertEquals(
            listOf(
                "marketplace", "login", "login.php", "checkpoint", "recover",
                "two_step_verification", "help", "policies", "privacy", "terms", "cookies",
            ),
            WebRules.allowedPathPrefixes,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["facebook.com", "www.facebook.com", "M.FACEBOOK.COM", "web.facebook.com", "mbasic.facebook.com", "facebook.com.", "a.b.facebook.com"])
    fun isFacebookHostAcceptsFacebookAndSubdomains(host: String) {
        assertTrue(WebRules.isFacebookHost(host), host)
    }

    @ParameterizedTest
    @ValueSource(strings = ["notfacebook.com", "facebook.com.evil.test", "evilfacebook.com", "example.com", "", "facebook", "fb.com", "facebook.co"])
    fun isFacebookHostRejectsLookalikes(host: String) {
        assertFalse(WebRules.isFacebookHost(host), host)
    }

    @Test
    fun marketplaceUrlConstant() {
        assertEquals("https://www.facebook.com/marketplace/", WebRules.MARKETPLACE_URL)
        assertTrue(WebRules.isAllowedUrl(WebRules.MARKETPLACE_URL))
    }

    @Test
    fun marketplaceSearchUrlWithQuery() {
        assertEquals(
            "https://www.facebook.com/marketplace/search/?query=red%20bike",
            WebRules.marketplaceSearchUrl("red bike"),
        )
    }

    @Test
    fun marketplaceSearchUrlTrimsAndEncodes() {
        assertEquals(
            "https://www.facebook.com/marketplace/search/?query=tv%20%26%20stand%20%C3%A9",
            WebRules.marketplaceSearchUrl("  tv & stand é  "),
        )
    }

    @Test
    fun marketplaceSearchUrlBlankQueryHasNoQueryString() {
        assertEquals("https://www.facebook.com/marketplace/search/", WebRules.marketplaceSearchUrl(""))
        assertEquals("https://www.facebook.com/marketplace/search/", WebRules.marketplaceSearchUrl("   "))
    }

    @Test
    fun marketplaceSearchUrlIsAllowed() {
        assertTrue(WebRules.isAllowedUrl(WebRules.marketplaceSearchUrl("sofa")))
    }
}
