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
  Save,
  Trash2,
  Undo2,
  UserPlus,
  Users,
  X
} from 'lucide-vue-next'
import {
  documentApi,
  type DocumentAccessMetadata,
  type DocumentPermission,
  type DocumentUserAuthorization
} from '@/api/documents'
import {
  DocumentCollaborationClient,
  type DocumentCollaborationError,
  type DocumentConnectionState,
  type DocumentReconnectAccessResult
} from '@/collaboration/DocumentCollaborationClient'
import { ResourceReference } from '@/editor/ResourceReference'
import { useAuthStore } from '@/stores/auth'
import { confirmAction, toastError, toastSuccess } from '@/utils/feedback'
import { readAuthSession } from '@/utils/authSession'

/** 当前页面路由，用于区分创建模式和编辑模式并读取文档 ID。 */
const route = useRoute()
/** 用于返回文档列表页或跳转到新创建文档的编辑页。 */
const router = useRouter()
/** 当前登录用户信息，用于生成协作 Awareness 的本地展示名称和用户 ID。 */
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
/** 权限管理弹窗是否打开；创建模式和非所有者不会打开该弹窗。 */
const authorizationModalVisible = ref(false)
/** 权限列表请求是否正在执行。 */
const authorizationLoading = ref(false)
/** 权限管理操作错误；仅在弹窗内展示，不影响正文同步状态。 */
const authorizationError = ref<string | null>(null)
/** 当前文档的全部授权记录，包含已撤销记录及其待提交草稿。 */
const authorizations = ref<AuthorizationRow[]>([])
/** 新增授权表单中的用户 ID，使用字符串避免输入过程中的非安全数字转换。 */
const newAuthorizationUserId = ref('')
/** 新增授权表单中的权限，默认按最小权限 READ。 */
const newAuthorizationPermission = ref<DocumentPermission>('READ')
/** 新增授权表单中的启用状态，默认立即生效。 */
const newAuthorizationEnabled = ref(true)
/** 新增授权请求是否正在执行。 */
const addingAuthorization = ref(false)
/** 当前正在保存或撤销的授权用户 ID；同一时间只允许一个行操作。 */
const authorizationOperationUserId = ref<number | null>(null)

/** 授权列表中的可编辑行，草稿字段只在保存成功后由服务端数据覆盖。 */
interface AuthorizationRow extends DocumentUserAuthorization {
  draftPermission: DocumentPermission
  draftEnabled: boolean
}

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

/** 将服务端授权记录扩展为带本地待提交草稿的表格行。 */
function toAuthorizationRow(value: DocumentUserAuthorization): AuthorizationRow {
  return {
    ...value,
    draftPermission: value.permission,
    draftEnabled: value.enabled
  }
}

/** 将后端 LocalDateTime 字符串格式化为本地时间；异常值保留原文便于识别。 */
function formatAuthorizationTime(value: string): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ')
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  }).format(date)
}

