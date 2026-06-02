<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminApi, type AdminNoteItem } from '@/api/admin'
import { getNoteStatusInfo, NoteStatusCode, type NoteBacklinkVO } from '@/api/notes'
import { enhanceArticleContent } from '@/utils/enhanceArticle'
import {
  ArrowLeft, Globe, Calendar, HardDrive, Layers, Hash, ImageIcon, Link,
  Network, CheckCircle2, AlertTriangle, ListTree, ArrowUpToLine,
  LayoutPanelTop, Loader2, RefreshCw, X, ChevronRight, FileText, Trash2, Clock
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()

// ── State ─────────────────────────────────────────
const loading = ref(true)
const note = ref<AdminNoteItem | null>(null)
const error = ref<string | null>(null)
const converting = ref(false)
const matrixCollapsed = ref(false)

// Floating TOC state
const showTocPanel = ref(false)
const tocWrapperRef = ref<HTMLElement | null>(null)
const tocBallRef = ref<HTMLElement | null>(null)
const tocPanelRef = ref<HTMLElement | null>(null)

// 笔记正文容器 ref —— 用于在 v-html 更新后局部增强文章内容
const articleContentRef = ref<HTMLElement | null>(null)

// 动态管理悬浮面板的方向和定位
const panelPosClasses = ref(['bottom-full', 'right-0', 'mb-4', 'origin-bottom-right'])

// ── Backlinks (反向引用) — lazy fetched on click ──
const backlinks = ref<NoteBacklinkVO[]>([])
const backlinksLoading = ref(false)
const backlinksError = ref<string | null>(null)
const backlinksExpanded = ref(false)
const backlinksFetched = ref(false)

async function fetchBacklinks() {
  const noteId = Number(route.params.noteId)
  if (!noteId || isNaN(noteId)) return
  backlinksLoading.value = true
  backlinksError.value = null
  try {
    backlinks.value = await adminApi.getNoteBacklinks(noteId)
    backlinksFetched.value = true
  } catch (e: any) {
    backlinksError.value = e?.message || '加载反向引用失败'
  } finally {
    backlinksLoading.value = false
  }
}

async function toggleBacklinks() {
  if (backlinksExpanded.value) {
    backlinksExpanded.value = false
    return
  }
  backlinksExpanded.value = true
  if (!backlinksFetched.value) {
    await fetchBacklinks()
  }
}

function handleBacklinkClick(b: NoteBacklinkVO) {
  if (b.anchor) {
    router.push(`/admin/notes/${b.sourceNoteId}#${b.anchor}`)
  } else {
    router.push(`/admin/notes/${b.sourceNoteId}`)
  }
}

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

const allRelationsIntact = computed(() => {
  if (!note.value) return false
  return note.value.missingCount === 0
})

// ── Actions ───────────────────────────────────────
async function handleConvert() {
  if (!note.value || converting.value) return
  converting.value = true
  try {
    await adminApi.convertNote(note.value.id)
    showAlert('转换指令已下达')
    await fetchNote()
    await nextTick()
    await bindTocEvents()
  } catch {
    showAlert('转换指令执行失败')
  } finally {
    converting.value = false
  }
}

async function handleDelete() {
  if (!note.value) return
  if (!showConfirm('确定删除该笔记吗？管理端删除操作不可撤回')) return
  try {
    await adminApi.deleteNotes([note.value.id])
    showAlert('笔记已删除')
    router.push('/admin/notes')
  } catch { showAlert('删除失败') }
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
    const res = await adminApi.getNoteInfo(noteId)
    note.value = res as unknown as AdminNoteItem
    scrollToHashAnchor()
  } catch (e: any) {
    error.value = e?.message || '加载笔记详情失败'
  } finally {
    loading.value = false
  }
}

// ── Anchor scrolling ──────────────────────────────
function scrollToHashAnchor() {
  const hash = route.hash
  if (!hash) return
  try {
    const id = decodeURIComponent(hash.replace('#', ''))
    if (!id) return
    nextTick(() => {
      setTimeout(() => {
        let el = document.getElementById(id)
        if (!el) el = document.getElementById(id.toLowerCase().replace(/\s+/g, '-'))
        if (!el) el = document.getElementById(id.replace(/\s+/g, '-'))
        if (el) {
          el.scrollIntoView({ behavior: 'smooth' })
        }
      }, 150)
    })
  } catch (e) {
    console.error('Hash scroll error', e)
  }
}

watch(
  () => route.hash,
  (newHash, oldHash) => {
    if (newHash && newHash !== oldHash && !loading.value) {
      scrollToHashAnchor()
    }
  }
)

