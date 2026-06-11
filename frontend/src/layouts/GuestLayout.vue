<script setup lang="ts">
import { useRouter } from 'vue-router'
import { BookOpen, LogIn, Sparkles } from 'lucide-vue-next'

const router = useRouter()

function goLogin() {
  router.push('/login')
}
</script>

<template>
  <div class="min-h-screen bg-[var(--cn-bg)] text-[var(--cn-text)] selection:bg-black/10">
    <header class="sticky top-0 z-40 border-b border-[var(--cn-border)] bg-[rgba(251,251,250,0.9)] backdrop-blur-xl">
      <div class="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <router-link to="/guest/notes" class="flex min-w-0 items-center gap-3">
          <div class="cn-brand-mark">
            <BookOpen class="h-4 w-4" />
          </div>
          <div class="min-w-0">
            <div class="cn-brand-wordmark truncate">CORE<span> NODE</span></div>
            <div class="truncate text-[10px] font-semibold uppercase tracking-[0.22em] text-[var(--cn-text-muted)]">Public Archive</div>
          </div>
        </router-link>

        <div class="flex items-center gap-2">
          <router-link to="/guest/notes" class="hidden items-center rounded-lg px-3 py-2 text-xs font-semibold text-[var(--cn-text-soft)] transition hover:bg-[var(--cn-surface-muted)] hover:text-[var(--cn-text)] sm:inline-flex">
            <Sparkles class="mr-2 h-4 w-4" />
            公开笔记
          </router-link>
          <button class="cn-btn cn-btn-primary px-3" type="button" @click="goLogin">
            <LogIn class="h-4 w-4" />
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
