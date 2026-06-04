<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { EditorView, keymap, lineNumbers, highlightActiveLine, highlightSpecialChars, drawSelection, rectangularSelection, placeholder, Decoration, ViewPlugin, type DecorationSet, type ViewUpdate } from '@codemirror/view'
import { EditorState, RangeSetBuilder } from '@codemirror/state'
import { markdown } from '@codemirror/lang-markdown'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { syntaxHighlighting, defaultHighlightStyle, syntaxTree } from '@codemirror/language'
import { closeBrackets, autocompletion, completionKeymap } from '@codemirror/autocomplete'
import { noteApi } from '@/api/notes'
import { topicApi } from '@/api/topics'
import type { TopicItem } from '@/api/topics'
import { markdownContentToFile, formatCharCount, estimateByteSize, formatBytes, MAX_NOTE_FILE_SIZE } from '@/utils/markdown'
import { markdownLinter, markdownLintGutter } from '@/utils/markdownLint'
import { createBracketLinkCompletion, type NoteOption } from '@/utils/noteCompletion'
import { ArrowLeft, Save, Loader2, AlertTriangle, FilePlus, FileEdit, Layers, Upload, FileUp, PenLine } from 'lucide-vue-next'
import { alertInfo, confirmAction, toastError, toastWarning } from '@/utils/feedback'

const route = useRoute()
const router = useRouter()

// ── Mode detection ──────────────────────────────────
const isCreateMode = computed(() => route.path.includes('/notes/new'))
const noteId = computed(() => {
  if (isCreateMode.value) return null
  const id = Number(route.params.noteId)
  return isNaN(id) ? null : id
})

// ── State ───────────────────────────────────────────
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const title = ref('')
const originalContent = ref('')
const saved = ref(false)
const topicId = ref<number | undefined>(undefined)
const topicList = ref<TopicItem[]>([])

// ── CodeMirror ──────────────────────────────────────
const editorContainer = ref<HTMLElement | null>(null)
let editorView: EditorView | null = null

const isDirty = ref(false)
const charCount = ref(0)
const byteSize = ref(0)

const exceedsSizeLimit = computed(() => byteSize.value > MAX_NOTE_FILE_SIZE)

// ── Edit/Upload mode + file-upload state ────────────
type EditMode = 'text' | 'file'
const editMode = ref<EditMode>('text')
const uploadFile = ref<File | null>(null)
const isDragging = ref(false)
const fileImportInputRef = ref<HTMLInputElement | null>(null)
const fileUploadInputRef = ref<HTMLInputElement | null>(null)

const uploadFileSize = computed(() => uploadFile.value?.size ?? 0)
const uploadExceedsSize = computed(() => uploadFileSize.value > MAX_NOTE_FILE_SIZE)

// ── Recent notes cache for [[ autocompletion ────────
const recentNotesCache = ref<NoteOption[]>([])

// ── Custom dark theme ────────────────────────────────
const editorTheme = EditorView.theme({
  '&': {
    backgroundColor: '#020617',
    color: '#cbd5e1',
    height: '100%',
    fontSize: '0.9375rem',
    lineHeight: '1.8',
  },
  '.cm-scroller': {
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
    overflow: 'auto',
  },
  '.cm-content': {
    caretColor: '#818cf8',
    padding: '2rem 3rem',
  },
  '.cm-cursor, .cm-dropCursor': {
    borderLeftColor: '#818cf8',
  },
  '&.cm-focused .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection': {
    backgroundColor: 'rgba(129, 140, 248, 0.25)',
  },
  '.cm-activeLine': {
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
  },
  '.cm-gutters': {
    backgroundColor: '#020617',
    color: '#475569',
    borderRight: '1px solid rgba(255, 255, 255, 0.06)',
  },
  '.cm-activeLineGutter': {
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
    color: '#818cf8',
  },
  '.cm-foldPlaceholder': {
    backgroundColor: 'rgba(59, 130, 246, 0.1)',
    color: '#60a5fa',
    border: '1px solid rgba(59, 130, 246, 0.2)',
    borderRadius: '4px',
    padding: '0 6px',
  },
  '.cm-tooltip': {
    backgroundColor: '#0f172a',
    color: '#cbd5e1',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '12px',
    boxShadow: '0 12px 40px rgba(0, 0, 0, 0.5)',
  },
  '.cm-searchMatch': {
    backgroundColor: 'rgba(245, 158, 11, 0.3)',
    outline: '1px solid rgba(245, 158, 11, 0.5)',
  },
  '.cm-matchingBracket': {
    backgroundColor: 'rgba(59, 130, 246, 0.2)',
    outline: '1px solid rgba(59, 130, 246, 0.4)',
  },
  '.cm-hr-line': {
    borderBottom: '1px dashed rgba(148, 163, 184, 0.45)',
    paddingBottom: '0.25rem',
    marginBottom: '0.25rem',
    color: '#94a3b8',
  },
})

