package io.github.saadahmed1122.bibi.domain.model

/**
 * The voice pack (SPEC-voice.md): Parakeet TDT 0.6B v2 int8 for speech-to-text and Piper en_US amy
 * low for speech, the exact files S3 measured. Sizes and SHA-256 taken from the S3 copies on the
 * vivo, 2026-11-01. [sourceHint] is part of the folder name in the downloaded archives, used to tell
 * the two `tokens.txt` files apart in a picked folder.
 */
object VoicePack {
    data class Entry(val dir: String, val sourceHint: String, val name: String, val size: Long, val sha256: String)

    const val STT_DIR = "parakeet-tdt-0.6b-v2-int8"
    const val TTS_DIR = "piper-en_US-amy-low"

    val FILES = listOf(
        Entry(STT_DIR, "parakeet", "encoder.int8.onnx", 652_184_296, "a32b12d17bbbc309d0686fbbcc2987b5e9b8333a7da83fa6b089f0a2acd651ab"),
        Entry(STT_DIR, "parakeet", "decoder.int8.onnx", 7_257_753, "b6bb64963457237b900e496ee9994b59294526439fbcc1fecf705b31a15c6b4e"),
        Entry(STT_DIR, "parakeet", "joiner.int8.onnx", 1_739_080, "7946164367946e7f9f29a122407c3252b680dbae9a51343eb2488d057c3c43d2"),
        Entry(STT_DIR, "parakeet", "tokens.txt", 9_384, "ec182b70dd42113aff6c5372c75cac58c952443eb22322f57bbd7f53977d497d"),
        Entry(TTS_DIR, "piper", "en_US-amy-low.onnx", 63_104_657, "8275b02c37c4ce6483b26a91704807e6de0c3f0bf8068e5d3b3598ab0da4253e"),
        Entry(TTS_DIR, "piper", "tokens.txt", 763, "42d1a69ed2b91a51928a711aa228ed9f3dc021c6d359a3e9c4f37eb1d20f80bd"),
        Entry(TTS_DIR, "piper", "lexicon-en-us.txt", 6_305_773, "8b67ae549a465eee78f92ecb74644ad90c3c58ce65f19c68baf85878a10ef73a"),
    )
}