/** 严格解析授权目标用户 ID，避免空值、小数和超出安全整数范围的请求。 */
function parseAuthorizationUserId(value: string): number | null {
  const normalized = value.trim()
  if (!/^\d+$/.test(normalized)) return null
  const parsed = Number(normalized)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

/** 清空新增授权表单，下一次新增默认从最小 READ 权限开始。 */
function resetAuthorizationForm(): void {
  newAuthorizationUserId.value = ''
  newAuthorizationPermission.value = 'READ'
  newAuthorizationEnabled.value = true
}

/** 加载当前文档的全部授权记录，并丢弃路由切换后返回的过期结果。 */
async function loadAuthorizations(): Promise<void> {
  const requestedDocumentId = documentId.value
  if (requestedDocumentId === null || !isOwner.value || accessUnavailable.value) return

  authorizationLoading.value = true
  authorizationError.value = null
  try {
    const records = await documentApi.listAuthorizations(requestedDocumentId)
    if (documentId.value !== requestedDocumentId) return
    authorizations.value = records.map(toAuthorizationRow)
  } catch (cause) {
    if (documentId.value !== requestedDocumentId) return
    authorizationError.value = getErrorMessage(cause, '无法加载文档授权')
    toastError(authorizationError.value)
  } finally {
    if (documentId.value === requestedDocumentId) authorizationLoading.value = false
  }
}

/** 打开权限管理弹窗时总是重新读取服务端状态，避免展示过期授权。 */
function openAuthorizationModal(): void {
  if (!isOwner.value || documentId.value === null || accessUnavailable.value) return
  authorizationModalVisible.value = true
  authorizationError.value = null
  resetAuthorizationForm()
  void loadAuthorizations()
}

/** 关闭权限管理弹窗；已提交的服务端数据不会因关闭而丢失。 */
function closeAuthorizationModal(): void {
  authorizationModalVisible.value = false
  authorizationError.value = null
}

/** 新增或重新写入一条用户授权记录。 */
async function addAuthorization(): Promise<void> {
  const requestedDocumentId = documentId.value
  if (!isOwner.value || requestedDocumentId === null || addingAuthorization.value) return

  const userId = parseAuthorizationUserId(newAuthorizationUserId.value)
  if (userId === null) {
    authorizationError.value = '请输入有效的用户 ID'
    toastError(authorizationError.value)
    return
  }
  if (userId === metadata.value?.ownerUserId) {
    authorizationError.value = '不能给文档所有者添加授权'
    toastError(authorizationError.value)
    return
  }

  addingAuthorization.value = true
  authorizationError.value = null
  try {
    await documentApi.upsertAuthorization(requestedDocumentId, userId, {
      permission: newAuthorizationPermission.value,
      enabled: newAuthorizationEnabled.value
    })
    toastSuccess('文档授权已保存')
    resetAuthorizationForm()
    await loadAuthorizations()
  } catch (cause) {
    authorizationError.value = getErrorMessage(cause, '保存文档授权失败')
    toastError(authorizationError.value)
  } finally {
    addingAuthorization.value = false
  }
}

/** 保存某条授权记录的权限和启用状态。 */
async function saveAuthorization(row: AuthorizationRow): Promise<boolean> {
  const requestedDocumentId = documentId.value
  if (!isOwner.value || requestedDocumentId === null || authorizationOperationUserId.value !== null) return false

  authorizationOperationUserId.value = row.userId
  authorizationError.value = null
  try {
    await documentApi.upsertAuthorization(requestedDocumentId, row.userId, {
      permission: row.draftPermission,
      enabled: row.draftEnabled
    })
    toastSuccess('文档权限已更新')
    await loadAuthorizations()
    return true
  } catch (cause) {
    // 请求失败时恢复服务端已知值，避免草稿状态看起来像已经生效。
    row.draftPermission = row.permission
    row.draftEnabled = row.enabled
    authorizationError.value = getErrorMessage(cause, '更新文档权限失败')
    toastError(authorizationError.value)
    return false
  } finally {
    authorizationOperationUserId.value = null
  }
}

/** 已撤销记录通过 PUT enabled=true 重新启用，并保留原权限选择。 */
async function enableAuthorization(row: AuthorizationRow): Promise<void> {
  const previousEnabled = row.draftEnabled
  row.draftEnabled = true
  if (!await saveAuthorization(row)) row.draftEnabled = previousEnabled
}

/** 调用软撤销接口；授权记录保留在列表中以便后续重新启用。 */
async function revokeAuthorization(row: AuthorizationRow): Promise<void> {
  if (!isOwner.value || documentId.value === null || authorizationOperationUserId.value !== null) return
  if (!await confirmAction({
    title: '撤销文档授权',
    content: `确定撤销用户 #${row.userId} 的文档访问权限吗？历史授权记录会保留。`,
    okText: '确认撤销',
    danger: true
  })) return

  authorizationOperationUserId.value = row.userId
  authorizationError.value = null
  try {
    await documentApi.revokeAuthorization(documentId.value, row.userId)
    toastSuccess('文档授权已撤销')
    await loadAuthorizations()
  } catch (cause) {
    authorizationError.value = getErrorMessage(cause, '撤销文档授权失败')
    toastError(authorizationError.value)
  } finally {
    authorizationOperationUserId.value = null
  }
}

/** 用当前认证用户的昵称、用户名作为 Awareness 展示名称。 */
function currentUserLabel(): string {
  return authStore.user?.nickname || authStore.user?.username || '当前用户'
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
  if (!value.owner) authorizationModalVisible.value = false
  if (value.deleted) {
    accessUnavailable.value = true
    error.value = DOCUMENT_UNAVAILABLE_MESSAGE
    authorizationModalVisible.value = false
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
  authorizationModalVisible.value = false
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
  authorizationModalVisible.value = false
  authorizationLoading.value = false
  authorizationError.value = null
  authorizations.value = []
  authorizationOperationUserId.value = null
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
  resetAuthorizationForm()

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
    client.updateLocalAwareness({
      user: {
        userId: authStore.user?.id ?? null,
        name: currentUserLabel(),
        // 当前后端不会回送本地 Session 元数据；本地先使用黑色占位，远端展示以后端元数据为准。
        color: '#000000'
      }
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
      <div class="document-editor-header-actions">
        <button
          v-if="isOwner && documentId !== null && !accessUnavailable"
          class="permission-button"
          type="button"
          @click="openAuthorizationModal"
        >
          <Users class="h-4 w-4" />
          权限管理
        </button>
        <div class="connection-status" :class="`is-${connectionState}`">
          <Cloud class="h-4 w-4" />
          {{ connectionLabel }}
        </div>
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

    <Teleport to="body">
      <Transition name="authorization-modal">
        <div
          v-if="authorizationModalVisible"
          class="authorization-modal-backdrop"
          role="presentation"
          @click.self="closeAuthorizationModal"
        >
          <div class="authorization-modal-card" role="dialog" aria-modal="true" aria-labelledby="authorization-modal-title">
            <header class="authorization-modal-header">
              <div>
                <span class="authorization-eyebrow">DOCUMENT ACCESS</span>
                <h2 id="authorization-modal-title">文档权限管理</h2>
                <p>所有者可以给指定用户授予正文的只读或可编辑权限。</p>
              </div>
              <button class="authorization-close-button" type="button" title="关闭" @click="closeAuthorizationModal">
                <X class="h-5 w-5" />
              </button>
            </header>

            <div class="authorization-modal-body">
              <form class="authorization-form" @submit.prevent="addAuthorization">
                <div class="authorization-form-heading">
                  <div>
                    <h3>添加或重新授权</h3>
                    <p>请输入已注册且启用的用户 ID。</p>
                  </div>
                  <UserPlus class="h-5 w-5" />
                </div>
                <div class="authorization-form-grid">
                  <label class="authorization-field">
                    <span>用户 ID</span>
                    <input
                      v-model="newAuthorizationUserId"
                      type="text"
                      inputmode="numeric"
                      autocomplete="off"
                      placeholder="例如 10002"
                      :disabled="addingAuthorization || authorizationOperationUserId !== null"
                    />
                  </label>
                  <label class="authorization-field">
                    <span>正文权限</span>
                    <select
                      v-model="newAuthorizationPermission"
                      :disabled="addingAuthorization || authorizationOperationUserId !== null"
                    >
                      <option value="READ">READ · 只读</option>
                      <option value="WRITE">WRITE · 可编辑正文</option>
                    </select>
                  </label>
                </div>
                <div class="authorization-form-actions">
                  <label class="authorization-checkbox">
                    <input v-model="newAuthorizationEnabled" type="checkbox" :disabled="addingAuthorization || authorizationOperationUserId !== null" />
                    <span>立即启用授权</span>
                  </label>
                  <button class="primary-button authorization-submit-button" type="submit" :disabled="addingAuthorization || authorizationLoading || authorizationOperationUserId !== null">
                    <Loader2 v-if="addingAuthorization" class="h-4 w-4 animate-spin" />
                    <UserPlus v-else class="h-4 w-4" />
                    {{ addingAuthorization ? '保存中…' : '保存授权' }}
                  </button>
                </div>
              </form>

              <div class="authorization-list-section">
                <div class="authorization-list-heading">
                  <div>
                    <h3>已有授权</h3>
                    <p>已撤销的历史记录仍会保留，可重新启用。</p>
                  </div>
                  <button class="authorization-refresh-button" type="button" :disabled="authorizationLoading" @click="loadAuthorizations">
                    <Loader2 v-if="authorizationLoading" class="h-4 w-4 animate-spin" />
                    <span v-else>刷新</span>
                  </button>
                </div>

                <div v-if="authorizationLoading && authorizations.length === 0" class="authorization-state">
                  <Loader2 class="h-5 w-5 animate-spin" /> 正在加载授权记录…
                </div>
                <div v-else-if="authorizationError" class="authorization-state is-error">
                  <CircleAlert class="h-5 w-5" />
                  <span>{{ authorizationError }}</span>
                  <button type="button" class="authorization-retry-button" @click="loadAuthorizations">重新加载</button>
                </div>
                <div v-else-if="authorizations.length === 0" class="authorization-state">
                  <Users class="h-5 w-5" /> 暂无直接授权用户
                </div>
                <div v-else class="authorization-rows">
                  <article v-for="authorization in authorizations" :key="authorization.userId" class="authorization-row">
                    <div class="authorization-row-heading">
                      <strong>用户 #{{ authorization.userId }}</strong>
                      <span class="authorization-status" :class="{ 'is-disabled': !authorization.draftEnabled }">
                        {{ authorization.draftEnabled ? '已启用' : '已撤销' }}
                      </span>
                    </div>
                    <div class="authorization-row-controls">
                      <label class="authorization-field authorization-row-permission">
                        <span>正文权限</span>
                        <select
                          v-model="authorization.draftPermission"
                          :disabled="authorizationOperationUserId !== null || addingAuthorization"
                        >
                          <option value="READ">READ · 只读</option>
                          <option value="WRITE">WRITE · 可编辑正文</option>
                        </select>
                      </label>
                      <label class="authorization-checkbox">
                        <input
                          v-model="authorization.draftEnabled"
                          type="checkbox"
                          :disabled="authorizationOperationUserId !== null || addingAuthorization"
                        />
                        <span>启用</span>
                      </label>
                      <button
                        class="authorization-save-button"
                        type="button"
                        :disabled="authorizationOperationUserId !== null || addingAuthorization || (authorization.draftPermission === authorization.permission && authorization.draftEnabled === authorization.enabled)"
                        @click="saveAuthorization(authorization)"
                      >
                        <Loader2 v-if="authorizationOperationUserId === authorization.userId" class="h-3.5 w-3.5 animate-spin" />
                        <Save v-else class="h-3.5 w-3.5" />
                        保存
                      </button>
                      <button
                        v-if="authorization.draftEnabled"
                        class="authorization-revoke-button"
                        type="button"
                        :disabled="authorizationOperationUserId !== null || addingAuthorization"
                        @click="revokeAuthorization(authorization)"
                      >
                        <Trash2 class="h-3.5 w-3.5" />
                        撤销
                      </button>
                      <button
                        v-else
                        class="authorization-enable-button"
                        type="button"
                        :disabled="authorizationOperationUserId !== null || addingAuthorization"
                        @click="enableAuthorization(authorization)"
                      >
                        <UserPlus class="h-3.5 w-3.5" />
                        重新启用
                      </button>
                    </div>
                    <p class="authorization-row-time">最后更新：{{ formatAuthorizationTime(authorization.updateTime) }}</p>
                  </article>
                </div>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </section>
</template>

<style scoped>
.document-editor-page { max-width: 1120px; margin: 0 auto; }
.document-editor-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.document-editor-header-actions { display: flex; align-items: center; gap: 12px; }
.back-button, .editor-toolbar button { display: inline-flex; align-items: center; gap: 7px; border: 1px solid var(--cn-border); border-radius: var(--cn-radius-sm); background: var(--cn-surface); color: var(--cn-text-soft); font-size: 12px; font-weight: 700; transition: all var(--cn-fast) var(--cn-ease); }
.back-button { padding: 8px 11px; }
.back-button:hover, .editor-toolbar button:hover:not(:disabled), .editor-toolbar button.active { border-color: var(--cn-border-strong); background: var(--cn-surface-muted); color: var(--cn-text); }
.permission-button { display: inline-flex; align-items: center; gap: 7px; border: 1px solid color-mix(in srgb, var(--cn-accent) 45%, var(--cn-border)); border-radius: var(--cn-radius-sm); background: color-mix(in srgb, var(--cn-accent) 10%, var(--cn-surface)); color: var(--cn-accent); padding: 8px 11px; font-size: 12px; font-weight: 700; transition: all var(--cn-fast) var(--cn-ease); }
.permission-button:hover { border-color: var(--cn-accent); background: color-mix(in srgb, var(--cn-accent) 16%, var(--cn-surface)); }
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
.authorization-modal-backdrop { position: fixed; inset: 0; z-index: 60; display: flex; align-items: center; justify-content: center; background: color-mix(in srgb, #0f172a 72%, transparent); padding: 20px; backdrop-filter: blur(7px); }
.authorization-modal-card { display: flex; width: min(760px, 100%); max-height: min(86vh, 760px); flex-direction: column; overflow: hidden; border: 1px solid var(--cn-border-strong); border-radius: var(--cn-radius-lg); background: var(--cn-surface); box-shadow: var(--cn-shadow-md); }
.authorization-modal-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; border-bottom: 1px solid var(--cn-border); padding: 22px 24px 18px; }
.authorization-eyebrow { color: var(--cn-text-muted); font-size: 10px; font-weight: 800; letter-spacing: .18em; }
.authorization-modal-header h2 { margin: 6px 0 5px; color: var(--cn-text); font-size: 20px; font-weight: 800; }
.authorization-modal-header p, .authorization-form-heading p, .authorization-list-heading p { margin: 0; color: var(--cn-text-muted); font-size: 12px; line-height: 1.55; }
.authorization-close-button { display: inline-flex; flex: 0 0 auto; border: 0; background: transparent; color: var(--cn-text-muted); padding: 4px; transition: color var(--cn-fast) var(--cn-ease); }
.authorization-close-button:hover { color: var(--cn-text); }
.authorization-modal-body { overflow-y: auto; padding: 20px 24px 24px; }
.authorization-form { border: 1px solid var(--cn-border); border-radius: var(--cn-radius-md); background: var(--cn-bg-subtle); padding: 16px; }
.authorization-form-heading, .authorization-list-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.authorization-form-heading > svg, .authorization-list-heading > svg { flex: 0 0 auto; color: var(--cn-accent); }
.authorization-form-heading h3, .authorization-list-heading h3 { margin: 0 0 3px; color: var(--cn-text); font-size: 14px; font-weight: 800; }
.authorization-form-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 12px; margin-top: 14px; }
.authorization-field { display: grid; gap: 6px; min-width: 0; }
.authorization-field > span { color: var(--cn-text-soft); font-size: 11px; font-weight: 750; }
.authorization-field input, .authorization-field select { width: 100%; min-height: 36px; border: 1px solid var(--cn-border); border-radius: var(--cn-radius-sm); outline: none; background: var(--cn-surface); color: var(--cn-text); padding: 8px 10px; font-size: 12px; transition: border-color var(--cn-fast) var(--cn-ease), background var(--cn-fast) var(--cn-ease); }
.authorization-field input:focus, .authorization-field select:focus { border-color: var(--cn-accent); background: var(--cn-surface-muted); }
.authorization-field input:disabled, .authorization-field select:disabled { cursor: wait; opacity: .6; }
.authorization-form-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 14px; }
.authorization-checkbox { display: inline-flex; align-items: center; gap: 7px; color: var(--cn-text-soft); font-size: 12px; }
.authorization-checkbox input { width: 15px; height: 15px; accent-color: var(--cn-accent); }
.authorization-submit-button { margin-top: 0; }
.authorization-list-section { margin-top: 22px; }
.authorization-refresh-button, .authorization-retry-button { display: inline-flex; align-items: center; gap: 5px; border: 1px solid var(--cn-border); border-radius: var(--cn-radius-sm); background: var(--cn-surface); color: var(--cn-text-muted); padding: 6px 9px; font-size: 11px; font-weight: 700; transition: all var(--cn-fast) var(--cn-ease); }
.authorization-refresh-button:hover:not(:disabled), .authorization-retry-button:hover { border-color: var(--cn-border-strong); background: var(--cn-surface-muted); color: var(--cn-text); }
.authorization-refresh-button:disabled { cursor: wait; opacity: .6; }
.authorization-state { display: flex; min-height: 120px; align-items: center; justify-content: center; gap: 8px; margin-top: 12px; border: 1px dashed var(--cn-border); border-radius: var(--cn-radius-md); background: var(--cn-bg-subtle); color: var(--cn-text-muted); font-size: 12px; text-align: center; }
.authorization-state.is-error { flex-wrap: wrap; color: var(--cn-danger); }
.authorization-rows { display: grid; gap: 10px; margin-top: 12px; }
.authorization-row { border: 1px solid var(--cn-border); border-radius: var(--cn-radius-md); background: var(--cn-surface); padding: 13px 14px 11px; }
.authorization-row-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.authorization-row-heading strong { color: var(--cn-text); font-size: 13px; font-weight: 800; }
.authorization-status { border: 1px solid color-mix(in srgb, var(--cn-success) 45%, var(--cn-border)); border-radius: 999px; background: color-mix(in srgb, var(--cn-success) 10%, transparent); color: var(--cn-success); padding: 3px 7px; font-size: 10px; font-weight: 750; }
.authorization-status.is-disabled { border-color: var(--cn-border); background: var(--cn-bg-subtle); color: var(--cn-text-muted); }
.authorization-row-controls { display: flex; align-items: flex-end; gap: 8px; margin-top: 11px; }
.authorization-row-permission { flex: 1 1 220px; }
.authorization-row-controls > .authorization-checkbox { min-height: 36px; padding: 0 4px; }
.authorization-save-button, .authorization-revoke-button, .authorization-enable-button { display: inline-flex; min-height: 36px; align-items: center; justify-content: center; gap: 5px; border: 1px solid var(--cn-border); border-radius: var(--cn-radius-sm); background: var(--cn-surface); color: var(--cn-text-soft); padding: 7px 9px; font-size: 11px; font-weight: 750; white-space: nowrap; transition: all var(--cn-fast) var(--cn-ease); }
.authorization-save-button:hover:not(:disabled), .authorization-enable-button:hover:not(:disabled) { border-color: var(--cn-accent); background: color-mix(in srgb, var(--cn-accent) 10%, var(--cn-surface)); color: var(--cn-accent); }
.authorization-revoke-button { border-color: color-mix(in srgb, var(--cn-danger) 35%, var(--cn-border)); color: var(--cn-danger); }
.authorization-revoke-button:hover:not(:disabled) { background: color-mix(in srgb, var(--cn-danger) 10%, var(--cn-surface)); }
.authorization-save-button:disabled, .authorization-revoke-button:disabled, .authorization-enable-button:disabled { cursor: wait; opacity: .45; }
.authorization-row-time { margin: 9px 0 0; color: var(--cn-text-faint); font-size: 10px; }
:deep(.document-tiptap-editor) { min-height: 62vh; outline: none; padding: 34px clamp(22px, 5vw, 70px); color: var(--cn-text); font-size: 16px; line-height: 1.8; }
:deep(.document-tiptap-editor > :first-child) { margin-top: 0; }
:deep(.document-tiptap-editor h1), :deep(.document-tiptap-editor h2), :deep(.document-tiptap-editor h3) { color: var(--cn-text); line-height: 1.3; }
:deep(.document-tiptap-editor p.is-editor-empty:first-child::before) { float: left; height: 0; color: var(--cn-text-faint); content: '开始记录你的想法…'; pointer-events: none; }
:deep(.document-resource-reference) { display: inline-block; border-radius: 4px; background: color-mix(in srgb, var(--cn-accent) 12%, transparent); color: var(--cn-accent); padding: 0 4px; font-size: .92em; font-weight: 700; }
@media (max-width: 640px) { .document-editor-header { align-items: flex-start; flex-direction: column; } .document-editor-header-actions { width: 100%; justify-content: space-between; } .create-card { margin-top: 20px; padding: 26px 20px; } .document-title-row { align-items: flex-start; flex-direction: column; gap: 2px; } .document-title-input { font-size: 22px; } .editor-toolbar { flex-wrap: wrap; } .collaborator-count { margin-left: 0; } .authorization-modal-backdrop { align-items: flex-end; padding: 10px; } .authorization-modal-card { max-height: 92vh; } .authorization-modal-header, .authorization-modal-body { padding-inline: 16px; } .authorization-form-grid { grid-template-columns: 1fr; } .authorization-form-actions { align-items: flex-start; flex-direction: column; } .authorization-submit-button { width: 100%; } .authorization-row-controls { align-items: stretch; flex-wrap: wrap; } .authorization-row-permission { flex-basis: 100%; } .authorization-row-controls > .authorization-checkbox { flex: 1 1 auto; } :deep(.document-tiptap-editor) { padding: 24px 20px; } }
</style>
