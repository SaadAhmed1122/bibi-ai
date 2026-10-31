package io.github.saadahmed1122.bibi

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GEMMA_NOTICE_ASSET = "gemma_notice.txt"

// TODO(#34): confirm exact notice wording required by the Gemma terms [VERIFY]
private fun readGemmaNotice(context: Context): String =
    context.assets.open(GEMMA_NOTICE_ASSET).bufferedReader().use { it.readText() }

/** Loads the bundled Gemma notice off the main thread. */
@Composable
fun TermsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val notice by produceState<String?>(null, context) {
        value = withContext(Dispatchers.IO) { readGemmaNotice(context) }
    }
    TermsScreen(notice = notice, onBack = onBack, modifier = modifier)
}

/** [notice] is null while loading. */
@Composable
fun TermsScreen(notice: String?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.terms_title), style = MaterialTheme.typography.headlineSmall)
            SelectionContainer {
                Text(notice ?: stringResource(R.string.loading), style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
    }
}

@Preview
@Composable
private fun TermsScreenPreview() {
    BibiTheme {
        TermsScreen(
            notice = "Model: Gemma 3 1B\n\nGemma is provided under and subject to the Gemma Terms of Use " +
                "found at https://ai.google.dev/gemma/terms",
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun TermsScreenLoadingPreview() {
    BibiTheme { TermsScreen(notice = null, onBack = {}) }
}
