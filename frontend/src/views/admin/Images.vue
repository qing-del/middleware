<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import type { AdminImageItem, PageResult } from '@/api/admin'
import { Image, Search, Trash2, Loader2, X, ChevronLeft, ChevronRight, Globe, Eye, Upload, Info } from 'lucide-vue-next'

const loading = ref(true)
const imageList = ref<AdminImageItem[]>([])
const total = ref(0)
const filterUserId = ref('')
const filterTopicId = ref('')
const searchFilename = ref('')
const filterIsPass = ref('')
const filterIsPublic = ref('')
const currentPage = ref(1)
const pageSize = ref(12)
const selectedIds = ref<Set<number>>(new Set())
const previewUrl = ref('')
const showPreview = ref(false)

const isBatchMode = computed(() => selectedIds.value.size > 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function getStatusInfo(isPass: number) {
  switch (isPass) {
    case 1: return { label: '已通过', cls: 'text-emerald-300 bg-emerald-500/10 border-emerald-500/20' }
    case 2: return { label: '已拒绝', cls: 'text-rose-300 bg-rose-500/10 border-rose-500/20' }
    default: return { label: '待审核', cls: 'text-amber-300 bg-amber-500/10 border-amber-500/20' }
  }
}

function formatBytes(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, index)).toFixed(index > 0 ? 1 : 0)} ${units[index]}`
}

function formatDate(raw: string): string {
  if (!raw) return '-'
  const d = new Date(raw)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
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

async function fetchImages() {
  try {
    const res = await adminApi.getImageList({
      userId: filterUserId.value ? Number(filterUserId.value) : undefined,
      topicId: filterTopicId.value ? Number(filterTopicId.value) : undefined,
      filename: searchFilename.value || undefined,
      isPass: filterIsPass.value ? Number(filterIsPass.value) : undefined,
      isPublic: filterIsPublic.value ? Number(filterIsPublic.value) : undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    })
    imageList.value = (res as unknown as PageResult<AdminImageItem>).records ?? []
    total.value = (res as unknown as PageResult<AdminImageItem>).total ?? 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loading.value = true
  fetchImages()
}

function handlePageChange(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  currentPage.value = page
  loading.value = true
  fetchImages()
}

function toggleSelect(id: number) {
  selectedIds.value.has(id) ? selectedIds.value.delete(id) : selectedIds.value.add(id)
}

async function handleDelete(id: number) {
  if (!confirm('确定删除该图片吗？')) return
  await adminApi.deleteImages([id])
  await fetchImages()
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) return
  if (!confirm(`确定删除 ${selectedIds.value.size} 张图片吗？`)) return
  await adminApi.deleteImages([...selectedIds.value])
  selectedIds.value.clear()
  await fetchImages()
}

async function handleSetPublic(id: number, isPublic: number) {
  await adminApi.setImagePublic({ id }, isPublic)
  await fetchImages()
}

async function handleTransfer() {
  if (selectedIds.value.size === 0) return
  await adminApi.transferToCloud([...selectedIds.value])
  selectedIds.value.clear()
  await fetchImages()
}

onMounted(() => {
  fetchImages()
})
</script>

<template>
  <div class="relative mx-auto max-w-[1400px] space-y-6 pb-20">
    <div class="relative z-10 flex flex-col justify-between gap-4 md:flex-row md:items-center">
      <div class="flex items-center space-x-3">
        <div class="rounded-xl border border-cyan-400/20 bg-cyan-400/10 p-2 text-cyan-300"><Image class="h-5 w-5" /></div>
        <div><h2 class="text-xl font-bold text-white">云端图床</h2><p class="mt-0.5 text-xs text-slate-400">管理全局图片资产与可见性状态</p></div>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <input v-model="filterUserId" type="number" placeholder="UID..." class="admin-input w-20" @keyup.enter="handleSearch" />
        <input v-model="filterTopicId" type="number" placeholder="主题 ID..." class="admin-input w-24" @keyup.enter="handleSearch" />
        <select v-model="filterIsPass" class="admin-input w-28" @change="handleSearch">
          <option value="">审核: 全部</option><option value="0">待审核</option><option value="1">已通过</option><option value="2">已拒绝</option>
        </select>
        <select v-model="filterIsPublic" class="admin-input w-28" @change="handleSearch">
          <option value="">可见: 全部</option><option value="0">私有</option><option value="1">公开</option>
        </select>
        <div class="group relative flex h-9 w-9 items-center overflow-hidden rounded-xl border border-white/10 bg-black/20 transition-all duration-300 ease-out hover:w-40 focus-within:!w-56 focus-within:border-cyan-400/50 focus-within:bg-black/40">
          <label class="z-10 flex h-full w-9 flex-shrink-0 cursor-pointer items-center justify-center text-slate-500 transition-colors group-hover:text-slate-300 group-focus-within:text-cyan-300"><Search class="h-4 w-4" /></label>
          <input v-model="searchFilename" type="text" placeholder="文件名..." class="absolute left-9 h-full w-[180px] bg-transparent pr-4 text-sm text-white opacity-0 outline-none transition-opacity duration-300 group-hover:opacity-100 focus-within:!opacity-100 placeholder:text-slate-500" @keyup.enter="handleSearch" />
        </div>
      </div>
    </div>

    <div class="glass-panel sticky top-0 z-30 flex items-center justify-between rounded-xl px-4 py-3 transition-all duration-300" :class="isBatchMode ? 'translate-y-0 opacity-100' : '-translate-y-[10px] opacity-0 pointer-events-none'">
      <span class="text-sm font-bold text-cyan-200">已选取 <span class="mx-1 text-white">{{ selectedIds.size }}</span> 张图片</span>
      <div class="flex items-center space-x-2">
        <button class="flex items-center space-x-1.5 rounded-lg border border-cyan-400/20 bg-cyan-400/10 px-3 py-1.5 text-xs font-bold text-cyan-200 transition-all hover:bg-cyan-400 hover:text-slate-950" @click="handleTransfer"><Upload class="h-3.5 w-3.5" /><span>迁移云端</span></button>
        <button class="flex items-center space-x-1.5 rounded-lg border border-rose-500/20 bg-rose-500/10 px-3 py-1.5 text-xs font-bold text-rose-300 transition-all hover:bg-rose-500 hover:text-white" @click="handleBatchDelete"><Trash2 class="h-3.5 w-3.5" /><span>批量删除</span></button>
      </div>
    </div>

    <div v-if="loading" class="relative z-10 flex flex-col items-center justify-center space-y-3 py-24"><Loader2 class="h-8 w-8 animate-spin text-cyan-300" /><span class="text-xs text-slate-500">加载中...</span></div>
    <div v-else-if="imageList.length === 0" class="relative z-10 flex flex-col items-center justify-center space-y-4 py-24"><Image class="h-12 w-12 text-slate-600" /><p class="text-sm text-slate-500">暂无图片数据</p></div>
    <TransitionGroup v-else name="staggered-fade" tag="div" class="relative z-10 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      <div v-for="(img, index) in imageList" :key="img.id" class="glass-panel glass-card group relative overflow-hidden rounded-2xl" :style="{ transitionDelay: `${index * 40}ms` }">
        <div class="absolute left-3 top-3 z-10 opacity-0 transition-opacity group-hover:opacity-100"><input type="checkbox" class="glass-checkbox" :checked="selectedIds.has(img.id)" @change="toggleSelect(img.id)" /></div>
        <div class="relative h-40 w-full overflow-hidden bg-black/50">
          <img v-if="img.ossUrl" :src="img.ossUrl" class="h-full w-full object-cover transition-transform duration-700 group-hover:scale-110" />
          <div v-else class="flex h-full w-full items-center justify-center text-slate-600"><Image class="h-10 w-10" /></div>
          <div v-if="img.isPass === 2" class="pointer-events-none absolute inset-0 bg-rose-500/10 mix-blend-overlay"></div>
          <div class="absolute inset-0 flex items-center justify-center gap-2 bg-black/60 opacity-0 transition-opacity duration-300 group-hover:opacity-100">
            <button v-if="img.ossUrl" class="rounded-full bg-white/10 p-2 backdrop-blur-md transition-all hover:scale-110 hover:bg-white/20" title="预览" @click="previewUrl = img.ossUrl; showPreview = true"><Eye class="h-4 w-4 text-white" /></button>
            <button class="rounded-full border border-rose-500/30 bg-rose-500/20 p-2 backdrop-blur-md transition-all hover:scale-110 hover:bg-rose-500/40" title="删除" @click="handleDelete(img.id)"><Trash2 class="h-4 w-4 text-rose-200" /></button>
          </div>
          <span class="absolute right-3 top-3 z-10 rounded-lg border border-white/10 bg-black/40 px-2 py-0.5 font-mono text-[10px] text-white backdrop-blur-md">{{ formatBytes(img.fileSize) }}</span>
        </div>
        <div class="p-3">
          <h3 class="truncate text-xs font-bold text-white" :title="img.filename">{{ img.filename || '未命名' }}</h3>
          <div class="mt-2 flex items-center justify-between">
            <span class="text-[10px] text-slate-500">UID: {{ img.userId }} {{ formatDate(img.uploadTime || img.createTime) }}</span>
          </div>
          <div class="mt-2 flex items-center justify-between">
            <div class="flex items-center space-x-2">
              <span class="inline-flex items-center rounded-md border px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider" :class="getStatusInfo(img.isPass).cls">{{ getStatusInfo(img.isPass).label }}</span>
              <div v-if="img.isPass === 2" class="group/tooltip relative flex items-center">
                <Info class="h-3.5 w-3.5 cursor-help text-rose-300" />
                <div class="pointer-events-none absolute left-1/2 top-full z-20 mt-2 w-40 -translate-x-1/2 scale-95 rounded-xl border border-rose-500/20 bg-slate-950/95 px-3 py-2 text-[11px] leading-5 text-rose-100 opacity-0 shadow-[0_14px_40px_rgba(15,23,42,0.45)] transition-all duration-200 ease-out group-hover/tooltip:scale-100 group-hover/tooltip:opacity-100">该图片审核未通过。</div>
              </div>
            </div>
            <button class="text-[10px] font-bold uppercase transition-colors" :class="img.isPublic === 1 ? 'text-cyan-200' : 'text-slate-500 hover:text-cyan-200'" @click="handleSetPublic(img.id, img.isPublic === 1 ? 0 : 1)" :title="img.isPublic === 1 ? '设为私有' : '设为公开'"><Globe class="mr-1 inline h-3.5 w-3.5" />{{ img.isPublic === 1 ? '公开' : '私有' }}</button>
          </div>
        </div>
      </div>
    </TransitionGroup>

    <div v-if="!loading && imageList.length > 0" class="relative z-10 flex items-center justify-between rounded-xl border border-white/5 bg-white/[0.01] px-4 py-4">
      <span class="text-xs text-slate-500">共 {{ formatNumber(total) }} 张图片</span>
      <div class="flex items-center space-x-1">
        <button class="flex h-7 w-7 items-center justify-center rounded text-slate-500 hover:bg-white/5 hover:text-white disabled:opacity-50" :disabled="currentPage <= 1" @click="handlePageChange(currentPage - 1)"><ChevronLeft class="h-4 w-4" /></button>
        <template v-for="page in visiblePages()" :key="page"><button v-if="totalPages > 1" class="flex h-7 w-7 items-center justify-center rounded text-xs font-bold transition-colors" :class="page === currentPage ? 'border border-cyan-400/30 bg-cyan-400/15 text-cyan-200' : 'text-slate-400 hover:bg-white/5 hover:text-white'" @click="handlePageChange(page)">{{ page }}</button></template>
        <button class="flex h-7 w-7 items-center justify-center rounded text-slate-500 hover:bg-white/5 hover:text-white" :disabled="currentPage >= totalPages" @click="handlePageChange(currentPage + 1)"><ChevronRight class="h-4 w-4" /></button>
      </div>
    </div>

    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showPreview" class="fixed inset-0 z-[60] bg-black/90 backdrop-blur-md" @click="showPreview = false"></div>
      </Transition>
      <Transition name="modal">
        <div v-if="showPreview" class="fixed inset-0 z-[60] flex items-center justify-center px-4" @click.self="showPreview = false">
          <div class="relative z-10 max-h-[90vh] max-w-[90vw]">
            <button class="absolute -top-12 right-0 p-2 text-slate-400 transition-colors hover:text-white" @click="showPreview = false"><X class="h-6 w-6" /></button>
            <img :src="previewUrl" class="max-h-[85vh] max-w-full rounded-xl object-contain shadow-2xl" />
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.glass-panel { background: rgba(255,255,255,0.02); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.05); box-shadow: inset 0 1px 1px rgba(255,255,255,0.05); }
.glass-card { transition: all 0.3s cubic-bezier(0.34,1.56,0.64,1); }
.glass-card:hover { transform: translateY(-6px) scale(1.02); background: rgba(255,255,255,0.08); border-color: rgba(34,211,238,0.35); box-shadow: 0 20px 40px -15px rgba(34,211,238,0.2), 0 0 20px -2px rgba(99,102,241,0.12), inset 0 1px 1px rgba(255,255,255,0.1); }
.staggered-fade-enter-active { transition: all 0.5s ease-out; }
.staggered-fade-enter-from { opacity: 0; transform: translateY(20px); }
.staggered-fade-leave-active { transition: all 0.3s ease-in; position: absolute; }
.staggered-fade-leave-to { opacity: 0; transform: scale(0.9); }
.staggered-fade-move { transition: transform 0.4s ease; }
.glass-checkbox { appearance: none; width: 16px; height: 16px; border: 1px solid rgba(255,255,255,0.2); border-radius: 4px; background: rgba(0,0,0,0.2); cursor: pointer; position: relative; transition: all 0.2s; }
.glass-checkbox:checked { background: #22d3ee; border-color: #22d3ee; box-shadow: 0 0 10px rgba(34,211,238,0.35); }
.glass-checkbox:checked::after { content: ''; position: absolute; left: 5px; top: 2px; width: 4px; height: 8px; border: solid white; border-width: 0 2px 2px 0; transform: rotate(45deg); }
.admin-input { height: 36px; border-radius: 0.75rem; border: 1px solid rgba(255,255,255,0.1); background: rgba(0,0,0,0.2); padding: 0 0.75rem; color: white; font-size: 0.75rem; outline: none; transition: border-color 0.2s ease, background-color 0.2s ease; }
.admin-input::placeholder { color: rgb(100 116 139); }
.admin-input:focus { border-color: rgba(34,211,238,0.5); background: rgba(0,0,0,0.35); }
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.modal-enter-active, .modal-leave-active { transition: opacity 0.28s ease, transform 0.38s cubic-bezier(0.22,1,0.36,1); }
.modal-enter-from, .modal-leave-to { opacity: 0; transform: scale(0.92) translateY(18px); }
.modal-enter-to, .modal-leave-from { opacity: 1; transform: scale(1) translateY(0); }
</style>
