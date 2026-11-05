package app.lockin.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class TipRotatorTest {

    @Test
    fun tipsComeFromMotivationSource() {
        assertEquals(Motivation.tips, TipRotator { 0.0 }.tips)
        assertEquals(32, TipRotator { 0.0 }.tips.size)
    }

    @Test
    fun tipsAreDistinctAndHaveNoPaddedWhitespace() {
        val tips = TipRotator { 0.0 }.tips
        assertEquals(tips.size, tips.toSet().size, "tips must be distinct")
        tips.forEach { tip ->
            assertTrue(tip.isNotEmpty(), "empty tip")
            assertEquals(tip, tip.trim(), "padded tip: '$tip'")
        }
    }

    @Test
    fun neverRepeatsTwiceInARowOverSeededRandom() {
        val rnd = Random(20260712L)
        val rotator = TipRotator { rnd.nextDouble() }
        var last = -1
        var previousIndex = -1
        for (i in 0 until 5000) {
            val (index, tip) = rotator.next(last)
            assertTrue(index in rotator.tips.indices, "index out of range: $index")
            assertEquals(rotator.tips[index], tip)
            if (previousIndex >= 0) assertNotEquals(previousIndex, index, "repeat at iteration $i")
            previousIndex = index
            last = index
        }
    }

    @Test
    fun chainedCallsNeverRepeatWhenRandomIsFixed() {
        for (fixed in listOf(0.0, 0.5, 0.9999999)) {
            val rotator = TipRotator { fixed }
            var last = 0
            repeat(1000) {
                val (index, _) = rotator.next(last)
                assertNotEquals(last, index)
                assertTrue(index in rotator.tips.indices)
                last = index
            }
        }
    }

    @Test
    fun indexStaysInRangeAtBothRandomExtremes() {
        for (value in listOf(0.0, 0.9999999)) {
            val rotator = TipRotator { value }
            val size = rotator.tips.size
            for (last in 0 until size) {
                val (index, tip) = rotator.next(last)
                assertTrue(index in 0 until size)
                assertNotEquals(last, index)
                assertEquals(rotator.tips[index], tip)
            }
        }
    }

    @Test
    fun outOfRangeRandomValuesAreClamped() {
        val size = TipRotator { 0.0 }.tips.size
        for (value in listOf(1.0, 2.0, -1.0, Double.NaN)) {
            val (index, _) = TipRotator { value }.next(0)
            assertTrue(index in 0 until size, "value $value gave $index")
            assertNotEquals(0, index)
        }
    }

    @Test
    fun outOfRangeLastIndexIsAccepted() {
        val rotator = TipRotator { 0.9999999 }
        val size = rotator.tips.size
        for (last in listOf(-1, size, 999)) {
            assertTrue(rotator.next(last).first in 0 until size)
        }
    }

    @Test
    fun everyTipIsReachable() {
        val rnd = Random(11L)
        val rotator = TipRotator { rnd.nextDouble() }
        val seen = mutableSetOf<Int>()
        var last = 0
        repeat(5000) {
            val (index, _) = rotator.next(last)
            seen.add(index)
            last = index
        }
        assertFalse(seen.isEmpty())
        assertEquals(rotator.tips.indices.toSet(), seen)
    }
}
