<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api/admin'
import type { AdminNoteItem, AdminNoteQueryParams, AdminTopicItem, AdminUserItem, PageResult } from '@/api/admin'
import { getNoteStatusInfo, NoteStatusCode } from '@/api/notes'
import {
  AlertTriangle, ArrowRight, CheckCircle2, ChevronLeft, ChevronRight, Clock,
  Eye, FileCode, FileText, FolderTree, Globe, HardDrive, Hash,
  RefreshCw, Search, Trash2, Users, X, XCircle
} from 'lucide-vue-next'
import { alertWarning, confirmAction } from '@/utils/feedback'

const router = useRouter()

const loadingUsers = ref(false)
const users = ref<AdminUserItem[]>([])
const userTotal = ref(0)
const userPage = ref(1)
const userPageSize = ref(12)
const filterUserId = ref('')
const filterUsername = ref('')
const filterUserStatus = ref('')

const selectedUser = ref<AdminUserItem | null>(null)
const loadingWorkspace = ref(false)
const directories = ref<AdminTopicItem[]>([])
const noteList = ref<AdminNoteItem[]>([])
const noteTotal = ref(0)
const notePage = ref(1)
const notePageSize = ref(12)
const filterTitle = ref('')
const filterStatus = ref('')
const currentDirectoryId = ref<number | null>(null)
const breadcrumbs = ref<AdminTopicItem[]>([])
const selectedIds = ref<Set<number>>(new Set())

const showSourceModal = ref(false)
const sourceContent = ref('')
const sourceTitle = ref('')

const userTotalPages = computed(() => Math.max(1, Math.ceil(userTotal.value / userPageSize.value)))
const noteTotalPages = computed(() => Math.max(1, Math.ceil(noteTotal.value / notePageSize.value)))
const isBatchMode = computed(() => selectedIds.value.size > 0)
const currentDirectoryName = computed(() => breadcrumbs.value[breadcrumbs.value.length - 1]?.topicName ?? '用户根目录')

function formatBytes(bytes?: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(i > 0 ? 1 : 0) + ' ' + units[i]
}

