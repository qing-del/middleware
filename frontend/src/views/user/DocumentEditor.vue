<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { Editor } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import Collaboration from '@tiptap/extension-collaboration'
import * as Y from 'yjs'
import {
  ArrowLeft,
  Bold,
  CircleAlert,
  Cloud,
  FilePlus2,
  Italic,
  Link2,
  List,
  Loader2,
  Redo2,
  Undo2,
  Users
} from 'lucide-vue-next'
import {
  documentApi,
  type DocumentAccessMetadata
} from '@/api/documents'
import {
  DocumentCollaborationClient,
  type DocumentCollaborationError,
  type DocumentConnectionState,
  type DocumentReconnectAccessResult
} from '@/collaboration/DocumentCollaborationClient'
import { ResourceReference } from '@/editor/ResourceReference'
import { useAuthStore } from '@/stores/auth'
import { toastSuccess } from '@/utils/feedback'
import { readAuthSession } from '@/utils/authSession'

/** 当前页面路由，用于区分创建模式和编辑模式并读取文档 ID。 */
const route = useRoute()
/** 用于返回文档列表页或跳转到新创建文档的编辑页。 */
const router = useRouter()
/** 当前登录用户信息，用于生成协作 awareness 中的展示名称和颜色。 */
const authStore = useAuthStore()

/** Tiptap 编辑器挂载的 DOM 容器；示例：编辑器区域的 `.editor-host` 元素。 */
const editorHost = ref<HTMLElement | null>(null)
/** 页面是否正在加载文档元数据或初始化协作编辑器。 */
const loading = ref(false)
/** 创建协作文档请求是否正在执行。 */
const creating = ref(false)
/** 标题更新请求是否正在执行，用于锁定标题输入框。 */
const savingTitle = ref(false)
/** 页面当前可展示的错误信息；无错误时为 `null`。 */
const error = ref<string | null>(null)
/** 当前文档的服务端元数据；创建模式或尚未加载时为 `null`。 */
const metadata = ref<DocumentAccessMetadata | null>(null)
/** 标题输入框中的本地草稿，失焦或回车时提交。 */
const titleDraft = ref('')
/** 创建模式下的新文档标题；示例：`未命名协作文档`。 */
const newDocumentTitle = ref('未命名协作文档')
/** 文档 WebSocket 协作连接状态；示例：`synced`。 */
const connectionState = ref<DocumentConnectionState>('closed')
/** 面向用户展示的连接错误或同步提示；无附加提示时为 `null`。 */
const connectionMessage = ref<string | null>(null)
/** 当前 awareness 中的协作者数量，至少展示当前用户 1 人。 */
const collaboratorCount = ref(1)
/** Tiptap 事务递增版本，用于触发工具栏格式状态重新计算。 */
const editorVersion = ref(0)
/** 文档已被服务端拒绝或确认不存在；此状态下不再尝试协同连接。 */
const accessUnavailable = ref(false)

/** 当前页面创建的 Tiptap 编辑器实例；销毁前或创建模式下为 `null`。 */
let editor: Editor | null = null
/** 与 Tiptap 共享内容绑定的 Yjs 文档；销毁前或创建模式下为 `null`。 */
let ydoc: Y.Doc | null = null
/** 当前页面使用的 WebSocket/Yjs 协作客户端；未初始化时为 `null`。 */
let collaborationClient: DocumentCollaborationClient | null = null
/** 最近一次编辑器初始化序号，用于忽略过期异步请求的结果。 */
let initializationVersion = 0

