<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  AlertTriangle, ArrowRight, CheckCircle2, ChevronLeft, ChevronRight, Clock,
  Eye, FileCode, FileText, FileUp, FolderTree, Globe, Hash, Home, Image,
  Layers, Link, Loader2, Network, PenLine, Plus, RefreshCw, Search, Send,
  Trash2, UploadCloud, X, XCircle
} from 'lucide-vue-next'
import { noteApi, getNoteStatusInfo, NoteStatusCode } from '@/api/notes'
import type { NoteItem, NoteQueryParams, NoteUploadVO, PageResult } from '@/api/notes'
import { topicApi } from '@/api/topics'
import type { TopicItem } from '@/api/topics'
import { alertInfo, confirmAction, toastError, toastSuccess, toastWarning } from '@/utils/feedback'

const router = useRouter()

const loading = ref(true)
const directories = ref<TopicItem[]>([])
const notes = ref<NoteItem[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(12)
const keyword = ref('')

const currentDirectoryId = ref<number | null>(null)
const breadcrumbs = ref<TopicItem[]>([])

const showUploadModal = ref(false)
const uploadFile = ref<File | null>(null)
const uploadDragging = ref(false)
const uploadSubmitting = ref(false)

const showDirectoryModal = ref(false)
const directoryName = ref('')
const directorySortOrder = ref(0)
const directorySubmitting = ref(false)

const showSourceModal = ref(false)
const sourceContent = ref('')
const sourceTitle = ref('')
const currentSourceId = ref<number | null>(null)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const currentDirectoryName = computed(() => breadcrumbs.value[breadcrumbs.value.length - 1]?.topicName ?? '根目录')

function formatBytes(bytes?: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(i > 0 ? 1 : 0) + ' ' + units[i]
}

function formatDate(raw?: string): string {
  if (!raw) return '-'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function formatNumber(n: number): string {
  return n.toLocaleString()
}

function visiblePages(): number[] {
  const pages: number[] = []
  let start = Math.max(1, currentPage.value - 2)
  const end = Math.min(totalPages.value, start + 4)
  if (end - start < 4) start = Math.max(1, end - 4)
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
}

function getStatusIcon(iconName: string) {
  const map: Record<string, any> = {
    FilePlus: FileText,
    AlertTriangle,
    RefreshCw,
    FileCode,
    Clock,
    CheckCircle2,
    Globe,
    XCircle,
    Trash2,
    FileEdit: PenLine,
    HelpCircle: FileText
  }
  return map[iconName] ?? FileText
}

function topicStatus(topic: TopicItem) {
  if (topic.isPass === 1) return { label: '已通过', cls: 'text-emerald-400 border-emerald-500/25 bg-emerald-500/10' }
  if (topic.isPass === 2) return { label: '已拒绝', cls: 'text-rose-400 border-rose-500/25 bg-rose-500/10' }
  return { label: '待审核', cls: 'text-amber-400 border-amber-500/25 bg-amber-500/10' }
}

function noteQueryParams(): NoteQueryParams {
  return {
    title: keyword.value.trim() || undefined,
    keyword: keyword.value.trim() || undefined,
    topicId: currentDirectoryId.value ?? undefined,
    unclassified: currentDirectoryId.value == null ? true : undefined,
    scope: 'personal',
    pageNum: currentPage.value,
    pageSize: pageSize.value
  }
}

async function fetchWorkspace() {
  loading.value = true
  try {
    const [dirs, notePage] = await Promise.all([
      topicApi.getChildren({ parentId: currentDirectoryId.value }),
      noteApi.getList(noteQueryParams())
    ])
    directories.value = dirs ?? []
    notes.value = (notePage as PageResult<NoteItem>).records ?? []
    total.value = (notePage as PageResult<NoteItem>).total ?? 0
  } finally {
    loading.value = false
  }
}

function search() {
  currentPage.value = 1
  void fetchWorkspace()
}

function changePage(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  currentPage.value = page
  void fetchWorkspace()
}

async function enterDirectory(topic: TopicItem) {
  currentDirectoryId.value = topic.id
  breadcrumbs.value.push(topic)
  currentPage.value = 1
  keyword.value = ''
  await fetchWorkspace()
}

async function goRoot() {
  currentDirectoryId.value = null
  breadcrumbs.value = []
  currentPage.value = 1
  await fetchWorkspace()
}

async function goBreadcrumb(index: number) {
  const target = breadcrumbs.value[index]
  breadcrumbs.value = breadcrumbs.value.slice(0, index + 1)
  currentDirectoryId.value = target.id
  currentPage.value = 1
  await fetchWorkspace()
}

function openUploadModal() {
  uploadFile.value = null
  uploadDragging.value = false
  showUploadModal.value = true
}

function closeUploadModal() {
  showUploadModal.value = false
  uploadFile.value = null
  uploadDragging.value = false
}

function handleFileDrop(e: DragEvent) {
  uploadDragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file?.name.endsWith('.md') || file?.name.endsWith('.markdown')) uploadFile.value = file
}

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) uploadFile.value = file
}

