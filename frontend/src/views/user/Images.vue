<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { imageApi } from '@/api/images'
import { topicApi } from '@/api/topics'
import type { ImageItem } from '@/api/images'
import type { TopicItem } from '@/api/topics'
import { noteApi, getNoteStatusInfo } from '@/api/notes'
import type { ImageBacklinkVO } from '@/api/notes'
import {
  Image, Upload, Search, Globe, Trash2, Send, Info,
  Loader2, X, ChevronLeft, ChevronRight, Link, Maximize2, FileText
} from 'lucide-vue-next'

const loading = ref(true)
const imageList = ref<ImageItem[]>([])
const total = ref(0)
const searchFilename = ref('')
const searchMode = ref<'personal' | 'global'>('personal')
const currentPage = ref(1)
const pageSize = ref(12)
const selectedIds = ref<Set<number>>(new Set())

const modalVisible = ref(false)
const uploadFile = ref<File | null>(null)
const uploadPreview = ref('')
const uploadTopicId = ref<number | undefined>(undefined)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement>()

const previewUrl = ref('')
const showPreview = ref(false)
const topicOptions = ref<TopicItem[]>([])

const router = useRouter()

// ── Image backlinks ──
const showImageBacklinks = ref(false)
const imageBacklinks = ref<ImageBacklinkVO[]>([])
const imageBacklinksLoading = ref(false)
const imageBacklinksFetchedId = ref<number | null>(null)

async function openImageBacklinks(imageId: number) {
  showImageBacklinks.value = true
  if (imageBacklinksFetchedId.value !== imageId) {
    imageBacklinksLoading.value = true
    try {
      imageBacklinks.value = await noteApi.getImageBacklinks(imageId)
      imageBacklinksFetchedId.value = imageId
    } finally {
      imageBacklinksLoading.value = false
    }
  }
}

function handleImageBacklinkClick(b: ImageBacklinkVO) {
  showImageBacklinks.value = false
  router.push(`/user/notes/${b.sourceNoteId}`)
}

