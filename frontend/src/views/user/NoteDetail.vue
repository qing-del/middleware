<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { noteApi, getNoteStatusInfo, NoteStatusCode } from '@/api/notes'
import type { NoteDetailVO } from '@/api/notes'
import {
  ArrowLeft, Globe, FileEdit, Calendar, HardDrive, Layers, Hash, ImageIcon, Link,
  Network, ShieldCheck, CheckCircle2, AlertTriangle, ListTree, ArrowUpToLine,
  LayoutPanelTop, Loader2, RefreshCw, X, ChevronRight, FileText, Clock
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()

// ── State ─────────────────────────────────────────
const loading = ref(true)
const note = ref<NoteDetailVO | null>(null)
const error = ref<string | null>(null)
const converting = ref(false)
const matrixCollapsed = ref(false)

// Floating TOC state
const showTocPanel = ref(false)
const tocWrapperRef = ref<HTMLElement | null>(null)
const tocBallRef = ref<HTMLElement | null>(null)
const tocPanelRef = ref<HTMLElement | null>(null)

// 动态管理悬浮面板的方向和定位，解决直接操作 DOM class 被 Vue 重新渲染覆盖的问题
const panelPosClasses = ref(['bottom-full', 'right-0', 'mb-4', 'origin-bottom-right'])

// ── Status icon resolver ──────────────────────────
function resolveStatusIcon(iconName: string) {
  const map: Record<string, any> = {
    FilePlus: FileText, AlertTriangle, RefreshCw, FileCode: FileText,
    Clock, CheckCircle2, Globe, XCircle: X, Trash2: X
  }
  return map[iconName] ?? FileText
}

// ── Helpers ───────────────────────────────────────
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

function isMissingImg(img: { isMissing: number }) { return img.isMissing === 1 }
function isMissingLink(link: { isMissing: number }) { return link.isMissing === 1 }

// ── Relation integrity ────────────────────────────
const allRelationsIntact = () => {
  if (!note.value) return false
  const n = note.value
  if (n.missingCount > 0) return false
  if (n.images.some(i => i.isMissing)) return false
  if (n.eachNotes.some(e => e.isMissing)) return false
  return true
}

const hasAnyRelations = () => {
  if (!note.value) return false
  const n = note.value
  return (n.tags?.length ?? 0) > 0 || (n.images?.length ?? 0) > 0 || (n.eachNotes?.length ?? 0) > 0
}

// ── Actions ───────────────────────────────────────
async function handleConvert() {
  if (!note.value || converting.value) return
  converting.value = true
  try {
    await noteApi.convertNote(note.value.id)
    showAlert('转换成功，正在刷新...')
    await fetchNote()
  } catch {
    showAlert('转换失败，请确认笔记关联完整')
  } finally {
    converting.value = false
  }
}

async function handlePublish() {
  if (!note.value) return
  try {
    await noteApi.publish(note.value.id, 1)
    showAlert('发布成功')
    await fetchNote()
  } catch { showAlert('发布失败') }
}

async function handleUnpublish() {
  if (!note.value) return
  if (!showConfirm('确定下架该笔记吗？公开链接将不可访问')) return
  try {
    await noteApi.publish(note.value.id, 0)
    await fetchNote()
  } catch { showAlert('下架失败') }
}

async function handleSubmitAudit() {
  if (!note.value) return
  if (!showConfirm('确认提交审核吗？提交后将无法编辑')) return
  try {
    await noteApi.submitAudit(note.value.id)
    await fetchNote()
  } catch { showAlert('提交审核失败') }
}

// ── Anchor scrolling ──────────────────────────────
function scrollToHashAnchor() {
  const hash = route.hash
  if (!hash) return
  const id = hash.replace('#', '')
  if (!id) return
  // Wait for v-html DOM to be fully rendered
  nextTick(() => {
    const el = document.getElementById(id)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' })
    }
  })
}

// ── Data fetching ─────────────────────────────────
async function fetchNote() {
  const noteId = Number(route.params.noteId)
  if (!noteId || isNaN(noteId)) {
    error.value = '无效的笔记 ID'
    loading.value = false
    return
  }
  loading.value = true
  error.value = null
  try {
    note.value = await noteApi.getDetail(noteId)
    // After data loads, scroll to hash anchor if present
    scrollToHashAnchor()
  } catch (e: any) {
    error.value = e?.message || '加载笔记失败'
  } finally {
    loading.value = false
  }
}

