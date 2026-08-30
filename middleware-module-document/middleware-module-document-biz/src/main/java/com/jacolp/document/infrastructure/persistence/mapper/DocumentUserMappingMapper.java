package com.jacolp.document.infrastructure.persistence.mapper;

import com.jacolp.document.infrastructure.persistence.dataobject.DocumentUserMappingDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 查询文档与用户之间当前生效的直接授权关系。 */
@Mapper
public interface DocumentUserMappingMapper {

    /** 查询一个用户对指定文档的生效授权；不存在或已撤销时返回 {@code null}。 */
    DocumentUserMappingDO selectEnabledByDocumentIdAndUserId(@Param("documentId") Long documentId,
                                                              @Param("userId") Long userId);
}
