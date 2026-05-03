package com.jacolp.facade;

import com.jacolp.pojo.dto.note.NoteChangeConfirmDTO;
import com.jacolp.pojo.vo.note.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface NoteFacade {
    /**
     * 上传笔记
     * @param file
     * @param topicId
     * @return
     */
    NoteUploadVO uploadNote(MultipartFile file, Long topicId);

    /**
     * 修改笔记源文件
     * @param noteId
     * @param file
     * @return
     */
    NoteDiffVO modifyNoteSource(Long noteId, MultipartFile file);

    /**
     * 确认修改笔记
     * @param noteId
     * @param dto
     * @return
     */
    NoteChangeDiffVO confirmChange(Long noteId, NoteChangeConfirmDTO dto);

    /**
     * 获取修改笔记详情
     * @param noteId
     * @return
     */
    NoteModifyDiffDetailVO getModifyDiff(Long noteId);

    /**
     * 转换笔记 -- (管理员专属)
     * @param noteId
     * @return
     */
    NoteConvertResultVO adminConvertNote(Long noteId);

    /**
     * 批量删除笔记 -- (管理员专属)
     * @param ids
     */
    void adminDeleteNotes(List<Long> ids);

    /**
     * 获取笔记关联信息
     * @param noteId
     * @return
     */
    NoteRelationDetailVO getRelationInfo(Long noteId);
}
