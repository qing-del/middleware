package com.jacolp.facade.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.jacolp.pojo.entity.*;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.jacolp.annotation.StorageHandler;
import com.jacolp.component.JsonOperator;
import com.jacolp.constant.NoteConstant;
import com.jacolp.constant.TagConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.context.NoteImageResolveContext;
import com.jacolp.context.PermissionContext;
import com.jacolp.context.StorageUpdateContext;
import com.jacolp.converter.MarkdownHtmlEngine;
import com.jacolp.enums.NoteStatus;
import com.jacolp.enums.StorageOperationType;
import com.jacolp.exception.BaseException;
import com.jacolp.facade.NoteFacade;
import com.jacolp.pojo.dto.image.ImageMappingBindDTO;
import com.jacolp.pojo.dto.note.EachMappingBindDTO;
import com.jacolp.pojo.dto.note.NoteChangeConfirmDTO;
import com.jacolp.pojo.dto.note.UploadToInsertNoteDTO;
import com.jacolp.pojo.dto.tag.TagMappingBindDTO;
import com.jacolp.pojo.vo.image.ImageSimpleVO;
import com.jacolp.pojo.vo.note.NoteChangeDiffVO;
import com.jacolp.pojo.vo.note.NoteCheckBindingVO;
import com.jacolp.pojo.vo.note.NoteDetailVO;
import com.jacolp.pojo.vo.note.NoteDiffVO;
import com.jacolp.pojo.vo.note.NoteEachMappingRowVO;
import com.jacolp.pojo.vo.note.NoteEachSimpleVO;
import com.jacolp.pojo.vo.note.NoteModifyDiffDetailVO;
import com.jacolp.pojo.vo.note.NoteRelationDetailVO;
import com.jacolp.pojo.vo.note.NoteUploadVO;
import com.jacolp.pojo.vo.note.NoteVO;
import com.jacolp.service.ImageService;
import com.jacolp.service.NoteChangeDiffService;
import com.jacolp.service.NoteContextService;
import com.jacolp.service.NoteConvertService;
import com.jacolp.service.NoteCoreService;
import com.jacolp.service.NoteRelationService;
import com.jacolp.service.TagService;
import com.jacolp.service.TopicService;

import lombok.extern.slf4j.Slf4j;

/**
 * 笔记门面编排层实现。
 *
 * <p>作为 Controller 与子 Service 之间的协调层，负责将多步操作编排为完整业务流程。
 * 各子 Service 只负责单一数据表的读写，Facade 持有全部子 Service 引用并按顺序调度。</p>
 *
 * <h3>权限模型</h3>
 * <p>所有权校验委托 {@link NoteCoreService#getById(Long)}，其内部通过
 * {@link com.jacolp.context.PermissionContext#isAdmin()} 自动区分管理端/用户端。</p>
 */
@Service
@Slf4j
public class NoteFacadeImpl implements NoteFacade {

    @Autowired private NoteCoreService noteCoreService;
    @Autowired private NoteContextService noteContextService;
    @Autowired private NoteChangeDiffService noteChangeDiffService;
    @Autowired private NoteConvertService noteConvertService;
    @Autowired private NoteRelationService noteRelationService;

    @Autowired private TopicService topicService;
    @Autowired private TagService tagService;
    @Autowired private ImageService imageService;

    @Autowired private JsonOperator jsonOperator;

    // ==================== 上传笔记 ====================

    /**
     * 上传笔记 —— 完整 5 步编排。
     *
     * <ol>
     *   <li>校验主题存在性 + 同主题同名唯一性</li>
     *   <li>扫描 Markdown 提取标签、图片、内联笔记</li>
     *   <li>计算缺失信息掩码后插入笔记行</li>
     *   <li>将 Markdown 原文写入内容表</li>
     *   <li>批量建立标签/图片/内联笔记三类映射（初始全部未绑定）</li>
     * </ol>
     *
     * @param file 笔记源文件
     * @param topicId 笔记主题
     * @return 上传结果 -- 笔记缺失的关联信息
     */
    @Override
    @StorageHandler(operationType = StorageOperationType.UPLOAD)
    public NoteUploadVO uploadNote(MultipartFile file, Long topicId) {
        Long userId = BaseContext.getCurrentId();
        if (!topicService.topicExists(topicId)) {
            throw new BaseException("主题不存在");
        }
        String originalFilename = normalizeFilename(file.getOriginalFilename());
        String rawMarkdown = readMultipartAsString(file);

        // ① 查重 — 同一用户在同一主题下不允许同名笔记
        if (noteCoreService.countByUserIdAndTopicIdAndTitle(userId, topicId, originalFilename)) {
            throw new BaseException("你在对应主题下已有同名的笔记，无法上传！");
        }

        // 解析 Markdown — 一次扫描提取标签、图片、内联笔记三类关联
        MarkdownHtmlEngine.NoteReletionInfo scanResult = MarkdownHtmlEngine.scanNoteReletionInfo(rawMarkdown);

        // 构建传输使用的数据使用的 dto
        UploadToInsertNoteDTO dto = buildUploadToInsertNoteDTO(file, topicId, userId, originalFilename, scanResult);

        // 插入笔记行
        Long noteId = noteCoreService.insertNote(dto);

        // 插入笔记文本 — 通过 NoteImageResolveContext 为图片解析插件提供 noteId
        NoteImageResolveContext.setCurrentNoteId(noteId);
        try {
            NoteContextEntity contextEntity = buildNoteContextEntity(noteId, rawMarkdown);
            noteContextService.insert(contextEntity);
        } finally {
            NoteImageResolveContext.clear();
        }

        // 建立三类映射 — 标签/图片/内联笔记，初始 target_id = null 等待用户绑定
        noteRelationService.initTagBatchInsertMappings(noteId, dto.getTags());
        noteRelationService.initImageBatchInsertMappings(noteId, dto.getImageNames());
        noteRelationService.initNoteBatchInsertMappings(noteId, scanResult.noteNames());

        // 构建返回结果
        NoteUploadVO vo = new NoteUploadVO();
        vo.setNoteId(noteId);
        BeanUtils.copyProperties(dto, vo);
        return vo;
    }


