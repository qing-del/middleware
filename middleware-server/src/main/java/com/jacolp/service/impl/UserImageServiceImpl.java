package com.jacolp.service.impl;

import com.aliyun.oss.AliyunOSSOperator;
import com.jacolp.constant.ImageConstant;
import com.jacolp.constant.TopicConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.ImageMapper;
import com.jacolp.mapper.TopicMapper;
import com.jacolp.mapper.UserMapper;
import com.jacolp.pojo.domain.UserQuoteStorageDO;
import com.jacolp.pojo.entity.ImageEntity;
import com.jacolp.pojo.entity.TopicEntity;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.pojo.vo.image.UserImageDetailVO;
import com.jacolp.service.UserImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 用户端图片服务实现
 */
@Service
@Slf4j
public class UserImageServiceImpl implements UserImageService {

    @Autowired
    private ImageMapper imageMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TopicMapper topicMapper;

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserImageDetailVO uploadImage(MultipartFile file, Long topicId) {
        Long userId = BaseContext.getCurrentId();
        String filename = file.getOriginalFilename();

        // 校验文件名
        if (!StringUtils.hasText(filename)) {
            throw new BaseException(ImageConstant.IMAGE_EMPTY_FILENAME);
        }

        // 校验主题是否存在
        if (topicId != null && topicId > 0) {
            int count = topicMapper.countById(topicId);
            if (count <= 0) {
                throw new BaseException(TopicConstant.TOPIC_NOT_FOUND);
            }
        }

        // 校验文件大小是否在用户剩余配额内
        UserQuoteStorageDO storageInfo = userMapper.selectQuoteStorageById(userId);
        if (storageInfo == null) {
            throw new BaseException("获取用户存储信息失败");
        }

        Long maxStorageBytes = storageInfo.getMaxStorageBytes();
        Long usedStorageBytes = storageInfo.getUsedStorageBytes();
        if (maxStorageBytes != null && usedStorageBytes != null) {
            Long remaining = maxStorageBytes - usedStorageBytes;
            if (remaining < file.getSize()) {
                throw new BaseException("存储配额不足，剩余空间: " + remaining + " 字节");
            }
        }

        // 唯一性校验：(user_id, topic_id, filename)
        int count = imageMapper.countByUserIdTopicIdAndFilename(userId, topicId, filename);
        if (count > 0) {
            throw new BaseException(ImageConstant.IMAGE_NAME_DUPLICATE);
        }

        // 上传到阿里云 OSS
        String ossUrl = uploadToAliyunOss(file, userId, filename);

        // 写入数据库
        ImageEntity image = new ImageEntity();
        image.setUserId(userId);
        image.setTopicId(topicId);
        image.setFilename(filename);
        image.setOssUrl(ossUrl);
        image.setStorageType(ImageConstant.DEFAULT_STORAGE_TYPE);
        image.setFileSize(file.getSize());
        image.setIsPublic(ImageConstant.IS_PUBLIC_NO);
        image.setIsPass(ImageConstant.AUDIT_STATUS_APPROVED);
        image.setUploadTime(LocalDateTime.now());

        int insertCount = imageMapper.insertImage(image);
        if (insertCount <= 0) {
            throw new BaseException("图片上传失败");
        }

        // 更新用户已用存储空间
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsedStorageBytes(usedStorageBytes + file.getSize());
        int countUser = userMapper.updateById(user);
        if (countUser <= 0) {
            throw new BaseException(UserConstant.UPDATE_USER_STORAGE_FAILED);
        }

        // 转换为 VO 返回
        UserImageDetailVO vo = new UserImageDetailVO();
        BeanUtils.copyProperties(image, vo);
        return vo;
    }

