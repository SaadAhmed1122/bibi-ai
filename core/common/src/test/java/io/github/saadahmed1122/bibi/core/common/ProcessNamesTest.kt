package io.github.saadahmed1122.bibi.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessNamesTest {
    @Test
    fun inferenceProcessIsDetected() {
        assertTrue(ProcessNames.isInference("io.github.saadahmed1122.bibi:inference"))
    }

    @Test
    fun mainProcessIsNotInference() {
        assertFalse(ProcessNames.isInference("io.github.saadahmed1122.bibi"))
    }
}