    // ==================== 修改笔记源文件 ====================

    /**
     * 修改笔记源文件 —— 完整 5 步编排。
     *
     * <ol>
     *   <li>校验笔记所有权 + 检查是否存在未确认的 diff</li>
     *   <li>从内容表读取旧 Markdown，新内容暂存到 {@code markdown_content_new} 列</li>
     *   <li>分别扫描新旧文本的标签/图片/内联笔记</li>
     *   <li>对比新旧扫描结果构建 DiffVO（新增/移除列表）</li>
     *   <li>持久化 diff 记录（状态=待确认），供后续确认或取消</li>
     * </ol>
     *
     * @param noteId 笔记 ID
     * @param file 更新笔记的源文件
     * @return DiffVO -- 新旧标签/图片/内联笔记列表
     * @throws BaseException 笔记不存在、权限不足、笔记已存在未确认的变更
     */
    @Override
    @StorageHandler(operationType = StorageOperationType.MODIFY)
    public NoteDiffVO modifyNoteSource(Long noteId, MultipartFile file) {
        // 校验所有权，非所有者抛出 PERMISSION_DENIED
        NoteEntity existed = noteCoreService.getById(noteId);
        normalizeFilename(file.getOriginalFilename());

        // 检查未确认 diff — 同一时刻只能有一个待确认的变更
        if (noteChangeDiffService.countByNoteIdAndStatus(noteId, NoteConstant.NOTE_DIFF_STATUS_PENDING)) {
            throw new BaseException(NoteConstant.NOTE_DIFF_EXIST);
        }

        // 读取旧内容，新内容暂存到 markdown_content_new
        NoteContextEntity noteContext = noteContextService.getByNoteId(noteId);
        if (noteContext == null) {
            throw new BaseException(NoteConstant.NOTE_CONTENT_NOT_FOUND);
        }
        String oldMarkdown = noteContext.getMarkdownContent();
        String newMarkdown = readMultipartAsString(file);

        // 扫描新旧文本，对比差异
        MarkdownHtmlEngine.NoteReletionInfo oldScan = MarkdownHtmlEngine.scanNoteReletionInfo(oldMarkdown);
        MarkdownHtmlEngine.NoteReletionInfo newScan = MarkdownHtmlEngine.scanNoteReletionInfo(newMarkdown);
        NoteDiffVO diffVO = buildDiff(
                oldScan.tags(), newScan.tags(),
                oldScan.imageNames(), newScan.imageNames(),
                oldScan.noteNames(), newScan.noteNames());

        // 保存新内容
        noteContext.setMarkdownContentNew(newMarkdown);
        noteContextService.update(noteContext);

        // 持久化 diff 记录 — 同时保存 scanJson 供确认时复用，避免二次扫描
        NoteChangeDiffEntity diffEntity = buildNoteChangeDiffEntity(noteId, file.getSize(), diffVO,
                newScan, existed.getMdFileSize());
        noteChangeDiffService.insert(diffEntity);

        return diffVO;
    }


    // ==================== 确认或取消变更 ====================

