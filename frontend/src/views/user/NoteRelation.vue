<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { noteApi, getNoteStatusInfo } from '@/api/notes'
import type { NoteDetailVO, NoteRelationDetailVO, NoteItem } from '@/api/notes'
import { imageApi } from '@/api/images'
import type { ImageItem } from '@/api/images'
import { tagApi } from '@/api/tags'
import type { TagItem } from '@/api/tags'
import {
  ArrowLeft, ChevronRight, AlertTriangle, CheckCircle2, RefreshCw,
  ImageIcon, Hash, Link, Search, Plug, X, FileText, Unlink2,
  Loader2, Network
} from 'lucide-vue-next'

const route = useRoute()

const loading = ref(true)
const error = ref<string | null>(null)
const note = ref<NoteDetailVO | null>(null)
const relationDetail = ref<NoteRelationDetailVO | null>(null)

const relationLoading = ref(false)
const relationError = ref<string | null>(null)
const relationTab = ref<'images' | 'tags' | 'links'>('images')

type BindTargetType = 'image' | 'tag' | 'note'

interface BindResultItem {
  id: number
  title: string
  subtitle?: string
  meta?: string
  status?: { label: string; cls: string }
}

const showBindModal = ref(false)
const bindSearchQuery = ref('')
const bindSearchLoading = ref(false)
const bindSearchError = ref<string | null>(null)
const bindResults = ref<BindResultItem[]>([])
const selectedBindTargetId = ref<number | null>(null)
const currentBind = ref<{ type: BindTargetType; mappingId: number; parsedName: string } | null>(null)

function showAlert(msg: string) { window.alert(msg) }
function showConfirm(msg: string): boolean { return window.confirm(msg) }

function formatBytes(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const u = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(i > 0 ? 1 : 0) + ' ' + u[i]
}

