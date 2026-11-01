package io.github.saadahmed1122.bibi.feature.chat

import android.content.Context
import io.github.saadahmed1122.bibi.domain.usecase.NeverStore

/** [MemoryTexts] from this module's string resources. */
class ResourceMemoryTexts(private val context: Context) : MemoryTexts {
    override fun saved() = context.getString(R.string.memory_saved)

    override fun blocked(reason: NeverStore.Reason) = context.getString(
        when (reason) {
            NeverStore.Reason.CARD -> R.string.memory_blocked_card
            NeverStore.Reason.PASSWORD -> R.string.memory_blocked_password
            NeverStore.Reason.ID -> R.string.memory_blocked_id
        },
    )

    override fun noticeSaved() = context.getString(R.string.memory_notice_saved)

    override fun safeMode() = context.getString(R.string.memory_safe_mode)
}
