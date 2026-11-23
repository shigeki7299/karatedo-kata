package jp.infinity.krate.katachecker.pose

import androidx.compose.ui.geometry.Offset
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

data class ScoreRow(val label: String, val score: Int?)

data class ScoreSheet(
    val rows: List<ScoreRow>,
    val total: Int,
    val maximum: Int,
)

fun scoreKata(model: PoseFrame, subject: PoseFrame): ScoreSheet? {
    if (!model.hasTorso() || !subject.hasTorso()) return null
    val rows = listOf(
        ScoreRow("腰の高さ", hipScore(model, subject)),
        ScoreRow("バランス", compare(balance(model), balance(subject), 0.40f)),
        ScoreRow("切れ", snapScore(model, subject)),
        ScoreRow("体幹", trunkScore(model, subject)),
    )
    if (rows.all { it.score == null }) return null
    return ScoreSheet(rows, rows.sumOf { it.score ?: 0 }, 100)
}

fun averageScores(samples: List<ScoreSheet>): ScoreSheet? {
    if (samples.isEmpty()) return null
    val labels = samples.first().rows.map { it.label }
    val rows = labels.mapIndexed { index, label ->
        val mean = samples.map { it.rows.getOrNull(index)?.score ?: 0 }.average()
        ScoreRow(label, mean.roundToInt().coerceIn(0, 25))
    }
    return ScoreSheet(rows, rows.sumOf { it.score ?: 0 }, 100)
}

private fun PoseFrame.point(type: Int): Offset? = landmarks[type]

private fun PoseFrame.hasTorso(): Boolean {
    return point(PoseLandmark.LEFT_SHOULDER) != null &&
        point(PoseLandmark.RIGHT_SHOULDER) != null &&
        point(PoseLandmark.LEFT_HIP) != null &&
        point(PoseLandmark.RIGHT_HIP) != null
}

private fun hipScore(model: PoseFrame, subject: PoseFrame): Int? {
    val height = compare(hipIndex(model), hipIndex(subject), 0.15f)
    val knee = compare(kneeBend(model), kneeBend(subject), 25f)
    return when {
        height != null && knee != null -> (height + knee) / 2
        else -> height ?: knee
    }
}

private fun hipIndex(frame: PoseFrame): Float? {
    val shoulder = frame.mid(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER) ?: return null
    val hip = frame.mid(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP) ?: return null
    val ankles = listOfNotNull(
        frame.point(PoseLandmark.LEFT_ANKLE),
        frame.point(PoseLandmark.RIGHT_ANKLE),
    )
    if (ankles.isEmpty()) return null
    val lowest = ankles.maxBy { it.y }
    val span = lowest.y - shoulder.y
    if (span < 0.02f) return null
    return (lowest.y - hip.y) / span
}

private fun kneeBend(frame: PoseFrame): Float? {
    return meanAngle(
        frame.jointAngle(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
        frame.jointAngle(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
    )
}

private fun balance(frame: PoseFrame): Float? {
    val hip = frame.mid(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP) ?: return null
    val ankles = listOfNotNull(
        frame.point(PoseLandmark.LEFT_ANKLE),
        frame.point(PoseLandmark.RIGHT_ANKLE),
    )
    if (ankles.isEmpty()) return null
    val ankle = if (ankles.size == 1) ankles[0] else Offset(
        (ankles[0].x + ankles[1].x) / 2f,
        (ankles[0].y + ankles[1].y) / 2f,
    )
    val width = frame.distance(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER) ?: return null
    if (width < 0.01f) return null
    return abs(hip.x - ankle.x) / width
}

private fun snapScore(model: PoseFrame, subject: PoseFrame): Int? {
    val pairs = listOf(
        Triple(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
        Triple(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
        Triple(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
        Triple(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
    ).mapNotNull { (upper, joint, lower) ->
        val modelAngle = model.jointAngle(upper, joint, lower) ?: return@mapNotNull null
        val subjectAngle = subject.jointAngle(upper, joint, lower) ?: return@mapNotNull null
        abs(modelAngle - subjectAngle)
    }
    if (pairs.size < 2) return null
    return similarity(pairs.average().toFloat(), 35f)
}

private fun trunkScore(model: PoseFrame, subject: PoseFrame): Int? {
    val lean = compare(trunkLean(model), trunkLean(subject), 15f) ?: return null
    val twist = compare(trunkTwist(model), trunkTwist(subject), 12f)
    return if (twist == null) lean else (lean + twist) / 2
}

private fun trunkLean(frame: PoseFrame): Float? {
    val shoulder = frame.mid(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER) ?: return null
    val hip = frame.mid(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP) ?: return null
    val rise = hip.y - shoulder.y
    if (rise < 0.01f) return null
    return Math.toDegrees(atan2(abs(shoulder.x - hip.x).toDouble(), rise.toDouble())).toFloat()
}

private fun trunkTwist(frame: PoseFrame): Float? {
    val shoulders = frame.lineTilt(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER) ?: return null
    val hips = frame.lineTilt(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP) ?: return null
    return abs(angleDelta(shoulders, hips))
}

private fun compare(model: Float?, subject: Float?, tolerance: Float): Int? {
    if (model == null || subject == null) return null
    return similarity(abs(model - subject), tolerance)
}

private fun similarity(delta: Float, tolerance: Float): Int {
    if (tolerance <= 0f) return 0
    val ratio = 1f - (delta / tolerance).coerceIn(0f, 1f)
    return (ratio * 25f).roundToInt().coerceIn(0, 25)
}

private fun PoseFrame.mid(first: Int, second: Int): Offset? {
    val a = point(first) ?: return null
    val b = point(second) ?: return null
    return Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
}

private fun PoseFrame.distance(first: Int, second: Int): Float? {
    val a = point(first) ?: return null
    val b = point(second) ?: return null
    return hypot(a.x - b.x, a.y - b.y)
}

private fun PoseFrame.jointAngle(upper: Int, joint: Int, lower: Int): Float? {
    val a = point(upper) ?: return null
    val b = point(joint) ?: return null
    val c = point(lower) ?: return null
    val abx = a.x - b.x
    val aby = a.y - b.y
    val cbx = c.x - b.x
    val cby = c.y - b.y
    val magnitude = hypot(abx, aby) * hypot(cbx, cby)
    if (magnitude < 1e-4f) return null
    val cosine = ((abx * cbx + aby * cby) / magnitude).coerceIn(-1f, 1f)
    return Math.toDegrees(acos(cosine.toDouble())).toFloat()
}

private fun PoseFrame.lineTilt(first: Int, second: Int): Float? {
    val a = point(first) ?: return null
    val b = point(second) ?: return null
    return Math.toDegrees(atan2((a.y - b.y).toDouble(), (a.x - b.x).toDouble())).toFloat()
}

private fun angleDelta(first: Float, second: Float): Float {
    var delta = first - second
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    return delta
}

private fun meanAngle(first: Float?, second: Float?): Float? = when {
    first != null && second != null -> (first + second) / 2f
    else -> first ?: second
}
