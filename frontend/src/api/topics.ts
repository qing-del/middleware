import request from '@/utils/request'

export interface TopicItem {
  id: number
  topicName: string
  sortOrder: number
  noteCount: number
  isPass: number       // 0=待审核, 1=已通过, 2=已拒绝
  createTime: string
  updateTime: string
}

export interface TopicPageResult {
  total: number
  records: TopicItem[]
}

export interface TopicQueryParams {
  keyword?: string
  pageNum?: number
  pageSize?: number
}

export interface TopicAddParams {
  topicName: string
  sortOrder?: number
}

export interface TopicModifyParams {
  id: number
  sortOrder: number
}

export interface TopicStatsVO {
  topicCount: number
  passedCount: number
}

export const topicApi = {
  /** 条件分页查询主题列表 */
  getList(params: TopicQueryParams): Promise<TopicPageResult> {
    return request.post('/user/topic/list', params)
  },

  /** 新增主题 */
  addTopic(data: TopicAddParams): Promise<string> {
    return request.post('/user/topic/add', data)
  },

  /** 修改主题（仅排序等级） */
  modifyTopic(data: TopicModifyParams): Promise<string> {
    return request.put('/user/topic/modify', data)
  },

  /** 批量删除主题 */
  deleteTopics(ids: number[]): Promise<string> {
    return request.delete('/user/topic/delete', {
      params: { ids: ids.join(',') }
    })
  },

  /** 发起主题审核申请 */
  submitAudit(id: number): Promise<string> {
    return request.post('/user/topic/submitAudit', null, {
      params: { id }
    })
  },

  /** 获取用户主题统计 */
  getStats(): Promise<TopicStatsVO> {
    return request.get('/user/topic/stats')
  }
}
