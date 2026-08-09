package com.jacolp.module.system.biz.application.port.out;

import com.jacolp.module.system.biz.application.authorization.model.OAuth2AuthorizationConsentMetadata;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for stored OAuth2 consent metadata only.
 */
public interface OAuth2AuthorizationConsentMetadataRepository {

    Optional<OAuth2AuthorizationConsentMetadata> findByRegisteredClientIdAndPrincipalName(
            String registeredClientId, String principalName);

    List<OAuth2AuthorizationConsentMetadata> findByPrincipalName(String principalName);

    int insert(OAuth2AuthorizationConsentMetadata consent);

    int updateAuthorities(OAuth2AuthorizationConsentMetadata consent);

    int deleteByRegisteredClientIdAndPrincipalName(String registeredClientId, String principalName);
}
