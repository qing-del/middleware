package com.jacolp.adapter.media;

import com.jacolp.middleware.module.media.biz.application.vo.image.ImageVO;
import com.jacolp.middleware.module.media.biz.infrastructure.persistence.dataobject.ImageDO;
import com.jacolp.middleware.module.media.biz.infrastructure.persistence.mapper.ImageMapper;
import com.jacolp.pojo.entity.ImageEntity;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Temporary server-side bridge for legacy media use cases. It contains no SQL and will be
 * removed when the image application service moves to media-biz.
 */
@Component
public class LegacyImagePersistenceGateway {
    private final ImageMapper imageMapper;

    public LegacyImagePersistenceGateway(ImageMapper imageMapper) {
        this.imageMapper = imageMapper;
    }

    public ImageEntity selectById(Long id) { return toEntity(imageMapper.selectById(id)); }
    public int countByUserIdTopicIdAndFilename(Long userId, Long topicId, String filename) {
        return imageMapper.countByUserIdTopicIdAndFilename(userId, topicId, filename);
    }
    public int insertImage(ImageEntity image) { return imageMapper.insertImage(toDO(image)); }
    public int updateImage(ImageEntity image) { return imageMapper.updateImage(toDO(image)); }
    public List<ImageVO> listByCondition(ImageEntity image) { return imageMapper.listByCondition(toDO(image)); }
    public int deleteByIds(List<Long> ids) { return imageMapper.deleteByIds(ids); }
    public int updateAuditStatusByIds(List<Long> ids, Short auditStatus) { return imageMapper.updateAuditStatusByIds(ids, auditStatus); }
    public Long sumImageFileSizeByUserId(Long userId) { return imageMapper.sumImageFileSizeByUserId(userId); }
    public List<ImageEntity> selectByIds(List<Long> ids) { return imageMapper.selectByIds(ids).stream().map(LegacyImagePersistenceGateway::toEntity).toList(); }
    public ImageEntity selectByUserIdAndFilename(Long userId, String filename) { return toEntity(imageMapper.selectByUserIdAndFilename(userId, filename)); }
    public List<ImageEntity> selectByUserIdAndTopicIdAndFilenames(Long userId, Long topicId, List<String> filenames) {
        return imageMapper.selectByUserIdAndTopicIdAndFilenames(userId, topicId, filenames).stream().map(LegacyImagePersistenceGateway::toEntity).toList();
    }
    public List<ImageVO> listByUserCondition(Long userId, Long topicId, String filename, boolean globalScope) {
        return imageMapper.listByUserCondition(userId, topicId, filename, globalScope);
    }
    public long countByUserId(Long userId) { return imageMapper.countByUserId(userId); }
    public long countPassedByUserId(Long userId) { return imageMapper.countPassedByUserId(userId); }

    private static ImageDO toDO(ImageEntity source) {
        if (source == null) return null;
        ImageDO target = new ImageDO();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private static ImageEntity toEntity(ImageDO source) {
        if (source == null) return null;
        ImageEntity target = new ImageEntity();
        BeanUtils.copyProperties(source, target);
        return target;
    }
}
