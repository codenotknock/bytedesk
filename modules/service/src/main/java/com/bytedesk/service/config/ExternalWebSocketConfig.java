package com.bytedesk.service.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.bytedesk.service.external.ExternalMessageBridge;
import com.bytedesk.service.external.ExternalWebSocketHandler;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 外部WebSocket配置
 */
@Slf4j
@Configuration
@EnableWebSocket
public class ExternalWebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private ExternalMessageBridge messageBridge;

    @Autowired
    private ExternalWebSocketHandler externalWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        log.info("注册外部WebSocket处理器");

        // 设置消息桥接器
        externalWebSocketHandler.setMessageBridge(messageBridge);

        // 注册处理器，同时支持原始WebSocket和SockJS
        registry.addHandler(externalWebSocketHandler, "/ws/external")
            .setAllowedOrigins("*")
            .addInterceptors(webSocketHandshakeInterceptor());
    }

    @Bean
    public HandshakeInterceptor webSocketHandshakeInterceptor() {
        return new WebSocketHandshakeInterceptor();
    }

    /*
    @Bean(name = "externalWebSocketTaskScheduler")
    public TaskScheduler externalWebSocketTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("external-websocket-scheduler-");
        scheduler.initialize();
        return scheduler;
    }
     */

    /**
     * WebSocket握手拦截器，用于身份验证和参数传递
     */
    public static class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
            // 获取URL参数
            String token = extractTokenFromRequest(request);

            // 将token存入attributes，以便在WebSocketHandler中获取
            if (token != null) {
                attributes.put("token", token);
            }

            // 总是允许握手
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                WebSocketHandler wsHandler, Exception exception) {
            // 握手后处理，可以记录日志等
        }

        /**
         * 从请求中提取token
         */
        private String extractTokenFromRequest(ServerHttpRequest request) {
            // 尝试从URL参数中获取token
            String uri = request.getURI().toString();
            if (uri.contains("token=")) {
                int tokenIndex = uri.indexOf("token=");
                String tokenParam = uri.substring(tokenIndex + 6); // "token=".length() = 6
                // 如果有其他参数，截取到下一个&
                if (tokenParam.contains("&")) {
                    tokenParam = tokenParam.substring(0, tokenParam.indexOf("&"));
                }
                return tokenParam;
            }
            return null;
        }
    }
}