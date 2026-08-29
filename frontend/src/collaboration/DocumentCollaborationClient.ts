import * as Y from 'yjs'
import {
  Awareness,
  applyAwarenessUpdate,
  encodeAwarenessUpdate,
  removeAwarenessStates
} from 'y-protocols/awareness'
import {
  DocumentWsFrameType,
  createDocumentWsControl,
  createDocumentWsRequestId,
  decodeDocumentWsFrame,
  encodeDocumentWsFrame,
  parseDocumentWsControl,
  type DocumentWsControlMessage
} from '@/collaboration/documentProtocol'

export type DocumentConnectionState = 'connecting' | 'synchronizing' | 'synced' | 'reconnecting' | 'closed' | 'error'

export interface DocumentCollaborationClientOptions {
  /** 要加入协作 Room 的文档 ID；example: {@code 42} */
  documentId: number
  /** 用于 WebSocket bearer 子协议的访问令牌；example: {@code 'eyJhbGciOi...'} */
  accessToken: string
  /** 承载文档正文和 Yjs 状态的本地文档对象；example: {@code new Y.Doc()} */
  ydoc: Y.Doc
  /** 连接状态变化回调；example: {@code (state) => console.log(state)} */
  onStateChange?: (state: DocumentConnectionState, message?: string) => void
  /** 在线协作者数量变化回调；example: {@code (count) => collaboratorCount.value = count} */
  onAwarenessChange?: (count: number) => void
}

interface PendingUpdate {
  /** 客户端为该更新生成的幂等 UUID；example: {@code '550e8400-e29b-41d4-a716-446655440000'} */
  id: string
  /** 尚未收到 UPDATE_ACCEPTED 的 Yjs 二进制更新；example: {@code new Uint8Array([1, 2, 127])} */
  payload: Uint8Array
}

interface PendingBootstrapFrame {
  /** Bootstrap 帧类型；当前只允许 SNAPSHOT_STATE 或 BOOTSTRAP_UPDATE。 */
  type: DocumentWsFrameType.SNAPSHOT_STATE | DocumentWsFrameType.BOOTSTRAP_UPDATE
  /** 从服务端收到的不可变 Yjs 二进制负载。 */
  payload: Uint8Array
}

/** 标记服务端下发的 Yjs 更新，避免写回客户端更新队列。 */
const REMOTE_UPDATE_ORIGIN = Symbol('document-remote-update')
/** 标记服务端下发的 awareness 更新，避免再次广播给服务端。 */
const REMOTE_AWARENESS_ORIGIN = Symbol('document-remote-awareness')
/** 标记本地 awareness 清理动作，避免将离线状态当作普通变更发送。 */
const LOCAL_AWARENESS_ORIGIN = Symbol('document-local-awareness')
/** 重连退避等待时间上限；示例：`10000` 毫秒。 */
const MAX_RECONNECT_DELAY_MS = 10_000

/**
 * 将项目的文档 WebSocket 协议连接到一个 Y.Doc。
 *
 * 此处刻意不使用 y-websocket、Hocuspocus 或托管 Tiptap 服务：Spring 端点仍是唯一的网络事实来源。
 */
export class DocumentCollaborationClient {
  /** 当前 Yjs 文档对应的 awareness 状态集合。 */
  readonly awareness: Awareness

  /** 要加入协作 Room 的文档 ID；example: {@code 42} */
  private readonly documentId: number
  /** 用于 WebSocket bearer 子协议的访问令牌。 */
  private readonly accessToken: string
  /** 承载文档正文、快照和本地更新的 Yjs 文档。 */
  private readonly ydoc: Y.Doc
  /** 连接状态变化回调，由页面层更新同步提示。 */
  private readonly onStateChange?: (state: DocumentConnectionState, message?: string) => void
  /** awareness 在线人数变化回调，由页面层更新协作者数量。 */
  private readonly onAwarenessChange?: (count: number) => void
  /** 尚未收到 UPDATE_ACCEPTED 的本地更新，按客户端更新 UUID 索引。 */
  private readonly pendingUpdates = new Map<string, PendingUpdate>()
  /** 当前同步尝试尚未完成最终构建的 Snapshot/Bootstrap 帧。 */
  private readonly pendingBootstrapFrames: PendingBootstrapFrame[] = []
  /** 当前同步尝试期间收到的远端 CRDT_UPDATE，不能在 Bootstrap 完成前直接应用。 */
  private readonly pendingRemoteUpdates: Uint8Array[] = []
  /** 当前 WebSocket 实例；未连接或连接已关闭时为 `null`。 */
  private socket: WebSocket | null = null
  /** 当前唯一的重连定时器；没有待重连任务时为 `null`。 */
  private reconnectTimer: ReturnType<typeof window.setTimeout> | null = null
  /** 已连续发起的重连次数，用于计算指数退避时长。 */
  private reconnectAttempts = 0
  /** 客户端是否已经释放，不再允许创建连接或处理事件。 */
  private disposed = false
  /** 服务端是否已经发送 SYNC_COMPLETE，可以发送本地更新。 */
  private synchronized = false
  /** 当前对外公布的连接状态；初始值为 `closed`。 */
  private currentState: DocumentConnectionState = 'closed'

