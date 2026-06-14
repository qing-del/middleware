import request from '@/utils/request'

export interface TopicItem {
  id: number
  userId?: number
  topicName: string
  parentId?: number | null
  sortOrder: number
  noteCount: number
  createTime: string
  updateTime: string
}

export interface TopicPageResult {
  total: number
  records: TopicItem[]
}

export interface TopicQueryParams {
  keyword?: string
  scope?: 'personal' | 'global'
  parentId?: number | null
  pageNum?: number
  pageSize?: number
}

export interface TopicAddParams {
  topicName: string
  parentId?: number | null
  sortOrder?: number
}

export interface TopicModifyParams {
  id: number
  parentId?: number | null
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

  /** 查询指定父级下的一层主题目录 */
  getChildren(params: { parentId?: number | null } = {}): Promise<TopicItem[]> {
    return request.get('/user/topic/children', {
      params: params.parentId == null ? {} : { parentId: params.parentId }
    })
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

  /** 获取用户主题统计 */
  getStats(): Promise<TopicStatsVO> {
    return request.get('/user/topic/stats')
  }
}
