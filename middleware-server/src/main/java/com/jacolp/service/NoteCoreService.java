package com.jacolp.service;

import com.jacolp.pojo.dto.note.NoteModifyInfoDTO;
import com.jacolp.pojo.dto.note.NoteQueryDTO;
import com.jacolp.pojo.dto.note.UserNoteQueryDTO;
import com.jacolp.pojo.entity.NoteEntity;
import com.jacolp.pojo.vo.note.NoteStatsVO;
import com.jacolp.result.PageResult;

import java.util.List;

public interface NoteCoreService {

    /**
     * 更新笔记
     * @param noteEntity 笔记
     */
    void update(NoteEntity noteEntity);

    /**
     * 获取笔记列表
     * @param dto 查询条件
     */
    PageResult listNotes(NoteQueryDTO dto);

    /**
     * 获取当前用户笔记统计。
     */
    // TODO 直接搬迁 getUserNoteStats 即可
    NoteStatsVO getUserNoteStats();

    /**
     * 用户端条件查询：当前用户自己的笔记 + 别人已发布的笔记。
     */
    PageResult listUserNotes(UserNoteQueryDTO dto);

    /**
     * 获取笔记 -- (用户专属)
     * <p>会校验笔记是否属于当前请求者</p>
     * @param id 笔记 ID
     * @return 笔记
     */
    NoteEntity getById(Long id);

    /**
     * 获取笔记 -- (管理员专属)
     * <p>不做笔记归属权的校验</p>
     * @param id 笔记 ID
     * @return 笔记
     */
    NoteEntity adminGetById(Long id);

    /**
     * 批量获取笔记
     * @param ids 笔记 ID
     * @return 笔记列表
     */
    List<NoteEntity> getByIds(List<Long> ids);

    /**
     * 检查是否存在笔记
     * @param userId 用户 ID
     * @param topicId 主题 ID
     * @param title 笔记标题
     * @return 存在返回 true
     */
    boolean countByUserIdAndTopicIdAndTitle(Long userId, Long topicId, String title);


    /**
     * 获取笔记
     * @param userId 用户 ID
     * @param topicId 主题 ID
     * @param title 笔记标题
     * @return 存在就返回笔记
     * @throws com.jacolp.exception.BaseException 不存在
     */
    NoteEntity getByUserIdAndTopicIdAndTitle(Long userId, Long topicId, String title);

    /**
     * 保存笔记
     * TODO 将 insertBaseNote 这个方法关联的方法都塞到 Impl 类中(实现之后删掉这行注释)(*)
     * @param noteEntity 笔记
     */
    void saveInitNote(NoteEntity noteEntity);

    /**
     * 删除笔记转换结果 -- (管理员)
     * <p>不做笔记归属权的校验，直接删除笔记转换结果</p>
     * @param noteId 笔记 ID
     */
    void adminDeleteConverted(Long noteId);

    /**
     * 批量更新笔记状态
     * @param noteIds 笔记 ID
     * @param status 笔记状态
     */
    void updateStatusByIds(List<Long> noteIds, Short status);

    /**
     * 修改笔记信息
     * @param dto 修改信息
     */
    void modifyInfo(NoteModifyInfoDTO dto);
}
