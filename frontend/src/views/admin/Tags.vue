<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import type { AdminTagItem, PageResult } from '@/api/admin'
import { Hash, Search, Trash2, Loader2, ChevronLeft, ChevronRight, Pencil } from 'lucide-vue-next'

const loading = ref(true)
const tagList = ref<AdminTagItem[]>([])
const total = ref(0)
const searchKeyword = ref('')
const filterUserId = ref('')
const currentPage = ref(1)
const pageSize = ref(15)
const selectedIds = ref<Set<number>>(new Set())

// 修改标签名
const editingTagId = ref<number | null>(null)
const editingTagName = ref('')

const isBatchMode = computed(() => selectedIds.value.size > 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function getStatusInfo(isPass: number) {
  switch (isPass) {
    case 1: return { label: '已通过', cls: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20' }
    case 2: return { label: '已拒绝', cls: 'text-rose-400 bg-rose-500/10 border-rose-500/20' }
    default: return { label: '待审核', cls: 'text-amber-400 bg-amber-500/10 border-amber-500/20' }
  }
}
function formatDate(raw: string): string {
  if (!raw) return '-'
  const d = new Date(raw); const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function formatNumber(n: number): string { return n.toLocaleString() }
function visiblePages(): number[] {
  const pages: number[] = []; const tp = totalPages.value; const cp = currentPage.value
  let start = Math.max(1, cp-2); const end = Math.min(tp, start+4)
  if (end-start < 4) start = Math.max(1, end-4)
  for (let i = start; i <= end; i++) pages.push(i); return pages
}

async function fetchTags() {
  try {
    const res = await adminApi.getTagList({
      keyword: searchKeyword.value || undefined,
      userId: filterUserId.value ? Number(filterUserId.value) : undefined,
      pageNum: currentPage.value, pageSize: pageSize.value
    })
    tagList.value = (res as unknown as PageResult<AdminTagItem>).records ?? []
    total.value = (res as unknown as PageResult<AdminTagItem>).total ?? 0
  } finally { loading.value = false }
}
function handleSearch() { currentPage.value = 1; loading.value = true; fetchTags() }
function handlePageChange(page: number) { if (page < 1 || page > totalPages.value || page === currentPage.value) return; currentPage.value = page; loading.value = true; fetchTags() }
function toggleSelectAll(checked: boolean) { if (checked) tagList.value.forEach(t => selectedIds.value.add(t.id)); else selectedIds.value.clear() }
function toggleSelect(id: number) { selectedIds.value.has(id) ? selectedIds.value.delete(id) : selectedIds.value.add(id) }

function startEdit(tag: AdminTagItem) { editingTagId.value = tag.id; editingTagName.value = tag.tagName }
async function saveEdit() {
  if (!editingTagId.value || !editingTagName.value.trim()) return
  await adminApi.modifyTag({ id: editingTagId.value, tagName: editingTagName.value.trim() })
  editingTagId.value = null; editingTagName.value = ''; await fetchTags()
}
function cancelEdit() { editingTagId.value = null; editingTagName.value = '' }

async function handleDelete(id: number) { if (!confirm('确定删除该标签吗？')) return; await adminApi.deleteTags([id]); await fetchTags() }
async function handleBatchDelete() {
  if (selectedIds.value.size === 0) return
  if (!confirm(`确定删除已选择的 ${selectedIds.value.size} 个标签吗？`)) return
  await adminApi.deleteTags([...selectedIds.value]); selectedIds.value.clear(); await fetchTags()
}

onMounted(() => { fetchTags() })
</script>

<template>
  <div class="relative max-w-6xl mx-auto space-y-6">
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 relative z-10">
      <div class="flex items-center space-x-3">
        <div class="p-2 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400"><Hash class="w-5 h-5" /></div>
        <div><h2 class="text-xl font-bold text-white">标签矩阵</h2><p class="text-xs text-slate-400 mt-0.5">管理全局所有用户的标签资产</p></div>
      </div>
      <div class="flex items-center space-x-3">
        <input v-model="filterUserId" type="number" placeholder="用户ID过滤..." class="w-28 bg-black/20 border border-white/10 rounded-xl py-2 px-3 text-xs text-white placeholder:text-slate-500 outline-none focus:border-rose-500/50 transition-colors h-9" @keyup.enter="handleSearch" />
        <div class="relative group flex items-center bg-black/20 border border-white/10 rounded-xl overflow-hidden transition-all duration-300 ease-out w-9 hover:w-28 focus-within:!w-64 focus-within:bg-black/40 focus-within:border-rose-500/50 focus-within:ring-2 focus-within:ring-rose-500/10 h-9">
          <label class="w-9 h-full flex-shrink-0 flex items-center justify-center text-slate-500 group-hover:text-slate-300 group-focus-within:text-rose-400 transition-colors cursor-pointer z-10"><Search class="w-4 h-4" /></label>
          <input v-model="searchKeyword" type="text" placeholder="搜索标签名称..." class="absolute left-9 w-[220px] h-full bg-transparent text-sm text-white placeholder:text-slate-500 outline-none opacity-0 group-hover:opacity-100 focus-within:!opacity-100 transition-opacity duration-300 pr-4" @keyup.enter="handleSearch" />
        </div>
      </div>
    </div>

    <div class="glass-panel rounded-xl px-4 py-3 flex items-center justify-between transition-all duration-300 relative z-10"
      :class="isBatchMode ? 'opacity-100 pointer-events-auto translate-y-0' : 'opacity-0 pointer-events-none -translate-y-[10px]'">
      <div class="flex items-center space-x-3"><span class="flex h-2 w-2 relative"><span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-400 opacity-75"></span><span class="relative inline-flex rounded-full h-2 w-2 bg-rose-500"></span></span><span class="text-sm font-bold text-rose-300">已选择 <span class="text-white mx-1">{{ selectedIds.size }}</span> 个标签</span></div>
      <button class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-rose-500/10 text-rose-400 hover:bg-rose-500 hover:text-white transition-all text-xs font-bold border border-rose-500/20" @click="handleBatchDelete"><Trash2 class="w-3.5 h-3.5" /><span>批量删除</span></button>
    </div>

    <div class="glass-panel rounded-2xl overflow-hidden border border-white/10 relative z-10">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse">
          <thead><tr>
            <th class="px-4 py-4 border-b border-white/5 w-10"><input type="checkbox" class="glass-checkbox" :checked="selectedIds.size === tagList.length && tagList.length > 0" @change="toggleSelectAll(($event.target as HTMLInputElement).checked)" /></th>
            <th class="px-4 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">ID</th>
            <th class="px-4 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">标签名称</th>
            <th class="px-4 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">状态</th>
            <th class="px-4 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">创建时间</th>
            <th class="px-4 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider text-right">操作</th>
          </tr></thead>
          <tbody class="divide-y divide-white/5">
            <tr v-if="loading"><td colspan="6" class="px-6 py-16 text-center"><Loader2 class="w-6 h-6 text-rose-400 animate-spin mx-auto mb-3" /><span class="text-xs text-slate-500">加载中...</span></td></tr>
            <tr v-else-if="tagList.length === 0"><td colspan="6" class="px-6 py-16 text-center text-sm text-slate-500">暂无标签数据</td></tr>
            <tr v-for="tag in tagList" :key="tag.id" class="hover:bg-white/[0.02] transition-colors group">
              <td class="px-4 py-4"><input type="checkbox" class="glass-checkbox" :checked="selectedIds.has(tag.id)" @change="toggleSelect(tag.id)" /></td>
              <td class="px-4 py-4 text-xs text-slate-500 font-mono">{{ tag.id }}</td>
              <td class="px-4 py-4">
                <div v-if="editingTagId === tag.id" class="flex items-center space-x-2">
                  <input v-model="editingTagName" class="bg-black/20 border border-rose-500/50 rounded-lg py-1 px-2 text-sm text-white outline-none w-40" @keyup.enter="saveEdit" @keyup.escape="cancelEdit" />
                  <button class="text-xs text-emerald-400 hover:text-emerald-300" @click="saveEdit">保存</button>
                  <button class="text-xs text-slate-400 hover:text-white" @click="cancelEdit">取消</button>
                </div>
                <div v-else class="inline-flex items-center space-x-1.5 px-2.5 py-1 rounded-full border border-white/10 tag-badge w-max">
                  <Hash class="w-3.5 h-3.5 text-purple-400" /><span class="text-sm font-bold text-slate-200">{{ tag.tagName }}</span>
                </div>
              </td>
              <td class="px-4 py-4"><span class="inline-flex items-center px-2 py-1 rounded-md text-[10px] font-bold uppercase tracking-wider border" :class="getStatusInfo(tag.isPass).cls">{{ getStatusInfo(tag.isPass).label }}</span></td>
              <td class="px-4 py-4 text-xs text-slate-500">{{ formatDate(tag.createTime) }}</td>
              <td class="px-4 py-4 text-right">
                <div class="flex items-center justify-end space-x-2 opacity-50 group-hover:opacity-100 transition-opacity">
                  <button class="w-7 h-7 rounded bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-colors" title="编辑" @click="startEdit(tag)"><Pencil class="w-3.5 h-3.5" /></button>
                  <button class="w-7 h-7 rounded bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-colors" title="删除" @click="handleDelete(tag.id)"><Trash2 class="w-3.5 h-3.5" /></button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!loading && tagList.length > 0" class="px-6 py-4 border-t border-white/5 flex items-center justify-between bg-white/[0.01]">
        <span class="text-xs text-slate-500">共 {{ formatNumber(total) }} 条记录</span>
        <div class="flex items-center space-x-1">
          <button class="w-7 h-7 rounded flex items-center justify-center text-slate-500 hover:bg-white/5 hover:text-white disabled:opacity-50" :disabled="currentPage <= 1" @click="handlePageChange(currentPage - 1)"><ChevronLeft class="w-4 h-4" /></button>
          <template v-for="page in visiblePages()" :key="page"><button v-if="totalPages > 1" class="w-7 h-7 rounded flex items-center justify-center text-xs font-bold transition-colors" :class="page === currentPage ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30' : 'text-slate-400 hover:bg-white/5 hover:text-white'" @click="handlePageChange(page)">{{ page }}</button></template>
          <button class="w-7 h-7 rounded flex items-center justify-center text-slate-500 hover:bg-white/5 hover:text-white" :disabled="currentPage >= totalPages" @click="handlePageChange(currentPage + 1)"><ChevronRight class="w-4 h-4" /></button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.glass-panel { background: rgba(255,255,255,0.02); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.05); box-shadow: inset 0 1px 1px rgba(255,255,255,0.05); }
.glass-checkbox { appearance: none; width: 16px; height: 16px; border: 1px solid rgba(255,255,255,0.2); border-radius: 4px; background: rgba(0,0,0,0.2); cursor: pointer; position: relative; transition: all 0.2s; }
.glass-checkbox:checked { background: #f43f5e; border-color: #f43f5e; box-shadow: 0 0 10px rgba(244,63,94,0.4); }
.glass-checkbox:checked::after { content: ''; position: absolute; left: 5px; top: 2px; width: 4px; height: 8px; border: solid white; border-width: 0 2px 2px 0; transform: rotate(45deg); }
.tag-badge { background: linear-gradient(145deg, rgba(255,255,255,0.05) 0%, rgba(255,255,255,0.01) 100%); box-shadow: inset 0 1px 0 rgba(255,255,255,0.1); }
</style>
