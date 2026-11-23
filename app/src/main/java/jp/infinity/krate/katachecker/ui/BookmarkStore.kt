package jp.infinity.krate.katachecker.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs

class VideoBookmarks(initial: List<Long>) {
    var marks by mutableStateOf(initial)
        private set

    var toggle: (Long) -> Unit = {}

    fun replace(next: List<Long>) {
        marks = next
    }
}

class BookmarkStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(keys: List<String>): List<Long> {
        for (key in keys) {
            val marks = read(key)
            if (marks.isNotEmpty()) return marks
        }
        return emptyList()
    }

    fun toggle(keys: List<String>, current: List<Long>, positionMs: Long): List<Long> {
        if (keys.isEmpty()) return current
        val position = positionMs.coerceAtLeast(0L)
        val hit = current.indexOfFirst { abs(it - position) <= SAME_MARK_MS }
        val next = if (hit >= 0) {
            current.filterIndexed { index, _ -> index != hit }
        } else {
            (current + position).distinct().sorted()
        }
        val editor = prefs.edit()
        val encoded = next.joinToString(",")
        for (key in keys) {
            if (next.isEmpty()) editor.remove(key) else editor.putString(key, encoded)
        }
        editor.apply()
        return next
    }

    private fun read(key: String): List<Long> {
        val text = prefs.getString(key, null) ?: return emptyList()
        return text.split(',').mapNotNull { it.toLongOrNull() }.distinct().sorted()
    }

    companion object {
        private const val PREFS = "video_bookmarks"
        private const val SAME_MARK_MS = 500L
        private val mediaId = Regex("""(?:video%3A|video:|/media/)(\d+)""")
        private val youtubeInName = Regex("""空手形_youtube_([A-Za-z0-9_-]{11})_""")

        fun keys(
            youtubeId: String?,
            uri: String?,
            displayName: String?,
            kataId: String?,
        ): List<String> {
            val keys = LinkedHashSet<String>()
            val fromName = displayName?.let { youtubeInName.find(it)?.groupValues?.getOrNull(1) }
            val youtube = youtubeId?.takeIf { it.isNotBlank() } ?: fromName
            if (youtube != null) keys += "yt:$youtube"
            if (!uri.isNullOrBlank()) {
                val decoded = Uri.decode(uri)
                mediaId.find(decoded)?.groupValues?.getOrNull(1)?.let { keys += "media:$it" }
                keys += "uri:$uri"
            }
            if (!displayName.isNullOrBlank()) keys += "name:$displayName"
            if (keys.isEmpty() && !kataId.isNullOrBlank()) keys += "kata:$kataId"
            return keys.toList()
        }
    }
}

@Composable
fun rememberVideoBookmarks(
    youtubeId: String?,
    uri: String?,
    displayName: String?,
    kataId: String?,
): VideoBookmarks {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { BookmarkStore(context) }
    val keys = BookmarkStore.keys(youtubeId, uri, displayName, kataId)
    val identity = keys.joinToString("|")
    val state = remember(identity) { VideoBookmarks(store.load(keys)) }
    state.toggle = { position -> state.replace(store.toggle(keys, state.marks, position)) }
    return state
}
