package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.ai.inference.api.InferenceClient
import io.github.saadahmed1122.bibi.ai.inference.api.RouteResult
import io.github.saadahmed1122.bibi.core.tools.ActionRunner
import io.github.saadahmed1122.bibi.core.tools.AppResolver
import io.github.saadahmed1122.bibi.core.tools.LlmRoute
import io.github.saadahmed1122.bibi.core.tools.ShortcutParser
import io.github.saadahmed1122.bibi.core.tools.SlotToolRouter
import io.github.saadahmed1122.bibi.core.tools.ToolCall
import io.github.saadahmed1122.bibi.core.tools.ToolRouting
import io.github.saadahmed1122.bibi.core.tools.ToolSelection

/** What a typed message turns into (SPEC-tools.md). */
sealed interface TurnPlan {
    /** Normal chat with Gemma. */
    data object Chat : TurnPlan

    /** A tool was meant but a value is missing or unclear: Bibi asks instead of guessing. */
    data class Ask(val question: String) : TurnPlan

    data class Act(val call: ToolCall) : TurnPlan
}

/**
 * Shortcut phrases first (no model, S4 gate path), then the router in `:inference`, then slot
 * extraction for the one tool it chose. The router never runs anything; a "none", a low score or a
 * router that isn't loaded means chat.
 */
class PlanTurn(private val client: InferenceClient, private val apps: () -> AppResolver?) {
    suspend operator fun invoke(text: String): TurnPlan {
        ShortcutParser.parse(text)?.let { return TurnPlan.Act(it) }
        val routed = client.route(text) as? RouteResult.Label ?: return TurnPlan.Chat
        val selection = ToolRouting.select(routed.label, routed.confidence) as? ToolSelection.Expose ?: return TurnPlan.Chat
        return when (val route = SlotToolRouter.route(selection, text, apps()).route) {
            is LlmRoute.Call -> TurnPlan.Act(route.call)
            is LlmRoute.AskUser -> TurnPlan.Ask(route.question)
            is LlmRoute.NoToolCall, is LlmRoute.Rejected -> TurnPlan.Chat
        }
    }
}

/** Bibi's words for action outcomes; the app supplies them from string resources. */
interface ActionTexts {
    fun done(call: ToolCall, target: String): String

    fun failed(reason: String): String

    fun denied(reason: String): String

    fun cancelled(): String

    fun timedOut(): String

    /** Bibi's message above the confirmation card, which itself shows the exact target. */
    fun confirm(): String
}

/** Everything the chat needs for phone actions. Null in [ChatViewModel] means chat only. */
class ChatActions(
    val plan: suspend (String) -> TurnPlan,
    val runner: ActionRunner,
    val texts: ActionTexts,
)
