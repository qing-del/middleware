<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api/admin'
import type { AdminNoteItem, AdminNoteQueryParams, PageResult } from '@/api/admin'
import { getNoteStatusInfo, NoteStatusCode } from '@/api/notes'
import {
  FileText, Search, Trash2, Loader2, ChevronLeft, ChevronRight,
  RefreshCw, FileCode, Globe, X, Layers, Clock,
  CheckCircle2, AlertTriangle, XCircle
} from 'lucide-vue-next'

// ── State ─────────────────────────────────────────
const router = useRouter()
const loading = ref(true)
const noteList = ref<AdminNoteItem[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(15)

// Filters
const filterUserId = ref('')
const filterTopicId = ref('')
const filterTitle = ref('')
const filterStatus = ref('')

// Selection
const selectedIds = ref<Set<number>>(new Set())

// User nickname cache
const userNicknameCache = ref<Map<number, string>>(new Map())

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

function formatDate(raw: string): string {
  if (!raw) return '-'
  const d = new Date(raw)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatNumber(n: number): string { return n.toLocaleString() }

function getUserDisplayName(userId: number): string {
  const nick = userNicknameCache.value.get(userId)
  return nick ? `${nick} (UID:${userId})` : `UID:${userId}`
}

async function prefetchUserNicknames() {
  const ids = [...new Set(noteList.value.map(n => n.userId).filter(Boolean))]
  const uncached = ids.filter(id => !userNicknameCache.value.has(id))
  if (uncached.length === 0) return
  const results = await Promise.allSettled(
    uncached.map(id => adminApi.getUserDetail(id))
  )
  results.forEach((r, i) => {
    if (r.status === 'fulfilled' && r.value) {
      userNicknameCache.value.set(uncached[i], r.value.nickname || String(uncached[i]))
    }
  })
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

function isStatusEmphasized(status: number): boolean {
  return [NoteStatusCode.PENDING_INFO, NoteStatusCode.PENDING_AUDIT, NoteStatusCode.REJECTED].includes(status as any)
}

function getStatusEmphasisClass(status: number): string {
  if (status === NoteStatusCode.REJECTED) return 'status-breathe status-glow-rose'
  if (status === NoteStatusCode.PENDING_INFO || status === NoteStatusCode.PENDING_AUDIT) return 'status-breathe status-glow-amber'
  return 'status-chip-steady'
}

function getStatusIcon(iconName: string) {
  const map: Record<string, any> = {
    FilePlus: FileCode, AlertTriangle, RefreshCw, FileCode, Clock, CheckCircle2, Globe, XCircle, Trash2
  }
  return map[iconName] ?? FileText
}

// ── Data fetching ─────────────────────────────────
async function fetchNotes() {
  loading.value = true
  try {
    const params: AdminNoteQueryParams = {
      userId: filterUserId.value ? Number(filterUserId.value) : undefined,
      topicId: filterTopicId.value ? Number(filterTopicId.value) : undefined,
      title: filterTitle.value || undefined,
      status: filterStatus.value ? Number(filterStatus.value) : undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    }
    const res = await adminApi.getNoteList(params)
    noteList.value = (res as unknown as PageResult<AdminNoteItem>).records ?? []
    total.value = (res as unknown as PageResult<AdminNoteItem>).total ?? 0
    if (noteList.value.length > 0) void prefetchUserNicknames()
  } finally {
    loading.value = false
  }
}

function handleSearch() { currentPage.value = 1; fetchNotes() }
function handlePageChange(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  currentPage.value = page; fetchNotes()
}

function toggleSelectAll(checked: boolean) {
  if (checked) noteList.value.forEach(n => selectedIds.value.add(n.id))
  else selectedIds.value.clear()
}
function toggleSelect(id: number) {
  selectedIds.value.has(id) ? selectedIds.value.delete(id) : selectedIds.value.add(id)
}

// ── Actions ───────────────────────────────────────
async function handleDelete(id: number) {
  if (!showConfirm('确定删除该笔记吗？')) return
  await adminApi.deleteNotes([id])
  await fetchNotes()
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) return
  if (!showConfirm(`确定删除已选择的 ${selectedIds.value.size} 篇笔记吗？`)) return
  await adminApi.deleteNotes([...selectedIds.value])
  selectedIds.value.clear()
  await fetchNotes()
}

async function handleConvert(id: number) {
  try {
    await adminApi.convertNote(id)
    await fetchNotes()
  } catch {
    showAlert('转换失败，请确认笔记关联完整')
  }
}

async function handleViewSource(id: number) {
  try {
    const src = await adminApi.getNoteSource(id)
    const note = noteList.value.find(n => n.id === id)
    sourceTitle.value = note?.title ?? '笔记源文件'
    sourceContent.value = src as unknown as string
    showSourceModal.value = true
  } catch {
    showAlert('无法获取源文件')
  }
}

function handleViewHtml(id: number) {
  router.push(`/admin/notes/${id}`)
}

// ── Init ──────────────────────────────────────────
onMounted(() => { fetchNotes() })
</script>

<template>
  <div class="relative max-w-[1400px] mx-auto space-y-6">
    <!-- ═══ Header ═══ -->
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 relative z-10">
      <div class="flex items-center space-x-3">
        <div class="p-2 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400">
          <FileText class="w-5 h-5" />
        </div>
        <div>
          <h2 class="text-xl font-bold text-white">全局笔记</h2>
          <p class="text-xs text-slate-400 mt-0.5">管理所有用户的笔记资产与生命周期</p>
        </div>
      </div>

      <div class="flex items-center space-x-2 flex-wrap gap-y-2">
        <input v-model="filterUserId" type="number" placeholder="UID..." class="w-20 bg-black/20 border border-white/10 rounded-xl py-2 px-2 text-xs text-white placeholder:text-slate-500 outline-none focus:border-rose-500/50 h-9" @keyup.enter="handleSearch" />
        <input v-model="filterTopicId" type="number" placeholder="主题ID..." class="w-20 bg-black/20 border border-white/10 rounded-xl py-2 px-2 text-xs text-white placeholder:text-slate-500 outline-none focus:border-rose-500/50 h-9" @keyup.enter="handleSearch" />
        <select v-model="filterStatus" class="bg-black/20 border border-white/10 rounded-xl py-2 px-2 text-xs text-white outline-none focus:border-rose-500/50 h-9" @change="handleSearch">
          <option value="">状态:全部</option>
          <option :value="NoteStatusCode.NEW">已创建</option>
          <option :value="NoteStatusCode.PENDING_INFO">缺失信息</option>
          <option :value="NoteStatusCode.READY_TO_CONVERT">待转换</option>
          <option :value="NoteStatusCode.CONVERTED">已转换</option>
          <option :value="NoteStatusCode.PENDING_AUDIT">审核中</option>
          <option :value="NoteStatusCode.APPROVED">已通过</option>
          <option :value="NoteStatusCode.PUBLISHED">已公开</option>
          <option :value="NoteStatusCode.REJECTED">已拒绝</option>
        </select>
        <div class="relative group flex items-center bg-black/20 border border-white/10 rounded-xl overflow-hidden transition-all duration-300 ease-out w-9 hover:w-40 focus-within:!w-56 focus-within:bg-black/40 focus-within:border-rose-500/50 h-9">
          <label class="w-9 h-full flex-shrink-0 flex items-center justify-center text-slate-500 group-hover:text-slate-300 group-focus-within:text-rose-400 transition-colors cursor-pointer z-10"><Search class="w-4 h-4" /></label>
          <input v-model="filterTitle" type="text" placeholder="标题..." class="absolute left-9 w-[180px] h-full bg-transparent text-sm text-white placeholder:text-slate-500 outline-none opacity-0 group-hover:opacity-100 focus-within:!opacity-100 transition-opacity duration-300 pr-4" @keyup.enter="handleSearch" />
        </div>
      </div>
    </div>

    <!-- ═══ Batch Action Bar ═══ -->
    <Transition name="batch-float">
      <div v-if="isBatchMode" class="glass-panel rounded-xl px-4 py-3 flex items-center justify-between sticky top-0 z-30">
        <div class="flex items-center space-x-3">
          <span class="flex h-2 w-2 relative"><span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-400 opacity-75" /><span class="relative inline-flex rounded-full h-2 w-2 bg-rose-500" /></span>
          <span class="text-sm font-bold text-rose-300">已选取 <span class="text-white mx-1">{{ selectedIds.size }}</span> 篇笔记</span>
        </div>
        <button class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-rose-500/10 text-rose-400 hover:bg-rose-500 hover:text-white transition-all text-xs font-bold border border-rose-500/20" @click="handleBatchDelete">
          <Trash2 class="w-3.5 h-3.5" /><span>批量删除</span>
        </button>
      </div>
    </Transition>

    <!-- ═══ Data Table ═══ -->
    <div class="glass-panel rounded-2xl overflow-hidden border border-white/10 relative z-10">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr>
              <th class="px-4 py-4 border-b border-white/5 w-10"><input type="checkbox" class="glass-checkbox" :checked="selectedIds.size === noteList.length && noteList.length > 0" @change="toggleSelectAll(($event.target as HTMLInputElement).checked)" /></th>
              <th class="px-4 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">ID</th>
              <th class="px-4 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider w-[25%]">标题</th>
              <th class="px-4 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">主题</th>
              <th class="px-4 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">大小</th>
              <th class="px-4 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">状态</th>
              <th class="px-4 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">创建时间</th>
              <th class="px-4 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr v-if="loading"><td colspan="8" class="px-6 py-16 text-center"><Loader2 class="w-6 h-6 text-rose-400 animate-spin mx-auto mb-3" /><span class="text-xs text-slate-500">加载中...</span></td></tr>
            <tr v-else-if="noteList.length === 0"><td colspan="8" class="px-6 py-16 text-center text-sm text-slate-500">暂无笔记数据</td></tr>
            <TransitionGroup v-else name="list">
            <tr v-for="note in noteList" :key="note.id" class="hover:bg-white/[0.02] transition-colors group" @dblclick="handleViewSource(note.id)">
              <td class="px-4 py-4" @click.stop><input type="checkbox" class="glass-checkbox" :checked="selectedIds.has(note.id)" @change="toggleSelect(note.id)" /></td>
              <td class="px-4 py-4 text-xs text-slate-500 font-mono">{{ note.id }}</td>
              <td class="px-4 py-4">
                <div class="flex flex-col">
                  <span class="text-sm font-bold text-slate-200 truncate max-w-[260px]">{{ note.title }}</span>
                  <span class="text-[10px] text-slate-500 mt-0.5">{{ getUserDisplayName(note.userId) }}</span>
                </div>
              </td>
              <td class="px-4 py-4">
                <span v-if="note.topicName" class="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold text-indigo-300 bg-indigo-500/10 border border-indigo-500/20">
                  <Layers class="w-3 h-3 mr-1" /> {{ note.topicName }}
                </span>
                <span v-else class="text-xs text-slate-600">-</span>
              </td>
              <td class="px-4 py-4 text-xs text-slate-500 font-mono">{{ formatBytes(note.mdFileSize) }}</td>
              <td class="px-4 py-4">
                <span class="inline-flex items-center px-2 py-1 rounded-md text-[10px] font-bold uppercase tracking-wider border note-status-chip" :class="[getNoteStatusInfo(note.status).cls, isStatusEmphasized(note.status) ? getStatusEmphasisClass(note.status) : 'status-chip-steady']">
                  <component :is="getStatusIcon(getNoteStatusInfo(note.status).icon)" class="w-3 h-3 mr-1" />
                  {{ getNoteStatusInfo(note.status).label }}
                </span>
                <span v-if="note.status === NoteStatusCode.PENDING_INFO && note.missingCount > 0" class="text-[10px] text-amber-500 ml-1">缺{{ note.missingCount }}项</span>
              </td>
              <td class="px-4 py-4 text-xs text-slate-500">{{ formatDate(note.createTime) }}</td>
              <td class="px-4 py-4 text-right">
                <div class="flex items-center justify-end space-x-2 opacity-50 group-hover:opacity-100 transition-opacity" @click.stop>
                  <button class="w-7 h-7 rounded bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-colors" title="查看源文件" @click="handleViewSource(note.id)">
                    <FileCode class="w-3.5 h-3.5" />
                  </button>
                  <button class="w-7 h-7 rounded bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-colors" title="查看预览" @click="handleViewHtml(note.id)">
                    <Globe class="w-3.5 h-3.5" />
                  </button>
                  <button v-if="note.status === NoteStatusCode.READY_TO_CONVERT || note.status === NoteStatusCode.REJECTED"
                    class="w-7 h-7 rounded bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-colors" title="转换笔记" @click="handleConvert(note.id)">
                    <RefreshCw class="w-3.5 h-3.5" />
                  </button>
                  <button class="w-7 h-7 rounded bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-colors" title="删除" @click="handleDelete(note.id)">
                    <Trash2 class="w-3.5 h-3.5" />
                  </button>
                </div>
              </td>
            </tr>
            </TransitionGroup>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div v-if="!loading && noteList.length > 0" class="px-4 py-3 border-t border-white/5 flex items-center justify-between bg-white/[0.01]">
        <span class="text-xs text-slate-500">共 {{ formatNumber(total) }} 篇笔记</span>
        <div class="flex items-center space-x-1">
          <button class="w-7 h-7 rounded flex items-center justify-center text-slate-500 hover:bg-white/5 hover:text-white disabled:opacity-50" :disabled="currentPage <= 1" @click="handlePageChange(currentPage - 1)"><ChevronLeft class="w-4 h-4" /></button>
          <template v-for="page in visiblePages()" :key="page">
            <button v-if="totalPages > 1" class="w-7 h-7 rounded flex items-center justify-center text-xs font-bold transition-colors"
              :class="page === currentPage ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30' : 'text-slate-400 hover:bg-white/5 hover:text-white'"
              @click="handlePageChange(page)">{{ page }}</button>
          </template>
          <button class="w-7 h-7 rounded flex items-center justify-center text-slate-500 hover:bg-white/5 hover:text-white" :disabled="currentPage >= totalPages" @click="handlePageChange(currentPage + 1)"><ChevronRight class="w-4 h-4" /></button>
        </div>
      </div>
    </div>

    <!-- ═══ Source Viewer Modal ═══ -->
    <Teleport to="body">
      <Transition name="modal-backdrop">
        <div v-if="showSourceModal" class="fixed inset-0 z-[60] bg-black/90 backdrop-blur-md" @click="showSourceModal = false" />
      </Transition>
      <Transition name="modal-panel">
        <div v-if="showSourceModal" class="fixed inset-0 z-[60] flex items-center justify-center px-4 pointer-events-none" @click.self="showSourceModal = false">
          <div class="glass-panel modal-card relative z-10 max-w-3xl w-full max-h-[85vh] rounded-2xl overflow-hidden flex flex-col pointer-events-auto">
            <div class="flex items-center justify-between px-6 py-4 border-b border-white/10">
              <div class="flex items-center space-x-3">
                <div class="w-8 h-8 rounded-lg bg-rose-500/10 flex items-center justify-center text-rose-400">
                  <FileText class="w-4 h-4" />
                </div>
                <h3 class="text-sm font-bold text-white truncate max-w-[400px]">{{ sourceTitle }}</h3>
              </div>
              <button class="text-slate-500 hover:text-white p-2 rounded-full hover:bg-white/5" @click="showSourceModal = false"><X class="w-5 h-5" /></button>
            </div>
            <div class="flex-1 overflow-y-auto p-6">
              <pre class="text-sm text-slate-300 font-mono whitespace-pre-wrap break-all">{{ sourceContent }}</pre>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.glass-panel { background: rgba(255,255,255,0.02); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.05); box-shadow: inset 0 1px 1px rgba(255,255,255,0.05); }
.glass-checkbox { appearance: none; width: 16px; height: 16px; border: 1px solid rgba(255,255,255,0.2); border-radius: 4px; background: rgba(0,0,0,0.2); cursor: pointer; position: relative; transition: all 0.2s; }
.glass-checkbox:checked { background: #f43f5e; border-color: #f43f5e; box-shadow: 0 0 10px rgba(244,63,94,0.4); }
.glass-checkbox:checked::after { content: ''; position: absolute; left: 5px; top: 2px; width: 4px; height: 8px; border: solid white; border-width: 0 2px 2px 0; transform: rotate(45deg); }

.note-status-chip { transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease, color 0.22s ease, background-color 0.22s ease; }

/* ── Modal Transitions ── */
.modal-backdrop-enter-active,
.modal-backdrop-leave-active { transition: opacity 0.28s ease; }
.modal-backdrop-enter-from,
.modal-backdrop-leave-to { opacity: 0; }

.modal-panel-enter-active,
.modal-panel-leave-active { transition: opacity 0.32s ease, transform 0.42s cubic-bezier(0.25, 1, 0.5, 1); }
.modal-panel-enter-from,
.modal-panel-leave-to { opacity: 0; transform: translateY(18px) scale(0.96); }
.modal-panel-enter-to,
.modal-panel-leave-from { opacity: 1; transform: translateY(0) scale(1); }

.modal-card { transform-origin: center center; box-shadow: 0 24px 80px rgba(15, 23, 42, 0.45), inset 0 1px 1px rgba(255, 255, 255, 0.05); }

/* ── Batch Float Animation ── */
@keyframes batch-slide-up {
  0% { opacity: 0; transform: translateY(24px) scale(0.96); }
  72% { opacity: 1; transform: translateY(-4px) scale(1.01); }
  100% { opacity: 1; transform: translateY(0) scale(1); }
}
.batch-float-enter-active { transition: opacity 0.42s cubic-bezier(0.22, 1.2, 0.36, 1); }
.batch-float-enter-from,
.batch-float-leave-to { opacity: 0; }
.batch-float-leave-active { transition: opacity 0.26s ease; }
.batch-float-leave-active > * { opacity: 0; transform: translateY(16px) scale(0.98); transition: transform 0.26s ease, opacity 0.26s ease; }

/* ── List Transitions ── */
.list-enter-active,
.list-leave-active { transition: all 0.4s ease; }
.list-enter-from { opacity: 0; transform: translateY(20px); }
.list-leave-to { opacity: 0; transform: translateX(30px); }
.list-move { transition: transform 0.4s ease; }

/* ── Status Breathing ── */
@keyframes status-breathe {
  0%, 100% { transform: translateY(0) scale(1); opacity: 1; }
  50% { transform: translateY(-0.5px) scale(1.02); opacity: 0.92; }
}
.status-breathe { animation: status-breathe 2.5s ease-in-out infinite; }
.status-glow-amber { box-shadow: 0 0 18px rgba(245, 158, 11, 0.16); }
.status-glow-rose { box-shadow: 0 0 18px rgba(244, 63, 94, 0.16); }
.status-chip-steady { box-shadow: none; }

/* ── prefers-reduced-motion ── */
@media (prefers-reduced-motion: reduce) {
  .status-breathe,
  .animate-ping { animation: none; }
  .batch-float-enter-active,
  .batch-float-leave-active,
  .modal-backdrop-enter-active,
  .modal-backdrop-leave-active,
  .modal-panel-enter-active,
  .modal-panel-leave-active,
  .list-enter-active,
  .list-leave-active,
  .list-move,
  .note-status-chip { transition-duration: 0.01s !important; animation-duration: 0.01s !important; }
  .list-enter-from,
  .list-leave-to,
  .modal-panel-enter-from,
  .modal-panel-leave-to { opacity: 0; transform: none; }
}
</style>
