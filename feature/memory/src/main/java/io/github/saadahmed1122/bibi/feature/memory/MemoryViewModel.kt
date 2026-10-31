package io.github.saadahmed1122.bibi.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.saadahmed1122.bibi.domain.model.MemoryItem
import io.github.saadahmed1122.bibi.domain.usecase.MemoryRepository
import io.github.saadahmed1122.bibi.domain.usecase.NeverStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [editing] is the item being edited and its draft; [blocked] is set when a draft is something Bibi never stores. */
data class MemoryUiState(
    val items: List<MemoryItem> = emptyList(),
    val query: String = "",
    val editing: Pair<Long, String>? = null,
    val blocked: NeverStore.Reason? = null,
)

sealed interface MemoryIntent {
    data class Query(val text: String) : MemoryIntent
    data class TogglePin(val item: MemoryItem) : MemoryIntent
    data class Delete(val id: Long) : MemoryIntent
    data class StartEdit(val item: MemoryItem) : MemoryIntent
    data class EditText(val text: String) : MemoryIntent
    data object SaveEdit : MemoryIntent
    data object CancelEdit : MemoryIntent
}

/** The Memory screen (SPEC-memory.md): list or search, pin, edit, delete. */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModel(
    private val repository: MemoryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
) : ViewModel(scope) {
    private val query = MutableStateFlow("")
    private val editing = MutableStateFlow<Pair<Long, String>?>(null)
    private val blocked = MutableStateFlow<NeverStore.Reason?>(null)

    /** Everything while the query is blank, else the keyword matches (refreshed when memory changes). */
    private val items = combine(query, repository.observeAll()) { q, all -> q to all }.flatMapLatest { (q, all) ->
        flow { emit(if (q.isBlank()) all else repository.search(q, SEARCH_LIMIT)) }
    }

    val state: StateFlow<MemoryUiState> = combine(items, query, editing, blocked) { list, q, edit, why ->
        MemoryUiState(list, q, edit, why)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MemoryUiState())

    fun onIntent(intent: MemoryIntent) {
        when (intent) {
            is MemoryIntent.Query -> query.value = intent.text
            is MemoryIntent.TogglePin -> viewModelScope.launch { repository.setPinned(intent.item.id, !intent.item.pinned, clock()) }
            is MemoryIntent.Delete -> viewModelScope.launch { repository.delete(intent.id) }
            is MemoryIntent.StartEdit -> {
                editing.value = intent.item.id to intent.item.text
                blocked.value = null
            }
            is MemoryIntent.EditText -> editing.value = editing.value?.copy(second = intent.text)
            MemoryIntent.SaveEdit -> save()
            MemoryIntent.CancelEdit -> {
                editing.value = null
                blocked.value = null
            }
        }
    }

    private fun save() {
        val (id, draft) = editing.value ?: return
        val text = draft.trim()
        if (text.isEmpty()) return
        // An edit can't sneak in what "remember" refuses.
        NeverStore.check(text)?.let {
            blocked.value = it
            return
        }
        editing.value = null
        blocked.value = null
        viewModelScope.launch { repository.updateText(id, text, clock()) }
    }

    private companion object {
        const val SEARCH_LIMIT = 50
    }
}
