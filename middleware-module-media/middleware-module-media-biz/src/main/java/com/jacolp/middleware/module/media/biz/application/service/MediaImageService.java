package com.jacolp.middleware.module.media.biz.application.service;

import com.jacolp.middleware.module.media.api.model.MediaFileSummary;
import com.jacolp.middleware.module.media.biz.application.dto.image.ImageModifyInfoDTO;
import com.jacolp.middleware.module.media.biz.application.dto.image.ImageQueryDTO;
import com.jacolp.middleware.module.media.biz.application.dto.image.UserImageQueryDTO;
import com.jacolp.middleware.module.media.biz.application.vo.image.ImageBatchDeleteVO;
import com.jacolp.middleware.module.media.biz.application.vo.image.ImageOverviewVO;
import com.jacolp.middleware.module.media.biz.application.vo.image.ImageVO;
import com.jacolp.middleware.module.media.biz.application.vo.image.UserImageDetailVO;
import com.jacolp.middleware.module.media.biz.application.vo.image.ImageNoteSimpleVO;
import com.jacolp.result.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Application boundary for image operations owned by the media module. */
public interface MediaImageService {
    ImageVO uploadImage(MultipartFile file, Long topicId);
    void modifyImageFile(Long id, MultipartFile file);
    void modifyImageInfo(ImageModifyInfoDTO dto);
    ImageBatchDeleteVO deleteImages(List<Long> ids);
    PageResult listImages(ImageQueryDTO dto);
    PageResult listUserImages(Long userId, UserImageQueryDTO dto);
    List<ImageNoteSimpleVO> listNotesByImageId(Long imageId);
    void transferToCloud(List<Long> ids);
    void setImagePublic(Long imageId, Short isPublic);
    ImageOverviewVO getUserImageOverview();
    UserImageDetailVO getUserImageDetail(Long id);
    void deleteImage(Long id);
    void submitImageAudit(Long id);
    void cancelImageAudit(Long id);
    int updateAuditStatusByIds(List<Long> ids, Short status);
    MediaFileSummary findRequired(Long id);
}
