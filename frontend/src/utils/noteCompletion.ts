import type { Completion, CompletionContext, CompletionResult, CompletionSource } from '@codemirror/autocomplete'
import { noteApi } from '@/api/notes'

export interface NoteOption {
  id: number
  title: string
}

interface BracketLinkOptions {
  /** Reactive accessor for the local cache (e.g. () => recentNotesCache.value) */
  getCache: () => NoteOption[]
  /** Minimum local matches before skipping the remote API. Default 3. */
  localThreshold?: number
}

function buildApply(title: string) {
  return (view: any, _completion: Completion, from: number, to: number) => {
    const insert = `${title}]]`
    view.dispatch({
      changes: { from, to, insert },
      selection: { anchor: from + insert.length },
    })
  }
}

function toCompletion(opt: NoteOption): Completion {
  return {
    label: opt.title,
    type: 'text',
    detail: `#${opt.id}`,
    apply: buildApply(opt.title),
  }
}

function dedupeByTitle(items: NoteOption[]): NoteOption[] {
  const seen = new Set<string>()
  const out: NoteOption[] = []
  for (const it of items) {
    if (!it.title) continue
    if (seen.has(it.title)) continue
    seen.add(it.title)
    out.push(it)
  }
  return out
}

// In-flight remote searches keyed by keyword, so concurrent CompletionSource invocations
// (CodeMirror re-runs as the user types) coalesce into one network request per keyword.
const remoteCache = new Map<string, Promise<NoteOption[]>>()

function fetchRemote(keyword: string): Promise<NoteOption[]> {
  const cached = remoteCache.get(keyword)
  if (cached) return cached
  const p = (async () => {
    try {
      const res = await noteApi.searchNotes({ keyword, pageNum: 1, pageSize: 10 })
      const records = (res as unknown as { records?: { id: number; title: string }[] }).records ?? []
      return records.map((r) => ({ id: r.id, title: r.title }))
    } catch {
      return []
    }
  })()
  remoteCache.set(keyword, p)
  // Evict after a short window so stale results don't block fresh queries forever.
  setTimeout(() => remoteCache.delete(keyword), 5000)
  return p
}

export function createBracketLinkCompletion(opts: BracketLinkOptions): CompletionSource {
  const localThreshold = opts.localThreshold ?? 3

  return async (ctx: CompletionContext): Promise<CompletionResult | null> => {
    const before = ctx.matchBefore(/\[\[[^\[\]\n]*$/)
    if (!before) return null

    const keyword = before.text.slice(2).trim()
    const from = before.from + 2
    const to = ctx.pos

    const cache = opts.getCache() ?? []
    const lowerKw = keyword.toLowerCase()
    const localMatches = keyword
      ? cache.filter((n) => n.title.toLowerCase().includes(lowerKw))
      : cache.slice(0, 20)

    let combined: NoteOption[] = localMatches

    if (keyword.length >= 2 && localMatches.length < localThreshold) {
      const remote = await fetchRemote(keyword)
      combined = dedupeByTitle([...localMatches, ...remote])
    }

    if (combined.length === 0) return null

    return {
      from,
      to,
      options: combined.slice(0, 20).map(toCompletion),
      validFor: /^[^\[\]\n]*$/,
    }
  }
}
