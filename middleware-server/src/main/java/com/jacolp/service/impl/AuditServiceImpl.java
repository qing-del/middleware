package com.jacolp.service.impl;

import java.util.List;

import com.jacolp.pojo.dto.audit.AuditReviewContext;
import com.jacolp.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jacolp.exception.BaseException;
import com.jacolp.middleware.module.audit.biz.infrastructure.persistence.mapper.ImageAuditMapper;
import com.jacolp.middleware.module.audit.biz.infrastructure.persistence.mapper.MetaAuditMapper;
import com.jacolp.middleware.module.audit.biz.infrastructure.persistence.mapper.NoteAuditMapper;
import com.jacolp.middleware.module.audit.biz.infrastructure.persistence.dataobject.ImageAuditRecordDO;
import com.jacolp.middleware.module.audit.biz.infrastructure.persistence.dataobject.MetaAuditRecordDO;
import com.jacolp.middleware.module.audit.biz.infrastructure.persistence.dataobject.NoteAuditRecordDO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuditServiceImpl implements AuditService {

    @Autowired private MetaAuditMapper metaAuditMapper;
    @Autowired private ImageAuditMapper imageAuditMapper;
    @Autowired private NoteAuditMapper noteAuditMapper;


    /**
     * 分页查询主题/标签审核列表。
     * @param dto 查询条件（申请类型、审核状态、申请人、分页参数）
     * @return 分页结果
     */

    /**
     * 分页查询图片审核列表。
     *
     * @param dto 查询条件（审核状态、申请人、分页参数）
     * @return 分页结果
     */

    /**
     * 分页查询笔记审核列表。
     *
     * @param dto 查询条件（审核状态、申请人、分页参数）
     * @return 分页结果
     */

    /**
     * 批量审核主题/标签申请。
     * <p>仅处理待审核记录，审核完成后同步回写主题/标签及标签映射状态。</p>
     *
     * @param context 批量审核参数
     * @return 实际处理条数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MetaAuditRecordDO> batchReviewMeta(AuditReviewContext context) {


        // 2) 只拉取待审记录，避免把已处理数据混入本次事务。
        List<MetaAuditRecordDO> pendingRecords = metaAuditMapper.selectPendingByIds(context.getIds());
        if (pendingRecords == null || pendingRecords.isEmpty()) {
            return List.of();
        }

        // 3) 先批量更新审核表，再同步回写业务主表与映射表。
        List<Long> reviewIds = pendingRecords.stream().map(MetaAuditRecordDO::getId).toList();
        int affected = metaAuditMapper.batchReviewByIds(reviewIds, context.getStatus(), context.getReviewerUserId(), context.getRejectReason());

        validRealAffectedByPendingRecores(pendingRecords.size(), affected);

        return pendingRecords;
    }

    /**
     * 批量审核图片申请。
     * <p>仅处理待审核记录，审核完成后同步回写图片及图片映射状态。</p>
     *
     * @param context 批量审核参数
     * @return 实际处理条数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ImageAuditRecordDO> batchReviewImage(AuditReviewContext context) {
        // 2) 只处理待审核的图片记录。
        List<ImageAuditRecordDO> pendingRecords = imageAuditMapper.selectPendingByIds(context.getIds());
        if (pendingRecords == null || pendingRecords.isEmpty()) {
            return List.of();
        }

        // 3) 先更新审核表，再批量回写图片与图片映射的审核状态。
        List<Long> reviewIds = pendingRecords.stream().map(ImageAuditRecordDO::getId).toList();
        int affected = imageAuditMapper.batchReviewByIds(reviewIds, context.getStatus(), context.getReviewerUserId(), context.getRejectReason());

        validRealAffectedByPendingRecores(pendingRecords.size(), affected);

        return pendingRecords;
    }

    /**
     * 批量审核笔记申请。
     * <p>仅处理待审核记录，审核完成后同步回写笔记及内联映射状态。</p>
     *
     * @param context 批量审核参数
     * @return 实际处理条数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<NoteAuditRecordDO> batchReviewNote(AuditReviewContext context) {
        // 2) 只处理待审核的笔记记录。
        List<NoteAuditRecordDO> pendingRecords = noteAuditMapper.selectPendingByIds(context.getIds());
        if (pendingRecords == null || pendingRecords.isEmpty()) {
            return List.of();
        }

        // 3) 先更新审核表，再批量回写笔记与内联映射的审核状态。
        List<Long> reviewIds = pendingRecords.stream().map(NoteAuditRecordDO::getId).toList();
        int affected = noteAuditMapper.batchReviewByIds(reviewIds, context.getStatus(), context.getReviewerUserId(), context.getRejectReason());

        validRealAffectedByPendingRecores(pendingRecords.size(), affected);

        return pendingRecords;
    }


    // ===== 供其他 Service 调用的内部方法 =====
    @Override
    public boolean hasPendingMetaAudit(Short applyType, Long targetId) {
        return metaAuditMapper.countPendingAuditByApplyTypeAndTargetId(applyType, targetId) > 0;
    }

    /**
     * 创建主题/标签审核记录。
     * @param record 审核记录
     * @throws RuntimeException 创建失败
     */
    @Override
    public void createMetaAuditRecord(MetaAuditRecordDO record) {
        int affected = metaAuditMapper.insertAuditRecord(record);
        if (affected < 1) {
            throw new RuntimeException("创建审核记录失败");
        }
    }

    @Override
    public ImageAuditRecordDO getImageAuditRecordById(Long id) {
        return imageAuditMapper.selectById(id);
    }

    @Override
    public boolean hasPendingImageAudit(Long imageId) {
        return imageAuditMapper.countPendingAuditByImageId(imageId) > 0;
    }

    @Override
    public void createImageAuditRecord(ImageAuditRecordDO record) {
        imageAuditMapper.insertAuditRecord(record);
    }

    @Override
    public void updateImageAuditRecord(ImageAuditRecordDO record) {
        imageAuditMapper.updateAuditRecord(record);
    }

    @Override
    public boolean hasPendingNoteAudit(Long noteId) {
        return noteAuditMapper.countPendingAuditByNoteId(noteId) > 0;
    }

    @Override
    public void createNoteAuditRecord(NoteAuditRecordDO record) {
        noteAuditMapper.insertAuditRecord(record);
    }

    @Override
    public void cancelMetaAudit(Short applyType, Long targetId) {
        int affected = metaAuditMapper.deletePendingByApplyTypeAndTargetId(applyType, targetId);
        if (affected < 1) {
            throw new BaseException("未找到待审核的申请记录");
        }
    }

    @Override
    public void cancelImageAudit(Long imageId) {
        int affected = imageAuditMapper.deletePendingByImageId(imageId);
        if (affected < 1) {
            throw new BaseException("未找到待审核的申请记录");
        }
    }

    @Override
    public void cancelNoteAudit(Long noteId) {
        int affected = noteAuditMapper.deletePendingByNoteId(noteId);
        if (affected < 1) {
            throw new BaseException("未找到待审核的申请记录");
        }
    }

    /**
     * 规范化页码。
     *
     * @param pageNum 请求页码
     * @return 合法页码（非法时返回默认值）
     */
    /**
     * 校验实际处理条数。
     * @param pendingRecords 待处理条数
     * @param affected       实际处理条数
     * @throws BaseException 待处理条数与实际处理数不一致
     */
    private static void validRealAffectedByPendingRecores(int pendingRecords, int affected) {
        if (pendingRecords != affected) {
            log.error("Wait to audit the number of records isn't same as real handler number, please check again!");
            throw new BaseException("待审核记录数与实际处理数不一致，请检查！");
        }
    }
}
