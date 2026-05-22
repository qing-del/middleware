<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { noteApi, getNoteStatusInfo, NoteStatusCode } from '@/api/notes'
import type { NoteDetailVO, NoteRelationDetailVO, NoteItem } from '@/api/notes'
import { imageApi } from '@/api/images'
import type { ImageItem } from '@/api/images'
import { tagApi } from '@/api/tags'
import type { TagItem } from '@/api/tags'
import AudioTaskModal from '@/components/AudioTaskModal.vue'
import {
  ArrowLeft, Globe, FileEdit, Calendar, HardDrive, Layers, Hash, ImageIcon, Link,
  Network, ShieldCheck, CheckCircle2, AlertTriangle, ListTree, ArrowUpToLine,
  LayoutPanelTop, Loader2, RefreshCw, X, ChevronRight, FileText, Clock,
  Search, Plug, Unlink2, Mic
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()

// ── State ─────────────────────────────────────────
const loading = ref(true)
const note = ref<NoteDetailVO | null>(null)
const error = ref<string | null>(null)
const converting = ref(false)
const matrixCollapsed = ref(false)

type BindTargetType = 'image' | 'tag' | 'note'

interface BindResultItem {
  id: number
  title: string
  subtitle?: string
  meta?: string
  status?: { label: string; cls: string }
}

const relationLoading = ref(false)
const relationError = ref<string | null>(null)
const relationDetail = ref<NoteRelationDetailVO | null>(null)
const relationTab = ref<'images' | 'tags' | 'links'>('images')

const showBindModal = ref(false)
const bindSearchQuery = ref('')
const bindSearchLoading = ref(false)
const bindSearchError = ref<string | null>(null)
const bindResults = ref<BindResultItem[]>([])
const selectedBindTargetId = ref<number | null>(null)
const currentBind = ref<{ type: BindTargetType; mappingId: number; parsedName: string } | null>(null)

// Floating TOC state
const showTocPanel = ref(false)
const tocWrapperRef = ref<HTMLElement | null>(null)
const tocBallRef = ref<HTMLElement | null>(null)
const tocPanelRef = ref<HTMLElement | null>(null)

type AudioTaskModalExpose = { open: () => void }
const audioModalRef = ref<AudioTaskModalExpose | null>(null)

const openAudioModal = () => {
  audioModalRef.value?.open()
}

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

// ── Relation integrity ────────────────────────────
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

const allRelationsIntact = () => {
  if (relationDetail.value) {
    return relationDetail.value.images.every(i => i.isMissing === 0)
      && relationDetail.value.tags.every(t => t.isMissing === 0)
      && relationDetail.value.eachNotes.every(e => e.isMissing === 0)
  }
  if (!note.value) return false
  const n = note.value
  if (n.missingCount > 0) return false
  if (n.images.some(i => i.isMissing)) return false
  if (n.eachNotes.some(e => e.isMissing)) return false
  return true
}

const hasAnyRelations = () => {
  if (relationDetail.value) {
    const detail = relationDetail.value
    return (detail.tags?.length ?? 0) > 0 || (detail.images?.length ?? 0) > 0 || (detail.eachNotes?.length ?? 0) > 0
  }
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
    await nextTick()
    await bindTocEvents()
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
  try {
    const id = decodeURIComponent(hash.replace('#', ''))
    if (!id) return
    // Wait for v-html DOM to be fully rendered
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
    void fetchRelations()
  } catch (e: any) {
    error.value = e?.message || '加载笔记失败'
  } finally {
    loading.value = false
  }
}

async function fetchRelations() {
  const noteId = Number(route.params.noteId)
  if (!noteId || isNaN(noteId)) return
  relationLoading.value = true
  relationError.value = null
  try {
    relationDetail.value = await noteApi.getRelations(noteId)
  } catch (e: any) {
    relationError.value = e?.message || '加载关联映射失败'
  } finally {
    relationLoading.value = false
  }
}

async function refreshNoteSummary() {
  const noteId = Number(route.params.noteId)
  if (!noteId || isNaN(noteId)) return
  try {
    note.value = await noteApi.getDetail(noteId)
  } catch {
    // ignore refresh errors
  }
}

async function refreshRelations() {
  await Promise.all([refreshNoteSummary(), fetchRelations()])
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
    relationDetail.value = null
    relationError.value = null
    relationLoading.value = false
    relationTab.value = 'images'
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

function handleEachNoteClick(each: { targetNoteId: number; isMissing: number; anchor?: string }) {
  if (!each.isMissing) {
    if (each.anchor) {
      router.push(`/user/notes/${each.targetNoteId}#${each.anchor}`)
    } else {
      router.push(`/user/notes/${each.targetNoteId}`)
    }
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

// ── TOC link click ────────────────────────────────
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
      <div class="detail-grid grid grid-cols-1 gap-8 items-start pb-10" :class="matrixCollapsed ? 'detail-grid--collapsed' : 'detail-grid--expanded'">
        <!-- Left: Article reading area -->
        <div class="detail-main glass-panel rounded-[2rem] p-8 md:p-14 relative overflow-hidden shadow-2xl transition-all duration-300">
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
        <aside class="detail-matrix xl:sticky xl:top-8" :class="matrixCollapsed ? 'detail-matrix--collapsed' : 'detail-matrix--expanded'" :aria-hidden="matrixCollapsed">
          <Transition name="matrix-panel">
            <div v-show="!matrixCollapsed" class="detail-matrix-inner">
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
            </div>
          </Transition>
        </aside>
      </div>

      <!-- ═══ Relation Binding Console ═══ -->
      <section class="space-y-6">
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
                系统扫描到 Markdown 源码中存在引用的资产，若未绑定或审核未通过请手动指定映射目标，完成后可继续转换与发布。
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
            <span class="ml-3 text-sm text-slate-500">加载关联映射中...</span>
          </div>
          <div v-else-if="relationError" class="glass-panel rounded-2xl p-8 text-center">
            <div class="w-12 h-12 rounded-xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center mx-auto mb-3">
              <X class="w-5 h-5 text-rose-400" />
            </div>
            <p class="text-sm text-slate-400">{{ relationError }}</p>
          </div>
          <div v-else>
            <!-- Images mapping -->
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

            <!-- Tags mapping -->
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

            <!-- Each-notes mapping -->
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
      </section>

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
                    <Link class="w-4 h-4" />
                    <span>确认映射并绑定</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>

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
            <div class="flex flex-wrap items-center gap-2 mb-4 pb-3" :class="{ 'border-b border-white/5': note.converted.tocHtml }">
              <button class="flex-1 py-1.5 rounded-lg bg-white/5 hover:bg-blue-500/20 text-slate-400 hover:text-blue-300 text-xs font-bold transition-colors flex items-center justify-center" @click="scrollToTop">
                <ArrowUpToLine class="w-3.5 h-3.5 mr-1" /> 返回顶部
              </button>
              <button class="flex-1 py-1.5 rounded-lg bg-white/5 hover:bg-purple-500/20 text-slate-400 hover:text-purple-300 text-xs font-bold transition-colors flex items-center justify-center" @click="toggleMatrix">
                <LayoutPanelTop class="w-3.5 h-3.5 mr-1" /> 资产矩阵
              </button>
              <button class="flex-1 py-1.5 rounded-lg bg-white/5 hover:bg-indigo-500/20 text-slate-400 hover:text-indigo-300 text-xs font-bold transition-colors flex items-center justify-center" @click="openAudioModal">
                <Mic class="w-3.5 h-3.5 mr-1" /> 音频助手
              </button>
            </div>
            <!-- tocHtml rendered (only when there's actual TOC content) -->
            <div v-if="note.converted.tocHtml" id="toc-list" class="toc-list" v-html="note.converted.tocHtml" @click="onTocLinkClick" />
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

      <AudioTaskModal ref="audioModalRef" :showTrigger="false" />
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
  /* 调整时间与 Grid 动画同步，并换用无回弹的平滑曲线 */
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
    /* 更换为 0.5s 配合 0.4, 0, 0.2, 1 曲线，彻底消除 Grid 的橡皮筋错觉 */
    transition:
      grid-template-columns 0.5s cubic-bezier(0.4, 0, 0.2, 1),
      column-gap 0.5s cubic-bezier(0.4, 0, 0.2, 1);
  }

  .detail-grid--expanded {
    grid-template-columns: minmax(0, 3fr) minmax(18rem, 1.12fr);
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
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: rgba(59, 130, 246, 0.5); }

.target-item.selected {
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.5);
  box-shadow: 0 0 15px rgba(59, 130, 246, 0.2);
}

/* ── TOC list styles ── */
.toc-list :deep(.toc-sidebar) { display: contents; } /* strip sidebar wrapper, keep children */
.toc-list :deep(.toc-header)  { display: none; }     /* hide "目录" title + collapse button */
.toc-list :deep(.toc-fab)     { display: none; }     /* hide floating action button */
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
  background: rgba(59, 130, 246, 0.08);
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
  border-left: 2px solid rgba(59, 130, 246, 0.2);
  margin-left: 0.5rem;
  border-radius: 0 6px 6px 0;
}
.toc-list :deep(.toc-level-4) {
  font-size: 0.75rem; font-weight: 400;
  color: #64748b;
  padding-left: 2rem;
  border-left: 2px solid rgba(59, 130, 246, 0.1);
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

/* ── Internal note link ── */
.article-content :deep(.internal-note-link),
.article-content :deep(.hash-link) {
  color: #60a5fa; text-decoration: underline;
  text-underline-offset: 4px; text-decoration-color: rgba(59, 130, 246, 0.3);
  cursor: pointer; transition: color 0.2s;
}
.article-content :deep(.internal-note-link:hover),
.article-content :deep(.hash-link) { color: #93c5fd; }

/* ── Animate ping── */
@keyframes ping { 75%, 100% { transform: scale(2); opacity: 0; } }
.animate-ping { animation: ping 1s cubic-bezier(0, 0, 0.2, 1) infinite; }

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

@media (prefers-reduced-motion: reduce) {
  .detail-grid,
  .detail-matrix,
  .matrix-panel-enter-active,
  .matrix-panel-leave-active {
    transition-duration: 0.16s;
  }
}
</style>
