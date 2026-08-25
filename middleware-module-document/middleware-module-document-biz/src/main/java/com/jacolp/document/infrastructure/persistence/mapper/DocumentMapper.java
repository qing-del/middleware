package com.jacolp.document.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
/** 将文档元数据、快照指针和个人空间条件映射到 {@code biz_document} SQL。 */
public interface DocumentMapper {

    /** 插入一条文档元数据记录，并回填自增主键。 */
    int insert(DocumentDO document);

    /** 按主键读取文档，包括已删除记录供内部状态判断。 */
    DocumentDO selectById(@Param("id") Long id);

    /** 只读取指定个人空间中的活跃文档，统一实现范围隔离。 */
    DocumentDO selectActiveByIdAndTeamId(@Param("id") Long id, @Param("teamId") Long teamId);

    /** 列出指定个人空间的全部活跃文档。 */
    List<DocumentDO> listActiveByTeamId(@Param("teamId") Long teamId);

    /** 在文档仍活跃且归属匹配时更新最后修改审计字段。 */
    int updateLastModificationIfActive(@Param("id") Long id, @Param("teamId") Long teamId,
                                       @Param("lastModifyTime") LocalDateTime lastModifyTime,
                                       @Param("lastModifyUserId") Long lastModifyUserId);

    /** 在个人范围内更新活跃文档标题和最后修改审计字段。 */
    int updateTitleIfActive(@Param("id") Long id, @Param("teamId") Long teamId,
                            @Param("title") String title,
                            @Param("lastModifyTime") LocalDateTime lastModifyTime,
                            @Param("lastModifyUserId") Long lastModifyUserId);

    /** 仅当前快照仍对应预期日志位点时，才把读取入口切换到新快照对象。 */
    int updateSnapshotPointerIfPersistedLogId(@Param("id") Long id,
                                              @Param("expectedPersistedLogId") Long expectedPersistedLogId,
                                              @Param("contentObjectKey") String contentObjectKey,
                                              @Param("persistedLogId") Long persistedLogId);

    /** 在个人范围内把文档标记为删除，不物理清理正文和操作日志。 */
    int softDeleteByIdAndTeamId(@Param("id") Long id, @Param("teamId") Long teamId,
                                @Param("lastModifyTime") LocalDateTime lastModifyTime,
                                @Param("lastModifyUserId") Long lastModifyUserId);
}
