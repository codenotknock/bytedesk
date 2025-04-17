package com.bytedesk.service.external;

import java.io.IOException;
import org.springframework.web.socket.WebSocketSession;
import com.alibaba.fastjson2.JSONObject;

/**
 * 外部消息处理接口
 * 定义WebSocket消息处理方法
 */
public interface ExternalMessageHandler {

    /**
     * 处理用户问题
     */
    void handleUserQuestion(WebSocketSession session, JSONObject data, String username);
    
    /**
     * 处理重连
     */
    void handleReconnect(WebSocketSession session, JSONObject json, String username);
    
    /**
     * 处理心跳
     */
    void handleHeartbeat(WebSocketSession session, JSONObject json);
    
    /**
     * 发送错误消息
     */
    void sendErrorMessage(WebSocketSession session, String errorMessage) throws IOException;
    
    /**
     * 保存会话映射
     */
    void saveThreadMapping(String username, String threadId);
    
    /**
     * 获取会话ID
     */
    String getThreadIdByUsername(String username);
} 