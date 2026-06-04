<script setup lang="ts">
import { ref } from 'vue'
import { Mic, X, Send, Loader2, Volume2, Gauge, Waves } from 'lucide-vue-next'
import { audioApi } from '@/api/audio'
import { toastError, toastSuccess } from '@/utils/feedback'

const props = withDefaults(defineProps<{ showTrigger?: boolean }>(), {
  showTrigger: true
})

const isOpen = ref(false)
const submitting = ref(false)

const form = ref({
  text: '',
  speed: 1.0,
  noiseType: 'PURE',
  noiseFactor: 0.5
})

const noiseTypes = [
  { label: '无背景音 (PURE)', value: 'PURE' },
  { label: '白噪音 (WHITE_NOISE)', value: 'WHITE_NOISE' },
  { label: '粉红噪音 (PINK_NOISE)', value: 'PINK_NOISE' },
  { label: '布朗噪音 (BROWN_NOISE)', value: 'BROWN_NOISE' },
  { label: '咖啡馆 (CAFE)', value: 'CAFE' },
  { label: '机场 (AIRPORT)', value: 'AIRPORT' },
  { label: '地铁 (SUBWAY)', value: 'SUBWAY' }
]

async function handleSubmit() {
  if (!form.value.text.trim()) return
  submitting.value = true
  try {
    const res = await audioApi.generate({
      text: form.value.text,
      speed: form.value.speed,
      noiseType: form.value.noiseType,
      noiseFactor: form.value.noiseFactor
    })
    toastSuccess(`音频生成任务已提交，任务 ID: ${res.taskId}`)
    isOpen.value = false
    // 重置表单
    form.value.text = ''
  } catch (error) {
    console.error('Submit audio task failed:', error)
    toastError('任务提交失败，请稍后重试。')
  } finally {
    submitting.value = false
  }
}

function toggleModal() {
  isOpen.value = !isOpen.value
}

function openModal() {
  isOpen.value = true
}

defineExpose({ open: openModal })
</script>

