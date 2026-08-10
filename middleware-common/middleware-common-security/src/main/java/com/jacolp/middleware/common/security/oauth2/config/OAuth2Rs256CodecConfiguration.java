package com.jacolp.middleware.common.security.oauth2.config;

import com.jacolp.middleware.common.security.oauth2.jwt.RequiredAudienceJwtValidator;
import com.jacolp.middleware.common.security.oauth2.key.RsaKeyMaterial;
import com.jacolp.middleware.common.security.oauth2.key.RsaPemKeyMaterialLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * RS256 codec infrastructure, kept disabled until explicitly configured.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "jacolp.oauth2.rs256", name = "enabled", havingValue = "true")
public class OAuth2Rs256CodecConfiguration {

    @Bean
    RsaKeyMaterial oauth2Rs256KeyMaterial(OAuth2Rs256Properties properties) {
        return new RsaPemKeyMaterialLoader().load(
                properties.getPrivateKeyLocation(), properties.getPublicKeyLocation());
    }

    @Bean
    JwtEncoder oauth2Rs256JwtEncoder(RsaKeyMaterial keyMaterial) {
        return NimbusJwtEncoder.withKeyPair(keyMaterial.publicKey(), keyMaterial.privateKey())
                .algorithm(SignatureAlgorithm.RS256)
                .jwkPostProcessor(builder -> builder.keyID(keyMaterial.keyId()))
                .build();
    }

    @Bean
    JwtDecoder oauth2Rs256JwtDecoder(RsaKeyMaterial keyMaterial, OAuth2Rs256Properties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keyMaterial.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.getIssuer()),
                new RequiredAudienceJwtValidator(properties.getAudience())));
        return decoder;
    }
}