watch(() => route.params.noteId, async (newId, oldId) => {
  if (newId === oldId || !newId) return
  loading.value = true
  error.value = null
  note.value = null
  showTocPanel.value = false
  backlinks.value = []
  backlinksExpanded.value = false
  backlinksFetched.value = false
  backlinksError.value = null
  window.scrollTo(0, 0)

  await fetchNote()
  await nextTick()
  await bindTocEvents()
}, { immediate: true })

// ── 文章内容增强（表格、代码高亮、Mermaid） ──────
// 后端把 Markdown 转成 HTML，v-html 写入 DOM 后还需要后续增强。
// watch bodyHtml 在 nextTick 后对 articleContentRef 范围内执行：
//   1. 表格包装  2. 代码语法高亮  3. Mermaid 渲染
watch(
  () => note.value?.converted?.bodyHtml,
  async (html) => {
    if (!html) return
    await nextTick()
    await enhanceArticleContent(articleContentRef.value)
  },
  { immediate: true }
)

// ── Floating TOC Logic ────────────────────────────
function onTocLinkClick(e: Event) {
  const link = (e.target as HTMLElement).closest('a[href^="#"]') as HTMLAnchorElement | null
  if (!link) return
  e.preventDefault()
  const rawId = link.getAttribute('href')?.slice(1)
  if (rawId) {
    try {
      const id = decodeURIComponent(rawId)
      let el = document.getElementById(id) || document.getElementById(rawId)
      if (el) {
        el.scrollIntoView({ behavior: 'smooth' })
        router.replace({ hash: `#${rawId}` })
      }
    } catch (err) {
      const el = document.getElementById(rawId)
      if (el) el.scrollIntoView({ behavior: 'smooth' })
    }
  }
  showTocPanel.value = false
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
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

  if (isLeft) { classes.push('left-0') } 
  else { classes.push('right-0') }

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

async function bindTocEvents() {
  await nextTick()
  if (tocBallRef.value) {
    tocBallRef.value.removeEventListener('mousedown', tocPointerDown)
    tocBallRef.value.removeEventListener('touchstart', tocPointerDown)
    tocBallRef.value.addEventListener('mousedown', tocPointerDown)
    tocBallRef.value.addEventListener('touchstart', tocPointerDown, { passive: false })
  }
}

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
  <div class="relative max-w-[1600px] mx-auto space-y-6 px-4 md:px-8 py-6">
    <!-- ═══ Loading ═══ -->
    <div v-if="loading" class="flex items-center justify-center py-32">
      <Loader2 class="w-6 h-6 text-rose-400 animate-spin" />
      <span class="ml-3 text-sm text-slate-500 font-bold uppercase tracking-widest">检索资产详情...</span>
    </div>

    <!-- ═══ Error ═══ -->
    <div v-else-if="error" class="glass-panel rounded-2xl p-12 text-center">
      <div class="w-14 h-14 rounded-2xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center mx-auto mb-4">
        <X class="w-6 h-6 text-rose-400" />
      </div>
      <h2 class="text-lg font-bold text-white mb-2">详情获取失败</h2>
      <p class="text-sm text-slate-400 mb-6">{{ error }}</p>
      <button class="px-4 py-2 rounded-xl bg-white/5 border border-white/10 text-slate-300 hover:bg-white/10 transition-all flex items-center mx-auto" @click="$router.push('/admin/notes')">
        <ArrowLeft class="w-4 h-4 mr-2" /> 返回全局列表
      </button>
    </div>

    <template v-else-if="note">
      <!-- ═══ Top Action Bar ═══ -->
      <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div class="flex items-center space-x-3">
          <button class="group flex items-center space-x-2 text-slate-400 hover:text-white transition-colors bg-white/5 hover:bg-white/10 px-4 py-2 rounded-xl border border-white/5" @click="$router.push('/admin/notes')">
            <ArrowLeft class="w-4 h-4 group-hover:-translate-x-1 transition-transform" />
            <span class="text-sm font-bold">返回管理列表</span>
          </button>
          <div class="hidden md:flex items-center text-sm font-medium text-slate-400">
            <span>管理大盘</span>
            <ChevronRight class="w-4 h-4 mx-1 opacity-50" />
            <span class="hover:text-white cursor-pointer" @click="$router.push('/admin/notes')">全局笔记</span>
            <ChevronRight class="w-4 h-4 mx-1 opacity-50" />
            <span class="text-rose-300 truncate max-w-[300px]">{{ note.title }}</span>
          </div>
        </div>

        <div class="flex items-center space-x-3 flex-wrap gap-y-2">
          <span class="inline-flex items-center px-2.5 py-1 rounded-md text-[10px] font-bold uppercase tracking-widest border" :class="getNoteStatusInfo(note.status).cls">
            <component :is="resolveStatusIcon(getNoteStatusInfo(note.status).icon)" class="w-3 h-3 mr-1" />
            {{ getNoteStatusInfo(note.status).label }}
          </span>

          <button class="px-3 py-1.5 rounded-xl bg-rose-500/10 text-rose-400 border border-rose-500/20 text-xs font-bold hover:bg-rose-500 hover:text-white transition-all flex items-center" @click="handleDelete">
            <Trash2 class="w-3.5 h-3.5 mr-1.5" /><span>强制删除</span>
          </button>
          
          <button v-if="note.status === NoteStatusCode.READY_TO_CONVERT" class="px-3 py-1.5 rounded-xl bg-rose-500 text-white shadow-lg shadow-rose-500/20 text-xs font-bold hover:bg-rose-600 transition-all flex items-center" @click="handleConvert">
            <RefreshCw class="w-3.5 h-3.5 mr-1.5" /><span>重新转换</span>
          </button>
        </div>
      </div>

      <!-- ═══ Dual-Column Layout ═══ -->
      <div class="detail-grid grid grid-cols-1 gap-8 items-start pb-10" :class="matrixCollapsed ? 'detail-grid--collapsed' : 'detail-grid--expanded'">
        
        <!-- Left: Article reading area -->
        <div class="detail-main glass-panel rounded-[2rem] p-8 md:p-14 relative overflow-hidden shadow-2xl transition-all duration-300">
          <div class="absolute top-0 right-0 w-[500px] h-[500px] bg-rose-500/5 blur-[100px] rounded-full pointer-events-none" />

          <div v-if="!note.converted" class="py-20 text-center">
            <AlertTriangle class="w-12 h-12 text-amber-400 mx-auto mb-4" />
            <h3 class="text-xl font-bold text-white mb-2">内容未就绪</h3>
            <p class="text-sm text-slate-400 mb-6">该笔记尚未生成 HTML 预览版本。</p>
          </div>

          <article v-else class="relative z-10 max-w-4xl mx-auto transition-all duration-300">
            <header class="mb-10 pb-10 border-b border-white/5">
              <div class="flex items-center space-x-3 mb-6 flex-wrap gap-y-2 text-slate-500 text-[10px] uppercase font-bold tracking-widest">
                <span class="bg-indigo-500/10 text-indigo-300 px-2.5 py-1 rounded-lg border border-indigo-500/20">
                  <Layers class="w-3.5 h-3.5 inline mr-1" /> {{ note.topicName || '未归属' }}
                </span>
                <span class="flex items-center"><Calendar class="w-3.5 h-3.5 mr-1.5 opacity-60" /> {{ formatDate(note.createTime) }}</span>
                <span class="flex items-center"><HardDrive class="w-3.5 h-3.5 mr-1.5 opacity-60" /> {{ formatBytes(note.mdFileSize) }}</span>
                <span class="text-slate-400 font-mono">UID: {{ note.userId }}</span>
              </div>
              <h1 class="text-3xl md:text-5xl font-black text-white tracking-tight leading-tight mb-6">
                {{ note.converted.meta.title || note.title }}
              </h1>
              <div class="flex flex-wrap gap-2">
                <span v-for="tag in note.converted.meta.tags" :key="tag" class="mini-tag px-3 py-1 rounded-lg text-[10px] font-black text-slate-400 border border-white/10 uppercase tracking-tighter hover:text-rose-400 transition-colors cursor-pointer">
                  # {{ tag }}
                </span>
              </div>
            </header>

            <div ref="articleContentRef" class="article-content" v-html="note.converted.bodyHtml" />
          </article>
        </div>

        <!-- Right: Relation matrix sidebar -->
        <aside class="detail-matrix xl:sticky xl:top-8" :class="matrixCollapsed ? 'detail-matrix--collapsed' : 'detail-matrix--expanded'" :aria-hidden="matrixCollapsed">
          <Transition name="matrix-panel">
            <div v-show="!matrixCollapsed" class="detail-matrix-inner">
              <div class="glass-panel rounded-2xl overflow-hidden border border-white/10 flex flex-col h-[calc(100vh-10rem)]">
                <!-- Title bar -->
                <div class="p-4 border-b border-white/5 bg-black/40 flex items-center justify-between">
                  <span class="text-sm font-bold text-white flex items-center">
                    <Network class="w-4 h-4 mr-2 text-rose-400" /> 资源关联审查
                  </span>
                  <span class="flex h-2 w-2 relative">
                    <span v-if="allRelationsIntact" class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
                    <span class="relative inline-flex rounded-full h-2 w-2" :class="allRelationsIntact ? 'bg-emerald-500' : 'bg-rose-500'" />
                  </span>
                </div>

                <!-- Content -->
                <div class="flex-1 overflow-y-auto custom-scrollbar p-5 space-y-8">
                  <!-- Integrity banner -->
                  <div :class="allRelationsIntact ? 'bg-emerald-500/10 border-emerald-500/20' : 'bg-rose-500/10 border-rose-500/20'" class="p-3 rounded-xl border transition-colors">
                    <p class="text-xs font-bold" :class="allRelationsIntact ? 'text-emerald-300' : 'text-rose-300'">
                      {{ allRelationsIntact ? '资源链路完整' : '检测到链路断裂' }}
                    </p>
                    <p class="text-[10px] mt-1 opacity-70 leading-relaxed" :class="allRelationsIntact ? 'text-emerald-400' : 'text-rose-400'">
                      {{ allRelationsIntact ? '全站扫描未发现缺失引用的图片、标签或双链笔记。' : `当前笔记引用了 ${note.missingCount} 个不存在的站内资源。` }}
                    </p>
                  </div>

                  <!-- Tags section -->
                  <div v-if="note.tags?.length">
                    <h4 class="text-[10px] font-black text-slate-500 uppercase tracking-[0.2em] mb-3 flex items-center">
                      <Hash class="w-3.5 h-3.5 mr-1.5 text-purple-400" />标签关系 ({{ note.tags.length }})
                    </h4>
                    <div class="flex flex-wrap gap-2">
                      <span v-for="tag in note.tags" :key="tag" class="mini-tag px-2 py-1 rounded text-[10px] font-bold text-slate-300 border border-white/10 hover:border-purple-500/50 hover:text-purple-300 cursor-pointer transition-colors">
                        # {{ tag }}
                      </span>
                    </div>
                  </div>

                  <!-- Images section -->
                  <div v-if="note.images?.length">
                    <h4 class="text-[10px] font-black text-slate-500 uppercase tracking-[0.2em] mb-3 flex items-center">
                      <ImageIcon class="w-3.5 h-3.5 mr-1.5 text-sky-400" />引用图片 ({{ note.images.length }})
                    </h4>
                    <div class="space-y-2">
                      <div v-for="img in note.images" :key="img.imageId" class="flex items-center justify-between bg-black/20 p-2.5 rounded-xl border transition-colors group cursor-pointer" :class="img.isMissing ? 'border-rose-500/30 hover:border-rose-500/50' : 'border-white/5 hover:border-sky-500/30'">
                        <span class="text-xs truncate max-w-[140px]" :class="img.isMissing ? 'text-rose-400' : 'text-slate-300 group-hover:text-sky-300'">
                          {{ img.filename || img.parsedImageName }}
                        </span>
                        <AlertTriangle v-if="img.isMissing" class="w-3.5 h-3.5 text-rose-500 flex-shrink-0" />
                        <CheckCircle2 v-else class="w-3.5 h-3.5 text-emerald-500 flex-shrink-0" />
                      </div>
                    </div>
                  </div>

                  <!-- Links section -->
                  <div v-if="note.eachNotes?.length">
                    <h4 class="text-[10px] font-black text-slate-500 uppercase tracking-[0.2em] mb-3 flex items-center">
                      <Link class="w-3.5 h-3.5 mr-1.5 text-emerald-400" />关联笔记 ({{ note.eachNotes.length }})
                    </h4>
                    <div class="space-y-2">
                      <div v-for="link in note.eachNotes" :key="link.targetNoteId" class="flex items-center justify-between bg-black/20 p-2.5 rounded-xl border transition-colors group cursor-pointer" :class="link.isMissing ? 'border-rose-500/30 hover:border-rose-500/50' : 'border-white/5 hover:border-emerald-500/30'">
                        <span class="text-xs truncate max-w-[140px]" :class="link.isMissing ? 'text-rose-400' : 'text-slate-300 group-hover:text-emerald-300'">
                          {{ link.targetNoteTitle || link.parsedNoteName }}
                        </span>
                        <AlertTriangle v-if="link.isMissing" class="w-3.5 h-3.5 text-rose-500 flex-shrink-0" />
                        <CheckCircle2 v-else class="w-3.5 h-3.5 text-emerald-500 flex-shrink-0" />
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Backlinks (反向引用) — Admin lazy-load -->
              <div class="mt-4 glass-panel rounded-2xl border border-white/10 overflow-hidden">
                <button
                  class="w-full flex items-center justify-between px-4 py-3 text-left hover:bg-rose-500/5 transition-colors group"
                  :class="backlinksExpanded ? 'border-b border-white/5 bg-rose-500/5' : ''"
                  @click="toggleBacklinks"
                >
                  <span class="flex items-center gap-2 text-sm font-bold text-white">
                    <Link class="w-4 h-4 text-rose-400" />
                    反向引用
                    <span v-if="backlinksFetched" class="px-1.5 py-0.5 rounded text-[10px] font-black bg-rose-500/10 text-rose-300 border border-rose-500/20">{{ backlinks.length }}</span>
                    <span v-else class="text-[10px] text-slate-500 font-normal">点击加载</span>
                  </span>
                  <ChevronRight class="w-4 h-4 text-slate-400 transition-transform" :class="backlinksExpanded ? 'rotate-90 text-rose-300' : 'group-hover:text-rose-400'" />
                </button>
                <Transition name="fade">
                  <div v-if="backlinksExpanded" class="p-3 max-h-[40vh] overflow-y-auto custom-scrollbar space-y-2">
                    <div v-if="backlinksLoading" class="text-xs text-slate-500 text-center py-4 flex items-center justify-center gap-2">
                      <Loader2 class="w-3.5 h-3.5 animate-spin" /> 加载中...
                    </div>
                    <div v-else-if="backlinksError" class="text-xs text-rose-400 text-center py-4">{{ backlinksError }}</div>
                    <div v-else-if="!backlinks.length" class="text-xs text-slate-500 text-center py-4">暂无笔记引用此篇</div>
                    <div v-else>
                      <div
                        v-for="b in backlinks"
                        :key="b.sourceNoteId"
                        class="flex items-center justify-between bg-black/20 p-2.5 rounded-xl border border-white/5 hover:border-rose-500/30 group transition-colors cursor-pointer mb-2 last:mb-0"
                        @click="handleBacklinkClick(b)"
                      >
                        <div class="flex items-center space-x-2 overflow-hidden">
                          <div class="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 bg-rose-500/10 text-rose-400">
                            <FileText class="w-4 h-4" />
                          </div>
                          <div class="flex flex-col min-w-0">
                            <span class="text-xs font-medium truncate text-slate-300 group-hover:text-rose-300 transition-colors">
                              {{ b.sourceNoteTitle }}
                            </span>
                            <span class="text-[9px] text-slate-500 mt-0.5 truncate">via [[{{ b.parsedNoteName }}{{ b.anchor ? '#' + b.anchor : '' }}]]</span>
                          </div>
                        </div>
                        <div class="flex items-center gap-1.5 shrink-0 ml-2">
                          <span class="text-[9px] font-bold px-1.5 py-0.5 rounded border" :class="getNoteStatusInfo(b.sourceNoteStatus).cls">{{ getNoteStatusInfo(b.sourceNoteStatus).label }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </Transition>
              </div>
            </div>
          </Transition>
        </aside>
      </div>

      <!-- ═══ Floating TOC ═══ -->
      <Teleport to="body">
        <div v-if="note?.converted" ref="tocWrapperRef" id="toc-draggable" class="fixed z-50 right-8 bottom-12 touch-none">
          <!-- TOC Panel -->
          <div ref="tocPanelRef" id="toc-panel" class="absolute w-64 glass-panel rounded-2xl p-5 transition-all duration-300 max-h-[60vh] overflow-y-auto custom-scrollbar shadow-[0_15px_40px_rgba(0,0,0,0.5)] border border-rose-500/30 bg-[#020617]/95"
            :class="[panelPosClasses, showTocPanel ? 'scale-100 opacity-100 pointer-events-auto' : 'scale-0 opacity-0 pointer-events-none']">
            <h4 class="text-xs font-black text-slate-400 uppercase tracking-widest mb-4 flex items-center border-b border-white/5 pb-3">
              <ListTree class="w-4 h-4 mr-2 text-rose-400" /> 文档快速导航
            </h4>
            <!-- Quick action buttons -->
            <div class="flex items-center space-x-2 mb-4 pb-3" :class="{ 'border-b border-white/5': note.converted.tocHtml }">
              <button class="flex-1 py-1.5 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-300 text-xs font-bold transition-colors flex items-center justify-center" @click="scrollToTop">
                <ArrowUpToLine class="w-3.5 h-3.5 mr-1" /> 返回顶部
              </button>
              <button class="flex-1 py-1.5 rounded-lg bg-white/5 hover:bg-purple-500/20 text-slate-400 hover:text-purple-300 text-xs font-bold transition-colors flex items-center justify-center" @click="toggleMatrix">
                <LayoutPanelTop class="w-3.5 h-3.5 mr-1" /> 资产矩阵
              </button>
            </div>
            <!-- tocHtml rendered -->
            <div v-if="note.converted.tocHtml" id="toc-list" class="toc-list" v-html="note.converted.tocHtml" @click="onTocLinkClick" />
            <p v-else class="text-[10px] text-slate-500 text-center py-2">暂无目录结构</p>
          </div>

          <!-- Draggable ball -->
          <div ref="tocBallRef" id="toc-ball" class="w-12 h-12 rounded-full glass-panel flex items-center justify-center cursor-pointer shadow-[0_0_20px_rgba(244,63,94,0.2)] hover:shadow-[0_0_25px_rgba(244,63,94,0.4)] text-rose-400 border border-rose-500/40 transition-[box-shadow,transform] active:scale-95 bg-[#020617]/90 group">
            <ListTree class="w-5 h-5 transition-transform group-hover:scale-110" />
            <div class="absolute inset-0 rounded-full border border-rose-400/40 animate-ping opacity-20 pointer-events-none" />
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

.detail-grid,
.detail-main,
.detail-matrix {
  min-width: 0;
}

.detail-matrix {
  overflow: hidden;
  transform-origin: top right;
  transition:
    opacity 0.4s ease-out,
    transform 0.5s cubic-bezier(0.4, 0, 0.2, 1);
}

.detail-matrix-inner {
  min-width: 0;
}

@media (min-width: 1280px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1fr);
    column-gap: 2rem;
    transition:
      grid-template-columns 0.5s cubic-bezier(0.4, 0, 0.2, 1),
      column-gap 0.5s cubic-bezier(0.4, 0, 0.2, 1);
  }

  .detail-grid--expanded {
    grid-template-columns: minmax(0, 3fr) minmax(18rem, 1fr);
  }

  .detail-grid--collapsed {
    grid-template-columns: minmax(0, 1fr) 0fr;
    column-gap: 0;
  }

  .detail-matrix--expanded {
    opacity: 1;
    transform: translateX(0);
  }

  .detail-matrix--collapsed {
    opacity: 0;
    transform: translateX(0);
    pointer-events: none;
  }
}

