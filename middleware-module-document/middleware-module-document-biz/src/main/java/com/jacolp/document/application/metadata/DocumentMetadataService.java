package com.jacolp.document.application.metadata;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.document.api.model.DocumentMetadata;
import com.jacolp.document.application.access.DocumentAccess;
import com.jacolp.document.application.access.DocumentAccessService;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 处理文档元数据增删改查；正文 CRDT 状态不经过 HTTP 元数据接口。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentMetadataService {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_TITLE_LENGTH = 255;

    private final DocumentMapper documentMapper;
    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentAccessService documentAccessService;

    /** 兼容旧测试和直接调用方；生产 Bean 使用带 ACL 的构造方法。 */
    public DocumentMetadataService(DocumentMapper documentMapper, DocumentRedisRepository documentRedisRepository) {
        this(documentMapper, documentRedisRepository, null);
    }

    /** 创建带资源级 ACL 判定的元数据服务。 */
    @Autowired
    public DocumentMetadataService(DocumentMapper documentMapper, DocumentRedisRepository documentRedisRepository,
                                   DocumentAccessService documentAccessService) {
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository,
                "documentRedisRepository must not be null");
        this.documentAccessService = documentAccessService;
    }

    /** 创建所有者为当前用户的文档并返回元数据。 */
    public DocumentMetadata create(long ownerUserId, String title) {
        requireUserId(ownerUserId);
        LocalDateTime now = LocalDateTime.now(APPLICATION_ZONE);
        DocumentDO document = new DocumentDO(null, ownerUserId, normalizeTitle(title), null, 0L, now,
                ownerUserId, false, 0L, null, null);
        if (documentMapper.insert(document) != 1 || document.getId() == null) {
            throw new BaseException("创建文档失败");
        }
        return toMetadata(document, ownerAccess(document));
    }

    /** 查询当前用户可见的活跃文档列表，包括所有者和有效授权文档。 */
    public List<DocumentMetadata> list(long userId) {
        requireUserId(userId);
        return documentMapper.listActiveVisibleByUserId(userId).stream()
                .map(document -> toMetadata(document, accessFor(userId, document))).toList();
    }

    /** 按当前用户的文档 ACL 读取元数据；不存在和无权限统一由访问服务处理。 */
    public DocumentMetadata get(long userId, long documentId) {
        if (documentAccessService == null) {
            DocumentDO document = findRequired(userId, documentId);
            return toMetadata(document, ownerAccess(document));
        }
        return toMetadata(documentAccessService.requireRead(documentId, userId));
    }

    /** 校验并更新文档标题；标题管理仍然只允许所有者。 */
    public DocumentMetadata updateTitle(long ownerUserId, long documentId, String title) {
        requireUserId(ownerUserId);
        requireDocumentId(documentId);
        String normalizedTitle = normalizeTitle(title);
        if (documentAccessService != null) {
            documentAccessService.requireOwner(documentId, ownerUserId);
        }
        if (documentMapper.updateTitleIfActive(documentId, ownerUserId, normalizedTitle,
                LocalDateTime.now(APPLICATION_ZONE), ownerUserId) != 1) {
            throw notFound();
        }
        DocumentDO document = documentAccessService == null
                ? findRequired(ownerUserId, documentId)
                : documentAccessService.requireOwner(documentId, ownerUserId).document();
        return toMetadata(document, ownerAccess(document));
    }

    /** 只软删除没有活跃协作会话的文档。 */
    public void delete(long ownerUserId, long documentId) {
        DocumentDO document = documentAccessService == null
                ? findRequired(ownerUserId, documentId)
                : documentAccessService.requireOwner(documentId, ownerUserId).document();
        if (documentRedisRepository.countPresence(document.getId()) > 0) {
            throw new BaseException("文档仍有活跃协作会话，暂不能删除");
        }
        if (documentMapper.softDeleteByIdAndOwnerUserId(documentId, ownerUserId,
                LocalDateTime.now(APPLICATION_ZONE), ownerUserId) != 1) {
            throw notFound();
        }
    }

    /** 旧构造方法的所有者范围查询，仅保留测试/兼容调用路径。 */
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

    /** 将数据库文档及本次 ACL 结果映射为跨模块元数据 DTO。 */
    private DocumentMetadata toMetadata(DocumentDO document, DocumentAccess access) {
        LocalDateTime lastModifyTime = Objects.requireNonNull(document.getLastModifyTime(),
                "active document must have lastModifyTime");
        return new DocumentMetadata(document.getId(), document.getOwnerUserId(), document.getTitle(),
                lastModifyTime.atZone(APPLICATION_ZONE).toInstant().toEpochMilli(), document.getLastModifyUserId(),
                Boolean.TRUE.equals(document.getDeleted()), access.permission().name(), access.owner());
    }

    private DocumentMetadata toMetadata(DocumentAccess access) {
        return toMetadata(access.document(), access);
    }

    private DocumentAccess accessFor(long userId, DocumentDO document) {
        return documentAccessService == null ? ownerAccess(document)
                : documentAccessService.requireRead(document.getId(), userId);
    }

    private static DocumentAccess ownerAccess(DocumentDO document) {
        return new DocumentAccess(document, DocumentPermission.WRITE, true);
    }

    private static String normalizeTitle(String title) {
        if (title == null) throw new BaseException("文档标题不能为空");
        String normalized = title.trim();
        if (normalized.isEmpty()) throw new BaseException("文档标题不能为空");
        if (normalized.length() > MAX_TITLE_LENGTH) throw new BaseException("文档标题不能超过 255 个字符");
        return normalized;
    }

    private static void requireUserId(long userId) {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
    }

    private static void requireDocumentId(long documentId) {
        if (documentId <= 0) throw new BaseException("文档不存在或无权访问");
    }

    private static BaseException notFound() {
        return new BaseException("文档不存在或无权访问");
    }
}