    /**
     * 确认或取消笔记变更。
     *
     * <p><b>确认时</b>：用新内容覆盖旧内容 → 删除旧三类映射 → 重新扫描建立新映射 → 状态回退为 NEW。</p>
     * <p><b>取消时</b>：清除临时版本内容，旧内容不变。</p>
     *
     * @param noteId 笔记 ID
     * @param dto    confirm=true 确认，confirm=false 取消
     * @throws BaseException 笔记不存在、权限不足、笔记不存在未确认的变更
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmChange(Long noteId, NoteChangeConfirmDTO dto) {
        // 检查笔记和内容是否存在
        NoteEntity existed = noteCoreService.getById(noteId);
        NoteContextEntity contextEntity = noteContextService.getByNoteId(noteId);
        if (contextEntity == null || contextEntity.getMarkdownContentNew() == null) {
            throw new BaseException("待确认的新版本不存在");
        }

        // 检查是否存在待确认 diff，不存在存在即返回报错
        NoteChangeDiffEntity diffEntity = noteChangeDiffService
                .getByNoteIdAndStatus(noteId, NoteConstant.NOTE_DIFF_STATUS_PENDING);
        if (diffEntity == null) {
            throw new BaseException(NoteConstant.NOTE_CHANGE_DIFF_NOT_FOUND);
        }

        if (Boolean.TRUE.equals(dto.getConfirm())) {
            // === 确认变更 ===
            String newMarkdown = contextEntity.getMarkdownContentNew();

            // 从 diffJson 中获取新增/移除列表
            NoteDiffVO diff = jsonOperator.fromJson(diffEntity.getDiffJson(), NoteDiffVO.class);

            // 用新内容覆盖旧内容，清除临时版本
            contextEntity.setMarkdownContent(newMarkdown);
            contextEntity.setMarkdownContentNew(null);  // 清除临时版本
            noteContextService.update(contextEntity);

            // 删除旧映射 → 重新建立新映射
            noteRelationService.deleteByNoteIds(List.of(noteId));
            noteRelationService.initTagBatchInsertMappings(noteId, diff.getNewTags());
            noteRelationService.initImageBatchInsertMappings(noteId, diff.getNewImages());
            noteRelationService.initNoteBatchInsertMappings(noteId, diff.getNewNoteNames());

            // 状态回到 NEW
            existed.setStatus(NoteStatus.NEW.getCode());
            noteCoreService.update(existed);

            // 设置结果值参数
            diffEntity.setStatus(NoteConstant.NOTE_DIFF_STATUS_CONFIRMED);
        } else {
            // === 取消变更 ===
            contextEntity.setMarkdownContentNew(null);
            noteContextService.update(contextEntity);

            // 设置结果值参数
            diffEntity.setStatus(NoteConstant.NOTE_DIFF_STATUS_CANCELED);
        }

        noteChangeDiffService.updateStatus(diffEntity.getNoteId(), diffEntity.getStatus()); // 更新 diff 状态
    }


    // ==================== 查询变更详情 ====================

    /**
     * 获取笔记修改的 Diff 详情。
     * <p>- 通过 {@link PermissionContext#isAdmin()} 来决定是否开启所有权校验</p>
     * <p>返回旧内容、新内容及差异摘要，供前端确认页展示。</p>
     */
    @Override
    public NoteModifyDiffDetailVO getModifyDiff(Long noteId) {
        // 获取新旧文本
        NoteContextEntity contextEntity = noteContextService.getByNoteIdWithValid(noteId);
        if (contextEntity == null) {
            throw new BaseException(NoteConstant.NOTE_CONTENT_NOT_FOUND);
        }
        if (contextEntity.getMarkdownContentNew() == null) {
            throw new BaseException(NoteConstant.NOTE_CHANGE_DIFF_NOT_FOUND);
        }
        String oldSource = contextEntity.getMarkdownContent();
        String newSource = contextEntity.getMarkdownContentNew();

        // 获取元信息的新旧列表和构建差异
        NoteChangeDiffEntity diffEntity = noteChangeDiffService
                .getByNoteIdAndStatus(noteId, NoteConstant.NOTE_DIFF_STATUS_PENDING);
        NoteDiffVO diff = jsonOperator.fromJson(diffEntity.getDiffJson(), NoteDiffVO.class);

        // 构建完整的差异信息
        NoteChangeDiffVO diffVO = new NoteChangeDiffVO(
                noteId, NoteConstant.NOTE_DIFF_STATUS_PENDING,
                diffEntity.getOldFileSize(), diffEntity.getNewFileSize(),
                diffEntity.getNewFileSize() - diffEntity.getOldFileSize(), diff);

        return new NoteModifyDiffDetailVO(noteId, oldSource, newSource, diffVO);
    }

