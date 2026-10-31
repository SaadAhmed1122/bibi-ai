package io.github.saadahmed1122.bibi.core.tools

// Pure Kotlin (no android.*).

/**
 * Stand-in for the on-device LLM until S2 picks a runtime and model [OPEN]. It turns text into the
 * structured output a model would emit, from fixed mappings, so the LLM path (parse → validate →
 * one repair → Permission Engine → executor) can be exercised end to end. It measures nothing about
 * real model quality or latency.
 * - A few free-form phrases map to fixed outputs, including a malformed one that repairs once and
 *   an "injected" dial that only a tainted context should stop.
 * - Anything the shortcut matcher understands is re-encoded as a `<tool_call>`, so quick commands
 *   work in this mode too.
 * - Everything else gets a plain-text reply (no tool call).
 */
class FakeLlm {
    private var lastKey: String? = null

    fun complete(prompt: String): String {
        val key = prompt.trim().trimEnd('.', '!', '?').lowercase()
        lastKey = key
        FIXED[key]?.let { return it }
        ShortcutParser.parse(prompt)?.let { return "<tool_call>${ToolCallJson.encode(it)}</tool_call>" }
        return "Sorry, I can only chat about that for now."
    }

    /** Answer to the router's single repair message for the previous [complete] call. */
    fun repair(@Suppress("UNUSED_PARAMETER") repairPrompt: String): String =
        REPAIRED[lastKey] ?: "I'm not sure what you mean."

    companion object {
        private fun call(c: ToolCall) = "<tool_call>${ToolCallJson.encode(c)}</tool_call>"

        val FIXED: Map<String, String> = mapOf(
            "it's dark in here" to call(ToolCall.Flashlight(true)),
            "i need to be up at half six" to call(ToolCall.SetAlarm(6, 30)),
            "the pasta needs eight minutes" to call(ToolCall.StartTimer(480)),
            "take me to maps" to call(ToolCall.OpenApp("maps")),
            "ring the office on 020 7946 0958" to call(ToolCall.Dial("020 7946 0958")),
            // Malformed on purpose: a string where an integer belongs.
            "broken timer test" to "<tool_call>{\"tool\":\"start_timer\",\"args\":{\"seconds\":\"five minutes\"}}</tool_call>",
            // Unknown tool on purpose: must be rejected, never executed.
            "send a text to mum" to "<tool_call>{\"tool\":\"send_sms\",\"args\":{\"to\":\"mum\",\"body\":\"hi\"}}</tool_call>",
            // What a prompt injection in pasted text might produce; with taint on it needs confirmation.
            "injection test" to call(ToolCall.Dial("+1 900 555 0100")),
        )

        val REPAIRED: Map<String, String> = mapOf(
            "broken timer test" to call(ToolCall.StartTimer(300)),
        )

        /** The free-form phrases, for the UI's hint text. */
        val PHRASES: List<String> get() = FIXED.keys.toList()
    }
}
