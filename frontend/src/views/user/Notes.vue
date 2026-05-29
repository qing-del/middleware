<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { noteApi, getNoteStatusInfo, NoteStatusCode } from '@/api/notes'
import type { NoteItem, PageResult, NoteQueryParams, NoteDiffVO, NoteRelationDetailVO } from '@/api/notes'
import { topicApi } from '@/api/topics'
import type { TopicItem } from '@/api/topics'
import {
  FileText, Search, FileUp, Globe, Trash2, Loader2, ChevronLeft, ChevronRight,
  Hash, Image, Link, Network, Layers, Eye, Wrench, RefreshCw, RotateCcw, FileDiff,
  CornerUpLeft, AlertCircle, Clock, CheckCircle2, XCircle, FileEdit,
  AlertTriangle, UploadCloud, ArrowRight, X, FolderTree, ChevronDown,
  Send, FilePlus, FileCode, HelpCircle, Ban, PenLine
} from 'lucide-vue-next'

const router = useRouter()
const loading = ref(true)
const noteList = ref<NoteItem[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(15)

const searchKeyword = ref('')
const filterTopicId = ref('')
const filterStatus = ref('')

const selectedIds = ref<Set<number>>(new Set())

const showUploadModal = ref(false)
const uploadFile = ref<File | null>(null)
const uploadTopicId = ref<number | undefined>(undefined)
const uploadDragging = ref(false)
const uploadMode = ref<'create' | 'modify'>('create')
const uploadTargetNoteId = ref<number | null>(null)
const uploadTargetTitle = ref('')
const uploadSubmitting = ref(false)
const topicList = ref<TopicItem[]>([])

const searchMode = ref<'personal' | 'global'>('personal')

const showSourceModal = ref(false)
const sourceContent = ref('')
const sourceTitle = ref('')
const currentSourceId = ref<number | null>(null)

// ── Relation Details Cache ───────────────────────
const relationCache = ref<Record<number, NoteRelationDetailVO>>({})

function getNoteRelationCount(note: NoteItem, kind: 'tag' | 'image' | 'link'): number {
  const cached = relationCache.value[note.id]
  if (cached) {
    if (kind === 'tag') return cached.tags?.length ?? 0
    if (kind === 'image') return cached.images?.length ?? 0
    return cached.eachNotes?.length ?? 0
  }
  // Fallback to server-provided counts
  if (kind === 'tag') return note.tagCount ?? 0
  if (kind === 'image') return note.imageCount ?? 0
  return note.eachNoteCount ?? 0
}

async function ensureRelations(note: NoteItem) {
  if (relationCache.value[note.id]) return relationCache.value[note.id]
  try {
    const res = await noteApi.getRelations(note.id)
    relationCache.value[note.id] = res

    // Sync data back to note item for tags (helps initial rendering of tag list if available)
    if (res.tags?.length && (!note.tags || !note.tags.length)) {
      note.tags = res.tags.filter(t => !t.isMissing).map(t => t.tagName || t.parsedTagName)
    }
    return res
  } catch {
    return null
  }
}

const isBatchMode = computed(() => selectedIds.value.size > 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const isModifyUpload = computed(() => uploadMode.value === 'modify')
const uploadModalTitle = computed(() => isModifyUpload.value ? '修改笔记源文件' : '上传 Markdown 笔记')
const uploadModalSubtitle = computed(() => isModifyUpload.value ? 'Replace Source & Generate Diff' : 'Upload Documentation')
const uploadButtonText = computed(() => isModifyUpload.value ? '上传新版本并生成 Diff' : '确认读取并解析')

// ── Global tooltip ────────────────────────────────
const tooltipVisible = ref(false)
const tooltipText = ref('')
const tooltipX = ref(0)
const tooltipY = ref(0)
const tooltipColor = ref('blue')

async function showRelationTooltip(e: MouseEvent, note: NoteItem, kind: 'tags' | 'images' | 'links' | 'status') {
  const colorMap: Record<string, string> = { tags: 'purple', images: 'amber', links: 'emerald', status: 'blue' }
  const color = kind === 'status'
    ? (note.status === NoteStatusCode.REJECTED ? 'rose' : (note.status === NoteStatusCode.PENDING_INFO || note.status === NoteStatusCode.PENDING_AUDIT ? 'amber' : 'blue'))
    : colorMap[kind]

  // 1. Show immediate basic info
  tooltipText.value = kind === 'status' ? getStatusTooltip(note) : getRelationTooltip(note, kind)
  tooltipColor.value = color
  tooltipVisible.value = true
  positionTooltip(e)

  // 2. Fetch/Ensure details in background
  const details = await ensureRelations(note)
  if (details && tooltipVisible.value) {
    // 3. Update with details if still visible and same note
    if (kind === 'status') {
      tooltipText.value = getDetailedStatusTooltip(note, details)
    } else {
      tooltipText.value = getDetailedRelationTooltip(note, kind, details)
    }
  }
}

function getDetailedRelationTooltip(_note: NoteItem, kind: 'tags' | 'images' | 'links', relations: NoteRelationDetailVO): string {
  if (kind === 'tags') {
    const list = relations.tags.map(t => `#${t.tagName || t.parsedTagName}${t.isMissing ? ' (缺失)' : ''}`)
    return list.length ? `关联标签 (${list.length})：\n${list.join('\n')}` : '无关联标签。'
  }
  if (kind === 'images') {
    const list = relations.images.map(img => `• ${img.filename || img.parsedImageName}${img.isMissing ? ' (缺失)' : ' (OK)'}`)
    return list.length ? `关联图片 (${list.length})：\n${list.join('\n')}` : '无关联图片。'
  }
  if (kind === 'links') {
    const list = relations.eachNotes.map(link => `[[${link.targetNoteTitle || link.parsedNoteName}]]${link.isMissing ? ' (缺失)' : ' (OK)'}`)
    return list.length ? `双链关联 (${list.length})：\n${list.join('\n')}` : '无双链关联。'
  }
  return ''
}

function getDetailedStatusTooltip(note: NoteItem, relations: NoteRelationDetailVO): string {
  if (note.status === NoteStatusCode.PENDING_INFO) {
    const missing: string[] = []
    relations.tags.filter(t => t.isMissing).forEach(t => missing.push(`标签: ${t.parsedTagName}`))
    relations.images.filter(i => i.isMissing).forEach(i => missing.push(`图片: ${i.parsedImageName}`))
    relations.eachNotes.filter(e => e.isMissing).forEach(e => missing.push(`双链: ${e.parsedNoteName}`))
    return missing.length ? `检测到以下缺失项：\n${missing.join('\n')}` : getStatusTooltip(note)
  }
  return getStatusTooltip(note)
}

function positionTooltip(e: MouseEvent) {
  tooltipX.value = e.clientX
  tooltipY.value = e.clientY
}

function hideTooltip() {
  tooltipVisible.value = false
}

function showAlert(msg: string) { window.alert(msg) }
function showConfirm(msg: string): boolean { return window.confirm(msg) }

function formatBytes(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, index)).toFixed(index > 0 ? 1 : 0) + ' ' + units[index]
}

function formatNumber(n: number): string {
  return n.toLocaleString()
}

function visiblePages(): number[] {
  const pages: number[] = []
  const tp = totalPages.value
  const cp = currentPage.value
  let start = Math.max(1, cp - 2)
  const end = Math.min(tp, start + 4)
  if (end - start < 4) start = Math.max(1, end - 4)
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
}

