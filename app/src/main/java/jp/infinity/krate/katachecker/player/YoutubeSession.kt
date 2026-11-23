package jp.infinity.krate.katachecker.player

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

object YoutubeIds {
    private val embedded = Regex(
        """(?:v=|vi=|youtu\.be/|/embed/|/shorts/|/live/)([A-Za-z0-9_-]{11})""",
    )
    private val bare = Regex("""^[A-Za-z0-9_-]{11}$""")

    fun parse(input: String): String? {
        val value = input.trim()
        if (bare.matches(value)) return value
        return embedded.find(value)?.groupValues?.getOrNull(1)
    }
}

class YoutubeSession {
    private val main = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var scrubbing = false
    private var resumeAfterScrub = false
    private var pendingId: String? = null
    private var pendingAutoplay = false
    private var playbackSpeed = 1f

    var ready by mutableStateOf(false)
        private set
    var playing by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var errorCode by mutableIntStateOf(0)
        private set

    fun bind(view: WebView) {
        webView = view
    }

    fun unbind() {
        webView = null
        ready = false
    }

    fun cue(videoId: String, autoplay: Boolean = false) {
        pendingId = videoId
        pendingAutoplay = autoplay
        positionMs = 0L
        durationMs = 0L
        playing = false
        errorCode = 0
        if (ready) {
            dispatchCue()
        }
    }

    fun showInvalidUrl() {
        errorCode = INVALID_URL
    }

    fun togglePlay() {
        val script = if (playing) "pause()" else "play()"
        evaluate(script)
    }

    fun play() {
        evaluate("play()")
    }

    fun pause() {
        evaluate("pause()")
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        evaluate("setRate($speed)")
    }

    fun beginScrub() {
        if (scrubbing) return
        scrubbing = true
        resumeAfterScrub = playing
        if (playing) pause()
    }

    fun endScrub() {
        if (!scrubbing) return
        scrubbing = false
        if (resumeAfterScrub) {
            resumeAfterScrub = false
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        this.positionMs = positionMs.coerceAtLeast(0L)
        evaluate("seekTo(${this.positionMs / 1000.0})")
    }

    fun onPageLoaded() {
        Log.i(TAG, "page loaded pending=$pendingId")
        main.post {
            if (!ready) {
                dispatchCue()
            }
        }
    }

    fun onReady() {
        Log.i(TAG, "player ready pending=$pendingId")
        main.post {
            ready = true
            dispatchCue()
        }
    }

    fun onState(state: Int) {
        main.post {
            playing = state == STATE_PLAYING
            if (state == STATE_ENDED && durationMs > 0L) {
                positionMs = durationMs
            }
        }
    }

    fun onTime(currentSeconds: Double, durationSeconds: Double) {
        main.post {
            positionMs = (currentSeconds * 1000.0).toLong().coerceAtLeast(0L)
            if (durationSeconds > 0.0) {
                durationMs = (durationSeconds * 1000.0).toLong()
            }
        }
    }

    fun onError(code: Int) {
        Log.e(TAG, "player error $code")
        main.post {
            playing = false
            errorCode = code
        }
    }

    private fun dispatchCue() {
        val videoId = pendingId ?: return
        evaluate("cue(${JSONObject.quote(videoId)}, $pendingAutoplay)")
        evaluate("setRate($playbackSpeed)")
    }

    private fun evaluate(script: String) {
        val view = webView ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            view.evaluateJavascript(script, null)
        } else {
            main.post { webView?.evaluateJavascript(script, null) }
        }
    }

    private companion object {
        const val TAG = "YoutubeWeb"
        const val INVALID_URL = -1
        const val STATE_ENDED = 0
        const val STATE_PLAYING = 1
    }
}

class YoutubeJsBridge(
    private val session: YoutubeSession,
) {
    @JavascriptInterface
    fun onReady() {
        session.onReady()
    }

    @JavascriptInterface
    fun onState(state: Int) {
        session.onState(state)
    }

    @JavascriptInterface
    fun onTime(currentSeconds: Double, durationSeconds: Double) {
        session.onTime(currentSeconds, durationSeconds)
    }

    @JavascriptInterface
    fun onError(code: Int) {
        session.onError(code)
    }
}
