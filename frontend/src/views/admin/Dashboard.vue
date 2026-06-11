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
      textStyle: { color: '#777771', fontSize: 11, fontFamily: 'Inter, sans-serif' }
    },
    xAxis: {
      type: 'category',
      show: true,
      axisLine: { lineStyle: { color: '#dededb' } },
      axisTick: { show: false },
      axisLabel: { show: false },
    },
    yAxis: {
      type: 'value',
      max: yMax,
      splitLine: { lineStyle: { color: '#eeeeec' } },
      axisLabel: {
        color: '#777771',
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
      lineStyle: { color: '#111111', width: 2 },
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
      lineStyle: { color: '#15803d', width: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(21,128,61,0.18)' },
          { offset: 1, color: 'rgba(21,128,61,0.02)' }
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
        lineStyle: { color: '#2563eb', width: 1.5 },
        data: [],
      },
      {
        name: '阻塞中',
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#b91c1c', width: 2.5 },
        data: [],
      },
      {
        name: '等待中',
        type: 'line',
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#777771', width: 1 },
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
  <div class="admin-dashboard">
    <section class="admin-hero">
      <p class="admin-eyebrow">
        <ShieldCheck class="h-3.5 w-3.5" />
        Admin console
      </p>
      <h2>欢迎回来，{{ authStore.user?.nickname || 'System Admin' }}</h2>
      <p>全站资源监控、内容审核与用户资产都集中在这里。页面保持安静，异常状态会自己浮上来。</p>
    </section>

    <section class="metric-grid" aria-label="管理概览">
      <article class="metric-card">
        <div class="metric-head">
          <span class="metric-icon"><Users class="h-5 w-5" /></span>
          <span class="metric-badge">+12</span>
        </div>
        <p class="metric-label">Total Users</p>
        <strong class="metric-value">4,281</strong>
      </article>

      <article class="metric-card">
        <div class="metric-head">
          <span class="metric-icon"><FileText class="h-5 w-5" /></span>
          <span class="metric-badge">+142</span>
        </div>
        <p class="metric-label">Global Notes</p>
        <strong class="metric-value">128.5 <span>K</span></strong>
      </article>

      <article class="metric-card is-danger">
        <div class="metric-head">
          <span class="metric-icon"><ClipboardCheck class="h-5 w-5" /></span>
          <span class="metric-badge metric-badge-danger">
            <ShieldAlert class="h-3 w-3" />
            Action Req
          </span>
        </div>
        <p class="metric-label">Pending Audits</p>
        <strong class="metric-value">24</strong>
      </article>

      <article class="metric-card">
        <div class="metric-head">
          <span class="metric-icon"><HardDrive class="h-5 w-5" /></span>
          <span class="metric-badge">68% Used</span>
        </div>
        <p class="metric-label">OSS Storage</p>
        <strong class="metric-value">6.8 <span>TB</span></strong>
        <div class="storage-bar" aria-hidden="true"><div></div></div>
      </article>
    </section>

    <section class="monitor-section">
      <div class="monitor-bar">
        <div class="monitor-title">
          <span :class="['status-dot', monitorEnabled ? 'is-running' : '']"></span>
          <div>
            <h3>实时服务器监控</h3>
            <p>{{ monitorEnabled ? 'WebSocket 已连接，正在采集运行指标' : '监控默认关闭，按需开启以减少页面负载' }}</p>
          </div>
          <span class="monitor-state">{{ monitorEnabled ? 'RUNNING' : 'IDLE' }}</span>
        </div>
        <button
          type="button"
          class="monitor-toggle"
          :class="monitorEnabled ? 'is-danger' : 'is-start'"
          :disabled="monitorLoading"
          @click="toggleMonitor"
        >
          <Play v-if="!monitorEnabled && !monitorLoading" class="h-3.5 w-3.5" />
          <Square v-else-if="monitorEnabled && !monitorLoading" class="h-3.5 w-3.5" />
          <Activity v-else class="h-3.5 w-3.5 animate-spin" />
          {{ monitorLoading ? 'CONNECTING...' : (monitorEnabled ? '关闭监控' : '开启监控') }}
        </button>
      </div>

      <div v-if="!monitorEnabled && !monitorLoading" class="monitor-empty">
        <span class="empty-icon"><Cpu class="h-7 w-7" /></span>
        <div>
          <p>监控已关闭</p>
          <span>点击「开启监控」建立 WebSocket 连接，实时采集 CPU、JVM 堆内存、线程状态等指标。</span>
        </div>
      </div>

      <div v-if="monitorLoading" class="monitor-empty">
        <span class="empty-icon"><Activity class="h-7 w-7 animate-spin" /></span>
        <div>
          <p>正在连接 WebSocket...</p>
          <span>连接成功后会显示最新服务器指标。</span>
        </div>
      </div>

      <template v-if="monitorEnabled">
        <div class="metric-grid monitor-grid">
          <article class="metric-card">
            <div class="metric-head">
              <span class="metric-icon"><Cpu class="h-5 w-5" /></span>
              <span class="status-dot is-running"></span>
            </div>
            <p class="metric-label">CPU 占用率</p>
            <strong class="metric-value">{{ cpuUsage.toFixed(1) }}<span>%</span></strong>
          </article>

          <article class="metric-card">
            <div class="metric-head">
              <span class="metric-icon"><MemoryStick class="h-5 w-5" /></span>
              <span class="status-dot is-running"></span>
            </div>
            <p class="metric-label">JVM 堆内存</p>
            <strong class="metric-value">{{ heapUsedMB }}<span> / {{ heapMaxMB }} MB</span></strong>
          </article>

          <article :class="['metric-card', blockedThreads > 100 ? 'is-danger' : '']">
            <div class="metric-head">
              <span class="metric-icon"><AlertTriangle class="h-5 w-5" /></span>
              <span :class="['status-dot', blockedThreads > 100 ? 'is-danger' : '']"></span>
            </div>
            <p class="metric-label">阻塞线程</p>
            <strong class="metric-value">{{ blockedThreads }}<span> 个</span></strong>
          </article>

          <article class="metric-card">
            <div class="metric-head">
              <span class="metric-icon"><Activity class="h-5 w-5" /></span>
              <span class="status-dot is-running"></span>
            </div>
            <p class="metric-label">业务吞吐量</p>
            <strong class="metric-value">{{ currentQps }}<span> QPS</span></strong>
          </article>
        </div>

        <div class="chart-grid">
          <article class="chart-card">
            <h3>CPU 使用率趋势</h3>
            <div ref="chartCpuRef" class="chart-host"></div>
          </article>
          <article class="chart-card">
            <h3>JVM Heap 内存 (GC 活动)</h3>
            <div ref="chartHeapRef" class="chart-host"></div>
          </article>
          <article class="chart-card">
            <h3>线程状态分布</h3>
            <div ref="chartThreadRef" class="chart-host"></div>
          </article>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.admin-dashboard {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  gap: 20px;
}

.admin-hero,
.metric-card,
.monitor-section,
.monitor-empty,
.chart-card {
  border: 1px solid var(--cn-border);
  border-radius: var(--cn-radius-lg);
  background: var(--cn-surface);
  box-shadow: var(--cn-shadow-xs);
}

.admin-hero {
  padding: 28px;
}

.admin-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px;
  color: var(--cn-text-muted);
  font-size: 11px;
  font-weight: 720;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.admin-hero h2 {
  margin: 0;
  color: var(--cn-text);
  font-size: clamp(24px, 3vw, 34px);
  font-weight: 760;
  letter-spacing: 0;
}

.admin-hero p:last-child {
  max-width: 720px;
  margin: 10px 0 0;
  color: var(--cn-text-soft);
  font-size: 14px;
  line-height: 1.75;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  min-height: 166px;
  padding: 22px;
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

.metric-card.is-danger {
  border-color: rgba(185, 28, 28, 0.28);
  background: color-mix(in srgb, var(--cn-surface) 94%, #b91c1c 6%);
}

.metric-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.metric-icon,
.empty-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--cn-border);
  border-radius: var(--cn-radius-sm);
  background: var(--cn-surface-muted);
  color: var(--cn-text);
}

.metric-icon {
  width: 36px;
  height: 36px;
}

.metric-badge,
.monitor-state {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--cn-border);
  border-radius: 999px;
  padding: 4px 8px;
  background: var(--cn-bg-subtle);
  color: var(--cn-text-soft);
  font-size: 11px;
  font-weight: 680;
}

.metric-badge-danger {
  border-color: rgba(185, 28, 28, 0.24);
  color: var(--cn-danger);
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
  font-size: 32px;
  font-weight: 760;
  line-height: 1.12;
}

.metric-value span {
  color: var(--cn-text-muted);
  font-size: 17px;
  font-weight: 560;
}

.storage-bar {
  height: 6px;
  margin-top: 18px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--cn-surface-muted);
}

.storage-bar > div {
  width: 68%;
  height: 100%;
  border-radius: inherit;
  background: var(--cn-accent);
}

.monitor-section {
  display: grid;
  gap: 16px;
  padding: 18px;
}

.monitor-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.monitor-title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.monitor-title h3 {
  margin: 0;
  color: var(--cn-text);
  font-size: 14px;
  font-weight: 760;
}

.monitor-title p {
  margin: 3px 0 0;
  color: var(--cn-text-muted);
  font-size: 12px;
}

.status-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--cn-border-strong);
}