function getStatusIcon(iconName: string) {
  const map: Record<string, any> = {
    FilePlus, AlertTriangle, RefreshCw, FileCode, Clock, CheckCircle2, Globe, XCircle, Trash2, HelpCircle, FileEdit
  }
  return map[iconName] ?? HelpCircle
}

function isStatusEmphasized(status: number): boolean {
  return [
    NoteStatusCode.PENDING_INFO,
    NoteStatusCode.PENDING_AUDIT,
    NoteStatusCode.REJECTED
  ].includes(status as any)
}

function getStatusEmphasisClass(status: number): string {
  if (status === NoteStatusCode.REJECTED) return 'status-breathe status-glow-rose'
  if (status === NoteStatusCode.PENDING_INFO || status === NoteStatusCode.PENDING_AUDIT) {
    return 'status-breathe status-glow-amber'
  }
  return 'status-chip-steady'
}

function getStatusTooltip(note: NoteItem): string {
  switch (note.status) {
    case NoteStatusCode.NEW:
      return '笔记已创建，可继续检查关联资源并推进后续流程。'
    case NoteStatusCode.PENDING_INFO:
      return `当前仍有 ${note.missingCount ?? 0} 项关联缺失，建议先补全图片、标签或双链资源。`
    case NoteStatusCode.READY_TO_CONVERT:
      return '关联检查已通过，可以执行 Markdown 转换。'
    case NoteStatusCode.CONVERTED:
      return '阅读版 HTML 已生成，可以预览并提交审核。'
    case NoteStatusCode.PENDING_AUDIT:
      return '已提交审核，当前阶段不可修改或删除该笔记。'
    case NoteStatusCode.APPROVED:
      return '审核已通过，等待你决定是否公开发布。'
    case NoteStatusCode.PUBLISHED:
      return '笔记已公开，外部访问链路已经生效。'
    case NoteStatusCode.REJECTED:
      return '审核未通过，请根据反馈修正后重新提交。'
    default:
      return '当前状态暂无额外说明。'
  }
}

function getRelationTooltip(note: NoteItem, kind: 'tags' | 'images' | 'links'): string {
  if (kind === 'tags') {
    if (note.missingCount > 0 && (note.missingInfoMask & 1) !== 0) {
      return `标签关联 ${note.tagCount ?? 0} 项，其中有标签处于缺失状态。`
    }
    return `标签关联 ${note.tagCount ?? 0} 项，当前检查通过。`
  }
  if (kind === 'links') {
    if (note.missingCount > 0 && (note.missingInfoMask & 4) !== 0) {
      return `双链关联 ${note.eachNoteCount ?? 0} 项，其中有内联笔记处于缺失状态。`
    }
    return `双链关联 ${note.eachNoteCount ?? 0} 项，当前检查通过。`
  }
  if (note.missingCount > 0 && (note.missingInfoMask & 2) !== 0) {
    return `图片关联 ${note.imageCount ?? 0} 项，其中仍有 ${note.missingCount} 项存在缺失或异常。`
  }
  return `图片关联 ${note.imageCount ?? 0} 项，当前检查通过。`
}

async function fetchTopics() {
  try {
    const res = await topicApi.getList({ pageSize: 100 })
    topicList.value = (res as unknown as { records: TopicItem[] }).records ?? []
  } catch {
    // non-critical
  }
}

async function preloadRelations(notes: NoteItem[]) {
  // Use Promise.all to fetch concurrently for better speed, 
  // or a sequential loop if we want to reduce server peak load.
  // Given pageSize is small (15), concurrent is fine.
  await Promise.all(notes.map(n => ensureRelations(n)))
}

async function fetchNotes() {
  loading.value = true
  try {
    const params: NoteQueryParams = {
      keyword: searchKeyword.value || undefined,
      topicId: filterTopicId.value ? Number(filterTopicId.value) : undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    }

    const apiMethod = searchMode.value === 'global'
      ? noteApi.searchNotes(params)
      : noteApi.getList(params)

    const res = await apiMethod
    let records = (res as unknown as PageResult<NoteItem>).records ?? []
    total.value = (res as unknown as PageResult<NoteItem>).total ?? 0

    if (filterStatus.value !== '') {
      const code = Number(filterStatus.value)
      records = records.filter(n => n.status === code)
      total.value = records.length
    }

    noteList.value = records
    
    // Auto-load relations for current page
    if (records.length > 0) {
      void preloadRelations(records)
    }
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  fetchNotes()
}

function handlePageChange(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  currentPage.value = page
  fetchNotes()
}

function handleTabChange(statusCode: string) {
  filterStatus.value = statusCode
  currentPage.value = 1
  fetchNotes()
}

function toggleSelectAll(checked: boolean) {
  if (checked) noteList.value.forEach(n => selectedIds.value.add(n.id))
  else selectedIds.value.clear()
}

function toggleSelect(id: number) {
  selectedIds.value.has(id) ? selectedIds.value.delete(id) : selectedIds.value.add(id)
}

function resetUploadState() {
  uploadFile.value = null
  uploadTopicId.value = undefined
  uploadDragging.value = false
  uploadMode.value = 'create'
  uploadTargetNoteId.value = null
  uploadTargetTitle.value = ''
}

function openCreateUploadModal() {
  resetUploadState()
  showUploadModal.value = true
}

function openModifyUploadModal(note: NoteItem) {
  resetUploadState()
  uploadMode.value = 'modify'
  uploadTargetNoteId.value = note.id
  uploadTargetTitle.value = note.title
  showUploadModal.value = true
}

function closeUploadModal() {
  showUploadModal.value = false
  resetUploadState()
}

function handleFileDrop(e: DragEvent) {
  uploadDragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file?.name.endsWith('.md')) uploadFile.value = file
}

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) uploadFile.value = file
}

function getDiffChanges(oldItems: string[] = [], newItems: string[] = []) {
  const oldSet = new Set(oldItems)
  const newSet = new Set(newItems)
  return {
    added: newItems.filter(item => !oldSet.has(item)),
    removed: oldItems.filter(item => !newSet.has(item)),
  }
}

function formatDiffSection(label: string, oldItems: string[] = [], newItems: string[] = []) {
  const { added, removed } = getDiffChanges(oldItems, newItems)
  if (!added.length && !removed.length) return `${label}：无变化`
  const changes: string[] = []
  if (added.length) changes.push(`新增 ${added.join('、')}`)
  if (removed.length) changes.push(`移除 ${removed.join('、')}`)
  return `${label}：${changes.join('；')}`
}

function buildModifyDiffSummary(diff: NoteDiffVO): string {
  return [
    '新版本已上传，检测到以下关联变化：',
    formatDiffSection('标签', diff.oldTags, diff.newTags),
    formatDiffSection('图片', diff.oldImages, diff.newImages),
    formatDiffSection('双链', diff.oldNoteNames, diff.newNoteNames),
  ].join('\n')
}

