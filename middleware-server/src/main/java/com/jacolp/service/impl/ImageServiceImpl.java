package com.jacolp.service.impl;

import java.time.LocalDateTime;
import java.util.*;

import com.jacolp.constant.PageConstant;
import com.jacolp.constant.TopicConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.mapper.*;
import com.jacolp.pojo.domain.UserQuoteStorageDO;
import com.jacolp.pojo.entity.ImageDeleteDeadLetterEntity;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.pojo.vo.ImageBatchDeleteVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.aliyun.oss.AliyunOSSOperator;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.context.BaseContext;
import com.jacolp.constant.ImageConstant;
import com.jacolp.exception.BaseException;
import com.jacolp.pojo.domain.ImageNoteCountDO;
import com.jacolp.pojo.dto.ImageAuditReviewDTO;
import com.jacolp.pojo.dto.ImageModifyInfoDTO;
import com.jacolp.pojo.dto.ImageQueryDTO;
import com.jacolp.pojo.entity.ImageAuditRecordEntity;
import com.jacolp.pojo.entity.ImageEntity;
import com.jacolp.pojo.vo.ImageVO;
import com.jacolp.pojo.vo.NoteSimpleVO;
import com.jacolp.result.PageResult;
import com.jacolp.service.ImageService;

import lombok.extern.slf4j.Slf4j;

/**
 * 图片服务实现。
 * 
 * 约束：
 * 1. 所有查询和修改都按当前登录用户隔离（user_id）。
 * 2. 删除图片前必须校验该图片是否被笔记引用。
 * 3. 图片只使用云对象存储：
 *    - 1: 阿里云 OSS（默认启用）
 *    - 2: Cloudflare R2（仅预留扩展位，暂未实现）
 */
@Service
@Slf4j
public class ImageServiceImpl implements ImageService {

    @Autowired private ImageMapper imageMapper;
    @Autowired private ImageAuditMapper imageAuditMapper;
    @Autowired private AliyunOSSOperator aliyunOSSOperator;
    @Autowired private NoteMapper noteMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private TopicMapper topicMapper;
    @Autowired private ImageDeleteDeadLetterMapper imageDeleteDeadLetterMapper;

    /**
     * 上传图片。
     * 
     * 流程：
     * 1. 按 (user_id, topic_id, filename) 校验唯一性。
     * 2. 写入物理文件。
     * 3. 写入数据库记录。
     * 4. 累加用户已用存储空间。
     */
    @Override
    public ImageVO uploadImage(MultipartFile file, Long topicId) {
        Long userId = BaseContext.getCurrentId();
        String filename = file.getOriginalFilename();
        validateFilename(filename);

        // 校验主题是否存在
        validateTopic(topicId);

        // 唯一性校验：(user_id, topic_id, filename)
        int count = imageMapper.countByUserIdTopicIdAndFilename(userId, topicId, filename);
        if (count > 0) {
            throw new BaseException(ImageConstant.IMAGE_NAME_DUPLICATE);
        }

        // 默认上传到阿里云 OSS，object key 规范：image/{userId}/{filename}
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
        
        // 管理员添加直接通过
        image.setIsPass(ImageConstant.AUDIT_STATUS_APPROVED);
        image.setUploadTime(LocalDateTime.now());

        int insertCount = imageMapper.insertImage(image);
        if (insertCount <= 0) {
            throw new BaseException("图片上传失败");
        }


        // 转换为 VO 返回
        ImageVO vo = new ImageVO();
        BeanUtils.copyProperties(image, vo);
        vo.setId(null);

        return vo;
    }

    /**
     * 修改图片源文件。
     * @id 图片 ID
     */
    @Override
    public void modifyImageFile(Long id, MultipartFile newFile) {
        Long userId = BaseContext.getCurrentId();
        validateImageId(id);

        // 查询旧记录
        ImageEntity existed = imageMapper.selectById(id);
        if (existed == null) {
            throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        }
        if (!existed.getUserId().equals(userId)) {
            // 管理员也不能随便修改别人的图片内容
            throw new BaseException(ImageConstant.IMAGE_NOT_OWNER);
        }

        if (existed.getStorageType() == null
            || existed.getStorageType() != ImageConstant.STORAGE_TYPE_ALIYUN_OSS) {
            // TODO 预留 R2 扩展，当前仅先实现阿里云 OSS
            throw new BaseException(ImageConstant.IMAGE_STORAGE_PROVIDER_NOT_SUPPORTED);
        }

        Long newFileSize = newFile.getSize();

        // 获取原来的 object key
        String oldObjectKey = extractObjectKeyFromUrl(existed.getOssUrl());
        String newOssUrl = uploadToAliyunOss(newFile, oldObjectKey);    // 覆盖上传

        // 更新数据库
        existed.setOssUrl(newOssUrl);
        existed.setFileSize(newFileSize);

        int updateCount = imageMapper.updateImage(existed);
        if (updateCount <= 0) {
            throw new BaseException("图片更新失败");
        }
    }

