<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { guestNoteApi, type GuestNoteDetailVO } from '@/api/notes'
import { enhanceArticleContent } from '@/utils/enhanceArticle'
import {
  ArrowLeft, ArrowUpToLine, Calendar, FileText, Hash, Image as ImageIcon,
  Layers, LayoutPanelTop, Link, ListTree, Loader2, PanelRightClose, PanelRightOpen, Tags
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref<string | null>(null)
const note = ref<GuestNoteDetailVO | null>(null)
const articleContentRef = ref<HTMLElement | null>(null)
const showTocPanel = ref(false)
const tocWrapperRef = ref<HTMLElement | null>(null)
const tocBallRef = ref<HTMLElement | null>(null)
const matrixCollapsed = ref(false)
const panelPosClasses = ref(['bottom-full', 'right-0', 'mb-4', 'origin-bottom-right'])

function formatDate(raw?: string) {
  if (!raw) return '-'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
  const main = document.querySelector('main')
  if (main) main.scrollTo({ top: 0, behavior: 'smooth' })
  showTocPanel.value = false
}

function toggleMatrix() {
  matrixCollapsed.value = !matrixCollapsed.value
  showTocPanel.value = false
}

function scrollToHashAnchor() {
  const hash = route.hash
  if (!hash) return
  try {
    const rawId = hash.replace('#', '')
    const id = decodeURIComponent(rawId)
    nextTick(() => {
      setTimeout(() => {
        const el = document.getElementById(id) || document.getElementById(rawId)
        if (el) el.scrollIntoView({ behavior: 'smooth' })
      }, 120)
    })
  } catch {
    // ignore malformed hash
  }
}

function onArticleClick(e: Event) {
  const target = e.target as HTMLElement
  const internal = target.closest('.internal-note-link') as HTMLAnchorElement | null
  if (internal) {
    e.preventDefault()
    const noteId = internal.dataset.noteId
    if (!noteId || noteId === 'null') return
    const anchor = internal.dataset.anchor
    router.push(anchor ? `/guest/notes/${noteId}#${anchor}` : `/guest/notes/${noteId}`)
    return
  }

  const hashLink = target.closest('a[href^="#"]') as HTMLAnchorElement | null
  if (hashLink) {
    const href = hashLink.getAttribute('href')
    if (!href || href === '#') return
    e.preventDefault()
    router.replace({ hash: href })
  }
}

function onTocClick(e: Event) {
  const link = (e.target as HTMLElement).closest('a[href^="#"]') as HTMLAnchorElement | null
  if (!link) return
  e.preventDefault()
  const href = link.getAttribute('href')
  if (href) {
    router.replace({ hash: href })
    showTocPanel.value = false
  }
}

function onTocLinkClick(e: Event) {
  const link = (e.target as HTMLElement).closest('a[href^="#"]') as HTMLAnchorElement | null
  if (!link) return
  e.preventDefault()
  const rawId = link.getAttribute('href')?.slice(1)
  if (!rawId) {
    showTocPanel.value = false
    return
  }
  try {
    const id = decodeURIComponent(rawId)
    const el = document.getElementById(id) || document.getElementById(rawId)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' })
      router.replace({ hash: `#${rawId}` })
    }
  } catch {
    const el = document.getElementById(rawId)
    if (el) el.scrollIntoView({ behavior: 'smooth' })
  }
  showTocPanel.value = false
}

let tocHasMoved = false
let tocStartX = 0
let tocStartY = 0
let tocInitialLeft = 0
let tocInitialTop = 0