// ── Save keybinding ──────────────────────────────────
const saveKeymap = keymap.of([
  {
    key: 'Mod-s',
    run: () => {
      handleSave()
      return true
    },
    preventDefault: true,
  },
])

// ── HorizontalRule (---) line decoration ─────────────
const hrLineDeco = Decoration.line({ class: 'cm-hr-line' })

function buildHrDecorations(view: EditorView): DecorationSet {
  const builder = new RangeSetBuilder<Decoration>()
  for (const { from, to } of view.visibleRanges) {
    syntaxTree(view.state).iterate({
      from,
      to,
      enter: (node) => {
        if (node.name === 'HorizontalRule') {
          const line = view.state.doc.lineAt(node.from)
          builder.add(line.from, line.from, hrLineDeco)
        }
      },
    })
  }
  return builder.finish()
}

const horizontalRulePlugin = ViewPlugin.fromClass(
  class {
    decorations: DecorationSet
    constructor(view: EditorView) {
      this.decorations = buildHrDecorations(view)
    }
    update(u: ViewUpdate) {
      if (u.docChanged || u.viewportChanged) {
        this.decorations = buildHrDecorations(u.view)
      }
    }
  },
  { decorations: (v) => v.decorations },
)

// ── Editor helpers ───────────────────────────────────
function createEditor(doc: string) {
  if (!editorContainer.value) return

  const extensions = [
    markdown(),
    lineNumbers(),
    highlightActiveLine(),
    highlightSpecialChars(),
    drawSelection(),
    rectangularSelection(),
    closeBrackets(),
    history(),
    syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
    EditorView.lineWrapping,
    keymap.of([...defaultKeymap, ...historyKeymap, ...completionKeymap, indentWithTab]),
    saveKeymap,
    editorTheme,
    placeholder('Start writing in Markdown...'),
    markdownLintGutter,
    markdownLinter,
    horizontalRulePlugin,
    autocompletion({
      override: [createBracketLinkCompletion({ getCache: () => recentNotesCache.value })],
      activateOnTyping: true,
      maxRenderedOptions: 20,
    }),
    EditorView.updateListener.of((update) => {
      if (update.docChanged) {
        const content = update.state.doc.toString()
        charCount.value = content.length
        byteSize.value = estimateByteSize(content)
        isDirty.value = content !== originalContent.value
      }
    }),
  ]

  const state = EditorState.create({ doc, extensions })

  editorView = new EditorView({
    state,
    parent: editorContainer.value,
  })
}

function getEditorContent(): string {
  return editorView?.state.doc.toString() ?? ''
}

function setEditorContent(content: string) {
  if (!editorView) return
  editorView.dispatch({
    changes: { from: 0, to: editorView.state.doc.length, insert: content },
  })
}

// ── Data fetching ────────────────────────────────────
async function fetchTopics() {
  try {
    const res = await topicApi.getList({ pageSize: 100 })
    topicList.value = (res as unknown as { records: TopicItem[] }).records ?? []
  } catch { /* non-critical */ }
}

async function fetchRecentNotes() {
  try {
    const res = await noteApi.getList({ pageNum: 1, pageSize: 200 })
    const records = (res as unknown as { records?: { id: number; title: string }[] }).records ?? []
    recentNotesCache.value = records.map((r) => ({ id: r.id, title: r.title }))
  } catch { /* completion will fall back to API */ }
}

// ── File import (text mode) ──────────────────────────
function triggerImportFile() {
  fileImportInputRef.value?.click()
}

function stripMdExt(name: string): string {
  return name.replace(/\.md$/i, '')
}

async function readFileAsText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(reader.error ?? new Error('读取失败'))
    reader.readAsText(file, 'utf-8')
  })
}

