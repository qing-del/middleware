import request from '@/utils/request'

/** 文档级直接授权；WRITE 在服务端语义上包含 READ。 */
export type DocumentPermission = 'READ' | 'WRITE'

/** 协作文档 API 返回的基础元数据；CRDT 正文只通过 WebSocket 传输。 */
export interface DocumentMetadata {
  /** 文档数据库主键；example: {@code 42} */
  documentId: number
  /** 文档所有者用户 ID；example: {@code 10001} */
  ownerUserId: number
  /** 文档标题；example: {@code 项目设计文档} */
  title: string
  /** 最近一次接受更新的 Unix 毫秒时间戳；example: {@code 1756080000000} */
  lastModifyTime: number
  /** 最近修改用户 ID，没有修改者时为 null；example: {@code 10001} */
  lastModifyUserId: number | null
  /** 逻辑删除标记；example: {@code false} */
  deleted: boolean
}

/** 已完成资源级授权判定的文档元数据；权限来自服务端，不由前端推断。 */
export interface DocumentAccessMetadata extends DocumentMetadata {
  /** 当前调用方对该文档的直接权限；只允许 READ 或 WRITE。 */
  permission: DocumentPermission
  /** 当前调用方是否为文档所有者。 */
  owner: boolean
}

/**
 * 校验元数据的基础字段，并对缺失或未知权限执行 fail-closed 归一化。
 *
 * 服务端应返回大写 READ/WRITE 和布尔 owner；异常权限值不会被当成可写权限。
 */
export function normalizeDocumentAccessMetadata(value: unknown): DocumentAccessMetadata {
  if (!value || typeof value !== 'object') throw new Error('文档元数据无效')

  const source = value as Partial<DocumentMetadata> & {
    permission?: unknown
    owner?: unknown
  }
  const documentId = source.documentId
  const ownerUserId = source.ownerUserId
  if (typeof documentId !== 'number' || !Number.isSafeInteger(documentId) || documentId <= 0
      || typeof ownerUserId !== 'number' || !Number.isSafeInteger(ownerUserId) || ownerUserId <= 0
      || typeof source.title !== 'string' || !source.title.trim()
      || typeof source.lastModifyTime !== 'number' || !Number.isFinite(source.lastModifyTime)
      || (source.lastModifyUserId !== null && typeof source.lastModifyUserId !== 'number')
      || typeof source.deleted !== 'boolean') {
    throw new Error('文档元数据无效')
  }

  const hasValidPermission = source.permission === 'READ' || source.permission === 'WRITE'
  const hasValidOwner = typeof source.owner === 'boolean'

  return {
    documentId,
    ownerUserId,
    title: source.title,
    lastModifyTime: source.lastModifyTime,
    lastModifyUserId: source.lastModifyUserId ?? null,
    deleted: source.deleted,
    // 任一 ACL 字段缺失或未知时整体按只读处理，绝不误开放写权限。
    permission: hasValidPermission && hasValidOwner && source.permission === 'WRITE' ? 'WRITE' : 'READ',
    owner: hasValidPermission && hasValidOwner && source.owner === true
  }
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

  /** 获取指定文档的元数据和当前调用方权限，不读取 CRDT 正文。 */
  async getMetadata(documentId: number): Promise<DocumentAccessMetadata> {
    // 页面会把资源拒绝统一展示为中性提示，避免拦截器弹出后端原始错误文本。
    const value = await request.get<unknown>(`/user/document/${documentId}/meta`, { _silentErrorToast: true })
    return normalizeDocumentAccessMetadata(value)
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
