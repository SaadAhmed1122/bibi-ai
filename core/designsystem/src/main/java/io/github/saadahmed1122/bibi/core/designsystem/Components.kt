package io.github.saadahmed1122.bibi.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp

/**
 * A standard screen: optional Back, a big title, then [content] in a scrolling column with the
 * screen gutter. In landscape the content keeps a readable width, centred.
 */
@Composable
fun BibiScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(modifier = modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Space.readable)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.gutter)
                    .padding(bottom = Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                ScreenTitleRow(onBack, actions)
                Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                content()
            }
        }
    }
}

/** Back (when given) on the left, [actions] on the right, at touch size. */
@Composable
fun ScreenTitleRow(onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = Space.touch), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) BackButton(onBack)
        Box(Modifier.weight(1f))
        actions()
    }
}

@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onBack, modifier = modifier) {
        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.ds_back))
    }
}

/** A small, quiet heading above a group of rows. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = Space.l).semantics { heading() },
    )
}

/** A rounded group of rows on the brightest surface. */
@Composable
fun BibiCard(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainerLowest, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = color, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = Space.xs), content = content)
    }
}

/** One row in a [BibiCard]: title, optional [subtitle], optional [trailing]; clickable when [onClick] is given. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    role: Role? = Role.Button,
    trailing: (@Composable () -> Unit)? = if (onClick != null) ({ Chevron() }) else null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch + Space.s)
            .then(if (onClick != null) Modifier.clickable(role = role, onClick = onClick) else Modifier)
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/** A [ListRow] with a switch; the whole row toggles. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    ListRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        role = Role.Switch,
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun Chevron() {
    Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The main action on a screen: a filled pill. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: Int? = null) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = Space.touch), enabled = enabled, shape = CircleShape) {
        ButtonContent(text, icon)
    }
}

/** A second action: a tonal pill. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: Int? = null) {
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = Space.touch), enabled = enabled, shape = CircleShape) {
        ButtonContent(text, icon)
    }
}

@Composable
private fun RowScope.ButtonContent(text: String, icon: Int?) {
    if (icon != null) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(Space.l + Space.xs))
        Box(Modifier.size(Space.s))
    }
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

enum class BannerTone { Info, Warning, Error }

/** A message about the screen's state, with an optional action; read out when it appears. */
@Composable
fun Banner(text: String, modifier: Modifier = Modifier, tone: BannerTone = BannerTone.Info, action: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        BannerTone.Info -> colors.secondaryContainer to colors.onSecondaryContainer
        BannerTone.Warning -> colors.tertiaryContainer to colors.onTertiaryContainer
        BannerTone.Error -> colors.errorContainer to colors.onErrorContainer
    }
    Surface(color = bg, contentColor = fg, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            action?.invoke()
        }
    }
}

/** Bibi in [mood] at [size]; the mood's description is read by TalkBack unless [decorative]. */
@Composable
fun BibiAvatar(mood: BibiMood, modifier: Modifier = Modifier, size: Dp = Space.avatarSmall, decorative: Boolean = false) {
    Image(
        painter = painterResource(mood.frame),
        contentDescription = if (decorative) null else stringResource(mood.description),
        modifier = modifier.size(size),
    )
}

/** Nothing here yet: Bibi and one line saying what will appear. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier, mood: BibiMood = BibiMood.IDLE) {
    Column(modifier.fillMaxWidth().padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.m)) {
        BibiAvatar(mood, size = Space.avatarLarge, decorative = true)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(name = "Components light")
@Preview(name = "Components dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Components 200%", fontScale = 2f)
@Composable
private fun ComponentsPreview() {
    BibiTheme {
        BibiScreen(title = "Settings", onBack = {}) {
            SectionHeader("Bibi")
            BibiCard {
                SwitchRow("Floating Bibi", checked = true, onCheckedChange = {}, subtitle = "Over other apps")
                ListRow("Privacy", subtitle = "What Bibi keeps", onClick = {})
            }
            Banner("Bibi is getting ready…")
            PrimaryButton("Talk to Bibi", onClick = {})
            SecondaryButton("Type instead", onClick = {})
            EmptyState("Nothing saved yet.")
        }
    }
}
