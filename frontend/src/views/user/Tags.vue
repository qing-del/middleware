<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { tagApi } from '@/api/tags'
import type { TagItem } from '@/api/tags'
import {
  Tags, Plus, Search, Globe, Hash, Send, Info, Loader2,
  X, ChevronLeft, ChevronRight, Trash2
} from 'lucide-vue-next'

// ── 响应式状态 ────────────────────────────────────
const loading = ref(true)
const tagList = ref<TagItem[]>([])
const total = ref(0)
const searchKeyword = ref('')
const searchMode = ref<'personal' | 'global'>('personal')
const currentPage = ref(1)
const pageSize = ref(15)
const selectedIds = ref<Set<number>>(new Set())

// 模态框
const modalVisible = ref(false)
const formTagName = ref('')
const submitting = ref(false)

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
async function fetchTags() {
  try {
    const res = await tagApi.getList({
      keyword: searchKeyword.value || undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    })
    tagList.value = res.records ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

// ── 事件处理 ──────────────────────────────────────
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
}

// ---- 选择逻辑 ----
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

// ---- 模态框逻辑 ----
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
    alert('标签名称不能超过 20 个字符')
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

// ---- 删除逻辑 ----
async function handleDelete(id: number) {
  if (!confirm('确定删除该标签吗？')) return
  await tagApi.deleteTags([id])
  selectedIds.value.delete(id)
  await fetchTags()
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) return
  if (!confirm(`确定要删除已选择的 ${selectedIds.value.size} 个标签吗？`)) return
  await tagApi.deleteTags([...selectedIds.value])
  selectedIds.value.clear()
  await fetchTags()
}

// ---- 审核提交 ----
async function handleSubmitAudit(id: number) {
  if (!confirm('确认提交该标签进行审核吗？')) return
  await tagApi.submitAudit(id)
  await fetchTags()
}

// ── 生命周期 ──────────────────────────────────────
onMounted(() => {
  fetchTags()
})
</script>