    /**
     * 修改图片信息（改名/换主题）。
     */
    @Override
    public void modifyImageInfo(ImageModifyInfoDTO dto) {
        Long userId = BaseContext.getCurrentId();
        validateImageId(dto.getId());
        validateTopic(dto.getTopicId());

        // 查询现有记录
        ImageEntity existed = imageMapper.selectById(dto.getId());
        if (existed == null || !existed.getUserId().equals(userId)) {
            throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        }

        // 仅更新提供的字段
        if (dto.getFilename() != null && !dto.getFilename().isEmpty()) {
            String newFilename = dto.getFilename().trim();
            // 检查新文件名是否重复
            if (!newFilename.equals(existed.getFilename())) {
                int count = imageMapper.countByUserIdTopicIdAndFilename(userId, dto.getTopicId(), newFilename);
                if (count > 0) {
                    throw new BaseException(ImageConstant.IMAGE_NAME_DUPLICATE);
                }
            }
            existed.setFilename(newFilename);
        }

        // 如果传过来的主题不为空 则换主题
        if (dto.getTopicId() != null) {
            existed.setTopicId(dto.getTopicId());
        }

        int updateCount = imageMapper.updateImage(existed);
        if (updateCount <= 0) {
            throw new BaseException("图片信息更新失败");
        }
    }