    /**
     * 将笔记 Markdown 原文转换为 HTML
     * <p>前置校验：笔记不能处于"信息缺失"状态，且缺失计数必须为 0。
     * 转换前将当前 noteId 注入 {@link NoteImageResolveContext} 供图片插件使用。</p>
     * <p>- 管理员调用的话会跳过所有权的校验</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void convertNote(Long noteId) {
        NoteEntity note = noteCoreService.getById(noteId);

        // 校验信息完整性 — 缺失信息的笔记不允许转换
        NoteStatus noteStatus = NoteStatus.fromCode(note.getStatus());
        if (noteStatus.canTransitionTo(NoteStatus.CONVERTED)    // 使用枚举类中的检查来进行检测
                || (note.getMissingCount() != null && note.getMissingCount() > 0)) {
            throw new BaseException(NoteConstant.NOTE_MISSING_INFO);
        }

        // 读取笔记内容
        NoteContextEntity context = noteContextService.getByNoteId(noteId);
        if (context == null) {
            throw new BaseException(NoteConstant.NOTE_CONTENT_NOT_FOUND);
        }

        noteConvertService.convertAndSave(noteId, context.getMarkdownContent());
    }

    /**
     * 批量删除笔记（管理端）。
     *
     * <ol>
     *   <li>校验所有笔记存在且不处于审核中/已公开状态</li>
     *   <li>汇总各用户的文件大小（供 {@code @StorageHandler} 回收配额）</li>
     *   <li>依次清理转换结果 → Diff 记录 → 文本内容 → 三类映射</li>
     *   <li>笔记行状态标记为 DELETED（软删除）</li>
     *   <li>通过 {@link StorageUpdateContext} 传递存储回收信息给切面 - 进入了该方法就一定要清除 StorageUpdateContext 中的内容</li>
     * </ol>
     */
    @Override
    @StorageHandler(operationType = StorageOperationType.BATCH_DELETE)
    public void adminDeleteNotes(List<Long> ids) {
        // 批量获取笔记 & 检查数量级是否能对应上
        List<NoteEntity> notes = noteCoreService.getByIds(ids);
        if (notes.size() != ids.size()) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }

        // 检查笔记状态 & 构建存储回收信息
        Map<Long, Long> userStorageMap = checkAndBuildStorageMap(notes);

        // 依次清理五类关联数据
        noteConvertService.deleteAllByNoteIds(ids);
        noteChangeDiffService.deleteByNoteIds(ids);
        noteContextService.deleteByNoteIds(ids);
        noteRelationService.deleteByNoteIds(ids);

        // 软删除笔记行
        noteCoreService.updateStatusByIds(ids, NoteStatus.DELETED.getCode());

