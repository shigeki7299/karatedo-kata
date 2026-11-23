package jp.infinity.krate.katachecker.ui

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

class VideoTaps {
    var onTap: () -> Unit = {}
    var onDoubleTap: () -> Unit = {}
}

class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)

    val zoomed: Boolean
        get() = scale > 1.01f

    fun reset() {
        scale = 1f
        panX = 0f
        panY = 0f
    }

    fun change(zoomChange: Float, pan: Offset, centroid: Offset, width: Float, height: Float) {
        if (width <= 1f || height <= 1f) return
        val old = scale
        val next = (old * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        val center = Offset(width / 2f, height / 2f)
        val layoutPoint = center + (centroid - center - Offset(panX, panY)) / old
        val focused = centroid - center - (layoutPoint - center) * next + pan
        scale = next
        if (next <= MIN_SCALE) {
            panX = 0f
            panY = 0f
            return
        }
        val maxX = width * (next - 1f) / 2f
        val maxY = height * (next - 1f) / 2f
        panX = focused.x.coerceIn(-maxX, maxX)
        panY = focused.y.coerceIn(-maxY, maxY)
    }
}

class ZoomTouchLayout(context: Context) : FrameLayout(context) {
    var sharedTouch: View.OnTouchListener? = null

    fun attachSharedTouch() {
        val listener = sharedTouch ?: return
        attachZoomTouch(listener)
    }
}

private fun View.attachZoomTouch(listener: View.OnTouchListener) {
    setOnTouchListener(listener)
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).attachZoomTouch(listener)
        }
    }
}

fun zoomTouchListener(
    context: Context,
    zoom: ZoomState,
    taps: VideoTaps,
): View.OnTouchListener {
    val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                if (!zoom.zoomed) taps.onTap()
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (zoom.zoomed) zoom.reset() else taps.onDoubleTap()
                return true
            }
        },
    )
    var lastSpan = 0f
    var lastCentroid = Offset.Zero
    var lastPoint = Offset.Zero
    return View.OnTouchListener { view, event ->
        detector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastPoint = Offset(event.x, event.y)
                lastSpan = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    lastSpan = event.span()
                    lastCentroid = event.centroid()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val width = view.width.toFloat()
                val height = view.height.toFloat()
                if (event.pointerCount >= 2 && lastSpan > 0f) {
                    val span = event.span()
                    val centroid = event.centroid()
                    zoom.change(
                        zoomChange = span / lastSpan,
                        pan = centroid - lastCentroid,
                        centroid = centroid,
                        width = width,
                        height = height,
                    )
                    lastSpan = span
                    lastCentroid = centroid
                } else if (event.pointerCount == 1 && zoom.zoomed) {
                    val point = Offset(event.x, event.y)
                    zoom.change(
                        zoomChange = 1f,
                        pan = point - lastPoint,
                        centroid = point,
                        width = width,
                        height = height,
                    )
                    lastPoint = point
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                lastSpan = 0f
                val remaining = event.pointerCount - 1
                if (remaining == 1) {
                    val index = if (event.actionIndex == 0) 1 else 0
                    lastPoint = Offset(event.getX(index), event.getY(index))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> lastSpan = 0f
        }
        true
    }
}

private fun MotionEvent.span(): Float {
    if (pointerCount < 2) return 0f
    return hypot(getX(0) - getX(1), getY(0) - getY(1)).coerceAtLeast(1f)
}

private fun MotionEvent.centroid(): Offset {
    var x = 0f
    var y = 0f
    for (index in 0 until pointerCount) {
        x += getX(index)
        y += getY(index)
    }
    return Offset(x / pointerCount, y / pointerCount)
}

fun View.applyZoom(zoom: ZoomState) {
    pivotX = width / 2f
    pivotY = height / 2f
    scaleX = zoom.scale
    scaleY = zoom.scale
    translationX = zoom.panX
    translationY = zoom.panY
    (parent as? ViewGroup)?.clipChildren = true
}

fun Modifier.zoomDraw(zoom: ZoomState): Modifier = graphicsLayer {
    scaleX = zoom.scale
    scaleY = zoom.scale
    translationX = zoom.panX
    translationY = zoom.panY
    transformOrigin = TransformOrigin.Center
}

fun Modifier.pinchToZoom(zoom: ZoomState, taps: VideoTaps, scope: CoroutineScope): Modifier = pointerInput(zoom) {
    var lastTap = 0L
    var pending: Job? = null
    val confirmDelay = viewConfiguration.doubleTapTimeoutMillis.coerceAtLeast(280)
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downTime = down.uptimeMillis
        var moved = false
        var panning = false
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                moved = true
                zoom.change(
                    zoomChange = event.calculateZoom(),
                    pan = event.calculatePan(),
                    centroid = event.calculateCentroid(useCurrent = true),
                    width = size.width.toFloat(),
                    height = size.height.toFloat(),
                )
                event.changes.forEach { it.consume() }
            } else if (pressed.size == 1 && zoom.zoomed) {
                val change = pressed[0]
                if (!panning && abs(change.positionChange().x) + abs(change.positionChange().y) > viewConfiguration.touchSlop) {
                    panning = true
                    moved = true
                }
                if (panning) {
                    zoom.change(
                        zoomChange = 1f,
                        pan = change.positionChange(),
                        centroid = change.position,
                        width = size.width.toFloat(),
                        height = size.height.toFloat(),
                    )
                    change.consume()
                }
            }
        } while (event.changes.any { it.pressed })
        if (!moved && lastTap != 0L && downTime - lastTap in 40..confirmDelay) {
            pending?.cancel()
            pending = null
            if (zoom.zoomed) zoom.reset() else taps.onDoubleTap()
            lastTap = 0L
        } else if (!moved && !zoom.zoomed) {
            lastTap = downTime
            pending?.cancel()
            pending = scope.launch {
                delay(confirmDelay)
                taps.onTap()
            }
        } else {
            pending?.cancel()
            pending = null
        }
    }
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 4f