async function handleUpload() {
  if (!uploadFile.value || uploadSubmitting.value) return
  const modifying = isModifyUpload.value
  uploadSubmitting.value = true
  try {
    if (modifying) {
      const noteId = uploadTargetNoteId.value
      if (noteId == null) {
        showAlert('缺少目标笔记ID，无法执行修改。')
        return
      }
      const diff = await noteApi.modifyFile(noteId, uploadFile.value)
      closeUploadModal()
      showAlert(buildModifyDiffSummary(diff))
      if (showConfirm('已生成待确认变更，是否立即确认并应用到正式版本？')) {
        await noteApi.confirmChange(noteId, true)
        showAlert('变更已确认并生效。')
      } else {
        showAlert('变更已保留为待确认状态，可稍后确认或回滚。')
      }
      await fetchNotes()
      return
    }

    const res = await noteApi.uploadNote(uploadFile.value, uploadTopicId.value)
    closeUploadModal()
    await fetchNotes()
    const missing = (res as unknown as { missingImages?: string[]; missingTags?: string[]; missingNoteNames?: string[] })
    const totalMissing = (missing.missingImages?.length ?? 0) + (missing.missingTags?.length ?? 0) + (missing.missingNoteNames?.length ?? 0)
    if (totalMissing > 0) {
      showAlert(`笔记上传成功，但仍有 ${totalMissing} 项关联资源需要补全。`)
    }
  } catch {
    showAlert(modifying ? '源文件修改失败，请重试。' : '上传失败，请重试。')
  } finally {
    uploadSubmitting.value = false
  }
}

async function handleDelete(id: number) {
  if (!showConfirm('确定删除这篇笔记吗？此操作不可恢复。')) return
  await noteApi.deleteNote(id)
  await fetchNotes()
}

async function handlePublish(id: number) {
  await noteApi.publish(id, 1)
  await fetchNotes()
}

async function handleUnpublish(id: number) {
  await noteApi.publish(id, 0)
  await fetchNotes()
}

async function handleSubmitAudit(id: number) {
  await noteApi.submitAudit(id)
  await fetchNotes()
}

async function handleCancelAudit(id: number) {
  if (!showConfirm('确认撤销审核申请吗？撤销后笔记状态会回退到已转换。')) return
  try {
    await noteApi.cancelAudit(id)
    await fetchNotes()
  } catch {
    showAlert('撤销审核失败')
  }
}

async function handleConvert(id: number) {
  try {
    await noteApi.convertNote(id)
    await fetchNotes()
  } catch {
    showAlert('转换失败，请确认笔记没有关联异常。')
  }
}

async function handleDeleteConverted(id: number) {
  if (!showConfirm('确定删除转换缓存吗？笔记将回退为"待转换"状态，需重新执行转换。')) return
  try {
    await noteApi.deleteConverted(id)
    await fetchNotes()
  } catch {
    showAlert('删除转换缓存失败，请重试。')
  }
}

async function handleCheckRelations(id: number) {
  try {
    const res = await noteApi.checkRelations(id)
    const result = res as unknown as {
      complete: boolean
      missingCount: number
      missingTags: string[]
      missingImages: string[]
      missingNoteNames: string[]
    }
    if (result.complete) {
      showAlert('关联完整性校验通过。')
    } else {
      const lines: string[] = [`仍有 ${result.missingCount} 项关联需要补全：`]
      if (result.missingTags?.length) lines.push(`\n标签缺失：${result.missingTags.join('、')}`)
      if (result.missingImages?.length) lines.push(`\n图片缺失：${result.missingImages.join('、')}`)
      if (result.missingNoteNames?.length) lines.push(`\n双链缺失：${result.missingNoteNames.join('、')}`)
      showAlert(lines.join(''))
    }
    await fetchNotes()
  } catch {
    showAlert('校验失败，请重试。')
  }
}

async function handleViewSource(id: number) {
  try {
    const src = await noteApi.getSource(id)
    const note = noteList.value.find(n => n.id === id)
    currentSourceId.value = id
    sourceTitle.value = note?.title ?? '笔记源文件'
    sourceContent.value = src as unknown as string
    showSourceModal.value = true
  } catch {
    showAlert('无法获取源文件。')
  }
}

function handleEditSource() {
  if (currentSourceId.value == null) return
  const id = currentSourceId.value
  showSourceModal.value = false
  router.push({ name: 'UserNoteEdit', params: { noteId: id }, query: { title: sourceTitle.value } })
}

function handleViewHtml(id: number) {
  router.push(`/user/notes/${id}`)
}

function handleViewDiff(id: number) {
  router.push(`/user/notes/${id}/diff`)
}

function handleOpenRelations(id: number) {
  router.push(`/user/notes/${id}/relations`)
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) return
  if (!showConfirm(`确定删除已选择的 ${selectedIds.value.size} 篇笔记吗？`)) return
  for (const id of selectedIds.value) {
    try {
      await noteApi.deleteNote(id)
    } catch {
      // continue
    }
  }
  selectedIds.value.clear()
  await fetchNotes()
}

async function handleBatchPublish() {
  if (selectedIds.value.size === 0) return
  let count = 0
  for (const id of selectedIds.value) {
    try {
      await noteApi.publish(id, 1)
      count++
    } catch {
      // skip
    }
  }
  selectedIds.value.clear()
  await fetchNotes()
  if (count > 0) showAlert(`成功发布了 ${count} 篇笔记。`)
}

function toggleGlobalSearch() {
  searchMode.value = searchMode.value === 'personal' ? 'global' : 'personal'
}

onMounted(() => {
  fetchTopics()
  fetchNotes()
})
</script>

