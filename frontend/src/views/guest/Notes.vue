<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { guestNoteApi, type GuestNoteItem, type NoteQueryParams, type PageResult } from '@/api/notes'
import { ChevronLeft, ChevronRight, Eye, FileText, Layers, Search, Tags } from 'lucide-vue-next'

const router = useRouter()

const loading = ref(false)
const notes = ref<GuestNoteItem[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(12)
const keyword = ref('')
const topicId = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function formatDate(raw?: string) {
  if (!raw) return '-'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function visiblePages() {
  const pages: number[] = []
  let start = Math.max(1, currentPage.value - 2)
  const end = Math.min(totalPages.value, start + 4)
  if (end - start < 4) start = Math.max(1, end - 4)
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
}

async function fetchNotes() {
  loading.value = true
  try {
    const params: NoteQueryParams = {
      keyword: keyword.value.trim() || undefined,
      topicId: topicId.value ? Number(topicId.value) : undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    }
    const res = await guestNoteApi.getList(params)
    notes.value = (res as PageResult<GuestNoteItem>).records ?? []
    total.value = (res as PageResult<GuestNoteItem>).total ?? 0
  } finally {
    loading.value = false
  }
}

function search() {
  currentPage.value = 1
  void fetchNotes()
}

function changePage(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  currentPage.value = page
  void fetchNotes()
}

function openNote(id: number) {
  router.push(`/guest/notes/${id}`)
}

onMounted(fetchNotes)
</script>

<template>
  <section class="space-y-6">
    <div class="flex flex-col gap-5 border-b border-white/10 pb-6 lg:flex-row lg:items-end lg:justify-between">
      <div>
        <p class="mb-2 text-xs font-black uppercase tracking-[0.22em] text-cyan-300">Published Notes</p>
        <h1 class="text-3xl font-black tracking-normal text-white sm:text-4xl">公开笔记</h1>
      </div>

      <div class="grid gap-3 sm:grid-cols-[minmax(0,1fr)_8rem_auto] lg:w-[42rem]">
        <label class="relative block">
          <Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
          <input v-model="keyword" class="h-11 w-full rounded-lg border border-white/10 bg-white/[0.03] pl-10 pr-3 text-sm text-white outline-none transition placeholder:text-slate-600 focus:border-cyan-400/50 focus:bg-cyan-400/5" placeholder="搜索标题" @keydown.enter="search" />
        </label>
        <label class="relative block">
          <Layers class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
          <input v-model="topicId" class="h-11 w-full rounded-lg border border-white/10 bg-white/[0.03] pl-10 pr-3 text-sm text-white outline-none transition placeholder:text-slate-600 focus:border-cyan-400/50 focus:bg-cyan-400/5" placeholder="主题" inputmode="numeric" @keydown.enter="search" />
        </label>
        <button class="h-11 rounded-lg bg-cyan-500 px-5 text-sm font-black text-slate-950 transition hover:bg-cyan-300" @click="search">检索</button>
      </div>
    </div>

    <div v-if="loading" class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <div v-for="i in 6" :key="i" class="h-48 animate-pulse rounded-lg border border-white/10 bg-white/[0.03]" />
    </div>

    <div v-else-if="notes.length === 0" class="rounded-lg border border-white/10 bg-white/[0.03] p-12 text-center">
      <FileText class="mx-auto mb-4 h-10 w-10 text-slate-600" />
      <p class="text-sm font-bold text-slate-400">暂无公开笔记</p>
    </div>

    <div v-else class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <article v-for="note in notes" :key="note.id" class="group flex min-h-52 flex-col rounded-lg border border-white/10 bg-white/[0.03] p-5 transition hover:-translate-y-0.5 hover:border-cyan-400/40 hover:bg-cyan-400/[0.04] hover:shadow-[0_18px_40px_rgba(8,47,73,0.28)]">
        <div class="mb-4 flex items-start justify-between gap-3">
          <div class="min-w-0">
            <p class="mb-2 flex items-center text-[10px] font-black uppercase tracking-[0.18em] text-slate-500">
              <Layers class="mr-1.5 h-3.5 w-3.5 text-cyan-300" />
              {{ note.topicName || '未归属主题' }}
            </p>
            <h2 class="line-clamp-2 text-lg font-black leading-snug text-white">{{ note.title }}</h2>
          </div>
          <button class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-white/10 bg-white/[0.04] text-slate-400 transition group-hover:border-cyan-400/40 group-hover:text-cyan-200" title="查看" @click="openNote(note.id)">
            <Eye class="h-4 w-4" />
          </button>
        </div>

        <p class="line-clamp-3 min-h-[4.5rem] text-sm leading-6 text-slate-400">{{ note.description || '这篇笔记还没有描述。' }}</p>

        <div class="mt-5 flex flex-wrap gap-2">
          <span v-for="tag in note.tags?.slice(0, 4)" :key="tag" class="inline-flex items-center rounded-md border border-cyan-400/20 bg-cyan-400/10 px-2 py-1 text-[11px] font-bold text-cyan-100">
            <Tags class="mr-1 h-3 w-3" />
            {{ tag }}
          </span>
        </div>

        <div class="mt-auto flex items-center justify-between border-t border-white/10 pt-4 text-xs text-slate-500">
          <span>{{ formatDate(note.createTime) }}</span>
          <button class="font-black text-cyan-300 transition hover:text-cyan-100" @click="openNote(note.id)">阅读全文</button>
        </div>
      </article>
    </div>

    <div v-if="!loading && notes.length > 0" class="flex items-center justify-between border-t border-white/10 pt-5">
      <span class="text-xs font-bold text-slate-500">共 {{ total }} 篇</span>
      <div class="flex items-center gap-1">
        <button class="flex h-8 w-8 items-center justify-center rounded-md text-slate-500 transition hover:bg-white/5 hover:text-white disabled:opacity-40" :disabled="currentPage <= 1" @click="changePage(currentPage - 1)">
          <ChevronLeft class="h-4 w-4" />
        </button>
        <button v-for="page in visiblePages()" :key="page" class="flex h-8 w-8 items-center justify-center rounded-md text-xs font-black transition" :class="page === currentPage ? 'bg-cyan-400 text-slate-950' : 'text-slate-400 hover:bg-white/5 hover:text-white'" @click="changePage(page)">
          {{ page }}
        </button>
        <button class="flex h-8 w-8 items-center justify-center rounded-md text-slate-500 transition hover:bg-white/5 hover:text-white disabled:opacity-40" :disabled="currentPage >= totalPages" @click="changePage(currentPage + 1)">
          <ChevronRight class="h-4 w-4" />
        </button>
      </div>
    </div>
  </section>
</template>