/** 当前路由是否处于创建协作文档模式。 */
const isCreateMode = computed(() => route.name === 'UserDocumentCreate')
/** 从当前路由解析出的正整数文档 ID；创建模式或地址无效时为 `null`。 */
const documentId = computed<number | null>(() => {
  if (isCreateMode.value) return null
  const parsed = Number(route.params.documentId)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
})
/** 编辑器是否已经完成同步；READ 用户也可以在此状态查看实时内容。 */
const editorIsSynced = computed(() => connectionState.value === 'synced' && Boolean(editor) && !accessUnavailable.value)
/** 当前调用方是否为文档所有者。标题和文档管理操作只允许该身份。 */
const isOwner = computed(() => metadata.value?.owner === true)
/** 当前调用方是否拥有正文写权限；OWNER 始终拥有写权限。 */
const canWrite = computed(() => metadata.value?.owner === true || metadata.value?.permission === 'WRITE')
/** 编辑器是否已经同步且允许执行会改变正文的操作。 */
const editorCanEdit = computed(() => editorIsSynced.value && canWrite.value)
/** 页面上展示的资源级权限标签。 */
const accessLabel = computed(() => {
  if (isOwner.value) return '所有者'
  if (metadata.value?.permission === 'WRITE') return '可编辑'
  return '只读'
})
/** 将内部连接状态转换为页面上显示的中文状态文本。 */
const connectionLabel = computed(() => {
  /** 各连接状态对应的页面展示文案。 */
  const labels: Record<DocumentConnectionState, string> = {
    connecting: '正在连接',
    synchronizing: '正在同步历史',
    synced: '已实时同步',
    reconnecting: '连接中断，正在重连',
    closed: '已关闭',
    error: '同步失败'
  }
  return labels[connectionState.value]
})

/** 用当前认证用户的昵称、用户名作为 awareness 展示名称。 */
function currentUserLabel(): string {
  return authStore.user?.nickname || authStore.user?.username || '当前用户'
}

/** 根据稳定字符串生成协作者颜色，保证同一用户刷新后颜色一致。 */
function createColor(seed: string): string {
  let hash = 0
  for (const char of seed) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return `hsl(${hash % 360} 62% 46%)`
}

/** 将任意请求异常转换为页面可展示的错误文本。 */
function getErrorMessage(cause: unknown, fallback: string): string {
  return cause instanceof Error && cause.message ? cause.message : fallback
}

/** 资源不存在或无权访问时统一使用的中性提示，避免泄露文档存在性。 */
const DOCUMENT_UNAVAILABLE_MESSAGE = '文档不存在或无权访问'

/** 从 Axios 兼容错误中读取 HTTP 状态，不依赖具体请求库错误类型。 */
function responseStatus(cause: unknown): number | null {
  if (!cause || typeof cause !== 'object') return null
  const response = (cause as { response?: { status?: unknown } }).response
  return typeof response?.status === 'number' ? response.status : null
}

/** 判断元数据请求是否返回了资源拒绝/不存在。 */
function isDocumentUnavailableError(cause: unknown): boolean {
  const status = responseStatus(cause)
  return status === 403 || status === 404
}

/** 根据已校验的服务端元数据计算正文写权限。 */
function canWriteMetadata(value: Pick<DocumentAccessMetadata, 'owner' | 'permission'>): boolean {
  return value.owner || value.permission === 'WRITE'
}

/** 把最新元数据和权限同步到页面、编辑器及协同客户端。 */
function applyAccessMetadata(value: DocumentAccessMetadata): void {
  metadata.value = value
  titleDraft.value = value.title
  collaborationClient?.setWriteEnabled(canWriteMetadata(value))
  editor?.setEditable(editorIsSynced.value && canWriteMetadata(value))
  if (value.deleted) {
    accessUnavailable.value = true
    error.value = DOCUMENT_UNAVAILABLE_MESSAGE
    editor?.setEditable(false)
  }
}

/** 处理 WebSocket 资源级拒绝，立即停止编辑并阻止客户端自动重连。 */
function handleAccessError(accessError: DocumentCollaborationError): void {
  if (accessError.code !== 'DOCUMENT_FORBIDDEN' && accessError.code !== 'DOCUMENT_NOT_FOUND'
      && accessError.code !== 'DOCUMENT_METADATA_INVALID') return
  accessUnavailable.value = true
  error.value = DOCUMENT_UNAVAILABLE_MESSAGE
  connectionMessage.value = DOCUMENT_UNAVAILABLE_MESSAGE
  editor?.setEditable(false)
}

