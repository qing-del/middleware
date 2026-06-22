<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi, type AuditNoteItem, type AuditMetaItem, type AuditImageItem, type AuditBatchReviewDTO, type PageResult } from '@/api/admin'
import {
  Shield, Search, FileText, Image as ImageIcon, Hash,
  Loader2, ChevronLeft, ChevronRight, Eye, X, ExternalLink,
  User, Clock, AlertCircle, CheckCircle2
} from 'lucide-vue-next'
import { confirmAction, toastWarning } from '@/utils/feedback'

// ── Types & Constants ───────────────────────────
type AuditType = 'note' | 'image' | 'meta'

interface NormalizedAuditItem {
  id: number
  resourceId: number
  title: string
  applicant: string
  applicantId: number
  submitTime: string
  status: number
  type: AuditType
  raw: any
}

// ── State ─────────────────────────────────────────
const router = useRouter()
const currentType = ref<AuditType>('note')
const currentStatus = ref(0)
const currentPage = ref(1)
const pageSize = ref(15)
const totalCount = ref(0)
const applicantIdFilter = ref('')
const loading = ref(true)
const auditList = ref<NormalizedAuditItem[]>([])
const selectedIds = ref<Set<number>>(new Set())

// Modal State
const showModal = ref(false)
const currentRecord = ref<NormalizedAuditItem | null>(null)
const rejectReason = ref('')
const processingReview = ref(false)

// ── Computed ──────────────────────────────────────
const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / pageSize.value)))
const isBatchMode = computed(() => selectedIds.value.size > 0 && currentStatus.value === pendingStatusFor())
const statusOptions = computed(() => currentType.value === 'note'
  ? [
      { value: 0, label: '待审核' },
      { value: 1, label: '已通过' },
      { value: 2, label: '已驳回' }
    ]
  : [
      { value: 1, label: '审核中' },
      { value: 2, label: '已通过' },
      { value: 3, label: '已驳回' }
    ])

// ── Helpers ───────────────────────────────────────
function pendingStatusFor(type: AuditType = currentType.value): number {
  return type === 'note' ? 0 : 1
}

function approveStatusFor(type: AuditType = currentType.value): number {
  return type === 'note' ? 1 : 2
}

function rejectStatusFor(type: AuditType = currentType.value): number {
  return type === 'note' ? 2 : 3
}

