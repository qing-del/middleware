package com.jacolp.service;

import com.jacolp.pojo.dto.tag.UserTagAddDTO;
import com.jacolp.pojo.dto.tag.UserTagAssignDTO;
import com.jacolp.pojo.dto.tag.UserTagRemoveDTO;
import com.jacolp.pojo.vo.tag.UserTagSimpleVO;

import java.util.List;

/**
 * 用户端标签服务接口
 */
public interface UserTagService {

    /**
     * 查询当前用户的标签列表
     * @return 标签列表
     */
    List<UserTagSimpleVO> listTags();

    /**
     * 创建标签
     * @param dto 创建标签请求
     */
    void createTag(UserTagAddDTO dto);

    /**
     * 删除标签（软删除）
     * @param id 标签ID
     */
    void deleteTag(Long id);

    /**
     * 绑定标签
     * @param dto 绑定标签请求
     */
    void assignTag(UserTagAssignDTO dto);

    /**
     * 解除绑定
     * @param dto 解除绑定请求
     */
    void removeTag(UserTagRemoveDTO dto);
}