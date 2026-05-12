<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { 
  ShieldCheck, Users, FileText, ClipboardCheck, HardDrive, ShieldAlert 
} from 'lucide-vue-next'

const authStore = useAuthStore()
</script>

<template>
  <div class="max-w-6xl mx-auto space-y-8">
    <!-- 管理员欢迎卡片 -->
    <div class="glass-panel rounded-3xl p-8 relative overflow-hidden group">
      <div class="absolute -top-24 -right-24 w-64 h-64 bg-rose-500/10 blur-[60px] rounded-full group-hover:bg-rose-500/20 transition-all duration-700 pointer-events-none"></div>

      <div class="relative z-10">
        <div class="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-white/5 border border-white/10 mb-4">
          <ShieldCheck class="text-rose-400 w-3 h-3" />
          <span class="text-[10px] text-rose-300 font-bold uppercase tracking-[0.2em]">Highest Privilege</span>
        </div>
        <h2 class="text-3xl font-black text-white tracking-tight mb-2">
          欢迎回来，<span class="text-transparent bg-clip-text bg-gradient-to-r from-rose-400 to-orange-400">{{ authStore.user?.nickname || 'System Admin' }}</span>
        </h2>
        <p class="text-slate-400 text-sm max-w-xl leading-relaxed">
          全站资源监控与审批通道已开启。您可以管理用户资产、批量审核笔记申请，并维护内容生态的健康运转。
        </p>
      </div>
    </div>

    <!-- Admin 专属数据大盘 -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-6">
      <!-- 统计卡片 1: 总用户 -->
      <div class="glass-panel rounded-2xl p-6 hover:bg-white/[0.04] transition-colors border-t border-white/10 border-l-0 border-r-0 border-b-0">
        <div class="flex justify-between items-start mb-4">
          <div class="p-2.5 bg-indigo-500/10 rounded-xl text-indigo-400">
            <Users class="w-5 h-5" />
          </div>
          <span class="text-xs font-bold text-emerald-400 bg-emerald-400/10 px-2 py-1 rounded-lg">+12</span>
        </div>
        <h3 class="text-slate-400 text-xs font-bold uppercase tracking-widest mb-1">Total Users</h3>
        <div class="text-3xl font-black text-white">4,281</div>
      </div>

      <!-- 统计卡片 2: 全站笔记 -->
      <div class="glass-panel rounded-2xl p-6 hover:bg-white/[0.04] transition-colors border-t border-white/10 border-l-0 border-r-0 border-b-0">
        <div class="flex justify-between items-start mb-4">
          <div class="p-2.5 bg-purple-500/10 rounded-xl text-purple-400">
            <FileText class="w-5 h-5" />
          </div>
          <span class="text-xs font-bold text-emerald-400 bg-emerald-400/10 px-2 py-1 rounded-lg">+142</span>
        </div>
        <h3 class="text-slate-400 text-xs font-bold uppercase tracking-widest mb-1">Global Notes</h3>
        <div class="text-3xl font-black text-white">128.5 <span class="text-lg text-slate-500">K</span></div>
      </div>

      <!-- 统计卡片 3: 待审核事项 -->
      <div class="glass-panel rounded-2xl p-6 bg-rose-500/5 hover:bg-rose-500/10 transition-colors border border-rose-500/20 shadow-[inset_0_1px_1px_rgba(255,255,255,0.05),0_0_20px_rgba(244,63,94,0.05)] relative overflow-hidden group">
        <div class="absolute -right-4 -bottom-4 opacity-10 group-hover:opacity-20 transition-opacity">
          <ShieldAlert class="w-32 h-32 text-rose-500" />
        </div>
        <div class="flex justify-between items-start mb-4 relative z-10">
          <div class="p-2.5 bg-rose-500/20 rounded-xl text-rose-400">
            <ClipboardCheck class="w-5 h-5" />
          </div>
          <span class="text-[10px] font-black text-rose-300 bg-rose-500/20 px-2 py-1 rounded-lg uppercase tracking-widest animate-pulse">Action Req</span>
        </div>
        <h3 class="text-rose-200/80 text-xs font-bold uppercase tracking-widest mb-1 relative z-10">Pending Audits</h3>
        <div class="text-3xl font-black text-rose-400 relative z-10">24</div>
      </div>

      <!-- 统计卡片 4: 系统存储 -->
      <div class="glass-panel rounded-2xl p-6 hover:bg-white/[0.04] transition-colors border-t border-white/10 border-l-0 border-r-0 border-b-0">
        <div class="flex justify-between items-start mb-4">
          <div class="p-2.5 bg-emerald-500/10 rounded-xl text-emerald-400">
            <HardDrive class="w-5 h-5" />
          </div>
          <span class="text-[10px] font-bold text-slate-400 bg-white/5 px-2 py-1 rounded-lg uppercase">68% Used</span>
        </div>
        <h3 class="text-slate-400 text-xs font-bold uppercase tracking-widest mb-1">OSS Storage</h3>
        <div class="text-3xl font-black text-white">6.8 <span class="text-lg text-slate-500">TB</span></div>
        <div class="w-full bg-black/40 h-1.5 rounded-full mt-4 overflow-hidden">
          <div class="bg-gradient-to-r from-emerald-500 to-teal-400 w-[68%] h-full rounded-full"></div>
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