/** 释放当前编辑器、Y.Doc、协作连接和页面状态，供路由切换复用。 */
function teardownEditor(): void {
  collaborationClient?.dispose()
  collaborationClient = null
  editor?.destroy()
  editor = null
  ydoc?.destroy()
  ydoc = null
  editorVersion.value += 1
  collaboratorCount.value = 1
  connectionState.value = 'closed'
  connectionMessage.value = null
  accessUnavailable.value = false
}

/** 按当前路由加载元数据、创建 Tiptap/Yjs 绑定并启动协作连接。 */
async function initializeEditor(): Promise<void> {
  /** 本次初始化请求的序号，用于丢弃路由变化后返回的过期结果。 */
  const requestedVersion = ++initializationVersion
  teardownEditor()
  error.value = null
  metadata.value = null
  titleDraft.value = ''
  accessUnavailable.value = false

  if (isCreateMode.value) return
  if (documentId.value === null) {
    error.value = '文档地址无效'
    return
  }

  loading.value = true
  try {
    /** 服务端返回的文档元数据，作为标题和协作连接配置的来源。 */
    const loadedMetadata = await documentApi.getMetadata(documentId.value)
    if (requestedVersion !== initializationVersion) return
    applyAccessMetadata(loadedMetadata)
    if (loadedMetadata.deleted) return
    await nextTick()
    if (requestedVersion !== initializationVersion || !editorHost.value) return

    /** 当前登录会话的访问令牌，用于 WebSocket bearer 子协议。 */
    const accessToken = readAuthSession().accessToken
    if (!accessToken) throw new Error('登录状态已失效，请重新登录')

    /** 与当前 Tiptap 编辑器绑定的共享 Yjs 文档。 */
    const document = new Y.Doc()
    // 编辑器绑定前先创建具名 Yjs 根节点；所有 Tiptap 文档内容随后都从这个唯一的
    // `content` fragment 读写。
    document.getXmlFragment('content')
    // 先绑定回调再连接，确保 bootstrap 完成前页面只读且过期路由不会更新当前状态。
    /** 负责 WebSocket、Yjs 更新队列和 awareness 广播的协作客户端。 */
    const client = new DocumentCollaborationClient({
      documentId: loadedMetadata.documentId,
      accessToken,
      ydoc: document,
      canWrite: canWriteMetadata(loadedMetadata),
      onStateChange: (state, message) => {
        if (requestedVersion !== initializationVersion) return
        connectionState.value = state
        connectionMessage.value = message || null
        editor?.setEditable(state === 'synced' && canWrite.value && !accessUnavailable.value)
      },
      onAwarenessChange: count => {
        if (requestedVersion === initializationVersion) collaboratorCount.value = Math.max(1, count)
      },
      onAccessError: accessError => {
        if (requestedVersion === initializationVersion) handleAccessError(accessError)
      },
      onBeforeReconnect: async (): Promise<DocumentReconnectAccessResult> => {
        if (requestedVersion !== initializationVersion || documentId.value === null) {
          return { status: 'denied', code: 'DOCUMENT_FORBIDDEN' }
        }
        try {
          const refreshedMetadata = await documentApi.getMetadata(documentId.value)
          if (requestedVersion !== initializationVersion) return { status: 'retry' }
          applyAccessMetadata(refreshedMetadata)
          if (refreshedMetadata.deleted) {
            return { status: 'denied', code: 'DOCUMENT_NOT_FOUND', message: DOCUMENT_UNAVAILABLE_MESSAGE }
          }
          return { status: 'ready', canWrite: canWriteMetadata(refreshedMetadata) }
        } catch (cause) {
          if (isDocumentUnavailableError(cause)) {
            return { status: 'denied', code: 'DOCUMENT_FORBIDDEN', message: DOCUMENT_UNAVAILABLE_MESSAGE }
          }
          if (cause instanceof Error && cause.message === '文档元数据无效') {
            return { status: 'denied', code: 'DOCUMENT_METADATA_INVALID', message: '文档暂不可用' }
          }
          return { status: 'retry' }
        }
      }
    })

    /** 绑定编辑器 DOM 和协作扩展的 Tiptap 实例。 */
    const instance = new Editor({
      element: editorHost.value,
      editable: false,
      extensions: [
        StarterKit.configure({ undoRedo: false }),
        Collaboration.configure({ document, field: 'content' }),
        ResourceReference
      ],
      editorProps: {
        attributes: {
          class: 'document-tiptap-editor',
          'aria-label': '协作文档编辑器'
        }
      },
      onTransaction: () => { editorVersion.value += 1 }
    })

    if (requestedVersion !== initializationVersion) {
      // 元数据加载期间路由可能已经变化；丢弃这个游离编辑器，避免它挂到下一个文档页面。
      instance.destroy()
      client.dispose()
      document.destroy()
      return
    }
    ydoc = document
    collaborationClient = client
    editor = instance
    client.setLocalAwareness({
      user: { name: currentUserLabel(), color: createColor(currentUserLabel()) }
    })
    client.connect()
  } catch (cause) {
    if (requestedVersion === initializationVersion) {
      // 元数据请求阶段不区分不存在、无权和其他服务端失败，统一使用中性提示，
      // 避免通过页面反馈泄露文档是否存在。
      if (!metadata.value || isDocumentUnavailableError(cause)) {
        accessUnavailable.value = true
        error.value = DOCUMENT_UNAVAILABLE_MESSAGE
      } else {
        error.value = getErrorMessage(cause, '无法打开协作文档')
      }
    }
  } finally {
    if (requestedVersion === initializationVersion) loading.value = false
  }
}

