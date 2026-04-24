package com.jacolp.service;

import com.jacolp.pojo.dto.tag.TagAddDTO;
import com.jacolp.pojo.dto.tag.TagBatchAddDTO;
import com.jacolp.pojo.dto.tag.TagModifyDTO;
import com.jacolp.pojo.dto.tag.TagQueryDTO;
import com.jacolp.pojo.dto.tag.UserTagQueryDTO;
import com.jacolp.pojo.vo.TagBatchAddVO;
import com.jacolp.result.PageResult;

import java.util.List;

public interface TagService {

    void addTag(TagAddDTO dto);

    TagBatchAddVO batchAddTags(TagBatchAddDTO dto);

    void modifyTag(TagModifyDTO dto);

    void deleteTags(List<Long> ids);

    PageResult listTags(TagQueryDTO dto);

    /**
     * 用户端条件查询：当前用户自己的标签 + 别人已通过审核的标签。
     */
    PageResult listUserTags(UserTagQueryDTO dto);

    /**
     * 用户端发起标签审核申请。
     */
    void submitTagAudit(Long tagId);
}