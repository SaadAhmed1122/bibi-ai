package io.github.saadahmed1122.bibi.companion.overlay

/** How far the chat card sits above its resting place so the keyboard doesn't cover it. Pure, for tests. */
internal object KeyboardLift {
    /**
     * Screen y values in px. [overlap] is how much of the card the keyboard covers now, with the card's
     * bottom edge at [windowBottom]; at rest that edge is at [restBottom]. A covered card is lifted so its
     * bottom sits [gap] above the keyboard. The result depends only on where the keyboard is, so the same
     * report twice gives the same lift. A card already clear stays put; a hidden keyboard drops it back.
     */
    fun next(current: Int, keyboardVisible: Boolean, overlap: Int, windowBottom: Int, restBottom: Int, gap: Int): Int =
        when {
            !keyboardVisible -> 0
            overlap > 0 -> maxOf(0, restBottom - (windowBottom - overlap - gap))
            else -> current
        }
}