    @Override
    public UserImageDetailVO getImageDetail(Long id) {
        Long userId = BaseContext.getCurrentId();

        // 校验图片ID
        if (id == null || id <= 0) {
            throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        }

        // 查询图片
        ImageEntity image = imageMapper.selectById(id);
        if (image == null) {
            throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        }

        // 校验图片归属
        if (!image.getUserId().equals(userId)) {
            throw new BaseException("只能查看自己的图片");
        }

        // 转换为 VO
        UserImageDetailVO vo = new UserImageDetailVO();
        BeanUtils.copyProperties(image, vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteImage(Long id) {
        Long userId = BaseContext.getCurrentId();

        // 校验图片ID
        if (id == null || id <= 0) {
            throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        }

        // 查询图片
        ImageEntity image = imageMapper.selectById(id);
        if (image == null) {
            throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        }

        // 校验图片归属
        if (!image.getUserId().equals(userId)) {
            throw new BaseException("只能删除自己的图片");
        }

        // 从云存储删除对象文件
        if (image.getStorageType() != null &&
            image.getStorageType() == ImageConstant.STORAGE_TYPE_ALIYUN_OSS) {
            deleteFromAliyunOss(image);
        }

        // 删除数据库记录
        List<Long> ids = new ArrayList<>();
        ids.add(id);
        int deleteCount = imageMapper.deleteByIds(ids);
        if (deleteCount <= 0) {
            throw new BaseException("删除图片失败");
        }

        // 更新用户已用存储空间
        UserQuoteStorageDO storageInfo = userMapper.selectQuoteStorageById(userId);
        if (storageInfo != null && storageInfo.getUsedStorageBytes() != null) {
            Long usedStorageBytes = storageInfo.getUsedStorageBytes();
            UserEntity user = new UserEntity();
            user.setId(userId);
            user.setUsedStorageBytes(Math.max(0L, usedStorageBytes - image.getFileSize()));
            int countUser = userMapper.updateById(user);
            if (countUser <= 0) {
                throw new BaseException(UserConstant.UPDATE_USER_STORAGE_FAILED);
            }
        }
    }

    /**
     * 上传文件到阿里云 OSS
     * @param file 文件
     * @param userId 用户ID
     * @param filename 文件名
     * @return OSS URL
     */
    private String uploadToAliyunOss(MultipartFile file, Long userId, String filename) {
        try {
            String objectKey = buildObjectKey(userId, filename);
            byte[] fileBytes = file.getBytes();
            String ossUrl = aliyunOSSOperator.uploadByObjectName(fileBytes, objectKey);
            log.info("上传到阿里云 OSS 成功，objectKey: {}, ossUrl: {}", objectKey, ossUrl);
            return ossUrl;
        } catch (Exception e) {
            log.error("上传文件到阿里云 OSS 失败，userId: {}, filename: {}", userId, filename, e);
            throw new BaseException(ImageConstant.IMAGE_TRANSFER_FAILED);
        }
    }

    /**
     * 从阿里云 OSS 删除对象
     * @param image 图片实体
     */
    private boolean deleteFromAliyunOss(ImageEntity image) {
        try {
            String objectKey = extractObjectKeyFromUrl(image.getOssUrl());
            if (objectKey == null) {
                return false;
            }
            boolean result = aliyunOSSOperator.delete(objectKey);
            log.info("从阿里云 OSS 删除对象：{}, 结果: {}", objectKey, result);
            return result;
        } catch (Exception e) {
            log.error("删除阿里云 OSS 对象失败，imageId: {}", image.getId(), e);
        }
        return false;
    }

    /**
     * 构造规范 object key：image/{userId}/{uuid}.ext
     */
    private String buildObjectKey(Long userId, String filename) {
        UUID uuid = UUID.randomUUID();
        String extension = "";
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = filename.substring(lastDotIndex);
        }
        return ImageConstant.IMAGE_OSS_DIRECTORY_PREFIX + "/" + userId + "/" + uuid + extension;
    }

    /**
     * 从完整 URL 中提取 object key
     * @param ossUrl OSS URL
     * @return object key
     */
    private String extractObjectKeyFromUrl(String ossUrl) {
        if (ossUrl == null || ossUrl.isEmpty()) {
            return null;
        }
        int index = ossUrl.indexOf(ImageConstant.IMAGE_OSS_DIRECTORY_PREFIX);
        if (index < 0 || index + 1 >= ossUrl.length()) {
            return null;
        }
        return ossUrl.substring(index);
    }
}