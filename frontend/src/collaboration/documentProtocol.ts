export const DOCUMENT_WS_PROTOCOL_VERSION = 1
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
}

export enum DocumentWsFrameType {
  CLIENT_UPDATE = 0x01,
  CRDT_UPDATE = 0x02,
  SNAPSHOT_STATE = 0x03,
  BOOTSTRAP_UPDATE = 0x04,
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
  const compact = value.replace(/-/g, '')
  if (!/^[0-9a-f]{32}$/i.test(compact)) {
    throw new Error('文档 WebSocket 协议 UUID 无效')
  }

  const result = new Uint8Array(16)
  for (let index = 0; index < result.length; index += 1) {
    result[index] = Number.parseInt(compact.slice(index * 2, index * 2 + 2), 16)
  }
  return result
}

function bytesUuid(bytes: Uint8Array): string {
  // 解码时重新插入 UUID 分段，保持事件 ID 在客户端与服务端之间可关联。
  if (bytes.length !== 16) throw new Error('文档 WebSocket 协议 UUID 长度无效')
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

/** 生成控制帧和 CLIENT_UPDATE 共用的请求关联 ID。 */
export function createDocumentWsRequestId(): string {
  return crypto.randomUUID()
}

/** 创建带协议版本、默认空字段和唯一 requestId 的控制帧。 */
export function createDocumentWsControl(
  type: DocumentWsControlType,
  overrides: Partial<Omit<DocumentWsControlMessage, 'protocolVersion' | 'type'>> = {}
): DocumentWsControlMessage {
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
  const frame = new Uint8Array(DOCUMENT_WS_HEADER_BYTES + payload.byteLength)
  frame[0] = DOCUMENT_WS_PROTOCOL_VERSION
  frame[1] = type
  frame.set(uuidBytes(eventId), 2)
  frame.set(payload, DOCUMENT_WS_HEADER_BYTES)
  return frame.buffer
}

/** 校验版本和帧类型后解码二进制头，并返回未解析的 payload。 */
export function decodeDocumentWsFrame(data: ArrayBuffer): DocumentWsBinaryFrame {
  const frame = new Uint8Array(data)
  if (frame.byteLength < DOCUMENT_WS_HEADER_BYTES) {
    throw new Error('文档 WebSocket 二进制帧长度不足')
  }
  if (frame[0] !== DOCUMENT_WS_PROTOCOL_VERSION) {
    throw new Error(`不支持的文档 WebSocket 协议版本：${frame[0]}`)
  }

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
  const value: unknown = JSON.parse(data)
  if (!value || typeof value !== 'object') throw new Error('文档 WebSocket 控制帧无效')
  const control = value as Partial<DocumentWsControlMessage>
  if (control.protocolVersion !== DOCUMENT_WS_PROTOCOL_VERSION || typeof control.type !== 'string') {
    throw new Error('文档 WebSocket 控制帧不兼容')
  }
  // 此处只校验外层结构；收到控制帧的处理器再判断该类型是否必须包含某个字段。
  return control as DocumentWsControlMessage
}
