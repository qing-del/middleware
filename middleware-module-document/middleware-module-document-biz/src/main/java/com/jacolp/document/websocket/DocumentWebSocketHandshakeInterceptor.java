package com.jacolp.document.websocket;

import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.common.security.context.SecurityContextCurrentPrincipalAccessor;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Authenticates a browser WebSocket handshake using the existing RS256 access token.
 *
 * <p>Native browser WebSocket clients cannot add an Authorization header, therefore the token is
 * passed as the single {@code bearer.&lt;JWT&gt;} Sec-WebSocket-Protocol value. The token is never read
 * from a query parameter.</p>
 */
@Component
public class DocumentWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String PRINCIPAL_ATTRIBUTE =
            DocumentWebSocketHandshakeInterceptor.class.getName() + ".principal";
    private static final String BEARER_PROTOCOL_PREFIX = "bearer.";
    private static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";

    private final JwtDecoder jwtDecoder;

    public DocumentWebSocketHandshakeInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = Objects.requireNonNull(jwtDecoder, "jwtDecoder must not be null");
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler webSocketHandler, Map<String, Object> attributes) {
        String token = extractAccessToken(request.getHeaders());
        if (token == null) {
            return false;
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            CurrentPrincipal principal = SecurityContextCurrentPrincipalAccessor.fromJwt(jwt);
            attributes.put(PRINCIPAL_ATTRIBUTE, principal);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler webSocketHandler, Exception exception) {
        // Authentication is complete before the upgrade; no session cleanup is required here.
    }

    public static CurrentPrincipal requirePrincipal(Map<String, Object> attributes) {
        Object principal = attributes.get(PRINCIPAL_ATTRIBUTE);
        if (principal instanceof CurrentPrincipal currentPrincipal) {
            return currentPrincipal;
        }
        throw new IllegalStateException("authenticated document WebSocket principal is missing");
    }

    private static String extractAccessToken(HttpHeaders headers) {
        String protocolHeader = headers.getFirst(SEC_WEBSOCKET_PROTOCOL);
        if (protocolHeader == null) {
            return null;
        }
        String[] protocols = protocolHeader.split(",", -1);
        if (protocols.length != 1) {
            return null;
        }
        String protocol = protocols[0].trim();
        if (!protocol.startsWith(BEARER_PROTOCOL_PREFIX) || protocol.length() == BEARER_PROTOCOL_PREFIX.length()) {
            return null;
        }
        return protocol.substring(BEARER_PROTOCOL_PREFIX.length());
    }
}
