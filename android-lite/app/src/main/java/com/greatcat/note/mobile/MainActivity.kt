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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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

private val Forest = Color(0xFF163A36)
private val Moss = Color(0xFF486B58)
private val Paper = Color(0xFFFFFCF5)
private val Sand = Color(0xFFF3B562)
private val Ink = Color(0xFF222D2A)

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
        state.reader?.let { ReaderScreen(it, viewModel::closeReader) } ?: HomeScreen(
            state = state,
            onSync = viewModel::sync,
            onRefresh = viewModel::refreshFiles,
            onOpen = { openLauncher.launch(arrayOf("text/*", "application/pdf", "application/octet-stream")) },
            onImport = { importLauncher.launch(arrayOf("text/*", "application/pdf", "application/octet-stream")) },
            onSettings = { settingsVisible = true },
            onFile = viewModel::openVaultFile,
        )

        if (settingsVisible) {
            SettingsSheet(
                current = state.settings,
                hasToken = state.hasToken,
                onDismiss = { settingsVisible = false },
                onSave = { settings, token ->
                    viewModel.saveSettings(settings, token)
                    settingsVisible = false
                },
                onClearToken = viewModel::clearToken,
            )
        }
    }
}

@Composable
private fun GreatcatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Forest,
            onPrimary = Color.White,
            secondary = Moss,
            tertiary = Sand,
            background = Paper,
            surface = Paper,
            onSurface = Ink,
        ),
        typography = MaterialTheme.typography.copy(
            displaySmall = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Serif),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif),
        ),
        content = content,
    )
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
    val visibleFiles = remember(state.files, query) {
        state.files.filter { query.isBlank() || it.relativePath.contains(query, ignoreCase = true) }
    }

    Scaffold(containerColor = Paper) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Header(state.busy, onSync, onSettings)
                QuickActions(onOpen, onImport, onRefresh)
                StatusCard(state)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    label = { Text("搜索 Markdown 或 PDF") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("我的资料", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${visibleFiles.size} 个文件", color = Moss)
                }
            }
            if (visibleFiles.isEmpty()) item { EmptyLibrary() }
            else items(visibleFiles, key = { it.relativePath }) { file -> FileRow(file, onFile) }
        }
    }
}

@Composable
private fun Header(busy: Boolean, onSync: () -> Unit, onSettings: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Forest, Color(0xFF2E5A4A), Color(0xFF8B7040))),
        ).padding(horizontal = 22.dp, vertical = 28.dp),
    ) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Text("GREATCAT", color = Sand, fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Text("随身知识库", color = Color.White, style = MaterialTheme.typography.displaySmall)
            Text("Markdown · PDF · Git", color = Color.White.copy(alpha = 0.75f))
        }
        Column(Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.End) {
            Button(
                onClick = onSync,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Sand, contentColor = Ink),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("立即同步", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onSettings) { Text("仓库设置", color = Color.White) }
        }
    }
}

@Composable
private fun QuickActions(onOpen: () -> Unit, onImport: () -> Unit, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("打开文件") }
        OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("导入仓库") }
        OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) { Text("刷新") }
    }
}

@Composable
private fun StatusCard(state: AppState) {
    val isError = state.error != null
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = if (isError) Color(0xFFFFE8E1) else Color(0xFFE8F0E8)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(if (isError) Color(0xFFB33A2B) else Moss, CircleShape))
            Spacer(Modifier.width(12.dp))
            Text(state.error ?: state.status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FileRow(file: VaultFile, onFile: (VaultFile) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onFile(file) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).background(
                if (file.kind == FileKind.PDF) Color(0xFFFFE0D8) else Color(0xFFDDEBE0),
                RoundedCornerShape(14.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (file.kind == FileKind.PDF) "PDF" else "MD", fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(file.file.nameWithoutExtension, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(file.relativePath, color = Moss, fontSize = 12.sp, maxLines = 1)
        }
        Text(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(file.modifiedAt)), color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun EmptyLibrary() {
    Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("还没有资料", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("配置 Git 仓库并同步，或直接打开手机里的文件。", color = Moss)
    }
}

@Composable
private fun ReaderScreen(target: ReaderTarget, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(target.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Reader(target, Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    current: RepoSettings,
    hasToken: Boolean,
    onDismiss: () -> Unit,
    onSave: (RepoSettings, String) -> Unit,
    onClearToken: () -> Unit,
) {
    var remote by remember(current) { mutableStateOf(current.remoteUrl) }
    var branch by remember(current) { mutableStateOf(current.branch) }
    var username by remember(current) { mutableStateOf(current.username) }
    var token by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 32.dp)) {
            Text("Git 仓库", style = MaterialTheme.typography.headlineMedium)
            Text("使用 HTTPS 地址。令牌由 Android Keystore 加密保存在本机。", color = Moss)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(remote, { remote = it }, Modifier.fillMaxWidth(), label = { Text("仓库地址") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(branch, { branch = it }, Modifier.weight(1f), label = { Text("分支") }, singleLine = true)
                OutlinedTextField(username, { username = it }, Modifier.weight(1f), label = { Text("用户名") }, singleLine = true)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (hasToken) "新令牌（留空则保留）" else "访问令牌") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onSave(RepoSettings(remote, branch, username), token) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存设置") }
            if (hasToken) {
                TextButton(onClick = onClearToken, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("清除本机令牌", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
