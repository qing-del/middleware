package com.jacolp.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.constant.AuditConstant;
import com.jacolp.constant.NoteConstant;
import com.jacolp.constant.TopicConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.*;
import com.jacolp.pojo.domain.TagNoteCountDO;
import com.jacolp.pojo.dto.note.UserNoteDetailDTO;
import com.jacolp.pojo.dto.note.UserNoteSearchDTO;
import com.jacolp.pojo.dto.note.UserNoteUpdateDTO;
import com.jacolp.pojo.entity.*;
import com.jacolp.pojo.vo.note.NoteVO;
import com.jacolp.pojo.vo.note.UserNoteDetailVO;
import com.jacolp.result.PageResult;
import com.jacolp.service.UserNoteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户端笔记服务实现
 */
@Service
@Slf4j
public class UserNoteServiceImpl implements UserNoteService {

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private NoteContextMapper noteContextMapper;

    @Autowired
    private NoteConvertMapper noteConvertMapper;

    @Autowired
    private NoteTagMappingMapper noteTagMappingMapper;

    @Autowired
    private NoteImageMappingMapper noteImageMappingMapper;

    @Autowired
    private NoteEachMappingMapper noteEachMappingMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private TopicMapper topicMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNote(MultipartFile file, Long topicId) {
        Long userId = BaseContext.getCurrentId();

        // 校验文件名和后缀
        String originalFilename = normalizeFilename(file.getOriginalFilename());
        if (!originalFilename.toLowerCase().endsWith(NoteConstant.ALLOWED_NOTE_FORMAT)) {
            throw new BaseException(NoteConstant.NOTE_INVALID_FORMAT);
        }

        // 校验主题
        validateTopic(topicId);

        // 校验文件大小是否在用户剩余配额内
        com.jacolp.pojo.domain.UserQuoteStorageDO storageInfo =
            userMapper.selectQuoteStorageById(userId);
        if (storageInfo != null && storageInfo.getMaxStorageBytes() != null) {
            Long maxStorageBytes = storageInfo.getMaxStorageBytes();
            Long usedStorageBytes = storageInfo.getUsedStorageBytes();
            if (usedStorageBytes != null && maxStorageBytes < usedStorageBytes + file.getSize()) {
                throw new BaseException("存储配额不足");
            }
        }

        // 读取文件内容
        String markdownContent;
        try {
            markdownContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BaseException(NoteConstant.NOTE_FILE_READ_ERROR);
        }

        // 检查是否已存在同名笔记
        String title = stripMarkdownExtension(originalFilename);
        if (noteMapper.countByUserIdAndTopicIdAndTitle(userId, topicId, title) != 0) {
            throw new BaseException("已在对应主题下存在同名笔记");
        }

        // 创建笔记
        NoteEntity note = new NoteEntity();
        note.setUserId(userId);
        note.setTopicId(topicId);
        note.setTitle(title);
        note.setDescription(null);
        note.setIsPublished(NoteConstant.IS_PUBLISHED_NO);
        note.setStorageType(NoteConstant.DEFAULT_STORAGE_TYPE);
        note.setIsMissingInfo(NoteConstant.MISSED_INFO);
        note.setIsPass(AuditConstant.WAIT);
        note.setIsDeleted(NoteConstant.NOT_DELETED);
        note.setMdFileSize(file.getSize());
        note.setCreateTime(LocalDateTime.now());
        note.setUpdateTime(LocalDateTime.now());

        int count = noteMapper.insertNote(note);
        if (count <= 0) {
            throw new BaseException("创建笔记失败");
        }

        // 保存笔记内容
        NoteContextEntity context = new NoteContextEntity();
        context.setNoteId(note.getId());
        context.setMarkdownContent(markdownContent);
        noteContextMapper.insertContext(context);

        // 更新用户已用存储空间
        if (storageInfo != null && storageInfo.getUsedStorageBytes() != null) {
            UserEntity user = new UserEntity();
            user.setId(userId);
            user.setUsedStorageBytes(storageInfo.getUsedStorageBytes() + file.getSize());
            userMapper.updateById(user);
        }

        return note.getId();
    }