function updateTocPanelOrigin() {
  const wrapper = tocWrapperRef.value
  if (!wrapper) return
  const rect = wrapper.getBoundingClientRect()
  const isLeft = rect.left < window.innerWidth / 2
  const isTop = rect.top < window.innerHeight / 2
  const classes: string[] = []

  if (isTop) classes.push('top-full', 'mt-4')
  else classes.push('bottom-full', 'mb-4')

  if (isLeft) classes.push('left-0')
  else classes.push('right-0')

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

function tocPointerDown(e: MouseEvent | TouchEvent) {
  if ((e.target as HTMLElement).closest('#guest-toc-panel')) return
  e.preventDefault()

  tocHasMoved = false
  const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
  const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY
  tocStartX = clientX
  tocStartY = clientY

  const wrapper = tocWrapperRef.value
  if (!wrapper) return
  const rect = wrapper.getBoundingClientRect()
  tocInitialLeft = rect.left
  tocInitialTop = rect.top

  wrapper.style.left = `${tocInitialLeft}px`
  wrapper.style.top = `${tocInitialTop}px`
  wrapper.style.right = 'auto'
  wrapper.style.bottom = 'auto'

  document.addEventListener('mousemove', tocPointerMove)
  document.addEventListener('mouseup', tocPointerUp)
  document.addEventListener('touchmove', tocPointerMove, { passive: false })
  document.addEventListener('touchend', tocPointerUp)
}

function tocPointerMove(e: MouseEvent | TouchEvent) {
  if ('touches' in e) e.preventDefault()

  const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
  const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY
  const dx = clientX - tocStartX
  const dy = clientY - tocStartY

  if (Math.abs(dx) <= 3 && Math.abs(dy) <= 3) return
  tocHasMoved = true
  if (showTocPanel.value) showTocPanel.value = false

  const wrapper = tocWrapperRef.value
  if (!wrapper) return
  const maxLeft = window.innerWidth - wrapper.offsetWidth
  const maxTop = window.innerHeight - wrapper.offsetHeight
  const newLeft = Math.max(0, Math.min(tocInitialLeft + dx, maxLeft))
  const newTop = Math.max(0, Math.min(tocInitialTop + dy, maxTop))

  wrapper.style.left = `${newLeft}px`
  wrapper.style.top = `${newTop}px`
}

function tocPointerUp() {
  document.removeEventListener('mousemove', tocPointerMove)
  document.removeEventListener('mouseup', tocPointerUp)
  document.removeEventListener('touchmove', tocPointerMove)
  document.removeEventListener('touchend', tocPointerUp)

  if (!tocHasMoved) toggleTocPanel()
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
    wrapper.style.left = `${currentLeft}px`
    wrapper.style.top = `${currentTop}px`
    if (showTocPanel.value) updateTocPanelOrigin()
  }
}

async function bindTocEvents() {
  await nextTick()
  if (!tocBallRef.value) return
  tocBallRef.value.removeEventListener('mousedown', tocPointerDown)
  tocBallRef.value.removeEventListener('touchstart', tocPointerDown)
  tocBallRef.value.addEventListener('mousedown', tocPointerDown)
  tocBallRef.value.addEventListener('touchstart', tocPointerDown, { passive: false })
}

async function fetchNote() {
  const noteId = Number(route.params.noteId)
  if (!noteId || Number.isNaN(noteId)) {
    error.value = '无效的笔记 ID'
    loading.value = false
    return
  }
  loading.value = true
  error.value = null
  note.value = null
  try {
    note.value = await guestNoteApi.getDetail(noteId)
    scrollToHashAnchor()
  } catch (e: any) {
    error.value = e?.message || '公开笔记不存在'
  } finally {
    loading.value = false
    await bindTocEvents()
  }
}

watch(
  () => route.params.noteId,
  async (newId, oldId) => {
    if (!newId || newId === oldId) return
    showTocPanel.value = false
    matrixCollapsed.value = false
    const main = document.querySelector('main')
    if (main) main.scrollTop = 0
    window.scrollTo(0, 0)
    await fetchNote()
  }
)

watch(
  () => route.hash,
  () => {
    if (!loading.value) scrollToHashAnchor()
  }
)

watch(
  () => note.value?.converted?.bodyHtml,
  async (html) => {
    if (!html) return
    await nextTick()
    await enhanceArticleContent(articleContentRef.value)
  },
  { immediate: true }
)

