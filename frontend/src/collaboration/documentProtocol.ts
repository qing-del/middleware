/** 文档 WebSocket 控制帧和二进制帧共用的协议版本号。 */
export const DOCUMENT_WS_PROTOCOL_VERSION = 1
/** 二进制帧固定头长度：1 字节版本 + 1 字节类型 + 16 字节 UUID。 */
export const DOCUMENT_WS_HEADER_BYTES = 18

export type DocumentWsControlType =
  | 'JOIN_DOCUMENT'
  | 'JOIN_ACCEPTED'
  | 'SYNC_COMPLETE'
  | 'LEAVE_DOCUMENT'
  | 'UPDATE_ACCEPTED'
  | 'AWARENESS_META'
  | 'ERROR'
  | 'PING'
  | 'PONG'

/** Awareness 元数据控制帧的生命周期动作；服务端只发送这两个值。 */
export type DocumentWsAwarenessAction = 'UPSERT' | 'REMOVE'

export interface DocumentWsControlMessage {
  /** 控制帧协议版本；example: {@code 1} */
  protocolVersion: number
  /** 控制消息类型；example: {@code 'JOIN_DOCUMENT'} */
  type: DocumentWsControlType
  /** 请求关联 UUID；example: {@code '550e8400-e29b-41d4-a716-446655440000'} */
  requestId: string | null
  /** 关联的文档 ID；全局错误时为 null；example: {@code 42} */
  documentId: number | null
  /** 客户端 Yjs 更新 UUID；非更新消息时为 null；example: {@code '6ba7b810-9dad-11d1-80b4-00c04fd430c8'} */
  clientUpdateId: string | null
  /** Redis Stream 条目 ID；确认服务端接收位置；example: {@code '1756080000000-0'} */
  redisOpId: string | null
  /** 机器可判断的错误或状态编码；example: {@code 'DOCUMENT_NOT_FOUND'} */
  code: string | null
  /** 面向客户端展示的附加消息；example: {@code '文档不存在'} */
  message: string | null
  /** JOIN_DOCUMENT 绑定的 Yjs Awareness client ID；其他普通控制帧通常不携带。 */
  awarenessClientId?: number | null
  /** AWARENESS_META 的新增、更新或移除动作。 */
  action?: DocumentWsAwarenessAction | null
  /** AWARENESS_META 关联的服务端 WebSocket Session ID。 */
  sessionId?: string | null
  /** AWARENESS_META 关联的认证用户 ID；REMOVE 时为空。 */
  userId?: number | null
  /** AWARENESS_META 关联的认证用户名；REMOVE 时为空。 */
  name?: string | null
  /** AWARENESS_META 关联的服务端颜色；REMOVE 时为空。 */
  color?: string | null
}

/** JOIN_DOCUMENT 的强类型控制帧；服务端要求 Awareness client ID 必须存在。 */
export interface DocumentWsJoinDocumentMessage extends DocumentWsControlMessage {
  type: 'JOIN_DOCUMENT'
  awarenessClientId: number
}

/** 服务端下发的 Awareness Session 元数据控制帧的公共字段。 */
interface DocumentWsAwarenessMetaBase extends DocumentWsControlMessage {
  type: 'AWARENESS_META'
  requestId: string
  documentId: number
  awarenessClientId: number
  sessionId: string
}

/** 服务端下发的 Awareness Session 元数据控制帧；按 action 区分字段是否必填。 */
export type DocumentWsAwarenessMeta =
  | (DocumentWsAwarenessMetaBase & {
      action: 'UPSERT'
      userId: number
      name: string
      color: string
    })
  | (DocumentWsAwarenessMetaBase & {
      action: 'REMOVE'
      userId: null
      name: null
      color: null
    })

export enum DocumentWsFrameType {
  /** 客户端提交的 Yjs 更新。 */
  CLIENT_UPDATE = 0x01,
  /** 服务端广播的其他客户端 Yjs 更新。 */
  CRDT_UPDATE = 0x02,
  /** 服务端发送的完整 Yjs 快照状态。 */
  SNAPSHOT_STATE = 0x03,
  /** 服务端发送的快照之后历史更新。 */
  BOOTSTRAP_UPDATE = 0x04,
  /** 客户端或服务端发送的 awareness 在线状态。 */
  AWARENESS = 0x05
}

