package com.jacolp.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.jacolp.pojo.dto.image.ImageMappingBindDTO;
import com.jacolp.pojo.dto.note.EachMappingBindDTO;
import com.jacolp.pojo.dto.note.NoteChangeConfirmDTO;
import com.jacolp.pojo.dto.note.NoteModifyInfoDTO;
import com.jacolp.pojo.dto.note.NoteQueryDTO;
import com.jacolp.pojo.dto.note.NoteVisibleDTO;
import com.jacolp.pojo.dto.note.UserNoteDetailDTO;
import com.jacolp.pojo.dto.note.UserNoteQueryDTO;
import com.jacolp.pojo.dto.note.UserNoteSearchDTO;
import com.jacolp.pojo.dto.note.UserNoteUpdateDTO;
import com.jacolp.pojo.dto.tag.TagMappingBindDTO;
import com.jacolp.pojo.entity.NoteEntity;
import com.jacolp.pojo.vo.image.ImageSimpleVO;
import com.jacolp.pojo.vo.note.NoteSimpleVO;
import com.jacolp.pojo.vo.note.NoteChangeDiffVO;
import com.jacolp.pojo.vo.note.NoteCheckBindingVO;
import com.jacolp.pojo.vo.note.NoteConvertResultVO;
import com.jacolp.pojo.vo.note.NoteDetailVO;
import com.jacolp.pojo.vo.note.NoteDiffVO;
import com.jacolp.pojo.vo.note.NoteModifyDiffDetailVO;
import com.jacolp.pojo.vo.note.NoteRelationDetailVO;
import com.jacolp.pojo.vo.note.NoteStatsVO;
import com.jacolp.pojo.vo.note.NoteUploadVO;
import com.jacolp.pojo.vo.note.UserNoteDetailVO;
import com.jacolp.result.PageResult;

public interface NoteServiceOld {

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
     * 确认修改
     * @param noteId
     * @param dto
     * @return
     */
    NoteChangeDiffVO confirmChange(Long noteId, NoteChangeConfirmDTO dto);

    /**
     * 获取修改详情
     * @param noteId
     * @return
     */
    NoteModifyDiffDetailVO getModifyDiff(Long noteId);

    /**
     * 获取笔记原内容
     * @param noteId
     * @return 返回 md 文本内容
     */
    String adminGetSource(Long noteId);

    /**
     * 转换笔记
     * @param noteId
     * @return
     */
    NoteConvertResultVO adminConvertNote(Long noteId);

    /**
     * 删除转换后的笔记
     * @param noteId
     */
    void adminDeleteConverted(Long noteId);

    /**
     * 发布笔记
     * @param noteId
     */
    void publishNote(Long noteId);

    /**
     * 删除笔记
     * @param ids
     */
    void adminDeleteNotes(List<Long> ids);

    /**
     * 列出笔记中的图片
     * @param noteId
     * @return
     */
    List<ImageSimpleVO> listImagesByNoteId(Long noteId);

    /**
     * 设置笔记可见性
     * @param isVisible
     * @param dto
     */
    void adminSetVisible(Short isVisible, NoteVisibleDTO dto);

    /**
     * 设置笔记发布状态
     * @param noteId 笔记ID
     * @param status 发布状态（1:发布, 0:下架）
     */
    void setNotePublishStatus(Long noteId, Short status);

    /**
     * 修改笔记信息
     * @param dto
     */
    void modifyInfo(NoteModifyInfoDTO dto);

    /**
     * 列出笔记
     * @param dto
     * @return
     */
    PageResult listNotes(NoteQueryDTO dto);

    /**
     * 获取笔记信息
     * @param noteId
     * @return
     */
    NoteDetailVO adminGetInfo(Long noteId);

    /**
     * 获取笔记内容
     * @param noteId
     * @return
     */
    NoteConvertResultVO adminOpenNote(Long noteId);

    NoteRelationDetailVO getRelationInfo(Long noteId);

    void bindTagMapping(TagMappingBindDTO dto);

    void unbindTagMapping(Long mappingId);

    void bindImageMapping(ImageMappingBindDTO dto);

    void unbindImageMapping(Long mappingId);

    void bindEachMapping(EachMappingBindDTO dto);

    void unbindEachMapping(Long mappingId);

    NoteCheckBindingVO checkRelationCompletion(Long noteId);

    /**
     * 用户端条件查询：当前用户自己的笔记 + 别人已发布的笔记。
     */
    PageResult listUserNotes(UserNoteQueryDTO dto);

    /**
     * 用户端发起笔记审核申请。
     */
    void submitNoteAudit(Long noteId);

    /**
     * 获取当前用户笔记统计。
     */
    NoteStatsVO getUserNoteStats();

    // ===== 用户端方法 =====

    Long createUserNote(MultipartFile file, Long topicId);

    PageResult listUserNotesBySearch(UserNoteSearchDTO dto);

    UserNoteDetailVO getUserNoteDetail(UserNoteDetailDTO dto);

    String getUserNoteSource(Long noteId);

    NoteConvertResultVO getUserNoteConvertedHtml(Long noteId);

    void updateUserNote(MultipartFile file, UserNoteUpdateDTO dto);

    void deleteUserNote(Long id);

    PageResult searchUserNotes(UserNoteSearchDTO dto);

    NoteEntity getNoteEntityById(Long id);

    List<NoteSimpleVO> listNoteSimplesByImageId(Long imageId);

    int updateStatusByIds(List<Long> ids, Short status);
}