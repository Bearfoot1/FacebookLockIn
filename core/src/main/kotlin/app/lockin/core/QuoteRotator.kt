package app.lockin.core

/**
 * Picks motivational quotes for blocked screens. A quote is never repeated
 * immediately (when there is more than one quote).
 *
 * @param random source of values in [0, 1); injectable for tests.
 */
class QuoteRotator(private val random: () -> Double = Math::random) {

    val quotes: List<String> = Motivation.quotes

    /**
     * Returns (newIndex, quote). When there is more than one quote, newIndex is never
     * [lastIndex]; a lastIndex outside the list simply imposes no restriction.
     */
    fun next(lastIndex: Int): Pair<Int, String> {
        val index = pickDifferentIndex(quotes.size, lastIndex, random)
        return index to quotes[index]
    }
}

/**
 * Same no-repeat rotation as [QuoteRotator], over the tips from [Motivation.tips].
 */
class TipRotator(private val random: () -> Double = Math::random) {

    val tips: List<String> = Motivation.tips

    /**
     * Returns (newIndex, tip). When there is more than one tip, newIndex is never
     * [lastIndex]; a lastIndex outside the list simply imposes no restriction.
     */
    fun next(lastIndex: Int): Pair<Int, String> {
        val index = pickDifferentIndex(tips.size, lastIndex, random)
        return index to tips[index]
    }
}

/**
 * Picks a uniform index in [0, size) that differs from [lastIndex] when [lastIndex]
 * is in range and size > 1. Out-of-range random values are clamped.
 */
private fun pickDifferentIndex(size: Int, lastIndex: Int, random: () -> Double): Int {
    require(size > 0) { "cannot rotate an empty list" }
    if (size == 1) return 0

    val restricted = lastIndex in 0 until size
    val choices = if (restricted) size - 1 else size

    val unit = random().let { if (it.isNaN()) 0.0 else it.coerceIn(0.0, 0.999_999_999_999) }
    val pick = (unit * choices).toInt().coerceIn(0, choices - 1)
    return if (restricted && pick >= lastIndex) pick + 1 else pick
}
