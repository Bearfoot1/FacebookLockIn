package app.lockin.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class QuoteRotatorTest {

    @Test
    fun quotesComeFromMotivationSource() {
        assertEquals(Motivation.quotes, QuoteRotator { 0.0 }.quotes)
        assertTrue(QuoteRotator { 0.0 }.quotes.size >= 30, "expected at least 30 quotes")
    }

    @Test
    fun quotesAreDistinctAndHaveNoPaddedWhitespace() {
        val quotes = QuoteRotator { 0.0 }.quotes
        assertEquals(quotes.size, quotes.toSet().size, "quotes must be distinct")
        quotes.forEach { quote ->
            assertTrue(quote.isNotEmpty(), "empty quote")
            assertEquals(quote, quote.trim(), "padded quote: '$quote'")
        }
    }

    @Test
    fun quotesHaveNoEmDashAttribution() {
        QuoteRotator { 0.0 }.quotes.forEach { quote ->
            assertFalse(quote.contains("—"), quote)
        }
    }

    @Test
    fun neverRepeatsTwiceInARowOverSeededRandom() {
        val rnd = Random(20260711L)
        val rotator = QuoteRotator { rnd.nextDouble() }
        var last = -1
        var previousIndex = -1
        for (i in 0 until 5000) {
            val (index, quote) = rotator.next(last)
            assertTrue(index in rotator.quotes.indices, "index out of range: $index")
            assertEquals(rotator.quotes[index], quote)
            if (previousIndex >= 0) assertNotEquals(previousIndex, index, "repeat at iteration $i")
            previousIndex = index
            last = index
        }
    }

    @Test
    fun chainedCallsNeverRepeatWhenRandomIsFixed() {
        for (fixed in listOf(0.0, 0.5, 0.9999999)) {
            val rotator = QuoteRotator { fixed }
            var last = 0
            repeat(1000) {
                val (index, _) = rotator.next(last)
                assertNotEquals(last, index)
                assertTrue(index in rotator.quotes.indices)
                last = index
            }
        }
    }

    @Test
    fun indexStaysInRangeWhenRandomReturnsAlmostOne() {
        val rotator = QuoteRotator { 0.9999999 }
        val size = rotator.quotes.size
        for (last in 0 until size) {
            val (index, quote) = rotator.next(last)
            assertTrue(index in 0 until size, "index $index out of range")
            assertNotEquals(last, index)
            assertEquals(rotator.quotes[index], quote)
        }
    }

    @Test
    fun indexStaysInRangeWhenRandomReturnsZero() {
        val rotator = QuoteRotator { 0.0 }
        val size = rotator.quotes.size
        for (last in 0 until size) {
            val (index, _) = rotator.next(last)
            assertTrue(index in 0 until size)
            assertNotEquals(last, index)
        }
    }

    @Test
    fun outOfRangeLastIndexIsAccepted() {
        val rotator = QuoteRotator { 0.9999999 }
        val size = rotator.quotes.size
        for (last in listOf(-1, size, 999)) {
            val (index, _) = rotator.next(last)
            assertTrue(index in 0 until size)
        }
        assertEquals(size - 1, rotator.next(-1).first)
    }

    @Test
    fun outOfRangeRandomValuesAreClamped() {
        val size = QuoteRotator { 0.0 }.quotes.size
        for (value in listOf(1.0, 1.5, -0.3, Double.NaN, Double.POSITIVE_INFINITY)) {
            val rotator = QuoteRotator { value }
            val (index, _) = rotator.next(0)
            assertTrue(index in 0 until size, "value $value gave $index")
            assertNotEquals(0, index)
        }
    }

    @Test
    fun everyQuoteIsReachableFromSomeLastIndex() {
        val rnd = Random(7L)
        val rotator = QuoteRotator { rnd.nextDouble() }
        val seen = mutableSetOf<Int>()
        var last = 0
        repeat(5000) {
            val (index, _) = rotator.next(last)
            seen.add(index)
            last = index
        }
        assertEquals(rotator.quotes.indices.toSet(), seen)
    }
}
