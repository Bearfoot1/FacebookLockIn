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
}
