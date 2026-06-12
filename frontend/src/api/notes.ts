import request from '@/utils/request'

// ── Shared types ──────────────────────────────────
export interface PageResult<T> {
  total: number
  records: T[]
}

// ── NoteStatus enum ───────────────────────────────
/** Mirrors backend com.jacolp.enums.NoteStatus */
export const NoteStatusCode = {
  /** 已创建 - 刚上传，关联信息可能不完整 */
  NEW: 0 as const,
  /** 缺失信息 - 存在标签/图片/内链缺失 */
  PENDING_INFO: 1 as const,
  /** 待转换 - 关联完整，等待 MD→HTML 转换 */
  READY_TO_CONVERT: 2 as const,
  /** 已转换 - 已完成 Markdown→HTML */
  CONVERTED: 3 as const,
  /** 审核中 - 已提交审核，等待管理员审核 */
  PENDING_AUDIT: 4 as const,
  /** 已通过 - 审核通过，尚未公开 */
  APPROVED: 5 as const,
  /** 已公开 - 审核通过且已发布 */
  PUBLISHED: 6 as const,
  /** 已拒绝 - 审核未通过 */
  REJECTED: 7 as const,
  /** 已删除 - 软删除 */
  DELETED: 8 as const,
} as const

export type NoteStatusCodeType = typeof NoteStatusCode[keyof typeof NoteStatusCode]

export interface NoteStatusInfo {
  code: NoteStatusCodeType
  label: string
  cls: string
  icon: string  // lucide icon name hint
  deletable: boolean
  modifiable: boolean  // can re-upload / modify
}

export function getNoteStatusInfo(code: number): NoteStatusInfo {
  const map: Record<number, NoteStatusInfo> = {
    [NoteStatusCode.NEW]: {
      code: NoteStatusCode.NEW, label: '已创建', deletable: true, modifiable: true,
      cls: 'text-sky-400 bg-sky-500/10 border-sky-500/20', icon: 'FilePlus'
    },
    [NoteStatusCode.PENDING_INFO]: {
      code: NoteStatusCode.PENDING_INFO, label: '缺失信息', deletable: true, modifiable: true,
      cls: 'text-amber-400 bg-amber-500/10 border-amber-500/20', icon: 'AlertTriangle'
    },
    [NoteStatusCode.READY_TO_CONVERT]: {
      code: NoteStatusCode.READY_TO_CONVERT, label: '待转换', deletable: true, modifiable: true,
      cls: 'text-violet-400 bg-violet-500/10 border-violet-500/20', icon: 'RefreshCw'
    },
    [NoteStatusCode.CONVERTED]: {
      code: NoteStatusCode.CONVERTED, label: '已转换', deletable: true, modifiable: true,
      cls: 'text-indigo-400 bg-indigo-500/10 border-indigo-500/20', icon: 'FileCode'
    },
    [NoteStatusCode.PENDING_AUDIT]: {
      code: NoteStatusCode.PENDING_AUDIT, label: '审核中', deletable: false, modifiable: false,
      cls: 'text-amber-400 bg-amber-500/10 border-amber-500/20', icon: 'Clock'
    },
    [NoteStatusCode.APPROVED]: {
      code: NoteStatusCode.APPROVED, label: '已通过', deletable: true, modifiable: true,
      cls: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20', icon: 'CheckCircle2'
    },
    [NoteStatusCode.PUBLISHED]: {
      code: NoteStatusCode.PUBLISHED, label: '已公开', deletable: false, modifiable: false,
      cls: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20', icon: 'Globe'
    },
    [NoteStatusCode.REJECTED]: {
      code: NoteStatusCode.REJECTED, label: '已拒绝', deletable: true, modifiable: true,
      cls: 'text-rose-400 bg-rose-500/10 border-rose-500/20', icon: 'XCircle'
    },
    [NoteStatusCode.DELETED]: {
      code: NoteStatusCode.DELETED, label: '已删除', deletable: false, modifiable: false,
      cls: 'text-slate-500 bg-slate-500/10 border-slate-500/20', icon: 'Trash2'
    },
  }
  return map[code] ?? {
    code: code as NoteStatusCodeType, label: '未知', deletable: false, modifiable: false,
    cls: 'text-slate-400 bg-slate-500/10 border-slate-500/20', icon: 'HelpCircle'
  }
}

