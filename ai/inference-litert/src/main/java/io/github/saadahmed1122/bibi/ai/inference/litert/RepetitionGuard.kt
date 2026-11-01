package io.github.saadahmed1122.bibi.ai.inference.litert

/**
 * Stops greedy decoding's loops (#107): Gemma 3 1B with top-k 1 can repeat a short phrase until
 * the token cap ("Sara-Marie-Jones-Smith-Jones-Smith-…", about 35 s on the vivo). A loop is the
 * same piece of [MIN_UNIT]..[MAX_UNIT] characters, with a letter or digit in it, repeated at least
 * [MIN_REPEATS] times at the end and covering [MIN_LOOP_CHARS] or more.
 */
internal object RepetitionGuard {
    private const val MIN_UNIT = 2
    private const val MAX_UNIT = 60
    private const val MIN_REPEATS = 3
    private const val MIN_LOOP_CHARS = 30

    /** The reply to keep (up to the first copy of the repeated piece, then "…"), or null when [text] isn't looping. */
    fun check(text: String): String? {
        val end = text.trimEnd()
        for (unit in MIN_UNIT..minOf(MAX_UNIT, end.length / MIN_REPEATS)) {
            val piece = end.substring(end.length - unit)
            if (piece.none(Char::isLetterOrDigit)) continue
            var repeats = 1
            var start = end.length - unit
            while (start >= unit && end.regionMatches(start - unit, piece, 0, unit)) {
                repeats++
                start -= unit
            }
            if (repeats >= MIN_REPEATS && repeats * unit >= MIN_LOOP_CHARS) {
                return end.substring(0, start + unit).trimEnd() + "…"
            }
        }
        return null
    }
}
