package com.jacolp.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.annotation.StorageHandler;
import com.jacolp.constant.AuditConstant;
import com.jacolp.constant.ImageConstant;
import com.jacolp.constant.NoteConstant;
import com.jacolp.constant.TagConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.context.NoteImageResolveContext;
import com.jacolp.context.StorageUpdateContext;
import com.jacolp.converter.MarkdownHtmlEngine;
import com.jacolp.converter.MarkdownHtmlEngine.FrontMatter;
import com.jacolp.converter.MarkdownHtmlEngine.HtmlProcessResult;
import com.jacolp.enums.NoteStatus;
import com.jacolp.enums.NoteMissingInfoMask;
import com.jacolp.enums.StorageOperationType;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.NoteChangeDiffMapper;
import com.jacolp.mapper.NoteContextMapper;
import com.jacolp.mapper.NoteConvertMapper;
import com.jacolp.mapper.NoteEachMappingMapper;
import com.jacolp.mapper.NoteImageMappingMapper;
import com.jacolp.mapper.NoteMapper;
import com.jacolp.mapper.NoteTagMappingMapper;
import com.jacolp.pojo.dto.note.*;
import com.jacolp.pojo.dto.tag.TagNoteCountDTO;
import com.jacolp.pojo.dto.user.UserQuoteStorageDTO;
import com.jacolp.pojo.dto.image.ImageMappingBindDTO;
import com.jacolp.pojo.dto.tag.TagMappingBindDTO;
import com.jacolp.pojo.entity.ImageEntity;
import com.jacolp.pojo.entity.NoteChangeDiffEntity;
import com.jacolp.pojo.entity.NoteContextEntity;
import com.jacolp.pojo.entity.NoteConvertedEntity;
import com.jacolp.pojo.entity.NoteAuditRecordEntity;
import com.jacolp.pojo.entity.NoteEntity;
import com.jacolp.pojo.entity.NoteEachMappingEntity;
import com.jacolp.pojo.entity.NoteImageMappingEntity;
import com.jacolp.pojo.entity.NoteTagMappingEntity;
import com.jacolp.pojo.entity.TagEntity;
import com.jacolp.pojo.vo.image.ImageSimpleVO;
import com.jacolp.pojo.vo.note.NoteChangeDiffVO;
import com.jacolp.pojo.vo.note.NoteCheckBindingVO;
import com.jacolp.pojo.vo.note.NoteConvertMetaVO;
import com.jacolp.pojo.vo.note.NoteConvertResultVO;
import com.jacolp.pojo.vo.note.NoteDetailVO;
import com.jacolp.pojo.vo.note.NoteDiffVO;
import com.jacolp.pojo.vo.note.NoteEachSimpleVO;
import com.jacolp.pojo.vo.note.NoteEachMappingRowVO;
import com.jacolp.pojo.vo.note.NoteImageMappingRowVO;
import com.jacolp.pojo.vo.note.NoteModifyDiffDetailVO;
import com.jacolp.pojo.vo.note.NoteRelationDetailVO;
import com.jacolp.pojo.vo.note.NoteSimpleVO;
import com.jacolp.pojo.vo.note.NoteTagMappingRowVO;
import com.jacolp.pojo.vo.note.NoteStatsVO;
import com.jacolp.pojo.vo.note.NoteUploadVO;
import com.jacolp.pojo.vo.note.NoteVO;
import com.jacolp.pojo.vo.note.UserNoteDetailVO;
import com.jacolp.result.PageResult;
import com.jacolp.service.AuditService;
import com.jacolp.service.ImageService;
import com.jacolp.service.NoteServiceOld;
import com.jacolp.service.TagService;
import com.jacolp.service.TopicService;
import com.jacolp.service.AdminUserService;
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
public class NoteServiceImplOld implements NoteServiceOld {

    // ==== 笔记模块的 Mapper ====
    @Autowired private NoteMapper noteMapper;
    @Autowired private NoteConvertMapper noteConvertMapper;
    @Autowired private NoteChangeDiffMapper noteChangeDiffMapper;
    @Autowired private NoteContextMapper noteContextMapper;
    @Autowired private NoteEachMappingMapper noteEachMappingMapper;
    @Autowired private NoteTagMappingMapper noteTagMappingMapper;
    @Autowired private NoteImageMappingMapper noteImageMappingMapper;

