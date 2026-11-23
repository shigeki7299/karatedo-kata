package jp.infinity.krate.katachecker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val TECHNIQUE_PDF_URL =
    "https://www.nipponbudokan.or.jp/pdf/shidosho/karatedo/Karatedo-03.pdf"

private val pdfLock = Mutex()

@Composable
fun BasicTechniqueDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val file = remember { File(context.cacheDir, "karatedo-03.pdf") }
    var pageCount by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(file) {
        val ready = withContext(Dispatchers.IO) { ensureTechniquePdf(file) }
        if (!ready) {
            error = "資料を表示できませんでした"
            loading = false
            return@LaunchedEffect
        }
        pageCount = withContext(Dispatchers.IO) { pdfPageCount(file) }
        if (pageCount <= 0) error = "資料を表示できませんでした"
        loading = false
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("基本動作と基本技術", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "日本武道館　少年少女武道指導書　Ⅲ章",
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceIn(1, 1280)
                    when {
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(error.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        }
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(pageCount) { index ->
                                PdfPage(file = file, index = index, widthPx = widthPx)
                            }
                        }
                    }
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
private fun PdfPage(file: File, index: Int, widthPx: Int) {
    var bitmap by remember(file, index, widthPx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file, index, widthPx) {
        bitmap = withContext(Dispatchers.IO) {
            pdfLock.withLock { renderPdfPage(file, index, widthPx) }
        }
    }
    val image = bitmap
    if (image == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "${index + 1}ページ",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    }
}

private fun ensureTechniquePdf(file: File): Boolean {
    if (file.isFile && file.length() > 10_000L) return true
    val part = File(file.parentFile, "${file.name}.part")
    return runCatching {
        val connection = (URL(TECHNIQUE_PDF_URL).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "KarateKata")
        }
        try {
            if (connection.responseCode !in 200..299) return false
            connection.inputStream.use { input ->
                part.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (part.length() < 10_000L) {
            part.delete()
            return false
        }
        if (file.exists()) file.delete()
        part.copyTo(file, overwrite = true)
        part.delete()
        file.isFile && file.length() > 10_000L
    }.getOrElse {
        part.delete()
        false
    }
}

private fun pdfPageCount(file: File): Int {
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { it.pageCount }
    }
}

private fun renderPdfPage(file: File, index: Int, widthPx: Int): Bitmap? {
    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(index).use { page ->
                    val heightPx = (widthPx.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    Canvas(bitmap).drawColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }.getOrNull()
}
