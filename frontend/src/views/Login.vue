<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { useAuthStore } from '@/stores/auth'
import {
  Zap, ChevronRight, Link, FileDiff, User, Mail, Lock,
  ShieldCheck, Loader2, ArrowRight, CheckCircle2
} from 'lucide-vue-next'

const router = useRouter()
const authStore = useAuthStore()

// 视图状态：login, register, admin
const currentView = ref<'login' | 'register' | 'admin'>('login')
const showFeatures = ref(false)
const isAnimating = ref(false)

// 表单数据
const formData = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

// DOM refs
const cardRef = ref<HTMLElement | null>(null)
const formContentRef = ref<HTMLElement | null>(null)
const loaderIconRef = ref<HTMLElement | null>(null)
const submitIconRef = ref<HTMLElement | null>(null)
const submitTextRef = ref<HTMLElement | null>(null)
const toastTimeoutRef = ref<number | null>(null)

// Canvas 相关
const canvasRef = ref<HTMLCanvasElement | null>(null)
let animationFrameId: number
let particles: any[] = []
const mouse = { x: -1000, y: -1000, radius: 250 }

const viewConfig = computed(() => {
  switch (currentView.value) {
    case 'admin':
      return { title: 'PORTAL', bgClass: 'bg-rose-600', lineClass: 'h-1 w-8 bg-rose-500 rounded-full shadow-[0_0_10px_rgba(244,63,94,0.5)]' }
    case 'register':
      return { title: 'CREATE', bgClass: 'bg-purple-600', lineClass: 'h-1 w-12 bg-purple-500 rounded-full shadow-[0_0_10px_rgba(168,85,247,0.5)]' }
    default:
      return { title: 'ACCESS', bgClass: 'bg-indigo-600', lineClass: 'h-1 w-8 bg-indigo-500 rounded-full shadow-[0_0_10px_rgba(99,102,241,0.5)]' }
  }
})

// Canvas 星空类
class StarParticle {
  x: number
  y: number
  angle: number
  orbitRadius: number
  speed: number
  driftX: number
  driftY: number
  size: number
  color: string

  constructor(canvas: HTMLCanvasElement) {
    this.x = Math.random() * canvas.width
    this.y = Math.random() * canvas.height
    this.angle = Math.random() * Math.PI * 2
    this.orbitRadius = Math.random() * 2 + 0.5
    this.speed = (Math.random() * 0.01) + 0.005
    this.driftX = (Math.random() - 0.5) * 0.4
    this.driftY = (Math.random() - 0.5) * 0.4
    this.size = Math.random() * 2
    this.color = Math.random() > 0.6 ? '#6366f1' : (Math.random() > 0.5 ? '#8b5cf6' : '#cbd5e1')
  }

  update() {
    this.angle += this.speed
    let vx = Math.cos(this.angle) * this.orbitRadius * 0.1 + this.driftX
    let vy = Math.sin(this.angle) * this.orbitRadius * 0.1 + this.driftY

    const dx = mouse.x - this.x
    const dy = mouse.y - this.y
    const dist = Math.sqrt(dx * dx + dy * dy)

    if (dist < mouse.radius && dist > 0) {
      const force = (mouse.radius - dist) / mouse.radius
      const nx = dx / dist
      const ny = dy / dist
      const tx = -ny
      const ty = nx
      const orbitSpeed = 1.2 * force
      vx += tx * orbitSpeed
      vy += ty * orbitSpeed
      const targetRadius = 80
      if (dist > targetRadius) {
        vx += nx * force * 0.4
      } else {
        vx -= nx * force * 0.4
      }
    }

    this.x += vx
    this.y += vy

    const canvas = canvasRef.value!
    if (this.x < -50) this.x = canvas.width + 50
    if (this.x > canvas.width + 50) this.x = -50
    if (this.y < -50) this.y = canvas.height + 50
    if (this.y > canvas.height + 50) this.y = -50
  }

  draw(ctx: CanvasRenderingContext2D) {
    ctx.beginPath()
    ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2)
    ctx.fillStyle = this.color
    ctx.globalAlpha = Math.sin(this.angle) * 0.3 + 0.5
    ctx.fill()
  }
}

function resizeCanvas() {
  const canvas = canvasRef.value
  if (!canvas) return
  const oldWidth = canvas.width || window.innerWidth
  const oldHeight = canvas.height || window.innerHeight

  canvas.width = window.innerWidth
  canvas.height = window.innerHeight

  if (particles.length > 0) {
    const ratioX = canvas.width / oldWidth
    const ratioY = canvas.height / oldHeight
    particles.forEach(p => {
      p.x *= ratioX
      p.y *= ratioY
    })
  }
}

