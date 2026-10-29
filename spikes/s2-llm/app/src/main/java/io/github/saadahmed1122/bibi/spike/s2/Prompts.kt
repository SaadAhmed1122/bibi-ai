package io.github.saadahmed1122.bibi.spike.s2

/** One benchmark prompt from assets/prompts.tsv. */
data class Prompt(val id: String, val text: String)

/** The system instruction plus the ordered prompts. Pure Kotlin, JVM-testable. */
data class PromptSet(val system: String, val prompts: List<Prompt>) {

    companion object {
        const val SYSTEM_ID = "system"

        /**
         * Parses `<id><TAB><text>` lines; blank lines and lines starting with `#` are skipped.
         * Throws if there is no `system` line, a line has no tab, or an id repeats.
         */
        fun parse(tsv: String): PromptSet {
            var system: String? = null
            val prompts = mutableListOf<Prompt>()
            tsv.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                val tab = line.indexOf('\t')
                require(tab > 0) { "no tab in line: $line" }
                val id = line.substring(0, tab).trim()
                val text = line.substring(tab + 1).trim()
                require(text.isNotEmpty()) { "empty text for $id" }
                if (id == SYSTEM_ID) {
                    require(system == null) { "two system lines" }
                    system = text
                } else {
                    require(prompts.none { it.id == id }) { "duplicate id $id" }
                    prompts += Prompt(id, text)
                }
            }
            return PromptSet(requireNotNull(system) { "no system line" }, prompts)
        }
    }
}
