<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { noteApi } from '@/api/notes'
import type { NoteModifyDiffDetailVO, NoteDiffVO } from '@/api/notes'
import { ArrowLeft, FileDiff, FileText, Loader2, CheckCircle2, XCircle } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref<string | null>(null)
const diffDetail = ref<NoteModifyDiffDetailVO | null>(null)
const confirming = ref(false)

function showAlert(msg: string) { window.alert(msg) }
function showConfirm(msg: string): boolean { return window.confirm(msg) }

function formatBytes(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, index)).toFixed(index > 0 ? 1 : 0) + ' ' + units[index]
}

function getDiffChanges(oldItems: string[] = [], newItems: string[] = []) {
  const oldSet = new Set(oldItems)
  const newSet = new Set(newItems)
  return {
    added: newItems.filter(item => !oldSet.has(item)),
    removed: oldItems.filter(item => !newSet.has(item)),
  }
}

function formatDiffSection(label: string, oldItems: string[] = [], newItems: string[] = []) {
  const { added, removed } = getDiffChanges(oldItems, newItems)
  if (!added.length && !removed.length) return `${label}：无变化`
  const changes: string[] = []
  if (added.length) changes.push(`新增 ${added.join('、')}`)
  if (removed.length) changes.push(`移除 ${removed.join('、')}`)
  return `${label}：${changes.join('；')}`
}

const diffSnapshot = computed<NoteDiffVO | null>(() => diffDetail.value?.diff?.diff ?? null)
const diffSummary = computed(() => {
  if (!diffSnapshot.value) return []
  const diff = diffSnapshot.value
  return [
    formatDiffSection('标签', diff.oldTags, diff.newTags),
    formatDiffSection('图片', diff.oldImages, diff.newImages),
    formatDiffSection('双链', diff.oldNoteNames, diff.newNoteNames),
  ]
})

async function fetchDiff() {
  const noteId = Number(route.params.noteId)
  if (!noteId || Number.isNaN(noteId)) {
    error.value = '无效的笔记 ID'
    loading.value = false
    return
  }
  loading.value = true
  error.value = null
  try {
    diffDetail.value = await noteApi.getDiff(noteId)
  } catch (e: any) {
    error.value = e?.message || '未找到可查看的 Diff 信息'
  } finally {
    loading.value = false
  }
}

async function handleConfirmChange(confirm: boolean) {
  if (!diffDetail.value || confirming.value) return
  const noteId = diffDetail.value.noteId
  const tip = confirm ? '确认应用本次变更吗？' : '确认取消变更并回滚吗？'
  if (!showConfirm(tip)) return
  confirming.value = true
  try {
    await noteApi.confirmChange(noteId, confirm)
    showAlert(confirm ? '变更已确认并生效。' : '变更已取消并回滚。')
    router.push(`/user/notes/${noteId}`)
  } catch {
    showAlert(confirm ? '确认变更失败，请重试。' : '取消变更失败，请重试。')
  } finally {
    confirming.value = false
  }
}

onMounted(() => {
  fetchDiff()
})
</script>

