package io.github.saadahmed1122.bibi.ai.inference.litert

import io.github.saadahmed1122.bibi.ai.inference.api.Persona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaPromptTest {
    @Test
    fun eachPresetHasItsOwnPrompt() {
        assertEquals("system_bibi.txt", PersonaPrompt.asset(Persona.FRIENDLY))
        assertEquals("system_bibi_concise.txt", PersonaPrompt.asset(Persona.CONCISE))
    }

    @Test
    fun anUnknownPresetIsFriendly() {
        assertEquals(Persona.FRIENDLY, PersonaPrompt.normalize("pirate"))
        assertEquals("system_bibi.txt", PersonaPrompt.asset(""))
    }

    @Test
    fun theSamePresetNeverReopens() {
        assertFalse(PersonaPrompt.needsReopen(Persona.CONCISE, Persona.CONCISE, modelLoaded = true))
        assertFalse(PersonaPrompt.needsReopen(Persona.FRIENDLY, "unknown", modelLoaded = true))
    }

    @Test
    fun aNewPresetReopensOnlyWithAModel() {
        assertTrue(PersonaPrompt.needsReopen(Persona.FRIENDLY, Persona.CONCISE, modelLoaded = true))
        assertFalse(PersonaPrompt.needsReopen(Persona.FRIENDLY, Persona.CONCISE, modelLoaded = false))
    }

    @Test
    fun notesFollowThePromptAndAreCapped() {
        assertEquals("Base.", PersonaPrompt.compose("Base.", "  "))
        assertEquals("Base.\n\nNotes.", PersonaPrompt.compose("Base.", "Notes."))
        assertEquals("Base.".length + 2 + PersonaPrompt.MAX_NOTES_CHARS, PersonaPrompt.compose("Base.", "x".repeat(5_000)).length)
    }
}