// ── Note list item (from list/search endpoints) ───
export interface NoteItem {
  id: number
  userId: number
  topicId: number | null
  topicName: string | null
  title: string
  description: string
  storageType: number
  /** NoteStatus code: 0=NEW...6=PUBLISHED...8=DELETED */
  status: number
  /** 是否存在待确认变更: 0=否, 1=是 */
  isChanging?: number
  /** 审核状态位: 0=待审核, 1=已通过, 2=已拒绝 (legacy, prefer NoteStatus) */
  isPass: number
  /** 缺漏信息掩码 */
  missingInfoMask: number
  /** 缺漏项数量 */
  missingCount: number
  /** Markdown文件大小(字节) */
  mdFileSize: number
  /** 是否有待确认的变更 (has diff) */
  hasDiff?: number
  /** 关联标签名称列表 */
  tags?: string[]
  /** 关联标签数量 */
  tagCount: number
  /** 关联图片数量 */
  imageCount: number
  /** 关联双链笔记数量 */
  eachNoteCount: number
  createTime: string
  updateTime: string
}

// ── Query params ──────────────────────────────────
export interface NoteQueryParams {
  keyword?: string
  topicId?: number
  unclassified?: boolean
  tagId?: number
  title?: string
  scope?: 'personal' | 'global'
  pageNum?: number
  pageSize?: number
}

// ── Note detail (GET /user/note/{noteId}) ─────────
export interface NoteDetailVO {
  id: number
  userId: number
  topicId: number | null
  topicName: string | null
  title: string
  description: string
  storageType: number
  status: number
  /** 是否存在待确认变更: 0=否, 1=是 */
  isChanging?: number
  missingInfoMask: number
  missingCount: number
  mdFileSize: number
  createTime: string
  updateTime: string
  tags: string[]
  images: NoteImageSimpleVO[]
  eachNotes: NoteEachSimpleVO[]
  converted: NoteConvertResultVO | null
}

export interface NoteImageSimpleVO {
  imageId: number
  noteId: number
  parsedImageName: string
  filename: string
  ossUrl: string
  isPublic: number
  isPass: number
  isCrossUser: number
  isMissing: number
  createTime: string
}

export interface NoteEachSimpleVO {
  targetNoteId: number
  targetNoteTitle: string
  parsedNoteName: string
  anchor: string
  nickname: string
  isMissing: number
}

export interface NoteConvertResultVO {
  meta: NoteConvertMetaVO
  tocHtml: string
  bodyHtml: string
}

export interface NoteConvertMetaVO {
  title: string
  tags: string[]
  createTime: string
}

// ── Guest public notes ───────────────────────────
export interface GuestNoteItem {
  id: number
  topicId?: number
  topicName?: string
  title: string
  description?: string
  tags: string[]
  createTime: string
  updateTime: string
}

export type PublicNoteItem = GuestNoteItem

export interface GuestNoteDetailVO {
  id: number
  topicId?: number
  topicName?: string
  title: string
  description?: string
  tags: string[]
  images: NoteImageSimpleVO[]
  eachNotes: NoteEachSimpleVO[]
  converted: NoteConvertResultVO
  createTime: string
  updateTime: string
}

export type PublicNoteDetailVO = GuestNoteDetailVO

// ── Upload ────────────────────────────────────────
export interface NoteUploadVO {
  noteId: number
  missingTags: string[]
  missingImages: string[]
  missingNoteNames: string[]
}

// ── Diff ──────────────────────────────────────────
export interface NoteDiffVO {
  oldTags: string[]
  newTags: string[]
  oldImages: string[]
  newImages: string[]
  oldNoteNames: string[]
  newNoteNames: string[]
}

export interface NoteChangeDiffVO {
  noteId: number
  status: number
  oldFileSize: number
  newFileSize: number
  diffFileSize: number
  diff: NoteDiffVO
}

export interface NoteModifyDiffDetailVO {
  noteId: number
  oldSource: string
  newSource: string
  diff: NoteChangeDiffVO
}

// ── Relation ──────────────────────────────────────
export interface NoteRelationDetailVO {
  noteId: number
  tags: NoteTagMappingRowVO[]
  images: NoteImageMappingRowVO[]
  eachNotes: NoteEachMappingRowVO[]
}

