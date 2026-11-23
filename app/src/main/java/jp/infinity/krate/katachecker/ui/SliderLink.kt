package jp.infinity.krate.katachecker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SliderLink {
    var enabled by mutableStateOf(false)
        private set

    var playYoutube: () -> Unit = {}
    var pauseYoutube: () -> Unit = {}
    var playLocal: () -> Unit = {}
    var pauseLocal: () -> Unit = {}

    fun toggle() {
        enabled = !enabled
    }

    fun align() {
        enabled = true
    }

    fun withYoutube(play: Boolean) {
        if (play) playYoutube() else pauseYoutube()
        if (!enabled) return
        if (play) playLocal() else pauseLocal()
    }

    fun withLocal(play: Boolean) {
        if (play) playLocal() else pauseLocal()
        if (!enabled) return
        if (play) playYoutube() else pauseYoutube()
    }
}
