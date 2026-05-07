package com.jacolp.facade;

import com.jacolp.pojo.dto.audit.AuditBatchReviewDTO;

public interface AuditFacade {
    /**
     * 批量审核主题/标签申请。
     * <p>仅处理待审核记录，审核完成后同步回写主题/标签及标签映射状态。</p>
     *
     * @param dto 批量审核参数
     * @return 实际处理条数
     */
    int batchReviewMeta(AuditBatchReviewDTO dto);

    /**
     * 批量审核图片申请。
     * <p>仅处理待审核记录，审核完成后同步回写图片及图片映射状态。</p>
     *
     * @param dto 批量审核参数
     * @return 实际处理条数
     */
    int batchReviewImage(AuditBatchReviewDTO dto);

    /**
     * 批量审核笔记申请。
     * <p>仅处理待审核记录，审核完成后同步回写笔记及内联映射状态。</p>
     *
     * @param dto 批量审核参数
     * @return 实际处理条数
     */
    int batchReviewNote(AuditBatchReviewDTO dto);
}
