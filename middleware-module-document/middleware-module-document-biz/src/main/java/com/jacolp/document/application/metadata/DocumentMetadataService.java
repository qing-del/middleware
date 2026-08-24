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

/** 处理个人空间中的文档元数据增删改查，不读取或解析 CRDT 正文。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentMetadataService {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_TITLE_LENGTH = 255;

    private final DocumentMapper documentMapper;
    private final DocumentRedisRepository documentRedisRepository;

    public DocumentMetadataService(DocumentMapper documentMapper, DocumentRedisRepository documentRedisRepository) {
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository,
                "documentRedisRepository must not be null");
    }

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

    public List<DocumentMetadata> list(long ownerUserId) {
        requireUserId(ownerUserId);
        return documentMapper.listActiveByTeamId(ownerUserId).stream().map(this::toMetadata).toList();
    }

    public DocumentMetadata get(long ownerUserId, long documentId) {
        return toMetadata(findRequired(ownerUserId, documentId));
    }

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
            throw new BaseException("文档仍有活跃协作会话，暂不能删除");
        }
        if (documentMapper.softDeleteByIdAndTeamId(documentId, ownerUserId,
                LocalDateTime.now(APPLICATION_ZONE), ownerUserId) != 1) {
            throw notFound();
        }
    }

    private DocumentDO findRequired(long ownerUserId, long documentId) {
        requireUserId(ownerUserId);
        requireDocumentId(documentId);
        DocumentDO document = documentMapper.selectActiveByIdAndTeamId(documentId, ownerUserId);
        if (document == null) {
            throw notFound();
        }
        return document;
    }

    private DocumentMetadata toMetadata(DocumentDO document) {
        LocalDateTime lastModifyTime = Objects.requireNonNull(document.getLastModifyTime(),
                "active document must have lastModifyTime");
        return new DocumentMetadata(document.getId(), document.getTeamId(), document.getTitle(),
                lastModifyTime.atZone(APPLICATION_ZONE).toInstant().toEpochMilli(), document.getLastModifyUserId(),
                Boolean.TRUE.equals(document.getDeleted()));
    }

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

    private static void requireUserId(long ownerUserId) {
        if (ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId must be positive");
        }
    }

    private static void requireDocumentId(long documentId) {
        if (documentId <= 0) {
            throw new BaseException("文档不存在或无权访问");
        }
    }

    private static BaseException notFound() {
        return new BaseException("文档不存在或无权访问");
    }
}
