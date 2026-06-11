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
const sidebarTocRef = ref<HTMLElement | null>(null)
const floatingTocRef = ref<HTMLElement | null>(null)
const showTocPanel = ref(false)
const tocWrapperRef = ref<HTMLElement | null>(null)
const tocBallRef = ref<HTMLElement | null>(null)
const matrixCollapsed = ref(false)
const panelPosClasses = ref(['bottom-full', 'right-0', 'mb-4', 'origin-bottom-right'])
const activeAnchor = ref<string>('')

// 固定头部偏移 —— GuestLayout 的 header 是 h-16 (4rem)，加点呼吸距离
const HEADER_OFFSET = 88

// 媒体查询：尊重用户的减少动态效果偏好
const prefersReducedMotion = typeof window !== 'undefined'
  && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

const CALLOUT_TYPE_ALIASES: Record<string, string> = {
  note: 'note',
  info: 'info',
  tip: 'tip',
  hint: 'tip',
  important: 'tip',
  success: 'success',
  check: 'success',
  done: 'success',
  warning: 'warning',
  caution: 'warning',
  attention: 'warning',
  question: 'question',
  help: 'question',
  faq: 'question',
  failure: 'failure',
  fail: 'failure',
  error: 'failure',
  danger: 'failure',
  bug: 'bug',
  example: 'example',
  quote: 'quote',
  cite: 'quote'
}

const CALLOUT_DEFAULT_TITLES: Record<string, string> = {
  note: 'Note',
  info: 'Info',
  tip: 'Tip',
  success: 'Success',
  warning: 'Warning',
  question: 'Question',
  failure: 'Failure',
  bug: 'Bug',
  example: 'Example',
  quote: 'Quote'
}