    /**
     * 云厂商迁移入口（当前仅阿里云已实现，R2 迁移逻辑预留）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferToCloud(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        int successCount = 0;
        int failureCount = 0;

        for (Long imageId : ids) {
            try {
                ImageEntity image = imageMapper.selectById(imageId);
                if (image == null) {
                    continue;
                }
                if (image.getStorageType() != null
                    && image.getStorageType() == ImageConstant.STORAGE_TYPE_ALIYUN_OSS) {
                    // 已经是默认云厂商，无需迁移
                    continue;
                }
                // TODO 预留：未来实现 R2 -> 阿里云 OSS 迁移
                log.warn("暂不支持从当前存储类型迁移到阿里云 OSS，imageId: {}, storageType: {}",
                        imageId, image.getStorageType());
                failureCount++;
            } catch (Exception e) {
                log.error("转移到云存储失败，imageId: {}", imageId, e);
                failureCount++;
            }
        }

        log.info("转移到云存储完成，成功: {}, 失败: {}", successCount, failureCount);
    }

    /**
     * 已废弃：不再支持 OSS -> 本地迁移。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferToLocal(List<Long> ids) {
        throw new BaseException("本地存储方案已废弃，当前仅支持云对象存储");
    }

    /**
     * 删除图片（批量）。
     * <p>
     * 策略：先全量校验引用，如有任何一张图片被引用，则全量拒绝。
     *
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImageBatchDeleteVO deleteImages(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BaseException("待删除的图片 ID 列表不能为空");
        }

        // 转换为 LinkedHashSet 以去重并保持顺序
        Set<Long> idSet = new LinkedHashSet<>(ids);

        // 查询删除检查
        List<ImageNoteCountDO> deleteChecks = imageMapper.selectDeleteChecksByIds(new ArrayList<>(idSet));

        // 检查所有引用情况
        List<String> inUseImageNames = new ArrayList<>();
        for (ImageNoteCountDO check : deleteChecks) {
            if (check.getRefCount() != null && check.getRefCount() > 0) {
                inUseImageNames.add(check.getFilename());
            }
        }

        // 若存在被引用的图片，直接拒绝
        if (!inUseImageNames.isEmpty()) {
            String message = ImageConstant.IMAGE_IN_USE + "：" + String.join(", ", inUseImageNames);
            throw new BaseException(message);
        }

        // 创建一个用于存储用户示范空间量的映射表
        HashMap<Long, Long> userStorageMap = new HashMap<>();
        ImageBatchDeleteVO result = new ImageBatchDeleteVO(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>());
        ArrayList<ImageDeleteDeadLetterEntity> imageDeleteDeadLetterEntities = new ArrayList<>();

        List<ImageEntity> images = imageMapper.selectByIds(new ArrayList<>(idSet));
        images.forEach(image -> {
            ImageDeleteDeadLetterEntity imageDeleteDeadLetterEntity = new ImageDeleteDeadLetterEntity(
                    image.getId(),
                    image.getOssUrl(),
                    ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_WAITING,
                    0,
                    null,
                    null);

            // 获取插入队列结果
            boolean addResult =  imageDeleteDeadLetterEntities.add(imageDeleteDeadLetterEntity);

            // 做保底检查
            if (!addResult) {
                result.getFailIds().add(image.getId());
                result.getFailFileNames().add(image.getFilename());
            } else {
                result.getSuccessIds().add(image.getId());
                result.getSuccessFileNames().add(image.getFilename());
            }

            // 更新用户空间量
            userStorageMap.merge(image.getUserId(), image.getFileSize(), Long::sum);
        });

        // 插入到死信队列
        int countDeadLetter = imageDeleteDeadLetterMapper.insertBatch(imageDeleteDeadLetterEntities);
        if (countDeadLetter < imageDeleteDeadLetterEntities.size()) {
            log.error("插入死信队列失败，count: {}, size: {}", countDeadLetter, imageDeleteDeadLetterEntities.size());
            throw new BaseException(ImageConstant.FAILED_TO_INSERT_IMAGE_DELETE_DEAD_LETTER);
        }

        // 删除数据库记录
        int deleteCount = imageMapper.deleteByIds(result.getSuccessIds());  // 仅删除成功列表中的数据行
        if (deleteCount < idSet.size()) {
            throw new BaseException("图片删除失败");
        }

        // 更新用户空间量
        userStorageMap.forEach((userId, storageSize) -> {
            UserQuoteStorageDO userStorageInfo = userMapper.selectQuoteStorageById(userId);
            UserEntity user = new UserEntity();
            user.setId(userId);
            user.setUsedStorageBytes(userStorageInfo.getUsedStorageBytes() - storageSize);
            int countUser = userMapper.updateById(user);
            if (countUser <= 0) {
                log.error("更新用户空间量失败，userId: {}", userId);
                throw new BaseException(UserConstant.UPDATE_USER_STORAGE_FAILED);
            }
        });

        return result;
    }

    /**
     * 获取图片列表。
     */
    @Override
    public PageResult listImages(ImageQueryDTO dto) {
        if (dto == null) {
            dto = new ImageQueryDTO();
        }

        // 分页参数处理
        Integer pageNumParam = dto.getPageNum();
        Integer pageSizeParam = dto.getPageSize();
        int pageNum = pageNumParam == null || pageNumParam <= 0 ? PageConstant.DEFAULT_PAGE : pageNumParam;
        int pageSize = pageSizeParam == null || pageSizeParam <= 0 ? PageConstant.DEFAULT_PAGE_SIZE : pageSizeParam;

        PageHelper.startPage(pageNum, pageSize);

        ImageEntity query = new ImageEntity();
        BeanUtils.copyProperties(dto, query);

        List<ImageVO> records = imageMapper.listByCondition(query);
        PageInfo<ImageVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 查询图片关联的笔记列表。
     */
    @Override
    public List<NoteSimpleVO> listNotesByImageId(Long imageId) {
        validateImageId(imageId);

        ArrayList<NoteSimpleVO> notes = noteMapper.selectNoteSimpleByImageId(imageId);
        // TODO 后续可以加入是否筛选 非删除/公开 的数据

        return notes;
    }

    /**
     * 公开/取消公开图片。
     */
    @Override
    public void setImagePublic(Long imageId, Short isPublic) {
        Long userId = BaseContext.getCurrentId();
        validateImageId(imageId);

        ImageEntity image = imageMapper.selectById(imageId);
        if (image == null || !image.getUserId().equals(userId)) {
            throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        }

        image.setIsPublic(isPublic);
        int updateCount = imageMapper.updateImage(image);
        if (updateCount <= 0) {
            throw new BaseException("图片公开状态更新失败");
        }
    }

    /**
     * 管理员审核图片。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditReviewImage(ImageAuditReviewDTO dto) {
        Long reviewerId = BaseContext.getCurrentId();
        validateImageId(dto.getAuditId());

        // 查询审核记录
        ImageAuditRecordEntity auditRecord = imageAuditMapper.selectById(dto.getAuditId());
        if (auditRecord == null || auditRecord.getStatus() != ImageConstant.AUDIT_STATUS_PENDING) {
            throw new BaseException(ImageConstant.IMAGE_AUDIT_ALREADY_PROCESSED);
        }

        if (dto.getApproved()) {
            // 批准
            auditRecord.setStatus(ImageConstant.AUDIT_STATUS_APPROVED);
            auditRecord.setReviewerUserId(reviewerId);
            auditRecord.setReviewTime(LocalDateTime.now());

            // 更新图片的审核状态
            ImageEntity image = imageMapper.selectById(auditRecord.getImageId());
            if (image != null) {
                image.setIsPass(ImageConstant.AUDIT_STATUS_APPROVED);
                imageMapper.updateImage(image);
            }
        } else {
            // 拒绝
            if (dto.getRejectReason() == null || dto.getRejectReason().isEmpty()) {
                throw new BaseException(ImageConstant.IMAGE_REJECT_REASON_NOT_EMPTY);
            }
            auditRecord.setStatus(ImageConstant.AUDIT_STATUS_REJECTED);
            auditRecord.setReviewerUserId(reviewerId);
            auditRecord.setRejectReason(dto.getRejectReason());
            auditRecord.setReviewTime(LocalDateTime.now());

            // 更新图片的审核状态
            ImageEntity image = imageMapper.selectById(auditRecord.getImageId());
            if (image != null) {
                image.setIsPass(ImageConstant.AUDIT_STATUS_REJECTED);
                imageMapper.updateImage(image);
            }
        }

        imageAuditMapper.updateAuditRecord(auditRecord);
    }


    // ============ 私有方法 ============
    /**
     * 删除对象文件（当前仅支持阿里云 OSS）。
     */
    private boolean deleteFile(ImageEntity image) {
        if (image.getStorageType() != null
            && image.getStorageType() == ImageConstant.STORAGE_TYPE_ALIYUN_OSS) {
            return deleteFromAliyunOss(image);
        }
        throw new BaseException(ImageConstant.IMAGE_STORAGE_PROVIDER_NOT_SUPPORTED);
    }

    /**
     * 上传文件到阿里云 OSS（默认存储策略）。
     */
    private String uploadToAliyunOss(MultipartFile file, Long userId, String filename) {
        try {
            String objectKey = buildObjectKey(userId, filename);
            return uploadToAliyunOss(file, objectKey);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("上传文件到阿里云 OSS 失败，userId: {}, filename: {}", userId, filename, e);
            throw new BaseException(ImageConstant.IMAGE_TRANSFER_FAILED);
        }
    }

    /**
     * 上传文件到阿里云 OSS（指定 object key，可用于覆盖原对象）。
     */
    private String uploadToAliyunOss(MultipartFile file, String objectKey) {
        try {
            byte[] fileBytes = file.getBytes();
            String ossUrl = aliyunOSSOperator.uploadByObjectName(fileBytes, objectKey);
            log.info("上传到阿里云 OSS 成功，objectKey: {}, ossUrl: {}", objectKey, ossUrl);
            return ossUrl;
        } catch (Exception e) {
            log.error("上传到阿里云 OSS 失败，objectKey: {}", objectKey, e);
            throw new BaseException(ImageConstant.IMAGE_TRANSFER_FAILED);
        }
    }

    /**
     * 从阿里云 OSS 删除对象。
     */
    private boolean deleteFromAliyunOss(ImageEntity image) {
        try {
            String objectKey = extractObjectKeyFromUrl(image.getOssUrl());  // 从阿里云 OSS URL 中提取 object key
            if (objectKey == null) return false;
            boolean result = aliyunOSSOperator.delete(objectKey);
            log.info("从阿里云 OSS 删除对象：{}, 结果: {}", objectKey, result);
            return result;
        } catch (Exception e) {
            log.error("删除阿里云 OSS 对象失败，imageId: {}", image.getId(), e);
            // 删除流程中记录日志即可，避免影响主事务
        }

        return false;   // not achievable
    }

    /**
     * 构造规范 object key：image/{userId}/{uuid}
     * 这里会采用 uuid 的随机算法来防止文件名冲突的覆盖
     */
    private String buildObjectKey(Long userId, String filename) {
        UUID uuid = UUID.randomUUID();
        return ImageConstant.IMAGE_OSS_DIRECTORY_PREFIX + "/"
                + userId + "/"
                + uuid + filename.substring(filename.lastIndexOf('.'));
    }

    /**
     * 从完整 URL 中提取 object key。
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

    /**
     * 校验文件名。
     * 如果文件名为空则抛出异常
     */
    private void validateFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new BaseException(ImageConstant.IMAGE_EMPTY_FILENAME);
        }
    }

    /**
     * 校验图片 ID。
     * 只是单纯校验图片 ID 是否合法，不进行图片是否存在的校验
     */
    private void validateImageId(Long id) {
        if (id == null || id <= 0) {
            throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        }
    }

    /**
     * 校验主题 ID 是否合法
     * 会先校验主题 ID 的内容是否合法
     * 然后就回到数据库校验主题是否存在
     * @param topicId
     */
    private void validateTopic(Long topicId) {
        // 校验 topicId 是否合法
        if (topicId != null && topicId > 0) {
            int count = topicMapper.countById(topicId);
            if (count <= 0) {
                throw new BaseException(TopicConstant.TOPIC_NOT_FOUND);
            }
        }
    }
}
