package jp.infinity.krate.katachecker.kata

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ModelKataStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "model_kata").apply { mkdirs() }
    private val appContext = context.applicationContext

    fun has(style: KataStyle, category: KataCategory, kataId: String): Boolean {
        return fileFor(style, category, kataId).isFile
    }

    suspend fun extractAndSave(
        video: Uri,
        style: KataStyle,
        category: KataCategory,
        kata: KataEntry,
        onProgress: (done: Int, total: Int) -> Unit,
    ) {
        val frames = withContext(Dispatchers.IO) {
            readSkeleton(video, onProgress)
        }
        if (frames.count { it.length() > 0 } < MIN_POSES) {
            error("人を検出できませんでした")
        }
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("style", style.id)
                .put("category", category.id)
                .put("kataId", kata.id)
                .put("kataName", kata.name)
                .put("savedAt", System.currentTimeMillis())
                .put("frames", JSONArray().apply { frames.forEach(::put) })
            val file = fileFor(style, category, kata.id)
            file.parentFile?.mkdirs()
            file.writeText(body.toString())
        }
    }

    private suspend fun readSkeleton(
        video: Uri,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<JSONArray> {
        val retriever = MediaMetadataRetriever()
        val detector = PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                .build(),
        )
        try {
            retriever.setDataSource(appContext, video)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            val durationUs = durationMs.coerceAtLeast(1L) * 1000L
            val stepUs = maxOf(SAMPLE_STEP_US, durationUs / MAX_SAMPLES)
            val times = mutableListOf<Long>()
            var time = 0L
            while (time < durationUs) {
                times += time
                time += stepUs
            }
            val frames = ArrayList<JSONArray>(times.size)
            times.forEachIndexed { index, sampleUs ->
                val bitmap = retriever.getFrameAtTime(sampleUs, MediaMetadataRetriever.OPTION_CLOSEST)
                frames += if (bitmap == null) JSONArray() else landmarksOf(detector, bitmap)
                withContext(Dispatchers.Main) { onProgress(index + 1, times.size) }
            }
            return frames
        } finally {
            detector.close()
            retriever.release()
        }
    }

    private suspend fun landmarksOf(detector: com.google.mlkit.vision.pose.PoseDetector, bitmap: Bitmap): JSONArray {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val pose = detector.process(image).await()
            val width = bitmap.width.coerceAtLeast(1)
            val height = bitmap.height.coerceAtLeast(1)
            JSONArray().apply {
                pose.allPoseLandmarks.forEach { landmark ->
                    put(
                        JSONObject()
                            .put("t", landmark.landmarkType)
                            .put("x", landmark.position.x / width)
                            .put("y", landmark.position.y / height)
                            .put("p", landmark.inFrameLikelihood.toDouble()),
                    )
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun fileFor(style: KataStyle, category: KataCategory, kataId: String): File {
        return File(root, "${style.id}/${category.id}/$kataId.json")
    }

    private companion object {
        const val SAMPLE_STEP_US = 200_000L
        const val MAX_SAMPLES = 400
        const val MIN_POSES = 3
    }
}

private suspend fun Task<Pose>.await(): Pose = suspendCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
