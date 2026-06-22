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
  <button
    v-if="props.showTrigger"
    class="audio-trigger"
    type="button"
    aria-label="Open audio generation"
    @click="toggleModal"
  >
    <Mic class="h-5 w-5" />
    <span v-if="submitting" class="absolute -right-1 -top-1 h-3 w-3 rounded-full bg-[var(--cn-accent)]"></span>
  </button>

  <Teleport to="body">
    <Transition name="modal">
      <div v-if="isOpen" class="fixed inset-0 z-[110] flex items-center justify-center px-4">
        <div class="absolute inset-0 bg-black/25 backdrop-blur-[2px]" @click="toggleModal" />

        <div class="audio-modal">
          <div class="flex items-center justify-between border-b border-[var(--cn-border)] px-6 py-5">
            <div class="flex items-center gap-3">
              <div class="modal-icon">
                <Mic class="h-5 w-5" />
              </div>
              <div>
                <h3 class="text-lg font-semibold tracking-tight text-[var(--cn-text)]">音频生成助手</h3>
                <p class="text-[11px] font-semibold uppercase tracking-[0.18em] text-[var(--cn-text-muted)]">AI Audio Generation</p>
              </div>
            </div>
            <button class="icon-button" type="button" @click="toggleModal">
              <X class="h-5 w-5" />
            </button>
          </div>

          <div class="custom-scrollbar max-h-[70vh] space-y-6 overflow-y-auto p-6">
            <div class="space-y-2">
              <label class="field-label">
                <Volume2 class="h-3.5 w-3.5" />
                待生成的纯文本内容
              </label>
              <textarea
                v-model="form.text"
                rows="5"
                placeholder="请输入需要转换为语音的文字..."
                class="cn-input w-full resize-none rounded-lg px-4 py-3 text-sm"
              ></textarea>
            </div>

            <div class="grid gap-6 sm:grid-cols-2">
              <div class="space-y-3">
                <label class="field-label">
                  <Gauge class="h-3.5 w-3.5" />
                  语速倍率 ({{ form.speed.toFixed(1) }}x)
                </label>
                <input
                  v-model.number="form.speed"
                  type="range"
                  min="0.5"
                  max="3.0"
                  step="0.1"
                  class="range-input"
                />
                <div class="flex justify-between text-[10px] font-semibold text-[var(--cn-text-muted)]">
                  <span>0.5x</span>
                  <span>3.0x</span>
                </div>
              </div>

              <div class="space-y-3">
                <label class="field-label">
                  <Waves class="h-3.5 w-3.5" />
                  背景音因子 ({{ form.noiseFactor.toFixed(1) }})
                </label>
                <input
                  v-model.number="form.noiseFactor"
                  type="range"
                  min="0.0"
                  max="2.0"
                  step="0.1"
                  class="range-input"
                />
                <div class="flex justify-between text-[10px] font-semibold text-[var(--cn-text-muted)]">
                  <span>0.0</span>
                  <span>2.0</span>
                </div>
              </div>
            </div>

            <div class="space-y-2">
              <label class="field-label">
                <Waves class="h-3.5 w-3.5" />
                背景音类型 (Noise Type)
              </label>
              <div class="grid grid-cols-1 gap-2">
                <button
                  v-for="noise in noiseTypes"
                  :key="noise.value"
                  type="button"
                  :class="['noise-option', form.noiseType === noise.value ? 'noise-option-active' : '']"
                  @click="form.noiseType = noise.value"
                >
                  <span class="flex-1 text-left text-xs font-semibold">{{ noise.label }}</span>
                  <span class="radio-dot">
                    <span v-if="form.noiseType === noise.value"></span>
                  </span>
                </button>
              </div>
            </div>
          </div>

          <div class="flex gap-3 border-t border-[var(--cn-border)] bg-[var(--cn-bg-subtle)] p-6">
            <button class="cn-btn flex-1" type="button" @click="toggleModal">
              取消
            </button>
            <button
              class="cn-btn cn-btn-primary flex-[2]"
              type="button"
              :disabled="submitting || !form.text.trim()"
              @click="handleSubmit"
            >
              <Loader2 v-if="submitting" class="h-4 w-4 animate-spin" />
              <Send v-else class="h-4 w-4" />
              <span>{{ submitting ? '提交任务中...' : '开始合成音频' }}</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.audio-trigger {
  position: fixed;
  right: 32px;
  bottom: 96px;
  z-index: 100;
  display: flex;
  height: 52px;
  width: 52px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--cn-accent);
  border-radius: 999px;
  background: var(--cn-accent);
  color: var(--cn-text-inverse);
  box-shadow: var(--cn-shadow-sm);
  transition:
    transform var(--cn-fast) var(--cn-ease),
    background-color var(--cn-fast) var(--cn-ease);
}

.audio-trigger:hover {
  background: var(--cn-accent-hover);
  transform: translateY(-1px);
}

.audio-modal {
  position: relative;
  display: flex;
  width: min(100%, 540px);
  max-height: 88vh;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--cn-border);
  border-radius: var(--cn-radius-xl);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: var(--cn-shadow-md);
}

.modal-icon,
.icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--cn-border);
  border-radius: var(--cn-radius-sm);
  background: var(--cn-surface);
  color: var(--cn-text);
}

.modal-icon {
  height: 40px;
  width: 40px;
}

.icon-button {
  height: 34px;
  width: 34px;
  color: var(--cn-text-muted);
  transition: all var(--cn-fast) var(--cn-ease);
}

.icon-button:hover {
  border-color: var(--cn-border-strong);
  background: var(--cn-surface-muted);
  color: var(--cn-text);
}

.field-label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--cn-text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.range-input {
  width: 100%;
  accent-color: var(--cn-accent);
}

.noise-option {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--cn-border);
  border-radius: var(--cn-radius-md);
  background: var(--cn-surface);
  padding: 12px 14px;
  color: var(--cn-text-soft);
  transition: all var(--cn-fast) var(--cn-ease);
}

.noise-option:hover,
.noise-option-active {
  border-color: var(--cn-border-strong);
  background: var(--cn-surface-muted);
  color: var(--cn-text);
}

.radio-dot {
  display: flex;
  height: 16px;
  width: 16px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--cn-border-strong);
  border-radius: 999px;
}

.radio-dot span {
  height: 8px;
  width: 8px;
  border-radius: 999px;
  background: var(--cn-accent);
}

.modal-enter-active,
.modal-leave-active {
  transition: all 0.24s var(--cn-ease);
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
  transform: scale(0.98) translateY(8px);
}

.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background: var(--cn-border-strong);
  border-radius: 999px;
}
</style>
