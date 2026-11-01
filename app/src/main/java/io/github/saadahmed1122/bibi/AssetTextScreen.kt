package io.github.saadahmed1122.bibi

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.saadahmed1122.bibi.core.designsystem.BibiScreen
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The bundled texts: Gemma terms notice, licenses (SPEC-privacy.md) and the privacy policy. */
enum class AssetText(val file: String, val title: Int) {
    // TODO(#34): confirm exact notice wording required by the Gemma terms [VERIFY]
    GEMMA_TERMS("gemma_notice.txt", R.string.terms_title),
    LICENSES("licenses.txt", R.string.licenses_title),

    /** Same text as docs/privacy-policy.md, which the owner hosts for Play (AssetTextTest checks). */
    PRIVACY_POLICY("privacy_policy.txt", R.string.policy_title),
}

/** Loads [text] off the main thread. */
@Composable
fun AssetTextRoute(text: AssetText, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val body by produceState<String?>(null, context, text) {
        value = withContext(Dispatchers.IO) { context.assets.open(text.file).bufferedReader().use { it.readText() } }
    }
    AssetTextScreen(title = stringResource(text.title), body = body, onBack = onBack, modifier = modifier)
}

/** [body] is null while loading. */
@Composable
fun AssetTextScreen(title: String, body: String?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BibiScreen(title = title, modifier = modifier, onBack = onBack) {
        SelectionContainer {
            Text(body ?: stringResource(R.string.loading), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Preview
@Composable
private fun AssetTextScreenPreview() {
    BibiTheme {
        AssetTextScreen(
            title = "Gemma Terms of Use",
            body = "Model: Gemma 3 1B\n\nGemma is provided under and subject to the Gemma Terms of Use " +
                "found at https://ai.google.dev/gemma/terms",
            onBack = {},
        )
    }
}

@Preview(fontScale = 2f, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AssetTextScreenLoadingPreview() {
    BibiTheme { AssetTextScreen(title = "Licenses", body = null, onBack = {}) }
}
