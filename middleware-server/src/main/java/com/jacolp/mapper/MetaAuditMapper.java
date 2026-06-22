package com.jacolp.mapper;

import com.jacolp.pojo.entity.MetaAuditRecordEntity;
import com.jacolp.pojo.vo.audit.MetaAuditVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MetaAuditMapper {

    List<MetaAuditVO> listByCondition(@Param("applyType") Short applyType,
                                      @Param("status") Short status,
                                      @Param("applicantUserId") Long applicantUserId);

    List<MetaAuditRecordEntity> selectPendingByIds(@Param("ids") List<Long> ids);

    int batchReviewByIds(@Param("ids") List<Long> ids,
                         @Param("status") Short status,
                         @Param("reviewerUserId") Long reviewerUserId,
                         @Param("rejectReason") String rejectReason);

    int insertAuditRecord(MetaAuditRecordEntity record);

    @Select("SELECT COUNT(*) FROM biz_tag_audit_record WHERE target_id = #{targetId} AND status = 1")
    int countPendingAuditByApplyTypeAndTargetId(@Param("applyType") Short applyType, @Param("targetId") Long targetId);

    @Update("UPDATE biz_tag_audit_record SET status = 0, update_time = NOW() WHERE target_id = #{targetId} AND status = 1")
    int deletePendingByApplyTypeAndTargetId(@Param("applyType") Short applyType, @Param("targetId") Long targetId);
}
