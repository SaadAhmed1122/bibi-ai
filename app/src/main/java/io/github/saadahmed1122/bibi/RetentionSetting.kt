package io.github.saadahmed1122.bibi

import io.github.saadahmed1122.bibi.domain.usecase.Retention
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The memory retention choice (FR-093), saved by the caller; [apply] runs it at once when it changes. */
class RetentionSetting(read: () -> String?, private val write: (String) -> Unit, private val apply: (Retention) -> Unit) {
    private val _current = MutableStateFlow(Retention.from(read()))
    val current: StateFlow<Retention> = _current.asStateFlow()

    fun choose(retention: Retention) {
        if (retention == _current.value) return
        _current.value = retention
        write(retention.name)
        apply(retention)
    }
}
