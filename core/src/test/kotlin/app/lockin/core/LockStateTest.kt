package app.lockin.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class LockStateTest {

    @Test
    fun dataClassEqualityAndCopy() {
        val state = LockState(enabled = true, blockCount = 2, lastQuote = 5)
        assertEquals(LockState(true, 2, 5), state)
        assertEquals(LockState(true, 3, 5), state.copy(blockCount = 3))
        assertNotEquals(LockState(false, 2, 5), state)
    }
}
