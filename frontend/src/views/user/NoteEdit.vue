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

// ── Custom minimal editor theme ──────────────────────
const editorTheme = EditorView.theme({
  '&': {
    backgroundColor: 'var(--cn-surface)',
    color: 'var(--cn-text)',
    height: '100%',
    fontSize: '0.9375rem',
    lineHeight: '1.8',
  },
  '.cm-scroller': {
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
    overflow: 'auto',
  },
  '.cm-content': {
    caretColor: 'var(--cn-accent)',
    padding: '2rem 3rem',
  },
  '.cm-cursor, .cm-dropCursor': {
    borderLeftColor: 'var(--cn-accent)',
  },
  '&.cm-focused .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection': {
    backgroundColor: 'rgba(17, 17, 17, 0.12)',
  },
  '.cm-activeLine': {
    backgroundColor: 'var(--cn-bg-subtle)',
  },
  '.cm-gutters': {
    backgroundColor: 'var(--cn-bg-subtle)',
    color: 'var(--cn-text-faint)',
    borderRight: '1px solid var(--cn-border)',
  },
  '.cm-activeLineGutter': {
    backgroundColor: 'var(--cn-surface-muted)',
    color: 'var(--cn-text)',
  },
  '.cm-foldPlaceholder': {
    backgroundColor: 'var(--cn-surface-muted)',
    color: 'var(--cn-text-soft)',
    border: '1px solid var(--cn-border)',
    borderRadius: '4px',
    padding: '0 6px',
  },
  '.cm-tooltip': {
    backgroundColor: 'var(--cn-surface)',
    color: 'var(--cn-text)',
    border: '1px solid var(--cn-border)',
    borderRadius: '12px',
    boxShadow: 'var(--cn-shadow-md)',
  },
  '.cm-searchMatch': {
    backgroundColor: 'rgba(180, 83, 9, 0.16)',
    outline: '1px solid rgba(180, 83, 9, 0.28)',
  },
  '.cm-matchingBracket': {
    backgroundColor: 'rgba(17, 17, 17, 0.08)',
    outline: '1px solid rgba(17, 17, 17, 0.18)',
  },
  '.cm-hr-line': {
    borderBottom: '1px dashed var(--cn-border-strong)',
    paddingBottom: '0.25rem',
    marginBottom: '0.25rem',
    color: 'var(--cn-text-muted)',
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
  <div class="note-edit-page">
    <header class="editor-toolbar">
      <div class="toolbar-main">
        <button class="cn-btn toolbar-button" @click="$router.back()">
          <ArrowLeft class="h-4 w-4" />
          <span class="hidden sm:inline">返回</span>
        </button>

        <span class="mode-badge" :class="isCreateMode ? 'is-create' : 'is-edit'">
          <FilePlus v-if="isCreateMode" class="h-3.5 w-3.5" />
          <FileEdit v-else class="h-3.5 w-3.5" />
          {{ isCreateMode ? '新建笔记' : '编辑笔记' }}
        </span>

        <div class="title-field">
          <input
            v-if="editingTitle"
            ref="titleInputRef"
            v-model="title"
            type="text"
            class="editor-title-input"
            placeholder="输入笔记标题..."
            @blur="finishEditTitle"
            @keyup.enter="finishEditTitle"
          />
          <button
            v-else
            class="editor-title-button"
            :class="{ 'is-empty': !title }"
            @click="startEditTitle"
          >
            {{ title || '点击设置标题...' }}
          </button>
          <span v-if="title" class="file-suffix">.md</span>
        </div>

        <div v-if="isCreateMode" class="topic-select">
          <select v-model="topicId" class="editor-select">
            <option :value="undefined">无归属主题</option>
            <option v-for="t in topicList" :key="t.id" :value="t.id">{{ t.topicName }}</option>
          </select>
          <Layers class="h-3.5 w-3.5" />
        </div>

        <div class="mode-switch" role="group" aria-label="编辑模式">
          <button
            type="button"
            class="mode-button"
            :class="{ 'is-active': editMode === 'text' }"
            @click="editMode = 'text'"
          >
            <PenLine class="h-3.5 w-3.5" /> 文本编辑
          </button>
          <button
            type="button"
            class="mode-button"
            :class="{ 'is-active': editMode === 'file' }"
            @click="editMode = 'file'"
          >
            <FileUp class="h-3.5 w-3.5" /> 文件上传
          </button>
        </div>

        <button
          v-if="editMode === 'text'"
          type="button"
          class="cn-btn toolbar-button"
          title="从本地导入 .md 文件覆盖编辑器内容"
          @click="triggerImportFile"
        >
          <Upload class="h-3.5 w-3.5" /> 导入 .md
        </button>
        <input
          ref="fileImportInputRef"
          type="file"
          accept=".md,text/markdown"
          class="hidden"
          @change="handleImportFile"
        />
      </div>

      <div class="toolbar-actions">
        <span v-if="isDirty" class="dirty-dot" title="有未保存的更改" />
        <button
          class="cn-btn cn-btn-primary save-button"
          :disabled="saving || loading"
          @click="handleSave"
        >
          <Loader2 v-if="saving" class="h-4 w-4 animate-spin" />
          <Save v-else class="h-4 w-4" />
          <span>{{ saving ? '保存中...' : '保存' }}</span>
          <span class="shortcut">Ctrl+S</span>
        </button>
      </div>
    </header>

    <main class="editor-shell">
      <div v-if="editMode === 'text'" ref="editorContainer" class="editor-host" />

      <div v-else class="upload-panel">
        <div
          class="upload-dropzone"
          :class="{ 'is-dragging': isDragging }"
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @drop="onDrop"
        >
          <div v-if="!uploadFile" class="upload-content">
            <span class="upload-icon"><FileUp class="h-6 w-6" /></span>
            <h3>拖拽 .md 文件到此处</h3>
            <p>或点击下方按钮选择文件，最大 300KB。</p>
            <button type="button" class="cn-btn cn-btn-primary upload-button" @click="pickUploadFile">
              <Upload class="h-4 w-4" /> 选择文件
            </button>
            <input
              ref="fileUploadInputRef"
              type="file"
              accept=".md,text/markdown"
              class="hidden"
              @change="onUploadInputChange"
            />
          </div>

          <div v-else class="upload-content">
            <span class="upload-icon"><FileEdit class="h-6 w-6" /></span>
            <h3 class="upload-file-name">{{ uploadFile.name }}</h3>
            <p :class="{ 'is-danger': uploadExceedsSize }">
              {{ formatBytes(uploadFileSize) }}
              <span v-if="uploadExceedsSize"> · 超出 300KB 限制</span>
            </p>
            <div class="upload-actions">
              <button type="button" class="cn-btn toolbar-button" @click="clearUploadFile">重新选择</button>
              <span>点击右上角“保存”完成上传</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="loading" class="editor-overlay">
        <Loader2 class="h-6 w-6 animate-spin" />
        <span>加载笔记内容...</span>
      </div>

      <div v-if="error" class="editor-overlay is-error">
        <span class="error-icon"><AlertTriangle class="h-6 w-6" /></span>
        <h3>加载失败</h3>
        <p>{{ error }}</p>
        <button class="cn-btn" @click="$router.push('/user/notes')">返回笔记列表</button>
      </div>
    </main>

    <footer class="editor-status">
      <div>
        <span>{{ formatCharCount(charCount) }}</span>
        <span>约 {{ formatBytes(byteSize) }}</span>
      </div>
      <div>
        <span v-if="exceedsSizeLimit" class="status-danger">
          <AlertTriangle class="h-3.5 w-3.5" /> 超过 300KB 限制
        </span>
        <span v-if="isDirty" class="status-warning">未保存</span>
        <span v-else-if="saved" class="status-success">已保存</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.note-edit-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 5rem);
  max-width: 1600px;
  min-height: 620px;
  margin: 0 auto;
}

.editor-toolbar,
.editor-shell {
  border: 1px solid var(--cn-border);
  border-radius: var(--cn-radius-lg);
  background: var(--cn-surface);
  box-shadow: var(--cn-shadow-xs);
}

.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
  padding: 12px 14px;
}

