package com.jacolp.mapper;

import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.jacolp.pojo.domain.ImageNoteCountDO;
import com.jacolp.pojo.entity.ImageEntity;
import com.jacolp.pojo.vo.image.ImageVO;

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

    int updatePassByIds(@Param("ids") List<Long> ids,
                        @Param("isPass") Short isPass);

    /**
     * 查询用户存储图片的总大小
     * @param userId 用户 ID
     * @return 如果查到了就返回对应的sum值，查不到就会返回 0
     */
    @Select("SELECT IFNULL(SUM(file_size), 0) FROM biz_image WHERE user_id = #{userId}")
    Long sumImageFileSizeByUserId(@Param("userId") Long userId);

    /**
     * 按图片 ID 批量查询图片。
     */
    List<ImageEntity> selectByIds(ArrayList<Long> ids);

    /**
     * 按用户和文件名查询图片。
     */
    @Select("SELECT id, user_id, topic_id, filename, oss_url, storage_type, file_size, is_public, is_pass, upload_time FROM biz_image WHERE user_id = #{userId} AND filename = #{filename} LIMIT 1")
    ImageEntity selectByUserIdAndFilename(@Param("userId") Long userId, @Param("filename") String filename);

    /**
     * 批量查询指定用户、指定话题下的图片（命中联合索引）
     * <p>索引：(user_id, topic_id, filename(40))</p>
     * @param userId  用户 ID
     * @param topicId 话题 ID
     * @param filenames 文件名列表
     * @return 匹配到的图片列表
     */
    List<ImageEntity> selectByUserIdAndTopicIdAndFilenames(@Param("userId") Long userId,
                                                           @Param("topicId") Long topicId,
                                                           @Param("filenames") List<String> filenames);

    /**
     * 用户端条件查询：当前用户自己的图片 + 别人已公开的图片。
     */
    List<ImageVO> listByUserCondition(@Param("userId") Long userId,
                                      @Param("topicId") Long topicId,
                                      @Param("filename") String filename);
}
