package jp.infinity.krate.katachecker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import jp.infinity.krate.katachecker.pose.ScoreRow
import jp.infinity.krate.katachecker.pose.ScoreSheet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val chartFill = Color(0xFFC6E000)

@Composable
fun ScoreDialog(
    sheet: ScoreSheet?,
    message: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("２画面差分採点") },
        text = {
            if (sheet == null) {
                Text(message ?: "左右の骨格が取れていません。両方に骨格が出ているときに採点してください。")
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "同時再生で比べました。左がお手本、右が採点者です。各項目25点、合計100点です。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ScoreRadar(sheet.rows)
                    sheet.rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(row.label)
                            Text(row.score?.let { "$it / 25" } ?: "—")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("合計", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${sheet.total} / ${sheet.maximum}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
    )
}

@Composable
private fun ScoreRadar(rows: List<ScoreRow>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 28.dp),
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            val count = rows.size.coerceAtLeast(1)
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
            rows.forEachIndexed { index, row ->
                val scale = ((row.score ?: 0) / 25f).coerceIn(0f, 1f)
                val point = spoke(index, scale)
                if (index == 0) shape.moveTo(point.x, point.y) else shape.lineTo(point.x, point.y)
            }
            shape.close()
            drawPath(shape, chartFill.copy(alpha = 0.45f))
            drawPath(shape, chartFill, style = Stroke(width = 2.dp.toPx()))
            rows.forEachIndexed { index, row ->
                val scale = ((row.score ?: 0) / 25f).coerceIn(0f, 1f)
                drawCircle(chartFill, radius = 3.dp.toPx(), center = spoke(index, scale))
            }
        }
        if (rows.size >= 4) {
            Text(rows[0].label, modifier = Modifier.align(Alignment.TopCenter))
            Text(rows[1].label, modifier = Modifier.align(Alignment.CenterEnd))
            Text(rows[2].label, modifier = Modifier.align(Alignment.BottomCenter))
            Text(rows[3].label, modifier = Modifier.align(Alignment.CenterStart))
        }
    }
}
