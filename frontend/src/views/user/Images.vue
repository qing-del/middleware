<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { imageApi } from '@/api/images'
import { topicApi } from '@/api/topics'
import type { ImageItem } from '@/api/images'
import type { TopicItem } from '@/api/topics'
import {
  Image, Upload, Search, Globe, Trash2, Send, Info,
  Loader2, X, ChevronLeft, ChevronRight, Link, Maximize2
} from 'lucide-vue-next'

// ── 响应式状态 ────────────────────────────────────
const loading = ref(true)
const imageList = ref<ImageItem[]>([])
const total = ref(0)
const searchFilename = ref('')
const searchMode = ref<'personal' | 'global'>('personal')
const currentPage = ref(1)
const pageSize = ref(12)
const selectedIds = ref<Set<number>>(new Set())

// 上传
const modalVisible = ref(false)
const uploadFile = ref<File | null>(null)
const uploadPreview = ref('')
const uploadTopicId = ref<number | undefined>(undefined)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement>()

// 全屏预览
const previewUrl = ref('')
const showPreview = ref(false)

// 主题列表（供上传弹窗下拉）
const topicOptions = ref<TopicItem[]>([])

// ── 计算属性 ──────────────────────────────────────
const isBatchMode = computed(() => selectedIds.value.size > 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

// ── 工具函数 ──────────────────────────────────────
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
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(i > 0 ? 1 : 0) + ' ' + units[i]
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

// ── 数据获取 ──────────────────────────────────────
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
  } catch { /* silently fail, topic selector is optional */ }
}

// ── 事件处理 ──────────────────────────────────────
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

