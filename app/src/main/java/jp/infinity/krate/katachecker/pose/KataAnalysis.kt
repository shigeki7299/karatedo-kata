package jp.infinity.krate.katachecker.pose

import androidx.compose.ui.geometry.Offset
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class AiAxis(val label: String, val score: Int)

data class AiReport(
    val title: String,
    val score: Int,
    val technical: Int,
    val competition: Int,
    val technicalAxes: List<AiAxis>,
    val competitionAxes: List<AiAxis>,
    val goods: List<String>,
    val fixes: List<String>,
)

fun analyzePerformance(frames: List<PoseFrame>, kataName: String): AiReport? {
    val samples = frames.mapNotNull(::poseSample).thin(MAX_SAMPLES)
    if (samples.size < MIN_SAMPLES) return null
    val speeds = hipSpeeds(samples)
    val still = speeds.map { it < STILL_SPEED }
    val front = samples.filter { it.front }
    val stanceScore = scoreStance(front.ifEmpty { samples })
    val techniqueScore = scoreTechnique(samples)
    val transitionScore = scoreTransitions(speeds)
    val breathScore = scoreBreath(still)
    val kimeScore = scoreKime(samples, still)
    val rhythmScore = scoreRhythm(speeds)
    val accuracyScore = scoreAccuracy(front)
    val difficultyScore = kataDifficulty(kataName)
    val wobbleScore = scoreWobble(speeds, still)
    val returnScore = scoreReturn(samples)
    val speedScore = scoreSpeed(speeds)
    val balanceScore = scoreBalance(samples)
    val powerScore = ((stanceScore + speedScore) / 2).coerceIn(0, 100)

    val technicalAxes = listOf(
        AiAxis("立ち方", stanceScore),
        AiAxis("技", techniqueScore),
        AiAxis("移行動作", transitionScore),
        AiAxis("呼吸", breathScore),
        AiAxis("極め", kimeScore),
        AiAxis("リズム", rhythmScore),
        AiAxis("正確さ", accuracyScore),
        AiAxis("形の難度", difficultyScore),
        AiAxis("ふらつき", wobbleScore),
        AiAxis("元の位置", returnScore),
    )
    val competitionAxes = listOf(
        AiAxis("力強さ", powerScore),
        AiAxis("スピード", speedScore),
        AiAxis("バランス", balanceScore),
    )
    val technical = (technicalAxes.map { it.score }.average() * 0.70).roundToInt().coerceIn(0, 70)
    val competition = (competitionAxes.map { it.score }.average() * 0.30).roundToInt().coerceIn(0, 30)
    val judged = (technicalAxes + competitionAxes).filter { it.label != "形の難度" }
    val fixes = judged.filter { it.score < 70 }.sortedBy { it.score }.take(2)
    val goods = judged.filter { it.score >= 78 }.sortedByDescending { it.score }.take(2).map { goodOf(it.label) }
    val title = if (kataName.isBlank() || kataName == "形") "AIチェック" else "$kataName AIチェック"
    return AiReport(
        title = title,
        score = technical + competition,
        technical = technical,
        competition = competition,
        technicalAxes = technicalAxes,
        competitionAxes = competitionAxes,
        goods = goods.ifEmpty { listOf("最後まで演武できました") },
        fixes = fixes.map { it.label },
    )
}

private fun <T> List<T>.thin(limit: Int): List<T> {
    if (size <= limit) return this
    return List(limit) { index -> this[(index * (size - 1)) / (limit - 1)] }
}

private fun goodOf(label: String): String = when (label) {
    "立ち方" -> "立ち方が安定しています"
    "技" -> "技が低い位置まで決まっています"
    "移行動作" -> "立ちから立ちへ移れています"
    "呼吸" -> "技のあとに間が取れています"
    "極め" -> "技の最後が伸びています"
    "リズム" -> "速いところと溜めに差があります"
    "正確さ" -> "同じ立ちが揃っています"
    "ふらつき" -> "極めたあとに体が止まっています"
    "元の位置" -> "開始位置に戻っています"
    "力強さ" -> "腰を落として技が出ています"
    "スピード" -> "移動が鋭いです"
    "バランス" -> "軸が安定しています"
    else -> "最後まで演武できました"
}

private data class PoseSample(
    val hip: Offset,
    val torso: Float,
    val knee: Float?,
    val front: Boolean,
    val stance: Float?,
    val balance: Float?,
    val block: Float?,
    val elbow: Float?,
)