.toolbar-main,
.toolbar-actions,
.upload-actions,
.editor-status > div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar-main {
  min-width: 0;
  flex: 1;
  flex-wrap: wrap;
}

.toolbar-button {
  min-height: 32px;
  padding: 0 10px;
  font-size: 12px;
}

.mode-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  border: 1px solid var(--cn-border);
  border-radius: var(--cn-radius-sm);
  padding: 0 9px;
  background: var(--cn-bg-subtle);
  color: var(--cn-text-soft);
  font-size: 11px;
  font-weight: 720;
}

.mode-badge.is-create {
  color: var(--cn-success);
}

.mode-badge.is-edit {
  color: var(--cn-info);
}

.title-field {
  display: flex;
  min-width: 180px;
  max-width: min(420px, 100%);
  align-items: center;
  gap: 4px;
}

.editor-title-input,
.editor-title-button,
.editor-select {
  border: 1px solid var(--cn-border);
  border-radius: var(--cn-radius-sm);
  background: var(--cn-surface);
  color: var(--cn-text);
}

.editor-title-input {
  min-width: 220px;
  padding: 6px 10px;
  font-size: 13px;
  font-weight: 680;
}

.editor-title-button {
  max-width: 400px;
  overflow: hidden;
  padding: 6px 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 700;
  transition: background-color var(--cn-fast) var(--cn-ease);
}

