package jp.infinity.krate.katachecker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private data class ScoreBand(val label: String, val score: String, val notes: List<String>)

private data class Deduction(val detail: String, val slight: String, val large: String)

private val scoreBands = listOf(
    ScoreBand("違反", "0.0", listOf("異なる形を演武した場合", "異なる形を告げた場合")),
    ScoreBand("非常に悪い演武", "5.0 ～ 5.8", listOf("形演武開始の礼、または終了の礼を行わなかった場合", "明らかな技の中断、または演武停止")),
    ScoreBand("平均以下の演武", "6.0 ～ 6.8", listOf("審判妨害", "安全性に問題があり審判が介入せざるを得なかった場合", "審判への接触")),
    ScoreBand("平均的な演武", "7.0 ～ 7.8", listOf("演武中に帯が落下した場合")),
    ScoreBand("非常に良い演武", "8.0 ～ 8.8", listOf("形および分解時間が5分を超過した場合")),
    ScoreBand("優れた演武", "9.0 ～ 9.8", listOf("分解演武における頭部への軽微な接触")),
    ScoreBand("完璧な演武", "10.0", listOf("主審の指示に従った模範的な演武", "基本形および指定形に大きな変更がない場合")),
)

private val deductions = listOf(
    Deduction("バランスを崩した場合", "-0.2", "-0.4"),
    Deduction("演武中に帯が緩み、臀部まで下がった場合", "-0.2", "-0.4"),
    Deduction("時間の浪費（不必要な歩行、過度の礼、開始までに時間を要した場合等）", "-0.2", "-0.4"),
    Deduction("不正確な動き、または不十分な立ち方", "-0.4", "-0.6"),
    Deduction("不十分なブロックや突き", "-0.4", "-0.6"),
    Deduction("非同時性の動き（個人競技において技の終了前に次の技を出す等）", "-0.4", "-0.6"),
    Deduction("非同時性の動き（団体競技において動作が一致しない等）", "-0.8", "-1.0"),
    Deduction("足踏み、胸や腕の叩打、聞こえるほどの大きな呼吸音などによる意図的な発声", "-0.8", "-1.0"),
    Deduction("分解演武中の技のコントロール不足により負傷を招いた場合", "-0.8", "-1.0"),
    Deduction("基本形または指定形に変更があった場合", "-0.8", "-1.2"),
)

@Composable
fun ScoringStandardDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text("形競技採点基準", style = MaterialTheme.typography.titleLarge)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("① 得点配分の指針", style = MaterialTheme.typography.titleMedium)
                    TwoColumnRow("評価", "点数", header = true)
                    scoreBands.forEach { band ->
                        TwoColumnRow(band.label, band.score, header = false)
                    }
                    Text(
                        "評価基準",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    scoreBands.forEach { band ->
                        Text(
                            "${band.label}（${band.score}）",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        band.notes.forEach { note ->
                            Text("・ $note", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text(
                        "② 減点基準",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ThreeColumnRow("内容", "わずか（又は1回）", "大きい（又は複数回）", header = true)
                    deductions.forEach { row ->
                        ThreeColumnRow(row.detail, row.slight, row.large, header = false)
                    }
                    Text(
                        "全ての合算により点数は決定する。",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = buildAnnotatedString {
                            append("難易度の高い形を選択するより\"")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("自分が最も完成度高く演武できる形を選ぶ")
                            }
                            append("\"の選択が勝つためには必要です。")
                        },
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "一方で、難易度の高い形は、次の内容を見せやすいため高得点につながりやすい。",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    listOf(
                        "高い柔軟性",
                        "高度なバランス",
                        "複雑なリズム変化",
                        "高速な回転や跳躍",
                        "技術レベルの表現",
                    ).forEach { line ->
                        Text("・ $line", style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        "これは、一般的な考え方を記載したもので、各大会ごとの競技規定があるため参考としてください。",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("閉じる") }
                }
            }
        }
    }
}

@Composable
private fun TwoColumnRow(left: String, right: String, header: Boolean) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(
                text = left,
                modifier = Modifier.weight(0.62f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = right,
                modifier = Modifier.weight(0.38f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.End,
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun ThreeColumnRow(detail: String, slight: String, large: String, header: Boolean) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(
                text = detail,
                modifier = Modifier.weight(0.56f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = slight,
                modifier = Modifier.weight(0.22f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.End,
            )
            Text(
                text = large,
                modifier = Modifier.weight(0.22f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.End,
            )
        }
        HorizontalDivider()
    }
}