function formatDate(raw?: string): string {
  if (!raw) return '-'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function formatNumber(n: number): string {
  return n.toLocaleString()
}

function visiblePages(current: number, total: number): number[] {
  const pages: number[] = []
  let start = Math.max(1, current - 2)
  const end = Math.min(total, start + 4)
  if (end - start < 4) start = Math.max(1, end - 4)
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
}

function getStatusIcon(iconName: string) {
  const map: Record<string, any> = {
    FilePlus: FileText,
    AlertTriangle,
    RefreshCw,
    FileCode,
    Clock,
    CheckCircle2,
    Globe,
    XCircle,
    Trash2,
    FileEdit: FileText,
    HelpCircle: FileText
  }
  return map[iconName] ?? FileText
}

function roleText(roleId?: number): string {
  if (roleId === 1) return 'Creator'
  if (roleId === 2) return 'Admin'
  if (roleId === 4) return 'VIP'
  return 'User'
}

function userStatusText(status?: number): string {
  if (status === 1) return '正常'
  if (status === 0) return '禁用'
  if (status === 2) return '未激活'
  return '未知'
}

function userStatusClass(status?: number): string {
  if (status === 1) return 'text-emerald-700 bg-emerald-50 border-emerald-200'
  if (status === 0) return 'text-rose-700 bg-rose-50 border-rose-200'
  return 'text-amber-700 bg-amber-50 border-amber-200'
}

function topicStatus(topic: AdminTopicItem) {
  if (topic.isPass === 1) return { label: '已通过', cls: 'text-emerald-700 bg-emerald-50 border-emerald-200' }
  if (topic.isPass === 2) return { label: '已拒绝', cls: 'text-rose-700 bg-rose-50 border-rose-200' }
  return { label: '待审核', cls: 'text-amber-700 bg-amber-50 border-amber-200' }
}

function noteQueryParams(): AdminNoteQueryParams {
  return {
    userId: selectedUser.value?.id,
    title: filterTitle.value.trim() || undefined,
    status: filterStatus.value ? Number(filterStatus.value) : undefined,
    topicId: currentDirectoryId.value ?? undefined,
    unclassified: currentDirectoryId.value == null ? true : undefined,
    pageNum: notePage.value,
    pageSize: notePageSize.value
  }
}

async function fetchUsers() {
  loadingUsers.value = true
  try {
    const res = await adminApi.getUserList({
      id: filterUserId.value ? Number(filterUserId.value) : undefined,
      username: filterUsername.value.trim() || undefined,
      status: filterUserStatus.value ? Number(filterUserStatus.value) : undefined,
      pageNum: userPage.value,
      pageSize: userPageSize.value
    })
    users.value = (res as PageResult<AdminUserItem>).records ?? []
    userTotal.value = (res as PageResult<AdminUserItem>).total ?? 0
  } finally {
    loadingUsers.value = false
  }
}

async function fetchWorkspace() {
  if (!selectedUser.value) return
  loadingWorkspace.value = true
  selectedIds.value.clear()
  try {
    const [dirs, notes] = await Promise.all([
      adminApi.getTopicChildren({ userId: selectedUser.value.id, parentId: currentDirectoryId.value }),
      adminApi.getNoteList(noteQueryParams())
    ])
    directories.value = dirs ?? []
    noteList.value = (notes as PageResult<AdminNoteItem>).records ?? []
    noteTotal.value = (notes as PageResult<AdminNoteItem>).total ?? 0
  } finally {
    loadingWorkspace.value = false
  }
}

function searchUsers() {
  userPage.value = 1
  void fetchUsers()
}

function searchWorkspace() {
  notePage.value = 1
  void fetchWorkspace()
}

function changeUserPage(page: number) {
  if (page < 1 || page > userTotalPages.value || page === userPage.value) return
  userPage.value = page
  void fetchUsers()
}

function changeNotePage(page: number) {
  if (page < 1 || page > noteTotalPages.value || page === notePage.value) return
  notePage.value = page
  void fetchWorkspace()
}

async function enterUser(user: AdminUserItem) {
  selectedUser.value = user
  currentDirectoryId.value = null
  breadcrumbs.value = []
  notePage.value = 1
  filterTitle.value = ''
  filterStatus.value = ''
  await fetchWorkspace()
}

function backToUsers() {
  selectedUser.value = null
  directories.value = []
  noteList.value = []
  noteTotal.value = 0
  currentDirectoryId.value = null
  breadcrumbs.value = []
  selectedIds.value.clear()
}

async function goUserRoot() {
  currentDirectoryId.value = null
  breadcrumbs.value = []
  notePage.value = 1
  await fetchWorkspace()
}

async function enterDirectory(topic: AdminTopicItem) {
  currentDirectoryId.value = topic.id
  breadcrumbs.value.push(topic)
  notePage.value = 1
  filterTitle.value = ''
  await fetchWorkspace()
}

async function goBreadcrumb(index: number) {
  const target = breadcrumbs.value[index]
  breadcrumbs.value = breadcrumbs.value.slice(0, index + 1)
  currentDirectoryId.value = target.id
  notePage.value = 1
  await fetchWorkspace()
}

function toggleSelectAll(checked: boolean) {
  if (checked) noteList.value.forEach(n => selectedIds.value.add(n.id))
  else selectedIds.value.clear()
}

function toggleSelect(id: number) {
  selectedIds.value.has(id) ? selectedIds.value.delete(id) : selectedIds.value.add(id)
}

async function handleDelete(note: AdminNoteItem) {
  if (!await confirmAction({ content: `确定删除笔记「${note.title}」吗？`, danger: true })) return
  await adminApi.deleteNotes([note.id])
  await fetchWorkspace()
}

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) return
  if (!await confirmAction({ content: `确定删除已选择的 ${selectedIds.value.size} 篇笔记吗？`, danger: true })) return
  await adminApi.deleteNotes([...selectedIds.value])
  selectedIds.value.clear()
  await fetchWorkspace()
}

