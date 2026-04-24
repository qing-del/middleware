package com.jacolp.service;

import com.jacolp.pojo.dto.AuditBatchReviewDTO;
import com.jacolp.pojo.dto.ImageAuditListDTO;
import com.jacolp.pojo.dto.MetaAuditListDTO;
import com.jacolp.pojo.dto.NoteAuditListDTO;
import com.jacolp.result.PageResult;

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
     * @param dto 批量审核参数
     * @return 实际处理条数
     */
    int batchReviewMeta(AuditBatchReviewDTO dto);

    /**
     * 批量审核图片申请。
     * <p>仅处理待审核记录，并同步回写图片及图片映射状态。</p>
     *
     * @param dto 批量审核参数
     * @return 实际处理条数
     */
    int batchReviewImage(AuditBatchReviewDTO dto);

    /**
     * 批量审核笔记申请。
     * <p>仅处理待审核记录，并同步回写笔记及内联映射状态。</p>
     *
     * @param dto 批量审核参数
     * @return 实际处理条数
     */
    int batchReviewNote(AuditBatchReviewDTO dto);
}
