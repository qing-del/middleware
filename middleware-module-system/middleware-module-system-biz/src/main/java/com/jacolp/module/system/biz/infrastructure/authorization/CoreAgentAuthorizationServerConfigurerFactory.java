package com.jacolp.module.system.biz.infrastructure.authorization;

import com.jacolp.module.system.biz.web.authorization.CoreAgentAuthorizationCodeRequestAuthenticationProvider;
import com.jacolp.module.system.biz.web.authorization.CoreAgentAuthorizationCodeTokenAuthenticationConverter;
import com.jacolp.module.system.biz.web.authorization.CoreAgentAuthorizationCodeTokenAuthenticationProvider;
import com.jacolp.module.system.biz.web.authorization.CoreAgentAuthorizationConsentAuthenticationProvider;
import com.jacolp.module.system.biz.web.authorization.CoreAgentAuthorizationEndpointAuthenticationConverter;
import com.jacolp.module.system.biz.web.authorization.CoreAgentPublicClientAuthenticationConverter;
import com.jacolp.module.system.biz.web.authorization.CoreAgentPublicClientAuthenticationProvider;
import com.jacolp.module.system.biz.web.authorization.CoreAgentRefreshTokenAuthenticationConverter;
import com.jacolp.module.system.biz.web.authorization.CoreAgentRefreshTokenAuthenticationProvider;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.authentication.AuthenticationConverter;

import java.util.List;
import java.util.Objects;

/**
 * Applies the project-owned CORE AGENT protocol components to a SAS configurer.
 *
 * <p>It is intentionally a factory rather than a {@code SecurityFilterChain}: the following
 * security-chain commit owns applying it to the five exact browser/token paths. Replacing every
 * SAS default converter and provider prevents default code, refresh, client-authentication, and
 * authorization persistence flows from reaching the fail-closed authorization service.</p>
 */
public final class CoreAgentAuthorizationServerConfigurerFactory {

    private final ActiveRegisteredClientRepository registeredClientRepository;
    private final FailClosedOAuth2AuthorizationService authorizationService;
    private final OAuth2AuthorizationConsentService authorizationConsentService;
    private final AuthorizationServerSettings authorizationServerSettings;
    private final CoreAgentPublicClientAuthenticationConverter publicClientAuthenticationConverter;
    private final CoreAgentPublicClientAuthenticationProvider publicClientAuthenticationProvider;
    private final CoreAgentAuthorizationEndpointAuthenticationConverter authorizationEndpointAuthenticationConverter;
    private final CoreAgentAuthorizationCodeRequestAuthenticationProvider authorizationCodeRequestAuthenticationProvider;
    private final CoreAgentAuthorizationConsentAuthenticationProvider authorizationConsentAuthenticationProvider;
    private final OAuth2AuthorizationCodeRequestAuthenticationProvider sasAuthorizationValidatorBootstrapProvider;
    private final CoreAgentAuthorizationCodeTokenAuthenticationConverter authorizationCodeTokenAuthenticationConverter;
    private final CoreAgentRefreshTokenAuthenticationConverter refreshTokenAuthenticationConverter;
    private final CoreAgentAuthorizationCodeTokenAuthenticationProvider authorizationCodeTokenAuthenticationProvider;
    private final CoreAgentRefreshTokenAuthenticationProvider refreshTokenAuthenticationProvider;

    public CoreAgentAuthorizationServerConfigurerFactory(
            ActiveRegisteredClientRepository registeredClientRepository,
            FailClosedOAuth2AuthorizationService authorizationService,
            OAuth2AuthorizationConsentService authorizationConsentService,
            AuthorizationServerSettings authorizationServerSettings,
            CoreAgentPublicClientAuthenticationConverter publicClientAuthenticationConverter,
            CoreAgentPublicClientAuthenticationProvider publicClientAuthenticationProvider,
            CoreAgentAuthorizationEndpointAuthenticationConverter authorizationEndpointAuthenticationConverter,
            CoreAgentAuthorizationCodeRequestAuthenticationProvider authorizationCodeRequestAuthenticationProvider,
            CoreAgentAuthorizationConsentAuthenticationProvider authorizationConsentAuthenticationProvider,
            CoreAgentAuthorizationCodeTokenAuthenticationConverter authorizationCodeTokenAuthenticationConverter,
            CoreAgentRefreshTokenAuthenticationConverter refreshTokenAuthenticationConverter,
            CoreAgentAuthorizationCodeTokenAuthenticationProvider authorizationCodeTokenAuthenticationProvider,
            CoreAgentRefreshTokenAuthenticationProvider refreshTokenAuthenticationProvider) {
        this.registeredClientRepository = Objects.requireNonNull(registeredClientRepository, "registeredClientRepository");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService");
        this.authorizationConsentService = Objects.requireNonNull(authorizationConsentService, "authorizationConsentService");
        this.authorizationServerSettings = Objects.requireNonNull(authorizationServerSettings, "authorizationServerSettings");
        this.publicClientAuthenticationConverter = Objects.requireNonNull(publicClientAuthenticationConverter,
                "publicClientAuthenticationConverter");
        this.publicClientAuthenticationProvider = Objects.requireNonNull(publicClientAuthenticationProvider,
                "publicClientAuthenticationProvider");
        this.authorizationEndpointAuthenticationConverter = Objects.requireNonNull(authorizationEndpointAuthenticationConverter,
                "authorizationEndpointAuthenticationConverter");
        this.authorizationCodeRequestAuthenticationProvider = Objects.requireNonNull(
                authorizationCodeRequestAuthenticationProvider, "authorizationCodeRequestAuthenticationProvider");
        this.authorizationConsentAuthenticationProvider = Objects.requireNonNull(
                authorizationConsentAuthenticationProvider, "authorizationConsentAuthenticationProvider");
        this.sasAuthorizationValidatorBootstrapProvider = new OAuth2AuthorizationCodeRequestAuthenticationProvider(
                this.registeredClientRepository, this.authorizationService, this.authorizationConsentService);
        this.authorizationCodeTokenAuthenticationConverter = Objects.requireNonNull(authorizationCodeTokenAuthenticationConverter,
                "authorizationCodeTokenAuthenticationConverter");
        this.refreshTokenAuthenticationConverter = Objects.requireNonNull(refreshTokenAuthenticationConverter,
                "refreshTokenAuthenticationConverter");
        this.authorizationCodeTokenAuthenticationProvider = Objects.requireNonNull(authorizationCodeTokenAuthenticationProvider,
                "authorizationCodeTokenAuthenticationProvider");
        this.refreshTokenAuthenticationProvider = Objects.requireNonNull(refreshTokenAuthenticationProvider,
                "refreshTokenAuthenticationProvider");
    }

