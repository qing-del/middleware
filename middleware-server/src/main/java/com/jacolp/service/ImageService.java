package com.jacolp.service;

import java.util.List;

import com.jacolp.context.PermissionContext;
import org.springframework.web.multipart.MultipartFile;

import com.jacolp.pojo.dto.image.ImageAuditReviewDTO;
import com.jacolp.pojo.entity.ImageEntity;
import com.jacolp.pojo.dto.image.ImageModifyInfoDTO;
import com.jacolp.pojo.dto.image.ImageQueryDTO;
import com.jacolp.pojo.dto.image.UserImageQueryDTO;
import com.jacolp.pojo.vo.image.ImageBatchDeleteVO;
import com.jacolp.pojo.vo.image.ImageOverviewVO;
import com.jacolp.pojo.vo.image.ImageVO;
import com.jacolp.pojo.vo.image.UserImageDetailVO;
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
     * <p>- 会使用 {@link PermissionContext} 检查是否需要校验所属权（管理员跳过校验）</p>
     * @param  dto 修改信息
     */
    void modifyImageInfo(ImageModifyInfoDTO dto);

    /**
     * 云厂商迁移入口（默认阿里云 OSS，预留 Cloudflare R2）。
     */
    void transferToCloud(List<Long> ids);

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
     * 公开/取消公开图片 -- (通用)
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

    /**
     * 获取当前用户图片统计。
     */
    ImageOverviewVO getUserImageOverview();

    /**
     * 根据ID查询图片，供其他Service内部调用。
     */
    ImageEntity getById(Long id);

    /**
     * 根据ID列表批量查询图片，供其他Service内部调用。
     */
    List<ImageEntity> getByIds(List<Long> ids);

    /**
     * 根据用户ID、主题ID、文件名列表批量查询图片，供其他Service内部调用。
     */
    List<ImageEntity> getByUserIdAndTopicIdAndFilenames(Long userId, Long topicId, List<String> filenames);

    // ===== 用户端方法 =====

    UserImageDetailVO getUserImageDetail(Long id);

    void deleteImage(Long id);

    // ===== 交给“包工头”使用的方法 =====

    int updatePassStatusByIds(List<Long> ids, Short isPass);
}
