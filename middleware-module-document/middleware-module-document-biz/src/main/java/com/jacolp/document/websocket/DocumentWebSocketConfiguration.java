package com.jacolp.document.websocket;

import com.jacolp.common.web.config.CorsProperties;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the authenticated document collaboration endpoint only when the module is enabled. */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentWebSocketConfiguration implements WebSocketConfigurer {

    private final DocumentWebSocketHandler handler;
    private final DocumentWebSocketHandshakeInterceptor handshakeInterceptor;
    private final CorsProperties corsProperties;

    public DocumentWebSocketConfiguration(DocumentWebSocketHandler handler,
                                          DocumentWebSocketHandshakeInterceptor handshakeInterceptor,
                                          CorsProperties corsProperties) {
        this.handler = Objects.requireNonNull(handler, "handler must not be null");
        this.handshakeInterceptor = Objects.requireNonNull(handshakeInterceptor, "handshakeInterceptor must not be null");
        this.corsProperties = Objects.requireNonNull(corsProperties, "corsProperties must not be null");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/document")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns().toArray(String[]::new));
    }
}