async function handleUpload() {
  if (!uploadFile.value || uploadSubmitting.value) return
  uploadSubmitting.value = true
  try {
    const res = await noteApi.uploadNote(uploadFile.value, currentDirectoryId.value ?? undefined)
    closeUploadModal()
    await fetchWorkspace()
    const missing = res as NoteUploadVO
    const missingCount = (missing.missingTags?.length ?? 0)
      + (missing.missingImages?.length ?? 0)
      + (missing.missingNoteNames?.length ?? 0)
    if (missingCount > 0) {
      alertInfo(`笔记上传成功，但仍有 ${missingCount} 项关联资源需要补全。`)
    } else {
      toastSuccess('笔记上传成功。')
    }
  } catch {
    toastError('上传失败，请重试。')
  } finally {
    uploadSubmitting.value = false
  }
}

function openDirectoryModal() {
  directoryName.value = ''
  directorySortOrder.value = 0
  showDirectoryModal.value = true
}

function closeDirectoryModal() {
  showDirectoryModal.value = false
}

async function handleCreateDirectory() {
  const name = directoryName.value.trim()
  if (!name) return
  if (name.length > 25) {
    toastWarning('目录名称不能超过 25 个字符')
    return
  }
  directorySubmitting.value = true
  try {
    await topicApi.addTopic({
      topicName: name,
      parentId: currentDirectoryId.value,
      sortOrder: directorySortOrder.value
    })
    closeDirectoryModal()
    await fetchWorkspace()
    toastSuccess('目录已创建。')
  } finally {
    directorySubmitting.value = false
  }
}

async function handleDeleteDirectory(topic: TopicItem) {
  if (!await confirmAction({ content: `确定删除目录「${topic.topicName}」吗？目录下存在笔记时后端会拒绝删除。`, danger: true })) return
  await topicApi.deleteTopics([topic.id])
  await fetchWorkspace()
}

async function handleSubmitTopicAudit(topic: TopicItem) {
  await topicApi.submitAudit(topic.id)
  await fetchWorkspace()
}

async function handleCancelTopicAudit(topic: TopicItem) {
  if (!await confirmAction({ content: '确认撤销该目录的审核申请吗？' })) return
  await topicApi.cancelAudit(topic.id)
  await fetchWorkspace()
}

async function handleViewSource(note: NoteItem) {
  try {
    sourceContent.value = await noteApi.getSource(note.id)
    sourceTitle.value = note.title
    currentSourceId.value = note.id
    showSourceModal.value = true
  } catch {
    toastError('无法获取源文件。')
  }
}

function handleEditSource() {
  if (currentSourceId.value == null) return
  router.push({ name: 'UserNoteEdit', params: { noteId: currentSourceId.value }, query: { title: sourceTitle.value } })
}

async function handleDeleteNote(note: NoteItem) {
  if (!await confirmAction({ content: `确定删除笔记「${note.title}」吗？`, danger: true })) return
  await noteApi.deleteNote(note.id)
  await fetchWorkspace()
}

async function handleConvert(note: NoteItem) {
  try {
    await noteApi.convertNote(note.id)
    await fetchWorkspace()
  } catch {
    toastError('转换失败，请确认笔记没有关联异常。')
  }
}

