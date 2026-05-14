import request from '@/utils/request'

// ── Shared types ──────────────────────────────────
export interface PageResult<T> {
  total: number
  records: T[]
}

// ── Topics ────────────────────────────────────────
export interface AdminTopicItem {
  id: number
  topicName: string
  sortOrder: number
  noteCount: number
  isPass: number
  createTime: string
  updateTime: string
}

export interface TopicQueryParams {
  userId?: number
  keyword?: string
  sortBy?: string
  pageNum?: number
  pageSize?: number
}

// ── Tags ──────────────────────────────────────────
export interface AdminTagItem {
  id: number
  tagName: string
  isPass: number
  createTime: string
}

export interface TagQueryParams {
  userId?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
}

// ── Images ────────────────────────────────────────
export interface AdminImageItem {
  id: number
  filename: string
  ossUrl: string
  fileSize: number
  userId: number
  isPublic: number
  isPass: number
  storageType: number
  uploadTime: string
  createTime: string
}

export interface ImageQueryParams {
  userId?: number
  topicId?: number
  filename?: string
  storageType?: number
  isPublic?: number
  isPass?: number
  pageNum?: number
  pageSize?: number
}

// ── Users ─────────────────────────────────────────
export interface AdminUserItem {
  id: number
  username: string
  nickname: string
  email: string
  roleId: number
  status: number
  maxStorageBytes: number
  usedStorageBytes: number
  createTime: string
}

export interface UserQueryParams {
  id?: number
  username?: string
  status?: number
  roleId?: number
  pageNum?: number
  pageSize?: number
}

export interface UserCreateParams {
  username: string
  password: string
  roleId: number
  nickname?: string
  email?: string
  status?: number
}

export interface UserModifyParams {
  id: number
  username?: string
  nickname?: string
  email?: string
  roleId?: number
  status?: number
  newPassword?: string
  confirmPassword?: string
  password?: string
}

export interface UserStatusParams {
  id?: number
  targetUserId?: number
}

// ── API methods ───────────────────────────────────
export const adminApi = {
  // === Topics ===
  getTopicList(params: TopicQueryParams): Promise<PageResult<AdminTopicItem>> {
    return request.post('/admin/topic/list', params)
  },
  deleteTopics(ids: number[]): Promise<string> {
    return request.delete('/admin/topic/delete', {
      params: { ids: ids.join(',') }
    })
  },

  // === Tags ===
  getTagList(params: TagQueryParams): Promise<PageResult<AdminTagItem>> {
    return request.post('/admin/tag/list', params)
  },
  deleteTags(ids: number[]): Promise<string> {
    return request.delete('/admin/tag/delete', {
      params: { ids: ids.join(',') }
    })
  },
  modifyTag(data: { id: number; tagName: string }): Promise<string> {
    return request.put('/admin/tag/modify', data)
  },

  // === Images ===
  getImageList(params: ImageQueryParams): Promise<PageResult<AdminImageItem>> {
    return request.post('/admin/image/list', params)
  },
  deleteImages(ids: number[]): Promise<string> {
    return request.delete('/admin/image/delete', {
      params: { ids: ids.join(',') }
    })
  },
  modifyImageInfo(data: { id: number; topicId?: number }): Promise<string> {
    return request.put('/admin/image/modify-info', data)
  },
  setImagePublic(data: { id: number }, isPublic: number): Promise<string> {
    return request.post(`/admin/image/public/${isPublic}`, data)
  },
  transferToCloud(ids: number[]): Promise<string> {
    return request.put('/admin/image/transfer-to-cloud', null, {
      params: { ids: ids.join(',') }
    })
  },

  // === Users ===
  getUserList(params: UserQueryParams): Promise<PageResult<AdminUserItem>> {
    return request.post('/admin/user/list', params)
  },
  getUserDetail(id: number): Promise<AdminUserItem> {
    return request.get('/admin/user/user', { params: { id } })
  },
  createUser(data: UserCreateParams): Promise<string> {
    return request.post('/admin/user/user', data)
  },
  modifyUser(data: UserModifyParams): Promise<string> {
    return request.put('/admin/user/user', data)
  },
  deleteUsers(ids: number[]): Promise<string> {
    return request.delete('/admin/user/user', { params: { ids: ids.join(',') } })
  },
  setUserStatus(status: number, data: UserStatusParams): Promise<string> {
    return request.post(`/admin/user/status/${status}`, data)
  },

  // === Notes ===
  getNoteList(params: AdminNoteQueryParams): Promise<PageResult<AdminNoteItem>> {
    return request.post('/admin/note/list', params)
  },
  /** 查询笔记详情（含关联和转换内容） */
  getNoteInfo(noteId: number): Promise<AdminNoteItem> {
    return request.get(`/admin/note/info/${noteId}`)
  },
  /** 获取笔记 Markdown 源内容 */
  getNoteSource(noteId: number): Promise<string> {
    return request.get(`/admin/note/source/${noteId}`)
  },
  /** 查看笔记已转换的 HTML（管理端打开任意笔记） */
  openNoteHtml(noteId: number): Promise<{ meta: { title: string; tags: string[]; createTime: string }; tocHtml: string; bodyHtml: string }> {
    return request.get(`/admin/note/open/${noteId}`)
  },
  /** 转换笔记为 HTML */
  convertNote(noteId: number): Promise<Record<string, never>> {
    return request.post(`/admin/note/convert/${noteId}`)
  },
  /** 删除笔记转换缓存 */
  deleteNoteConvert(noteId: number): Promise<string> {
    return request.delete(`/admin/note/convert/${noteId}`)
  },
  /** 修改笔记元信息 */
  modifyNoteInfo(data: { id: number; topicId?: number; description?: string }): Promise<string> {
    return request.put('/admin/note/info', data)
  },
  /** 批量删除笔记 */
  deleteNotes(ids: number[]): Promise<string> {
    return request.delete('/admin/note/delete', { params: { ids: ids.join(',') } })
  }
}

// ── Admin Note types ────────────────────────────
export interface AdminNoteItem {
  id: number
  userId: number
  topicId: number
  topicName: string
  title: string
  description: string
  storageType: number
  /** NoteStatus code */
  status: number
  missingInfoMask: number
  missingCount: number
  mdFileSize: number
  createTime: string
  updateTime: string
  tags?: string[]
  images?: AdminNoteImageVO[]
  eachNotes?: AdminNoteEachVO[]
  converted?: { meta: { title: string; tags: string[]; createTime: string }; tocHtml: string; bodyHtml: string }
}

export interface AdminNoteImageVO {
  imageId: number; noteId: number; parsedImageName: string; filename: string
  ossUrl: string; isPublic: number; isPass: number; isCrossUser: number; isMissing: number; createTime: string
}

export interface AdminNoteEachVO {
  targetNoteId: number; targetNoteTitle: string; parsedNoteName: string
  anchor: string; nickname: string; isMissing: number
}

export interface AdminNoteQueryParams {
  userId?: number
  topicId?: number
  title?: string
  status?: number
  pageNum?: number
  pageSize?: number
}
