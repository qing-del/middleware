package com.jacolp.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.jacolp.pojo.entity.ImageAuditRecordEntity;
import com.jacolp.pojo.vo.audit.ImageAuditVO;

/**
 * 图片审核记录数据访问层。
 */
@Mapper
public interface ImageAuditMapper {

    /**
     * 按审核记录 ID 查询。
     */
    @Select("SELECT id, applicant_user_id, image_id, apply_reason, status, reviewer_user_id, reject_reason, create_time, review_time, update_time FROM biz_image_audit_record WHERE id = #{id}")
    ImageAuditRecordEntity selectById(@Param("id") Long id);

    /**
     * 检查是否已存在待审核的审核记录。
     */
    @Select("SELECT COUNT(*) FROM biz_image_audit_record WHERE image_id = #{imageId} AND status = 1")
    int countPendingAuditByImageId(@Param("imageId") Long imageId);

    /**
     * 新增审核记录。
     */
    int insertAuditRecord(ImageAuditRecordEntity record);

    /**
     * 修改审核记录（批准或拒绝）。
     */
    int updateAuditRecord(ImageAuditRecordEntity record);

    /**
     * 条件查询审核列表。
     */
    List<ImageAuditVO> listByCondition(@Param("status") Short status,
                                       @Param("applicantUserId") Long applicantUserId);

    /**
     * 查询待审核审核列表。
     */
    List<ImageAuditVO> listPendingAudits();

    List<ImageAuditRecordEntity> selectPendingByIds(@Param("ids") List<Long> ids);

    int batchReviewByIds(@Param("ids") List<Long> ids,
                         @Param("status") Short status,
                         @Param("reviewerUserId") Long reviewerUserId,
                         @Param("rejectReason") String rejectReason);

    /**
     * 删除指定图片的待审核记录（用户撤销审核申请）。
     */
    @org.apache.ibatis.annotations.Update("UPDATE biz_image_audit_record SET status = 0, update_time = NOW() WHERE image_id = #{imageId} AND status = 1")
    int deletePendingByImageId(@Param("imageId") Long imageId);
}