<template>
  <div class="relative max-w-[1400px] mx-auto space-y-6 pb-36 md:pb-40">
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 relative z-10">
      <div class="flex items-center space-x-3">
        <div class="p-2 rounded-xl bg-blue-500/10 border border-blue-500/20 text-blue-400">
          <FileText class="w-5 h-5" />
        </div>
        <div>
          <h2 class="text-xl font-bold text-white">核心数字资产</h2>
          <p class="text-xs text-slate-400 mt-0.5">管理你的 Markdown 笔记，处理关联异常并推进审核与发布。</p>
        </div>
      </div>

      <div class="flex items-center space-x-4">
        <div class="flex items-center space-x-2.5 cursor-pointer group" @click="toggleGlobalSearch">
          <span class="text-xs font-bold text-slate-400 group-hover:text-blue-300 transition-colors">{{ searchMode === 'personal' ? '个人检索' : '全局搜索' }}</span>
          <div class="relative p-[1px] rounded-full overflow-hidden">
            <div class="absolute inset-0 bg-[conic-gradient(from_0deg,transparent_0_340deg,rgba(59,130,246,0.8)_360deg)] animate-[spin_2s_linear_infinite] opacity-0 group-hover:opacity-100 transition-opacity" />
            <div class="relative w-10 h-5 rounded-full flex items-center px-0.5 transition-colors duration-300 z-10" :class="searchMode === 'global' ? 'bg-blue-500 border border-blue-500/50' : 'bg-black/50 border border-white/10'">
              <div class="w-4 h-4 rounded-full transform transition-all duration-300" :class="searchMode === 'global' ? 'translate-x-5 bg-white shadow-[0_0_10px_rgba(255,255,255,0.8)]' : 'translate-x-0 bg-slate-400'" />
            </div>
          </div>
          <Globe class="w-4 h-4 transition-colors" :class="searchMode === 'global' ? 'text-blue-400' : 'text-slate-500 group-hover:text-blue-400'" />
        </div>

        <div class="w-px h-5 bg-white/10 mx-1" />

        <div class="relative group flex items-center bg-black/20 border border-white/10 rounded-xl overflow-hidden transition-all duration-300 ease-out w-9 hover:w-28 focus-within:!w-64 focus-within:bg-black/40 focus-within:border-blue-500/50 focus-within:ring-2 focus-within:ring-blue-500/10 h-9">
          <label class="w-9 h-full flex-shrink-0 flex items-center justify-center text-slate-500 group-hover:text-slate-300 group-focus-within:text-blue-400 transition-colors cursor-pointer z-10">
            <Search class="w-4 h-4" />
          </label>
          <input v-model="searchKeyword" type="text" placeholder="搜索笔记标题或内容..." class="absolute left-9 w-[220px] h-full bg-transparent text-sm text-white placeholder:text-slate-500 outline-none opacity-0 group-hover:opacity-100 focus-within:!opacity-100 transition-opacity duration-300 pr-4" @keyup.enter="handleSearch" />
        </div>

        <button class="group relative px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-bold rounded-xl shadow-[0_0_15px_rgba(16,185,129,0.4)] transition-all overflow-hidden flex items-center space-x-2" @click="$router.push('/user/notes/new')">
          <div class="absolute inset-0 bg-[linear-gradient(to_right,transparent,rgba(255,255,255,0.2),transparent)] -translate-x-[150%] group-hover:translate-x-[150%] transition-transform duration-700 ease-out" />
          <FilePlus class="w-4 h-4" />
          <span>新建笔记</span>
        </button>
        <button class="group relative px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-bold rounded-xl shadow-[0_0_15px_rgba(59,130,246,0.4)] transition-all overflow-hidden flex items-center space-x-2" @click="openCreateUploadModal">
          <div class="absolute inset-0 bg-[linear-gradient(to_right,transparent,rgba(255,255,255,0.2),transparent)] -translate-x-[150%] group-hover:translate-x-[150%] transition-transform duration-700 ease-out" />
          <FileUp class="w-4 h-4" />
          <span>上传笔记</span>
        </button>
      </div>
    </div>

    <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 relative z-10">
      <div class="flex gap-2 bg-black/20 p-1 rounded-xl border border-white/5 flex-wrap">
        <button
          v-for="tab in [
            { id: '', label: '全部笔记', icon: FileText },
            { id: String(NoteStatusCode.PUBLISHED), label: '已公开', icon: Globe },
            { id: String(NoteStatusCode.PENDING_AUDIT), label: '审核中', icon: Clock },
            { id: String(NoteStatusCode.PENDING_INFO), label: '关联异常', icon: AlertTriangle },
          ]"
          :key="tab.id"
          class="px-4 py-1.5 rounded-lg text-xs font-bold transition-colors flex items-center gap-1.5"
          :class="filterStatus === tab.id ? 'bg-white/10 text-white shadow-sm' : 'text-slate-400 hover:text-white'"
          @click="handleTabChange(tab.id)"
        >
          <component :is="tab.icon" class="w-3.5 h-3.5" />
          {{ tab.label }}
        </button>
      </div>

      <div class="relative group min-w-[180px]">
        <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-500">
          <FolderTree class="w-3.5 h-3.5" />
        </div>
        <select v-model="filterTopicId" class="w-full bg-black/20 border border-white/[0.05] shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] rounded-lg py-2 pl-9 pr-4 outline-none focus:border-blue-500/50 transition-all text-xs font-bold text-slate-300 appearance-none cursor-pointer" @change="handleSearch">
          <option value="" class="bg-[#0b0d14]">全部归属主题</option>
          <option v-for="t in topicList" :key="t.id" :value="t.id" class="bg-[#0b0d14]">{{ t.topicName }}</option>
        </select>
        <div class="absolute inset-y-0 right-0 pr-3 flex items-center pointer-events-none text-slate-500">
          <ChevronDown class="w-3.5 h-3.5" />
        </div>
      </div>
    </div>

    <Transition name="batch-float">
      <div v-if="isBatchMode" class="fixed inset-x-0 bottom-6 z-40 px-4 sm:px-6 pointer-events-none">
        <div class="floating-batch-bar glass-panel mx-auto flex max-w-3xl flex-col gap-3 rounded-2xl border border-blue-500/20 px-4 py-3 shadow-[0_18px_45px_rgba(2,6,23,0.48),0_0_30px_rgba(59,130,246,0.12)] pointer-events-auto sm:flex-row sm:items-center sm:justify-between">
          <div class="flex items-center space-x-3">
            <span class="flex h-2.5 w-2.5 relative">
              <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75" />
              <span class="relative inline-flex rounded-full h-2.5 w-2.5 bg-blue-500 shadow-[0_0_12px_rgba(59,130,246,0.7)]" />
            </span>
            <span class="text-sm font-bold text-blue-300">已选中 <span class="text-white mx-1">{{ selectedIds.size }}</span> 篇笔记</span>
          </div>
          <div class="flex items-center justify-end space-x-2">
            <button class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500 hover:text-white transition-all text-xs font-bold border border-emerald-500/20 hover:-translate-y-0.5" @click="handleBatchPublish">
              <Globe class="w-3.5 h-3.5" />
              <span>批量发布</span>
            </button>
            <button class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-rose-500/10 text-rose-400 hover:bg-rose-500 hover:text-white transition-all text-xs font-bold border border-rose-500/20 hover:-translate-y-0.5" @click="handleBatchDelete">
              <Trash2 class="w-3.5 h-3.5" />
              <span>批量删除</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <div class="glass-panel rounded-2xl border border-white/10 relative z-10 shadow-2xl">
      <div class="overflow-x-auto rounded-2xl">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr>
              <th class="px-5 py-4 border-b border-white/5 w-10">
                <input type="checkbox" class="glass-checkbox" :checked="selectedIds.size === noteList.length && noteList.length > 0" @change="toggleSelectAll(($event.target as HTMLInputElement).checked)" />
              </th>
              <th class="px-5 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider w-[32%]">核心资产 (Title & Meta)</th>
              <th class="px-5 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">关联矩阵 (Relations)</th>
              <th class="px-5 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">笔记状态 (Status)</th>
              <th class="px-5 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider text-right">控制台 (Actions)</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr v-if="loading">
              <td colspan="5" class="px-6 py-16 text-center">
                <Loader2 class="w-6 h-6 text-blue-400 animate-spin mx-auto mb-3" />
                <span class="text-xs text-slate-500">加载中...</span>
              </td>
            </tr>
            <tr v-else-if="noteList.length === 0">
              <td colspan="5" class="px-6 py-16 text-center text-sm text-slate-500">暂无笔记数据，点击右上角“上传笔记”开始创建。</td>
            </tr>
            <tr
              v-for="note in noteList"
              :key="note.id"
              class="hover:bg-white/[0.02] transition-colors group cursor-pointer"
              :class="{ 'bg-rose-500/[0.01]': note.status === NoteStatusCode.REJECTED || note.status === NoteStatusCode.PENDING_INFO }"
              @dblclick="handleViewSource(note.id)"
            >
              <td class="px-5 py-4" @click.stop>
                <input type="checkbox" class="glass-checkbox" :checked="selectedIds.has(note.id)" @change="toggleSelect(note.id)" />
              </td>
              <td class="px-5 py-4">
                <div class="flex flex-col">
                  <div class="flex items-center space-x-2">
                    <h3 class="text-sm font-bold text-slate-200 group-hover:text-blue-300 transition-colors truncate max-w-[260px]">{{ note.title }}</h3>
                    <span v-if="note.mdFileSize" class="text-[9px] font-mono text-slate-500 bg-black/40 px-1.5 py-0.5 rounded border border-white/5 flex-shrink-0">{{ formatBytes(note.mdFileSize) }}</span>
                    <span v-if="searchMode === 'global'" class="text-[9px] text-slate-600 bg-black/30 px-1.5 py-0.5 rounded border border-white/5 flex-shrink-0">UID:{{ note.userId }}</span>
                  </div>
                  <div class="flex items-center space-x-2 mt-2 flex-wrap gap-y-1">
                    <span v-if="note.topicName" class="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold text-indigo-300 bg-indigo-500/10 border border-indigo-500/20">
                      <Layers class="w-3 h-3 mr-1" /> {{ note.topicName }}
                    </span>
                    <template v-if="note.tags && note.tags.length">
                      <span v-for="(tag, ti) in (note.tags as unknown as string[])" :key="ti" class="mini-tag px-2 py-0.5 rounded text-[10px] font-bold text-slate-400 border border-white/10"># {{ tag }}</span>
                    </template>
                    <span v-else-if="getNoteRelationCount(note, 'tag') > 0" class="text-[10px] text-slate-600">含 {{ getNoteRelationCount(note, 'tag') }} 个标签</span>
                    <span v-else class="text-[10px] text-slate-600"># 独立笔记 (无标签)</span>
                  </div>
                </div>
              </td>
              <td class="px-5 py-4">
                <div class="flex items-center space-x-3 flex-wrap gap-y-1">
                  <span v-if="(note.missingInfoMask & 1) !== 0" tabindex="0" class="relation-chip status-breathe status-glow-amber inline-flex items-center space-x-1 rounded-md border border-amber-500/20 bg-amber-500/10 px-2 py-1 text-xs text-amber-500 font-bold"
                    @mouseenter="e => showRelationTooltip(e, note, 'tags')"
                    @mousemove="positionTooltip"
                    @mouseleave="hideTooltip">
                    <Hash class="w-3.5 h-3.5" />
                    <span>{{ getNoteRelationCount(note, 'tag') }}</span>
                  </span>
                  <span v-else tabindex="0" class="relation-chip inline-flex items-center space-x-1 rounded-md border border-white/10 bg-white/[0.02] px-2 py-1 text-xs text-slate-400"
                    @mouseenter="e => showRelationTooltip(e, note, 'tags')"
                    @mousemove="positionTooltip"
                    @mouseleave="hideTooltip">
                    <Hash class="w-3.5 h-3.5 text-purple-400" />
                    <span>{{ getNoteRelationCount(note, 'tag') }}</span>
                  </span>
                  <span v-if="(note.missingInfoMask & 2) !== 0" tabindex="0" class="relation-chip status-breathe status-glow-amber inline-flex items-center space-x-1 rounded-md border border-amber-500/20 bg-amber-500/10 px-2 py-1 text-xs text-amber-500 font-bold"
                    @mouseenter="e => showRelationTooltip(e, note, 'images')"
                    @mousemove="positionTooltip"
                    @mouseleave="hideTooltip">
                    <Image class="w-3.5 h-3.5" />
                    <span>{{ getNoteRelationCount(note, 'image') }}</span>
                  </span>
                  <span v-else tabindex="0" class="relation-chip inline-flex items-center space-x-1 rounded-md border border-white/10 bg-white/[0.02] px-2 py-1 text-xs text-slate-400"
                    @mouseenter="e => showRelationTooltip(e, note, 'images')"
                    @mousemove="positionTooltip"
                    @mouseleave="hideTooltip">
                    <Image class="w-3.5 h-3.5 text-blue-400" />
                    <span>{{ getNoteRelationCount(note, 'image') }}</span>
                  </span>
                  <span v-if="(note.missingInfoMask & 4) !== 0" tabindex="0" class="relation-chip status-breathe status-glow-amber inline-flex items-center space-x-1 rounded-md border border-amber-500/20 bg-amber-500/10 px-2 py-1 text-xs text-amber-500 font-bold"
                    @mouseenter="e => showRelationTooltip(e, note, 'links')"
                    @mousemove="positionTooltip"
                    @mouseleave="hideTooltip">
                    <Link class="w-3.5 h-3.5" />
                    <span>{{ getNoteRelationCount(note, 'link') }}</span>
                  </span>
                  <span v-else tabindex="0" class="relation-chip inline-flex items-center space-x-1 rounded-md border border-white/10 bg-white/[0.02] px-2 py-1 text-xs text-slate-400"
                    @mouseenter="e => showRelationTooltip(e, note, 'links')"
                    @mousemove="positionTooltip"
                    @mouseleave="hideTooltip">
                    <Link class="w-3.5 h-3.5 text-emerald-400" />
                    <span>{{ getNoteRelationCount(note, 'link') }}</span>
                  </span>
                </div>
              </td>
              <td class="px-5 py-4">
                <div class="flex flex-col items-start space-y-1.5">
                  <span tabindex="0" class="note-status-chip inline-flex items-center px-2 py-1 rounded-md text-[10px] font-bold uppercase tracking-wider border"
                    :class="[getNoteStatusInfo(note.status).cls, isStatusEmphasized(note.status) ? getStatusEmphasisClass(note.status) : 'status-chip-steady']"
                    @mouseenter="e => showRelationTooltip(e, note, 'status')"
                    @mousemove="positionTooltip"
                    @mouseleave="hideTooltip">
                    <component :is="getStatusIcon(getNoteStatusInfo(note.status).icon)" class="w-3 h-3 mr-1" />
                    {{ getNoteStatusInfo(note.status).label }}
                  </span>
                  <span v-if="note.status === NoteStatusCode.PENDING_INFO && note.missingCount > 0" class="text-[10px] text-amber-500 flex items-center">
                    <AlertTriangle class="w-3 h-3 mr-1" /> 缺 {{ note.missingCount }} 项关联
                  </span>
                  <span v-else-if="note.status === NoteStatusCode.APPROVED" class="text-[10px] text-blue-400 flex items-center">
                    <Send class="w-3 h-3 mr-1" /> 待公开发布
                  </span>
                  <span v-else-if="note.status === NoteStatusCode.PUBLISHED" class="text-[10px] text-emerald-500 flex items-center">
                    <CheckCircle2 class="w-3 h-3 mr-1" /> 可公开访问
                  </span>
                  <span v-else-if="note.status === NoteStatusCode.REJECTED" class="text-[10px] text-rose-400 flex items-center">
                    <XCircle class="w-3 h-3 mr-1" /> 审核未通过
                  </span>
                  <span v-else-if="note.status === NoteStatusCode.PENDING_AUDIT" class="text-[10px] text-amber-400 flex items-center alert-pulse">
                    <Clock class="w-3 h-3 mr-1" /> 等待管理员审核
                  </span>
                </div>
              </td>
              <td class="px-5 py-4 text-right">
                <div class="flex items-center justify-end space-x-2 opacity-50 group-hover:opacity-100 transition-opacity" @click.stop>
                  <button v-if="note.isChanging === 1" class="diff-action" title="查看 Diff 信息" @click="handleViewDiff(note.id)">
                    <FileDiff class="w-4 h-4" />
                    <span>Diff</span>
                  </button>
                  <template v-if="note.status === NoteStatusCode.NEW">
                    <button class="relation-entry" title="手动绑定关联" @click="handleOpenRelations(note.id)">
                      <Network class="w-3.5 h-3.5" />
                      <span>映射</span>
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-violet-500/20 text-slate-400 hover:text-violet-400 flex items-center justify-center transition-all" title="校验关联" @click="handleCheckRelations(note.id)">
                      <Wrench class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-blue-500/20 text-slate-400 hover:text-blue-400 flex items-center justify-center transition-all" title="重新上传" @click="openModifyUploadModal(note)">
                      <FileUp class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-all" title="删除" @click="handleDelete(note.id)">
                      <Trash2 class="w-4 h-4" />
                    </button>
                  </template>

                  <template v-else-if="note.status === NoteStatusCode.PENDING_INFO">
                    <button class="relation-entry" title="手动绑定关联" @click="handleOpenRelations(note.id)">
                      <Network class="w-3.5 h-3.5" />
                      <span>映射</span>
                    </button>
                    <button class="flex items-center space-x-1 px-2.5 py-1.5 rounded-lg bg-blue-500/10 hover:bg-blue-500/20 text-blue-400 border border-blue-500/20 transition-colors text-[10px] font-bold uppercase" title="校验关联并修复" @click="handleCheckRelations(note.id)">
                      <Wrench class="w-3.5 h-3.5" />
                      <span>补全资源</span>
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-blue-500/20 text-slate-400 hover:text-blue-400 flex items-center justify-center transition-all" title="重新上传" @click="openModifyUploadModal(note)">
                      <FileUp class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-all" title="删除" @click="handleDelete(note.id)">
                      <Trash2 class="w-4 h-4" />
                    </button>
                  </template>

                  <template v-else-if="note.status === NoteStatusCode.READY_TO_CONVERT">
                    <button class="relation-entry" title="手动绑定关联" @click="handleOpenRelations(note.id)">
                      <Network class="w-3.5 h-3.5" />
                      <span>映射</span>
                    </button>
                    <button class="flex items-center space-x-1 px-2.5 py-1.5 rounded-lg bg-indigo-500/10 hover:bg-indigo-500/20 text-indigo-400 border border-indigo-500/20 transition-colors text-[10px] font-bold uppercase" title="转换笔记为 HTML" @click="handleConvert(note.id)">
                      <RefreshCw class="w-3.5 h-3.5" />
                      <span>转换笔记</span>
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-blue-500/20 text-slate-400 hover:text-blue-400 flex items-center justify-center transition-all" title="重新上传" @click="openModifyUploadModal(note)">
                      <FileUp class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-all" title="删除" @click="handleDelete(note.id)">
                      <Trash2 class="w-4 h-4" />
                    </button>
                  </template>

                  <template v-else-if="note.status === NoteStatusCode.CONVERTED">
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-emerald-500/20 text-slate-400 hover:text-emerald-400 flex items-center justify-center transition-all" title="查看 HTML" @click="handleViewHtml(note.id)">
                      <Eye class="w-4 h-4" />
                    </button>
                    <button class="flex items-center space-x-1 px-2.5 py-1.5 rounded-lg bg-orange-500/10 hover:bg-orange-500/20 text-orange-400 border border-orange-500/20 transition-colors text-[10px] font-bold uppercase" title="删除转换缓存" @click="handleDeleteConverted(note.id)">
                      <RotateCcw class="w-3.5 h-3.5" />
                      <span>删除缓存</span>
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-blue-500/20 text-slate-400 hover:text-blue-400 flex items-center justify-center transition-all" title="提交审核" @click="handleSubmitAudit(note.id)">
                      <Send class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-all" title="删除" @click="handleDelete(note.id)">
                      <Trash2 class="w-4 h-4" />
                    </button>
                  </template>

                  <template v-else-if="note.status === NoteStatusCode.PENDING_AUDIT">
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-emerald-500/20 text-slate-400 hover:text-emerald-400 flex items-center justify-center transition-all" title="查看 HTML" @click="handleViewHtml(note.id)">
                      <Eye class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-all" title="取消审核" @click="handleCancelAudit(note.id)">
                      <CornerUpLeft class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 text-slate-600 flex items-center justify-center transition-all" disabled title="审核中不可删除">
                      <Ban class="w-4 h-4" />
                    </button>
                  </template>

                  <template v-else-if="note.status === NoteStatusCode.APPROVED">
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-emerald-500/20 text-slate-400 hover:text-emerald-400 flex items-center justify-center transition-all" title="查看 HTML" @click="handleViewHtml(note.id)">
                      <Eye class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-emerald-500/20 text-slate-400 hover:text-emerald-400 flex items-center justify-center transition-all shadow-[0_0_10px_rgba(16,185,129,0.2)]" title="公开发布" @click="handlePublish(note.id)">
                      <Globe class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-blue-500/20 text-slate-400 hover:text-blue-400 flex items-center justify-center transition-all" title="重新上传" @click="openModifyUploadModal(note)">
                      <FileUp class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-all" title="删除" @click="handleDelete(note.id)">
                      <Trash2 class="w-4 h-4" />
                    </button>
                  </template>

                  <template v-else-if="note.status === NoteStatusCode.PUBLISHED">
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-emerald-500/20 text-slate-400 hover:text-emerald-400 flex items-center justify-center transition-all" title="查看 HTML" @click="handleViewHtml(note.id)">
                      <Eye class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-amber-500/20 text-slate-400 hover:text-amber-400 flex items-center justify-center transition-all" title="取消公开" @click="handleUnpublish(note.id)">
                      <FileEdit class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 text-slate-600 flex items-center justify-center transition-all" disabled title="已公开状态不可删除或修改">
                      <Ban class="w-4 h-4" />
                    </button>
                  </template>

                  <template v-else-if="note.status === NoteStatusCode.REJECTED">
                    <button class="w-8 h-8 rounded-lg bg-rose-500/10 hover:bg-rose-500/30 text-rose-400 flex items-center justify-center transition-all border border-rose-500/20" title="审核驳回" @click="showAlert('管理员驳回了这篇笔记的审核申请，请修正后重新提交。')">
                      <AlertCircle class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-blue-500/20 text-slate-400 hover:text-blue-400 flex items-center justify-center transition-all" title="重新上传" @click="openModifyUploadModal(note)">
                      <FileUp class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-all" title="删除" @click="handleDelete(note.id)">
                      <Trash2 class="w-4 h-4" />
                    </button>
                  </template>

                  <template v-else>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-emerald-500/20 text-slate-400 hover:text-emerald-400 flex items-center justify-center transition-all" title="查看详情" @click="handleViewSource(note.id)">
                      <Eye class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-all" title="删除" @click="handleDelete(note.id)">
                      <Trash2 class="w-4 h-4" />
                    </button>
                  </template>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="!loading && noteList.length > 0" class="px-6 py-4 border-t border-white/5 flex items-center justify-between bg-white/[0.01]">
        <span class="text-xs text-slate-500">共 {{ formatNumber(total) }} 篇笔记</span>
        <div class="flex items-center space-x-1">
          <button class="w-7 h-7 rounded flex items-center justify-center text-slate-500 hover:bg-white/5 hover:text-white disabled:opacity-50" :disabled="currentPage <= 1" @click="handlePageChange(currentPage - 1)">
            <ChevronLeft class="w-4 h-4" />
          </button>
          <template v-for="page in visiblePages()" :key="page">
            <button v-if="totalPages > 1" class="w-7 h-7 rounded flex items-center justify-center text-xs font-bold transition-colors" :class="page === currentPage ? 'bg-blue-500/20 text-blue-400 border border-blue-500/30' : 'text-slate-400 hover:bg-white/5 hover:text-white'" @click="handlePageChange(page)">{{ page }}</button>
          </template>
          <button class="w-7 h-7 rounded flex items-center justify-center text-slate-500 hover:bg-white/5 hover:text-white" :disabled="currentPage >= totalPages" @click="handlePageChange(currentPage + 1)">
            <ChevronRight class="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <Transition name="modal-backdrop">
        <div v-if="showUploadModal" class="fixed inset-0 z-50 bg-black/70 backdrop-blur-md" @click="closeUploadModal" />
      </Transition>
      <Transition name="modal-panel">
        <div v-if="showUploadModal" class="fixed inset-0 z-50 flex items-center justify-center px-4 pointer-events-none">
        <div class="glass-panel modal-card w-full max-w-xl rounded-3xl p-8 relative z-10 pointer-events-auto">
          <div class="absolute -top-10 -right-10 w-40 h-40 bg-blue-500/20 blur-[50px] rounded-full pointer-events-none" />
          <div class="flex justify-between items-center mb-6">
            <div class="flex items-center space-x-3">
              <div class="w-10 h-10 rounded-xl bg-blue-500/10 flex items-center justify-center text-blue-400 border border-blue-500/20">
                <FileUp class="w-5 h-5" />
              </div>
              <div>
                <h3 class="text-xl font-bold text-white">{{ uploadModalTitle }}</h3>
                <p class="text-[10px] text-slate-400 mt-0.5 uppercase tracking-widest">{{ uploadModalSubtitle }}</p>
              </div>
            </div>
            <button class="text-slate-500 hover:text-white transition-colors p-2 rounded-full hover:bg-white/5" @click="closeUploadModal">
              <X class="w-5 h-5" />
            </button>
          </div>
          <p v-if="isModifyUpload" class="mb-4 rounded-lg border border-blue-500/20 bg-blue-500/10 px-3 py-2 text-xs text-blue-200">
            目标笔记：{{ uploadTargetTitle || `#${uploadTargetNoteId}` }}
          </p>

          <div class="space-y-6">
            <div
              class="w-full h-40 border-2 border-dashed rounded-xl flex flex-col items-center justify-center cursor-pointer group transition-colors"
              :class="uploadDragging ? 'border-blue-500 bg-blue-500/10' : 'border-white/20 bg-white/5 hover:bg-blue-500/10 hover:border-blue-500/50'"
              @click="($refs.fileInput as HTMLInputElement)?.click()"
              @dragover.prevent="uploadDragging = true"
              @dragleave.prevent="uploadDragging = false"
              @drop.prevent="handleFileDrop"
            >
              <div v-if="uploadFile" class="text-center">
                <div class="w-12 h-12 rounded-full bg-blue-500/20 text-blue-400 flex items-center justify-center mx-auto mb-2">
                  <FileText class="w-6 h-6" />
                </div>
                <p class="text-sm font-bold text-white">{{ uploadFile.name }}</p>
                <p class="text-xs text-slate-500 mt-1">{{ formatBytes(uploadFile.size) }}</p>
              </div>
              <template v-else>
                <div class="w-12 h-12 rounded-full bg-blue-500/20 text-blue-400 flex items-center justify-center mb-3 group-hover:-translate-y-1 transition-transform shadow-[0_0_15px_rgba(59,130,246,0.3)]">
                  <UploadCloud class="w-6 h-6" />
                </div>
                <p class="text-sm font-bold text-white mb-1 group-hover:text-blue-300 transition-colors">
                  {{ isModifyUpload ? '点击或拖拽新版 `.md` 文件到此处' : '点击或拖拽 `.md` 文件到此处' }}
                </p>
                <p class="text-xs text-slate-400">
                  {{ isModifyUpload ? '系统会读取旧版内容并生成 Diff，等待你确认或回滚。' : '支持原生 Markdown 语法，系统会自动扫描双链与关联资源。' }}
                </p>
              </template>
            </div>
            <input ref="fileInput" type="file" accept=".md,.markdown" class="hidden" @change="handleFileSelect" />

            <div v-if="!isModifyUpload" class="grid grid-cols-2 gap-4">
              <div class="col-span-2 sm:col-span-1">
                <label class="block text-xs font-bold text-slate-400 uppercase tracking-widest mb-2">
                  初始归属主题 <span class="text-slate-500 normal-case tracking-normal">(可选)</span>
                </label>
                <div class="relative group">
                  <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-500 group-focus-within:text-blue-400 transition-colors">
                    <Layers class="w-4 h-4" />
                  </div>
                  <select v-model="uploadTopicId" class="w-full bg-black/20 border border-white/[0.05] shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] rounded-xl py-3 pl-10 pr-4 outline-none focus:bg-black/40 focus:border-blue-500/50 focus:ring-2 focus:ring-blue-500/10 transition-all text-sm text-white appearance-none cursor-pointer">
                    <option :value="undefined" class="bg-[#0b0d14]">-- 暂不归类 --</option>
                    <option v-for="t in topicList" :key="t.id" :value="t.id" class="bg-[#0b0d14]">{{ t.topicName }}</option>
                  </select>
                  <div class="absolute inset-y-0 right-0 pr-3 flex items-center pointer-events-none text-slate-500">
                    <ChevronDown class="w-4 h-4" />
                  </div>
                </div>
              </div>
            </div>

            <div class="pt-4 flex justify-end space-x-3 border-t border-white/10 mt-6">
              <button class="px-5 py-2.5 rounded-xl text-sm font-bold text-slate-400 hover:text-white hover:bg-white/5 transition-colors" @click="closeUploadModal">取消</button>
              <button class="group relative px-6 py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-bold rounded-xl shadow-[0_0_15px_rgba(59,130,246,0.4)] transition-all overflow-hidden flex items-center space-x-2" :disabled="!uploadFile || uploadSubmitting" @click="handleUpload">
                <div class="absolute inset-0 bg-[linear-gradient(to_right,transparent,rgba(255,255,255,0.2),transparent)] -translate-x-[150%] group-hover:translate-x-[150%] transition-transform duration-700 ease-out" />
                <Loader2 v-if="uploadSubmitting" class="w-4 h-4 animate-spin" />
                <span>{{ uploadSubmitting ? (isModifyUpload ? '处理中...' : '上传中...') : uploadButtonText }}</span>
                <ArrowRight v-if="!uploadSubmitting" class="w-4 h-4 group-hover:translate-x-1 transition-transform" />
              </button>
            </div>
          </div>
        </div>
      </div>
      </Transition>
    </Teleport>

    <Teleport to="body">
      <Transition name="modal-backdrop">
        <div v-if="showSourceModal" class="fixed inset-0 z-[60] bg-black/90 backdrop-blur-md" @click="showSourceModal = false" />
      </Transition>
      <Transition name="modal-panel">
        <div v-if="showSourceModal" class="fixed inset-0 z-[60] flex items-center justify-center px-4 pointer-events-none">
        <div class="glass-panel modal-card relative z-10 max-w-3xl w-full max-h-[85vh] rounded-2xl overflow-hidden flex flex-col pointer-events-auto">
          <div class="flex items-center justify-between px-6 py-4 border-b border-white/10">
            <div class="flex items-center space-x-3">
              <div class="w-8 h-8 rounded-lg bg-blue-500/10 flex items-center justify-center text-blue-400">
                <FileText class="w-4 h-4" />
              </div>
              <h3 class="text-sm font-bold text-white truncate max-w-[400px]">{{ sourceTitle }}</h3>
            </div>
            <button class="text-slate-500 hover:text-white p-2 rounded-full hover:bg-white/5" @click="showSourceModal = false">
              <X class="w-5 h-5" />
            </button>
          </div>
          <div class="flex-1 overflow-y-auto p-6">
            <pre class="text-sm text-slate-300 font-mono whitespace-pre-wrap break-all">{{ sourceContent }}</pre>
          </div>
          <div class="px-6 py-4 border-t border-white/10 flex justify-end gap-3">
            <button
              class="px-4 py-2 rounded-lg text-sm text-slate-400 hover:text-white hover:bg-white/5 transition"
              @click="showSourceModal = false"
            >关闭</button>
            <button
              class="px-4 py-2 rounded-lg text-sm bg-blue-500/20 text-blue-300 hover:bg-blue-500/30 border border-blue-500/30 flex items-center gap-2 transition"
              @click="handleEditSource"
            >
              <PenLine class="w-4 h-4" />
              <span>编辑源文件</span>
            </button>
          </div>
        </div>
      </div>
      </Transition>
    </Teleport>

    <!-- ── Global tooltip ── -->
    <Teleport to="body">
      <div
        v-if="tooltipVisible"
        class="global-tooltip"
        :class="`tooltip-${tooltipColor}`"
        :style="{ left: tooltipX + 'px', top: tooltipY + 'px' }"
      >{{ tooltipText }}</div>
    </Teleport>

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