  /** 创建 Y.Doc 与 WebSocket/awareness 事件之间的桥接客户端。 */
  constructor(options: DocumentCollaborationClientOptions) {
    this.documentId = options.documentId
    this.accessToken = options.accessToken
    this.ydoc = options.ydoc
    this.onStateChange = options.onStateChange
    this.onAwarenessChange = options.onAwarenessChange
    this.awareness = new Awareness(this.ydoc)
    this.ydoc.on('update', this.handleDocumentUpdate)
    this.awareness.on('update', this.handleAwarenessUpdate)
    this.awareness.on('change', this.handleAwarenessChange)
  }

  /** 建立 bearer 子协议连接并发送 JOIN；已连接或已销毁时保持幂等。 */
  connect(): void {
    if (this.disposed || this.socket) return
    this.setState(this.reconnectAttempts > 0 ? 'reconnecting' : 'connecting')

    /** 本次连接尝试创建的 WebSocket；回调通过它判断事件是否属于当前连接。 */
    const socket = new WebSocket(documentWebSocketUrl(), [`bearer.${this.accessToken}`])
    socket.binaryType = 'arraybuffer'
    socket.onopen = () => {
      if (this.socket !== socket || this.disposed) return
      this.reconnectAttempts = 0
      this.synchronized = false
      // 每次 JOIN 都重新收集 Bootstrap；本地 pendingUpdates 需要跨重连保留，
      // 而旧连接尚未完成的接收缓存由新一轮 Bootstrap 重新覆盖。
      this.pendingBootstrapFrames.length = 0
      this.pendingRemoteUpdates.length = 0
      this.setState('synchronizing')
      this.sendControl(createDocumentWsControl('JOIN_DOCUMENT', {
        requestId: createDocumentWsRequestId(),
        documentId: this.documentId
      }))
    }
    socket.onmessage = event => this.handleSocketMessage(socket, event)
    socket.onerror = () => {
      // 下方 close 回调会承担重试职责，同时保留浏览器提供的原始错误细节。
    }
    socket.onclose = () => this.handleSocketClosed(socket)
    this.socket = socket
  }

  /** 更新本地 awareness，并在已同步连接上立即广播。 */
  setLocalAwareness(state: Record<string, unknown>): void {
    this.awareness.setLocalState(state)
    if (this.synchronized) this.sendLocalAwareness()
  }

