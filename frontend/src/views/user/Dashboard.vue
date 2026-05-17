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
  <div class="max-w-5xl mx-auto space-y-8">
    <!-- 欢迎卡片 -->
    <div class="glass-panel rounded-3xl p-8 relative overflow-hidden group">
      <div class="absolute -top-24 -right-24 w-64 h-64 bg-indigo-500/10 blur-[60px] rounded-full group-hover:bg-indigo-500/20 transition-all duration-700 pointer-events-none"></div>

      <div class="relative z-10">
        <div class="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-white/5 border border-white/10 mb-4">
          <Sparkles class="text-indigo-400 w-3 h-3" />
          <span class="text-[10px] text-indigo-300 font-bold uppercase tracking-[0.2em]">System Optimal</span>
        </div>
        <h2 class="text-3xl font-black text-white tracking-tight mb-2">
          欢迎回来，<span class="text-transparent bg-clip-text bg-gradient-to-r from-indigo-400 to-purple-400">{{ authStore.user?.nickname || 'Node Explorer' }}</span>
        </h2>
        <p class="text-slate-400 text-sm max-w-xl leading-relaxed">
          您的数字化资产已同步就绪。尽情享受沉浸式的创作与管理体验。
        </p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <!-- 笔记统计 -->
      <div class="glass-panel rounded-2xl p-6 hover:bg-white/[0.04] transition-colors border-t border-white/10 border-l-0 border-r-0 border-b-0">
        <div class="flex justify-between items-start mb-4">
          <div class="p-2.5 bg-indigo-500/10 rounded-xl text-indigo-400">
            <FileText class="w-5 h-5" />
          </div>
          <span v-if="noteStats" class="text-xs font-bold text-emerald-400 bg-emerald-400/10 px-2 py-1 rounded-lg">
            公开 {{ formatNumber(noteStats.publicNoteCount) }}
          </span>
        </div>
        <h3 class="text-slate-400 text-xs font-bold uppercase tracking-widest mb-1">Total Notes</h3>
        <div v-if="loading" class="flex items-center space-x-2 text-slate-500">
          <Loader2 class="w-4 h-4 animate-spin" />
          <span class="text-xs">加载中...</span>
        </div>
        <div v-else class="text-3xl font-black text-white">{{ formatNumber(noteStats?.noteTotalCount ?? 0) }}</div>
        <p v-if="!loading && noteStats" class="text-[10px] text-slate-500 mt-1 uppercase tracking-wider">
          已通过 {{ formatNumber(noteStats.passedNoteCount) }} 篇
        </p>
      </div>

      <!-- 主题与标签统计 -->
      <div class="glass-panel rounded-2xl p-6 hover:bg-white/[0.04] transition-colors border-t border-white/10 border-l-0 border-r-0 border-b-0">
        <div class="flex justify-between items-start mb-4">
          <div class="p-2.5 bg-purple-500/10 rounded-xl text-purple-400">
            <Layers class="w-5 h-5" />
          </div>
        </div>
        <h3 class="text-slate-400 text-xs font-bold uppercase tracking-widest mb-1">Topics & Tags</h3>
        <div v-if="loading" class="flex items-center space-x-2 text-slate-500">
          <Loader2 class="w-4 h-4 animate-spin" />
          <span class="text-xs">加载中...</span>
        </div>
        <div v-else class="text-3xl font-black text-white">
          {{ formatNumber(topicStats?.topicCount ?? 0) }} <span class="text-lg text-slate-500 font-normal">/</span> {{ formatNumber(tagStats?.tagCount ?? 0) }}
        </div>
        <p v-if="!loading" class="text-[10px] text-slate-500 mt-1 uppercase tracking-wider">
          主题 / 标签
        </p>
      </div>

      <!-- 存储用量 -->
      <div class="glass-panel rounded-2xl p-6 hover:bg-white/[0.04] transition-colors border-t border-white/10 border-l-0 border-r-0 border-b-0">
        <div class="flex justify-between items-start mb-4">
          <div class="p-2.5 bg-rose-500/10 rounded-xl text-rose-400">
            <HardDrive class="w-5 h-5" />
          </div>
          <span v-if="!loading && overview" class="text-xs font-bold text-slate-400 bg-white/5 px-2 py-1 rounded-lg">
            {{ storagePercent }}% Used
          </span>
        </div>
        <h3 class="text-slate-400 text-xs font-bold uppercase tracking-widest mb-1">Storage Usage</h3>
        <div v-if="loading" class="flex items-center space-x-2 text-slate-500">
          <Loader2 class="w-4 h-4 animate-spin" />
          <span class="text-xs">加载中...</span>
        </div>
        <template v-else>
          <div class="text-3xl font-black text-white">
            {{ formatBytes(overview?.usedStorageBytes ?? 0).split(' ')[0] }} <span class="text-lg text-slate-500">{{ formatBytes(overview?.usedStorageBytes ?? 0).split(' ')[1] }}</span>
          </div>
          <div class="w-full bg-black/40 h-1.5 rounded-full mt-4 overflow-hidden">
            <div class="bg-gradient-to-r from-rose-500 to-indigo-500 h-full rounded-full transition-all duration-700" :style="{ width: storagePercent + '%' }"></div>
          </div>
          <p class="text-[10px] text-slate-500 mt-1 uppercase tracking-wider">
            上限 {{ formatBytes(overview?.maxStorageBytes ?? 0) }}
          </p>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.glass-panel {
  background: rgba(255, 255, 255, 0.02);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.05);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.05);
}
</style>
