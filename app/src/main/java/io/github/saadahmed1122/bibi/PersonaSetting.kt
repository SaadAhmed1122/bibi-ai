package io.github.saadahmed1122.bibi

import io.github.saadahmed1122.bibi.ai.inference.api.Persona
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bibi's personality preset (SPEC-personality.md). [read] and [write] are the saved value; [apply]
 * sends a change to a running `:inference` (the client also sends [current] at every bind).
 */
class PersonaSetting(
    read: () -> String?,
    private val write: (String) -> Unit,
    private val apply: (String) -> Unit,
) {
    private val _current = MutableStateFlow(if (read() == Persona.CONCISE) Persona.CONCISE else Persona.FRIENDLY)
    val current: StateFlow<String> = _current.asStateFlow()

    fun choose(persona: String) {
        if (persona == _current.value || (persona != Persona.FRIENDLY && persona != Persona.CONCISE)) return
        _current.value = persona
        write(persona)
        apply(persona)
    }
}
