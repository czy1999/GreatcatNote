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

  async function syncNow() {
    if (!vaultPath) return
    setError('')
    setStatus('正在同步...')
    await invoke('git_pull', { vaultPath })
    const changed = await invoke<unknown[]>('get_modified_files', { vaultPath, includeStats: false })
    if (changed.length > 0) {
      await invoke('git_commit', {
        vaultPath,
        message: `Mobile sync ${new Date().toLocaleString()}`,
      })
    }
    await invoke('git_push', { vaultPath })
    const remote = await invoke<GitRemoteStatus>('git_remote_status', { vaultPath })
    setStatus(`同步完成 · ${remote.branch || 'main'} · ahead ${remote.ahead} / behind ${remote.behind}`)
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
