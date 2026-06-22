<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { tagApi } from '@/api/tags'
import type { TagItem } from '@/api/tags'
import { noteApi, getNoteStatusInfo } from '@/api/notes'
import type { TagBacklinkVO } from '@/api/notes'
import { useAuthStore } from '@/stores/auth'
import {
  Tags, Plus, Search, Globe, Hash, Send, Info, Loader2,
  X, ChevronLeft, ChevronRight, Trash2, Link, FileText, CornerUpLeft
} from 'lucide-vue-next'
import { confirmAction, toastWarning } from '@/utils/feedback'

const authStore = useAuthStore()
const loading = ref(true)
const tagList = ref<TagItem[]>([])
const total = ref(0)
const searchKeyword = ref('')
const searchMode = ref<'personal' | 'global'>('personal')
const currentPage = ref(1)
const pageSize = ref(15)
const selectedIds = ref<Set<number>>(new Set())

const modalVisible = ref(false)
const formTagName = ref('')
const submitting = ref(false)

const router = useRouter()

// ── Tag backlinks ──
const expandedTagId = ref<number | null>(null)
const tagBacklinks = ref<TagBacklinkVO[]>([])
const tagBacklinksLoading = ref(false)
const tagBacklinksFetchedTagId = ref<number | null>(null)

async function fetchTagBacklinks(tagId: number) {
  tagBacklinksLoading.value = true
  try {
    tagBacklinks.value = await noteApi.getTagBacklinks(tagId)
    tagBacklinksFetchedTagId.value = tagId
  } finally {
    tagBacklinksLoading.value = false
  }
}

async function toggleTagBacklinks(tagId: number) {
  if (expandedTagId.value === tagId) {
    expandedTagId.value = null
    return
  }
  expandedTagId.value = tagId
  if (tagBacklinksFetchedTagId.value !== tagId) {
    await fetchTagBacklinks(tagId)
  }
}

function handleTagBacklinkClick(b: TagBacklinkVO) {
  router.push(`/user/notes/${b.sourceNoteId}`)
}

