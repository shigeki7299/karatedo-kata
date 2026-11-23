package jp.infinity.krate.katachecker.pose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

data class PoseFrame(
    val points: List<Offset>,
    val bones: List<Pair<Offset, Offset>>,
    val landmarks: Map<Int, Offset> = emptyMap(),
)

private val boneTypes = listOf(
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
    PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
    PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
    PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
    PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
    PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
    PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_HEEL,
    PoseLandmark.LEFT_HEEL to PoseLandmark.LEFT_FOOT_INDEX,
    PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_FOOT_INDEX,
    PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_HEEL,
    PoseLandmark.RIGHT_HEEL to PoseLandmark.RIGHT_FOOT_INDEX,
    PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_FOOT_INDEX,
    PoseLandmark.NOSE to PoseLandmark.LEFT_EYE,
    PoseLandmark.NOSE to PoseLandmark.RIGHT_EYE,
    PoseLandmark.LEFT_EYE to PoseLandmark.LEFT_EAR,
    PoseLandmark.RIGHT_EYE to PoseLandmark.RIGHT_EAR,
    PoseLandmark.NOSE to PoseLandmark.LEFT_SHOULDER,
    PoseLandmark.NOSE to PoseLandmark.RIGHT_SHOULDER,
)

fun Pose.toOverlay(imageWidth: Int, imageHeight: Int): PoseFrame? {
    if (imageWidth <= 0 || imageHeight <= 0) return null

    val landmarks = mutableMapOf<Int, Offset>()
    fun point(type: Int, minimum: Float = MIN_LIKELIHOOD): Offset? {
        val landmark = getPoseLandmark(type) ?: return null
        if (landmark.inFrameLikelihood < minimum) return null
        val offset = Offset(
            landmark.position.x / imageWidth,
            landmark.position.y / imageHeight,
        )
        landmarks[type] = offset
        return offset
    }

    val head = listOf(
        PoseLandmark.NOSE,
        PoseLandmark.LEFT_EYE,
        PoseLandmark.RIGHT_EYE,
        PoseLandmark.LEFT_EAR,
        PoseLandmark.RIGHT_EAR,
    ).firstNotNullOfOrNull { point(it) } ?: return null
    val leftShoulder = point(PoseLandmark.LEFT_SHOULDER)
    val rightShoulder = point(PoseLandmark.RIGHT_SHOULDER)
    val leftHip = point(PoseLandmark.LEFT_HIP)
    val rightHip = point(PoseLandmark.RIGHT_HIP)
    val leftPair = leftShoulder != null && leftHip != null
    val rightPair = rightShoulder != null && rightHip != null
    if (!leftPair && !rightPair) return null
    val shoulder = when {
        leftPair && rightPair -> midpoint(leftShoulder, rightShoulder)
        leftPair -> leftShoulder
        else -> rightShoulder
    } ?: return null
    val hip = when {
        leftPair && rightPair -> midpoint(leftHip, rightHip)
        leftPair -> leftHip
        else -> rightHip
    } ?: return null
    val dx = hip.x - shoulder.x
    val dy = hip.y - shoulder.y
    if (dy < MIN_TORSO || kotlin.math.abs(dx) > dy * MAX_LEAN) return null
    if (head.y > hip.y - 0.02f) return null
    val visible = allPoseLandmarks.count { it.inFrameLikelihood >= MIN_LIKELIHOOD }
    if (visible < MIN_LANDMARKS) return null

    val bones = boneTypes.mapNotNull { (start, end) ->
        val from = point(start)
        val to = point(end)
        if (from != null && to != null) from to to else null
    }
    if (bones.isEmpty()) return null
    val points = bones.flatMap { listOf(it.first, it.second) }.distinct()
    return PoseFrame(points, bones, landmarks)
}

private fun midpoint(first: Offset?, second: Offset?): Offset? = when {
    first != null && second != null -> Offset((first.x + second.x) / 2f, (first.y + second.y) / 2f)
    else -> first ?: second
}

@Composable
fun PoseOverlay(frame: PoseFrame?, modifier: Modifier = Modifier) {
    val pose = frame ?: return
    Canvas(modifier.fillMaxSize()) {
        fun map(point: Offset) = Offset(point.x * size.width, point.y * size.height)
        val outline = 3.dp.toPx()
        val stroke = 2.dp.toPx()
        val line = Color(0xFFC6E000)
        pose.bones.forEach { (start, end) ->
            drawLine(Color.Black.copy(alpha = 0.45f), map(start), map(end), outline, StrokeCap.Round)
            drawLine(line, map(start), map(end), stroke, StrokeCap.Round)
        }
        pose.points.forEach { point ->
            drawCircle(line, radius = 2.5.dp.toPx(), center = map(point))
        }
    }
}

@Composable
fun VideoGrid(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val color = Color.White
        val stroke = 1.dp.toPx()
        val x1 = size.width / 3f
        val x2 = size.width * 2f / 3f
        val y1 = size.height / 3f
        val y2 = size.height * 2f / 3f
        drawLine(color, Offset(x1, 0f), Offset(x1, size.height), stroke)
        drawLine(color, Offset(x2, 0f), Offset(x2, size.height), stroke)
        drawLine(color, Offset(0f, y1), Offset(size.width, y1), stroke)
        drawLine(color, Offset(0f, y2), Offset(size.width, y2), stroke)
    }
}

private const val MIN_LIKELIHOOD = 0.5f
private const val MIN_LANDMARKS = 6
private const val MIN_TORSO = 0.035f
private const val MAX_LEAN = 2.2f
