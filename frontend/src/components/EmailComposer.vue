<script setup lang="ts">
import { ref } from 'vue'
import { Mail, Send, Eye, Loader2 } from 'lucide-vue-next'

const props = defineProps<{
  mode: 'admin' | 'user'
}>()

const emit = defineEmits<{
  submit: [data: { subject: string; body: string; userId?: number; roleId?: number }]
}>()

const subject = ref('')
const body = ref('')
const userId = ref<number | undefined>()
const roleId = ref<number | undefined>()
const showPreview = ref(false)
const sending = ref(false)

const accent = props.mode === 'admin' ? 'rose' : 'indigo'

async function handleSubmit() {
  if (!subject.value.trim() || !body.value.trim()) return
  sending.value = true
  try {
    emit('submit', {
      subject: subject.value,
      body: body.value,
      userId: userId.value || undefined,
      roleId: roleId.value || undefined
    })
  } finally {
    sending.value = false
  }
}

function handleReset() {
  subject.value = ''
  body.value = ''
  userId.value = undefined
  roleId.value = undefined
}
</script>

<template>
  <div class="space-y-5">
    <!-- 标题栏 -->
    <div class="flex items-center gap-3">
      <div :class="[
        'w-10 h-10 rounded-xl flex items-center justify-center',
        mode === 'admin'
          ? 'bg-gradient-to-br from-rose-500 to-indigo-600 shadow-[0_0_15px_rgba(244,63,94,0.3)]'
          : 'bg-gradient-to-br from-indigo-500 to-purple-600 shadow-[0_0_15px_rgba(99,102,241,0.3)]'
      ]">
        <Mail class="text-white w-5 h-5" />
      </div>
      <div>
        <h2 class="text-lg font-bold text-slate-100 tracking-tight">
          {{ mode === 'admin' ? '撰写邮件' : '邮件信息' }}
        </h2>
        <p class="text-[11px] text-slate-500 font-medium tracking-wide">
          {{ mode === 'admin' ? 'COMPOSE EMAIL' : 'EMAIL SETTINGS' }}
        </p>
      </div>
    </div>

    <!-- 表单卡片 -->
    <div class="glass-panel rounded-2xl p-6 space-y-5">
      <!-- 收件人区域 (admin only) -->
      <div v-if="mode === 'admin'" class="grid grid-cols-2 gap-4">
        <div class="space-y-2">
          <label class="text-xs font-semibold text-slate-400 uppercase tracking-wider">用户 ID</label>
          <input
            v-model.number="userId"
            type="number"
            placeholder="指定用户 ID（选填）"
            class="w-full bg-black/20 border border-white/[0.05] rounded-xl px-4 py-2.5 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-rose-500/50 focus:ring-4 focus:ring-rose-500/10 transition-all"
          />
        </div>
        <div class="space-y-2">
          <label class="text-xs font-semibold text-slate-400 uppercase tracking-wider">角色筛选</label>
          <select
            v-model.number="roleId"
            class="w-full bg-black/20 border border-white/[0.05] rounded-xl px-4 py-2.5 text-sm text-slate-200 focus:outline-none focus:border-rose-500/50 focus:ring-4 focus:ring-rose-500/10 transition-all appearance-none cursor-pointer"
          >
            <option :value="undefined">全部角色</option>
            <option :value="1">Creator</option>
            <option :value="2">Admin</option>
            <option :value="3">User</option>
            <option :value="4">VIP</option>
          </select>
        </div>
      </div>

      <!-- 主题 -->
      <div class="space-y-2">
        <label class="text-xs font-semibold text-slate-400 uppercase tracking-wider">邮件主题</label>
        <input
          v-model="subject"
          type="text"
          placeholder="请输入邮件主题"
          :class="[
            'w-full bg-black/20 border rounded-xl px-4 py-2.5 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:ring-4 transition-all',
            mode === 'admin'
              ? 'border-white/[0.05] focus:border-rose-500/50 focus:ring-rose-500/10'
              : 'border-white/[0.05] focus:border-indigo-500/50 focus:ring-indigo-500/10'
          ]"
        />
      </div>

      <!-- 邮件正文 -->
      <div class="space-y-2">
        <div class="flex items-center justify-between">
          <label class="text-xs font-semibold text-slate-400 uppercase tracking-wider">邮件正文</label>
          <button
            @click="showPreview = !showPreview"
            :class="[
              'flex items-center gap-1.5 text-[11px] font-medium transition-colors',
              showPreview ? 'text-emerald-400' : 'text-slate-500 hover:text-slate-300'
            ]"
          >
            <Eye class="w-3.5 h-3.5" />
            {{ showPreview ? '编辑' : '预览' }}
          </button>
        </div>
        <div v-if="showPreview" class="min-h-[180px] bg-black/20 border border-white/[0.05] rounded-xl p-4 text-sm text-slate-300 leading-relaxed prose-preview" v-html="body || '<span class=\'text-slate-600 italic\'>暂无内容</span>'"></div>
        <textarea
          v-else
          v-model="body"
          rows="10"
          placeholder="请输入邮件正文（支持 HTML）"
          :class="[
            'w-full bg-black/20 border rounded-xl px-4 py-3 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:ring-4 transition-all resize-y font-mono',
            mode === 'admin'
              ? 'border-white/[0.05] focus:border-rose-500/50 focus:ring-rose-500/10'
              : 'border-white/[0.05] focus:border-indigo-500/50 focus:ring-indigo-500/10'
          ]"
        ></textarea>
      </div>

      <!-- 操作按钮 -->
      <div class="flex items-center gap-3 pt-2">
        <button
          @click="handleSubmit"
          :disabled="sending || !subject.trim() || !body.trim()"
          :class="[
            'flex items-center gap-2 px-6 py-2.5 rounded-xl text-sm font-semibold transition-all disabled:opacity-40 disabled:cursor-not-allowed',
            mode === 'admin'
              ? 'bg-rose-500 hover:bg-rose-400 text-white shadow-[0_0_20px_rgba(244,63,94,0.3)] hover:shadow-[0_0_30px_rgba(244,63,94,0.5)]'
              : 'bg-indigo-500 hover:bg-indigo-400 text-white shadow-[0_0_20px_rgba(99,102,241,0.3)] hover:shadow-[0_0_30px_rgba(99,102,241,0.5)]'
          ]"
        >
          <Loader2 v-if="sending" class="w-4 h-4 animate-spin" />
          <Send v-else class="w-4 h-4" />
          {{ sending ? '发送中...' : '发送邮件' }}
        </button>
        <button
          @click="handleReset"
          class="px-5 py-2.5 rounded-xl text-sm font-medium text-slate-400 hover:text-slate-200 bg-white/[0.02] border border-white/[0.05] hover:bg-white/[0.06] hover:border-white/10 transition-all"
        >
          重置
        </button>
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

.prose-preview :deep(a) {
  color: #818cf8;
  text-decoration: underline;
}
</style>
