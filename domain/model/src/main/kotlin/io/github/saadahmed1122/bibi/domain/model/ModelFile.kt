package io.github.saadahmed1122.bibi.domain.model

/** The one model file P1 accepts (SPEC-model-import.md; measured 2026-10-31 from the S2 file). */
object ModelFile {
    const val NAME = "gemma3-1b-it-int4.litertlm"
    const val SIZE_BYTES = 584_417_280L
    const val SHA256 = "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be"
}

/**
 * The action router's model (SPEC-tools.md): MediaPipe Decision Maker's embedding model. Optional;
 * without it only the shortcut phrases trigger actions. Measured 2026-10-31 from the S4 copy on the vivo.
 */
object RouterModelFile {
    const val NAME = "embeddinggemma-2-text-270m.litertlm"
    const val SIZE_BYTES = 164_626_432L
    const val SHA256 = "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb"
}