  /** 停止重连、通知服务端离开并解除 Yjs/awareness 监听。 */
  dispose(): void {
    if (this.disposed) return
    this.disposed = true
    if (this.reconnectTimer !== null) {
      window.clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.sendControl(createDocumentWsControl('LEAVE_DOCUMENT', {
      requestId: createDocumentWsRequestId(),
      documentId: this.documentId
    }))
    this.socket?.close(1000, 'document editor disposed')
    this.socket = null
    this.ydoc.off('update', this.handleDocumentUpdate)
    this.awareness.off('update', this.handleAwarenessUpdate)
    this.awareness.off('change', this.handleAwarenessChange)
    this.pendingBootstrapFrames.length = 0
    this.pendingRemoteUpdates.length = 0
    removeAwarenessStates(this.awareness, [this.ydoc.clientID], LOCAL_AWARENESS_ORIGIN)
    this.awareness.destroy()
    this.setState('closed')
  }

  /** 将本地 Yjs 更新放入待确认队列，直到服务端返回 UPDATE_ACCEPTED。 */
  private readonly handleDocumentUpdate = (update: Uint8Array, origin: unknown): void => {
    if (origin === REMOTE_UPDATE_ORIGIN || this.disposed) return
    /** 用于服务端确认和本地重连重放的客户端更新 UUID。 */
    const id = createDocumentWsRequestId()
    // 每个本地 Yjs 变更都会保留到服务端发送 UPDATE_ACCEPTED；因此 bootstrap 期间的变更
    // 能在首次 SYNC_COMPLETE 和后续重连后继续发送。
    this.pendingUpdates.set(id, { id, payload: update.slice() })
    if (this.synchronized) this.sendPendingUpdate(this.pendingUpdates.get(id)!)
  }

  /** 仅在本地状态变化且连接已同步时发送 awareness。 */
  private readonly handleAwarenessUpdate = (_changes: unknown, origin: unknown): void => {
    if (origin !== REMOTE_AWARENESS_ORIGIN && this.synchronized && !this.disposed) {
      this.sendLocalAwareness()
    }
  }

  /** 把 awareness 状态数量通知页面层更新协作者显示。 */
  private readonly handleAwarenessChange = (): void => {
    this.onAwarenessChange?.(this.awareness.getStates().size)
  }

  /** 区分文本控制帧和二进制帧，并把协议错误转为连接 error 状态。 */
  private handleSocketMessage(socket: WebSocket, event: MessageEvent<string | ArrayBuffer>): void {
    if (this.socket !== socket || this.disposed) return
    try {
      if (typeof event.data === 'string') {
        this.handleControl(parseDocumentWsControl(event.data))
        return
      }
      if (event.data instanceof ArrayBuffer) this.handleBinary(decodeDocumentWsFrame(event.data))
    } catch (error) {
      /** 面向页面层展示的协议处理错误文本。 */
      const message = error instanceof Error ? error.message : '文档同步协议处理失败'
      this.fail(message)
    }
  }

  /** 处理同步完成、更新确认、心跳和服务端错误控制帧。 */
  private handleControl(control: DocumentWsControlMessage): void {
    switch (control.type) {
      case 'SYNC_COMPLETE':
        // SYNC_COMPLETE 是服务端已经排完全部 Bootstrap 帧的结束边界；先完成最终
        // Y.Doc 构建，再允许本地编辑进入服务端，避免把同步中的半成品暴露给发送链路。
        this.applyPendingBootstrap()
        this.synchronized = true
        this.setState('synced')
        // 最终构建完成后才发送本地队列中的变更，从而让它们在服务端快照、历史更新
        // 和同步期间收到的远端更新之后应用。
        this.pendingUpdates.forEach(update => this.sendPendingUpdate(update))
        this.sendLocalAwareness()
        break
      case 'UPDATE_ACCEPTED':
        if (control.clientUpdateId) this.pendingUpdates.delete(control.clientUpdateId)
        break
      case 'PING':
        this.sendControl(createDocumentWsControl('PONG', {
          requestId: control.requestId,
          documentId: this.documentId
        }))
        break
      case 'ERROR':
        this.fail(control.message || control.code || '文档服务拒绝了当前连接')
        this.socket?.close(1008, 'document protocol error')
        break
      default:
        break
    }
  }

  /** 把服务端二进制帧按 Bootstrap 和 Remote Queue 顺序应用到 Y.Doc。 */
  private applyPendingBootstrap(): void {
    for (const frame of this.pendingBootstrapFrames) {
      // Snapshot 与 Bootstrap Update 都来自服务端，统一使用远端 origin，避免再次生成 CLIENT_UPDATE。
      Y.applyUpdate(this.ydoc, frame.payload, REMOTE_UPDATE_ORIGIN)
    }
    for (const update of this.pendingRemoteUpdates) {
      // Remote Queue 允许与 Snapshot/OpLog/Redis 重复，重复合并交给 Yjs 处理。
      Y.applyUpdate(this.ydoc, update, REMOTE_UPDATE_ORIGIN)
    }
    this.pendingBootstrapFrames.length = 0
    this.pendingRemoteUpdates.length = 0
  }

  /** 把服务端快照、历史、协作者更新接入同步缓存或 Y.Doc。 */
  private handleBinary(frame: ReturnType<typeof decodeDocumentWsFrame>): void {
    switch (frame.type) {
      case DocumentWsFrameType.SNAPSHOT_STATE:
      case DocumentWsFrameType.BOOTSTRAP_UPDATE:
        if (!this.synchronized) {
          // Snapshot 与 Bootstrap Update 必须等到结束边界后再参与最终构建。
          this.pendingBootstrapFrames.push({ type: frame.type, payload: frame.payload.slice() })
          break
        }
        // 正常 ACTIVE 阶段不应再收到 Bootstrap 帧；即使收到，也保持 Yjs 远端合并语义。
        Y.applyUpdate(this.ydoc, frame.payload, REMOTE_UPDATE_ORIGIN)
        break
      case DocumentWsFrameType.CRDT_UPDATE:
        if (!this.synchronized) {
          // 这是 Tlive 之后到达的远端更新，先保存；它可能与 Redis 或 OpLog 重复，不能丢弃。
          this.pendingRemoteUpdates.push(frame.payload.slice())
          break
        }
        // ACTIVE 阶段的实时更新可以直接应用；仍使用远端 origin，避免回写服务端。
        Y.applyUpdate(this.ydoc, frame.payload, REMOTE_UPDATE_ORIGIN)
        break
      case DocumentWsFrameType.AWARENESS:
        applyAwarenessUpdate(this.awareness, frame.payload, REMOTE_AWARENESS_ORIGIN)
        break
      default:
        break
    }
  }

  /** 连接关闭后清除同步标志，并进入受控的指数退避重连。 */
  private handleSocketClosed(socket: WebSocket): void {
    if (this.socket === socket) this.socket = null
    this.synchronized = false
    if (this.disposed || this.currentState === 'error') return
    this.scheduleReconnect()
  }

  /** 安排唯一的指数退避定时器，避免并发创建多个重连连接。 */
  private scheduleReconnect(): void {
    if (this.reconnectTimer !== null || this.disposed) return
    // 每次失败连接都会增加等待时间，上限十秒；同一时刻只保留一个重试定时器。
    /** 本次重连等待时间，按指数退避计算并限制在十秒以内。 */
    const delay = Math.min(500 * 2 ** this.reconnectAttempts, MAX_RECONNECT_DELAY_MS)
    this.reconnectAttempts += 1
    this.setState('reconnecting')
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, delay)
  }

