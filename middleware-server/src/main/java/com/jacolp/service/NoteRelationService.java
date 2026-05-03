package com.jacolp.service;

import java.util.List;

import com.jacolp.pojo.dto.image.ImageMappingBindDTO;
import com.jacolp.pojo.dto.note.EachMappingBindDTO;
import com.jacolp.pojo.dto.tag.TagMappingBindDTO;
import com.jacolp.pojo.entity.ImageEntity;
import com.jacolp.pojo.entity.NoteEntity;
import com.jacolp.pojo.entity.NoteTagMappingEntity;
import com.jacolp.pojo.entity.TagEntity;
import com.jacolp.pojo.vo.note.NoteCheckBindingVO;
import com.jacolp.pojo.vo.note.NoteRelationDetailVO;

public interface NoteRelationService {

    /**
     * 批量插入 标签初始关系行关系
     * @param noteId 笔记 ID
     * @param tags 标签名称列表
     * @return 插入的行数
     */
    // TODO 这里将 buildTagMappings 方法拷贝过来，然后同时批量插入(*)
    int initTagBatchInsertMappings(Long noteId, List<String> tags);


    /**
     * 批量插入 图片初始关系行关系
     * @param noteId 笔记 ID
     * @param images 图片名称列表
     * @return 插入的行数
     */
    // TODO 这里只是插入未绑定的映射关系行(*)
    int initImageBatchInsertMappings(Long noteId, List<String> images);


    /**
     * 批量插入初始关系行关系
     * @param noteId 笔记 ID
     * @param noteTitles 笔记标题列表
     * @return 插入的行数
     */
    // TODO 这里将 buildEachMappings 拷贝过来，然后同时插入映射关联数据行(*)
    int initNoteBatchInsertMappings(Long noteId, List<String> noteTitles);


    /**
     * 获取笔记关系信息
     * @param noteId
     * @return
     */
    NoteRelationDetailVO getRelationInfo(Long noteId);

    /**
     * 绑定标签关系
     * @param dto
     */
    // TODO 改动了行参，其他地方对其这里(*)
    void bindTagMapping(TagMappingBindDTO dto, TagEntity targetTag);

    /**
     * 解绑标签关系
     * @param mappingId
     */
    void unbindTagMapping(Long mappingId);

    /**
     * 绑定图片关系
     * @param dto
     */
    // TODO 改动了行参，其他地方对其这里(*)
    void bindImageMapping(ImageMappingBindDTO dto, ImageEntity targetImage);

    /**
     * 解绑图片关系
     * @param mappingId
     */
    void unbindImageMapping(Long mappingId);

    /**
     * 绑定关系
     * @param dto
     */
    // TODO 改动了行参，其他地方对其这里(*)
    void bindEachMapping(EachMappingBindDTO dto, NoteEntity targetNote);

    /**
     * 解绑关系
     * @param mappingId
     */
    void unbindEachMapping(Long mappingId);

    /**
     * 检查笔记关系是否完成
     * @param noteId
     * @return
     */
    NoteCheckBindingVO checkRelationCompletion(Long noteId);

    /**
     * 获取笔记标签关系列表
     * @param noteId
     * @return
     */
    List<NoteTagMappingEntity> listTagMappingsByNoteId(Long noteId);

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
     * 获取笔记关系的通过状态
     * @param noteId 笔记 ID
     * @return 如果笔记关系全部通过 返回 true 否则返回 false
     */
    // TODO 截取自 setNotePublishStatus (*)
    boolean countByNoteIdAndPass(Long noteId);

    // TODO (*)
    //  汇总标签/图片/内联笔记三类映射并返回 - 可能需要一个新的 DTO
    //  return buildNoteRelationDetail(noteId);
    //  截取自 NoteServiceImpl.getRelationInfo()
}
