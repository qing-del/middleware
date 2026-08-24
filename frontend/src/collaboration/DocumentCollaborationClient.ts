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
  documentId: number
  accessToken: string
  ydoc: Y.Doc
  onStateChange?: (state: DocumentConnectionState, message?: string) => void
  onAwarenessChange?: (count: number) => void
}

interface PendingUpdate {
  id: string
  payload: Uint8Array
}

const REMOTE_UPDATE_ORIGIN = Symbol('document-remote-update')
const REMOTE_AWARENESS_ORIGIN = Symbol('document-remote-awareness')
const LOCAL_AWARENESS_ORIGIN = Symbol('document-local-awareness')
const MAX_RECONNECT_DELAY_MS = 10_000

/**
 * 将项目的文档 WebSocket 协议连接到一个 Y.Doc。
 *
 * 此处刻意不使用 y-websocket、Hocuspocus 或托管 Tiptap 服务：Spring 端点仍是唯一的网络事实来源。
 */
export class DocumentCollaborationClient {
  readonly awareness: Awareness

  private readonly documentId: number
  private readonly accessToken: string
  private readonly ydoc: Y.Doc
  private readonly onStateChange?: (state: DocumentConnectionState, message?: string) => void
  private readonly onAwarenessChange?: (count: number) => void
  private readonly pendingUpdates = new Map<string, PendingUpdate>()
  private socket: WebSocket | null = null
  private reconnectTimer: ReturnType<typeof window.setTimeout> | null = null
  private reconnectAttempts = 0
  private disposed = false
  private synchronized = false
  private currentState: DocumentConnectionState = 'closed'

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

  connect(): void {
    if (this.disposed || this.socket) return
    this.setState(this.reconnectAttempts > 0 ? 'reconnecting' : 'connecting')

    const socket = new WebSocket(documentWebSocketUrl(), [`bearer.${this.accessToken}`])
    socket.binaryType = 'arraybuffer'
    socket.onopen = () => {
      if (this.socket !== socket || this.disposed) return
      this.reconnectAttempts = 0
      this.synchronized = false
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

  setLocalAwareness(state: Record<string, unknown>): void {
    this.awareness.setLocalState(state)
    if (this.synchronized) this.sendLocalAwareness()
  }

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
    removeAwarenessStates(this.awareness, [this.ydoc.clientID], LOCAL_AWARENESS_ORIGIN)
    this.awareness.destroy()
    this.setState('closed')
  }

  private readonly handleDocumentUpdate = (update: Uint8Array, origin: unknown): void => {
    if (origin === REMOTE_UPDATE_ORIGIN || this.disposed) return
    const id = createDocumentWsRequestId()
    // 每个本地 Yjs 变更都会保留到服务端发送 UPDATE_ACCEPTED；因此 bootstrap 期间的变更
    // 能在首次 SYNC_COMPLETE 和后续重连后继续发送。
    this.pendingUpdates.set(id, { id, payload: update.slice() })
    if (this.synchronized) this.sendPendingUpdate(this.pendingUpdates.get(id)!)
  }

  private readonly handleAwarenessUpdate = (_changes: unknown, origin: unknown): void => {
    if (origin !== REMOTE_AWARENESS_ORIGIN && this.synchronized && !this.disposed) {
      this.sendLocalAwareness()
    }
  }

  private readonly handleAwarenessChange = (): void => {
    this.onAwarenessChange?.(this.awareness.getStates().size)
  }

  private handleSocketMessage(socket: WebSocket, event: MessageEvent<string | ArrayBuffer>): void {
    if (this.socket !== socket || this.disposed) return
    try {
      if (typeof event.data === 'string') {
        this.handleControl(parseDocumentWsControl(event.data))
        return
      }
      if (event.data instanceof ArrayBuffer) this.handleBinary(decodeDocumentWsFrame(event.data))
    } catch (error) {
      const message = error instanceof Error ? error.message : '文档同步协议处理失败'
      this.fail(message)
    }
  }

  private handleControl(control: DocumentWsControlMessage): void {
    switch (control.type) {
      case 'SYNC_COMPLETE':
        this.synchronized = true
        this.setState('synced')
        // bootstrap 帧已经写入 Y.Doc，此时才发送本地队列中的变更，
        // 从而让它们在服务端快照和历史更新之后应用。
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

  private handleBinary(frame: ReturnType<typeof decodeDocumentWsFrame>): void {
    switch (frame.type) {
      case DocumentWsFrameType.SNAPSHOT_STATE:
      case DocumentWsFrameType.BOOTSTRAP_UPDATE:
      case DocumentWsFrameType.CRDT_UPDATE:
        // 为服务端下发的更新标记独立来源，写入 Y.Doc 时便不会生成新的 CLIENT_UPDATE，
        // 从而避免同一段 Yjs 字节又被回传给服务端。
        Y.applyUpdate(this.ydoc, frame.payload, REMOTE_UPDATE_ORIGIN)
        break
      case DocumentWsFrameType.AWARENESS:
        applyAwarenessUpdate(this.awareness, frame.payload, REMOTE_AWARENESS_ORIGIN)
        break
      default:
        break
    }
  }

  private handleSocketClosed(socket: WebSocket): void {
    if (this.socket === socket) this.socket = null
    this.synchronized = false
    if (this.disposed || this.currentState === 'error') return
    this.scheduleReconnect()
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer !== null || this.disposed) return
    // 每次失败连接都会增加等待时间，上限十秒；同一时刻只保留一个重试定时器。
    const delay = Math.min(500 * 2 ** this.reconnectAttempts, MAX_RECONNECT_DELAY_MS)
    this.reconnectAttempts += 1
    this.setState('reconnecting')
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, delay)
  }

  private sendPendingUpdate(update: PendingUpdate): void {
    if (!this.synchronized || !this.isSocketOpen()) return
    this.socket!.send(encodeDocumentWsFrame(DocumentWsFrameType.CLIENT_UPDATE, update.id, update.payload))
  }

  private sendLocalAwareness(): void {
    if (!this.synchronized || !this.isSocketOpen()) return
    const payload = encodeAwarenessUpdate(this.awareness, [this.ydoc.clientID])
    this.socket!.send(encodeDocumentWsFrame(
      DocumentWsFrameType.AWARENESS,
      createDocumentWsRequestId(),
      payload
    ))
  }

  private sendControl(control: DocumentWsControlMessage): void {
    if (!this.isSocketOpen()) return
    this.socket!.send(JSON.stringify(control))
  }

  private isSocketOpen(): boolean {
    return this.socket?.readyState === WebSocket.OPEN
  }

  private fail(message: string): void {
    this.setState('error', message)
  }

  private setState(state: DocumentConnectionState, message?: string): void {
    this.currentState = state
    this.onStateChange?.(state, message)
  }
}

function documentWebSocketUrl(): string {
  const configured = import.meta.env.VITE_DOCUMENT_WS_URL?.trim()
  if (configured) return configured
  const scheme = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${scheme}//${window.location.host}/ws/document`
}