/** 校验标题并创建文档，然后跳转到新文档编辑路由。 */
async function createDocument(): Promise<void> {
  /** 去除首尾空白后的待创建文档标题。 */
  const title = newDocumentTitle.value.trim()
  if (!title) {
    error.value = '请填写文档标题'
    return
  }

  creating.value = true
  error.value = null
  try {
    /** 服务端创建成功后返回的新文档元数据。 */
    const created = await documentApi.create(title)
    await router.replace({ name: 'UserDocumentEditor', params: { documentId: created.documentId } })
    toastSuccess('协作文档已创建')
  } catch (cause) {
    error.value = getErrorMessage(cause, '创建协作文档失败')
  } finally {
    creating.value = false
  }
}

/** 只提交实际变化的标题，并在失败时恢复服务端已知标题。 */
async function saveTitle(): Promise<void> {
  if (!metadata.value || !isOwner.value || savingTitle.value || accessUnavailable.value) return
  /** 去除首尾空白后的待保存标题。 */
  const title = titleDraft.value.trim()
  if (!title) {
    titleDraft.value = metadata.value.title
    return
  }
  if (title === metadata.value.title) return

  savingTitle.value = true
  try {
    const updatedMetadata = await documentApi.updateTitle(metadata.value.documentId, title)
    // 旧兼容响应可能不带 ACL 字段；保留本次页面已验证的访问状态。
    metadata.value = { ...metadata.value, ...updatedMetadata }
    titleDraft.value = metadata.value.title
  } catch (cause) {
    titleDraft.value = metadata.value.title
    error.value = getErrorMessage(cause, '保存文档标题失败')
  } finally {
    savingTitle.value = false
  }
}

/** 只在编辑器已完成同步时执行工具栏命令。 */
function runEditorCommand(command: () => boolean): void {
  if (editorCanEdit.value) command()
}

/** 从 Tiptap 当前 selection 读取格式状态，驱动工具栏 active 样式刷新。 */
function isActive(name: string): boolean {
  // Tiptap 的更新不在 Vue 响应式系统内；读取该计数器能让每次编辑事务都刷新工具栏格式状态。
  void editorVersion.value
  return editor?.isActive(name) ?? false
}

/** 插入仅保存引用属性的行内资源引用节点。 */
function insertResourceReference(): void {
  if (!editorCanEdit.value) return
  /** 用户为行内资源引用输入的展示文本。 */
  const displayText = window.prompt('请输入引用的显示文本')?.trim()
  if (!displayText) return
  editor?.chain().focus().insertContent({
    type: 'resourceReference',
    attrs: {
      refId: crypto.randomUUID(),
      resourceType: null,
      resourceId: null,
      displayText,
      alias: null
    }
  }).run()
}

