package com.jacolp.document.application.share;

import com.jacolp.common.core.exception.BaseException;
import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.common.security.oauth2.authorization.PermissionScopeMatcher;
import com.jacolp.document.application.access.DocumentAccessDeniedException;
import com.jacolp.document.controller.DocumentShareLinkRedeemResponse;
import com.jacolp.document.enums.DocumentPermission;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentShareLinkDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentShareLinkRedemptionDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentUserMappingDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentShareLinkMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentUserMappingMapper;
import com.jacolp.system.api.UserProfileApi;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jacolp.common.security.oauth2.token.OpaqueTokenProtector;

/** Redeems a document share link into a direct document ACL grant. */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentShareLinkRedemptionService {

    private final DocumentShareLinkMapper shareLinkMapper;
    private final DocumentMapper documentMapper;
    private final DocumentUserMappingMapper mappingMapper;
    private final UserProfileApi userProfileApi;
    private final OpaqueTokenProtector tokenProtector;

    public DocumentShareLinkRedemptionService(DocumentShareLinkMapper shareLinkMapper,
                                              DocumentMapper documentMapper,
                                              DocumentUserMappingMapper mappingMapper,
                                              UserProfileApi userProfileApi,
                                              OpaqueTokenProtector tokenProtector) {
        this.shareLinkMapper = Objects.requireNonNull(shareLinkMapper);
        this.documentMapper = Objects.requireNonNull(documentMapper);
        this.mappingMapper = Objects.requireNonNull(mappingMapper);
        this.userProfileApi = Objects.requireNonNull(userProfileApi);
        this.tokenProtector = Objects.requireNonNull(tokenProtector);
    }

    @Transactional(rollbackFor = Exception.class)
    public DocumentShareLinkRedeemResponse redeem(CurrentPrincipal principal, String code) {
        if (principal == null || !"user".equals(principal.clientId())) throw DocumentAccessDeniedException.forbidden();
        if (code == null || code.isBlank() || code.length() > 256) throw DocumentAccessDeniedException.forbidden();

        byte[] tokenHash;
        try {
            tokenHash = Base64.getUrlDecoder().decode(tokenProtector.fingerprint(code));
        } catch (IllegalArgumentException exception) {
            throw DocumentAccessDeniedException.forbidden();
        }
        DocumentShareLinkDO candidate = shareLinkMapper.selectByTokenHash(tokenHash);
        if (candidate == null || candidate.getId() == null) throw DocumentAccessDeniedException.forbidden();
        DocumentShareLinkDO link = shareLinkMapper.selectByIdForUpdate(candidate.getId());
        if (link == null || !validLink(link)) throw DocumentAccessDeniedException.forbidden();
        requireScope(principal, link.getPermission());

        DocumentDO document = documentMapper.selectActiveById(link.getDocumentId());
        if (document == null || document.getOwnerUserId() == null || !userProfileApi.isActiveUser(principal.userId())) {
            throw DocumentAccessDeniedException.forbidden();
        }
        if (Objects.equals(document.getOwnerUserId(), principal.userId())) {
            return new DocumentShareLinkRedeemResponse(document.getId(), DocumentPermission.WRITE, true);
        }

        DocumentShareLinkRedemptionDO existingRedemption = shareLinkMapper.selectRedemption(link.getId(), principal.userId());
        DocumentUserMappingDO mapping = mappingMapper.selectByDocumentIdAndUserId(link.getDocumentId(), principal.userId());
        if (existingRedemption != null) {
            // A successful redemption is idempotent. Do not re-grant a permission that the
            // owner may have subsequently revoked, and do not consume another quota slot.
            return new DocumentShareLinkRedeemResponse(document.getId(),
                    highest(mapping == null ? null : mapping.getPermission(), existingRedemption.getPermission()), false);
        }
        DocumentPermission finalPermission = highest(mapping == null ? null : mapping.getPermission(), link.getPermission());
        boolean alreadyWritable = mapping != null
                && Boolean.TRUE.equals(mapping.getEnabled())
                && mapping.getPermission() == DocumentPermission.WRITE;
        if (!alreadyWritable) {
            DocumentUserMappingDO grant = new DocumentUserMappingDO(link.getDocumentId(), principal.userId(),
                    finalPermission, true, null, null);
            if (mappingMapper.upsertByDocumentOwner(grant, document.getOwnerUserId()) <= 0) {
                throw DocumentAccessDeniedException.forbidden();
            }
        }
        DocumentShareLinkRedemptionDO redemption = new DocumentShareLinkRedemptionDO(link.getId(), principal.userId(),
                link.getPermission(), LocalDateTime.now());
        if (shareLinkMapper.insertRedemption(redemption) <= 0 || shareLinkMapper.incrementUsedCountIfAvailable(link.getId()) <= 0) {
            throw DocumentAccessDeniedException.forbidden();
        }
        return new DocumentShareLinkRedeemResponse(document.getId(), finalPermission, false);
    }

    private static boolean validLink(DocumentShareLinkDO link) {
        return Boolean.TRUE.equals(link.getEnabled()) && link.getExpiresAt() != null
                && link.getExpiresAt().isAfter(LocalDateTime.now())
                && link.getMaxUses() != null && link.getUsedCount() != null
                && link.getUsedCount() < link.getMaxUses()
                && link.getDocumentId() != null && link.getPermission() != null;
    }

    private static boolean hasQuota(DocumentShareLinkDO link) {
        return link.getUsedCount() < link.getMaxUses();
    }

    private static void requireScope(CurrentPrincipal principal, DocumentPermission permission) {
        String required = permission == DocumentPermission.WRITE ? "document:write" : "document:read";
        if (permission == DocumentPermission.WRITE
                ? !PermissionScopeMatcher.grants(principal.scopes(), required)
                : !(PermissionScopeMatcher.grants(principal.scopes(), "document:read")
                || PermissionScopeMatcher.grants(principal.scopes(), "document:write"))) {
            throw new com.jacolp.common.core.exception.PermissionDeniedException("权限不足");
        }
    }

    private static DocumentPermission highest(DocumentPermission first, DocumentPermission second) {
        return first == DocumentPermission.WRITE || second == DocumentPermission.WRITE
                ? DocumentPermission.WRITE : DocumentPermission.READ;
    }
}
