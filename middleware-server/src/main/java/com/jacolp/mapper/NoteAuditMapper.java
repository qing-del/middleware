package com.jacolp.mapper;

import com.jacolp.pojo.entity.NoteAuditRecordEntity;
import com.jacolp.pojo.vo.NoteAuditVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoteAuditMapper {

    List<NoteAuditVO> listByCondition(@Param("status") Short status,
                                      @Param("applicantUserId") Long applicantUserId);

    List<NoteAuditRecordEntity> selectPendingByIds(@Param("ids") List<Long> ids);

    int batchReviewByIds(@Param("ids") List<Long> ids,
                         @Param("status") Short status,
                         @Param("reviewerUserId") Long reviewerUserId,
                         @Param("rejectReason") String rejectReason);
}