    /** Applies only explicit project implementations; it never creates or applies an HttpSecurity chain. */
    public void configure(OAuth2AuthorizationServerConfigurer configurer) {
        Objects.requireNonNull(configurer, "configurer");
        configurer.registeredClientRepository(registeredClientRepository)
                .authorizationService(authorizationService)
                .authorizationConsentService(authorizationConsentService)
                .authorizationServerSettings(authorizationServerSettings)
                .clientAuthentication(clientAuthentication -> {
                    clientAuthentication.authenticationConverters(converters ->
                            replaceClientAuthenticationConverters(converters, clientAuthenticationConverters()));
                    clientAuthentication.authenticationProviders(providers ->
                            replaceClientAuthenticationProviders(providers, clientAuthenticationProviders()));
                })
                .authorizationEndpoint(authorizationEndpoint -> {
                    authorizationEndpoint.consentPage(CoreAgentAuthorizationServerConfiguration.CONSENT_PAGE);
                    authorizationEndpoint.authorizationRequestConverters(converters ->
                            replaceAuthorizationEndpointConverters(converters, authorizationEndpointConverters()));
                    authorizationEndpoint.authenticationProviders(providers ->
                            replaceAuthorizationEndpointProviders(providers, authorizationEndpointProviders()));
                })
                .tokenEndpoint(tokenEndpoint -> {
                    tokenEndpoint.accessTokenRequestConverters(converters ->
                            replaceTokenEndpointConverters(converters, tokenEndpointConverters()));
                    tokenEndpoint.authenticationProviders(providers ->
                            replaceTokenEndpointProviders(providers, tokenEndpointProviders()));
                });
    }

    private List<AuthenticationConverter> clientAuthenticationConverters() {
        return List.of(publicClientAuthenticationConverter);
    }

    private List<AuthenticationProvider> clientAuthenticationProviders() {
        return List.of(publicClientAuthenticationProvider);
    }

    private List<AuthenticationConverter> authorizationEndpointConverters() {
        return List.of(authorizationEndpointAuthenticationConverter);
    }

    List<AuthenticationProvider> authorizationEndpointProviders() {
        return List.of(authorizationCodeRequestAuthenticationProvider, authorizationConsentAuthenticationProvider,
                sasAuthorizationValidatorBootstrapProvider);
    }

    private List<AuthenticationConverter> tokenEndpointConverters() {
        return List.of(authorizationCodeTokenAuthenticationConverter, refreshTokenAuthenticationConverter);
    }

    private List<AuthenticationProvider> tokenEndpointProviders() {
        return List.of(authorizationCodeTokenAuthenticationProvider, refreshTokenAuthenticationProvider);
    }

    static void replaceClientAuthenticationConverters(List<AuthenticationConverter> target,
                                                      List<AuthenticationConverter> replacements) {
        replace(target, replacements);
    }

    static void replaceClientAuthenticationProviders(List<AuthenticationProvider> target,
                                                    List<AuthenticationProvider> replacements) {
        replace(target, replacements);
    }

    static void replaceAuthorizationEndpointConverters(List<AuthenticationConverter> target,
                                                       List<AuthenticationConverter> replacements) {
        replace(target, replacements);
    }

    static void replaceAuthorizationEndpointProviders(List<AuthenticationProvider> target,
                                                     List<AuthenticationProvider> replacements) {
        replace(target, replacements);
    }

    static void replaceTokenEndpointConverters(List<AuthenticationConverter> target,
                                                List<AuthenticationConverter> replacements) {
        replace(target, replacements);
    }

    static void replaceTokenEndpointProviders(List<AuthenticationProvider> target,
                                              List<AuthenticationProvider> replacements) {
        replace(target, replacements);
    }

    private static <T> void replace(List<T> target, List<? extends T> replacements) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(replacements, "replacements");
        if (replacements.isEmpty() || replacements.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("CORE AGENT component replacements must be non-empty and non-null");
        }
        target.clear();
        target.addAll(replacements);
    }
}
