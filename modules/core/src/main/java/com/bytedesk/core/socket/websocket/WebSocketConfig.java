package com.bytedesk.core.socket.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;


/**
 * @author fuzhouling
 * @date 2025/04/16
 * @program bytedesk
 * @description WebSocket配置类
 **/
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private ExternalWebSocketHandler externalWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(externalWebSocketHandler, "/api/v1/external/websocket")
                .addInterceptors(new WebSocketHandshakeInterceptor())
                .setAllowedOrigins("*"); // 允许所有来源，生产环境应限制来源
    }

    /**
     * WebSocket握手拦截器，用于身份验证和参数传递
     */
    public static class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
            
            if (request instanceof ServletServerHttpRequest) {
                HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
                
                // 获取token参数
                String token = servletRequest.getParameter("token");
                if (token != null && !token.isEmpty()) {
                    attributes.put("token", token);
                    return true;
                }
                
                // 如果没有token参数，尝试从请求头获取
                token = servletRequest.getHeader("token");
                if (token != null && !token.isEmpty()) {
                    attributes.put("token", token);
                    return true;
                }
                
                // 测试环境允许无token连接
                if (System.getProperty("spring.profiles.active", "").contains("dev") || 
                    System.getProperty("spring.profiles.active", "").contains("test")) {
                    attributes.put("token", "test_token");
                    return true;
                }
            }
            
            // 如果没有token，拒绝连接
            return false;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                WebSocketHandler wsHandler, Exception exception) {
            // 添加一些安全相关的响应头
            response.getHeaders().add("X-Frame-Options", "DENY");
            response.getHeaders().add("X-Content-Type-Options", "nosniff");
            response.getHeaders().add("X-XSS-Protection", "1; mode=block");
            
            // 记录连接信息
            if (exception != null) {
                // 如果握手过程中出现异常，可以记录日志
                System.out.println("WebSocket握手异常: " + exception.getMessage());
            }
        }
    }
} 