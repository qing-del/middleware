package com.jacolp.service;

import java.util.List;
import java.util.Map;

import com.jacolp.exception.BaseException;
import com.jacolp.pojo.dto.image.ImageMappingBindDTO;
import com.jacolp.pojo.dto.note.EachMappingBindDTO;
import com.jacolp.pojo.dto.note.NoteMissingInfoDTO;
import com.jacolp.pojo.dto.tag.TagMappingBindDTO;
import com.jacolp.pojo.entity.*;
import com.jacolp.pojo.vo.note.NoteCheckBindingVO;
import com.jacolp.pojo.vo.note.NoteRelationDetailVO;
import com.jacolp.pojo.vo.note.NoteSimpleVO;
import org.apache.ibatis.annotations.Param;

public interface NoteRelationService {

    /**
     * 批量插入 标签初始关系行关系
     * @param noteId 笔记 ID
     * @param tags 标签名称列表
     * @return 插入的行数
     */
    int initTagBatchInsertMappings(Long noteId, List<String> tags);


    /**
     * 批量插入 图片初始关系行关系
     * @param noteId 笔记 ID
     * @param images 图片名称列表
     * @return 插入的行数
     */
    int initImageBatchInsertMappings(Long noteId, List<String> images);


    /**
     * 批量插入初始关系行关系
     * @param noteId 笔记 ID
     * @param noteTitles 笔记标题列表
     * @return 插入的行数
     */
    int initNoteBatchInsertMappings(Long noteId, List<String> noteTitles);

    /**
     * 获取笔记关系详情
     * @param noteId 笔记 ID
     * @param tagMappings 标签关系列表
     * @param tagMap 标签列表
     * @param imageMappings 图片关系列表
     * @param imageMap 图片列表
     * @param eachMappings 关系列表
     * @param targetNoteMap 笔记列表
     * @return 笔记关系详情
     */
    NoteRelationDetailVO getRelationInfo(
            Long noteId,
            List<NoteTagMappingEntity> tagMappings, Map<Long, TagEntity> tagMap,
            List<NoteImageMappingEntity> imageMappings, Map<Long, ImageEntity> imageMap,
            List<NoteEachMappingEntity> eachMappings, Map<Long, NoteEntity> targetNoteMap);

    /**
     * 绑定标签关系
     * @param dto
     */
    NoteTagMappingEntity bindTagMapping(TagMappingBindDTO dto, TagEntity targetTag);

    /**
     * 解绑标签关系
     * @param mappingId
     */
    NoteTagMappingEntity unbindTagMapping(Long mappingId);

    /**
     * 绑定图片关系
     * @param dto
     * @return 被绑定的 映射行实体
     */
    NoteImageMappingEntity bindImageMapping(ImageMappingBindDTO dto, ImageEntity targetImage);

    /**
     * 解绑图片关系
     * @param mappingId
     * @return 被取消绑定的 映射行实体
     */
    NoteImageMappingEntity unbindImageMapping(Long mappingId);

    /**
     * 绑定关系
     * @param dto
     * @param targetNote
     * @return 被绑定的 映射行实体
     */
    NoteEachMappingEntity bindEachMapping(EachMappingBindDTO dto, NoteEntity targetNote);

    /**
     * 解绑关系
     * @param mappingId
     * @return 被取消绑定的 映射行实体
     */
    NoteEachMappingEntity unbindEachMapping(Long mappingId);

    /**
     * 检查笔记关系是否完成
     * @param note
     * @return
     */
    NoteCheckBindingVO checkRelationCompletion(NoteEntity note);

    /**
     * 获取笔记标签关系列表
     * @param noteId
     * @return
     */
    List<NoteTagMappingEntity> listTagMappingsByNoteId(Long noteId);

    /**
     * 获取笔记图片关系列表
     * @param noteId 笔记 ID
     * @return 图片关系列表
     */
    List<NoteImageMappingEntity> listImageMappingsByNoteId(Long noteId);

