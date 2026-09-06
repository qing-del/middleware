import assert from 'node:assert/strict'
import test from 'node:test'
import * as Y from 'yjs'
import {
  encodeAwarenessUpdate
} from 'y-protocols/awareness'
import {
  createDocumentWsControl,
  decodeDocumentWsFrame,
  DocumentWsFrameType
} from '../src/collaboration/documentProtocol.ts'
import { DocumentCollaborationClient } from '../src/collaboration/DocumentCollaborationClient.ts'

type SentData = string | ArrayBuffer

class FakeWebSocket {
  static readonly OPEN = 1
  static readonly instances: FakeWebSocket[] = []

  readonly sent: SentData[] = []
  readonly url: string
  readonly protocols: string[]
  readyState = 0
  binaryType = ''
  onopen: (() => void) | null = null
  onmessage: ((event: MessageEvent<string | ArrayBuffer>) => void) | null = null
  onerror: (() => void) | null = null
  onclose: (() => void) | null = null

  constructor(url: string, protocols: string[]) {
    this.url = url
    this.protocols = protocols
    FakeWebSocket.instances.push(this)
  }

  send(data: SentData): void {
    this.sent.push(data)
  }

  close(): void {
    this.readyState = 3
    this.onclose?.()
  }

  open(): void {
    this.readyState = FakeWebSocket.OPEN
    this.onopen?.()
  }

  receive(data: SentData): void {
    this.onmessage?.({ data } as MessageEvent<string | ArrayBuffer>)
  }
}

test('uses a fresh Awareness ID for reconnects without replacing the Y.Doc identity', () => {
  const globalObject = globalThis as typeof globalThis & {
    WebSocket: typeof WebSocket
    window: Window & typeof globalThis
  }
  const originalWebSocket = globalObject.WebSocket
  const originalWindow = globalObject.window
  const reconnectCallbacks: Array<() => void> = []
  globalObject.WebSocket = FakeWebSocket as unknown as typeof WebSocket
  globalObject.window = {
    location: { protocol: 'http:', host: 'localhost' },
    setTimeout: (callback: () => void) => {
      reconnectCallbacks.push(callback)
      return reconnectCallbacks.length
    },
    clearTimeout: () => undefined
  } as unknown as Window & typeof globalThis

  const ydoc = new Y.Doc()
  const ydocClientId = ydoc.clientID
  const client = new DocumentCollaborationClient({
    documentId: 42,
    accessToken: 'test-token',
    ydoc,
    canWrite: true
  })
  client.updateLocalAwareness({ user: { name: 'Alice' } })

  try {
    client.connect()
    const firstSocket = FakeWebSocket.instances[0]
    assert.ok(firstSocket)
    firstSocket.open()
    const firstJoin = findControl(firstSocket, 'JOIN_DOCUMENT')
    const firstAwarenessId = firstJoin.awarenessClientId
    assert.ok(firstAwarenessId > 0)
    assert.notEqual(firstAwarenessId, ydocClientId)
    assert.deepEqual(client.awareness.getLocalState(), { user: { name: 'Alice' } })

    firstSocket.receive(JSON.stringify(createDocumentWsControl('SYNC_COMPLETE', { documentId: 42 })))
    const firstAwarenessFrame = findFrame(firstSocket, DocumentWsFrameType.AWARENESS)
    assert.deepEqual(
      new Uint8Array(firstAwarenessFrame.payload),
      encodeAwarenessUpdate(client.awareness, [firstAwarenessId])
    )

    ydoc.getMap('content').set('pending', true)
    const firstPendingUpdate = findFrame(firstSocket, DocumentWsFrameType.CLIENT_UPDATE)

    firstSocket.onclose?.()
    assert.equal(reconnectCallbacks.length, 1)
    reconnectCallbacks.shift()?.()

    const secondSocket = FakeWebSocket.instances[1]
    assert.ok(secondSocket)
    secondSocket.open()
    const secondJoin = findControl(secondSocket, 'JOIN_DOCUMENT')
    const secondAwarenessId = secondJoin.awarenessClientId
    assert.ok(secondAwarenessId > 0)
    assert.notEqual(secondAwarenessId, firstAwarenessId)
    assert.equal(ydoc.clientID, ydocClientId)
    assert.deepEqual(client.awareness.getLocalState(), { user: { name: 'Alice' } })
    assert.equal(client.awareness.getStates().has(firstAwarenessId), false)
    assert.equal(client.awareness.getStates().has(secondAwarenessId), true)
    assert.equal(client.getAwarenessProvider().awareness.states.has(secondAwarenessId), true)
    assert.equal(client.getAwarenessProvider().awareness.getStates().has(secondAwarenessId), false)

    secondSocket.receive(JSON.stringify(createDocumentWsControl('SYNC_COMPLETE', { documentId: 42 })))
    const replayedUpdate = findFrame(secondSocket, DocumentWsFrameType.CLIENT_UPDATE)
    assert.equal(replayedUpdate.eventId, firstPendingUpdate.eventId)
    const secondAwarenessFrame = findFrame(secondSocket, DocumentWsFrameType.AWARENESS)
    assert.deepEqual(
      new Uint8Array(secondAwarenessFrame.payload),
      encodeAwarenessUpdate(client.awareness, [secondAwarenessId])
    )

    secondSocket.receive(JSON.stringify(createDocumentWsControl('AWARENESS_META', {
      documentId: 42,
      action: 'REMOVE',
      awarenessClientId: firstAwarenessId,
      sessionId: 'old-session',
      userId: null,
      name: null,
      color: null
    })))
    assert.deepEqual(client.awareness.getLocalState(), { user: { name: 'Alice' } })
    assert.equal(client.awareness.getStates().has(secondAwarenessId), true)
  } finally {
    client.dispose()
    globalObject.WebSocket = originalWebSocket
    globalObject.window = originalWindow
    FakeWebSocket.instances.length = 0
  }
})

function findControl(
  socket: FakeWebSocket,
  type: 'JOIN_DOCUMENT',
): { awarenessClientId: number } {
  const control = socket.sent
    .filter((data): data is string => typeof data === 'string')
    .map(data => JSON.parse(data) as { type: string; awarenessClientId?: number })
    .find(value => value.type === type)
  assert.ok(control)
  assert.equal(typeof control.awarenessClientId, 'number')
  return { awarenessClientId: control.awarenessClientId }
}

function findFrame(socket: FakeWebSocket, type: DocumentWsFrameType) {
  const frame = socket.sent
    .filter((data): data is ArrayBuffer => data instanceof ArrayBuffer)
    .map(data => decodeDocumentWsFrame(data))
    .find(value => value.type === type)
  assert.ok(frame)
  return frame
}
