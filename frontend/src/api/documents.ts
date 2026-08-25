import request from '@/utils/request'

/** v0.3 协作文档 API 返回的元数据；CRDT 正文只通过 WebSocket 传输。 */
export interface DocumentMetadata {
  /** 文档数据库主键；example: {@code 42} */
  documentId: number
  /** 个人空间标识，固定为文档所有者用户 ID；example: {@code 10001} */
  teamId: number
  /** 文档标题；example: {@code 项目设计文档} */
  title: string
  /** 最近一次接受更新的 Unix 毫秒时间戳；example: {@code 1756080000000} */
  lastModifyTime: number
  /** 最近修改用户 ID，没有修改者时为 null；example: {@code 10001} */
  lastModifyUserId: number | null
  /** 逻辑删除标记；example: {@code false} */
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