    /**
     * 获取笔记关系列表
     * @param noteId 笔记 ID
     * @return 笔记关系列表
     */
    List<NoteEachMappingEntity> listEachMappingsByNoteId(Long noteId);

    /**
     * 批量插入标签关系
     * @param mappings
     * @return
     */
    int batchInsertTagMappings(List<NoteTagMappingEntity> mappings);

    /**
     * 批量删除标签关系
     * <p>解除所有和这个标签相关的绑定</p>
     * @param tagId 标签 ID
     * @return 删除的行数
     */
    int unbindTagMappingById(Long tagId);

    /**
     * 尝试批量绑定标签。
     * <p>如果标签已存在且可绑定，则绑定。</p>
     * @param mappings 待绑定的标签映射行
     * @param tagMap 可供选择的标签
     * @throws BaseException 批量绑定失败
     */
    void tryBatchBindTagMappings(List<NoteTagMappingEntity> mappings, Map<String, TagEntity> tagMap);

    /**
     * 尝试批量绑定图片关系。
     * <p>如果图片已存在且可绑定，则绑定。</p>
     * @param mappings 待绑定的图片映射行
     * @param imageMap 可供选择的图片
     * @throws BaseException 批量绑定失败
     */
    void tryBatchBindImageMappings(List<NoteImageMappingEntity> mappings, Map<String, ImageEntity> imageMap);

    /**
     * 尝试批量绑定关系。
     * <p>如果笔记已存在且可绑定，则绑定。</p>
     * @param mappings 待绑定的关系映射行
     * @param noteMap 可供选择的笔记
     * @throws BaseException 批量绑定失败
     */
    void tryBatchBindNoteMappings(List<NoteEachMappingEntity> mappings, Map<String, NoteEntity> noteMap);

    /**
     * 批量更新标签关系状态
     * @param tagIds 标签 ID
     * @param status 状态
     */
    void updateTagMappingPassByTagIds(List<Long> tagIds, Short status);

    /**
     * 批量更新图片关系状态
     * @param imageIds 图片 ID
     * @param status 状态
     */
    void updateImageMappingPassByImageIds(List<Long> imageIds, Short status);

    /**
     * 批量更新关系状态
     * @param sourceNoteIds 源笔记 ID
     * @param status 状态
     */
    void updateEachMappingPassBySourceNoteIds(List<Long> sourceNoteIds, Short status);


    /**
     * 批量删除 以 noteId 为源头的笔记关系
     * @param noteIds 笔记 ID 列表
     */
    void deleteByNoteIds(List<Long> noteIds);

    /**
     * 判断引用关系是否存在
     * @param id 被引用数据类型的 ID
     * @param type 被引用的类型：1-主题,2-标签,3-图片,4-笔记
     * @return 引用关系是否存在
     */
    // TODO 全局的默认策略就是被引用了不得被删除(*)
    boolean isRelated(Long id, Short type);

    /**
     * 判断引用关系是否存在
     * @param ids 被引用数据类型的 ID 列表
     * @param type 被引用的类型：1-主题,2-标签,3-图片,4-笔记
     * @return 引用关系是否存在
     */
    // TODO 全局的默认策略就是被引用了不得被删除(*)
    boolean isRelatedAll(List<Long> ids, Short type);

    /**
     * 计算笔记缺失信息
     * @param noteId 笔记 ID
     * @return 缺失信息数量
     */
    NoteMissingInfoDTO computeNoteMissingInfo(Long noteId);

    /**
     * 获取笔记关系的通过状态
     * @param noteId 笔记 ID
     * @return 如果笔记关系全部通过 返回 true 否则返回 false
     */
    boolean countByNoteIdAndPass(Long noteId);

    /**
     * 获取图片简要列表
     * <p>- 此处没有权限校验</p>
     * @param imageId 图片 ID
     * @return 图片关系列表
     */
    List<NoteSimpleVO> listNoteSimplesByImageId(Long imageId);

    /**
     * 获取图片关系数量
     * @param tagId 标签 ID
     * @return 图片关系数量
     */
    long countRelationByTagId(@Param("tagId") Long tagId);
}