.glass-checkbox {
  appearance: none;
  width: 16px;
  height: 16px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.2);
  cursor: pointer;
  position: relative;
  transition: all 0.2s;
}

.glass-checkbox:checked {
  background: #3b82f6;
  border-color: #3b82f6;
  box-shadow: 0 0 10px rgba(59, 130, 246, 0.4);
}

.glass-checkbox:checked::after {
  content: '';
  position: absolute;
  left: 5px;
  top: 2px;
  width: 4px;
  height: 8px;
  border: solid white;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}

.mini-tag {
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.08) 0%, rgba(255, 255, 255, 0.02) 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.1);
}

.floating-batch-bar {
  position: relative;
  overflow: hidden;
}

.floating-batch-bar::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(120deg, rgba(59, 130, 246, 0.12), transparent 28%, transparent 72%, rgba(16, 185, 129, 0.08));
  pointer-events: none;
}

.relation-chip,
.note-status-chip {
  transition:
    transform 0.22s ease,
    border-color 0.22s ease,
    box-shadow 0.22s ease,
    color 0.22s ease,
    background-color 0.22s ease;
}

.diff-action {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.35rem 0.65rem;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: #fbbf24;
  background: rgba(245, 158, 11, 0.12);
  border: 1px solid rgba(245, 158, 11, 0.35);
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease, color 0.2s ease;
}

