package com.jacolp.service;

import com.jacolp.pojo.dto.audit.AuditBatchReviewDTO;
import com.jacolp.pojo.dto.audit.AuditReviewContext;
import com.jacolp.pojo.dto.image.ImageAuditListDTO;
import com.jacolp.pojo.dto.audit.MetaAuditListDTO;
import com.jacolp.pojo.dto.note.NoteAuditListDTO;
import com.jacolp.pojo.entity.ImageAuditRecordEntity;
import com.jacolp.pojo.entity.MetaAuditRecordEntity;
import com.jacolp.pojo.entity.NoteAuditRecordEntity;
import com.jacolp.result.PageResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AuditService {

    /**
     * 分页查询主题/标签审核列表。
     *
     * @param dto 查询条件（申请类型、审核状态、申请人、分页参数）
     * @return 分页结果
     */
    PageResult listMetaAudits(MetaAuditListDTO dto);

    /**
     * 分页查询图片审核列表。
     *
     * @param dto 查询条件（审核状态、申请人、分页参数）
     * @return 分页结果
     */
    PageResult listImageAudits(ImageAuditListDTO dto);

    /**
     * 分页查询笔记审核列表。
     *
     * @param dto 查询条件（审核状态、申请人、分页参数）
     * @return 分页结果
     */
    PageResult listNoteAudits(NoteAuditListDTO dto);

    /**
     * 批量审核主题/标签申请。
     * <p>仅处理待审核记录，并同步回写主题/标签及标签映射状态。</p>
     *
     * @param context 批量审核参数
     * @return 处理的元信息记录实体类列表
     */
    List<MetaAuditRecordEntity> batchReviewMeta(AuditReviewContext context);

    /**
     * 批量审核图片申请。
     * <p>仅处理待审核记录，并同步回写图片及图片映射状态。</p>
     *
     * @param context 批量审核参数
     * @return 实际处理条数
     */
    List<ImageAuditRecordEntity> batchReviewImage(AuditReviewContext context);

    /**
     * 批量审核笔记申请。
     * <p>仅处理待审核记录，并同步回写笔记及内联映射状态。</p>
     *
     * @param context 批量审核参数
     * @return 实际处理条数
     */
    List<NoteAuditRecordEntity> batchReviewNote(AuditReviewContext context);

    // ===== 供其他 Service 调用的内部方法 =====

    boolean hasPendingMetaAudit(Short applyType, Long targetId);

    /**
     * 创建主题/标签审核记录。
     * @param record 审核记录
     * @throws RuntimeException 创建失败
     */
    void createMetaAuditRecord(MetaAuditRecordEntity record);

    ImageAuditRecordEntity getImageAuditRecordById(Long id);

    boolean hasPendingImageAudit(Long imageId);

    void createImageAuditRecord(ImageAuditRecordEntity record);

    void updateImageAuditRecord(ImageAuditRecordEntity record);

    boolean hasPendingNoteAudit(Long noteId);

    void createNoteAuditRecord(NoteAuditRecordEntity record);

    /**
     * 撤销主题/标签的待审核申请（删除待审核记录）。
     * @param applyType 申请类型（1=主题, 2=标签）
     * @param targetId 主题/标签 ID
     * @throws com.jacolp.exception.BaseException 未找到待审核记录
     */
    void cancelMetaAudit(Short applyType, Long targetId);

    /**
     * 撤销图片的待审核申请（删除待审核记录）。
     * @param imageId 图片 ID
     * @throws com.jacolp.exception.BaseException 未找到待审核记录
     */
    void cancelImageAudit(Long imageId);

    /**
     * 撤销笔记的待审核申请（删除待审核记录）。
     * <p>仅删除审核记录，笔记状态由调用方处理。</p>
     * @param noteId 笔记 ID
     * @throws com.jacolp.exception.BaseException 未找到待审核记录
     */
    void cancelNoteAudit(Long noteId);
}
