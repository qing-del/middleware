<script setup lang="ts">
import { useRouter } from 'vue-router'
import { BookOpen, LogIn, Sparkles } from 'lucide-vue-next'

const router = useRouter()

function goLogin() {
  router.push('/login')
}
</script>

<template>
  <div class="min-h-screen bg-[#020617] text-slate-100 selection:bg-cyan-500/30">
    <header class="sticky top-0 z-40 border-b border-white/10 bg-[#020617]/90 backdrop-blur-xl">
      <div class="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <router-link to="/guest/notes" class="flex min-w-0 items-center gap-3">
          <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-cyan-400/30 bg-cyan-400/10 text-cyan-300 shadow-[0_0_18px_rgba(34,211,238,0.18)]">
            <BookOpen class="h-5 w-5" />
          </div>
          <div class="min-w-0">
            <div class="truncate text-sm font-black uppercase tracking-[0.18em] text-white">CORENODE</div>
            <div class="truncate text-[10px] font-bold uppercase tracking-[0.22em] text-slate-500">Public Archive</div>
          </div>
        </router-link>

        <div class="flex items-center gap-2">
          <router-link to="/guest/notes" class="hidden rounded-lg px-3 py-2 text-xs font-bold uppercase tracking-[0.16em] text-slate-400 transition hover:bg-white/5 hover:text-white sm:inline-flex">
            <Sparkles class="mr-2 h-4 w-4 text-cyan-300" />
            公开笔记
          </router-link>
          <button class="inline-flex items-center rounded-lg border border-white/10 bg-white/[0.03] px-3 py-2 text-xs font-bold uppercase tracking-[0.16em] text-slate-200 transition hover:border-cyan-400/40 hover:bg-cyan-400/10 hover:text-cyan-100" @click="goLogin">
            <LogIn class="mr-2 h-4 w-4" />
            登录
          </button>
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <router-view />
    </main>
  </div>
</template>
