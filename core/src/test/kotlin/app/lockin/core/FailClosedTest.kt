package app.lockin.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FailClosedTest {
    @Test
    fun `unparseable Facebook text with a raw space is locked`() {
        assertTrue(WebRules.isLockedUrl("https://www.facebook.com/groups/x y"))
        assertTrue(WebRules.isLockedUrl("www.facebook.com/reel/1 "))
    }

    @Test
    fun `unparseable Messenger text is locked`() {
        assertTrue(WebRules.isLockedUrl("https://www.messenger.com/t/1 x"))
    }

    @Test
    fun `unparseable text that does not mention Facebook still fails open`() {
        assertFalse(WebRules.isLockedUrl("not a url at all"))
    }

    @Test
    fun `other sites and searches that mention Facebook are never locked`() {
        // Seen in Chrome (2026-11-06): Lock-In must only ever act on Facebook itself.
        assertFalse(WebRules.isLockedUrl("https://how to unblock facebook.com feed"))
        assertFalse(WebRules.isLockedUrl("example.com/share?u=facebook.com/reel/1 x"))
        assertFalse(WebRules.isLockedUrl("https://example.com facebook.com/watch"))
        assertFalse(WebRules.isLockedUrl("https://www.google.com/search?q=facebook.com+reels"))
        assertFalse(WebRules.isLockedUrl("https://facebook.com.evil.test/watch x"))
        assertFalse(WebRules.isLockedUrl("about:blank"))
        assertFalse(WebRules.isLockedUrl("https://about:blank"))
    }

    @Test
    fun `Samsung Internet URL bar text with a left-to-right mark is judged`() {
        // Real URL bar text: U+200E then the host, no scheme, no path.
        assertTrue(WebRules.isLockedUrl("https://\u200Em.facebook.com"))
        assertTrue(WebRules.isLockedUrl("\u200Ewww.facebook.com/watch/"))
        assertFalse(WebRules.isLockedUrl("\u200Ewww.facebook.com/marketplace/"))
        assertTrue(WebRules.isLockedUrl("https://www.face\u200Bbook.com/reel/1"))
    }

    @Test
    fun `host-less input that mentions Facebook is locked`() {
        assertTrue(WebRules.isLockedUrl("https://m.facebook.com\u00A0/watch"))
    }

    @Test
    fun `host-only URL bar text is detected`() {
        assertTrue(WebRules.isHostOnlyDisplay("\u200Em.facebook.com"))
        assertTrue(WebRules.isHostOnlyDisplay("https://\u200Em.facebook.com"))
        assertTrue(WebRules.isHostOnlyDisplay("www.facebook.com"))
        assertFalse(WebRules.isHostOnlyDisplay("m.facebook.com/watch"))
        assertFalse(WebRules.isHostOnlyDisplay("https://www.facebook.com/"))
        assertFalse(WebRules.isHostOnlyDisplay("facebook.com?sk=h"))
        assertFalse(WebRules.isHostOnlyDisplay(""))
        assertFalse(WebRules.isHostOnlyDisplay("search facebook"))
    }
}
