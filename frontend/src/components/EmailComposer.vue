<script setup lang="ts">
import { ref } from 'vue'
import { Mail, Send, Eye, Loader2 } from 'lucide-vue-next'

defineProps<{
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
const emptyPreview = '<span class="empty-preview">暂无内容</span>'

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
    <div class="flex items-center gap-3">
      <div class="composer-icon">
        <Mail class="h-5 w-5" />
      </div>
      <div>
        <h2 class="text-lg font-semibold tracking-tight text-[var(--cn-text)]">
          {{ mode === 'admin' ? '撰写邮件' : '邮件信息' }}
        </h2>
        <p class="text-[11px] font-semibold uppercase tracking-[0.18em] text-[var(--cn-text-muted)]">
          {{ mode === 'admin' ? 'Compose Email' : 'Email Settings' }}
        </p>
      </div>
    </div>

    <div class="composer-card space-y-5">
      <div v-if="mode === 'admin'" class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-2">
          <label class="field-label">用户 ID</label>
          <input
            v-model.number="userId"
            type="number"
            placeholder="指定用户 ID（选填）"
            class="cn-input w-full rounded-lg px-4 py-2.5 text-sm"
          />
        </div>
        <div class="space-y-2">
          <label class="field-label">角色筛选</label>
          <select
            v-model.number="roleId"
            class="cn-input w-full cursor-pointer appearance-none rounded-lg px-4 py-2.5 text-sm"
          >
            <option :value="undefined">全部角色</option>
            <option :value="1">Creator</option>
            <option :value="2">Admin</option>
            <option :value="3">User</option>
            <option :value="4">VIP</option>
          </select>
        </div>
      </div>

      <div class="space-y-2">
        <label class="field-label">邮件主题</label>
        <input
          v-model="subject"
          type="text"
          placeholder="请输入邮件主题"
          class="cn-input w-full rounded-lg px-4 py-2.5 text-sm"
        />
      </div>

      <div class="space-y-2">
        <div class="flex items-center justify-between">
          <label class="field-label">邮件正文</label>
          <button
            class="preview-toggle"
            type="button"
            @click="showPreview = !showPreview"
          >
            <Eye class="h-3.5 w-3.5" />
            {{ showPreview ? '编辑' : '预览' }}
          </button>
        </div>
        <div
          v-if="showPreview"
          class="prose-preview min-h-[180px] rounded-lg border border-[var(--cn-border)] bg-[var(--cn-surface-muted)] p-4 text-sm leading-relaxed text-[var(--cn-text-soft)]"
          v-html="body || emptyPreview"
        ></div>
        <textarea
          v-else
          v-model="body"
          rows="10"
          placeholder="请输入邮件正文（支持 HTML）"
          class="cn-input w-full resize-y rounded-lg px-4 py-3 font-mono text-sm"
        ></textarea>
      </div>

      <div class="flex items-center gap-3 pt-2">
        <button
          class="cn-btn cn-btn-primary px-6"
          type="button"
          :disabled="sending || !subject.trim() || !body.trim()"
          @click="handleSubmit"
        >
          <Loader2 v-if="sending" class="h-4 w-4 animate-spin" />
          <Send v-else class="h-4 w-4" />
          {{ sending ? '发送中...' : '发送邮件' }}
        </button>
        <button
          class="cn-btn px-5"
          type="button"
          @click="handleReset"
        >
          重置
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.composer-icon {
  display: flex;
  height: 40px;
  width: 40px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--cn-border);
  border-radius: var(--cn-radius-sm);
  background: var(--cn-surface);
  color: var(--cn-text);
}

.composer-card {
  border: 1px solid var(--cn-border);
  border-radius: var(--cn-radius-xl);
  background: var(--cn-surface);
  padding: 24px;
  box-shadow: var(--cn-shadow-xs);
}

.field-label {
  display: block;
  color: var(--cn-text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.preview-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--cn-text-muted);
  font-size: 12px;
  font-weight: 600;
  transition: color var(--cn-fast) var(--cn-ease);
}

.preview-toggle:hover {
  color: var(--cn-text);
}

.prose-preview :deep(a) {
  color: var(--cn-text);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.prose-preview :deep(.empty-preview) {
  color: var(--cn-text-faint);
  font-style: italic;
}
</style>
