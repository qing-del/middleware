package com.jacolp.service.impl;

import java.util.List;

import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
import com.jacolp.pojo.entity.NoteContextEntity;
import com.jacolp.pojo.entity.NoteEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jacolp.constant.NoteConstant;
import com.jacolp.context.NoteImageResolveContext;
import com.jacolp.converter.MarkdownHtmlEngine;
import com.jacolp.converter.MarkdownHtmlEngine.FrontMatter;
import com.jacolp.converter.MarkdownHtmlEngine.HtmlProcessResult;
import com.jacolp.exception.BaseException;
import com.jacolp.component.JsonOperator;
import com.jacolp.mapper.NoteConvertMapper;
import com.jacolp.pojo.entity.NoteConvertedEntity;
import com.jacolp.pojo.vo.note.NoteConvertMetaVO;
import com.jacolp.pojo.vo.note.NoteConvertResultVO;
import com.jacolp.service.NoteConvertService;

import lombok.extern.slf4j.Slf4j;

/**
 * 笔记 Markdown→HTML 转换实现。
 *
 * <p>负责 {@code biz_note_converted} 表的读写。通过 {@link MarkdownHtmlEngine}
 * 将 Markdown 原文转换为 TOC + 正文 HTML，并提取前置元信息（标题、标签、创建时间）。
 * 转换过程中将当前 noteId 放入 {@link NoteImageResolveContext}，供图片解析插件使用。</p>
 */
@Service
@Slf4j
public class NoteConvertServiceImpl implements NoteConvertService {

    @Autowired private NoteConvertMapper noteConvertMapper;
    @Autowired private MarkdownHtmlEngine markdownHtmlEngine;
    @Autowired private JsonOperator jsonOperator;

    /**
     * 将 Markdown 原文转换为 HTML 并写入数据库。
     * @param note      笔记 ID
     * @param context Markdown 原文
     * @return 解析出的标题（可能不同于文件名）
     * @throws BaseException 写入数据库失败的时候会抛出此异常
     */
    @Override
    public String convertAndSave(NoteEntity note, NoteContextEntity context) {
        // 设置上下文供图片解析插件获取 noteId
        NoteImageResolveContext.setCurrentNoteId(note.getId());
        try {
            HtmlProcessResult result = markdownHtmlEngine.process(context.getMarkdownContent());
            FrontMatter meta = result.meta().withFallbackTitle(note.getTitle());

            NoteConvertedEntity converted = buildNoteConvertEntity(note.getId(), meta, result);
            int affected = noteConvertMapper.upsertConverted(converted);
            if (affected < 1) {
                log.error("Note convert failed, noteId: {}", note.getId());
                throw new BaseException(NoteConstant.NOTE_CONVERT_FAILED);
            }
            return meta.title();
        } finally {
            NoteImageResolveContext.clear();
        }
    }

    /**
     * 删除单条转换结果。
     * @throws BaseException 删除失败的时候会抛出此异常
     */
    @Override
    public void delete(Long noteId) {
        int affected = noteConvertMapper.deleteByNoteId(noteId);
        if (affected < 1) {
            log.error("Note convert delete failed, noteId: {}", noteId);
            throw new BaseException(NoteConstant.NOTE_DELETE_FAILED);
        }
    }

    /**
     * 批量删除转换结果。
     */
    @Override
    public void deleteAllByNoteIds(List<Long> noteIds) {
        if (noteIds != null && !noteIds.isEmpty()) {
            noteConvertMapper.deleteByNoteIds(noteIds);
        }
    }

    /**
     * 获取笔记转换结果
     * <p>- 如果是管理员会跳过校验</p>
     * @param noteId 笔记 ID
     * @return 转换结果 VO
     */
    @Override
    public NoteConvertResultVO getNoteConvert(Long noteId) {
        NoteConvertedEntity converted = null;
        if (PermissionContext.isAdmin()) {
            converted = noteConvertMapper.selectByNoteIdWithValidUserId(noteId, null);
        } else {
            converted = noteConvertMapper.selectByNoteIdWithValidUserId(noteId, BaseContext.getCurrentId());
        }

        // 检查笔记是否存在 / 用户没有所属权也会返回 null
        if (converted == null) {
            throw new BaseException(NoteConstant.NOTE_NOT_CONVERTED);
        }

        // 返回结果
        return toConvertResultVO(converted);
    }

    @Override
    public NoteConvertResultVO getPublishedNoteConvert(Long noteId) {
        NoteConvertedEntity converted = noteConvertMapper.selectPublishedByNoteId(noteId);
        if (converted == null) {
            throw new BaseException(NoteConstant.NOTE_NOT_CONVERTED);
        }
        return toConvertResultVO(converted);
    }

    /**
     * 将库表实体映射为 VO。
     */
    private NoteConvertResultVO toConvertResultVO(NoteConvertedEntity entity) {
        NoteConvertResultVO resultVO = new NoteConvertResultVO();
        NoteConvertMetaVO metaVO = new NoteConvertMetaVO();

        metaVO.setTitle(entity.getTitle());
        metaVO.setCreateTime(entity.getCreateTimeStr());
        List<String> tags = jsonOperator.fromJson(entity.getTagsJson(), new TypeReference<List<String>>() {});
        metaVO.setTags(tags != null ? tags : List.of());

        resultVO.setMeta(metaVO);
        resultVO.setTocHtml(entity.getTocHtml());
        resultVO.setBodyHtml(entity.getBodyHtml());
        return resultVO;
    }

    /**
     * 构建笔记转换实体。
     */
    private @NonNull NoteConvertedEntity buildNoteConvertEntity(Long noteId, FrontMatter meta, HtmlProcessResult result) {
        NoteConvertedEntity converted = new NoteConvertedEntity();
        converted.setNoteId(noteId);
        converted.setTitle(meta.title());
        converted.setTagsJson(jsonOperator.toJson(meta.tags()));
        converted.setCreateTimeStr(meta.createTime());
        converted.setTocHtml(result.tocHtml());
        converted.setBodyHtml(result.bodyHtml());
        return converted;
    }
}
