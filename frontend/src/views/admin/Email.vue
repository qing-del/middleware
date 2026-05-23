<script setup lang="ts">
import { ref } from 'vue'
import { emailApi } from '@/api/email'
import type { EmailResult } from '@/types'
import EmailComposer from '@/components/EmailComposer.vue'
import { Mail, CheckCircle2, XCircle, Clock } from 'lucide-vue-next'

const lastResult = ref<EmailResult | null>(null)
const sending = ref(false)

interface SentLog {
  subject: string
  successCount: number
  failCount: number
  time: string
}

const sentLogs = ref<SentLog[]>([])

async function handleSubmit(data: { subject: string; body: string; userId?: number; roleId?: number }) {
  sending.value = true
  try {
    const result = await emailApi.sendCustomEmail(data)
    lastResult.value = result
    sentLogs.value.unshift({
      subject: data.subject,
      successCount: result.successCount,
      failCount: result.failCount,
      time: new Date().toLocaleString()
    })
  } catch {
    lastResult.value = { successCount: 0, failCount: 1, message: '发送失败，请检查邮件配置' }
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <div class="space-y-8">
    <!-- 页面标题 -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-4">
        <div class="w-12 h-12 rounded-2xl bg-gradient-to-br from-rose-500 to-indigo-600 flex items-center justify-center shadow-[0_0_20px_rgba(244,63,94,0.4)]">
          <Mail class="text-white w-6 h-6" />
        </div>
        <div>
          <h1 class="text-2xl font-black text-slate-100 tracking-tighter">邮件中心</h1>
          <p class="text-[11px] text-slate-500 font-medium tracking-wider uppercase mt-0.5">Email Dispatch Center</p>
        </div>
      </div>
    </div>

    <!-- 发送结果提示 -->
    <div v-if="lastResult" :class="[
      'flex items-center gap-3 px-5 py-4 rounded-2xl border text-sm',
      lastResult.failCount > 0
        ? 'bg-amber-500/5 border-amber-500/20 text-amber-300'
        : 'bg-emerald-500/5 border-emerald-500/20 text-emerald-300'
    ]">
      <CheckCircle2 v-if="lastResult.failCount === 0" class="w-5 h-5 flex-shrink-0" />
      <XCircle v-else class="w-5 h-5 flex-shrink-0" />
      <span class="font-medium">{{ lastResult.message }}</span>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
      <!-- 撰写区 -->
      <div class="lg:col-span-2">
        <EmailComposer mode="admin" @submit="handleSubmit" />
      </div>

      <!-- 发送记录 -->
      <div class="space-y-4">
        <div class="flex items-center gap-2.5">
          <div class="w-8 h-8 rounded-lg bg-white/[0.03] border border-white/[0.06] flex items-center justify-center">
            <Clock class="w-4 h-4 text-slate-400" />
          </div>
          <div>
            <h3 class="text-sm font-bold text-slate-300">最近发送</h3>
            <p class="text-[10px] text-slate-500 font-medium tracking-wide">RECENT SENDS</p>
          </div>
        </div>

        <div v-if="sentLogs.length === 0" class="glass-panel rounded-2xl p-6 text-center">
          <p class="text-sm text-slate-500">暂无发送记录</p>
        </div>

        <div v-else class="space-y-2">
          <div
            v-for="(log, i) in sentLogs"
            :key="i"
            class="glass-panel rounded-xl p-4 space-y-2"
          >
            <p class="text-sm font-semibold text-slate-200 truncate">{{ log.subject }}</p>
            <div class="flex items-center gap-3 text-[11px]">
              <span :class="log.failCount > 0 ? 'text-amber-400' : 'text-emerald-400'">
                {{ log.failCount > 0 ? `部分失败 (${log.successCount}/${log.successCount + log.failCount})` : '全部成功' }}
              </span>
              <span class="text-slate-600">{{ log.time }}</span>
            </div>
          </div>
        </div>
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