async function handleConvert(note: AdminNoteItem) {
  try {
    await adminApi.convertNote(note.id)
    await fetchWorkspace()
  } catch {
    alertWarning('转换失败，请确认笔记关联完整')
  }
}

async function handleViewSource(note: AdminNoteItem) {
  try {
    sourceContent.value = await adminApi.getNoteSource(note.id)
    sourceTitle.value = note.title
    showSourceModal.value = true
  } catch {
    alertWarning('无法获取源文件')
  }
}

function handleViewHtml(note: AdminNoteItem) {
  router.push(`/admin/notes/${note.id}`)
}

onMounted(fetchUsers)
</script>

<template>
  <div class="admin-directory-shell mx-auto max-w-[1400px] space-y-6 pb-24">
    <section class="admin-note-hero rounded-lg border border-white/10 p-6">
      <div class="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <div class="mb-3 flex flex-wrap items-center gap-2 text-xs font-bold text-slate-300">
            <button class="crumb" @click="backToUsers">
              <Users class="h-3.5 w-3.5" />
              <span>Users</span>
            </button>
            <template v-if="selectedUser">
              <ChevronRight class="h-3.5 w-3.5 text-slate-500" />
              <button class="crumb" @click="goUserRoot">{{ selectedUser.nickname || selectedUser.username }}</button>
              <template v-for="(crumbItem, index) in breadcrumbs" :key="crumbItem.id">
                <ChevronRight class="h-3.5 w-3.5 text-slate-500" />
                <button class="crumb" @click="goBreadcrumb(index)">{{ crumbItem.topicName }}</button>
              </template>
            </template>
          </div>
          <h1 class="text-2xl font-black tracking-normal text-white sm:text-3xl">
            {{ selectedUser ? currentDirectoryName : '全局笔记用户目录' }}
          </h1>
          <p class="mt-2 text-sm text-slate-300">
            {{ selectedUser ? '按用户目录逐层查看主题和笔记资产。' : '先选择一个用户，再进入该用户的目录和笔记。' }}
          </p>
        </div>

        <div v-if="!selectedUser" class="grid gap-3 sm:grid-cols-[7rem_minmax(0,1fr)_8rem_auto] lg:w-[42rem]">
          <input v-model="filterUserId" class="form-control" type="number" placeholder="UID" @keydown.enter="searchUsers" />
          <label class="relative block">
            <Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
            <input v-model="filterUsername" class="form-control pl-10" placeholder="搜索用户名" @keydown.enter="searchUsers" />
          </label>
          <select v-model="filterUserStatus" class="form-control" @change="searchUsers">
            <option value="">全部状态</option>
            <option value="1">正常</option>
            <option value="0">禁用</option>
            <option value="2">未激活</option>
          </select>
          <button class="action-button primary" @click="searchUsers">检索</button>
        </div>

        <div v-else class="grid gap-3 sm:grid-cols-[minmax(0,1fr)_8rem_auto] lg:w-[38rem]">
          <label class="relative block">
            <Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
            <input v-model="filterTitle" class="form-control pl-10" placeholder="搜索当前目录笔记" @keydown.enter="searchWorkspace" />
          </label>
          <select v-model="filterStatus" class="form-control" @change="searchWorkspace">
            <option value="">全部状态</option>
            <option :value="NoteStatusCode.NEW">已创建</option>
            <option :value="NoteStatusCode.PENDING_INFO">缺失信息</option>
            <option :value="NoteStatusCode.READY_TO_CONVERT">待转换</option>
            <option :value="NoteStatusCode.CONVERTED">已转换</option>
            <option :value="NoteStatusCode.PENDING_AUDIT">审核中</option>
            <option :value="NoteStatusCode.APPROVED">已通过</option>
            <option :value="NoteStatusCode.PUBLISHED">已公开</option>
            <option :value="NoteStatusCode.REJECTED">已拒绝</option>
          </select>
          <button class="action-button primary" @click="searchWorkspace">检索</button>
        </div>
      </div>
    </section>

    <template v-if="!selectedUser">
      <section v-if="loadingUsers" class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <div v-for="i in 8" :key="i" class="h-48 animate-pulse rounded-lg border border-white/10 bg-white/[0.03]" />
      </section>

      <section v-else-if="users.length === 0" class="rounded-lg border border-white/10 bg-white/[0.03] p-12 text-center">
        <Users class="mx-auto mb-4 h-10 w-10 text-slate-600" />
        <p class="text-sm font-bold text-slate-400">暂无用户数据</p>
      </section>

      <section v-else class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <article v-for="user in users" :key="user.id" class="float-card user-card">
          <div class="mb-4 flex items-start justify-between gap-3">
            <div class="flex h-11 w-11 items-center justify-center rounded-lg bg-rose-50 text-rose-700 ring-1 ring-rose-200">
              <Users class="h-5 w-5" />
            </div>
            <span class="rounded-md border px-2 py-1 text-[10px] font-black uppercase tracking-[0.12em]" :class="userStatusClass(user.status)">
              {{ userStatusText(user.status) }}
            </span>
          </div>
          <h2 class="line-clamp-1 text-lg font-black text-slate-950">{{ user.nickname || user.username }}</h2>
          <p class="mt-1 text-sm font-bold text-slate-500">@{{ user.username }} · UID {{ user.id }}</p>
          <div class="mt-4 grid grid-cols-2 gap-2">
            <div class="metric-pill">
              <FileText class="h-3.5 w-3.5" />
              <span>{{ user.noteCount ?? 0 }} 篇</span>
            </div>
            <div class="metric-pill">
              <Hash class="h-3.5 w-3.5" />
              <span>{{ roleText(user.roleId) }}</span>
            </div>
          </div>
          <div class="mt-auto border-t border-slate-200 pt-4">
            <div class="mb-3 flex items-center gap-2 text-xs text-slate-500">
              <HardDrive class="h-3.5 w-3.5" />
              <span>{{ formatBytes(user.usedStorageBytes) }} / {{ formatBytes(user.maxStorageBytes) }}</span>
            </div>
            <button class="card-link" @click="enterUser(user)">
              进入用户目录
              <ArrowRight class="h-3.5 w-3.5" />
            </button>
          </div>
        </article>
      </section>

      <div v-if="users.length > 0" class="flex items-center justify-between border-t border-white/10 pt-5">
        <span class="text-xs font-bold text-slate-400">共 {{ formatNumber(userTotal) }} 个用户</span>
        <div class="flex items-center gap-1">
          <button class="pager-button" :disabled="userPage <= 1" @click="changeUserPage(userPage - 1)"><ChevronLeft class="h-4 w-4" /></button>
          <button v-for="page in visiblePages(userPage, userTotalPages)" :key="page" class="pager-button text-xs font-black" :class="{ active: page === userPage }" @click="changeUserPage(page)">{{ page }}</button>
          <button class="pager-button" :disabled="userPage >= userTotalPages" @click="changeUserPage(userPage + 1)"><ChevronRight class="h-4 w-4" /></button>
        </div>
      </div>
    </template>

    <template v-else>
      <section class="grid gap-3 sm:grid-cols-3">
        <div class="summary-card">
          <FolderTree class="h-4 w-4 text-rose-600" />
          <span>{{ directories.length }}</span>
          <small>Directories</small>
        </div>
        <div class="summary-card">
          <FileText class="h-4 w-4 text-emerald-600" />
          <span>{{ noteTotal }}</span>
          <small>Notes Here</small>
        </div>
        <div class="summary-card">
          <Users class="h-4 w-4 text-amber-600" />
          <span>UID {{ selectedUser.id }}</span>
          <small>{{ selectedUser.username }}</small>
        </div>
      </section>

      <Transition name="batch-float">
        <div v-if="isBatchMode" class="batch-bar">
          <span class="text-sm font-black text-rose-700">已选择 {{ selectedIds.size }} 篇笔记</span>
          <button class="danger-button" @click="handleBatchDelete">
            <Trash2 class="h-3.5 w-3.5" />
            <span>批量删除</span>
          </button>
        </div>
      </Transition>

      <section v-if="loadingWorkspace" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div v-for="i in 8" :key="i" class="h-52 animate-pulse rounded-lg border border-white/10 bg-white/[0.03]" />
      </section>

      <section v-else-if="directories.length === 0 && noteList.length === 0" class="rounded-lg border border-white/10 bg-white/[0.03] p-12 text-center">
        <FolderTree class="mx-auto mb-4 h-10 w-10 text-slate-600" />
        <p class="text-sm font-bold text-slate-400">当前目录暂无内容</p>
      </section>

      <template v-else>
        <div v-if="noteList.length > 0" class="flex items-center justify-end">
          <label class="inline-flex items-center gap-2 text-xs font-bold text-slate-400">
            <input type="checkbox" class="admin-checkbox" :checked="selectedIds.size === noteList.length && noteList.length > 0" @change="toggleSelectAll(($event.target as HTMLInputElement).checked)" />
            <span>选择当前页全部笔记</span>
          </label>
        </div>

        <section class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <article v-for="dir in directories" :key="`dir-${dir.id}`" class="float-card directory-card">
            <button class="absolute inset-0 z-0" title="进入目录" @click="enterDirectory(dir)" />
            <div class="relative z-10 flex h-full flex-col">
              <div class="mb-4 flex items-start justify-between">
                <div class="flex h-11 w-11 items-center justify-center rounded-lg bg-rose-50 text-rose-700 ring-1 ring-rose-200">
                  <FolderTree class="h-5 w-5" />
                </div>
                <span class="rounded-md border px-2 py-1 text-[10px] font-black uppercase tracking-[0.12em]" :class="topicStatus(dir).cls">
                  {{ topicStatus(dir).label }}
                </span>
              </div>
              <h2 class="line-clamp-2 text-lg font-black leading-snug text-slate-950">{{ dir.topicName }}</h2>
              <p class="mt-2 text-sm text-slate-600">{{ dir.noteCount ?? 0 }} 篇直接挂载笔记</p>
              <div class="mt-auto border-t border-slate-200 pt-4">
                <button class="card-link" @click.stop="enterDirectory(dir)">
                  进入
                  <ArrowRight class="h-3.5 w-3.5" />
                </button>
              </div>
            </div>
          </article>

          <article v-for="note in noteList" :key="`note-${note.id}`" class="float-card note-card">
            <div class="mb-4 flex items-start justify-between gap-3">
              <label class="relative z-10">
                <input type="checkbox" class="admin-checkbox" :checked="selectedIds.has(note.id)" @change="toggleSelect(note.id)" />
              </label>
              <span class="inline-flex items-center gap-1 rounded-md border px-2 py-1 text-[10px] font-black uppercase tracking-[0.12em]" :class="getNoteStatusInfo(note.status).cls">
                <component :is="getStatusIcon(getNoteStatusInfo(note.status).icon)" class="h-3 w-3" />
                {{ getNoteStatusInfo(note.status).label }}
              </span>
            </div>

            <div class="mb-3 flex h-11 w-11 items-center justify-center rounded-lg bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200">
              <FileText class="h-5 w-5" />
            </div>
            <h2 class="line-clamp-2 text-lg font-black leading-snug text-slate-950">{{ note.title }}</h2>
            <p class="mt-2 line-clamp-2 min-h-10 text-sm leading-5 text-slate-600">{{ note.description || '这篇笔记还没有描述。' }}</p>

            <div class="mt-4 flex flex-wrap gap-2 text-[10px] font-bold uppercase tracking-[0.12em]">
              <span class="mini-chip">{{ formatBytes(note.mdFileSize) }}</span>
              <span class="mini-chip">{{ formatDate(note.createTime) }}</span>
              <span v-if="note.missingCount > 0" class="mini-chip danger">缺 {{ note.missingCount }} 项</span>
            </div>

            <div class="mt-auto border-t border-slate-200 pt-4">
              <div class="mb-3 flex items-center justify-between text-xs text-slate-500">
                <span>#{{ note.id }}</span>
                <span>{{ note.topicName || '未分类' }}</span>
              </div>
              <div class="flex flex-wrap gap-2">
                <button class="icon-button" title="查看源文件" @click="handleViewSource(note)"><Eye class="h-3.5 w-3.5" /></button>
                <button v-if="note.status >= NoteStatusCode.CONVERTED" class="icon-button" title="查看 HTML" @click="handleViewHtml(note)"><FileCode class="h-3.5 w-3.5" /></button>
                <button v-if="note.status === NoteStatusCode.READY_TO_CONVERT || note.status === NoteStatusCode.REJECTED" class="icon-button" title="转换笔记" @click="handleConvert(note)"><RefreshCw class="h-3.5 w-3.5" /></button>
                <button class="icon-button danger" title="删除" @click="handleDelete(note)"><Trash2 class="h-3.5 w-3.5" /></button>
              </div>
            </div>
          </article>
        </section>

        <div v-if="noteList.length > 0" class="flex items-center justify-between border-t border-white/10 pt-5">
          <span class="text-xs font-bold text-slate-400">共 {{ formatNumber(noteTotal) }} 篇笔记</span>
          <div class="flex items-center gap-1">
            <button class="pager-button" :disabled="notePage <= 1" @click="changeNotePage(notePage - 1)"><ChevronLeft class="h-4 w-4" /></button>
            <button v-for="page in visiblePages(notePage, noteTotalPages)" :key="page" class="pager-button text-xs font-black" :class="{ active: page === notePage }" @click="changeNotePage(page)">{{ page }}</button>
            <button class="pager-button" :disabled="notePage >= noteTotalPages" @click="changeNotePage(notePage + 1)"><ChevronRight class="h-4 w-4" /></button>
          </div>
        </div>
      </template>
    </template>

    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showSourceModal" class="fixed inset-0 z-[60] bg-black/85 backdrop-blur-md" @click="showSourceModal = false" />
      </Transition>
      <Transition name="modal">
        <div v-if="showSourceModal" class="fixed inset-0 z-[60] flex items-center justify-center px-4 pointer-events-none">
          <div class="modal-card pointer-events-auto flex max-h-[85vh] w-full max-w-3xl flex-col overflow-hidden rounded-lg border border-white/10 bg-[#050816] shadow-2xl">
            <div class="flex items-center justify-between border-b border-white/10 px-5 py-4">
              <h3 class="truncate text-sm font-black text-white">{{ sourceTitle }}</h3>
              <button class="modal-close" @click="showSourceModal = false"><X class="h-4 w-4" /></button>
            </div>
            <pre class="flex-1 overflow-y-auto p-5 text-sm leading-6 text-slate-300 whitespace-pre-wrap">{{ sourceContent }}</pre>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.admin-note-hero {
  background:
    radial-gradient(circle at top left, rgba(244, 63, 94, 0.15), transparent 34rem),
    linear-gradient(135deg, rgba(255, 255, 255, 0.045), rgba(255, 255, 255, 0.015));
}

