import request from '@/utils/request'

/** Metadata returned by the v0.3 collaboration document API. CRDT content stays on WebSocket. */
export interface DocumentMetadata {
  documentId: number
  teamId: number
  title: string
  lastModifyTime: number
  lastModifyUserId: number | null
  deleted: boolean
}

export const documentApi = {
  list(): Promise<DocumentMetadata[]> {
    return request.get('/user/document')
  }
}
