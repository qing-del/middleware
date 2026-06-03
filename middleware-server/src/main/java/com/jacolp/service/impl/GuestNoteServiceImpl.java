package com.jacolp.service.impl;

import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.constant.NoteConstant;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.NoteEachMappingMapper;
import com.jacolp.mapper.NoteImageMappingMapper;
import com.jacolp.mapper.NoteMapper;
import com.jacolp.mapper.NoteTagMappingMapper;
import com.jacolp.pojo.dto.note.GuestNoteQueryDTO;
import com.jacolp.pojo.vo.note.GuestNoteDetailVO;
import com.jacolp.pojo.vo.note.GuestNoteListVO;
import com.jacolp.pojo.vo.note.NoteVO;
import com.jacolp.result.PageResult;
import com.jacolp.service.GuestNoteService;
import com.jacolp.service.NoteConvertService;

@Service
public class GuestNoteServiceImpl implements GuestNoteService {

    @Autowired private NoteMapper noteMapper;
    @Autowired private NoteTagMappingMapper noteTagMappingMapper;
    @Autowired private NoteImageMappingMapper noteImageMappingMapper;
    @Autowired private NoteEachMappingMapper noteEachMappingMapper;
    @Autowired private NoteConvertService noteConvertService;

    /**
     * 列出公开笔记
     * @param dto 查询参数
     * @return 笔记列表
     */
    @Override
    public PageResult listPublishedNotes(GuestNoteQueryDTO dto) {
        GuestNoteQueryDTO query = normalizeQuery(dto);

        PageHelper.startPage(query.getPageNumOrDefault(), query.getPageSizeOrDefault());
        List<NoteVO> records = noteMapper.listGuestPublished(query);
        PageInfo<NoteVO> pageInfo = new PageInfo<>(records);

        List<GuestNoteListVO> list = pageInfo.getList().stream()
                .map(this::toListVO)
                .toList();
        return new PageResult(pageInfo.getTotal(), list);
    }

    /**
     * 获取公开笔记详情
     * @param noteId 笔记ID
     * @return 笔记详情
     */
    @Override
    public GuestNoteDetailVO getPublishedNoteDetail(Long noteId) {
        if (noteId == null || noteId <= 0) {
            throw new BaseException(NoteConstant.NOTE_ID_INVALID);
        }

        NoteVO note = noteMapper.selectGuestPublishedVoById(noteId);
        if (note == null) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        GuestNoteDetailVO vo = new GuestNoteDetailVO();
        BeanUtils.copyProperties(note, vo);
        vo.setTags(noteTagMappingMapper.selectPublicTagNamesByNoteId(noteId));
        vo.setImages(noteImageMappingMapper.selectPublicImagesByNoteId(noteId));
        vo.setEachNotes(noteEachMappingMapper.selectPublicEachNotesBySourceNoteId(noteId));
        vo.setConverted(noteConvertService.getPublishedNoteConvert(noteId));
        return vo;
    }

    /**
     * 转换为列表视图对象
     * @param note
     * @return 列表视图对
     */
    private GuestNoteListVO toListVO(NoteVO note) {
        GuestNoteListVO vo = new GuestNoteListVO();
        BeanUtils.copyProperties(note, vo);
        vo.setTags(noteTagMappingMapper.selectPublicTagNamesByNoteId(note.getId()));
        return vo;
    }

    /**
     * 正常化查询参数
     * @param dto
     * @return 非空的查询参数
     */
    private GuestNoteQueryDTO normalizeQuery(GuestNoteQueryDTO dto) {
        GuestNoteQueryDTO query = dto == null ? new GuestNoteQueryDTO() : dto;
        if (!StringUtils.hasText(query.getKeyword())) {
            query.setKeyword(null);
        } else {
            query.setKeyword(query.getKeyword().trim());
        }
        return query;
    }
}