  /** 发送一条仍在等待服务端确认的客户端更新。 */
  private sendPendingUpdate(update: PendingUpdate): void {
    if (!this.synchronized || !this.isSocketOpen()) return
    this.socket!.send(encodeDocumentWsFrame(DocumentWsFrameType.CLIENT_UPDATE, update.id, update.payload))
  }

  /** 发送当前客户端的 awareness 状态，不写入持久化更新队列。 */
  private sendLocalAwareness(): void {
    if (!this.synchronized || !this.isSocketOpen()) return
    /** 当前用户 awareness 的二进制编码，不进入 Yjs 持久化更新队列。 */
    const payload = encodeAwarenessUpdate(this.awareness, [this.ydoc.clientID])
    this.socket!.send(encodeDocumentWsFrame(
      DocumentWsFrameType.AWARENESS,
      createDocumentWsRequestId(),
      payload
    ))
  }

  /** 发送控制 JSON；连接未打开时静默等待后续重连。 */
  private sendControl(control: DocumentWsControlMessage): void {
    if (!this.isSocketOpen()) return
    this.socket!.send(JSON.stringify(control))
  }

  /** 判断当前 WebSocket 是否处于可发送状态。 */
  private isSocketOpen(): boolean {
    return this.socket?.readyState === WebSocket.OPEN
  }

  /** 记录不可恢复的协议错误并通知页面层。 */
  private fail(message: string): void {
    this.setState('error', message)
  }

  /** 更新连接状态并向页面层发出状态变化通知。 */
  private setState(state: DocumentConnectionState, message?: string): void {
    this.currentState = state
    this.onStateChange?.(state, message)
  }
}

/** 按环境变量或当前页面 host 解析文档 WebSocket 地址。 */
function documentWebSocketUrl(): string {
  /** 环境变量中配置的 WebSocket 地址；未配置时回退到当前页面 host。 */
  const configured = import.meta.env.VITE_DOCUMENT_WS_URL?.trim()
  if (configured) return configured
  /** 根据当前页面协议选择 ws 或 wss。 */
  const scheme = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${scheme}//${window.location.host}/ws/document`
}