async function handleSubmitAudit(note: NoteItem) {
  await noteApi.submitAudit(note.id)
  await fetchWorkspace()
}

async function handlePublish(note: NoteItem) {
  await noteApi.publish(note.id, 1)
  await fetchWorkspace()
}

async function handleUnpublish(note: NoteItem) {
  await noteApi.publish(note.id, 0)
  await fetchWorkspace()
}

function openNoteHtml(note: NoteItem) {
  router.push(`/user/notes/${note.id}`)
}

function openRelations(note: NoteItem) {
  router.push(`/user/notes/${note.id}/relations`)
}

onMounted(fetchWorkspace)
</script>

<template>
  <div class="directory-shell mx-auto max-w-[1400px] space-y-6 pb-28">
    <section class="directory-hero relative overflow-hidden rounded-lg border border-white/10 p-6">
      <div class="relative z-10 flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <div class="mb-3 flex flex-wrap items-center gap-2 text-xs font-bold text-slate-400">
            <button class="crumb" @click="goRoot">
              <Home class="h-3.5 w-3.5" />
              <span>Root</span>
            </button>
            <template v-for="(crumbItem, index) in breadcrumbs" :key="crumbItem.id">
              <ChevronRight class="h-3.5 w-3.5 text-slate-600" />
              <button class="crumb" @click="goBreadcrumb(index)">{{ crumbItem.topicName }}</button>
            </template>
          </div>
          <h1 class="text-2xl font-black tracking-normal text-white sm:text-3xl">{{ currentDirectoryName }}</h1>
          <p class="mt-2 text-sm text-slate-300">目录和笔记都在这里展开，像文件夹一样逐层进入。</p>
        </div>

        <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
          <label class="relative block sm:w-72">
            <Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
            <input
              v-model="keyword"
              class="h-10 w-full rounded-lg border border-white/10 bg-black/20 pl-10 pr-3 text-sm text-white outline-none transition placeholder:text-slate-400 focus:border-blue-400/50 focus:bg-blue-500/5"
              placeholder="搜索当前目录笔记"
              @keydown.enter="search"
            />
          </label>
          <button class="action-button secondary" @click="openDirectoryModal">
            <Plus class="h-4 w-4" />
            <span>新建目录</span>
          </button>
          <button class="action-button primary" @click="openUploadModal">
            <UploadCloud class="h-4 w-4" />
            <span>上传笔记</span>
          </button>
        </div>
      </div>
    </section>

    <section class="grid gap-3 sm:grid-cols-3">
      <div class="metric-card">
        <FolderTree class="h-4 w-4 text-blue-600" />
        <span>{{ directories.length }}</span>
        <small>Directories</small>
      </div>
      <div class="metric-card">
        <FileText class="h-4 w-4 text-emerald-600" />
        <span>{{ total }}</span>
        <small>Notes Here</small>
      </div>
      <div class="metric-card">
        <Layers class="h-4 w-4 text-amber-600" />
        <span>{{ currentDirectoryId == null ? 'Root' : '#' + currentDirectoryId }}</span>
        <small>Current Directory</small>
      </div>
    </section>

    <section v-if="loading" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <div v-for="i in 8" :key="i" class="h-52 animate-pulse rounded-lg border border-white/10 bg-white/[0.03]" />
    </section>

    <section v-else-if="directories.length === 0 && notes.length === 0" class="rounded-lg border border-white/10 bg-white/[0.03] p-12 text-center">
      <FolderTree class="mx-auto mb-4 h-10 w-10 text-slate-600" />
      <p class="text-sm font-bold text-slate-300">当前目录还是空的</p>
      <div class="mt-5 flex justify-center gap-3">
        <button class="action-button secondary" @click="openDirectoryModal">新建目录</button>
        <button class="action-button primary" @click="openUploadModal">上传笔记</button>
      </div>
    </section>

    <template v-else>
      <section class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <article v-for="dir in directories" :key="`dir-${dir.id}`" class="float-card directory-card group">
          <button class="absolute inset-0 z-0" title="进入目录" @click="enterDirectory(dir)" />
          <div class="relative z-10 flex h-full flex-col">
            <div class="mb-4 flex items-start justify-between">
              <div class="flex h-11 w-11 items-center justify-center rounded-lg bg-blue-50 text-blue-700 ring-1 ring-blue-200">
                <FolderTree class="h-5 w-5" />
              </div>
              <span class="rounded-md border px-2 py-1 text-[10px] font-black uppercase tracking-[0.14em]" :class="topicStatus(dir).cls">
                {{ topicStatus(dir).label }}
              </span>
            </div>
            <h2 class="line-clamp-2 text-lg font-black leading-snug text-slate-950">{{ dir.topicName }}</h2>
            <p class="mt-2 text-sm text-slate-600">{{ dir.noteCount ?? 0 }} 篇直接挂载笔记</p>
            <div class="mt-auto flex items-center justify-between border-t border-slate-200 pt-4">
              <button class="card-link" @click.stop="enterDirectory(dir)">
                进入
                <ArrowRight class="h-3.5 w-3.5" />
              </button>
              <div class="relative z-20 flex gap-1">
                <button v-if="dir.isPass === 0" class="icon-button amber" title="撤销审核" @click.stop="handleCancelTopicAudit(dir)">
                  <ChevronLeft class="h-3.5 w-3.5" />
                </button>
                <button v-if="dir.isPass !== 1" class="icon-button emerald" title="提交审核" @click.stop="handleSubmitTopicAudit(dir)">
                  <Send class="h-3.5 w-3.5" />
                </button>
                <button class="icon-button danger" title="删除目录" @click.stop="handleDeleteDirectory(dir)">
                  <Trash2 class="h-3.5 w-3.5" />
                </button>
              </div>
            </div>
          </div>
        </article>

        <article v-for="note in notes" :key="`note-${note.id}`" class="float-card note-card">
          <div class="mb-4 flex items-start justify-between gap-3">
            <div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200">
              <FileText class="h-5 w-5" />
            </div>
            <span class="inline-flex items-center gap-1 rounded-md border px-2 py-1 text-[10px] font-black uppercase tracking-[0.12em]" :class="getNoteStatusInfo(note.status).cls">
              <component :is="getStatusIcon(getNoteStatusInfo(note.status).icon)" class="h-3 w-3" />
              {{ getNoteStatusInfo(note.status).label }}
            </span>
          </div>

          <h2 class="line-clamp-2 text-lg font-black leading-snug text-slate-950">{{ note.title }}</h2>
          <p class="mt-2 line-clamp-2 min-h-10 text-sm leading-5 text-slate-600">{{ note.description || '这篇笔记还没有描述。' }}</p>

          <div class="mt-4 flex flex-wrap gap-2 text-[10px] font-bold uppercase tracking-[0.12em] text-slate-600">
            <span class="mini-chip"><Hash class="h-3 w-3" />{{ note.tagCount ?? 0 }}</span>
            <span class="mini-chip"><Image class="h-3 w-3" />{{ note.imageCount ?? 0 }}</span>
            <span class="mini-chip"><Link class="h-3 w-3" />{{ note.eachNoteCount ?? 0 }}</span>
            <span class="mini-chip">{{ formatBytes(note.mdFileSize) }}</span>
          </div>

          <div class="mt-auto border-t border-slate-200 pt-4">
            <div class="mb-3 flex items-center justify-between text-xs text-slate-500">
              <span>{{ formatDate(note.createTime) }}</span>
              <span>#{{ note.id }}</span>
            </div>
            <div class="flex flex-wrap gap-2">
              <button class="icon-button" title="查看源文件" @click="handleViewSource(note)"><Eye class="h-3.5 w-3.5" /></button>
              <button class="icon-button" title="关联映射" @click="openRelations(note)"><Network class="h-3.5 w-3.5" /></button>
              <button v-if="note.status >= NoteStatusCode.CONVERTED" class="icon-button emerald" title="查看 HTML" @click="openNoteHtml(note)"><FileCode class="h-3.5 w-3.5" /></button>
              <button v-if="note.status === NoteStatusCode.READY_TO_CONVERT" class="icon-button emerald" title="转换" @click="handleConvert(note)"><RefreshCw class="h-3.5 w-3.5" /></button>
              <button v-if="note.status === NoteStatusCode.CONVERTED" class="icon-button amber" title="提交审核" @click="handleSubmitAudit(note)"><Send class="h-3.5 w-3.5" /></button>
              <button v-if="note.status === NoteStatusCode.APPROVED" class="icon-button emerald" title="公开发布" @click="handlePublish(note)"><Globe class="h-3.5 w-3.5" /></button>
              <button v-if="note.status === NoteStatusCode.PUBLISHED" class="icon-button amber" title="取消公开" @click="handleUnpublish(note)"><XCircle class="h-3.5 w-3.5" /></button>
              <button v-if="note.status !== NoteStatusCode.PENDING_AUDIT && note.status !== NoteStatusCode.PUBLISHED" class="icon-button danger" title="删除" @click="handleDeleteNote(note)"><Trash2 class="h-3.5 w-3.5" /></button>
            </div>
          </div>
        </article>
      </section>

      <div v-if="notes.length > 0" class="flex items-center justify-between border-t border-white/10 pt-5">
        <span class="text-xs font-bold text-slate-300">共 {{ formatNumber(total) }} 篇笔记</span>
        <div class="flex items-center gap-1">
          <button class="pager-button" :disabled="currentPage <= 1" @click="changePage(currentPage - 1)">
            <ChevronLeft class="h-4 w-4" />
          </button>
          <button v-for="page in visiblePages()" :key="page" class="pager-button text-xs font-black" :class="{ active: page === currentPage }" @click="changePage(page)">
            {{ page }}
          </button>
          <button class="pager-button" :disabled="currentPage >= totalPages" @click="changePage(currentPage + 1)">
            <ChevronRight class="h-4 w-4" />
          </button>
        </div>
      </div>
    </template>

    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showUploadModal" class="fixed inset-0 z-50 bg-black/70 backdrop-blur-md" @click="closeUploadModal" />
      </Transition>
      <Transition name="modal">
        <div v-if="showUploadModal" class="fixed inset-0 z-50 flex items-center justify-center px-4 pointer-events-none">
          <div class="modal-card pointer-events-auto w-full max-w-xl rounded-lg border border-white/10 bg-[#050816] p-7 shadow-2xl">
            <div class="mb-6 flex items-center justify-between">
              <div>
                <h3 class="text-xl font-black text-white">上传 Markdown 笔记</h3>
                <p class="mt-1 text-xs text-slate-300">目标目录：{{ currentDirectoryName }}</p>
              </div>
              <button class="icon-button" @click="closeUploadModal"><X class="h-4 w-4" /></button>
            </div>
            <div
              class="upload-zone"
              :class="{ active: uploadDragging }"
              @click="($refs.fileInput as HTMLInputElement)?.click()"
              @dragover.prevent="uploadDragging = true"
              @dragleave.prevent="uploadDragging = false"
              @drop.prevent="handleFileDrop"
            >
              <template v-if="uploadFile">
                <FileText class="mb-3 h-8 w-8 text-blue-300" />
                <p class="font-bold text-white">{{ uploadFile.name }}</p>
                <p class="mt-1 text-xs text-slate-300">{{ formatBytes(uploadFile.size) }}</p>
              </template>
              <template v-else>
                <FileUp class="mb-3 h-8 w-8 text-blue-300" />
                <p class="font-bold text-white">点击或拖拽 `.md` 文件到这里</p>
                <p class="mt-1 text-xs text-slate-300">根目录上传会成为未归类笔记，目录内上传会直接挂到当前目录。</p>
              </template>
            </div>
            <input ref="fileInput" type="file" accept=".md,.markdown" class="hidden" @change="handleFileSelect" />
            <div class="mt-6 flex justify-end gap-3">
              <button class="action-button secondary" @click="closeUploadModal">取消</button>
              <button class="action-button primary" :disabled="!uploadFile || uploadSubmitting" @click="handleUpload">
                <Loader2 v-if="uploadSubmitting" class="h-4 w-4 animate-spin" />
                <span>{{ uploadSubmitting ? '上传中...' : '确认上传' }}</span>
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showDirectoryModal" class="fixed inset-0 z-50 bg-black/70 backdrop-blur-md" @click="closeDirectoryModal" />
      </Transition>
      <Transition name="modal">
        <div v-if="showDirectoryModal" class="fixed inset-0 z-50 flex items-center justify-center px-4 pointer-events-none">
          <form class="modal-card pointer-events-auto w-full max-w-md rounded-lg border border-white/10 bg-[#050816] p-7 shadow-2xl" @submit.prevent="handleCreateDirectory">
            <div class="mb-6 flex items-center justify-between">
              <div>
                <h3 class="text-xl font-black text-white">新建目录</h3>
                <p class="mt-1 text-xs text-slate-300">父级目录：{{ currentDirectoryName }}</p>
              </div>
              <button type="button" class="icon-button" @click="closeDirectoryModal"><X class="h-4 w-4" /></button>
            </div>
            <label class="mb-4 block">
              <span class="mb-2 block text-xs font-black uppercase tracking-[0.16em] text-slate-300">目录名称</span>
              <input v-model="directoryName" maxlength="25" required class="form-input" placeholder="例如：MySQL 内核" />
            </label>
            <label class="block">
              <span class="mb-2 block text-xs font-black uppercase tracking-[0.16em] text-slate-300">排序等级</span>
              <input v-model.number="directorySortOrder" type="number" class="form-input" placeholder="默认 0" />
            </label>
            <div class="mt-6 flex justify-end gap-3">
              <button type="button" class="action-button secondary" @click="closeDirectoryModal">取消</button>
              <button type="submit" class="action-button primary" :disabled="directorySubmitting">
                <Loader2 v-if="directorySubmitting" class="h-4 w-4 animate-spin" />
                <span>{{ directorySubmitting ? '保存中...' : '创建目录' }}</span>
              </button>
            </div>
          </form>
        </div>
      </Transition>
    </Teleport>

    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showSourceModal" class="fixed inset-0 z-[60] bg-black/85 backdrop-blur-md" @click="showSourceModal = false" />
      </Transition>
      <Transition name="modal">
        <div v-if="showSourceModal" class="fixed inset-0 z-[60] flex items-center justify-center px-4 pointer-events-none">
          <div class="modal-card pointer-events-auto flex max-h-[85vh] w-full max-w-3xl flex-col overflow-hidden rounded-lg border border-white/10 bg-[#050816] shadow-2xl">
            <div class="flex items-center justify-between border-b border-white/10 px-5 py-4">
              <h3 class="truncate text-sm font-black text-white">{{ sourceTitle }}</h3>
              <button class="icon-button" @click="showSourceModal = false"><X class="h-4 w-4" /></button>
            </div>
            <pre class="flex-1 overflow-y-auto p-5 text-sm leading-6 text-slate-300 whitespace-pre-wrap">{{ sourceContent }}</pre>
            <div class="flex justify-end gap-3 border-t border-white/10 px-5 py-4">
              <button class="action-button secondary" @click="showSourceModal = false">关闭</button>
              <button class="action-button primary" @click="handleEditSource">
                <PenLine class="h-4 w-4" />
                <span>编辑源文件</span>
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.directory-hero {
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.18), transparent 34rem),
    linear-gradient(135deg, rgba(255, 255, 255, 0.045), rgba(255, 255, 255, 0.015));
}

