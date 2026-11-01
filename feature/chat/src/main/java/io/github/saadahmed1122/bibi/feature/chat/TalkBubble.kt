package io.github.saadahmed1122.bibi.feature.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.core.designsystem.Durations
import io.github.saadahmed1122.bibi.core.designsystem.LocalBibiAccents
import io.github.saadahmed1122.bibi.core.designsystem.Space
import io.github.saadahmed1122.bibi.domain.model.Message
import kotlinx.coroutines.delay
import io.github.saadahmed1122.bibi.core.designsystem.R as DsR

/**
 * Tap-to-talk over other apps (#118): starts the conversation when shown, stops it when removed.
 * After it ends, the last answer stays readable for [LINGER_MS], then [onEnd].
 */
@Composable
fun TalkRoute(viewModel: ChatViewModel, onOpenChat: () -> Unit, onEnd: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Only this conversation's messages. By time, not by count: the history may still be loading.
    val startedAt = remember { System.currentTimeMillis() }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.openChat.collect { onOpenChat() } }
    DisposableEffect(Unit) {
        viewModel.onIntent(ChatIntent.StartTalk)
        onDispose { viewModel.onIntent(ChatIntent.StopTalk) }
    }
    LaunchedEffect(state.talk) {
        if (state.talk != null) {
            started = true
        } else if (started) {
            delay(LINGER_MS)
            onEnd()
        }
    }
    val exchange = state.messages.filter { it.createdAt >= startedAt }
    TalkBubble(
        phase = state.talk,
        said = exchange.lastOrNull { it.role == Message.Role.USER }?.text,
        reply = state.streamingText?.ifEmpty { null } ?: exchange.lastOrNull()?.takeIf { it.role == Message.Role.BIBI }?.text,
        onOpenChat = onOpenChat,
        onStop = { viewModel.onIntent(ChatIntent.StopTalk) },
    )
}

/** The bubble: what you said, Bibi's answer, and what Bibi is doing now. Tapping it opens the chat. */
@Composable
fun TalkBubble(
    phase: TalkPhase?,
    said: String?,
    reply: String?,
    onOpenChat: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val maxReplyHeight = (LocalConfiguration.current.screenHeightDp * 0.35f).dp
    Column(modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(Space.s), horizontalAlignment = Alignment.End) {
        if (said != null) {
            Text(
                said,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp))
                    .background(colors.secondary)
                    .padding(horizontal = Space.m + Space.xs, vertical = Space.s),
            )
        }
        Surface(
            color = colors.surfaceContainerLowest,
            contentColor = colors.onSurface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 8.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.talk_open_chat), onClick = onOpenChat),
        ) {
            Column(Modifier.padding(start = Space.l, top = Space.m, bottom = Space.s, end = Space.xs)) {
                if (reply != null) {
                    Text(
                        reply,
                        style = MaterialTheme.typography.bodyLarge,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = Space.m).heightIn(max = maxReplyHeight).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PhaseLabel(phase, Modifier.weight(1f))
                    if (phase != null) {
                        IconButton(onClick = onStop) {
                            Icon(painterResource(DsR.drawable.ic_close), contentDescription = stringResource(R.string.talk_stop), tint = colors.onSurfaceVariant)
                        }
                    } else {
                        Box(Modifier.height(Space.touch))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseLabel(phase: TalkPhase?, modifier: Modifier = Modifier) {
    val label = stringResource(
        when (phase) {
            TalkPhase.LISTENING -> R.string.talk_listening
            TalkPhase.HEARING -> R.string.talk_hearing
            TalkPhase.THINKING -> R.string.talk_thinking
            TalkPhase.SPEAKING -> R.string.talk_speaking
            null -> R.string.talk_ended
        },
    )
    Row(modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        if (phase == TalkPhase.LISTENING || phase == TalkPhase.HEARING || phase == TalkPhase.SPEAKING) Wave(lively = phase != TalkPhase.LISTENING)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Four small bars; they bounce while someone is talking and breathe slowly while Bibi waits. */
@Composable
private fun Wave(lively: Boolean) {
    val transition = rememberInfiniteTransition(label = "wave")
    val period = if (lively) Durations.STANDARD * 2 else Durations.STANDARD * 5
    val color = LocalBibiAccents.current.listening
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(14.dp)) {
        repeat(4) { i ->
            val h by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(period, delayMillis = i * period / 6), RepeatMode.Reverse),
                label = "bar$i",
            )
            Box(Modifier.width(3.dp).height(14.dp * h).clip(CircleShape).background(color))
        }
    }
}

private const val LINGER_MS = 4_000L

@Preview(name = "Speaking")
@Composable
private fun TalkBubbleSpeakingPreview() = BibiTheme {
    TalkBubble(TalkPhase.SPEAKING, "Set a timer for ten minutes", "Done! Your 10-minute timer is running.", {}, {})
}

@Preview(name = "Listening, dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TalkBubbleListeningPreview() = BibiTheme { TalkBubble(TalkPhase.LISTENING, null, null, {}, {}) }

@Preview(name = "Ended, 200%", fontScale = 2f)
@Composable
private fun TalkBubbleEndedPreview() = BibiTheme { TalkBubble(null, "What time is it", "It's 9:41.", {}, {}) }