export interface DocumentWsBinaryFrame {
  /** 二进制帧类型；example: {@code DocumentWsFrameType.CLIENT_UPDATE} */
  type: DocumentWsFrameType
  /** 用于关联事件的 UUID；example: {@code '550e8400-e29b-41d4-a716-446655440000'} */
  eventId: string
  /** 未解析的 Yjs 或 awareness 二进制负载；example: {@code new Uint8Array([1, 2, 127])} */
  payload: Uint8Array
}

function uuidBytes(value: string): Uint8Array {
  // 线上的 UUID 固定为 16 字节，先去掉短横线并严格校验十六进制格式。
  /** 去掉短横线后的 32 位十六进制 UUID。 */
  const compact = value.replace(/-/g, '')
  if (!/^[0-9a-f]{32}$/i.test(compact)) {
    throw new Error('文档 WebSocket 协议 UUID 无效')
  }

  /** 按协议顺序存放 UUID 的 16 个字节。 */
  const result = new Uint8Array(16)
  for (let index = 0; index < result.length; index += 1) {
    result[index] = Number.parseInt(compact.slice(index * 2, index * 2 + 2), 16)
  }
  return result
}

function bytesUuid(bytes: Uint8Array): string {
  // 解码时重新插入 UUID 分段，保持事件 ID 在客户端与服务端之间可关联。
  if (bytes.length !== 16) throw new Error('文档 WebSocket 协议 UUID 长度无效')
  /** 不带短横线的完整小写十六进制 UUID。 */
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

/** 生成控制帧和 CLIENT_UPDATE 共用的请求关联 ID。 */
export function createDocumentWsRequestId(): string {
  return crypto.randomUUID()
}

type DocumentWsControlOverrides = Partial<Omit<DocumentWsControlMessage, 'protocolVersion' | 'type'>>

/** JOIN_DOCUMENT 必须在类型层携带当前 Yjs Awareness client ID。 */
export function createDocumentWsControl(
  type: 'JOIN_DOCUMENT',
  overrides: DocumentWsControlOverrides & Pick<DocumentWsJoinDocumentMessage, 'awarenessClientId'>
): DocumentWsJoinDocumentMessage

/** 其他控制帧沿用原有的默认字段和 requestId 行为。 */
export function createDocumentWsControl(
  type: Exclude<DocumentWsControlType, 'JOIN_DOCUMENT'>,
  overrides?: DocumentWsControlOverrides
): DocumentWsControlMessage

/** 创建带协议版本、默认空字段和唯一 requestId 的控制帧。 */
export function createDocumentWsControl(
  type: DocumentWsControlType,
  overrides: DocumentWsControlOverrides = {}
): DocumentWsControlMessage {
  if (type === 'JOIN_DOCUMENT' && !isPositiveSafeInteger(overrides.awarenessClientId)) {
    throw new Error('JOIN_DOCUMENT 必须携带有效的 awarenessClientId')
  }
  return {
    protocolVersion: DOCUMENT_WS_PROTOCOL_VERSION,
    type,
    // 服务端会校验每个控制帧的 requestId，LEAVE 和 PONG 也不例外。
    requestId: createDocumentWsRequestId(),
    documentId: null,
    clientUpdateId: null,
    redisOpId: null,
    code: null,
    message: null,
    ...overrides
  }
}

/** 按 Java codec 的固定 18 字节头编码二进制帧，payload 保持原始字节。 */
export function encodeDocumentWsFrame(
  type: DocumentWsFrameType,
  eventId: string,
  payload: Uint8Array
): ArrayBuffer {
  // 二进制帧固定在不透明的 Yjs/awareness payload 前写入协议版本、一个帧类型字节和 16 字节 UUID；
  // Java codec 按这个精确布局读取。
  /** 按固定头和原始 payload 分配的完整二进制帧。 */
  const frame = new Uint8Array(DOCUMENT_WS_HEADER_BYTES + payload.byteLength)
  frame[0] = DOCUMENT_WS_PROTOCOL_VERSION
  frame[1] = type
  frame.set(uuidBytes(eventId), 2)
  frame.set(payload, DOCUMENT_WS_HEADER_BYTES)
  return frame.buffer
}

/** 校验版本和帧类型后解码二进制头，并返回未解析的 payload。 */
export function decodeDocumentWsFrame(data: ArrayBuffer): DocumentWsBinaryFrame {
  /** 将待解码的 ArrayBuffer 视为可按字节访问的协议帧。 */
  const frame = new Uint8Array(data)
  if (frame.byteLength < DOCUMENT_WS_HEADER_BYTES) {
    throw new Error('文档 WebSocket 二进制帧长度不足')
  }
  if (frame[0] !== DOCUMENT_WS_PROTOCOL_VERSION) {
    throw new Error(`不支持的文档 WebSocket 协议版本：${frame[0]}`)
  }

  /** 从二进制头读取的帧类型。 */
  const type = frame[1] as DocumentWsFrameType
  if (!Object.values(DocumentWsFrameType).includes(type)) {
    throw new Error(`未知的文档 WebSocket 帧类型：${frame[1]}`)
  }

  return {
    type,
    eventId: bytesUuid(frame.slice(2, DOCUMENT_WS_HEADER_BYTES)),
    payload: frame.slice(DOCUMENT_WS_HEADER_BYTES)
  }
}

/** 解析控制 JSON 的外层兼容性，字段语义由消息处理器进一步校验。 */
export function parseDocumentWsControl(data: string): DocumentWsControlMessage {
  /** JSON.parse 得到的未知值，先验证对象结构再转换成控制帧。 */
  const value: unknown = JSON.parse(data)
  if (!value || typeof value !== 'object') throw new Error('文档 WebSocket 控制帧无效')
  /** 通过外层协议校验后的控制帧字段集合。 */
  const control = value as Partial<DocumentWsControlMessage>
  if (control.protocolVersion !== DOCUMENT_WS_PROTOCOL_VERSION || typeof control.type !== 'string') {
    throw new Error('文档 WebSocket 控制帧不兼容')
  }
  if (control.type === 'JOIN_DOCUMENT' && !isPositiveSafeInteger(control.awarenessClientId)) {
    throw new Error('JOIN_DOCUMENT 缺少有效的 awarenessClientId')
  }
  // 此处只校验外层结构；收到控制帧的处理器再判断该类型是否必须包含某个字段。
  return control as DocumentWsControlMessage
}

/**
 * 严格解析服务端的 Awareness Session 元数据。
 *
 * <p>通用控制帧解析器只负责外层协议兼容性；身份字段在这里统一校验，避免
 * 将服务端元数据中的空值、错误文档或未知动作写入本地 Session 索引。</p>
 */
export function parseDocumentWsAwarenessMeta(
  control: DocumentWsControlMessage,
  expectedDocumentId?: number
): DocumentWsAwarenessMeta {
  if (control.type !== 'AWARENESS_META') {
    throw new Error('文档 WebSocket 控制帧不是 Awareness 元数据')
  }
  if (typeof control.requestId !== 'string' || control.requestId.trim().length === 0) {
    throw new Error('Awareness 元数据 requestId 无效')
  }
  if (!isPositiveSafeInteger(control.documentId)) {
    throw new Error('Awareness 元数据 documentId 无效')
  }
  if (expectedDocumentId !== undefined && control.documentId !== expectedDocumentId) {
    throw new Error('Awareness 元数据 documentId 与当前文档不匹配')
  }
  if (!isPositiveSafeInteger(control.awarenessClientId)) {
    throw new Error('Awareness 元数据 awarenessClientId 无效')
  }
  if (typeof control.sessionId !== 'string' || control.sessionId.trim().length === 0) {
    throw new Error('Awareness 元数据 sessionId 无效')
  }
  if (control.action !== 'UPSERT' && control.action !== 'REMOVE') {
    throw new Error('Awareness 元数据 action 无效')
  }

  const userId = control.userId ?? null
  const name = control.name ?? null
  const color = control.color ?? null
  if (control.action === 'UPSERT') {
    if (!isPositiveSafeInteger(userId)) {
      throw new Error('Awareness UPSERT 的 userId 无效')
    }
    if (!isNonBlankString(name)) {
      throw new Error('Awareness UPSERT 的 name 无效')
    }
    if (!isNonBlankString(color)) {
      throw new Error('Awareness UPSERT 的 color 无效')
    }

    return {
      ...control,
      type: 'AWARENESS_META',
      requestId: control.requestId,
      documentId: control.documentId,
      awarenessClientId: control.awarenessClientId,
      action: 'UPSERT',
      sessionId: control.sessionId,
      userId,
      name,
      color
    }
  }

  return {
    ...control,
    type: 'AWARENESS_META',
    requestId: control.requestId,
    documentId: control.documentId,
    awarenessClientId: control.awarenessClientId,
    action: 'REMOVE',
    sessionId: control.sessionId,
    userId: null,
    name: null,
    color: null
  }
}

/** 校验协议中的正整数 ID，避免把浮点数或超出安全范围的值当成索引。 */
function isPositiveSafeInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

/** 校验服务端元数据中的必填展示字符串。 */
function isNonBlankString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}
