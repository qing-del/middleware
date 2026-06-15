import request from '@/utils/request'

export interface ImageItem {
  id: number
  filename: string
  ossUrl: string
  fileSize: number
  isPublic: number
  auditStatus: number  // 0=待审核, 1=审核中, 2=已通过, 3=已拒绝, 4=已删除
  uploadTime: string
  createTime: string
}

export interface ImagePageResult {
  total: number
  records: ImageItem[]
}

export interface ImageQueryParams {
  topicId?: number
  filename?: string
  scope?: 'personal' | 'global'
  pageNum?: number
  pageSize?: number
}

export const imageApi = {
  /** 条件分页查询图片列表 */
  getList(params: ImageQueryParams): Promise<ImagePageResult> {
    return request.post('/user/image/list', params)
  },

  /** 上传图片 (multipart/form-data) */
  upload(file: File, topicId?: number): Promise<string> {
    const formData = new FormData()
    formData.append('file', file)
    if (topicId != null) formData.append('topicId', String(topicId))
    return request.post('/user/image/upload', formData)
  },

  /** 删除单张图片 */
  deleteImage(id: number): Promise<string> {
    return request.delete(`/user/image/${id}`)
  },

  /** 发起图片审核申请 */
  submitAudit(id: number): Promise<string> {
    return request.post('/user/audit/image/submitAudit', null, {
      params: { id }
    })
  },

  /** 撤销图片审核申请 */
  cancelAudit(id: number): Promise<string> {
    return request.post('/user/audit/image/cancelAudit', null, {
      params: { id }
    })
  },

  /** 获取用户图片统计 */
  getStats(): Promise<{ imageCount: number; passedCount: number }> {
    return request.get('/user/image/overview')
  }
}
