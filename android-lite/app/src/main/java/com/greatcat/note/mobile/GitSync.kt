package com.greatcat.note.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.net.URI

internal fun validateRemoteUrl(value: String): String {
    val uri = runCatching { URI(value.trim()) }.getOrElse { throw IllegalArgumentException("仓库地址无效") }
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) {
        "目前仅支持不含账号或令牌的 HTTPS 仓库地址"
    }
    return uri.toString()
}

internal fun validateBranch(value: String): String {
    val branch = value.trim()
    require(branch.matches(Regex("[A-Za-z0-9._/-]+"))) { "分支名称无效" }
    require(!branch.startsWith('-') && !branch.startsWith('/') && !branch.endsWith('/')) { "分支名称无效" }
    require(".." !in branch && "@{" !in branch && "//" !in branch) { "分支名称无效" }
    return branch
}

data class SyncReport(val message: String, val changed: Boolean)

class GitSync(private val vaultDirectory: File) {
    suspend fun sync(settings: RepoSettings, token: String): SyncReport = withContext(Dispatchers.IO) {
        val remote = validateRemoteUrl(settings.remoteUrl)
        val branch = validateBranch(settings.branch)
        val credentials = credentials(settings.username, token)

        if (!File(vaultDirectory, ".git").isDirectory) {
            require(vaultDirectory.listFiles().isNullOrEmpty()) {
                "本地仓库目录已有文件，无法安全克隆"
            }
            vaultDirectory.parentFile?.mkdirs()
            clone(remote, branch, credentials)
            return@withContext SyncReport("首次克隆完成", true)
        }

        Git.open(vaultDirectory).use { git ->
            require(git.repository.branch == branch) {
                "本地分支是 ${git.repository.branch}，配置分支是 $branch"
            }
            val committed = commitLocalChanges(git, settings.username)
            pullSafely(git, branch, credentials)
            val pushed = push(git, credentials)
            SyncReport(
                message = if (committed || pushed) "增量同步完成，本地与远端已对齐" else "已是最新状态",
                changed = committed || pushed,
            )
        }
    }

    private fun clone(remote: String, branch: String, credentials: CredentialsProvider?) {
        val command = Git.cloneRepository()
            .setURI(remote)
            .setBranch("refs/heads/$branch")
            .setDirectory(vaultDirectory)
        credentials?.let(command::setCredentialsProvider)
        command.call().close()
    }

    private fun commitLocalChanges(git: Git, username: String): Boolean {
        if (git.status().call().isClean) return false
        git.add().addFilepattern(".").call()
        git.add().addFilepattern(".").setUpdate(true).call()
        val author = username.ifBlank { "GreatcatNote Mobile" }
        git.commit()
            .setMessage("sync: mobile changes")
            .setAuthor(author, "greatcatnote-mobile@localhost")
            .call()
        return true
    }

    private fun pullSafely(git: Git, branch: String, credentials: CredentialsProvider?) {
        try {
            val command = git.pull().setRebase(true).setRemoteBranchName(branch)
            credentials?.let(command::setCredentialsProvider)
            require(command.call().isSuccessful) { "拉取产生冲突，已停止同步，请先在电脑端处理" }
        } catch (error: Exception) {
            if (git.repository.repositoryState != RepositoryState.SAFE) {
                runCatching { git.rebase().setOperation(RebaseCommand.Operation.ABORT).call() }
            }
            throw error
        }
    }

    private fun push(git: Git, credentials: CredentialsProvider?): Boolean {
        val command = git.push()
        credentials?.let(command::setCredentialsProvider)
        val updates = command.call().flatMap { it.remoteUpdates }
        val rejected = updates.firstOrNull {
            it.status !in setOf(RemoteRefUpdate.Status.OK, RemoteRefUpdate.Status.UP_TO_DATE)
        }
        require(rejected == null) { "推送失败：${rejected?.status}" }
        return updates.any { it.status == RemoteRefUpdate.Status.OK }
    }

    private fun credentials(username: String, token: String): CredentialsProvider? {
        if (token.isBlank()) return null
        return UsernamePasswordCredentialsProvider(username.ifBlank { "git" }, token)
    }
}