<template>
  <div class="relative max-w-[1400px] mx-auto space-y-6 pb-12">
    <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
      <div class="flex items-center space-x-3">
        <button class="group flex items-center space-x-2 text-slate-400 hover:text-white transition-colors bg-white/5 hover:bg-white/10 px-4 py-2 rounded-xl border border-white/5" @click="$router.push('/user/notes')">
          <ArrowLeft class="w-4 h-4 group-hover:-translate-x-1 transition-transform" />
          <span class="text-sm font-bold">返回列表</span>
        </button>
        <div class="hidden md:flex items-center space-x-2 text-slate-400 text-sm">
          <FileDiff class="w-4 h-4 text-amber-400" />
          <span>笔记 Diff 信息</span>
        </div>
      </div>
      <div class="flex items-center space-x-2">
        <button class="flex items-center space-x-1.5 px-3 py-2 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 hover:bg-emerald-500 hover:text-white transition-all text-xs font-bold" :disabled="confirming || !diffDetail" @click="handleConfirmChange(true)">
          <CheckCircle2 class="w-3.5 h-3.5" />
          <span>{{ confirming ? '处理中...' : '确认变更' }}</span>
        </button>
        <button class="flex items-center space-x-1.5 px-3 py-2 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-300 hover:bg-rose-500 hover:text-white transition-all text-xs font-bold" :disabled="confirming || !diffDetail" @click="handleConfirmChange(false)">
          <XCircle class="w-3.5 h-3.5" />
          <span>{{ confirming ? '处理中...' : '取消变更' }}</span>
        </button>
      </div>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-24">
      <Loader2 class="w-6 h-6 text-amber-400 animate-spin" />
      <span class="ml-3 text-sm text-slate-500">加载 Diff 信息中...</span>
    </div>

    <div v-else-if="error" class="glass-panel rounded-2xl p-10 text-center">
      <div class="w-12 h-12 rounded-xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center mx-auto mb-4">
        <XCircle class="w-5 h-5 text-rose-400" />
      </div>
      <h2 class="text-lg font-bold text-white mb-2">无法读取 Diff</h2>
      <p class="text-sm text-slate-400">{{ error }}</p>
    </div>

    <template v-else-if="diffDetail">
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div class="glass-panel rounded-2xl p-5 border border-white/5">
          <div class="text-xs text-slate-500 mb-2">旧版本大小</div>
          <div class="text-lg font-bold text-white">{{ formatBytes(diffDetail.diff?.oldFileSize ?? 0) }}</div>
        </div>
        <div class="glass-panel rounded-2xl p-5 border border-white/5">
          <div class="text-xs text-slate-500 mb-2">新版本大小</div>
          <div class="text-lg font-bold text-white">{{ formatBytes(diffDetail.diff?.newFileSize ?? 0) }}</div>
        </div>
        <div class="glass-panel rounded-2xl p-5 border border-white/5">
          <div class="text-xs text-slate-500 mb-2">Diff 变化量</div>
          <div class="text-lg font-bold text-white">{{ formatBytes(diffDetail.diff?.diffFileSize ?? 0) }}</div>
        </div>
      </div>

      <div class="glass-panel rounded-2xl p-6 border border-white/5">
        <div class="flex items-center space-x-2 mb-3">
          <FileDiff class="w-4 h-4 text-amber-400" />
          <h3 class="text-sm font-bold text-white">关联变更摘要</h3>
        </div>
        <div class="space-y-2 text-sm text-slate-300">
          <p v-for="(line, idx) in diffSummary" :key="idx">{{ line }}</p>
          <p v-if="diffSummary.length === 0" class="text-slate-500">暂无可展示的差异摘要。</p>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div class="glass-panel rounded-2xl border border-white/5 overflow-hidden">
          <div class="flex items-center justify-between px-5 py-3 border-b border-white/5 bg-black/30">
            <div class="flex items-center space-x-2">
              <FileText class="w-4 h-4 text-slate-400" />
              <span class="text-xs font-bold text-slate-300">旧版本内容</span>
            </div>
          </div>
          <pre class="p-5 text-xs text-slate-300 font-mono whitespace-pre-wrap break-all max-h-[60vh] overflow-y-auto">{{ diffDetail.oldSource }}</pre>
        </div>

        <div class="glass-panel rounded-2xl border border-white/5 overflow-hidden">
          <div class="flex items-center justify-between px-5 py-3 border-b border-white/5 bg-black/30">
            <div class="flex items-center space-x-2">
              <FileText class="w-4 h-4 text-emerald-400" />
              <span class="text-xs font-bold text-slate-300">新版本内容</span>
            </div>
          </div>
          <pre class="p-5 text-xs text-slate-300 font-mono whitespace-pre-wrap break-all max-h-[60vh] overflow-y-auto">{{ diffDetail.newSource }}</pre>
        </div>
      </div>
    </template>
  </div>
</template>
