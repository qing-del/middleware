<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, type Ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import {
  ShieldCheck, Users, FileText, ClipboardCheck, HardDrive, ShieldAlert,
  Cpu, MemoryStick, AlertTriangle, Activity, Play, Square
} from 'lucide-vue-next'
import * as echarts from 'echarts'

const authStore = useAuthStore()

// --- Server monitoring state ---
const MAX_POINTS = 60

const cpuData = ref<number[]>([])
const heapData = ref<number[]>([])
const runnableData = ref<number[]>([])
const blockedData = ref<number[]>([])
const waitingData = ref<number[]>([])
const qpsData = ref<number[]>([])

const cpuUsage = ref(0)
const heapUsedMB = ref(0)
const heapMaxMB = ref(0)
const blockedThreads = ref(0)
const currentQps = ref(0)

const chartCpuRef = ref<HTMLDivElement | null>(null)
const chartHeapRef = ref<HTMLDivElement | null>(null)
const chartThreadRef = ref<HTMLDivElement | null>(null)

let chartCpuInst: echarts.ECharts | null = null
let chartHeapInst: echarts.ECharts | null = null
let chartThreadInst: echarts.ECharts | null = null
let ws: WebSocket | null = null

const monitorEnabled = ref(false)
const monitorLoading = ref(false)

function clearData() {
  cpuData.value = []
  heapData.value = []
  runnableData.value = []
  blockedData.value = []
  waitingData.value = []
  qpsData.value = []
  cpuUsage.value = 0
  heapUsedMB.value = 0
  heapMaxMB.value = 0
  blockedThreads.value = 0
  currentQps.value = 0
}

function pushData(arr: Ref<number[]>, val: number) {
  arr.value.push(val)
  if (arr.value.length > MAX_POINTS) arr.value.shift()
}

function makeDarkLineOpt(seriesData: object[], yMax?: number, yFormatter?: string) {
  return {
    backgroundColor: 'transparent',
    grid: { top: 40, right: 20, bottom: 30, left: 50 },
    legend: {
      top: 8,
      textStyle: { color: '#94a3b8', fontSize: 11, fontFamily: 'Inter, sans-serif' }
    },
    xAxis: {
      type: 'category',
      show: true,
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
      axisTick: { show: false },
      axisLabel: { show: false },
    },
    yAxis: {
      type: 'value',
      max: yMax,
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.06)' } },
      axisLabel: {
        color: '#64748b',
        fontSize: 10,
        fontFamily: 'Inter, sans-serif',
        formatter: yFormatter || '{value}'
      },
    },
    series: seriesData,
  } as echarts.EChartsOption
}

function initCharts() {
  if (chartCpuRef.value) {
    chartCpuInst = echarts.init(chartCpuRef.value)
    chartCpuInst.setOption(makeDarkLineOpt([{
      name: 'CPU',
      type: 'line',
      smooth: true,
      symbol: 'none',
      lineStyle: { color: '#22d3ee', width: 2 },
      data: [],
    }], 100, '{value}%'))
  }

  if (chartHeapRef.value) {
    chartHeapInst = echarts.init(chartHeapRef.value)
    chartHeapInst.setOption(makeDarkLineOpt([{
      name: '堆内存',
      type: 'line',
      smooth: true,
      symbol: 'none',
      lineStyle: { color: '#34d399', width: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(52,211,153,0.35)' },
          { offset: 1, color: 'rgba(52,211,153,0.02)' }
        ])
      },
      data: [],
    }], undefined, '{value} MB'))
  }

  if (chartThreadRef.value) {
    chartThreadInst = echarts.init(chartThreadRef.value)
    chartThreadInst.setOption(makeDarkLineOpt([
      {
        name: '可运行',
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#818cf8', width: 1.5 },
        data: [],
      },
      {
        name: '阻塞中',
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#f43f5e', width: 2.5 },
        data: [],
      },
      {
        name: '等待中',
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#94a3b8', width: 1 },
        data: [],
      },
    ]))
  }
}

function updateCharts() {
  if (chartCpuInst) {
    chartCpuInst.setOption({
      xAxis: { data: cpuData.value.map((_, i) => i) },
      series: [{ data: cpuData.value }],
    })
  }
  if (chartHeapInst) {
    chartHeapInst.setOption({
      xAxis: { data: heapData.value.map((_, i) => i) },
      series: [{ data: heapData.value }],
    })
  }
  if (chartThreadInst) {
    chartThreadInst.setOption({
      xAxis: { data: runnableData.value.map((_, i) => i) },
      series: [
        { data: runnableData.value },
        { data: blockedData.value },
        { data: waitingData.value },
      ],
    })
  }
}

