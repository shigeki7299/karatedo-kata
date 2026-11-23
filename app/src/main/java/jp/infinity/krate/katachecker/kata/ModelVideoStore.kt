package jp.infinity.krate.katachecker.kata

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import jp.infinity.krate.katachecker.player.YoutubeIds
import org.json.JSONObject

class ModelVideoStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun url(kata: KataEntry): String {
        val saved = entry(kata)
        return if (saved.has("url")) saved.optString("url") else ModelVideoDefaults.url(kata)
    }

    fun setUrl(kata: KataEntry, url: String) {
        write(kata, entry(kata).put("url", url))
    }

    fun fileUri(kata: KataEntry): String? = entry(kata).optString("fileUri").ifBlank { null }

    fun fileName(kata: KataEntry): String? = entry(kata).optString("fileName").ifBlank { null }

    fun hasFile(kata: KataEntry): Boolean = fileUri(kata) != null

    fun savedFileReadable(kata: KataEntry): Boolean {
        val savedUri = fileUri(kata) ?: return false
        return readable(savedUri)
    }

    fun resolveFile(kata: KataEntry): Pair<String, String>? {
        val savedUri = fileUri(kata)
        val savedName = fileName(kata)
        if (savedUri != null && readable(savedUri)) {
            return savedUri to (savedName ?: kataLabel(kata))
        }
        val found = findOnDevice(kata, savedName) ?: return null
        setFile(kata, found.first, found.second)
        return found
    }

    fun setFile(kata: KataEntry, uri: String, name: String) {
        write(kata, entry(kata).put("fileUri", uri).put("fileName", name))
    }

    private fun entry(kata: KataEntry): JSONObject {
        val text = prefs.getString(key(kata), null) ?: return JSONObject()
        return try {
            JSONObject(text)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun write(kata: KataEntry, body: JSONObject) {
        prefs.edit().putString(key(kata), body.toString()).apply()
    }

    private fun key(kata: KataEntry): String {
        return "${kata.style.id}/${kata.category.id}/${kata.name}"
    }

    private fun readable(uri: String): Boolean {
        return try {
            appContext.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } == true
        } catch (_: Exception) {
            false
        }
    }

    private fun findOnDevice(kata: KataEntry, savedName: String?): Pair<String, String>? {
        val resolver = appContext.contentResolver
        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_MODIFIED,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection += MediaStore.Video.Media.IS_PENDING
        }
        val cursor = try {
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
            )
        } catch (_: Exception) {
            null
        } ?: return null
        cursor.use { rows ->
            val idIndex = rows.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameIndex = rows.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val pendingIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                rows.getColumnIndex(MediaStore.Video.Media.IS_PENDING)
            } else {
                -1
            }
            var bestRank = Int.MAX_VALUE
            var best: Pair<String, String>? = null
            while (rows.moveToNext()) {
                if (pendingIndex >= 0 && rows.getInt(pendingIndex) == 1) continue
                val name = rows.getString(nameIndex) ?: continue
                val rank = fileRank(name, kata, savedName)
                if (rank < 0 || rank >= bestRank) continue
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    rows.getLong(idIndex),
                ).toString()
                if (!readable(uri)) continue
                bestRank = rank
                best = uri to name
            }
            return best
        }
    }

    private fun fileRank(displayName: String, kata: KataEntry, savedName: String?): Int {
        val base = displayName.substringBeforeLast('.')
        val savedBase = savedName?.substringBeforeLast('.')
        if (!savedBase.isNullOrBlank() && (displayName == savedName || base == savedBase)) return 0
        if (base.startsWith(kataLabel(kata))) return 1
        if (base == kata.name) return 2
        val videoId = YoutubeIds.parse(url(kata))
        if (videoId != null && base.startsWith("空手形_youtube_${videoId}_")) return 3
        return -1
    }

    private fun kataLabel(kata: KataEntry): String = "（${kata.style.label}）${kata.name}"

    private companion object {
        const val PREFS = "model_videos"
    }
}

