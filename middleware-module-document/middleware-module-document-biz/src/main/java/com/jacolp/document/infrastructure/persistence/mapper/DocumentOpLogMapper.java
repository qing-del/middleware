package com.jacolp.document.infrastructure.persistence.mapper;

import java.util.List;

import com.jacolp.document.infrastructure.persistence.dataobject.DocumentOpLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentOpLogMapper {

    /**
     * Inserts a Redis stream cutoff batch. Existing redis/client operation IDs are deliberately
     * ignored so replay after a DB-commit/Redis-XDEL crash stays idempotent.
     */
    int insertBatchIgnoringDuplicates(@Param("logs") List<DocumentOpLogDO> logs);

    List<DocumentOpLogDO> selectByDocumentIdAfterId(@Param("documentId") Long documentId,
                                                    @Param("afterId") Long afterId,
                                                    @Param("limit") Integer limit);

    int deleteByDocumentIdThroughId(@Param("documentId") Long documentId,
                                    @Param("throughId") Long throughId);
}
