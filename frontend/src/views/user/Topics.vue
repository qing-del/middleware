<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { topicApi } from '@/api/topics'
import type { TopicItem } from '@/api/topics'
import { useAuthStore } from '@/stores/auth'
import {
  Layers, Plus, Search, Globe, FolderTree, Pencil, Trash2,
  Send, Info, Loader2, X, ChevronLeft, ChevronRight,
  ArrowUpDown, CornerUpLeft
} from 'lucide-vue-next'

const authStore = useAuthStore()
const loading = ref(true)
const topicList = ref<TopicItem[]>([])
const total = ref(0)
const searchKeyword = ref('')
const searchMode = ref<'personal' | 'global'>('personal')
const currentPage = ref(1)
const pageSize = ref(15)
const selectedIds = ref<Set<number>>(new Set())

const modalVisible = ref(false)
const modalMode = ref<'add' | 'edit'>('add')
const editingTopic = ref<TopicItem | null>(null)
const formTopicName = ref('')
const formSortOrder = ref(0)
const submitting = ref(false)

const isBatchMode = computed(() => selectedIds.value.size > 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function getStatusInfo(isPass: number): { label: string; cls: string } {
  switch (isPass) {
    case 1:
      return { label: '已通过', cls: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20' }
    case 2:
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

async function fetchTopics() {
  try {
    const res = await topicApi.getList({
      keyword: searchKeyword.value || undefined,
      scope: searchMode.value,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    })
    topicList.value = res.records ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loading.value = true
  fetchTopics()
}

function handlePageChange(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  currentPage.value = page
  loading.value = true
  fetchTopics()
}

function toggleGlobalSearch() {
  searchMode.value = searchMode.value === 'personal' ? 'global' : 'personal'
  currentPage.value = 1
  selectedIds.value.clear()
  loading.value = true
  fetchTopics()
}

function toggleSelectAll(checked: boolean) {
  if (checked) {
    topicList.value.forEach(t => selectedIds.value.add(t.id))
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
  modalMode.value = 'add'
  editingTopic.value = null
  formTopicName.value = ''
  formSortOrder.value = 0
  modalVisible.value = true
}

function openEditModal(topic: TopicItem) {
  modalMode.value = 'edit'
  editingTopic.value = topic
  formTopicName.value = topic.topicName
  formSortOrder.value = topic.sortOrder
  modalVisible.value = true
}

function closeModal() {
  modalVisible.value = false
}

async function handleSubmit() {
  const name = formTopicName.value.trim()
  if (!name) return
  if (name.length > 25) {
    alert('主题名称不能超过 25 个字符')
    return
  }

  submitting.value = true
  try {
    if (modalMode.value === 'add') {
      await topicApi.addTopic({ topicName: name, sortOrder: formSortOrder.value })
    } else if (editingTopic.value) {
      await topicApi.modifyTopic({ id: editingTopic.value.id, sortOrder: formSortOrder.value })
    }
    closeModal()
    await fetchTopics()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  if (!confirm('确定删除该主题吗？')) return
  await topicApi.deleteTopics([id])
  selectedIds.value.delete(id)
  await fetchTopics()
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) return
  if (!confirm(`确定要删除已选择的 ${selectedIds.value.size} 个主题吗？`)) return
  await topicApi.deleteTopics([...selectedIds.value])
  selectedIds.value.clear()
  await fetchTopics()
}

async function handleSubmitAudit(id: number) {
  if (!confirm('确认提交该主题进行审核吗？')) return
  await topicApi.submitAudit(id)
  await fetchTopics()
}

async function handleCancelAudit(id: number) {
  if (!confirm('确认撤销该主题的审核申请吗？')) return
  try {
    await topicApi.cancelAudit(id)
    await fetchTopics()
  } catch {
    // 错误信息由 request 拦截器统一弹出
  }
}

onMounted(() => {
  fetchTopics()
})
</script>

<template>
  <div class="relative mx-auto max-w-6xl space-y-6">
    <div class="fixed top-[-10%] right-[-5%] z-0 h-[500px] w-[500px] rounded-full bg-indigo-600/10 blur-[150px] pointer-events-none"></div>
    <div class="fixed bottom-[-10%] left-[-5%] z-0 h-[400px] w-[400px] rounded-full bg-purple-600/10 blur-[120px] pointer-events-none"></div>

    <div class="relative z-10 flex flex-col justify-between gap-4 md:flex-row md:items-center">
      <div class="flex items-center space-x-3">
        <div class="rounded-xl border border-indigo-500/20 bg-indigo-500/10 p-2 text-indigo-400">
          <Layers class="h-5 w-5" />
        </div>
        <div>
          <h2 class="text-xl font-bold text-white">主题生态目录</h2>
          <p class="mt-0.5 text-xs text-slate-400">构建和管理您的笔记核心分类维度</p>
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
            :class="searchMode === 'global' ? 'text-indigo-400' : 'text-slate-500'"
          />
          <span
            class="relative z-10 whitespace-nowrap text-xs font-bold transition-colors"
            :class="searchMode === 'global' ? 'text-indigo-300' : 'text-slate-400'"
          >
            {{ searchMode === 'global' ? '全局搜索' : '个人生态' }}
          </span>
          <div
            class="relative ml-1 inline-flex h-4 w-7 items-center rounded-full border transition-colors"
            :class="searchMode === 'global'
              ? 'border-indigo-500/50 bg-indigo-500'
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

        <div class="group relative flex h-9 w-9 items-center overflow-hidden rounded-xl border border-white/10 bg-black/20 transition-all duration-300 ease-out hover:w-28 focus-within:!w-64 focus-within:border-indigo-500/50 focus-within:bg-black/40 focus-within:ring-2 focus-within:ring-indigo-500/10">
          <label for="topic-search" class="z-10 flex h-full w-9 flex-shrink-0 cursor-pointer items-center justify-center text-slate-500 transition-colors group-hover:text-slate-300 group-focus-within:text-indigo-400">
            <Search class="h-4 w-4" />
          </label>
          <input
            id="topic-search"
            v-model="searchKeyword"
            type="text"
            placeholder="搜索主题名称..."
            class="absolute left-9 h-full w-[220px] bg-transparent pr-4 text-sm text-white opacity-0 outline-none transition-opacity duration-300 group-hover:opacity-100 focus-within:!opacity-100 placeholder:text-slate-500"
            @keyup.enter="handleSearch"
          />
        </div>

        <button
          class="group relative flex items-center space-x-2 overflow-hidden rounded-xl bg-indigo-600 px-4 py-2 text-sm font-bold text-white shadow-[0_0_15px_rgba(99,102,241,0.4)] transition-all hover:bg-indigo-500"
          @click="openAddModal"
        >
          <div class="absolute inset-0 -translate-x-[150%] bg-[linear-gradient(to_right,transparent,rgba(255,255,255,0.2),transparent)] transition-transform duration-700 ease-out group-hover:translate-x-[150%]"></div>
          <Plus class="relative z-10 h-4 w-4" />
          <span class="relative z-10">新建主题</span>
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
          <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-indigo-400 opacity-75"></span>
          <span class="relative inline-flex h-2 w-2 rounded-full bg-indigo-500"></span>
        </span>
        <span class="text-sm font-bold text-indigo-300">
          已选择 <span class="mx-1 text-white">{{ selectedIds.size }}</span> 个主题
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
                  :checked="selectedIds.size === topicList.length && topicList.length > 0"
                  @change="toggleSelectAll(($event.target as HTMLInputElement).checked)"
                />
              </th>
              <th class="border-b border-white/5 px-6 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">主题名称</th>
              <th class="border-b border-white/5 px-6 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">排序等级</th>
              <th class="border-b border-white/5 px-6 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">状态</th>
              <th class="border-b border-white/5 px-6 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">创建时间</th>
              <th class="border-b border-white/5 px-6 py-4 text-right text-xs font-bold uppercase tracking-wider text-slate-400">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr v-if="loading">
              <td colspan="6" class="px-6 py-16 text-center">
                <div class="flex flex-col items-center space-y-3">
                  <Loader2 class="h-6 w-6 animate-spin text-indigo-400" />
                  <span class="text-xs text-slate-500">加载中...</span>
                </div>
              </td>
            </tr>

            <tr v-else-if="topicList.length === 0">
              <td colspan="6" class="px-6 py-16 text-center">
                <div class="flex flex-col items-center space-y-3">
                  <Layers class="h-8 w-8 text-slate-600" />
                  <p class="text-sm text-slate-500">暂无主题数据</p>
                  <button
                    class="text-xs font-bold text-indigo-400 transition-colors hover:text-indigo-300"
                    @click="openAddModal"
                  >
                    立即创建第一个主题
                  </button>
                </div>
              </td>
            </tr>

            <tr
              v-for="topic in topicList"
              :key="topic.id"
              class="group transition-colors duration-200 hover:bg-white/5"
            >
              <td class="px-6 py-4">
                <input
                  type="checkbox"
                  class="glass-checkbox"
                  :checked="isSelected(topic.id)"
                  @change="toggleSelect(topic.id)"
                />
              </td>
              <td class="px-6 py-4">
                <div class="flex items-center space-x-3">
                  <div class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg bg-indigo-500/10 text-indigo-400">
                    <FolderTree class="h-4 w-4" />
                  </div>
                  <div class="flex flex-col">
                    <span class="text-sm font-bold text-slate-200">{{ topic.topicName }}</span>
                    <span v-if="searchMode === 'global' && topic.userId && topic.userId !== authStore.user?.id" class="text-[9px] text-slate-600 bg-black/30 px-1.5 py-0.5 rounded border border-white/5 mt-0.5 w-max">UID:{{ topic.userId }}</span>
                  </div>
                </div>
              </td>
              <td class="px-6 py-4">
                <span class="rounded bg-black/30 px-2 py-1 font-mono text-xs text-slate-400">{{ topic.sortOrder }}</span>
              </td>
              <td class="px-6 py-4">
                <div class="flex items-center space-x-2">
                  <span
                    class="inline-flex items-center rounded-md border px-2 py-1 text-[10px] font-bold uppercase tracking-wider"
                    :class="getStatusInfo(topic.isPass).cls"
                  >
                    {{ getStatusInfo(topic.isPass).label }}
                  </span>
                  <div v-if="topic.isPass === 2" class="group/tooltip relative flex items-center">
                    <Info class="h-4 w-4 cursor-help text-rose-400" />
                    <div
                      class="pointer-events-none absolute left-1/2 top-full z-20 mt-2 w-44 -translate-x-1/2 scale-95 rounded-xl border border-rose-500/20 bg-slate-950/95 px-3 py-2 text-[11px] leading-5 text-rose-100 opacity-0 shadow-[0_14px_40px_rgba(15,23,42,0.45)] transition-all duration-200 ease-out group-hover/tooltip:scale-100 group-hover/tooltip:opacity-100"
                    >
                      该主题审核未通过，可修改后重新提交审核。
                    </div>
                  </div>
                </div>
              </td>
              <td class="px-6 py-4 text-xs text-slate-500">
                {{ formatDate(topic.createTime) }}
              </td>
              <td class="px-6 py-4 text-right">
                <div class="flex items-center justify-end space-x-2 translate-x-1 opacity-0 transition-all duration-200 group-hover:translate-x-0 group-hover:opacity-100">
                  <button
                    v-if="topic.isPass === 0"
                    class="flex items-center space-x-1 rounded border border-orange-500/20 bg-orange-500/10 px-2 py-1 text-[10px] font-bold uppercase text-orange-400 transition-colors hover:bg-orange-500/20"
                    title="撤销审核"
                    @click="handleCancelAudit(topic.id)"
                  >
                    <CornerUpLeft class="h-3 w-3" />
                    <span>撤销</span>
                  </button>
                  <button
                    v-if="topic.isPass !== 1"
                    class="flex items-center space-x-1 rounded border border-teal-500/20 bg-teal-500/10 px-2 py-1 text-[10px] font-bold uppercase text-teal-400 transition-colors hover:bg-teal-500/20"
                    title="提交审核"
                    @click="handleSubmitAudit(topic.id)"
                  >
                    <Send class="h-3 w-3" />
                    <span>送审</span>
                  </button>
                  <button
                    class="flex h-7 w-7 items-center justify-center rounded bg-white/5 text-slate-400 transition-colors hover:bg-indigo-500/20 hover:text-indigo-400"
                    title="编辑排序"
                    @click="openEditModal(topic)"
                  >
                    <Pencil class="h-3.5 w-3.5" />
                  </button>
                  <button
                    class="flex h-7 w-7 items-center justify-center rounded bg-white/5 text-slate-400 transition-colors hover:bg-rose-500/20 hover:text-rose-400"
                    title="删除"
                    @click="handleDelete(topic.id)"
                  >
                    <Trash2 class="h-3.5 w-3.5" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div
        v-if="!loading && topicList.length > 0"
        class="flex items-center justify-between border-t border-white/5 bg-white/[0.01] px-6 py-4"
      >
        <span class="text-xs text-slate-500">
          共 {{ formatNumber(total) }} 条记录
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
                ? 'border border-indigo-500/30 bg-indigo-500/20 text-indigo-400'
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
            <div class="pointer-events-none absolute -top-10 -right-10 h-32 w-32 rounded-full bg-indigo-500/20 blur-[40px]"></div>

            <div class="mb-6 flex items-center justify-between">
              <h3 class="text-xl font-bold text-white">
                {{ modalMode === 'add' ? '新建主题' : '编辑主题' }}
              </h3>
              <button class="text-slate-500 transition-colors hover:text-white" @click="closeModal">
                <X class="h-5 w-5" />
              </button>
            </div>

            <form class="space-y-5" @submit.prevent="handleSubmit">
              <div>
                <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-slate-400">
                  主题名称 <span v-if="modalMode === 'add'" class="text-rose-500">*</span>
                </label>
                <div class="group relative">
                  <div class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-500 transition-colors group-focus-within:text-indigo-400">
                    <Layers class="h-[18px] w-[18px]" />
                  </div>
                  <input
                    v-model="formTopicName"
                    type="text"
                    placeholder="例如：前端架构"
                    :disabled="modalMode === 'edit'"
                    :required="modalMode === 'add'"
                    maxlength="25"
                    class="w-full rounded-xl border border-white/[0.05] bg-black/20 py-3 pl-10 pr-4 text-sm text-white shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] outline-none transition-all placeholder:text-slate-600 focus:border-indigo-500/50 focus:bg-black/40 focus:ring-2 focus:ring-indigo-500/10 disabled:cursor-not-allowed disabled:opacity-50"
                  />
                </div>
              </div>

              <div>
                <label class="mb-2 block text-xs font-bold uppercase tracking-widest text-slate-400">排序等级</label>
                <div class="group relative">
                  <div class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-500 transition-colors group-focus-within:text-indigo-400">
                    <ArrowUpDown class="h-[18px] w-[18px]" />
                  </div>
                  <input
                    v-model.number="formSortOrder"
                    type="number"
                    placeholder="默认: 0 (数值越大越靠前)"
                    class="w-full rounded-xl border border-white/[0.05] bg-black/20 py-3 pl-10 pr-4 text-sm text-white shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] outline-none transition-all placeholder:text-slate-600 focus:border-indigo-500/50 focus:bg-black/40 focus:ring-2 focus:ring-indigo-500/10"
                  />
                </div>
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
                  class="flex items-center space-x-2 rounded-xl bg-indigo-600 px-5 py-2.5 text-sm font-bold text-white shadow-[0_0_15px_rgba(99,102,241,0.4)] transition-all hover:bg-indigo-500"
                  :disabled="submitting"
                >
                  <Loader2 v-if="submitting" class="h-4 w-4 animate-spin" />
                  <span>{{ submitting ? '保存中...' : '确认保存' }}</span>
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
  background: #6366f1;
  border-color: #6366f1;
  box-shadow: 0 0 10px rgba(99, 102, 241, 0.4);
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
  background: linear-gradient(90deg, rgba(129, 140, 248, 0.15), rgba(232, 121, 249, 0.15), rgba(56, 189, 248, 0.15), rgba(129, 140, 248, 0.15));
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
  border-color: rgba(232, 121, 249, 0.4);
  box-shadow: 0 0 15px rgba(232, 121, 249, 0.2), inset 0 0 10px rgba(129, 140, 248, 0.1);
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
