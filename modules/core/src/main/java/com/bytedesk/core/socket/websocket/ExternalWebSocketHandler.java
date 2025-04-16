package com.bytedesk.core.socket.websocket;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bytedesk.core.message.IMessageSendService;
import com.bytedesk.core.message.MessageProtobuf;
import com.bytedesk.core.message.MessageTypeEnum;
import com.bytedesk.core.message.MessageStatusEnum;
import com.bytedesk.core.enums.ClientEnum;
import com.bytedesk.core.rbac.user.UserProtobuf;
import com.bytedesk.core.thread.ThreadProtobuf;
import com.bytedesk.core.thread.ThreadTypeEnum;
import com.bytedesk.core.thread.ThreadProcessStatusEnum;

import lombok.extern.slf4j.Slf4j;


/**
 * @author fuzhouling
 * @date 2025/04/16
 * @program bytedesk
 * @description 处理外部服务WebSocket连接的处理器
 **/

@Slf4j
@Component
public class ExternalWebSocketHandler extends TextWebSocketHandler {

    // 直接存储用户名到会话的映射: username -> session
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    // 反向查询: sessionId -> username
    private final Map<String, String> sessionUsernameMap = new ConcurrentHashMap<>();
    // 存储用户名和线程ID的映射: username -> threadId
    private final Map<String, String> usernameThreadMap = new ConcurrentHashMap<>();
    
    @Autowired
    private IMessageSendService messageSendService;
    
    // 模拟简单的消息队列，用于测试
    private final Map<String, JSONObject> pendingMessages = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("外部服务建立WebSocket连接: {}", session.getId());
        
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
        
        // 发送连接成功消息
        JSONObject response = new JSONObject();
        response.put("event", "connect");
        response.put("status", "success");
        response.put("sessionId", session.getId());
        response.put("timestamp", System.currentTimeMillis());
        
        // 如果有已存储的会话ID，添加到响应中
        String existingThreadId = usernameThreadMap.get(username);
        if (existingThreadId != null) {
            response.put("threadId", existingThreadId);
        }
        
        session.sendMessage(new TextMessage(response.toJSONString()));
        
        // 检查是否有待发送的消息
        checkPendingMessages(username);
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("接收到外部服务消息: {}", payload);
        
