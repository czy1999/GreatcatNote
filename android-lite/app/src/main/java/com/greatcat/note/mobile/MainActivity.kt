package com.greatcat.note.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GreatcatNoteApp() }
    }
}

private val Pine = Color(0xFF18352E)
private val Leaf = Color(0xFF3F6856)
private val Canvas = Color(0xFFF7F3E9)
private val Paper = Color(0xFFFFFDF8)
private val Amber = Color(0xFFE9A94B)
private val Ink = Color(0xFF202A27)
private val Muted = Color(0xFF6C7772)
private val Mist = Color(0xFFE4ECE5)
private val Coral = Color(0xFFB94B3A)

private enum class LibraryFilter { ALL, MARKDOWN, PDF }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GreatcatNoteApp(viewModel: GreatcatViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var settingsVisible by remember { mutableStateOf(false) }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::openExternal)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importDocument)
    }

    GreatcatTheme {
        when {
            state.reader != null -> ReaderScreen(state.reader!!, viewModel::closeReader)
            !state.hasToken -> ConnectScreen(state, viewModel::connect)
            else -> HomeScreen(
                state = state,
                onSync = viewModel::sync,
                onRefresh = viewModel::refreshFiles,
                onOpen = { openLauncher.launch(arrayOf("text/*", "application/pdf", "application/octet-stream")) },
                onImport = { importLauncher.launch(arrayOf("text/*", "application/pdf", "application/octet-stream")) },
                onSettings = { settingsVisible = true },
                onFile = viewModel::openVaultFile,
            )
        }

        if (settingsVisible && state.hasToken) {
            TokenSheet(
                onDismiss = { settingsVisible = false },
                onSave = { token ->
                    viewModel.saveToken(token)
                    settingsVisible = false
                },
                onClearToken = {
                    viewModel.clearToken()
                    settingsVisible = false
                },
            )
        }
    }
}

@Composable
private fun GreatcatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Pine,
            onPrimary = Color.White,
            secondary = Leaf,
            tertiary = Amber,
            background = Canvas,
            surface = Paper,
            onSurface = Ink,
            error = Coral,
        ),
        typography = MaterialTheme.typography.copy(
            displaySmall = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Serif),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
        ),
        content = content,
    )
}

@Composable
private fun ConnectScreen(state: AppState, onConnect: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    Scaffold(containerColor = Canvas) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).background(
                Brush.verticalGradient(listOf(Color(0xFFDCE7DE), Canvas, Paper)),
            ).padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(shape = RoundedCornerShape(22.dp), color = Pine, modifier = Modifier.size(68.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("G", color = Amber, fontFamily = FontFamily.Serif, fontSize = 34.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("连接我的知识库", style = MaterialTheme.typography.displaySmall, color = Pine)
            Spacer(Modifier.height(8.dp))
            Text("仓库信息已经为你配置好，只需输入一次 GitHub 私有令牌。", color = Muted, lineHeight = 22.sp)
            Spacer(Modifier.height(24.dp))
            RepositoryIdentity()
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("GitHub 私有令牌") },
                placeholder = { Text("github_pat_...") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            state.error?.let {
                Text(it, color = Coral, modifier = Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onConnect(token) },
                enabled = token.isNotBlank() && !state.busy,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pine),
            ) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("连接并同步", fontWeight = FontWeight.Bold)
            }
            Text(
                "令牌使用 Android Keystore 加密，仅保存在这台设备。",
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                color = Muted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun RepositoryIdentity() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Mist, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Text("GC", color = Pine, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("czy1999 / Greatcat", fontWeight = FontWeight.Bold)
                Text("私有仓库 · master", color = Muted, fontSize = 12.sp)
            }
            Text("专属", color = Leaf, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HomeScreen(
    state: AppState,
    onSync: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onFile: (VaultFile) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    val visibleFiles = remember(state.files, query, filter) {
        state.files.filter { file ->
            (query.isBlank() || file.relativePath.contains(query, ignoreCase = true)) &&
                (filter == LibraryFilter.ALL || filter.name == file.kind.name)
        }
    }

    Scaffold(containerColor = Canvas) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 36.dp),
        ) {
            item {
                LibraryHeader(state, onSync, onSettings)
                ActionRow(onOpen, onImport)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    placeholder = { Text("搜索标题或路径") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = filter == LibraryFilter.ALL, onClick = { filter = LibraryFilter.ALL }, label = { Text("全部") })
                    FilterChip(selected = filter == LibraryFilter.MARKDOWN, onClick = { filter = LibraryFilter.MARKDOWN }, label = { Text("Markdown") })
                    FilterChip(selected = filter == LibraryFilter.PDF, onClick = { filter = LibraryFilter.PDF }, label = { Text("PDF") })
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("最近内容", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${visibleFiles.size} 项", color = Muted, fontSize = 12.sp)
                    TextButton(onClick = onRefresh) { Text("刷新") }
                }
            }
            if (visibleFiles.isEmpty()) item { EmptyLibrary(query.isNotBlank()) }
            else items(visibleFiles, key = { it.relativePath }) { file -> FileCard(file, onFile) }
        }
    }
}

