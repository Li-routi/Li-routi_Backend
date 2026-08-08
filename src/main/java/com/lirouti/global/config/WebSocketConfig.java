package com.lirouti.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import com.lirouti.global.websocket.StompErrorHandler;
import com.lirouti.global.websocket.WebSocketAuthInterceptor;
import com.lirouti.global.websocket.WebSocketSessionRegistry;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final StompErrorHandler stompErrorHandler;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
        registry.setPreserveReceiveOrder(true);
        // HTTP RestControllerAdvice와 별도로 STOMP ERROR frame을 JSON 계약으로 처리한다.
        registry.setErrorHandler(stompErrorHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // handshake에서 연결된 Principal을 STOMP CONNECT 시점에도 검증한다.
        registration.interceptors(webSocketAuthInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // 권한 회수 시 연결을 직접 닫을 수 있도록 STOMP 하위의 실제 WebSocket 세션을 추적한다.
        registration.addDecoratorFactory(webSocketSessionRegistry.decoratorFactory());
    }
}
