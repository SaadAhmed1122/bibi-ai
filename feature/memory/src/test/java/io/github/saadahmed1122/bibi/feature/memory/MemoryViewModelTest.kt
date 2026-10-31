package io.github.saadahmed1122.bibi.feature.memory

import io.github.saadahmed1122.bibi.domain.model.MemoryItem.Kind.FACT
import io.github.saadahmed1122.bibi.domain.usecase.NeverStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryViewModelTest {
    private val memory = FakeMemory()

    private fun TestScope.viewModel() =
        MemoryViewModel(memory, clock = { 99 }, scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()))

    @Test
    fun listsEverythingAndSearchesByKeyword() = runTest {
        memory.add(FACT, "My sister is Sara", 1)
        memory.add(FACT, "The gym opens at 6", 2)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)
        vm.onIntent(MemoryIntent.Query("gym"))
        advanceUntilIdle()
        assertEquals(listOf("The gym opens at 6"), vm.state.value.items.map { it.text })
        vm.onIntent(MemoryIntent.Query(""))
        advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)
    }

    @Test
    fun pinEditAndDelete() = runTest {
        val id = memory.add(FACT, "My cat is Milo", 1)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(MemoryIntent.TogglePin(vm.state.value.items.single()))
        advanceUntilIdle()
        assertTrue(memory.items.single().pinned)

        vm.onIntent(MemoryIntent.StartEdit(memory.items.single()))
        vm.onIntent(MemoryIntent.EditText("My cat is Luna"))
        vm.onIntent(MemoryIntent.SaveEdit)
        advanceUntilIdle()
        assertEquals("My cat is Luna", memory.items.single().text)
        assertEquals(99L, memory.items.single().updatedAt)
        assertNull(vm.state.value.editing)

        vm.onIntent(MemoryIntent.Delete(id))
        advanceUntilIdle()
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test
    fun anEditCannotStoreASecret() = runTest {
        memory.add(FACT, "My bank is Meezan", 1)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(MemoryIntent.StartEdit(memory.items.single()))
        vm.onIntent(MemoryIntent.EditText("My bank PIN is 4821"))
        vm.onIntent(MemoryIntent.SaveEdit)
        advanceUntilIdle()
        assertEquals(NeverStore.Reason.PASSWORD, vm.state.value.blocked)
        assertEquals("My bank is Meezan", memory.items.single().text)
    }
}
