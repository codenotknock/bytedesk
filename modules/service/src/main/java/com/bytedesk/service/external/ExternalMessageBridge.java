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
                    handleUserQuestion(session, data, username);
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
        messageAdapter.saveThreadMapping(username, threadId);
    }
    
    /**
     * 获取会话ID
     */
    @Override
    public String getThreadIdByUsername(String username) {
        return messageAdapter.getThreadIdByUsername(username);
    }
    
    /**
     * 监听消息事件，转发客服回复消息到WebSocket
     */
    @EventListener
    public void handleExternalMessageEvent(ExternalMessageEvent event) {
        log.debug("接收到外部消息事件: type={}", event.getType());
        
        try {
            // 1. 解析消息
            String messageJson = event.getMessageJson();
            JSONObject message = JSON.parseObject(messageJson);
            
            // 2. 获取相关信息
            JSONObject thread = message.getJSONObject("thread");
            if (thread == null) {
                log.warn("消息中缺少thread信息");
                return;
            }
            
            JSONObject threadUser = thread.getJSONObject("user");
            if (threadUser == null) {
                log.warn("线程中缺少user信息");
                return;
            }
            
            String threadId = thread.getString("uid");
            String visitorUid = threadUser.getString("uid");
            String visitorNickname = threadUser.getString("nickname");
            
            // 3. 查找所有可能匹配的WebSocket会话
            for (Map.Entry<String, WebSocketSession> entry : sessionMap.entrySet()) {
                String username = entry.getKey();
                
                // 3.1 检查会话ID映射
                String mappedThreadId = messageAdapter.getThreadIdByUsername(username);
                if (threadId.equals(mappedThreadId)) {
                    WebSocketSession session = entry.getValue();
                    if (session != null && session.isOpen()) {
                        // 3.2 创建消息响应
                        JSONObject response = new JSONObject();
                        
                        // 根据消息类型设置不同的事件
                        String messageType = message.getString("type");
                        switch (messageType) {
                            case "WELCOME":
                                response.put("event", "thread-created");
                                response.put("data", message);
                                break;
                            default:
                                response.put("event", "agent-reply");
                                response.put("data", message);
                                break;
                        }
                        
                        // 3.3 发送消息
                        session.sendMessage(new TextMessage(response.toJSONString()));
                        log.info("已转发消息到WebSocket客户端: username={}, threadId={}", username, threadId);
                        return; // 找到匹配会话后退出
                    }
                }
            }
            
            log.debug("未找到匹配的WebSocket会话来转发消息: threadId={}, visitorUid={}", threadId, visitorUid);
        } catch (Exception e) {
            log.error("转发消息到WebSocket客户端异常", e);
        }
    }
} 