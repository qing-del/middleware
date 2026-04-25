package com.jacolp.service;

import java.util.List;

import com.jacolp.pojo.dto.image.UserImageQueryDTO;
import com.jacolp.pojo.vo.image.ImageBatchDeleteVO;
import org.springframework.web.multipart.MultipartFile;

import com.jacolp.pojo.dto.image.ImageAuditReviewDTO;
import com.jacolp.pojo.dto.image.ImageModifyInfoDTO;
import com.jacolp.pojo.dto.image.ImageQueryDTO;
import com.jacolp.pojo.vo.image.ImageVO;
import com.jacolp.pojo.vo.note.NoteSimpleVO;
import com.jacolp.result.PageResult;

/**
 * 图片服务接口。
 */
public interface ImageService {

    /**
     * 上传图片。
     */
    ImageVO uploadImage(MultipartFile file, Long topicId);

    /**
     * 修改图片源文件。
     */
    void modifyImageFile(Long id, MultipartFile newFile);

    /**
     * 修改图片信息（改名/换主题）。
     */
    void modifyImageInfo(ImageModifyInfoDTO dto);

    /**
     * 云厂商迁移入口（默认阿里云 OSS，预留 Cloudflare R2）。
     */
    void transferToCloud(List<Long> ids);

    /**
     * 已废弃：不再支持迁移到本地存储。
     */
    void transferToLocal(List<Long> ids);

    /**
     * 删除图片（批量）。
     *
     * @return
     */
    ImageBatchDeleteVO deleteImages(List<Long> ids);

    /**
     * 获取图片列表。
     */
    PageResult listImages(ImageQueryDTO dto);

    /**
     * 查询图片关联的笔记列表。
     */
    List<NoteSimpleVO> listNotesByImageId(Long imageId);

    /**
     * 公开/取消公开图片。
     */
    void setImagePublic(Long imageId, Short isPublic);

    /**
     * 管理员审核图片。
     */
    void auditReviewImage(ImageAuditReviewDTO dto);

    /**
     * 用户端条件查询：当前用户自己的图片 + 别人已公开的图片。
     */
    PageResult listUserImages(Long userId, UserImageQueryDTO dto);

    /**
     * 用户端发起图片审核申请。
     */
    void submitImageAudit(Long imageId);
}