// ---- 选择逻辑 ----
function toggleSelectAll(checked: boolean) {
  if (checked) {
    imageList.value.forEach(img => selectedIds.value.add(img.id))
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

// ---- 上传逻辑 ----
function triggerFileInput() {
  fileInput.value?.click()
}

function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  // 客户端校验
  if (file.size > 5 * 1024 * 1024) {
    alert('图片大小不能超过 5MB')
    return
  }
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/bmp', 'image/svg+xml'].includes(file.type)) {
    alert('不支持的图片格式，仅支持 JPG/PNG/WEBP/GIF/BMP/SVG')
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

function closeModal() {
  if (uploading.value) return
  modalVisible.value = false
}

async function handleUpload() {
  if (!uploadFile.value) return

  uploading.value = true
  try {
    await imageApi.upload(uploadFile.value, uploadTopicId.value)
    closeModal()
    await fetchImages()
  } finally {
    uploading.value = false
  }
}

// ---- 删除逻辑 ----
async function handleDelete(id: number) {
  if (!confirm('确定删除该图片吗？删除后不可恢复。')) return
  await imageApi.deleteImage(id)
  selectedIds.value.delete(id)
  await fetchImages()
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) return
  if (!confirm(`确定要删除已选择的 ${selectedIds.value.size} 张图片吗？删除后不可恢复。`)) return
  const ids = [...selectedIds.value]
  await Promise.all(ids.map(id => imageApi.deleteImage(id)))
  selectedIds.value.clear()
  await fetchImages()
}

// ---- 批量审核 ----
async function handleBatchSubmitAudit() {
  if (selectedIds.value.size === 0) return
  if (!confirm(`确认为已选择的 ${selectedIds.value.size} 张图片提交审核吗？`)) return
  const ids = [...selectedIds.value]
  await Promise.all(ids.map(id => imageApi.submitAudit(id)))
  selectedIds.value.clear()
  await fetchImages()
}

// ---- 单图审核 ----
async function handleSubmitAudit(id: number) {
  if (!confirm('确认提交该图片进行审核吗？')) return
  await imageApi.submitAudit(id)
  await fetchImages()
}

// ---- 链接复制 ----
function copyImageUrl(url: string) {
  navigator.clipboard.writeText(url).then(() => {
    // brief visual feedback handled by the browser
  })
}

// ── 生命周期 ──────────────────────────────────────
onMounted(() => {
  fetchImages()
  fetchTopics()
})
</script>

<template>
  <div class="relative max-w-[1400px] mx-auto space-y-6 pb-20">
    <!-- 环境光晕 -->
    <div class="fixed top-[-10%] right-[-5%] w-[500px] h-[500px] bg-purple-600/10 blur-[150px] rounded-full pointer-events-none z-0"></div>
    <div class="fixed bottom-[-10%] left-[-5%] w-[400px] h-[400px] bg-indigo-600/10 blur-[120px] rounded-full pointer-events-none z-0"></div>

    <!-- 页面头部操作栏 -->
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 relative z-10">
      <div class="flex items-center space-x-3">
        <div class="p-2 rounded-xl bg-purple-500/10 border border-purple-500/20 text-purple-400">
          <Image class="w-5 h-5" />
        </div>
        <div>
          <h2 class="text-xl font-bold text-white">图床画廊</h2>
          <p class="text-xs text-slate-400 mt-0.5">储存和管理您的数字化图像资产</p>
        </div>
      </div>

      <div class="flex items-center space-x-4">
        <!-- 全局检索开关 -->
        <div
          class="search-toggle inline-flex items-center space-x-2 px-4 py-2 rounded-full cursor-pointer select-none border transition-all duration-300 overflow-hidden"
          :class="searchMode === 'global'
            ? 'is-active border-purple-500/40 shadow-[inset_0_0_10px_rgba(168,85,247,0.1)]'
            : 'border-white/10 bg-white/[0.02] hover:border-white/20'"
          @click="toggleGlobalSearch"
        >
          <div class="relative w-8 h-4 rounded-full border transition-colors duration-300"
            :class="searchMode === 'global' ? 'bg-purple-500/20 border-purple-500/50' : 'bg-black/50 border-white/10'">
            <div
              class="absolute top-0 w-4 h-4 rounded-full shadow-sm transition-transform duration-300"
              :class="searchMode === 'global'
                ? 'translate-x-4 bg-white shadow-[0_0_10px_rgba(255,255,255,0.8)]'
                : 'translate-x-0 bg-slate-400'"
            ></div>
          </div>
          <div class="flex items-center space-x-1.5">
            <Globe
              class="w-3.5 h-3.5 transition-colors duration-300"
              :class="searchMode === 'global' ? 'text-purple-400' : 'text-slate-500'"
            />
            <span
              class="text-[11px] font-bold uppercase tracking-widest transition-colors duration-300"
              :class="searchMode === 'global' ? 'text-purple-300' : 'text-slate-400'"
            >全局图库</span>
          </div>
        </div>

        <!-- 搜索框 -->
        <div class="relative group flex items-center bg-black/20 border border-white/10 rounded-xl overflow-hidden transition-all duration-300 ease-out w-9 hover:w-28 focus-within:!w-64 focus-within:bg-black/40 focus-within:border-purple-500/50 focus-within:ring-2 focus-within:ring-purple-500/10 h-9">
          <label for="image-search" class="w-9 h-full flex-shrink-0 flex items-center justify-center text-slate-500 group-hover:text-slate-300 group-focus-within:text-purple-400 transition-colors cursor-pointer z-10">
            <Search class="w-4 h-4" />
          </label>
          <input
            id="image-search"
            v-model="searchFilename"
            type="text"
            placeholder="搜索图片文件名..."
            class="absolute left-9 w-[220px] h-full bg-transparent text-sm text-white placeholder:text-slate-500 outline-none opacity-0 group-hover:opacity-100 focus-within:!opacity-100 transition-opacity duration-300 pr-4"
            @keyup.enter="handleSearch"
          />
        </div>

        <!-- 上传按钮 -->
        <button
          class="group relative px-4 py-2 bg-purple-600 hover:bg-purple-500 text-white text-sm font-bold rounded-xl shadow-[0_0_15px_rgba(168,85,247,0.4)] transition-all overflow-hidden flex items-center space-x-2"
          @click="openUploadModal"
        >
          <div class="absolute inset-0 bg-[linear-gradient(to_right,transparent,rgba(255,255,255,0.2),transparent)] -translate-x-[150%] group-hover:translate-x-[150%] transition-transform duration-700 ease-out"></div>
          <Upload class="w-4 h-4 relative z-10" />
          <span class="relative z-10">上传图片</span>
        </button>
      </div>
    </div>

    <!-- 工具栏：全选 + 筛选 -->
    <div class="flex items-center justify-between relative z-10">
      <label class="flex items-center space-x-2 cursor-pointer select-none">
        <input
          type="checkbox"
          class="glass-checkbox"
          :checked="selectedIds.size === imageList.length && imageList.length > 0"
          @change="toggleSelectAll(($event.target as HTMLInputElement).checked)"
        />
        <span class="text-xs font-bold text-slate-400 uppercase tracking-wider">全选当前页</span>
      </label>
      <span class="text-xs text-slate-500">共 {{ formatNumber(total) }} 张图片</span>
    </div>

    <!-- 批量操作悬浮条 -->
    <div
      class="glass-panel rounded-xl px-4 py-3 flex items-center justify-between transition-all duration-300 sticky top-0 z-30 mb-4"
      :class="isBatchMode
        ? 'opacity-100 pointer-events-auto translate-y-0'
        : 'opacity-0 pointer-events-none -translate-y-[10px]'"
    >
      <div class="flex items-center space-x-3">
        <span class="flex h-2 w-2 relative">
          <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-purple-400 opacity-75"></span>
          <span class="relative inline-flex rounded-full h-2 w-2 bg-purple-500"></span>
        </span>
        <span class="text-sm font-bold text-purple-300">
          已选取 <span class="text-white mx-1">{{ selectedIds.size }}</span> 张图片
        </span>
      </div>
      <div class="flex items-center space-x-2">
        <button
          class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-teal-500/10 text-teal-400 hover:bg-teal-500 hover:text-white transition-all text-xs font-bold border border-teal-500/20 hover:shadow-[0_0_15px_rgba(20,184,166,0.4)]"
          @click="handleBatchSubmitAudit"
        >
          <Send class="w-3.5 h-3.5" />
          <span>批量提审</span>
        </button>
        <button
          class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-rose-500/10 text-rose-400 hover:bg-rose-500 hover:text-white transition-all text-xs font-bold border border-rose-500/20 hover:shadow-[0_0_15px_rgba(244,63,94,0.4)]"
          @click="handleBatchDelete"
        >
          <Trash2 class="w-3.5 h-3.5" />
          <span>批量删除</span>
        </button>
      </div>
    </div>

    <!-- 图片网格 -->
    <!-- 加载中 -->
    <div v-if="loading" class="flex flex-col items-center justify-center py-24 space-y-3 relative z-10">
      <Loader2 class="w-8 h-8 text-purple-400 animate-spin" />
      <span class="text-xs text-slate-500">加载图库中...</span>
    </div>

    <!-- 空数据 -->
    <div v-else-if="imageList.length === 0" class="flex flex-col items-center justify-center py-24 space-y-4 relative z-10">
      <Image class="w-12 h-12 text-slate-600" />
      <p class="text-sm text-slate-500">图库中暂无图片</p>
      <button
        class="text-sm font-bold text-purple-400 hover:text-purple-300 transition-colors flex items-center space-x-1.5"
        @click="openUploadModal"
      >
        <Upload class="w-4 h-4" />
        <span>上传第一张图片</span>
      </button>
    </div>

    <!-- 卡片网格 -->
    <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6 relative z-10">
      <div
        v-for="img in imageList"
        :key="img.id"
        class="glass-panel glass-card rounded-2xl overflow-hidden relative group"
        :class="{ 'border-rose-500/30 shadow-[0_0_15px_rgba(244,63,94,0.1)]': img.isPass === 2 }"
      >
        <!-- 选中复选框 -->
        <div class="absolute top-3 left-3 z-10 opacity-0 group-hover:opacity-100 transition-opacity">
          <input
            type="checkbox"
            class="glass-checkbox"
            :checked="selectedIds.has(img.id)"
            @change="toggleSelect(img.id)"
          />
        </div>

        <!-- 缩略图 -->
        <div class="h-48 w-full relative overflow-hidden bg-black/50">
          <img
            v-if="img.ossUrl"
            :src="img.ossUrl"
            :alt="img.filename"
            class="w-full h-full object-cover transition-transform duration-700 group-hover:scale-110"
            :class="{ 'grayscale-[30%]': img.isPass === 2 }"
          />
          <div v-else class="w-full h-full flex items-center justify-center text-slate-600">
            <Image class="w-10 h-10" />
          </div>

          <!-- 被拒图片覆盖层 -->
          <div v-if="img.isPass === 2" class="absolute inset-0 bg-rose-500/10 mix-blend-overlay pointer-events-none"></div>

          <!-- 悬浮操作覆盖层 -->
          <div class="absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex items-center justify-center gap-3">
            <button
              class="p-2 bg-white/10 hover:bg-white/20 backdrop-blur-md rounded-full hover:scale-110 transition-all"
              title="复制外链"
              @click="copyImageUrl(img.ossUrl)"
            >
              <Link class="w-4 h-4 text-white" />
            </button>
            <button
              v-if="img.ossUrl"
              class="p-2 bg-white/10 hover:bg-white/20 backdrop-blur-md rounded-full hover:scale-110 transition-all"
              title="查看原图"
              @click="previewUrl = img.ossUrl; showPreview = true"
            >
              <Maximize2 class="w-4 h-4 text-white" />
            </button>
            <button
              v-if="img.isPass === 2"
              class="p-2 bg-teal-500/20 hover:bg-teal-500/40 border border-teal-500/30 backdrop-blur-md rounded-full hover:scale-110 transition-all"
              title="重新提交审核"
              @click="handleSubmitAudit(img.id)"
            >
              <Send class="w-4 h-4 text-teal-300" />
            </button>
            <button
              class="p-2 bg-rose-500/20 hover:bg-rose-500/40 border border-rose-500/30 backdrop-blur-md rounded-full hover:scale-110 transition-all"
              title="删除"
              @click="handleDelete(img.id)"
            >
              <Trash2 class="w-4 h-4 text-rose-300" />
            </button>
          </div>

          <!-- 文件大小角标 -->
          <span class="absolute top-3 right-3 z-10 text-[10px] font-mono text-white bg-black/40 backdrop-blur-md border border-white/10 px-2 py-0.5 rounded-lg">
            {{ formatBytes(img.fileSize) }}
          </span>
        </div>

        <!-- 底部信息 -->
        <div class="p-4">
          <h3 class="text-sm font-bold text-white truncate" :title="img.filename">
            {{ img.filename || '未命名' }}
          </h3>
          <div class="flex items-center justify-between mt-3">
            <span class="text-xs text-slate-500">{{ formatDate(img.uploadTime || img.createTime) }}</span>
            <span
              class="inline-flex items-center space-x-1 px-2 py-0.5 rounded-md text-[10px] font-bold uppercase tracking-wider border"
              :class="getStatusInfo(img.isPass).cls"
            >
              <Info v-if="img.isPass === 2" class="w-3 h-3 cursor-help" title="审核未通过，可重新提交" />
              <span>{{ getStatusInfo(img.isPass).label }}</span>
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div
      v-if="!loading && imageList.length > 0"
      class="py-4 flex items-center justify-between bg-white/[0.01] rounded-xl border border-white/5 px-4 relative z-10"
    >
      <span class="text-xs text-slate-500">共加载 {{ formatNumber(total) }} 张图片</span>
      <div class="flex items-center space-x-1">
        <button
          class="w-7 h-7 rounded flex items-center justify-center text-slate-500 hover:bg-white/5 hover:text-white disabled:opacity-50"
          :disabled="currentPage <= 1"
          @click="handlePageChange(currentPage - 1)"
        >
          <ChevronLeft class="w-4 h-4" />
        </button>
        <template v-for="page in visiblePages()" :key="page">
          <button
            v-if="totalPages > 1"
            class="w-7 h-7 rounded flex items-center justify-center text-xs font-bold transition-colors"
            :class="page === currentPage
              ? 'bg-purple-500/20 text-purple-400 border border-purple-500/30'
              : 'text-slate-400 hover:bg-white/5 hover:text-white'"
            @click="handlePageChange(page)"
          >
            {{ page }}
          </button>
        </template>
        <button
          class="w-7 h-7 rounded flex items-center justify-center text-slate-500 hover:bg-white/5 hover:text-white"
          :disabled="currentPage >= totalPages"
          @click="handlePageChange(currentPage + 1)"
        >
          <ChevronRight class="w-4 h-4" />
        </button>
      </div>
    </div>

    <!-- 上传弹窗 -->
    <Teleport to="body">
      <div
        v-if="modalVisible"
        class="fixed inset-0 z-50 flex items-center justify-center"
      >
        <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="closeModal"></div>

        <div
          class="glass-panel w-full max-w-lg rounded-3xl p-8 relative z-10 transform transition-all duration-300"
          :class="modalVisible ? 'scale-100' : 'scale-95'"
        >
          <div class="absolute -top-10 -right-10 w-32 h-32 bg-purple-500/20 blur-[40px] rounded-full pointer-events-none"></div>

          <div class="flex justify-between items-center mb-6">
            <h3 class="text-xl font-bold text-white">上传新图片</h3>
            <button class="text-slate-500 hover:text-white transition-colors" @click="closeModal" :disabled="uploading">
              <X class="w-5 h-5" />
            </button>
          </div>

          <div class="space-y-5">
            <!-- 拖拽/点击上传区域 -->
            <div
              v-if="!uploadFile"
              class="w-full h-48 border-2 border-dashed border-white/20 rounded-xl bg-white/5 hover:bg-white/10 hover:border-purple-500/50 flex flex-col items-center justify-center cursor-pointer transition-colors group"
              @click="triggerFileInput"
            >
              <div class="w-12 h-12 rounded-full bg-purple-500/20 text-purple-400 flex items-center justify-center mb-3 group-hover:-translate-y-1 transition-transform shadow-[0_0_15px_rgba(168,85,247,0.3)]">
                <Upload class="w-6 h-6" />
              </div>
              <p class="text-sm font-bold text-white mb-1 group-hover:text-purple-300 transition-colors">点击或拖拽文件到此处</p>
              <p class="text-xs text-slate-400">支持 JPG, PNG, WEBP, GIF, 最大 5MB</p>
            </div>

            <!-- 已选文件预览 -->
            <div v-else class="relative w-full h-48 rounded-xl overflow-hidden bg-black/50">
              <img v-if="uploadPreview" :src="uploadPreview" class="w-full h-full object-contain" />
              <button
                class="absolute top-2 right-2 w-7 h-7 rounded-full bg-black/60 text-white flex items-center justify-center hover:bg-rose-500/60 transition-colors"
                @click="uploadFile = null; uploadPreview = ''"
              >
                <X class="w-4 h-4" />
              </button>
            </div>

            <!-- 隐藏文件选择器 -->
            <input
              ref="fileInput"
              type="file"
              accept="image/jpeg,image/png,image/webp,image/gif,image/bmp,image/svg+xml"
              class="hidden"
              @change="handleFileChange"
            />

            <!-- 归属主题 -->
            <div>
              <label class="block text-xs font-bold text-slate-400 uppercase tracking-widest mb-2">
                所属主题 <span class="text-slate-500 normal-case tracking-normal">(可选)</span>
              </label>
              <div class="relative group">
                <select
                  v-model="uploadTopicId"
                  class="w-full bg-black/20 border border-white/[0.05] shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] rounded-xl py-3 pl-4 pr-10 outline-none focus:bg-black/40 focus:border-purple-500/50 focus:ring-2 focus:ring-purple-500/10 transition-all text-sm text-white appearance-none cursor-pointer"
                >
                  <option :value="undefined" class="bg-[#0b0d14]">-- 不关联任何主题 --</option>
                  <option v-for="t in topicOptions" :key="t.id" :value="t.id" class="bg-[#0b0d14]">
                    {{ t.topicName }}
                  </option>
                </select>
                <div class="absolute inset-y-0 right-0 pr-3 flex items-center pointer-events-none text-slate-500">
                  <ChevronLeft class="w-4 h-4 rotate-[-90deg]" />
                </div>
              </div>
            </div>

            <!-- 操作按钮 -->
            <div class="pt-4 flex justify-end space-x-3">
              <button
                type="button"
                class="px-5 py-2.5 rounded-xl text-sm font-bold text-slate-400 hover:text-white hover:bg-white/5 transition-colors"
                @click="closeModal"
                :disabled="uploading"
              >
                取消
              </button>
              <button
                type="button"
                class="px-5 py-2.5 bg-purple-600 hover:bg-purple-500 text-white text-sm font-bold rounded-xl shadow-[0_0_15px_rgba(168,85,247,0.4)] transition-all flex items-center space-x-2 disabled:opacity-50 disabled:cursor-not-allowed"
                :disabled="!uploadFile || uploading"
                @click="handleUpload"
              >
                <Loader2 v-if="uploading" class="w-4 h-4 animate-spin" />
                <span>{{ uploading ? '上传中...' : '确认上传' }}</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 全屏预览模态框 -->
    <Teleport to="body">
      <div
        v-if="showPreview"
        class="fixed inset-0 z-[60] flex items-center justify-center"
        @click.self="showPreview = false"
      >
        <div class="absolute inset-0 bg-black/90 backdrop-blur-md" @click="showPreview = false"></div>
        <div class="relative z-10 max-w-[90vw] max-h-[90vh]">
          <button
            class="absolute -top-12 right-0 text-slate-400 hover:text-white transition-colors p-2"
            @click="showPreview = false"
          >
            <X class="w-6 h-6" />
          </button>
          <img
            :src="previewUrl"
            class="max-w-full max-h-[85vh] rounded-xl object-contain shadow-2xl"
          />
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* 液态玻璃基类 */
.glass-panel {
  background: rgba(255, 255, 255, 0.02);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.05);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.05);
}

