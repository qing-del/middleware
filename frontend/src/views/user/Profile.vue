<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { emailApi } from '@/api/email'
import { adminApi } from '@/api/admin'
import type { UserModifyParams } from '@/api/admin'
import type { EmailStatus } from '@/types'
import {
  User, Mail, Lock, ShieldCheck, AlertCircle,
  Loader2, KeyRound, HardDrive, Save
} from 'lucide-vue-next'

const authStore = useAuthStore()

// --- Email / activation state ---
const emailLoading = ref(true)
const emailStatus = ref<EmailStatus | null>(null)
const activationCode = ref('')
const verifyingCode = ref(false)
const resending = ref(false)
const emailMessage = ref('')

// --- Profile edit state ---
const profileSaving = ref(false)
const profileMsg = ref('')
const editNickname = ref('')
const editEmail = ref('')

// --- Password change state ---
const passwordSaving = ref(false)
const passwordMsg = ref('')
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')

const accent = computed(() => authStore.isAdmin ? 'rose' : 'indigo')

async function fetchEmailStatus() {
  emailLoading.value = true
  try {
    emailStatus.value = await emailApi.getEmailStatus()
  } catch {
    emailStatus.value = null
  } finally {
    emailLoading.value = false
  }
}

async function handleResend() {
  resending.value = true
  emailMessage.value = ''
  try {
    await emailApi.resendActivation()
    emailMessage.value = '激活邮件已重新发送'
  } catch {
    emailMessage.value = '发送失败，请稍后重试'
  } finally {
    resending.value = false
  }
}

async function handleVerifyCode() {
  if (!activationCode.value.trim()) return
  verifyingCode.value = true
  emailMessage.value = ''
  try {
    const msg = await emailApi.verifyCode(activationCode.value.trim())
    emailMessage.value = (msg as unknown as string) || '激活成功'
    activationCode.value = ''
    await fetchEmailStatus()
  } catch {
    emailMessage.value = '激活码无效或已过期'
  } finally {
    verifyingCode.value = false
  }
}

async function handleSaveProfile() {
  profileSaving.value = true
  profileMsg.value = ''
  try {
    if (authStore.isAdmin && authStore.user) {
      const payload: UserModifyParams = {
        id: authStore.user.id!,
        nickname: editNickname.value || undefined,
        email: editEmail.value || undefined
      }
      await adminApi.modifyUser(payload)
    } else {
      await authStore.updateProfile({
        nickname: editNickname.value || undefined,
        email: editEmail.value || undefined
      })
    }
    profileMsg.value = '保存成功'
  } catch {
    profileMsg.value = '保存失败'
  } finally {
    profileSaving.value = false
  }
}