const isBatchMode = computed(() => selectedIds.value.size > 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function getStatusInfo(auditStatus: number): { label: string; cls: string } {
  switch (auditStatus) {
    case 1:
      return { label: '审核中', cls: 'text-sky-400 bg-sky-500/10 border-sky-500/20' }
    case 2:
      return { label: '已通过', cls: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20' }
    case 3:
      return { label: '已拒绝', cls: 'text-rose-400 bg-rose-500/10 border-rose-500/20' }
    default:
      return { label: '待审核', cls: 'text-amber-400 bg-amber-500/10 border-amber-500/20' }
  }
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

async function fetchTags() {
  try {
    const res = await tagApi.getList({
      keyword: searchKeyword.value || undefined,
      scope: searchMode.value,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    })
    tagList.value = res.records ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loading.value = true
  fetchTags()
}

function handlePageChange(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  currentPage.value = page
  loading.value = true
  fetchTags()
}

function toggleGlobalSearch() {
  searchMode.value = searchMode.value === 'personal' ? 'global' : 'personal'
  currentPage.value = 1
  selectedIds.value.clear()
  expandedTagId.value = null
  loading.value = true
  fetchTags()
}

function toggleSelectAll(checked: boolean) {
  if (checked) {
    tagList.value.forEach(t => selectedIds.value.add(t.id))
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

function isSelected(id: number): boolean {
  return selectedIds.value.has(id)
}

function openAddModal() {
  formTagName.value = ''
  modalVisible.value = true
}

function closeModal() {
  modalVisible.value = false
}

async function handleSubmit() {
  const name = formTagName.value.trim()
  if (!name) return
  if (name.length > 20) {
    toastWarning('标签名称不能超过 20 个字符')
    return
  }

  submitting.value = true
  try {
    await tagApi.addTag({ tagName: name })
    closeModal()
    await fetchTags()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  if (!await confirmAction({ content: '确定删除该标签吗？', danger: true })) return
  await tagApi.deleteTags([id])
  selectedIds.value.delete(id)
  await fetchTags()
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) return
  if (!await confirmAction({ content: `确定要删除已选择的 ${selectedIds.value.size} 个标签吗？`, danger: true })) return
  await tagApi.deleteTags([...selectedIds.value])
  selectedIds.value.clear()
  await fetchTags()
}

async function handleSubmitAudit(id: number) {
  if (!await confirmAction({ content: '确认提交该标签进行审核吗？' })) return
  await tagApi.submitAudit(id)
  await fetchTags()
}

async function handleCancelAudit(id: number) {
  if (!await confirmAction({ content: '确认撤销该标签的审核申请吗？' })) return
  try {
    await tagApi.cancelAudit(id)
    await fetchTags()
  } catch {
    // 错误信息由 request 拦截器统一弹出
  }
}

onMounted(() => {
  fetchTags()
})
</script>

<template>
  <div class="relative mx-auto max-w-6xl space-y-6">
    <div class="fixed top-[-10%] right-[-5%] z-0 h-[500px] w-[500px] rounded-full bg-purple-600/10 blur-[150px] pointer-events-none"></div>
    <div class="fixed bottom-[-10%] left-[-5%] z-0 h-[400px] w-[400px] rounded-full bg-violet-600/10 blur-[120px] pointer-events-none"></div>

    <div class="relative z-10 flex flex-col justify-between gap-4 md:flex-row md:items-center">
      <div class="flex items-center space-x-3">
        <div class="rounded-xl border border-purple-500/20 bg-purple-500/10 p-2 text-purple-400">
          <Tags class="h-5 w-5" />
        </div>
        <div>
          <h2 class="text-xl font-bold text-white">标签矩阵控制台</h2>
          <p class="mt-0.5 text-xs text-slate-400">构建和管理您的原子化标签维度</p>
        </div>
      </div>

      <div class="flex items-center space-x-4">
        <div
          class="global-search-btn flex cursor-pointer select-none items-center space-x-2 rounded-xl border border-white/5 bg-white/[0.02] px-3 py-1.5 transition-colors"
          :class="{ 'is-active': searchMode === 'global' }"
          @click="toggleGlobalSearch"
        >
          <Globe
            class="relative z-10 h-3.5 w-3.5 transition-colors"
            :class="searchMode === 'global' ? 'text-purple-400' : 'text-slate-500'"
          />
          <span
            class="relative z-10 whitespace-nowrap text-xs font-bold transition-colors"
            :class="searchMode === 'global' ? 'text-purple-300' : 'text-slate-400'"
          >
            {{ searchMode === 'global' ? '全局搜索' : '个人生态' }}
          </span>
          <div
            class="relative ml-1 inline-flex h-4 w-7 items-center rounded-full border transition-colors"
            :class="searchMode === 'global'
              ? 'border-purple-500/50 bg-purple-500'
              : 'border-white/10 bg-black/50'"
          >
            <span
              class="inline-block h-3 w-3 transform rounded-full transition-transform"
              :class="searchMode === 'global'
                ? 'translate-x-3.5 bg-white'
                : 'translate-x-0.5 bg-slate-400'"
            ></span>
          </div>
        </div>

        <div class="group relative flex h-9 w-9 items-center overflow-hidden rounded-xl border border-white/10 bg-black/20 transition-all duration-300 ease-out hover:w-28 focus-within:!w-64 focus-within:border-purple-500/50 focus-within:bg-black/40 focus-within:ring-2 focus-within:ring-purple-500/10">
          <label for="tag-search" class="z-10 flex h-full w-9 flex-shrink-0 cursor-pointer items-center justify-center text-slate-500 transition-colors group-hover:text-slate-300 group-focus-within:text-purple-400">
            <Search class="h-4 w-4" />
          </label>
          <input
            id="tag-search"
            v-model="searchKeyword"
            type="text"
            placeholder="搜索特定标签..."
            class="absolute left-9 h-full w-[220px] bg-transparent pr-4 text-sm text-white opacity-0 outline-none transition-opacity duration-300 group-hover:opacity-100 focus-within:!opacity-100 placeholder:text-slate-500"
            @keyup.enter="handleSearch"
          />
        </div>

        <button
          class="group relative flex items-center space-x-2 overflow-hidden rounded-xl bg-purple-600 px-4 py-2 text-sm font-bold text-white shadow-[0_0_15px_rgba(139,92,246,0.4)] transition-all hover:bg-purple-500"
          @click="openAddModal"
        >
          <div class="absolute inset-0 -translate-x-[150%] bg-[linear-gradient(to_right,transparent,rgba(255,255,255,0.2),transparent)] transition-transform duration-700 ease-out group-hover:translate-x-[150%]"></div>
          <Plus class="relative z-10 h-4 w-4" />
          <span class="relative z-10">新建标签</span>
        </button>
      </div>
    </div>

    <div
      class="glass-panel relative z-10 flex items-center justify-between rounded-xl px-4 py-3 transition-all duration-300"
      :class="isBatchMode
        ? 'translate-y-0 opacity-100 pointer-events-auto'
        : '-translate-y-[10px] opacity-0 pointer-events-none'"
    >
      <div class="flex items-center space-x-3">
        <span class="relative flex h-2 w-2">
          <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-purple-400 opacity-75"></span>
          <span class="relative inline-flex h-2 w-2 rounded-full bg-purple-500"></span>
        </span>
        <span class="text-sm font-bold text-purple-300">
          已选择 <span class="mx-1 text-white">{{ selectedIds.size }}</span> 个原子标签
        </span>
      </div>
      <button
        class="flex items-center space-x-1.5 rounded-lg border border-rose-500/20 bg-rose-500/10 px-3 py-1.5 text-xs font-bold text-rose-400 transition-all hover:bg-rose-500 hover:text-white hover:shadow-[0_0_15px_rgba(244,63,94,0.4)]"
        @click="handleBatchDelete"
      >
        <Trash2 class="h-3.5 w-3.5" />
        <span>批量删除</span>
      </button>
    </div>

    <div class="glass-panel relative z-10 overflow-hidden rounded-2xl border border-white/10">
      <div class="overflow-x-auto">
        <table class="w-full border-collapse text-left">
          <thead>
            <tr>
              <th class="w-10 border-b border-white/5 px-6 py-4">
                <input
                  type="checkbox"
                  class="glass-checkbox"
                  :checked="selectedIds.size === tagList.length && tagList.length > 0"
                  @change="toggleSelectAll(($event.target as HTMLInputElement).checked)"
                />
              </th>
              <th class="border-b border-white/5 px-6 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">原子标签</th>
              <th class="border-b border-white/5 px-6 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">审核状态</th>
              <th class="border-b border-white/5 px-6 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">确立时间</th>
              <th class="border-b border-white/5 px-6 py-4 text-right text-xs font-bold uppercase tracking-wider text-slate-400">操作控制台</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr v-if="loading">
              <td colspan="5" class="px-6 py-16 text-center">
                <div class="flex flex-col items-center space-y-3">
                  <Loader2 class="h-6 w-6 animate-spin text-purple-400" />
                  <span class="text-xs text-slate-500">加载中...</span>
                </div>
              </td>
            </tr>

            <tr v-else-if="tagList.length === 0">
              <td colspan="5" class="px-6 py-16 text-center">
                <div class="flex flex-col items-center space-y-3">
                  <Tags class="h-8 w-8 text-slate-600" />
                  <p class="text-sm text-slate-500">暂无原子标签数据</p>
                  <button
                    class="text-xs font-bold text-purple-400 transition-colors hover:text-purple-300"
                    @click="openAddModal"
                  >
                    立即定义第一个标签
                  </button>
                </div>
              </td>
            </tr>

            <template v-for="tag in tagList" :key="tag.id">
            <tr
              class="group transition-colors duration-200 hover:bg-white/5"
            >
              <td class="px-6 py-4">
                <input
                  type="checkbox"
                  class="glass-checkbox"
                  :checked="isSelected(tag.id)"
                  @change="toggleSelect(tag.id)"
                />
              </td>
              <td class="px-6 py-4">
                <div class="flex flex-col">
                  <div class="tag-badge inline-flex w-max items-center space-x-1.5 rounded-full border border-white/10 px-3 py-1.5">
                    <Hash class="h-3.5 w-3.5 text-purple-400" />
                    <span class="text-sm font-bold text-slate-200">{{ tag.tagName }}</span>
                  </div>
                  <span v-if="searchMode === 'global' && tag.userId && tag.userId !== authStore.user?.id" class="text-[9px] text-slate-600 bg-black/30 px-1.5 py-0.5 rounded border border-white/5 mt-1.5 ml-1 w-max">UID:{{ tag.userId }}</span>
                </div>
              </td>
              <td class="px-6 py-4">
                <div class="flex items-center space-x-2">
                  <span
                    class="inline-flex items-center rounded-md border px-2 py-1 text-[10px] font-bold uppercase tracking-wider"
                    :class="getStatusInfo(tag.auditStatus).cls"
                  >
                    {{ getStatusInfo(tag.auditStatus).label }}
                  </span>
                  <div v-if="tag.auditStatus === 3" class="group/tooltip relative flex items-center">
                    <Info class="h-4 w-4 cursor-help text-rose-400" />
                    <div
                      class="pointer-events-none absolute left-1/2 top-full z-20 mt-2 w-44 -translate-x-1/2 scale-95 rounded-xl border border-rose-500/20 bg-slate-950/95 px-3 py-2 text-[11px] leading-5 text-rose-100 opacity-0 shadow-[0_14px_40px_rgba(15,23,42,0.45)] transition-all duration-200 ease-out group-hover/tooltip:scale-100 group-hover/tooltip:opacity-100"
                    >
                      该标签审核未通过，可修改后重新提交审核。
                    </div>
                  </div>
                </div>
              </td>
              <td class="px-6 py-4 text-xs text-slate-500">
                {{ formatDate(tag.createTime) }}
              </td>
              <td class="px-6 py-4 text-right">
                <div class="flex items-center justify-end space-x-2 translate-x-1 opacity-0 transition-all duration-200 group-hover:translate-x-0 group-hover:opacity-100">
                  <button
                    class="flex items-center space-x-1 rounded border border-cyan-500/20 bg-cyan-500/10 px-2 py-1 text-[10px] font-bold uppercase text-cyan-400 transition-colors hover:bg-cyan-500/20"
                    title="查看引用笔记"
                    @click="toggleTagBacklinks(tag.id)"
                  >
                    <Link class="h-3 w-3" />
                    <span>引用</span>
                    <ChevronRight class="h-3 w-3 transition-transform" :class="expandedTagId === tag.id ? 'rotate-90' : ''" />
                  </button>
                  <button
                    v-if="tag.auditStatus === 1"
                    class="flex items-center space-x-1 rounded border border-orange-500/20 bg-orange-500/10 px-2 py-1 text-[10px] font-bold uppercase text-orange-400 transition-colors hover:bg-orange-500/20"
                    title="撤销审核"
                    @click="handleCancelAudit(tag.id)"
                  >
                    <CornerUpLeft class="h-3 w-3" />
                    <span>撤销</span>
                  </button>
                  <button
                    v-if="tag.auditStatus === 0 || tag.auditStatus === 3"
                    class="flex items-center space-x-1 rounded border border-teal-500/20 bg-teal-500/10 px-2 py-1 text-[10px] font-bold uppercase text-teal-400 transition-colors hover:bg-teal-500/20"
                    title="提交审核"
                    @click="handleSubmitAudit(tag.id)"
                  >
                    <Send class="h-3 w-3" />
                    <span>送审</span>
                  </button>
                  <button
                    class="flex h-7 w-7 items-center justify-center rounded bg-white/5 text-slate-400 transition-colors hover:bg-rose-500/20 hover:text-rose-400"
                    title="删除"
                    @click="handleDelete(tag.id)"
                  >
                    <Trash2 class="h-3.5 w-3.5" />
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="expandedTagId === tag.id" class="border-b border-cyan-500/10 bg-cyan-500/[0.02]">
              <td :colspan="5" class="px-6 py-4">
                <div v-if="tagBacklinksLoading" class="text-xs text-slate-500 text-center py-4 flex items-center justify-center gap-2">
                  <Loader2 class="w-3.5 h-3.5 animate-spin" /> 加载中...
                </div>
                <div v-else-if="!tagBacklinks.length" class="text-xs text-slate-500 text-center py-4">
                  暂无笔记引用此标签
                </div>
                <div v-else class="space-y-2 max-h-[40vh] overflow-y-auto custom-scrollbar">
                  <div
                    v-for="b in tagBacklinks"
                    :key="b.sourceNoteId"
                    class="flex items-center justify-between bg-black/20 p-2.5 rounded-xl border border-white/5 hover:border-cyan-500/30 group/ref transition-colors cursor-pointer"
                    @click="handleTagBacklinkClick(b)"
                  >
                    <div class="flex items-center space-x-2 overflow-hidden">
                      <div class="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 bg-cyan-500/10 text-cyan-400">
                        <FileText class="w-4 h-4" />
                      </div>
                      <div class="flex flex-col min-w-0">
                        <span class="text-xs font-medium truncate text-slate-300 group-hover/ref:text-cyan-300 transition-colors">
                          {{ b.sourceNoteTitle }}
                        </span>
                        <span class="text-[9px] text-slate-500 mt-0.5 truncate">via [[{{ b.parsedTagName }}]]</span>
                      </div>
                    </div>
                    <div class="flex items-center gap-1.5 shrink-0 ml-2">
                      <span v-if="b.isCrossUser === 1" class="text-[9px] font-bold px-1.5 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/20" title="跨用户引用">跨用户</span>
                      <span class="text-[9px] font-bold px-1.5 py-0.5 rounded border" :class="getNoteStatusInfo(b.sourceNoteStatus).cls">{{ getNoteStatusInfo(b.sourceNoteStatus).label }}</span>
                    </div>
                  </div>
                </div>
              </td>
            </tr>
            </template>
          </tbody>
        </table>
      </div>

      <div
        v-if="!loading && tagList.length > 0"
        class="flex items-center justify-between border-t border-white/5 bg-white/[0.01] px-6 py-4"
      >
        <span class="text-xs text-slate-500">
          发现 {{ formatNumber(total) }} 条原子标签
        </span>
        <div class="flex items-center space-x-1">
          <button
            class="flex h-7 w-7 items-center justify-center rounded text-slate-500 hover:bg-white/5 hover:text-white disabled:opacity-50"
            :disabled="currentPage <= 1"
            @click="handlePageChange(currentPage - 1)"
          >
            <ChevronLeft class="h-4 w-4" />
          </button>
          <template v-for="page in visiblePages()" :key="page">
            <button
              v-if="totalPages > 1"
              class="flex h-7 w-7 items-center justify-center rounded text-xs font-bold transition-colors"
              :class="page === currentPage
                ? 'border border-purple-500/30 bg-purple-500/20 text-purple-400'
                : 'text-slate-400 hover:bg-white/5 hover:text-white'"
              @click="handlePageChange(page)"
            >
              {{ page }}
            </button>
          </template>
          <button
            class="flex h-7 w-7 items-center justify-center rounded text-slate-500 hover:bg-white/5 hover:text-white"
            :disabled="currentPage >= totalPages"
            @click="handlePageChange(currentPage + 1)"
          >
            <ChevronRight class="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="modalVisible"
          class="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm"
          @click="closeModal"
        ></div>
      </Transition>

      <Transition name="modal">
        <div
          v-if="modalVisible"
          class="fixed inset-0 z-50 flex items-center justify-center px-4"
        >
          <div class="glass-panel modal-card relative z-10 w-full max-w-md rounded-3xl p-8">
            <div class="pointer-events-none absolute -top-10 -right-10 h-32 w-32 rounded-full bg-purple-500/20 blur-[40px]"></div>

            <div class="mb-6 flex items-center justify-between">
              <h3 class="text-xl font-bold text-white">定义原子标签</h3>
              <button class="text-slate-500 transition-colors hover:text-white" @click="closeModal">
                <X class="h-5 w-5" />
              </button>
            </div>

            <form class="space-y-5" @submit.prevent="handleSubmit">
              <div>
                <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-slate-400">
                  标签名称 <span class="text-rose-500">*</span>
                </label>
                <div class="group relative">
                  <div class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-500 transition-colors group-focus-within:text-purple-400">
                    <Hash class="h-[18px] w-[18px]" />
                  </div>
                  <input
                    v-model="formTagName"
                    type="text"
                    placeholder="例如：前端架构"
                    required
                    maxlength="20"
                    class="w-full rounded-xl border border-white/[0.05] bg-black/20 py-3 pl-10 pr-4 text-sm text-white shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] outline-none transition-all placeholder:text-slate-600 focus:border-purple-500/50 focus:bg-black/40 focus:ring-2 focus:ring-purple-500/10"
                  />
                </div>
                <p class="mt-2 text-[10px] text-slate-500">标签需要通过审核才能在公网生效。</p>
              </div>

              <div class="flex justify-end space-x-3 pt-4">
                <button
                  type="button"
                  class="rounded-xl px-5 py-2.5 text-sm font-bold text-slate-400 transition-colors hover:bg-white/5 hover:text-white"
                  @click="closeModal"
                >
                  取消
                </button>
                <button
                  type="submit"
                  class="flex items-center space-x-2 rounded-xl bg-purple-600 px-5 py-2.5 text-sm font-bold text-white shadow-[0_0_15px_rgba(139,92,246,0.4)] transition-all hover:bg-purple-500"
                  :disabled="submitting"
                >
                  <Loader2 v-if="submitting" class="h-4 w-4 animate-spin" />
                  <span>{{ submitting ? '创建中...' : '确认创建' }}</span>
                </button>
              </div>
            </form>
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

.tag-badge {
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.05) 0%, rgba(255, 255, 255, 0.01) 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.1);
}

@keyframes shine {
  to {
    background-position: 200% center;
  }
}

.global-search-btn {
  position: relative;
  overflow: hidden;
  transition: all 0.3s ease;
}

.global-search-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(139, 92, 246, 0.15), rgba(168, 85, 247, 0.15), rgba(59, 130, 246, 0.15), rgba(139, 92, 246, 0.15));
  background-size: 200% auto;
  animation: shine 3s linear infinite;
  opacity: 0;
  transition: opacity 0.3s ease;
  z-index: 0;
}

.global-search-btn:hover::before,
.global-search-btn.is-active::before {
  opacity: 1;
}

.global-search-btn.is-active {
  border-color: rgba(168, 85, 247, 0.4);
  box-shadow: 0 0 15px rgba(168, 85, 247, 0.2), inset 0 0 10px rgba(139, 92, 246, 0.1);
}

.global-search-btn > * {
  position: relative;
  z-index: 1;
}

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

.modal-enter-to,
.modal-leave-from {
  opacity: 1;
  transform: scale(1) translateY(0);
}

.modal-card {
  transform-origin: center center;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.45), inset 0 1px 1px rgba(255, 255, 255, 0.05);
}
</style>
