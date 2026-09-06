import assert from 'node:assert/strict'
import test from 'node:test'
import * as Y from 'yjs'
import {
  Awareness,
  encodeAwarenessUpdate
} from 'y-protocols/awareness'
import {
  createDocumentWsControl,
  decodeDocumentWsFrame,
  DocumentWsFrameType,
  encodeDocumentWsFrame
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

class FakeTimerScheduler {
  private nextId = 1
  private readonly tasks = new Map<number, () => void>()

  readonly setTimeout = (callback: () => void): number => {
    const id = this.nextId
    this.nextId += 1
    this.tasks.set(id, callback)
    return id
  }

  readonly clearTimeout = (id: number): void => {
    this.tasks.delete(id)
  }

  runNext(): void {
    const next = this.tasks.keys().next()
    assert.equal(next.done, false)
    const id = next.value as number
    const callback = this.tasks.get(id)
    assert.ok(callback)
    this.tasks.delete(id)
    callback()
  }

  runAll(): void {
    while (this.tasks.size > 0) this.runNext()
  }

  pendingCount(): number {
    return this.tasks.size
  }
}

function installFakeBrowser(): {
  scheduler: FakeTimerScheduler
  restore: () => void
} {
  const globalObject = globalThis as typeof globalThis & {
    WebSocket: typeof WebSocket
    window: Window & typeof globalThis
  }
  const originalWebSocket = globalObject.WebSocket
  const originalWindow = globalObject.window
  const scheduler = new FakeTimerScheduler()
  FakeWebSocket.instances.length = 0
  globalObject.WebSocket = FakeWebSocket as unknown as typeof WebSocket
  globalObject.window = {
    location: { protocol: 'http:', host: 'localhost' },
    setTimeout: scheduler.setTimeout,
    clearTimeout: scheduler.clearTimeout
  } as unknown as Window & typeof globalThis

  return {
    scheduler,
    restore: () => {
      globalObject.WebSocket = originalWebSocket
      globalObject.window = originalWindow
      FakeWebSocket.instances.length = 0
    }
  }
}

test('uses a fresh Awareness ID for reconnects without replacing the Y.Doc identity', () => {
  const environment = installFakeBrowser()
  const { scheduler } = environment
  const originalDateNow = Date.now
  Date.now = () => 0

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
    assert.equal(scheduler.pendingCount(), 1)
    scheduler.runNext()

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
    Date.now = originalDateNow
    environment.restore()
  }
})

test('sends local Awareness once and throttles rapid updates to the latest state', () => {
  const environment = installFakeBrowser()
  const { scheduler } = environment
  const originalDateNow = Date.now
  let now = 0
  Date.now = () => now

  const client = new DocumentCollaborationClient({
    documentId: 42,
    accessToken: 'test-token',
    ydoc: new Y.Doc(),
    canWrite: true
  })

  try {
    client.connect()
    const socket = FakeWebSocket.instances[0]
    assert.ok(socket)
    socket.open()
    socket.receive(JSON.stringify(createDocumentWsControl('SYNC_COMPLETE', { documentId: 42 })))
    const initialCount = countFrames(socket, DocumentWsFrameType.AWARENESS)

    client.updateLocalAwareness({ cursor: { anchor: 1, head: 1 } })
    client.updateLocalAwareness({ cursor: { anchor: 2, head: 2 } })
    client.updateLocalAwareness({ cursor: { anchor: 3, head: 3 } })
    assert.equal(countFrames(socket, DocumentWsFrameType.AWARENESS), initialCount)
    assert.equal(scheduler.pendingCount(), 1)

    now = 50
    scheduler.runNext()
    assert.equal(countFrames(socket, DocumentWsFrameType.AWARENESS), initialCount + 1)
    assert.deepEqual(
      new Uint8Array(lastFrame(socket, DocumentWsFrameType.AWARENESS).payload),
      encodeAwarenessUpdate(client.awareness, [client.awareness.clientID])
    )

    now = 100
    client.updateLocalAwareness({ cursor: { anchor: 4, head: 4 } })
    assert.equal(countFrames(socket, DocumentWsFrameType.AWARENESS), initialCount + 2)
    assert.equal(scheduler.pendingCount(), 0)

    const remoteAwareness = new Awareness(new Y.Doc())
    remoteAwareness.setLocalState({ user: { name: 'Bob' } })
    socket.receive(encodeDocumentWsFrame(
      DocumentWsFrameType.AWARENESS,
      '00000000-0000-4000-8000-000000000001',
      encodeAwarenessUpdate(remoteAwareness, [remoteAwareness.clientID])
    ))
    assert.equal(countFrames(socket, DocumentWsFrameType.AWARENESS), initialCount + 2)
    remoteAwareness.destroy()
  } finally {
    client.dispose()
    Date.now = originalDateNow
    environment.restore()
  }
})

test('clears pending Awareness sends across reconnect and dispose', () => {
  const environment = installFakeBrowser()
  const { scheduler } = environment
  const originalDateNow = Date.now
  Date.now = () => 0

  const client = new DocumentCollaborationClient({
    documentId: 42,
    accessToken: 'test-token',
    ydoc: new Y.Doc(),
    canWrite: true
  })

  try {
    client.connect()
    const firstSocket = FakeWebSocket.instances[0]
    assert.ok(firstSocket)
    firstSocket.open()
    firstSocket.receive(JSON.stringify(createDocumentWsControl('SYNC_COMPLETE', { documentId: 42 })))

    client.updateLocalAwareness({ cursor: { anchor: 1, head: 1 } })
    assert.equal(scheduler.pendingCount(), 1)

    firstSocket.onclose?.()
    assert.equal(scheduler.pendingCount(), 1)
    scheduler.runNext()

    const secondSocket = FakeWebSocket.instances[1]
    assert.ok(secondSocket)
    secondSocket.open()
    secondSocket.receive(JSON.stringify(createDocumentWsControl('SYNC_COMPLETE', { documentId: 42 })))
    assert.equal(countFrames(secondSocket, DocumentWsFrameType.AWARENESS), 1)
    scheduler.runAll()
    assert.equal(countFrames(secondSocket, DocumentWsFrameType.AWARENESS), 1)

    client.updateLocalAwareness({ cursor: { anchor: 2, head: 2 } })
    assert.equal(scheduler.pendingCount(), 1)
    const beforeDispose = countFrames(secondSocket, DocumentWsFrameType.AWARENESS)
    client.dispose()
    scheduler.runAll()
    assert.equal(countFrames(secondSocket, DocumentWsFrameType.AWARENESS), beforeDispose)
  } finally {
    client.dispose()
    Date.now = originalDateNow
    environment.restore()
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

function countFrames(socket: FakeWebSocket, type: DocumentWsFrameType): number {
  return socket.sent
    .filter((data): data is ArrayBuffer => data instanceof ArrayBuffer)
    .map(data => decodeDocumentWsFrame(data))
    .filter(value => value.type === type)
    .length
}

function lastFrame(socket: FakeWebSocket, type: DocumentWsFrameType) {
  const frames = socket.sent
    .filter((data): data is ArrayBuffer => data instanceof ArrayBuffer)
    .map(data => decodeDocumentWsFrame(data))
    .filter(value => value.type === type)
  const frame = frames[frames.length - 1]
  assert.ok(frame)
  return frame
}