/* 图片卡片悬浮效果 */
.glass-card {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}
.glass-card:hover {
  transform: translateY(-4px);
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(168, 85, 247, 0.3);
  box-shadow: 0 10px 40px -10px rgba(168, 85, 247, 0.15), 0 0 20px -5px rgba(59, 130, 246, 0.15);
}

/* 自定义复选框 (紫色主题) */
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
  background: #8b5cf6;
  border-color: #8b5cf6;
  box-shadow: 0 0 10px rgba(139, 92, 246, 0.4);
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

/* 全局检索开关 — 跑马灯光效 */
@keyframes shine {
  to { background-position: 200% center; }
}
.search-toggle {
  position: relative;
  background-color: rgba(255, 255, 255, 0.02);
}
.search-toggle::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(129,140,248,0.15), rgba(232,121,249,0.15), rgba(244,114,182,0.15), rgba(232,121,249,0.15), rgba(129,140,248,0.15));
  background-size: 200% auto;
  animation: shine 3s linear infinite;
  opacity: 0;
  transition: opacity 0.3s ease;
  z-index: 0;
  border-radius: 9999px;
}
.search-toggle:hover::before {
  opacity: 1;
}
.search-toggle.is-active::before {
  opacity: 1;
  background: linear-gradient(90deg, rgba(129,140,248,0.3), rgba(232,121,249,0.3), rgba(244,114,182,0.3), rgba(232,121,249,0.3), rgba(129,140,248,0.3));
  animation: shine 2s linear infinite;
}
.search-toggle > * {
  position: relative;
  z-index: 1;
}
</style>
