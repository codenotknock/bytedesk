package com.bytedesk.service.external;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外部消息桥接器
 * 负责连接WebSocketHandler和MessageAdapter
 */
@Slf4j
@Component
public class ExternalMessageBridge implements ExternalMessageHandler {
    
    @Autowired
    private ExternalMessageAdapter messageAdapter;
    
    // 存储WebSocket会话: username -> session
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    // 存储待处理的消息: key -> message
    private final Map<String, JSONObject> pendingMessages = new ConcurrentHashMap<>();


    /**
     * 处理消息
     * 根据消息类型路由到对应的处理方法
     */
    public String processMessage(String payload) {
        try {
            JSONObject json = JSON.parseObject(payload);
            String type = json.getString("type");
            String username = json.getString("username");
            JSONObject data = json.getJSONObject("data");
            
            if (username == null || username.isEmpty()) {
                log.warn("消息中未提供username");
                return createErrorResponse("消息格式错误: 缺少username字段");
            }
            
            WebSocketSession session = sessionMap.get(username);
            if (session == null || !session.isOpen()) {
                log.warn("找不到用户的WebSocket会话: {}", username);
                return createErrorResponse("会话不存在或已关闭");
            }
            
            switch (type) {
                case "user-question":
                    handleUserQuestion(session, data, data.getString("userNick"));
                    break;
                case "reconnect":
                    handleReconnect(session, json, username);
                    break;
                case "heartbeat":
                    handleHeartbeat(session, json);
                    break;
                default:
                    log.warn("未知的消息类型: {}", type);
                    return createErrorResponse("未知的消息类型: " + type);
            }
            
            // 返回空表示已处理，具体响应由handler自己发送
            return null;
        } catch (Exception e) {
            log.error("处理WebSocket消息异常", e);
            return createErrorResponse("处理消息异常: " + e.getMessage());
        }
    }
    
    /**
     * 创建错误响应
     */
    private String createErrorResponse(String message) {
        JSONObject response = new JSONObject();
        response.put("event", "error");
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return response.toJSONString();
    }




    /**
     * 注册会话
     */
    public void registerSession(String username, WebSocketSession session) {
        sessionMap.put(username, session);
    }
    
    /**
     * 移除会话
     */
    public void removeSession(String username) {
        sessionMap.remove(username);
    }
    
    /**
     * 处理用户问题
     */
    @Override
    public void handleUserQuestion(WebSocketSession session, JSONObject data, String username) {
        // 检查更新 sessionMap
        if (!sessionMap.containsKey(username)) {
            sessionMap.put(username, session);
        } else {
            // 检查是否一致
            if (!Objects.equals(sessionMap.get(username),session)) {
                log.warn("会话不一致，将替换会话: {}", username);
                sessionMap.put(username, session);
            }
        }
        log.debug("桥接用户问题消息: username={}", username);
        messageAdapter.handleUserQuestion(session, data, username);
    }
    
    /**
     * 处理重连
     */
    @Override
    public void handleReconnect(WebSocketSession session, JSONObject json, String username) {
        log.debug("桥接重连消息: username={}", username);
        messageAdapter.handleReconnect(session, json, username);
    }
    
    /**
     * 处理心跳
     */
    @Override
    public void handleHeartbeat(WebSocketSession session, JSONObject json) {
        messageAdapter.handleHeartbeat(session, json);
    }
    
    /**
     * 发送错误消息
     */
    @Override
    public void sendErrorMessage(WebSocketSession session, String errorMessage) throws IOException {
        messageAdapter.sendErrorMessage(session, errorMessage);
    }
    
    /**
     * 保存会话映射
     */
    @Override
    public void saveThreadMapping(String username, String threadId) {
        try {
            // 将String类型的threadId转换为ThreadProtobuf对象传递给messageAdapter
            com.bytedesk.core.thread.ThreadProtobuf thread = null;
            if (threadId != null) {
                thread = new com.bytedesk.core.thread.ThreadProtobuf();
                thread.setUid(threadId);
            }
            messageAdapter.saveThreadMapping(username, thread);
        } catch (Exception e) {
            log.error("保存会话映射异常: {}", e.getMessage());
        }
    }
    
    /**
     * 获取会话ID
     */
    @Override
    public String getThreadIdByUsername(String username) {
        return messageAdapter.getThreadIdByUsername(username);
    }

    /**
     * 向指定用户名的会话发送客服回复
     */
    public void sendAgentReply(String username, Object message) {
        WebSocketSession session = sessionMap.get(username);
        if (session != null && session.isOpen()) {
            try {
                JSONObject response = new JSONObject();
                response.put("event", "agent-reply");
                response.put("username", username);
                response.put("data", JSON.toJSON(message));

                session.sendMessage(new TextMessage(response.toJSONString()));
                log.info("发送客服回复 {} {}", username, message);
            } catch (IOException e) {
                log.error("发送客服回复异常: {}", e.getMessage());
            }
        } else {
            log.warn("客户端不在线，无法发送客服回复消息，username: {}", username);
            // 存储消息到待处理队列
            JSONObject response = new JSONObject();
            response.put("event", "agent-reply");
            response.put("data", JSON.toJSON(message));

            pendingMessages.put(username + ":" + System.currentTimeMillis(), response);
        }
    }

} 