private fun poseSample(frame: PoseFrame): PoseSample? {
    val hip = hipCenter(frame) ?: return null
    val torso = torsoSpan(frame) ?: return null
    val elbows = listOfNotNull(
        frame.angle(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
        frame.angle(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
    )
    return PoseSample(
        hip = hip,
        torso = torso,
        knee = bentKnee(frame),
        front = isFrontStance(frame),
        stance = stanceWidth(frame),
        balance = balance(frame),
        block = lowBlockDrop(frame),
        elbow = elbows.maxOrNull(),
    )
}

private fun hipSpeeds(samples: List<PoseSample>): List<Float> {
    if (samples.size < 2) return List(samples.size) { 0f }
    val speeds = MutableList(samples.size) { 0f }
    for (index in 1 until samples.size) {
        val previous = samples[index - 1]
        val current = samples[index]
        val scale = ((previous.torso + current.torso) / 2f).coerceAtLeast(0.02f)
        speeds[index] = hypot(current.hip.x - previous.hip.x, current.hip.y - previous.hip.y) / scale
    }
    speeds[0] = speeds[1]
    return speeds
}

private fun scoreStance(samples: List<PoseSample>): Int {
    val knees = samples.mapNotNull { it.knee }
    if (knees.isEmpty()) return 60
    return knees.map { mark(it, best = 120f, worst = 175f) }.average().roundToInt()
}

private fun scoreTechnique(samples: List<PoseSample>): Int {
    val blocks = samples.mapNotNull { it.block }
    if (blocks.size >= 3) {
        return blocks.map { mark(it, best = 0.55f, worst = 0.12f) }.average().roundToInt()
    }
    val elbows = samples.mapNotNull { it.elbow }
    if (elbows.isEmpty()) return 60
    return elbows.map { mark(it, best = 170f, worst = 110f) }.average().roundToInt()
}

private fun scoreTransitions(speeds: List<Float>): Int {
    if (speeds.isEmpty()) return 60
    val moving = speeds.count { it >= STILL_SPEED }.toFloat() / speeds.size
    return when {
        moving in 0.18f..0.62f -> 88
        moving < 0.08f -> 42
        moving > 0.8f -> 55
        else -> 70
    }
}

private fun scoreBreath(still: List<Boolean>): Int {
    var pauses = 0
    var moves = 0
    var quiet = 0
    var moving = false
    still.forEach { isStill ->
        if (isStill) {
            if (moving) quiet++
        } else {
            if (quiet in 2..8) pauses++
            quiet = 0
            moves++
            moving = true
        }
    }
    if (moves < 3) return 60
    val ratio = pauses.toFloat() / moves
    return when {
        ratio in 0.25f..1.2f -> 86
        ratio < 0.1f -> 48
        else -> 68
    }
}

private fun scoreKime(samples: List<PoseSample>, still: List<Boolean>): Int {
    val settled = samples.filterIndexed { index, _ -> still.getOrNull(index) == true }.mapNotNull { it.elbow }
    if (settled.size < 3) return 60
    val sharp = settled.count { it >= 155f || it <= 80f }.toFloat() / settled.size
    return (sharp * 100f).roundToInt()
}

private fun scoreRhythm(speeds: List<Float>): Int {
    val peaks = speeds.indices.filter { index ->
        index > 0 && index < speeds.lastIndex &&
            speeds[index] >= MOVE_SPEED &&
            speeds[index] >= speeds[index - 1] &&
            speeds[index] > speeds[index + 1]
    }
    if (peaks.size < 3) return 62
    val gaps = peaks.zipWithNext { left, right -> (right - left).toFloat() }
    val spread = variation(gaps)
    return when {
        spread <= 0.85f -> 90
        spread <= 1.3f -> 70
        else -> 46
    }
}

private fun scoreAccuracy(front: List<PoseSample>): Int {
    val widths = front.mapNotNull { it.stance }
    if (widths.size < 4) return 68
    return mark(variation(widths), best = 0.08f, worst = 0.55f)
}

private fun scoreWobble(speeds: List<Float>, still: List<Boolean>): Int {
    val drift = speeds.filterIndexed { index, _ -> still.getOrNull(index) == true }
    if (drift.size < 4) return 65
    return mark(drift.average().toFloat(), best = 0.003f, worst = 0.02f)
}

private fun scoreReturn(samples: List<PoseSample>): Int {
    val count = (samples.size * 0.15f).roundToInt().coerceAtLeast(2)
    val start = meanHip(samples.take(count))
    val end = meanHip(samples.takeLast(count))
    val scale = samples.map { it.torso }.average().toFloat().coerceAtLeast(0.02f)
    val distance = hypot(end.x - start.x, end.y - start.y) / scale
    return mark(distance, best = 0.2f, worst = 1.1f)
}

private fun scoreSpeed(speeds: List<Float>): Int {
    if (speeds.isEmpty()) return 60
    val sorted = speeds.sorted()
    val fast = sorted[(sorted.lastIndex * 0.9f).roundToInt()]
    return mark(fast, best = 0.09f, worst = 0.012f)
}

private fun scoreBalance(samples: List<PoseSample>): Int {
    val values = samples.mapNotNull { it.balance }
    if (values.isEmpty()) return 60
    return mark(values.average().toFloat(), best = 0.06f, worst = 0.45f)
}

private fun kataDifficulty(name: String): Int = when {
    name.contains("五段") -> 92
    name.contains("四段") -> 86
    name.contains("三段") -> 80
    name.contains("二段") -> 74
    name.contains("初段") || name.contains("一段") -> 68
    name.isBlank() || name == "形" -> 70
    else -> 78
}

private fun meanHip(samples: List<PoseSample>): Offset {
    val x = samples.map { it.hip.x }.average().toFloat()
    val y = samples.map { it.hip.y }.average().toFloat()
    return Offset(x, y)
}

private fun variation(values: List<Float>): Float {
    if (values.size < 2) return 0f
    val mean = values.average().toFloat()
    if (abs(mean) < 1e-4f) return 0f
    val variance = values.map { (it - mean) * (it - mean) }.average()
    return sqrt(variance).toFloat() / abs(mean)
}

private fun mark(value: Float, best: Float, worst: Float): Int {
    if (best == worst) return 0
    val ratio = (value - worst) / (best - worst)
    return (ratio.coerceIn(0f, 1f) * 100f).roundToInt()
}

private fun isFrontStance(frame: PoseFrame): Boolean {
    val knees = kneeAngles(frame)
    if (knees.size < 2) return false
    val width = stanceWidth(frame) ?: return false
    return knees.min() < 155f && knees.max() - knees.min() > 12f && width > 0.7f
}

private fun bentKnee(frame: PoseFrame): Float? = kneeAngles(frame).minOrNull()

private fun kneeAngles(frame: PoseFrame): List<Float> = listOfNotNull(
    frame.angle(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
    frame.angle(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
)

private fun lowBlockDrop(frame: PoseFrame): Float? {
    val torso = torsoSpan(frame) ?: return null
    val drops = listOf(
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
    ).mapNotNull { (upper, wristType) ->
        val (shoulderType, elbowType) = upper
        val elbow = frame.angle(shoulderType, elbowType, wristType) ?: return@mapNotNull null
        val wrist = frame.point(wristType) ?: return@mapNotNull null
        val hip = hipCenter(frame) ?: return@mapNotNull null
        if (elbow < 140f || wrist.y < hip.y + 0.02f) return@mapNotNull null
        (wrist.y - hip.y) / torso
    }
    return drops.maxOrNull()
}

private fun stanceWidth(frame: PoseFrame): Float? {
    val left = frame.point(PoseLandmark.LEFT_ANKLE) ?: return null
    val right = frame.point(PoseLandmark.RIGHT_ANKLE) ?: return null
    val torso = torsoSpan(frame) ?: return null
    return abs(left.x - right.x) / torso
}

private fun balance(frame: PoseFrame): Float? {
    val hip = hipCenter(frame) ?: return null
    val ankles = listOfNotNull(frame.point(PoseLandmark.LEFT_ANKLE), frame.point(PoseLandmark.RIGHT_ANKLE))
    if (ankles.isEmpty()) return null
    val ankleX = ankles.map { it.x }.average().toFloat()
    val width = frame.span(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER) ?: return null
    if (width < 0.01f) return null
    return abs(hip.x - ankleX) / width
}

private fun torsoSpan(frame: PoseFrame): Float? {
    val shoulder = shoulderCenter(frame) ?: return null
    val hip = hipCenter(frame) ?: return null
    val span = hip.y - shoulder.y
    return span.takeIf { it >= 0.02f }
}

private fun shoulderCenter(frame: PoseFrame): Offset? =
    frame.center(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)

private fun hipCenter(frame: PoseFrame): Offset? =
    frame.center(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)

private fun PoseFrame.point(type: Int): Offset? = landmarks[type]

private fun PoseFrame.center(first: Int, second: Int): Offset? {
    val a = point(first) ?: return null
    val b = point(second) ?: return null
    return Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
}

private fun PoseFrame.span(first: Int, second: Int): Float? {
    val a = point(first) ?: return null
    val b = point(second) ?: return null
    return hypot(a.x - b.x, a.y - b.y)
}

private fun PoseFrame.angle(upper: Int, joint: Int, lower: Int): Float? {
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

private const val MIN_SAMPLES = 8
private const val MAX_SAMPLES = 120
private const val STILL_SPEED = 0.018f
private const val MOVE_SPEED = 0.03f