/** 返回文档列表页。 */
function goBack(): void {
  void router.push({ name: 'UserDocuments' })
}

watch(() => route.fullPath, () => { void initializeEditor() })
onMounted(() => { void initializeEditor() })
onBeforeRouteLeave(() => { teardownEditor() })
onUnmounted(() => { teardownEditor() })
</script>

<template>
  <section class="document-editor-page">
    <header class="document-editor-header">
      <button class="back-button" type="button" @click="goBack"><ArrowLeft class="h-4 w-4" /> 返回文档</button>
      <div class="connection-status" :class="`is-${connectionState}`">
        <Cloud class="h-4 w-4" />
        {{ connectionLabel }}
      </div>
    </header>

    <div v-if="isCreateMode" class="create-card">
      <FilePlus2 class="create-icon" />
      <span class="eyebrow">NEW COLLABORATIVE DOCUMENT</span>
      <h1>创建协作文档</h1>
      <p>文档内容会以 Yjs 更新通过当前 WebSocket 协议同步；不会使用其他协作服务。</p>
      <form class="create-form" @submit.prevent="createDocument">
        <label for="new-document-title">文档标题</label>
        <input id="new-document-title" v-model="newDocumentTitle" maxlength="255" autocomplete="off" :disabled="creating" />
        <button class="primary-button" type="submit" :disabled="creating">
          <Loader2 v-if="creating" class="h-4 w-4 animate-spin" />
          <FilePlus2 v-else class="h-4 w-4" />
          创建并开始编辑
        </button>
      </form>
      <p v-if="error" class="error-message"><CircleAlert class="h-4 w-4" /> {{ error }}</p>
    </div>

    <template v-else>
      <div v-if="loading && !metadata" class="state-panel"><Loader2 class="h-5 w-5 animate-spin" /> 正在加载协作文档…</div>
      <div v-else-if="accessUnavailable" class="state-panel is-error access-unavailable-panel">
        <CircleAlert class="h-5 w-5" />
        <div>
          <strong>{{ DOCUMENT_UNAVAILABLE_MESSAGE }}</strong>
          <p>当前页面已停止协同连接，请返回文档列表。</p>
          <button class="back-button" type="button" @click="goBack">返回文档</button>
        </div>
      </div>
      <div v-else-if="error && !metadata" class="state-panel is-error"><CircleAlert class="h-5 w-5" /> {{ error }}</div>
      <template v-else-if="metadata">
        <div class="document-title-row">
          <input v-model="titleDraft" class="document-title-input" maxlength="255" :disabled="savingTitle || !isOwner" :aria-readonly="!isOwner" @blur="saveTitle" @keyup.enter="saveTitle" />
          <span v-if="savingTitle" class="title-saving"><Loader2 class="h-3.5 w-3.5 animate-spin" /> 正在保存标题</span>
          <span class="access-badge" :class="{ 'is-readonly': !canWrite }">{{ accessLabel }}</span>
        </div>
        <p v-if="editorIsSynced && !canWrite" class="readonly-notice">你拥有此文档的只读权限，可以接收实时更新，但不能修改正文。</p>

        <div class="editor-toolbar" aria-label="文档编辑工具栏">
          <button type="button" title="撤销" :disabled="!editorCanEdit" @click="runEditorCommand(() => editor?.chain().focus().undo().run() ?? false)"><Undo2 class="h-4 w-4" /></button>
          <button type="button" title="重做" :disabled="!editorCanEdit" @click="runEditorCommand(() => editor?.chain().focus().redo().run() ?? false)"><Redo2 class="h-4 w-4" /></button>
          <span class="toolbar-separator" />
          <button type="button" title="加粗" :class="{ active: isActive('bold') }" :disabled="!editorCanEdit" @click="runEditorCommand(() => editor?.chain().focus().toggleBold().run() ?? false)"><Bold class="h-4 w-4" /></button>
          <button type="button" title="斜体" :class="{ active: isActive('italic') }" :disabled="!editorCanEdit" @click="runEditorCommand(() => editor?.chain().focus().toggleItalic().run() ?? false)"><Italic class="h-4 w-4" /></button>
          <button type="button" title="项目列表" :class="{ active: isActive('bulletList') }" :disabled="!editorCanEdit" @click="runEditorCommand(() => editor?.chain().focus().toggleBulletList().run() ?? false)"><List class="h-4 w-4" /></button>
          <span class="toolbar-separator" />
          <button type="button" title="插入资源引用" :disabled="!editorCanEdit" @click="insertResourceReference"><Link2 class="h-4 w-4" /><span>引用</span></button>
          <span class="collaborator-count"><Users class="h-4 w-4" /> {{ collaboratorCount }}</span>
        </div>

        <div class="editor-shell" :class="{ 'is-readonly': !editorCanEdit }">
          <div ref="editorHost" class="editor-host" :aria-readonly="!editorCanEdit" />
          <div v-if="!editorIsSynced" class="sync-overlay"><Loader2 v-if="connectionState !== 'error'" class="h-5 w-5 animate-spin" /> {{ connectionMessage || connectionLabel }}</div>
        </div>
        <p v-if="error" class="error-message"><CircleAlert class="h-4 w-4" /> {{ error }}</p>
      </template>
    </template>
  </section>
