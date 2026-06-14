import request from '@/utils/request'

export interface TagItem {
  id: number
  userId?: number
  tagName: string
  auditStatus: number  // 0=待审核, 1=审核中, 2=已通过, 3=已拒绝, 4=已删除
  createTime: string
}

export interface TagPageResult {
  total: number
  records: TagItem[]
}

export interface TagQueryParams {
  keyword?: string
  scope?: 'personal' | 'global'
  pageNum?: number
  pageSize?: number
}

export interface TagAddParams {
  tagName: string
}

export interface TagStatsVO {
  tagCount: number
  passedCount: number
}

export const tagApi = {
  /** 条件分页查询标签列表 */
  getList(params: TagQueryParams): Promise<TagPageResult> {
    return request.post('/user/tag/list', params)
  },

  /** 新增标签 */
  addTag(data: TagAddParams): Promise<string> {
    return request.post('/user/tag/add', data)
  },

  /** 批量删除标签 */
  deleteTags(ids: number[]): Promise<string> {
    return request.delete('/user/tag/delete', {
      params: { ids: ids.join(',') }
    })
  },

  /** 发起标签审核申请 */
  submitAudit(id: number): Promise<string> {
    return request.post('/user/tag/submitAudit', null, {
      params: { id }
    })
  },

  /** 撤销标签审核申请 */
  cancelAudit(id: number): Promise<string> {
    return request.post('/user/tag/cancelAudit', null, {
      params: { id }
    })
  },

  /** 获取用户标签统计 */
  getStats(): Promise<TagStatsVO> {
    return request.get('/user/tag/stats')
  }
}