function initParticles() {
  const canvas = canvasRef.value
  if (!canvas) return
  particles = Array.from({ length: 150 }, () => new StarParticle(canvas))
}

function animateCanvas() {
  const canvas = canvasRef.value
  const ctx = canvas?.getContext('2d')
  if (!canvas || !ctx) return

  ctx.fillStyle = 'rgba(2, 6, 23, 0.15)'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.globalCompositeOperation = 'lighter'

  particles.forEach(p => {
    p.update()
    p.draw(ctx)
  })

  for (let i = 0; i < particles.length; i++) {
    for (let j = i + 1; j < particles.length; j++) {
      const dx = particles[i].x - particles[j].x
      const dy = particles[i].y - particles[j].y
      const dist = Math.sqrt(dx * dx + dy * dy)
      if (dist < 100) {
        ctx.beginPath()
        ctx.strokeStyle = `rgba(99, 102, 241, ${0.1 * (1 - dist / 100)})`
        ctx.lineWidth = 0.5
        ctx.moveTo(particles[i].x, particles[i].y)
        ctx.lineTo(particles[j].x, particles[j].y)
        ctx.stroke()
      }
    }
  }
  ctx.globalCompositeOperation = 'source-over'
  animationFrameId = requestAnimationFrame(animateCanvas)
}

function setView(newView: 'login' | 'register' | 'admin') {
  if (currentView.value === newView || isAnimating.value) return
  isAnimating.value = true

  const card = cardRef.value
  if (!card) return

  const startHeight = card.offsetHeight
  const startWidth = card.offsetWidth

  card.style.height = startHeight + 'px'
  card.style.maxWidth = startWidth + 'px'

  if (formContentRef.value) {
    formContentRef.value.style.opacity = '0'
    formContentRef.value.style.transform = 'scale(0.96) translateY(10px)'
  }

  setTimeout(() => {
    currentView.value = newView

    card.style.transition = 'none'
    const fieldEmail = document.getElementById('field-email')
    const fieldConfirm = document.getElementById('field-confirm')
    if (fieldEmail) fieldEmail.style.transition = 'none'
    if (fieldConfirm) fieldConfirm.style.transition = 'none'

    card.classList.remove('max-w-[380px]', 'max-w-[420px]', 'max-w-[460px]')
    card.style.maxWidth = ''

    if (newView === 'admin') {
      card.classList.add('max-w-[380px]')
    } else if (newView === 'register') {
      card.classList.add('max-w-[460px]')
    } else {
      card.classList.add('max-w-[420px]')
    }

    card.style.height = 'auto'
    const targetHeight = card.offsetHeight
    const targetWidth = card.offsetWidth

    card.style.height = startHeight + 'px'
    card.style.maxWidth = startWidth + 'px'
    void card.offsetHeight

    card.style.transition = ''
    if (fieldEmail) fieldEmail.style.transition = ''
    if (fieldConfirm) fieldConfirm.style.transition = ''

    card.style.height = targetHeight + 'px'
    card.style.maxWidth = targetWidth + 'px'

    if (formContentRef.value) {
      formContentRef.value.style.opacity = '1'
      formContentRef.value.style.transform = 'scale(1) translateY(0)'
    }

    setTimeout(() => {
      if (card.style.height === targetHeight + 'px') {
        card.style.height = 'auto'
      }
      card.style.maxWidth = ''
      isAnimating.value = false
    }, 500)
  }, 300)
}

function toggleFeatures() {
  showFeatures.value = !showFeatures.value
  const panel = document.getElementById('features-panel')
  const iconContainer = document.getElementById('toggle-icon-container')
  if (!panel || !iconContainer) return

  if (showFeatures.value) {
    panel.classList.remove('feature-panel-hide')
    panel.classList.add('feature-panel-show')
    iconContainer.classList.add('bg-indigo-500', 'border-indigo-500', 'rotate-90', 'text-white', 'shadow-[0_0_15px_rgba(99,102,241,0.5)]')
  } else {
    panel.classList.remove('feature-panel-show')
    panel.classList.add('feature-panel-hide')
    iconContainer.classList.remove('bg-indigo-500', 'border-indigo-500', 'rotate-90', 'text-white', 'shadow-[0_0_15px_rgba(99,102,241,0.5)]')
  }
}

