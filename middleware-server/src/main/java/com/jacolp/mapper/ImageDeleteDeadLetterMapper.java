package com.jacolp.mapper;

import com.jacolp.pojo.entity.ImageDeleteDeadLetterEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ImageDeleteDeadLetterMapper {
    /**
     * 批量插入
     * 必须要保证有 imageUrl 字段
     * @param list 删除图片死信实体类
     * @return 插入数量
     */
    int insertBatch(List<ImageDeleteDeadLetterEntity> list);

    /**
     * 批量更新
     * @param ids 需要批量更新的id
     * @return 更新数量
     */
    int updateBatch(List<Long> ids, short status);

    /**
     * 批量查询
     * @param status 状态
     * @return 查询结果
     */
    @Select("SELECT id, image_url FROM biz_image_delete_dead_letter WHERE status = #{status}")
    List<ImageDeleteDeadLetterEntity> selectBatch(short status);
}