<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { 
  Sparkles, FileText, Layers, HardDrive 
} from 'lucide-vue-next'

const authStore = useAuthStore()
</script>

<template>
  <div class="max-w-5xl mx-auto space-y-8">
    <!-- 欢迎卡片 -->
    <div class="glass-panel rounded-3xl p-8 relative overflow-hidden group">
      <div class="absolute -top-24 -right-24 w-64 h-64 bg-indigo-500/10 blur-[60px] rounded-full group-hover:bg-indigo-500/20 transition-all duration-700 pointer-events-none"></div>

      <div class="relative z-10">
        <div class="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-white/5 border border-white/10 mb-4">
          <Sparkles class="text-indigo-400 w-3 h-3" />
          <span class="text-[10px] text-indigo-300 font-bold uppercase tracking-[0.2em]">System Optimal</span>
        </div>
        <h2 class="text-3xl font-black text-white tracking-tight mb-2">
          欢迎回来，<span class="text-transparent bg-clip-text bg-gradient-to-r from-indigo-400 to-purple-400">{{ authStore.user?.nickname || 'Node Explorer' }}</span>
        </h2>
        <p class="text-slate-400 text-sm max-w-xl leading-relaxed">
          您的数字化资产已同步就绪。尽情享受沉浸式的创作与管理体验。
        </p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <div class="glass-panel rounded-2xl p-6 hover:bg-white/[0.04] transition-colors border-t border-white/10 border-l-0 border-r-0 border-b-0">
        <div class="flex justify-between items-start mb-4">
          <div class="p-2.5 bg-indigo-500/10 rounded-xl text-indigo-400">
            <FileText class="w-5 h-5" />
          </div>
          <span class="text-xs font-bold text-emerald-400 bg-emerald-400/10 px-2 py-1 rounded-lg">+12%</span>
        </div>
        <h3 class="text-slate-400 text-xs font-bold uppercase tracking-widest mb-1">Total Notes</h3>
        <div class="text-3xl font-black text-white">1,248</div>
      </div>

      <div class="glass-panel rounded-2xl p-6 hover:bg-white/[0.04] transition-colors border-t border-white/10 border-l-0 border-r-0 border-b-0">
        <div class="flex justify-between items-start mb-4">
          <div class="p-2.5 bg-purple-500/10 rounded-xl text-purple-400">
            <Layers class="w-5 h-5" />
          </div>
        </div>
        <h3 class="text-slate-400 text-xs font-bold uppercase tracking-widest mb-1">Topics & Tags</h3>
        <div class="text-3xl font-black text-white">64 / 312</div>
      </div>

      <div class="glass-panel rounded-2xl p-6 hover:bg-white/[0.04] transition-colors border-t border-white/10 border-l-0 border-r-0 border-b-0">
        <div class="flex justify-between items-start mb-4">
          <div class="p-2.5 bg-rose-500/10 rounded-xl text-rose-400">
            <HardDrive class="w-5 h-5" />
          </div>
          <span class="text-xs font-bold text-slate-400 bg-white/5 px-2 py-1 rounded-lg">24% Used</span>
        </div>
        <h3 class="text-slate-400 text-xs font-bold uppercase tracking-widest mb-1">Storage Usage</h3>
        <div class="text-3xl font-black text-white">1.2 <span class="text-lg text-slate-500">GB</span></div>
        <div class="w-full bg-black/40 h-1.5 rounded-full mt-4 overflow-hidden">
          <div class="bg-gradient-to-r from-rose-500 to-indigo-500 w-[24%] h-full rounded-full"></div>
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