function handleResize() {
  chartCpuInst?.resize()
  chartHeapInst?.resize()
  chartThreadInst?.resize()
}

async function startMonitor() {
  if (monitorLoading.value || monitorEnabled.value) return
  monitorLoading.value = true
  try {
    monitorEnabled.value = true
    await nextTick()
    initCharts()
    window.addEventListener('resize', handleResize)

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/ws/monitor`

    ws = new WebSocket(wsUrl)
    ws.onmessage = (event) => {
      try {
        const d = JSON.parse(event.data)
        cpuUsage.value = d.cpuUsage
        heapUsedMB.value = d.heapUsedMB
        heapMaxMB.value = d.heapMaxMB
        blockedThreads.value = d.blockedThreads
        currentQps.value = d.qps

        pushData(cpuData, Math.round(d.cpuUsage * 10) / 10)
        pushData(heapData, d.heapUsedMB)
        pushData(runnableData, d.runnableThreads)
        pushData(blockedData, d.blockedThreads)
        pushData(waitingData, d.waitingThreads)
        pushData(qpsData, d.qps)

        updateCharts()
      } catch { /* ignore malformed frames */ }
    }
    ws.onerror = () => { stopMonitor() }
    ws.onclose = () => { if (monitorEnabled.value) stopMonitor() }
  } catch {
    stopMonitor()
  } finally {
    monitorLoading.value = false
  }
}

function stopMonitor() {
  ws?.close()
  ws = null
  chartCpuInst?.dispose()
  chartHeapInst?.dispose()
  chartThreadInst?.dispose()
  chartCpuInst = null
  chartHeapInst = null
  chartThreadInst = null
  window.removeEventListener('resize', handleResize)
  clearData()
  monitorEnabled.value = false
}

function toggleMonitor() {
  if (monitorEnabled.value) {
    stopMonitor()
  } else {
    startMonitor()
  }
}

onMounted(() => {
  // 监控默认关闭，用户手动开启
})

onUnmounted(() => {
  if (monitorEnabled.value) stopMonitor()
})
</script>

<template>
  <div class="max-w-6xl mx-auto space-y-8">
    <!-- Ambient Glow Orbs -->
    <div class="fixed top-[-12%] right-[-6%] z-0 h-[520px] w-[520px] rounded-full bg-rose-500/10 blur-[160px] pointer-events-none"></div>
    <div class="fixed bottom-[-14%] left-[-6%] z-0 h-[460px] w-[460px] rounded-full bg-indigo-500/10 blur-[150px] pointer-events-none"></div>

    <!-- 管理员欢迎卡片 -->
    <div class="glass-panel rounded-3xl p-8 relative overflow-hidden group">
      <div class="absolute -top-24 -right-24 w-64 h-64 bg-rose-500/10 blur-[60px] rounded-full group-hover:bg-rose-500/20 transition-all duration-700 pointer-events-none"></div>

      <div class="relative z-10">
        <div class="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-white/5 border border-white/10 mb-4">
          <ShieldCheck class="text-rose-400 w-3 h-3" />
          <span class="text-[10px] text-rose-300 font-bold uppercase tracking-[0.2em]">Highest Privilege</span>
        </div>
        <h2 class="text-3xl font-black text-white tracking-tight mb-2">
          欢迎回来，<span class="text-transparent bg-clip-text bg-gradient-to-r from-rose-400 to-orange-400">{{ authStore.user?.nickname || 'System Admin' }}</span>
        </h2>
        <p class="text-slate-400 text-sm max-w-xl leading-relaxed">
          全站资源监控与审批通道已开启。您可以管理用户资产、批量审核笔记申请，并维护内容生态的健康运转。
        </p>
      </div>
    </div>

    <!-- Admin 专属数据大盘 -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-6">
      <!-- 统计卡片 1: 总用户 -->
      <div class="glass-panel rounded-2xl p-6 hover:bg-white/[0.04] transition-colors border-t border-white/10 border-l-0 border-r-0 border-b-0">
        <div class="flex justify-between items-start mb-4">
          <div class="p-2.5 bg-indigo-500/10 rounded-xl text-indigo-400">
            <Users class="w-5 h-5" />
          </div>
          <span class="text-xs font-bold text-emerald-400 bg-emerald-400/10 px-2 py-1 rounded-lg">+12</span>
        </div>
        <h3 class="text-slate-400 text-xs font-bold uppercase tracking-widest mb-1">Total Users</h3>
        <div class="text-3xl font-black text-white">4,281</div>
      </div>

      <!-- 统计卡片 2: 全站笔记 -->
      <div class="glass-panel rounded-2xl p-6 hover:bg-white/[0.04] transition-colors border-t border-white/10 border-l-0 border-r-0 border-b-0">
        <div class="flex justify-between items-start mb-4">
          <div class="p-2.5 bg-purple-500/10 rounded-xl text-purple-400">
            <FileText class="w-5 h-5" />
          </div>
          <span class="text-xs font-bold text-emerald-400 bg-emerald-400/10 px-2 py-1 rounded-lg">+142</span>
        </div>
        <h3 class="text-slate-400 text-xs font-bold uppercase tracking-widest mb-1">Global Notes</h3>
        <div class="text-3xl font-black text-white">128.5 <span class="text-lg text-slate-500">K</span></div>
      </div>

      <!-- 统计卡片 3: 待审核事项 -->
      <div class="glass-panel rounded-2xl p-6 bg-rose-500/5 hover:bg-rose-500/10 transition-colors border border-rose-500/20 shadow-[inset_0_1px_1px_rgba(255,255,255,0.05),0_0_20px_rgba(244,63,94,0.05)] relative overflow-hidden group">
        <div class="absolute -right-4 -bottom-4 opacity-10 group-hover:opacity-20 transition-opacity">
          <ShieldAlert class="w-32 h-32 text-rose-500" />
        </div>
        <div class="flex justify-between items-start mb-4 relative z-10">
          <div class="p-2.5 bg-rose-500/20 rounded-xl text-rose-400">
            <ClipboardCheck class="w-5 h-5" />
          </div>
          <span class="text-[10px] font-black text-rose-300 bg-rose-500/20 px-2 py-1 rounded-lg uppercase tracking-widest animate-pulse">Action Req</span>
        </div>
        <h3 class="text-rose-200/80 text-xs font-bold uppercase tracking-widest mb-1 relative z-10">Pending Audits</h3>
        <div class="text-3xl font-black text-rose-400 relative z-10">24</div>
      </div>

      <!-- 统计卡片 4: 系统存储 -->
      <div class="glass-panel rounded-2xl p-6 hover:bg-white/[0.04] transition-colors border-t border-white/10 border-l-0 border-r-0 border-b-0">
        <div class="flex justify-between items-start mb-4">
          <div class="p-2.5 bg-emerald-500/10 rounded-xl text-emerald-400">
            <HardDrive class="w-5 h-5" />
          </div>
          <span class="text-[10px] font-bold text-slate-400 bg-white/5 px-2 py-1 rounded-lg uppercase">68% Used</span>
        </div>
        <h3 class="text-slate-400 text-xs font-bold uppercase tracking-widest mb-1">OSS Storage</h3>
        <div class="text-3xl font-black text-white">6.8 <span class="text-lg text-slate-500">TB</span></div>
        <div class="w-full bg-black/40 h-1.5 rounded-full mt-4 overflow-hidden">
          <div class="bg-gradient-to-r from-emerald-500 to-teal-400 w-[68%] h-full rounded-full"></div>
        </div>
      </div>
    </div>

    <!-- ========== 服务器实时监控区域 ========== -->
    <div class="flex items-center justify-between mb-2 mt-8">
      <div class="flex items-center space-x-3">
        <div :class="['w-2 h-2 rounded-full shadow-[0_0_8px_rgba(52,211,153,0.8)]', monitorEnabled ? 'bg-emerald-400 animate-pulse' : 'bg-slate-600']"></div>
        <h3 class="text-sm font-bold text-slate-300 uppercase tracking-widest">实时服务器监控</h3>
        <span v-if="monitorEnabled" class="text-[9px] font-bold text-emerald-400 bg-emerald-400/10 px-1.5 py-0.5 rounded uppercase tracking-wider">RUNNING</span>
        <span v-else class="text-[9px] font-bold text-slate-500 bg-white/5 px-1.5 py-0.5 rounded uppercase tracking-wider">IDLE</span>
      </div>
      <button
        @click="toggleMonitor"
        :disabled="monitorLoading"
        :class="['relative flex items-center space-x-2 px-4 py-2 rounded-xl text-xs font-bold uppercase tracking-widest transition-all duration-300 overflow-hidden group/toggle', monitorEnabled ? 'bg-rose-500/10 border border-rose-500/30 text-rose-400 hover:bg-rose-500/20 hover:shadow-[0_0_15px_rgba(244,63,94,0.3)]' : 'bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/20 hover:shadow-[0_0_15px_rgba(52,211,153,0.3)] cursor-pointer']"
      >
        <div :class="['absolute inset-0 rounded-xl transition-opacity duration-300', monitorEnabled ? 'bg-gradient-to-r from-transparent via-rose-500/10 to-transparent -translate-x-full group-hover/toggle:translate-x-full' : 'bg-gradient-to-r from-transparent via-emerald-500/10 to-transparent -translate-x-full group-hover/toggle:translate-x-full']"></div>
        <Play v-if="!monitorEnabled && !monitorLoading" class="w-3.5 h-3.5 relative z-10" />
        <Square v-else-if="monitorEnabled && !monitorLoading" class="w-3.5 h-3.5 relative z-10" />
        <span class="relative z-10">{{ monitorLoading ? 'CONNECTING...' : (monitorEnabled ? '关闭监控' : '开启监控') }}</span>
      </button>
    </div>

    <!-- 关闭态：占位卡片 -->
    <div v-if="!monitorEnabled && !monitorLoading" class="glass-panel rounded-2xl p-10 flex flex-col items-center justify-center text-center space-y-4">
      <div class="w-16 h-16 rounded-2xl bg-white/[0.03] border border-white/5 flex items-center justify-center">
        <Cpu class="w-8 h-8 text-slate-600" />
      </div>
      <div>
        <p class="text-sm font-bold text-slate-400">监控已关闭</p>
        <p class="text-xs text-slate-600 mt-1 max-w-sm">点击上方「开启监控」按钮建立 WebSocket 连接，实时采集服务器 CPU、JVM 堆内存、线程状态等指标。</p>
      </div>
    </div>

    <!-- 加载态 -->
    <div v-if="monitorLoading" class="glass-panel rounded-2xl p-10 flex flex-col items-center justify-center text-center space-y-4">
      <div class="w-16 h-16 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center animate-pulse">
        <Activity class="w-8 h-8 text-emerald-400 animate-spin" />
      </div>
      <p class="text-sm font-bold text-emerald-400">正在连接 WebSocket...</p>
    </div>

    <!-- 开启态：完整监控大盘 -->
    <template v-if="monitorEnabled">
      <!-- 4 个实时监控卡片 -->
      <div class="grid grid-cols-1 md:grid-cols-4 gap-6">
        <!-- CPU -->
        <div class="glass-panel rounded-2xl p-6 hover:bg-white/[0.04] transition-colors relative overflow-hidden group">
          <div class="absolute -top-6 -right-6 w-20 h-20 bg-cyan-500/10 blur-2xl rounded-full group-hover:bg-cyan-500/20 transition-all duration-700 pointer-events-none"></div>
          <div class="flex justify-between items-start mb-4 relative z-10">
            <div class="p-2.5 bg-cyan-500/10 rounded-xl text-cyan-400">
              <Cpu class="w-5 h-5" />
            </div>
            <div class="w-2 h-2 rounded-full bg-cyan-400 shadow-[0_0_8px_rgba(34,211,238,0.8)] animate-pulse"></div>
          </div>
          <h3 class="text-slate-400 text-xs font-bold uppercase tracking-widest mb-1 relative z-10">CPU 占用率</h3>
          <div class="text-3xl font-black text-white relative z-10">{{ cpuUsage.toFixed(1) }}<span class="text-lg text-slate-500">%</span></div>
        </div>

        <!-- Heap Memory -->
        <div class="glass-panel rounded-2xl p-6 hover:bg-white/[0.04] transition-colors relative overflow-hidden group">
          <div class="absolute -top-6 -right-6 w-20 h-20 bg-emerald-500/10 blur-2xl rounded-full group-hover:bg-emerald-500/20 transition-all duration-700 pointer-events-none"></div>
          <div class="flex justify-between items-start mb-4 relative z-10">
            <div class="p-2.5 bg-emerald-500/10 rounded-xl text-emerald-400">
              <MemoryStick class="w-5 h-5" />
            </div>
            <div class="w-2 h-2 rounded-full bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.8)] animate-pulse"></div>
          </div>
          <h3 class="text-slate-400 text-xs font-bold uppercase tracking-widest mb-1 relative z-10">JVM 堆内存</h3>
          <div class="text-3xl font-black text-white relative z-10">{{ heapUsedMB }}<span class="text-lg text-slate-500"> / {{ heapMaxMB }} MB</span></div>
        </div>

        <!-- Blocked Threads -->
        <div :class="['glass-panel rounded-2xl p-6 transition-colors relative overflow-hidden group', blockedThreads > 100 ? 'border border-rose-500/30 bg-rose-500/5 shadow-[inset_0_1px_1px_rgba(255,255,255,0.05),0_0_20px_rgba(244,63,94,0.15)]' : 'hover:bg-white/[0.04]']">
          <div :class="['absolute -top-6 -right-6 w-20 h-20 blur-2xl rounded-full transition-all duration-700 pointer-events-none', blockedThreads > 100 ? 'bg-rose-500/30' : 'bg-rose-500/10 group-hover:bg-rose-500/20']"></div>
          <div class="flex justify-between items-start mb-4 relative z-10">
            <div :class="['p-2.5 rounded-xl', blockedThreads > 100 ? 'bg-rose-500/20 text-rose-400' : 'bg-rose-500/10 text-rose-400']">
              <AlertTriangle class="w-5 h-5" />
            </div>
            <div :class="['w-2 h-2 rounded-full', blockedThreads > 100 ? 'bg-rose-500 shadow-[0_0_12px_rgba(244,63,94,1)] animate-pulse' : 'bg-rose-400 shadow-[0_0_8px_rgba(251,113,133,0.6)]']"></div>
          </div>
          <h3 class="text-slate-400 text-xs font-bold uppercase tracking-widest mb-1 relative z-10">阻塞线程</h3>
          <div :class="['text-3xl font-black relative z-10 transition-colors', blockedThreads > 100 ? 'text-rose-400 animate-pulse' : 'text-white']">{{ blockedThreads }}<span :class="['text-lg', blockedThreads > 100 ? 'text-rose-400/70' : 'text-slate-500']"> 个</span></div>
        </div>

        <!-- QPS -->
        <div class="glass-panel rounded-2xl p-6 hover:bg-white/[0.04] transition-colors relative overflow-hidden group">
          <div class="absolute -top-6 -right-6 w-20 h-20 bg-amber-500/10 blur-2xl rounded-full group-hover:bg-amber-500/20 transition-all duration-700 pointer-events-none"></div>
          <div class="flex justify-between items-start mb-4 relative z-10">
            <div class="p-2.5 bg-amber-500/10 rounded-xl text-amber-400">
              <Activity class="w-5 h-5" />
            </div>
            <div class="w-2 h-2 rounded-full bg-amber-400 shadow-[0_0_8px_rgba(251,191,36,0.8)] animate-pulse"></div>
          </div>
          <h3 class="text-slate-400 text-xs font-bold uppercase tracking-widest mb-1 relative z-10">业务吞吐量</h3>
          <div class="text-3xl font-black text-white relative z-10">{{ currentQps }}<span class="text-lg text-slate-500"> QPS</span></div>
        </div>
      </div>

      <!-- 3 个 ECharts 实时折线图 -->
      <div class="grid grid-cols-1 gap-6">
        <div class="glass-panel rounded-2xl p-6">
          <h3 class="text-xs font-bold text-slate-400 uppercase tracking-widest mb-2">CPU 使用率趋势</h3>
          <div ref="chartCpuRef" class="w-full" style="height:320px"></div>
        </div>
        <div class="glass-panel rounded-2xl p-6">
          <h3 class="text-xs font-bold text-slate-400 uppercase tracking-widest mb-2">JVM Heap 内存 (GC 活动)</h3>
          <div ref="chartHeapRef" class="w-full" style="height:320px"></div>
        </div>
        <div class="glass-panel rounded-2xl p-6">
          <h3 class="text-xs font-bold text-slate-400 uppercase tracking-widest mb-2">线程状态分布</h3>
          <div ref="chartThreadRef" class="w-full" style="height:320px"></div>
        </div>
      </div>
    </template>
    <!-- ========== 监控区域结束 ========== -->

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

.stat-card {
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.3s ease, background-color 0.3s ease;
}
.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 40px -10px rgba(0, 0, 0, 0.3);
}

@media (prefers-reduced-motion: reduce) {
  .animate-pulse,
  .animate-spin { animation: none !important; }
  .stat-card { transition-duration: 0.01s !important; }
  .stat-card:hover { transform: none; }
}
</style>