function showToast(msg: string, type: 'success' | 'error' = 'success') {
  const toast = document.getElementById('toast-container')
  const toastMsg = document.getElementById('toast-msg')
  if (!toast || !toastMsg) return

  toastMsg.textContent = msg
  toast.classList.remove('translate-y-20', 'opacity-0', 'pointer-events-none')

  if (toastTimeoutRef.value) {
    clearTimeout(toastTimeoutRef.value)
  }
  toastTimeoutRef.value = window.setTimeout(() => {
    toast.classList.add('translate-y-20', 'opacity-0', 'pointer-events-none')
  }, 3000)
}

async function handleSubmit() {
  if (isAnimating.value) return

  const btn = document.getElementById('submit-btn') as HTMLButtonElement
  if (!btn) return

  btn.disabled = true
  if (submitTextRef.value) submitTextRef.value.classList.add('opacity-0')
  if (submitIconRef.value) submitIconRef.value.classList.add('hidden')
  if (loaderIconRef.value) loaderIconRef.value.classList.remove('hidden')

  try {
    if (currentView.value === 'admin') {
      await authStore.adminLogin({ username: formData.username, password: formData.password })
    } else {
      await authStore.login({ username: formData.username, password: formData.password })
    }

    showToast('安全认证通过...', 'success')

    setTimeout(() => {
      router.push(currentView.value === 'admin' ? '/admin' : '/user')
    }, 1000)
  } catch (error: any) {
    showToast(error.message || '认证失败', 'error')
  } finally {
    btn.disabled = false
    if (submitTextRef.value) submitTextRef.value.classList.remove('opacity-0')
    if (submitIconRef.value) submitIconRef.value.classList.remove('hidden')
    if (loaderIconRef.value) loaderIconRef.value.classList.add('hidden')
  }
}

onMounted(() => {
  resizeCanvas()
  initParticles()
  animateCanvas()

  window.addEventListener('resize', resizeCanvas)
  window.addEventListener('mousemove', (e) => {
    mouse.x = e.clientX
    mouse.y = e.clientY
  })
  document.body.addEventListener('mouseleave', () => {
    mouse.x = -1000
    mouse.y = -1000
  })
})

onUnmounted(() => {
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId)
  }
  if (toastTimeoutRef.value) {
    clearTimeout(toastTimeoutRef.value)
  }
  window.removeEventListener('resize', resizeCanvas)
})
</script>

