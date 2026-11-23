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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val procedures = listOf(
    "①" to "３名以下の場合　演武数：１（メダルマッチのみ）",
    "②" to "４名の場合　演武数：２（２グループで予選、１位のみ決勝）",
    "③" to "５名の場合　演武数：２（２グループで予選、３位決定戦ある）",
    "④" to "１０名以下の場合　演武数：３（２グループで予選、決勝と３位決定線）",
    "⑤" to "２４名以下の場合　演武数：３（予選は第２ラウンドまで、メダルマッチは変化なし）",
    "⑥" to "４８名以下の場合　演武数：４（４グループで予選は第３ラウンド、メダルマッチ変化なし）",
)

@Composable
fun KataProcedureDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp)) {
                Text("形競技実施要領", style = MaterialTheme.typography.titleLarge)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    procedures.forEach { (mark, body) ->
                        Text(
                            text = "$mark　$body",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                    Text(
                        "各大会ごとの競技規定があるため、絶対的なものではありません。必ず大会ごとに確認してください。",
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
