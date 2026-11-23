package jp.infinity.krate.katachecker.pose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

data class CapturedRegion(
    val bitmap: Bitmap,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val screenCrop: Boolean,
)

fun Bitmap.scaledForPose(maxWidth: Int = 360): Bitmap {
    if (width <= maxWidth) return this
    val targetHeight = (height * (maxWidth.toFloat() / width)).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, maxWidth, targetHeight, true)
}

fun PoseFrame.undoViewZoom(view: View): PoseFrame {
    val scaleX = view.scaleX
    val scaleY = view.scaleY
    if (scaleX < 0.01f || scaleY < 0.01f) return this
    if (
        abs(scaleX - 1f) < 0.01f &&
        abs(scaleY - 1f) < 0.01f &&
        abs(view.translationX) < 1f &&
        abs(view.translationY) < 1f
    ) {
        return this
    }
    val width = view.width.toFloat()
    val height = view.height.toFloat()
    if (width <= 1f || height <= 1f) return this
    val pivotX = view.pivotX
    val pivotY = view.pivotY
    fun map(point: Offset): Offset {
        val localX = point.x * width
        val localY = point.y * height
        val contentX = pivotX + (localX - view.translationX - pivotX) / scaleX
        val contentY = pivotY + (localY - view.translationY - pivotY) / scaleY
        return Offset(contentX / width, contentY / height)
    }
    return PoseFrame(
        points = points.map(::map),
        bones = bones.map { (start, end) -> map(start) to map(end) },
        landmarks = landmarks.mapValues { (_, point) -> map(point) },
    )
}

suspend fun View.captureForPose(): CapturedRegion? {
    if (!isLaidOut || width <= 1 || height <= 1) return null
    val source = findLargestVideoSurface(this) ?: this
    val bitmap = when (source) {
        is TextureView -> source.bitmap
        is SurfaceView -> source.copyPixels()
        else -> null
    }
    if (bitmap != null && !bitmap.looksBlank()) {
        val (left, top) = locationInside(this, source)
        return CapturedRegion(bitmap, left, top, source.width, source.height, screenCrop = false)
    }
    bitmap?.recycle()
    val fallback = copyWindowRegion()
    if (fallback == null || fallback.looksBlank()) {
        fallback?.recycle()
        return null
    }
    return CapturedRegion(fallback, 0, 0, width, height, screenCrop = true)
}

private fun Bitmap.looksBlank(): Boolean {
    var bright = 0
    val stepX = (width / 8).coerceAtLeast(1)
    val stepY = (height / 8).coerceAtLeast(1)
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = getPixel(x, y)
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            if (red + green + blue > 48) bright++
            x += stepX
        }
        y += stepY
    }
    return bright < 4
}

private fun findLargestVideoSurface(root: View): View? {
    var best: View? = null
    var bestArea = 0
    fun walk(view: View) {
        if (view !== root && (view is SurfaceView || view is TextureView)) {
            val area = view.width * view.height
            if (area > bestArea && view.width > 32 && view.height > 32) {
                best = view
                bestArea = area
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                walk(view.getChildAt(index))
            }
        }
    }
    walk(root)
    return best
}

private fun locationInside(ancestor: View, child: View): Pair<Int, Int> {
    if (ancestor === child) return 0 to 0
    var x = 0
    var y = 0
    var view: View = child
    while (view !== ancestor) {
        val parent = view.parent as? View ?: return 0 to 0
        x += view.left - parent.scrollX
        y += view.top - parent.scrollY
        view = parent
    }
    return x to y
}

private suspend fun SurfaceView.copyPixels(): Bitmap? {
    if (width <= 1 || height <= 1 || !holder.surface.isValid) return null
    return requestCopy(width, height) { bitmap, listener, handler ->
        PixelCopy.request(this, bitmap, listener, handler)
    }
}

private suspend fun View.copyWindowRegion(): Bitmap? {
    val activity = context.findActivity() ?: return null
    val zoomed = scaleX > 1.01f || scaleY > 1.01f
    val rect = if (zoomed) layoutRectInWindow() else unscaledRectInWindow()
    if (rect.width() <= 1 || rect.height() <= 1) return null
    return requestCopy(rect.width(), rect.height()) { bitmap, listener, handler ->
        PixelCopy.request(activity.window, rect, bitmap, listener, handler)
    }
}

private fun View.unscaledRectInWindow(): Rect {
    val location = IntArray(2)
    getLocationInWindow(location)
    return Rect(location[0], location[1], location[0] + width, location[1] + height)
}

private fun View.layoutRectInWindow(): Rect {
    val parentView = parent as? View
    if (parentView == null) {
        val location = IntArray(2)
        getLocationInWindow(location)
        return Rect(location[0], location[1], location[0] + width, location[1] + height)
    }
    val parentLocation = IntArray(2)
    parentView.getLocationInWindow(parentLocation)
    val x = parentLocation[0] + left - parentView.scrollX
    val y = parentLocation[1] + top - parentView.scrollY
    return Rect(x, y, x + width, y + height)
}

private suspend fun requestCopy(
    width: Int,
    height: Int,
    request: (Bitmap, PixelCopy.OnPixelCopyFinishedListener, Handler) -> Unit,
): Bitmap? = suspendCancellableCoroutine { continuation ->
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val handler = Handler(Looper.getMainLooper())
    try {
        request(
            bitmap,
            PixelCopy.OnPixelCopyFinishedListener { result ->
                if (!continuation.isActive) {
                    bitmap.recycle()
                    return@OnPixelCopyFinishedListener
                }
                if (result == PixelCopy.SUCCESS) {
                    continuation.resume(bitmap)
                } else {
                    bitmap.recycle()
                    continuation.resume(null)
                }
            },
            handler,
        )
    } catch (_: IllegalArgumentException) {
        bitmap.recycle()
        if (continuation.isActive) continuation.resume(null)
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
