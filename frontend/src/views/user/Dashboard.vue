<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { dashboardApi } from '@/api/dashboard'
import type { NoteStats, TopicStats, TagStats, ImageStats, UserOverview } from '@/api/dashboard'
import {
  Sparkles, FileText, Layers, HardDrive, Loader2
} from 'lucide-vue-next'

const authStore = useAuthStore()

const loading = ref(true)
const noteStats = ref<NoteStats | null>(null)
const topicStats = ref<TopicStats | null>(null)
const tagStats = ref<TagStats | null>(null)
const imageStats = ref<ImageStats | null>(null)
const overview = ref<UserOverview | null>(null)

const storagePercent = computed(() => {
  if (!overview.value || overview.value.maxStorageBytes === 0) return 0
  return Math.round((overview.value.usedStorageBytes / overview.value.maxStorageBytes) * 100)
})

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  const val = bytes / Math.pow(1024, i)
  return val.toFixed(i > 0 ? 1 : 0) + ' ' + units[i]
}

function formatNumber(n: number): string {
  return n.toLocaleString()
}

onMounted(async () => {
  try {
    const [notes, topics, tags, images, userOverview] = await Promise.all([
      dashboardApi.getNoteStats(),
      dashboardApi.getTopicStats(),
      dashboardApi.getTagStats(),
      dashboardApi.getImageStats(),
      dashboardApi.getUserOverview()
    ])
    noteStats.value = notes
    topicStats.value = topics
    tagStats.value = tags
    imageStats.value = images
    overview.value = userOverview
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="dashboard-page">
    <section class="dashboard-hero">
      <div>
        <p class="dashboard-eyebrow">
          <Sparkles class="h-3.5 w-3.5" />
          Workspace ready
        </p>
        <h2>欢迎回来，{{ authStore.user?.nickname || 'Node Explorer' }}</h2>
        <p>你的笔记、主题、标签和素材用量都在这里。保持轻一点，写作会更快进入状态。</p>
      </div>
    </section>

    <section class="metric-grid" aria-label="账户概览">
      <article class="metric-card">
        <div class="metric-head">
          <span class="metric-icon"><FileText class="h-5 w-5" /></span>
          <span v-if="noteStats" class="metric-badge">公开 {{ formatNumber(noteStats.publicNoteCount) }}</span>
        </div>
        <p class="metric-label">Total Notes</p>
        <div v-if="loading" class="metric-loading">
          <Loader2 class="h-4 w-4 animate-spin" />
          加载中...
        </div>
        <strong v-else class="metric-value">{{ formatNumber(noteStats?.noteTotalCount ?? 0) }}</strong>
        <p v-if="!loading && noteStats" class="metric-note">已通过 {{ formatNumber(noteStats.passedNoteCount) }} 篇</p>
      </article>

      <article class="metric-card">
        <div class="metric-head">
          <span class="metric-icon"><Layers class="h-5 w-5" /></span>
        </div>
        <p class="metric-label">Topics & Tags</p>
        <div v-if="loading" class="metric-loading">
          <Loader2 class="h-4 w-4 animate-spin" />
          加载中...
        </div>
        <strong v-else class="metric-value">
          {{ formatNumber(topicStats?.topicCount ?? 0) }}
          <span>/</span>
          {{ formatNumber(tagStats?.tagCount ?? 0) }}
        </strong>
        <p v-if="!loading" class="metric-note">主题 / 标签</p>
      </article>

      <article class="metric-card">
        <div class="metric-head">
          <span class="metric-icon"><HardDrive class="h-5 w-5" /></span>
          <span v-if="!loading && overview" class="metric-badge">{{ storagePercent }}% Used</span>
        </div>
        <p class="metric-label">Storage Usage</p>
        <div v-if="loading" class="metric-loading">
          <Loader2 class="h-4 w-4 animate-spin" />
          加载中...
        </div>
        <template v-else>
          <strong class="metric-value">
            {{ formatBytes(overview?.usedStorageBytes ?? 0).split(' ')[0] }}
            <span>{{ formatBytes(overview?.usedStorageBytes ?? 0).split(' ')[1] }}</span>
          </strong>
          <div class="storage-bar" aria-hidden="true">
            <div :style="{ width: storagePercent + '%' }"></div>
          </div>
          <p class="metric-note">上限 {{ formatBytes(overview?.maxStorageBytes ?? 0) }}</p>
        </template>
      </article>
    </section>
  </div>
</template>

<style scoped>
.dashboard-page {
  max-width: 1120px;
  margin: 0 auto;
  display: grid;
  gap: 20px;
}

.dashboard-hero {
  border: 1px solid var(--cn-border);
  border-radius: var(--cn-radius-lg);
  background: var(--cn-surface);
  padding: 28px;
  box-shadow: var(--cn-shadow-xs);
}

.dashboard-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: var(--cn-text-muted);
  font-size: 11px;
  font-weight: 720;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.dashboard-hero h2 {
  margin: 0;
  color: var(--cn-text);
  font-size: clamp(24px, 3vw, 34px);
  font-weight: 760;
  letter-spacing: 0;
}

.dashboard-hero p:last-child {
  max-width: 620px;
  margin: 10px 0 0;
  color: var(--cn-text-soft);
  font-size: 14px;
  line-height: 1.75;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  min-height: 184px;
  border: 1px solid var(--cn-border);
  border-radius: var(--cn-radius-lg);
  background: var(--cn-surface);
  padding: 22px;
  box-shadow: var(--cn-shadow-xs);
  transition:
    border-color var(--cn-fast) var(--cn-ease),
    box-shadow var(--cn-fast) var(--cn-ease),
    transform var(--cn-fast) var(--cn-ease);
}

.metric-card:hover {
  border-color: var(--cn-border-strong);
  box-shadow: var(--cn-shadow-sm);
  transform: translateY(-1px);
}

.metric-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.metric-icon {
  display: inline-flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--cn-border);
  border-radius: var(--cn-radius-sm);
  background: var(--cn-surface-muted);
  color: var(--cn-text);
}

.metric-badge {
  border: 1px solid var(--cn-border);
  border-radius: 999px;
  padding: 4px 8px;
  background: var(--cn-bg-subtle);
  color: var(--cn-text-soft);
  font-size: 12px;
  font-weight: 650;
}

.metric-label {
  margin: 0 0 6px;
  color: var(--cn-text-muted);
  font-size: 11px;
  font-weight: 720;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.metric-value {
  display: block;
  color: var(--cn-text);
  font-size: 34px;
  font-weight: 760;
  line-height: 1.12;
}

.metric-value span {
  color: var(--cn-text-muted);
  font-size: 18px;
  font-weight: 560;
}

.metric-note,
.metric-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0 0;
  color: var(--cn-text-muted);
  font-size: 12px;
}

.storage-bar {
  height: 6px;
  margin-top: 18px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--cn-surface-muted);
}

.storage-bar > div {
  height: 100%;
  border-radius: inherit;
  background: var(--cn-accent);
  transition: width var(--cn-normal) var(--cn-ease);
}

@media (max-width: 900px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .metric-card,
  .storage-bar > div {
    transition-duration: 0.01s;
  }

  .metric-card:hover {
    transform: none;
  }
}
</style>
