package com.greatcat.note.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.method.LinkMovementMethod
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Reader(target: ReaderTarget, modifier: Modifier = Modifier) {
    when (target.kind) {
        FileKind.MARKDOWN -> MarkdownReader(target, modifier)
        FileKind.PDF -> PdfReader(target, modifier)
    }
}

@Composable
private fun MarkdownReader(target: ReaderTarget, modifier: Modifier) {
    val context = LocalContext.current
    val content by produceState<Result<String>?>(initialValue = null, target.key) {
        value = runCatching { readMarkdown(context, target) }
    }
    val markwon = remember(context) { Markwon.create(context) }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFFFFCF5))) {
        when (val result = content) {
            null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            else -> result.fold(
                onSuccess = { markdown ->
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            ScrollView(viewContext).apply {
                                isFillViewport = true
                                setBackgroundColor(AndroidColor.rgb(255, 253, 248))
                                addView(TextView(viewContext).apply {
                                    setPadding(52, 42, 52, 120)
                                    textSize = 19f
                                    setTextColor(AndroidColor.rgb(32, 42, 39))
                                    setLineSpacing(10f, 1.12f)
                                    includeFontPadding = false
                                    setTextIsSelectable(true)
                                    movementMethod = LinkMovementMethod.getInstance()
                                })
                            }
                        },
                        update = { scroll -> markwon.setMarkdown(scroll.getChildAt(0) as TextView, markdown) },
                    )
                },
                onFailure = { ReaderError(it.message ?: "无法读取 Markdown") },
            )
        }
    }
}

@Composable
private fun PdfReader(target: ReaderTarget, modifier: Modifier) {
    val context = LocalContext.current
    var pageIndex by remember(target.key) { mutableIntStateOf(0) }
    val page by produceState<PdfPageState>(PdfPageState.Loading, target.key, pageIndex) {
        value = withContext(Dispatchers.IO) {
            runCatching { renderPdfPage(context, target, pageIndex) }
                .fold(PdfPageState::Ready) { PdfPageState.Failed(it.message ?: "无法读取 PDF") }
        }
    }

    Column(modifier.fillMaxSize().background(Color(0xFFEAE5DA))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFFFFCF5)).padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) }, enabled = pageIndex > 0) {
                Text("上一页")
            }
            Text(
                text = (page as? PdfPageState.Ready)?.let { "${it.page.index + 1} / ${it.page.count}" } ?: "PDF",
                style = MaterialTheme.typography.labelLarge,
            )
            Button(
                onClick = { pageIndex += 1 },
                enabled = (page as? PdfPageState.Ready)?.let { pageIndex + 1 < it.page.count } == true,
            ) {
                Text("下一页")
            }
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val current = page) {
                PdfPageState.Loading -> CircularProgressIndicator()
                is PdfPageState.Failed -> ReaderError(current.message)
                is PdfPageState.Ready -> {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    ) {
                        Image(
                            bitmap = current.page.bitmap.asImageBitmap(),
                            contentDescription = "PDF 第 ${current.page.index + 1} 页",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderError(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}

private suspend fun readMarkdown(context: Context, target: ReaderTarget): String = withContext(Dispatchers.IO) {
    val bytes = when (target) {
        is ReaderTarget.Vault -> {
            require(target.value.file.length() <= MAX_MARKDOWN_BYTES) { "Markdown 文件超过 10 MB" }
            target.value.file.readBytes()
        }
        is ReaderTarget.External -> context.contentResolver.openInputStream(target.uri).use { input ->
            requireNotNull(input) { "无法打开文件" }
            input.readNBytes((MAX_MARKDOWN_BYTES + 1).toInt())
        }
    }
    require(bytes.size <= MAX_MARKDOWN_BYTES) { "Markdown 文件超过 10 MB" }
    String(bytes, Charsets.UTF_8)
}

private fun renderPdfPage(context: Context, target: ReaderTarget, requestedPage: Int): RenderedPdfPage {
    openPdfDescriptor(context, target).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            require(renderer.pageCount > 0) { "PDF 没有可显示的页面" }
            val index = requestedPage.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(index).use { page ->
                val width = 1400
                val height = (width.toLong() * page.height / page.width).coerceAtMost(4096).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return RenderedPdfPage(bitmap, index, renderer.pageCount)
            }
        }
    }
}

private fun openPdfDescriptor(context: Context, target: ReaderTarget): ParcelFileDescriptor = when (target) {
    is ReaderTarget.Vault -> ParcelFileDescriptor.open(target.value.file, ParcelFileDescriptor.MODE_READ_ONLY)
    is ReaderTarget.External -> requireNotNull(context.contentResolver.openFileDescriptor(target.uri, "r")) {
        "无法打开 PDF"
    }
}

private data class RenderedPdfPage(val bitmap: Bitmap, val index: Int, val count: Int)

private sealed interface PdfPageState {
    data object Loading : PdfPageState
    data class Ready(val page: RenderedPdfPage) : PdfPageState
    data class Failed(val message: String) : PdfPageState
}

private const val MAX_MARKDOWN_BYTES = 10L * 1024 * 1024