<template>
  <div class="min-h-screen w-full flex items-center justify-center overflow-hidden selection:bg-indigo-500/30 tracking-tight">

    <!-- Canvas 背景 -->
    <canvas ref="canvasRef" id="bg-canvas" class="fixed inset-0 z-0 bg-[#020617]"></canvas>

    <!-- 环境光晕 -->
    <div class="fixed top-[-20%] right-[-10%] w-[600px] h-[600px] bg-indigo-600/10 blur-[150px] rounded-full pointer-events-none"></div>
    <div class="fixed bottom-[-10%] left-[-10%] w-[400px] h-[400px] bg-purple-600/5 blur-[120px] rounded-full pointer-events-none"></div>

    <div class="container max-w-6xl w-full grid grid-cols-1 lg:grid-cols-12 gap-12 relative z-10 px-6 py-12">

      <!-- 左侧区块 -->
      <div class="lg:col-span-7 flex flex-col justify-center space-y-10">
        <div class="space-y-6 animate-[fade-in_1s_ease-out]">
          <div class="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-white/5 border border-white/10 backdrop-blur-md shadow-[inset_0_1px_1px_rgba(255,255,255,0.1)]">
            <Zap class="text-indigo-400 fill-indigo-400 w-3.5 h-3.5" />
            <span class="text-[10px] text-indigo-300 font-bold uppercase tracking-[0.2em]">Next-Gen Content Hub</span>
          </div>

          <h1 class="text-7xl md:text-[5.5rem] tracking-tighter leading-[0.85] text-white">
            <span class="font-light tracking-[0.05em] text-slate-300">CORE</span><br />
            <span class="font-black text-transparent bg-clip-text bg-[linear-gradient(110deg,#818cf8,45%,#e879f9,55%,#818cf8)] animate-shine drop-shadow-[0_0_25px_rgba(99,102,241,0.3)]">NODE</span>
          </h1>

          <p class="text-xl text-slate-400 font-medium max-w-md leading-relaxed border-l-2 border-indigo-500/50 pl-6">
            构建您的数字化资产，<br />
            沉浸式 Markdown 编辑与智能渲染。
          </p>
        </div>

        <div class="flex flex-col space-y-6">
          <!-- 展开特性按钮 -->
          <button id="toggle-features-btn" @click="toggleFeatures" class="group flex items-center space-x-3 text-xs text-slate-500 hover:text-white transition-all uppercase tracking-widest font-bold w-max">
            <div id="toggle-icon-container" class="flex items-center justify-center w-8 h-8 rounded-full border border-slate-800 transition-all duration-500 group-hover:border-indigo-500">
              <ChevronRight class="w-4 h-4" />
            </div>
            <span>查看架构核心特性</span>
          </button>

          <!-- 特性面板 -->
          <div id="features-panel" class="grid grid-cols-1 md:grid-cols-2 gap-3 transition-all duration-700 ease-in-out feature-panel-hide">
            <div class="p-4 rounded-2xl bg-white/[0.03] border border-white/[0.05] backdrop-blur-xl group hover:bg-white/[0.06] transition-all shadow-[inset_0_1px_1px_rgba(255,255,255,0.05)]">
              <div class="flex items-center space-x-3 mb-2">
                <div class="text-indigo-400 p-1.5 bg-indigo-500/10 rounded-lg">
                  <Link class="w-4 h-4" />
                </div>
                <span class="text-white text-sm font-bold">支持 Obsidian 的双链机制</span>
              </div>
              <p class="text-slate-500 text-xs leading-relaxed">原生支持反向链接与网状引用，打破信息孤岛。</p>
            </div>

            <div class="p-4 rounded-2xl bg-white/[0.03] border border-white/[0.05] backdrop-blur-xl group hover:bg-white/[0.06] transition-all shadow-[inset_0_1px_1px_rgba(255,255,255,0.05)]">
              <div class="flex items-center space-x-3 mb-2">
                <div class="text-indigo-400 p-1.5 bg-indigo-500/10 rounded-lg">
                  <FileDiff class="w-4 h-4" />
                </div>
                <span class="text-white text-sm font-bold">智能 Diff 引擎</span>
              </div>
              <p class="text-slate-500 text-xs leading-relaxed">变更追踪、实时对比与原子级修改确认。</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：液态玻璃伸缩卡片 -->
      <div class="lg:col-span-5 flex items-center justify-center relative">
        <div id="glass-card" ref="cardRef" class="w-full max-w-[420px] p-10 rounded-[3rem] bg-white/[0.02] border border-white/[0.08] backdrop-blur-[50px] shadow-[0_40px_100px_-20px_rgba(0,0,0,0.8),inset_0_1px_1px_rgba(255,255,255,0.2),inset_0_-1px_1px_rgba(0,0,0,0.4)] relative overflow-hidden group/card glass-card-morph">

          <!-- 流动光晕 -->
          <div class="absolute -top-16 -left-16 w-48 h-48 bg-indigo-500/20 blur-[50px] rounded-full mix-blend-screen animate-blob pointer-events-none opacity-50 group-hover/card:opacity-100 transition-opacity duration-700"></div>
          <div class="absolute -bottom-16 -right-16 w-48 h-48 bg-purple-500/20 blur-[50px] rounded-full mix-blend-screen animate-blob animation-delay-2000 pointer-events-none opacity-50 group-hover/card:opacity-100 transition-opacity duration-700"></div>

          <!-- 内部表单内容 -->
          <div id="form-content" ref="formContentRef" class="relative z-10 form-transition">
            <div class="mb-10 text-center">
              <h2 id="form-title" class="text-3xl font-black text-white tracking-tight">{{ viewConfig.title }}</h2>
              <div class="flex justify-center mt-3">
                <div id="form-title-line" :class="viewConfig.lineClass"></div>
              </div>
            </div>

            <form id="auth-form" @submit.prevent="handleSubmit" class="flex flex-col">
              <!-- 用户名 -->
              <div class="relative group transition-all duration-300">
                <div class="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-500 group-focus-within:text-indigo-400 transition-colors">
                  <User class="w-[18px] h-[18px]" />
                </div>
                <input type="text" v-model="formData.username" name="username" placeholder="用户名" required class="w-full bg-black/20 border border-white/[0.05] shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] rounded-2xl py-4 pl-12 pr-4 outline-none focus:bg-black/40 focus:border-indigo-500/50 focus:ring-4 focus:ring-indigo-500/10 transition-all text-sm text-white placeholder:text-slate-600" />
              </div>

              <!-- 邮箱 (Grid 平滑折叠) -->
              <div id="field-email" :class="['field-wrapper', { 'is-expanded': currentView === 'register' }]">
                <div class="field-inner">
                  <div class="relative group">
                    <div class="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-500 group-focus-within:text-indigo-400 transition-colors">
                      <Mail class="w-[18px] h-[18px]" />
                    </div>
                    <input type="email" v-model="formData.email" name="email" placeholder="邮箱地址" :required="currentView === 'register'" class="w-full bg-black/20 border border-white/[0.05] shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] rounded-2xl py-4 pl-12 pr-4 outline-none focus:bg-black/40 focus:border-indigo-500/50 focus:ring-4 focus:ring-indigo-500/10 transition-all text-sm text-white placeholder:text-slate-600" />
                  </div>
                </div>
              </div>

              <!-- 密码 -->
              <div class="relative group transition-all duration-300 mt-4">
                <div class="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-500 group-focus-within:text-indigo-400 transition-colors">
                  <Lock class="w-[18px] h-[18px]" />
                </div>
                <input type="password" v-model="formData.password" name="password" placeholder="密码" required class="w-full bg-black/20 border border-white/[0.05] shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] rounded-2xl py-4 pl-12 pr-4 outline-none focus:bg-black/40 focus:border-indigo-500/50 focus:ring-4 focus:ring-indigo-500/10 transition-all text-sm text-white placeholder:text-slate-600" />
              </div>

              <!-- 确认密码 (Grid 平滑折叠) -->
              <div id="field-confirm" :class="['field-wrapper', { 'is-expanded': currentView === 'register' }]">
                <div class="field-inner">
                  <div class="relative group">
                    <div class="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-500 group-focus-within:text-indigo-400 transition-colors">
                      <ShieldCheck class="w-[18px] h-[18px]" />
                    </div>
                    <input type="password" v-model="formData.confirmPassword" name="confirmPassword" placeholder="确认密码" :required="currentView === 'register'" class="w-full bg-black/20 border border-white/[0.05] shadow-[inset_0_2px_4px_rgba(0,0,0,0.2)] rounded-2xl py-4 pl-12 pr-4 outline-none focus:bg-black/40 focus:border-indigo-500/50 focus:ring-4 focus:ring-indigo-500/10 transition-all text-sm text-white placeholder:text-slate-600" />
                  </div>
                </div>
              </div>

              <!-- 提交按钮 -->
              <button type="submit" id="submit-btn" class="group/btn relative w-full h-16 mt-8 overflow-hidden rounded-[1.5rem] transition-all hover:scale-[1.02] active:scale-[0.98] disabled:opacity-50 shadow-[0_10px_20px_-10px_rgba(99,102,241,0.6)]">
                <div :class="['absolute inset-0 transition-colors duration-500', currentView === 'admin' ? 'bg-rose-600 group-hover/btn:bg-rose-500' : currentView === 'register' ? 'bg-purple-600 group-hover/btn:bg-purple-500' : 'bg-indigo-600 group-hover/btn:bg-indigo-500']"></div>

                <!-- 高光反射 -->
                <div class="absolute inset-x-0 top-0 h-1/2 bg-gradient-to-b from-white/30 to-transparent opacity-60"></div>

                <!-- 扫光动画 -->
                <div class="absolute -inset-16 bg-[linear-gradient(to_right,transparent,rgba(255,255,255,0.4),transparent)] -rotate-45 translate-x-[-150%] group-hover/btn:translate-x-[150%] transition-transform duration-1000 ease-out"></div>

                <div class="relative flex items-center justify-center space-x-3 text-white font-black uppercase tracking-[0.2em] text-xs">
                  <Loader2 ref="loaderIconRef" id="loader-icon" class="w-5 h-5 animate-spin hidden" />
                  <span ref="submitTextRef" id="submit-text">{{ currentView === 'admin' ? '管理员核验' : currentView === 'register' ? '注册并加入' : '身份认证' }}</span>
                  <ArrowRight ref="submitIconRef" id="submit-icon" class="w-[18px] h-[18px] group-hover/btn:translate-x-1 transition-transform" />
                </div>
              </button>
            </form>

            <!-- 动态切换链接 -->
            <div class="mt-10 flex flex-col items-center space-y-6">
              <div class="flex items-center space-x-3 w-full opacity-20">
                <div class="h-px flex-1 bg-white"></div>
                <span class="text-[10px] text-white">SYSTEM</span>
                <div class="h-px flex-1 bg-white"></div>
              </div>

              <div id="switch-links" class="flex flex-col items-center space-y-4">
                <template v-if="currentView === 'admin'">
                  <button @click="setView('login')" type="button" class="group flex items-center space-x-2 text-xs text-rose-400 font-bold hover:text-rose-300 transition-all uppercase tracking-widest">
                    <div class="w-1.5 h-1.5 rounded-full bg-rose-500 group-hover:animate-ping"></div>
                    <span>退出管理入口</span>
                  </button>
                </template>
                <template v-else>
                  <button @click="setView('register')" type="button" class="text-xs text-indigo-400 font-bold hover:text-indigo-300 transition-all uppercase tracking-wider underline underline-offset-8 decoration-indigo-500/20 hover:decoration-indigo-500">
                    创建账户
                  </button>
                  <button @click="setView('admin')" type="button" class="text-[10px] text-slate-600 hover:text-slate-300 transition-colors uppercase tracking-[0.2em]">
                    管理控制台授权
                  </button>
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部装饰 -->
    <div class="fixed bottom-10 w-full px-12 flex justify-between items-end select-none pointer-events-none">
      <div class="flex flex-col">
        <span class="text-[9px] text-slate-700 uppercase tracking-[0.3em] font-black italic">CoreNode Hybrid Infrastructure</span>
        <span class="text-[8px] text-slate-800 uppercase tracking-[0.5em] mt-1">Distributed Sync Protocol Active</span>
      </div>
      <div class="flex items-center space-x-6">
        <div class="flex flex-col items-end">
          <span class="text-[9px] text-slate-700 font-bold tracking-widest uppercase">System Core v0.0.1</span>
          <span class="text-[8px] text-emerald-900 uppercase font-black tracking-tighter">Status: Optimal</span>
        </div>
        <div class="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-pulse shadow-[0_0_10px_rgba(99,102,241,1)]"></div>
      </div>
    </div>

    <!-- 消息通知 (Toast) -->
    <div id="toast-container" class="fixed bottom-12 right-12 px-8 py-5 rounded-[2.5rem] backdrop-blur-3xl shadow-[0_20px_50px_-10px_rgba(0,0,0,0.5),inset_0_1px_1px_rgba(255,255,255,0.1)] border-l-4 transition-all duration-500 z-50 transform translate-y-20 opacity-0 pointer-events-none bg-emerald-500/10 border-emerald-500/50 text-emerald-400">
      <div class="flex items-center space-x-4">
        <div id="toast-icon-wrapper" class="p-2 rounded-xl bg-emerald-500/20 shadow-[inset_0_1px_1px_rgba(255,255,255,0.2)]">
          <CheckCircle2 id="toast-icon" class="w-5 h-5" />
        </div>
        <div class="flex flex-col">
          <span id="toast-title" class="text-[9px] font-black uppercase tracking-[0.2em] opacity-40 leading-none mb-1">Authorization</span>
          <span id="toast-msg" class="text-sm font-bold leading-none">认证成功</span>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