@Composable
private fun LibraryHeader(state: AppState, onSync: () -> Unit, onSettings: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Pine, Color(0xFF2C5548), Color(0xFF6E643C))),
        ).padding(horizontal = 22.dp, vertical = 24.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("GREATCAT NOTE", color = Amber, fontSize = 11.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSettings) { Text("账户", color = Color.White) }
            }
            Text("我的知识库", color = Color.White, style = MaterialTheme.typography.displaySmall)
            Text("czy1999/Greatcat · master", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(if (state.error == null) Amber else Color(0xFFFF8A75), CircleShape))
                        Spacer(Modifier.width(9.dp))
                        Text(
                            state.error ?: state.status,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = onSync,
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), color = Ink, strokeWidth = 2.dp)
                    else Text("同步", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(onOpen: () -> Unit, onImport: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
            Text("打开本机文件")
        }
        Button(onClick = onImport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
            Text("导入知识库")
        }
    }
}

@Composable
private fun FileCard(file: VaultFile, onFile: (VaultFile) -> Unit) {
    val folder = file.relativePath.substringBeforeLast('/', "知识库")
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).clickable { onFile(file) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).background(
                    if (file.kind == FileKind.PDF) Color(0xFFFFE3DC) else Mist,
                    RoundedCornerShape(14.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (file.kind == FileKind.PDF) "PDF" else "MD", color = if (file.kind == FileKind.PDF) Coral else Pine, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(file.file.nameWithoutExtension, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(folder, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(file.modifiedAt)), color = Muted, fontSize = 11.sp)
                Text("打开", color = Leaf, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyLibrary(searching: Boolean) {
    Column(Modifier.fillMaxWidth().padding(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (searching) "没有匹配内容" else "知识库还是空的", style = MaterialTheme.typography.headlineMedium, color = Pine)
        Spacer(Modifier.height(8.dp))
        Text(if (searching) "换个关键词试试。" else "点击同步，从 GitHub 获取 Markdown 和 PDF。", color = Muted)
    }
}

@Composable
private fun ReaderScreen(target: ReaderTarget, onBack: () -> Unit) {
    Scaffold(containerColor = Paper) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().background(Paper).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("返回") }
                Column(Modifier.weight(1f)) {
                    Text(target.name.substringBeforeLast('.'), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (target.kind == FileKind.PDF) "PDF 阅读" else "Markdown 阅读", color = Muted, fontSize = 11.sp)
                }
                Surface(color = if (target.kind == FileKind.PDF) Color(0xFFFFE3DC) else Mist, shape = RoundedCornerShape(10.dp)) {
                    Text(if (target.kind == FileKind.PDF) "PDF" else "MD", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
            Reader(target, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenSheet(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClearToken: () -> Unit,
) {
    var token by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 32.dp)) {
            Text("专属仓库", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            RepositoryIdentity()
            Spacer(Modifier.height(16.dp))
            Text("更新私有令牌", fontWeight = FontWeight.Bold)
            Text("仓库、分支和用户名已固定，无需重复配置。", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("新令牌") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { onSave(token) },
                enabled = token.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("保存新令牌") }
            TextButton(onClick = onClearToken, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("退出并清除本机令牌", color = Coral)
            }
        }
    }
}
