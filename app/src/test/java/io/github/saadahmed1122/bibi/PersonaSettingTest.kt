package io.github.saadahmed1122.bibi

import io.github.saadahmed1122.bibi.ai.inference.api.Persona
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonaSettingTest {
    private var saved: String? = null
    private val applied = mutableListOf<String>()
    private fun setting() = PersonaSetting(read = { saved }, write = { saved = it }, apply = { applied += it })

    @Test
    fun friendlyByDefaultAndForUnknownValues() {
        assertEquals(Persona.FRIENDLY, setting().current.value)
        saved = "pirate"
        assertEquals(Persona.FRIENDLY, setting().current.value)
    }

    @Test
    fun aChoiceIsSavedAppliedAndKeptAfterARestart() {
        setting().choose(Persona.CONCISE)
        assertEquals(Persona.CONCISE, saved)
        assertEquals(listOf(Persona.CONCISE), applied)
        assertEquals(Persona.CONCISE, setting().current.value)
    }

    @Test
    fun theSameChoiceOrAnUnknownOneDoesNothing() {
        val s = setting()
        s.choose(Persona.FRIENDLY)
        s.choose("pirate")
        assertEquals(null, saved)
        assertEquals(emptyList<String>(), applied)
    }
}
