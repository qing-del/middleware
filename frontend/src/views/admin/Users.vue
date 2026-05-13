<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import type { AdminUserItem, PageResult, UserModifyParams } from '@/api/admin'
import { Users, Search, Trash2, Loader2, X, ChevronLeft, ChevronRight, Plus, Pencil, ShieldBan, ShieldCheck } from 'lucide-vue-next'

const loading = ref(true)
const userList = ref<AdminUserItem[]>([])
const total = ref(0)
const filterId = ref(''); const filterUsername = ref('')
const filterStatus = ref(''); const filterRoleId = ref('')
const currentPage = ref(1); const pageSize = ref(15)
const selectedIds = ref<Set<number>>(new Set())

// 模态框
const modalVisible = ref(false); const modalMode = ref<'create' | 'edit'>('create')
const editingUserId = ref<number | null>(null)
const form = ref({ username: '', password: '', nickname: '', email: '', roleId: 3, status: 1 })
const submitting = ref(false)

const isBatchMode = computed(() => selectedIds.value.size > 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function roleLabel(roleId: number): string { const m: Record<number,string>={1:'Creator',2:'Admin',3:'User',4:'VIP'}; return m[roleId]||'Unknown' }
function statusLabel(status: number): string { const m: Record<number,string>={0:'禁用',1:'正常',2:'未激活'}; return m[status]||'Unknown' }

function formatBytes(bytes: number): string { if(!bytes||bytes===0)return '0 B'; const u=['B','KB','MB','GB']; const i=Math.floor(Math.log(bytes)/Math.log(1024)); return (bytes/Math.pow(1024,i)).toFixed(i>0?1:0)+' '+u[i] }
function formatDate(raw: string): string { if(!raw)return '-'; const d=new Date(raw); const p=(n:number)=>String(n).padStart(2,'0'); return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}` }
function formatNumber(n: number): string { return n.toLocaleString() }
function visiblePages(): number[] { const p:number[]=[],tp=totalPages.value,cp=currentPage.value; let s=Math.max(1,cp-2); const e=Math.min(tp,s+4); if(e-s<4)s=Math.max(1,e-4); for(let i=s;i<=e;i++)p.push(i); return p }

async function fetchUsers() {
  try {
    const res = await adminApi.getUserList({
      id: filterId.value?Number(filterId.value):undefined, username: filterUsername.value||undefined,
      status: filterStatus.value?Number(filterStatus.value):undefined, roleId: filterRoleId.value?Number(filterRoleId.value):undefined,
      pageNum: currentPage.value, pageSize: pageSize.value
    }); userList.value = (res as unknown as PageResult<AdminUserItem>).records??[]; total.value = (res as unknown as PageResult<AdminUserItem>).total??0
  } finally { loading.value = false }
}
function handleSearch() { currentPage.value=1; loading.value=true; fetchUsers() }
function handlePageChange(page: number) { if(page<1||page>totalPages.value||page===currentPage.value)return; currentPage.value=page; loading.value=true; fetchUsers() }
function toggleSelectAll(checked: boolean) { if(checked)userList.value.forEach(u=>selectedIds.value.add(u.id)); else selectedIds.value.clear() }
function toggleSelect(id: number) { selectedIds.value.has(id)?selectedIds.value.delete(id):selectedIds.value.add(id) }

function openCreateModal() { modalMode.value='create'; editingUserId.value=null; form.value={username:'',password:'',nickname:'',email:'',roleId:3,status:1}; modalVisible.value=true }
function openEditModal(user: AdminUserItem) { modalMode.value='edit'; editingUserId.value=user.id; form.value={username:user.username,password:'',nickname:user.nickname||'',email:user.email||'',roleId:user.roleId,status:user.status}; modalVisible.value=true }
function closeModal() { modalVisible.value=false }

async function handleSubmit() {
  if(!form.value.username.trim())return
  submitting.value=true
  try {
    if(modalMode.value==='create') {
      await adminApi.createUser({...form.value, username: form.value.username.trim()})
    } else if(editingUserId.value) {
      const d: UserModifyParams = {id:editingUserId.value, username:form.value.username.trim(), nickname:form.value.nickname||undefined, email:form.value.email||undefined, roleId:form.value.roleId, status:form.value.status}
      if(form.value.password)d.newPassword=form.value.password; if(form.value.password)d.confirmPassword=form.value.password
      await adminApi.modifyUser(d)
    }
    closeModal(); await fetchUsers()
  } finally { submitting.value=false }
}

async function handleDelete(id: number) { if(!confirm('确定删除该用户吗？'))return; await adminApi.deleteUsers([id]); await fetchUsers() }
async function handleBatchDelete() { if(selectedIds.value.size===0)return; if(!confirm(`确定删除${selectedIds.value.size}个用户吗？`))return; await adminApi.deleteUsers([...selectedIds.value]); selectedIds.value.clear(); await fetchUsers() }
async function handleToggleStatus(user: AdminUserItem) { const newStatus = user.status===1?0:1; await adminApi.setUserStatus(newStatus,{id:user.id}); await fetchUsers() }

onMounted(() => { fetchUsers() })
</script>

<template>
  <div class="relative max-w-7xl mx-auto space-y-6">
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 relative z-10">
      <div class="flex items-center space-x-3">
        <div class="p-2 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400"><Users class="w-5 h-5" /></div>
        <div><h2 class="text-xl font-bold text-white">用户管理</h2><p class="text-xs text-slate-400 mt-0.5">管理平台所有注册用户</p></div>
      </div>
      <div class="flex items-center space-x-2 flex-wrap gap-y-2">
        <input v-model="filterId" type="number" placeholder="UID..." class="w-20 bg-black/20 border border-white/10 rounded-xl py-2 px-2 text-xs text-white placeholder:text-slate-500 outline-none focus:border-rose-500/50 h-9" @keyup.enter="handleSearch" />
        <select v-model="filterStatus" class="bg-black/20 border border-white/10 rounded-xl py-2 px-2 text-xs text-white outline-none focus:border-rose-500/50 h-9" @change="handleSearch">
          <option value="">状态:全部</option><option value="1">正常</option><option value="0">禁用</option><option value="2">未激活</option>
        </select>
        <select v-model="filterRoleId" class="bg-black/20 border border-white/10 rounded-xl py-2 px-2 text-xs text-white outline-none focus:border-rose-500/50 h-9" @change="handleSearch">
          <option value="">角色:全部</option><option value="1">Creator</option><option value="2">Admin</option><option value="3">User</option><option value="4">VIP</option>
        </select>
        <div class="relative group flex items-center bg-black/20 border border-white/10 rounded-xl overflow-hidden transition-all duration-300 ease-out w-9 hover:w-28 focus-within:!w-48 focus-within:bg-black/40 focus-within:border-rose-500/50 h-9">
          <label class="w-9 h-full flex-shrink-0 flex items-center justify-center text-slate-500 group-focus-within:text-rose-400 cursor-pointer z-10"><Search class="w-4 h-4" /></label>
          <input v-model="filterUsername" type="text" placeholder="用户名..." class="absolute left-9 w-[150px] h-full bg-transparent text-sm text-white placeholder:text-slate-500 outline-none opacity-0 group-hover:opacity-100 focus-within:!opacity-100 transition-opacity duration-300 pr-4" @keyup.enter="handleSearch" />
        </div>
        <button class="group relative px-3 py-2 bg-rose-600 hover:bg-rose-500 text-white text-sm font-bold rounded-xl shadow-[0_0_15px_rgba(244,63,94,0.4)] transition-all flex items-center space-x-1.5" @click="openCreateModal"><Plus class="w-4 h-4" /><span>新建用户</span></button>
      </div>
    </div>

    <div class="glass-panel rounded-xl px-4 py-3 flex items-center justify-between transition-all duration-300 relative z-10" :class="isBatchMode?'opacity-100':'opacity-0 pointer-events-none -translate-y-[10px]'">
      <span class="text-sm font-bold text-rose-300">已选择 <span class="text-white mx-1">{{ selectedIds.size }}</span> 个用户</span>
      <button class="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-rose-500/10 text-rose-400 hover:bg-rose-500 hover:text-white transition-all text-xs font-bold border border-rose-500/20" @click="handleBatchDelete"><Trash2 class="w-3.5 h-3.5" /><span>批量删除</span></button>
    </div>

    <div class="glass-panel rounded-2xl overflow-hidden border border-white/10 relative z-10">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse">
          <thead><tr>
            <th class="px-3 py-4 border-b border-white/5 w-10"><input type="checkbox" class="glass-checkbox" :checked="selectedIds.size===userList.length&&userList.length>0" @change="toggleSelectAll(($event.target as HTMLInputElement).checked)" /></th>
            <th class="px-3 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">ID</th>
            <th class="px-3 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">用户名</th>
            <th class="px-3 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">昵称</th>
            <th class="px-3 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">邮箱</th>
            <th class="px-3 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">角色</th>
            <th class="px-3 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">状态</th>
            <th class="px-3 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">存储</th>
            <th class="px-3 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider">创建时间</th>
            <th class="px-3 py-4 border-b border-white/5 text-xs font-bold text-slate-400 uppercase tracking-wider text-right">操作</th>
          </tr></thead>
          <tbody class="divide-y divide-white/5">
            <tr v-if="loading"><td colspan="10" class="px-6 py-16 text-center"><Loader2 class="w-6 h-6 text-rose-400 animate-spin mx-auto mb-3" /><span class="text-xs text-slate-500">加载中...</span></td></tr>
            <tr v-else-if="userList.length===0"><td colspan="10" class="px-6 py-16 text-center text-sm text-slate-500">暂无用户数据</td></tr>
            <tr v-for="user in userList" :key="user.id" class="hover:bg-white/[0.02] transition-colors group">
              <td class="px-3 py-3"><input type="checkbox" class="glass-checkbox" :checked="selectedIds.has(user.id)" @change="toggleSelect(user.id)" /></td>
              <td class="px-3 py-3 text-xs text-slate-500 font-mono">{{ user.id }}</td>
              <td class="px-3 py-3"><span class="text-sm font-bold text-slate-200">{{ user.username }}</span></td>
              <td class="px-3 py-3 text-xs text-slate-400">{{ user.nickname||'-' }}</td>
              <td class="px-3 py-3 text-xs text-slate-400 truncate max-w-[120px]">{{ user.email||'-' }}</td>
              <td class="px-3 py-3"><span class="text-[10px] font-bold uppercase px-2 py-0.5 rounded border" :class="user.roleId<=2?'text-rose-400 bg-rose-500/10 border-rose-500/20':'text-slate-400 bg-slate-500/10 border-slate-500/20'">{{ roleLabel(user.roleId) }}</span></td>
              <td class="px-3 py-3"><span class="text-[10px] font-bold uppercase px-2 py-0.5 rounded border" :class="user.status===1?'text-emerald-400 bg-emerald-500/10 border-emerald-500/20':user.status===0?'text-rose-400 bg-rose-500/10 border-rose-500/20':'text-amber-400 bg-amber-500/10 border-amber-500/20'">{{ statusLabel(user.status) }}</span></td>
              <td class="px-3 py-3 text-xs text-slate-500">{{ formatBytes(user.usedStorageBytes) }} / {{ formatBytes(user.maxStorageBytes) }}</td>
              <td class="px-3 py-3 text-xs text-slate-500">{{ formatDate(user.createTime) }}</td>
              <td class="px-3 py-3 text-right">
                <div class="flex items-center justify-end space-x-1.5 opacity-50 group-hover:opacity-100 transition-opacity">
                  <button class="w-7 h-7 rounded bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-colors" :title="user.status===1?'封禁':'解封'" @click="handleToggleStatus(user)">
                    <ShieldBan v-if="user.status===1" class="w-3.5 h-3.5" /><ShieldCheck v-else class="w-3.5 h-3.5" />
                  </button>
                  <button class="w-7 h-7 rounded bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-colors" title="编辑" @click="openEditModal(user)"><Pencil class="w-3.5 h-3.5" /></button>
                  <button class="w-7 h-7 rounded bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-400 flex items-center justify-center transition-colors" title="删除" @click="handleDelete(user.id)"><Trash2 class="w-3.5 h-3.5" /></button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!loading&&userList.length>0" class="px-6 py-4 border-t border-white/5 flex items-center justify-between bg-white/[0.01]">
        <span class="text-xs text-slate-500">共 {{ formatNumber(total) }} 个用户</span>
        <div class="flex items-center space-x-1">
          <button class="w-7 h-7 rounded flex items-center justify-center text-slate-500 hover:bg-white/5 hover:text-white disabled:opacity-50" :disabled="currentPage<=1" @click="handlePageChange(currentPage-1)"><ChevronLeft class="w-4 h-4" /></button>
          <template v-for="page in visiblePages()" :key="page"><button v-if="totalPages>1" class="w-7 h-7 rounded flex items-center justify-center text-xs font-bold transition-colors" :class="page===currentPage?'bg-rose-500/20 text-rose-400 border border-rose-500/30':'text-slate-400 hover:bg-white/5 hover:text-white'" @click="handlePageChange(page)">{{ page }}</button></template>
          <button class="w-7 h-7 rounded flex items-center justify-center text-slate-500 hover:bg-white/5 hover:text-white" :disabled="currentPage>=totalPages" @click="handlePageChange(currentPage+1)"><ChevronRight class="w-4 h-4" /></button>
        </div>
      </div>
    </div>

    <!-- 创建/编辑用户 Modal -->
    <Teleport to="body">
      <div v-if="modalVisible" class="fixed inset-0 z-50 flex items-center justify-center">
        <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="closeModal"></div>
        <div class="glass-panel w-full max-w-lg rounded-3xl p-8 relative z-10 transform transition-all duration-300" :class="modalVisible?'scale-100':'scale-95'">
          <div class="absolute -top-10 -right-10 w-32 h-32 bg-rose-500/20 blur-[40px] rounded-full pointer-events-none"></div>
          <div class="flex justify-between items-center mb-6"><h3 class="text-xl font-bold text-white">{{ modalMode==='create'?'新建用户':'编辑用户' }}</h3><button class="text-slate-500 hover:text-white" @click="closeModal"><X class="w-5 h-5" /></button></div>
          <form class="space-y-4" @submit.prevent="handleSubmit">
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-bold text-slate-400 uppercase tracking-widest mb-1.5">用户名 <span class="text-rose-500">*</span></label>
                <input v-model="form.username" type="text" required class="w-full bg-black/20 border border-white/[0.05] rounded-xl py-2.5 px-3 outline-none focus:border-rose-500/50 focus:ring-2 focus:ring-rose-500/10 transition-all text-sm text-white placeholder:text-slate-600" />
              </div>
              <div>
                <label class="block text-xs font-bold text-slate-400 uppercase tracking-widest mb-1.5">密码{{ modalMode==='edit'?' (留空不修改)':'' }} <span v-if="modalMode==='create'" class="text-rose-500">*</span></label>
                <input v-model="form.password" type="password" :required="modalMode==='create'" class="w-full bg-black/20 border border-white/[0.05] rounded-xl py-2.5 px-3 outline-none focus:border-rose-500/50 transition-all text-sm text-white" />
              </div>
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-bold text-slate-400 uppercase tracking-widest mb-1.5">昵称</label>
                <input v-model="form.nickname" type="text" class="w-full bg-black/20 border border-white/[0.05] rounded-xl py-2.5 px-3 outline-none focus:border-rose-500/50 transition-all text-sm text-white" />
              </div>
              <div>
                <label class="block text-xs font-bold text-slate-400 uppercase tracking-widest mb-1.5">邮箱</label>
                <input v-model="form.email" type="email" class="w-full bg-black/20 border border-white/[0.05] rounded-xl py-2.5 px-3 outline-none focus:border-rose-500/50 transition-all text-sm text-white" />
              </div>
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs font-bold text-slate-400 uppercase tracking-widest mb-1.5">角色</label>
                <select v-model="form.roleId" class="w-full bg-black/20 border border-white/[0.05] rounded-xl py-2.5 px-3 outline-none focus:border-rose-500/50 transition-all text-sm text-white"><option :value="1">Creator</option><option :value="2">Admin</option><option :value="3">User</option><option :value="4">VIP</option></select>
              </div>
              <div>
                <label class="block text-xs font-bold text-slate-400 uppercase tracking-widest mb-1.5">状态</label>
                <select v-model="form.status" class="w-full bg-black/20 border border-white/[0.05] rounded-xl py-2.5 px-3 outline-none focus:border-rose-500/50 transition-all text-sm text-white"><option :value="1">正常</option><option :value="0">禁用</option><option :value="2">未激活</option></select>
              </div>
            </div>
            <div class="pt-4 flex justify-end space-x-3">
              <button type="button" class="px-5 py-2.5 rounded-xl text-sm font-bold text-slate-400 hover:text-white hover:bg-white/5 transition-colors" @click="closeModal">取消</button>
              <button type="submit" class="px-5 py-2.5 bg-rose-600 hover:bg-rose-500 text-white text-sm font-bold rounded-xl shadow-[0_0_15px_rgba(244,63,94,0.4)] transition-all flex items-center space-x-2" :disabled="submitting"><Loader2 v-if="submitting" class="w-4 h-4 animate-spin" /><span>{{ submitting?'保存中...':'确认保存' }}</span></button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.glass-panel { background: rgba(255,255,255,0.02); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border: 1px solid rgba(255,255,255,0.05); box-shadow: inset 0 1px 1px rgba(255,255,255,0.05); }
.glass-checkbox { appearance: none; width: 16px; height: 16px; border: 1px solid rgba(255,255,255,0.2); border-radius: 4px; background: rgba(0,0,0,0.2); cursor: pointer; position: relative; transition: all 0.2s; }
.glass-checkbox:checked { background: #f43f5e; border-color: #f43f5e; box-shadow: 0 0 10px rgba(244,63,94,0.4); }
.glass-checkbox:checked::after { content: ''; position: absolute; left: 5px; top: 2px; width: 4px; height: 8px; border: solid white; border-width: 0 2px 2px 0; transform: rotate(45deg); }
</style>
