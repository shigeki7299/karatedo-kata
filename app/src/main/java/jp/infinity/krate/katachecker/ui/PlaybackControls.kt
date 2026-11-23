package jp.infinity.krate.katachecker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.infinity.krate.katachecker.R

@Composable
fun PlaybackControls(
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    canPlay: Boolean,
    poseEnabled: Boolean,
    playbackSpeed: Float,
    linked: Boolean,
    onToggle: () -> Unit,
    onCycleSpeed: () -> Unit,
    onTogglePose: () -> Unit,
    onToggleLink: () -> Unit,
    onSeek: (Long) -> Unit,
    onPreviewSeek: (Long) -> Unit = {},
    bookmarksMs: List<Long> = emptyList(),
    onToggleBookmark: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    val rangeEnd = durationMs.toFloat().coerceAtLeast(1f)
    val shown = if (dragging) dragPosition.toLong() else positionMs
    val sliderValue = shown.toFloat().coerceIn(0f, rangeEnd)
    val marks = bookmarksMs.sorted()
    val previousBookmark = marks.lastOrNull { it < shown - 250L }
    val nextBookmark = marks.firstOrNull { it > shown + 250L }
    val previousLabel = stringResource(R.string.bookmark_previous)
    val nextLabel = stringResource(R.string.bookmark_next)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggle, enabled = canPlay) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                )
            }
            Text(
                text = "${formatPlaybackTime(shown)} / ${formatPlaybackTime(durationMs)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onCycleSpeed,
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text(playbackSpeedLabel(playbackSpeed))
            }
            TextButton(
                onClick = onTogglePose,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(stringResource(if (poseEnabled) R.string.pose_on else R.string.pose_off))
            }
            TextButton(
                onClick = onToggleLink,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    text = stringResource(if (linked) R.string.slider_linked else R.string.slider_link),
                    color = if (linked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            IconButton(
                onClick = { onToggleBookmark(shown) },
                enabled = canPlay && durationMs > 0L,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bookmark_ribbon),
                    contentDescription = stringResource(R.string.bookmark),
                )
            }
            IconButton(
                onClick = { previousBookmark?.let(onSeek) },
                enabled = canPlay && previousBookmark != null,
                modifier = Modifier.semantics { contentDescription = previousLabel },
            ) {
                Text(
                    text = "◀◀",
                    fontSize = 14.sp,
                    color = if (canPlay && previousBookmark != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
            IconButton(
                onClick = { nextBookmark?.let(onSeek) },
                enabled = canPlay && nextBookmark != null,
                modifier = Modifier.semantics { contentDescription = nextLabel },
            ) {
                Text(
                    text = "▶▶",
                    fontSize = 14.sp,
                    color = if (canPlay && nextBookmark != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
            val thumbWidth = 4.dp
            val travel = maxWidth - thumbWidth
            bookmarksMs.forEach { mark ->
                val fraction = (mark.toFloat() / rangeEnd).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = thumbWidth / 2 + travel * fraction - 12.dp)
                        .size(24.dp)
                        .clickable(enabled = canPlay) { onSeek(mark) },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(
                        text = "▼",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Slider(
            value = sliderValue,
            onValueChange = { value ->
                dragging = true
                dragPosition = value
                onPreviewSeek(value.toLong())
            },
            onValueChangeFinished = {
                onSeek(dragPosition.toLong())
                dragging = false
            },
            valueRange = 0f..rangeEnd,
            enabled = canPlay && durationMs > 0L,
        )
    }
}

private val playbackSpeeds = floatArrayOf(1f, 0.5f, 2f)

internal fun nextPlaybackSpeed(current: Float): Float {
    val index = playbackSpeeds.indexOfFirst { it == current }
    return playbackSpeeds[(if (index < 0) 0 else index + 1) % playbackSpeeds.size]
}

internal fun playbackSpeedLabel(speed: Float): String = when (speed) {
    0.5f -> "×0.5"
    2f -> "×2.0"
    else -> "×1.0"
}

internal fun formatPlaybackTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0L) / 1000L
    val hours = safe / 3600L
    val minutes = (safe % 3600L) / 60L
    val seconds = safe % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
