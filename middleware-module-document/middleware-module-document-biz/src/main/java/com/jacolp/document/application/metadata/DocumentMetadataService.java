package com.jacolp.document.application.metadata;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.document.api.model.DocumentMetadata;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 处理文档所有者的元数据增删改查，不读取或解析 CRDT 正文。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentMetadataService {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_TITLE_LENGTH = 255;

    private final DocumentMapper documentMapper;
    private final DocumentRedisRepository documentRedisRepository;

    /** 创建只操作个人空间元数据、不触碰 CRDT 正文的服务。 */
    public DocumentMetadataService(DocumentMapper documentMapper, DocumentRedisRepository documentRedisRepository) {
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository,
                "documentRedisRepository must not be null");
    }

    /** 创建所有者为当前用户的文档并返回不含正文指针的元数据。 */
    public DocumentMetadata create(long ownerUserId, String title) {
        requireUserId(ownerUserId);
        LocalDateTime now = LocalDateTime.now(APPLICATION_ZONE);
        DocumentDO document = new DocumentDO(null, ownerUserId, normalizeTitle(title), null, 0L, now,
                ownerUserId, false, 0L, null, null);
        if (documentMapper.insert(document) != 1 || document.getId() == null) {
            throw new BaseException("创建文档失败");
        }
        return toMetadata(document);
    }

    /** 查询当前用户拥有的未删除文档列表。 */
    public List<DocumentMetadata> list(long ownerUserId) {
        requireUserId(ownerUserId);
        return documentMapper.listActiveByOwnerUserId(ownerUserId).stream().map(this::toMetadata).toList();
    }

    /** 在当前用户个人范围内读取一份文档元数据。 */
    public DocumentMetadata get(long ownerUserId, long documentId) {
        return toMetadata(findRequired(ownerUserId, documentId));
    }

    /** 校验并更新文档标题，再返回更新后的元数据。 */
    public DocumentMetadata updateTitle(long ownerUserId, long documentId, String title) {
        requireUserId(ownerUserId);
        requireDocumentId(documentId);
        String normalizedTitle = normalizeTitle(title);
        if (documentMapper.updateTitleIfActive(documentId, ownerUserId, normalizedTitle,
                LocalDateTime.now(APPLICATION_ZONE), ownerUserId) != 1) {
            throw notFound();
        }
        return toMetadata(findRequired(ownerUserId, documentId));
    }

    /**
     * 只软删除没有活跃协作会话的文档；Redis、MinIO 和操作日志内容暂不物理清理。
     */
    public void delete(long ownerUserId, long documentId) {
        DocumentDO document = findRequired(ownerUserId, documentId);
        if (documentRedisRepository.countPresence(document.getId()) > 0) {
            // 删除不会主动踢出协作者；只在全局 presence 为零时软删除，避免已打开 Room 继续接收更新。
            throw new BaseException("文档仍有活跃协作会话，暂不能删除");
        }
        if (documentMapper.softDeleteByIdAndOwnerUserId(documentId, ownerUserId,
                LocalDateTime.now(APPLICATION_ZONE), ownerUserId) != 1) {
            throw notFound();
        }
    }

    /** 按所有者和文档 ID 查询活跃文档，统一隐藏不存在与越权的区别。 */
    private DocumentDO findRequired(long ownerUserId, long documentId) {
        requireUserId(ownerUserId);
        requireDocumentId(documentId);
        DocumentDO document = documentMapper.selectActiveById(documentId);
        if (document != null && !Objects.equals(document.getOwnerUserId(), ownerUserId)) {
            document = null;
        }
        if (document == null) {
            throw notFound();
        }
        return document;
    }

    /** 将数据库时间和字段映射为跨模块使用的元数据 DTO。 */
    private DocumentMetadata toMetadata(DocumentDO document) {
        LocalDateTime lastModifyTime = Objects.requireNonNull(document.getLastModifyTime(),
                "active document must have lastModifyTime");
        return new DocumentMetadata(document.getId(), document.getOwnerUserId(), document.getTitle(),
                lastModifyTime.atZone(APPLICATION_ZONE).toInstant().toEpochMilli(), document.getLastModifyUserId(),
                Boolean.TRUE.equals(document.getDeleted()));
    }

    /** 标准化标题并执行非空、长度上限校验。 */
    private static String normalizeTitle(String title) {
        if (title == null) {
            throw new BaseException("文档标题不能为空");
        }
        String normalized = title.trim();
        if (normalized.isEmpty()) {
            throw new BaseException("文档标题不能为空");
        }
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new BaseException("文档标题不能超过 255 个字符");
        }
        return normalized;
    }

    /** 校验认证用户 ID 为正数。 */
    private static void requireUserId(long ownerUserId) {
        if (ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId must be positive");
        }
    }

    /** 校验文档 ID，并使用统一的无权访问错误。 */
    private static void requireDocumentId(long documentId) {
        if (documentId <= 0) {
            throw new BaseException("文档不存在或无权访问");
        }
    }

    /** 创建不泄露资源是否存在的统一业务异常。 */
    private static BaseException notFound() {
        return new BaseException("文档不存在或无权访问");
    }
}