async function handleImportFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // allow re-import of the same file
  if (!file) return
  if (!/\.md$/i.test(file.name)) {
    toastWarning('请选择 .md 文件')
    return
  }
  if (file.size > MAX_NOTE_FILE_SIZE) {
    toastWarning(`文件大小超过 300KB 限制（${formatBytes(file.size)}）`)
    return
  }
  const currentContent = getEditorContent()
  if (isDirty.value && currentContent.trim()) {
    const ok = await confirmAction({
      title: '覆盖导入',
      content: '编辑器中有未保存的内容，导入将覆盖。继续吗？'
    })
    if (!ok) return
  }
  try {
    const text = await readFileAsText(file)
    setEditorContent(text)
    if (!title.value.trim()) title.value = stripMdExt(file.name)
    originalContent.value = '' // mark as dirty
    isDirty.value = true
  } catch (err: any) {
    toastError(err?.message || '读取文件失败')
  }
}

// ── File-upload mode handlers ───────────────────────
function pickUploadFile() {
  fileUploadInputRef.value?.click()
}

function setUploadFile(file: File | null) {
  if (!file) { uploadFile.value = null; return }
  if (!/\.md$/i.test(file.name)) {
    toastWarning('请选择 .md 文件')
    return
  }
  uploadFile.value = file
  if (!title.value.trim()) title.value = stripMdExt(file.name)
}

function onUploadInputChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  input.value = ''
  setUploadFile(file)
}

function onDragOver(e: DragEvent) {
  e.preventDefault()
  isDragging.value = true
}

function onDragLeave(e: DragEvent) {
  e.preventDefault()
  isDragging.value = false
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  isDragging.value = false
  const file = e.dataTransfer?.files?.[0] ?? null
  setUploadFile(file)
}

function clearUploadFile() {
  uploadFile.value = null
}

async function loadSource() {
  if (!noteId.value) return
  loading.value = true
  error.value = null
  try {
    const src = await noteApi.getSource(noteId.value)
    originalContent.value = src as unknown as string
    setEditorContent(originalContent.value)
    charCount.value = originalContent.value.length
    byteSize.value = estimateByteSize(originalContent.value)
    isDirty.value = false
  } catch (e: any) {
    error.value = e?.message || '无法加载笔记源文件'
  } finally {
    loading.value = false
  }
}

// ── Save ─────────────────────────────────────────────

async function handleSave() {
  if (saving.value) return
  if (editMode.value === 'file') {
    await handleFileUploadSave()
    return
  }

  // Validate title
  if (!title.value.trim()) {
    toastWarning('请先输入笔记标题')
    return
  }

  // Validate size
  if (exceedsSizeLimit.value) {
    toastWarning(`文件大小超过 300KB 限制（当前 ${formatBytes(byteSize.value)}），请精简内容后重试。`)
    return
  }

  const content = getEditorContent()
  saving.value = true

  try {
    const file = markdownContentToFile(content, title.value.trim())

    if (isCreateMode.value) {
      const res = await noteApi.uploadNote(file, topicId.value)
      const result = res as unknown as { noteId: number; missingTags?: string[]; missingImages?: string[]; missingNoteNames?: string[] }
      saved.value = true
      isDirty.value = false
      const missingTotal = (result.missingTags?.length ?? 0) + (result.missingImages?.length ?? 0) + (result.missingNoteNames?.length ?? 0)
      if (missingTotal > 0) {
        alertInfo(`笔记创建成功，但仍有 ${missingTotal} 项关联资源需要补全。`)
      }
      router.replace(`/user/notes/${result.noteId}`)
    } else {
      if (noteId.value == null) { toastError('笔记 ID 无效'); return }
      await noteApi.modifyFile(noteId.value, file)
      saved.value = true
      isDirty.value = false
      router.push(`/user/notes/${noteId.value}/diff`)
    }
  } catch (e: any) {
    const message = e?.message || (isCreateMode.value ? '创建笔记失败' : '保存失败')
    toastError(message)
  } finally {
    saving.value = false
  }
}