// ── Router reuse watcher ─────────────────────────
// When navigating from NoteDetail → NoteDetail (e.g. via internal link),
// Vue Router reuses the same component instance, so onMounted won't fire.
// watch on route.params.noteId handles this case.
watch(
  () => route.params.noteId,
  async (newId, oldId) => {
    if (newId === oldId || !newId) return
    // Reset state for the new note
    loading.value = true
    error.value = null
    note.value = null
    showTocPanel.value = false
    // Reset scroll position
    const main = document.querySelector('main')
    if (main) main.scrollTop = 0
    window.scrollTo(0, 0)
    
    await fetchNote()
    await nextTick()
    await bindTocEvents()
  },
  { immediate: true }
)

// ── Internal link interception ────────────────────
function handleInternalLinkClick(e: Event) {
  const link = (e.target as HTMLElement).closest('.internal-note-link') as HTMLAnchorElement | null
  if (!link) return
  e.preventDefault()
  const noteId = link.dataset.noteId
  const anchor = link.dataset.anchor
  if (noteId) {
    router.push(anchor ? `/user/notes/${noteId}#${anchor}` : `/user/notes/${noteId}`)
  }
}

function handleEachNoteClick(each: { targetNoteId: number; isMissing: number }) {
  if (!each.isMissing) {
    router.push(`/user/notes/${each.targetNoteId}`)
  }
}

// ── TOC link click ────────────────────────────────
function onTocLinkClick(e: Event) {
  const link = (e.target as HTMLElement).closest('a[href^="#"]') as HTMLAnchorElement | null
  if (!link) return
  e.preventDefault()
  const id = link.getAttribute('href')?.slice(1)
  if (id) {
    const el = document.getElementById(id)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' })
    }
  }
  showTocPanel.value = false
}

// ── TOC panel helpers ─────────────────────────────
function scrollToTop() {
  const main = document.querySelector('main')
  if (main) main.scrollTo({ top: 0, behavior: 'smooth' })
  showTocPanel.value = false
}

function toggleMatrix() {
  matrixCollapsed.value = !matrixCollapsed.value
  showTocPanel.value = false
}

// ── Floating TOC: drag logic ──────────────────────
let tocHasMoved = false
let tocStartX = 0, tocStartY = 0
let tocInitialLeft = 0, tocInitialTop = 0

function updateTocPanelOrigin() {
  const wrapper = tocWrapperRef.value
  if (!wrapper) return
  const rect = wrapper.getBoundingClientRect()
  const isLeft = rect.left < window.innerWidth / 2
  const isTop = rect.top < window.innerHeight / 2

  const classes = []

  if (isTop) { classes.push('top-full', 'mt-4') }
  else { classes.push('bottom-full', 'mb-4') }

  if (isLeft) { 
    classes.push('left-0') 
  } else { 
    classes.push('right-0') 
  }

  const originY = isTop ? 'top' : 'bottom'
  const originX = isLeft ? 'left' : 'right'
  classes.push(`origin-${originY}-${originX}`)
  
  panelPosClasses.value = classes
}

function toggleTocPanel() {
  if (showTocPanel.value) {
    showTocPanel.value = false
  } else {
    updateTocPanelOrigin()
    showTocPanel.value = true
  }
}

function tocPointerDown(e: PointerEvent | MouseEvent | TouchEvent) {
  if ((e.target as HTMLElement).closest('#toc-panel')) return
  e.preventDefault()

  tocHasMoved = false
  const clientX = 'touches' in e ? (e as TouchEvent).touches[0].clientX : (e as MouseEvent).clientX
  const clientY = 'touches' in e ? (e as TouchEvent).touches[0].clientY : (e as MouseEvent).clientY
  tocStartX = clientX
  tocStartY = clientY

  const wrapper = tocWrapperRef.value
  if (!wrapper) return
  const rect = wrapper.getBoundingClientRect()
  tocInitialLeft = rect.left
  tocInitialTop = rect.top

  wrapper.style.left = tocInitialLeft + 'px'
  wrapper.style.top = tocInitialTop + 'px'
  wrapper.style.right = 'auto'
  wrapper.style.bottom = 'auto'

  document.addEventListener('mousemove', tocPointerMove)
  document.addEventListener('mouseup', tocPointerUp)
  document.addEventListener('touchmove', tocPointerMove, { passive: false })
  document.addEventListener('touchend', tocPointerUp)
}

