<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
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

const paneOld = ref<HTMLElement | null>(null)
const paneNew = ref<HTMLElement | null>(null)

let isSyncingLeft = false
let isSyncingRight = false

type DiffLineType = 'same' | 'added' | 'removed' | 'empty'

interface DiffLine {
  text: string
  type: DiffLineType
}

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

function buildSideBySideDiff(oldSource: string, newSource: string) {
  const oldLines = (oldSource ?? '').split('\n')
  const newLines = (newSource ?? '').split('\n')
  const n = oldLines.length
  const m = newLines.length
  const dir = new Uint8Array((n + 1) * (m + 1))

  let prev = new Int32Array(m + 1)
  let curr = new Int32Array(m + 1)

  for (let i = 1; i <= n; i += 1) {
    for (let j = 1; j <= m; j += 1) {
      if (oldLines[i - 1] === newLines[j - 1]) {
        curr[j] = prev[j - 1] + 1
        dir[i * (m + 1) + j] = 1
      } else if (prev[j] >= curr[j - 1]) {
        curr[j] = prev[j]
        dir[i * (m + 1) + j] = 2
      } else {
        curr[j] = curr[j - 1]
        dir[i * (m + 1) + j] = 3
      }
    }
    const swap = prev
    prev = curr
    curr = swap
    curr.fill(0)
  }

  const ops: Array<{ type: 'same' | 'added' | 'removed'; oldText?: string; newText?: string }> = []
  let i = n
  let j = m

  while (i > 0 || j > 0) {
    const state = dir[i * (m + 1) + j]
    if (i > 0 && j > 0 && state === 1) {
      ops.push({ type: 'same', oldText: oldLines[i - 1], newText: newLines[j - 1] })
      i -= 1
      j -= 1
    } else if (i > 0 && (j === 0 || state === 2)) {
      ops.push({ type: 'removed', oldText: oldLines[i - 1] })
      i -= 1
    } else {
      ops.push({ type: 'added', newText: newLines[j - 1] })
      j -= 1
    }
  }

  ops.reverse()

  const left: DiffLine[] = []
  const right: DiffLine[] = []

  for (const op of ops) {
    if (op.type === 'same') {
      left.push({ text: op.oldText ?? '', type: 'same' })
      right.push({ text: op.newText ?? '', type: 'same' })
    } else if (op.type === 'removed') {
      left.push({ text: op.oldText ?? '', type: 'removed' })
      right.push({ text: '', type: 'empty' })
    } else {
      left.push({ text: '', type: 'empty' })
      right.push({ text: op.newText ?? '', type: 'added' })
    }
  }

  return { left, right }
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

const diffLines = computed(() => {
  if (!diffDetail.value) return { left: [], right: [] }
  return buildSideBySideDiff(diffDetail.value.oldSource || '', diffDetail.value.newSource || '')
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

function onLeftScroll() {
  if (!paneOld.value || !paneNew.value) return
  if (!isSyncingLeft) {
    isSyncingRight = true
    paneNew.value.scrollTop = paneOld.value.scrollTop
    paneNew.value.scrollLeft = paneOld.value.scrollLeft
  }
  isSyncingLeft = false
}

function onRightScroll() {
  if (!paneOld.value || !paneNew.value) return
  if (!isSyncingRight) {
    isSyncingLeft = true
    paneOld.value.scrollTop = paneNew.value.scrollTop
    paneOld.value.scrollLeft = paneNew.value.scrollLeft
  }
  isSyncingRight = false
}

function bindScrollSync() {
  if (!paneOld.value || !paneNew.value) return
  paneOld.value.removeEventListener('scroll', onLeftScroll)
  paneNew.value.removeEventListener('scroll', onRightScroll)
  paneOld.value.addEventListener('scroll', onLeftScroll)
  paneNew.value.addEventListener('scroll', onRightScroll)
}

function unbindScrollSync() {
  if (!paneOld.value || !paneNew.value) return
  paneOld.value.removeEventListener('scroll', onLeftScroll)
  paneNew.value.removeEventListener('scroll', onRightScroll)
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

watch(diffDetail, async () => {
  await nextTick()
  bindScrollSync()
})

onUnmounted(() => {
  unbindScrollSync()
})
</script>

<template>
  <div class="relative max-w-[1600px] mx-auto space-y-6 pb-12">
    <div class="glass-panel rounded-2xl p-5 border border-white/10 shrink-0 flex flex-col lg:flex-row justify-between lg:items-center gap-4 bg-[#0f172a]/80 shadow-xl relative overflow-hidden">
      <div class="absolute inset-y-0 right-0 w-1/3 bg-gradient-to-l from-cyan-500/10 to-transparent pointer-events-none"></div>
      <div class="flex items-center gap-6 z-10">
        <button class="group flex items-center justify-center w-10 h-10 rounded-xl bg-white/5 hover:bg-white/10 text-slate-400 hover:text-white transition-all border border-white/5" @click="$router.push('/user/notes')">
          <ArrowLeft class="w-5 h-5 group-hover:-translate-x-1 transition-transform" />
        </button>
        <div class="flex flex-col">
          <h2 class="text-lg font-bold text-white flex items-center gap-2">
            笔记变更比对
            <span class="px-2 py-0.5 rounded text-[10px] uppercase font-black tracking-wider bg-amber-500/10 text-amber-400 border border-amber-500/20">Diff Pending</span>
          </h2>
          <div class="flex items-center space-x-4 mt-1.5 text-xs">
            <span class="text-slate-500">文件大小变更:</span>
            <div class="flex items-center font-mono font-bold">
              <span class="text-slate-400 line-through">{{ formatBytes(diffDetail?.diff?.oldFileSize ?? 0) }}</span>
              <FileDiff class="w-3 h-3 mx-1.5 text-slate-500" />
              <span class="text-emerald-400">{{ formatBytes(diffDetail?.diff?.newFileSize ?? 0) }}</span>
              <span class="ml-2 text-emerald-400 bg-emerald-500/10 px-1.5 rounded">+{{ formatBytes(diffDetail?.diff?.diffFileSize ?? 0) }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="flex items-center gap-3 z-10">
        <button class="px-5 py-2.5 rounded-xl text-sm font-bold text-rose-400 hover:text-white bg-rose-500/10 hover:bg-rose-500 transition-all border border-rose-500/20 flex items-center gap-2" :disabled="confirming || !diffDetail" @click="handleConfirmChange(false)">
          <XCircle class="w-4 h-4" />放弃变更
        </button>
        <button class="group relative px-6 py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-bold rounded-xl shadow-[0_0_15px_rgba(59,130,246,0.4)] transition-all overflow-hidden flex items-center gap-2" :disabled="confirming || !diffDetail" @click="handleConfirmChange(true)">
          <div class="absolute inset-0 bg-[linear-gradient(to_right,transparent,rgba(255,255,255,0.2),transparent)] -translate-x-[150%] group-hover:translate-x-[150%] transition-transform duration-700 ease-out"></div>
          <CheckCircle2 class="w-4 h-4" />
          <span>{{ confirming ? '处理中...' : '确认覆盖并重载映射' }}</span>
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

      <div class="glass-panel rounded-2xl overflow-hidden flex flex-col bg-[#020617] border border-white/10 shadow-2xl relative">
        <div class="h-10 bg-[#0f172a] border-b border-white/5 flex items-center shrink-0">
          <div class="flex-1 px-4 text-xs font-bold text-slate-400 flex items-center justify-between border-r border-white/5">
            <div class="flex items-center gap-2">
              <FileText class="w-3.5 h-3.5 text-rose-400" />
              <span>原始版本 (线上运行中)</span>
            </div>
            <span class="px-1.5 py-0.5 rounded bg-black/40 text-slate-500 font-mono">v1.0.0</span>
          </div>
          <div class="flex-1 px-4 text-xs font-bold text-slate-400 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <FileText class="w-3.5 h-3.5 text-emerald-400" />
              <span>修改后版本 (本次上传)</span>
            </div>
            <span class="px-1.5 py-0.5 rounded bg-blue-500/20 text-blue-400 font-mono">Draft</span>
          </div>
        </div>

        <div class="diff-container editor-font text-[13px] text-slate-300">
          <div ref="paneOld" class="diff-pane custom-scrollbar">
            <div class="code-lines">
              <div v-for="(line, idx) in diffLines.left" :key="`l-${idx}`" class="code-line" :class="{ 'diff-removed': line.type === 'removed', 'empty-line': line.type === 'empty' }">
                <span class="diff-marker">{{ line.type === 'removed' ? '-' : '' }}</span>
                <span class="code-text">{{ line.text }}</span>
              </div>
            </div>
          </div>

          <div class="diff-divider hidden md:block"></div>

          <div ref="paneNew" class="diff-pane custom-scrollbar">
            <div class="code-lines">
              <div v-for="(line, idx) in diffLines.right" :key="`r-${idx}`" class="code-line" :class="{ 'diff-added': line.type === 'added', 'empty-line': line.type === 'empty' }">
                <span class="diff-marker">{{ line.type === 'added' ? '+' : '' }}</span>
                <span class="code-text">{{ line.text }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
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

.editor-font {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.diff-container { display: flex; width: 100%; height: 100%; }
.diff-pane { flex: 1; overflow-y: auto; overflow-x: auto; background: #0b0f19; position: relative; }
.diff-divider { width: 4px; background: rgba(255,255,255,0.02); cursor: col-resize; z-index: 10; border-left: 1px solid rgba(255,255,255,0.05); border-right: 1px solid rgba(255,255,255,0.05); }

.code-lines { counter-reset: line; padding-bottom: 2rem; min-width: max-content; }
.code-line { display: flex; line-height: 1.6; min-height: 1.6rem; }
.code-line::before {
  counter-increment: line;
  content: counter(line);
  width: 3rem;
  flex-shrink: 0;
  text-align: right;
  padding-right: 1rem;
  color: #475569;
  user-select: none;
  border-right: 1px solid rgba(255, 255, 255, 0.05);
  margin-right: 1rem;
  background: rgba(0,0,0,0.2);
}

.code-line.empty-line { background: transparent; }
.code-line.empty-line::before { content: ''; border-right-color: transparent; background: transparent; }

.diff-marker {
  width: 0.75rem;
  margin-right: 0.5rem;
  text-align: center;
  user-select: none;
  opacity: 0.4;
}

.code-text { white-space: pre; }

.code-line.diff-removed { background-color: rgba(244, 63, 94, 0.1); color: #fecdd3; }
.code-line.diff-removed::before { border-right: 1px solid #f43f5e; color: #f43f5e; background: rgba(244, 63, 94, 0.05); }
.code-line.diff-removed .diff-marker { color: #f43f5e; font-weight: bold; opacity: 1; }

.code-line.diff-added { background-color: rgba(16, 185, 129, 0.1); color: #d1fae5; }
.code-line.diff-added::before { border-right: 1px solid #10b981; color: #10b981; background: rgba(16, 185, 129, 0.05); }
.code-line.diff-added .diff-marker { color: #10b981; font-weight: bold; opacity: 1; }

.custom-scrollbar::-webkit-scrollbar { width: 6px; height: 6px; }
.custom-scrollbar::-webkit-scrollbar-track { background: rgba(0,0,0,0.2); border-radius: 4px; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.1); border-radius: 4px; }
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: rgba(59, 130, 246, 0.5); }
</style>
