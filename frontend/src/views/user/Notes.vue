<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { noteApi, getNoteStatusInfo, NoteStatusCode } from '@/api/notes'
import type { NoteItem, PageResult, NoteQueryParams } from '@/api/notes'
import { topicApi } from '@/api/topics'
import type { TopicItem } from '@/api/topics'
import {
  FileText, Search, FileUp, Globe, Trash2, Loader2, ChevronLeft, ChevronRight,
  Hash, Image, Link, Layers, Eye, Wrench, RefreshCw,
  CornerUpLeft, AlertCircle, Clock, CheckCircle2, XCircle, FileEdit,
  AlertTriangle, UploadCloud, ArrowRight, X, FolderTree, ChevronDown,
  Send, FilePlus, FileCode, HelpCircle, Ban
} from 'lucide-vue-next'

// ── State ─────────────────────────────────────────
const loading = ref(true)
const noteList = ref<NoteItem[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(15)

// Filters
const searchKeyword = ref('')
const filterTopicId = ref('')
const filterStatus = ref('')  // NoteStatus code or '' for all

// Selection
const selectedIds = ref<Set<number>>(new Set())

// Upload modal
const showUploadModal = ref(false)
const uploadFile = ref<File | null>(null)
const uploadTopicId = ref<number | undefined>(undefined)
const uploadDragging = ref(false)
const topicList = ref<TopicItem[]>([])

// Global search toggle
const searchMode = ref<'personal' | 'global'>('personal')

// Source preview
const showSourceModal = ref(false)
const sourceContent = ref('')
const sourceTitle = ref('')

// ── Computed ──────────────────────────────────────
const isBatchMode = computed(() => selectedIds.value.size > 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

// ── Helpers ───────────────────────────────────────
function showAlert(msg: string) { window.alert(msg) }
function showConfirm(msg: string): boolean { return window.confirm(msg) }

function formatBytes(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const u = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(i > 0 ? 1 : 0) + ' ' + u[i]
}

function formatNumber(n: number): string { return n.toLocaleString() }

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

// ── Data fetching ─────────────────────────────────
async function fetchTopics() {
  try {
    const res = await topicApi.getList({ pageSize: 100 })
    topicList.value = (res as unknown as { records: TopicItem[] }).records ?? []
  } catch { /* non-critical */ }
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

    // Client-side status filter if backend doesn't support it
    if (filterStatus.value !== '') {
      const code = Number(filterStatus.value)
      records = records.filter(n => n.status === code)
      total.value = records.length
    }

    noteList.value = records
  } finally {
    loading.value = false
  }
}

// ── Actions ───────────────────────────────────────
function handleSearch() { currentPage.value = 1; fetchNotes() }
function handlePageChange(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  currentPage.value = page; fetchNotes()
}
function handleTabChange(statusCode: string) { filterStatus.value = statusCode; currentPage.value = 1; fetchNotes() }

function toggleSelectAll(checked: boolean) {
  if (checked) noteList.value.forEach(n => selectedIds.value.add(n.id))
  else selectedIds.value.clear()
}
function toggleSelect(id: number) {
  selectedIds.value.has(id) ? selectedIds.value.delete(id) : selectedIds.value.add(id)
}

// Upload
function toggleUploadModal() { showUploadModal.value = !showUploadModal.value; if (!showUploadModal.value) { uploadFile.value = null; uploadTopicId.value = undefined } }

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

async function handleUpload() {
  if (!uploadFile.value) return
  try {
    const res = await noteApi.uploadNote(uploadFile.value, uploadTopicId.value)
    toggleUploadModal()
    await fetchNotes()
    const missing = (res as unknown as { missingImages?: string[]; missingTags?: string[]; missingNoteNames?: string[] })
    const totalMissing = (missing.missingImages?.length ?? 0) + (missing.missingTags?.length ?? 0) + (missing.missingNoteNames?.length ?? 0)
    if (totalMissing > 0) {
      showAlert(`笔记已上传成功！但有 ${totalMissing} 项关联资源需要补全。`)
    }
  } catch {
    showAlert('上传失败，请重试')
  }
}

// Single actions
async function handleDelete(id: number) {
  if (!showConfirm('确定删除该笔记吗？此操作不可恢复。')) return
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

async function handleCancelAudit(_id: number) {
  showAlert('取消审核将回到"已转换"状态，你可以继续修改笔记内容')
}

async function handleConvert(id: number) {
  try {
    await noteApi.convertNote(id)
    await fetchNotes()
  } catch {
    showAlert('转换失败，请确认笔记没有关联异常')
  }
}

async function handleCheckRelations(id: number) {
  try {
    const res = await noteApi.checkRelations(id)
    const result = res as unknown as { complete: boolean; missingCount: number }
    if (result.complete) {
      showAlert('关联完整性校验通过！')
    } else {
      showAlert(`还有 ${result.missingCount} 项关联需要补全`)
    }
    await fetchNotes()
  } catch {
    showAlert('校验失败，请重试')
  }
}

async function handleViewSource(id: number) {
  try {
    const src = await noteApi.getSource(id)
    const note = noteList.value.find(n => n.id === id)
    sourceTitle.value = note?.title ?? '笔记源文件'
    sourceContent.value = src as unknown as string
    showSourceModal.value = true
  } catch {
    showAlert('无法获取源文件')
  }
}

async function handleViewHtml(id: number) {
  try {
    await noteApi.getConverted(id)
    showAlert('HTML 预览功能开发中，将通过阅读页展示')
  } catch {
    showAlert('该笔记尚未转换，请先点击"转换笔记"')
  }
}

// Batch actions
async function handleBatchDelete() {
  if (selectedIds.value.size === 0) return
  if (!showConfirm(`确定删除已选择的 ${selectedIds.value.size} 篇笔记吗？`)) return
  for (const id of selectedIds.value) {
    try { await noteApi.deleteNote(id) } catch { /* continue */ }
  }
  selectedIds.value.clear()
  await fetchNotes()
}

async function handleBatchPublish() {
  if (selectedIds.value.size === 0) return
  let count = 0
  for (const id of selectedIds.value) {
    try { await noteApi.publish(id, 1); count++ } catch { /* skip */ }
  }
  selectedIds.value.clear()
  await fetchNotes()
  if (count > 0) showAlert(`成功发布了 ${count} 篇笔记`)
}

function toggleGlobalSearch() {
  searchMode.value = searchMode.value === 'personal' ? 'global' : 'personal'
}

// ── Init ──────────────────────────────────────────
onMounted(() => {
  fetchTopics()
  fetchNotes()
})
</script>

<template>
  <div class="relative max-w-[1400px] mx-auto space-y-6 pb-20">
    <!-- ═══ Header ═══ -->
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 relative z-10">
      <div class="flex items-center space-x-3">
        <div class="p-2 rounded-xl bg-blue-500/10 border border-blue-500/20 text-blue-400">
          <FileText class="w-5 h-5" />
        </div>
        <div>
          <h2 class="text-xl font-bold text-white">核心数字资产</h2>
          <p class="text-xs text-slate-400 mt-0.5">管理您的 Markdown 笔记、解决关联异常与发布文章</p>
        </div>
      </div>

      <div class="flex items-center space-x-4">
        <!-- 全局检索开关 -->
        <div class="flex items-center space-x-2.5 cursor-pointer group" @click="toggleGlobalSearch">
          <span class="text-xs font-bold text-slate-400 group-hover:text-blue-300 transition-colors">{{ searchMode === 'personal' ? '个人生态' : '全局搜索' }}</span>
          <div class="relative p-[1px] rounded-full overflow-hidden">
            <div class="absolute inset-0 bg-[conic-gradient(from_0deg,transparent_0_340deg,rgba(59,130,246,0.8)_360deg)] animate-[spin_2s_linear_infinite] opacity-0 group-hover:opacity-100 transition-opacity" />
            <div class="relative w-10 h-5 rounded-full flex items-center px-0.5 transition-colors duration-300 z-10" :class="searchMode === 'global' ? 'bg-blue-500 border border-blue-500/50' : 'bg-black/50 border border-white/10'">
              <div class="w-4 h-4 rounded-full transform transition-all duration-300" :class="searchMode === 'global' ? 'translate-x-5 bg-white shadow-[0_0_10px_rgba(255,255,255,0.8)]' : 'translate-x-0 bg-slate-400'" />
            </div>
          </div>
          <Globe class="w-4 h-4 transition-colors" :class="searchMode === 'global' ? 'text-blue-400' : 'text-slate-500 group-hover:text-blue-400'" />
        </div>

        <div class="w-px h-5 bg-white/10 mx-1" />

        <!-- 搜索框 -->
        <div class="relative group flex items-center bg-black/20 border border-white/10 rounded-xl overflow-hidden transition-all duration-300 ease-out w-9 hover:w-28 focus-within:!w-64 focus-within:bg-black/40 focus-within:border-blue-500/50 focus-within:ring-2 focus-within:ring-blue-500/10 h-9">
          <label class="w-9 h-full flex-shrink-0 flex items-center justify-center text-slate-500 group-hover:text-slate-300 group-focus-within:text-blue-400 transition-colors cursor-pointer z-10">
            <Search class="w-4 h-4" />
          </label>
          <input v-model="searchKeyword" type="text" placeholder="检索笔记标题或内容..." class="absolute left-9 w-[220px] h-full bg-transparent text-sm text-white placeholder:text-slate-500 outline-none opacity-0 group-hover:opacity-100 focus-within:!opacity-100 transition-opacity duration-300 pr-4" @keyup.enter="handleSearch" />
        </div>

        <!-- 上传按钮 -->
        <button class="group relative px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-bold rounded-xl shadow-[0_0_15px_rgba(59,130,246,0.4)] transition-all overflow-hidden flex items-center space-x-2" @click="toggleUploadModal">
          <div class="absolute inset-0 bg-[linear-gradient(to_right,transparent,rgba(255,255,255,0.2),transparent)] -translate-x-[150%] group-hover:translate-x-[150%] transition-transform duration-700 ease-out" />
          <FileUp class="w-4 h-4" />
          <span>上传笔记</span>
        </button>
      </div>
    </div>

    <!-- ═══ Filter Bar: Tabs + Topic Selector ═══ -->
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
          :class="filterStatus === tab.id
            ? 'bg-white/10 text-white shadow-sm'
            : 'text-slate-400 hover:text-white'"
          @click="handleTabChange(tab.id)"
        >
          <component :is="tab.icon" class="w-3.5 h-3.5" />
          {{ tab.label }}
        </button>
      </div>

      <!-- Topic filter dropdown -->
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

    <!-- ═══ Batch Action Bar ═══ -->
    <div class="glass-panel rounded-xl px-4 py-3 flex items-center justify-between transition-all duration-300 sticky top-0 z-30"
      :class="isBatchMode ? 'opacity-100 pointer-events-auto translate-y-0' : 'opacity-0 pointer-events-none -translate-y-[10px]'">
      <div class="flex items-center space-x-3">
        <span class="flex h-2 w-2 relative">
          <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75" />
          <span class="relative inline-flex rounded-full h-2 w-2 bg-blue-500" />
        </span>
        <span class="text-sm font-bold text-blue-300">已选取 <span class="text-white mx-1">{{ selectedIds.size }}</span> 篇笔记</span>
      </div>
      <div class="flex items-center space-x-2">
        <button class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-emerald-500/10 text-emerald-400 hover:bg-emerald-500 hover:text-white transition-all text-xs font-bold border border-emerald-500/20" @click="handleBatchPublish">
          <Globe class="w-3.5 h-3.5" />
          <span>批量发布</span>
        </button>
        <button class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-rose-500/10 text-rose-400 hover:bg-rose-500 hover:text-white transition-all text-xs font-bold border border-rose-500/20" @click="handleBatchDelete">
          <Trash2 class="w-3.5 h-3.5" />
          <span>批量删除</span>
        </button>
      </div>
    </div>

    <!-- ═══ Data Table ═══ -->
    <div class="glass-panel rounded-2xl overflow-hidden border border-white/10 relative z-10 shadow-2xl">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr>
              <th class="px-5 py-4 border-b border-white/5 w-10">
                <input type="checkbox" class="glass-checkbox" :checked="selectedIds.size === noteList.length && noteList.length > 0"
                  @change="toggleSelectAll(($event.target as HTMLInputElement).checked)" />
              </th>
              <th class="px-5 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider w-[32%]">核心资产 (Title & Meta)</th>
              <th class="px-5 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">关联矩阵 (Relations)</th>
              <th class="px-5 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">笔记状态 (Status)</th>
              <th class="px-5 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider text-right">管控台 (Actions)</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <!-- Loading -->
            <tr v-if="loading">
              <td colspan="5" class="px-6 py-16 text-center">
                <Loader2 class="w-6 h-6 text-blue-400 animate-spin mx-auto mb-3" />
                <span class="text-xs text-slate-500">加载中...</span>
              </td>
            </tr>
            <!-- Empty -->
            <tr v-else-if="noteList.length === 0">
              <td colspan="5" class="px-6 py-16 text-center text-sm text-slate-500">暂无笔记数据，点击右上角"上传笔记"开始创作</td>
            </tr>
            <!-- Rows -->
            <tr v-for="note in noteList" :key="note.id"
              class="hover:bg-white/[0.02] transition-colors group cursor-pointer"
              :class="{ 'bg-rose-500/[0.01]': note.status === NoteStatusCode.REJECTED || note.status === NoteStatusCode.PENDING_INFO }"
              @dblclick="handleViewSource(note.id)">
              <!-- Checkbox -->
              <td class="px-5 py-4" @click.stop>
                <input type="checkbox" class="glass-checkbox" :checked="selectedIds.has(note.id)" @change="toggleSelect(note.id)" />
              </td>
              <!-- 核心资产 -->
              <td class="px-5 py-4">
                <div class="flex flex-col">
                  <div class="flex items-center space-x-2">
                    <h3 class="text-sm font-bold text-slate-200 group-hover:text-blue-300 transition-colors truncate max-w-[260px]">{{ note.title }}</h3>
                    <span v-if="note.mdFileSize" class="text-[9px] font-mono text-slate-500 bg-black/40 px-1.5 py-0.5 rounded border border-white/5 flex-shrink-0">{{ formatBytes(note.mdFileSize) }}</span>
                  </div>
                  <div class="flex items-center space-x-2 mt-2 flex-wrap gap-y-1">
                    <!-- Topic badge -->
                    <span v-if="note.topicName" class="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold text-indigo-300 bg-indigo-500/10 border border-indigo-500/20">
                      <Layers class="w-3 h-3 mr-1" /> {{ note.topicName }}
                    </span>
                    <!-- Tags -->
                    <template v-if="note.tags && note.tags.length">
                      <span v-for="(tag, ti) in (note.tags as unknown as string[])" :key="ti"
                        class="mini-tag px-2 py-0.5 rounded text-[10px] font-bold text-slate-400 border border-white/10"># {{ tag }}</span>
                    </template>
                    <span v-else-if="note.tagCount > 0" class="text-[10px] text-slate-600">含 {{ note.tagCount }} 个标签</span>
                    <span v-else class="text-[10px] text-slate-600"># 独立笔记 (无标签)</span>
                  </div>
                </div>
              </td>
              <!-- 关联矩阵 -->
              <td class="px-5 py-4">
                <div class="flex items-center space-x-3 flex-wrap gap-y-1">
                  <div class="flex items-center space-x-1 text-xs text-slate-400" :title="`${note.tagCount ?? 0} 个标签`">
                    <Hash class="w-3.5 h-3.5 text-purple-400" /><span>{{ note.tagCount ?? 0 }}</span>
                  </div>
                  <div v-if="note.missingCount > 0"
                    class="flex items-center space-x-1 text-xs text-amber-500 bg-amber-500/10 px-1.5 py-0.5 rounded cursor-help border border-amber-500/20 alert-pulse"
                    title="存在未上传或关联异常的图片资源">
                    <Image class="w-3.5 h-3.5" /><span class="font-bold">{{ note.imageCount ?? 0 }}</span>
                  </div>
                  <div v-else class="flex items-center space-x-1 text-xs text-slate-400" :title="`${note.imageCount ?? 0} 张图片`">
                    <Image class="w-3.5 h-3.5 text-blue-400" /><span>{{ note.imageCount ?? 0 }}</span>
                  </div>
                  <div class="flex items-center space-x-1 text-xs text-slate-400" :title="`${note.eachNoteCount ?? 0} 条双链`">
                    <Link class="w-3.5 h-3.5 text-emerald-400" /><span>{{ note.eachNoteCount ?? 0 }}</span>
                  </div>
                </div>
              </td>
              <!-- 笔记状态 (NoteStatus-based) -->
              <td class="px-5 py-4">
                <div class="flex flex-col items-start space-y-1.5">
                  <span class="inline-flex items-center px-2 py-1 rounded-md text-[10px] font-bold uppercase tracking-wider border" :class="getNoteStatusInfo(note.status).cls">
                    <component :is="getStatusIcon(getNoteStatusInfo(note.status).icon)" class="w-3 h-3 mr-1" />
                    {{ getNoteStatusInfo(note.status).label }}
                  </span>
                  <!-- Supplementary info depending on state -->
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
              <!-- 管控台 - Status-aware actions -->
              <td class="px-5 py-4 text-right">
                <div class="flex items-center justify-end space-x-2 opacity-50 group-hover:opacity-100 transition-opacity" @click.stop>

                  <!-- NEW (0): check relations, convert, upload, delete -->
                  <template v-if="note.status === NoteStatusCode.NEW">
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-violet-500/20 text-slate-400 hover:text-violet-400 flex items-center justify-center transition-all" title="校验关联" @click="handleCheckRelations(note.id)">
                      <Wrench class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-blue-500/20 text-slate-400 hover:text-blue-400 flex items-center justify-center transition-all" title="重新上传" @click="toggleUploadModal">
                      <FileUp class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-all" title="删除" @click="handleDelete(note.id)">
                      <Trash2 class="w-4 h-4" />
                    </button>
                  </template>

                  <!-- PENDING_INFO (1): repair/check, convert, upload, delete -->
                  <template v-else-if="note.status === NoteStatusCode.PENDING_INFO">
                    <button class="flex items-center space-x-1 px-2.5 py-1.5 rounded-lg bg-blue-500/10 hover:bg-blue-500/20 text-blue-400 border border-blue-500/20 transition-colors text-[10px] font-bold uppercase" title="校验关联并修复" @click="handleCheckRelations(note.id)">
                      <Wrench class="w-3.5 h-3.5" />
                      <span>补全资源</span>
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-blue-500/20 text-slate-400 hover:text-blue-400 flex items-center justify-center transition-all" title="重新上传" @click="toggleUploadModal">
                      <FileUp class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-all" title="删除" @click="handleDelete(note.id)">
                      <Trash2 class="w-4 h-4" />
                    </button>
                  </template>

                  <!-- READY_TO_CONVERT (2): convert, upload, delete -->
                  <template v-else-if="note.status === NoteStatusCode.READY_TO_CONVERT">
                    <button class="flex items-center space-x-1 px-2.5 py-1.5 rounded-lg bg-indigo-500/10 hover:bg-indigo-500/20 text-indigo-400 border border-indigo-500/20 transition-colors text-[10px] font-bold uppercase" title="转换笔记为 HTML" @click="handleConvert(note.id)">
                      <RefreshCw class="w-3.5 h-3.5" />
                      <span>转换笔记</span>
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-blue-500/20 text-slate-400 hover:text-blue-400 flex items-center justify-center transition-all" title="重新上传" @click="toggleUploadModal">
                      <FileUp class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-all" title="删除" @click="handleDelete(note.id)">
                      <Trash2 class="w-4 h-4" />
                    </button>
                  </template>

                  <!-- CONVERTED (3): view HTML, submit audit, upload, delete -->
                  <template v-else-if="note.status === NoteStatusCode.CONVERTED">
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-emerald-500/20 text-slate-400 hover:text-emerald-400 flex items-center justify-center transition-all" title="查看阅读页 HTML" @click="handleViewHtml(note.id)">
                      <Eye class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-blue-500/20 text-slate-400 hover:text-blue-400 flex items-center justify-center transition-all" title="提交审核" @click="handleSubmitAudit(note.id)">
                      <Send class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-blue-500/20 text-slate-400 hover:text-blue-400 flex items-center justify-center transition-all" title="重新上传" @click="toggleUploadModal">
                      <FileUp class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-all" title="删除" @click="handleDelete(note.id)">
                      <Trash2 class="w-4 h-4" />
                    </button>
                  </template>

                  <!-- PENDING_AUDIT (4): view HTML, cancel audit (cannot delete or modify) -->
                  <template v-else-if="note.status === NoteStatusCode.PENDING_AUDIT">
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-emerald-500/20 text-slate-400 hover:text-emerald-400 flex items-center justify-center transition-all" title="查看阅读页 HTML" @click="handleViewHtml(note.id)">
                      <Eye class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-all" title="取消审核" @click="handleCancelAudit(note.id)">
                      <CornerUpLeft class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 text-slate-600 flex items-center justify-center transition-all" disabled title="审核中不可删除">
                      <Ban class="w-4 h-4" />
                    </button>
                  </template>

                  <!-- APPROVED (5): view HTML, publish, upload, delete -->
                  <template v-else-if="note.status === NoteStatusCode.APPROVED">
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-emerald-500/20 text-slate-400 hover:text-emerald-400 flex items-center justify-center transition-all" title="查看阅读页 HTML" @click="handleViewHtml(note.id)">
                      <Eye class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-emerald-500/20 text-slate-400 hover:text-emerald-400 flex items-center justify-center transition-all shadow-[0_0_10px_rgba(16,185,129,0.2)]" title="公开发布" @click="handlePublish(note.id)">
                      <Globe class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-blue-500/20 text-slate-400 hover:text-blue-400 flex items-center justify-center transition-all" title="重新上传" @click="toggleUploadModal">
                      <FileUp class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-all" title="删除" @click="handleDelete(note.id)">
                      <Trash2 class="w-4 h-4" />
                    </button>
                  </template>

                  <!-- PUBLISHED (6): view HTML, unpublish (cannot delete or modify) -->
                  <template v-else-if="note.status === NoteStatusCode.PUBLISHED">
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-emerald-500/20 text-slate-400 hover:text-emerald-400 flex items-center justify-center transition-all" title="查看阅读页 HTML" @click="handleViewHtml(note.id)">
                      <Eye class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-amber-500/20 text-slate-400 hover:text-amber-400 flex items-center justify-center transition-all" title="取消公开" @click="handleUnpublish(note.id)">
                      <FileEdit class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 text-slate-600 flex items-center justify-center transition-all" disabled title="已公开不可删除/修改">
                      <Ban class="w-4 h-4" />
                    </button>
                  </template>

                  <!-- REJECTED (7): view source, re-convert, upload, delete -->
                  <template v-else-if="note.status === NoteStatusCode.REJECTED">
                    <button class="w-8 h-8 rounded-lg bg-rose-500/10 hover:bg-rose-500/30 text-rose-400 flex items-center justify-center transition-all border border-rose-500/20" title="审核被驳回" @click="showAlert('管理员驳回了该笔记的审核申请。请修改后重新提交。')">
                      <AlertCircle class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-blue-500/20 text-slate-400 hover:text-blue-400 flex items-center justify-center transition-all" title="重新上传" @click="toggleUploadModal">
                      <FileUp class="w-4 h-4" />
                    </button>
                    <button class="w-8 h-8 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-all" title="删除" @click="handleDelete(note.id)">
                      <Trash2 class="w-4 h-4" />
                    </button>
                  </template>

                  <!-- Fallback: generic actions -->
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

      <!-- Pagination -->
      <div v-if="!loading && noteList.length > 0" class="px-6 py-4 border-t border-white/5 flex items-center justify-between bg-white/[0.01]">
        <span class="text-xs text-slate-500">共 {{ formatNumber(total) }} 篇笔记</span>
        <div class="flex items-center space-x-1">
          <button class="w-7 h-7 rounded flex items-center justify-center text-slate-500 hover:bg-white/5 hover:text-white disabled:opacity-50" :disabled="currentPage <= 1" @click="handlePageChange(currentPage - 1)">
            <ChevronLeft class="w-4 h-4" />
          </button>
          <template v-for="page in visiblePages()" :key="page">
            <button v-if="totalPages > 1" class="w-7 h-7 rounded flex items-center justify-center text-xs font-bold transition-colors"
              :class="page === currentPage ? 'bg-blue-500/20 text-blue-400 border border-blue-500/30' : 'text-slate-400 hover:bg-white/5 hover:text-white'"
              @click="handlePageChange(page)">{{ page }}</button>
          </template>
          <button class="w-7 h-7 rounded flex items-center justify-center text-slate-500 hover:bg-white/5 hover:text-white" :disabled="currentPage >= totalPages" @click="handlePageChange(currentPage + 1)">
            <ChevronRight class="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>

    <!-- ═══ Upload Modal ═══ -->
    <Teleport to="body">
      <div v-if="showUploadModal" class="fixed inset-0 z-50 flex items-center justify-center" @click.self="toggleUploadModal">
        <div class="absolute inset-0 bg-black/70 backdrop-blur-md" />
        <div class="glass-panel w-full max-w-xl rounded-3xl p-8 relative z-10 transform transition-transform duration-300">
          <div class="absolute -top-10 -right-10 w-40 h-40 bg-blue-500/20 blur-[50px] rounded-full pointer-events-none" />
          <div class="flex justify-between items-center mb-6">
            <div class="flex items-center space-x-3">
              <div class="w-10 h-10 rounded-xl bg-blue-500/10 flex items-center justify-center text-blue-400 border border-blue-500/20">
                <FileUp class="w-5 h-5" />
              </div>
              <div>
                <h3 class="text-xl font-bold text-white">上传 Markdown 笔记</h3>
                <p class="text-[10px] text-slate-400 mt-0.5 uppercase tracking-widest">Upload Documentation</p>
              </div>
            </div>
            <button class="text-slate-500 hover:text-white transition-colors p-2 rounded-full hover:bg-white/5" @click="toggleUploadModal">
              <X class="w-5 h-5" />
            </button>
          </div>

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
                <p class="text-sm font-bold text-white mb-1 group-hover:text-blue-300 transition-colors">点击或拖拽 .md 文件到此处</p>
                <p class="text-xs text-slate-400">支持原生 Markdown 语法，引擎将自动扫描双链与关联资源</p>
              </template>
            </div>
            <input ref="fileInput" type="file" accept=".md,.markdown" class="hidden" @change="handleFileSelect" />

            <div class="grid grid-cols-2 gap-4">
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
              <button class="px-5 py-2.5 rounded-xl text-sm font-bold text-slate-400 hover:text-white hover:bg-white/5 transition-colors" @click="toggleUploadModal">取消</button>
              <button class="group relative px-6 py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-bold rounded-xl shadow-[0_0_15px_rgba(59,130,246,0.4)] transition-all overflow-hidden flex items-center space-x-2" :disabled="!uploadFile" @click="handleUpload">
                <div class="absolute inset-0 bg-[linear-gradient(to_right,transparent,rgba(255,255,255,0.2),transparent)] -translate-x-[150%] group-hover:translate-x-[150%] transition-transform duration-700 ease-out" />
                <span>确认读取并解析</span>
                <ArrowRight class="w-4 h-4 group-hover:translate-x-1 transition-transform" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ═══ Source Viewer Modal ═══ -->
    <Teleport to="body">
      <div v-if="showSourceModal" class="fixed inset-0 z-[60] flex items-center justify-center" @click.self="showSourceModal = false">
        <div class="absolute inset-0 bg-black/90 backdrop-blur-md" @click="showSourceModal = false" />
        <div class="glass-panel relative z-10 max-w-3xl w-full max-h-[85vh] rounded-2xl overflow-hidden flex flex-col mx-4">
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
        </div>
      </div>
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
  appearance: none; width: 16px; height: 16px; border: 1px solid rgba(255, 255, 255, 0.2); border-radius: 4px; background: rgba(0, 0, 0, 0.2); cursor: pointer; position: relative; transition: all 0.2s;
}
.glass-checkbox:checked { background: #3b82f6; border-color: #3b82f6; box-shadow: 0 0 10px rgba(59, 130, 246, 0.4); }
.glass-checkbox:checked::after {
  content: ''; position: absolute; left: 5px; top: 2px; width: 4px; height: 8px; border: solid white; border-width: 0 2px 2px 0; transform: rotate(45deg);
}
.mini-tag {
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.08) 0%, rgba(255, 255, 255, 0.02) 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.1);
}
@keyframes gentle-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.8; transform: scale(1.05); }
}
.alert-pulse { animation: gentle-pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite; }
</style>
