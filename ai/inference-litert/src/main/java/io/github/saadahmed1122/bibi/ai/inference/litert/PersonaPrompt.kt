package io.github.saadahmed1122.bibi.ai.inference.litert

import io.github.saadahmed1122.bibi.ai.inference.api.Persona

/** Which system prompt asset each personality preset uses (SPEC-personality.md). */
internal object PersonaPrompt {
    const val FRIENDLY_ASSET = "system_bibi.txt"
    const val CONCISE_ASSET = "system_bibi_concise.txt"

    /** Unknown ids are Friendly, so an old or bad saved value can't break chat. */
    fun normalize(persona: String): String = if (persona == Persona.CONCISE) Persona.CONCISE else Persona.FRIENDLY

    fun asset(persona: String): String = if (normalize(persona) == Persona.CONCISE) CONCISE_ASSET else FRIENDLY_ASSET

    /** Longest pinned-notes block the service accepts; the app sends about 520 characters at most. */
    const val MAX_NOTES_CHARS = 600

    /** The system prompt: the preset's text, then the pinned memory notes if any. */
    fun compose(base: String, notes: String): String {
        val n = notes.trim().take(MAX_NOTES_CHARS)
        return if (n.isEmpty()) base else "$base\n\n$n"
    }

    /** A change needs a reopen only when it is a real change and a model is loading or loaded. */
    fun needsReopen(current: String, requested: String, modelLoaded: Boolean): Boolean =
        modelLoaded && normalize(current) != normalize(requested)
}
