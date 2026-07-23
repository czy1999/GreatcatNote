import { Excalidraw, FONT_FAMILY, THEME } from '@excalidraw/excalidraw'
import '@excalidraw/excalidraw/index.css'
import { WarningCircle } from '@phosphor-icons/react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ComponentProps } from 'react'
import { trackEvent } from '../lib/telemetry'
import { cacheNoteContent } from '../hooks/useTabManagement'
import { persistContent } from '../hooks/useSaveNote'
import type { ExcalidrawInitialDataState } from '@excalidraw/excalidraw/types'
import type { BinaryFiles } from '@excalidraw/excalidraw/types'

type ExcalidrawOnChangeArgs = Parameters<NonNullable<ComponentProps<typeof Excalidraw>['onChange']>>
type DrawingGuardWindow = Window & { __greatcatnoteConfirmLeaveDrawing?: () => boolean }

interface ExcalidrawFilePreviewProps {
  content: string
  onContentSaved?: (path: string, content: string) => void
  path: string
  title: string
}

interface ParsedExcalidrawFile {
  initialData: ExcalidrawInitialDataState
  raw: Record<string, unknown>
}

function parseExcalidrawFile(content: string): ParsedExcalidrawFile | null {
  const parsed = JSON.parse(content) as Record<string, unknown>
  if (!Array.isArray(parsed.elements)) return null

  return {
    initialData: {
      appState: {
        ...(typeof parsed.appState === 'object' && parsed.appState ? parsed.appState : {}),
        currentItemFontFamily: FONT_FAMILY.Virgil,
        viewModeEnabled: false,
      },
      elements: parsed.elements,
      files: typeof parsed.files === 'object' && parsed.files ? parsed.files as BinaryFiles : undefined,
      libraryItems: Array.isArray(parsed.libraryItems) ? parsed.libraryItems : undefined,
      scrollToContent: true,
    },
    raw: parsed,
  }
}

function serializeExcalidrawFile(
  raw: Record<string, unknown>,
  elements: ExcalidrawOnChangeArgs[0],
  appState: ExcalidrawOnChangeArgs[1],
  files: ExcalidrawOnChangeArgs[2],
) {
  return `${JSON.stringify({
    ...raw,
    type: typeof raw.type === 'string' ? raw.type : 'excalidraw',
    version: typeof raw.version === 'number' ? raw.version : 2,
    source: typeof raw.source === 'string' ? raw.source : 'https://github.com/excalidraw/excalidraw',
    elements,
    appState: {
      ...appState,
      collaborators: undefined,
    },
    files,
  }, null, 2)}\n`
}

function ExcalidrawPreviewError({ title }: { title: string }) {
  return (
    <section className="flex min-h-0 flex-1 items-center justify-center bg-background px-8 text-center" aria-label={title}>
      <div className="space-y-2">
        <WarningCircle size={34} className="mx-auto text-muted-foreground" aria-hidden="true" />
        <h2 className="m-0 text-[15px] font-semibold text-foreground">Excalidraw preview unavailable</h2>
        <p className="m-0 max-w-md text-[13px] leading-6 text-muted-foreground">
          GreatcatNote could not read this file as an Excalidraw scene.
        </p>
      </div>
    </section>
  )
}

export function ExcalidrawFilePreview({ content, onContentSaved, path, title }: ExcalidrawFilePreviewProps) {
  const [dirty, setDirty] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const draftRef = useRef(content)
  const dirtyRef = useRef(false)
  const scene = useMemo(() => {
    try {
      return parseExcalidrawFile(content)
    } catch {
      return null
    }
  }, [content])

  useEffect(() => {
    draftRef.current = content
    dirtyRef.current = false
    setDirty(false)
    setError(null)
  }, [content])

  useEffect(() => {
    dirtyRef.current = dirty
  }, [dirty])

  useEffect(() => {
    const confirmLeave = () => {
      if (!dirtyRef.current) return true
      return window.confirm('This drawing has unsaved changes. Leave without saving?')
    }
    const guardedWindow = window as DrawingGuardWindow
    guardedWindow.__greatcatnoteConfirmLeaveDrawing = confirmLeave
    return () => {
      if (guardedWindow.__greatcatnoteConfirmLeaveDrawing === confirmLeave) {
        delete guardedWindow.__greatcatnoteConfirmLeaveDrawing
      }
    }
  }, [])

  useEffect(() => {
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!dirtyRef.current) return
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [])

  useEffect(() => {
    trackEvent('excalidraw_file_preview_opened')
  }, [])

  const onChange = useCallback((...args: ExcalidrawOnChangeArgs) => {
    if (!scene) return
    draftRef.current = serializeExcalidrawFile(scene.raw, args[0], args[1], args[2])
    setDirty(draftRef.current !== content)
  }, [content, scene])

  const onSave = useCallback(async () => {
    const nextContent = draftRef.current
    setSaving(true)
    setError(null)
    try {
      await persistContent(path, nextContent)
      cacheNoteContent(path, nextContent)
      onContentSaved?.(path, nextContent)
      dirtyRef.current = false
      setDirty(false)
      trackEvent('excalidraw_file_saved')
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setSaving(false)
    }
  }, [onContentSaved, path])

  if (!scene) return <ExcalidrawPreviewError title={title} />

  return (
    <section className="relative min-h-0 flex-1 bg-background" aria-label={title}>
      <div className="absolute right-3 top-3 z-10 flex items-center gap-2 rounded-lg border border-border bg-background/90 px-3 py-2 shadow-sm backdrop-blur">
        {error && <span className="max-w-[280px] truncate text-xs text-destructive" title={error}>{error}</span>}
        <button
          type="button"
          className="rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground disabled:cursor-not-allowed disabled:opacity-50"
          disabled={!dirty || saving}
          onClick={onSave}
        >
          {saving ? 'Saving...' : dirty ? 'Save drawing' : 'Saved'}
        </button>
      </div>
      <Excalidraw
        initialData={scene.initialData}
        onChange={onChange}
        theme={THEME.LIGHT}
        UIOptions={{
          canvasActions: {
            export: false,
            loadScene: false,
            saveAsImage: false,
            saveToActiveFile: false,
          },
        }}
      />
    </section>
  )
}
