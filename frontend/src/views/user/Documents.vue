<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { FileText, Loader2, RefreshCw, Users } from 'lucide-vue-next'
import { documentApi, type DocumentMetadata } from '@/api/documents'

const loading = ref(true)
const error = ref<string | null>(null)
const documents = ref<DocumentMetadata[]>([])

const visibleDocuments = computed(() => documents.value.filter(document => !document.deleted))

function formatTime(timestamp: number): string {
  if (!timestamp) return '-'
  const value = new Date(timestamp)
  if (Number.isNaN(value.getTime())) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  }).format(value)
}

async function loadDocuments() {
  loading.value = true
  error.value = null
  try {
    documents.value = await documentApi.list()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法加载协作文档'
  } finally {
    loading.value = false
  }
}

onMounted(() => void loadDocuments())
</script>

<template>
  <section class="document-workspace">
    <header class="workspace-hero">
      <div>
        <span class="eyebrow">COLLABORATIVE DOCUMENTS</span>
        <h1>笔记模块</h1>
        <p>这是新的实时协作文档空间；旧 Markdown 笔记仍可通过侧边栏的“笔记模块（旧）”访问。</p>
      </div>
      <button class="refresh-button" type="button" :disabled="loading" @click="loadDocuments">
        <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
        <RefreshCw v-else class="h-4 w-4" />
        刷新
      </button>
    </header>

    <div v-if="loading" class="state-panel">
      <Loader2 class="h-5 w-5 animate-spin" />
      正在加载协作文档…
    </div>
    <div v-else-if="error" class="state-panel is-error">
      <p>{{ error }}</p>
      <button class="refresh-button" type="button" @click="loadDocuments">重新加载</button>
    </div>
    <div v-else-if="visibleDocuments.length === 0" class="state-panel">
      <FileText class="h-8 w-8" />
      <div>
        <h2>还没有协作文档</h2>
        <p>协同编辑器将在这个新的笔记模块中创建和打开文档。</p>
      </div>
    </div>
    <div v-else class="document-grid">
      <article v-for="document in visibleDocuments" :key="document.documentId" class="document-card">
        <FileText class="h-5 w-5" />
        <div class="min-w-0 flex-1">
          <h2 class="truncate">{{ document.title }}</h2>
          <p>最后修改：{{ formatTime(document.lastModifyTime) }}</p>
        </div>
        <span class="document-id">#{{ document.documentId }}</span>
      </article>
    </div>

    <p class="workspace-footnote"><Users class="h-3.5 w-3.5" /> 第一版按个人文档域展示，仅返回当前登录用户的文档。</p>
  </section>
</template>

<style scoped>
.document-workspace { max-width: 1120px; margin: 0 auto; }
.workspace-hero { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; border: 1px solid var(--cn-border); border-radius: var(--cn-radius-lg); background: var(--cn-surface); box-shadow: var(--cn-shadow-xs); padding: 28px; }
.eyebrow { color: var(--cn-text-muted); font-size: 10px; font-weight: 800; letter-spacing: .18em; }
h1 { margin: 8px 0; color: var(--cn-text); font-size: 28px; font-weight: 800; }
.workspace-hero p, .state-panel p, .document-card p, .workspace-footnote { color: var(--cn-text-muted); font-size: 13px; line-height: 1.6; }
.workspace-hero p { max-width: 620px; margin: 0; }
.refresh-button { display: inline-flex; align-items: center; gap: 8px; border: 1px solid var(--cn-border); border-radius: var(--cn-radius-sm); background: var(--cn-surface); color: var(--cn-text-soft); padding: 8px 11px; font-size: 12px; font-weight: 700; transition: all var(--cn-fast) var(--cn-ease); }
.refresh-button:hover:not(:disabled) { border-color: var(--cn-border-strong); background: var(--cn-surface-muted); color: var(--cn-text); }
.refresh-button:disabled { opacity: .65; cursor: wait; }
.state-panel { display: flex; min-height: 180px; align-items: center; justify-content: center; gap: 12px; margin-top: 18px; border: 1px dashed var(--cn-border-strong); border-radius: var(--cn-radius-lg); background: var(--cn-bg-subtle); color: var(--cn-text-soft); text-align: center; }
.state-panel h2 { margin: 0; color: var(--cn-text); font-size: 16px; }
.state-panel p { margin: 4px 0 0; }
.state-panel.is-error { flex-direction: column; color: var(--cn-danger); }
.document-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 14px; margin-top: 18px; }
.document-card { display: flex; align-items: center; gap: 12px; min-width: 0; border: 1px solid var(--cn-border); border-radius: var(--cn-radius-md); background: var(--cn-surface); box-shadow: var(--cn-shadow-xs); padding: 16px; color: var(--cn-accent); }
.document-card h2 { margin: 0; color: var(--cn-text); font-size: 15px; font-weight: 750; }
.document-card p { margin: 4px 0 0; }
.document-id { color: var(--cn-text-faint); font-family: var(--cn-font-mono); font-size: 11px; }
.workspace-footnote { display: inline-flex; align-items: center; gap: 6px; margin: 18px 2px 0; }
@media (max-width: 640px) { .workspace-hero { flex-direction: column; padding: 20px; } .refresh-button { width: 100%; justify-content: center; } }
</style>