async function handleChangePassword() {
  if (!currentPassword.value || !newPassword.value || !confirmPassword.value) {
    passwordMsg.value = '请填写所有密码字段'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    passwordMsg.value = '两次输入的新密码不一致'
    return
  }
  passwordSaving.value = true
  passwordMsg.value = ''
  try {
    if (authStore.isAdmin && authStore.user) {
      const payload: UserModifyParams = {
        id: authStore.user.id!,
        password: currentPassword.value,
        newPassword: newPassword.value,
        confirmPassword: confirmPassword.value
      }
      await adminApi.modifyUser(payload)
    } else {
      await authStore.updateProfile({
        password: currentPassword.value,
        newPassword: newPassword.value,
        confirmPassword: confirmPassword.value
      })
    }
    passwordMsg.value = '密码修改成功'
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch {
    passwordMsg.value = '密码修改失败，请检查当前密码是否正确'
  } finally {
    passwordSaving.value = false
  }
}

function formatBytes(bytes: number | undefined): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(i > 0 ? 1 : 0)} ${units[i]}`
}

onMounted(() => {
  if (authStore.user) {
    editNickname.value = authStore.user.nickname || ''
    editEmail.value = authStore.user.email || ''
  }
  fetchEmailStatus()
})
</script>

<template>
  <div class="space-y-8">
    <!-- 页面标题 -->
    <div class="flex items-center gap-4">
      <div :class="[
        'w-12 h-12 rounded-2xl flex items-center justify-center',
        authStore.isAdmin
          ? 'bg-gradient-to-br from-rose-500 to-indigo-600 shadow-[0_0_20px_rgba(244,63,94,0.4)]'
          : 'bg-gradient-to-br from-indigo-500 to-purple-600 shadow-[0_0_20px_rgba(99,102,241,0.4)]'
      ]">
        <User class="text-white w-6 h-6" />
      </div>
      <div>
        <h1 class="text-2xl font-black text-slate-100 tracking-tighter">个人设置</h1>
        <p class="text-[11px] text-slate-500 font-medium tracking-wider uppercase mt-0.5">Personal Settings</p>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-8">
      <!-- 卡片 1: 基本资料 -->
      <div class="glass-panel rounded-2xl p-6 space-y-5">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg bg-white/[0.03] border border-white/[0.06] flex items-center justify-center">
            <User class="w-4 h-4 text-slate-400" />
          </div>
          <div>
            <h3 class="text-sm font-bold text-slate-200">基本资料</h3>
            <p class="text-[10px] text-slate-500 font-medium tracking-wide">PROFILE INFO</p>
          </div>
        </div>

        <div class="space-y-4">
          <div>
            <label class="text-[10px] text-slate-500 font-semibold uppercase tracking-wider mb-1 block">用户名</label>
            <p class="text-sm text-slate-300 font-medium bg-black/20 border border-white/[0.05] rounded-xl px-4 py-2.5">
              {{ authStore.user?.username || '-' }}
            </p>
          </div>
          <div>
            <label class="text-[10px] text-slate-500 font-semibold uppercase tracking-wider mb-1 block">昵称</label>
            <input
              v-model="editNickname"
              type="text"
              class="w-full bg-black/20 border border-white/[0.05] rounded-xl px-4 py-2.5 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-indigo-500/50 focus:ring-4 focus:ring-indigo-500/10 transition-all"
              placeholder="输入昵称"
            />
          </div>
          <div>
            <label class="text-[10px] text-slate-500 font-semibold uppercase tracking-wider mb-1 block">邮箱</label>
            <input
              v-model="editEmail"
              type="email"
              class="w-full bg-black/20 border border-white/[0.05] rounded-xl px-4 py-2.5 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-indigo-500/50 focus:ring-4 focus:ring-indigo-500/10 transition-all"
              placeholder="输入邮箱地址"
            />
          </div>

          <div v-if="authStore.user?.maxStorageBytes" class="flex items-center gap-3 bg-black/20 border border-white/[0.05] rounded-xl px-4 py-2.5">
            <HardDrive class="w-4 h-4 text-slate-500" />
            <span class="text-[11px] text-slate-400 font-medium">
              存储空间：{{ formatBytes(authStore.user.usedStorageBytes) }} / {{ formatBytes(authStore.user.maxStorageBytes) }}
            </span>
          </div>
        </div>

        <div class="flex items-center gap-3 pt-2">
          <button
            @click="handleSaveProfile"
            :disabled="profileSaving"
            class="flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold bg-indigo-500 hover:bg-indigo-400 text-white shadow-[0_0_20px_rgba(99,102,241,0.3)] hover:shadow-[0_0_30px_rgba(99,102,241,0.5)] transition-all disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <Loader2 v-if="profileSaving" class="w-4 h-4 animate-spin" />
            <Save v-else class="w-4 h-4" />
            {{ profileSaving ? '保存中...' : '保存修改' }}
          </button>
          <p v-if="profileMsg" :class="[
            'text-xs font-medium',
            profileMsg.includes('成功') ? 'text-emerald-400' : 'text-rose-400'
          ]">{{ profileMsg }}</p>
        </div>
      </div>

      <!-- 卡片 2: 密码修改 -->
      <div class="glass-panel rounded-2xl p-6 space-y-5">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg bg-white/[0.03] border border-white/[0.06] flex items-center justify-center">
            <Lock class="w-4 h-4 text-slate-400" />
          </div>
          <div>
            <h3 class="text-sm font-bold text-slate-200">修改密码</h3>
            <p class="text-[10px] text-slate-500 font-medium tracking-wide">CHANGE PASSWORD</p>
          </div>
        </div>

        <div class="space-y-4">
          <div>
            <label class="text-[10px] text-slate-500 font-semibold uppercase tracking-wider mb-1 block">当前密码</label>
            <input
              v-model="currentPassword"
              type="password"
              class="w-full bg-black/20 border border-white/[0.05] rounded-xl px-4 py-2.5 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-indigo-500/50 focus:ring-4 focus:ring-indigo-500/10 transition-all"
              placeholder="输入当前密码"
            />
          </div>
          <div>
            <label class="text-[10px] text-slate-500 font-semibold uppercase tracking-wider mb-1 block">新密码</label>
            <input
              v-model="newPassword"
              type="password"
              class="w-full bg-black/20 border border-white/[0.05] rounded-xl px-4 py-2.5 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-indigo-500/50 focus:ring-4 focus:ring-indigo-500/10 transition-all"
              placeholder="输入新密码"
            />
          </div>
          <div>
            <label class="text-[10px] text-slate-500 font-semibold uppercase tracking-wider mb-1 block">确认新密码</label>
            <input
              v-model="confirmPassword"
              type="password"
              class="w-full bg-black/20 border border-white/[0.05] rounded-xl px-4 py-2.5 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-indigo-500/50 focus:ring-4 focus:ring-indigo-500/10 transition-all"
              placeholder="再次输入新密码"
            />
          </div>
        </div>

        <div class="flex items-center gap-3 pt-2">
          <button
            @click="handleChangePassword"
            :disabled="passwordSaving"
            class="flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold bg-indigo-500 hover:bg-indigo-400 text-white shadow-[0_0_20px_rgba(99,102,241,0.3)] hover:shadow-[0_0_30px_rgba(99,102,241,0.5)] transition-all disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <Loader2 v-if="passwordSaving" class="w-4 h-4 animate-spin" />
            <Lock v-else class="w-4 h-4" />
            {{ passwordSaving ? '修改中...' : '修改密码' }}
          </button>
          <p v-if="passwordMsg" :class="[
            'text-xs font-medium',
            passwordMsg.includes('成功') ? 'text-emerald-400' : 'text-rose-400'
          ]">{{ passwordMsg }}</p>
        </div>
      </div>

      <!-- 卡片 3: 邮箱与激活 -->
      <div class="glass-panel rounded-2xl p-6 space-y-5 lg:col-span-2">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg bg-white/[0.03] border border-white/[0.06] flex items-center justify-center">
            <Mail class="w-4 h-4 text-slate-400" />
          </div>
          <div>
            <h3 class="text-sm font-bold text-slate-200">邮箱与激活</h3>
            <p class="text-[10px] text-slate-500 font-medium tracking-wide">EMAIL &amp; ACTIVATION</p>
          </div>
        </div>

        <div v-if="emailLoading" class="flex items-center justify-center py-8">
          <Loader2 class="w-5 h-5 text-indigo-400 animate-spin" />
        </div>

        <template v-else-if="emailStatus">
          <!-- 激活状态 -->
          <div class="flex items-center gap-4">
            <div :class="[
              'w-12 h-12 rounded-2xl flex items-center justify-center',
              emailStatus.isActive
                ? 'bg-emerald-500/10 border border-emerald-500/20 text-emerald-400'
                : 'bg-amber-500/10 border border-amber-500/20 text-amber-400'
            ]">
              <ShieldCheck v-if="emailStatus.isActive" class="w-6 h-6" />
              <AlertCircle v-else class="w-6 h-6" />
            </div>
            <div>
              <p class="text-sm font-bold text-slate-200">
                {{ emailStatus.isActive ? '账号已激活' : '账号未激活' }}
              </p>
              <p class="text-xs text-slate-500">{{ emailStatus.email || '未绑定邮箱' }}</p>
            </div>
          </div>

          <!-- 6-digit code verification (if not active) -->
          <div v-if="!emailStatus.isActive" class="space-y-3">
            <div class="flex items-center gap-3">
              <input
                v-model="activationCode"
                type="text"
                maxlength="6"
                class="w-48 bg-black/20 border border-white/[0.05] rounded-xl px-4 py-2.5 text-sm text-slate-200 placeholder:text-slate-600 text-center tracking-[8px] font-mono focus:outline-none focus:border-indigo-500/50 focus:ring-4 focus:ring-indigo-500/10 transition-all"
                placeholder="000000"
              />
              <button
                @click="handleVerifyCode"
                :disabled="verifyingCode || activationCode.length < 6"
                class="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold bg-indigo-500 hover:bg-indigo-400 text-white transition-all disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <Loader2 v-if="verifyingCode" class="w-4 h-4 animate-spin" />
                <KeyRound v-else class="w-4 h-4" />
                验证激活码
              </button>
            </div>
          </div>

          <!-- Action buttons -->
          <div v-if="!emailStatus.isActive" class="flex flex-wrap items-center gap-3">
            <button
              v-if="emailStatus.email"
              @click="handleResend"
              :disabled="resending"
              class="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold bg-indigo-500 hover:bg-indigo-400 text-white transition-all disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <Loader2 v-if="resending" class="w-4 h-4 animate-spin" />
              <Mail v-else class="w-4 h-4" />
              发送激活邮件
            </button>
          </div>

          <!-- Feedback -->
          <p v-if="emailMessage" :class="[
            'text-xs font-medium px-4 py-2.5 rounded-xl',
            emailMessage.includes('失败') || emailMessage.includes('无效') || emailMessage.includes('过期')
              ? 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
              : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
          ]">{{ emailMessage }}</p>

          <p v-if="!emailStatus.email" class="text-xs text-slate-500 bg-black/20 border border-white/[0.05] rounded-xl p-4">
            请先在基本资料中绑定邮箱地址，邮箱将用于账号激活和重要通知。
          </p>
        </template>
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
