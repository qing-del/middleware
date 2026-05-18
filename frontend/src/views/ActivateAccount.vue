<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { Loader2, CheckCircle2, XCircle } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()

const state = ref<'loading' | 'success' | 'error'>('loading')
const message = ref('正在激活账号...')

async function activate() {
  const token = route.params.token as string
  if (!token) {
    state.value = 'error'
    message.value = '未提供激活令牌'
    return
  }

  try {
    const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
    await axios.get(`${baseURL}/user/user/active/${token}`, {
      headers: { activeToken: token }
    })
    state.value = 'success'
    message.value = '账号激活成功！3 秒后跳转到登录页...'
    setTimeout(() => {
      router.push('/login')
    }, 3000)
  } catch {
    state.value = 'error'
    message.value = '激活失败，链接可能已过期或账号已激活'
  }
}

onMounted(() => {
  activate()
})
</script>

<template>
  <div class="min-h-screen bg-[#020617] flex items-center justify-center p-8 selection:bg-indigo-500/30">
    <!-- 环境光晕 -->
    <div class="fixed top-[-10%] left-[-5%] w-[500px] h-[500px] bg-indigo-600/10 blur-[150px] rounded-full pointer-events-none"></div>
    <div class="fixed bottom-[-10%] right-[-5%] w-[400px] h-[400px] bg-purple-600/10 blur-[120px] rounded-full pointer-events-none"></div>

    <div class="glass-panel rounded-3xl p-10 max-w-md w-full text-center space-y-6 relative z-10">
      <div class="flex justify-center">
        <div :class="[
          'w-20 h-20 rounded-3xl flex items-center justify-center',
          state === 'loading'
            ? 'bg-indigo-500/10 border border-indigo-500/20'
            : state === 'success'
              ? 'bg-emerald-500/10 border border-emerald-500/20'
              : 'bg-rose-500/10 border border-rose-500/20'
        ]">
          <Loader2 v-if="state === 'loading'" class="w-10 h-10 text-indigo-400 animate-spin" />
          <CheckCircle2 v-else-if="state === 'success'" class="w-10 h-10 text-emerald-400" />
          <XCircle v-else class="w-10 h-10 text-rose-400" />
        </div>
      </div>

      <div>
        <h1 class="text-xl font-black text-slate-100 tracking-tighter">
          <span class="bg-gradient-to-r from-indigo-400 to-purple-400 bg-clip-text text-transparent">CORE</span><span class="font-light">NODE</span>
        </h1>
        <p class="text-[10px] text-slate-500 font-semibold uppercase tracking-widest mt-1">Account Activation</p>
      </div>

      <p :class="[
        'text-sm font-medium',
        state === 'loading' ? 'text-slate-400' : state === 'success' ? 'text-emerald-300' : 'text-rose-300'
      ]">
        {{ message }}
      </p>

      <button
        v-if="state === 'error'"
        @click="router.push('/login')"
        class="px-6 py-2.5 rounded-xl text-sm font-semibold bg-white/[0.03] border border-white/[0.08] text-slate-300 hover:bg-white/[0.08] transition-all"
      >
        前往登录页
      </button>
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
