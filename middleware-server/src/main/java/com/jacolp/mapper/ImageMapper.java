package com.jacolp.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.jacolp.pojo.domain.ImageNoteCountDO;
import com.jacolp.pojo.entity.ImageEntity;
import com.jacolp.pojo.vo.ImageVO;

/**
 * 图片数据访问层。
 */
@Mapper
public interface ImageMapper {

    /**
     * 按图片 ID 查询。
     */
    @Select("SELECT id, user_id, topic_id, filename, oss_url, storage_type, file_size, is_public, is_pass, upload_time FROM biz_image WHERE id = #{id}")
    ImageEntity selectById(@Param("id") Long id);

    /**
     * 查询同用户同主题下是否存在同名图片。
     */
    @Select("SELECT COUNT(*) FROM biz_image WHERE user_id = #{userId} AND topic_id = IFNULL(#{topicId}, 0) AND filename = #{filename}")
    int countByUserIdTopicIdAndFilename(@Param("userId") Long userId, @Param("topicId") Long topicId, @Param("filename") String filename);

    /**
     * 新增图片。
     */
    int insertImage(ImageEntity image);

    /**
     * 修改图片信息。
     */
    int updateImage(ImageEntity image);

    /**
     * 条件分页查询图片列表。
     */
    List<ImageVO> listByCondition(ImageEntity query);

    /**
     * 删除前校验：查询目标图片及其被引用次数。
     */
    List<ImageNoteCountDO> selectDeleteChecksByIds(@Param("ids") List<Long> ids);

    /**
     * 批量删除图片。
     */
    int deleteByIds(@Param("ids") List<Long> ids);

    /**
     * 按用户查询所有已有的标签名。
     */
    @Select("SELECT COUNT(*) FROM biz_image WHERE user_id = #{userId}")
    Long sumImageFileSizeByUserId(@Param("userId") Long userId);
}