object ModelVideoDefaults {
    fun url(kata: KataEntry): String {
        return when (kata.style to kata.category to kata.name) {
            KataStyle.GOJU to KataCategory.KIHON to "ゲキサイ第一" ->
                "https://youtu.be/evjNRopKKyw?si=o-X3JTlpbwmjMTtN"
            KataStyle.GOJU to KataCategory.KIHON to "ゲキサイ第二" ->
                "https://youtu.be/k4MpMBFhc4M?si=PL8oL3VEMFiPodpp"
            KataStyle.SHITO to KataCategory.KIHON to "平安初段" ->
                "https://youtu.be/KHzPVdKSfnE?si=o5eHviMUvrespGRI"
            KataStyle.SHITO to KataCategory.KIHON to "平安二段" ->
                "https://youtu.be/4Zca6feGmTY?si=cTgK-c2UbdTQbGcM"
            KataStyle.SHITO to KataCategory.KIHON to "平安三段" ->
                "https://youtu.be/p5TDFjqYqWM?si=9aRVFqhycAO8nGfp"
            KataStyle.SHITO to KataCategory.KIHON to "平安四段" ->
                "https://youtu.be/iFviK73bxGM?si=WmxuPi7LcOCqBoPL"
            KataStyle.SHITO to KataCategory.KIHON to "平安五段" ->
                "https://youtu.be/atnYRDJmZOg?si=krC5WRcktnnCR2fl"
            KataStyle.GOJU to KataCategory.SHITEI to "セーパイ" ->
                "https://youtu.be/OoSxrN5FLkI?si=90Yg-ZzYLHdbYhsj"
            KataStyle.GOJU to KataCategory.SHITEI to "サイファ" ->
                "https://youtu.be/z5MaVMIr4AQ?si=Se5HfTsaRLJ8DD1a"
            KataStyle.GOJU to KataCategory.SHITEI to "セーサン" ->
                "https://youtu.be/lYE3FJ0ukMs?si=LcYj0bL_ELQSCg_a"
            KataStyle.GOJU to KataCategory.SHITEI to "クルルンファ" ->
                "https://youtu.be/oULdn4nv5Fc?si=eQ7nrc6QZyKpThtR"
            KataStyle.SHITO to KataCategory.SHITEI to "バッサイダイ" ->
                "https://youtu.be/bwG4pF82-uE?si=BwJDpTZJhmoZonz5"
            KataStyle.SHITO to KataCategory.SHITEI to "セイエンチン" ->
                "https://youtu.be/XAntKeiLiJ8?si=qitgq5UAbl1eNojb"
            KataStyle.SHITO to KataCategory.SHITEI to "マツムラローハイ" ->
                "https://youtu.be/X2Z-3qsozVs?si=7VD9L2fCo8unhiK4"
            KataStyle.SHITO to KataCategory.SHITEI to "ニーパイポ" ->
                "https://youtu.be/HtIagF-nsK0?si=CRd6dKynD6rxyX5q"
            KataStyle.GOJU to KataCategory.TOKUI to "サンチン" ->
                "https://youtu.be/XueYrcrE-Mw?si=K-lgezzB7DiPAUo7"
            KataStyle.GOJU to KataCategory.TOKUI to "シソーチン" ->
                "https://youtu.be/GzCr4AcrhE4?si=MIAx3Cf21xSUPyb5"
            KataStyle.SHITO to KataCategory.TOKUI to "五十四歩" ->
                "https://youtu.be/3PVFgn3ESVo?si=I3kvj4qPMyYdoB1t"
            KataStyle.SHITO to KataCategory.TOKUI to "アーナン" ->
                "https://youtu.be/-tEdT8GvYUU?si=3eKENHTXo-mgcSpO"
            KataStyle.SHITO to KataCategory.TOKUI to "パーチュー" ->
                "https://youtu.be/EPqeiJ2najs?si=BZhRGotc_HXmpiFX"
            KataStyle.SHITO to KataCategory.TOKUI to "パイクー" ->
                "https://youtu.be/lrHgKljKnbE?si=PNjJpWq6u_6lPiZh"
            KataStyle.SHITO to KataCategory.TOKUI to "ヘイクー" ->
                "https://youtu.be/-YPtKAzzIu0?si=bevCrPQxdUt7_FNm"
            KataStyle.SHITO to KataCategory.TOKUI to "チャタンヤラ・クーサンクー" ->
                "https://youtu.be/Z5752E8fmw8?si=VQmp1eNxqxuvL4At"
            else -> ""
        }
    }
}