async function handleFileUploadSave() {
  if (!uploadFile.value) {
    toastWarning('请先选择要上传的 .md 文件')
    return
  }
  if (uploadExceedsSize.value) {
    toastWarning(`文件大小超过 300KB 限制（${formatBytes(uploadFileSize.value)}）`)
    return
  }
  saving.value = true
  try {
    if (isCreateMode.value) {
      const res = await noteApi.uploadNote(uploadFile.value, topicId.value)
      const result = res as unknown as { noteId: number; missingTags?: string[]; missingImages?: string[]; missingNoteNames?: string[] }
      saved.value = true
      const missingTotal = (result.missingTags?.length ?? 0) + (result.missingImages?.length ?? 0) + (result.missingNoteNames?.length ?? 0)
      if (missingTotal > 0) {
        alertInfo(`笔记创建成功，但仍有 ${missingTotal} 项关联资源需要补全。`)
      }
      router.replace(`/user/notes/${result.noteId}`)
    } else {
      if (noteId.value == null) { toastError('笔记 ID 无效'); return }
      await noteApi.modifyFile(noteId.value, uploadFile.value)
      saved.value = true
      router.push(`/user/notes/${noteId.value}/diff`)
    }
  } catch (e: any) {
    toastError(e?.message || '上传失败')
  } finally {
    saving.value = false
  }
}

// ── Title editing ────────────────────────────────────
const editingTitle = ref(false)
const titleInputRef = ref<HTMLInputElement | null>(null)

function startEditTitle() {
  editingTitle.value = true
  nextTick(() => titleInputRef.value?.focus())
}

function finishEditTitle() {
  editingTitle.value = false
}

