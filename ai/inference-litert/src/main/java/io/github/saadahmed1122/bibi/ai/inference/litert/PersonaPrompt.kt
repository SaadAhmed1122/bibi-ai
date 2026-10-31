package io.github.saadahmed1122.bibi.ai.inference.litert

import io.github.saadahmed1122.bibi.ai.inference.api.Persona

/** Which system prompt asset each personality preset uses (SPEC-personality.md). */
internal object PersonaPrompt {
    const val FRIENDLY_ASSET = "system_bibi.txt"
    const val CONCISE_ASSET = "system_bibi_concise.txt"

    /** Unknown ids are Friendly, so an old or bad saved value can't break chat. */
    fun normalize(persona: String): String = if (persona == Persona.CONCISE) Persona.CONCISE else Persona.FRIENDLY

    fun asset(persona: String): String = if (normalize(persona) == Persona.CONCISE) CONCISE_ASSET else FRIENDLY_ASSET

    /** A change needs a reopen only when it is a real change and a model is loading or loaded. */
    fun needsReopen(current: String, requested: String, modelLoaded: Boolean): Boolean =
        modelLoaded && normalize(current) != normalize(requested)
}
