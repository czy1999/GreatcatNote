import { useEffect, useMemo, useState } from 'react'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { convertFileSrc, invoke } from '@tauri-apps/api/core'
import './MobileApp.css'

type VaultEntry = {
  path: string
  filename: string
  title: string
  fileKind?: string
}

type GitRemoteStatus = {
  branch: string
  has_remote: boolean
  ahead: number
  behind: number
}

function isMarkdown(entry: VaultEntry | null): boolean {
  return !!entry && /\.(md|markdown)$/i.test(entry.filename)
}

function isPdf(entry: VaultEntry | null): boolean {
  return !!entry && /\.pdf$/i.test(entry.filename)
}

function noteTitle(entry: VaultEntry): string {
  return entry.title || entry.filename
}

function nowNoteName(): string {
  return `note-${new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '')}.md`
}

export default function MobileApp() {
  const [vaultPath, setVaultPath] = useState('')
  const [entries, setEntries] = useState<VaultEntry[]>([])
  const [active, setActive] = useState<VaultEntry | null>(null)
  const [content, setContent] = useState('')
  const [remoteUrl, setRemoteUrl] = useState('')
  const [syncRepo, setSyncRepo] = useState(() => window.localStorage.getItem('greatcatnote.mobile.repo') ?? '')
  const [syncBranch, setSyncBranch] = useState(() => window.localStorage.getItem('greatcatnote.mobile.branch') ?? 'main')
  const [syncToken, setSyncToken] = useState('')
  const [status, setStatus] = useState('启动中...')
  const [error, setError] = useState('')

  const visibleEntries = useMemo(
    () => entries.filter((entry) => /\.(md|markdown|pdf)$/i.test(entry.filename)),
    [entries],
  )

  async function refresh(path = vaultPath) {
    if (!path) return
    const nextEntries = await invoke<VaultEntry[]>('reload_vault', { path })
    setEntries(nextEntries)
    setStatus(`已读取 ${nextEntries.length} 个文件`)
  }

  async function openEntry(entry: VaultEntry) {
    setActive(entry)
    setError('')
    if (isMarkdown(entry)) {
      setContent(await invoke<string>('get_note_content', { path: entry.path, vaultPath }))
    } else {
      setContent('')
    }
  }

  async function saveActive() {
    if (!active || !isMarkdown(active)) return
    await invoke('save_note_content', { path: active.path, content, vaultPath })
    setStatus('已保存')
    await refresh()
  }

  async function createNote() {
    const filename = nowNoteName()
    const path = `${vaultPath}/${filename}`
    const body = `# ${filename.replace(/\\.md$/, '')}\n\n`
    await invoke('create_note_content', { path, content: body, vaultPath })
    await refresh()
    await openEntry({ path, filename, title: filename.replace(/\.md$/, ''), fileKind: 'markdown' })
  }

  async function ensureVault() {
    setError('')
    const defaultPath = await invoke<string>('get_default_vault_path')
    const exists = await invoke<boolean>('check_vault_exists', { path: defaultPath })
    if (!exists) {
      await invoke<string>('create_empty_vault', { targetPath: defaultPath })
      await invoke('create_note_content', {
        path: `${defaultPath}/welcome.md`,
        content: '# GreatcatNote Android\n\n这里是你的移动端知识库。把 Markdown 和 PDF 放进来，就可以阅读和同步。\n',
        vaultPath: defaultPath,
      })
    }
    setVaultPath(defaultPath)
    await refresh(defaultPath)
  }

  async function cloneRemote() {
    if (!remoteUrl.trim()) return
    setError('')
    const target = await invoke<string>('get_default_vault_path')
    setStatus('正在克隆...')
    const clonedPath = await invoke<string>('clone_git_repo', { url: remoteUrl.trim(), localPath: target })
    setVaultPath(clonedPath)
    await refresh(clonedPath)
  }

  function syncHeaders(): HeadersInit {
    return {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${syncToken.trim()}`,
      'X-GitHub-Api-Version': '2022-11-28',
    }
  }

  function syncManifestKey(): string {
    return `greatcatnote.mobile.manifest.${syncRepo.trim()}.${syncBranch.trim()}`
  }

  function readManifest(): Record<string, string> {
    try {
      return JSON.parse(window.localStorage.getItem(syncManifestKey()) ?? '{}') as Record<string, string>
    } catch {
      return {}
    }
  }

  function saveManifest(manifest: Record<string, string>) {
    window.localStorage.setItem(syncManifestKey(), JSON.stringify(manifest))
  }

  async function githubJson<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(`https://api.github.com/repos/${syncRepo.trim()}${path}`, {
      ...init,
      headers: { ...syncHeaders(), ...(init.headers ?? {}) },
    })
    if (!response.ok) throw new Error(`GitHub ${response.status}: ${await response.text()}`)
    return response.json() as Promise<T>
  }

  async function remoteTree(): Promise<Record<string, string>> {
    const ref = await githubJson<{ object: { sha: string } }>(`/git/ref/heads/${encodeURIComponent(syncBranch.trim())}`)
    const tree = await githubJson<{ tree: Array<{ path: string, type: string, sha: string }> }>(`/git/trees/${ref.object.sha}?recursive=1`)
    return Object.fromEntries(
      tree.tree
        .filter((item) => item.type === 'blob' && /\.(md|markdown|pdf)$/i.test(item.path))
        .map((item) => [item.path, item.sha]),
    )
  }

  async function pullGithubChanges() {
    if (!vaultPath || !syncRepo.trim() || !syncToken.trim()) return
    window.localStorage.setItem('greatcatnote.mobile.repo', syncRepo.trim())
    window.localStorage.setItem('greatcatnote.mobile.branch', syncBranch.trim())
    const manifest = readManifest()
    const tree = await remoteTree()
    let changed = 0
    for (const [path, sha] of Object.entries(tree)) {
      if (manifest[path] === sha) continue
      const blob = await githubJson<{ content: string, encoding: string }>(`/git/blobs/${sha}`)
      if (blob.encoding !== 'base64') continue
      await invoke('write_file_base64', {
        path: `${vaultPath}/${path}`,
        contentBase64: blob.content,
        vaultPath,
      })
      manifest[path] = sha
      changed += 1
    }
    saveManifest(manifest)
    setStatus(`已增量拉取 ${changed} 个文件`)
    await refresh()
  }

  function bytesToBase64(bytes: Uint8Array): string {
    let binary = ''
    for (const byte of bytes) binary += String.fromCharCode(byte)
    return btoa(binary)
  }

  async function gitBlobSha(text: string): Promise<string> {
    const body = new TextEncoder().encode(text)
    const header = new TextEncoder().encode(`blob ${body.length}\0`)
    const payload = new Uint8Array(header.length + body.length)
    payload.set(header)
    payload.set(body, header.length)
    const digest = await crypto.subtle.digest('SHA-1', payload)
    return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('')
  }

  async function pushMarkdownChanges() {
    if (!vaultPath || !syncRepo.trim() || !syncToken.trim()) return
    const tree = await remoteTree()
    const manifest = readManifest()
    let changed = 0
    for (const entry of entries.filter(isMarkdown)) {
      const relativePath = entry.path.startsWith(`${vaultPath}/`) ? entry.path.slice(vaultPath.length + 1) : entry.filename
      const markdown = await invoke<string>('get_note_content', { path: entry.path, vaultPath })
      const localSha = await gitBlobSha(markdown)
      if (tree[relativePath] === localSha) {
        manifest[relativePath] = localSha
        continue
      }
      await githubJson(`/contents/${relativePath.split('/').map(encodeURIComponent).join('/')}`, {
        method: 'PUT',
        body: JSON.stringify({
          branch: syncBranch.trim(),
          content: bytesToBase64(new TextEncoder().encode(markdown)),
          message: `Mobile sync ${relativePath}`,
          sha: tree[relativePath],
        }),
      })
      manifest[relativePath] = localSha
      changed += 1
    }
    saveManifest(manifest)
    setStatus(`已上传 ${changed} 个 Markdown 文件`)
  }

  async function syncNow() {
    setError('')
    setStatus('正在增量同步 GitHub...')
    if (syncRepo.trim() && syncToken.trim()) {
      await pullGithubChanges()
      await pushMarkdownChanges()
      setStatus('GitHub 增量同步完成')
      await refresh()
      return
    }
    if (!vaultPath) return
    await invoke('git_pull', { vaultPath })
    const changed = await invoke<unknown[]>('get_modified_files', { vaultPath, includeStats: false })
    if (changed.length > 0) await invoke('git_commit', { vaultPath, message: `Mobile sync ${new Date().toLocaleString()}` })
    await invoke('git_push', { vaultPath })
    const remote = await invoke<GitRemoteStatus>('git_remote_status', { vaultPath })
    setStatus(`系统 git 同步完成 · ${remote.branch || 'main'} · ahead ${remote.ahead} / behind ${remote.behind}`)
    await refresh()
  }

  async function run(action: () => Promise<void>) {
    try {
      await action()
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      setError(message)
      setStatus('操作失败')
    }
  }

  useEffect(() => {
    void run(ensureVault)
  }, [])

  const pdfSrc = active && isPdf(active) ? convertFileSrc(active.path) : ''

  return (
    <main className="mobile-app">
      <div className="mobile-shell">
        <section className="mobile-hero">
          <p className="mobile-kicker">GreatcatNote Lite</p>
          <h1>手机里的安静知识库</h1>
          <p>{vaultPath || '准备本地 vault...'}</p>
          <div className="mobile-actions">
            <button className="mobile-button" onClick={() => void run(createNote)}>新建 Markdown</button>
            <button className="mobile-button secondary" onClick={() => void run(refresh)}>刷新</button>
            <button className="mobile-button secondary" onClick={() => void run(syncNow)}>增量同步</button>
          </div>
        </section>

        <section className="mobile-card">
          <div className="mobile-sync-row">
            <input
              className="mobile-input"
              value={remoteUrl}
              onChange={(event) => setRemoteUrl(event.target.value)}
              placeholder="Git 仓库 URL，可选"
            />
            <button className="mobile-button secondary" onClick={() => void run(cloneRemote)}>克隆</button>
          </div>
          <div className="mobile-sync-row">
            <input className="mobile-input" value={syncRepo} onChange={(event) => setSyncRepo(event.target.value)} placeholder="GitHub repo: owner/name" />
            <input className="mobile-input" value={syncBranch} onChange={(event) => setSyncBranch(event.target.value)} placeholder="branch" />
            <input className="mobile-input" value={syncToken} onChange={(event) => setSyncToken(event.target.value)} placeholder="GitHub token" type="password" />
          </div>
          <p className="mobile-muted">{status}</p>
          {error && <div className="mobile-error">{error}</div>}
        </section>

        <section className="mobile-card mobile-note-list">
          {visibleEntries.map((entry) => (
            <button
              className={`mobile-note-item ${active?.path === entry.path ? 'active' : ''}`}
              key={entry.path}
              onClick={() => void run(() => openEntry(entry))}
            >
              <span className="mobile-note-title">{noteTitle(entry)}</span>
              <span className="mobile-note-meta">{entry.filename}</span>
            </button>
          ))}
        </section>

        {active && (
          <section className="mobile-editor">
            <header className="mobile-editor-header">
              <span className="mobile-editor-title">{noteTitle(active)}</span>
              {isMarkdown(active) && <button className="mobile-button secondary" onClick={() => void run(saveActive)}>保存</button>}
            </header>
            {isMarkdown(active) && (
              <>
                <textarea className="mobile-textarea" value={content} onChange={(event) => setContent(event.target.value)} />
                <article className="mobile-markdown-preview">
                  <Markdown remarkPlugins={[remarkGfm]}>{content}</Markdown>
                </article>
              </>
            )}
            {isPdf(active) && <object className="mobile-pdf" data={pdfSrc} type="application/pdf">无法在当前 WebView 内预览 PDF。</object>}
          </section>
        )}
      </div>
    </main>
  )
}
