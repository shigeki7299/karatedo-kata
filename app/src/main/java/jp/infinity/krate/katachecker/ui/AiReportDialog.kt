package jp.infinity.krate.katachecker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import jp.infinity.krate.katachecker.pose.AiAxis
import jp.infinity.krate.katachecker.pose.AiReport
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val chartFill = Color(0xFFC6E000)

@Composable
private fun AxisRadar(axes: List<AiAxis>, height: androidx.compose.ui.unit.Dp) {
    if (axes.isEmpty()) return
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f * 0.62f
        val count = axes.size
        fun spoke(index: Int, scale: Float): Offset {
            val angle = -PI / 2.0 + 2.0 * PI * index / count
            return Offset(
                center.x + (cos(angle) * radius * scale).toFloat(),
                center.y + (sin(angle) * radius * scale).toFloat(),
            )
        }
        val grid = Color(0xFF8A8A8A)
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { scale ->
            val ring = Path()
            repeat(count) { index ->
                val point = spoke(index, scale)
                if (index == 0) ring.moveTo(point.x, point.y) else ring.lineTo(point.x, point.y)
            }
            ring.close()
            drawPath(ring, grid.copy(alpha = 0.45f), style = Stroke(width = 1.dp.toPx()))
        }
        repeat(count) { index ->
            drawLine(grid.copy(alpha = 0.45f), center, spoke(index, 1f), strokeWidth = 1.dp.toPx())
        }
        val shape = Path()
        axes.forEachIndexed { index, axis ->
            val point = spoke(index, axis.score / 100f)
            if (index == 0) shape.moveTo(point.x, point.y) else shape.lineTo(point.x, point.y)
        }
        shape.close()
        drawPath(shape, chartFill.copy(alpha = 0.45f))
        drawPath(shape, chartFill, style = Stroke(width = 2.dp.toPx()))
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 12.sp.toPx()
            isAntiAlias = true
        }
        axes.forEachIndexed { index, axis ->
            val angle = -PI / 2.0 + 2.0 * PI * index / count
            val point = spoke(index, 1.28f)
            paint.textAlign = when {
                cos(angle) > 0.4 -> android.graphics.Paint.Align.LEFT
                cos(angle) < -0.4 -> android.graphics.Paint.Align.RIGHT
                else -> android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(axis.label, point.x, point.y, paint)
        }
    }
}

private val scoreGreen = Color(0xFF1B7F3A)
private val fixRed = Color(0xFFC62828)
private val badgeFill = Color(0xFFE5F6EA)

@Composable
fun AiReportDialog(
    report: AiReport?,
    message: String?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(max = 640.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (report == null) {
                    Text("ＡＩ解析", style = MaterialTheme.typography.titleLarge)
                    Text(message ?: "解析できませんでした。")
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(report.title, style = MaterialTheme.typography.titleLarge)
                        Surface(color = badgeFill, shape = RoundedCornerShape(50)) {
                            Text(
                                text = "解析完了",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = scoreGreen,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "${report.score}点",
                            color = scoreGreen,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 52.sp,
                        )
                        Text(
                            text = "今回の練習スコア",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                    Text("技術点 ${report.technical} / 70", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "立ち方・技・移行動作・呼吸・極め・リズム・正確さ・形の難度・ふらつき・元の位置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AxisRadar(report.technicalAxes, height = 300.dp)
                    Text("競技点 ${report.competition} / 30", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "力強さ・スピード・バランス",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AxisRadar(report.competitionAxes, height = 220.dp)
                    Text(
                        "各軸は高いほど良い点数です。ふらつきは揺れが少ないほど高くなります。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    Text("良かったところ", style = MaterialTheme.typography.titleMedium)
                    report.goods.forEach { line ->
                        Text("✓  $line", color = scoreGreen, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text("次に直すところ", style = MaterialTheme.typography.titleMedium)
                    report.fixes.forEach { line ->
                        Text("●  $line", color = fixRed, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (report.fixes.isEmpty()) {
                        Text("大きなずれはありません。", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("閉じる") }
                }
            }
        }
    }
}