.diff-action::before {
  content: '';
  position: absolute;
  inset: -1px;
  background: linear-gradient(120deg, rgba(251, 191, 36, 0.4), rgba(59, 130, 246, 0.3), rgba(251, 191, 36, 0.4));
  opacity: 0;
  transition: opacity 0.3s ease;
}

.diff-action > * {
  position: relative;
  z-index: 1;
}

.diff-action:hover {
  transform: translateY(-1px);
  color: #fff;
  box-shadow: 0 0 18px rgba(245, 158, 11, 0.35);
}

.diff-action:hover::before {
  opacity: 1;
}

.relation-entry {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.35rem 0.6rem;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: #93c5fd;
  background: rgba(59, 130, 246, 0.12);
  border: 1px solid rgba(59, 130, 246, 0.3);
  transition: transform 0.2s ease, box-shadow 0.2s ease, color 0.2s ease, background-color 0.2s ease;
}

.relation-entry:hover {
  background: rgba(59, 130, 246, 0.22);
  color: #fff;
  transform: translateY(-1px);
  box-shadow: 0 0 14px rgba(59, 130, 246, 0.35);
}

.relation-chip:focus-visible,
.note-status-chip:focus-visible {
  outline: none;
  box-shadow:
    0 0 0 1px rgba(59, 130, 246, 0.5),
    0 0 0 5px rgba(59, 130, 246, 0.14);
}

