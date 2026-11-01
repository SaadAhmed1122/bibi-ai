package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.ai.inference.api.InferenceClient
import io.github.saadahmed1122.bibi.ai.inference.api.RouteResult
import io.github.saadahmed1122.bibi.core.tools.ActionRunner
import io.github.saadahmed1122.bibi.core.tools.AppResolver
import io.github.saadahmed1122.bibi.core.tools.LlmRoute
import io.github.saadahmed1122.bibi.core.tools.ShortcutParser
import io.github.saadahmed1122.bibi.core.tools.SlotToolRouter
import io.github.saadahmed1122.bibi.core.tools.ToolCall
import io.github.saadahmed1122.bibi.core.tools.ToolName
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
            // Ask for a missing value only when the user named the action ("set an alarm"); otherwise
            // the router guessed from a chat message ("how do I get better sleep") and it is chat.
            is LlmRoute.AskUser -> if (namesTool(selection.tool, text)) TurnPlan.Ask(route.question) else TurnPlan.Chat
            is LlmRoute.NoToolCall, is LlmRoute.Rejected -> TurnPlan.Chat
        }
    }
}

/** Whether [text] contains a word for [tool] itself, as opposed to a topic near it. */
internal fun namesTool(tool: ToolName, text: String): Boolean = TOOL_WORDS.getValue(tool).containsMatchIn(text)

private val TOOL_WORDS = mapOf(
    ToolName.OPEN_APP to Regex("\\b(open|launch)\\b", RegexOption.IGNORE_CASE),
    ToolName.SET_ALARM to Regex("\\b(alarms?|wake me)\\b", RegexOption.IGNORE_CASE),
    ToolName.START_TIMER to Regex("\\b(timers?|countdown|count down)\\b", RegexOption.IGNORE_CASE),
    ToolName.FLASHLIGHT to Regex("\\b(flash\\s?light|torch|lights?)\\b", RegexOption.IGNORE_CASE),
    ToolName.DIAL to Regex("\\b(call|dial|phone|ring)\\b", RegexOption.IGNORE_CASE),
)

/** Bibi's words for action outcomes; the app supplies them from string resources. */
interface ActionTexts {
    fun done(call: ToolCall, target: String): String

    fun failed(reason: String): String

    fun denied(reason: String): String

    fun cancelled(): String

    fun timedOut(): String

    /** Bibi's message above the confirmation card, which itself shows the exact target. */
    fun confirm(): String

    /** Bibi's reply to a clear action request while Safe mode is on. */
    fun safeMode(): String = "Safe mode is on, so I can't do phone actions. You can turn it off in Settings."
}

/** Everything the chat needs for phone actions. Null in [ChatViewModel] means chat only. */
class ChatActions(
    val plan: suspend (String) -> TurnPlan,
    val runner: ActionRunner,
    val texts: ActionTexts,
    /** False in Safe mode (FR-076): no shortcut, no router, no action; messages are chat. */
    val enabled: () -> Boolean = { true },
)
