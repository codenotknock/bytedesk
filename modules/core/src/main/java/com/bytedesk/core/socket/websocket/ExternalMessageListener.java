package com.bytedesk.core.socket.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bytedesk.core.message.MessageProtobuf;
import com.bytedesk.core.message.MessageTypeEnum;
import com.bytedesk.core.message.event.MessageSendEvent;
import com.bytedesk.core.rbac.user.UserProtobuf;
import com.bytedesk.core.thread.ThreadProtobuf;

import lombok.extern.slf4j.Slf4j;

/**
 * @author fuzhouling
 * @date 2025/04/16
 * @program bytedesk
 * @description 监听客服回复并通过WebSocket发送给外部服务
 **/
@Slf4j
@Component
public class ExternalMessageListener {

    @Autowired
    private ExternalWebSocketHandler externalWebSocketHandler;
    
    /**
     * 监听客服发送的消息
     */
    @Async
    @EventListener
    public void handleMessageSendEvent(MessageSendEvent event) {
        try {
            String messageJson = event.getMessageJson();
            MessageProtobuf message = JSON.parseObject(messageJson, MessageProtobuf.class);
            
            // 获取会话信息
            ThreadProtobuf thread = message.getThread();
            if (thread == null) {
                log.warn("消息没有会话信息，无法处理: {}", messageJson);
                return;
            }
            
            // 获取会话的额外信息
            String threadExtra = thread.getExtra();
            JSONObject extraJson = null;
            if (threadExtra != null) {
                try {
                    extraJson = JSON.parseObject(threadExtra);
                } catch (Exception e) {
                    log.warn("会话额外信息解析失败: {}", threadExtra);
                }
            }
            
            // 检查是否是来自外部的会话
            boolean isExternalThread = false;
            String username = null;
            
            // 从会话额外信息中检查是否是外部会话
            if (extraJson != null && extraJson.containsKey("client") && "EXTERNAL".equals(extraJson.getString("client"))) {
                isExternalThread = true;
                
                // 获取用户名
                if (extraJson.containsKey("username")) {
                    username = extraJson.getString("username");
                }
            }
            
            // 如果topic以external.开头，也认为是外部会话
            if (!isExternalThread && thread.getTopic() != null && thread.getTopic().startsWith("external.")) {
                isExternalThread = true;
                
                // 尝试从topic中提取信息 (假设格式是 external.chat.xxx)
                String threadId = thread.getUid();
                if (threadId != null) {
                    // 通过其他方式查找对应的用户名
                    // 这里需要更加复杂的逻辑来查找，或者依赖于extra中的信息
                }
            }
            
            // 检查消息类型，跳过一些不需要推送给外部客户端的系统消息
            MessageTypeEnum messageType = message.getType();
            if (messageType == MessageTypeEnum.TYPING || 
                messageType == MessageTypeEnum.READ || 
                messageType == MessageTypeEnum.DELIVERED) {
                // 忽略这些类型的消息
                return;
            }
            
            // 检查是否是客服发送的消息
            UserProtobuf user = message.getUser();
            if (user == null) {
                log.warn("消息没有用户信息，无法处理: {}", messageJson);
                return;
            }
            
            String userUid = user.getUid();
            if (userUid != null && userUid.startsWith("external_")) {
                // 如果是外部用户发送的消息，不需要推送回去
                log.debug("忽略外部用户发送的消息: {}", userUid);
                return;
            }
            
            // 如果是外部会话，发送给对应的WebSocket连接
            if (isExternalThread) {
                // 如果有用户名，向用户发送消息
                if (username != null) {
                    log.info("向外部客户端推送消息: username={}, messageType={}", username, messageType);
                    externalWebSocketHandler.sendAgentReply(username, message);
                    return;
                }
                
                // 如果无法获取用户名，记录警告
                log.warn("无法获取外部会话的用户名，消息无法推送: threadId={}", thread.getUid());
            }
            
        } catch (Exception e) {
            log.error("处理消息发送事件异常", e);
        }
    }
} 