<template>
  <!-- 悬浮球 -->
  <button
    v-if="props.showTrigger"
    class="fixed bottom-24 right-8 z-[100] w-14 h-14 rounded-full bg-indigo-600 hover:bg-indigo-500 text-white shadow-[0_0_20px_rgba(79,70,229,0.5)] flex items-center justify-center transition-all hover:scale-110 active:scale-95 group"
    @click="toggleModal"
  >
    <Mic class="w-6 h-6 transition-transform group-hover:rotate-12" />
    <span class="absolute -top-1 -right-1 flex h-3 w-3" v-if="submitting">
      <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-indigo-400 opacity-75"></span>
      <span class="relative inline-flex rounded-full h-3 w-3 bg-indigo-500"></span>
    </span>
  </button>

  <!-- 弹窗 -->
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="isOpen" class="fixed inset-0 z-[110] flex items-center justify-center px-4">
        <!-- 背景遮罩 -->
        <div class="absolute inset-0 bg-black/80 backdrop-blur-sm" @click="toggleModal" />
        
        <!-- 窗口内容 -->
        <div class="relative w-full max-w-lg glass-panel rounded-3xl overflow-hidden shadow-2xl border border-white/10">
          <div class="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-indigo-500 via-purple-500 to-indigo-500" />
          
          <!-- 头部 -->
          <div class="px-6 py-5 flex items-center justify-between border-b border-white/5">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-xl bg-indigo-500/10 flex items-center justify-center text-indigo-400">
                <Mic class="w-5 h-5" />
              </div>
              <div>
                <h3 class="text-lg font-bold text-white tracking-tight">音频生成助手</h3>
                <p class="text-[11px] text-slate-500 font-medium tracking-wide uppercase">AI Audio Generation</p>
              </div>
            </div>
            <button @click="toggleModal" class="p-2 rounded-full hover:bg-white/5 text-slate-500 hover:text-white transition-colors">
              <X class="w-5 h-5" />
            </button>
          </div>

          <!-- 表单区 -->
          <div class="p-6 space-y-6 max-h-[70vh] overflow-y-auto custom-scrollbar">
            <!-- 文本输入 -->
            <div class="space-y-2">
              <label class="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
                <Volume2 class="w-3.5 h-3.5 text-indigo-400" />
                待生成的纯文本内容
              </label>
              <textarea
                v-model="form.text"
                rows="5"
                placeholder="请输入需要转换为语音的文字..."
                class="w-full bg-black/30 border border-white/[0.05] rounded-2xl px-4 py-3 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-indigo-500/50 focus:ring-4 focus:ring-indigo-500/10 transition-all resize-none"
              ></textarea>
            </div>

            <div class="grid grid-cols-2 gap-6">
              <!-- 播放倍速 -->
              <div class="space-y-3">
                <label class="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
                  <Gauge class="w-3.5 h-3.5 text-amber-400" />
                  语速倍率 ({{ form.speed.toFixed(1) }}x)
                </label>
                <div class="px-2">
                  <input
                    type="range"
                    v-model.number="form.speed"
                    min="0.5"
                    max="3.0"
                    step="0.1"
                    class="w-full h-1.5 bg-white/10 rounded-lg appearance-none cursor-pointer accent-indigo-500"
                  />
                  <div class="flex justify-between mt-2">
                    <span class="text-[10px] text-slate-600 font-bold">0.5x</span>
                    <span class="text-[10px] text-slate-600 font-bold">3.0x</span>
                  </div>
                </div>
              </div>

              <!-- 背景音量 -->
              <div class="space-y-3">
                <label class="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
                  <Waves class="w-3.5 h-3.5 text-blue-400" />
                  背景音因子 ({{ form.noiseFactor.toFixed(1) }})
                </label>
                <div class="px-2">
                  <input
                    type="range"
                    v-model.number="form.noiseFactor"
                    min="0.0"
                    max="2.0"
                    step="0.1"
                    class="w-full h-1.5 bg-white/10 rounded-lg appearance-none cursor-pointer accent-blue-500"
                  />
                  <div class="flex justify-between mt-2">
                    <span class="text-[10px] text-slate-600 font-bold">0.0</span>
                    <span class="text-[10px] text-slate-600 font-bold">2.0</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 背景音类型 -->
            <div class="space-y-2">
              <label class="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
                <Waves class="w-3.5 h-3.5 text-indigo-400" />
                背景音类型 (Noise Type)
              </label>
              <div class="grid grid-cols-1 gap-2">
                <div 
                  v-for="noise in noiseTypes" 
                  :key="noise.value"
                  class="flex items-center px-4 py-3 rounded-xl border cursor-pointer transition-all"
                  :class="form.noiseType === noise.value 
                    ? 'bg-indigo-500/10 border-indigo-500/30 text-indigo-400' 
                    : 'bg-white/[0.02] border-white/5 text-slate-400 hover:bg-white/[0.05]'"
                  @click="form.noiseType = noise.value"
                >
                  <div class="flex-1 text-xs font-bold">{{ noise.label }}</div>
                  <div 
                    class="w-4 h-4 rounded-full border-2 flex items-center justify-center"
                    :class="form.noiseType === noise.value ? 'border-indigo-500' : 'border-slate-700'"
                  >
                    <div v-if="form.noiseType === noise.value" class="w-2 h-2 rounded-full bg-indigo-500" />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 底部按钮 -->
          <div class="p-6 bg-white/[0.01] border-t border-white/5 flex gap-3">
            <button
              class="flex-1 py-3 px-4 rounded-2xl text-sm font-bold text-slate-400 hover:text-white hover:bg-white/5 transition-all"
              @click="toggleModal"
            >
              取消
            </button>
            <button
              class="flex-[2] py-3 px-4 rounded-2xl bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-bold shadow-[0_4px_15px_rgba(79,70,229,0.3)] flex items-center justify-center gap-2 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
              :disabled="submitting || !form.text.trim()"
              @click="handleSubmit"
            >
              <Loader2 v-if="submitting" class="w-4 h-4 animate-spin" />
              <Send v-else class="w-4 h-4" />
              <span>{{ submitting ? '提交任务中...' : '开始合成音频' }}</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.glass-panel {
  background: rgba(15, 23, 42, 0.9);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}

.modal-enter-active, .modal-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.modal-enter-from, .modal-leave-to {
  opacity: 0;
  transform: scale(0.9) translateY(20px);
}

.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 2px;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.2);
}
</style>
