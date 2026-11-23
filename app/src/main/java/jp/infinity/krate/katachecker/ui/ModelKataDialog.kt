package jp.infinity.krate.katachecker.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import jp.infinity.krate.katachecker.R
import jp.infinity.krate.katachecker.kata.KataEntry
import jp.infinity.krate.katachecker.kata.ModelVideoStore
import jp.infinity.krate.katachecker.player.YoutubeDownloadException
import jp.infinity.krate.katachecker.player.YoutubeDownloadFailure
import jp.infinity.krate.katachecker.player.YoutubeDownloader
import jp.infinity.krate.katachecker.player.YoutubeIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ModelKataDialog(
    store: ModelVideoStore,
    onDismiss: () -> Unit,
    onChanged: (KataEntry) -> Unit,
) {
    val context = LocalContext.current
    val entries = remember { kataMenuEntries() }
    val urls = remember {
        mutableStateMapOf<String, String>().apply {
            entries.forEach { put(it.id, store.url(it)) }
        }
    }
    val files = remember {
        mutableStateMapOf<String, String>().apply {
            entries.forEach { kata -> store.fileName(kata)?.let { put(kata.id, it) } }
        }
    }
    var savingId by remember { mutableStateOf<String?>(null) }
    var savePercent by remember { mutableIntStateOf(-1) }
    var errorId by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var pickId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        val id = pickId ?: return@rememberLauncherForActivityResult
        pickId = null
        if (picked == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                picked,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val kata = entries.first { it.id == id }
        val name = videoDisplayName(context, picked) ?: "映像"
        store.setFile(kata, picked.toString(), name)
        files[id] = name
        errorId = null
        onChanged(kata)
    }
    Dialog(
        onDismissRequest = {
            if (savingId == null) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("お手本設定", style = MaterialTheme.typography.titleLarge)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    items(entries, key = { it.id }) { kata ->
                        ModelKataRow(
                            kata = kata,
                            url = urls[kata.id].orEmpty(),
                            fileName = files[kata.id],
                            saving = savingId == kata.id,
                            percent = if (savingId == kata.id) savePercent else -1,
                            error = if (errorId == kata.id) errorText else null,
                            enabled = savingId == null || savingId == kata.id,
                            onUrl = { value ->
                                urls[kata.id] = value
                                store.setUrl(kata, value)
                            },
                            onSave = {
                                if (savingId == kata.id) {
                                    job?.cancel()
                                    return@ModelKataRow
                                }
                                val id = YoutubeIds.parse(urls[kata.id].orEmpty())
                                if (id == null) {
                                    errorId = kata.id
                                    errorText = context.getString(R.string.youtube_invalid)
                                    return@ModelKataRow
                                }
                                errorId = null
                                savingId = kata.id
                                savePercent = -1
                                job = scope.launch {
                                    try {
                                        val saved = YoutubeDownloader(context.applicationContext).download(
                                            videoId = id,
                                            displayName = modelVideoFileName(kata),
                                        ) { percent ->
                                            savePercent = percent
                                        }
                                        val name = videoDisplayName(context, saved) ?: modelVideoFileName(kata)
                                        store.setFile(kata, saved.toString(), name)
                                        files[kata.id] = name
                                        onChanged(kata)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: YoutubeDownloadException) {
                                        errorId = kata.id
                                        errorText = context.getString(
                                            when (error.failure) {
                                                YoutubeDownloadFailure.Unavailable -> R.string.youtube_save_unavailable
                                                YoutubeDownloadFailure.Failed -> R.string.youtube_save_failed
                                            },
                                        )
                                    } catch (_: Exception) {
                                        errorId = kata.id
                                        errorText = context.getString(R.string.youtube_save_failed)
                                    } finally {
                                        if (savingId == kata.id) {
                                            savingId = null
                                            savePercent = -1
                                        }
                                    }
                                }
                            },
                            onPick = {
                                pickId = kata.id
                                openFile.launch(arrayOf("video/*"))
                            },
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            job?.cancel()
                            onDismiss()
                        },
                    ) {
                        Text(if (savingId == null) "閉じる" else "中止")
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelKataRow(
    kata: KataEntry,
    url: String,
    fileName: String?,
    saving: Boolean,
    percent: Int,
    error: String?,
    enabled: Boolean,
    onUrl: (String) -> Unit,
    onSave: () -> Unit,
    onPick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = kataOptionLabel(kata, fileName != null),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = onUrl,
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = enabled && !saving,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onSave,
                enabled = enabled,
            ) {
                Text(if (saving) "中止" else "保存")
            }
        }
        if (saving) {
            LinearProgressIndicator(
                progress = { if (percent < 0) 0f else percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = fileName.orEmpty(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onPick, enabled = enabled && !saving) {
                Text("ファイル指定")
            }
        }
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HorizontalDivider()
    }
}

private fun modelVideoFileName(kata: KataEntry): String {
    val day = SimpleDateFormat("yyyyMMdd", Locale.JAPAN).format(Date())
    return "${kataFileLabel(kata)}_$day.mp4"
}
