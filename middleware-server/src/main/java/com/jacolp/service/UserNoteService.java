package com.jacolp.service;

import com.jacolp.pojo.dto.note.UserNoteDetailDTO;
import com.jacolp.pojo.dto.note.UserNoteUpdateDTO;
import com.jacolp.pojo.dto.note.UserNoteSearchDTO;
import com.jacolp.pojo.vo.note.UserNoteDetailVO;
import com.jacolp.result.PageResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户端笔记服务接口
 */
public interface UserNoteService {

    /**
     * 创建笔记
     * @param file .md 文件
     * @param topicId 主题ID
     * @return 创建的笔记ID
     */
    Long createNote(MultipartFile file, Long topicId);

    /**
     * 查询笔记列表
     * @param dto 查询条件
     * @return 笔记列表
     */
    PageResult listNotes(UserNoteSearchDTO dto);

    /**
     * 查看笔记详情
     * @param dto 查询条件
     * @return 笔记详情
     */
    UserNoteDetailVO getNoteDetail(UserNoteDetailDTO dto);

    /**
     * 更新笔记内容
     * @param file .md 文件
     * @param dto 更新条件
     */
    void updateNote(MultipartFile file, UserNoteUpdateDTO dto);

    /**
     * 删除笔记
     * @param id 笔记ID
     */
    void deleteNote(Long id);

    /**
     * 全文搜索
     * @param dto 搜索条件
     * @return 搜索结果
     */
    PageResult searchNotes(UserNoteSearchDTO dto);
}