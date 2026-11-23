package jp.infinity.krate.katachecker.player

import android.content.Context

internal object YoutubeStore {
    private const val PREFS = "youtube_playback"
    private const val URL = "url"
    private const val VIDEO_ID = "video_id"

    fun url(context: Context): String {
        return prefs(context).getString(URL, "").orEmpty()
    }

    fun videoId(context: Context): String? {
        return prefs(context).getString(VIDEO_ID, null)
    }

    fun save(context: Context, url: String, videoId: String) {
        prefs(context).edit()
            .putString(URL, url)
            .putString(VIDEO_ID, videoId)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
