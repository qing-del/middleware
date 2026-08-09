package com.jacolp.module.system.biz.application.port.out;

import com.jacolp.module.system.biz.application.authorization.model.OAuth2RegisteredClientMetadata;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for OAuth2 client metadata only.
 */
public interface OAuth2RegisteredClientMetadataRepository {

    Optional<OAuth2RegisteredClientMetadata> findById(String id);

    Optional<OAuth2RegisteredClientMetadata> findByClientId(String clientId);

    List<OAuth2RegisteredClientMetadata> findByStatus(String status);

    int insert(OAuth2RegisteredClientMetadata registeredClient);

    int updateById(OAuth2RegisteredClientMetadata registeredClient);

    int deleteById(String id);
}