function tocPointerMove(e: MouseEvent | TouchEvent) {
  const clientX = 'touches' in e ? (e as TouchEvent).touches[0].clientX : (e as MouseEvent).clientX
  const clientY = 'touches' in e ? (e as TouchEvent).touches[0].clientY : (e as MouseEvent).clientY

  const dx = clientX - tocStartX
  const dy = clientY - tocStartY

  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
    tocHasMoved = true
    if (showTocPanel.value) toggleTocPanel()

    const wrapper = tocWrapperRef.value
    if (!wrapper) return
    let newLeft = tocInitialLeft + dx
    let newTop = tocInitialTop + dy

    const maxLeft = window.innerWidth - wrapper.offsetWidth
    const maxTop = window.innerHeight - wrapper.offsetHeight
    newLeft = Math.max(0, Math.min(newLeft, maxLeft))
    newTop = Math.max(0, Math.min(newTop, maxTop))

    wrapper.style.left = newLeft + 'px'
    wrapper.style.top = newTop + 'px'
  }
}

function tocPointerUp() {
  document.removeEventListener('mousemove', tocPointerMove)
  document.removeEventListener('mouseup', tocPointerUp)
  document.removeEventListener('touchmove', tocPointerMove)
  document.removeEventListener('touchend', tocPointerUp)

  if (!tocHasMoved) {
    toggleTocPanel()
  }
}

function onTocResize() {
  const wrapper = tocWrapperRef.value
  if (!wrapper || !(wrapper.style.left || wrapper.style.top)) return

  let currentLeft = parseFloat(wrapper.style.left)
  let currentTop = parseFloat(wrapper.style.top)
  const maxLeft = window.innerWidth - wrapper.offsetWidth
  const maxTop = window.innerHeight - wrapper.offsetHeight

  let adjusted = false
  if (currentLeft > maxLeft) { currentLeft = maxLeft; adjusted = true }
  if (currentTop > maxTop) { currentTop = maxTop; adjusted = true }
  if (currentLeft < 0) { currentLeft = 0; adjusted = true }
  if (currentTop < 0) { currentTop = 0; adjusted = true }

  if (adjusted) {
    wrapper.style.left = currentLeft + 'px'
    wrapper.style.top = currentTop + 'px'
    if (showTocPanel.value) updateTocPanelOrigin()
  }
}

// ── TOC ball event binding helper ────────────────
// Called both in onMounted (initial mount) and by the
// watcher (after DOM re-renders when noteId changes).
async function bindTocEvents() {
  await nextTick()
  if (tocBallRef.value) {
    tocBallRef.value.removeEventListener('mousedown', tocPointerDown)
    tocBallRef.value.removeEventListener('touchstart', tocPointerDown)
    tocBallRef.value.addEventListener('mousedown', tocPointerDown)
    tocBallRef.value.addEventListener('touchstart', tocPointerDown, { passive: false })
  }
}

// ── Lifecycle ─────────────────────────────────────
onMounted(async () => {
  window.addEventListener('resize', onTocResize)
})

onUnmounted(() => {
  if (tocBallRef.value) {
    tocBallRef.value.removeEventListener('mousedown', tocPointerDown)
    tocBallRef.value.removeEventListener('touchstart', tocPointerDown)
  }
  window.removeEventListener('resize', onTocResize)
  document.removeEventListener('mousemove', tocPointerMove)
  document.removeEventListener('mouseup', tocPointerUp)
  document.removeEventListener('touchmove', tocPointerMove)
  document.removeEventListener('touchend', tocPointerUp)
})
</script>

