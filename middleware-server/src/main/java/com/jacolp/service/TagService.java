package com.jacolp.service;

import com.jacolp.pojo.dto.TagAddDTO;
import com.jacolp.pojo.dto.TagBatchAddDTO;
import com.jacolp.pojo.dto.TagModifyDTO;
import com.jacolp.pojo.dto.TagQueryDTO;
import com.jacolp.pojo.vo.TagBatchAddVO;
import com.jacolp.result.PageResult;

import java.util.List;

public interface TagService {

    void addTag(TagAddDTO dto);

    TagBatchAddVO batchAddTags(TagBatchAddDTO dto);

    void modifyTag(TagModifyDTO dto);

    void deleteTags(List<Long> ids);

    PageResult listTags(TagQueryDTO dto);
}