.status-dot.is-running {
  background: var(--cn-success);
}

.status-dot.is-danger {
  background: var(--cn-danger);
}

.monitor-toggle {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid var(--cn-accent);
  border-radius: var(--cn-radius-sm);
  padding: 0 14px;
  background: var(--cn-accent);
  color: var(--cn-text-inverse);
  font-size: 12px;
  font-weight: 720;
  letter-spacing: 0.04em;
  transition:
    background-color var(--cn-fast) var(--cn-ease),
    border-color var(--cn-fast) var(--cn-ease),
    color var(--cn-fast) var(--cn-ease);
}

.monitor-toggle:hover {
  background: var(--cn-accent-hover);
}

.monitor-toggle.is-danger {
  border-color: rgba(185, 28, 28, 0.24);
  background: transparent;
  color: var(--cn-danger);
}

.monitor-toggle.is-danger:hover {
  background: rgba(185, 28, 28, 0.08);
}

.monitor-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  min-height: 180px;
  padding: 28px;
  text-align: left;
}

.empty-icon {
  width: 56px;
  height: 56px;
  color: var(--cn-text-muted);
}

.monitor-empty p {
  margin: 0 0 4px;
  color: var(--cn-text);
  font-size: 14px;
  font-weight: 720;
}

.monitor-empty span {
  display: block;
  max-width: 460px;
  color: var(--cn-text-muted);
  font-size: 12px;
  line-height: 1.7;
}

.monitor-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.chart-grid {
  display: grid;
  gap: 14px;
}

.chart-card {
  padding: 20px;
}

.chart-card h3 {
  margin: 0 0 10px;
  color: var(--cn-text-muted);
  font-size: 11px;
  font-weight: 720;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.chart-host {
  width: 100%;
  height: 320px;
}

@media (max-width: 1080px) {
  .metric-grid,
  .monitor-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .metric-grid,
  .monitor-grid {
    grid-template-columns: 1fr;
  }

  .monitor-bar,
  .monitor-empty {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .animate-pulse,
  .animate-spin {
    animation: none !important;
  }

  .metric-card {
    transition-duration: 0.01s;
  }

  .metric-card:hover {
    transform: none;
  }
}
</style>