@keyframes batch-slide-up {
  0% {
    opacity: 0;
    transform: translateY(24px) scale(0.96);
  }
  72% {
    opacity: 1;
    transform: translateY(-4px) scale(1.01);
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes status-breathe {
  0%, 100% {
    transform: translateY(0) scale(1);
    opacity: 1;
  }
  50% {
    transform: translateY(-0.5px) scale(1.02);
    opacity: 0.92;
  }
}

@keyframes gentle-pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.84;
    transform: scale(1.04);
  }
}

.batch-float-enter-active {
  transition: opacity 0.42s cubic-bezier(0.22, 1.2, 0.36, 1);
}

.batch-float-enter-active .floating-batch-bar {
  animation: batch-slide-up 0.42s cubic-bezier(0.22, 1.2, 0.36, 1) both;
}

.batch-float-enter-from,
.batch-float-leave-to {
  opacity: 0;
}

.batch-float-leave-active {
  transition: opacity 0.26s ease;
}

.batch-float-leave-active .floating-batch-bar {
  opacity: 0;
  transform: translateY(16px) scale(0.98);
  transition: transform 0.26s ease, opacity 0.26s ease;
}

.modal-backdrop-enter-active,
.modal-backdrop-leave-active {
  transition: opacity 0.28s ease;
}

