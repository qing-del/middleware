package com.jacolp.document.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
/** 将文档元数据、快照指针和个人空间条件映射到 {@code biz_document} SQL。 */
public interface DocumentMapper {

    int insert(DocumentDO document);

    DocumentDO selectById(@Param("id") Long id);

    DocumentDO selectActiveByIdAndTeamId(@Param("id") Long id, @Param("teamId") Long teamId);

    List<DocumentDO> listActiveByTeamId(@Param("teamId") Long teamId);

    int updateLastModificationIfActive(@Param("id") Long id, @Param("teamId") Long teamId,
                                       @Param("lastModifyTime") LocalDateTime lastModifyTime,
                                       @Param("lastModifyUserId") Long lastModifyUserId);

    int updateTitleIfActive(@Param("id") Long id, @Param("teamId") Long teamId,
                            @Param("title") String title,
                            @Param("lastModifyTime") LocalDateTime lastModifyTime,
                            @Param("lastModifyUserId") Long lastModifyUserId);

    /** 仅当前快照仍对应预期日志位点时，才把读取入口切换到新快照对象。 */
    int updateSnapshotPointerIfPersistedLogId(@Param("id") Long id,
                                              @Param("expectedPersistedLogId") Long expectedPersistedLogId,
                                              @Param("contentObjectKey") String contentObjectKey,
                                              @Param("persistedLogId") Long persistedLogId);

    int softDeleteByIdAndTeamId(@Param("id") Long id, @Param("teamId") Long teamId,
                                @Param("lastModifyTime") LocalDateTime lastModifyTime,
                                @Param("lastModifyUserId") Long lastModifyUserId);
}
