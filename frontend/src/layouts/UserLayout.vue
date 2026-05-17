<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  ChevronLeft, Zap, LayoutDashboard, FileText, Layers, Hash,
  Image as ImageIcon, LogOut, Search, Bell, CheckCircle2
} from 'lucide-vue-next'

const route = useRoute()
const authStore = useAuthStore()

const isCollapsed = ref(false)
const isLoading = ref(true)

const menuItems = computed(() => [
  { id: 'dashboard', label: '工作台', icon: LayoutDashboard, to: '/user/dashboard' },
  { id: 'notes', label: '我的笔记', icon: FileText, to: '/user/notes' },
  { id: 'topics', label: '主题管理', icon: Layers, to: '/user/topics' },
  { id: 'tags', label: '标签生态', icon: Hash, to: '/user/tags' },
  { id: 'images', label: '图床画廊', icon: ImageIcon, to: '/user/images' }
])

function isMenuActive(id: string): boolean {
  return route.path.startsWith(`/user/${id}`) || (id === 'dashboard' && route.path === '/user')
}

function toggleSidebar() {
  isCollapsed.value = !isCollapsed.value
}

function getRoleBadgeClass(roleId: number) {
  if (roleId === 1 || roleId === 2) {
    return 'text-[9px] font-bold uppercase tracking-widest px-2 py-[2px] rounded-full mt-0.5 border border-rose-500/30 text-rose-400 bg-rose-500/10 shadow-[0_0_10px_rgba(244,63,94,0.2)]'
  }
  return 'text-[9px] font-bold uppercase tracking-widest px-2 py-[2px] rounded-full mt-0.5 border border-indigo-500/30 text-indigo-400 bg-indigo-500/10'
}

function getRoleText(roleId: number) {
  if (roleId === 1 || roleId === 2) return 'Administrator'
  if (roleId === 3) return 'User'
  if (roleId === 4) return 'VIP User'
  return 'Unknown'
}

function handleLogout() {
  authStore.logout()
}

onMounted(async () => {
  try {
    await authStore.fetchUserInfo()
  } catch (error) {
    console.error('获取用户信息失败', error)
  } finally {
    isLoading.value = false
  }
})
</script>