/* 动画定义 */
@keyframes shine {
  to { background-position: 200% center; }
}
.animate-shine {
  background-size: 200% auto;
  animation: shine 4s linear infinite;
}

@keyframes blob {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(20px, -30px) scale(1.1); }
  66% { transform: translate(-20px, 20px) scale(0.9); }
}
.animate-blob {
  animation: blob 8s infinite cubic-bezier(0.4, 0, 0.2, 1);
}
.animation-delay-2000 {
  animation-delay: 2s;
}

/* 表单内容淡入淡出过渡 */
.form-transition {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 卡片整体的高宽伸缩过渡 */
.glass-card-morph {
  transition: max-width 0.5s cubic-bezier(0.4, 0, 0.2, 1),
              height 0.5s cubic-bezier(0.4, 0, 0.2, 1),
              box-shadow 0.5s ease;
}

/* 内部 Grid 折叠动画 */
.field-wrapper {
  display: grid;
  grid-template-rows: 0fr;
  transition: all 0.5s cubic-bezier(0.4, 0, 0.2, 1);
  opacity: 0;
  transform: translateY(-5px);
  margin-top: 0;
}
.field-wrapper.is-expanded {
  grid-template-rows: 1fr;
  opacity: 1;
  transform: translateY(0);
  margin-top: 1rem;
}
.field-inner { overflow: hidden; }

/* 特性面板 */
.feature-panel-hide {
  max-height: 0;
  opacity: 0;
  transform: translateY(-10px);
  pointer-events: none;
}
.feature-panel-show {
  max-height: 400px;
  opacity: 1;
  transform: translateY(0);
}
</style>
