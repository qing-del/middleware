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
      <Loader2 class="h-8 w-8 animate-spin text-cyan-300" />
    </div>

    <div v-else-if="error" class="mx-auto max-w-2xl rounded-lg border border-white/10 bg-white/[0.03] p-10 text-center">
      <FileText class="mx-auto mb-4 h-10 w-10 text-slate-600" />
      <h1 class="mb-2 text-xl font-black text-white">无法打开笔记</h1>
      <p class="text-sm text-slate-400">{{ error }}</p>
      <button class="mt-6 rounded-lg bg-cyan-500 px-5 py-2 text-sm font-black text-slate-950 transition hover:bg-cyan-300" @click="router.push('/user/public-notes')">返回广场</button>
    </div>

    <template v-else-if="note">
      <button class="mb-6 inline-flex items-center rounded-lg border border-white/10 bg-white/[0.03] px-3 py-2 text-sm font-bold text-slate-300 transition hover:border-cyan-400/40 hover:text-cyan-100" @click="router.push('/user/public-notes')">
        <ArrowLeft class="mr-2 h-4 w-4" />
        返回广场
      </button>

      <div class="grid gap-8 xl:grid-cols-[minmax(0,1fr)_22rem]">
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
            <div v-if="note.tags?.length" class="mt-5 flex flex-wrap gap-2">
              <span v-for="tag in note.tags" :key="tag" class="inline-flex items-center rounded-md border border-cyan-400/20 bg-cyan-400/10 px-2.5 py-1 text-xs font-bold text-cyan-100">
                <Tags class="mr-1.5 h-3.5 w-3.5" />
                {{ tag }}
              </span>
            </div>
          </header>

          <div ref="articleContentRef" class="article-content rounded-lg border border-white/10 bg-white/[0.025] p-5 sm:p-8" v-html="note.converted?.bodyHtml" @click="onArticleClick" />
        </article>

        <aside class="space-y-4">
          <section class="rounded-lg border border-white/10 bg-white/[0.03] p-4">
            <h2 class="mb-3 text-xs font-black uppercase tracking-[0.18em] text-slate-400">资源矩阵</h2>
            <div class="grid grid-cols-3 gap-2">
              <div class="stat-card"><Hash class="h-4 w-4 text-cyan-300" /><b>{{ note.tags?.length ?? 0 }}</b><span>Tags</span></div>
              <div class="stat-card"><ImageIcon class="h-4 w-4 text-amber-300" /><b>{{ note.images?.length ?? 0 }}</b><span>Images</span></div>
              <div class="stat-card"><Link class="h-4 w-4 text-emerald-300" /><b>{{ note.eachNotes?.length ?? 0 }}</b><span>Links</span></div>
            </div>
          </section>
          <section class="rounded-lg border border-white/10 bg-white/[0.03] p-4">
            <h2 class="mb-3 text-xs font-black uppercase tracking-[0.18em] text-slate-400">公开双链</h2>
            <div v-if="note.eachNotes?.length" class="space-y-2">
              <button v-for="link in note.eachNotes" :key="`${link.targetNoteId}-${link.anchor}`" class="block w-full rounded-md border border-white/10 bg-white/[0.03] px-3 py-2 text-left text-sm text-slate-300 transition hover:border-cyan-400/40 hover:text-cyan-100" @click="router.push(link.anchor ? `/user/public-notes/${link.targetNoteId}#${link.anchor}` : `/user/public-notes/${link.targetNoteId}`)">
                {{ link.nickname || link.targetNoteTitle || link.parsedNoteName }}
              </button>
            </div>
            <p v-else class="text-sm text-slate-500">暂无公开双链。</p>
          </section>
        </aside>
      </div>
    </template>
  </section>
</template>

<style scoped>
.stat-card {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  border-radius: 0.45rem;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
  padding: 0.75rem;
}

.stat-card b {
  color: white;
  font-size: 1.1rem;
}

.stat-card span {
  color: #64748b;
  font-size: 0.62rem;
  font-weight: 900;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.article-content {
  color: #cbd5e1;
  font-size: 1rem;
  line-height: 1.8;
}

.article-content :deep(h1),
.article-content :deep(h2),
.article-content :deep(h3) {
  color: white;
  font-weight: 900;
  margin: 1.5rem 0 1rem;
}

.article-content :deep(p) {
  margin-bottom: 1rem;
}

.article-content :deep(a),
.article-content :deep(.internal-note-link) {
  color: #67e8f9;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 4px;
}

.article-content :deep(pre) {
  overflow-x: auto;
  border-radius: 0.75rem;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: #08111f;
  padding: 1rem;
}

.article-content :deep(img) {
  max-width: 100%;
  border-radius: 0.75rem;
}
</style>