function formatDate(raw: string): string {
  if (!raw) return '-'
  const d = new Date(raw)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function resolveAuditStatus(isPass?: number) {
  if (isPass === 1) return { label: '已通过', cls: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20' }
  if (isPass === 2) return { label: '未通过', cls: 'text-rose-400 bg-rose-500/10 border-rose-500/20' }
  return { label: '待审核', cls: 'text-amber-400 bg-amber-500/10 border-amber-500/20' }
}

function getBindTypeLabel(type?: BindTargetType) {
  if (type === 'image') return 'Image'
  if (type === 'tag') return 'Tag'
  if (type === 'note') return 'Link'
  return 'Resource'
}

function getBindTypeBadgeClass(type?: BindTargetType) {
  if (type === 'image') return 'bg-sky-500/20 text-sky-400 border border-sky-500/30'
  if (type === 'tag') return 'bg-purple-500/20 text-purple-400 border border-purple-500/30'
  if (type === 'note') return 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
  return 'bg-white/10 text-slate-300 border border-white/10'
}

const relationStats = computed(() => {
  const detail = relationDetail.value
  if (!detail) {
    return {
      imagesTotal: 0,
      tagsTotal: 0,
      linksTotal: 0,
      imagesMissing: 0,
      tagsMissing: 0,
      linksMissing: 0,
      missingTotal: 0,
      boundTotal: 0
    }
  }
  const imagesTotal = detail.images?.length ?? 0
  const tagsTotal = detail.tags?.length ?? 0
  const linksTotal = detail.eachNotes?.length ?? 0
  const imagesMissing = detail.images?.filter(row => row.isMissing === 1).length ?? 0
  const tagsMissing = detail.tags?.filter(row => row.isMissing === 1).length ?? 0
  const linksMissing = detail.eachNotes?.filter(row => row.isMissing === 1).length ?? 0
  const missingTotal = imagesMissing + tagsMissing + linksMissing
  const boundTotal = imagesTotal + tagsTotal + linksTotal - missingTotal
  return { imagesTotal, tagsTotal, linksTotal, imagesMissing, tagsMissing, linksMissing, missingTotal, boundTotal }
})

async function fetchPage() {
  const noteId = Number(route.params.noteId)
  if (!noteId || isNaN(noteId)) {
    error.value = '无效的笔记 ID'
    loading.value = false
    return
  }
  loading.value = true
  error.value = null
  try {
    const [detail, relations] = await Promise.all([
      noteApi.getDetail(noteId),
      noteApi.getRelations(noteId)
    ])
    note.value = detail
    relationDetail.value = relations
  } catch (e: any) {
    error.value = e?.message || '加载关联映射失败'
  } finally {
    loading.value = false
  }
}

async function refreshRelations() {
  const noteId = Number(route.params.noteId)
  if (!noteId || isNaN(noteId)) return
  relationLoading.value = true
  relationError.value = null
  try {
    const [detail, relations] = await Promise.all([
      noteApi.getDetail(noteId),
      noteApi.getRelations(noteId)
    ])
    note.value = detail
    relationDetail.value = relations
  } catch (e: any) {
    relationError.value = e?.message || '刷新关联映射失败'
  } finally {
    relationLoading.value = false
  }
}

async function handleRecheckRelations() {
  const noteId = Number(route.params.noteId)
  if (!noteId || isNaN(noteId)) return
  try {
    const result = await noteApi.checkRelations(noteId)
    if (result.complete) {
      showAlert('关联完整性校验通过。')
    } else {
      const lines: string[] = [`仍有 ${result.missingCount} 项关联需要补全：`]
      if (result.missingTags?.length) lines.push(`\n标签缺失：${result.missingTags.join('、')}`)
      if (result.missingImages?.length) lines.push(`\n图片缺失：${result.missingImages.join('、')}`)
      if (result.missingNoteNames?.length) lines.push(`\n双链缺失：${result.missingNoteNames.join('、')}`)
      showAlert(lines.join(''))
    }
    await refreshRelations()
  } catch {
    showAlert('关联校验失败，请重试')
  }
}

function openBindModal(type: BindTargetType, mappingId: number, parsedName: string) {
  currentBind.value = { type, mappingId, parsedName }
  bindSearchQuery.value = parsedName || ''
  bindResults.value = []
  selectedBindTargetId.value = null
  bindSearchError.value = null
  showBindModal.value = true
  if (bindSearchQuery.value) {
    void handleBindSearch()
  }
}

function closeBindModal() {
  showBindModal.value = false
  bindSearchQuery.value = ''
  bindResults.value = []
  selectedBindTargetId.value = null
  bindSearchError.value = null
  currentBind.value = null
}

async function handleBindSearch() {
  if (!currentBind.value) return
  const keyword = bindSearchQuery.value.trim()
  if (!keyword) {
    bindResults.value = []
    return
  }
  bindSearchLoading.value = true
  bindSearchError.value = null
  selectedBindTargetId.value = null
  try {
    if (currentBind.value.type === 'image') {
      const res = await imageApi.getList({ filename: keyword, pageNum: 1, pageSize: 8 })
      bindResults.value = (res.records ?? []).map((img: ImageItem) => ({
        id: img.id,
        title: img.filename || '未命名图片',
        subtitle: formatBytes(img.fileSize),
        meta: formatDate(img.uploadTime || img.createTime),
        status: resolveAuditStatus(img.isPass)
      }))
    } else if (currentBind.value.type === 'tag') {
      const res = await tagApi.getList({ keyword, pageNum: 1, pageSize: 8 })
      bindResults.value = (res.records ?? []).map((tag: TagItem) => ({
        id: tag.id,
        title: `#${tag.tagName}`,
        meta: formatDate(tag.createTime),
        status: resolveAuditStatus(tag.isPass)
      }))
    } else {
      const res = await noteApi.searchNotes({ keyword, pageNum: 1, pageSize: 8 })
      bindResults.value = (res.records ?? []).map((item: NoteItem) => ({
        id: item.id,
        title: item.title,
        subtitle: item.topicName ? `主题：${item.topicName}` : '未归属主题',
        meta: formatDate(item.updateTime || item.createTime),
        status: { label: getNoteStatusInfo(item.status).label, cls: getNoteStatusInfo(item.status).cls }
      }))
    }
  } catch {
    bindSearchError.value = '搜索失败，请重试'
  } finally {
    bindSearchLoading.value = false
  }
}

async function handleBindConfirm() {
  if (!currentBind.value || selectedBindTargetId.value == null) {
    showAlert('请选择一个目标资源')
    return
  }
  try {
    const targetId = selectedBindTargetId.value
    if (currentBind.value.type === 'image') {
      await noteApi.bindImage(currentBind.value.mappingId, targetId)
    } else if (currentBind.value.type === 'tag') {
      await noteApi.bindTag(currentBind.value.mappingId, targetId)
    } else {
      await noteApi.bindEach(currentBind.value.mappingId, targetId)
    }
    showAlert('关联绑定成功')
    closeBindModal()
    await refreshRelations()
  } catch {
    showAlert('绑定失败，请重试')
  }
}

async function handleUnbind(type: BindTargetType, mappingId: number) {
  if (!showConfirm('确认解除该关联绑定吗？')) return
  try {
    if (type === 'image') await noteApi.unbindImage(mappingId)
    else if (type === 'tag') await noteApi.unbindTag(mappingId)
    else await noteApi.unbindEach(mappingId)
    showAlert('绑定已解除')
    await refreshRelations()
  } catch {
    showAlert('解绑失败，请重试')
  }
}

onMounted(() => {
  fetchPage()
})
</script>

<template>
  <div class="relative max-w-[1400px] mx-auto space-y-6 pb-12">
    <div v-if="loading" class="flex items-center justify-center py-24">
      <Loader2 class="w-6 h-6 text-blue-400 animate-spin" />
      <span class="ml-3 text-sm text-slate-500">加载关联映射中...</span>
    </div>

    <div v-else-if="error" class="glass-panel rounded-2xl p-10 text-center">
      <div class="w-12 h-12 rounded-xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center mx-auto mb-4">
        <X class="w-5 h-5 text-rose-400" />
      </div>
      <h2 class="text-lg font-bold text-white mb-2">无法读取关联映射</h2>
      <p class="text-sm text-slate-400">{{ error }}</p>
    </div>

    <template v-else>
      <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div class="flex items-center space-x-3">
          <button class="group flex items-center space-x-2 text-slate-400 hover:text-white transition-colors bg-white/5 hover:bg-white/10 px-4 py-2 rounded-xl border border-white/5" @click="$router.push('/user/notes')">
            <ArrowLeft class="w-4 h-4 group-hover:-translate-x-1 transition-transform" />
            <span class="text-sm font-bold">返回列表</span>
          </button>
          <div class="hidden md:flex items-center text-sm font-medium text-slate-400">
            <span class="hover:text-white cursor-pointer transition-colors" @click="$router.push('/user/notes')">我的笔记</span>
            <ChevronRight class="w-4 h-4 mx-1 opacity-50" />
            <span class="text-blue-300 truncate max-w-[300px]">{{ note?.title }}</span>
          </div>
        </div>
        <span v-if="note" class="inline-flex items-center px-2.5 py-1 rounded-md text-[10px] font-bold uppercase tracking-widest border" :class="getNoteStatusInfo(note.status).cls">
          <component :is="getNoteStatusInfo(note.status).icon === 'AlertTriangle' ? AlertTriangle : CheckCircle2" class="w-3 h-3 mr-1" />
          {{ getNoteStatusInfo(note.status).label }}
        </span>
      </div>

      <div class="glass-panel rounded-2xl p-6 border border-white/10 shadow-xl relative overflow-hidden flex flex-col md:flex-row justify-between items-start md:items-center gap-6 bg-gradient-to-br from-[#0f172a]/80 to-transparent">
        <div class="flex items-start gap-4 relative z-10">
          <div class="w-12 h-12 rounded-xl flex items-center justify-center shrink-0 mt-1" :class="relationStats.missingTotal > 0 ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20' : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'">
            <AlertTriangle v-if="relationStats.missingTotal > 0" class="w-6 h-6" />
            <CheckCircle2 v-else class="w-6 h-6" />
          </div>
          <div>
            <h2 class="text-xl font-bold text-white flex items-center gap-2">
              关联健康度 {{ relationStats.missingTotal > 0 ? '异常' : '正常' }}
              <span v-if="relationStats.missingTotal > 0" class="px-2 py-0.5 rounded text-[10px] uppercase font-black tracking-widest bg-rose-500/10 text-rose-400 border border-rose-500/20">{{ relationStats.missingTotal }} Missing</span>
            </h2>
            <p class="text-sm text-slate-400 mt-1.5 max-w-xl leading-relaxed">
              引擎扫描到 Markdown 源码中存在引用的资产，但由于重名或不在当前主题内，系统无法自动补全关联。请手动指定映射目标，修复后方可继续转换与审核。
            </p>
          </div>
        </div>

        <div class="flex gap-4 relative z-10 shrink-0">
          <div class="flex flex-col items-center justify-center w-20 h-20 rounded-full border-[3px] border-emerald-500/30 relative">
            <span class="text-lg font-black text-emerald-400">{{ relationStats.boundTotal }}</span>
            <span class="text-[9px] text-slate-500 uppercase font-bold">已绑定</span>
          </div>
          <div class="flex flex-col items-center justify-center w-20 h-20 rounded-full border-[3px] border-rose-500/20 relative">
            <span class="text-lg font-black text-rose-400">{{ relationStats.missingTotal }}</span>
            <span class="text-[9px] text-slate-500 uppercase font-bold">待修复</span>
          </div>
        </div>
      </div>

      <div class="flex items-center gap-2 border-b border-white/5 pb-px">
        <button class="px-5 py-3 text-sm font-bold border-b-2 transition-colors duration-300 flex items-center gap-2" :class="relationTab === 'images' ? 'text-blue-400 border-blue-500' : 'text-slate-400 border-transparent hover:text-white'" @click="relationTab = 'images'">
          <ImageIcon class="w-4 h-4" />
          图片映射
          <span v-if="relationStats.imagesMissing" class="ml-1 w-5 h-5 rounded flex items-center justify-center text-[10px] bg-rose-500/20 text-rose-400">{{ relationStats.imagesMissing }}</span>
        </button>
        <button class="px-5 py-3 text-sm font-bold border-b-2 transition-colors duration-300 flex items-center gap-2" :class="relationTab === 'tags' ? 'text-blue-400 border-blue-500' : 'text-slate-400 border-transparent hover:text-white'" @click="relationTab = 'tags'">
          <Hash class="w-4 h-4" />
          标签映射
          <span v-if="relationStats.tagsMissing" class="ml-1 w-5 h-5 rounded flex items-center justify-center text-[10px] bg-rose-500/20 text-rose-400">{{ relationStats.tagsMissing }}</span>
        </button>
        <button class="px-5 py-3 text-sm font-bold border-b-2 transition-colors duration-300 flex items-center gap-2" :class="relationTab === 'links' ? 'text-blue-400 border-blue-500' : 'text-slate-400 border-transparent hover:text-white'" @click="relationTab = 'links'">
          <Link class="w-4 h-4" />
          双链映射
          <span v-if="relationStats.linksMissing" class="ml-1 w-5 h-5 rounded flex items-center justify-center text-[10px] bg-rose-500/20 text-rose-400">{{ relationStats.linksMissing }}</span>
        </button>
        <div class="flex-1"></div>
        <button class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white/5 hover:bg-blue-500/20 text-slate-400 hover:text-blue-300 text-xs font-bold transition-all border border-transparent hover:border-blue-500/30" @click="handleRecheckRelations">
          <RefreshCw class="w-3.5 h-3.5" />
          重新校验关联
        </button>
      </div>

      <div class="relative min-h-[320px]">
        <div v-if="relationLoading" class="flex items-center justify-center py-16">
          <Loader2 class="w-5 h-5 text-blue-400 animate-spin" />
          <span class="ml-3 text-sm text-slate-500">刷新关联映射中...</span>
        </div>
        <div v-else-if="relationError" class="glass-panel rounded-2xl p-8 text-center">
          <div class="w-12 h-12 rounded-xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center mx-auto mb-3">
            <X class="w-5 h-5 text-rose-400" />
          </div>
          <p class="text-sm text-slate-400">{{ relationError }}</p>
        </div>
        <div v-else>
          <div v-if="relationTab === 'images'" class="space-y-3">
            <div v-if="!relationDetail?.images?.length" class="glass-panel rounded-2xl p-10 text-center text-slate-500">
              暂无图片映射记录
            </div>
            <div v-else class="space-y-3">
              <div v-for="row in relationDetail.images" :key="row.mappingId" class="glass-panel rounded-xl p-4 border transition-colors flex items-center justify-between" :class="row.isMissing === 1 ? 'border-rose-500/30 bg-rose-500/5 hover:bg-rose-500/10' : 'border-white/5 bg-black/20 hover:bg-white/5'">
                <div class="flex items-center gap-4">
                  <div class="w-10 h-10 rounded-lg flex items-center justify-center shrink-0" :class="row.isMissing === 1 ? 'bg-rose-500/10 text-rose-400' : 'bg-sky-500/10 text-sky-400'">
                    <ImageIcon class="w-5 h-5" />
                  </div>
                  <div class="flex flex-col">
                    <div class="flex items-center gap-2">
                      <span class="text-xs text-slate-400 uppercase tracking-widest font-bold">Parsed from Markdown</span>
                      <span v-if="row.isCrossUser === 1" class="text-[10px] font-bold px-1.5 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/20">跨用户</span>
                    </div>
                    <div class="font-mono text-sm text-slate-200 mt-0.5 break-all">
                      {{ row.parsedImageName || row.filename || '未命名图片' }}
                    </div>
                    <div class="flex items-center gap-1.5 mt-2">
                      <span v-if="row.isMissing === 1" class="px-2 py-0.5 rounded text-[10px] font-bold bg-rose-500/20 text-rose-400 flex items-center">未找到匹配资源</span>
                      <span v-else class="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center">绑定成功</span>
                      <span v-if="row.isMissing !== 1" class="text-xs text-slate-500">目标文件：<span class="text-blue-300 ml-1">{{ row.filename || '-' }}</span></span>
                      <span v-if="row.isMissing !== 1" class="px-2 py-0.5 rounded text-[10px] font-bold border" :class="resolveAuditStatus(row.isPass).cls">{{ resolveAuditStatus(row.isPass).label }}</span>
                    </div>
                  </div>
                </div>
                <div class="flex items-center gap-3 shrink-0 ml-4">
                  <button v-if="row.isMissing === 1" class="px-4 py-2 rounded-xl bg-blue-600/20 hover:bg-blue-600/40 text-blue-400 hover:text-blue-300 border border-blue-500/30 transition-all text-sm font-bold flex items-center gap-2" @click="openBindModal('image', row.mappingId, row.parsedImageName)">
                    <Search class="w-4 h-4" /> 检索并手动绑定
                  </button>
                  <button v-else class="w-9 h-9 rounded-xl bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 transition-all flex items-center justify-center border border-transparent hover:border-rose-500/30" title="解除绑定" @click="handleUnbind('image', row.mappingId)">
                    <Unlink2 class="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div v-if="relationTab === 'tags'" class="space-y-3">
            <div v-if="!relationDetail?.tags?.length" class="glass-panel rounded-2xl p-10 text-center text-slate-500">
              暂无标签映射记录
            </div>
            <div v-else class="space-y-3">
              <div v-for="row in relationDetail.tags" :key="row.mappingId" class="glass-panel rounded-xl p-4 border transition-colors flex items-center justify-between" :class="row.isMissing === 1 ? 'border-rose-500/30 bg-rose-500/5 hover:bg-rose-500/10' : 'border-white/5 bg-black/20 hover:bg-white/5'">
                <div class="flex items-center gap-4">
                  <div class="w-10 h-10 rounded-lg flex items-center justify-center shrink-0" :class="row.isMissing === 1 ? 'bg-rose-500/10 text-rose-400' : 'bg-purple-500/10 text-purple-400'">
                    <Hash class="w-5 h-5" />
                  </div>
                  <div class="flex flex-col">
                    <div class="flex items-center gap-2">
                      <span class="text-xs text-slate-400 uppercase tracking-widest font-bold">Parsed Tag</span>
                    </div>
                    <div class="text-sm font-bold text-slate-200 mt-0.5">
                      # {{ row.parsedTagName || row.tagName || '未命名标签' }}
                    </div>
                    <div class="flex items-center gap-1.5 mt-2">
                      <span v-if="row.isMissing === 1" class="px-2 py-0.5 rounded text-[10px] font-bold bg-rose-500/20 text-rose-400 flex items-center">未找到匹配资源</span>
                      <span v-else class="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center">绑定成功</span>
                      <span v-if="row.isMissing !== 1" class="text-xs text-slate-500">目标标签：<span class="text-blue-300 ml-1">{{ row.tagName || '-' }}</span></span>
                      <span v-if="row.isMissing !== 1" class="px-2 py-0.5 rounded text-[10px] font-bold border" :class="resolveAuditStatus(row.isPass).cls">{{ resolveAuditStatus(row.isPass).label }}</span>
                    </div>
                  </div>
                </div>
                <div class="flex items-center gap-3 shrink-0 ml-4">
                  <button v-if="row.isMissing === 1" class="px-4 py-2 rounded-xl bg-blue-600/20 hover:bg-blue-600/40 text-blue-400 hover:text-blue-300 border border-blue-500/30 transition-all text-sm font-bold flex items-center gap-2" @click="openBindModal('tag', row.mappingId, row.parsedTagName)">
                    <Search class="w-4 h-4" /> 检索并手动绑定
                  </button>
                  <button v-else class="w-9 h-9 rounded-xl bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 transition-all flex items-center justify-center border border-transparent hover:border-rose-500/30" title="解除绑定" @click="handleUnbind('tag', row.mappingId)">
                    <Unlink2 class="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div v-if="relationTab === 'links'" class="space-y-3">
            <div v-if="!relationDetail?.eachNotes?.length" class="glass-panel rounded-2xl p-10 text-center text-slate-500">
              暂无双链映射记录
            </div>
            <div v-else class="space-y-3">
              <div v-for="row in relationDetail.eachNotes" :key="row.mappingId" class="glass-panel rounded-xl p-4 border transition-colors flex items-center justify-between" :class="row.isMissing === 1 ? 'border-rose-500/30 bg-rose-500/5 hover:bg-rose-500/10' : 'border-white/5 bg-black/20 hover:bg-white/5'">
                <div class="flex items-center gap-4">
                  <div class="w-10 h-10 rounded-lg flex items-center justify-center shrink-0" :class="row.isMissing === 1 ? 'bg-rose-500/10 text-rose-400' : 'bg-emerald-500/10 text-emerald-400'">
                    <Link class="w-5 h-5" />
                  </div>
                  <div class="flex flex-col">
                    <div class="flex items-center gap-2">
                      <span class="text-xs text-slate-400 uppercase tracking-widest font-bold">Obsidian Link</span>
                    </div>
                    <div class="font-mono text-sm text-slate-200 mt-0.5 break-all">
                      [[{{ row.parsedNoteName || row.nickname || row.targetNoteTitle || '未命名笔记' }}]]
                    </div>
                    <div class="flex items-center gap-1.5 mt-2">
                      <span v-if="row.isMissing === 1" class="px-2 py-0.5 rounded text-[10px] font-bold bg-rose-500/20 text-rose-400 flex items-center">目标笔记不存在</span>
                      <span v-else class="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center">绑定成功</span>
                      <span v-if="row.isMissing !== 1" class="text-xs text-slate-500">目标笔记：<span class="text-blue-300 ml-1">{{ row.targetNoteTitle || '-' }}</span></span>
                      <span v-if="row.isMissing !== 1" class="px-2 py-0.5 rounded text-[10px] font-bold border" :class="resolveAuditStatus(row.isPass).cls">{{ resolveAuditStatus(row.isPass).label }}</span>
                    </div>
                    <span v-if="row.anchor" class="text-[10px] text-slate-500 mt-1">锚点：{{ row.anchor }}</span>
                  </div>
                </div>
                <div class="flex items-center gap-3 shrink-0 ml-4">
                  <button v-if="row.isMissing === 1" class="px-4 py-2 rounded-xl bg-blue-600/20 hover:bg-blue-600/40 text-blue-400 hover:text-blue-300 border border-blue-500/30 transition-all text-sm font-bold flex items-center gap-2" @click="openBindModal('note', row.mappingId, row.parsedNoteName || row.nickname)">
                    <Search class="w-4 h-4" /> 全局检索并绑定
                  </button>
                  <button v-else class="w-9 h-9 rounded-xl bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 transition-all flex items-center justify-center border border-transparent hover:border-rose-500/30" title="解除绑定" @click="handleUnbind('note', row.mappingId)">
                    <Unlink2 class="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showBindModal" class="fixed inset-0 z-50 bg-black/60 backdrop-blur-md" @click="closeBindModal"></div>
      </Transition>
      <Transition name="modal">
        <div v-if="showBindModal" class="fixed inset-0 z-50 flex items-center justify-center px-4" @click.self="closeBindModal">
          <div class="glass-panel w-full max-w-2xl rounded-3xl p-6 relative z-10 shadow-[0_20px_50px_rgba(0,0,0,0.5)] bg-[#020617]/90 border border-white/10">
            <div class="absolute -top-10 -right-10 w-40 h-40 bg-blue-500/20 blur-[60px] rounded-full pointer-events-none"></div>
            <div class="flex justify-between items-start mb-5">
              <div class="flex gap-4">
                <div class="w-12 h-12 rounded-2xl bg-blue-500/10 flex items-center justify-center text-blue-400 border border-blue-500/20 shrink-0">
                  <Plug class="w-6 h-6" />
                </div>
                <div>
                  <h3 class="text-lg font-bold text-white flex items-center gap-2">
                    强制绑定关联目标
                    <span class="px-2 py-0.5 rounded text-[10px] font-black uppercase tracking-widest" :class="getBindTypeBadgeClass(currentBind?.type)">{{ getBindTypeLabel(currentBind?.type) }}</span>
                  </h3>
                  <p class="text-xs text-slate-400 mt-1">
                    为 Markdown 源码中解析出的 <span class="text-blue-300 font-mono bg-blue-500/10 px-1 rounded">{{ currentBind?.parsedName || '-' }}</span> 指定全站目标资源。
                  </p>
                </div>
              </div>
              <button class="text-slate-500 hover:text-white transition-colors p-2 rounded-xl hover:bg-white/5" @click="closeBindModal">
                <X class="w-5 h-5" />
              </button>
            </div>

            <div class="relative group flex items-center bg-[#0b0f19] border border-white/10 rounded-xl overflow-hidden focus-within:border-blue-500/50 focus-within:ring-2 focus-within:ring-blue-500/20 transition-all h-12 mb-4">
              <div class="w-12 h-full flex items-center justify-center text-slate-500">
                <Search class="w-5 h-5" />
              </div>
              <input v-model="bindSearchQuery" type="text" placeholder="输入全站资源的名称、标题进行模糊搜索..." class="w-full h-full bg-transparent text-sm text-white placeholder:text-slate-500 outline-none pr-4" @keyup.enter="handleBindSearch" />
              <button class="absolute right-2 px-3 py-1.5 text-xs font-bold text-white bg-blue-600 hover:bg-blue-500 rounded-lg transition-colors" :disabled="bindSearchLoading" @click="handleBindSearch">搜索</button>
            </div>

            <div class="h-64 overflow-y-auto custom-scrollbar space-y-2 pr-2">
              <div v-if="bindSearchLoading" class="text-xs text-slate-500">搜索中...</div>
              <div v-else-if="bindSearchError" class="text-xs text-rose-400">{{ bindSearchError }}</div>
              <div v-else-if="bindResults.length === 0" class="text-xs text-slate-500">暂无匹配结果</div>
              <label v-else v-for="item in bindResults" :key="item.id" class="target-item glass-panel p-3 rounded-xl flex items-center gap-4 cursor-pointer transition-colors border border-white/5" :class="selectedBindTargetId === item.id ? 'selected' : 'hover:bg-white/5'" @click="selectedBindTargetId = item.id">
                <input type="radio" name="bind-target" class="hidden" :checked="selectedBindTargetId === item.id" />
                <div class="w-12 h-12 rounded-lg bg-black/50 border border-white/10 overflow-hidden shrink-0 flex items-center justify-center">
                  <component :is="currentBind?.type === 'tag' ? Hash : currentBind?.type === 'note' ? FileText : ImageIcon" class="w-5 h-5 text-slate-500" />
                </div>
                <div class="flex-1 min-w-0">
                  <div class="text-sm font-bold text-white truncate">{{ item.title }}</div>
                  <div class="text-[10px] text-slate-400 flex items-center gap-2 mt-1">
                    <span v-if="item.subtitle" class="text-indigo-300 bg-indigo-500/10 px-1.5 rounded">{{ item.subtitle }}</span>
                    <span v-if="item.meta">{{ item.meta }}</span>
                    <span v-if="item.status" class="px-1.5 py-0.5 rounded border" :class="item.status.cls">{{ item.status.label }}</span>
                  </div>
                </div>
                <div class="w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0" :class="selectedBindTargetId === item.id ? 'border-blue-500 bg-blue-500' : 'border-slate-600'">
                  <div v-if="selectedBindTargetId === item.id" class="w-2 h-2 rounded-full bg-white"></div>
                </div>
              </label>
            </div>

            <div class="pt-5 mt-2 border-t border-white/10 flex justify-between items-center">
              <span class="text-xs text-amber-400 bg-amber-500/10 px-2 py-1 rounded flex items-center border border-amber-500/20">跨主题绑定将建立全局映射</span>
              <div class="flex gap-3">
                <button type="button" class="px-5 py-2.5 rounded-xl text-sm font-bold text-slate-400 hover:text-white hover:bg-white/5 transition-colors" @click="closeBindModal">取消</button>
                <button type="button" class="group relative px-6 py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-bold rounded-xl shadow-[0_0_15px_rgba(59,130,246,0.4)] transition-all overflow-hidden flex items-center gap-2 disabled:opacity-50" :disabled="!selectedBindTargetId || bindSearchLoading" @click="handleBindConfirm">
                  <div class="absolute inset-0 bg-[linear-gradient(to_right,transparent,rgba(255,255,255,0.2),transparent)] -translate-x-[150%] group-hover:translate-x-[150%] transition-transform duration-700 ease-out"></div>
                  <Network class="w-4 h-4" />
                  <span>确认映射并绑定</span>
                </button>
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

.custom-scrollbar::-webkit-scrollbar { width: 4px; height: 4px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.1); border-radius: 10px; }
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: rgba(59, 130, 246, 0.5); }

.target-item.selected {
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.5);
  box-shadow: 0 0 15px rgba(59, 130, 246, 0.2);
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.28s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
.modal-enter-active, .modal-leave-active {
  transition: opacity 0.32s ease, transform 0.42s cubic-bezier(0.25, 1, 0.5, 1);
}
.modal-enter-from, .modal-leave-to {
  opacity: 0;
  transform: scale(0.96) translateY(14px);
}
.modal-enter-to, .modal-leave-from {
  opacity: 1;
  transform: scale(1) translateY(0);
}
</style>
