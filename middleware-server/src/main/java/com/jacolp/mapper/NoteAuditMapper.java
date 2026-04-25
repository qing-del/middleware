package com.jacolp.mapper;

import com.jacolp.pojo.entity.NoteAuditRecordEntity;
import com.jacolp.pojo.vo.audit.NoteAuditVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    int insertAuditRecord(NoteAuditRecordEntity record);

    @Select("SELECT COUNT(*) FROM biz_note_audit_record WHERE note_id = #{noteId} AND status = 0")
    int countPendingAuditByNoteId(@Param("noteId") Long noteId);
}
