package io.github.saadahmed1122.bibi

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import io.github.saadahmed1122.bibi.core.common.crash.CrashLogWriter
import io.github.saadahmed1122.bibi.core.common.usage.DeviceInfo
import io.github.saadahmed1122.bibi.core.common.usage.UsageData
import io.github.saadahmed1122.bibi.core.common.usage.UsageReport
import io.github.saadahmed1122.bibi.core.common.usage.UsageTracker
import io.github.saadahmed1122.bibi.core.designsystem.BibiCard
import io.github.saadahmed1122.bibi.core.designsystem.BibiScreen
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.core.designsystem.PrimaryButton
import io.github.saadahmed1122.bibi.core.designsystem.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Settings > Share usage numbers (SPEC-test-report.md): the whole report is shown before it is shared. */
@Composable
fun UsageRoute(usage: UsageTracker, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val report = remember {
        UsageReport.format(usage.snapshot(), DeviceInfo(BuildConfig.VERSION_NAME, Build.MODEL, Build.VERSION.RELEASE))
    }
    ShareTextScreen(R.string.usage_title, R.string.usage_explain, report, onShare = { shareText(context, report) }, onBack = onBack, modifier = modifier)
}

/** Settings > Share crash log (#132): the newest records, shown in full before they are shared. */
@Composable
fun CrashLogShareRoute(crashLog: CrashLogWriter, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val log by produceState<String?>(null) { value = withContext(Dispatchers.IO) { crashLog.read() } }
    val header = "Bibi AI crash log (${BuildConfig.VERSION_NAME}, ${Build.MODEL}, Android ${Build.VERSION.RELEASE})"
    val empty = stringResource(R.string.crash_share_empty)
    val text = log?.let { crashShareText(it, header, empty) }
    ShareTextScreen(
        R.string.crash_share_title, R.string.crash_share_explain, text ?: stringResource(R.string.loading),
        onShare = { text?.let { shareText(context, it) } }, onBack = onBack, modifier = modifier,
    )
}

/** [header] and the newest [maxChars] of the log (a share Intent can't carry much more), or [empty]. */
internal fun crashShareText(log: String, header: String, empty: String, maxChars: Int = 60_000): String =
    if (log.isBlank()) empty else header + "\n\n" + log.takeLast(maxChars)

/** A text shown in full, then shared through the share sheet (usage numbers, crash log). */
@Composable
fun ShareTextScreen(title: Int, explain: Int, report: String, onShare: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BibiScreen(title = stringResource(title), modifier = modifier, onBack = onBack) {
        Text(
            stringResource(explain),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BibiCard {
            SelectionContainer {
                Text(report, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(Space.l))
            }
        }
        PrimaryButton(stringResource(R.string.usage_share), onClick = onShare)
    }
}

/** The share sheet with plain text; nothing is sent until the user picks an app. */
private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.usage_share)))
}

private val previewReport = UsageReport.format(
    UsageData.EMPTY.copy(sessions = 12, crashedSessions = 0),
    DeviceInfo("0.3.0-p3", "V2310", "15"),
)

@Preview
@Composable
private fun UsagePreview() = BibiTheme { ShareTextScreen(R.string.usage_title, R.string.usage_explain, previewReport, {}, {}) }

@Preview(fontScale = 2f, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UsageDarkLargePreview() = BibiTheme { ShareTextScreen(R.string.usage_title, R.string.usage_explain, previewReport, {}, {}) }