export interface NoteTagMappingRowVO {
  mappingId: number
  noteId: number
  tagId: number
  parsedTagName: string
  tagName: string
  isPass: number
  isMissing: number
}

export interface NoteImageMappingRowVO {
  mappingId: number
  noteId: number
  imageId: number
  parsedImageName: string
  filename: string
  isCrossUser: number
  isPass: number
  isMissing: number
}

export interface NoteEachMappingRowVO {
  mappingId: number
  sourceNoteId: number
  targetNoteId: number
  parsedNoteName: string
  targetNoteTitle: string
  anchor: string
  nickname: string
  isPass: number
  isMissing: number
}

// ── Backlinks (reverse references) ────────────────
export interface NoteBacklinkVO {
  sourceNoteId: number
  sourceNoteTitle: string
  parsedNoteName: string
  anchor: string
  nickname: string
  /** 0=本人 1=跨用户 */
  isCrossUser: number
  /** 引用方笔记的状态码（NoteStatusCode） */
  sourceNoteStatus: number
  createTime: string
}

export interface TagBacklinkVO {
  sourceNoteId: number
  sourceNoteTitle: string
  parsedTagName: string
  /** 0=本人 1=跨用户 */
  isCrossUser: number
  /** 引用方笔记的状态码（NoteStatusCode） */
  sourceNoteStatus: number
  createTime: string
}

export interface ImageBacklinkVO {
  sourceNoteId: number
  sourceNoteTitle: string
  parsedImageName: string
  /** 0=本人 1=跨用户 */
  isCrossUser: number
  /** 引用方笔记的状态码（NoteStatusCode） */
  sourceNoteStatus: number
  createTime: string
}

// ── Check binding ─────────────────────────────────
export interface NoteCheckBindingVO {
  noteId: number
  status: number
  statusDesc: string
  missingInfoMask: number
  missingCount: number
  missingTags: string[]
  missingImages: string[]
  missingNoteNames: string[]
  complete: boolean
}

// ── Stats ─────────────────────────────────────────
export interface NoteStatsVO {
  noteTotalCount: number
  publicNoteCount: number
  passedNoteCount: number
}

