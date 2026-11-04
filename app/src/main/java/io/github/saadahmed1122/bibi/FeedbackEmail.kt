package io.github.saadahmed1122.bibi

import java.net.URLEncoder

/** Settings > Send feedback (SPEC-feedback.md): a prefilled email. Only the app version and phone go in. */
object FeedbackEmail {
    fun mailto(address: String, appVersion: String, model: String, android: String): String {
        val body = listOf(
            "App version: $appVersion",
            "Phone: $model, Android $android",
            "",
            "What did you try?",
            "",
            "What happened?",
            "",
            "What did you expect?",
            "",
        ).joinToString("\n")
        return "mailto:$address?subject=${encode("Bibi AI feedback $appVersion")}&body=${encode(body)}"
    }

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