.crumb,
.action-button,
.card-link,
.icon-button,
.pager-button,
.danger-button,
.modal-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.18s ease;
}

.crumb {
  gap: 0.35rem;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.035);
  padding: 0.35rem 0.65rem;
  color: #cbd5e1;
}

.crumb:hover {
  border-color: rgba(251, 113, 133, 0.42);
  color: #fecdd3;
}

.form-control {
  height: 2.75rem;
  width: 100%;
  border-radius: 0.5rem;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(0, 0, 0, 0.22);
  padding: 0 0.75rem;
  color: white;
  font-size: 0.875rem;
  outline: none;
}

.form-control::placeholder {
  color: #94a3b8;
}

.form-control:focus {
  border-color: rgba(251, 113, 133, 0.48);
  box-shadow: 0 0 0 4px rgba(244, 63, 94, 0.12);
}

.action-button {
  height: 2.75rem;
  gap: 0.45rem;
  border-radius: 0.5rem;
  padding: 0 1rem;
  font-size: 0.875rem;
  font-weight: 900;
}

.action-button.primary {
  background: #f43f5e;
  color: white;
}

.action-button.primary:hover {
  background: #fb7185;
}

.summary-card,
.float-card {
  border: 1px solid rgba(226, 232, 240, 0.95);
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.94)),
    #f8fafc;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.14);
}

