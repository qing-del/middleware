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
  protocolVersion: number
  type: DocumentWsControlType
  requestId: string | null
  documentId: number | null
  clientUpdateId: string | null
  redisOpId: string | null
  code: string | null
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
  type: DocumentWsFrameType
  eventId: string
  payload: Uint8Array
}

function uuidBytes(value: string): Uint8Array {
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
  if (bytes.length !== 16) throw new Error('文档 WebSocket 协议 UUID 长度无效')
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export function createDocumentWsRequestId(): string {
  return crypto.randomUUID()
}

export function createDocumentWsControl(
  type: DocumentWsControlType,
  overrides: Partial<Omit<DocumentWsControlMessage, 'protocolVersion' | 'type'>> = {}
): DocumentWsControlMessage {
  return {
    protocolVersion: DOCUMENT_WS_PROTOCOL_VERSION,
    type,
    requestId: null,
    documentId: null,
    clientUpdateId: null,
    redisOpId: null,
    code: null,
    message: null,
    ...overrides
  }
}

export function encodeDocumentWsFrame(
  type: DocumentWsFrameType,
  eventId: string,
  payload: Uint8Array
): ArrayBuffer {
  const frame = new Uint8Array(DOCUMENT_WS_HEADER_BYTES + payload.byteLength)
  frame[0] = DOCUMENT_WS_PROTOCOL_VERSION
  frame[1] = type
  frame.set(uuidBytes(eventId), 2)
  frame.set(payload, DOCUMENT_WS_HEADER_BYTES)
  return frame.buffer
}

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

export function parseDocumentWsControl(data: string): DocumentWsControlMessage {
  const value: unknown = JSON.parse(data)
  if (!value || typeof value !== 'object') throw new Error('文档 WebSocket 控制帧无效')
  const control = value as Partial<DocumentWsControlMessage>
  if (control.protocolVersion !== DOCUMENT_WS_PROTOCOL_VERSION || typeof control.type !== 'string') {
    throw new Error('文档 WebSocket 控制帧不兼容')
  }
  return control as DocumentWsControlMessage
}
