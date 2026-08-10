package com.jacolp.module.system.biz.application.port.out;

import com.jacolp.module.system.biz.application.authorization.model.RoleMetadata;

import java.util.List;
import java.util.Optional;

/**
 * Read-only persistence port for the role catalogue.
 */
public interface RoleMetadataRepository {

    Optional<RoleMetadata> findById(Long id);

    List<RoleMetadata> findAll();
}
