package com.jacolp.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.constant.AuditConstant;
import com.jacolp.constant.PageConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.ImageAuditMapper;
import com.jacolp.mapper.ImageMapper;
import com.jacolp.mapper.MetaAuditMapper;
import com.jacolp.mapper.NoteAuditMapper;
import com.jacolp.mapper.NoteEachMappingMapper;
import com.jacolp.mapper.NoteImageMappingMapper;
import com.jacolp.mapper.NoteMapper;
import com.jacolp.mapper.NoteTagMappingMapper;
import com.jacolp.mapper.TagMapper;
import com.jacolp.mapper.TopicMapper;
import com.jacolp.pojo.dto.audit.AuditBatchReviewDTO;
import com.jacolp.pojo.dto.image.ImageAuditListDTO;
import com.jacolp.pojo.dto.audit.MetaAuditListDTO;
import com.jacolp.pojo.dto.note.NoteAuditListDTO;
import com.jacolp.pojo.entity.ImageAuditRecordEntity;
import com.jacolp.pojo.entity.MetaAuditRecordEntity;
import com.jacolp.pojo.entity.NoteAuditRecordEntity;
import com.jacolp.pojo.vo.ImageAuditVO;
import com.jacolp.pojo.vo.MetaAuditVO;
import com.jacolp.pojo.vo.NoteAuditVO;
import com.jacolp.result.PageResult;
import com.jacolp.service.AuditService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AuditServiceImpl implements AuditService {

    @Autowired private MetaAuditMapper metaAuditMapper;
    @Autowired private ImageAuditMapper imageAuditMapper;
    @Autowired private NoteAuditMapper noteAuditMapper;

    @Autowired private TopicMapper topicMapper;
    @Autowired private TagMapper tagMapper;
    @Autowired private ImageMapper imageMapper;
    @Autowired private NoteMapper noteMapper;
    @Autowired private NoteTagMappingMapper noteTagMappingMapper;
    @Autowired private NoteImageMappingMapper noteImageMappingMapper;
    @Autowired private NoteEachMappingMapper noteEachMappingMapper;


    /**
     * 分页查询主题/标签审核列表。
     *
     * @param dto 查询条件（申请类型、审核状态、申请人、分页参数）
     * @return 分页结果
     */
    @Override
    public PageResult listMetaAudits(MetaAuditListDTO dto) {
        // 校验请求参数
        MetaAuditListDTO query = dto == null ? new MetaAuditListDTO() : dto;
        int pageNum = normalizePageNum(query.getPageNum() == null ? PageConstant.DEFAULT_PAGE : query.getPageNum());
        int pageSize = normalizePageSize(query.getPageNum() == null ? PageConstant.DEFAULT_PAGE_SIZE : query.getPageSize());
        PageHelper.startPage(pageNum, pageSize);
        List<MetaAuditVO> records = metaAuditMapper.listByCondition(
                query.getApplyType(),
                query.getStatus(),
                query.getApplicantUserId()
        );
        PageInfo<MetaAuditVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 分页查询图片审核列表。
     *
     * @param dto 查询条件（审核状态、申请人、分页参数）
     * @return 分页结果
     */
    @Override
    public PageResult listImageAudits(ImageAuditListDTO dto) {
        ImageAuditListDTO query = dto == null ? new ImageAuditListDTO() : dto;
        int pageNum = normalizePageNum(query.getPageNum() == null ? PageConstant.DEFAULT_PAGE : query.getPageNum());
        int pageSize = normalizePageSize(query.getPageNum() == null ? PageConstant.DEFAULT_PAGE_SIZE : query.getPageSize());
        PageHelper.startPage(pageNum, pageSize);
        List<ImageAuditVO> records = imageAuditMapper.listByCondition(query.getStatus(), query.getApplicantUserId());
        PageInfo<ImageAuditVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 分页查询笔记审核列表。
     *
     * @param dto 查询条件（审核状态、申请人、分页参数）
     * @return 分页结果
     */
    @Override
    public PageResult listNoteAudits(NoteAuditListDTO dto) {
        NoteAuditListDTO query = dto == null ? new NoteAuditListDTO() : dto;
        int pageNum = normalizePageNum(query.getPageNum() == null ? PageConstant.DEFAULT_PAGE : query.getPageNum());
        int pageSize = normalizePageSize(query.getPageNum() == null ? PageConstant.DEFAULT_PAGE_SIZE : query.getPageSize());
        PageHelper.startPage(pageNum, pageSize);
        List<NoteAuditVO> records = noteAuditMapper.listByCondition(query.getStatus(), query.getApplicantUserId());
        PageInfo<NoteAuditVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 批量审核主题/标签申请。
     * <p>仅处理待审核记录，审核完成后同步回写主题/标签及标签映射状态。</p>
     *
     * @param dto 批量审核参数
     * @return 实际处理条数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchReviewMeta(AuditBatchReviewDTO dto) {
        // 1) 校验请求参数并过滤无效 ID。
        ReviewContext context = validateReviewRequest(dto);

        // 2) 只拉取待审记录，避免把已处理数据混入本次事务。
        List<MetaAuditRecordEntity> pendingRecords = metaAuditMapper.selectPendingByIds(context.ids);
        if (pendingRecords == null || pendingRecords.isEmpty()) {
            return 0;
        }

        // 3) 先批量更新审核表，再同步回写业务主表与映射表。
        List<Long> reviewIds = pendingRecords.stream().map(MetaAuditRecordEntity::getId).toList();
        int affected = metaAuditMapper.batchReviewByIds(reviewIds, context.status, context.reviewerUserId, context.rejectReason);

        List<Long> topicIds = new ArrayList<>();
        List<Long> tagIds = new ArrayList<>();
        for (MetaAuditRecordEntity record : pendingRecords) {
            if (record.getApplyType() == null || record.getTargetId() == null) {
                continue;
            }
            if (record.getApplyType().equals(AuditConstant.TOPIC_APPLY_TYPE)) {
                topicIds.add(record.getTargetId());
            } else if (record.getApplyType().equals(AuditConstant.TAG_APPLY_TYPE)) {
                tagIds.add(record.getTargetId());
            }
        }

        if (!topicIds.isEmpty()) {
            topicMapper.updatePassByIds(topicIds, context.status);
        }
        if (!tagIds.isEmpty()) {
            updateTagsAndTagMappingsPass(tagIds, context, affected);
        }

        return affected;
    }

    /**
     * 批量审核图片申请。
     * <p>仅处理待审核记录，审核完成后同步回写图片及图片映射状态。</p>
     *
     * @param dto 批量审核参数
     * @return 实际处理条数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchReviewImage(AuditBatchReviewDTO dto) {
        // 1) 校验请求参数。
        ReviewContext context = validateReviewRequest(dto);

        // 2) 只处理待审核的图片记录。
        List<ImageAuditRecordEntity> pendingRecords = imageAuditMapper.selectPendingByIds(context.ids);
        if (pendingRecords == null || pendingRecords.isEmpty()) {
            return 0;
        }

        // 3) 先更新审核表，再批量回写图片与图片映射的审核状态。
        List<Long> reviewIds = pendingRecords.stream().map(ImageAuditRecordEntity::getId).toList();
        int affected = imageAuditMapper.batchReviewByIds(reviewIds, context.status, context.reviewerUserId, context.rejectReason);

        List<Long> imageIds = pendingRecords.stream()
                .map(ImageAuditRecordEntity::getImageId)
                .filter(id -> id != null)
                .toList();
        if (!imageIds.isEmpty()) {
            updateImagesAndImageMappingsPass(imageIds, context, affected);
        }

        return affected;
    }

    /**
     * 批量审核笔记申请。
     * <p>仅处理待审核记录，审核完成后同步回写笔记及内联映射状态。</p>
     *
     * @param dto 批量审核参数
     * @return 实际处理条数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchReviewNote(AuditBatchReviewDTO dto) {
        // 1) 校验请求参数。
        ReviewContext context = validateReviewRequest(dto);

        // 2) 只处理待审核的笔记记录。
        List<NoteAuditRecordEntity> pendingRecords = noteAuditMapper.selectPendingByIds(context.ids);
        if (pendingRecords == null || pendingRecords.isEmpty()) {
            return 0;
        }

        // 3) 先更新审核表，再批量回写笔记与内联映射的审核状态。
        List<Long> reviewIds = pendingRecords.stream().map(NoteAuditRecordEntity::getId).toList();
        int affected = noteAuditMapper.batchReviewByIds(reviewIds, context.status, context.reviewerUserId, context.rejectReason);

        List<Long> noteIds = pendingRecords.stream()
                .map(NoteAuditRecordEntity::getNoteId)
                .filter(id -> id != null)
                .toList();
        if (!noteIds.isEmpty()) {
            updateNotesAndEachMappingsPass(noteIds, context, affected);
        }

        return affected;
    }

    /**
     * 校验批量审核请求并构造上下文。
     * <p>会做 ID 去重、审核状态合法性校验，并在拒绝场景下填充默认拒绝原因。</p>
     *
     * @param dto 批量审核参数
     * @return 审核上下文(一定是非空集合)
     */
    private ReviewContext validateReviewRequest(AuditBatchReviewDTO dto) {
        // 检查审核记录ID列表
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BaseException("审核记录ID列表不能为空");
        }

        // 检查审核状态是否有效
        if (!AuditConstant.PASS.equals(dto.getStatus())
                && !AuditConstant.REJECT.equals(dto.getStatus())) {
            throw new BaseException("无效的审核状态");
        }

        // 去重
        Set<Long> idSet = new LinkedHashSet<>();
        for (Long id : dto.getIds()) {
            if (id == null || id <= 0L) {
                continue;
            }
            idSet.add(id);
        }
        // 检查一下列表是否为空
        if (idSet.isEmpty()) {
            throw new BaseException("审核记录ID列表不能为空");
        }

        // 填充默认拒绝原因
        String rejectReason = null;
        if (AuditConstant.REJECT.equals(dto.getStatus())) {
            rejectReason = StringUtils.hasText(dto.getRejectReason())
                    ? dto.getRejectReason().trim()
                    : AuditConstant.DEFAULT_REJECT_REASON;
        }

        ReviewContext context = new ReviewContext();
        context.ids = new ArrayList<>(idSet);
        context.status = dto.getStatus();
        context.reviewerUserId = BaseContext.getCurrentId();
        context.rejectReason = rejectReason;
        return context;
    }

    /**
     * 规范化页码。
     *
     * @param pageNum 请求页码
     * @return 合法页码（非法时返回默认值）
     */
    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? PageConstant.DEFAULT_PAGE : pageNum;
    }

    /**
     * 规范化分页大小。
     *
     * @param pageSize 请求页大小
     * @return 合法分页大小（非法时返回默认值）
     */
    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize <= 0 ? PageConstant.DEFAULT_PAGE_SIZE : pageSize;
    }

    private static class ReviewContext {
        private List<Long> ids;
        private Short status;
        private Long reviewerUserId;
        private String rejectReason;
    }

    /**
     * 批量审核标签及其映射行。
     * <p>先更新 biz_tag，再回写对应的 biz_note_tag_mapping，避免逐条循环更新。</p>
     *
     * @param tagIds 标签ID列表
     * @param context 审核上下文
     * @param affected 本次审核表实际影响行数
     */
    private void updateTagsAndTagMappingsPass(List<Long> tagIds, ReviewContext context, int affected) {
        // 批量更新标签状态。
        int count = tagMapper.updatePassByIds(tagIds, context.status);
        // 批量更新标签映射状态。
        noteTagMappingMapper.updateByTagIds(tagIds, context.status);
        // 保守校验：如果业务表更新行数不足，直接回滚。
        if (count < affected) {
            log.error("Failed to update tag status! : {}", tagIds);
            throw new BaseException("更新标签状态失败");
        }
    }

    /**
     * 批量审核图片及其映射行。
     * <p>先更新 biz_image，再回写对应的 biz_note_image_mapping，保持审核状态一致。</p>
     *
     * @param imageIds 图片ID列表
     * @param context 审核上下文
     * @param affected 本次审核表实际影响行数
     */
    private void updateImagesAndImageMappingsPass(List<Long> imageIds, ReviewContext context, int affected) {
        // 批量更新图片状态。
        int count = imageMapper.updatePassByIds(imageIds, context.status);
        // 批量更新图片映射状态。
        noteImageMappingMapper.updateByImageIds(imageIds, context.status);
        // 保守校验：业务表更新必须跟审核表影响行数一致。
        if (count < affected) {
            log.error("Failed to update image status! : {}", imageIds);
            throw new BaseException("更新图片状态失败");
        }
    }

    /**
     * 批量审核笔记及其内联映射。
     * <p>先更新 biz_note，再回写对应的 biz_note_each_mapping，保持审核状态一致。</p>
     *
     * @param noteIds 笔记ID列表
     * @param context 审核上下文
     * @param affected 本次审核表实际影响行数
     */
    private void updateNotesAndEachMappingsPass(List<Long> noteIds, ReviewContext context, int affected) {
        // 批量更新笔记状态。
        int count = noteMapper.updatePassByIds(noteIds, context.status);
        // 批量更新内联映射状态。
        noteEachMappingMapper.updateBySourceNoteIds(noteIds, context.status);
        // 保守校验：业务表更新必须跟审核表影响行数一致。
        if (count < affected) {
            log.error("Failed to update note status! : {}", noteIds);
            throw new BaseException("更新笔记状态失败");
        }
    }
}