.editor-title-button:hover {
  background: var(--cn-surface-muted);
}

.editor-title-button.is-empty {
  color: var(--cn-text-muted);
  font-style: italic;
}

.file-suffix {
  color: var(--cn-text-muted);
  font-family: var(--cn-font-mono);
  font-size: 11px;
}

.topic-select {
  position: relative;
  display: flex;
  align-items: center;
}

.topic-select svg {
  position: absolute;
  left: 10px;
  color: var(--cn-text-muted);
  pointer-events: none;
}

.editor-select {
  min-height: 32px;
  padding: 0 10px 0 30px;
  color: var(--cn-text-soft);
  font-size: 12px;
  font-weight: 650;
}

.mode-switch {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--cn-border);
  border-radius: var(--cn-radius-sm);
  background: var(--cn-surface-muted);
  padding: 2px;
}

.mode-button {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  gap: 6px;
  border-radius: 6px;
  padding: 0 10px;
  color: var(--cn-text-muted);
  font-size: 11px;
  font-weight: 720;
  transition:
    background-color var(--cn-fast) var(--cn-ease),
    color var(--cn-fast) var(--cn-ease);
}

.mode-button:hover {
  color: var(--cn-text);
}

.mode-button.is-active {
  background: var(--cn-surface);
  color: var(--cn-text);
  box-shadow: var(--cn-shadow-xs);
}

.dirty-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--cn-warning);
}

.save-button {
  min-height: 34px;
  padding: 0 14px;
}

.shortcut {
  color: rgba(255, 255, 255, 0.64);
  font-size: 10px;
  font-weight: 600;
}

.editor-shell {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.editor-host {
  width: 100%;
  height: 100%;
}

:deep(.cm-editor) {
  height: 100%;
}

:deep(.cm-focused) {
  outline: none;
}

.upload-panel {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.upload-dropzone {
  width: min(680px, 100%);
  border: 1.5px dashed var(--cn-border-strong);
  border-radius: var(--cn-radius-xl);
  background: var(--cn-bg-subtle);
  padding: 40px;
  transition:
    border-color var(--cn-fast) var(--cn-ease),
    background-color var(--cn-fast) var(--cn-ease);
}

.upload-dropzone.is-dragging {
  border-color: var(--cn-accent);
  background: var(--cn-surface-muted);
}

.upload-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
}

.upload-icon,
.error-icon {
  display: inline-flex;
  width: 56px;
  height: 56px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--cn-border);
  border-radius: var(--cn-radius-lg);
  background: var(--cn-surface);
  color: var(--cn-text);
}

.upload-content h3 {
  max-width: 100%;
  margin: 0;
  color: var(--cn-text);
  font-size: 16px;
  font-weight: 740;
}

.upload-content p,
.upload-actions span {
  margin: 0;
  color: var(--cn-text-muted);
  font-size: 12px;
}

.upload-content p.is-danger {
  color: var(--cn-danger);
  font-weight: 700;
}

.upload-button {
  margin-top: 6px;
  padding: 0 14px;
}

.upload-file-name {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-overlay {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--cn-text-soft);
  backdrop-filter: blur(10px);
}

.editor-overlay.is-error {
  flex-direction: column;
  padding: 32px;
  text-align: center;
}

.editor-overlay.is-error h3 {
  margin: 4px 0 0;
  color: var(--cn-text);
  font-size: 18px;
  font-weight: 760;
}

.editor-overlay.is-error p {
  max-width: 520px;
  margin: 0 0 8px;
  color: var(--cn-text-muted);
  font-size: 13px;
  line-height: 1.7;
}

.error-icon {
  color: var(--cn-danger);
}

.editor-status {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 4px 0;
  color: var(--cn-text-muted);
  font-size: 12px;
}

.status-danger,
.status-warning,
.status-success {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-weight: 700;
}

.status-danger {
  color: var(--cn-danger);
}

.status-warning {
  color: var(--cn-warning);
}

.status-success {
  color: var(--cn-success);
}

@media (max-width: 900px) {
  .note-edit-page {
    height: auto;
    min-height: calc(100vh - 5rem);
  }

  .editor-toolbar,
  .editor-status {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-actions {
    justify-content: flex-end;
  }

  .editor-shell {
    min-height: 620px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .mode-button,
  .upload-dropzone,
  .editor-title-button {
    transition-duration: 0.01s;
  }
}
</style>
