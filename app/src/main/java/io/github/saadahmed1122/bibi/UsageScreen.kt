package io.github.saadahmed1122.bibi

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import io.github.saadahmed1122.bibi.core.common.usage.DeviceInfo
import io.github.saadahmed1122.bibi.core.common.usage.UsageData
import io.github.saadahmed1122.bibi.core.common.usage.UsageReport
import io.github.saadahmed1122.bibi.core.common.usage.UsageTracker
import io.github.saadahmed1122.bibi.core.designsystem.BibiCard
import io.github.saadahmed1122.bibi.core.designsystem.BibiScreen
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.core.designsystem.PrimaryButton
import io.github.saadahmed1122.bibi.core.designsystem.Space

/** Settings > Share usage numbers (SPEC-test-report.md): the whole report is shown before it is shared. */
@Composable
fun UsageRoute(usage: UsageTracker, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val report = remember {
        UsageReport.format(usage.snapshot(), DeviceInfo(BuildConfig.VERSION_NAME, Build.MODEL, Build.VERSION.RELEASE))
    }
    UsageScreen(report, onShare = { shareText(context, report) }, onBack = onBack, modifier = modifier)
}

@Composable
fun UsageScreen(report: String, onShare: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BibiScreen(title = stringResource(R.string.usage_title), modifier = modifier, onBack = onBack) {
        Text(
            stringResource(R.string.usage_explain),
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
private fun UsagePreview() = BibiTheme { UsageScreen(previewReport, {}, {}) }

@Preview(fontScale = 2f, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UsageDarkLargePreview() = BibiTheme { UsageScreen(previewReport, {}, {}) }
