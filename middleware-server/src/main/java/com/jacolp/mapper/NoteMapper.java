package com.jacolp.mapper;

import com.jacolp.pojo.vo.NoteSimpleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.ArrayList;

@Mapper
public interface NoteMapper {
    @Select("select sum(file_size) from biz_note where user_id = ${userId}")
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
