package com.jacolp.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.constant.*;
import com.jacolp.context.BaseContext;
import com.jacolp.context.NoteImageResolveContext;
import com.jacolp.converter.MarkdownHtmlEngine;
import com.jacolp.converter.MarkdownHtmlEngine.FrontMatter;
import com.jacolp.converter.MarkdownHtmlEngine.HtmlProcessResult;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.ImageMapper;
import com.jacolp.mapper.NoteChangeDiffMapper;
import com.jacolp.mapper.NoteContextMapper;
import com.jacolp.mapper.NoteConvertMapper;
import com.jacolp.mapper.NoteEachMappingMapper;
import com.jacolp.mapper.NoteImageMappingMapper;
import com.jacolp.mapper.NoteMapper;
import com.jacolp.mapper.NoteTagMappingMapper;
import com.jacolp.mapper.TagMapper;
import com.jacolp.mapper.TopicMapper;
import com.jacolp.mapper.UserMapper;
import com.jacolp.pojo.dto.EachMappingBindDTO;
import com.jacolp.pojo.dto.ImageMappingBindDTO;
import com.jacolp.pojo.dto.NoteChangeConfirmDTO;
import com.jacolp.pojo.dto.NoteModifyInfoDTO;
import com.jacolp.pojo.dto.NoteQueryDTO;
import com.jacolp.pojo.dto.NoteVisibleDTO;
import com.jacolp.pojo.dto.TagMappingBindDTO;
import com.jacolp.pojo.entity.ImageEntity;
import com.jacolp.pojo.entity.NoteChangeDiffEntity;
import com.jacolp.pojo.entity.NoteContextEntity;
import com.jacolp.pojo.entity.NoteConvertedEntity;
import com.jacolp.pojo.entity.NoteEntity;
import com.jacolp.pojo.entity.NoteEachMappingEntity;
import com.jacolp.pojo.entity.NoteImageMappingEntity;
import com.jacolp.pojo.entity.NoteTagMappingEntity;
import com.jacolp.pojo.entity.TagEntity;
import com.jacolp.pojo.entity.TopicEntity;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.pojo.vo.ImageSimpleVO;
import com.jacolp.pojo.vo.NoteChangeDiffVO;
import com.jacolp.pojo.vo.NoteConvertMetaVO;
import com.jacolp.pojo.vo.NoteConvertResultVO;
import com.jacolp.pojo.vo.NoteDetailVO;
import com.jacolp.pojo.vo.NoteDiffVO;
import com.jacolp.pojo.vo.NoteEachSimpleVO;
import com.jacolp.pojo.vo.NoteEachMappingRowVO;
import com.jacolp.pojo.vo.NoteImageMappingRowVO;
import com.jacolp.pojo.vo.NoteModifyDiffDetailVO;
import com.jacolp.pojo.vo.NoteRelationDetailVO;
import com.jacolp.pojo.vo.NoteTagMappingRowVO;
import com.jacolp.pojo.vo.NoteUploadVO;
import com.jacolp.pojo.vo.NoteVO;
import com.jacolp.result.PageResult;
import com.jacolp.service.NoteService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NoteServiceImpl implements NoteService {

    // ==== 笔记模块的 Mapper ====
    @Autowired private NoteMapper noteMapper;
    @Autowired private NoteConvertMapper noteConvertMapper;
    @Autowired private NoteChangeDiffMapper noteChangeDiffMapper;
    @Autowired private NoteContextMapper noteContextMapper;
    @Autowired private NoteEachMappingMapper noteEachMappingMapper;
    @Autowired private NoteTagMappingMapper noteTagMappingMapper;
    @Autowired private NoteImageMappingMapper noteImageMappingMapper;

    // ==== 来自其他模块的 Mapper ====
    @Autowired private TagMapper tagMapper;
    @Autowired private ImageMapper imageMapper;
    @Autowired private TopicMapper topicMapper;
    @Autowired private UserMapper userMapper;

    // ==== 来自 common 模块的 Bean 对象 ====
    @Autowired private MarkdownHtmlEngine markdownHtmlEngine;
    @Autowired private ObjectMapper objectMapper;

    /**
     * 上传笔记
     * <p>同步解析笔记的关联信息：标签、图片、笔记</p>
     * @param file    文件
     * @param topicId 所属话题ID
     * @return 笔记上传结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteUploadVO uploadNote(MultipartFile file, Long topicId) {
        // 获取当前用户ID
        Long userId = BaseContext.getCurrentId();
        validateTopic(topicId);

        // 读取文件
        String originalFilename = normalizeFilename(file.getOriginalFilename());
        String rawMarkdown = readMultipartAsString(file);

        // 检查是否已经存在笔记
        if (noteMapper.countByUserIdAndTopicIdAndTitle(userId, topicId, originalFilename) != 0) {
            log.warn("User {} upload note, but already exists, filename: {}", userId, originalFilename);
            throw new BaseException("你在对应主题下已有同名的笔记，无法上传！");
        }

        // 扫描标签、图片、笔记
        MarkdownHtmlEngine.NoteReletionInfo scanResult = MarkdownHtmlEngine.scanNoteReletionInfo(rawMarkdown);
        List<String> tags = normalizeDistinctList(scanResult.tags());
        List<String> imageNames = normalizeDistinctList(scanResult.imageNames());
        List<MarkdownHtmlEngine.ParsedNoteLink> noteLinks = List.copyOf(scanResult.noteLinks());

        // 插入笔记
        Long noteId = insertBaseNote(userId, topicId, originalFilename, file.getSize(), tags, imageNames, noteLinks);

        // 将笔记内容存入数据库
        NoteContextEntity contextEntity = new NoteContextEntity();
        contextEntity.setNoteId(noteId);
        contextEntity.setMarkdownContent(rawMarkdown);
        noteContextMapper.insertContext(contextEntity);

        // 建立关联映射
        persistRelationMappings(userId, noteId, originalFilename, tags, imageNames, noteLinks);

        // 构建返回结果
        NoteUploadVO vo = new NoteUploadVO();
        vo.setNoteId(noteId);
        // 这里使用批量查询计算缺失图片，避免逐条查询导致 O(n) 次网络 IO。
        vo.setMissingImages(resolveMissingImages(userId, topicId, imageNames));
        return vo;
    }

    /**
     * 修改笔记源文件 -- (通用)
     * @param noteId 笔记ID
     * @param file   文件
     * @return 笔记 Diff
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteDiffVO modifyNoteSource(Long noteId, MultipartFile file) {
        // 获取当前用户ID
        Long userId = BaseContext.getCurrentId();
        NoteEntity existed = validateOwnedNote(noteId, userId); // 验证笔记所有权

        // 格式化文件名
        normalizeFilename(file.getOriginalFilename());

        // 从数据库读取旧内容
        NoteContextEntity oldContext = noteContextMapper.selectByNoteId(noteId);
        if (oldContext == null) {
            throw new BaseException(NoteConstant.NOTE_CONTENT_NOT_FOUND);
        }
        String oldMarkdown = oldContext.getMarkdownContent(); // 旧内容
        String newMarkdown = readMultipartAsString(file); // 新内容

        // 扫描新旧内容的标签和图片，计算 Diff
        MarkdownHtmlEngine.NoteReletionInfo oldScan = MarkdownHtmlEngine.scanNoteReletionInfo(oldMarkdown);
        MarkdownHtmlEngine.NoteReletionInfo newScan = MarkdownHtmlEngine.scanNoteReletionInfo(newMarkdown);
        NoteDiffVO diffVO = buildDiff(
                oldScan.tags(), newScan.tags(),
                oldScan.imageNames(), newScan.imageNames(),
                oldScan.noteNames(), newScan.noteNames());

        // 获取不同文件的大小
        long oldFileSize = safeLong(existed.getMdFileSize());
        long newFileSize = file.getSize();

        // 新内容临时保存到 markdown_content_new，待 confirmChange 时处理
        oldContext.setMarkdownContentNew(newMarkdown);
        noteContextMapper.updateContext(oldContext);

        // Diff 记录持久化到数据库
        NoteChangeDiffEntity diffEntity = new NoteChangeDiffEntity();
        diffEntity.setNoteId(noteId);
        diffEntity.setStatus(NoteConstant.NOTE_DIFF_STATUS_PENDING); // 待确认
        diffEntity.setDiffJson(writeJson(diffVO));
        // diff = newFileSize - oldFileSize;
        diffEntity.setOldFileSize(oldFileSize);
        diffEntity.setNewFileSize(newFileSize);
        noteChangeDiffMapper.upsertDiff(diffEntity); // 插入或更新

        return diffVO;
    }

    /**
     * 确认笔记内容变更 -- (通用)
     * <p>这里确认修改之后，并<b>不会</b>将笔记的发布状态改为<b>未发布</b></p>
     * <p>这里会将笔记的 md 内容做修改</p>
     * <p>并且将 diff 数据做好修改，仅此而已</p>
     * <p>也不会删除 笔记-转化 的记录行 -> 说明如果笔记处于发布状态，则别人依旧是可以查询到旧的转换记录行</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteChangeDiffVO confirmChange(Long noteId, NoteChangeConfirmDTO dto) {
        // 获取当前用户ID
        Long userId = BaseContext.getCurrentId();
        NoteEntity existed = validateOwnedNote(noteId, userId);

        // 查询变更记录
        NoteChangeDiffEntity diffEntity = noteChangeDiffMapper.selectByNoteId(noteId);
        if (diffEntity == null) {
            throw new BaseException(NoteConstant.NOTE_CHANGE_DIFF_NOT_FOUND);
        }

        // 校验请求参数
        if (dto == null || dto.getConfirm() == null) {
            throw new BaseException("确认参数不能为空");
        }

        // 通过数据库查询 笔记-内容 记录行
        NoteContextEntity contextEntity = noteContextMapper.selectByNoteId(noteId);
        if (contextEntity == null || contextEntity.getMarkdownContentNew() == null) {
            throw new BaseException("待确认的新版本不存在");
        }

        // 提前构建返回结果
        NoteChangeDiffVO result = new NoteChangeDiffVO();

        // 解析 diff -> 即为获取标签、图片、内联笔记的差异
        NoteDiffVO diffVO = parseDiff(diffEntity.getDiffJson());

        if (Boolean.TRUE.equals(dto.getConfirm())) {
            // 确认：用新内容覆盖旧内容
            String newMarkdown = contextEntity.getMarkdownContentNew();
            MarkdownHtmlEngine.NoteReletionInfo scan = MarkdownHtmlEngine.scanNoteReletionInfo(newMarkdown);
            List<String> currentTags = normalizeDistinctList(scan.tags());
            List<String> currentImages = normalizeDistinctList(scan.imageNames());
            List<MarkdownHtmlEngine.ParsedNoteLink> currentNoteLinks = List.copyOf(scan.noteLinks());
            // TODO 后续可以在 modifyUpload 中就新文本扫描出来的标签、图片、内联笔记这些数据做一个 json 保存起来，这里直接从数据库中读取 避免二次扫描文本

            // 用新内容覆盖旧内容，清除新版本。
            contextEntity.setMarkdownContent(newMarkdown);
            contextEntity.setMarkdownContentNew(null);
            noteContextMapper.updateContext(contextEntity);

            // 更新标签、图片映射表
            noteTagMappingMapper.softDeleteByNoteId(noteId);
            noteImageMappingMapper.softDeleteByNoteId(noteId);
            noteEachMappingMapper.softDeleteBySourceNoteId(noteId);

            // 建立新的映射记录
            persistRelationMappings(userId, noteId, existed.getTitle(), currentTags, currentImages, currentNoteLinks);

            // 计算大小差异
            long baseSize = safeLong(existed.getMdFileSize());
            long deltaSize = safeLong(diffEntity.getNewFileSize()) - safeLong(diffEntity.getOldFileSize());
            existed.setMdFileSize(Math.max(0L, baseSize + deltaSize));
            existed.setIsMissingInfo(resolveMissingInfo(userId, existed.getTopicId(), currentTags, currentImages, currentNoteLinks));
            noteMapper.updateNote(existed);

            // 更新 笔记-差异 数据行的状态
            noteChangeDiffMapper.updateStatus(noteId, NoteConstant.NOTE_DIFF_STATUS_CONFIRMED); // 确认状态

            // 返回结果状态设置
            result.setStatus(NoteConstant.NOTE_DIFF_STATUS_CONFIRMED); // 确认状态
        } else {
            // ========== 以下是取消的处理逻辑 ==========

            // 取消时仅清除新版本与 diff 记录，旧内容保持不变。
            contextEntity.setMarkdownContentNew(null);
            noteContextMapper.updateContext(contextEntity);

            // 删除 diff 记录
            noteChangeDiffMapper.deleteByNoteId(noteId);

            // 返回结果状态设置
            result.setStatus(NoteConstant.NOTE_DIFF_STATUS_CANCELED); // 取消
        }

        // 构建返回结果
        result.setNoteId(noteId);
        result.setOldFileSize(diffEntity.getOldFileSize());
        result.setNewFileSize(diffEntity.getNewFileSize());
        result.setDiffFileSize(safeLong(diffEntity.getNewFileSize()) - safeLong(diffEntity.getOldFileSize()));
        result.setDiff(diffVO);
        return result;
    }

    /**
     * 获取笔记内容变更详情 - (通用)
     */
    @Override
    public NoteModifyDiffDetailVO getModifyDiff(Long noteId) {
        validateOwnedNote(noteId, BaseContext.getCurrentId());

        // 读取旧新内容与 Diff 记录
        NoteContextEntity contextEntity = noteContextMapper.selectByNoteId(noteId);
        NoteChangeDiffEntity diffEntity = noteChangeDiffMapper.selectByNoteId(noteId);

        // 验证 笔记-内容 是否存在
        if (contextEntity == null) {
            throw new BaseException(NoteConstant.NOTE_CONTENT_NOT_FOUND);
        }

        // 验证 笔记-差异 存在
        if (diffEntity == null || contextEntity.getMarkdownContentNew() == null) {
            throw new BaseException(NoteConstant.NOTE_CHANGE_DIFF_NOT_FOUND);
        }

        /* ============== 封装返回数据 ============== */

        String oldSource = contextEntity.getMarkdownContent();
        String newSource = contextEntity.getMarkdownContentNew();

        NoteChangeDiffVO diffVO = new NoteChangeDiffVO();
        diffVO.setNoteId(noteId);
        diffVO.setStatus(diffEntity.getStatus());
        diffVO.setOldFileSize(diffEntity.getOldFileSize());
        diffVO.setNewFileSize(diffEntity.getNewFileSize());
        diffVO.setDiffFileSize(safeLong(diffEntity.getNewFileSize()) - safeLong(diffEntity.getOldFileSize()));
        diffVO.setDiff(parseDiff(diffEntity.getDiffJson()));
        return new NoteModifyDiffDetailVO(noteId, oldSource, newSource, diffVO);
    }

    /**
     * 获取笔记源文件 -- (管理员)
     * <p>不做笔记归属权的校验，直接获取笔记内容</p>
     * @param noteId 笔记 ID
     * @return 笔记源内容
     */
    @Override
    public String adminGetSource(Long noteId) {
        // 通过 笔记id 获取 笔记-内容
        NoteContextEntity context = noteContextMapper.selectByNoteId(noteId);
        if (context == null) {
            throw new BaseException(NoteConstant.NOTE_CONTENT_NOT_FOUND);
        }
        return context.getMarkdownContent();
    }

    /**
     * 笔记转换 -- (管理员)
     * <p>
     * 不做笔记归属权的校验，直接转换笔记
     * </p>
     * 
     * @param noteId 笔记 ID
     * @return 转换结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteConvertResultVO adminConvertNote(Long noteId) {
        // 获取笔记并且校验存在性
        NoteEntity note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        // 校验是否做了信息转换
        if (note.getIsMissingInfo() != null && note.getIsMissingInfo().equals(NoteConstant.MISSED_INFO)) {
            throw new BaseException(NoteConstant.NOTE_MISSING_INFO);
        }

        // 从数据库读取笔记内容
        NoteContextEntity context = noteContextMapper.selectByNoteId(noteId);
        if (context == null) {
            throw new BaseException(NoteConstant.NOTE_CONTENT_NOT_FOUND);
        }
        String rawMarkdown = context.getMarkdownContent();
        String fallbackTitle = stripMarkdownExtension(note.getTitle());

        try {
            // 给插件链提供当前笔记上下文（例如图片解析阶段需要 noteId）。
            NoteImageResolveContext.setCurrentNoteId(noteId);

            // 这里加入了前置转换的因素，整体来说会把文本扫描两次
            HtmlProcessResult result = markdownHtmlEngine.process(rawMarkdown);
            FrontMatter meta = result.meta().withFallbackTitle(fallbackTitle);

            // 构建笔记转换结果
            NoteConvertedEntity converted = new NoteConvertedEntity();
            converted.setNoteId(noteId);
            converted.setTitle(meta.title());
            converted.setTagsJson(writeJson(meta.tags()));
            converted.setCreateTimeStr(meta.createTime());
            converted.setTocHtml(result.tocHtml());
            converted.setBodyHtml(result.bodyHtml());
            noteConvertMapper.upsertConverted(converted);

            if (StringUtils.hasText(meta.title()) && !meta.title().equals(note.getTitle())) {
                note.setTitle(meta.title());
                noteMapper.updateNote(note);
            }

            return toConvertResultVO(converted);
        } finally {
            NoteImageResolveContext.clear();
        }
    }

    /**
     * 删除笔记转换结果 -- (管理员)
     * <p>
     * 不做笔记归属权的校验，直接删除笔记转换结果
     * </p>
     * 
     * @param noteId 笔记 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDeleteConverted(Long noteId) {
        noteConvertMapper.deleteByNoteId(noteId); // 删除转换结果
        noteMapper.updatePublishStatus(noteId, NoteConstant.IS_PUBLISHED_NO); // 更新发布状态为 未发布
    }

    /**
     * 发布笔记 -- (管理员)
     * <p>不做笔记归属权的校验，直接发布笔记</p>
     * <p>但是会校验笔记是否做了转换</p>
     * @param noteId 笔记 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishNote(Long noteId) {
        // 验证笔记是否已转换
        if (noteConvertMapper.countByNoteId(noteId) <= 0) {
            throw new BaseException(NoteConstant.NOTE_NOT_CONVERTED);
        }
        // 更新笔记为发布装填
        int count = noteMapper.updatePublishStatus(noteId, NoteConstant.IS_PUBLISHED_YES);
        if (count <= 0) {
            log.error("Convented result related to non-existed note!");
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }
    }

    /**
     * 删除笔记 -- (管理员)
     * <p>不做笔记归属权的校验，直接删除笔记</p>
     * @param ids 笔记 ID 列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDeleteNotes(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BaseException("待删除的笔记 ID 列表不能为空");
        }

        List<NoteEntity> notes = noteMapper.selectByIds(ids);
        if (notes.size() != ids.size()) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        // Map<UserId, Storage>
        Map<Long, Long> userStorageMap = new LinkedHashMap<>();
        ArrayList<Long> userIds = new ArrayList<>(ids);
        for (NoteEntity note : notes) {
            userIds.add(note.getUserId());
            userStorageMap.merge(note.getUserId(), safeLong(note.getMdFileSize()), Long::sum);
        }

        // 批量删除关联数据，
        noteConvertMapper.deleteByNoteIds(ids);
        noteChangeDiffMapper.deleteByNoteIds(ids);
        noteContextMapper.deleteByNoteIds(ids);
        noteEachMappingMapper.softDeleteBySourceNoteIds(ids);
        noteTagMappingMapper.softDeleteByNoteIds(ids);
        noteImageMappingMapper.softDeleteByNoteIds(ids);

        // 批量标记软删除
        noteMapper.softDeleteByIds(ids);

        // 批量查询用户
        List<UserEntity> users = userMapper.selectByIds(userIds);

        if (users.size() != userIds.size()) {
            // TODO 后续加入一个异步记录到错误日志里面
            log.error("The picture is associated with a non-existent user.");
        }

        // 批量更新用户存储空间
        for (UserEntity user : users) {
            user.setUsedStorageBytes(
                    Math.max(user.getUsedStorageBytes() - userStorageMap.get(user.getId()), 0L));
        }

        int count = userMapper.upsertUser(users);
        if (count < users.size()) {
            log.error("Failed to update user storage!");
            throw new BaseException(UserConstant.UPDATE_USER_STORAGE_FAILED);
        }
    }

    /**
     * 列出笔记关联的图片 -- (通用)
     * 
     * @param noteId 笔记 ID
     * @return 图片列表
     */
    @Override
    public List<ImageSimpleVO> listImagesByNoteId(Long noteId) {
        // 验证笔记所有权
        validateOwnedNote(noteId, BaseContext.getCurrentId());

        return getImageSimpleVOS(noteId);
    }

    /**
     * 修改笔记可见性 -- (管理员)
     * <p>做完是否有转换记录之后</p>
     * <p>不做笔记归属权的校验，直接修改可见性</p>
     * @param isVisible 是否可见
     * @param dto       笔记可见性修改信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminSetVisible(Short isVisible, NoteVisibleDTO dto) {
        int count;

        if (isVisible != null && isVisible.equals(NoteConstant.IS_PUBLISHED_YES)) {
            // 如果传入的设置可见参数不为 null 且 isVisible 为 1
            if (noteConvertMapper.countByNoteId(dto.getId()) <= 0) {
                throw new BaseException(NoteConstant.NOTE_NOT_CONVERTED);
            }
            count = noteMapper.updatePublishStatus(dto.getId(), NoteConstant.IS_PUBLISHED_YES);
        } else {
            count = noteMapper.updatePublishStatus(dto.getId(), NoteConstant.IS_PUBLISHED_NO);
        }

        if (count < 1) {
            log.error("Failed to update note publish status!" +
                    "This is likely because the converted content is related to non-existed note!");
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }
    }

    /**
     * 设置笔记发布状态 -- (管理员)
     * <p>发布/下架笔记</p>
     *
     * @param noteId 笔记ID
     * @param status 发布状态（1:发布, 0:下架）
     */
    @Override
    public void setNotePublishStatus(Long noteId, Short status) {
        int count;

        if (status != null && status.equals(NoteConstant.IS_PUBLISHED_YES)) {
            // 如果状态为 1（发布），需要检查笔记是否已转换
            if (noteConvertMapper.countByNoteId(noteId) <= 0) {
                throw new BaseException(NoteConstant.NOTE_NOT_CONVERTED);
            }

            // 如果是已转换，则对通过性做校验
            // 1. 标签通过性校验
            if (noteTagMappingMapper.countByNoteIdAndPass(noteId, TagConstant.IS_PASS)
            != noteTagMappingMapper.countByNoteIdAndPass(noteId, null)) {
                throw new BaseException(TagConstant.TAG_NOT_PASS);
            }

            // 2. 图片通过性校验
            if (noteImageMappingMapper.countByNoteIdAndPass(noteId, TagConstant.IS_PASS)
            != noteImageMappingMapper.countByNoteIdAndPass(noteId, null)) {
                throw new BaseException(ImageConstant.IMAGE_NOT_PASS);
            }

            // 3. 内联笔记通过性校验
            if (noteEachMappingMapper.countByNoteIdAndPass(noteId, TagConstant.IS_PASS)
            != noteEachMappingMapper.countByNoteIdAndPass(noteId, null)) {
                throw new BaseException(NoteConstant.NOTE_EACH_NOT_PASS);
            }

            count = noteMapper.updatePublishStatus(noteId, NoteConstant.IS_PUBLISHED_YES);
        } else {
            count = noteMapper.updatePublishStatus(noteId, NoteConstant.IS_PUBLISHED_NO);
        }

        if (count < 1) {
            log.error("Failed to update note publish status! This is likely because the note does not exist!");
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }
    }

    /**
     * 修改笔记信息 -- (通用)
     * <p>
     * 会做笔记所有权的校验
     * </p>
     * 
     * @param dto 笔记信息修改信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void modifyInfo(NoteModifyInfoDTO dto) {
        NoteEntity note = validateOwnedNote(dto.getId(), BaseContext.getCurrentId());

        if (StringUtils.hasText(dto.getTitle())) {
            note.setTitle(dto.getTitle().trim());
        }
        if (dto.getDescription() != null) {
            note.setDescription(dto.getDescription().trim());
        }
        if (dto.getTopicId() != null) {
            validateTopic(dto.getTopicId());
            note.setTopicId(dto.getTopicId());
        }

        // 检查是否可以修改
        if (noteMapper.countByUserIdAndTopicIdAndTitle(note.getUserId(), note.getTopicId(), note.getTitle()) > 0) {
            throw new BaseException("你在对应主题下已存在同名的笔记，无法修改！");
        }

        int count = noteMapper.updateNote(note);

        if (count < 1) {
            log.error("Failed to update note info!");
            throw new BaseException(NoteConstant.NOTE_UPDATE_FAILED);
        }
    }

    @Override
    public PageResult listNotes(NoteQueryDTO dto) {
        if (dto == null) {
            dto = new NoteQueryDTO();
        }

        Integer pageNumParam = dto.getPageNum();
        Integer pageSizeParam = dto.getPageSize();
        int pageNum = pageNumParam == null || pageNumParam <= 0 ? PageConstant.DEFAULT_PAGE : pageNumParam;
        int pageSize = pageSizeParam == null || pageSizeParam <= 0 ? PageConstant.DEFAULT_PAGE_SIZE : pageSizeParam;
        PageHelper.startPage(pageNum, pageSize);

        List<NoteVO> records = noteMapper.listByCondition(dto);
        PageInfo<NoteVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 获取笔记详情 -- (管理员)
     * <p>会跳过笔记所属权的校验</p>
     * @param noteId
     * @return
     */
    @Override
    public NoteDetailVO getInfo(Long noteId) {
        NoteEntity note = noteMapper.selectById(noteId);

        if (note.getIsDeleted() != null && note.getIsDeleted().equals(NoteConstant.DELETED)) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        // 封装基本的信息
        NoteVO noteVO = new NoteVO();
        BeanUtils.copyProperties(note, noteVO);
        // TODO 如果后续在笔记数据行中冗余了 topic_name 就可以不做这一次查询了
        if (note.getTopicId() != null) {
            noteVO.setTopicName(topicMapper.selectById(note.getTopicId()).getTopicName());
        }


        NoteDetailVO detailVO = new NoteDetailVO();
        BeanUtils.copyProperties(noteVO, detailVO);

        // 获取标签
        List<Long> tagIds = noteTagMappingMapper.selectTagIdsByNoteId(noteId);
        detailVO.setTags(tagIds == null || tagIds.isEmpty()
                ? List.of()
                : tagMapper.selectByIds(tagIds).stream().map(TagEntity::getTagName).toList());

        // 获取图片
        detailVO.setImages(getImageSimpleVOS(noteId));

        // 获取笔记双链映射
        List<NoteEachMappingEntity> eachMappings = noteEachMappingMapper.selectBySourceNoteId(noteId);
        if (eachMappings == null || eachMappings.isEmpty()) {
            detailVO.setEachNotes(List.of());
        } else {
            List<Long> targetIds = eachMappings.stream()
                    .map(NoteEachMappingEntity::getTargetNoteId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            Map<Long, String> targetTitleMap;
            if (targetIds.isEmpty()) {
                targetTitleMap = Map.of();
            } else {
                targetTitleMap = noteMapper.selectByIds(targetIds).stream()
                        .filter(n -> n.getIsDeleted() == null || n.getIsDeleted().equals(NoteConstant.NOT_DELETED))
                        .collect(Collectors.toMap(NoteEntity::getId, NoteEntity::getTitle, (left, right) -> left));
            }

            List<NoteEachSimpleVO> eachNoteVos = eachMappings.stream().map(mapping -> {
                NoteEachSimpleVO vo = new NoteEachSimpleVO();
                vo.setTargetNoteId(mapping.getTargetNoteId());
                vo.setTargetNoteTitle(mapping.getTargetNoteId() == null ? null : targetTitleMap.get(mapping.getTargetNoteId()));
                vo.setParsedNoteName(mapping.getParsedNoteName());
                vo.setAnchor(mapping.getAnchor());
                vo.setNickname(mapping.getNickname());
                vo.setIsMissing(mapping.getTargetNoteId() == null ? NoteConstant.MISSED_INFO : NoteConstant.NOT_MISSED_INFO);
                return vo;
            }).toList();
            detailVO.setEachNotes(eachNoteVos);
        }

        // 获取转换结果
        NoteConvertedEntity converted = noteConvertMapper.selectByNoteId(noteId);
        detailVO.setConverted(converted == null ? null : toConvertResultVO(converted));
        return detailVO;
    }

    /**
     * 获取笔记转换结果 -- (管理员)
     * <p>会越过是否发布的校验</p>
     * @param noteId
     * @return
     */
    @Override
    public NoteConvertResultVO adminOpenNote(Long noteId) {
        // 校验笔记是否存在 和 是否删除
        NoteEntity note = noteMapper.selectById(noteId);
        if (note == null || note.getIsDeleted() != null && note.getIsDeleted().equals(NoteConstant.DELETED)) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        // 获取转换结果内容
        NoteConvertedEntity converted = noteConvertMapper.selectByNoteId(noteId);
        if (converted == null) {
            throw new BaseException(NoteConstant.NOTE_NOT_CONVERTED);
        }
        return toConvertResultVO(converted);
    }

    @Override
    public NoteRelationDetailVO getRelationInfo(Long noteId) {
        // 只允许查询自己笔记的关联映射，避免越权读取。
        validateOwnedNote(noteId, BaseContext.getCurrentId());
        // 汇总标签/图片/内联笔记三类映射并返回给前端。
        return buildNoteRelationDetail(noteId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindTagMapping(TagMappingBindDTO dto) {
        // 1) 基础参数校验：映射行ID + 标签ID。
        if (dto == null || dto.getMappingId() == null || dto.getTagId() == null) {
            throw new BaseException("映射ID和标签ID不能为空");
        }
        Long userId = BaseContext.getCurrentId();

        // 2) 校验映射行归属与目标标签存在性。
        NoteTagMappingEntity mapping = requireOwnedTagMapping(dto.getMappingId(), userId);
        TagEntity targetTag = tagMapper.selectByIdAndUserId(dto.getTagId(), userId);
        if (targetTag == null) {
            throw new BaseException("目标标签不存在");
        }

        // 3) 名称一致性 + 审核通过校验。
        if (!Objects.equals(mapping.getParsedTagName(), targetTag.getTagName())) {
            throw new BaseException("标签名称与映射行解析名称不一致，无法绑定");
        }
        if (!AuditConstant.PASS.equals(targetTag.getIsPass())) {
            throw new BaseException("目标标签未通过审核，无法绑定");
        }

        // 4) 执行绑定后，立即刷新笔记缺失状态。
        noteTagMappingMapper.bindTagById(mapping.getId(), targetTag.getId(), AuditConstant.PASS);
        refreshNoteMissingInfo(mapping.getNoteId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindTagMapping(Long mappingId) {
        Long userId = BaseContext.getCurrentId();
        // 先校验归属，再解绑，最后重算 is_missing_info。
        NoteTagMappingEntity mapping = requireOwnedTagMapping(mappingId, userId);
        noteTagMappingMapper.unbindTagById(mappingId);
        refreshNoteMissingInfo(mapping.getNoteId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindImageMapping(ImageMappingBindDTO dto) {
        // 1) 基础参数校验：映射行ID + 图片ID。
        if (dto == null || dto.getMappingId() == null || dto.getImageId() == null) {
            throw new BaseException("映射ID和图片ID不能为空");
        }
        Long userId = BaseContext.getCurrentId();

        // 2) 校验映射归属和目标图片存在。
        NoteImageMappingEntity mapping = requireOwnedImageMapping(dto.getMappingId(), userId);
        ImageEntity targetImage = imageMapper.selectById(dto.getImageId());
        if (targetImage == null) {
            throw new BaseException("目标图片不存在");
        }

        // 3) 文件名一致性 + 审核通过校验。
        if (!Objects.equals(mapping.getParsedImageName(), targetImage.getFilename())) {
            throw new BaseException("图片名称与映射行解析名称不一致，无法绑定");
        }
        // 检查一下图片归属
        if (!userId.equals(targetImage.getUserId())) {
            if (!AuditConstant.PASS.equals(targetImage.getIsPass())) {
                throw new BaseException("目标图片未通过审核，无法绑定");
            }
        }

        // 4) 计算是否跨用户引用，用于后续展示和风控。
        Short isCrossUser = targetImage.getUserId() != null && !targetImage.getUserId().equals(mapping.getNoteUserId())
                ? NoteConstant.IS_CROSS_USER
                : NoteConstant.NOT_IS_CROSS_USER;

        // 5) 执行绑定并回写完整性状态。
        noteImageMappingMapper.bindImageById(mapping.getId(), targetImage.getId(), targetImage.getUserId(), isCrossUser, targetImage.getIsPass());
        refreshNoteMissingInfo(mapping.getNoteId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindImageMapping(Long mappingId) {
        Long userId = BaseContext.getCurrentId();
        // 先做归属校验，解绑后再统一重算缺失状态。
        NoteImageMappingEntity mapping = requireOwnedImageMapping(mappingId, userId);
        noteImageMappingMapper.unbindImageById(mappingId);
        refreshNoteMissingInfo(mapping.getNoteId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindEachMapping(EachMappingBindDTO dto) {
        // 1) 基础参数校验：映射行ID + 目标笔记ID。
        if (dto == null || dto.getMappingId() == null || dto.getNoteId() == null) {
            throw new BaseException("映射ID和笔记ID不能为空");
        }
        Long userId = BaseContext.getCurrentId();

        // 2) 校验映射归属与目标笔记存在性。
        NoteEachMappingEntity mapping = requireOwnedEachMapping(dto.getMappingId(), userId);
        NoteEntity targetNote = noteMapper.selectById(dto.getNoteId());
        if (targetNote == null || NoteConstant.DELETED.equals(targetNote.getIsDeleted())) {
            throw new BaseException("目标笔记不存在");
        }

        // 3) 以标题与 parsed_note_name 对比，nickname 仅用于显示不参与绑定。
        if (!Objects.equals(mapping.getParsedNoteName(), targetNote.getTitle())) {
            throw new BaseException("笔记标题与映射行解析名称不一致，无法绑定");
        }
        if (!AuditConstant.PASS.equals(targetNote.getIsPass())) {
            throw new BaseException("目标笔记未通过审核，无法绑定");
        }

        // 4) 执行绑定并刷新来源笔记缺失状态。
        noteEachMappingMapper.bindNoteById(mapping.getId(), targetNote.getId(), AuditConstant.PASS);
        refreshNoteMissingInfo(mapping.getSourceNoteId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindEachMapping(Long mappingId) {
        Long userId = BaseContext.getCurrentId();
        // 解绑后也需要按最新映射重新计算完整性。
        NoteEachMappingEntity mapping = requireOwnedEachMapping(mappingId, userId);
        noteEachMappingMapper.unbindNoteById(mappingId);
        refreshNoteMissingInfo(mapping.getSourceNoteId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean checkRelationCompletion(Long noteId) {
        // 只允许笔记所有者触发完整性校验。
        Long userId = BaseContext.getCurrentId();
        NoteEntity note = validateOwnedNote(noteId, userId);

        // 在校验完整性前，先尝试批量补绑定“现在已存在且可绑定”的资源。
        syncBindableMappings(noteId, userId, note.getTopicId());

        // 基于三类映射结果计算完整性，并同步回写笔记主表。
        boolean complete = isRelationComplete(noteId);
        noteMapper.updateMissingInfo(noteId, complete ? NoteConstant.NOT_MISSED_INFO : NoteConstant.MISSED_INFO);
        return complete;
    }

    /**
     * 批量补绑定当前可命中的标签/图片/内联笔记映射。
     * <p>所有匹配与绑定都使用批量查询和批量更新，避免循环网络 IO。</p>
     *
     * @param noteId 来源笔记ID
     * @param userId 当前用户ID
     * @param topicId 来源笔记所属主题ID
     */
    private void syncBindableMappings(Long noteId, Long userId, Long topicId) {
        syncBindableTagMappings(noteId, userId);
        syncBindableImageMappings(noteId, userId, topicId);
        syncBindableEachMappings(noteId, userId, topicId);
    }

    /**
     * 批量补绑定标签映射：按 parsed_tag_name 命中用户标签并回写映射。
     */
    private void syncBindableTagMappings(Long noteId, Long userId) {
        List<NoteTagMappingEntity> mappings = Optional.ofNullable(noteTagMappingMapper.selectByNoteId(noteId))
                .orElse(List.of());
        if (mappings.isEmpty()) {
            return;
        }

        List<String> parsedNames = mappings.stream()
                .map(NoteTagMappingEntity::getParsedTagName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (parsedNames.isEmpty()) {
            return;
        }

        List<TagEntity> tags = Optional.ofNullable(tagMapper.selectIdsByNamesAndUserId(parsedNames, userId))
                .orElse(List.of());
        if (tags.isEmpty()) {
            return;
        }

        Map<String, TagEntity> tagMap = tags.stream()
                .collect(Collectors.toMap(TagEntity::getTagName, tag -> tag, (left, right) -> left));

        List<NoteTagMappingEntity> toBind = new ArrayList<>();
        for (NoteTagMappingEntity mapping : mappings) {
            TagEntity target = tagMap.get(mapping.getParsedTagName());
            if (target == null || !AuditConstant.PASS.equals(target.getIsPass())) {
                continue;
            }
            if (Objects.equals(mapping.getTagId(), target.getId())
                    && AuditConstant.PASS.equals(mapping.getIsPass())) {
                continue;
            }

            NoteTagMappingEntity bind = new NoteTagMappingEntity();
            bind.setId(mapping.getId());
            bind.setTagId(target.getId());
            bind.setIsPass(AuditConstant.PASS);
            toBind.add(bind);
        }

        if (!toBind.isEmpty()) {
            noteTagMappingMapper.batchBindTagByIds(toBind);
        }
    }

    /**
     * 批量补绑定图片映射：按 parsed_image_name 命中同用户同主题图片并回写映射。
     */
    private void syncBindableImageMappings(Long noteId, Long userId, Long topicId) {
        if (topicId == null) {
            return;
        }

        List<NoteImageMappingEntity> mappings = Optional.ofNullable(noteImageMappingMapper.selectByNoteId(noteId))
                .orElse(List.of());
        if (mappings.isEmpty()) {
            return;
        }

        List<String> parsedNames = mappings.stream()
                .map(NoteImageMappingEntity::getParsedImageName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (parsedNames.isEmpty()) {
            return;
        }

        List<ImageEntity> images = Optional.ofNullable(
                imageMapper.selectByUserIdAndTopicIdAndFilenames(userId, topicId, parsedNames))
                .orElse(List.of());
        if (images.isEmpty()) {
            return;
        }

        Map<String, ImageEntity> imageMap = images.stream()
                .collect(Collectors.toMap(ImageEntity::getFilename, image -> image, (left, right) -> left));

        List<NoteImageMappingEntity> toBind = new ArrayList<>();
        for (NoteImageMappingEntity mapping : mappings) {
            ImageEntity target = imageMap.get(mapping.getParsedImageName());
            if (target == null) {
                continue;
            }

            // 与手动绑定规则保持一致：同用户图片可绑定；跨用户图片需要审核通过。
            boolean isCrossUser = target.getUserId() != null && !target.getUserId().equals(mapping.getNoteUserId());
            if (isCrossUser && !AuditConstant.PASS.equals(target.getIsPass())) {
                continue;
            }

            Short crossUserFlag = isCrossUser ? NoteConstant.IS_CROSS_USER : NoteConstant.NOT_IS_CROSS_USER;
            boolean alreadyBound = Objects.equals(mapping.getImageId(), target.getId())
                    && Objects.equals(mapping.getImageUserId(), target.getUserId())
                    && Objects.equals(mapping.getIsCrossUser(), crossUserFlag)
                    && Objects.equals(mapping.getIsPass(), target.getIsPass());
            if (alreadyBound) {
                continue;
            }

            NoteImageMappingEntity bind = new NoteImageMappingEntity();
            bind.setId(mapping.getId());
            bind.setImageId(target.getId());
            bind.setImageUserId(target.getUserId());
            bind.setIsCrossUser(crossUserFlag);
            bind.setIsPass(target.getIsPass());
            toBind.add(bind);
        }

        if (!toBind.isEmpty()) {
            noteImageMappingMapper.batchBindImageByIds(toBind);
        }
    }

    /**
     * 批量补绑定内联笔记映射：按 parsed_note_name 命中同用户同主题目标笔记并回写映射。
     */
    private void syncBindableEachMappings(Long noteId, Long userId, Long topicId) {
        if (topicId == null) {
            return;
        }

        List<NoteEachMappingEntity> mappings = Optional.ofNullable(noteEachMappingMapper.selectBySourceNoteId(noteId))
                .orElse(List.of());
        if (mappings.isEmpty()) {
            return;
        }

        List<String> parsedNames = mappings.stream()
                .map(NoteEachMappingEntity::getParsedNoteName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (parsedNames.isEmpty()) {
            return;
        }

        List<NoteEntity> notes = Optional.ofNullable(
                noteMapper.selectByUserIdAndTopicIdAndTitles(userId, topicId, parsedNames))
                .orElse(List.of());
        if (notes.isEmpty()) {
            return;
        }

        Map<String, NoteEntity> noteMap = notes.stream()
                .collect(Collectors.toMap(NoteEntity::getTitle, note -> note, (left, right) -> left));

        List<NoteEachMappingEntity> toBind = new ArrayList<>();
        for (NoteEachMappingEntity mapping : mappings) {
            NoteEntity target = noteMap.get(mapping.getParsedNoteName());
            if (target == null || !AuditConstant.PASS.equals(target.getIsPass())) {
                continue;
            }
            if (Objects.equals(mapping.getTargetNoteId(), target.getId())
                    && AuditConstant.PASS.equals(mapping.getIsPass())) {
                continue;
            }

            NoteEachMappingEntity bind = new NoteEachMappingEntity();
            bind.setId(mapping.getId());
            bind.setTargetNoteId(target.getId());
            bind.setIsPass(AuditConstant.PASS);
            toBind.add(bind);
        }

        if (!toBind.isEmpty()) {
            noteEachMappingMapper.batchBindNoteByIds(toBind);
        }
    }

    /**
     * 插入笔记的基础数据
     * <p>
     * 这里只会检查 image 是否存在来初始最开始的 IsMissingInfo
     * </p>
     * 
     * @param userId
     * @param topicId
     * @param originalFilename
     * @param fileSize
     * @param imageNames
     * @return
     */
    private Long insertBaseNote(Long userId, Long topicId, String originalFilename,
            long fileSize, List<String> tags, List<String> imageNames, List<MarkdownHtmlEngine.ParsedNoteLink> noteLinks) {
        NoteEntity note = new NoteEntity();
        note.setUserId(userId);
        note.setTopicId(topicId);
        note.setTitle(stripMarkdownExtension(originalFilename));
        note.setDescription(null);
        note.setIsPublished(NoteConstant.IS_PUBLISHED_NO);
        note.setStorageType(NoteConstant.DEFAULT_STORAGE_TYPE);
        note.setIsMissingInfo(resolveMissingInfo(userId, topicId, tags, imageNames, noteLinks));
        note.setIsPass(AuditConstant.WAIT); // 插进来先待审核
        note.setIsDeleted(NoteConstant.NOT_DELETED);
        note.setMdFileSize(fileSize);
        if (noteMapper.insertNote(note) <= 0) {
            throw new BaseException(NoteConstant.NOTE_UPDATE_FAILED);
        }
        return note.getId();
    }

    /**
     * 批量建立 note-image 映射关系
     * <p>底层会检查数有没有合法的数据行存在，也就是对应的数据行</p>
     * <p>如果存在，不会重新插入，同时检查这条数据行的删除标记标记为 0</p>
     * <p> 如果不存在，则插入一条新的数据行</p>
     */
    private void persistRelationMappings(Long userId, Long noteId, String noteTitle,
                                         List<String> tags, List<String> imageNames) {
        // 先保证标签实体存在（不存在则补建），再落库标签映射。
        Map<String, TagEntity> tagMap = resolveTagMap(userId, tags);
        List<NoteTagMappingEntity> tagMappings = buildTagMappings(noteId, tags, tagMap);
        if (!tagMappings.isEmpty()) {
            noteTagMappingMapper.batchInsertMappings(tagMappings);
        }

        // 图片映射按解析名建立；未命中图片时保留空 image_id，等待后续绑定。
        if (!imageNames.isEmpty()) {
            noteImageMappingMapper.batchInsertMappings(buildImageMappings(noteId, userId, noteTitle, imageNames));
        }
    }

    /**
     * 批量建立 note-each 映射关系
     * <p>从笔记头部解析出标签，然后建立映射</p>
     * <p>建立从笔记双链中解析出来的图片、笔记</p>
     */
    private void persistRelationMappings(Long userId, Long noteId, String noteTitle,
            List<String> tags, List<String> imageNames, List<MarkdownHtmlEngine.ParsedNoteLink> noteLinks) {
        // 复用已有方法 先建立 标签、图片 映射
        persistRelationMappings(userId, noteId, noteTitle, tags, imageNames);

        // 建立 note-each 映射
        List<NoteEachMappingEntity> eachMappings = buildEachMappings(userId, noteId, noteLinks);
        if (!eachMappings.isEmpty()) {
            noteEachMappingMapper.batchInsertMappings(eachMappings);
        }
    }

    /**
     * 批量建立 note-tag 映射关系
     */
    private List<NoteTagMappingEntity> buildTagMappings(Long noteId, List<String> tags, Map<String, TagEntity> tagMap) {
        // 标签以“解析出的 tagName”为行粒度，便于后续按映射行绑定/解绑。
        List<String> parsedTags = normalizeDistinctList(tags);
        if (parsedTags.isEmpty()) {
            return List.of();
        }

        List<NoteTagMappingEntity> mappings = new ArrayList<>();
        for (String parsedTagName : parsedTags) {
            TagEntity tag = tagMap.get(parsedTagName);
            NoteTagMappingEntity mapping = new NoteTagMappingEntity();
            mapping.setNoteId(noteId);
            mapping.setTagId(tag == null ? null : tag.getId());
            mapping.setParsedTagName(parsedTagName);
            mapping.setIsPass(tag != null && AuditConstant.PASS.equals(tag.getIsPass())
                    ? AuditConstant.PASS
                    : AuditConstant.WAIT);
            mapping.setIsDeleted(NoteConstant.NOT_DELETED);
            mapping.setCreateTime(LocalDateTime.now());
            mappings.add(mapping);
        }
        return mappings;
    }

    /**
     * 批量建立 note-image 映射关系 map 表
     */
    private List<NoteImageMappingEntity> buildImageMappings(Long noteId, Long userId, String noteTitle,
            List<String> imageNames) {
        List<String> names = normalizeDistinctList(imageNames);
        if (names.isEmpty()) {
            return List.of();
        }

        // 获取来源笔记所在的话题 ID
        Long topicId = noteMapper.selectById(noteId).getTopicId();

        // 批量查询：命中 (user_id, topic_id, filename(40)) 联合索引，避免 N+1
        List<ImageEntity> found = imageMapper.selectByUserIdAndTopicIdAndFilenames(userId, topicId, names);
        Map<String, ImageEntity> imageMap = found == null || found.isEmpty()
                ? Collections.emptyMap()
                : found.stream().collect(Collectors.toMap(ImageEntity::getFilename, img -> img));

        // 构建映射列表；找不到的图片 imageId 置 null，留给用户后续自行绑定
        List<NoteImageMappingEntity> mappings = new ArrayList<>();
        for (String imageName : names) {
            ImageEntity image = imageMap.get(imageName);
            NoteImageMappingEntity mapping = new NoteImageMappingEntity();
            mapping.setNoteId(noteId);
            mapping.setNoteUserId(userId);
            mapping.setNoteTitle(noteTitle);
            mapping.setParsedImageName(imageName);
            mapping.setCreateTime(LocalDateTime.now());
            mapping.setIsDeleted(NoteConstant.NOT_DELETED);
            if (image != null) {
                // 图片存在时绑定 image_id；跨用户引用会标记 is_cross_user。
                mapping.setImageId(image.getId());
                mapping.setImageUserId(image.getUserId());
                mapping.setIsCrossUser(
                        image.getUserId() != null && !image.getUserId().equals(userId) ? NoteConstant.IS_CROSS_USER
                                : NoteConstant.NOT_IS_CROSS_USER);
                mapping.setIsPass(AuditConstant.PASS.equals(image.getIsPass()) ? AuditConstant.PASS : AuditConstant.WAIT);
            } else {
                // 图片缺失时先保留解析名，后续可补图再重新转换。
                mapping.setImageId(null);
                mapping.setImageUserId(null);
                mapping.setIsCrossUser(NoteConstant.NOT_IS_CROSS_USER);
                mapping.setIsPass(AuditConstant.WAIT);
            }
            mappings.add(mapping);
        }
        return mappings;
    }

    /**
     * 构建 note-each 映射实体列表。
     * <p>
     * 一次性批量查询目标笔记，避免 N+1。
     * 由于 {@code ParsedNoteLink} 已经在引擎扫描阶段完成了 (noteName, anchor, nickname)
     * 三元去重，此处仅按 noteName 批量查询就能得到 targetNoteId。
     * </p>
     */
    private List<NoteEachMappingEntity> buildEachMappings(Long userId, Long sourceNoteId,
            List<MarkdownHtmlEngine.ParsedNoteLink> noteLinks) {
        if (noteLinks == null || noteLinks.isEmpty()) {
            return List.of();
        }

        // 提取笔记名列表（去重），一次性批量查询
        List<String> names = noteLinks.stream()
                .map(MarkdownHtmlEngine.ParsedNoteLink::noteName)
                .distinct()
                .toList();

        // 获取来源笔记所在的话题 ID
        Long topicId = noteMapper.selectById(sourceNoteId).getTopicId();

        // 批量查询：命中 (user_id, topic_id, title(30), is_deleted) 联合唯一索引，避免 N+1
        List<NoteEntity> found = noteMapper.selectByUserIdAndTopicIdAndTitles(userId, topicId, names);
        Map<String, NoteEntity> noteMap = found == null || found.isEmpty()
                ? Collections.emptyMap()
                : found.stream().collect(Collectors.toMap(NoteEntity::getTitle, n -> n));

        // 构建映射列表；找不到目标笔记时 targetNoteId 置 null，留给用户后续自行绑定
        List<NoteEachMappingEntity> mappings = new ArrayList<>();
        for (MarkdownHtmlEngine.ParsedNoteLink link : noteLinks) {
            NoteEntity target = noteMap.get(link.noteName());
            NoteEachMappingEntity mapping = new NoteEachMappingEntity();
            mapping.setSourceNoteId(sourceNoteId);
            mapping.setTargetNoteId(target == null ? null : target.getId());
            mapping.setParsedNoteName(link.noteName());
            mapping.setAnchor(link.anchor());      // 可为 null
            mapping.setNickname(link.nickname());  // 可为 null
            mapping.setIsPass(target != null && AuditConstant.PASS.equals(target.getIsPass())
                    ? AuditConstant.PASS
                    : AuditConstant.WAIT);
            mapping.setIsDeleted(NoteConstant.NOT_DELETED);
            mappings.add(mapping);
        }
        return mappings;
    }

    /**
     * 批量查询图片，并检查确实的图片
     * <p>通过一条 IN 查询完成比对，网络 IO 复杂度为 O(1)</p>
     * @param userId 用户ID
     * @param topicId 主题ID
     * @param imageNames 图片名称列表
     * @return 缺失的图片名称列表
     */
    private List<String> resolveMissingImages(Long userId, Long topicId, List<String> imageNames) {
        List<String> names = normalizeDistinctList(imageNames);
        if (names.isEmpty()) {
            return List.of();
        }
        List<ImageEntity> found = imageMapper.selectByUserIdAndTopicIdAndFilenames(userId, topicId, names);
        Set<String> foundNames = found == null ?
                Set.of() : found.stream().map(ImageEntity::getFilename).collect(Collectors.toSet());

        // 如果是不存在 foundNames 中，则会被加入到列表中，否则会被过滤掉
        return names.stream().filter(name -> !foundNames.contains(name)).toList();
    }

    /**
     * 检查缺失的标签
     * <p>仅会查询标签的内容是否为空 不为空即为全部存在</p>
     * @return 缺失标签返回 true 否则返回 false
     */
    private boolean hasMissingTags(List<String> tags) {
        if (tags == null) {
            return false;
        }
        return tags.stream().anyMatch(tag -> !StringUtils.hasText(tag));
    }

    /**
     * 检查缺失的笔记
     * <p>检查该用户同主题下对应名字的笔记</p>
     * <p>涉及到 O(1) 的 网络IO 去 MySQL 批量查询</p>
     * @return 缺失笔记返回 true 否则返回 false
     */
    private boolean hasMissingNotes(Long userId, Long topicId, List<MarkdownHtmlEngine.ParsedNoteLink> noteLinks) {
        if (noteLinks == null || noteLinks.isEmpty()) {
            return false;
        }
        List<String> names = noteLinks.stream()
                .map(MarkdownHtmlEngine.ParsedNoteLink::noteName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (names.isEmpty()) {
            return false;
        }

        List<NoteEntity> found = noteMapper.selectByUserIdAndTopicIdAndTitles(userId, topicId, names);
        Set<String> foundTitles = found == null ? Set.of() : found.stream()
                .filter(note -> note.getIsDeleted() == null
                                || note.getIsDeleted().equals(NoteConstant.NOT_DELETED))
                .map(NoteEntity::getTitle)
                .collect(Collectors.toSet());

        // 如果存在标签 不被 foundTitles 包含 则会返回 true  -> 说明缺失标签
        return names.stream().anyMatch(name -> !foundTitles.contains(name));
    }

    /**
     * 检查缺失信息
     * @return 0=信息完整，1=存在缺失关联信息
     */
    private Short resolveMissingInfo(Long userId, Long topicId,
            List<String> tags, List<String> imageNames, List<MarkdownHtmlEngine.ParsedNoteLink> noteLinks) {
        boolean missingTags = hasMissingTags(tags);
        boolean missingImages = !resolveMissingImages(userId, topicId, imageNames).isEmpty();
        boolean missingNotes = hasMissingNotes(userId, topicId, noteLinks);
        return (missingTags || missingImages || missingNotes) ?
                NoteConstant.MISSED_INFO : NoteConstant.NOT_MISSED_INFO;
    }

    /**
     * 在数据库中批量查询标签
     * 
     * @param userId
     * @param tags
     * @return
     */
    private Map<String, TagEntity> resolveTagMap(Long userId, List<String> tags) {
        List<String> tagNames = normalizeDistinctList(tags);
        if (tagNames.isEmpty()) {
            return Map.of();
        }

        // 先批量查已有标签，减少后续单条查询。
        List<TagEntity> existing = tagMapper.selectIdsByNamesAndUserId(tagNames, userId);
        Map<String, TagEntity> existingMap = existing == null ? new HashMap<>() : existing.stream()
                .collect(Collectors.toMap(
                        TagEntity::getTagName,
                        tag -> tag,
                        (left, right) -> left,
                        HashMap::new)
                );

        // 仅为缺失标签补建，避免重复插入。
        List<TagEntity> needInsert = new ArrayList<>();
        for (String tagName : tagNames) {
            if (!existingMap.containsKey(tagName)) {
                TagEntity newTag = new TagEntity();
                newTag.setTagName(tagName);
                newTag.setUserId(userId);
                newTag.setIsPass(AuditConstant.WAIT);
                needInsert.add(newTag);
            }
        }

        if (!needInsert.isEmpty()) {
            int count = tagMapper.batchInsertTags(needInsert);
            if (count != needInsert.size()) {
                throw new BaseException("标签新增失败");
            }

            // 补建后重新查询，确保拿到完整 id + isPass。
            List<TagEntity> refreshed = tagMapper.selectIdsByNamesAndUserId(tagNames, userId);
            return refreshed == null ? Map.of() : refreshed.stream()
                    .collect(Collectors
                             .toMap(TagEntity::getTagName,
                                     tag -> tag,
                                     (left, right) -> left)
                    );
        }

        return existingMap;
    }

    /**
     * 校验映射标签归属
     * <p>会进行一次网络IO查询MySQL</p>
     * <p>校验通过会返回对应的映射数据行</p>
     * @param mappingId 标签映射行 id
     * @param userId 用户 id
     * @return 对应的映射行
     */
    private NoteTagMappingEntity requireOwnedTagMapping(Long mappingId, Long userId) {
        // 映射行存在性 + 来源笔记归属双重校验。
        NoteTagMappingEntity mapping = noteTagMappingMapper.selectById(mappingId);
        if (mapping == null) {
            throw new BaseException("标签映射行不存在");
        }
        validateOwnedNote(mapping.getNoteId(), userId);
        return mapping;
    }

    /**
     * 校验映射图片归属
     * <p>会进行一次网络IO查询MySQL</p>
     * <p>校验通过会返回对应的映射数据行</p>
     * @param mappingId 图片映射行 id
     * @param userId 用户 id
     * @return 对应的映射行
     */
    private NoteImageMappingEntity requireOwnedImageMapping(Long mappingId, Long userId) {
        // 映射行存在性 + 来源笔记归属双重校验。
        NoteImageMappingEntity mapping = noteImageMappingMapper.selectById(mappingId);
        if (mapping == null) {
            throw new BaseException("图片映射行不存在");
        }
        validateOwnedNote(mapping.getNoteId(), userId);
        return mapping;
    }

    /**
     * 校验映射笔记归属
     * <p>会进行一次网络IO查询MySQL</p>
     * <p>校验通过会返回对应的映射数据行</p>
     * @param mappingId 笔记映射行 id
     * @param userId 用户 id
     * @return 对应的映射行
     */
    private NoteEachMappingEntity requireOwnedEachMapping(Long mappingId, Long userId) {
        // 映射行存在性 + 来源笔记归属双重校验。
        NoteEachMappingEntity mapping = noteEachMappingMapper.selectById(mappingId);
        if (mapping == null) {
            throw new BaseException("笔记映射行不存在");
        }
        validateOwnedNote(mapping.getSourceNoteId(), userId);
        return mapping;
    }

    /**
     * 刷新笔记缺失状态。
     * <p>绑定/解绑任一关联后统一走该入口，避免不同路径状态不一致。</p>
     * @param noteId 笔记ID
     */
    private void refreshNoteMissingInfo(Long noteId) {
        // 统一入口：每次绑定/解绑后都由当前映射状态推导 is_missing_info。
        noteMapper.updateMissingInfo(noteId, isRelationComplete(noteId) ?
                NoteConstant.NOT_MISSED_INFO : NoteConstant.MISSED_INFO);
    }

    /**
     * 判断笔记关联是否完整。
     * <p>标签/图片/内联笔记三类映射都无缺失时才返回 true。</p>
     * <p>一共会进行 6 次的大批量网络IO</p>
     * @param noteId 笔记ID
     * @return true=完整，false=存在缺失
     */
    private boolean isRelationComplete(Long noteId) {
        // 复用聚合结果，分别判断三类映射是否都“无缺失”。
        NoteRelationDetailVO detail = buildNoteRelationDetail(noteId);
        boolean tagsComplete = detail.getTags().stream()
                .noneMatch(row -> NoteConstant.MISSED_INFO.equals(row.getIsMissing()));
        if (!tagsComplete) return false;

        boolean imagesComplete = detail.getImages().stream()
                .noneMatch(row -> NoteConstant.MISSED_INFO.equals(row.getIsMissing()));
        if (!imagesComplete) return false;

        boolean eachNotesComplete = detail.getEachNotes().stream()
                .noneMatch(row -> NoteConstant.MISSED_INFO.equals(row.getIsMissing()));
        if (!eachNotesComplete) return false;

        return true;
    }

    /**
     * 构建笔记关联关系详情。
     * @param noteId
     * @return
     */
    private NoteRelationDetailVO buildNoteRelationDetail(Long noteId) {
        // 1) 一次性读取三类映射。
        List<NoteTagMappingEntity> tagMappings =
                Optional.ofNullable(noteTagMappingMapper.selectByNoteId(noteId)).orElse(List.of());
        List<NoteImageMappingEntity> imageMappings =
                Optional.ofNullable(noteImageMappingMapper.selectByNoteId(noteId)).orElse(List.of());
        List<NoteEachMappingEntity> eachMappings =
                Optional.ofNullable(noteEachMappingMapper.selectBySourceNoteId(noteId)).orElse(List.of());

        // 2) 批量构建 id -> 实体缓存，避免行级 N+1 查询。
        Map<Long, TagEntity> tagMap = buildTagMap(tagMappings);
        Map<Long, ImageEntity> imageMap = buildImageMap(imageMappings);
        Map<Long, NoteEntity> targetNoteMap = buildTargetNoteMap(eachMappings);

        // 3) 组装返回 VO。
        NoteRelationDetailVO vo = new NoteRelationDetailVO();
        vo.setNoteId(noteId);
        vo.setTags(buildTagRows(tagMappings, tagMap));
        vo.setImages(buildImageRows(imageMappings, imageMap));
        vo.setEachNotes(buildEachRows(eachMappings, targetNoteMap));
        return vo;
    }

    /**
     * 批量回查标签实体并构建缓存。
     * <p>将 N 条映射的标签查询合并成 1 次网络 IO。</p>
     * @param mappings 标签映射列表
     * @return tagId -> TagEntity 映射
     */
    private Map<Long, TagEntity> buildTagMap(List<NoteTagMappingEntity> mappings) {
        // 提取已绑定 tag_id 后批量回查标签详情。
        // 对 ids 进行去重操作
        List<Long> ids = mappings.stream()
                .map(NoteTagMappingEntity::getTagId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 检查 ids 是否为空
        if (ids.isEmpty()) {
            return Map.of();
        }
        // 返回批量查询的映射建立map表
        return tagMapper.selectByIds(ids).stream()
                .collect(
                        Collectors.toMap(TagEntity::getId,
                                tag -> tag,
                                (left, right) -> left)
                );
    }

    /**
     * 批量回查图片实体并构建缓存。
     * <p>将 N 条映射的图片查询合并成 1 次网络 IO。</p>
     * @param mappings 图片映射列表
     * @return imageId -> ImageEntity 映射
     */
    private Map<Long, ImageEntity> buildImageMap(List<NoteImageMappingEntity> mappings) {
        // 提取已绑定 image_id 后批量回查图片详情。
        // 对 ids 进行去重操作
        List<Long> ids = mappings.stream()
                .map(NoteImageMappingEntity::getImageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 检查 ids 是否为空
        if (ids.isEmpty()) {
            return Map.of();
        }

        // 返回批量查询的映射建立map表
        return imageMapper.selectByIds(new ArrayList<>(ids)).stream()
                .collect(Collectors.toMap(
                        ImageEntity::getId,
                        image -> image,
                        (left, right) -> left)
                );
    }

    /**
     * 批量回查目标笔记实体并构建缓存。
     * <p>将 N 条内联映射的目标笔记查询合并成 1 次网络 IO。</p>
     * @param mappings 内联笔记映射列表
     * @return noteId -> NoteEntity 映射
     */
    private Map<Long, NoteEntity> buildTargetNoteMap(List<NoteEachMappingEntity> mappings) {
        // 提取已绑定 target_note_id 后批量回查目标笔记。
        List<Long> ids = mappings.stream()
                .map(NoteEachMappingEntity::getTargetNoteId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return noteMapper.selectByIds(ids).stream()
                        .collect(
                        Collectors.toMap(NoteEntity::getId,
                                note -> note,
                                (left, right) -> left)
                );
    }

    /**
     * 组装标签映射行 VO。
     * @param mappings 标签映射实体列表
     * @param tagMap 标签缓存
     * @return 标签映射行 VO 列表
     */
    private List<NoteTagMappingRowVO> buildTagRows(List<NoteTagMappingEntity> mappings, Map<Long, TagEntity> tagMap) {
        return mappings.stream().map(mapping -> {
            TagEntity tag = mapping.getTagId() == null ? null : tagMap.get(mapping.getTagId());
            // “有效绑定”要求：有 tag_id、名称一致
            boolean validBind = mapping.getTagId() != null
                    && tag != null
                    && Objects.equals(mapping.getParsedTagName(), tag.getTagName());

            NoteTagMappingRowVO row = new NoteTagMappingRowVO();
            row.setMappingId(mapping.getId());
            row.setNoteId(mapping.getNoteId());
            row.setTagId(mapping.getTagId());
            row.setParsedTagName(mapping.getParsedTagName());
            row.setTagName(tag == null ? null : tag.getTagName());
            row.setIsPass(mapping.getIsPass());
            row.setIsMissing(validBind ? NoteConstant.NOT_MISSED_INFO : NoteConstant.MISSED_INFO);
            return row;
        }).toList();
    }

    /**
     * 组装图片映射行 VO。
     * @param mappings 图片映射实体列表
     * @param imageMap 图片缓存
     * @return 图片映射行 VO 列表
     */
    private List<NoteImageMappingRowVO> buildImageRows(List<NoteImageMappingEntity> mappings, Map<Long, ImageEntity> imageMap) {
        return mappings.stream().map(mapping -> {
            ImageEntity image = mapping.getImageId() == null ? null : imageMap.get(mapping.getImageId());
            // “有效绑定”要求：有 image_id、文件名一致
            boolean validBind = mapping.getImageId() != null
                    && image != null
                    && Objects.equals(mapping.getParsedImageName(), image.getFilename());

            NoteImageMappingRowVO row = new NoteImageMappingRowVO();
            row.setMappingId(mapping.getId());
            row.setNoteId(mapping.getNoteId());
            row.setImageId(mapping.getImageId());
            row.setParsedImageName(mapping.getParsedImageName());
            row.setFilename(image == null ? null : image.getFilename());
            row.setIsCrossUser(mapping.getIsCrossUser());
            row.setIsPass(mapping.getIsPass());
            row.setIsMissing(validBind ? NoteConstant.NOT_MISSED_INFO : NoteConstant.MISSED_INFO);
            return row;
        }).toList();
    }

    /**
     * 组装内联笔记映射行 VO。
     * @param mappings 内联笔记映射实体列表
     * @param noteMap 目标笔记缓存
     * @return 内联笔记映射行 VO 列表
     */
    private List<NoteEachMappingRowVO> buildEachRows(List<NoteEachMappingEntity> mappings, Map<Long, NoteEntity> noteMap) {
        return mappings.stream().map(mapping -> {
            NoteEntity target = mapping.getTargetNoteId() == null ? null : noteMap.get(mapping.getTargetNoteId());
            // “有效绑定”要求：有 target_note_id、目标未删除、标题匹配
            boolean validBind = mapping.getTargetNoteId() != null
                    && target != null
                    && NoteConstant.NOT_DELETED.equals(target.getIsDeleted())
                    && Objects.equals(mapping.getParsedNoteName(), target.getTitle());

            NoteEachMappingRowVO row = new NoteEachMappingRowVO();
            row.setMappingId(mapping.getId());
            row.setSourceNoteId(mapping.getSourceNoteId());
            row.setTargetNoteId(mapping.getTargetNoteId());
            row.setParsedNoteName(mapping.getParsedNoteName());
            row.setTargetNoteTitle(target == null ? null : target.getTitle());
            row.setAnchor(mapping.getAnchor());
            row.setNickname(mapping.getNickname());
            row.setIsPass(mapping.getIsPass());
            row.setIsMissing(validBind ? NoteConstant.NOT_MISSED_INFO : NoteConstant.MISSED_INFO);
            return row;
        }).toList();
    }

    /**
     * 验证笔记所有权
     * <p>如果笔记不存在会报错</p>
     * <p>如果所有权通过即可返回该笔记</p>
     * @param noteId 笔记 id
     * @param userId 用户 id
     * @return 笔记 -- 返回值不可能为 null
     */
    private NoteEntity validateOwnedNote(Long noteId, Long userId) {
        NoteEntity note = noteMapper.selectById(noteId);
        if (note == null || note.getIsDeleted() != null && note.getIsDeleted().equals(NoteConstant.DELETED)) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }
        if (!note.getUserId().equals(userId)) {
            throw new BaseException(UserConstant.PERMISSION_DENIED);
        }
        return note;
    }

    /**
     * 校验 主题 ID 是否合法
     * <p>会通过 DB 进行校验</p>
     * <p>涉及一次 DB 的网络 IO 操作</p>
     * @param topicId
     */
    private void validateTopic(Long topicId) {
        if (topicId == null) {
            return;
        }
        TopicEntity topic = topicMapper.selectById(topicId);
        if (topic == null) {
            throw new BaseException(TopicConstant.TOPIC_NOT_FOUND);
        }
    }

    /**
     * 读取 MultipartFile 为字符串
     * @param file
     * @return
     */
    private String readMultipartAsString(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BaseException(NoteConstant.NOTE_FILE_READ_ERROR);
        }
    }

    /**
     * 统一文件名
     * <p>去除文件名中的非法字符</p>
     * @param originalFilename
     * @return 文件名
     */
    private String normalizeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BaseException(NoteConstant.NOTE_INVALID_FORMAT);
        }
        return Paths.get(originalFilename).getFileName().toString();
    }

    /**
     * 去掉 markdown 扩展名
     * <p>若是出现扩展名不为 .md 结尾的</p>
     * <p>直接返回，不会做处理</p>
     * <p>方法内不会做校验文件名是否合法</p>
     * @param filename
     * @return
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

    /**
     * 去除重复项并保持顺序。
     * @param values
     * @return
     */
    private List<String> normalizeDistinctList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        // 用 LinkedHashSet 去重并保留原始顺序，便于稳定展示与 Diff 计算。
        Set<String> set = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                set.add(value.trim());
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 构建笔记 diff
     * <p>只会对结果进行处理</p>
     * @param oldTags
     * @param newTags
     * @param oldImages
     * @param newImages
     * @param oldNoteNames
     * @param newNoteNames
     * @return
     */
    private NoteDiffVO buildDiff(List<String> oldTags, List<String> newTags,
            List<String> oldImages, List<String> newImages,
            List<String> oldNoteNames, List<String> newNoteNames) {
        Set<String> oldTagSet = new LinkedHashSet<>(normalizeDistinctList(oldTags));
        Set<String> newTagSet = new LinkedHashSet<>(normalizeDistinctList(newTags));
        Set<String> oldImageSet = new LinkedHashSet<>(normalizeDistinctList(oldImages));
        Set<String> newImageSet = new LinkedHashSet<>(normalizeDistinctList(newImages));
        Set<String> oldNoteSet = new LinkedHashSet<>(normalizeDistinctList(oldNoteNames));
        Set<String> newNoteSet = new LinkedHashSet<>(normalizeDistinctList(newNoteNames));

        // 六个集合差集：新增/移除标签、新增/移除图片、新增/移除双链笔记。
        NoteDiffVO diffVO = new NoteDiffVO();
        diffVO.setAddedTags(new ArrayList<>(difference(newTagSet, oldTagSet)));
        diffVO.setRemovedTags(new ArrayList<>(difference(oldTagSet, newTagSet)));
        diffVO.setAddedImages(new ArrayList<>(difference(newImageSet, oldImageSet)));
        diffVO.setRemovedImages(new ArrayList<>(difference(oldImageSet, newImageSet)));
        diffVO.setAddedNoteNames(new ArrayList<>(difference(newNoteSet, oldNoteSet)));
        diffVO.setRemovedNoteNames(new ArrayList<>(difference(oldNoteSet, newNoteSet)));
        return diffVO;
    }

    /**
     * 集合差集。
     * 
     * @param left
     * @param right
     * @return result = left - right
     */
    private Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    /**
     * 对象转 JSON。
     * 
     * @param value
     * @return
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BaseException("JSON 序列化失败");
        }
    }

    /**
     * JSON 解析成 VO。
     * <p>
     * 解析 笔记-差异 的 JSON 数据
     * </p>
     * 
     * @param diffJson
     * @return
     */
    private NoteDiffVO parseDiff(String diffJson) {
        try {
            return objectMapper.readValue(diffJson, NoteDiffVO.class);
        } catch (JsonProcessingException ex) {
            throw new BaseException("Diff 解析失败");
        }
    }

    /**
     * 转换结果转换成 VO。
     * 这里仅仅只会对结果进行组装
     * 
     * @param converted
     * @return
     */
    private NoteConvertResultVO toConvertResultVO(NoteConvertedEntity converted) {
        NoteConvertResultVO resultVO = new NoteConvertResultVO();
        NoteConvertMetaVO metaVO = new NoteConvertMetaVO();

        // metaVO 从 converted 中拷贝 title、tags、createTimeStr
        metaVO.setTitle(converted.getTitle());
        metaVO.setCreateTime(converted.getCreateTimeStr());
        metaVO.setTags(parseTags(converted.getTagsJson()));

        // resultVO 将 metaVO 封装到自己的属性里面
        resultVO.setMeta(metaVO);

        // resultVO 从 converted 中拷贝 tocHtml、bodyHtml
        resultVO.setTocHtml(converted.getTocHtml());
        resultVO.setBodyHtml(converted.getBodyHtml());

        return resultVO;
    }

    /**
     * 通过笔记 ID 获取图片列表
     * 
     * @param noteId
     * @return
     */
    private @NonNull List<ImageSimpleVO> getImageSimpleVOS(Long noteId) {
        // 批量查询 笔记-图片映射
        List<NoteImageMappingEntity> mappings = noteImageMappingMapper.selectByNoteId(noteId);
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }

        // 提取图片 ID
        List<Long> imageIds = mappings.stream()
                .map(NoteImageMappingEntity::getImageId)
                .filter(id -> id != null)
                .toList();
        // 建立 图片 ID -> 图片 的 map 映射表
        Map<Long, ImageEntity> imageMap = imageIds.isEmpty()
                ? Map.of()
                : imageMapper.selectByIds(new ArrayList<>(imageIds)).stream()
                        .collect(Collectors.toMap(ImageEntity::getId, image -> image, (left, right) -> left));

        // 批量封装图片
        List<ImageSimpleVO> result = new ArrayList<>();
        for (NoteImageMappingEntity mapping : mappings) {
            ImageSimpleVO vo = new ImageSimpleVO();
            vo.setNoteId(noteId);
            vo.setParsedImageName(mapping.getParsedImageName());
            vo.setIsCrossUser(mapping.getIsCrossUser());
            vo.setImageId(mapping.getImageId());

            // 检查这条映射是否有对应的图片
            if (mapping.getImageId() == null) {
                vo.setIsMissing(NoteConstant.MISSED_INFO);
            } else {
                ImageEntity image = imageMap.get(mapping.getImageId());
                if (image != null) {
                    vo.setFilename(image.getFilename());
                    vo.setOssUrl(image.getOssUrl());
                    vo.setIsPublic(image.getIsPublic());
                    vo.setIsPass(image.getIsPass());
                    vo.setCreateTime(image.getUploadTime());
                    vo.setIsMissing(NoteConstant.NOT_MISSED_INFO);
                } else {
                    vo.setIsMissing(NoteConstant.MISSED_INFO);
                }
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 解析标签 JSON。
     * 
     * @param tagsJson
     * @return
     */
    private List<String> parseTags(String tagsJson) {
        if (!StringUtils.hasText(tagsJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new BaseException("标签 JSON 解析失败");
        }
    }

    /**
     * 确保 Long 转换成 long。
     * <p>并且保证 value 不为 null</p>
     * 
     * @param value
     * @return
     */
    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