.matrix-panel-enter-active,
.matrix-panel-leave-active {
  transition:
    opacity 0.4s ease-out,
    transform 0.5s cubic-bezier(0.4, 0, 0.2, 1);
}

.matrix-panel-enter-from,
.matrix-panel-leave-to {
  opacity: 0;
  transform: translateX(0); 
}

/* ── Scrollbar ── */
.custom-scrollbar::-webkit-scrollbar { width: 4px; height: 4px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.1); border-radius: 10px; }
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: rgba(244, 63, 94, 0.5); }

/* ── TOC list styles ── */
.toc-list :deep(.toc-sidebar) { display: contents; }
.toc-list :deep(.toc-header)  { display: none; }
.toc-list :deep(.toc-fab)     { display: none; }
.toc-list :deep(.toc-nav) {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.toc-list :deep(.toc-link) {
  display: block;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  text-decoration: none;
  transition: color 0.15s, background 0.15s;
  border-radius: 6px;
  padding: 0.25rem 0.5rem;
  line-height: 1.5;
}
.toc-list :deep(.toc-link:hover) {
  color: #f8fafc;
  background: rgba(244, 63, 94, 0.08);
}
.toc-list :deep(.toc-level-1) {
  font-size: 0.9375rem; font-weight: 700;
  color: #e2e8f0;
  padding-left: 0.5rem;
  margin-top: 0.25rem;
}
.toc-list :deep(.toc-level-2) {
  font-size: 0.875rem; font-weight: 600;
  color: #cbd5e1;
  padding-left: 0.5rem;
  margin-top: 0.125rem;
}
.toc-list :deep(.toc-level-3) {
  font-size: 0.8125rem; font-weight: 400;
  color: #94a3b8;
  padding-left: 1.25rem;
  border-left: 2px solid rgba(244, 63, 94, 0.2);
  margin-left: 0.5rem;
  border-radius: 0 6px 6px 0;
}
.toc-list :deep(.toc-level-4) {
  font-size: 0.75rem; font-weight: 400;
  color: #64748b;
  padding-left: 2rem;
  border-left: 2px solid rgba(244, 63, 94, 0.1);
  margin-left: 0.5rem;
  border-radius: 0 6px 6px 0;
}

/* ═══ Article Content Typography ═══ */
.article-content { color: #cbd5e1; font-size: 1rem; line-height: 1.8; }
.article-content :deep(h1) {
  font-size: 2rem; font-weight: 900; margin-top: 0; margin-bottom: 1.25rem;
  color: #f8fafc; letter-spacing: -0.02em; line-height: 1.2;
  border-bottom: 2px solid rgba(59, 130, 246, 0.25);
  padding-bottom: 0.75rem;
}
.article-content :deep(h2) {
  font-size: 1.5rem; font-weight: 800; margin-top: 2.5rem; margin-bottom: 1rem;
  color: #f8fafc; display: flex; align-items: center;
}
.article-content :deep(h2::before) { content: '#'; color: #3b82f6; margin-right: 0.5rem; opacity: 0.6; font-size: 1.2rem; }
.article-content :deep(h3) { font-size: 1.25rem; font-weight: 700; margin-top: 2rem; margin-bottom: 1rem; color: #e2e8f0; }
.article-content :deep(h4),
.article-content :deep(h5),
.article-content :deep(h6) {
  font-weight: 700;
  line-height: 1.35;
  margin-top: 1.6rem;
  margin-bottom: 0.75rem;
}
.article-content :deep(h4) {
  font-size: 1.125rem;
  color: #f1f5f9;
  padding-left: 0.75rem;
  border-left: 3px solid rgba(59, 130, 246, 0.65);
}
.article-content :deep(h5) {
  font-size: 1rem;
  color: #dbeafe;
  display: flex;
  align-items: center;
}
.article-content :deep(h5::before) {
  content: '';
  width: 0.4rem;
  height: 0.4rem;
  margin-right: 0.55rem;
  border-radius: 9999px;
  background: #60a5fa;
  box-shadow: 0 0 10px rgba(96, 165, 250, 0.45);
  flex: 0 0 auto;
}
.article-content :deep(h6) {
  font-size: 0.9375rem;
  color: #bfdbfe;
  padding-bottom: 0.35rem;
  border-bottom: 1px dashed rgba(96, 165, 250, 0.35);
}

/* ── In-article TOC block (h1 + following ul) ── */
.article-content :deep(h1 + ul),
.article-content :deep(h1 + ul ul) {
  font-size: 1.0625rem;
  line-height: 1.9;
}
.article-content :deep(h1 + ul > li) {
  font-size: 1.0625rem;
  font-weight: 600;
  color: #e2e8f0;
  margin-bottom: 0.6rem;
}
.article-content :deep(h1 + ul > li > ul > li) {
  font-size: 0.9375rem;
  font-weight: 400;
  color: #94a3b8;
  margin-bottom: 0.3rem;
}
.article-content :deep(h1 + ul a) {
  color: #60a5fa;
  text-decoration: none;
  transition: color 0.15s;
}
.article-content :deep(h1 + ul a:hover) { color: #93c5fd; }
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
/* ===== Table 样式 ===== */
.article-content :deep(.table-wrapper) {
  overflow-x: auto;
  margin: 1.5rem 0;
  border-radius: 10px;
  -webkit-overflow-scrolling: touch;
}
.article-content :deep(.table-wrapper table) {
  width: 100%;
  min-width: 600px;
  border-collapse: collapse;
  font-size: 0.9rem;
  line-height: 1.6;
}
.article-content :deep(.table-wrapper thead) {
  border-bottom: 2px solid rgba(148, 163, 184, 0.25);
}
.article-content :deep(.table-wrapper th) {
  font-weight: 700;
  font-size: 0.85rem;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: 0.75rem 1rem;
  text-align: left;
  vertical-align: middle;
  white-space: nowrap;
}
.article-content :deep(.table-wrapper td) {
  padding: 0.65rem 1rem;
  vertical-align: middle;
  color: #cbd5e1;
  border-bottom: 1px solid rgba(148, 163, 184, 0.1);
}
.article-content :deep(.table-wrapper tbody tr:nth-child(even)) {
  background: rgba(148, 163, 184, 0.04);
}
.article-content :deep(.table-wrapper tbody tr:hover) {
  background: rgba(59, 130, 246, 0.08);
}
.article-content :deep(.table-wrapper tbody tr:last-child td) {
  border-bottom: none;
}
.article-content :deep(.table-wrapper td:first-child),
.article-content :deep(.table-wrapper th:first-child) {
  padding-left: 1.25rem;
}
.article-content :deep(.table-wrapper td:last-child),
.article-content :deep(.table-wrapper th:last-child) {
  padding-right: 1.25rem;
}

.article-content :deep(pre) {
  background: #0b0f19; border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 12px;
  padding: 1.25rem; overflow-x: auto; margin-bottom: 1.5rem; margin-top: 1rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.875rem; line-height: 1.75; color: #e2e8f0; position: relative;
  box-shadow: inset 0 2px 10px rgba(0, 0, 0, 0.5);
  -webkit-overflow-scrolling: touch;
}
.article-content :deep(:not(pre) > code) {
  background: rgba(255, 255, 255, 0.1); padding: 0.2em 0.4em; border-radius: 6px;
  font-size: 0.85em; line-height: 1.6; color: #93c5fd;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  border: 1px solid rgba(255, 255, 255, 0.05);
}
.article-content :deep(pre code) {
  display: block;
  min-width: max-content;
  background: transparent;
  padding: 0;
  border: none;
  color: inherit;
  line-height: inherit;
  font-family: inherit;
}

/* ===== Obsidian Callout 样式 ===== */
.article-content :deep(.callout) {
  border-radius: 12px;
  padding: 1rem 1.25rem;
  margin: 1.25rem 0;
  border-left: 4px solid;
  background: rgba(255, 255, 255, 0.03);
  line-height: 1.75;
}
.article-content :deep(.callout .callout-title) {
  font-weight: 700;
  font-size: 0.95rem;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.article-content :deep(.callout .callout-content) {
  color: #cbd5e1;
}
.article-content :deep(.callout .callout-content p) {
  margin-bottom: 0.5rem;
}
.article-content :deep(.callout .callout-content p:last-child) {
  margin-bottom: 0;
}
/* callout-note —— 蓝色 */
.article-content :deep(.callout.callout-note) {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.08);
}
.article-content :deep(.callout.callout-note .callout-title) {
  color: #93c5fd;
}
.article-content :deep(.callout.callout-note .callout-title::before) {
  content: '📝';
  font-size: 0.9rem;
}
/* callout-tip —— 绿色 */
.article-content :deep(.callout.callout-tip) {
  border-color: #22c55e;
  background: rgba(34, 197, 94, 0.08);
}
.article-content :deep(.callout.callout-tip .callout-title) {
  color: #86efac;
}
.article-content :deep(.callout.callout-tip .callout-title::before) {
  content: '💡';
  font-size: 0.9rem;
}
/* callout-warning —— 琥珀色 */
.article-content :deep(.callout.callout-warning) {
  border-color: #f59e0b;
  background: rgba(245, 158, 11, 0.08);
}
.article-content :deep(.callout.callout-warning .callout-title) {
  color: #fcd34d;
}
.article-content :deep(.callout.callout-warning .callout-title::before) {
  content: '⚠️';
  font-size: 0.9rem;
}
/* callout-question —— 紫色 */
.article-content :deep(.callout.callout-question) {
  border-color: #a855f7;
  background: rgba(168, 85, 247, 0.08);
}
.article-content :deep(.callout.callout-question .callout-title) {
  color: #c4b5fd;
}
.article-content :deep(.callout.callout-question .callout-title::before) {
  content: '❓';
  font-size: 0.9rem;
}

/* Mermaid 图表容器 —— 后端把 ```mermaid``` 代码块转成 <div class="mermaid">..</div> */
.article-content :deep(.mermaid) {
  width: 100%;
  overflow-x: auto;
  text-align: center;
  margin: 16px 0;
  background: #fff;
  border-radius: 12px;
  padding: 1rem;
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.article-content :deep(.mermaid svg) {
  max-width: 100%;
  height: auto;
}
.article-content :deep(img) {
  max-width: 100%; height: auto; border-radius: 12px; margin: 2rem auto; display: block;
  border: 1px solid rgba(255, 255, 255, 0.1); box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
}

/* ── Lists ── */
.article-content :deep(ul),
.article-content :deep(ol) {
  margin-bottom: 1.5rem;
  padding-left: 1.5rem;
}
.article-content :deep(ul) { list-style-type: disc; }
.article-content :deep(ol) { list-style-type: decimal; }
.article-content :deep(ul ul) {
  margin-top: 0.4rem;
  margin-bottom: 0.4rem;
  padding-left: 1.25rem;
  list-style-type: circle;
}
.article-content :deep(ul ul ul) { list-style-type: square; }
.article-content :deep(ol ol),
.article-content :deep(ul ol),
.article-content :deep(ol ul) {
  margin-top: 0.4rem;
  margin-bottom: 0.4rem;
  padding-left: 1.25rem;
}
.article-content :deep(li) {
  margin-bottom: 0.4rem;
  line-height: 1.7;
  color: #cbd5e1;
}
.article-content :deep(li a) {
  font-size: 1rem;
  font-weight: 500;
}

/* ── Animate ping ── */
@keyframes ping { 75%, 100% { transform: scale(2); opacity: 0; } }
.animate-ping { animation: ping 1s cubic-bezier(0, 0, 0.2, 1) infinite; }

@media (prefers-reduced-motion: reduce) {
  .detail-grid,
  .detail-matrix,
  .matrix-panel-enter-active,
  .matrix-panel-leave-active {
    transition-duration: 0.16s;
  }
}
</style>
