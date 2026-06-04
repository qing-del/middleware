<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import type { AdminUserItem, PageResult, UserModifyParams } from '@/api/admin'
import {
  Users, Search, Trash2, Loader2, X, ChevronLeft, ChevronRight,
  Plus, Pencil, ShieldBan, ShieldCheck
} from 'lucide-vue-next'
import { confirmAction } from '@/utils/feedback'

const loading = ref(true)
const userList = ref<AdminUserItem[]>([])
const total = ref(0)
const filterId = ref('')
const filterUsername = ref('')
const filterStatus = ref('')
const filterRoleId = ref('')
const currentPage = ref(1)
const pageSize = ref(15)
const selectedIds = ref<Set<number>>(new Set())

const modalVisible = ref(false)
const modalMode = ref<'create' | 'edit'>('create')
const editingUserId = ref<number | null>(null)
const form = ref({
  username: '',
  password: '',
  nickname: '',
  email: '',
  roleId: 3,
  status: 1,
  maxStorageBytes: undefined as number | undefined
})
const submitting = ref(false)

const isBatchMode = computed(() => selectedIds.value.size > 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function roleLabel(roleId: number): string {
  const mapping: Record<number, string> = { 1: 'Creator', 2: 'Admin', 3: 'User', 4: 'VIP' }
  return mapping[roleId] || 'Unknown'
}

function statusLabel(status: number): string {
  const mapping: Record<number, string> = { 0: '禁用', 1: '正常', 2: '未激活' }
  return mapping[status] || 'Unknown'
}

function roleClass(roleId: number): string {
  if (roleId === 2) return 'role-badge-admin text-cyan-50 border-cyan-400/30'
  if (roleId === 1) return 'text-indigo-200 bg-indigo-500/10 border-indigo-500/20'
  if (roleId === 4) return 'text-amber-200 bg-amber-500/10 border-amber-500/20'
  return 'text-slate-300 bg-slate-500/10 border-slate-500/20'
}

function statusClass(status: number): string {
  if (status === 1) return 'text-emerald-300 bg-emerald-500/10 border-emerald-500/20'
  if (status === 0) return 'text-rose-300 bg-rose-500/10 border-rose-500/20'
  return 'text-amber-300 bg-amber-500/10 border-amber-500/20'
}

function formatBytes(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, index)).toFixed(index > 0 ? 1 : 0)} ${units[index]}`
}

function formatDate(raw: string): string {
  if (!raw) return '-'
  const d = new Date(raw)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatNumber(n: number): string {
  return n.toLocaleString()
}

function visiblePages(): number[] {
  const pages: number[] = []
  const tp = totalPages.value
  const cp = currentPage.value
  let start = Math.max(1, cp - 2)
  const end = Math.min(tp, start + 4)
  if (end - start < 4) start = Math.max(1, end - 4)
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
}

async function fetchUsers() {
  try {
    const res = await adminApi.getUserList({
      id: filterId.value ? Number(filterId.value) : undefined,
      username: filterUsername.value || undefined,
      status: filterStatus.value ? Number(filterStatus.value) : undefined,
      roleId: filterRoleId.value ? Number(filterRoleId.value) : undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    })
    userList.value = (res as unknown as PageResult<AdminUserItem>).records ?? []
    total.value = (res as unknown as PageResult<AdminUserItem>).total ?? 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loading.value = true
  fetchUsers()
}

function handlePageChange(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  currentPage.value = page
  loading.value = true
  fetchUsers()
}

function toggleSelectAll(checked: boolean) {
  if (checked) {
    userList.value.forEach(user => selectedIds.value.add(user.id))
  } else {
    selectedIds.value.clear()
  }
}

function toggleSelect(id: number) {
  if (selectedIds.value.has(id)) {
    selectedIds.value.delete(id)
  } else {
    selectedIds.value.add(id)
  }
}

function openCreateModal() {
  modalMode.value = 'create'
  editingUserId.value = null
  form.value = { username: '', password: '', nickname: '', email: '', roleId: 3, status: 1, maxStorageBytes: undefined }
  modalVisible.value = true
}

function openEditModal(user: AdminUserItem) {
  modalMode.value = 'edit'
  editingUserId.value = user.id
  form.value = {
    username: user.username,
    password: '',
    nickname: user.nickname || '',
    email: user.email || '',
    roleId: user.roleId,
    status: user.status,
    maxStorageBytes: user.maxStorageBytes
  }
  modalVisible.value = true
}

function closeModal() {
  modalVisible.value = false
}

async function handleSubmit() {
  if (!form.value.username.trim()) return
  submitting.value = true
  try {
    if (modalMode.value === 'create') {
      await adminApi.createUser({
        ...form.value,
        username: form.value.username.trim()
      })
    } else if (editingUserId.value) {
      const payload: UserModifyParams = {
        id: editingUserId.value,
        username: form.value.username.trim(),
        nickname: form.value.nickname || undefined,
        email: form.value.email || undefined,
        roleId: form.value.roleId,
        status: form.value.status,
        maxStorageBytes: form.value.maxStorageBytes
      }
      if (form.value.password) {
        payload.newPassword = form.value.password
        payload.confirmPassword = form.value.password
      }
      await adminApi.modifyUser(payload)
    }
    closeModal()
    await fetchUsers()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  if (!await confirmAction({ content: '确定删除该用户吗？', danger: true })) return
  await adminApi.deleteUsers([id])
  await fetchUsers()
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) return
  if (!await confirmAction({ content: `确定删除 ${selectedIds.value.size} 个用户吗？`, danger: true })) return
  await adminApi.deleteUsers([...selectedIds.value])
  selectedIds.value.clear()
  await fetchUsers()
}

async function handleToggleStatus(user: AdminUserItem) {
  const newStatus = user.status === 1 ? 0 : 1
  await adminApi.setUserStatus(newStatus, { id: user.id })
  await fetchUsers()
}

onMounted(() => {
  fetchUsers()
})
</script>

<template>
  <div class="relative mx-auto max-w-7xl space-y-6">
    <div class="fixed top-[-12%] right-[-6%] z-0 h-[520px] w-[520px] rounded-full bg-cyan-500/10 blur-[160px] pointer-events-none"></div>
    <div class="fixed bottom-[-14%] left-[-6%] z-0 h-[460px] w-[460px] rounded-full bg-indigo-500/10 blur-[150px] pointer-events-none"></div>

    <div class="relative z-10 flex flex-col justify-between gap-4 md:flex-row md:items-center">
      <div class="flex items-center space-x-3">
        <div class="rounded-xl border border-cyan-400/20 bg-cyan-400/10 p-2 text-cyan-300">
          <Users class="h-5 w-5" />
        </div>
        <div>
          <h2 class="text-xl font-bold text-white">用户管理</h2>
          <p class="mt-0.5 text-xs text-slate-400">管理平台全部注册用户与权限状态</p>
        </div>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <input v-model="filterId" type="number" placeholder="UID..." class="admin-input w-20" @keyup.enter="handleSearch" />
        <select v-model="filterStatus" class="admin-input w-28" @change="handleSearch">
          <option value="">状态: 全部</option>
          <option value="1">正常</option>
          <option value="0">禁用</option>
          <option value="2">未激活</option>
        </select>
        <select v-model="filterRoleId" class="admin-input w-28" @change="handleSearch">
          <option value="">角色: 全部</option>
          <option value="1">Creator</option>
          <option value="2">Admin</option>
          <option value="3">User</option>
          <option value="4">VIP</option>
        </select>
        <div class="group relative flex h-9 w-9 items-center overflow-hidden rounded-xl border border-white/10 bg-black/20 transition-all duration-300 ease-out hover:w-28 focus-within:!w-48 focus-within:border-cyan-400/50 focus-within:bg-black/40 focus-within:ring-2 focus-within:ring-cyan-400/10">
          <label class="z-10 flex h-full w-9 flex-shrink-0 cursor-pointer items-center justify-center text-slate-500 transition-colors group-hover:text-slate-300 group-focus-within:text-cyan-300">
            <Search class="h-4 w-4" />
          </label>
          <input v-model="filterUsername" type="text" placeholder="用户名..." class="absolute left-9 h-full w-[150px] bg-transparent pr-4 text-sm text-white opacity-0 outline-none transition-opacity duration-300 group-hover:opacity-100 focus-within:!opacity-100 placeholder:text-slate-500" @keyup.enter="handleSearch" />
        </div>
        <button class="group relative flex items-center space-x-1.5 rounded-xl bg-indigo-600 px-3 py-2 text-sm font-bold text-white shadow-[0_0_15px_rgba(99,102,241,0.35)] transition-all hover:bg-indigo-500" @click="openCreateModal">
          <Plus class="h-4 w-4" />
          <span>新建用户</span>
        </button>
      </div>
    </div>

    <Transition name="batch-float">
      <div v-if="isBatchMode" class="glass-panel relative z-10 flex items-center justify-between rounded-xl px-4 py-3">
        <span class="text-sm font-bold text-cyan-200">已选择 <span class="mx-1 text-white">{{ selectedIds.size }}</span> 个用户</span>
        <button class="flex items-center space-x-1.5 rounded-lg border border-rose-500/20 bg-rose-500/10 px-3 py-1.5 text-xs font-bold text-rose-300 transition-all hover:bg-rose-500 hover:text-white" @click="handleBatchDelete">
          <Trash2 class="h-3.5 w-3.5" />
          <span>批量删除</span>
        </button>
      </div>
    </Transition>

    <div class="glass-panel relative z-10 overflow-hidden rounded-2xl border border-white/10">
      <div class="overflow-x-auto">
        <table class="w-full border-collapse text-left">
          <thead>
            <tr>
              <th class="w-10 border-b border-white/5 px-3 py-4"><input type="checkbox" class="glass-checkbox" :checked="selectedIds.size === userList.length && userList.length > 0" @change="toggleSelectAll(($event.target as HTMLInputElement).checked)" /></th>
              <th class="border-b border-white/5 px-3 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">ID</th>
              <th class="border-b border-white/5 px-3 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">用户名</th>
              <th class="border-b border-white/5 px-3 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">昵称</th>
              <th class="border-b border-white/5 px-3 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">邮箱</th>
              <th class="border-b border-white/5 px-3 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">角色</th>
              <th class="border-b border-white/5 px-3 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">状态</th>
              <th class="border-b border-white/5 px-3 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">存储</th>
              <th class="border-b border-white/5 px-3 py-4 text-xs font-bold uppercase tracking-wider text-slate-400">创建时间</th>
              <th class="border-b border-white/5 px-3 py-4 text-right text-xs font-bold uppercase tracking-wider text-slate-400">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/5">
            <tr v-if="loading">
              <td colspan="10" class="px-6 py-16 text-center">
                <Loader2 class="mx-auto mb-3 h-6 w-6 animate-spin text-cyan-300" />
                <span class="text-xs text-slate-500">加载中...</span>
              </td>
            </tr>
            <tr v-else-if="userList.length === 0">
              <td colspan="10" class="px-6 py-16 text-center text-sm text-slate-500">暂无用户数据</td>
            </tr>
            <tr
              v-for="user in userList"
              :key="user.id"
              class="group transition-colors duration-300"
              :class="user.status === 0 ? 'bg-rose-500/10 hover:bg-rose-500/15' : 'hover:bg-white/5'"
            >
              <td class="px-3 py-3"><input type="checkbox" class="glass-checkbox" :checked="selectedIds.has(user.id)" @change="toggleSelect(user.id)" /></td>
              <td class="px-3 py-3 font-mono text-xs text-slate-500">{{ user.id }}</td>
              <td class="px-3 py-3"><span class="text-sm font-bold text-slate-200">{{ user.username }}</span></td>
              <td class="px-3 py-3 text-xs text-slate-400">{{ user.nickname || '-' }}</td>
              <td class="max-w-[180px] truncate px-3 py-3 text-xs text-slate-400">{{ user.email || '-' }}</td>
              <td class="px-3 py-3">
                <span class="inline-flex items-center rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.18em]" :class="roleClass(user.roleId)">
                  {{ roleLabel(user.roleId) }}
                </span>
              </td>
              <td class="px-3 py-3">
                <span class="inline-flex items-center rounded-md border px-2 py-1 text-[10px] font-bold uppercase tracking-wider" :class="statusClass(user.status)">
                  {{ statusLabel(user.status) }}
                </span>
              </td>
              <td class="px-3 py-3 text-xs text-slate-500">{{ formatBytes(user.usedStorageBytes) }} / {{ formatBytes(user.maxStorageBytes) }}</td>
              <td class="px-3 py-3 text-xs text-slate-500">{{ formatDate(user.createTime) }}</td>
              <td class="px-3 py-3 text-right">
                <div class="flex items-center justify-end space-x-2 translate-x-1 opacity-0 transition-all duration-200 group-hover:translate-x-0 group-hover:opacity-100">
                  <button
                    class="toggle-shell"
                    :class="user.status === 1 ? 'toggle-on' : 'toggle-off'"
                    :title="user.status === 1 ? '封禁用户' : '解除封禁'"
                    @click="handleToggleStatus(user)"
                  >
                    <span class="toggle-knob">
                      <ShieldBan v-if="user.status === 1" class="h-3 w-3 text-emerald-500 transition-colors duration-300" />
                      <ShieldCheck v-else class="h-3 w-3 text-rose-500 transition-colors duration-300" />
                    </span>
                  </button>
                  <button class="flex h-7 w-7 items-center justify-center rounded bg-white/5 text-slate-400 transition-colors hover:bg-indigo-500/20 hover:text-indigo-300" title="编辑" @click="openEditModal(user)">
                    <Pencil class="h-3.5 w-3.5" />
                  </button>
                  <button class="flex h-7 w-7 items-center justify-center rounded bg-white/5 text-slate-400 transition-colors hover:bg-rose-500/20 hover:text-rose-300" title="删除" @click="handleDelete(user.id)">
                    <Trash2 class="h-3.5 w-3.5" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="!loading && userList.length > 0" class="flex items-center justify-between border-t border-white/5 bg-white/[0.01] px-6 py-4">
        <span class="text-xs text-slate-500">共 {{ formatNumber(total) }} 个用户</span>
        <div class="flex items-center space-x-1">
          <button class="flex h-7 w-7 items-center justify-center rounded text-slate-500 hover:bg-white/5 hover:text-white disabled:opacity-50" :disabled="currentPage <= 1" @click="handlePageChange(currentPage - 1)"><ChevronLeft class="h-4 w-4" /></button>
          <template v-for="page in visiblePages()" :key="page">
            <button v-if="totalPages > 1" class="flex h-7 w-7 items-center justify-center rounded text-xs font-bold transition-colors" :class="page === currentPage ? 'border border-cyan-400/30 bg-cyan-400/15 text-cyan-200' : 'text-slate-400 hover:bg-white/5 hover:text-white'" @click="handlePageChange(page)">{{ page }}</button>
          </template>
          <button class="flex h-7 w-7 items-center justify-center rounded text-slate-500 hover:bg-white/5 hover:text-white" :disabled="currentPage >= totalPages" @click="handlePageChange(currentPage + 1)"><ChevronRight class="h-4 w-4" /></button>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <Transition name="fade">
        <div v-if="modalVisible" class="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm" @click="closeModal"></div>
      </Transition>
      <Transition name="modal">
        <div v-if="modalVisible" class="fixed inset-0 z-50 flex items-center justify-center px-4">
          <div class="glass-panel modal-card relative z-10 w-full max-w-lg rounded-3xl p-8">
            <div class="pointer-events-none absolute -top-10 -right-10 h-32 w-32 rounded-full bg-cyan-400/20 blur-[40px]"></div>
            <div class="mb-6 flex items-center justify-between">
              <h3 class="text-xl font-bold text-white">{{ modalMode === 'create' ? '新建用户' : '编辑用户' }}</h3>
              <button class="text-slate-500 transition-colors hover:text-white" @click="closeModal"><X class="h-5 w-5" /></button>
            </div>
            <form class="space-y-4" @submit.prevent="handleSubmit">
              <div class="grid grid-cols-2 gap-4">
                <div>
                  <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-slate-400">用户名 <span class="text-rose-400">*</span></label>
                  <input v-model="form.username" type="text" required class="admin-input w-full" />
                </div>
                <div>
                  <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-slate-400">密码{{ modalMode === 'edit' ? '（留空不修改）' : '' }} <span v-if="modalMode === 'create'" class="text-rose-400">*</span></label>
                  <input v-model="form.password" type="password" :required="modalMode === 'create'" class="admin-input w-full" />
                </div>
              </div>
              <div class="grid grid-cols-2 gap-4">
                <div>
                  <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-slate-400">昵称</label>
                  <input v-model="form.nickname" type="text" class="admin-input w-full" />
                </div>
                <div>
                  <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-slate-400">邮箱</label>
                  <input v-model="form.email" type="email" class="admin-input w-full" />
                </div>
              </div>
              <div class="grid grid-cols-2 gap-4">
                <div>
                  <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-slate-400">角色</label>
                  <select v-model="form.roleId" class="admin-input w-full">
                    <option :value="1">Creator</option>
                    <option :value="2">Admin</option>
                    <option :value="3">User</option>
                    <option :value="4">VIP</option>
                  </select>
                </div>
                <div>
                  <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-slate-400">状态</label>
                  <select v-model="form.status" class="admin-input w-full">
                    <option :value="1">正常</option>
                    <option :value="0">禁用</option>
                    <option :value="2">未激活</option>
                  </select>
                </div>
              </div>
              <div>
                <label class="mb-1.5 block text-xs font-bold uppercase tracking-widest text-slate-400">存储配额 (字节)</label>
                <input v-model.number="form.maxStorageBytes" type="number" class="admin-input w-full" placeholder="默认 104857600 (100MB)" />
              </div>
              <div class="flex justify-end space-x-3 pt-4">
                <button type="button" class="rounded-xl px-5 py-2.5 text-sm font-bold text-slate-400 transition-colors hover:bg-white/5 hover:text-white" @click="closeModal">取消</button>
                <button type="submit" class="flex items-center space-x-2 rounded-xl bg-indigo-600 px-5 py-2.5 text-sm font-bold text-white shadow-[0_0_15px_rgba(99,102,241,0.35)] transition-all hover:bg-indigo-500" :disabled="submitting">
                  <Loader2 v-if="submitting" class="h-4 w-4 animate-spin" />
                  <span>{{ submitting ? '保存中...' : '确认保存' }}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      </Transition>
    </Teleport>
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

.glass-checkbox {
  appearance: none;
  width: 16px;
  height: 16px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.2);
  cursor: pointer;
  position: relative;
  transition: all 0.2s;
}

.glass-checkbox:checked {
  background: #22d3ee;
  border-color: #22d3ee;
  box-shadow: 0 0 10px rgba(34, 211, 238, 0.35);
}

.glass-checkbox:checked::after {
  content: '';
  position: absolute;
  left: 5px;
  top: 2px;
  width: 4px;
  height: 8px;
  border: solid white;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}

.admin-input {
  height: 36px;
  border-radius: 0.75rem;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(0, 0, 0, 0.2);
  padding: 0 0.75rem;
  color: white;
  font-size: 0.75rem;
  outline: none;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}

.admin-input::placeholder {
  color: rgb(100 116 139);
}

.admin-input:focus {
  border-color: rgba(34, 211, 238, 0.5);
  background: rgba(0, 0, 0, 0.35);
  box-shadow: 0 0 0 2px rgba(34, 211, 238, 0.08);
}

.toggle-shell {
  position: relative;
  display: inline-flex;
  height: 1.75rem;
  width: 3.25rem;
  align-items: center;
  border-radius: 9999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  transition: background-color 0.3s ease, border-color 0.3s ease, box-shadow 0.3s ease;
}

.toggle-on {
  background: rgba(16, 185, 129, 0.18);
  border-color: rgba(16, 185, 129, 0.25);
  box-shadow: inset 0 0 12px rgba(16, 185, 129, 0.15);
}

.toggle-off {
  background: rgba(244, 63, 94, 0.18);
  border-color: rgba(244, 63, 94, 0.25);
  box-shadow: inset 0 0 12px rgba(244, 63, 94, 0.18);
}

.toggle-knob {
  display: inline-flex;
  height: 1.25rem;
  width: 1.25rem;
  align-items: center;
  justify-content: center;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.3);
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1), background-color 0.3s ease;
}

.toggle-on .toggle-knob {
  transform: translateX(0.25rem);
}

.toggle-off .toggle-knob {
  transform: translateX(1.7rem);
}

.role-badge-admin {
  background: linear-gradient(120deg, rgba(20, 184, 166, 0.2), rgba(99, 102, 241, 0.22), rgba(20, 184, 166, 0.2));
  background-size: 200% 100%;
  animation: admin-flow 8s linear infinite;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08), 0 0 20px rgba(34, 211, 238, 0.08);
}

@keyframes admin-flow {
  0% { background-position: 0% 50%; }
  100% { background-position: 200% 50%; }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.28s ease, transform 0.38s cubic-bezier(0.22, 1, 0.36, 1);
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
  transform: scale(0.92) translateY(18px);
}

.modal-enter-to,
.modal-leave-from {
  opacity: 1;
  transform: scale(1) translateY(0);
}

.modal-card {
  transform-origin: center center;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.45), inset 0 1px 1px rgba(255, 255, 255, 0.05);
}

/* ── Batch Float Animation ── */
@keyframes batch-slide-up {
  0% { opacity: 0; transform: translateY(24px) scale(0.96); }
  72% { opacity: 1; transform: translateY(-4px) scale(1.01); }
  100% { opacity: 1; transform: translateY(0) scale(1); }
}
.batch-float-enter-active { transition: opacity 0.42s cubic-bezier(0.22, 1.2, 0.36, 1); }
.batch-float-enter-from,
.batch-float-leave-to { opacity: 0; }
.batch-float-leave-active { transition: opacity 0.26s ease; }
.batch-float-leave-active > * { opacity: 0; transform: translateY(16px) scale(0.98); transition: transform 0.26s ease, opacity 0.26s ease; }

@media (prefers-reduced-motion: reduce) {
  .fade-enter-active,
  .fade-leave-active,
  .modal-enter-active,
  .modal-leave-active,
  .batch-float-enter-active,
  .batch-float-leave-active { transition-duration: 0.01s !important; }
  .modal-enter-from,
  .modal-leave-to { opacity: 0; transform: none; }
  .toggle-knob { transition-duration: 0.01s; }
  .role-badge-admin { animation: none; }
}
</style>
