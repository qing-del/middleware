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
  /** 获取当前用户可见的活跃协作文档元数据。 */
  list(): Promise<DocumentMetadata[]> {
    return request.get('/user/document')
  },

  /** 在当前用户个人空间创建文档；归属范围由服务端认证主体决定。 */
  create(title: string): Promise<DocumentMetadata> {
    return request.post('/user/document', { title })
  },

  /** 获取指定文档的元数据，不读取 CRDT 正文。 */
  getMetadata(documentId: number): Promise<DocumentMetadata> {
    return request.get(`/user/document/${documentId}/meta`)
  },

  /** 更新指定文档标题并返回最新元数据。 */
  updateTitle(documentId: number, title: string): Promise<DocumentMetadata> {
    return request.patch(`/user/document/${documentId}/meta`, { title })
  },

  /** 请求逻辑删除指定文档；服务端会拒绝仍有活跃会话的文档。 */
  delete(documentId: number): Promise<void> {
    return request.delete(`/user/document/${documentId}`)
  }
}
