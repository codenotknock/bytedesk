package com.bytedesk.service.external;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外部WebSocket连接处理器
 * 负责处理WebSocket连接生命周期和消息转发
 */
@Slf4j
public class ExternalWebSocketHandler extends TextWebSocketHandler {

    private ExternalMessageBridge messageBridge;
    
    // 存储用户名到会话的映射: username -> session
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    // 反向查询映射: sessionId -> username
    private final Map<String, String> sessionUsernameMap = new ConcurrentHashMap<>();
    // 存储用户名和线程ID的映射: username -> threadId
    private final Map<String, String> usernameThreadMap = new ConcurrentHashMap<>();
    // 存储待处理的消息: key -> message
    private final Map<String, JSONObject> pendingMessages = new ConcurrentHashMap<>();
    
    public void setMessageBridge(ExternalMessageBridge messageBridge) {
        this.messageBridge = messageBridge;
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("外部WebSocket连接已建立: {}", session.getId());
        
        // 获取token用于初始验证
        String token = getToken(session);
        if (token == null || !validateToken(token)) {
            log.warn("无效的token，关闭连接: {}", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("无效的token"));
            return;
        }
        
        // 默认使用sessionId作为临时用户名，后续可以替换
        String username = session.getId();
        
        // 存储会话和用户的映射关系
        sessionMap.put(username, session);
        sessionUsernameMap.put(session.getId(), username);
        
        // 注册会话到桥接器
        if (messageBridge != null) {
            messageBridge.registerSession(username, session);
            
            // 发送连接成功响应
            JSONObject response = new JSONObject();
            response.put("event", "connect");
            response.put("status", "success");
            response.put("sessionId", session.getId());
            response.put("username", username);
            response.put("timestamp", System.currentTimeMillis());
            
            // 检查是否有已存储的会话ID
            String existingThreadId = messageBridge.getThreadIdByUsername(username);
            if (existingThreadId != null) {
                response.put("threadId", existingThreadId);
            }
            
            session.sendMessage(new TextMessage(response.toJSONString()));
            
            // 检查是否有待发送的消息
            checkPendingMessages(username);
        } else {
            log.error("消息桥接器未配置");
            session.sendMessage(new TextMessage("{\"error\":\"系统内部错误，消息处理器未配置\"}"));
        }
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("接收到外部WebSocket消息: {}", payload);
        
        if (messageBridge == null) {
            log.error("消息桥接器未配置，无法处理消息");
            JSONObject response = new JSONObject();
            response.put("event", "error");
            response.put("message", "系统尚未准备好，请稍后再试");
            response.put("timestamp", System.currentTimeMillis());
            session.sendMessage(new TextMessage(response.toJSONString()));
            return;
        }
        
        try {
            JSONObject json = JSON.parseObject(payload);
            
            // 获取当前用户名
            String username = sessionUsernameMap.get(session.getId());
            if (username == null) {
                username = session.getId();
                sessionUsernameMap.put(session.getId(), username);
            }
            
            // 提取事件类型，支持event字段和type字段
            String eventType = json.getString("event");
            if (eventType == null) {
                eventType = json.getString("type");
            }
            
            if (eventType == null) {
                log.warn("未知的事件/消息类型");
                messageBridge.sendErrorMessage(session, "未知的事件类型: 缺少event或type字段");
                return;
            }
            
            // 获取数据部分
            JSONObject data = json.getJSONObject("data");
            if (data == null && !"heartbeat".equals(eventType)) {
                log.warn("缺少data字段");
                messageBridge.sendErrorMessage(session, "消息格式错误: 缺少data字段");
                return;
            }
            
            // 根据事件类型处理
            switch (eventType) {
                case "heartbeat":
                    messageBridge.handleHeartbeat(session, json);
                    break;
                case "user-question":
                    messageBridge.handleUserQuestion(session, data, username);
                    break;
                case "reconnect":
                    messageBridge.handleReconnect(session, json, username);
                    
                    // 重连后更新用户名线程映射
                    if (data != null) {
                        String threadId = data.getString("threadId");
                        if (threadId != null && !threadId.isEmpty()) {
                            usernameThreadMap.put(username, threadId);
                            messageBridge.saveThreadMapping(username, threadId);
                        }
                    }
                    break;
                default:
                    log.warn("未知的事件类型: {}", eventType);
                    messageBridge.sendErrorMessage(session, "未知的事件类型: " + eventType);
            }
        } catch (Exception e) {
            log.error("处理外部WebSocket消息异常", e);
            try {
                messageBridge.sendErrorMessage(session, "处理消息异常: " + e.getMessage());
            } catch (Exception ex) {
                log.error("发送错误消息失败", ex);
                JSONObject response = new JSONObject();
                response.put("event", "error");
                response.put("message", "处理消息异常: " + e.getMessage());
                response.put("timestamp", System.currentTimeMillis());
                session.sendMessage(new TextMessage(response.toJSONString()));
            }
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("外部WebSocket连接关闭: {}, 原因: {}", session.getId(), status.getReason());
        
        // 移除会话
        String username = sessionUsernameMap.get(session.getId());
        if (username != null) {
            sessionMap.remove(username);
            sessionUsernameMap.remove(session.getId());
            
            // 清理桥接器中的会话
            if (messageBridge != null) {
                messageBridge.removeSession(username);
            }
            
            // 注意：不要删除usernameThreadMap中的映射，以便用户重连时能恢复会话
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("外部WebSocket连接传输错误: {}", session.getId(), exception);
        
        // 处理连接错误，尝试关闭问题会话
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR.withReason("传输错误: " + exception.getMessage()));
            }
        } catch (IOException e) {
            log.error("关闭错误会话异常", e);
        } finally {
            // 确保清理会话映射
            String username = sessionUsernameMap.get(session.getId());
            if (username != null) {
                sessionMap.remove(username);
                sessionUsernameMap.remove(session.getId());
                
                // 清理桥接器中的会话
                if (messageBridge != null) {
                    messageBridge.removeSession(username);
                }
                
                // 保留username关联的threadId以便重连
            }
        }
    }
    
    /**
     * 获取token
     */
    private String getToken(WebSocketSession session) {
        try {
            // 从URL参数中获取token
            Map<String, ?> attributes = session.getAttributes();
            String token = (String) attributes.get("token");
            
            // 如果URL参数中没有token，尝试从查询参数中获取
            if (token == null) {
                Map<String, ?> parameters = session.getHandshakeHeaders().toSingleValueMap();
                if (parameters.containsKey("token")) {
                    token = (String) parameters.get("token");
                }
            }
            
            return token;
        } catch (Exception e) {
            log.error("获取token异常", e);
            return null;
        }
    }
    
    /**
     * 验证token
     */
    private boolean validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        
        // 简化的token验证逻辑
        // 在实际生产环境中，应该使用 tokenRepository 来验证token的有效性
        return true;
    }
    
    /**
     * 检查并发送待处理的消息
     */
    private void checkPendingMessages(String username) {
        for (Map.Entry<String, JSONObject> entry : pendingMessages.entrySet()) {
            if (entry.getKey().startsWith(username + ":")) {
                WebSocketSession session = sessionMap.get(username);
                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(entry.getValue().toJSONString()));
                        pendingMessages.remove(entry.getKey());
                    } catch (IOException e) {
                        log.error("发送待处理消息失败: {}", e.getMessage());
                    }
                }
            }
        }
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
                response.put("data", JSON.toJSON(message));
                
                session.sendMessage(new TextMessage(response.toJSONString()));
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
    
    /**
     * 获取会话
     */
    public WebSocketSession getSession(String username) {
        return sessionMap.get(username);
    }
    
    /**
     * 保存线程ID映射
     */
    public void saveThreadMapping(String username, String threadId) {
        usernameThreadMap.put(username, threadId);
        // 同时更新桥接器中的映射
        if (messageBridge != null) {
            messageBridge.saveThreadMapping(username, threadId);
        }
    }
} 