<template>
  <div class="flex h-screen w-full overflow-hidden selection:bg-indigo-500/30 tracking-tight">

    <!-- 全局环境光晕 -->
    <div class="fixed top-[-10%] right-[-5%] w-[500px] h-[500px] bg-indigo-600/10 blur-[150px] rounded-full pointer-events-none z-0"></div>
    <div class="fixed bottom-[-10%] left-[-5%] w-[400px] h-[400px] bg-purple-600/10 blur-[120px] rounded-full pointer-events-none z-0 animate-blob animation-delay-2000"></div>

    <!-- 左侧菜单栏 -->
    <aside :class="['h-full glass-panel border-r border-white/5 flex flex-col relative z-20 flex-shrink-0 transition-all duration-300 ease-[cubic-bezier(0.4,0,0.2,1)]', isCollapsed ? 'w-20' : 'w-64']">

      <!-- 折叠按钮 -->
      <button @click="toggleSidebar" class="absolute -right-3 top-6 w-6 h-6 rounded-full bg-[#020617] border border-white/10 flex items-center justify-center text-slate-400 hover:text-indigo-400 hover:border-indigo-500/50 hover:shadow-[0_0_10px_rgba(99,102,241,0.3)] transition-all z-30 cursor-pointer">
        <ChevronLeft :class="['w-3 h-3 transition-transform duration-300', isCollapsed ? 'rotate-180' : '']" />
      </button>

      <!-- Logo 区域 -->
      <div class="h-16 flex items-center px-6 border-b border-white/5 overflow-hidden whitespace-nowrap">
        <div class="w-8 h-8 rounded-lg bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center shadow-[0_0_15px_rgba(99,102,241,0.4)] flex-shrink-0">
          <Zap class="text-white w-4 h-4" />
        </div>
        <h1 :class="['text-xl font-black tracking-tighter text-transparent bg-clip-text bg-[linear-gradient(110deg,#818cf8,45%,#e879f9,55%,#818cf8)] animate-shine ml-2 transition-all duration-300 whitespace-nowrap', isCollapsed ? 'opacity-0 w-0 overflow-hidden' : '']">
          CORE<span class="text-white font-light">NODE</span>
        </h1>
      </div>

      <!-- 菜单区 -->
      <nav class="flex-1 overflow-y-auto py-6 px-3 space-y-1">
        <template v-if="isLoading">
          <div class="h-12 w-full skeleton rounded-xl mb-1.5"></div>
          <div class="h-12 w-full skeleton rounded-xl mb-1.5"></div>
          <div class="h-12 w-full skeleton rounded-xl mb-1.5"></div>
        </template>
        <template v-else>
          <router-link v-for="item in menuItems" :key="item.id" :to="item.to" :class="['glass-nav-item flex items-center px-4 py-3 rounded-xl mb-1.5 overflow-hidden whitespace-nowrap group', isMenuActive(item.id) ? 'glass-nav-active' : 'text-slate-400']">
            <component :is="item.icon" :class="['w-5 h-5 flex-shrink-0 relative z-10', isMenuActive(item.id) ? '' : 'group-hover:scale-110 transition-transform']" />
            <span :class="['menu-label text-sm font-medium tracking-wide relative z-10 ml-3 transition-all duration-300 whitespace-nowrap', isCollapsed ? 'opacity-0 w-0 overflow-hidden' : '']">{{ item.label }}</span>
          </router-link>
        </template>
      </nav>

      <!-- 底部退出按钮 -->
      <div class="p-4 border-t border-white/5 overflow-hidden">
        <button @click="handleLogout" class="glass-logout-btn flex items-center px-4 py-3 rounded-xl text-slate-400 hover:text-rose-400 group whitespace-nowrap w-full">
          <LogOut class="w-5 h-5 flex-shrink-0 transition-transform group-hover:-translate-x-1 relative z-10" />
          <span :class="['text-sm font-medium tracking-wide relative z-10 ml-3 transition-all duration-300 whitespace-nowrap', isCollapsed ? 'opacity-0 w-0 overflow-hidden' : '']">断开连接 (Logout)</span>
        </button>
      </div>
    </aside>

    <!-- 右侧主体 -->
    <div class="flex-1 flex flex-col relative z-10 min-w-0">

      <!-- 顶部导航 -->
      <header class="h-16 glass-panel border-b border-white/5 flex items-center justify-between px-8 z-20 shrink-0">
        <div class="flex items-center space-x-4">
          <div class="flex flex-col">
            <span class="text-sm font-bold text-slate-200">控制台大盘</span>
            <span class="text-[10px] text-slate-500 uppercase tracking-widest font-semibold mt-0.5">Dashboard Overview</span>
          </div>
        </div>

        <div class="flex items-center space-x-6">
          <!-- 搜索框 -->
          <div class="flex items-center space-x-3">
            <div class="group relative flex items-center h-8 rounded-full hover:bg-indigo-500/10 transition-all duration-300 ease-out overflow-hidden w-8 hover:w-36 cursor-pointer border border-transparent hover:border-indigo-500/30">
              <div class="w-8 h-8 flex-shrink-0 flex items-center justify-center text-slate-400 group-hover:text-indigo-400 transition-colors z-10 relative">
                <Search class="w-4 h-4" />
              </div>
              <span class="text-[11px] font-bold tracking-widest uppercase text-indigo-300/80 absolute left-8 opacity-0 group-hover:opacity-100 transition-opacity duration-300 delay-75 whitespace-nowrap">
                Quick Search
              </span>
            </div>

            <!-- 通知 -->
            <div class="relative group/bell">
              <button class="w-8 h-8 rounded-full flex items-center justify-center text-slate-400 group-hover/bell:text-indigo-400 group-hover/bell:bg-indigo-500/10 group-hover/bell:border-indigo-500/30 border border-transparent transition-all relative z-10 cursor-pointer">
                <Bell class="w-4 h-4 group-hover/bell:rotate-12 transition-transform" />
                <span class="absolute top-1.5 right-1.5 w-1.5 h-1.5 bg-rose-500 rounded-full animate-pulse shadow-[0_0_8px_rgba(244,63,94,0.8)]"></span>
              </button>

              <div class="absolute top-full right-0 pt-3 opacity-0 translate-y-3 pointer-events-none group-hover/bell:opacity-100 group-hover/bell:translate-y-0 group-hover/bell:pointer-events-auto transition-all duration-300 ease-out z-50 origin-top-right w-72">
                <div class="glass-panel rounded-2xl p-2 shadow-2xl border border-white/10 relative overflow-hidden">
                  <div class="absolute -top-10 -right-10 w-24 h-24 bg-indigo-500/20 blur-2xl rounded-full pointer-events-none"></div>

                  <div class="flex items-center justify-between px-3 py-2 border-b border-white/5 mb-1 relative z-10">
                    <span class="text-[11px] font-bold text-slate-300 uppercase tracking-wider">Notifications</span>
                    <span class="text-[9px] text-indigo-400 bg-indigo-500/10 px-1.5 py-0.5 rounded font-black tracking-widest">2 NEW</span>
                  </div>

                  <div class="max-h-64 overflow-y-auto pr-1 space-y-1 relative z-10">
                    <div class="flex items-start space-x-3 p-2.5 rounded-xl hover:bg-white/[0.06] transition-colors cursor-pointer group/msg border border-transparent hover:border-white/5">
                      <div class="w-6 h-6 rounded-full bg-emerald-500/10 flex items-center justify-center flex-shrink-0 mt-0.5 text-emerald-400">
                        <CheckCircle2 class="w-3.5 h-3.5" />
                      </div>
                      <div class="flex flex-col min-w-0">
                        <span class="text-xs font-semibold text-slate-200 truncate group-hover/msg:text-indigo-300 transition-colors">Audit Approved</span>
                        <span class="text-[10px] text-slate-500 leading-snug mt-0.5 line-clamp-2">Your topic "Web3 Security" has been approved.</span>
                        <span class="text-[9px] text-slate-600 mt-1 font-medium">2 mins ago</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="w-px h-5 bg-white/10"></div>

          <!-- 用户信息 -->
          <div class="flex items-center space-x-3 cursor-pointer group">
            <template v-if="isLoading">
              <div class="flex items-center space-x-3">
                <div class="flex flex-col items-end space-y-1">
                  <div class="w-20 h-3 skeleton rounded"></div>
                  <div class="w-12 h-2 skeleton rounded"></div>
                </div>
                <div class="w-9 h-9 skeleton rounded-full"></div>
              </div>
            </template>
            <template v-else-if="authStore.user">
              <div class="flex items-center space-x-3">
                <div class="flex flex-col items-end">
                  <span class="text-sm font-bold text-slate-200 group-hover:text-white transition-colors">{{ authStore.user.nickname }}</span>
                  <span :class="getRoleBadgeClass(authStore.user.roleId)">{{ getRoleText(authStore.user.roleId) }}</span>
                </div>
                <div class="relative">
                  <div class="w-9 h-9 rounded-full bg-gradient-to-tr from-indigo-500 to-purple-500 p-[2px] group-hover:shadow-[0_0_15px_rgba(99,102,241,0.5)] transition-all">
                    <div class="w-full h-full rounded-full bg-[#020617] flex items-center justify-center overflow-hidden">
                      <span class="text-xs font-black text-white">{{ (authStore.user?.nickname || authStore.user?.username || 'U').charAt(0).toUpperCase() }}</span>
                    </div>
                  </div>
                  <div class="absolute bottom-0 right-0 w-2.5 h-2.5 bg-emerald-500 border-2 border-[#020617] rounded-full"></div>
                </div>
              </div>
            </template>
            <template v-else>
              <!-- API 失败时的占位显示 -->
              <div class="flex items-center space-x-3">
                <div class="flex flex-col items-end">
                  <span class="text-sm font-bold text-slate-200 group-hover:text-white transition-colors">Xx</span>
                  <span class="text-[9px] font-bold uppercase tracking-widest px-2 py-[2px] rounded-full mt-0.5 border border-slate-500/30 text-slate-400 bg-slate-500/10">Unknown</span>
                </div>
                <div class="relative">
                  <div class="w-9 h-9 rounded-full bg-gradient-to-tr from-slate-500 to-slate-600 p-[2px] group-hover:shadow-[0_0_15px_rgba(100,100,100,0.5)] transition-all">
                    <div class="w-full h-full rounded-full bg-[#020617] flex items-center justify-center overflow-hidden">
                      <span class="text-xs font-black text-white">?</span>
                    </div>
                  </div>
                  <div class="absolute bottom-0 right-0 w-2.5 h-2.5 bg-slate-500 border-2 border-[#020617] rounded-full"></div>
                </div>
              </div>
            </template>
          </div>
        </div>
      </header>

      <!-- 主体内容 -->
      <main class="flex-1 overflow-y-auto p-8 relative">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