<template>
  <div class="relative max-w-6xl mx-auto space-y-6">
    <!-- 环境光晕 -->
    <div class="fixed top-[-10%] right-[-5%] w-[500px] h-[500px] bg-purple-600/10 blur-[150px] rounded-full pointer-events-none z-0"></div>
    <div class="fixed bottom-[-10%] left-[-5%] w-[400px] h-[400px] bg-violet-600/10 blur-[120px] rounded-full pointer-events-none z-0"></div>

    <!-- 页面头部操作栏 -->
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 relative z-10">
      <div class="flex items-center space-x-3">
        <div class="p-2 rounded-xl bg-purple-500/10 border border-purple-500/20 text-purple-400">
          <Tags class="w-5 h-5" />
        </div>
        <div>
          <h2 class="text-xl font-bold text-white">标签矩阵控制台</h2>
          <p class="text-xs text-slate-400 mt-0.5">构建和管理您的原子化标签维度</p>
        </div>
      </div>

      <div class="flex items-center space-x-4">
        <!-- 全局搜索 Toggle 开关 -->
        <div
          class="global-search-btn flex items-center space-x-2 bg-white/[0.02] border border-white/5 px-3 py-1.5 rounded-xl cursor-pointer transition-colors select-none"
          :class="{ 'is-active': searchMode === 'global' }"
          @click="toggleGlobalSearch"
        >
          <Globe
            class="w-3.5 h-3.5 transition-colors relative z-10"
            :class="searchMode === 'global' ? 'text-purple-400' : 'text-slate-500'"
          />
          <span
            class="text-xs font-bold transition-colors whitespace-nowrap relative z-10"
            :class="searchMode === 'global' ? 'text-purple-300' : 'text-slate-400'"
          >
            {{ searchMode === 'global' ? '全局搜索' : '个人生态' }}
          </span>
          <div
            class="relative inline-flex h-4 w-7 items-center rounded-full border transition-colors ml-1"
            :class="searchMode === 'global'
              ? 'bg-purple-500 border-purple-500/50'
              : 'bg-black/50 border-white/10'"
          >
            <span
              class="inline-block h-3 w-3 transform rounded-full transition-transform"
              :class="searchMode === 'global'
                ? 'translate-x-3.5 bg-white'
                : 'translate-x-0.5 bg-slate-400'"
            ></span>
          </div>
        </div>

        <!-- 搜索框 (折叠展开式) -->
        <div class="relative group flex items-center bg-black/20 border border-white/10 rounded-xl overflow-hidden transition-all duration-300 ease-out w-9 hover:w-28 focus-within:!w-64 focus-within:bg-black/40 focus-within:border-purple-500/50 focus-within:ring-2 focus-within:ring-purple-500/10 h-9">
          <label for="tag-search" class="w-9 h-full flex-shrink-0 flex items-center justify-center text-slate-500 group-hover:text-slate-300 group-focus-within:text-purple-400 transition-colors cursor-pointer z-10">
            <Search class="w-4 h-4" />
          </label>
          <input
            id="tag-search"
            v-model="searchKeyword"
            type="text"
            placeholder="检索特定标签..."
            class="absolute left-9 w-[220px] h-full bg-transparent text-sm text-white placeholder:text-slate-500 outline-none opacity-0 group-hover:opacity-100 focus-within:!opacity-100 transition-opacity duration-300 pr-4"
            @keyup.enter="handleSearch"
          />
        </div>

        <!-- 新建按钮 -->
        <button
          class="group relative px-4 py-2 bg-purple-600 hover:bg-purple-500 text-white text-sm font-bold rounded-xl shadow-[0_0_15px_rgba(139,92,246,0.4)] transition-all overflow-hidden flex items-center space-x-2"
          @click="openAddModal"
        >
          <div class="absolute inset-0 bg-[linear-gradient(to_right,transparent,rgba(255,255,255,0.2),transparent)] -translate-x-[150%] group-hover:translate-x-[150%] transition-transform duration-700 ease-out"></div>
          <Plus class="w-4 h-4 relative z-10" />
          <span class="relative z-10">新建标签</span>
        </button>
      </div>
    </div>

    <!-- 批量操作悬浮条 -->
    <div
      class="glass-panel rounded-xl px-4 py-3 flex items-center justify-between transition-all duration-300 relative z-10"
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
          已选择 <span class="text-white mx-1">{{ selectedIds.size }}</span> 个原子标签
        </span>
      </div>
      <button
        class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-rose-500/10 text-rose-400 hover:bg-rose-500 hover:text-white transition-all text-xs font-bold border border-rose-500/20 hover:shadow-[0_0_15px_rgba(244,63,94,0.4)]"
        @click="handleBatchDelete"
      >
        <Trash2 class="w-3.5 h-3.5" />
        <span>批量删除</span>
      </button>
    </div>

    <!-- 数据表格容器 -->
    <div class="glass-panel rounded-2xl overflow-hidden border border-white/10 relative z-10">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr>
              <th class="px-6 py-4 border-b border-white/5 w-10">
                <input
                  type="checkbox"
                  class="glass-checkbox"
                  :checked="selectedIds.size === tagList.length && tagList.length > 0"
                  @change="toggleSelectAll(($event.target as HTMLInputElement).checked)"
                />
              </th>
              <th class="px-6 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">原子标签</th>
              <th class="px-6 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">审核状态</th>
              <th class="px-6 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">确立时间</th>
              <th class="px-6 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider text-right">操作控制台</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <!-- 加载中 -->
            <tr v-if="loading">
              <td colspan="5" class="px-6 py-16 text-center">
                <div class="flex flex-col items-center space-y-3">
                  <Loader2 class="w-6 h-6 text-purple-400 animate-spin" />
                  <span class="text-xs text-slate-500">加载中...</span>
                </div>
              </td>
            </tr>

            <!-- 空数据 -->
            <tr v-else-if="tagList.length === 0">
              <td colspan="5" class="px-6 py-16 text-center">
                <div class="flex flex-col items-center space-y-3">
                  <Tags class="w-8 h-8 text-slate-600" />
                  <p class="text-sm text-slate-500">暂无原子标签数据</p>
                  <button
                    class="text-xs font-bold text-purple-400 hover:text-purple-300 transition-colors"
                    @click="openAddModal"
                  >
                    立即定义第一个标签
                  </button>
                </div>
              </td>
            </tr>

            <!-- 数据行 -->
            <tr
              v-for="tag in tagList"
              :key="tag.id"
              class="hover:bg-white/[0.02] transition-colors group"
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
                <!-- 胶囊徽章 -->
                <div class="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-full border border-white/10 tag-badge w-max">
                  <Hash class="w-3.5 h-3.5 text-purple-400" />
                  <span class="text-sm font-bold text-slate-200">{{ tag.tagName }}</span>
                </div>
              </td>
              <td class="px-6 py-4">
                <div class="flex items-center space-x-2">
                  <span
                    class="inline-flex items-center px-2 py-1 rounded-md text-[10px] font-bold uppercase tracking-wider border"
                    :class="getStatusInfo(tag.isPass).cls"
                  >
                    {{ getStatusInfo(tag.isPass).label }}
                  </span>
                  <Info
                    v-if="tag.isPass === 2"
                    class="w-4 h-4 text-rose-400 cursor-help"
                    title="该标签审核未通过，可修改后重新提交审核"
                  />
                </div>
              </td>
              <td class="px-6 py-4 text-xs text-slate-500">
                {{ formatDate(tag.createTime) }}
              </td>
              <td class="px-6 py-4 text-right">
                <div class="flex items-center justify-end space-x-2 opacity-50 group-hover:opacity-100 transition-opacity">
                  <!-- 申审按钮 (仅未通过审核时显示) -->
                  <button
                    v-if="tag.isPass !== 1"
                    class="flex items-center space-x-1 px-2 py-1 rounded bg-teal-500/10 hover:bg-teal-500/20 text-teal-400 border border-teal-500/20 transition-colors text-[10px] font-bold uppercase"
                    title="提交审核"
                    @click="handleSubmitAudit(tag.id)"
                  >
                    <Send class="w-3 h-3" />
                    <span>申审</span>
                  </button>
                  <!-- 删除按钮 -->
                  <button
                    class="w-7 h-7 rounded bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-colors"
                    title="删除"
                    @click="handleDelete(tag.id)"
                  >
                    <Trash2 class="w-3.5 h-3.5" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页组件 -->
      <div
        v-if="!loading && tagList.length > 0"
        class="px-6 py-4 border-t border-white/5 flex items-center justify-between bg-white/[0.01]"
      >
        <span class="text-xs text-slate-500">
          发现 {{ formatNumber(total) }} 条原子标签
        </span>
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
    </div>

    <!-- 新建标签 Modal -->
    <Teleport to="body">
      <div
        v-if="modalVisible"
        class="fixed inset-0 z-50 flex items-center justify-center"
      >
        <div
          class="absolute inset-0 bg-black/60 backdrop-blur-sm"
          @click="closeModal"
        ></div>

        <div
          class="glass-panel w-full max-w-md rounded-3xl p-8 relative z-10 transform transition-all duration-300"
          :class="modalVisible ? 'scale-100' : 'scale-95'"
        >
          <div class="absolute -top-10 -right-10 w-32 h-32 bg-purple-500/20 blur-[40px] rounded-full pointer-events-none"></div>

          <div class="flex justify-between items-center mb-6">
            <h3 class="text-xl font-bold text-white">定义原子标签</h3>
            <button class="text-slate-500 hover:text-white transition-colors" @click="closeModal">
              <X class="w-5 h-5" />
            </button>
          </div>

          <form class="space-y-5" @submit.prevent="handleSubmit">
            <div>
              <label class="block text-xs font-bold text-slate-400 uppercase tracking-widest mb-2">
                标签名称 <span class="text-rose-500">*</span>
              </label>
              <div class="relative group">
                <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-500 group-focus-within:text-purple-400 transition-colors">
                  <Hash class="w-[18px] h-[18px]" />
                </div>
                <input
                  v-model="formTagName"
                  type="text"
                  placeholder="例如：前端架构"
                  required
                  maxlength="20"
                  class="w-full bg-black/20 border border-white/[0.05] shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] rounded-xl py-3 pl-10 pr-4 outline-none focus:bg-black/40 focus:border-purple-500/50 focus:ring-2 focus:ring-purple-500/10 transition-all text-sm text-white placeholder:text-slate-600"
                />
              </div>
              <p class="text-[10px] text-slate-500 mt-2">标签需要通过审核才能在公网生效</p>
            </div>

            <div class="pt-4 flex justify-end space-x-3">
              <button
                type="button"
                class="px-5 py-2.5 rounded-xl text-sm font-bold text-slate-400 hover:text-white hover:bg-white/5 transition-colors"
                @click="closeModal"
              >
                取消
              </button>
              <button
                type="submit"
                class="px-5 py-2.5 bg-purple-600 hover:bg-purple-500 text-white text-sm font-bold rounded-xl shadow-[0_0_15px_rgba(139,92,246,0.4)] transition-all flex items-center space-x-2"
                :disabled="submitting"
              >
                <Loader2 v-if="submitting" class="w-4 h-4 animate-spin" />
                <span>{{ submitting ? '创建中...' : '确认创建' }}</span>
              </button>
            </div>
          </form>
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

/* 标签胶囊徽章 */
.tag-badge {
  background: linear-gradient(145deg, rgba(255,255,255,0.05) 0%, rgba(255,255,255,0.01) 100%);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.1);
}

/* 全局搜索按钮流动彩虹特效 (紫色主题) */
@keyframes shine {
  to { background-position: 200% center; }
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
  background: linear-gradient(90deg, rgba(139,92,246,0.15), rgba(168,85,247,0.15), rgba(59,130,246,0.15), rgba(139,92,246,0.15));
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
</style>
