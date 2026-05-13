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
  }
}