<template>
  <div class="relative max-w-[1600px] mx-auto space-y-6">
    <!-- ═══ Loading ═══ -->
    <div v-if="loading" class="flex items-center justify-center py-32">
      <Loader2 class="w-6 h-6 text-blue-400 animate-spin" />
      <span class="ml-3 text-sm text-slate-500">加载笔记中...</span>
    </div>

    <!-- ═══ Error ═══ -->
    <div v-else-if="error" class="glass-panel rounded-2xl p-12 text-center">
      <div class="w-14 h-14 rounded-2xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center mx-auto mb-4">
        <X class="w-6 h-6 text-rose-400" />
      </div>
      <h2 class="text-lg font-bold text-white mb-2">加载失败</h2>
      <p class="text-sm text-slate-400 mb-6">{{ error }}</p>
      <button class="inline-flex items-center px-4 py-2 rounded-xl bg-blue-500/10 border border-blue-500/20 text-blue-400 hover:bg-blue-500 hover:text-white transition-all text-sm font-bold" @click="$router.push('/user/notes')">
        <ArrowLeft class="w-4 h-4 mr-2" /> 返回笔记列表
      </button>
    </div>

    <template v-else-if="note">
      <!-- ═══ Top Action Bar ═══ -->
      <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div class="flex items-center space-x-3">
          <button class="group flex items-center space-x-2 text-slate-400 hover:text-white transition-colors bg-white/5 hover:bg-white/10 px-4 py-2 rounded-xl border border-white/5" @click="$router.push('/user/notes')">
            <ArrowLeft class="w-4 h-4 group-hover:-translate-x-1 transition-transform" />
            <span class="text-sm font-bold">返回列表</span>
          </button>
          <!-- Breadcrumb -->
          <div class="hidden md:flex items-center text-sm font-medium text-slate-400">
            <span class="hover:text-white cursor-pointer transition-colors" @click="$router.push('/user/dashboard')">控制台大盘</span>
            <ChevronRight class="w-4 h-4 mx-1 opacity-50" />
            <span class="hover:text-white cursor-pointer transition-colors" @click="$router.push('/user/notes')">我的笔记</span>
            <ChevronRight class="w-4 h-4 mx-1 opacity-50" />
            <span class="text-blue-300 truncate max-w-[300px]">{{ note.title }}</span>
          </div>
        </div>

        <div class="flex items-center space-x-3 flex-wrap gap-y-2">
          <!-- Status badge -->
          <span v-if="note.status !== undefined" class="inline-flex items-center px-2.5 py-1 rounded-md text-[10px] font-bold uppercase tracking-widest border" :class="getNoteStatusInfo(note.status).cls">
            <component :is="resolveStatusIcon(getNoteStatusInfo(note.status).icon)" class="w-3 h-3 mr-1" />
            {{ getNoteStatusInfo(note.status).label }}
          </span>

          <!-- Action buttons based on status -->
          <template v-if="note.converted">
            <button v-if="note.status === NoteStatusCode.CONVERTED" class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-amber-500/10 border border-amber-500/20 text-amber-400 hover:bg-amber-500 hover:text-white transition-all text-xs font-bold" @click="handleSubmitAudit">
              <ShieldCheck class="w-3.5 h-3.5" /><span>提交审核</span>
            </button>
            <button v-if="note.status === NoteStatusCode.APPROVED" class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 hover:bg-emerald-500 hover:text-white transition-all text-xs font-bold" @click="handlePublish">
              <Globe class="w-3.5 h-3.5" /><span>一键发布</span>
            </button>
            <button v-if="note.status === NoteStatusCode.PUBLISHED" class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-amber-500/10 border border-amber-500/20 text-amber-400 hover:bg-amber-500 hover:text-white transition-all text-xs font-bold" @click="handleUnpublish">
              <X class="w-3.5 h-3.5" /><span>下架笔记</span>
            </button>
            <button v-if="getNoteStatusInfo(note.status).modifiable" class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-blue-500/10 border border-blue-500/20 text-blue-400 hover:bg-blue-500 hover:text-white transition-all text-xs font-bold" @click="showAlert('编辑功能开发中')">
              <FileEdit class="w-3.5 h-3.5" /><span>编辑源文件</span>
            </button>
          </template>
        </div>
      </div>

      <!-- ═══ Dual-Column Layout ═══ -->
      <div class="grid grid-cols-1 xl:grid-cols-4 gap-8 items-start pb-10">
        <!-- Left: Article reading area -->
        <div :class="matrixCollapsed ? 'xl:col-span-4' : 'xl:col-span-3'" class="glass-panel rounded-[2rem] p-8 md:p-14 relative overflow-hidden shadow-2xl transition-all duration-300">
          <!-- Background glow -->
          <div class="absolute top-0 right-0 w-[500px] h-[500px] bg-blue-500/5 blur-[100px] rounded-full pointer-events-none" />

          <!-- Not converted state -->
          <div v-if="!note.converted" class="relative z-10 flex flex-col items-center justify-center py-20 text-center">
            <div class="w-16 h-16 rounded-2xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center mb-5">
              <AlertTriangle class="w-7 h-7 text-amber-400" />
            </div>
            <h3 class="text-xl font-bold text-white mb-2">笔记尚未转换</h3>
            <p class="text-sm text-slate-400 max-w-md mb-6">该笔记尚未生成 HTML 阅读版本。请先确保关联信息完整，然后点击下方按钮进行转换。</p>
            <button class="flex items-center space-x-2 px-5 py-2.5 rounded-xl bg-blue-600/20 hover:bg-blue-600/40 text-blue-400 hover:text-blue-300 border border-blue-500/30 transition-all text-sm font-bold" :disabled="converting" @click="handleConvert">
              <Loader2 v-if="converting" class="w-4 h-4 animate-spin" />
              <RefreshCw v-else class="w-4 h-4" />
              <span>{{ converting ? '转换中...' : '转换笔记' }}</span>
            </button>
          </div>

          <!-- Converted: full reading view -->
          <template v-else>
            <article class="relative z-10 max-w-4xl mx-auto transition-all duration-300">
              <!-- Header Meta -->
              <header class="mb-10 pb-10 border-b border-white/5">
                <div class="flex items-center space-x-3 mb-6 flex-wrap gap-y-2">
                  <span v-if="note.topicName" class="inline-flex items-center px-2.5 py-1 rounded-md text-[11px] font-black uppercase tracking-widest text-indigo-300 bg-indigo-500/10 border border-indigo-500/20">
                    <Layers class="w-3.5 h-3.5 mr-1.5" /> {{ note.topicName }}
                  </span>
                  <span class="text-xs text-slate-500 font-medium flex items-center">
                    <Calendar class="w-3.5 h-3.5 mr-1.5" /> {{ formatDate(note.createTime) }}
                  </span>
                  <span class="text-xs text-slate-500 font-medium flex items-center">
                    <HardDrive class="w-3.5 h-3.5 mr-1.5" /> {{ formatBytes(note.mdFileSize) }}
                  </span>
                </div>

                <h1 class="text-3xl md:text-5xl font-black text-white tracking-tight leading-tight mb-6">
                  {{ note.converted.meta.title || note.title }}
                </h1>

                <div v-if="note.converted.meta.tags?.length" class="flex flex-wrap gap-2">
                  <span v-for="tag in note.converted.meta.tags" :key="tag" class="mini-tag px-3 py-1 rounded-lg text-xs font-bold text-slate-400 border border-white/10 hover:text-white transition-colors cursor-pointer">
                    # {{ tag }}
                  </span>
                </div>
                <div v-else-if="note.tags?.length" class="flex flex-wrap gap-2">
                  <span v-for="tag in note.tags" :key="tag" class="mini-tag px-3 py-1 rounded-lg text-xs font-bold text-slate-400 border border-white/10 hover:text-white transition-colors cursor-pointer">
                    # {{ tag }}
                  </span>
                </div>
              </header>

              <!-- Body content rendered via v-html -->
              <div class="article-content" v-html="note.converted.bodyHtml" @click="handleInternalLinkClick" />
            </article>
          </template>
        </div>

        <!-- Right: Relation matrix sidebar -->
        <aside v-show="!matrixCollapsed" class="xl:col-span-1 sticky top-8 transition-all duration-300 origin-right opacity-100 scale-100">
          <div v-if="hasAnyRelations()" class="glass-panel rounded-2xl overflow-hidden border border-white/10 flex flex-col" :class="note.converted ? 'h-[calc(100vh-10rem)]' : ''">
            <!-- Title bar -->
            <div class="flex items-center justify-between p-4 border-b border-white/5 bg-black/40">
              <span class="text-sm font-bold text-white flex items-center">
                <Network class="w-4 h-4 mr-2 text-indigo-400" />
                资产关联矩阵
              </span>
              <span class="flex h-2 w-2 relative">
                <span v-if="allRelationsIntact()" class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
                <span class="relative inline-flex rounded-full h-2 w-2" :class="allRelationsIntact() ? 'bg-emerald-500' : 'bg-amber-500'" />
              </span>
            </div>

            <!-- Content -->
            <div class="flex-1 overflow-y-auto custom-scrollbar p-5 space-y-8">
              <!-- Integrity banner -->
              <div :class="allRelationsIntact() ? 'bg-emerald-500/10 border-emerald-500/20' : 'bg-amber-500/10 border-amber-500/20'" class="p-3 rounded-xl mb-4 border">
                <div class="flex items-start space-x-2">
                  <CheckCircle2 v-if="allRelationsIntact()" class="w-4 h-4 text-emerald-400 mt-0.5 flex-shrink-0" />
                  <AlertTriangle v-else class="w-4 h-4 text-amber-400 mt-0.5 flex-shrink-0" />
                  <div>
                    <p :class="allRelationsIntact() ? 'text-emerald-300' : 'text-amber-300'" class="text-xs font-bold">
                      {{ allRelationsIntact() ? '关联资产完整' : '存在缺失关联' }}
                    </p>
                    <p :class="allRelationsIntact() ? 'text-emerald-400/70' : 'text-amber-400/70'" class="text-[10px] mt-1">
                      {{ allRelationsIntact() ? '系统验证通过，关联的标签、图片及双向链接均完好无损，无缺失对象。' : `仍有 ${note.missingCount} 项关联缺失，请补全后重新转换。` }}
                    </p>
                  </div>
                </div>
              </div>

              <!-- Tags section -->
              <div v-if="note.tags?.length">
                <h4 class="text-xs font-bold text-slate-400 uppercase tracking-widest mb-3 flex items-center">
                  <Hash class="w-3.5 h-3.5 mr-1.5 text-purple-400" />标签约束 ({{ note.tags.length }})
                </h4>
                <div class="flex flex-wrap gap-2">
                  <span v-for="tag in note.tags" :key="tag" class="mini-tag px-2 py-1 rounded text-xs font-medium text-slate-300 border border-white/10 hover:border-purple-500/50 hover:text-purple-300 cursor-pointer transition-colors">
                    # {{ tag }}
                  </span>
                </div>
              </div>

              <!-- Images section -->
              <div v-if="note.images?.length">
                <h4 class="text-xs font-bold text-slate-400 uppercase tracking-widest mb-3 flex items-center">
                  <ImageIcon class="w-3.5 h-3.5 mr-1.5 text-sky-400" />图床引用 ({{ note.images.length }})
                </h4>
                <div class="space-y-2">
                  <div v-for="img in note.images" :key="img.imageId" class="flex items-center justify-between bg-black/20 p-2.5 rounded-xl border group transition-colors cursor-pointer" :class="isMissingImg(img) ? 'border-amber-500/30 hover:border-amber-500/50' : 'border-white/5 hover:border-sky-500/30'">
                    <div class="flex items-center space-x-2 overflow-hidden">
                      <div class="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0" :class="isMissingImg(img) ? 'bg-amber-500/10 text-amber-400' : 'bg-sky-500/10 text-sky-400'">
                        <ImageIcon class="w-4 h-4" />
                      </div>
                      <div class="flex flex-col min-w-0">
                        <span class="text-xs truncate" :class="isMissingImg(img) ? 'text-amber-400' : 'text-slate-300 group-hover:text-sky-300 transition-colors'">
                          {{ img.filename || img.parsedImageName }}
                        </span>
                      </div>
                    </div>
                    <CheckCircle2 v-if="!isMissingImg(img)" class="w-4 h-4 text-emerald-500 flex-shrink-0" title="引用完好且审核通过" />
                    <AlertTriangle v-else class="w-4 h-4 text-amber-500 flex-shrink-0" title="图片缺失" />
                  </div>
                </div>
              </div>

              <!-- Each-notes (bidirectional links) section -->
              <div v-if="note.eachNotes?.length">
                <h4 class="text-xs font-bold text-slate-400 uppercase tracking-widest mb-3 flex items-center">
                  <Link class="w-3.5 h-3.5 mr-1.5 text-emerald-400" />数字双链 ({{ note.eachNotes.length }})
                </h4>
                <div class="space-y-2">
                  <div v-for="each in note.eachNotes" :key="each.targetNoteId" class="flex items-center justify-between bg-black/20 p-2.5 rounded-xl border group transition-colors cursor-pointer" :class="isMissingLink(each) ? 'border-amber-500/30 hover:border-amber-500/50' : 'border-white/5 hover:border-emerald-500/30'" @click="handleEachNoteClick(each)">
                    <div class="flex items-center space-x-2 overflow-hidden">
                      <div class="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0" :class="isMissingLink(each) ? 'bg-amber-500/10 text-amber-400' : 'bg-emerald-500/10 text-emerald-400'">
                        <FileText class="w-4 h-4" />
                      </div>
                      <div class="flex flex-col min-w-0">
                        <span class="text-xs font-medium truncate" :class="isMissingLink(each) ? 'text-amber-400' : 'text-slate-300 group-hover:text-emerald-300 transition-colors'">
                          {{ each.targetNoteTitle || each.parsedNoteName || each.nickname || '未命名笔记' }}
                        </span>
                        <span v-if="each.anchor" class="text-[9px] text-slate-500 mt-0.5 truncate">→ {{ each.anchor }}</span>
                      </div>
                    </div>
                    <CheckCircle2 v-if="!isMissingLink(each)" class="w-4 h-4 text-emerald-500 flex-shrink-0" title="双链目标存在且过审" />
                    <AlertTriangle v-else class="w-4 h-4 text-amber-500 flex-shrink-0" title="目标笔记缺失" />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Isolated placeholder (when no relations) -->
          <div v-else class="glass-panel flex flex-col items-center justify-center text-slate-500 rounded-2xl border border-white/10" :class="note.converted ? 'h-[calc(100vh-10rem)]' : 'h-64'">
            <div class="p-4 rounded-full bg-white/5 mb-3">
              <Layers class="w-8 h-8 opacity-20" />
            </div>
            <p class="text-xs font-bold uppercase tracking-widest">孤立节点 (Isolated)</p>
            <p class="text-[10px] mt-1 opacity-50 text-center max-w-[150px]">这篇笔记目前没有任何关联资产</p>
          </div>
        </aside>
      </div>

      <!-- ═══ Floating TOC ═══ -->
      <!-- Bug 1 fix: changed from v-if="note?.converted?.tocHtml" to v-if="note?.converted"
           so the floating ball stays visible even when tocHtml is empty,
           ensuring 返回顶部 / 资产矩阵 quick actions are always accessible. -->
      <Teleport to="body">
        <div v-if="note?.converted" ref="tocWrapperRef" id="toc-draggable" class="fixed z-50 right-8 bottom-12 touch-none">
          <!-- TOC Panel -->
          <div ref="tocPanelRef" id="toc-panel" class="absolute w-64 glass-panel rounded-2xl p-5 transition-all duration-300 max-h-[60vh] overflow-y-auto custom-scrollbar shadow-[0_15px_40px_rgba(0,0,0,0.5)] border border-blue-500/30 bg-[#020617]/95"
            :class="[panelPosClasses, showTocPanel ? 'scale-100 opacity-100 pointer-events-auto' : 'scale-0 opacity-0 pointer-events-none']">
            <h4 class="text-xs font-black text-slate-400 uppercase tracking-widest mb-4 flex items-center border-b border-white/5 pb-3">
              <ListTree class="w-4 h-4 mr-2 text-blue-400" /> 文档快速导航
            </h4>
            <!-- Quick action buttons — always visible -->
            <div class="flex items-center space-x-2 mb-4 pb-3" :class="{ 'border-b border-white/5': note.converted.tocHtml }">
              <button class="flex-1 py-1.5 rounded-lg bg-white/5 hover:bg-blue-500/20 text-slate-400 hover:text-blue-300 text-xs font-bold transition-colors flex items-center justify-center" @click="scrollToTop">
                <ArrowUpToLine class="w-3.5 h-3.5 mr-1" /> 返回顶部
              </button>
              <button class="flex-1 py-1.5 rounded-lg bg-white/5 hover:bg-purple-500/20 text-slate-400 hover:text-purple-300 text-xs font-bold transition-colors flex items-center justify-center" @click="toggleMatrix">
                <LayoutPanelTop class="w-3.5 h-3.5 mr-1" /> 资产矩阵
              </button>
            </div>
            <!-- tocHtml rendered (only when there's actual TOC content) -->
            <ul v-if="note.converted.tocHtml" id="toc-list" class="toc-list space-y-3 relative before:content-[''] before:absolute before:left-[3px] before:top-2 before:bottom-2 before:w-[1px] before:bg-white/10" v-html="note.converted.tocHtml" @click="onTocLinkClick" />
            <!-- Empty TOC hint -->
            <p v-else class="text-[10px] text-slate-500 text-center py-2">暂无目录结构</p>
          </div>

          <!-- Draggable ball -->
          <div ref="tocBallRef" id="toc-ball" class="w-12 h-12 rounded-full glass-panel flex items-center justify-center cursor-pointer shadow-[0_0_20px_rgba(59,130,246,0.2)] hover:shadow-[0_0_25px_rgba(59,130,246,0.4)] text-blue-400 border border-blue-500/40 transition-[box-shadow,transform] active:scale-95 bg-[#020617]/90 group">
            <ListTree class="w-5 h-5 transition-transform group-hover:scale-110" />
            <div class="absolute inset-0 rounded-full border border-blue-400/40 animate-ping opacity-20 pointer-events-none" />
          </div>
        </div>
      </Teleport>
    </template>
  </div>
</template>

<style scoped>
/* ── Glass panel ── */
.glass-panel {
  background: rgba(255, 255, 255, 0.02);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.05);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.05);
}