// ── Unsaved changes guard ────────────────────────────
function beforeUnloadHandler(e: BeforeUnloadEvent) {
  if (isDirty.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}

onBeforeRouteLeave(async (_to, _from, next) => {
  if (isDirty.value && !saved.value) {
    const confirmLeave = await confirmAction({
      title: '未保存的更改',
      content: '你有未保存的更改，确定离开吗？'
    })
    if (!confirmLeave) {
      next(false)
      return
    }
  }
  next()
})

watch(isDirty, (dirty) => {
  if (dirty) {
    window.addEventListener('beforeunload', beforeUnloadHandler)
  } else {
    window.removeEventListener('beforeunload', beforeUnloadHandler)
  }
})

// Re-mount CodeMirror after switching back to text mode (the container was unmounted).
watch(editMode, async (mode) => {
  if (mode !== 'text') return
  const preserved = editorView ? editorView.state.doc.toString() : originalContent.value
  editorView?.destroy()
  editorView = null
  await nextTick()
  if (editorContainer.value) createEditor(preserved)
})

// ── Lifecycle ────────────────────────────────────────
onMounted(async () => {
  await Promise.all([fetchTopics(), fetchRecentNotes()])
  await nextTick()
  createEditor('')
  if (isCreateMode.value) {
    title.value = ''
    originalContent.value = ''
    charCount.value = 0
    byteSize.value = 0
    isDirty.value = false
  } else {
    // Extract noteId from route; title will come from referrer or be loaded
    // First, try to get title from the route query (passed from NoteDetail)
    const passedTitle = route.query.title as string | undefined
    if (passedTitle) {
      title.value = passedTitle
    }
    await loadSource()
  }
})

onUnmounted(() => {
  editorView?.destroy()
  editorView = null
  window.removeEventListener('beforeunload', beforeUnloadHandler)
})
</script>

<template>
  <div class="relative h-[calc(100vh-5rem)] flex flex-col max-w-[1600px] mx-auto">
    <!-- ═══ Top Bar ═══ -->
    <div class="flex items-center justify-between gap-4 px-6 py-3 glass-panel rounded-2xl mb-4 shrink-0 border border-white/10">
      <div class="flex items-center gap-3">
        <button
          class="flex items-center gap-2 text-slate-400 hover:text-white transition-colors bg-white/5 hover:bg-white/10 px-3 py-1.5 rounded-xl border border-white/5 text-sm font-bold"
          @click="$router.back()"
        >
          <ArrowLeft class="w-4 h-4" />
          <span class="hidden sm:inline">返回</span>
        </button>

        <!-- Mode badge -->
        <span
          class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[10px] font-black uppercase tracking-widest border"
          :class="isCreateMode ? 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20' : 'text-indigo-400 bg-indigo-500/10 border-indigo-500/20'"
        >
          <FilePlus v-if="isCreateMode" class="w-3.5 h-3.5" />
          <FileEdit v-else class="w-3.5 h-3.5" />
          {{ isCreateMode ? '新建笔记' : '编辑笔记' }}
        </span>

        <!-- Title -->
        <div class="relative">
          <input
            v-if="editingTitle"
            ref="titleInputRef"
            v-model="title"
            type="text"
            class="bg-black/30 border border-indigo-500/50 rounded-lg px-3 py-1 text-sm font-bold text-white outline-none focus:ring-2 focus:ring-indigo-500/30 min-w-[200px]"
            placeholder="输入笔记标题..."
            @blur="finishEditTitle"
            @keyup.enter="finishEditTitle"
          />
          <button
            v-else
            class="text-sm font-bold truncate max-w-[400px] px-2 py-1 rounded-lg hover:bg-white/5 transition-colors"
            :class="title ? 'text-white' : 'text-slate-500 italic'"
            @click="startEditTitle"
          >
            {{ title || '点击设置标题...' }}
          </button>
          <span v-if="title" class="text-[10px] text-slate-500 ml-1 font-mono">.md</span>
        </div>

        <!-- Topic selector (create mode only) -->
        <div v-if="isCreateMode" class="relative group ml-3 hidden sm:block">
          <select
            v-model="topicId"
            class="bg-black/20 border border-white/10 rounded-lg py-1.5 pl-8 pr-3 outline-none focus:border-indigo-500/50 transition-all text-xs font-bold text-slate-300 appearance-none cursor-pointer"
          >
            <option :value="undefined" class="bg-[#0b0d14]">无归属主题</option>
            <option v-for="t in topicList" :key="t.id" :value="t.id" class="bg-[#0b0d14]">{{ t.topicName }}</option>
          </select>
          <Layers class="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none" />
        </div>

        <!-- Mode toggle: 编辑 / 文件上传 -->
        <div class="ml-3 inline-flex items-center bg-black/30 border border-white/10 rounded-xl p-0.5">
          <button
            type="button"
            class="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg text-[11px] font-bold transition-all"
            :class="editMode === 'text' ? 'bg-indigo-600/80 text-white shadow' : 'text-slate-400 hover:text-white'"
            @click="editMode = 'text'"
          >
            <PenLine class="w-3.5 h-3.5" /> 文本编辑
          </button>
          <button
            type="button"
            class="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg text-[11px] font-bold transition-all"
            :class="editMode === 'file' ? 'bg-emerald-600/80 text-white shadow' : 'text-slate-400 hover:text-white'"
            @click="editMode = 'file'"
          >
            <FileUp class="w-3.5 h-3.5" /> 文件上传
          </button>
        </div>

        <!-- Import file button (text mode only) -->
        <button
          v-if="editMode === 'text'"
          type="button"
          class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold text-slate-300 bg-white/5 hover:bg-white/10 border border-white/10 transition-colors"
          title="从本地导入 .md 文件覆盖编辑器内容"
          @click="triggerImportFile"
        >
          <Upload class="w-3.5 h-3.5" /> 导入 .md
        </button>
        <input
          ref="fileImportInputRef"
          type="file"
          accept=".md,text/markdown"
          class="hidden"
          @change="handleImportFile"
        />
      </div>

      <div class="flex items-center gap-3">
        <!-- Dirty indicator -->
        <span v-if="isDirty" class="w-2 h-2 rounded-full bg-amber-500 animate-pulse" title="有未保存的更改" />

        <!-- Save button -->
        <button
          class="group relative px-5 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-bold rounded-xl shadow-[0_0_15px_rgba(99,102,241,0.4)] transition-all overflow-hidden flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="saving || loading"
          @click="handleSave"
        >
          <div class="absolute inset-0 bg-[linear-gradient(to_right,transparent,rgba(255,255,255,0.2),transparent)] -translate-x-[150%] group-hover:translate-x-[150%] transition-transform duration-700 ease-out" />
          <Loader2 v-if="saving" class="w-4 h-4 animate-spin" />
          <Save v-else class="w-4 h-4" />
          <span>{{ saving ? '保存中...' : '保存' }}</span>
          <span class="hidden sm:inline text-[10px] text-indigo-300 opacity-70 ml-1">Ctrl+S</span>
        </button>
      </div>
    </div>

    <!-- ═══ Editor Body ═══ -->
    <div class="flex-1 glass-panel rounded-2xl overflow-hidden border border-white/10 relative">
      <!-- Editor container (text mode) -->
      <div v-if="editMode === 'text'" ref="editorContainer" class="h-full w-full editor-host" />

      <!-- File upload dropzone (file mode) -->
      <div v-else class="h-full w-full flex items-center justify-center p-8">
        <div
          class="w-full max-w-2xl border-2 border-dashed rounded-2xl px-8 py-10 transition-all"
          :class="isDragging ? 'border-emerald-400 bg-emerald-500/10' : 'border-white/15 bg-black/20 hover:border-emerald-400/40'"
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @drop="onDrop"
        >
          <div v-if="!uploadFile" class="flex flex-col items-center text-center gap-3">
            <div class="w-14 h-14 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center">
              <FileUp class="w-6 h-6 text-emerald-400" />
            </div>
            <h3 class="text-base font-bold text-white">拖拽 .md 文件到此处</h3>
            <p class="text-xs text-slate-400">或点击下方按钮选择文件（最大 300KB）</p>
            <button
              type="button"
              class="mt-2 inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-bold transition-colors"
              @click="pickUploadFile"
            >
              <Upload class="w-4 h-4" /> 选择文件
            </button>
            <input
              ref="fileUploadInputRef"
              type="file"
              accept=".md,text/markdown"
              class="hidden"
              @change="onUploadInputChange"
            />
          </div>
          <div v-else class="flex flex-col items-center text-center gap-3">
            <div class="w-14 h-14 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center">
              <FileEdit class="w-6 h-6 text-emerald-400" />
            </div>
            <h3 class="text-base font-bold text-white truncate max-w-full">{{ uploadFile.name }}</h3>
            <p class="text-xs" :class="uploadExceedsSize ? 'text-rose-400 font-bold' : 'text-slate-400'">
              {{ formatBytes(uploadFileSize) }}
              <span v-if="uploadExceedsSize"> · 超出 300KB 限制</span>
            </p>
            <div class="flex items-center gap-2 mt-2">
              <button
                type="button"
                class="px-3 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-slate-300 text-xs font-bold transition-colors"
                @click="clearUploadFile"
              >
                重新选择
              </button>
              <span class="text-[11px] text-slate-500">点击右上角"保存"完成上传</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Loading overlay -->
      <div v-if="loading" class="absolute inset-0 z-10 flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <Loader2 class="w-6 h-6 text-blue-400 animate-spin" />
        <span class="ml-3 text-sm text-slate-300">加载笔记内容...</span>
      </div>

      <!-- Error overlay -->
      <div v-if="error" class="absolute inset-0 z-10 flex flex-col items-center justify-center bg-black/70 backdrop-blur-sm p-8 text-center">
        <div class="w-14 h-14 rounded-2xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center mb-4">
          <AlertTriangle class="w-6 h-6 text-rose-400" />
        </div>
        <h3 class="text-lg font-bold text-white mb-2">加载失败</h3>
        <p class="text-sm text-slate-400 mb-4">{{ error }}</p>
        <button
          class="px-4 py-2 rounded-xl bg-blue-500/10 border border-blue-500/20 text-blue-400 hover:bg-blue-500 hover:text-white transition-all text-sm font-bold"
          @click="$router.push('/user/notes')"
        >
          返回笔记列表
        </button>
      </div>
    </div>

    <!-- ═══ Bottom Status Bar ═══ -->
    <div class="flex items-center justify-between px-4 py-2 mt-3 text-xs shrink-0">
      <div class="flex items-center gap-4 text-slate-500">
        <span>{{ formatCharCount(charCount) }}</span>
        <span>约 {{ formatBytes(byteSize) }}</span>
      </div>
      <div class="flex items-center gap-3">
        <span v-if="exceedsSizeLimit" class="text-rose-400 font-bold flex items-center gap-1">
          <AlertTriangle class="w-3.5 h-3.5" /> 超过 300KB 限制
        </span>
        <span v-if="isDirty" class="text-amber-400">未保存</span>
        <span v-else-if="saved" class="text-emerald-400">已保存</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.glass-panel {
  background: rgba(255, 255, 255, 0.02);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.05);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.05);
}

.editor-host {
  /* CodeMirror will fill this container completely */
}
</style>