.crumb {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.035);
  padding: 0.35rem 0.65rem;
  color: #cbd5e1;
  transition: all 0.18s ease;
}

.crumb:hover {
  border-color: rgba(96, 165, 250, 0.42);
  color: #bfdbfe;
}

.action-button {
  display: inline-flex;
  height: 2.5rem;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  border-radius: 0.5rem;
  padding: 0 0.9rem;
  font-size: 0.875rem;
  font-weight: 800;
  transition: all 0.18s ease;
}

.action-button.primary {
  background: #2563eb;
  color: white;
}

.action-button.primary:hover {
  background: #3b82f6;
}

.action-button.secondary {
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.04);
  color: #cbd5e1;
}

.action-button.secondary:hover {
  border-color: rgba(96, 165, 250, 0.4);
  color: white;
}

.action-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.metric-card,
.float-card {
  border: 1px solid rgba(226, 232, 240, 0.95);
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.94)),
    #f8fafc;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.14);
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  border-radius: 0.5rem;
  padding: 1rem;
}

.metric-card span {
  color: #0f172a;
  font-size: 1.05rem;
  font-weight: 900;
}

.metric-card small {
  margin-left: auto;
  color: #64748b;
  font-size: 0.65rem;
  font-weight: 900;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.float-card {
  position: relative;
  min-height: 14rem;
  overflow: hidden;
  border-radius: 0.5rem;
  padding: 1.1rem;
  transition: transform 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.float-card:hover {
  transform: translateY(-3px);
  border-color: rgba(96, 165, 250, 0.62);
  background:
    linear-gradient(145deg, #ffffff, #f1f5f9),
    #f8fafc;
  box-shadow: 0 22px 52px rgba(15, 23, 42, 0.18);
}

.directory-card::before,
.note-card::before {
  content: '';
  position: absolute;
  inset: auto -25% -35% auto;
  width: 10rem;
  height: 10rem;
  border-radius: 999px;
  filter: blur(48px);
  opacity: 0.14;
}

.directory-card::before {
  background: #3b82f6;
}

.note-card::before {
  background: #10b981;
}

.note-card,
.directory-card > div {
  display: flex;
  flex-direction: column;
}

.mini-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  border-radius: 999px;
  border: 1px solid rgba(203, 213, 225, 0.9);
  background: rgba(241, 245, 249, 0.88);
  color: #475569;
  padding: 0.3rem 0.55rem;
}

.card-link {
  position: relative;
  z-index: 20;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: #2563eb;
  font-size: 0.78rem;
  font-weight: 900;
}

.icon-button {
  display: inline-flex;
  height: 2rem;
  min-width: 2rem;
  align-items: center;
  justify-content: center;
  border-radius: 0.45rem;
  border: 1px solid rgba(203, 213, 225, 0.9);
  background: rgba(248, 250, 252, 0.86);
  color: #475569;
  transition: all 0.18s ease;
}

.icon-button:hover {
  border-color: rgba(96, 165, 250, 0.72);
  background: rgba(239, 246, 255, 0.95);
  color: #2563eb;
}

.icon-button.emerald:hover {
  border-color: rgba(16, 185, 129, 0.52);
  color: #047857;
}

.icon-button.amber:hover {
  border-color: rgba(245, 158, 11, 0.58);
  color: #b45309;
}

.icon-button.danger:hover {
  border-color: rgba(244, 63, 94, 0.55);
  color: #be123c;
}

.pager-button {
  display: inline-flex;
  height: 2rem;
  width: 2rem;
  align-items: center;
  justify-content: center;
  border-radius: 0.45rem;
  color: #cbd5e1;
  transition: all 0.18s ease;
}

.pager-button:hover,
.pager-button.active {
  background: rgba(59, 130, 246, 0.16);
  color: #bfdbfe;
}

.pager-button:disabled {
  cursor: not-allowed;
  opacity: 0.35;
}

.upload-zone {
  display: flex;
  min-height: 11rem;
  cursor: pointer;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 0.7rem;
  border: 2px dashed rgba(255, 255, 255, 0.18);
  background: rgba(255, 255, 255, 0.035);
  padding: 1.5rem;
  text-align: center;
  transition: all 0.18s ease;
}

.upload-zone:hover,
.upload-zone.active {
  border-color: rgba(96, 165, 250, 0.55);
  background: rgba(59, 130, 246, 0.08);
}

.form-input {
  width: 100%;
  border-radius: 0.55rem;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(0, 0, 0, 0.26);
  padding: 0.75rem 0.85rem;
  color: white;
  outline: none;
  transition: all 0.18s ease;
}

.form-input:focus {
  border-color: rgba(96, 165, 250, 0.48);
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.12);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.22s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.24s ease, transform 0.28s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
  transform: translateY(14px) scale(0.97);
}
</style>
