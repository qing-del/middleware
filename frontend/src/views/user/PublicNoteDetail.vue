<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userPublicNoteApi, type PublicNoteDetailVO } from '@/api/notes'
import { enhanceArticleContent } from '@/utils/enhanceArticle'
import { ArrowLeft, Calendar, FileText, Hash, Image as ImageIcon, Layers, Link, Loader2, Tags } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref<string | null>(null)
const note = ref<PublicNoteDetailVO | null>(null)
const articleContentRef = ref<HTMLElement | null>(null)

function formatDate(raw?: string) {
  if (!raw) return '-'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
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
  try {
    note.value = await userPublicNoteApi.getDetail(noteId)
  } catch (e: any) {
    error.value = e?.message || '公开笔记不存在'
  } finally {
    loading.value = false
  }
}

function onArticleClick(e: Event) {
  const internal = (e.target as HTMLElement).closest('.internal-note-link') as HTMLAnchorElement | null
  if (!internal) return
  e.preventDefault()
  const noteId = internal.dataset.noteId
  const anchor = internal.dataset.anchor
  if (!noteId || noteId === 'null') return
  router.push(anchor ? `/user/public-notes/${noteId}#${anchor}` : `/user/public-notes/${noteId}`)
}

watch(
  () => note.value?.converted?.bodyHtml,
  async (html) => {
    if (!html) return
    await nextTick()
    await enhanceArticleContent(articleContentRef.value)
  },
  { immediate: true }
)

onMounted(fetchNote)
</script>

<template>
  <section class="relative">
    <div v-if="loading" class="flex min-h-[50vh] items-center justify-center">
      <Loader2 class="h-8 w-8 animate-spin text-slate-500" />
    </div>

    <div v-else-if="error" class="public-empty-panel mx-auto max-w-2xl rounded-xl p-10 text-center">
      <FileText class="mx-auto mb-4 h-10 w-10 text-slate-400" />
      <h1 class="mb-2 text-xl font-black">无法打开笔记</h1>
      <p class="text-sm">{{ error }}</p>
      <button class="public-primary-button mt-6 rounded-lg px-5 py-2 text-sm font-black transition" @click="router.push('/user/public-notes')">返回广场</button>
    </div>

    <template v-else-if="note">
      <button class="public-ghost-button mb-6 inline-flex items-center rounded-lg px-3 py-2 text-sm font-bold transition" @click="router.push('/user/public-notes')">
        <ArrowLeft class="mr-2 h-4 w-4" />
        返回广场
      </button>

      <div class="grid gap-8 xl:grid-cols-[minmax(0,1fr)_22rem]">
        <article class="min-w-0">
          <header class="mb-8">
            <div class="public-meta mb-4 flex flex-wrap items-center gap-3 text-xs font-bold">
              <span class="public-public-chip inline-flex items-center rounded-md px-2.5 py-1">
                <Layers class="mr-1.5 h-3.5 w-3.5" />
                {{ note.topicName || '未归属主题' }}
              </span>
              <span class="inline-flex items-center">
                <Calendar class="mr-1.5 h-3.5 w-3.5" />
                {{ formatDate(note.createTime) }}
              </span>
            </div>
            <h1 class="public-title max-w-4xl text-3xl font-black leading-tight tracking-normal sm:text-5xl">{{ note.title }}</h1>
            <p v-if="note.description" class="public-description mt-5 max-w-3xl text-base leading-7">{{ note.description }}</p>
            <div v-if="note.tags?.length" class="mt-5 flex flex-wrap gap-2">
              <span v-for="tag in note.tags" :key="tag" class="public-tag-chip inline-flex items-center rounded-md px-2.5 py-1 text-xs font-bold">
                <Tags class="mr-1.5 h-3.5 w-3.5" />
                {{ tag }}
              </span>
            </div>
          </header>

          <div ref="articleContentRef" class="article-content public-article-panel rounded-xl p-5 sm:p-8" v-html="note.converted?.bodyHtml" @click="onArticleClick" />
        </article>

        <aside class="space-y-4">
          <section class="public-soft-panel rounded-xl p-4">
            <h2 class="mb-3 text-xs font-black uppercase tracking-[0.18em]">资源矩阵</h2>
            <div class="grid grid-cols-3 gap-2">
              <div class="stat-card"><Hash class="h-4 w-4 text-blue-600" /><b>{{ note.tags?.length ?? 0 }}</b><span>Tags</span></div>
              <div class="stat-card"><ImageIcon class="h-4 w-4 text-amber-600" /><b>{{ note.images?.length ?? 0 }}</b><span>Images</span></div>
              <div class="stat-card"><Link class="h-4 w-4 text-emerald-600" /><b>{{ note.eachNotes?.length ?? 0 }}</b><span>Links</span></div>
            </div>
          </section>
          <section class="public-soft-panel rounded-xl p-4">
            <h2 class="mb-3 text-xs font-black uppercase tracking-[0.18em]">公开双链</h2>
            <div v-if="note.eachNotes?.length" class="space-y-2">
              <button v-for="link in note.eachNotes" :key="`${link.targetNoteId}-${link.anchor}`" class="public-link-card block w-full rounded-md px-3 py-2 text-left text-sm transition" @click="router.push(link.anchor ? `/user/public-notes/${link.targetNoteId}#${link.anchor}` : `/user/public-notes/${link.targetNoteId}`)">
                {{ link.nickname || link.targetNoteTitle || link.parsedNoteName }}
              </button>
            </div>
            <p v-else class="text-sm">暂无公开双链。</p>
          </section>
        </aside>
      </div>
    </template>
  </section>
