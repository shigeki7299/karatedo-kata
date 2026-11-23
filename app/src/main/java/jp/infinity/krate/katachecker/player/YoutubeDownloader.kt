package jp.infinity.krate.katachecker.player

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

enum class YoutubeDownloadFailure {
    Unavailable,
    Failed,
}

class YoutubeDownloadException(val failure: YoutubeDownloadFailure) : Exception()

class YoutubeDownloader(private val context: Context) {
    suspend fun download(
        videoId: String,
        displayName: String? = null,
        onPercent: (Int) -> Unit,
    ): Uri {
        if (!VIDEO_ID.matches(videoId)) {
            throw YoutubeDownloadException(YoutubeDownloadFailure.Unavailable)
        }
        return withContext(Dispatchers.IO) {
            var failure: YoutubeDownloadException? = null
            for (client in CLIENTS) {
                coroutineContext.ensureActive()
                val stream = try {
                    resolve(client, videoId)
                } catch (error: YoutubeDownloadException) {
                    failure = error
                    continue
                }
                try {
                    return@withContext save(client, stream, videoId, displayName, onPercent)
                } catch (error: YoutubeDownloadException) {
                    failure = error
                }
            }
            throw failure ?: YoutubeDownloadException(YoutubeDownloadFailure.Unavailable)
        }
    }

    private fun resolve(client: Client, videoId: String): Stream {
        val clientJson = JSONObject()
            .put("clientName", client.name)
            .put("clientVersion", client.version)
            .put("osName", client.osName)
            .put("osVersion", client.osVersion)
        client.deviceMake?.let { clientJson.put("deviceMake", it) }
        client.deviceModel?.let { clientJson.put("deviceModel", it) }
        val body = JSONObject()
            .put("context", JSONObject().put("client", clientJson))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        val connection = open(PLAYER, client.userAgent).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { stream ->
                stream.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            if (connection.responseCode !in 200..299) {
                throw YoutubeDownloadException(YoutubeDownloadFailure.Unavailable)
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            return pick(JSONObject(text))
        } catch (error: YoutubeDownloadException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw YoutubeDownloadException(YoutubeDownloadFailure.Unavailable)
        } finally {
            connection.disconnect()
        }
    }

    private fun pick(response: JSONObject): Stream {
        val status = response.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
        if (status != "OK") throw YoutubeDownloadException(YoutubeDownloadFailure.Unavailable)
        val formats = response.optJSONObject("streamingData")?.optJSONArray("formats")
            ?: throw YoutubeDownloadException(YoutubeDownloadFailure.Unavailable)
        var best: Stream? = null
        for (index in 0 until formats.length()) {
            val format = formats.optJSONObject(index) ?: continue
            val url = format.optString("url")
            val mime = format.optString("mimeType")
            if (url.isBlank() || !mime.startsWith("video/mp4")) continue
            val height = format.optInt("height")
            val candidate = Stream(url, height, format.optInt("itag"))
            val current = best
            best = when {
                current == null -> candidate
                candidate.height in 1..720 && (current.height !in 1..720 || candidate.height > current.height) -> candidate
                current.height !in 1..720 && candidate.height in 1..current.height -> candidate
                else -> current
            }
        }
        val chosen = best ?: throw YoutubeDownloadException(YoutubeDownloadFailure.Unavailable)
        Log.i(TAG, "save itag=${chosen.itag} height=${chosen.height}")
        return chosen
    }

    private suspend fun save(
        client: Client,
        stream: Stream,
        videoId: String,
        displayName: String?,
        onPercent: (Int) -> Unit,
    ): Uri {
        val connection = open(stream.url, client.userAgent).apply { requestMethod = "GET" }
        val resolver = context.contentResolver
        var uri: Uri? = null
        try {
            if (connection.responseCode !in 200..299) {
                throw YoutubeDownloadException(YoutubeDownloadFailure.Unavailable)
            }
            val total = connection.contentLengthLong
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN).format(Date())
            val name = displayName ?: "空手形_youtube_${videoId}_$stamp.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw YoutubeDownloadException(YoutubeDownloadFailure.Failed)
            resolver.openOutputStream(uri)?.use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var received = 0L
                    var reported = -1
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        val percent = if (total > 0L) ((received * 100L) / total).toInt().coerceIn(0, 100) else -1
                        if (percent != reported) {
                            reported = percent
                            withContext(Dispatchers.Main) { onPercent(percent) }
                        }
                    }
                }
            } ?: throw YoutubeDownloadException(YoutubeDownloadFailure.Failed)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            return uri ?: throw YoutubeDownloadException(YoutubeDownloadFailure.Failed)
        } catch (error: YoutubeDownloadException) {
            uri?.let { resolver.delete(it, null, null) }
            throw error
        } catch (error: Exception) {
            uri?.let { resolver.delete(it, null, null) }
            if (error is CancellationException) throw error
            throw YoutubeDownloadException(YoutubeDownloadFailure.Failed)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, userAgent: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", userAgent)
        }
    }

    private data class Client(
        val name: String,
        val version: String,
        val userAgent: String,
        val osName: String,
        val osVersion: String,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
    )

    private data class Stream(val url: String, val height: Int, val itag: Int)

    private companion object {
        const val TAG = "YoutubeDownload"
        const val PLAYER = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
        val CLIENTS = listOf(
            Client(
                name = "ANDROID",
                version = "20.10.38",
                userAgent = "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip",
                osName = "Android",
                osVersion = "11",
            ),
            Client(
                name = "IOS",
                version = "20.10.4",
                userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X)",
                osName = "iOS",
                osVersion = "18.3.2",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
            ),
        )
    }
}