    @Override
    public PageResult listNotes(UserNoteSearchDTO dto) {
        Long userId = BaseContext.getCurrentId();

        // 分页参数处理
        int pageNum = dto.getPageNum() == null || dto.getPageNum() <= 0 ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() <= 0 ? 10 : dto.getPageSize();
        PageHelper.startPage(pageNum, pageSize);

        // 查询当前用户的笔记
        List<NoteVO> records = noteMapper.listByUserCondition(userId, dto.getTopicId(), dto.getKeyword());
        PageInfo<NoteVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    public UserNoteDetailVO getNoteDetail(UserNoteDetailDTO dto) {
        Long userId = BaseContext.getCurrentId();

        // 查询笔记
        NoteEntity note = noteMapper.selectById(dto.getId());
        if (note == null || note.getIsDeleted() == NoteConstant.DELETED) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        // 校验笔记归属
        if (!note.getUserId().equals(userId)) {
            throw new BaseException("只能查看自己的笔记");
        }

        // 查询笔记内容
        NoteContextEntity context = noteContextMapper.selectByNoteId(dto.getId());
        if (context == null) {
            throw new BaseException(NoteConstant.NOTE_CONTENT_NOT_FOUND);
        }

        // 查询转换结果
        NoteConvertedEntity converted = noteConvertMapper.selectByNoteId(dto.getId());

        // 查询标签
        List<Long> tagIds = noteTagMappingMapper.selectTagIdsByNoteId(dto.getId());
        List<String> tags = List.of();
        if (tagIds != null && !tagIds.isEmpty()) {
            List<TagEntity> tagEntities = tagMapper.selectByIds(tagIds);
            tags = tagEntities.stream().map(TagEntity::getTagName).collect(Collectors.toList());
        }

        // 组装返回结果
        UserNoteDetailVO vo = new UserNoteDetailVO();
        vo.setId(note.getId());
        vo.setTitle(note.getTitle());
        vo.setDescription(note.getDescription());
        vo.setMarkdownContent(context.getMarkdownContent());
        vo.setHtmlContent(converted != null ? converted.getBodyHtml() : null);
        vo.setTags(tags);
        vo.setCreateTime(note.getCreateTime());
        vo.setUpdateTime(note.getUpdateTime());
        vo.setIsPublished(note.getIsPublished());

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNote(MultipartFile file, UserNoteUpdateDTO dto) {
        Long userId = BaseContext.getCurrentId();

        // 查询笔记
        NoteEntity note = noteMapper.selectById(dto.getId());
        if (note == null || note.getIsDeleted() == NoteConstant.DELETED) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        // 校验笔记归属
        if (!note.getUserId().equals(userId)) {
            throw new BaseException("只能修改自己的笔记");
        }

        // 如果有文件上传，更新内容
        if (file != null && !file.isEmpty()) {
            // 校验文件名和后缀
            String originalFilename = normalizeFilename(file.getOriginalFilename());
            if (!originalFilename.toLowerCase().endsWith(NoteConstant.ALLOWED_NOTE_FORMAT)) {
                throw new BaseException(NoteConstant.NOTE_INVALID_FORMAT);
            }

            // 校验文件大小变化
            com.jacolp.pojo.domain.UserQuoteStorageDO storageInfo =
                userMapper.selectQuoteStorageById(userId);
            if (storageInfo != null && storageInfo.getMaxStorageBytes() != null) {
                Long maxStorageBytes = storageInfo.getMaxStorageBytes();
                Long usedStorageBytes = storageInfo.getUsedStorageBytes();
                long sizeDiff = file.getSize() - note.getMdFileSize();
                if (usedStorageBytes != null && maxStorageBytes < usedStorageBytes + sizeDiff) {
                    throw new BaseException("存储配额不足");
                }
            }

            // 读取文件内容
            String markdownContent;
            try {
                markdownContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new BaseException(NoteConstant.NOTE_FILE_READ_ERROR);
            }

            // 更新笔记内容
            NoteContextEntity context = noteContextMapper.selectByNoteId(dto.getId());
            if (context == null) {
                throw new BaseException(NoteConstant.NOTE_CONTENT_NOT_FOUND);
            }
            context.setMarkdownContent(markdownContent);
            noteContextMapper.updateContext(context);

            // 更新笔记大小
            note.setMdFileSize(file.getSize());

            // 更新转换缓存
            noteConvertMapper.deleteByNoteId(dto.getId());
        }

        // 更新其他字段
        if (StringUtils.hasText(dto.getTitle())) {
            note.setTitle(dto.getTitle());
        }
        if (dto.getDescription() != null) {
            note.setDescription(dto.getDescription());
        }
        if (dto.getTopicId() != null) {
            validateTopic(dto.getTopicId());
            note.setTopicId(dto.getTopicId());
        }
        note.setUpdateTime(LocalDateTime.now());

        int count = noteMapper.updateNote(note);
        if (count <= 0) {
            throw new BaseException("更新笔记失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNote(Long id) {
        Long userId = BaseContext.getCurrentId();

        // 查询笔记
        NoteEntity note = noteMapper.selectById(id);
        if (note == null || note.getIsDeleted() == NoteConstant.DELETED) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        // 校验笔记归属
        if (!note.getUserId().equals(userId)) {
            throw new BaseException("只能删除自己的笔记");
        }

        // 检查是否有标签引用该笔记
        List<TagNoteCountDO> tagChecks = tagMapper.selectDeleteChecksByIds(userId, List.of(id));
        for (TagNoteCountDO check : tagChecks) {
            if (check.getNoteCount() != null && check.getNoteCount() > 0) {
                throw new BaseException("该笔记正在被标签引用，无法删除");
            }
        }

        // 软删除笔记
        List<Long> ids = new ArrayList<>();
        ids.add(id);
        int count = noteMapper.softDeleteByIds(ids);
        if (count <= 0) {
            throw new BaseException("删除笔记失败");
        }

        // 更新用户已用存储空间
        com.jacolp.pojo.domain.UserQuoteStorageDO storageInfo =
            userMapper.selectQuoteStorageById(userId);
        if (storageInfo != null && storageInfo.getUsedStorageBytes() != null) {
            UserEntity user = new UserEntity();
            user.setId(userId);
            user.setUsedStorageBytes(Math.max(0L,
                storageInfo.getUsedStorageBytes() - note.getMdFileSize()));
            userMapper.updateById(user);
        }
    }

    @Override
    public PageResult searchNotes(UserNoteSearchDTO dto) {
        Long userId = BaseContext.getCurrentId();

        // 校验搜索关键词
        if (!StringUtils.hasText(dto.getKeyword())) {
            throw new BaseException("搜索关键词不能为空");
        }

        // 分页参数处理
        int pageNum = dto.getPageNum() == null || dto.getPageNum() <= 0 ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() <= 0 ? 10 : dto.getPageSize();
        PageHelper.startPage(pageNum, pageSize);

        // 搜索当前用户的笔记（基于标题和内容关键词检索）
        // 注意：由于当前设计，可能需要实现全文搜索逻辑
        // 这里暂时使用模糊搜索实现
        String keyword = dto.getKeyword().trim();
        List<NoteVO> records = noteMapper.listByUserCondition(userId, dto.getTopicId(), keyword);
        PageInfo<NoteVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 校验主题
     * @param topicId 主题ID
     */
    private void validateTopic(Long topicId) {
        if (topicId != null && topicId > 0) {
            int count = topicMapper.countById(topicId);
            if (count <= 0) {
                throw new BaseException(TopicConstant.TOPIC_NOT_FOUND);
            }
        }
    }

    /**
     * 规范化文件名
     * @param originalFilename 原始文件名
     * @return 规范化后的文件名
     */
    private String normalizeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BaseException(NoteConstant.NOTE_INVALID_FORMAT);
        }
        return Paths.get(originalFilename).getFileName().toString();
    }

    /**
     * 去掉 markdown 扩展名
     * @param filename 文件名
     * @return 去掉扩展名后的文件名
     */
    private String stripMarkdownExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return filename;
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(NoteConstant.ALLOWED_NOTE_FORMAT)) {
            return filename.substring(0, filename.length() - NoteConstant.ALLOWED_NOTE_FORMAT.length());
        }
        return filename;
    }
}