</template>

<style scoped>
.document-editor-page { max-width: 1120px; margin: 0 auto; }
.document-editor-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.back-button, .editor-toolbar button { display: inline-flex; align-items: center; gap: 7px; border: 1px solid var(--cn-border); border-radius: var(--cn-radius-sm); background: var(--cn-surface); color: var(--cn-text-soft); font-size: 12px; font-weight: 700; transition: all var(--cn-fast) var(--cn-ease); }
.back-button { padding: 8px 11px; }
.back-button:hover, .editor-toolbar button:hover:not(:disabled), .editor-toolbar button.active { border-color: var(--cn-border-strong); background: var(--cn-surface-muted); color: var(--cn-text); }
.connection-status { display: inline-flex; align-items: center; gap: 7px; color: var(--cn-text-muted); font-size: 12px; font-weight: 700; }
.connection-status.is-synced { color: var(--cn-success); }
.connection-status.is-error { color: var(--cn-danger); }
.create-card { max-width: 650px; margin: 10vh auto 0; border: 1px solid var(--cn-border); border-radius: var(--cn-radius-lg); background: var(--cn-surface); box-shadow: var(--cn-shadow-sm); padding: 36px; text-align: center; }
.create-icon { width: 34px; height: 34px; margin: 0 auto 14px; color: var(--cn-accent); }
.eyebrow { color: var(--cn-text-muted); font-size: 10px; font-weight: 800; letter-spacing: .18em; }
h1 { margin: 10px 0; color: var(--cn-text); font-size: 28px; font-weight: 800; }
.create-card > p { margin: 0; color: var(--cn-text-muted); font-size: 13px; line-height: 1.65; }
.create-form { display: grid; gap: 9px; margin-top: 26px; text-align: left; }
.create-form label { color: var(--cn-text-soft); font-size: 12px; font-weight: 750; }
.create-form input, .document-title-input { width: 100%; border: 1px solid var(--cn-border); border-radius: var(--cn-radius-sm); outline: none; background: var(--cn-bg-subtle); color: var(--cn-text); transition: border-color var(--cn-fast) var(--cn-ease); }
.create-form input { padding: 10px 12px; font-size: 14px; }
.create-form input:focus, .document-title-input:focus { border-color: var(--cn-accent); }
.primary-button { display: inline-flex; align-items: center; justify-content: center; gap: 8px; margin-top: 7px; border: 1px solid var(--cn-accent); border-radius: var(--cn-radius-sm); background: var(--cn-accent); color: white; padding: 10px 14px; font-size: 13px; font-weight: 750; }
.primary-button:disabled { cursor: wait; opacity: .7; }
.state-panel { display: flex; min-height: 260px; align-items: center; justify-content: center; gap: 10px; border: 1px dashed var(--cn-border-strong); border-radius: var(--cn-radius-lg); background: var(--cn-bg-subtle); color: var(--cn-text-muted); font-size: 13px; }
.state-panel.is-error, .error-message { color: var(--cn-danger); }
.access-unavailable-panel { text-align: center; }
.access-unavailable-panel > div { display: grid; justify-items: center; gap: 6px; }
.access-unavailable-panel strong { color: var(--cn-danger); font-size: 15px; }
.access-unavailable-panel p { margin: 0; color: var(--cn-text-muted); font-size: 12px; }
.access-unavailable-panel .back-button { margin-top: 6px; }
.document-title-row { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.document-title-input { border-color: transparent; background: transparent; padding: 4px 0; font-size: 27px; font-weight: 800; }
.document-title-input:hover { border-color: var(--cn-border); padding-inline: 8px; }
.document-title-input:disabled { cursor: default; opacity: .78; }
.title-saving, .collaborator-count, .access-badge { display: inline-flex; align-items: center; gap: 5px; flex: 0 0 auto; color: var(--cn-text-muted); font-size: 11px; font-weight: 700; }
.access-badge { border: 1px solid color-mix(in srgb, var(--cn-success) 45%, var(--cn-border)); border-radius: 999px; background: color-mix(in srgb, var(--cn-success) 10%, transparent); color: var(--cn-success); padding: 4px 8px; }
.access-badge.is-readonly { border-color: var(--cn-border); background: var(--cn-bg-subtle); color: var(--cn-text-muted); }
.readonly-notice { margin: -5px 0 12px; color: var(--cn-text-muted); font-size: 12px; }
.editor-toolbar { display: flex; align-items: center; gap: 5px; border: 1px solid var(--cn-border); border-radius: var(--cn-radius-md) var(--cn-radius-md) 0 0; background: var(--cn-surface); padding: 7px; }
.editor-toolbar button { min-height: 30px; justify-content: center; border-color: transparent; padding: 6px 8px; }
.editor-toolbar button:disabled { cursor: wait; opacity: .45; }
.toolbar-separator { width: 1px; height: 20px; margin: 0 3px; background: var(--cn-border); }
.collaborator-count { margin-left: auto; padding: 0 5px; }
.editor-shell { position: relative; min-height: 62vh; border: 1px solid var(--cn-border); border-top: 0; border-radius: 0 0 var(--cn-radius-md) var(--cn-radius-md); background: var(--cn-surface); overflow: hidden; }
.editor-shell.is-readonly { background: var(--cn-bg-subtle); }
.editor-host { min-height: 62vh; }
.sync-overlay { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; gap: 10px; background: color-mix(in srgb, var(--cn-surface) 88%, transparent); color: var(--cn-text-muted); font-size: 13px; font-weight: 700; }
.error-message { display: flex; align-items: center; gap: 7px; margin: 12px 0 0; font-size: 12px; }
:deep(.document-tiptap-editor) { min-height: 62vh; outline: none; padding: 34px clamp(22px, 5vw, 70px); color: var(--cn-text); font-size: 16px; line-height: 1.8; }
:deep(.document-tiptap-editor > :first-child) { margin-top: 0; }
:deep(.document-tiptap-editor h1), :deep(.document-tiptap-editor h2), :deep(.document-tiptap-editor h3) { color: var(--cn-text); line-height: 1.3; }
:deep(.document-tiptap-editor p.is-editor-empty:first-child::before) { float: left; height: 0; color: var(--cn-text-faint); content: '开始记录你的想法…'; pointer-events: none; }
:deep(.document-resource-reference) { display: inline-block; border-radius: 4px; background: color-mix(in srgb, var(--cn-accent) 12%, transparent); color: var(--cn-accent); padding: 0 4px; font-size: .92em; font-weight: 700; }
@media (max-width: 640px) { .document-editor-header { align-items: flex-start; flex-direction: column; } .create-card { margin-top: 20px; padding: 26px 20px; } .document-title-row { align-items: flex-start; flex-direction: column; gap: 2px; } .document-title-input { font-size: 22px; } .editor-toolbar { flex-wrap: wrap; } .collaborator-count { margin-left: 0; } :deep(.document-tiptap-editor) { padding: 24px 20px; } }
</style>
