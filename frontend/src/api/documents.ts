import request from '@/utils/request'

/** v0.3 协作文档 API 返回的元数据；CRDT 正文只通过 WebSocket 传输。 */
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