// ── API methods ───────────────────────────────────
export const noteApi = {
  // === Core Note Operations ===

  /** 条件查询笔记列表 (POST) */
  getList(params: NoteQueryParams): Promise<PageResult<NoteItem>> {
    return request.post('/user/note/list', params)
  },

  /** 全文搜索笔记 (GET) */
  searchNotes(params: NoteQueryParams): Promise<PageResult<NoteItem>> {
    return request.get('/user/note/search', { params })
  },

  /** 查看笔记详情 */
  getDetail(noteId: number): Promise<NoteDetailVO> {
    return request.get(`/user/note/${noteId}`)
  },

  /** 获取笔记Markdown源内容 */
  getSource(noteId: number): Promise<string> {
    return request.get(`/user/note/source/${noteId}`)
  },

  /** 上传新笔记 */
  uploadNote(file: File, topicId?: number): Promise<NoteUploadVO> {
    const fd = new FormData()
    fd.append('file', file)
    if (topicId != null) fd.append('topicId', String(topicId))
    return request.post('/user/note/upload', fd)
  },

  /** 修改笔记源文件（上传新版本，产生Diff） */
  modifyFile(noteId: number, file: File): Promise<NoteDiffVO> {
    const fd = new FormData()
    fd.append('file', file)
    return request.put(`/user/note/upload/${noteId}`, fd)
  },

  /** 查询变更Diff详情 */
  getDiff(noteId: number): Promise<NoteModifyDiffDetailVO> {
    return request.get(`/user/note/upload/${noteId}/diff`)
  },

  /** 确认或取消笔记变更 */
  confirmChange(noteId: number, confirm: boolean): Promise<Record<string, never>> {
    return request.post(`/user/note/upload/${noteId}/confirm`, { id: noteId, confirm })
  },

  /** 修改笔记元信息（描述、主题归属） */
  modifyInfo(noteId: number, data: { topicId?: number; description?: string }): Promise<string> {
    return request.put(`/user/note/${noteId}/info`, { ...data, id: noteId })
  },

  /** 删除笔记 */
  deleteNote(noteId: number): Promise<string> {
    return request.delete(`/user/note/${noteId}`)
  },

  /** 发起笔记审核申请 */
  submitAudit(noteId: number): Promise<string> {
    return request.post('/user/note/submitAudit', null, { params: { id: noteId } })
  },

  /** 撤销笔记审核申请 */
  cancelAudit(noteId: number): Promise<string> {
    return request.post('/user/note/cancelAudit', null, { params: { id: noteId } })
  },

  /** 转换笔记为HTML */
  convertNote(noteId: number): Promise<Record<string, never>> {
    return request.post('/user/note/convert', null, { params: { noteId } })
  },

  /** 获取笔记转换后的HTML */
  getConverted(noteId: number): Promise<NoteConvertResultVO> {
    return request.get(`/user/note/converted/${noteId}`)
  },

  /** 设置笔记发布状态 (1=发布, 0=下架) */
  publish(noteId: number, status: number): Promise<string> {
    return request.put(`/user/note/publish/${noteId}/${status}`)
  },

  /** 获取用户笔记统计 */
  getStats(): Promise<NoteStatsVO> {
    return request.get('/user/note/overview')
  },

  // === Relation Operations ===

  /** 查询笔记关联映射 */
  getRelations(noteId: number): Promise<NoteRelationDetailVO> {
    return request.get(`/user/note/relation/${noteId}`)
  },

  /** 查询笔记关联图片 */
  getRelationImages(noteId: number): Promise<NoteImageSimpleVO[]> {
    return request.get(`/user/note/relation/images/${noteId}`)
  },

  /** 查询反向引用笔记（哪些笔记引用了 noteId） */
  getBacklinks(noteId: number): Promise<NoteBacklinkVO[]> {
    return request.get(`/user/note/relation/backlinks/${noteId}`)
  },

  /** 查询标签反向引用笔记 */
  getTagBacklinks(tagId: number): Promise<TagBacklinkVO[]> {
    return request.get(`/user/note/relation/backlinks/tag/${tagId}`)
  },

  /** 查询图片反向引用笔记 */
  getImageBacklinks(imageId: number): Promise<ImageBacklinkVO[]> {
    return request.get(`/user/note/relation/backlinks/image/${imageId}`)
  },

  /** 绑定标签映射 */
  bindTag(mappingId: number, tagId: number): Promise<string> {
    return request.put('/user/note/relation/tag/bind', { mappingId, tagId })
  },

  /** 解绑标签映射 */
  unbindTag(mappingId: number): Promise<string> {
    return request.delete(`/user/note/relation/tag/unbind/${mappingId}`)
  },

  /** 绑定图片映射 */
  bindImage(mappingId: number, imageId: number): Promise<string> {
    return request.put('/user/note/relation/image/bind', { mappingId, imageId })
  },

  /** 解绑图片映射 */
  unbindImage(mappingId: number): Promise<string> {
    return request.delete(`/user/note/relation/image/unbind/${mappingId}`)
  },

  /** 绑定双链笔记映射 */
  bindEach(mappingId: number, noteId: number): Promise<string> {
    return request.put('/user/note/relation/each/bind', { mappingId, noteId })
  },

  /** 解绑双链笔记映射 */
  unbindEach(mappingId: number): Promise<string> {
    return request.delete(`/user/note/relation/each/unbind/${mappingId}`)
  },

  /** 校验关联完整性 */
  checkRelations(noteId: number): Promise<NoteCheckBindingVO> {
    return request.post(`/user/note/relation/check/${noteId}`)
  },

  /** 删除笔记转换缓存，状态回退为"待转换" */
  deleteConverted(noteId: number): Promise<string> {
    return request.delete('/user/note/convert', { params: { noteId } })
  },
}

export const guestNoteApi = {
  /** 分页查询公开笔记 */
  getList(params: NoteQueryParams): Promise<PageResult<GuestNoteItem>> {
    return request.get('/guest/note', { params })
  },

  /** 查看公开笔记详情 */
  getDetail(noteId: number): Promise<GuestNoteDetailVO> {
    return request.get(`/guest/note/${noteId}`)
  },
}

export const userPublicNoteApi = {
  /** 分页查询公共笔记广场 */
  getList(params: NoteQueryParams): Promise<PageResult<PublicNoteItem>> {
    return request.get('/user/public-note', { params })
  },

  /** 查看公共笔记详情 */
  getDetail(noteId: number): Promise<PublicNoteDetailVO> {
    return request.get(`/user/public-note/${noteId}`)
  },
}