const isBatchMode = computed(() => selectedIds.value.size > 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function getStatusInfo(isPass: number): { label: string; cls: string } {
  switch (isPass) {
    case 1: return { label: '已通过', cls: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20' }
    case 2: return { label: '已拒绝', cls: 'text-rose-400 bg-rose-500/10 border-rose-500/20' }
    default: return { label: '待审核', cls: 'text-amber-400 bg-amber-500/10 border-amber-500/20' }
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
    const res = await imageApi.getList({
      filename: searchFilename.value || undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    })
    imageList.value = res.records ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

async function fetchTopics() {
  try {
    const res = await topicApi.getList({ pageNum: 1, pageSize: 100 })
    topicOptions.value = res.records ?? []
  } catch {}
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

function toggleGlobalSearch() {
  searchMode.value = searchMode.value === 'personal' ? 'global' : 'personal'
}

function toggleSelectAll(checked: boolean) {
  if (checked) imageList.value.forEach(img => selectedIds.value.add(img.id))
  else selectedIds.value.clear()
}

function toggleSelect(id: number) {
  selectedIds.value.has(id) ? selectedIds.value.delete(id) : selectedIds.value.add(id)
}

function triggerFileInput() {
  fileInput.value?.click()
}

function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    alert('图片大小不能超过 5MB')
    return
  }
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/bmp', 'image/svg+xml'].includes(file.type)) {
    alert('仅支持 JPG / PNG / WEBP / GIF / BMP / SVG')
    return
  }
  uploadFile.value = file
  uploadPreview.value = URL.createObjectURL(file)
}

function openUploadModal() {
  uploadFile.value = null
  uploadPreview.value = ''
  uploadTopicId.value = undefined
  if (fileInput.value) fileInput.value.value = ''
  modalVisible.value = true
}

function closeModal(force?: boolean | Event) {
  const shouldForce = force === true
  if (uploading.value && !shouldForce) return
  modalVisible.value = false
}

async function handleUpload() {
  if (!uploadFile.value) return
  uploading.value = true
  try {
    await imageApi.upload(uploadFile.value, uploadTopicId.value)
    closeModal(true)
    await fetchImages()
  } finally {
    uploading.value = false
  }
}

async function handleDelete(id: number) {
  if (!confirm('确定删除该图片吗？删除后不可恢复。')) return
  await imageApi.deleteImage(id)
  selectedIds.value.delete(id)
  await fetchImages()
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) return
  if (!confirm(`确定删除已选择的 ${selectedIds.value.size} 张图片吗？删除后不可恢复。`)) return
  await Promise.all([...selectedIds.value].map(id => imageApi.deleteImage(id)))
  selectedIds.value.clear()
  await fetchImages()
}

async function handleBatchSubmitAudit() {
  if (selectedIds.value.size === 0) return
  if (!confirm(`确认提交已选择的 ${selectedIds.value.size} 张图片进行审核吗？`)) return
  await Promise.all([...selectedIds.value].map(id => imageApi.submitAudit(id)))
  selectedIds.value.clear()
  await fetchImages()
}

async function handleSubmitAudit(id: number) {
  if (!confirm('确认提交该图片进行审核吗？')) return
  await imageApi.submitAudit(id)
  await fetchImages()
}

function copyImageUrl(url: string) {
  navigator.clipboard.writeText(url).catch(() => {})
}

onMounted(() => {
  fetchImages()
  fetchTopics()
})
</script>

<template>
  <div class="relative mx-auto max-w-[1400px] space-y-6 pb-20">
    <div class="fixed top-[-10%] right-[-5%] z-0 h-[500px] w-[500px] rounded-full bg-purple-600/10 blur-[150px] pointer-events-none"></div>
    <div class="fixed bottom-[-10%] left-[-5%] z-0 h-[400px] w-[400px] rounded-full bg-indigo-600/10 blur-[120px] pointer-events-none"></div>

    <div class="relative z-10 flex flex-col justify-between gap-4 md:flex-row md:items-center">
      <div class="flex items-center space-x-3">
        <div class="rounded-xl border border-purple-500/20 bg-purple-500/10 p-2 text-purple-400"><Image class="h-5 w-5" /></div>
        <div><h2 class="text-xl font-bold text-white">图床画廊</h2><p class="mt-0.5 text-xs text-slate-400">存储和管理您的数字化图像资产</p></div>
      </div>
      <div class="flex items-center space-x-4">
        <div class="search-toggle inline-flex cursor-pointer items-center space-x-2 overflow-hidden rounded-full border px-4 py-2 transition-all duration-300" :class="searchMode === 'global' ? 'is-active border-purple-500/40 shadow-[inset_0_0_10px_rgba(168,85,247,0.1)]' : 'border-white/10 bg-white/[0.02] hover:border-white/20'" @click="toggleGlobalSearch">
          <div class="relative h-4 w-8 rounded-full border transition-colors duration-300" :class="searchMode === 'global' ? 'border-purple-500/50 bg-purple-500/20' : 'border-white/10 bg-black/50'">
            <div class="absolute top-0 h-4 w-4 rounded-full shadow-sm transition-transform duration-300" :class="searchMode === 'global' ? 'translate-x-4 bg-white shadow-[0_0_10px_rgba(255,255,255,0.8)]' : 'translate-x-0 bg-slate-400'"></div>
          </div>
          <div class="flex items-center space-x-1.5">
            <Globe class="h-3.5 w-3.5 transition-colors duration-300" :class="searchMode === 'global' ? 'text-purple-400' : 'text-slate-500'" />
            <span class="text-[11px] font-bold uppercase tracking-widest transition-colors duration-300" :class="searchMode === 'global' ? 'text-purple-300' : 'text-slate-400'">全局图库</span>
          </div>
        </div>
        <div class="group relative flex h-9 w-9 items-center overflow-hidden rounded-xl border border-white/10 bg-black/20 transition-all duration-300 ease-out hover:w-28 focus-within:!w-64 focus-within:border-purple-500/50 focus-within:bg-black/40 focus-within:ring-2 focus-within:ring-purple-500/10">
          <label for="image-search" class="z-10 flex h-full w-9 flex-shrink-0 cursor-pointer items-center justify-center text-slate-500 transition-colors group-hover:text-slate-300 group-focus-within:text-purple-400"><Search class="h-4 w-4" /></label>
          <input id="image-search" v-model="searchFilename" type="text" placeholder="搜索图片文件名..." class="absolute left-9 h-full w-[220px] bg-transparent pr-4 text-sm text-white opacity-0 outline-none transition-opacity duration-300 group-hover:opacity-100 focus-within:!opacity-100 placeholder:text-slate-500" @keyup.enter="handleSearch" />
        </div>
        <button class="group relative flex items-center space-x-2 overflow-hidden rounded-xl bg-purple-600 px-4 py-2 text-sm font-bold text-white shadow-[0_0_15px_rgba(168,85,247,0.4)] transition-all hover:bg-purple-500" @click="openUploadModal">
          <div class="absolute inset-0 -translate-x-[150%] bg-[linear-gradient(to_right,transparent,rgba(255,255,255,0.2),transparent)] transition-transform duration-700 ease-out group-hover:translate-x-[150%]"></div>
          <Upload class="relative z-10 h-4 w-4" />
          <span class="relative z-10">上传图片</span>
        </button>
      </div>
    </div>

    <div class="relative z-10 flex items-center justify-between">
      <label class="flex cursor-pointer select-none items-center space-x-2">
        <input type="checkbox" class="glass-checkbox" :checked="selectedIds.size === imageList.length && imageList.length > 0" @change="toggleSelectAll(($event.target as HTMLInputElement).checked)" />
        <span class="text-xs font-bold uppercase tracking-wider text-slate-400">全选当前页</span>
      </label>
      <span class="text-xs text-slate-500">共 {{ formatNumber(total) }} 张图片</span>
    </div>

    <div class="glass-panel sticky top-0 z-30 mb-4 flex items-center justify-between rounded-xl px-4 py-3 transition-all duration-300" :class="isBatchMode ? 'translate-y-0 opacity-100' : '-translate-y-[10px] opacity-0 pointer-events-none'">
      <div class="flex items-center space-x-3">
        <span class="relative flex h-2 w-2"><span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-purple-400 opacity-75"></span><span class="relative inline-flex h-2 w-2 rounded-full bg-purple-500"></span></span>
        <span class="text-sm font-bold text-purple-300">已选取 <span class="mx-1 text-white">{{ selectedIds.size }}</span> 张图片</span>
      </div>
      <div class="flex items-center space-x-2">
        <button class="flex items-center space-x-1.5 rounded-lg border border-teal-500/20 bg-teal-500/10 px-3 py-1.5 text-xs font-bold text-teal-400 transition-all hover:bg-teal-500 hover:text-white" @click="handleBatchSubmitAudit"><Send class="h-3.5 w-3.5" /><span>批量提审</span></button>
        <button class="flex items-center space-x-1.5 rounded-lg border border-rose-500/20 bg-rose-500/10 px-3 py-1.5 text-xs font-bold text-rose-400 transition-all hover:bg-rose-500 hover:text-white" @click="handleBatchDelete"><Trash2 class="h-3.5 w-3.5" /><span>批量删除</span></button>
      </div>
    </div>

    <div v-if="loading" class="relative z-10 flex flex-col items-center justify-center space-y-3 py-24"><Loader2 class="h-8 w-8 animate-spin text-purple-400" /><span class="text-xs text-slate-500">加载图库中...</span></div>
    <div v-else-if="imageList.length === 0" class="relative z-10 flex flex-col items-center justify-center space-y-4 py-24"><Image class="h-12 w-12 text-slate-600" /><p class="text-sm text-slate-500">图库中暂无图片</p><button class="flex items-center space-x-1.5 text-sm font-bold text-purple-400 transition-colors hover:text-purple-300" @click="openUploadModal"><Upload class="h-4 w-4" /><span>上传第一张图片</span></button></div>
    <TransitionGroup v-else name="staggered-fade" tag="div" class="relative z-10 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      <div v-for="(img, index) in imageList" :key="img.id" class="glass-panel glass-card group relative overflow-hidden rounded-2xl" :class="{ 'border-rose-500/30 shadow-[0_0_15px_rgba(244,63,94,0.1)]': img.isPass === 2 }" :style="{ transitionDelay: `${index * 50}ms` }">
        <div class="absolute left-3 top-3 z-10 opacity-0 transition-opacity group-hover:opacity-100"><input type="checkbox" class="glass-checkbox" :checked="selectedIds.has(img.id)" @change="toggleSelect(img.id)" /></div>
        <div class="relative h-48 w-full overflow-hidden bg-black/50">
          <img v-if="img.ossUrl" :src="img.ossUrl" :alt="img.filename" class="h-full w-full object-cover transition-transform duration-700 group-hover:scale-110" :class="{ 'grayscale-[30%]': img.isPass === 2 }" />
          <div v-else class="flex h-full w-full items-center justify-center text-slate-600"><Image class="h-10 w-10" /></div>
          <div v-if="img.isPass === 2" class="pointer-events-none absolute inset-0 bg-rose-500/10 mix-blend-overlay"></div>
          <div class="absolute inset-0 flex items-center justify-center gap-3 bg-black/60 opacity-0 transition-opacity duration-300 group-hover:opacity-100">
            <button class="rounded-full bg-white/10 p-2 backdrop-blur-md transition-all hover:scale-110 hover:bg-white/20" title="复制外链" @click="copyImageUrl(img.ossUrl)"><Link class="h-4 w-4 text-white" /></button>
            <button v-if="img.ossUrl" class="rounded-full bg-white/10 p-2 backdrop-blur-md transition-all hover:scale-110 hover:bg-white/20" title="查看原图" @click="previewUrl = img.ossUrl; showPreview = true"><Maximize2 class="h-4 w-4 text-white" /></button>
            <button class="rounded-full border border-cyan-500/30 bg-cyan-500/20 p-2 backdrop-blur-md transition-all hover:scale-110 hover:bg-cyan-500/40" title="查看引用笔记" @click="openImageBacklinks(img.id)"><FileText class="h-4 w-4 text-cyan-300" /></button>
            <button v-if="img.isPass === 2" class="rounded-full border border-teal-500/30 bg-teal-500/20 p-2 backdrop-blur-md transition-all hover:scale-110 hover:bg-teal-500/40" title="重新提交审核" @click="handleSubmitAudit(img.id)"><Send class="h-4 w-4 text-teal-300" /></button>
            <button class="rounded-full border border-rose-500/30 bg-rose-500/20 p-2 backdrop-blur-md transition-all hover:scale-110 hover:bg-rose-500/40" title="删除" @click="handleDelete(img.id)"><Trash2 class="h-4 w-4 text-rose-300" /></button>
          </div>
          <span class="absolute right-3 top-3 z-10 rounded-lg border border-white/10 bg-black/40 px-2 py-0.5 font-mono text-[10px] text-white backdrop-blur-md">{{ formatBytes(img.fileSize) }}</span>
        </div>
        <div class="p-4">
          <h3 class="truncate text-sm font-bold text-white" :title="img.filename">{{ img.filename || '未命名' }}</h3>
          <div class="mt-3 flex items-center justify-between">
            <span class="text-xs text-slate-500">{{ formatDate(img.uploadTime || img.createTime) }}</span>
            <div class="flex items-center space-x-2">
              <span class="inline-flex items-center space-x-1 rounded-md border px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider" :class="getStatusInfo(img.isPass).cls">
                <span>{{ getStatusInfo(img.isPass).label }}</span>
              </span>
              <div v-if="img.isPass === 2" class="group/tooltip relative flex items-center">
                <Info class="h-3.5 w-3.5 cursor-help text-rose-300" />
                <div class="pointer-events-none absolute left-1/2 top-full z-20 mt-2 w-40 -translate-x-1/2 scale-95 rounded-xl border border-rose-500/20 bg-slate-950/95 px-3 py-2 text-[11px] leading-5 text-rose-100 opacity-0 shadow-[0_14px_40px_rgba(15,23,42,0.45)] transition-all duration-200 ease-out group-hover/tooltip:scale-100 group-hover/tooltip:opacity-100">审核未通过，可重新提交。</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </TransitionGroup>

    <div v-if="!loading && imageList.length > 0" class="relative z-10 flex items-center justify-between rounded-xl border border-white/5 bg-white/[0.01] px-4 py-4">
      <span class="text-xs text-slate-500">共加载 {{ formatNumber(total) }} 张图片</span>
      <div class="flex items-center space-x-1">
        <button class="flex h-7 w-7 items-center justify-center rounded text-slate-500 hover:bg-white/5 hover:text-white disabled:opacity-50" :disabled="currentPage <= 1" @click="handlePageChange(currentPage - 1)"><ChevronLeft class="h-4 w-4" /></button>
        <template v-for="page in visiblePages()" :key="page"><button v-if="totalPages > 1" class="flex h-7 w-7 items-center justify-center rounded text-xs font-bold transition-colors" :class="page === currentPage ? 'border border-purple-500/30 bg-purple-500/20 text-purple-400' : 'text-slate-400 hover:bg-white/5 hover:text-white'" @click="handlePageChange(page)">{{ page }}</button></template>
        <button class="flex h-7 w-7 items-center justify-center rounded text-slate-500 hover:bg-white/5 hover:text-white" :disabled="currentPage >= totalPages" @click="handlePageChange(currentPage + 1)"><ChevronRight class="h-4 w-4" /></button>
      </div>
    </div>

    <Teleport to="body">
      <Transition name="fade">
        <div v-if="modalVisible" class="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm" @click="closeModal"></div>
      </Transition>
      <Transition name="modal">
        <div v-if="modalVisible" class="fixed inset-0 z-50 flex items-center justify-center px-4">
          <div class="glass-panel modal-card relative z-10 w-full max-w-lg rounded-3xl p-8">
            <div class="pointer-events-none absolute -right-10 -top-10 h-32 w-32 rounded-full bg-purple-500/20 blur-[40px]"></div>
            <div class="mb-6 flex items-center justify-between">
              <h3 class="text-xl font-bold text-white">上传新图片</h3>
              <button class="text-slate-500 transition-colors hover:text-white" :disabled="uploading" @click="closeModal"><X class="h-5 w-5" /></button>
            </div>
            <div class="space-y-5">
              <div v-if="!uploadFile" class="group flex h-48 w-full cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed border-white/20 bg-white/5 transition-colors hover:border-purple-500/50 hover:bg-white/10" @click="triggerFileInput">
                <div class="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-purple-500/20 text-purple-400 shadow-[0_0_15px_rgba(168,85,247,0.3)] transition-transform group-hover:-translate-y-1"><Upload class="h-6 w-6" /></div>
                <p class="mb-1 text-sm font-bold text-white transition-colors group-hover:text-purple-300">点击或拖拽文件到此处</p>
                <p class="text-xs text-slate-400">支持 JPG / PNG / WEBP / GIF，最大 5MB</p>
              </div>
              <div v-else class="relative h-48 w-full overflow-hidden rounded-xl bg-black/50">
                <img v-if="uploadPreview" :src="uploadPreview" class="h-full w-full object-contain" />
                <button class="absolute right-2 top-2 flex h-7 w-7 items-center justify-center rounded-full bg-black/60 text-white transition-colors hover:bg-rose-500/60" @click="uploadFile = null; uploadPreview = ''"><X class="h-4 w-4" /></button>
              </div>
              <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp,image/gif,image/bmp,image/svg+xml" class="hidden" @change="handleFileChange" />
              <div>
                <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-slate-400">所属主题 <span class="normal-case tracking-normal text-slate-500">（可选）</span></label>
                <div class="relative">
                  <select v-model="uploadTopicId" class="w-full cursor-pointer appearance-none rounded-xl border border-white/[0.05] bg-black/20 py-3 pl-4 pr-10 text-sm text-white shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] outline-none transition-all focus:border-purple-500/50 focus:bg-black/40 focus:ring-2 focus:ring-purple-500/10">
                    <option :value="undefined" class="bg-[#0b0d14]">-- 不关联任何主题 --</option>
                    <option v-for="t in topicOptions" :key="t.id" :value="t.id" class="bg-[#0b0d14]">{{ t.topicName }}</option>
                  </select>
                  <div class="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-3 text-slate-500"><ChevronLeft class="h-4 w-4 rotate-[-90deg]" /></div>
                </div>
              </div>
              <div class="flex justify-end space-x-3 pt-4">
                <button type="button" class="rounded-xl px-5 py-2.5 text-sm font-bold text-slate-400 transition-colors hover:bg-white/5 hover:text-white" :disabled="uploading" @click="closeModal">取消</button>
                <button type="button" class="flex items-center space-x-2 rounded-xl bg-purple-600 px-5 py-2.5 text-sm font-bold text-white shadow-[0_0_15px_rgba(168,85,247,0.4)] transition-all hover:bg-purple-500 disabled:cursor-not-allowed disabled:opacity-50" :disabled="!uploadFile || uploading" @click="handleUpload">
                  <Loader2 v-if="uploading" class="h-4 w-4 animate-spin" />
                  <span>{{ uploading ? '上传中...' : '确认上传' }}</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

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

    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showImageBacklinks" class="fixed inset-0 z-[60] bg-black/60 backdrop-blur-sm" @click="showImageBacklinks = false"></div>
      </Transition>
      <Transition name="modal">
        <div v-if="showImageBacklinks" class="fixed inset-0 z-[60] flex items-center justify-center px-4">
          <div class="glass-panel modal-card relative z-10 w-full max-w-lg rounded-3xl p-8">
            <div class="pointer-events-none absolute -right-10 -top-10 h-32 w-32 rounded-full bg-cyan-500/20 blur-[40px]"></div>
            <div class="mb-6 flex items-center justify-between">
              <h3 class="text-xl font-bold text-white">图片引用笔记</h3>
              <button class="text-slate-500 transition-colors hover:text-white" @click="showImageBacklinks = false"><X class="h-5 w-5" /></button>
            </div>
            <div class="max-h-[60vh] overflow-y-auto custom-scrollbar space-y-2">
              <div v-if="imageBacklinksLoading" class="text-xs text-slate-500 text-center py-8 flex items-center justify-center gap-2">
                <Loader2 class="w-4 h-4 animate-spin" /> 加载中...
              </div>
              <div v-else-if="!imageBacklinks.length" class="text-xs text-slate-500 text-center py-8">暂无笔记引用此图片</div>
              <div v-else>
                <div
                  v-for="b in imageBacklinks"
                  :key="b.sourceNoteId"
                  class="flex items-center justify-between bg-black/20 p-3 rounded-xl border border-white/5 hover:border-cyan-500/30 group/ref transition-colors cursor-pointer"
                  @click="handleImageBacklinkClick(b)"
                >
                  <div class="flex items-center space-x-2 overflow-hidden">
                    <div class="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 bg-cyan-500/10 text-cyan-400">
                      <FileText class="w-4 h-4" />
                    </div>
                    <div class="flex flex-col min-w-0">
                      <span class="text-xs font-medium truncate text-slate-300 group-hover/ref:text-cyan-300 transition-colors">
                        {{ b.sourceNoteTitle }}
                      </span>
                      <span class="text-[9px] text-slate-500 mt-0.5 truncate">via [[{{ b.parsedImageName }}]]</span>
                    </div>
                  </div>
                  <div class="flex items-center gap-1.5 shrink-0 ml-2">
                    <span v-if="b.isCrossUser === 1" class="text-[9px] font-bold px-1.5 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/20" title="跨用户引用">跨用户</span>
                    <span class="text-[9px] font-bold px-1.5 py-0.5 rounded border" :class="getNoteStatusInfo(b.sourceNoteStatus).cls">{{ getNoteStatusInfo(b.sourceNoteStatus).label }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.glass-panel { background: rgba(255,255,255,0.02); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.05); box-shadow: inset 0 1px 1px rgba(255,255,255,0.05); }
.glass-card { transition: all 0.3s cubic-bezier(0.34,1.56,0.64,1); }
.glass-card:hover { transform: translateY(-6px) scale(1.02); background: rgba(255,255,255,0.08); border-color: rgba(168,85,247,0.4); box-shadow: 0 20px 40px -15px rgba(168,85,247,0.25), 0 0 20px -2px rgba(236,72,153,0.15), inset 0 1px 1px rgba(255,255,255,0.1); }
.staggered-fade-enter-active { transition: all 0.5s ease-out; }
.staggered-fade-enter-from { opacity: 0; transform: translateY(20px); }
.staggered-fade-leave-active { transition: all 0.3s ease-in; position: absolute; }
.staggered-fade-leave-to { opacity: 0; transform: scale(0.9); }
.staggered-fade-move { transition: transform 0.4s ease; }
.glass-checkbox { appearance: none; width: 16px; height: 16px; border: 1px solid rgba(255,255,255,0.2); border-radius: 4px; background: rgba(0,0,0,0.2); cursor: pointer; position: relative; transition: all 0.2s; }
.glass-checkbox:checked { background: #8b5cf6; border-color: #8b5cf6; box-shadow: 0 0 10px rgba(139,92,246,0.4); }
.glass-checkbox:checked::after { content: ''; position: absolute; left: 5px; top: 2px; width: 4px; height: 8px; border: solid white; border-width: 0 2px 2px 0; transform: rotate(45deg); }
@keyframes shine { to { background-position: 200% center; } }
.search-toggle { position: relative; background-color: rgba(255,255,255,0.02); }
.search-toggle::before { content: ''; position: absolute; inset: 0; background: linear-gradient(90deg, rgba(129,140,248,0.15), rgba(232,121,249,0.15), rgba(244,114,182,0.15), rgba(232,121,249,0.15), rgba(129,140,248,0.15)); background-size: 200% auto; animation: shine 3s linear infinite; opacity: 0; transition: opacity 0.3s ease; z-index: 0; border-radius: 9999px; }
.search-toggle:hover::before { opacity: 1; }
.search-toggle.is-active::before { opacity: 1; background: linear-gradient(90deg, rgba(129,140,248,0.3), rgba(232,121,249,0.3), rgba(244,114,182,0.3), rgba(232,121,249,0.3), rgba(129,140,248,0.3)); animation: shine 2s linear infinite; }
.search-toggle > * { position: relative; z-index: 1; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.28s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.modal-enter-active, .modal-leave-active { transition: opacity 0.32s ease, transform 0.42s cubic-bezier(0.25,1,0.5,1); }
.modal-enter-from, .modal-leave-to { opacity: 0; transform: scale(0.96) translateY(14px); }
.modal-enter-to, .modal-leave-from { opacity: 1; transform: scale(1) translateY(0); }
.modal-card { transform-origin: center center; box-shadow: 0 24px 80px rgba(15,23,42,0.45), inset 0 1px 1px rgba(255,255,255,0.05); }

@media (prefers-reduced-motion: reduce) {
  .fade-enter-active,
  .fade-leave-active,
  .modal-enter-active,
  .modal-leave-active {
    transition-duration: 0.16s;
  }

  .modal-enter-from,
  .modal-leave-to,
  .modal-enter-to,
  .modal-leave-from {
    transform: none;
  }
}
</style>
