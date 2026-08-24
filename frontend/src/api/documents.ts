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
  },

  create(title: string): Promise<DocumentMetadata> {
    return request.post('/user/document', { title })
  },

  getMetadata(documentId: number): Promise<DocumentMetadata> {
    return request.get(`/user/document/${documentId}/meta`)
  },

  updateTitle(documentId: number, title: string): Promise<DocumentMetadata> {
    return request.patch(`/user/document/${documentId}/meta`, { title })
  },

  delete(documentId: number): Promise<void> {
    return request.delete(`/user/document/${documentId}`)
  }
}
