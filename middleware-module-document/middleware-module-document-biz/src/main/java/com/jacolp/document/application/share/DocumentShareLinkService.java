package com.jacolp.document.application.share;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.common.security.oauth2.token.OpaqueTokenProtector;
import com.jacolp.common.security.oauth2.token.SecureOAuth2TokenGenerator;
import com.jacolp.document.application.access.DocumentAccessService;
import com.jacolp.document.controller.DocumentShareLinkResponse;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentShareLinkDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentShareLinkMapper;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理文档所有者创建的权限分享短链。兑换逻辑在后续提交实现。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentShareLinkService {

    private static final long MAX_VALID_FOR_SECONDS = 365L * 24 * 60 * 60;
    private static final int MAX_USES = 100_000;

    private final DocumentAccessService accessService;
    private final DocumentShareLinkMapper shareLinkMapper;
    private final SecureOAuth2TokenGenerator tokenGenerator;
    private final OpaqueTokenProtector tokenProtector;
    private final String baseUrl;

    public DocumentShareLinkService(DocumentAccessService accessService,
                                    DocumentShareLinkMapper shareLinkMapper,
                                    SecureOAuth2TokenGenerator tokenGenerator,
                                    OpaqueTokenProtector tokenProtector,
                                    @Value("${jacolp.base-url}") String baseUrl) {
        this.accessService = Objects.requireNonNull(accessService, "accessService must not be null");
        this.shareLinkMapper = Objects.requireNonNull(shareLinkMapper, "shareLinkMapper must not be null");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator must not be null");
        this.tokenProtector = Objects.requireNonNull(tokenProtector, "tokenProtector must not be null");
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    /** 仅文档所有者可以创建短链；明文 token 不保存，只在响应 URL 中返回一次。 */
    @Transactional(rollbackFor = Exception.class)
    public DocumentShareLinkResponse create(long currentUserId, long documentId,
                                            DocumentPermission permission, long validForSeconds, int maxUses) {
        accessService.requireOwner(documentId, currentUserId);
        validate(permission, validForSeconds, maxUses);

        String rawCode = tokenGenerator.newOpaqueToken();
        DocumentShareLinkDO shareLink = new DocumentShareLinkDO(
                null, documentId, currentUserId, Base64.getUrlDecoder().decode(tokenProtector.fingerprint(rawCode)), permission,
                LocalDateTime.now().plusSeconds(validForSeconds), maxUses, 0, true, null, null, null);
        shareLinkMapper.insert(shareLink);
        if (shareLink.getId() == null || shareLink.getId() <= 0) {
            throw new BaseException("创建文档分享短链失败");
        }
        return toResponse(shareLink, baseUrl + "/s/" + rawCode);
    }

    /** 返回指定文档的全部短链，包括已取消、已过期及已耗尽记录。 */
    public List<DocumentShareLinkResponse> list(long currentUserId, long documentId) {
        accessService.requireOwner(documentId, currentUserId);
        List<DocumentShareLinkDO> links = shareLinkMapper.selectByDocumentId(documentId);
        if (links == null || links.isEmpty()) return List.of();
        return links.stream().map(link -> toResponse(link, null)).toList();
    }

    /** 仅生成者可以取消短链；取消是软状态更新且可重复调用。 */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(long currentUserId, long documentId, long shareLinkId) {
        accessService.requireOwner(documentId, currentUserId);
        if (shareLinkId <= 0) throw new IllegalArgumentException("shareLinkId must be positive");
        DocumentShareLinkDO link = shareLinkMapper.selectByIdForUpdate(shareLinkId);
        if (link == null || !Objects.equals(link.getDocumentId(), documentId)) {
            return;
        }
        if (!Objects.equals(link.getCreatorUserId(), currentUserId)) {
            throw new BaseException("无权取消该文档分享短链");
        }
        shareLinkMapper.revokeByIdAndCreator(shareLinkId, currentUserId);
    }

    private static void validate(DocumentPermission permission, long validForSeconds, int maxUses) {
        if (permission == null || (permission != DocumentPermission.READ && permission != DocumentPermission.WRITE)) {
            throw new BaseException("分享权限必须为 READ 或 WRITE");
        }
        if (validForSeconds <= 0 || validForSeconds > MAX_VALID_FOR_SECONDS) {
            throw new BaseException("分享短链有效时长超出允许范围");
        }
        if (maxUses <= 0 || maxUses > MAX_USES) {
            throw new BaseException("分享短链最大使用次数超出允许范围");
        }
    }

    private static DocumentShareLinkResponse toResponse(DocumentShareLinkDO link, String shareUrl) {
        if (link == null || link.getId() == null || link.getDocumentId() == null
                || link.getPermission() == null || link.getExpiresAt() == null
                || link.getMaxUses() == null || link.getUsedCount() == null) {
            throw new BaseException("文档分享短链数据无效");
        }
        return new DocumentShareLinkResponse(link.getId(), link.getDocumentId(), link.getPermission(), shareUrl,
                link.getExpiresAt(), link.getMaxUses(), link.getUsedCount(), Boolean.TRUE.equals(link.getEnabled()),
                link.getRevokedAt(), link.getCreateTime(), link.getUpdateTime());
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) throw new IllegalStateException("jacolp.base-url is required");
        String normalized = value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
