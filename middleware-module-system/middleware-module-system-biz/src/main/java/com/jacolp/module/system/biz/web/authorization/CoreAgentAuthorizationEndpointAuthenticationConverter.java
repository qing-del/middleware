package com.jacolp.module.system.biz.web.authorization;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeRequestAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationConsentAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * Adds only verified browser request details to Spring Authorization Server's official converters.
 * OAuth parameter parsing and its protocol errors remain entirely owned by the SAS 7.0.4 delegates.
 */
@Component
@ConditionalOnProperty(prefix = "jacolp.oauth2.rs256", name = "enabled", havingValue = "true")
public final class CoreAgentAuthorizationEndpointAuthenticationConverter implements AuthenticationConverter {

    static final String CONSENT_ACTION_PARAMETER = "consent_action";
    static final String INVALID_CONSENT_ACTION_DESCRIPTION = "Invalid CORE AGENT consent action";
    static final String INVALID_CONSENT_SESSION_DESCRIPTION = "Invalid CORE AGENT consent session";

    private final RequestMatcher authorizationEndpointMatcher =
            PathPatternRequestMatcher.pathPattern("/oauth2/authorize");
    private final AuthenticationConverter authorizationRequestConverter =
            new OAuth2AuthorizationCodeRequestAuthenticationConverter();
    private final AuthenticationConverter authorizationConsentConverter =
            new OAuth2AuthorizationConsentAuthenticationConverter();

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!authorizationEndpointMatcher.matches(request)) {
            return null;
        }
        Authentication authentication = authorizationRequestConverter.convert(request);
        if (authentication instanceof OAuth2AuthorizationCodeRequestAuthenticationToken token) {
            token.setDetails(details(request, null, true));
            return token;
        }
        if (authentication != null) {
            return null;
        }

        authentication = authorizationConsentConverter.convert(request);
        if (authentication instanceof OAuth2AuthorizationConsentAuthenticationToken token) {
            token.setDetails(details(request, requireConsentAction(request), false));
            return token;
        }
        return null;
    }

    private static CoreAgentAuthorizationEndpointRequestDetails details(HttpServletRequest request,
                                                                          CoreAgentAuthorizationEndpointRequestDetails.ConsentAction action,
                                                                          boolean createSession) {
        HttpSession session = request.getSession(createSession);
        if (session == null) {
            throw invalidRequest(INVALID_CONSENT_SESSION_DESCRIPTION);
        }
        return new CoreAgentAuthorizationEndpointRequestDetails(session, session.getId(), request.getRemoteAddr(),
                request.getParameterMap().containsKey("scope"), action);
    }

    private static CoreAgentAuthorizationEndpointRequestDetails.ConsentAction requireConsentAction(
            HttpServletRequest request) {
        String[] values = request.getParameterValues(CONSENT_ACTION_PARAMETER);
        if (values == null || values.length != 1) {
            throw invalidRequest(INVALID_CONSENT_ACTION_DESCRIPTION);
        }
        return switch (values[0]) {
            case "approve" -> CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.APPROVE;
            case "deny" -> CoreAgentAuthorizationEndpointRequestDetails.ConsentAction.DENY;
            default -> throw invalidRequest(INVALID_CONSENT_ACTION_DESCRIPTION);
        };
    }

    private static OAuth2AuthenticationException invalidRequest(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, description, null));
    }
}