function formatDate(raw?: string) {
  if (!raw) return '-'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function resolveCalloutType(rawType: string): string {
  const normalized = rawType.toLowerCase().trim()
  return CALLOUT_TYPE_ALIASES[normalized] ?? normalized.replace(/[^\w-]/g, '')
}

function stripCalloutMarker(html: string): string {
  return html.replace(/^\s*\[![\w-]+]\s*/i, '').trim()
}

function splitCalloutTitleAndBody(html: string, type: string) {
  const match = html.match(/(?:<br\s*\/?>|\r?\n)/i)
  if (!match || match.index == null) {
    return {
      titleHtml: html.trim() || CALLOUT_DEFAULT_TITLES[type] || type,
      bodyHtml: ''
    }
  }

  const titleHtml = html.slice(0, match.index).trim()
  const bodyHtml = html.slice(match.index + match[0].length).trim()
  return {
    titleHtml: titleHtml || CALLOUT_DEFAULT_TITLES[type] || type,
    bodyHtml
  }
}

function transformGuestCalloutBlockquote(blockquote: HTMLElement) {
  if (blockquote.dataset.calloutEnhanced === 'true') return

  const firstChild = blockquote.firstElementChild as HTMLElement | null
  if (!firstChild || firstChild.tagName.toLowerCase() !== 'p') return

  const marker = firstChild.textContent?.match(/^\s*\[!([\w-]+)]/i)
  if (!marker) return

  const type = resolveCalloutType(marker[1])
  if (!type) return

  const callout = document.createElement('div')
  callout.className = `callout callout-${type}`
  callout.dataset.calloutEnhanced = 'true'

  const title = document.createElement('div')
  title.className = 'callout-title'

  const content = document.createElement('div')
  content.className = 'callout-content'

  const { titleHtml, bodyHtml } = splitCalloutTitleAndBody(stripCalloutMarker(firstChild.innerHTML), type)
  title.innerHTML = titleHtml

  if (bodyHtml) {
    const leadingParagraph = document.createElement('p')
    leadingParagraph.innerHTML = bodyHtml
    content.appendChild(leadingParagraph)
  }

  let node = firstChild.nextSibling
  while (node) {
    const next = node.nextSibling
    content.appendChild(node)
    node = next
  }

  callout.append(title, content)
  blockquote.replaceWith(callout)
}

function markNestedGuestCallouts(container: HTMLElement) {
  container.querySelectorAll<HTMLElement>('.callout').forEach((callout) => {
    const parentCallout = callout.parentElement?.closest('.callout')
    callout.classList.toggle('callout--nested', Boolean(parentCallout))
  })
}

function normalizeGuestCallouts(container: HTMLElement) {
  const blockquotes = Array.from(container.querySelectorAll<HTMLElement>('blockquote')).reverse()
  blockquotes.forEach(transformGuestCalloutBlockquote)
  markNestedGuestCallouts(container)
}

async function enhanceGuestArticleContent() {
  const container = articleContentRef.value
  if (!container) return
  normalizeGuestCallouts(container)
  await enhanceArticleContent(container)
}

// ── 锚点查找 / 滚动 ─────────────────────────────────

/** CSS.escape 的安全 polyfill —— 老浏览器或 SSR 兜底 */
function safeCssEscape(value: string): string {
  if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') {
    try { return CSS.escape(value) } catch { /* fall through */ }
  }
  return value.replace(/["\\\]\[\(\)\{\}\.#:~>+*^$|?!,;@%&=`'/]/g, '\\$&')
}

/** 在指定根节点内按多种选择器查找锚点目标 */
function findInRoot(root: ParentNode | Document, key: string): HTMLElement | null {
  const escaped = safeCssEscape(key)
  const selectors = [
    `[id="${escaped}"]`,
    `[name="${escaped}"]`,
    `a[id="${escaped}"]`,
    `a[name="${escaped}"]`,
    `[data-anchor="${escaped}"]`,
  ]
  for (const sel of selectors) {
    try {
      const el = root.querySelector<HTMLElement>(sel)
      if (el) return el
    } catch { /* invalid selector, skip */ }
  }
  return null
}

/** 解码 + 在正文内优先查、document 兜底 */
function findAnchorTarget(rawId: string): HTMLElement | null {
  if (!rawId) return null
  let decoded = rawId
  try { decoded = decodeURIComponent(rawId) } catch { /* keep raw */ }

  const container = articleContentRef.value
  if (container) {
    const el = findInRoot(container, decoded) || (decoded !== rawId ? findInRoot(container, rawId) : null)
    if (el) return el
  }
  return findInRoot(document, decoded) || (decoded !== rawId ? findInRoot(document, rawId) : null)
}

/**
 * 滚动到指定锚点，window 滚动，扣除固定头部偏移。
 * 返回是否成功定位到元素。
 */
function scrollToAnchor(rawId: string, options?: { highlight?: boolean }): boolean {
  const el = findAnchorTarget(rawId)
  if (!el) return false

  const rect = el.getBoundingClientRect()
  const top = rect.top + window.scrollY - HEADER_OFFSET
  const behavior: ScrollBehavior = prefersReducedMotion ? 'auto' : 'smooth'
  window.scrollTo({ top: Math.max(0, top), behavior })

  // 兼容 main 内部可能有滚动容器（当前 GuestLayout 是 window 滚动，这里只是防御）
  const mainEl = document.querySelector('main')
  if (mainEl && mainEl.scrollHeight > mainEl.clientHeight) {
    const mainRect = mainEl.getBoundingClientRect()
    const offsetWithinMain = rect.top - mainRect.top + mainEl.scrollTop - HEADER_OFFSET
    try { mainEl.scrollTo({ top: Math.max(0, offsetWithinMain), behavior }) } catch { /* ignore */ }
  }

  setActiveAnchor(rawId)

  if (options?.highlight !== false && !prefersReducedMotion) {
    el.classList.remove('anchor-flash')
    void el.offsetWidth
    el.classList.add('anchor-flash')
    window.setTimeout(() => el.classList.remove('anchor-flash'), 1600)
  }
  return true
}

/** 给目录容器内当前 href 的链接打 active class */
function highlightTocLink(root: HTMLElement | null, rawId: string) {
  if (!root || !rawId) return
  root.querySelectorAll<HTMLElement>('.toc-link--active').forEach(n => n.classList.remove('toc-link--active'))
  const escaped = safeCssEscape(rawId)
  let target: HTMLElement | null = null
  try { target = root.querySelector<HTMLElement>(`a[href="#${escaped}"]`) } catch { target = null }
  if (target) target.classList.add('toc-link--active')
}

function setActiveAnchor(rawId: string) {
  activeAnchor.value = rawId
  highlightTocLink(sidebarTocRef.value, rawId)
  highlightTocLink(floatingTocRef.value, rawId)
}

function scrollToHashAnchor() {
  const hash = route.hash
  if (!hash) return
  const rawId = hash.replace('#', '')
  nextTick(() => {
    // v-html + enhanceArticleContent 后给浏览器一帧布局时间
    setTimeout(() => { scrollToAnchor(rawId) }, 120)
  })
}

/** 统一的 hash 链接点击处理 —— 正文/侧栏/浮动面板共用 */
function handleHashLinkClick(e: Event, opts?: { closePanel?: boolean }) {
  const link = (e.target as HTMLElement).closest('a[href^="#"]') as HTMLAnchorElement | null
  if (!link) return
  const href = link.getAttribute('href')
  if (!href || href === '#') return
  e.preventDefault()

  const rawId = href.slice(1)
  // 即时滚动，不依赖 hash watcher（同 hash 重复点击也能再次滚动）
  scrollToAnchor(rawId)
  if (route.hash !== href) {
    router.replace({ hash: href }).catch(() => { /* duplicate/aborted, ignore */ })
  }
  if (opts?.closePanel) showTocPanel.value = false
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
  handleHashLinkClick(e)
}

function onSidebarTocClick(e: Event) { handleHashLinkClick(e, { closePanel: true }) }
function onFloatingTocClick(e: Event) { handleHashLinkClick(e, { closePanel: true }) }

function scrollToTop() {
  const behavior: ScrollBehavior = prefersReducedMotion ? 'auto' : 'smooth'
  window.scrollTo({ top: 0, behavior })
  showTocPanel.value = false
}

function toggleMatrix() {
  matrixCollapsed.value = !matrixCollapsed.value
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
    await enhanceGuestArticleContent()
    // 增强完成后，正文里的 id 锚点才稳定 —— 此时再按当前 hash 定位一次
    if (route.hash) {
      scrollToHashAnchor()
    } else {
      activeAnchor.value = ''
    }
  },
  { immediate: true }
)

watch(
  () => note.value?.converted?.tocHtml,
  async () => {
    // 目录 v-html 更新后，若当前已有 activeAnchor，重新打高亮
    if (!activeAnchor.value) return
    await nextTick()
    highlightTocLink(sidebarTocRef.value, activeAnchor.value)
    highlightTocLink(floatingTocRef.value, activeAnchor.value)
  }
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
          <div class="guest-matrix__sticky space-y-4">
            <section class="guest-soft-panel rounded-lg border border-white/10 bg-white/[0.03] p-4">
              <div class="mb-3 flex items-center justify-between">
                <h2 class="text-xs font-black uppercase tracking-[0.18em] text-slate-400">资源矩阵</h2>
                <button class="hidden h-8 w-8 items-center justify-center rounded-md text-slate-500 transition hover:bg-white/5 hover:text-white xl:flex" @click="matrixCollapsed = true">
                  <PanelRightClose class="h-4 w-4" />
                </button>
              </div>
              <div class="grid grid-cols-3 gap-2">
                <div class="guest-stat-card rounded-md border border-white/10 bg-white/[0.03] p-3">
                  <Hash class="mb-2 h-4 w-4 text-cyan-300" />
                  <div class="text-lg font-black text-white">{{ note.tags?.length ?? 0 }}</div>
                  <div class="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-500">Tags</div>
                </div>
                <div class="guest-stat-card rounded-md border border-white/10 bg-white/[0.03] p-3">
                  <ImageIcon class="mb-2 h-4 w-4 text-amber-300" />
                  <div class="text-lg font-black text-white">{{ note.images?.length ?? 0 }}</div>
                  <div class="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-500">Images</div>
                </div>
                <div class="guest-stat-card rounded-md border border-white/10 bg-white/[0.03] p-3">
                  <Link class="mb-2 h-4 w-4 text-emerald-300" />
                  <div class="text-lg font-black text-white">{{ note.eachNotes?.length ?? 0 }}</div>
                  <div class="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-500">Links</div>
                </div>
              </div>
            </section>

            <section v-if="note.converted?.tocHtml" class="guest-soft-panel rounded-lg border border-white/10 bg-white/[0.03] p-4">
              <h2 class="mb-3 flex items-center text-xs font-black uppercase tracking-[0.18em] text-slate-400">
                <ListTree class="mr-2 h-4 w-4 text-cyan-300" />
                目录
              </h2>
              <div ref="sidebarTocRef" class="toc-list max-h-[36vh] overflow-y-auto pr-1" v-html="note.converted.tocHtml" @click="onSidebarTocClick" />
            </section>

            <section class="guest-soft-panel rounded-lg border border-white/10 bg-white/[0.03] p-4">
              <h2 class="mb-3 text-xs font-black uppercase tracking-[0.18em] text-slate-400">公开关联</h2>
              <div class="space-y-3 text-sm">
                <div v-if="note.eachNotes?.length" class="space-y-2">
                  <button v-for="link in note.eachNotes" :key="`${link.targetNoteId}-${link.anchor}`" class="guest-link-card block w-full rounded-md border border-white/10 bg-white/[0.03] px-3 py-2 text-left text-slate-300 transition hover:border-cyan-400/40 hover:text-cyan-100" @click="router.push(link.anchor ? `/guest/notes/${link.targetNoteId}#${link.anchor}` : `/guest/notes/${link.targetNoteId}`)">
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
            <div v-if="note.converted?.tocHtml" ref="floatingTocRef" class="toc-list" v-html="note.converted.tocHtml" @click="onFloatingTocClick" />
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
  /* 默认 stretch：aside 高度跟随正文，sticky 才能在滚动时持续粘附 */
}

/* aside 默认让 grid stretch（不显式设置 align-self），
   这样 aside 高度等于正文高度，position:sticky 才能在长文中持续粘住。
   注意：不能给 .guest-matrix 设置 overflow:hidden —— 否则 sticky 失效 */
.guest-matrix {
  transition: opacity 0.24s ease;
  /* min-width:0 已在模板的 utility class 中 */
}

@media (min-width: 1280px) {
  .guest-detail-grid--expanded {
    grid-template-columns: minmax(0, 1fr) minmax(18rem, 24rem);
  }

  .guest-detail-grid--collapsed {
    grid-template-columns: minmax(0, 1fr) 0fr;
  }

  /* 折叠态才隐藏溢出；展开态保持可见，sticky 不被破坏 */
  .guest-matrix--collapsed {
    opacity: 0;
    pointer-events: none;
    overflow: hidden;
  }
}

/* 右侧矩阵：随屏幕滚动悬浮。
   GuestLayout 是 window 滚动，aside 不设 align-self:start，让 grid stretch 把它撑满，
   于是 sticky 的有效范围 = 正文高度，在长文中能持续贴顶。
   max-height + overflow-y 防止内容超出视口被截掉。 */
.guest-matrix__sticky {
  position: sticky;
  top: 5.5rem;
  max-height: calc(100vh - 6.5rem);
  overflow-y: auto;
  overscroll-behavior: contain;
  /* 弱化滚动条 */
  scrollbar-width: thin;
  scrollbar-color: rgba(148, 163, 184, 0.18) transparent;
}
.guest-matrix__sticky::-webkit-scrollbar {
  width: 6px;
}
.guest-matrix__sticky::-webkit-scrollbar-track {
  background: transparent;
}
.guest-matrix__sticky::-webkit-scrollbar-thumb {
  background-color: rgba(148, 163, 184, 0.18);
  border-radius: 3px;
}
.guest-matrix__sticky::-webkit-scrollbar-thumb:hover {
  background-color: rgba(148, 163, 184, 0.35);
}

.guest-soft-panel {
  background: var(--cn-surface) !important;
  border-color: var(--cn-border) !important;
  color: var(--cn-text);
  box-shadow: var(--cn-shadow-xs);
}

.guest-soft-panel h2 {
  color: var(--cn-text-muted) !important;
}

.guest-soft-panel button:not(.guest-link-card) {
  color: var(--cn-text-muted) !important;
}

.guest-soft-panel button:not(.guest-link-card):hover {
  background: var(--cn-surface-muted) !important;
  color: var(--cn-text) !important;
}

.guest-stat-card,
.guest-link-card {
  background: var(--cn-bg-subtle) !important;
  border-color: var(--cn-border) !important;
  color: var(--cn-text-soft) !important;
}

.guest-stat-card div:first-of-type {
  color: var(--cn-text) !important;
}

.guest-stat-card div:last-of-type,
.guest-soft-panel p {
  color: var(--cn-text-muted) !important;
}

.guest-link-card:hover {
  background: var(--cn-surface-muted) !important;
  border-color: var(--cn-border-strong) !important;
  color: var(--cn-link-hover) !important;
}

.guest-soft-panel .toc-list :deep(.toc-link) {
  color: var(--cn-text-soft);
}

.guest-soft-panel .toc-list :deep(.toc-link:hover) {
  background: var(--cn-surface-muted);
  color: var(--cn-text);
}

.guest-soft-panel .toc-list :deep(.toc-link--active) {
  background: rgba(37, 99, 235, 0.08);
  border-left-color: var(--cn-link);
  color: var(--cn-link);
}

.guest-soft-panel .toc-list :deep(.toc-level-1),
.guest-soft-panel .toc-list :deep(.toc-level-2),
.guest-soft-panel .toc-list :deep(.toc-level-3),
.guest-soft-panel .toc-list :deep(.toc-level-4) {
  color: var(--cn-text-soft);
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
  position: relative;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-radius: 0.375rem;
  padding: 0.25rem 0.5rem;
  color: #94a3b8;
  text-decoration: none;
  transition: color 0.18s ease, background 0.18s ease, transform 0.18s ease, border-color 0.18s ease;
  border-left: 2px solid transparent;
}
.toc-list :deep(.toc-link:hover) {
  background: rgba(34, 211, 238, 0.08);
  color: #cffafe;
  transform: translateX(2px);
}
.toc-list :deep(.toc-link:active) {
  transform: translateX(1px) scale(0.98);
}
/* 当前激活的 TOC 项：左侧 cyan 竖条 + 高亮背景 */
.toc-list :deep(.toc-link--active) {
  color: #67e8f9;
  background: rgba(34, 211, 238, 0.12);
  border-left-color: #22d3ee;
  font-weight: 800;
}
.toc-list :deep(.toc-level-1) { font-size: 0.9rem; font-weight: 800; color: #e2e8f0; }
.toc-list :deep(.toc-level-2) { font-size: 0.82rem; font-weight: 700; padding-left: 0.75rem; }
.toc-list :deep(.toc-level-3),
.toc-list :deep(.toc-level-4) { font-size: 0.78rem; padding-left: 1.25rem; }

.article-content {
  color: #cbd5e1;
  font-size: 1rem;
  line-height: 1.8;
  scroll-behavior: smooth;
}
/* 锚点跳转后短暂高亮被定位的标题/段落 */
.article-content :deep(.anchor-flash) {
  animation: anchorFlash 1.6s ease-out;
  border-radius: 0.4rem;
}
@keyframes anchorFlash {
  0%   { background-color: rgba(34, 211, 238, 0.28); box-shadow: 0 0 0 6px rgba(34, 211, 238, 0.18); }
  60%  { background-color: rgba(34, 211, 238, 0.12); box-shadow: 0 0 0 4px rgba(34, 211, 238, 0.08); }
  100% { background-color: transparent;              box-shadow: 0 0 0 0 transparent; }
}
@media (prefers-reduced-motion: reduce) {
  .article-content :deep(.anchor-flash) { animation: none; }
  .toc-list :deep(.toc-link) { transition: none; }
  .toc-list :deep(.toc-link:hover) { transform: none; }
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
.article-content :deep(a),
.article-content :deep(.internal-note-link),
.article-content :deep(.hash-link) {
  color: var(--cn-link);
  cursor: pointer;
  text-decoration: underline;
  text-decoration-color: var(--cn-link-underline);
  text-decoration-thickness: 1px;
  text-underline-offset: 4px;
  transition:
    color var(--cn-fast) var(--cn-ease),
    text-decoration-color var(--cn-fast) var(--cn-ease),
    background-color var(--cn-fast) var(--cn-ease);
}
.article-content :deep(a:hover),
.article-content :deep(.internal-note-link:hover),
.article-content :deep(.hash-link:hover) {
  color: var(--cn-link-hover);
  text-decoration-color: currentColor;
}
.article-content :deep(a:visited) {
  color: var(--cn-link-visited);
}
.article-content :deep(a:focus-visible),
.article-content :deep(.internal-note-link:focus-visible),
.article-content :deep(.hash-link:focus-visible) {
  border-radius: var(--cn-radius-xs);
  outline: 2px solid rgba(37, 99, 235, 0.28);
  outline-offset: 3px;
}
.article-content :deep(.internal-note-link.unresolved) {
  color: var(--cn-text-muted);
  cursor: default;
  text-decoration-color: rgba(120, 120, 116, 0.36);
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
.article-content :deep(.callout) {
  --callout-accent: #22d3ee;
  --callout-bg: rgba(34, 211, 238, 0.08);
  --callout-border: rgba(34, 211, 238, 0.26);
  --callout-title: #a5f3fc;

  position: relative;
  margin: 1.35rem 0;
  border: 1px solid var(--callout-border);
  border-left: 4px solid var(--callout-accent);
  border-radius: 0.85rem;
  background:
    linear-gradient(135deg, var(--callout-bg), rgba(15, 23, 42, 0.62)),
    rgba(2, 6, 23, 0.5);
  padding: 1rem 1.15rem;
  color: #cbd5e1;
  line-height: 1.75;
  box-shadow: 0 14px 34px rgba(2, 6, 23, 0.22), inset 0 1px 0 rgba(255, 255, 255, 0.04);
}
.article-content :deep(.callout-title) {
  margin-bottom: 0.65rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--callout-title);
  font-size: 0.95rem;
  font-weight: 850;
  line-height: 1.35;
}
.article-content :deep(.callout-title::before) {
  content: '';
  width: 0.58rem;
  height: 0.58rem;
  flex: 0 0 auto;
  border-radius: 9999px;
  background: var(--callout-accent);
  box-shadow: 0 0 14px color-mix(in srgb, var(--callout-accent) 58%, transparent);
}
.article-content :deep(.callout-content) {
  color: #cbd5e1;
}
.article-content :deep(.callout-content > :first-child) {
  margin-top: 0;
}
.article-content :deep(.callout-content > :last-child) {
  margin-bottom: 0;
}
.article-content :deep(.callout-content p) {
  margin-bottom: 0.65rem;
}
.article-content :deep(.callout-content ul),
.article-content :deep(.callout-content ol) {
  margin: 0.6rem 0 0.75rem;
}
.article-content :deep(.callout-content li) {
  margin-bottom: 0.25rem;
}
.article-content :deep(.callout-content .table-wrapper),
.article-content :deep(.callout-content pre),
.article-content :deep(.callout-content .mermaid),
.article-content :deep(.callout-content img) {
  margin-top: 0.8rem;
  margin-bottom: 0.8rem;
}
.article-content :deep(.callout .callout) {
  margin: 0.85rem 0;
}
.article-content :deep(.callout--nested) {
  border-radius: 0.7rem;
  padding: 0.82rem 0.95rem;
  background:
    linear-gradient(135deg, var(--callout-bg), rgba(15, 23, 42, 0.78)),
    rgba(15, 23, 42, 0.56);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.035);
}
.article-content :deep(.callout--nested .callout-title) {
  margin-bottom: 0.45rem;
  font-size: 0.88rem;
}
.article-content :deep(.callout-note),
.article-content :deep(.callout-info) {
  --callout-accent: #22d3ee;
  --callout-bg: rgba(34, 211, 238, 0.09);
  --callout-border: rgba(34, 211, 238, 0.28);
  --callout-title: #a5f3fc;
}
.article-content :deep(.callout-tip) {
  --callout-accent: #34d399;
  --callout-bg: rgba(52, 211, 153, 0.1);
  --callout-border: rgba(52, 211, 153, 0.28);
  --callout-title: #bbf7d0;
}
.article-content :deep(.callout-success) {
  --callout-accent: #22c55e;
  --callout-bg: rgba(34, 197, 94, 0.1);
  --callout-border: rgba(34, 197, 94, 0.28);
  --callout-title: #86efac;
}
.article-content :deep(.callout-warning) {
  --callout-accent: #f59e0b;
  --callout-bg: rgba(245, 158, 11, 0.11);
  --callout-border: rgba(245, 158, 11, 0.3);
  --callout-title: #fde68a;
}
.article-content :deep(.callout-question),
.article-content :deep(.callout-example) {
  --callout-accent: #818cf8;
  --callout-bg: rgba(129, 140, 248, 0.1);
  --callout-border: rgba(129, 140, 248, 0.28);
  --callout-title: #c7d2fe;
}
.article-content :deep(.callout-failure),
.article-content :deep(.callout-bug) {
  --callout-accent: #fb7185;
  --callout-bg: rgba(251, 113, 133, 0.1);
  --callout-border: rgba(251, 113, 133, 0.3);
  --callout-title: #fecdd3;
}
.article-content :deep(.callout-quote) {
  --callout-accent: #94a3b8;
  --callout-bg: rgba(148, 163, 184, 0.09);
  --callout-border: rgba(148, 163, 184, 0.24);
  --callout-title: #e2e8f0;
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
