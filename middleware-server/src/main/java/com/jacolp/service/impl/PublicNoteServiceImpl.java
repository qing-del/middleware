package com.jacolp.service.impl;

import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.annotation.GuestCacheable;
import com.jacolp.constant.GuestCacheConstant;
import com.jacolp.constant.NoteConstant;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.NoteEachMappingMapper;
import com.jacolp.mapper.NoteImageMappingMapper;
import com.jacolp.mapper.NoteMapper;
import com.jacolp.mapper.NoteTagMappingMapper;
import com.jacolp.pojo.dto.note.PublicNoteQueryDTO;
import com.jacolp.pojo.vo.note.PublicNoteDetailVO;
import com.jacolp.pojo.vo.note.PublicNoteListVO;
import com.jacolp.pojo.vo.note.NoteVO;
import com.jacolp.result.PageResult;
import com.jacolp.service.PublicNoteService;
import com.jacolp.service.NoteConvertService;

@Service
public class PublicNoteServiceImpl implements PublicNoteService {

    @Autowired private NoteMapper noteMapper;
    @Autowired private NoteTagMappingMapper noteTagMappingMapper;
    @Autowired private NoteImageMappingMapper noteImageMappingMapper;
    @Autowired private NoteEachMappingMapper noteEachMappingMapper;
    @Autowired private NoteConvertService noteConvertService;

    @Override
    @GuestCacheable(
            cacheName = GuestCacheConstant.GUEST_NOTE_LIST_CACHE,
            ttlSeconds = GuestCacheConstant.GUEST_NOTE_LIST_TTL_SECONDS)
    public PageResult listPublishedNotes(PublicNoteQueryDTO dto) {
        PublicNoteQueryDTO query = normalizeQuery(dto);

        PageHelper.startPage(query.getPageNumOrDefault(), query.getPageSizeOrDefault());
        List<NoteVO> records = noteMapper.listPublicPublished(query);
        PageInfo<NoteVO> pageInfo = new PageInfo<>(records);

        List<PublicNoteListVO> list = pageInfo.getList().stream()
                .map(this::toListVO)
                .toList();
        return new PageResult(pageInfo.getTotal(), list);
    }

    @Override
    @GuestCacheable(
            cacheName = GuestCacheConstant.GUEST_NOTE_DETAIL_CACHE,
            ttlSeconds = GuestCacheConstant.GUEST_NOTE_DETAIL_TTL_SECONDS)
    public PublicNoteDetailVO getPublishedNoteDetail(Long noteId) {
        if (noteId == null || noteId <= 0) {
            throw new BaseException(NoteConstant.NOTE_ID_INVALID);
        }

        NoteVO note = noteMapper.selectPublicPublishedVoById(noteId);
        if (note == null) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        PublicNoteDetailVO vo = new PublicNoteDetailVO();
        BeanUtils.copyProperties(note, vo);
        vo.setTags(noteTagMappingMapper.selectPublicTagNamesByNoteId(noteId));
        vo.setImages(noteImageMappingMapper.selectPublicImagesByNoteId(noteId));
        vo.setEachNotes(noteEachMappingMapper.selectPublicEachNotesBySourceNoteId(noteId));
        vo.setConverted(noteConvertService.getPublishedNoteConvert(noteId));
        return vo;
    }

    private PublicNoteListVO toListVO(NoteVO note) {
        PublicNoteListVO vo = new PublicNoteListVO();
        BeanUtils.copyProperties(note, vo);
        vo.setTags(noteTagMappingMapper.selectPublicTagNamesByNoteId(note.getId()));
        return vo;
    }

    private PublicNoteQueryDTO normalizeQuery(PublicNoteQueryDTO dto) {
        PublicNoteQueryDTO query = dto == null ? new PublicNoteQueryDTO() : dto;
        if (!StringUtils.hasText(query.getKeyword())) {
            query.setKeyword(null);
        } else {
            query.setKeyword(query.getKeyword().trim());
        }
        return query;
    }
}