        // 传递给 @StorageHandler 切面用于批量更新用户存储用量
        StorageUpdateContext.setStorageMap(userStorageMap); // 在 StorageHandler 中已有兜底清除机制
    }

    /**
     * 删除笔记
     * <p>- 用户端调用的时候带有所有权校验</p>
     * @param noteId 笔记 ID
     */
    @Override
    @StorageHandler(operationType = StorageOperationType.DELETE)
    public void deleteNote(Long noteId) {
        NoteEntity note = noteCoreService.getById(noteId);

        NoteStatus currentStatus = NoteStatus.fromCode(note.getStatus());
        if (currentStatus == NoteStatus.PENDING_AUDIT) {
            throw new BaseException("笔记正在审核中，不能删除");
        }
        if (currentStatus == NoteStatus.PUBLISHED) {
            throw new BaseException("笔记已公开，请先下架后再删除");
        }

        // 清理关联数据
        noteConvertService.delete(noteId);
        noteContextService.deleteByNoteIds(List.of(noteId));
        noteRelationService.deleteByNoteIds(List.of(noteId));

        // 更新笔记状态（软删除）
        noteCoreService.updateStatusByIds(List.of(noteId), NoteStatus.DELETED.getCode());

        // 构建存储回收信息（上下文传递）
        StorageUpdateContext.setStorageMap(Map.of(note.getUserId(), note.getMdFileSize()));
    }

    /**
     * 更新笔记状态
     * <p>- 管理员调用不做所属校验</p>
     * <p>- 支持以下复杂逻辑的转换做了特殊处理：</p>
     * <ol>
     *     <li>- 已通过 -> 发布</li>
     * </ol>
     */
    @Override
    public void updateNoteStatus(Long noteId, Short status) {
        // 先获取笔记
        NoteEntity note = noteCoreService.getById(noteId);

        NoteStatus currentStatus = NoteStatus.fromCode(note.getStatus());
        NoteStatus targetStatus = NoteStatus.fromCode(status);

        // 使用“状态机“守卫检查是否可以转换
        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new BaseException(String.format(
                    "无法从 %s 状态转换到 %s 状态",
                    currentStatus.getDesc(),
                    targetStatus.getDesc()
            ));
        }

        if (targetStatus == NoteStatus.PUBLISHED) {
            // 委托 NoteRelationService 做三类映射的通过性校验
            if (!noteRelationService.countByNoteIdAndPass(noteId)) {
                throw new BaseException(TagConstant.TAG_NOT_PASS);
            }
        }

        noteCoreService.updateStatusByIds(List.of(noteId), status);
    }

    /**
     * 获取笔记三类关联映射详情（标签 / 图片 / 内联笔记）。
     * <p>通过 {@link NoteCoreService#getById} 校验所有权后委托给 {@link NoteRelationService}。</p>
     */
    @Override
    public NoteRelationDetailVO getRelationInfo(Long noteId) {
        if (!PermissionContext.isAdmin()) {
            noteCoreService.getById(noteId);    // 不是管理员才需要校验
        }

        // 先进行三联映射查询
        List<NoteTagMappingEntity> tagMappings = noteRelationService.listTagMappingsByNoteId(noteId);
        List<NoteImageMappingEntity> imageMappings = noteRelationService.listImageMappingsByNoteId(noteId);
        List<NoteEachMappingEntity> eachMappings = noteRelationService.listEachMappingsByNoteId(noteId);

        // 构建缓存
        Map<Long, TagEntity> tagMap = buildTagMap(tagMappings);
        Map<Long, ImageEntity> imageMap = buildImageMap(imageMappings);
        Map<Long, NoteEntity> targetNoteMap = buildTargetNoteMap(eachMappings);

        // 构建返回结果
        return noteRelationService.getRelationInfo(
                noteId,
                tagMappings, tagMap,
                imageMappings, imageMap,
                eachMappings, targetNoteMap);
    }

    /**
     * 获取笔记完整详情 --
     * <p>- 通过 {@link PermissionContext} 来控制是否校验所有权</p>
     * <p>聚合笔记基本信息、主题名、标签名列表、图片简要列表、
     * 双链映射及转换结果，供前端详情页一次性加载。</p>
     */
    @Override
    public NoteDetailVO getInfo(Long noteId) {
        // 查询笔记
        NoteVO noteVO = noteCoreService.getNoteVOById(noteId);

        NoteDetailVO detailVO = new NoteDetailVO();
        BeanUtils.copyProperties(noteVO, detailVO);

        // 标签名列表
        List<Long> tagIds = noteRelationService.listTagMappingsByNoteId(noteId)
                .stream()
                .map(NoteTagMappingEntity::getTagId)
                .filter(Objects::nonNull)
                .toList();
        detailVO.setTags(tagIds.isEmpty()
                ? List.of() : tagService.getByIds(tagIds).stream().map(TagEntity::getTagName).toList());

        // 图片简要列表
        detailVO.setImages(listImageSimpleVOsByNoteId(noteId)); // TODO 注意这里发生了 this 自调用

        // 双链映射
        NoteRelationDetailVO relation = getRelationInfo(noteId);    // TODO 这里发生了 this 调用
        buildEachNotesAndSetToDetailVO(relation.getEachNotes(), detailVO);

        // 获取笔记的转换结果（如果不存在）
        try {
            detailVO.setConverted(noteConvertService.getNoteConvert(noteId));
        } catch (BaseException e) {
            detailVO.setConverted(null);    // 抛出异常说明不存在转换结果，设置为 null
        }

        return detailVO;
    }

    /**
     * 获取图片简要列表
     * <p>- 此处没有权限校验</p>
     */
    @Override
    public List<ImageSimpleVO> listImageSimpleVOsByNoteId(Long noteId) {
        List<NoteImageMappingEntity> mappings = noteRelationService.listImageMappingsByNoteId(noteId);
        List<Long> imageIds = mappings
                .stream()
                .map(NoteImageMappingEntity::getImageId)
                .toList();

        Map<Long, ImageEntity> imageMap = imageIds.isEmpty()
                ? Map.of()
                : imageService.getByIds(imageIds).stream()
                  .collect(Collectors.toMap(ImageEntity::getId, image -> image, (left, right) -> left));

        List<ImageSimpleVO> result = new ArrayList<>();
        buildNoteImageSimpleVOList(noteId, mappings, imageMap, result);
        return result;
    }

    /**
     * 绑定标签映射关系
     */
    @Override
    public void bindTagMapping(TagMappingBindDTO dto) {
        TagEntity targetTag = tagService.getByIdAndUserId(dto.getTagId(), BaseContext.getCurrentId());
        noteRelationService.bindTagMapping(dto, targetTag);
    }

    /**
     * 绑定图片映射关系
     */
    @Override
    public void bindImageMapping(ImageMappingBindDTO dto) {
        ImageEntity targetImage = imageService.getById(dto.getImageId());
        noteRelationService.bindImageMapping(dto, targetImage);
    }

    /**
     * 绑定内联笔记映射关系
     */
    @Override
    public void bindEachMapping(EachMappingBindDTO dto) {
        NoteEntity targetNote = noteCoreService.getById(dto.getNoteId());
        noteRelationService.bindEachMapping(dto, targetNote);
    }

    @Override
    public NoteCheckBindingVO checkRelationCompletion(Long noteId) {
        NoteEntity note = noteCoreService.getById(noteId);

        // 先尝试自动补绑定已存在且可绑定的标签/图片/内联笔记
        syncBindableMappings(noteId, note.getUserId(), note.getTopicId());

        // 获取笔记检查需要使用的关联信息
        NoteCheckBindingVO vo = noteRelationService.checkRelationCompletion(note);

        // 检查是否需要更新笔记状态
        NoteStatus currentStatus = NoteStatus.fromCode(note.getStatus());
        NoteStatus targetStatus = NoteStatus.fromCode(NoteConstant.STATUS_READY_TO_CONVERT);
        // 检查是否可以进行转换
        if (vo.isComplete() && currentStatus.canTransitionTo(targetStatus)) {
            vo.setStatus(targetStatus.getCode());   // 转换成“可转换”状态
            vo.setStatusDesc(targetStatus.getDesc());
        } else {
            vo.setStatus(currentStatus.getCode());  // 保持原状态
            vo.setStatusDesc(currentStatus.getDesc());
        }

        // 组装更新使用的数据
        note.setStatus(vo.getStatus());
        note.setMissingInfoMask(vo.getMissingInfoMask());
        note.setMissingCount(vo.getMissingCount());
        noteCoreService.update(note);

        return vo;
    }



    // ==================== 私有辅助方法 ====================
    /**
     * 去除首尾空白
     */
    private String normalizeFilename(String filename) {
        if (filename == null) return null;
        return filename.trim();
    }
    /**
     * 以 UTF-8 读取文件内容
     */
    private String readMultipartAsString(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BaseException("读取文件失败: " + e.getMessage());
        }
    }

    /**
     * 去空白 + 去重 + 过滤空值
     */
    private List<String> normalizeDistinctList(List<String> list) {
        if (list == null || list.isEmpty()) return List.of();
        return list.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    /**
     * null 安全的 long 转换
     */
    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 构建新旧文本的 DiffVO
     */
    private NoteDiffVO buildDiff(List<String> oldTags, List<String> newTags,
                                 List<String> oldImages, List<String> newImages,
                                 List<String> oldNotes, List<String> newNotes) {
        NoteDiffVO vo = new NoteDiffVO();
        vo.setOldTags(oldTags);
        vo.setNewTags(newTags);
        vo.setOldImages(oldImages);
        vo.setNewImages(newImages);
        vo.setOldNoteNames(oldNotes);
        vo.setNewNoteNames(newNotes);
        return vo;
    }

    /**
     * 初始化上传笔记传输数据使用的 DTO 的内容
     */
    private UploadToInsertNoteDTO buildUploadToInsertNoteDTO(MultipartFile file, Long topicId, Long userId,
                                                             String originalFilename, MarkdownHtmlEngine.NoteReletionInfo scanResult) {
        UploadToInsertNoteDTO dto = new UploadToInsertNoteDTO();
        dto.setFileSize(file.getSize());
        dto.setTopicId(topicId);
        dto.setUserId(userId);
        dto.setOriginalFilename(originalFilename);
        dto.setTags(normalizeDistinctList(scanResult.tags()));
        dto.setImageNames(normalizeDistinctList(scanResult.imageNames()));
        dto.setNoteNames(List.copyOf(scanResult.noteLinks())
                .stream()
                .map(MarkdownHtmlEngine.ParsedNoteLink::noteName)
                .toList());
        return dto;
    }

    /**
     * 构建笔记文本内容实体
     */
    private static @NonNull NoteContextEntity buildNoteContextEntity(Long noteId, String rawMarkdown) {
        NoteContextEntity contextEntity = new NoteContextEntity();
        contextEntity.setNoteId(noteId);
        contextEntity.setMarkdownContent(rawMarkdown);
        return contextEntity;
    }

    /**
     * 构建 diffVO。
     */
    private @NonNull NoteChangeDiffEntity buildNoteChangeDiffEntity(Long noteId, Long fileSize, NoteDiffVO diffVO,
                                                                    MarkdownHtmlEngine.NoteReletionInfo newScan,
                                                                    Long existedFileSize) {
        NoteChangeDiffEntity diffEntity = new NoteChangeDiffEntity();
        diffEntity.setNoteId(noteId);
        diffEntity.setStatus(NoteConstant.NOTE_DIFF_STATUS_PENDING);
        diffEntity.setDiffJson(jsonOperator.toJson(diffVO));
        diffEntity.setScanJson(jsonOperator.toJson(newScan));
        diffEntity.setOldFileSize(safeLong(existedFileSize));
        diffEntity.setNewFileSize(fileSize);
        return diffEntity;
    }

    /**
     * 检查笔记状态
     * <p>构建用户存储回收量</p>
     *
     * @return 用户存储回收量的 <userId, fileSize(Long)> 的 Map
     * @throws BaseException 如果存在不可删除状态的笔记 会抛出此异常
     */
    private @NonNull Map<Long, Long> checkAndBuildStorageMap(List<NoteEntity> notes) {
        // 状态校验 — 审核中和已公开的笔记不允许删除
        for (NoteEntity note : notes) {
            NoteStatus status = NoteStatus.fromCode(note.getStatus());
            if (status == NoteStatus.PENDING_AUDIT) {
                throw new BaseException("笔记【" + note.getTitle() + "】正在审核中，不能删除");
            }
            if (status == NoteStatus.PUBLISHED) {
                throw new BaseException("笔记【" + note.getTitle() + "】已公开，请先下架后再删除");
            }
        }

        // 汇总各用户的存储回收量
        Map<Long, Long> userStorageMap = new LinkedHashMap<>();
        for (NoteEntity note : notes) {
            userStorageMap.merge(note.getUserId(), safeLong(note.getMdFileSize()), Long::sum);
        }
        return userStorageMap;
    }

    /**
     * 构建每个内联笔记的标题并设置给 {@link NoteDetailVO}
     * <p>- 如果是传入空集的话，会设置为空列表</p>
     * <p>- 否则会使用批量查询进行处理，然后转成合适的列表存到VO中</p>
     * <p>- 这里使用了 {@link NoteCoreService#getByIds(List)} 方法</p>
     * @param eachNotesList 内联笔记的映射行
     * @param detailVO      需要被修改的笔记详情VO
     */
    private void buildEachNotesAndSetToDetailVO(List<NoteEachMappingRowVO> eachNotesList, NoteDetailVO detailVO) {
        if (eachNotesList == null || eachNotesList.isEmpty()) {
            detailVO.setEachNotes(List.of());
        } else {
            // 使用 stream 将目标转为中的 连接笔记 ids 转为列表
            List<Long> targetIds = eachNotesList
                    .stream()
                    .map(NoteEachMappingRowVO::getTargetNoteId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            // 使用上一步得到的 ids 列表进行批量查询
            Map<Long, String> targetTitleMap = targetIds.isEmpty()
                    ? Map.of() : noteCoreService.getByIds(targetIds)
                                 .stream()
                                 .filter(n -> n.getStatus() == null || !NoteStatus.fromCode(n.getStatus()).isDeleted())
                                 .collect(Collectors.toMap(NoteEntity::getId, NoteEntity::getTitle, (left, right) -> left));

            // 构建列表
            detailVO.setEachNotes(eachNotesList
                    .stream()
                    .map(row -> {
                        NoteEachSimpleVO vo = new NoteEachSimpleVO();
                        vo.setTargetNoteId(row.getTargetNoteId());
                        vo.setTargetNoteTitle(row.getTargetNoteId() == null ? null : targetTitleMap.get(row.getTargetNoteId()));
                        vo.setParsedNoteName(row.getParsedNoteName());
                        vo.setAnchor(row.getAnchor());
                        vo.setNickname(row.getNickname());
                        vo.setIsMissing(row.getTargetNoteId() == null ? NoteConstant.MISSED_INFO : NoteConstant.NOT_MISSED_INFO);
                        return vo;
                    }).toList());
        }
    }

    /**
     * 构建简单图片信息列表
     */
    private static void buildNoteImageSimpleVOList(Long noteId, List<NoteImageMappingEntity> mappings, Map<Long, ImageEntity> imageMap, List<ImageSimpleVO> result) {
        for (NoteImageMappingEntity mapping : mappings) {
            ImageSimpleVO vo = new ImageSimpleVO();
            vo.setNoteId(noteId);
            vo.setParsedImageName(mapping.getParsedImageName());
            vo.setIsCrossUser(mapping.getIsCrossUser());
            vo.setImageId(mapping.getImageId());

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
    }

    /**
     * 同步可绑定的映射关系
     */
    private void syncBindableMappings(Long noteId, Long userId, Long topicId) {
        // 同步标签映射
        List<NoteTagMappingEntity> tagMappings = Optional.ofNullable
                        (noteRelationService.listTagMappingsByNoteId(noteId)).orElse(List.of());
        Map<String, TagEntity> tagMap = getTagEntitiesMap(tagMappings, userId);
        noteRelationService.tryBatchBindTagMappings(tagMappings, tagMap);

        if (topicId != null) return;

        // 同步图片映射
        List<NoteImageMappingEntity> imageMappings = Optional.ofNullable
                        (noteRelationService.listImageMappingsByNoteId(noteId)).orElse(List.of());
        Map<String, ImageEntity> imageMap = getImageEntitiesMap(imageMappings, userId, topicId);
        noteRelationService.tryBatchBindImageMappings(imageMappings, imageMap);

        // 同步内联笔记映射
        List<NoteEachMappingEntity> noteMappings = Optional.ofNullable
                        (noteRelationService.listEachMappingsByNoteId(noteId)).orElse(List.of());
        Map<String, NoteEntity> noteMap = getNoteEntitiesMap(noteMappings, userId, topicId);
        noteRelationService.tryBatchBindNoteMappings(noteMappings, noteMap);
    }

    /**
     * 获取标签实体
     * <p>- 如果传入空集，则返回空集</p>
     * <p>- 否则会进行批量查询，并返回结果</p>
     * <p>- 这里使用了 {@link TagService#getByNamesAndUserId(List, Long)} 方法</p>
     * @param mappings 标签映射行
     * @param userId   用户 id
     * @return 标签实体的 <tagName, TagEntity> 的 Map
     */
    private Map<String, TagEntity> getTagEntitiesMap(List<NoteTagMappingEntity> mappings, Long userId) {
        if (mappings.isEmpty()) {
            return Map.of();
        }

        List<String> parsedNames = mappings
                .stream()
                .map(NoteTagMappingEntity::getParsedTagName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (parsedNames.isEmpty()) {
            return Map.of();
        }

        List<TagEntity> tags = Optional.ofNullable(tagService.getByNamesAndUserId(parsedNames, userId))
                .orElse(List.of());
        if (tags.isEmpty()) {
            return Map.of();
        }

        return tags.stream()
                .collect(Collectors
                        .toMap(
                                TagEntity::getTagName,
                                tag -> tag,
                                (left, right) -> left)
                );
    }

    /**
     * 获取图片实体
     * <p>- 如果传入空集，则返回空集</p>
     * <p>- 否则会进行批量查询，并返回结果</p>
     * <p>- 这里使用了 {@link ImageService#getByUserIdAndTopicIdAndFilenames(Long, Long, List)} 方法</p>
     * @param mappings 图片映射行
     * @param userId   用户 id
     * @param topicId  话题 id
     * @return 图片实体的 <imageName, ImageEntity> 的 Map
     */
    private Map<String, ImageEntity> getImageEntitiesMap(List<NoteImageMappingEntity> mappings, Long userId, Long topicId) {
        if (mappings.isEmpty()) {
            return Map.of();
        }

        List<String> parsedNames = mappings.stream()
                .map(NoteImageMappingEntity::getParsedImageName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (parsedNames.isEmpty()) {
            return Map.of();
        }

        List<ImageEntity> images = Optional.ofNullable(
                        imageService.getByUserIdAndTopicIdAndFilenames(userId, topicId, parsedNames))
                .orElse(List.of());
        if (images.isEmpty()) {
            return Map.of();
        }

        return images.stream()
                .collect(Collectors
                        .toMap(
                                ImageEntity::getFilename,
                                image -> image,
                                (left, right) -> left)
                );
    }

    /**
     * 获取笔记实体
     * <p>- 如果传入空集，则返回空集</p>
     * <p>- 否则会进行批量查询，并返回结果</p>
     * <p>- 这里使用了 {@link NoteCoreService#getByUserIdAndTopicIdAndTitles(Long, Long, List)} 方法</p>
     * @param mappings 笔记映射行
     * @param userId   用户 id
     * @param topicId  话题 id
     * @return 笔记实体的 <noteName, NoteEntity> 的 Map
     */
    private Map<String, NoteEntity> getNoteEntitiesMap(List<NoteEachMappingEntity> mappings, Long userId, Long topicId) {
        if (mappings.isEmpty()) {
            return Map.of();
        }

        List<String> parsedNames = mappings.stream()
                .map(NoteEachMappingEntity::getParsedNoteName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (parsedNames.isEmpty()) {
            return Map.of();
        }

        List<NoteEntity> notes = Optional.ofNullable(
                        noteCoreService.getByUserIdAndTopicIdAndTitles(userId, topicId, parsedNames))
                .orElse(List.of());
        if (notes.isEmpty()) {
            return Map.of();
        }

        return notes.stream()
                .collect(Collectors
                        .toMap(
                                NoteEntity::getTitle,
                                note -> note,
                                (left, right) -> left)
                );
    }

    private Map<Long, TagEntity> buildTagMap(List<NoteTagMappingEntity> mappings) {
        List<Long> ids = mappings.stream()
                .map(NoteTagMappingEntity::getTagId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return Map.of();
        }
        return tagService.getByIds(ids).stream()
                .collect(Collectors.toMap(TagEntity::getId, tag -> tag, (left, right) -> left));
    }

    private Map<Long, ImageEntity> buildImageMap(List<NoteImageMappingEntity> mappings) {
        List<Long> ids = mappings.stream()
                .map(NoteImageMappingEntity::getImageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return Map.of();
        }
        return imageService.getByIds(ids).stream()
                .collect(Collectors.toMap(ImageEntity::getId, image -> image, (left, right) -> left));
    }

    private Map<Long, NoteEntity> buildTargetNoteMap(List<NoteEachMappingEntity> mappings) {
        List<Long> ids = mappings.stream()
                .map(NoteEachMappingEntity::getTargetNoteId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return noteCoreService.getByIds(ids).stream()
                .collect(Collectors.toMap(NoteEntity::getId, note -> note, (left, right) -> left));
    }
}
