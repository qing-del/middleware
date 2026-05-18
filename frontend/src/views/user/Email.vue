<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { emailApi } from '@/api/email'
import type { EmailStatus } from '@/types'
import { Mail, RefreshCw, Loader2, ShieldCheck, AlertCircle } from 'lucide-vue-next'

const loading = ref(true)
const resending = ref(false)
const status = ref<EmailStatus | null>(null)
const message = ref('')

async function fetchStatus() {
  loading.value = true
  try {
    status.value = await emailApi.getEmailStatus()
  } catch {
    message.value = '获取邮箱状态失败'
  } finally {
    loading.value = false
  }
}

async function handleResend() {
  resending.value = true
  message.value = ''
  try {
    await emailApi.resendActivation()
    message.value = '激活邮件已重新发送，请查收邮箱'
  } catch {
    message.value = '发送失败，请稍后重试'
  } finally {
    resending.value = false
  }
}

async function handleRequestActivation() {
  resending.value = true
  message.value = ''
  try {
    await emailApi.requestActivation()
    message.value = '激活邮件已发送，请查收邮箱'
  } catch {
    message.value = '发送失败，请确保已绑定邮箱'
  } finally {
    resending.value = false
  }
}

onMounted(() => {
  fetchStatus()
})
</script>

<template>
  <div class="space-y-8">
    <!-- 页面标题 -->
    <div class="flex items-center gap-4">
      <div class="w-12 h-12 rounded-2xl bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center shadow-[0_0_20px_rgba(99,102,241,0.4)]">
        <Mail class="text-white w-6 h-6" />
      </div>
      <div>
        <h1 class="text-2xl font-black text-slate-100 tracking-tighter">邮箱绑定</h1>
        <p class="text-[11px] text-slate-500 font-medium tracking-wider uppercase mt-0.5">Email Settings &amp; Activation</p>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="glass-panel rounded-2xl p-12 flex items-center justify-center">
      <Loader2 class="w-6 h-6 text-indigo-400 animate-spin" />
    </div>

    <!-- 内容 -->
    <template v-else-if="status">
      <!-- 激活状态卡片 -->
      <div class="glass-panel rounded-2xl p-6 space-y-5">
        <div class="flex items-center gap-4">
          <div :class="[
            'w-14 h-14 rounded-2xl flex items-center justify-center',
            status.isActive
              ? 'bg-emerald-500/10 border border-emerald-500/20 text-emerald-400'
              : 'bg-amber-500/10 border border-amber-500/20 text-amber-400'
          ]">
            <ShieldCheck v-if="status.isActive" class="w-7 h-7" />
            <AlertCircle v-else class="w-7 h-7" />
          </div>
          <div>
            <p class="text-base font-bold text-slate-100">
              {{ status.isActive ? '账号已激活' : '账号未激活' }}
            </p>
            <p class="text-xs text-slate-500 mt-0.5">
              {{ status.isActive ? '你的账号已通过邮箱验证' : '请激活你的账号以使用全部功能' }}
            </p>
          </div>
        </div>

        <!-- 邮箱信息 -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div class="bg-black/20 border border-white/[0.05] rounded-xl p-4">
            <p class="text-[10px] text-slate-500 font-semibold uppercase tracking-wider mb-1">绑定邮箱</p>
            <p class="text-sm text-slate-200 font-medium">{{ status.email || '未绑定' }}</p>
          </div>
          <div class="bg-black/20 border border-white/[0.05] rounded-xl p-4">
            <p class="text-[10px] text-slate-500 font-semibold uppercase tracking-wider mb-1">用户名</p>
            <p class="text-sm text-slate-200 font-medium">{{ status.username }}</p>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="flex flex-wrap items-center gap-3 pt-2">
          <button
            v-if="!status.isActive"
            @click="handleRequestActivation"
            :disabled="resending"
            class="flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold bg-indigo-500 hover:bg-indigo-400 text-white shadow-[0_0_20px_rgba(99,102,241,0.3)] hover:shadow-[0_0_30px_rgba(99,102,241,0.5)] transition-all disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <Loader2 v-if="resending" class="w-4 h-4 animate-spin" />
            <Mail v-else class="w-4 h-4" />
            发送激活邮件
          </button>
          <button
            v-if="!status.isActive && status.email"
            @click="handleResend"
            :disabled="resending"
            class="flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-medium text-indigo-400 bg-indigo-500/10 border border-indigo-500/20 hover:bg-indigo-500/20 hover:border-indigo-500/40 transition-all disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <RefreshCw :class="['w-4 h-4', resending ? 'animate-spin' : '']" />
            重新发送激活邮件
          </button>
        </div>

        <!-- 提示消息 -->
        <p v-if="message" :class="[
          'text-sm font-medium px-4 py-2.5 rounded-xl',
          message.includes('失败') ? 'bg-rose-500/10 text-rose-400 border border-rose-500/20' : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
        ]">
          {{ message }}
        </p>

        <p v-if="!status.email" class="text-xs text-slate-500 bg-black/20 border border-white/[0.05] rounded-xl p-4">
          请先在"我的信息"页面更新邮箱地址，邮箱将用于账号激活和重要通知。
        </p>
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
</style>
