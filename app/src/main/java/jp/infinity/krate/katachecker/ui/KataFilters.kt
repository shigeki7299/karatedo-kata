package jp.infinity.krate.katachecker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import jp.infinity.krate.katachecker.kata.KataCatalog
import jp.infinity.krate.katachecker.kata.KataCategory
import jp.infinity.krate.katachecker.kata.KataEntry
import jp.infinity.krate.katachecker.kata.KataStyle
@Composable
fun KataChoiceMenu(
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyLabel: String = "なし",
    fillWidth: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(
            onClick = { open = true },
            modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = value.ifBlank { emptyLabel }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (options.isEmpty()) {
                DropdownMenuItem(text = { Text(emptyLabel) }, onClick = { open = false }, enabled = false)
            } else {
                options.forEach { (id, label) ->
                    DropdownMenuItem(
                        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            onSelect(id)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

fun kataFileLabel(kata: KataEntry): String = "（${kata.style.label}）${kata.name}"

fun kataOptionLabel(kata: KataEntry, hasModel: Boolean): String {
    val mark = if (hasModel) "〇" else ""
    return mark + kataFileLabel(kata)
}

fun kataMenuEntries(): List<KataEntry> {
    return KataCatalog.all.sortedWith(
        compareBy(
            { KataStyle.entries.indexOf(it.style) },
            { KataCategory.entries.indexOf(it.category) },
        ),
    )
}
