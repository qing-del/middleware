package com.jacolp.document.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentMapper {

    int insert(DocumentDO document);

    DocumentDO selectById(@Param("id") Long id);

    DocumentDO selectActiveByIdAndTeamId(@Param("id") Long id, @Param("teamId") Long teamId);

    List<DocumentDO> listActiveByTeamId(@Param("teamId") Long teamId);

    int updateLastModificationIfActive(@Param("id") Long id, @Param("teamId") Long teamId,
                                       @Param("lastModifyTime") LocalDateTime lastModifyTime,
                                       @Param("lastModifyUserId") Long lastModifyUserId);

    int updateSnapshotPointerIfPersistedLogId(@Param("id") Long id,
                                              @Param("expectedPersistedLogId") Long expectedPersistedLogId,
                                              @Param("contentObjectKey") String contentObjectKey,
                                              @Param("persistedLogId") Long persistedLogId);

    int softDeleteByIdAndTeamId(@Param("id") Long id, @Param("teamId") Long teamId,
                                @Param("lastModifyTime") LocalDateTime lastModifyTime,
                                @Param("lastModifyUserId") Long lastModifyUserId);
}
