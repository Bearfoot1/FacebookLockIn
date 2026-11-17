package app.lockin.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RedirectRulesTest {

    @Test
    fun enabledAppIsRedirected() {
        assertTrue(RedirectRules.shouldRedirect("com.instagram.android", setOf("com.instagram.android")))
        assertTrue(RedirectRules.shouldRedirect("com.example.game", setOf("com.example.game")))
    }

    @Test
    fun appNotEnabledIsNotRedirected() {
        assertFalse(RedirectRules.shouldRedirect("com.instagram.android", emptySet()))
        assertFalse(RedirectRules.shouldRedirect("com.instagram.android", setOf("com.snapchat.android")))
    }

    @Test
    fun facebookAppsAreNeverRedirectApps() {
        for (pkg in AppRules.facebookPackages) {
            assertFalse(RedirectRules.canRedirect(pkg), pkg)
            assertFalse(RedirectRules.shouldRedirect(pkg, setOf(pkg)), pkg)
        }
    }

    @Test
    fun browsersAreNeverRedirectApps() {
        // A browser is the Marketplace fallback; redirecting it would loop.
        for (pkg in AppRules.browserPackages) {
            assertFalse(RedirectRules.shouldRedirect(pkg, setOf(pkg)), pkg)
        }
    }

    @Test
    fun systemScreensAreNeverRedirectApps() {
        for (pkg in listOf("com.android.settings", "com.android.systemui")) {
            assertFalse(RedirectRules.shouldRedirect(pkg, setOf(pkg)), pkg)
        }
        assertFalse(RedirectRules.canRedirect(""))
        assertFalse(RedirectRules.canRedirect("   "))
    }

    @Test
    fun commonAppsCanAllBeRedirected() {
        for (app in RedirectRules.commonApps) {
            for (pkg in app.packages) assertTrue(RedirectRules.canRedirect(pkg), pkg)
        }
    }

    @Test
    fun commonAppPackagesAreUnique() {
        val all = RedirectRules.commonApps.flatMap { it.packages }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun commonAppForFindsEveryPackage() {
        assertEquals("TikTok", RedirectRules.commonAppFor("com.zhiliaoapp.musically")?.name)
        assertEquals("TikTok", RedirectRules.commonAppFor("com.ss.android.ugc.trill")?.name)
        assertEquals("YouTube", RedirectRules.commonAppFor("com.google.android.youtube")?.name)
        assertNull(RedirectRules.commonAppFor("com.example.game"))
    }

    private val sixApps = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.google.android.youtube",
        "com.snapchat.android",
        "com.example.game",
        "com.example.other",
    )

    @Test
    fun tiktokPackagesCountAsOneApp() {
        assertEquals(1, RedirectRules.redirectAppCount(setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")))
        assertEquals(6, RedirectRules.redirectAppCount(sixApps))
    }

    @Test
    fun freeLimitIsFiveApps() {
        val four = setOf("com.instagram.android", "com.google.android.youtube", "com.snapchat.android", "com.example.game")
        assertTrue(RedirectRules.canEnableAnother(four, pro = false))
        val five = four + "com.example.other"
        assertFalse(RedirectRules.canEnableAnother(five, pro = false))
        assertTrue(RedirectRules.canEnableAnother(five, pro = true))
    }

    @Test
    fun activeRedirectsKeepsFirstFiveWithoutPro() {
        val active = RedirectRules.activeRedirects(sixApps, pro = false)
        assertEquals(5, RedirectRules.redirectAppCount(active))
        // Common apps come first; the last custom package (sorted) is the one dropped.
        assertTrue("com.ss.android.ugc.trill" in active)
        assertTrue("com.example.game" in active)
        assertFalse("com.example.other" in active)
    }

    @Test
    fun activeRedirectsKeepsAllWithPro() {
        assertEquals(sixApps, RedirectRules.activeRedirects(sixApps, pro = true))
    }

    @Test
    fun activeRedirectsUnderLimitIsUnchanged() {
        val two = setOf("com.instagram.android", "com.example.game")
        assertEquals(two, RedirectRules.activeRedirects(two, pro = false))
    }
}