.summary-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  border-radius: 0.5rem;
  padding: 1rem;
}

.summary-card span {
  color: #0f172a;
  font-size: 1.05rem;
  font-weight: 900;
}

.summary-card small {
  margin-left: auto;
  color: #64748b;
  font-size: 0.65rem;
  font-weight: 900;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.float-card {
  position: relative;
  min-height: 14rem;
  overflow: hidden;
  border-radius: 0.5rem;
  padding: 1.1rem;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.float-card:hover {
  transform: translateY(-3px);
  border-color: rgba(251, 113, 133, 0.45);
  box-shadow: 0 22px 52px rgba(15, 23, 42, 0.18);
}

.user-card,
.note-card,
.directory-card > div {
  display: flex;
  flex-direction: column;
}

.metric-pill,
.mini-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  border-radius: 999px;
  border: 1px solid rgba(203, 213, 225, 0.9);
  background: rgba(241, 245, 249, 0.9);
  color: #475569;
  padding: 0.35rem 0.6rem;
  font-size: 0.72rem;
  font-weight: 800;
}

.mini-chip.danger {
  border-color: rgba(244, 63, 94, 0.25);
  background: #fff1f2;
  color: #be123c;
}

.card-link {
  gap: 0.35rem;
  color: #e11d48;
  font-size: 0.78rem;
  font-weight: 900;
}

.icon-button,
.pager-button,
.modal-close {
  height: 2rem;
  min-width: 2rem;
  border-radius: 0.45rem;
  border: 1px solid rgba(203, 213, 225, 0.9);
  background: rgba(248, 250, 252, 0.86);
  color: #475569;
}

.icon-button:hover,
.pager-button:hover,
.pager-button.active,
.modal-close:hover {
  border-color: rgba(244, 63, 94, 0.42);
  background: #fff1f2;
  color: #be123c;
}

.icon-button.danger:hover,
.danger-button:hover {
  border-color: rgba(244, 63, 94, 0.55);
  background: #e11d48;
  color: white;
}

.pager-button:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.admin-checkbox {
  appearance: none;
  height: 1rem;
  width: 1rem;
  cursor: pointer;
  border-radius: 0.25rem;
  border: 1px solid rgba(148, 163, 184, 0.75);
  background: white;
  transition: all 0.18s ease;
}

.admin-checkbox:checked {
  border-color: #e11d48;
  background: #e11d48;
  box-shadow: inset 0 0 0 3px white;
}

.batch-bar {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-radius: 0.75rem;
  border: 1px solid rgba(251, 113, 133, 0.24);
  background: rgba(255, 241, 242, 0.96);
  padding: 0.8rem 1rem;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.18);
}

.danger-button {
  gap: 0.4rem;
  border-radius: 0.5rem;
  border: 1px solid rgba(244, 63, 94, 0.28);
  background: white;
  padding: 0.45rem 0.75rem;
  color: #be123c;
  font-size: 0.78rem;
  font-weight: 900;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.22s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.24s ease, transform 0.28s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
  transform: translateY(14px) scale(0.97);
}

@keyframes batch-slide-up {
  0% { opacity: 0; transform: translateY(18px) scale(0.98); }
  100% { opacity: 1; transform: translateY(0) scale(1); }
}

.batch-float-enter-active .batch-bar {
  animation: batch-slide-up 0.28s ease both;
}

.batch-float-enter-from,
.batch-float-leave-to {
  opacity: 0;
}
</style>