.modal-backdrop-enter-from,
.modal-backdrop-leave-to {
  opacity: 0;
}

.modal-panel-enter-active,
.modal-panel-leave-active {
  transition:
    opacity 0.32s ease,
    transform 0.42s cubic-bezier(0.25, 1, 0.5, 1);
}

.modal-panel-enter-from,
.modal-panel-leave-to {
  opacity: 0;
  transform: translateY(18px) scale(0.96);
}

.modal-panel-enter-to,
.modal-panel-leave-from {
  opacity: 1;
  transform: translateY(0) scale(1);
}

.modal-card {
  transform-origin: center center;
  box-shadow:
    0 24px 80px rgba(15, 23, 42, 0.45),
    inset 0 1px 1px rgba(255, 255, 255, 0.05);
}

.status-breathe {
  animation: status-breathe 2.5s ease-in-out infinite;
}

.status-chip-steady {
  box-shadow: none;
}

.status-glow-amber {
  box-shadow: 0 0 18px rgba(245, 158, 11, 0.16);
}

.status-glow-rose {
  box-shadow: 0 0 18px rgba(244, 63, 94, 0.16);
}

.alert-pulse {
  animation: gentle-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

@media (prefers-reduced-motion: reduce) {
  .status-breathe,
  .alert-pulse,
  .animate-ping {
    animation: none;
  }

  .batch-float-enter-active .floating-batch-bar,
  .batch-float-leave-active .floating-batch-bar,
  .modal-backdrop-enter-active,
  .modal-backdrop-leave-active,
  .modal-panel-enter-active,
  .modal-panel-leave-active,
  .relation-chip,
  .note-status-chip {
    animation: none;
    transition-duration: 0.16s;
  }
}
</style>

<style>
.global-tooltip {
  position: fixed;
  z-index: 9999;
  pointer-events: none;
  transform: translate(-50%, calc(-100% - 10px));
  border-radius: 0.75rem;
  background: rgba(2, 6, 23, 0.97);
  padding: 0.6rem 0.85rem;
  font-size: 11px;
  line-height: 1.55;
  max-width: 220px;
  box-shadow: 0 14px 40px rgba(15, 23, 42, 0.5);
  white-space: pre-wrap;
  word-break: break-all;
}
.tooltip-blue  { border: 1px solid rgba(59, 130, 246, 0.3);  color: #bfdbfe; }
.tooltip-purple{ border: 1px solid rgba(168, 85, 247, 0.3);  color: #e9d5ff; }
.tooltip-amber { border: 1px solid rgba(245, 158, 11, 0.3);  color: #fde68a; }
.tooltip-emerald{border: 1px solid rgba(16, 185, 129, 0.3);  color: #a7f3d0; }
.tooltip-rose  { border: 1px solid rgba(244, 63, 94, 0.3);   color: #fecdd3; }
</style>