    // ==== 来自其他模块的 Service ====
    @Autowired private TopicService topicService;
    @Autowired private TagService tagService;
    @Autowired private ImageService imageService;
    @Autowired private AuditService auditService;
    @Autowired private AdminUserService adminUserService;

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
    @StorageHandler(operationType = StorageOperationType.UPLOAD)
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
        MarkdownHtmlEngine.NoteRelationInfo scanResult = MarkdownHtmlEngine.scanNoteReletionInfo(rawMarkdown);
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
        // 这里使用批量查询计算缺失关联信息，避免逐条查询导致 O(n) 次网络 IO。
        vo.setMissingTags(tags);
        vo.setMissingImages(imageNames);
        vo.setMissingNoteNames(noteLinks.stream().map(MarkdownHtmlEngine.ParsedNoteLink::noteName).toList());
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
    @StorageHandler(operationType = StorageOperationType.MODIFY)
    public NoteDiffVO modifyNoteSource(Long noteId, MultipartFile file) {
        // 获取当前用户ID
        Long userId = BaseContext.getCurrentId();
        NoteEntity existed = validateOwnedNote(noteId, userId); // 验证笔记所有权

        // 格式化文件名
        normalizeFilename(file.getOriginalFilename());

        // 检查该笔记是否有待确认的diff数据行
        if (noteChangeDiffMapper.countByNoteIdAndStatus(noteId, NoteConstant.NOTE_DIFF_STATUS_PENDING) != 0) {
            throw new BaseException(NoteConstant.NOTE_DIFF_EXIST);
        }

        // 从数据库读取旧内容
        NoteContextEntity oldContext = noteContextMapper.selectByNoteId(noteId);
        if (oldContext == null) {
            throw new BaseException(NoteConstant.NOTE_CONTENT_NOT_FOUND);
        }
        String oldMarkdown = oldContext.getMarkdownContent(); // 旧内容
        String newMarkdown = readMultipartAsString(file); // 新内容

        // 扫描新旧内容的标签和图片，计算 Diff
        MarkdownHtmlEngine.NoteRelationInfo oldScan = MarkdownHtmlEngine.scanNoteReletionInfo(oldMarkdown);
        MarkdownHtmlEngine.NoteRelationInfo newScan = MarkdownHtmlEngine.scanNoteReletionInfo(newMarkdown);
        NoteDiffVO diffVO = buildDiff(
                oldScan.tags(), newScan.tags(),
                oldScan.imageNames(), newScan.imageNames(),
                oldScan.reflection(), newScan.reflection());

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
        diffEntity.setScanJson(writeJson(newScan)); // 保存新文本扫描结果，避免 confirmChange 二次扫描
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
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteChangeDiffVO confirmChange(Long noteId, NoteChangeConfirmDTO dto) {
        // 校验请求参数
        if (dto == null || dto.getConfirm() == null) {
            throw new BaseException("确认参数不能为空");
        }

        // 获取当前用户ID
        Long userId = BaseContext.getCurrentId();
        NoteEntity existed = validateOwnedNote(noteId, userId);

        // 查询变更记录
        NoteChangeDiffEntity diffEntity = noteChangeDiffMapper.selectByNoteId(noteId);
        if (diffEntity == null) {
            throw new BaseException(NoteConstant.NOTE_CHANGE_DIFF_NOT_FOUND);
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

            // 从 modifyNoteSource 时保存的 scanJson 中读取扫描结果，避免二次扫描 Markdown 文本
            MarkdownHtmlEngine.NoteRelationInfo scan = parseScanJson(diffEntity.getScanJson());
            List<String> currentTags = normalizeDistinctList(scan.tags());
            List<String> currentImages = normalizeDistinctList(scan.imageNames());
            List<MarkdownHtmlEngine.ParsedNoteLink> currentNoteLinks = List.copyOf(scan.noteLinks());

            // 用新内容覆盖旧内容，清除新版本。
            contextEntity.setMarkdownContent(newMarkdown);  // 用新内容覆盖旧内容
            contextEntity.setMarkdownContentNew(null);  // 清除新版本
            noteContextMapper.updateContext(contextEntity); // 更新到 DB

            // 更新标签、图片映射表（采取先全部删除，然后又全部插入新的策略）
            noteTagMappingMapper.softDeleteByNoteId(noteId);
            noteImageMappingMapper.softDeleteByNoteId(noteId);
            noteEachMappingMapper.softDeleteBySourceNoteId(noteId);

            // 建立新的映射记录
            persistRelationMappings(userId, noteId, existed.getTitle(), currentTags, currentImages, currentNoteLinks);

            // 计算大小差异
            long baseSize = safeLong(existed.getMdFileSize());
            long deltaSize = safeLong(diffEntity.getNewFileSize()) - safeLong(diffEntity.getOldFileSize());
            existed.setMdFileSize(Math.max(0L, baseSize + deltaSize));

            // 修改笔记后状态回到 NEW
            existed.setStatus(NoteStatus.NEW.getCode());

            // 更新笔记到 DB
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
        BeanUtils.copyProperties(diffEntity, diffVO);
        diffVO.setNoteId(noteId);
        diffVO.setDiffFileSize(safeLong(diffEntity.getNewFileSize()) - safeLong(diffEntity.getOldFileSize()));
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

        // 校验是否已准备好所有的关联信息
        NoteStatus noteStatus = NoteStatus.fromCode(note.getStatus());
        if (noteStatus == NoteStatus.PENDING_INFO
                || (note.getMissingCount() != null && note.getMissingCount() > 0)) {
            throw new BaseException(NoteConstant.NOTE_MISSING_INFO);
        }

        // 从数据库读取笔记内容
        NoteContextEntity context = noteContextMapper.selectByNoteId(noteId);
        if (context == null) {
            throw new BaseException(NoteConstant.NOTE_CONTENT_NOT_FOUND);
        }
        String rawMarkdown = context.getMarkdownContent();  // 笔记源内容
        String fallbackTitle = stripMarkdownExtension(note.getTitle()); // 备用标题

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
            noteConvertMapper.upsertConverted(converted);   // 如果笔记存在转换信息行，则更新

            // 检查转换信息中是否存在标题，则使用新扫描出来的标题
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
     * <p>不做笔记归属权的校验，直接删除笔记转换结果</p>
     * @param noteId 笔记 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDeleteConverted(Long noteId) {
        noteConvertMapper.deleteByNoteId(noteId); // 删除转换结果
        noteMapper.updateStatus(noteId, NoteStatus.READY_TO_CONVERT.getCode()); // 下架：PUBLISHED → REDY_TO_CONVERT
    }

    /**
     * 发布笔记 -- (管理员)
     * <p>不做笔记归属权的校验，直接发布笔记</p>
     * <p>但是会校验笔记是否做了转换</p>
     * @param noteId 笔记 ID
     */
    @Override
    public void publishNote(Long noteId) {
        // 验证笔记是否已转换
        if (noteConvertMapper.countByNoteId(noteId) <= 0) {
            throw new BaseException(NoteConstant.NOTE_NOT_CONVERTED);
        }

        // 检查是否处于可发布的状态
        NoteStatus noteStatus = NoteStatus.fromCode(noteMapper.selectStatusById(noteId));
        if (!noteStatus.canTransitionTo(NoteStatus.PUBLISHED)) {
            log.error("Invalid status transition: {} → {}", noteStatus, NoteStatus.PUBLISHED);
            throw new BaseException("不合法的状态转换");
        }

        // 更新笔记为发布状态
        int count = noteMapper.updateStatus(noteId, NoteStatus.PUBLISHED.getCode());
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
    @StorageHandler(operationType = StorageOperationType.BATCH_DELETE)
    public void adminDeleteNotes(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BaseException("待删除的笔记 ID 列表不能为空");
        }

        List<NoteEntity> notes = noteMapper.selectByIds(ids);
        if (notes.size() != ids.size()) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        // 校验状态：PENDING_AUDIT 和 PUBLISHED 状态不能删除
        for (NoteEntity note : notes) {
            NoteStatus status = NoteStatus.fromCode(note.getStatus());
            if (status == NoteStatus.PENDING_AUDIT) {
                throw new BaseException("笔记【" + note.getTitle() + "】正在审核中，不能删除");
            }
            if (status == NoteStatus.PUBLISHED) {
                throw new BaseException("笔记【" + note.getTitle() + "】已公开，请先下架后再删除");
            }
        }

        // Map<UserId, Storage>
        Map<Long, Long> userStorageMap = new LinkedHashMap<>();
        for (NoteEntity note : notes) {
            userStorageMap.merge(note.getUserId(), safeLong(note.getMdFileSize()), Long::sum);
        }

        // 批量删除关联数据，
        noteConvertMapper.deleteByNoteIds(ids);
        noteChangeDiffMapper.deleteByNoteIds(ids);
        noteContextMapper.deleteByNoteIds(ids);
        noteEachMappingMapper.softDeleteBySourceNoteIds(ids);
        noteTagMappingMapper.softDeleteByNoteIds(ids);
        noteImageMappingMapper.softDeleteByNoteIds(ids);

        // 使用新方法更新状态为 DELETED
        noteMapper.updateStatusByIds(ids, NoteStatus.DELETED.getCode());

        // 需要将 Map 记录到上下文方便切面类来处理
        StorageUpdateContext.setStorageMap(userStorageMap);
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

        if (isVisible != null && isVisible.equals(NoteStatus.PUBLISHED.getCode())) {
            // 如果传入的设置可见参数不为 null 且 isVisible 为 1
            if (noteConvertMapper.countByNoteId(dto.getId()) <= 0) {
                throw new BaseException(NoteConstant.NOTE_NOT_CONVERTED);
            }
            count = noteMapper.updateStatus(dto.getId(), NoteStatus.PUBLISHED.getCode());
        } else {
            count = noteMapper.updateStatus(dto.getId(), NoteStatus.APPROVED.getCode());
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
        NoteEntity note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        NoteStatus currentStatus = NoteStatus.fromCode(note.getStatus());

        NoteStatus targetStatus;
        if (status != null && status.equals(NoteStatus.PUBLISHED.getCode())) {
            // 发布：APPROVED → PUBLISHED
            targetStatus = NoteStatus.PUBLISHED;

            // 检查笔记是否已转换
            if (noteConvertMapper.countByNoteId(noteId) <= 0) {
                throw new BaseException(NoteConstant.NOTE_NOT_CONVERTED);
            }

            // 校验笔记本身是否通过
            if (!currentStatus.isApproved() && !currentStatus.isPublished()) {
                throw new BaseException(NoteConstant.NOTE_NOT_PASS);
            }

            // 对通过性做校验
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
        } else {
            // 下架：PUBLISHED → APPROVED
            targetStatus = NoteStatus.APPROVED;
        }

        // 状态转换校验
        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new BaseException(String.format(
                "无法从 %s 状态转换到 %s 状态",
                currentStatus.getDesc(),
                targetStatus.getDesc()
            ));
        }

        int count = noteMapper.updateStatus(noteId, targetStatus.getCode());
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

        // TODO 加入状态校验（如果是处于审核中、已发布的笔记无法修改，似乎状态机里面就的转换检查方法就可以检查）(*)

        // 修改笔记后状态回到 PENDING_AUDIT 待审核
        note.setStatus(NoteStatus.PENDING_AUDIT.getCode());

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

        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());

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
    public NoteDetailVO adminGetInfo(Long noteId) {
        NoteEntity note = noteMapper.selectById(noteId);

        if (NoteStatus.fromCode(note.getStatus()).isDeleted()) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        // 封装基本的信息
        NoteVO noteVO = new NoteVO();
        BeanUtils.copyProperties(note, noteVO);
        // TODO 如果后续在笔记数据行中冗余了 topic_name 就可以不做这一次查询了(*)
        if (note.getTopicId() != null) {
            noteVO.setTopicName(topicService.getTopicById(note.getTopicId()).getTopicName());
        }


        NoteDetailVO detailVO = new NoteDetailVO();
        BeanUtils.copyProperties(noteVO, detailVO);

        // 获取标签
        List<Long> tagIds = noteTagMappingMapper.selectTagIdsByNoteId(noteId);
        detailVO.setTags(tagIds == null || tagIds.isEmpty()
                ? List.of()
                : tagService.getByIds(tagIds).stream().map(TagEntity::getTagName).toList());

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
                        .filter(n -> n.getStatus() == null || !NoteStatus.fromCode(n.getStatus()).isDeleted())
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
    // TODO 上提到 NoteFacade，然后通过新的 Mask 位运算和 Count 计数来进行更新(*)
    public void bindTagMapping(TagMappingBindDTO dto) {
        // 1) 基础参数校验：映射行ID + 标签ID。
        if (dto == null || dto.getMappingId() == null || dto.getTagId() == null) {
            throw new BaseException("映射ID和标签ID不能为空");
        }
        Long userId = BaseContext.getCurrentId();

        // 2) 校验映射行归属与目标标签存在性。
        NoteTagMappingEntity mapping = requireOwnedTagMapping(dto.getMappingId(), userId);
        TagEntity targetTag = tagService.getByIdAndUserId(dto.getTagId(), userId);
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
    // TODO 上提到 NoteFacade，然后通过新的 Mask 位运算和 Count 计数来进行更新(*)
    public void unbindTagMapping(Long mappingId) {
        Long userId = BaseContext.getCurrentId();
        // 先校验归属，再解绑，最后重算 is_missing_info。
        NoteTagMappingEntity mapping = requireOwnedTagMapping(mappingId, userId);
        noteTagMappingMapper.unbindTagById(mappingId);
        refreshNoteMissingInfo(mapping.getNoteId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // TODO 上提到 NoteFacade，然后通过新的 Mask 位运算和 Count 计数来进行更新(*)
    public void bindImageMapping(ImageMappingBindDTO dto) {
        // 1) 基础参数校验：映射行ID + 图片ID。
        if (dto == null || dto.getMappingId() == null || dto.getImageId() == null) {
            throw new BaseException("映射ID和图片ID不能为空");
        }
        Long userId = BaseContext.getCurrentId();

        // 2) 校验映射归属和目标图片存在。
        NoteImageMappingEntity mapping = requireOwnedImageMapping(dto.getMappingId(), userId);
        ImageEntity targetImage = imageService.getById(dto.getImageId());
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
    // TODO 上提到 NoteFacade，然后通过新的 Mask 位运算和 Count 计数来进行更新(*)
    public void unbindImageMapping(Long mappingId) {    // TODO 上提到 NoteFacade，然后通过新的 Mask 位运算和 Count 计数来进行更新(*)
        Long userId = BaseContext.getCurrentId();
        // 先做归属校验，解绑后再统一重算缺失状态。
        NoteImageMappingEntity mapping = requireOwnedImageMapping(mappingId, userId);
        noteImageMappingMapper.unbindImageById(mappingId);
        refreshNoteMissingInfo(mapping.getNoteId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // TODO 上提到 NoteFacade，然后通过新的 Mask 位运算和 Count 计数来进行更新(*)
    public void bindEachMapping(EachMappingBindDTO dto) {
        // 1) 基础参数校验：映射行ID + 目标笔记ID。
        if (dto == null || dto.getMappingId() == null || dto.getNoteId() == null) {
            throw new BaseException("映射ID和笔记ID不能为空");
        }
        Long userId = BaseContext.getCurrentId();

        // 2) 校验映射归属与目标笔记存在性。
        NoteEachMappingEntity mapping = requireOwnedEachMapping(dto.getMappingId(), userId);
        NoteEntity targetNote = noteMapper.selectById(dto.getNoteId());
        if (targetNote == null || NoteStatus.fromCode(targetNote.getStatus()).isDeleted()) {
            throw new BaseException("目标笔记不存在");
        }

        // 3) 以标题与 parsed_note_name 对比，nickname 仅用于显示不参与绑定。
        if (!Objects.equals(mapping.getParsedNoteName(), targetNote.getTitle())) {
            throw new BaseException("笔记标题与映射行解析名称不一致，无法绑定");
        }
        NoteStatus targetStatus = NoteStatus.fromCode(targetNote.getStatus());
        if (!targetStatus.isApproved() && !targetStatus.isPublished()) {
            throw new BaseException("目标笔记未通过审核，无法绑定");
        }

        // 4) 执行绑定并刷新来源笔记缺失状态。
//        noteEachMappingMapper.bindNoteBySourceIdAndParseName(mapping.getId(), targetNote.getId(), AuditConstant.PASS);
        refreshNoteMissingInfo(mapping.getSourceNoteId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // TODO 上提到 NoteFacade，然后通过新的 Mask 位运算和 Count 计数来进行更新(*)
    public void unbindEachMapping(Long mappingId) { // TODO 上提到 NoteFacade，然后通过新的 Mask 位运算和 Count 计数来进行更新(*)
        Long userId = BaseContext.getCurrentId();
        // 解绑后也需要按最新映射重新计算完整性。
        NoteEachMappingEntity mapping = requireOwnedEachMapping(mappingId, userId);
        noteEachMappingMapper.unbindNoteById(mappingId);
        refreshNoteMissingInfo(mapping.getSourceNoteId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // TODO 上提到 NoteFacade，然后通过新的 Mask 位运算和 Count 计数来进行更新（这里保留强制触发扫描，这个是有兜底作用的）(*)
    public NoteCheckBindingVO checkRelationCompletion(Long noteId) {
        // 只允许笔记所有者触发完整性校验。
        Long userId = BaseContext.getCurrentId();
        NoteEntity note = validateOwnedNote(noteId, userId);

        NoteCheckBindingVO result = new NoteCheckBindingVO();
        result.setNoteId(noteId);

        // 获取笔记状态
        NoteStatus currentStatus = NoteStatus.fromCode(note.getStatus());

        // 批量补绑定”现在已存在且可绑定”的资源
        syncBindableMappings(noteId, userId, note.getTopicId());

        // 计算缺失信息掩码和数量
        int missingMask = calculateMissingMaskFromRelations(noteId);
        int missingCount = countInitMissingBits(missingMask);

        // 获取缺失列表
        List<String> missingTags = getMissingTagNames(noteId);
        List<String> missingImages = getMissingImageNames(noteId);
        List<String> missingNoteNames = getMissingEachNoteNames(noteId);

        // 检查是否信息完整
        boolean isComplete = missingCount == 0;

        // 确定最终状态
        NoteStatus targetStatus = currentStatus;
        if (currentStatus == NoteStatus.NEW) {
            targetStatus = NoteStatus.PENDING_INFO;
        } else if (isComplete && currentStatus == NoteStatus.PENDING_INFO) {
            targetStatus = NoteStatus.READY_TO_CONVERT;
        }

        // 一次性更新所有字段
        noteMapper.updateNoteFieldsForCheck(noteId, targetStatus.getCode(), missingMask, missingCount);

        // 构建返回结果
        result.setStatus(targetStatus.getCode());
        result.setStatusDesc(targetStatus.getDesc());
        result.setComplete(isComplete);
        result.setMissingTags(missingTags);
        result.setMissingImages(missingImages);
        result.setMissingNoteNames(missingNoteNames);

        return result;
    }

    /**
     * 用户端发起笔记审核申请。
     */
    // TODO 这里先行提取到 NoteFacade 中（需要完全重构还得配合 AuditService 重构）
    @Override
    public void submitNoteAudit(Long noteId) {
        Long userId = BaseContext.getCurrentId();
        if (noteId == null || noteId <= 0) {
            throw new BaseException(NoteConstant.NOTE_ID_INVALID);
        }

        NoteEntity note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }
        if (!note.getUserId().equals(userId)) {
            throw new BaseException(NoteConstant.NOTE_NOT_OWNER);
        }
        NoteStatus status = NoteStatus.fromCode(note.getStatus());
        if (status.isApproved() || status.isPublished()) {
            throw new BaseException(NoteConstant.NOTE_ALREADY_PASSED);
        }
        if (auditService.hasPendingNoteAudit(noteId)) {
            throw new BaseException(NoteConstant.NOTE_AUDIT_PENDING);
        }

        NoteAuditRecordEntity record = new NoteAuditRecordEntity();
        record.setApplicantUserId(userId);
        record.setNoteId(noteId);
        auditService.createNoteAuditRecord(record);
    }

    /**
     * 获取当前用户笔记统计。
     */
    @Override
    public NoteStatsVO getUserNoteStats() {
        Long userId = BaseContext.getCurrentId();
        // TODO 笔记不好走 索引下推 后续再想一想怎么优化
        long noteTotalCount = noteMapper.countByUserId(userId);
        long publicNoteCount = noteMapper.countPublicByUserId(userId);
        long approvedNoteCount = noteMapper.countApprovedByUserId(userId);
        return new NoteStatsVO(noteTotalCount, publicNoteCount, approvedNoteCount);
    }

    // ===== 用户端方法 =====

    /**
     * 用户端条件查询：当前用户自己的笔记 + 别人已发布的笔记。
     */
    @Override
    public PageResult listUserNotes(UserNoteQueryDTO dto) {
        if (dto == null) {
            dto = new UserNoteQueryDTO();
        }

        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());

        String title = (dto.getTitle() != null && !dto.getTitle().trim().isEmpty()) ? dto.getTitle().trim() : null;
        List<NoteVO> records = noteMapper.listByUserCondition(BaseContext.getCurrentId(), dto.getTopicId(), title);
        PageInfo<NoteVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // TODO 删掉，使用 upload 即可，校验配额使用 @StorageHandler 注解的切面类即可
    public Long createUserNote(MultipartFile file, Long topicId) {
        Long userId = BaseContext.getCurrentId();

        String originalFilename = normalizeFilename(file.getOriginalFilename());
        if (!originalFilename.toLowerCase().endsWith(NoteConstant.ALLOWED_NOTE_FORMAT)) {
            throw new BaseException(NoteConstant.NOTE_INVALID_FORMAT);
        }

        validateTopic(topicId);

        UserQuoteStorageDTO storageInfo = adminUserService.getUserQuoteStorage(userId);
        if (storageInfo != null && storageInfo.getMaxStorageBytes() != null) {
            Long maxStorageBytes = storageInfo.getMaxStorageBytes();
            Long usedStorageBytes = storageInfo.getUsedStorageBytes();
            if (usedStorageBytes != null && maxStorageBytes < usedStorageBytes + file.getSize()) {
                throw new BaseException("存储配额不足");
            }
        }

        String markdownContent;
        try {
            markdownContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BaseException(NoteConstant.NOTE_FILE_READ_ERROR);
        }

        String title = stripMarkdownExtension(originalFilename);
        if (noteMapper.countByUserIdAndTopicIdAndTitle(userId, topicId, title) != 0) {
            throw new BaseException("已在对应主题下存在同名笔记");
        }

        NoteEntity note = new NoteEntity();
        note.setUserId(userId);
        note.setTopicId(topicId);
        note.setTitle(title);
        note.setDescription(null);
        note.setStorageType(NoteConstant.DEFAULT_STORAGE_TYPE);
        note.setStatus(NoteStatus.NEW.getCode());
        note.setMissingInfoMask(0);
        note.setMissingCount(0);
        note.setMdFileSize(file.getSize());
        note.setCreateTime(LocalDateTime.now());
        note.setUpdateTime(LocalDateTime.now());

        int count = noteMapper.insertNote(note);
        if (count <= 0) {
            throw new BaseException("创建笔记失败");
        }

        NoteContextEntity context = new NoteContextEntity();
        context.setNoteId(note.getId());
        context.setMarkdownContent(markdownContent);
        noteContextMapper.insertContext(context);

        if (storageInfo != null && storageInfo.getUsedStorageBytes() != null) {
            adminUserService.updateUserStorageUsed(userId, storageInfo.getUsedStorageBytes() + file.getSize());
        }

        return note.getId();
    }

    @Override
    public PageResult listUserNotesBySearch(UserNoteSearchDTO dto) {
        Long userId = BaseContext.getCurrentId();

        int pageNum = dto.getPageNum() == null || dto.getPageNum() <= 0 ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() <= 0 ? 10 : dto.getPageSize();
        PageHelper.startPage(pageNum, pageSize);

        List<NoteVO> records = noteMapper.listByUserCondition(userId, dto.getTopicId(), dto.getKeyword());
        PageInfo<NoteVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    public UserNoteDetailVO getUserNoteDetail(UserNoteDetailDTO dto) {
        Long userId = BaseContext.getCurrentId();

        NoteEntity note = noteMapper.selectById(dto.getId());
        if (note == null || NoteStatus.fromCode(note.getStatus()).isDeleted()) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        if (!note.getUserId().equals(userId)) {
            throw new BaseException("只能查看自己的笔记");
        }

        NoteContextEntity context = noteContextMapper.selectByNoteId(dto.getId());
        if (context == null) {
            throw new BaseException(NoteConstant.NOTE_CONTENT_NOT_FOUND);
        }

        NoteConvertedEntity converted = noteConvertMapper.selectByNoteId(dto.getId());

        List<Long> tagIds = noteTagMappingMapper.selectTagIdsByNoteId(dto.getId());
        List<String> tags = List.of();
        if (tagIds != null && !tagIds.isEmpty()) {
            List<TagEntity> tagEntities = tagService.getByIds(tagIds);
            tags = tagEntities.stream().map(TagEntity::getTagName).collect(Collectors.toList());
        }

        UserNoteDetailVO vo = new UserNoteDetailVO();
        BeanUtils.copyProperties(note, vo);
        vo.setMarkdownContent(context.getMarkdownContent());
        vo.setHtmlContent(converted != null ? converted.getBodyHtml() : null);
        vo.setTags(tags);
        return vo;
    }

    @Override
    public String getUserNoteSource(Long noteId) {
        Long userId = BaseContext.getCurrentId();

        NoteEntity note = noteMapper.selectById(noteId);
        if (note == null || NoteStatus.fromCode(note.getStatus()).isDeleted()) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        if (!note.getUserId().equals(userId)) {
            throw new BaseException("只能查看自己的笔记");
        }

        NoteContextEntity context = noteContextMapper.selectByNoteId(noteId);
        if (context == null) {
            throw new BaseException(NoteConstant.NOTE_CONTENT_NOT_FOUND);
        }

        return context.getMarkdownContent();
    }

    @Override
    public NoteConvertResultVO getUserNoteConvertedHtml(Long noteId) {
        Long userId = BaseContext.getCurrentId();

        NoteEntity note = noteMapper.selectById(noteId);
        if (note == null || NoteStatus.fromCode(note.getStatus()).isDeleted()) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        if (!note.getUserId().equals(userId)) {
            throw new BaseException(NoteConstant.NOTE_NOT_OWNER);
        }

        NoteConvertedEntity converted = noteConvertMapper.selectByNoteId(noteId);
        if (converted == null) {
            throw new BaseException(NoteConstant.NOTE_NOT_CONVERTED);
        }

        List<Long> tagIds = noteTagMappingMapper.selectTagIdsByNoteId(noteId);
        List<String> tags = List.of();
        if (tagIds != null && !tagIds.isEmpty()) {
            List<TagEntity> tagEntities = tagService.getByIds(tagIds);
            tags = tagEntities.stream().map(TagEntity::getTagName).collect(Collectors.toList());
        }

        NoteConvertMetaVO meta = new NoteConvertMetaVO();
        meta.setTitle(note.getTitle());
        meta.setTags(tags);
        meta.setCreateTime(note.getCreateTime().toString());

        NoteConvertResultVO vo = new NoteConvertResultVO();
        vo.setMeta(meta);
        vo.setTocHtml(converted.getTocHtml());
        vo.setBodyHtml(converted.getBodyHtml());

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserNote(MultipartFile file, UserNoteUpdateDTO dto) {
        Long userId = BaseContext.getCurrentId();

        NoteEntity note = noteMapper.selectById(dto.getId());
        if (note == null || NoteStatus.fromCode(note.getStatus()).isDeleted()) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        if (!note.getUserId().equals(userId)) {
            throw new BaseException("只能修改自己的笔记");
        }

        if (file != null && !file.isEmpty()) {
            String originalFilename = normalizeFilename(file.getOriginalFilename());
            if (!originalFilename.toLowerCase().endsWith(NoteConstant.ALLOWED_NOTE_FORMAT)) {
                throw new BaseException(NoteConstant.NOTE_INVALID_FORMAT);
            }

            UserQuoteStorageDTO storageInfo = adminUserService.getUserQuoteStorage(userId);
            if (storageInfo != null && storageInfo.getMaxStorageBytes() != null) {
                Long maxStorageBytes = storageInfo.getMaxStorageBytes();
                Long usedStorageBytes = storageInfo.getUsedStorageBytes();
                long sizeDiff = file.getSize() - note.getMdFileSize();
                if (usedStorageBytes != null && maxStorageBytes < usedStorageBytes + sizeDiff) {
                    throw new BaseException("存储配额不足");
                }
            }

            String markdownContent;
            try {
                markdownContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new BaseException(NoteConstant.NOTE_FILE_READ_ERROR);
            }

            NoteContextEntity context = noteContextMapper.selectByNoteId(dto.getId());
            if (context == null) {
                throw new BaseException(NoteConstant.NOTE_CONTENT_NOT_FOUND);
            }
            context.setMarkdownContent(markdownContent);
            noteContextMapper.updateContext(context);

            note.setMdFileSize(file.getSize());
            note.setStatus(NoteStatus.NEW.getCode());
            noteConvertMapper.deleteByNoteId(dto.getId());
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
    public void deleteUserNote(Long id) {
        Long userId = BaseContext.getCurrentId();

        NoteEntity note = noteMapper.selectById(id);
        if (note == null || NoteStatus.fromCode(note.getStatus()).isDeleted()) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        if (!note.getUserId().equals(userId)) {
            throw new BaseException("只能删除自己的笔记");
        }

        NoteStatus currentStatus = NoteStatus.fromCode(note.getStatus());
        if (currentStatus == NoteStatus.PENDING_AUDIT) {
            throw new BaseException("笔记正在审核中，不能删除");
        }
        if (currentStatus == NoteStatus.PUBLISHED) {
            throw new BaseException("笔记已公开，请先下架后再删除");
        }

        List<TagNoteCountDTO> tagChecks = tagService.listDeleteChecksByIds(userId, List.of(id));
        for (TagNoteCountDTO check : tagChecks) {
            if (check.getNoteCount() != null && check.getNoteCount() > 0) {
                throw new BaseException("该笔记正在被标签引用，无法删除");
            }
        }

        noteConvertMapper.deleteByNoteId(id);
        noteContextMapper.deleteByNoteId(id);
        noteEachMappingMapper.softDeleteBySourceNoteId(id);
        noteTagMappingMapper.softDeleteByNoteId(id);
        noteImageMappingMapper.softDeleteByNoteId(id);

        int count = noteMapper.updateStatus(id, NoteStatus.DELETED.getCode());
        if (count <= 0) {
            throw new BaseException("删除笔记失败");
        }

        UserQuoteStorageDTO storageInfo = adminUserService.getUserQuoteStorage(userId);
        if (storageInfo != null && storageInfo.getUsedStorageBytes() != null) {
            adminUserService.updateUserStorageUsed(userId, Math.max(0L, storageInfo.getUsedStorageBytes() - note.getMdFileSize()));
        }
    }

    @Override
    public PageResult searchUserNotes(UserNoteSearchDTO dto) {
        Long userId = BaseContext.getCurrentId();

        if (!StringUtils.hasText(dto.getKeyword())) {
            throw new BaseException("搜索关键词不能为空");
        }

        int pageNum = dto.getPageNum() == null || dto.getPageNum() <= 0 ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() <= 0 ? 10 : dto.getPageSize();
        PageHelper.startPage(pageNum, pageSize);

        String keyword = dto.getKeyword().trim();
        List<NoteVO> records = noteMapper.listByUserCondition(userId, dto.getTopicId(), keyword);
        PageInfo<NoteVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    @Override
    public NoteEntity getNoteEntityById(Long id) {
        return noteMapper.selectById(id);
    }

    @Override
    public List<NoteSimpleVO> listNoteSimplesByImageId(Long imageId) {
        return noteMapper.selectNoteSimpleByImageId(imageId);
    }

    @Override
    public int updateStatusByIds(List<Long> ids, Short status) {
        return noteMapper.updateStatusByIds(ids, status);
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

        List<TagEntity> tags = Optional.ofNullable(tagService.getByNamesAndUserId(parsedNames, userId))
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
                imageService.getByUserIdAndTopicIdAndFilenames(userId, topicId, parsedNames))
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
            if (target == null) {
                continue;
            }
            NoteStatus targetNoteStatus = NoteStatus.fromCode(target.getStatus());
            if (!targetNoteStatus.isApproved() && !targetNoteStatus.isPublished()) {
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
     * <p>上传后默认为 NEW 状态，不进行数据库查询判断是否有缺失信息</p>
     * <p>直接根据扫描结果设置 missingInfoMask 和 missingCount</p>
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
        note.setStorageType(NoteConstant.DEFAULT_STORAGE_TYPE);
        note.setMdFileSize(fileSize);

        // 计算缺失信息掩码和数量
        int missingMask = calculateInitMissingInfoMask(tags, imageNames, noteLinks);
        int missingCount = countInitMissingBits(missingMask);

        // 统一设置为 NEW 状态
        note.setStatus(NoteStatus.NEW.getCode());
        note.setMissingInfoMask(missingMask);
        note.setMissingCount(missingCount);

        if (noteMapper.insertNote(note) <= 0) {
            throw new BaseException(NoteConstant.NOTE_UPDATE_FAILED);
        }
        return note.getId();
    }

    /**
     * 计算缺失信息掩码
     * <p>- 有关联就算是缺失</p>
     */
    private int calculateInitMissingInfoMask(List<String> tags, List<String> imageNames, List<MarkdownHtmlEngine.ParsedNoteLink> noteLinks) {
        int missingMask = 0;
        if (tags != null && !tags.isEmpty()) {
            missingMask |= NoteConstant.MISSING_TAG;
        }
        if (imageNames != null && !imageNames.isEmpty()) {
            missingMask |= NoteConstant.MISSING_IMAGE;
        }
        if (noteLinks != null && !noteLinks.isEmpty()) {
            missingMask |= NoteConstant.MISSING_NOTE;
        }
        return missingMask;
    }

    /**
     * 批量建立 note-image 映射关系
     * <p>直接插入映射行，不进行自动绑定（image_id 为 null）</p>
     * <p>如果不存在，则插入一条新的数据行</p>
     */
    private void persistRelationMappings(Long userId, Long noteId, String noteTitle,
                                         List<String> tags, List<String> imageNames) {
        // 建立标签映射（不创建标签，直接插入映射行）
        List<NoteTagMappingEntity> tagMappings = buildTagMappings(noteId, tags);
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
     * <p>这里不会进行自动绑定，只会单纯建立映射行</p>
     */
    private void persistRelationMappings(Long userId, Long noteId, String noteTitle,
            List<String> tags, List<String> imageNames, List<MarkdownHtmlEngine.ParsedNoteLink> noteLinks) {
        // 复用已有方法 先建立 标签、图片 映射
        persistRelationMappings(userId, noteId, noteTitle, tags, imageNames);

        // 建立 note-each 映射
        List<NoteEachMappingEntity> eachMappings = buildEachMappings(noteId, noteLinks);
        if (!eachMappings.isEmpty()) {
            noteEachMappingMapper.batchInsertMappings(eachMappings);
        }
    }

    /**
     * 批量建立 note-tag 映射关系（不自动创建标签）
     * <p>标签以”解析出的 tagName”为行粒度，便于后续按映射行绑定/解绑。</p>
     * <p>默认 tag_id 为 null，is_pass 为 WAIT，等待用户检查绑定</p>
     */
    private List<NoteTagMappingEntity> buildTagMappings(Long noteId, List<String> tags) {
        List<String> parsedTags = normalizeDistinctList(tags);
        if (parsedTags.isEmpty()) {
            return List.of();
        }

        List<NoteTagMappingEntity> mappings = new ArrayList<>();
        for (String parsedTagName : parsedTags) {
            NoteTagMappingEntity mapping = new NoteTagMappingEntity();
            mapping.setNoteId(noteId);
            mapping.setTagId(null);  // 默认不绑定
            mapping.setParsedTagName(parsedTagName);
            mapping.setIsPass(AuditConstant.WAIT);  // 默认待审核
            mapping.setIsDeleted(NoteConstant.NOT_DELETED);
            mapping.setCreateTime(LocalDateTime.now());
            mappings.add(mapping);
        }
        return mappings;
    }

    /**
     * 批量建立 note-image 映射关系（不自动绑定）
     * <p>直接插入映射行，不进行自动绑定（image_id 为 null）</p>
     */
    private List<NoteImageMappingEntity> buildImageMappings(Long noteId, Long userId, String noteTitle,
            List<String> imageNames) {
        List<String> names = normalizeDistinctList(imageNames);
        if (names.isEmpty()) {
            return List.of();
        }

        List<NoteImageMappingEntity> mappings = new ArrayList<>();
        for (String imageName : names) {
            NoteImageMappingEntity mapping = new NoteImageMappingEntity();
            mapping.setNoteId(noteId);
            mapping.setNoteUserId(userId);
            mapping.setNoteTitle(noteTitle);
            mapping.setParsedImageName(imageName);
            mapping.setImageId(null);  // 默认不绑定
            mapping.setImageUserId(null);
            mapping.setIsCrossUser(NoteConstant.NOT_IS_CROSS_USER);
            mapping.setIsPass(AuditConstant.WAIT);
            mapping.setCreateTime(LocalDateTime.now());
            mapping.setIsDeleted(NoteConstant.NOT_DELETED);
            mappings.add(mapping);
        }
        return mappings;
    }

    /**
     * 构建 note-each 映射实体列表（不自动绑定）
     * <p>直接插入映射行，不进行自动绑定（target_note_id 为 null）</p>
     */
    private List<NoteEachMappingEntity> buildEachMappings(Long sourceNoteId,
            List<MarkdownHtmlEngine.ParsedNoteLink> noteLinks) {
        if (noteLinks == null || noteLinks.isEmpty()) {
            return List.of();
        }

        List<NoteEachMappingEntity> mappings = new ArrayList<>();
        for (MarkdownHtmlEngine.ParsedNoteLink link : noteLinks) {
            NoteEachMappingEntity mapping = new NoteEachMappingEntity();
            mapping.setSourceNoteId(sourceNoteId);
            mapping.setTargetNoteId(null);  // 默认不绑定
            mapping.setParsedNoteName(link.noteName());
            mapping.setAnchor(link.anchor());
            mapping.setNickname(link.nickname());
            mapping.setIsPass(AuditConstant.WAIT);
            mapping.setIsDeleted(NoteConstant.NOT_DELETED);
            mapping.setCreateTime(LocalDateTime.now());
            mappings.add(mapping);
        }
        return mappings;
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
     * 刷新笔记缺失信息状态（使用新状态）。
     * <p>绑定/解绑任一关联后统一走该入口，避免不同路径状态不一致。</p>
     * @param noteId 笔记ID
     */
    private void refreshNoteMissingInfo(Long noteId) {
        // 统一入口：每次绑定/解绑后都由当前映射状态推导缺失信息。
        int missingMask = calculateMissingMaskFromRelations(noteId);
        int missingCount = countInitMissingBits(missingMask);

        noteMapper.updateMissingInfoFields(noteId, missingMask, missingCount);

        // 检查并自动转换状态
        checkAndAutoTransitionIfComplete(noteId);
    }

    /**
     * 从当前关联关系计算缺失信息掩码
     */
    private int calculateMissingMaskFromRelations(Long noteId) {
        int missingMask = 0;

        // 标签缺失判断
        long missingTagCount = noteTagMappingMapper.countByNoteIdAndTargetIdIsNull(noteId);
        if (missingTagCount > 0) {
            missingMask |= NoteConstant.MISSING_TAG;
        }

        // 图片缺失判断
        long missingImageCount = noteImageMappingMapper.countByNoteIdAndImageIdIsNull(noteId);
        if (missingImageCount > 0) {
            missingMask |= NoteConstant.MISSING_IMAGE;
        }

        // 内联笔记缺失判断
        long missingNoteCount = noteEachMappingMapper.countByNoteIdAndTargetIdIsNull(noteId);
        if (missingNoteCount > 0) {
            missingMask |= NoteConstant.MISSING_NOTE;
        }

        return missingMask;
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
        return tagService.getByIds(ids).stream()
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
        return imageService.getByIds(ids).stream()
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
                    && !NoteStatus.fromCode(target.getStatus()).isDeleted()
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
        if (note == null || NoteStatus.fromCode(note.getStatus()).isDeleted()) {
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
        topicService.getTopicById(topicId);
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
        diffVO.setOldTags(new ArrayList<>(difference(newTagSet, oldTagSet)));
        diffVO.setNewTags(new ArrayList<>(difference(oldTagSet, newTagSet)));
        diffVO.setOldImages(new ArrayList<>(difference(newImageSet, oldImageSet)));
        diffVO.setNewImages(new ArrayList<>(difference(oldImageSet, newImageSet)));
        diffVO.setOldNoteReflection(new ArrayList<>(difference(newNoteSet, oldNoteSet)));
        diffVO.setNewNoteReflection(new ArrayList<>(difference(oldNoteSet, newNoteSet)));
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
     * JSON 解析成扫描结果。
     * <p>从 modifyNoteSource 时保存的 scanJson 中反序列化 NoteReletionInfo</p>
     */
    private MarkdownHtmlEngine.NoteRelationInfo parseScanJson(String scanJson) {
        if (scanJson == null || scanJson.isEmpty()) {
            throw new BaseException("扫描数据缺失，请重新上传");
        }
        try {
            return objectMapper.readValue(scanJson, MarkdownHtmlEngine.NoteRelationInfo.class);
        } catch (JsonProcessingException ex) {
            throw new BaseException("扫描数据解析失败");
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
                : imageService.getByIds(imageIds).stream()
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

    /**
     * 转换笔记状态（带校验）
     *
     * @param noteId 笔记ID
     * @param targetStatus 目标状态
     */
    private void transitionNoteStatus(Long noteId, NoteStatus targetStatus) {
        NoteEntity note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        NoteStatus currentStatus = NoteStatus.fromCode(note.getStatus());

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new BaseException(String.format(
                "无法从 %s 状态转换到 %s 状态",
                currentStatus.getDesc(),
                targetStatus.getDesc()
            ));
        }

        noteMapper.updateStatus(noteId, targetStatus.getCode());

        if (targetStatus == NoteStatus.DELETED) {
            cleanupDeletedNote(note);
        }
    }

    /**
     * 检查并自动转换状态（当 missing_count = 0 时）
     *
     * @param noteId 笔记ID
     */
    private void checkAndAutoTransitionIfComplete(Long noteId) {
        NoteEntity note = noteMapper.selectById(noteId);
        if (note == null) {
            return;
        }

        if (note.getMissingCount() != null && note.getMissingCount() == 0) {
            // 扫描确认信息是否齐全
            int missingMask = scanMissingInfo(noteId);
            int missingCount = countInitMissingBits(missingMask);

            // 更新扫描结果
            noteMapper.updateMissingInfoFields(noteId, missingMask, missingCount);

            // 如果扫描后确认信息齐全，转换状态
            if (missingCount == 0) {
                NoteStatus currentStatus = NoteStatus.fromCode(note.getStatus());
                if (currentStatus == NoteStatus.PENDING_INFO) {
                    noteMapper.updateStatus(noteId, NoteStatus.READY_TO_CONVERT.getCode());
                }
            }
        }
    }

    /**
     * 扫描笔记缺失的信息
     *
     * @param noteId 笔记ID
     * @return 缺失信息掩码
     */
    private int scanMissingInfo(Long noteId) {
        int missingMask = 0;

        // 检查缺失的标签
        long missingTagCount = noteTagMappingMapper.countByNoteIdAndTargetIdIsNull(noteId);
        if (missingTagCount > 0) {
            missingMask |= NoteConstant.MISSING_TAG;
        }

        // 检查缺失的图片
        long missingImageCount = noteImageMappingMapper.countByNoteIdAndImageIdIsNull(noteId);
        if (missingImageCount > 0) {
            missingMask |= NoteConstant.MISSING_IMAGE;
        }

        // 检查缺失的内联笔记
        long missingNoteCount = noteEachMappingMapper.countByNoteIdAndTargetIdIsNull(noteId);
        if (missingNoteCount > 0) {
            missingMask |= NoteConstant.MISSING_NOTE;
        }

        return missingMask;
    }

    /**
     * 计算缺失信息的数量
     * <p>- 计算初始关联信息的总数</p>
     * @param missingMask 缺失信息掩码
     * @return 缺失数量
     */
    private int countInitMissingBits(int missingMask) {
        int count = 0;
        if (NoteMissingInfoMask.isTagMissing(missingMask)) count++;
        if (NoteMissingInfoMask.isImageMissing(missingMask)) count++;
        if (NoteMissingInfoMask.isNoteMissing(missingMask)) count++;
        return count;
    }

    /**
     * 清理已删除的笔记
     */
    private void cleanupDeletedNote(NoteEntity note) {
        // 软删除映射表中的记录
        noteTagMappingMapper.softDeleteByNoteId(note.getId());
        noteImageMappingMapper.softDeleteByNoteId(note.getId());
        noteEachMappingMapper.softDeleteBySourceNoteId(note.getId());
    }

    /**
     * 获取缺失的标签名称列表
     * <p>返回 tag_id 为 null 的映射行的 parsed_tag_name</p>
     * @param noteId 笔记ID
     * @return 缺失的标签名称列表
     */
    private List<String> getMissingTagNames(Long noteId) {
        List<NoteTagMappingEntity> mappings = noteTagMappingMapper.selectByNoteId(noteId);
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream()
                .filter(m -> m.getTagId() == null)
                .map(NoteTagMappingEntity::getParsedTagName)
                .filter(StringUtils::hasText)
                .toList();
    }

    /**
     * 获取缺失的图片名称列表
     * <p>返回 image_id 为 null 的映射行的 parsed_image_name</p>
     * @param noteId 笔记ID
     * @return 缺失的图片名称列表
     */
    private List<String> getMissingImageNames(Long noteId) {
        List<NoteImageMappingEntity> mappings = noteImageMappingMapper.selectByNoteId(noteId);
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream()
                .filter(m -> m.getImageId() == null)
                .map(NoteImageMappingEntity::getParsedImageName)
                .filter(StringUtils::hasText)
                .toList();
    }

    /**
     * 获取缺失的内联笔记名称列表
     * <p>返回 target_note_id 为 null 的映射行的 parsed_note_name</p>
     * @param noteId 笔记ID
     * @return 缺失的内联笔记名称列表
     */
    private List<String> getMissingEachNoteNames(Long noteId) {
        List<NoteEachMappingEntity> mappings = noteEachMappingMapper.selectBySourceNoteId(noteId);
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream()
                .filter(m -> m.getTargetNoteId() == null)
                .map(NoteEachMappingEntity::getParsedNoteName)
                .filter(StringUtils::hasText)
                .toList();
    }
}
