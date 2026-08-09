package com.jacolp.module.system.biz.application.authorization.model;

/**
 * Application-facing OAuth2 authorization-consent metadata.
 */
public record OAuth2AuthorizationConsentMetadata(
        String registeredClientId,
        String principalName,
        String authorities) {
}