</template>

<style scoped>
.public-empty-panel,
.public-soft-panel,
.public-article-panel {
  border: 1px solid var(--cn-border);
  background: var(--cn-surface);
  color: var(--cn-text);
  box-shadow: var(--cn-shadow-xs);
}

.public-empty-panel h1,
.public-title {
  color: var(--cn-text);
}

.public-empty-panel p,
.public-description,
.public-meta,
.public-soft-panel h2,
.public-soft-panel p {
  color: var(--cn-text-muted);
}

.public-primary-button {
  border: 1px solid var(--cn-accent);
  background: var(--cn-accent);
  color: var(--cn-text-inverse);
}

.public-primary-button:hover {
  background: var(--cn-accent-hover);
}

.public-ghost-button {
  border: 1px solid var(--cn-border);
  background: var(--cn-surface);
  color: var(--cn-text-soft);
}

.public-ghost-button:hover {
  border-color: var(--cn-border-strong);
  background: var(--cn-surface-muted);
  color: var(--cn-text);
}

.public-public-chip,
.public-tag-chip {
  border: 1px solid rgba(14, 116, 144, 0.22);
  background: #ecfeff;
  color: #155e75;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.72) inset;
}

.public-tag-chip {
  border-color: rgba(37, 99, 235, 0.18);
  background: #eff6ff;
  color: #1d4ed8;
}

.public-link-card {
  border: 1px solid var(--cn-border);
  background: var(--cn-bg-subtle);
  color: var(--cn-text-soft);
}

.public-link-card:hover {
  border-color: var(--cn-border-strong);
  background: var(--cn-surface-muted);
  color: var(--cn-link-hover);
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  border-radius: 0.45rem;
  border: 1px solid var(--cn-border);
  background: var(--cn-bg-subtle);
  padding: 0.75rem;
}

.stat-card b {
  color: var(--cn-text);
  font-size: 1.1rem;
}

.stat-card span {
  color: var(--cn-text-muted);
  font-size: 0.62rem;
  font-weight: 900;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.article-content {
  color: var(--cn-text-soft);
  font-size: 1rem;
  line-height: 1.8;
}

.article-content :deep(h1),
.article-content :deep(h2),
.article-content :deep(h3) {
  color: var(--cn-text);
  font-weight: 900;
  margin: 1.5rem 0 1rem;
}

.article-content :deep(h4),
.article-content :deep(h5),
.article-content :deep(h6) {
  color: var(--cn-text);
  font-weight: 800;
  margin: 1.25rem 0 0.75rem;
}

.article-content :deep(p) {
  margin-bottom: 1rem;
}

.article-content :deep(a),
.article-content :deep(.internal-note-link) {
  color: var(--cn-link);
  cursor: pointer;
  text-decoration: underline;
  text-decoration-color: var(--cn-link-underline);
  text-underline-offset: 4px;
}

.article-content :deep(a:hover),
.article-content :deep(.internal-note-link:hover) {
  color: var(--cn-link-hover);
  text-decoration-color: currentColor;
}

.article-content :deep(blockquote) {
  margin: 1.4rem 0;
  border-left: 4px solid #2563eb;
  border-radius: 0 0.6rem 0.6rem 0;
  background: rgba(37, 99, 235, 0.06);
  padding: 1rem 1.25rem;
  color: var(--cn-text-soft);
}

.article-content :deep(ul),
.article-content :deep(ol) {
  margin-bottom: 1.25rem;
  padding-left: 1.5rem;
}

.article-content :deep(ul) {
  list-style-type: disc;
}

.article-content :deep(ol) {
  list-style-type: decimal;
}

.article-content :deep(li) {
  margin-bottom: 0.35rem;
}

.article-content :deep(pre) {
  margin: 1.4rem 0;
  overflow-x: auto;
  border-radius: 0.75rem;
  border: 1px solid #1f2937;
  background: #0f172a;
  padding: 1rem;
  color: #e5e7eb;
}

.article-content :deep(:not(pre) > code) {
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 0.35rem;
  background: rgba(37, 99, 235, 0.07);
  padding: 0.15rem 0.35rem;
  color: #1d4ed8;
}

.article-content :deep(.table-wrapper) {
  margin: 1.5rem 0;
  overflow-x: auto;
  border-radius: 0.75rem;
  border: 1px solid var(--cn-border);
}

.article-content :deep(table) {
  width: 100%;
  min-width: 36rem;
  border-collapse: collapse;
}

.article-content :deep(th),
.article-content :deep(td) {
  border-bottom: 1px solid var(--cn-border);
  padding: 0.75rem 1rem;
  text-align: left;
}

.article-content :deep(th) {
  background: var(--cn-bg-subtle);
  color: var(--cn-text-muted);
  font-size: 0.78rem;
  text-transform: uppercase;
}

.article-content :deep(img) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 1.5rem auto;
  border-radius: 0.75rem;
  border: 1px solid var(--cn-border);
}

.article-content :deep(.mermaid) {
  margin: 1.5rem 0;
  overflow-x: auto;
  border-radius: 0.75rem;
  border: 1px solid var(--cn-border);
  background: #fff;
  padding: 1rem;
}
</style>
