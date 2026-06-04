package com.jacolp.service;

import com.jacolp.annotation.GuestCacheable;
import com.jacolp.constant.GuestCacheConstant;
import com.jacolp.pojo.dto.note.GuestNoteQueryDTO;
import com.jacolp.pojo.vo.note.GuestNoteDetailVO;
import com.jacolp.result.PageResult;

public interface GuestNoteService {

    /**
     * 列出公开笔记
     * @param dto 查询参数
     * @return 笔记列表
     */
    @GuestCacheable(
            cacheName = GuestCacheConstant.GUEST_NOTE_LIST_CACHE,
            ttlSeconds = GuestCacheConstant.GUEST_NOTE_LIST_TTL_SECONDS)
    PageResult listPublishedNotes(GuestNoteQueryDTO dto);

    /**
     * 获取公开笔记详情
     * @param noteId 笔记ID
     * @return 笔记详情
     */
    @GuestCacheable(
            cacheName = GuestCacheConstant.GUEST_NOTE_DETAIL_CACHE,
            ttlSeconds = GuestCacheConstant.GUEST_NOTE_DETAIL_TTL_SECONDS)
    GuestNoteDetailVO getPublishedNoteDetail(Long noteId);
}
