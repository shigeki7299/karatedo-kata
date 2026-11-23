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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val blocks = remember {
        // assets/help.md はリポジトリ直下の README.md と同じ本文です。
        val markdown = context.assets.open("help.md").bufferedReader().use { it.readText() }
        parseHelp(markdown)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp)) {
                Text("HELP", style = MaterialTheme.typography.titleLarge)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp, bottom = 8.dp),
                ) {
                    blocks.forEach { block -> HelpBlockView(block) }
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
private fun HelpBlockView(block: HelpBlock) {
    when (block) {
        is HelpBlock.Heading -> Text(
            text = block.text,
            modifier = Modifier.padding(top = if (block.level == 1) 4.dp else 16.dp, bottom = 6.dp),
            style = if (block.level == 1) {
                MaterialTheme.typography.headlineSmall
            } else {
                MaterialTheme.typography.titleMedium
            },
            fontWeight = FontWeight.Bold,
        )
        is HelpBlock.Paragraph -> Text(
            text = block.text,
            modifier = Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        is HelpBlock.Bullet -> Text(
            text = "・ ${block.text}",
            modifier = Modifier.padding(bottom = 4.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        is HelpBlock.Code -> Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = block.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
        is HelpBlock.Table -> Column(modifier = Modifier.padding(bottom = 8.dp)) {
            block.rows.forEachIndexed { index, cells ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        text = cells.getOrElse(0) { "" },
                        modifier = Modifier.weight(0.34f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        text = cells.getOrElse(1) { "" },
                        modifier = Modifier.weight(0.66f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

private sealed interface HelpBlock {
    data class Heading(val level: Int, val text: String) : HelpBlock
    data class Paragraph(val text: String) : HelpBlock
    data class Bullet(val text: String) : HelpBlock
    data class Table(val rows: List<List<String>>) : HelpBlock
    data class Code(val text: String) : HelpBlock
}

private fun parseHelp(markdown: String): List<HelpBlock> {
    val blocks = mutableListOf<HelpBlock>()
    val lines = markdown.replace("\r\n", "\n").lines()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        when {
            line.isBlank() -> index++
            line.startsWith("```") -> {
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].startsWith("```")) {
                    code += lines[index]
                    index++
                }
                if (index < lines.size) index++
                blocks += HelpBlock.Code(code.joinToString("\n"))
            }
            line.startsWith("|") -> {
                val rows = mutableListOf<List<String>>()
                while (index < lines.size && lines[index].startsWith("|")) {
                    val cells = lines[index].trim().trim('|').split('|').map { inline(it.trim()) }
                    val separator = cells.all { cell -> cell.isNotEmpty() && cell.all { it == '-' || it == ':' || it == ' ' } }
                    if (!separator) rows += cells
                    index++
                }
                if (rows.isNotEmpty()) blocks += HelpBlock.Table(rows)
            }
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                blocks += HelpBlock.Heading(level, inline(line.drop(level).trim()))
                index++
            }
            line.startsWith("- ") -> {
                blocks += HelpBlock.Bullet(inline(line.removePrefix("- ").trim()))
                index++
            }
            else -> {
                blocks += HelpBlock.Paragraph(inline(line.trim()))
                index++
            }
        }
    }
    return blocks
}

private val linkPattern = Regex("""\[([^]]+)]\(([^)]+)\)""")
private val codePattern = Regex("`([^`]+)`")

private fun inline(text: String): String {
    val linked = linkPattern.replace(text) { match ->
        "${match.groupValues[1]}（${match.groupValues[2]}）"
    }
    return codePattern.replace(linked) { it.groupValues[1] }
}
