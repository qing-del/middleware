package com.jacolp.service;

import com.jacolp.pojo.dto.image.UserImageDeleteDTO;
import com.jacolp.pojo.vo.image.UserImageDetailVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户端图片服务接口
 */
public interface UserImageService {

    /**
     * 上传图片
     * @param file 图片文件
     * @param topicId 主题ID（可选）
     * @return 上传后的图片详情
     */
    UserImageDetailVO uploadImage(MultipartFile file, Long topicId);

    /**
     * 获取图片详情
     * @param id 图片ID
     * @return 图片详情
     */
    UserImageDetailVO getImageDetail(Long id);

    /**
     * 删除图片
     * @param id 图片ID
     */
    void deleteImage(Long id);
}