onMounted(async () => {
  window.addEventListener('resize', onTocResize)
  await fetchNote()
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
  <section class="relative">
    <div v-if="loading" class="flex min-h-[50vh] items-center justify-center">
      <Loader2 class="h-8 w-8 animate-spin text-cyan-300" />
    </div>

    <div v-else-if="error" class="mx-auto max-w-2xl rounded-lg border border-white/10 bg-white/[0.03] p-10 text-center">
      <FileText class="mx-auto mb-4 h-10 w-10 text-slate-600" />
      <h1 class="mb-2 text-xl font-black text-white">无法打开笔记</h1>
      <p class="text-sm text-slate-400">{{ error }}</p>
      <button class="mt-6 rounded-lg bg-cyan-500 px-5 py-2 text-sm font-black text-slate-950 transition hover:bg-cyan-300" @click="router.push('/guest/notes')">返回列表</button>
    </div>

    <template v-else-if="note">
      <div class="mb-6 flex flex-wrap items-center justify-between gap-3 border-b border-white/10 pb-5">
        <button class="inline-flex items-center rounded-lg border border-white/10 bg-white/[0.03] px-3 py-2 text-sm font-bold text-slate-300 transition hover:border-cyan-400/40 hover:text-cyan-100" @click="router.push('/guest/notes')">
          <ArrowLeft class="mr-2 h-4 w-4" />
          返回
        </button>
        <button class="inline-flex items-center rounded-lg border border-white/10 bg-white/[0.03] px-3 py-2 text-sm font-bold text-slate-300 transition hover:border-cyan-400/40 hover:text-cyan-100 xl:hidden" @click="matrixCollapsed = !matrixCollapsed">
          <component :is="matrixCollapsed ? PanelRightOpen : PanelRightClose" class="mr-2 h-4 w-4" />
          资源矩阵
        </button>
      </div>

      <div class="guest-detail-grid grid gap-8" :class="matrixCollapsed ? 'guest-detail-grid--collapsed' : 'guest-detail-grid--expanded'">
        <article class="min-w-0">
          <header class="mb-8">
            <div class="mb-4 flex flex-wrap items-center gap-3 text-xs font-bold text-slate-400">
              <span class="inline-flex items-center rounded-md border border-cyan-400/20 bg-cyan-400/10 px-2.5 py-1 text-cyan-100">
                <Layers class="mr-1.5 h-3.5 w-3.5" />
                {{ note.topicName || '未归属主题' }}
              </span>
              <span class="inline-flex items-center">
                <Calendar class="mr-1.5 h-3.5 w-3.5 text-cyan-300" />
                {{ formatDate(note.createTime) }}
              </span>
            </div>
            <h1 class="max-w-4xl text-3xl font-black leading-tight tracking-normal text-white sm:text-5xl">{{ note.title }}</h1>
            <p v-if="note.description" class="mt-5 max-w-3xl text-base leading-7 text-slate-400">{{ note.description }}</p>
            <div v-if="note.tags?.length" class="mt-6 flex flex-wrap gap-2">
              <span v-for="tag in note.tags" :key="tag" class="inline-flex items-center rounded-md border border-cyan-400/20 bg-cyan-400/10 px-2.5 py-1 text-xs font-bold text-cyan-100">
                <Tags class="mr-1.5 h-3.5 w-3.5" />
                {{ tag }}
              </span>
            </div>
          </header>

          <div
            ref="articleContentRef"
            class="article-content rounded-lg border border-white/10 bg-white/[0.025] p-5 sm:p-8"
            v-html="note.converted?.bodyHtml"
            @click="onArticleClick"
          />
        </article>

        <aside class="guest-matrix min-w-0" :class="matrixCollapsed ? 'guest-matrix--collapsed' : 'guest-matrix--expanded'">
          <div class="sticky top-24 space-y-4">
            <section class="rounded-lg border border-white/10 bg-white/[0.03] p-4">
              <div class="mb-3 flex items-center justify-between">
                <h2 class="text-xs font-black uppercase tracking-[0.18em] text-slate-400">资源矩阵</h2>
                <button class="hidden h-8 w-8 items-center justify-center rounded-md text-slate-500 transition hover:bg-white/5 hover:text-white xl:flex" @click="matrixCollapsed = true">
                  <PanelRightClose class="h-4 w-4" />
                </button>
              </div>
              <div class="grid grid-cols-3 gap-2">
                <div class="rounded-md border border-white/10 bg-black/20 p-3">
                  <Hash class="mb-2 h-4 w-4 text-cyan-300" />
                  <div class="text-lg font-black text-white">{{ note.tags?.length ?? 0 }}</div>
                  <div class="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-500">Tags</div>
                </div>
                <div class="rounded-md border border-white/10 bg-black/20 p-3">
                  <ImageIcon class="mb-2 h-4 w-4 text-amber-300" />
                  <div class="text-lg font-black text-white">{{ note.images?.length ?? 0 }}</div>
                  <div class="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-500">Images</div>
                </div>
                <div class="rounded-md border border-white/10 bg-black/20 p-3">
                  <Link class="mb-2 h-4 w-4 text-emerald-300" />
                  <div class="text-lg font-black text-white">{{ note.eachNotes?.length ?? 0 }}</div>
                  <div class="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-500">Links</div>
                </div>
              </div>
            </section>

            <section v-if="note.converted?.tocHtml" class="rounded-lg border border-white/10 bg-white/[0.03] p-4">
              <h2 class="mb-3 flex items-center text-xs font-black uppercase tracking-[0.18em] text-slate-400">
                <ListTree class="mr-2 h-4 w-4 text-cyan-300" />
                目录
              </h2>
              <div class="toc-list max-h-[36vh] overflow-y-auto pr-1" v-html="note.converted.tocHtml" @click="onTocClick" />
            </section>

            <section class="rounded-lg border border-white/10 bg-white/[0.03] p-4">
              <h2 class="mb-3 text-xs font-black uppercase tracking-[0.18em] text-slate-400">公开关联</h2>
              <div class="space-y-3 text-sm">
                <div v-if="note.eachNotes?.length" class="space-y-2">
                  <button v-for="link in note.eachNotes" :key="`${link.targetNoteId}-${link.anchor}`" class="block w-full rounded-md border border-white/10 bg-black/20 px-3 py-2 text-left text-slate-300 transition hover:border-cyan-400/40 hover:text-cyan-100" @click="router.push(link.anchor ? `/guest/notes/${link.targetNoteId}#${link.anchor}` : `/guest/notes/${link.targetNoteId}`)">
                    {{ link.nickname || link.targetNoteTitle || link.parsedNoteName }}
                  </button>
                </div>
                <p v-else class="text-slate-500">暂无公开双链。</p>
              </div>
            </section>
          </div>
        </aside>
      </div>

      <Teleport to="body">
        <div v-if="note" ref="tocWrapperRef" id="guest-toc-draggable" class="fixed bottom-12 right-8 z-50 touch-none">
          <div
            id="guest-toc-panel"
            class="absolute max-h-[60vh] w-64 overflow-y-auto rounded-2xl border border-cyan-400/30 bg-[#020617]/95 p-5 shadow-[0_15px_40px_rgba(0,0,0,0.5)] backdrop-blur-xl transition-all duration-300"
            :class="[panelPosClasses, showTocPanel ? 'scale-100 opacity-100 pointer-events-auto' : 'scale-0 opacity-0 pointer-events-none']"
          >
            <h4 class="mb-4 flex items-center border-b border-white/5 pb-3 text-xs font-black uppercase tracking-[0.18em] text-slate-400">
              <ListTree class="mr-2 h-4 w-4 text-cyan-300" />
              文档快速导航
            </h4>
            <div class="mb-4 flex flex-wrap items-center gap-2 pb-3" :class="{ 'border-b border-white/5': note.converted?.tocHtml }">
              <button class="flex flex-1 items-center justify-center rounded-lg bg-white/5 py-1.5 text-xs font-bold text-slate-400 transition hover:bg-cyan-500/15 hover:text-cyan-200" title="返回顶部" @click="scrollToTop">
                <ArrowUpToLine class="mr-1 h-3.5 w-3.5" />
                返回顶部
              </button>
              <button class="flex flex-1 items-center justify-center rounded-lg bg-white/5 py-1.5 text-xs font-bold text-slate-400 transition hover:bg-emerald-500/15 hover:text-emerald-200" title="资源矩阵" @click="toggleMatrix">
                <LayoutPanelTop class="mr-1 h-3.5 w-3.5" />
                资源矩阵
              </button>
            </div>
            <div v-if="note.converted?.tocHtml" class="toc-list" v-html="note.converted.tocHtml" @click="onTocLinkClick" />
            <p v-else class="py-2 text-center text-[10px] text-slate-500">暂无目录结构</p>
          </div>

          <div
            ref="tocBallRef"
            id="guest-toc-ball"
            class="group flex h-12 w-12 cursor-pointer items-center justify-center rounded-full border border-cyan-400/40 bg-[#020617]/90 text-cyan-300 shadow-[0_0_20px_rgba(34,211,238,0.2)] backdrop-blur-xl transition-[box-shadow,transform] hover:shadow-[0_0_25px_rgba(34,211,238,0.4)] active:scale-95"
            title="文档快速导航"
          >
            <ListTree class="h-5 w-5 transition-transform group-hover:scale-110" />
            <div class="pointer-events-none absolute inset-0 rounded-full border border-cyan-300/40 opacity-20 animate-ping" />
          </div>
        </div>
      </Teleport>
    </template>
  </section>
</template>

<style scoped>
.guest-detail-grid {
  grid-template-columns: minmax(0, 1fr);
}

@media (min-width: 1280px) {
  .guest-detail-grid--expanded {
    grid-template-columns: minmax(0, 1fr) minmax(18rem, 24rem);
  }

  .guest-detail-grid--collapsed {
    grid-template-columns: minmax(0, 1fr) 0fr;
  }

  .guest-matrix {
    overflow: hidden;
    transition: opacity 0.24s ease;
  }

  .guest-matrix--collapsed {
    opacity: 0;
    pointer-events: none;
  }
}

.toc-list :deep(.toc-sidebar) { display: contents; }
.toc-list :deep(.toc-header),
.toc-list :deep(.toc-fab) { display: none; }
.toc-list :deep(.toc-nav) {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}
.toc-list :deep(.toc-link) {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-radius: 0.375rem;
  padding: 0.25rem 0.5rem;
  color: #94a3b8;
  text-decoration: none;
  transition: color 0.15s, background 0.15s;
}
.toc-list :deep(.toc-link:hover) {
  background: rgba(34, 211, 238, 0.08);
  color: #cffafe;
}
.toc-list :deep(.toc-level-1) { font-size: 0.9rem; font-weight: 800; color: #e2e8f0; }
.toc-list :deep(.toc-level-2) { font-size: 0.82rem; font-weight: 700; padding-left: 0.75rem; }
.toc-list :deep(.toc-level-3),
.toc-list :deep(.toc-level-4) { font-size: 0.78rem; padding-left: 1.25rem; }

.article-content {
  color: #cbd5e1;
  font-size: 1rem;
  line-height: 1.8;
}
.article-content :deep(h1) {
  margin: 0 0 1.25rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid rgba(34, 211, 238, 0.24);
  color: #f8fafc;
  font-size: 2rem;
  font-weight: 900;
  line-height: 1.2;
}
.article-content :deep(h2) {
  margin: 2.25rem 0 1rem;
  color: #f8fafc;
  font-size: 1.5rem;
  font-weight: 850;
  line-height: 1.3;
}
.article-content :deep(h3) {
  margin: 1.8rem 0 0.9rem;
  color: #e2e8f0;
  font-size: 1.22rem;
  font-weight: 800;
}
.article-content :deep(h4),
.article-content :deep(h5),
.article-content :deep(h6) {
  margin: 1.4rem 0 0.75rem;
  color: #dbeafe;
  font-weight: 750;
}
.article-content :deep(p) { margin-bottom: 1.15rem; }
.article-content :deep(a) {
  color: #67e8f9;
  text-decoration: underline;
  text-underline-offset: 4px;
  text-decoration-color: rgba(103, 232, 249, 0.35);
}
.article-content :deep(.internal-note-link.unresolved) {
  color: #64748b;
  cursor: default;
  text-decoration-color: rgba(100, 116, 139, 0.3);
}
.article-content :deep(ul),
.article-content :deep(ol) {
  margin-bottom: 1.35rem;
  padding-left: 1.5rem;
}
.article-content :deep(ul) { list-style-type: disc; }
.article-content :deep(ol) { list-style-type: decimal; }
.article-content :deep(li) { margin-bottom: 0.35rem; }
.article-content :deep(blockquote) {
  margin: 1.4rem 0;
  border-left: 4px solid #22d3ee;
  border-radius: 0 0.5rem 0.5rem 0;
  background: rgba(34, 211, 238, 0.07);
  padding: 1rem 1.25rem;
  color: #94a3b8;
}
.article-content :deep(pre) {
  margin: 1.4rem 0;
  overflow-x: auto;
  border-radius: 0.75rem;
  border: 1px solid rgba(255,255,255,0.08);
  background: #08111f;
  padding: 1rem;
  color: #e2e8f0;
}
.article-content :deep(:not(pre) > code) {
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 0.35rem;
  background: rgba(255,255,255,0.08);
  padding: 0.15rem 0.35rem;
  color: #a5f3fc;
}
.article-content :deep(.table-wrapper) {
  margin: 1.5rem 0;
  overflow-x: auto;
  border-radius: 0.75rem;
}
.article-content :deep(table) {
  width: 100%;
  min-width: 36rem;
  border-collapse: collapse;
}
.article-content :deep(th),
.article-content :deep(td) {
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
  padding: 0.75rem 1rem;
  text-align: left;
}
.article-content :deep(th) {
  color: #94a3b8;
  font-size: 0.78rem;
  text-transform: uppercase;
}
.article-content :deep(img) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 1.5rem auto;
  border-radius: 0.75rem;
  border: 1px solid rgba(255,255,255,0.1);
}
.article-content :deep(.mermaid) {
  margin: 1.5rem 0;
  overflow-x: auto;
  border-radius: 0.75rem;
  background: #fff;
  padding: 1rem;
}
.article-content :deep(.mermaid svg) {
  max-width: 100%;
  height: auto;
}
</style>
