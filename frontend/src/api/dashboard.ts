import request from '@/utils/request'

export interface NoteStats {
  noteTotalCount: number
  publicNoteCount: number
  passedNoteCount: number
}

export interface TopicStats {
  topicCount: number
  passedCount: number
}

export interface TagStats {
  tagCount: number
  passedCount: number
}

export interface ImageStats {
  imageCount: number
  passedCount: number
}

export interface UserOverview {
  username: string
  nickname: string
  email: string
  roleId: number
  status: number
  maxStorageBytes: number
  usedStorageBytes: number
  createTime: string
}

export const dashboardApi = {
  getNoteStats(): Promise<NoteStats> {
    return request.get('/user/note/overview')
  },
  getTopicStats(): Promise<TopicStats> {
    return request.get('/user/topic/stats')
  },
  getTagStats(): Promise<TagStats> {
    return request.get('/user/tag/stats')
  },
  getImageStats(): Promise<ImageStats> {
    return request.get('/user/image/overview')
  },
  getUserOverview(): Promise<UserOverview> {
    return request.get('/user/user/overview')
  }
}
