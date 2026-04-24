package com.jacolp.mapper;

import com.jacolp.pojo.entity.MetaAuditRecordEntity;
import com.jacolp.pojo.vo.MetaAuditVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    @Select("SELECT COUNT(*) FROM biz_meta_audit_record WHERE apply_type = #{applyType} AND target_id = #{targetId} AND status = 0")
    int countPendingAuditByApplyTypeAndTargetId(@Param("applyType") Short applyType, @Param("targetId") Long targetId);
}
