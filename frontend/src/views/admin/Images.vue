<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import type { AdminImageItem, PageResult } from '@/api/admin'
import { Image, Search, Trash2, Loader2, X, ChevronLeft, ChevronRight, Globe, Eye, Upload } from 'lucide-vue-next'

const loading = ref(true)
const imageList = ref<AdminImageItem[]>([])
const total = ref(0)
const filterUserId = ref(''); const filterTopicId = ref(''); const searchFilename = ref('')
const filterIsPass = ref(''); const filterIsPublic = ref('')
const currentPage = ref(1); const pageSize = ref(12)
const selectedIds = ref<Set<number>>(new Set())
const previewUrl = ref(''); const showPreview = ref(false)

const isBatchMode = computed(() => selectedIds.value.size > 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function getStatusInfo(isPass: number) {
  switch (isPass) { case 1: return { label: '已通过', cls: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20' }; case 2: return { label: '已拒绝', cls: 'text-rose-400 bg-rose-500/10 border-rose-500/20' }; default: return { label: '待审核', cls: 'text-amber-400 bg-amber-500/10 border-amber-500/20' } }
}
function formatBytes(bytes: number): string { if (!bytes || bytes === 0) return '0 B'; const u = ['B','KB','MB','GB']; const i = Math.floor(Math.log(bytes)/Math.log(1024)); return (bytes/Math.pow(1024,i)).toFixed(i>0?1:0)+' '+u[i] }
function formatDate(raw: string): string { if (!raw) return '-'; const d = new Date(raw); const p = (n:number)=>String(n).padStart(2,'0'); return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}` }
function formatNumber(n: number): string { return n.toLocaleString() }
function visiblePages(): number[] { const p:number[]=[], tp=totalPages.value, cp=currentPage.value; let s=Math.max(1,cp-2); const e=Math.min(tp,s+4); if(e-s<4)s=Math.max(1,e-4); for(let i=s;i<=e;i++)p.push(i); return p }

async function fetchImages() {
  try {
    const res = await adminApi.getImageList({
      userId: filterUserId.value?Number(filterUserId.value):undefined, topicId: filterTopicId.value?Number(filterTopicId.value):undefined,
      filename: searchFilename.value||undefined, isPass: filterIsPass.value?Number(filterIsPass.value):undefined,
      isPublic: filterIsPublic.value?Number(filterIsPublic.value):undefined, pageNum: currentPage.value, pageSize: pageSize.value
    }); imageList.value = (res as unknown as PageResult<AdminImageItem>).records??[]; total.value = (res as unknown as PageResult<AdminImageItem>).total??0
  } finally { loading.value = false }
}
function handleSearch() { currentPage.value=1; loading.value=true; fetchImages() }
function handlePageChange(page: number) { if(page<1||page>totalPages.value||page===currentPage.value)return; currentPage.value=page; loading.value=true; fetchImages() }
function toggleSelect(id: number) { selectedIds.value.has(id)?selectedIds.value.delete(id):selectedIds.value.add(id) }
async function handleDelete(id: number) { if(!confirm('确定删除该图片吗？'))return; await adminApi.deleteImages([id]); await fetchImages() }
async function handleBatchDelete() { if(selectedIds.value.size===0)return; if(!confirm(`确定删除${selectedIds.value.size}张图片吗？`))return; await adminApi.deleteImages([...selectedIds.value]); selectedIds.value.clear(); await fetchImages() }
async function handleSetPublic(id: number, isPublic: number) { await adminApi.setImagePublic({id}, isPublic); await fetchImages() }
async function handleTransfer() { if(selectedIds.value.size===0)return; await adminApi.transferToCloud([...selectedIds.value]); selectedIds.value.clear(); await fetchImages() }

onMounted(() => { fetchImages() })
</script>

<template>
  <div class="relative max-w-[1400px] mx-auto space-y-6 pb-20">
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 relative z-10">
      <div class="flex items-center space-x-3">
        <div class="p-2 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400"><Image class="w-5 h-5" /></div>
        <div><h2 class="text-xl font-bold text-white">云端图床</h2><p class="text-xs text-slate-400 mt-0.5">管理全局所有用户的图片资产</p></div>
      </div>
      <div class="flex items-center space-x-2 flex-wrap gap-y-2">
        <input v-model="filterUserId" type="number" placeholder="UID..." class="w-20 bg-black/20 border border-white/10 rounded-xl py-2 px-2 text-xs text-white placeholder:text-slate-500 outline-none focus:border-rose-500/50 h-9" @keyup.enter="handleSearch" />
        <input v-model="filterTopicId" type="number" placeholder="主题ID..." class="w-20 bg-black/20 border border-white/10 rounded-xl py-2 px-2 text-xs text-white placeholder:text-slate-500 outline-none focus:border-rose-500/50 h-9" @keyup.enter="handleSearch" />
        <select v-model="filterIsPass" class="bg-black/20 border border-white/10 rounded-xl py-2 px-2 text-xs text-white outline-none focus:border-rose-500/50 h-9" @change="handleSearch">
          <option value="">审核:全部</option><option value="0">待审核</option><option value="1">已通过</option><option value="2">已拒绝</option>
        </select>
        <select v-model="filterIsPublic" class="bg-black/20 border border-white/10 rounded-xl py-2 px-2 text-xs text-white outline-none focus:border-rose-500/50 h-9" @change="handleSearch">
          <option value="">可见:全部</option><option value="0">私有</option><option value="1">公开</option>
        </select>
        <div class="relative group flex items-center bg-black/20 border border-white/10 rounded-xl overflow-hidden transition-all duration-300 ease-out w-9 hover:w-40 focus-within:!w-56 focus-within:bg-black/40 focus-within:border-rose-500/50 h-9">
          <label class="w-9 h-full flex-shrink-0 flex items-center justify-center text-slate-500 group-hover:text-slate-300 group-focus-within:text-rose-400 transition-colors cursor-pointer z-10"><Search class="w-4 h-4" /></label>
          <input v-model="searchFilename" type="text" placeholder="文件名..." class="absolute left-9 w-[180px] h-full bg-transparent text-sm text-white placeholder:text-slate-500 outline-none opacity-0 group-hover:opacity-100 focus-within:!opacity-100 transition-opacity duration-300 pr-4" @keyup.enter="handleSearch" />
        </div>
      </div>
    </div>

    <div class="glass-panel rounded-xl px-4 py-3 flex items-center justify-between transition-all duration-300 sticky top-0 z-30" :class="isBatchMode?'opacity-100':'opacity-0 pointer-events-none -translate-y-[10px]'">
      <div class="flex items-center space-x-3"><span class="flex h-2 w-2 relative"><span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-400 opacity-75"></span><span class="relative inline-flex rounded-full h-2 w-2 bg-rose-500"></span></span><span class="text-sm font-bold text-rose-300">已选取 <span class="text-white mx-1">{{ selectedIds.size }}</span> 张图片</span></div>
      <div class="flex items-center space-x-2">
        <button class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-teal-500/10 text-teal-400 hover:bg-teal-500 hover:text-white transition-all text-xs font-bold border border-teal-500/20" @click="handleTransfer"><Upload class="w-3.5 h-3.5" /><span>迁移云端</span></button>
        <button class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-rose-500/10 text-rose-400 hover:bg-rose-500 hover:text-white transition-all text-xs font-bold border border-rose-500/20" @click="handleBatchDelete"><Trash2 class="w-3.5 h-3.5" /><span>批量删除</span></button>
      </div>
    </div>

    <div v-if="loading" class="flex flex-col items-center justify-center py-24 space-y-3 relative z-10"><Loader2 class="w-8 h-8 text-rose-400 animate-spin" /><span class="text-xs text-slate-500">加载图库中...</span></div>
    <div v-else-if="imageList.length === 0" class="flex flex-col items-center justify-center py-24 space-y-4 relative z-10"><Image class="w-12 h-12 text-slate-600" /><p class="text-sm text-slate-500">暂无图片数据</p></div>
    <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6 relative z-10">
      <div v-for="img in imageList" :key="img.id" class="glass-panel glass-card rounded-2xl overflow-hidden relative group">
        <div class="absolute top-3 left-3 z-10 opacity-0 group-hover:opacity-100 transition-opacity"><input type="checkbox" class="glass-checkbox" :checked="selectedIds.has(img.id)" @change="toggleSelect(img.id)" /></div>
        <div class="h-40 w-full relative overflow-hidden bg-black/50">
          <img v-if="img.ossUrl" :src="img.ossUrl" class="w-full h-full object-cover transition-transform duration-700 group-hover:scale-110" />
          <div v-else class="w-full h-full flex items-center justify-center text-slate-600"><Image class="w-10 h-10" /></div>
          <div class="absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex items-center justify-center gap-2">
            <button v-if="img.ossUrl" class="p-2 bg-white/10 hover:bg-white/20 backdrop-blur-md rounded-full hover:scale-110 transition-all" title="预览" @click="previewUrl=img.ossUrl; showPreview=true"><Eye class="w-4 h-4 text-white" /></button>
            <button class="p-2 bg-rose-500/20 hover:bg-rose-500/40 border border-rose-500/30 backdrop-blur-md rounded-full hover:scale-110 transition-all" title="删除" @click="handleDelete(img.id)"><Trash2 class="w-4 h-4 text-rose-300" /></button>
          </div>
          <span class="absolute top-3 right-3 z-10 text-[10px] font-mono text-white bg-black/40 backdrop-blur-md border border-white/10 px-2 py-0.5 rounded-lg">{{ formatBytes(img.fileSize) }}</span>
        </div>
        <div class="p-3">
          <h3 class="text-xs font-bold text-white truncate" :title="img.filename">{{ img.filename || '未命名' }}</h3>
          <div class="flex items-center justify-between mt-2">
            <span class="text-[10px] text-slate-500">UID:{{ img.userId }} {{ formatDate(img.uploadTime||img.createTime) }}</span>
          </div>
          <div class="flex items-center justify-between mt-2">
            <span class="inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-bold uppercase tracking-wider border" :class="getStatusInfo(img.isPass).cls">{{ getStatusInfo(img.isPass).label }}</span>
            <button class="text-[10px] font-bold uppercase" :class="img.isPublic===1?'text-emerald-400':'text-slate-500'" @click="handleSetPublic(img.id, img.isPublic===1?0:1)" :title="img.isPublic===1?'设为私有':'设为公开'"><Globe class="w-3.5 h-3.5 inline" /> {{ img.isPublic===1?'公开':'私有' }}</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!loading && imageList.length > 0" class="py-4 flex items-center justify-between bg-white/[0.01] rounded-xl border border-white/5 px-4 relative z-10">
      <span class="text-xs text-slate-500">共 {{ formatNumber(total) }} 张图片</span>
      <div class="flex items-center space-x-1">
        <button class="w-7 h-7 rounded flex items-center justify-center text-slate-500 hover:bg-white/5 hover:text-white disabled:opacity-50" :disabled="currentPage <= 1" @click="handlePageChange(currentPage-1)"><ChevronLeft class="w-4 h-4" /></button>
        <template v-for="page in visiblePages()" :key="page"><button v-if="totalPages > 1" class="w-7 h-7 rounded flex items-center justify-center text-xs font-bold transition-colors" :class="page===currentPage?'bg-rose-500/20 text-rose-400 border border-rose-500/30':'text-slate-400 hover:bg-white/5 hover:text-white'" @click="handlePageChange(page)">{{ page }}</button></template>
        <button class="w-7 h-7 rounded flex items-center justify-center text-slate-500 hover:bg-white/5 hover:text-white" :disabled="currentPage >= totalPages" @click="handlePageChange(currentPage+1)"><ChevronRight class="w-4 h-4" /></button>
      </div>
    </div>

    <Teleport to="body"><div v-if="showPreview" class="fixed inset-0 z-[60] flex items-center justify-center" @click.self="showPreview=false"><div class="absolute inset-0 bg-black/90 backdrop-blur-md" @click="showPreview=false"></div><div class="relative z-10 max-w-[90vw] max-h-[90vh]"><button class="absolute -top-12 right-0 text-slate-400 hover:text-white p-2" @click="showPreview=false"><X class="w-6 h-6" /></button><img :src="previewUrl" class="max-w-full max-h-[85vh] rounded-xl object-contain shadow-2xl" /></div></div></Teleport>
  </div>
</template>

<style scoped>
.glass-panel { background: rgba(255,255,255,0.02); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.05); box-shadow: inset 0 1px 1px rgba(255,255,255,0.05); }
.glass-card { transition: all 0.4s cubic-bezier(0.4,0,0.2,1); }
.glass-card:hover { transform: translateY(-4px); background: rgba(255,255,255,0.06); border-color: rgba(244,63,94,0.3); box-shadow: 0 10px 40px -10px rgba(244,63,94,0.15); }
.glass-checkbox { appearance: none; width: 16px; height: 16px; border: 1px solid rgba(255,255,255,0.2); border-radius: 4px; background: rgba(0,0,0,0.2); cursor: pointer; position: relative; transition: all 0.2s; }
.glass-checkbox:checked { background: #f43f5e; border-color: #f43f5e; box-shadow: 0 0 10px rgba(244,63,94,0.4); }
.glass-checkbox:checked::after { content: ''; position: absolute; left: 5px; top: 2px; width: 4px; height: 8px; border: solid white; border-width: 0 2px 2px 0; transform: rotate(45deg); }
</style>