function formatDate(raw: string): string {
  if (!raw) return '-'
  const d = new Date(raw)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function normalizeData(data: any[], type: AuditType): NormalizedAuditItem[] {
  return data.map(item => {
    if (type === 'note') {
      const note = item as AuditNoteItem
      return {
        id: note.id,
        resourceId: note.noteId || note.id,
        title: note.noteTitle || note.title || `笔记 #${note.noteId || note.id}`,
        applicant: note.applicantUsername || note.nickname || `UID: ${note.applicantUserId}`,
        applicantId: note.applicantUserId,
        submitTime: formatDate(note.updateTime),
        status: note.status,
        type: 'note',
        raw: item
      }
    } else if (type === 'image') {
      const img = item as AuditImageItem
      return {
        id: img.id,
        resourceId: img.imageId || img.id,
        title: img.filename,
        applicant: img.applicantUsername || img.nickname || `UID: ${img.applicantUserId}`,
        applicantId: img.applicantUserId,
        submitTime: formatDate(img.updateTime),
        status: img.status,
        type: 'image',
        raw: item
      }
    } else {
      const meta = item as AuditMetaItem
      return {
        id: meta.id,
        resourceId: meta.targetId || meta.id,
        title: `[标签] ${meta.targetName}`,
        applicant: meta.applicantUsername || meta.nickname || `UID: ${meta.applicantUserId}`,
        applicantId: meta.applicantUserId,
        submitTime: formatDate(meta.updateTime),
        status: meta.status,
        type: 'meta',
        raw: item
      }
    }
  })
}

function statusClass(status: number, type: AuditType = currentType.value): string {
  if (status === approveStatusFor(type)) return 'text-emerald-300 bg-emerald-500/10 border-emerald-500/20'
  if (status === rejectStatusFor(type)) return 'text-rose-300 bg-rose-500/10 border-rose-500/20'
  if (type !== 'note' && status === pendingStatusFor(type)) return 'text-sky-300 bg-sky-500/10 border-sky-500/20'
  return 'text-amber-300 bg-amber-500/10 border-amber-500/20'
}

function statusLabel(status: number, type: AuditType = currentType.value): string {
  if (status === approveStatusFor(type)) return '已通过'
  if (status === rejectStatusFor(type)) return '已驳回'
  return type === 'note' ? '待审核' : '审核中'
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

// ── Data Fetching ─────────────────────────────────
async function fetchAuditData() {
  loading.value = true
  try {
    const params = {
      status: currentStatus.value,
      applicantUserId: applicantIdFilter.value ? parseInt(applicantIdFilter.value) : undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    }

    let res: PageResult<any>
    if (currentType.value === 'note') {
      res = await adminApi.getNoteAuditList(params)
    } else if (currentType.value === 'image') {
      res = await adminApi.getImageAuditList(params)
    } else {
      res = await adminApi.getMetaAuditList(params)
    }

    auditList.value = normalizeData(res.records || [], currentType.value)
    totalCount.value = res.total || 0
  } catch (error) {
    console.error('Failed to fetch audit data:', error)
  } finally {
    loading.value = false
  }
}

// ── Actions ───────────────────────────────────────
function handleTypeChange(type: AuditType) {
  if (currentType.value === type) return
  currentType.value = type
  currentStatus.value = pendingStatusFor(type)
  currentPage.value = 1
  selectedIds.value.clear()
  fetchAuditData()
}

function handleStatusChange(status: number) {
  const sNum = Number(status)
  if (currentStatus.value === sNum) return
  currentStatus.value = sNum
  currentPage.value = 1
  selectedIds.value.clear()
  fetchAuditData()
}

function handlePageChange(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  currentPage.value = page
  fetchAuditData()
}

function toggleSelectAll(checked: boolean) {
  if (checked) {
    auditList.value.forEach(item => selectedIds.value.add(item.id))
  } else {
    selectedIds.value.clear()
  }
}

function toggleSelect(id: number) {
  if (selectedIds.value.has(id)) {
    selectedIds.value.delete(id)
  } else {
    selectedIds.value.add(id)
  }
}

async function handleBatchApprove() {
  if (selectedIds.value.size === 0) return
  if (!await confirmAction({ content: `确定通过选中的 ${selectedIds.value.size} 项审核吗？` })) return

  loading.value = true
  try {
    const data: AuditBatchReviewDTO = {
      ids: Array.from(selectedIds.value),
      status: approveStatusFor(),
    }

    if (currentType.value === 'note') await adminApi.batchReviewNotes(data)
    else if (currentType.value === 'image') await adminApi.batchReviewImages(data)
    else await adminApi.batchReviewMetas(data)

    selectedIds.value.clear()
    await fetchAuditData()
  } finally {
    loading.value = false
  }
}

function openDetail(item: NormalizedAuditItem) {
  currentRecord.value = item
  rejectReason.value = ''
  showModal.value = true
}

function viewAuditNoteDetail(item: NormalizedAuditItem) {
  router.push(`/admin/notes/${item.resourceId}`)
}

function closeModal() {
  showModal.value = false
}

async function handleSingleReview(status: number) {
  if (!currentRecord.value) return
  const rejectedStatus = rejectStatusFor()
  if (status === rejectedStatus && !rejectReason.value) {
    toastWarning('请填写驳回原因')
    return
  }

  processingReview.value = true
  try {
    const data: AuditBatchReviewDTO = {
      ids: [currentRecord.value.id],
      status,
      rejectReason: status === rejectedStatus ? rejectReason.value : undefined
    }

    if (currentType.value === 'note') await adminApi.batchReviewNotes(data)
    else if (currentType.value === 'image') await adminApi.batchReviewImages(data)
    else await adminApi.batchReviewMetas(data)

    closeModal()
    await fetchAuditData()
  } finally {
    processingReview.value = false
  }
}

// ── Lifecycle ─────────────────────────────────────
onMounted(() => {
  fetchAuditData()
})

watch(applicantIdFilter, () => {
  currentPage.value = 1
  fetchAuditData()
})
</script>

<template>
  <div class="relative mx-auto max-w-7xl space-y-6 pb-20">
    <!-- Background Glows (consistent with Users.vue) -->
    <div class="fixed top-[-12%] right-[-6%] z-0 h-[520px] w-[520px] rounded-full bg-rose-500/10 blur-[160px] pointer-events-none"></div>
    <div class="fixed bottom-[-14%] left-[-6%] z-0 h-[460px] w-[460px] rounded-full bg-indigo-500/10 blur-[150px] pointer-events-none"></div>

    <!-- Header (consistent with Users.vue) -->
    <div class="relative z-10 flex flex-col justify-between gap-4 md:flex-row md:items-center">
      <div class="flex items-center space-x-3">
        <div class="rounded-xl border border-rose-400/20 bg-rose-400/10 p-2 text-rose-300">
          <Shield class="h-5 w-5" />
        </div>
        <div>
          <h2 class="text-xl font-bold text-white">审核大厅</h2>
          <p class="mt-0.5 text-xs text-slate-400">管理全站资源内容的审批与合规监控</p>
        </div>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <!-- Tabs disguised as buttons -->
        <div class="flex bg-black/20 p-1 rounded-xl border border-white/10 mr-2">
          <button 
            v-for="tab in [{id:'note', label:'笔记'}, {id:'image', label:'图片'}, {id:'meta', label:'标签'}]"
            :key="tab.id"
            class="px-4 py-1.5 text-xs font-bold rounded-lg transition-all"
            :class="currentType === tab.id ? 'bg-rose-500 text-white shadow-lg shadow-rose-500/20' : 'text-slate-400 hover:text-slate-200'"
            @click="handleTypeChange(tab.id as AuditType)"
          >
            {{ tab.label }}
          </button>
        </div>

        <select :value="currentStatus" class="admin-input w-28" @change="handleStatusChange(Number(($event.target as HTMLSelectElement).value))">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">状态: {{ option.label }}</option>
        </select>

        <div class="group relative flex h-9 w-9 items-center overflow-hidden rounded-xl border border-white/10 bg-black/20 transition-all duration-300 ease-out hover:w-28 focus-within:!w-48 focus-within:border-rose-400/50 focus-within:bg-black/40 focus-within:ring-2 focus-within:ring-rose-400/10">
          <label class="z-10 flex h-full w-9 flex-shrink-0 cursor-pointer items-center justify-center text-slate-500 transition-colors group-hover:text-slate-300 group-focus-within:text-rose-300">
            <Search class="h-4 w-4" />
          </label>
          <input v-model="applicantIdFilter" type="number" placeholder="UID..." class="absolute left-9 h-full w-[150px] bg-transparent pr-4 text-sm text-white opacity-0 outline-none transition-opacity duration-300 group-hover:opacity-100 focus-within:!opacity-100 placeholder:text-slate-500" />
        </div>
      </div>
    </div>

    <!-- Batch Action Bar (consistent with Users.vue) -->
    <Transition name="batch-float">
      <div v-if="isBatchMode" class="glass-panel relative z-10 flex items-center justify-between rounded-xl px-4 py-3">
        <span class="text-sm font-bold text-rose-200">已选择 <span class="mx-1 text-white">{{ selectedIds.size }}</span> 项审核申请</span>
        <button class="flex items-center space-x-1.5 rounded-lg border border-emerald-500/20 bg-emerald-500/10 px-3 py-1.5 text-xs font-bold text-emerald-300 transition-all hover:bg-emerald-500 hover:text-white" @click="handleBatchApprove">
          <CheckCircle2 class="h-3.5 w-3.5" />
          <span>批量通过</span>
        </button>
      </div>
    </Transition>

    <!-- Data Table (consistent with Users.vue) -->
    <div class="glass-panel relative z-10 overflow-hidden rounded-2xl border border-white/10">
      <div class="overflow-x-auto">
        <table class="w-full border-collapse text-left">
          <thead>
            <tr>
              <th class="w-10 border-b border-white/5 px-4 py-4"><input type="checkbox" class="glass-checkbox" :checked="selectedIds.size === auditList.length && auditList.length > 0" @change="toggleSelectAll(($event.target as HTMLInputElement).checked)" /></th>
              <th class="border-b border-white/5 px-4 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">ID</th>
              <th class="border-b border-white/5 px-4 py-4 text-xs font-bold uppercase tracking-wider text-slate-400 w-1/3">审核内容</th>
              <th class="border-b border-white/5 px-4 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">申请人</th>
              <th class="border-b border-white/5 px-4 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">状态</th>
              <th class="border-b border-white/5 px-4 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">提交时间</th>
              <th class="border-b border-white/5 px-4 py-4 text-right text-xs font-bold uppercase tracking-wider text-slate-400">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr v-if="loading && auditList.length === 0">
              <td colspan="7" class="px-6 py-20 text-center">
                <Loader2 class="mx-auto mb-3 h-8 w-8 animate-spin text-rose-400" />
                <span class="text-xs font-bold uppercase tracking-widest text-slate-500">正在获取审批列表...</span>
              </td>
            </tr>
            <tr v-else-if="auditList.length === 0">
              <td colspan="7" class="px-6 py-20 text-center text-sm text-slate-500">
                <div class="flex flex-col items-center opacity-40">
                  <AlertCircle class="h-10 w-10 mb-2" />
                  <span>暂无待处理的审核申请</span>
                </div>
              </td>
            </tr>
            <template v-else>
              <transition-group name="list">
                <tr
                  v-for="item in auditList"
                  :key="item.id"
                  class="group transition-colors duration-300 hover:bg-white/5 cursor-pointer"
                  @click="openDetail(item)"
                >
                  <td class="px-4 py-4" @click.stop><input type="checkbox" class="glass-checkbox" :checked="selectedIds.has(item.id)" @change="toggleSelect(item.id)" /></td>
                  <td class="px-4 py-4 font-mono text-xs text-slate-500">{{ item.id }}</td>
                  <td class="px-4 py-4">
                    <div class="flex items-center space-x-3">
                      <div class="w-9 h-9 rounded-lg bg-white/5 flex items-center justify-center text-slate-400 group-hover:text-rose-400 transition-colors">
                        <FileText v-if="item.type === 'note'" class="w-4.5 h-4.5" />
                        <ImageIcon v-else-if="item.type === 'image'" class="w-4.5 h-4.5" />
                        <Hash v-else class="w-4.5 h-4.5" />
                      </div>
                      <div class="flex flex-col truncate">
                        <span class="text-sm font-bold text-slate-200 group-hover:text-rose-300 transition-colors truncate">{{ item.title }}</span>
                        <span class="text-[10px] text-slate-500 uppercase tracking-tighter mt-0.5">TYPE: {{ item.type.toUpperCase() }}</span>
                      </div>
                    </div>
                  </td>
                  <td class="px-4 py-4">
                    <div class="flex items-center space-x-2">
                      <div class="w-6 h-6 rounded-full bg-indigo-500/10 flex items-center justify-center">
                        <User class="w-3 h-3 text-indigo-400" />
                      </div>
                      <span class="text-xs text-slate-400">{{ item.applicant }}</span>
                    </div>
                  </td>
                  <td class="px-4 py-4">
                    <span class="inline-flex items-center rounded-md border px-2 py-1 text-[10px] font-bold uppercase tracking-wider" :class="statusClass(item.status, item.type)">
                      {{ statusLabel(item.status, item.type) }}
                    </span>
                  </td>
                  <td class="px-4 py-4">
                    <span class="text-xs text-slate-500 font-mono flex items-center">
                      <Clock class="w-3 h-3 mr-1.5 opacity-50" />
                      {{ item.submitTime }}
                    </span>
                  </td>
                  <td class="px-4 py-4 text-right">
                    <div class="flex items-center justify-end space-x-2 translate-x-1 opacity-0 transition-all duration-200 group-hover:translate-x-0 group-hover:opacity-100">
                      <button v-if="item.type === 'note'"
                        class="flex h-7 w-7 items-center justify-center rounded bg-white/5 text-slate-400 transition-colors hover:bg-rose-500/20 hover:text-rose-400" title="查看笔记详情" @click.stop="viewAuditNoteDetail(item)">
                        <ExternalLink class="h-3.5 w-3.5" />
                      </button>
                      <button v-else
                        class="flex h-7 w-7 items-center justify-center rounded bg-white/5 text-slate-400 transition-colors hover:bg-rose-500/20 hover:text-rose-400" title="审核详情" @click.stop="openDetail(item)">
                        <ExternalLink class="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </td>
                </tr>
              </transition-group>
            </template>
          </tbody>
        </table>
      </div>

      <!-- Pagination (consistent with Users.vue) -->
      <div v-if="!loading && auditList.length > 0" class="flex items-center justify-between border-t border-white/5 bg-white/[0.01] px-6 py-4">
        <span class="text-xs text-slate-500">共 {{ totalCount }} 条申请</span>
        <div class="flex items-center space-x-1">
          <button class="flex h-7 w-7 items-center justify-center rounded text-slate-500 hover:bg-white/5 hover:text-white disabled:opacity-50" :disabled="currentPage <= 1" @click="handlePageChange(currentPage - 1)"><ChevronLeft class="h-4 w-4" /></button>
          <template v-for="page in visiblePages()" :key="page">
            <button v-if="totalPages > 1" class="flex h-7 w-7 items-center justify-center rounded text-xs font-bold transition-colors" :class="page === currentPage ? 'border border-rose-400/30 bg-rose-400/15 text-rose-300' : 'text-slate-400 hover:bg-white/5 hover:text-white'" @click="handlePageChange(page)">{{ page }}</button>
          </template>
          <button class="flex h-7 w-7 items-center justify-center rounded text-slate-500 hover:bg-white/5 hover:text-white" :disabled="currentPage >= totalPages" @click="handlePageChange(currentPage + 1)"><ChevronRight class="h-4 w-4" /></button>
        </div>
      </div>

      <!-- Inner Loading Overlay (consistent with previous design but more subtle) -->
      <transition name="fade">
        <div v-if="loading && auditList.length > 0" class="absolute inset-0 bg-black/10 backdrop-blur-[1px] z-10 flex items-center justify-center rounded-2xl">
          <Loader2 class="w-8 h-8 animate-spin text-rose-500" />
        </div>
      </transition>
    </div>

    <!-- Modals (consistent with Users.vue) -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showModal" class="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm" @click="closeModal"></div>
      </Transition>
      <Transition name="modal">
        <div v-if="showModal" class="fixed inset-0 z-50 flex items-center justify-center px-4" @click.self="closeModal">
          <div class="glass-panel modal-card relative z-10 w-full max-w-2xl rounded-3xl p-8 overflow-hidden">
            <div class="pointer-events-none absolute -top-10 -right-10 h-32 w-32 rounded-full bg-rose-400/20 blur-[40px]"></div>
            
            <div class="mb-6 flex items-center justify-between relative z-10">
              <div class="flex items-center space-x-3">
                <div class="p-2 bg-rose-500/10 rounded-xl text-rose-400">
                  <Eye class="h-5 w-5" />
                </div>
                <div>
                  <h3 class="text-xl font-bold text-white">审核详情</h3>
                  <p class="text-[10px] text-slate-500 uppercase tracking-widest font-semibold mt-0.5">Review Application</p>
                </div>
              </div>
              <button class="text-slate-500 transition-colors hover:text-white" @click="closeModal"><X class="h-5 w-5" /></button>
            </div>

            <div class="space-y-6 relative z-10">
              <div class="grid grid-cols-2 gap-4">
                <div class="p-4 bg-white/5 rounded-2xl border border-white/10">
                  <span class="text-[10px] text-slate-500 uppercase block mb-1">申请标题 / 内容</span>
                  <span class="text-sm font-bold text-white">{{ currentRecord?.title }}</span>
                </div>
                <div class="p-4 bg-white/5 rounded-2xl border border-white/10">
                  <span class="text-[10px] text-slate-500 uppercase block mb-1">申请人</span>
                  <span class="text-sm font-bold text-white">{{ currentRecord?.applicant }}</span>
                </div>
              </div>

              <div class="p-4 bg-white/5 rounded-2xl border border-white/10">
                <span class="text-[10px] text-slate-500 uppercase block mb-2">内容预览</span>
                <div class="min-h-[120px] bg-black/30 rounded-xl flex items-center justify-center border border-white/5">
                  <template v-if="currentRecord?.type === 'note'">
                    <div class="flex flex-col items-center gap-3 py-4">
                      <FileText class="w-8 h-8 text-rose-400" />
                      <span class="text-sm font-bold text-white">{{ currentRecord?.title }}</span>
                      <button
                        class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-rose-500/10 text-rose-400 hover:bg-rose-500 hover:text-white transition-all text-xs font-bold border border-rose-500/20"
                        @click="currentRecord && viewAuditNoteDetail(currentRecord)"
                      >
                        <ExternalLink class="w-3.5 h-3.5" />
                        <span>查看完整笔记内容</span>
                      </button>
                    </div>
                  </template>
                  <template v-else-if="currentRecord?.type === 'image'">
                    <div class="p-4 w-full flex justify-center">
                      <img v-if="currentRecord.raw.ossUrl" :src="currentRecord.raw.ossUrl" class="max-h-40 rounded-lg shadow-2xl object-contain border border-white/10" />
                      <div v-else class="text-slate-600 flex flex-col items-center"><ImageIcon class="h-10 w-10 mb-2" /><span class="text-xs">暂无预览图</span></div>
                    </div>
                  </template>
                  <template v-else>
                    <div class="flex flex-col items-center opacity-40">
                      <Hash class="w-8 h-8 mb-2" />
                      <span class="text-xs">标签申请记录</span>
                    </div>
                  </template>
                </div>
              </div>

              <div v-if="currentStatus === pendingStatusFor()" class="space-y-4">
                <div>
                  <label class="mb-1.5 block text-[10px] font-bold uppercase tracking-widest text-slate-500">驳回反馈 (仅在驳回时必填)</label>
                  <input v-model="rejectReason" type="text" placeholder="请输入驳回理由..." class="admin-input w-full" />
                </div>
                <div class="flex justify-end space-x-3 pt-2">
                  <button 
                    class="rounded-xl px-5 py-2.5 text-sm font-bold text-rose-400 border border-rose-500/20 hover:bg-rose-500/10 transition-colors"
                    :disabled="processingReview"
                    @click="handleSingleReview(rejectStatusFor())"
                  >
                    驳回申请
                  </button>
                  <button 
                    class="flex items-center space-x-2 rounded-xl bg-emerald-600 px-5 py-2.5 text-sm font-bold text-white shadow-[0_0_15px_rgba(16,185,129,0.35)] transition-all hover:bg-emerald-500"
                    :disabled="processingReview"
                    @click="handleSingleReview(approveStatusFor())"
                  >
                    <Loader2 v-if="processingReview" class="h-4 w-4 animate-spin" />
                    <span>通过审核</span>
                  </button>
                </div>
              </div>
              <div v-else class="p-4 bg-white/5 rounded-2xl border border-white/10 flex items-center justify-between">
                <span class="text-xs text-slate-500 italic">该申请已完成审批，无法再次修改状态。</span>
                <span class="text-xs font-bold uppercase tracking-widest" :class="statusClass(currentRecord!.status, currentRecord!.type)">{{ statusLabel(currentRecord!.status, currentRecord!.type) }}</span>
              </div>
            </div>
          </div>
        </div>
      </Transition>
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
  background: #f43f5e;
  border-color: #f43f5e;
  box-shadow: 0 0 10px rgba(244, 63, 94, 0.4);
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

.admin-input {
  height: 36px;
  border-radius: 0.75rem;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(0, 0, 0, 0.2);
  padding: 0 0.75rem;
  color: white;
  font-size: 0.75rem;
  outline: none;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}

.admin-input::placeholder {
  color: rgb(100 116 139);
}

.admin-input:focus {
  border-color: rgba(244, 63, 94, 0.5);
  background: rgba(0, 0, 0, 0.35);
  box-shadow: 0 0 0 2px rgba(244, 63, 94, 0.08);
}

/* List Transitions */
.list-enter-active,
.list-leave-active {
  transition: all 0.4s ease;
}
.list-enter-from {
  opacity: 0;
  transform: translateY(20px);
}
.list-leave-to {
  opacity: 0;
  transform: translateX(30px);
}
.list-move {
  transition: transform 0.4s ease;
}

/* Modal Transitions (consistent with Users.vue) */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.28s ease, transform 0.38s cubic-bezier(0.22, 1, 0.36, 1);
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
  transform: scale(0.92) translateY(18px);
}

.modal-card {
  transform-origin: center center;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.45), inset 0 1px 1px rgba(255, 255, 255, 0.05);
}

/* Scrollbar Style */
::-webkit-scrollbar {
  width: 4px;
}
::-webkit-scrollbar-track {
  background: transparent;
}
::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 10px;
}
::-webkit-scrollbar-thumb:hover {
  background: rgba(244, 63, 94, 0.5);
}

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

@media (prefers-reduced-motion: reduce) {
  .list-enter-active,
  .list-leave-active,
  .list-move,
  .fade-enter-active,
  .fade-leave-active,
  .modal-enter-active,
  .modal-leave-active,
  .batch-float-enter-active,
  .batch-float-leave-active {
    transition-duration: 0.01s !important;
  }
  .list-enter-from,
  .list-leave-to,
  .modal-enter-from,
  .modal-leave-to {
    opacity: 0;
    transform: none;
  }
}
</style>
