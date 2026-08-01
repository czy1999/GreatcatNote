package com.greatcat.note.mobile

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

enum class FileKind { MARKDOWN, PDF }

data class VaultFile(
    val file: File,
    val relativePath: String,
    val kind: FileKind,
    val modifiedAt: Long,
)

sealed interface ReaderTarget {
    val name: String
    val kind: FileKind
    val key: String

    data class Vault(val value: VaultFile) : ReaderTarget {
        override val name = value.file.name
        override val kind = value.kind
        override val key = value.file.absolutePath
    }

    data class External(val uri: Uri, override val name: String, override val kind: FileKind) : ReaderTarget {
        override val key = uri.toString()
    }
}

data class AppState(
    val files: List<VaultFile> = emptyList(),
    val settings: RepoSettings = RepoSettings(),
    val hasToken: Boolean = false,
    val reader: ReaderTarget? = null,
    val busy: Boolean = false,
    val status: String = "配置 Git 仓库后即可开始增量同步",
    val error: String? = null,
)

class GreatcatViewModel(application: Application) : AndroidViewModel(application) {
    private val secureSettings = SecureSettings(application)
    private val vaultDirectory = File(application.filesDir, "vault")
    private val gitSync = GitSync(vaultDirectory)
    private val mutableState = MutableStateFlow(
        AppState(settings = secureSettings.load(), hasToken = secureSettings.hasToken()),
    )
    val state: StateFlow<AppState> = mutableState.asStateFlow()

    init {
        refreshFiles()
    }

    fun saveSettings(settings: RepoSettings, token: String) {
        runCatching {
            if (settings.remoteUrl.isNotBlank()) validateRemoteUrl(settings.remoteUrl)
            validateBranch(settings.branch)
            secureSettings.save(settings, token)
        }.onSuccess {
            mutableState.update {
                it.copy(settings = secureSettings.load(), hasToken = secureSettings.hasToken(), error = null)
            }
        }.onFailure(::showError)
    }

    fun clearToken() {
        secureSettings.clearToken()
        mutableState.update { it.copy(hasToken = false, status = "Git 令牌已清除") }
    }

    fun sync() {
        val settings = state.value.settings
        if (settings.remoteUrl.isBlank()) {
            showError(IllegalArgumentException("请先配置 Git 仓库"))
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, status = "正在安全同步...", error = null) }
            runCatching { gitSync.sync(settings, secureSettings.token()) }
                .onSuccess { report ->
                    refreshFilesNow()
                    mutableState.update { it.copy(busy = false, status = report.message) }
                }
                .onFailure { error ->
                    mutableState.update { it.copy(busy = false, error = error.readableMessage()) }
                }
        }
    }

    fun importDocument(uri: Uri) {
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, status = "正在导入文件...", error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val name = displayName(uri)
                    require(fileKind(name) != null) { "仅支持 Markdown 和 PDF 文件" }
                    val imports = File(vaultDirectory, "imports").apply { mkdirs() }
                    val destination = uniqueFile(imports, File(name).name)
                    getApplication<Application>().contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "无法读取所选文件" }
                        destination.outputStream().use(input::copyTo)
                    }
                    destination
                }
            }.onSuccess { destination ->
                refreshFilesNow()
                mutableState.update {
                    it.copy(busy = false, status = "已导入 ${destination.name}，下次同步会推送到 Git")
                }
            }.onFailure { error ->
                mutableState.update { it.copy(busy = false, error = error.readableMessage()) }
            }
        }
    }

    fun openExternal(uri: Uri) {
        runCatching {
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val name = displayName(uri)
            val kind = requireNotNull(fileKind(name)) { "仅支持 Markdown 和 PDF 文件" }
            ReaderTarget.External(uri, name, kind)
        }.onSuccess { target ->
            mutableState.update { it.copy(reader = target, error = null) }
        }.onFailure(::showError)
    }

    fun openVaultFile(file: VaultFile) {
        mutableState.update { it.copy(reader = ReaderTarget.Vault(file), error = null) }
    }

    fun closeReader() {
        mutableState.update { it.copy(reader = null) }
    }

    fun refreshFiles() {
        viewModelScope.launch { refreshFilesNow() }
    }

    private suspend fun refreshFilesNow() {
        val files = withContext(Dispatchers.IO) {
            if (!vaultDirectory.isDirectory) return@withContext emptyList()
            vaultDirectory.walkTopDown()
                .onEnter { it.name != ".git" }
                .filter { it.isFile }
                .mapNotNull { file ->
                    val kind = fileKind(file.name) ?: return@mapNotNull null
                    VaultFile(
                        file = file,
                        relativePath = file.relativeTo(vaultDirectory).invariantSeparatorsPath,
                        kind = kind,
                        modifiedAt = file.lastModified(),
                    )
                }
                .sortedWith(compareBy<VaultFile> { it.relativePath.lowercase() })
                .toList()
        }
        mutableState.update { it.copy(files = files) }
    }

    private fun displayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment ?: "document-${Instant.now().epochSecond}"
    }

    private fun uniqueFile(directory: File, name: String): File {
        val direct = File(directory, name)
        if (!direct.exists()) return direct
        val stem = direct.nameWithoutExtension
        val extension = direct.extension.let { if (it.isBlank()) "" else ".$it" }
        return generateSequence(2) { it + 1 }
            .map { File(directory, "$stem-$it$extension") }
            .first { !it.exists() }
    }

    private fun showError(error: Throwable) {
        mutableState.update { it.copy(error = error.readableMessage()) }
    }
}

internal fun fileKind(name: String): FileKind? = when (name.substringAfterLast('.', "").lowercase()) {
    "md", "markdown" -> FileKind.MARKDOWN
    "pdf" -> FileKind.PDF
    else -> null
}

private fun Throwable.readableMessage(): String = message?.takeIf { it.isNotBlank() }
    ?: cause?.message?.takeIf { it.isNotBlank() }
    ?: "操作失败，请稍后重试"
