<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import type { AdminTopicItem, PageResult } from '@/api/admin'
import { Layers, Search, Trash2, Loader2, ChevronLeft, ChevronRight, Info } from 'lucide-vue-next'

const loading = ref(true)
const topicList = ref<AdminTopicItem[]>([])
const total = ref(0)
const searchKeyword = ref('')
const filterUserId = ref('')
const sortBy = ref('')
const currentPage = ref(1)
const pageSize = ref(15)
const selectedIds = ref<Set<number>>(new Set())

// User nickname cache
const userNicknameCache = ref<Map<number, string>>(new Map())

function getUserDisplayName(userId: number): string {
  const nick = userNicknameCache.value.get(userId)
  return nick ? `${nick} (UID:${userId})` : `UID:${userId}`
}

async function prefetchUserNicknames() {
  const ids = [...new Set(topicList.value.map(t => t.userId).filter(Boolean) as number[])]
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

const isBatchMode = computed(() => selectedIds.value.size > 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function getStatusInfo(isPass: number) {
  switch (isPass) {
    case 1: return { label: '已通过', cls: 'text-emerald-300 bg-emerald-500/10 border-emerald-500/20' }
    case 2: return { label: '已拒绝', cls: 'text-rose-300 bg-rose-500/10 border-rose-500/20' }
    default: return { label: '待审核', cls: 'text-amber-300 bg-amber-500/10 border-amber-500/20' }
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
    const res = await adminApi.getTopicList({
      keyword: searchKeyword.value || undefined,
      userId: filterUserId.value ? Number(filterUserId.value) : undefined,
      sortBy: sortBy.value || undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    })
    topicList.value = (res as unknown as PageResult<AdminTopicItem>).records ?? []
    total.value = (res as unknown as PageResult<AdminTopicItem>).total ?? 0
    if (topicList.value.length > 0) void prefetchUserNicknames()
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

function toggleSelectAll(checked: boolean) {
  if (checked) topicList.value.forEach(topic => selectedIds.value.add(topic.id))
  else selectedIds.value.clear()
}

function toggleSelect(id: number) {
  selectedIds.value.has(id) ? selectedIds.value.delete(id) : selectedIds.value.add(id)
}

async function handleDelete(id: number) {
  if (!confirm('确定删除该主题吗？')) return
  await adminApi.deleteTopics([id])
  await fetchTopics()
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) return
  if (!confirm(`确定删除已选择的 ${selectedIds.value.size} 个主题吗？`)) return
  await adminApi.deleteTopics([...selectedIds.value])
  selectedIds.value.clear()
  await fetchTopics()
}

onMounted(() => {
  fetchTopics()
})
</script>

<template>
  <div class="relative mx-auto max-w-6xl space-y-6">
    <div class="relative z-10 flex flex-col justify-between gap-4 md:flex-row md:items-center">
      <div class="flex items-center space-x-3">
        <div class="rounded-xl border border-cyan-400/20 bg-cyan-400/10 p-2 text-cyan-300"><Layers class="h-5 w-5" /></div>
        <div>
          <h2 class="text-xl font-bold text-white">主题调度</h2>
          <p class="mt-0.5 text-xs text-slate-400">管理全局用户主题资产与审核状态</p>
        </div>
      </div>
      <div class="flex items-center space-x-3">
        <input v-model="filterUserId" type="number" placeholder="用户 ID..." class="admin-input w-28" @keyup.enter="handleSearch" />
        <input v-model="sortBy" placeholder="排序字段..." class="admin-input w-24" />
        <div class="group relative flex h-9 w-9 items-center overflow-hidden rounded-xl border border-white/10 bg-black/20 transition-all duration-300 ease-out hover:w-28 focus-within:!w-64 focus-within:border-cyan-400/50 focus-within:bg-black/40 focus-within:ring-2 focus-within:ring-cyan-400/10">
          <label class="z-10 flex h-full w-9 flex-shrink-0 cursor-pointer items-center justify-center text-slate-500 transition-colors group-hover:text-slate-300 group-focus-within:text-cyan-300"><Search class="h-4 w-4" /></label>
          <input v-model="searchKeyword" type="text" placeholder="搜索主题名称..." class="absolute left-9 h-full w-[220px] bg-transparent pr-4 text-sm text-white opacity-0 outline-none transition-opacity duration-300 group-hover:opacity-100 focus-within:!opacity-100 placeholder:text-slate-500" @keyup.enter="handleSearch" />
        </div>
      </div>
    </div>

    <Transition name="batch-float">
      <div v-if="isBatchMode" class="glass-panel relative z-10 flex items-center justify-between rounded-xl px-4 py-3">
        <span class="text-sm font-bold text-cyan-200">已选择 <span class="mx-1 text-white">{{ selectedIds.size }}</span> 个主题</span>
        <button class="flex items-center space-x-1.5 rounded-lg border border-rose-500/20 bg-rose-500/10 px-3 py-1.5 text-xs font-bold text-rose-300 transition-all hover:bg-rose-500 hover:text-white" @click="handleBatchDelete"><Trash2 class="h-3.5 w-3.5" /><span>批量删除</span></button>
      </div>
    </Transition>

    <div class="glass-panel relative z-10 overflow-hidden rounded-2xl border border-white/10">
      <div class="overflow-x-auto">
        <table class="w-full border-collapse text-left">
          <thead><tr>
            <th class="w-10 border-b border-white/5 px-4 py-4"><input type="checkbox" class="glass-checkbox" :checked="selectedIds.size === topicList.length && topicList.length > 0" @change="toggleSelectAll(($event.target as HTMLInputElement).checked)" /></th>
            <th class="border-b border-white/5 px-4 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">ID</th>
            <th class="border-b border-white/5 px-4 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">主题名称</th>
            <th class="border-b border-white/5 px-4 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">排序</th>
            <th class="border-b border-white/5 px-4 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">状态</th>
            <th class="border-b border-white/5 px-4 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">创建时间</th>
            <th class="border-b border-white/5 px-4 py-4 text-right text-xs font-bold uppercase tracking-wider text-slate-400">操作</th>
          </tr></thead>
          <tbody class="divide-y divide-white/5">
            <tr v-if="loading"><td colspan="7" class="px-6 py-16 text-center"><Loader2 class="mx-auto mb-3 h-6 w-6 animate-spin text-cyan-300" /><span class="text-xs text-slate-500">加载中...</span></td></tr>
            <tr v-else-if="topicList.length === 0"><td colspan="7" class="px-6 py-16 text-center text-sm text-slate-500">暂无主题数据</td></tr>
            <TransitionGroup v-else name="list">
            <tr v-for="topic in topicList" :key="topic.id" class="group transition-colors duration-200 hover:bg-white/5">
              <td class="px-4 py-4"><input type="checkbox" class="glass-checkbox" :checked="selectedIds.has(topic.id)" @change="toggleSelect(topic.id)" /></td>
              <td class="px-4 py-4 font-mono text-xs text-slate-500">{{ topic.id }}</td>
              <td class="px-4 py-4">
                <div class="flex flex-col">
                  <span class="text-sm font-bold text-slate-200">{{ topic.topicName }}</span>
                  <span v-if="topic.userId" class="text-[10px] text-slate-500 mt-0.5">{{ getUserDisplayName(topic.userId) }}</span>
                </div>
              </td>
              <td class="px-4 py-4"><span class="rounded bg-black/30 px-2 py-1 font-mono text-xs text-slate-400">{{ topic.sortOrder }}</span></td>
              <td class="px-4 py-4">
                <div class="flex items-center space-x-2">
                  <span class="inline-flex items-center rounded-md border px-2 py-1 text-[10px] font-bold uppercase tracking-wider" :class="getStatusInfo(topic.isPass).cls">{{ getStatusInfo(topic.isPass).label }}</span>
                  <div v-if="topic.isPass === 2" class="group/tooltip relative flex items-center">
                    <Info class="h-4 w-4 cursor-help text-rose-300" />
                    <div class="pointer-events-none absolute left-1/2 top-full z-20 mt-2 w-40 -translate-x-1/2 scale-95 rounded-xl border border-rose-500/20 bg-slate-950/95 px-3 py-2 text-[11px] leading-5 text-rose-100 opacity-0 shadow-[0_14px_40px_rgba(15,23,42,0.45)] transition-all duration-200 ease-out group-hover/tooltip:scale-100 group-hover/tooltip:opacity-100">
                      该主题审核未通过。
                    </div>
                  </div>
                </div>
              </td>
              <td class="px-4 py-4 text-xs text-slate-500">{{ formatDate(topic.createTime) }}</td>
              <td class="px-4 py-4 text-right">
                <div class="flex items-center justify-end space-x-2 translate-x-1 opacity-0 transition-all duration-200 group-hover:translate-x-0 group-hover:opacity-100">
                  <button class="flex h-7 w-7 items-center justify-center rounded bg-white/5 text-slate-400 transition-colors hover:bg-rose-500/20 hover:text-rose-300" title="删除" @click="handleDelete(topic.id)"><Trash2 class="h-3.5 w-3.5" /></button>
                </div>
              </td>
            </tr>
            </TransitionGroup>
          </tbody>
        </table>
      </div>
      <div v-if="!loading && topicList.length > 0" class="flex items-center justify-between border-t border-white/5 bg-white/[0.01] px-6 py-4">
        <span class="text-xs text-slate-500">共 {{ formatNumber(total) }} 条记录</span>
        <div class="flex items-center space-x-1">
          <button class="flex h-7 w-7 items-center justify-center rounded text-slate-500 hover:bg-white/5 hover:text-white disabled:opacity-50" :disabled="currentPage <= 1" @click="handlePageChange(currentPage - 1)"><ChevronLeft class="h-4 w-4" /></button>
          <template v-for="page in visiblePages()" :key="page"><button v-if="totalPages > 1" class="flex h-7 w-7 items-center justify-center rounded text-xs font-bold transition-colors" :class="page === currentPage ? 'border border-cyan-400/30 bg-cyan-400/15 text-cyan-200' : 'text-slate-400 hover:bg-white/5 hover:text-white'" @click="handlePageChange(page)">{{ page }}</button></template>
          <button class="flex h-7 w-7 items-center justify-center rounded text-slate-500 hover:bg-white/5 hover:text-white" :disabled="currentPage >= totalPages" @click="handlePageChange(currentPage + 1)"><ChevronRight class="h-4 w-4" /></button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.glass-panel { background: rgba(255,255,255,0.02); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.05); box-shadow: inset 0 1px 1px rgba(255,255,255,0.05); }
.glass-checkbox { appearance: none; width: 16px; height: 16px; border: 1px solid rgba(255,255,255,0.2); border-radius: 4px; background: rgba(0,0,0,0.2); cursor: pointer; position: relative; transition: all 0.2s; }
.glass-checkbox:checked { background: #22d3ee; border-color: #22d3ee; box-shadow: 0 0 10px rgba(34,211,238,0.35); }
.glass-checkbox:checked::after { content: ''; position: absolute; left: 5px; top: 2px; width: 4px; height: 8px; border: solid white; border-width: 0 2px 2px 0; transform: rotate(45deg); }
.admin-input { height: 36px; border-radius: 0.75rem; border: 1px solid rgba(255,255,255,0.1); background: rgba(0,0,0,0.2); padding: 0 0.75rem; color: white; font-size: 0.75rem; outline: none; transition: border-color 0.2s ease, background-color 0.2s ease; }
.admin-input::placeholder { color: rgb(100 116 139); }
.admin-input:focus { border-color: rgba(34,211,238,0.5); background: rgba(0,0,0,0.35); }

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

.list-enter-active,
.list-leave-active { transition: all 0.4s ease; }
.list-enter-from { opacity: 0; transform: translateY(20px); }
.list-leave-to { opacity: 0; transform: translateX(30px); }
.list-move { transition: transform 0.4s ease; }

@media (prefers-reduced-motion: reduce) {
  .list-enter-active,
  .list-leave-active,
  .list-move,
  .batch-float-enter-active,
  .batch-float-leave-active { transition-duration: 0.01s !important; }
  .list-enter-from,
  .list-leave-to { opacity: 0; transform: none; }
}
</style>
