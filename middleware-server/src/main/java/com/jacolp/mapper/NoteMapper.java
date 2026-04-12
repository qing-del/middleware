package com.jacolp.mapper;

import com.jacolp.pojo.vo.NoteSimpleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.ArrayList;

@Mapper
public interface NoteMapper {
    /**
     * 查询用户下所有笔记的存储空间
     * @param userId
     * @return 如果查到了就返回对应的sum值，查不到就会返回 0
     */
    @Select("select ifnull(sum(md_file_size), 0) as file_size from biz_note where user_id = #{userId} and is_deleted = 0")
    Long sumNoteFileSizeByUserId(Long userId);



    @Select("select" +
            "            note_id as id," +
            "            note_title as title," +
            "            is_cross_user," +
            "            is_deleted," +
            "            create_time" +
            " from biz_note_image_mapping" +
            " where image_id = ${imageId}")
    ArrayList<NoteSimpleVO> selectNoteSimpleByImageId(Long imageId);
}