/* ── Mini tag ── */
.mini-tag {
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.08) 0%, rgba(255, 255, 255, 0.02) 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.1);
}

/* ── Scrollbar ── */
.custom-scrollbar::-webkit-scrollbar { width: 4px; height: 4px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.1); border-radius: 10px; }
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: rgba(59, 130, 246, 0.5); }

/* ── TOC list styles ── */
.toc-list :deep(li) { position: relative; padding-left: 1rem; }
.toc-list :deep(li) > div:first-child {
  position: absolute; left: -2px; top: 0.375rem;
  width: 0.625rem; height: 0.625rem;
  border-radius: 50%; background: #3b82f6;
  border: 2px solid #020617;
}
.toc-list :deep(a) {
  font-size: 0.875rem; font-weight: 500;
  color: #94a3b8; text-decoration: none;
  display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  transition: color 0.2s;
}
.toc-list :deep(a:hover) { color: #f8fafc; }
.toc-list :deep(li.active > a) { color: #60a5fa; }
.toc-list :deep(ul) { margin-top: 0.5rem; padding-left: 0.75rem; border-left: 1px solid rgba(255,255,255,0.05); }
.toc-list :deep(ul li) { position: relative; padding-left: 0.75rem; }
.toc-list :deep(ul li) > div:first-child {
  position: absolute; left: 0; top: 0.5rem;
  width: 0.375rem; height: 0.375rem;
  border-radius: 50%; background: rgba(255,255,255,0.2);
  border: none;
}
.toc-list :deep(ul a) { font-size: 0.75rem; color: #64748b; }
.toc-list :deep(ul a:hover) { color: #cbd5e1; }

/* ═══ Article Content Typography ═══ */
.article-content { color: #cbd5e1; font-size: 1rem; line-height: 1.8; }
.article-content :deep(h2) {
  font-size: 1.5rem; font-weight: 800; margin-top: 2.5rem; margin-bottom: 1rem;
  color: #f8fafc; display: flex; align-items: center;
}
.article-content :deep(h2::before) { content: '#'; color: #3b82f6; margin-right: 0.5rem; opacity: 0.6; font-size: 1.2rem; }
.article-content :deep(h3) { font-size: 1.25rem; font-weight: 700; margin-top: 2rem; margin-bottom: 1rem; color: #e2e8f0; }
.article-content :deep(p) { margin-bottom: 1.25rem; }
.article-content :deep(strong) {
  color: #f8fafc; font-weight: 600;
  background: rgba(59, 130, 246, 0.15); padding: 0.1em 0.3em; border-radius: 4px;
}
.article-content :deep(blockquote) {
  border-left: 4px solid #3b82f6;
  background: linear-gradient(90deg, rgba(59, 130, 246, 0.1) 0%, rgba(59, 130, 246, 0.02) 100%);
  padding: 1rem 1.5rem; border-radius: 0 12px 12px 0; margin-bottom: 1.5rem;
  color: #94a3b8; font-style: italic;
}
.article-content :deep(pre) {
  background: #0b0f19; border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 12px;
  padding: 1.25rem; overflow-x: auto; margin-bottom: 1.5rem; margin-top: 1rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.875rem; color: #e2e8f0; position: relative;
  box-shadow: inset 0 2px 10px rgba(0, 0, 0, 0.5);
}
.article-content :deep(:not(pre) > code) {
  background: rgba(255, 255, 255, 0.1); padding: 0.2em 0.4em; border-radius: 6px;
  font-size: 0.85em; color: #93c5fd; font-family: monospace;
  border: 1px solid rgba(255, 255, 255, 0.05);
}
.article-content :deep(pre code) { background: transparent; padding: 0; border: none; color: inherit; }
.article-content :deep(img) {
  max-width: 100%; height: auto; border-radius: 12px; margin: 2rem auto; display: block;
  border: 1px solid rgba(255, 255, 255, 0.1); box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
}

/* ── Internal note link ── */
.article-content :deep(.internal-note-link) {
  color: #60a5fa; text-decoration: underline;
  text-underline-offset: 4px; text-decoration-color: rgba(59, 130, 246, 0.3);
  cursor: pointer; transition: color 0.2s;
}
.article-content :deep(.internal-note-link:hover) { color: #93c5fd; }

/* ── Animate ping── */
@keyframes ping { 75%, 100% { transform: scale(2); opacity: 0; } }
.animate-ping { animation: ping 1s cubic-bezier(0, 0, 0.2, 1) infinite; }
</style>