        try {
            JSONObject json = JSON.parseObject(payload);
            String event = json.getString("event");
            
            switch (event) {
                case "heartbeat":
                    handleHeartbeat(session, json);
                    break;
                case "user-question":
                    handleUserQuestion(session, json);
                    break;
                case "reconnect":
                    handleReconnect(session, json);
                    break;
                default:
                    log.warn("未知的事件类型: {}", event);
                    sendErrorMessage(session, "未知的事件类型: " + event);
            }
        } catch (Exception e) {
            log.error("处理外部服务消息异常", e);
            sendErrorMessage(session, "处理消息异常: " + e.getMessage());
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("外部服务WebSocket连接关闭: {}, 原因: {}", session.getId(), status.getReason());
        
        // 移除会话
        String username = sessionUsernameMap.get(session.getId());
        if (username != null) {
            sessionMap.remove(username);
            sessionUsernameMap.remove(session.getId());
            
            // 注意：不要删除usernameThreadMap中的映射，以便用户重连时能恢复会话
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("外部服务WebSocket连接传输错误: {}", session.getId(), exception);
        
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
                // 保留username关联的threadId以便重连
            }
        }
    }
    
    /**
     * 处理心跳请求
     */
    private void handleHeartbeat(WebSocketSession session, JSONObject json) throws IOException {
        JSONObject response = new JSONObject();
        response.put("event", "heartbeat");
        response.put("timestamp", System.currentTimeMillis());
        
        session.sendMessage(new TextMessage(response.toJSONString()));
    }
    
    /**
     * 处理重连请求
     */
    private void handleReconnect(WebSocketSession session, JSONObject json) throws IOException {
        JSONObject data = json.getJSONObject("data");
        String threadId = null;
        String username = sessionUsernameMap.get(session.getId());
        
        if (username == null) {
            // 如果没有用户名，使用会话ID作为临时用户名
            username = session.getId();
            sessionUsernameMap.put(session.getId(), username);
        }
        
        // 获取客户端提供的会话ID
        if (data != null) {
            threadId = data.getString("threadId");
            // 如果提供了用户名，更新用户名映射
            String providedUsername = data.getString("username");
            if (providedUsername != null && !providedUsername.isEmpty()) {
                // 更新为客户端提供的用户名
                String oldUsername = sessionUsernameMap.get(session.getId());
                
                // 转移旧用户名的会话ID到新用户名
                String existingThreadId = usernameThreadMap.get(oldUsername);
                if (existingThreadId != null) {
                    usernameThreadMap.put(providedUsername, existingThreadId);
                }
                
                // 移除旧映射
                if (!oldUsername.equals(session.getId())) {
                    sessionMap.remove(oldUsername);
                }
                
                // 更新映射关系
                sessionUsernameMap.put(session.getId(), providedUsername);
                sessionMap.put(providedUsername, session);
                username = providedUsername;
            }
        }
        
        // 如果客户端没有提供会话ID，尝试使用已存储的会话ID
        if (threadId == null) {
            threadId = usernameThreadMap.get(username);
        }
        
        JSONObject response = new JSONObject();
        response.put("event", "reconnect");
        response.put("status", "success");
        response.put("sessionId", session.getId());
        response.put("username", username);
        response.put("timestamp", System.currentTimeMillis());
        
        if (threadId != null) {
            response.put("threadId", threadId);
        }
        
        session.sendMessage(new TextMessage(response.toJSONString()));
        
        // 检查是否有待发送的消息
        checkPendingMessages(username);
    }
    
    /**
     * 处理用户问题
     */
    private void handleUserQuestion(WebSocketSession session, JSONObject json) throws IOException {
        JSONObject data = json.getJSONObject("data");
        if (data == null) {
            sendErrorMessage(session, "消息格式错误: 缺少data字段");
            return;
        }
        
        String userNick = data.getString("userNick");
        String question = data.getString("question");
        String customerId = data.getString("customer");
        String threadId = data.getString("threadId");
        String messageId = data.getString("messageId"); // 可选，用于客户端消息确认
        
        if (userNick == null || question == null) {
            sendErrorMessage(session, "消息格式错误: 缺少必要字段");
            return;
        }
        
        try {
            // 获取或更新用户名
            String username = sessionUsernameMap.get(session.getId());
            if (username == null || username.equals(session.getId())) {
                // 使用用户昵称作为用户名
                username = userNick;
                sessionUsernameMap.put(session.getId(), username);
                sessionMap.put(username, session);
            }
            
            // 如果没有会话ID，创建新会话
            if (threadId == null) {
                // 生成一个模拟的会话ID
                threadId = UUID.randomUUID().toString();
                
                // 存储用户名和threadId的映射关系
                usernameThreadMap.put(username, threadId);
                
                // 创建一个模拟的会话创建成功消息
                ThreadProtobuf thread = new ThreadProtobuf();
                thread.setUid(threadId);
                thread.setTopic("external.chat." + threadId);
                thread.setType(ThreadTypeEnum.WORKGROUP); // 设置类型为技能组会话
                thread.setStatus(ThreadProcessStatusEnum.CHATTING); // 设置状态为对话中
                
                // 添加额外信息
                JSONObject threadExtra = new JSONObject();
                threadExtra.put("client", "EXTERNAL"); // 在extra中存储客户端类型
                threadExtra.put("username", username); // 存储用户名，便于后续查询
                thread.setExtra(threadExtra.toJSONString());
                
                UserProtobuf agent = new UserProtobuf();
                agent.setNickname("客服助手");
                agent.setUid("system_helper");
                
                // 创建欢迎消息
                MessageProtobuf welcomeMessage = new MessageProtobuf();
                welcomeMessage.setType(MessageTypeEnum.WELCOME);
                welcomeMessage.setContent("您好，我是客服助手，请问有什么可以帮助您的？");
                welcomeMessage.setThread(thread);
                welcomeMessage.setUser(agent);
                welcomeMessage.setUid(UUID.randomUUID().toString());
                welcomeMessage.setClient(ClientEnum.WEB); // 设置为网页端
                welcomeMessage.setCreatedAt(LocalDateTime.now());
                welcomeMessage.setStatus(MessageStatusEnum.SUCCESS); // 发送成功状态
                
                // 发送初始消息
                messageSendService.sendJsonMessage(JSON.toJSONString(welcomeMessage));
                
                // 回复会话创建成功
                JSONObject response = new JSONObject();
                response.put("event", "thread-created");
                response.put("data", JSON.toJSON(welcomeMessage));
                response.put("username", username); // 返回用户名
                if (messageId != null) {
                    response.put("messageId", messageId); // 回传消息ID用于确认
                }
                session.sendMessage(new TextMessage(response.toJSONString()));
            }
            
            // 创建并发送用户消息
            MessageProtobuf messageProtobuf = new MessageProtobuf();
            messageProtobuf.setType(MessageTypeEnum.TEXT);
            messageProtobuf.setContent(question);
            messageProtobuf.setUid(messageId != null ? messageId : UUID.randomUUID().toString());
            messageProtobuf.setCreatedAt(LocalDateTime.now());
            messageProtobuf.setStatus(MessageStatusEnum.SUCCESS); // 发送成功状态
            messageProtobuf.setClient(ClientEnum.WEB); // 设置为网页端
            
            // 设置线程信息
            ThreadProtobuf thread = new ThreadProtobuf();
            thread.setUid(threadId);
            thread.setTopic("external.chat." + threadId);
            thread.setType(ThreadTypeEnum.WORKGROUP); // 设置类型为技能组会话
            thread.setStatus(ThreadProcessStatusEnum.CHATTING); // 设置状态为对话中
            
            // 添加额外信息
            JSONObject threadExtra = new JSONObject();
            threadExtra.put("client", "EXTERNAL"); // 在extra中存储客户端类型
            threadExtra.put("username", username); // 存储用户名
            thread.setExtra(threadExtra.toJSONString());
            
            messageProtobuf.setThread(thread);
            
            // 设置用户信息
            UserProtobuf user = new UserProtobuf();
            user.setNickname(userNick);
            // 创建一个基于用户名的唯一标识，确保一致性
            String userUid = "external_" + username;
            user.setUid(userUid);
            messageProtobuf.setUser(user);
            
            // 添加额外信息，如果需要
            JSONObject extra = new JSONObject();
            extra.put("source", "external_websocket");
            extra.put("username", username);
            if (customerId != null) {
                extra.put("customerId", customerId);
            }
            messageProtobuf.setExtra(extra.toJSONString());
            
            // 发送消息
            messageSendService.sendJsonMessage(JSON.toJSONString(messageProtobuf));
            
            // 回复消息发送成功
            JSONObject response = new JSONObject();
            response.put("event", "message-sent");
            response.put("status", "success");
            response.put("threadId", threadId);
            response.put("username", username);
            if (messageId != null) {
                response.put("messageId", messageId); // 回传消息ID用于确认
            }
            session.sendMessage(new TextMessage(response.toJSONString()));
            
        } catch (Exception e) {
            log.error("处理用户问题异常", e);
            sendErrorMessage(session, "处理用户问题异常: " + e.getMessage());
        }
    }
    
    /**
     * 发送错误消息
     */
    private void sendErrorMessage(WebSocketSession session, String errorMessage) throws IOException {
        JSONObject response = new JSONObject();
        response.put("event", "error");
        response.put("message", errorMessage);
        response.put("timestamp", System.currentTimeMillis());
        
        session.sendMessage(new TextMessage(response.toJSONString()));
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
    public void sendAgentReply(String username, MessageProtobuf message) {
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
     * 通过用户名获取会话ID
     */
    public String getThreadIdByUsername(String username) {
        return usernameThreadMap.get(username);
    }
} 