/* 玻璃拟物卡片 */
.glass-panel {
  background: rgba(255, 255, 255, 0.02);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.05);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.05);
}

/* 导航激活态 */
.glass-nav-active {
  background: rgba(99, 102, 241, 0.1);
  border-color: rgba(99, 102, 241, 0.3);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.1), 0 0 15px rgba(99, 102, 241, 0.15);
}
.glass-nav-active i { color: #818cf8; }
.glass-nav-active span { color: #fff; }

/* 导航项 */
.glass-nav-item {
  position: relative;
  overflow: hidden;
  border: 1px solid transparent;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}
.glass-nav-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: -150%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.08), transparent);
  transform: skewX(-20deg);
  transition: all 0.6s ease;
}
.glass-nav-item:hover::before { left: 150%; }
.glass-nav-item:hover {
  background: rgba(255, 255, 255, 0.03);
  border-color: rgba(255, 255, 255, 0.08);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.05);
}

/* 登出按钮 */
.glass-logout-btn {
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(255, 255, 255, 0.02);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}
.glass-logout-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -150%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(244, 63, 94, 0.15), transparent);
  transform: skewX(-20deg);
  transition: all 0.6s ease;
}
.glass-logout-btn:hover::before { left: 150%; }
.glass-logout-btn:hover {
  background: rgba(244, 63, 94, 0.05);
  border-color: rgba(244, 63, 94, 0.3);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.1), 0 0 15px rgba(244, 63, 94, 0.2);
}

/* 动画 */
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
.animation-delay-2000 { animation-delay: 2s; }

/* 骨架屏 */
.skeleton {
  background: linear-gradient(90deg, rgba(255,255,255,0.03) 25%, rgba(255,255,255,0.08) 50%, rgba(255,255,255,0.03) 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite;
}
@keyframes skeleton-loading {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>