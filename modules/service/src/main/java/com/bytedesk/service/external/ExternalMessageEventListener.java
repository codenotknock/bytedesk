package com.bytedesk.service.external;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bytedesk.core.message.event.MessageJsonEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * 监听消息事件，转发给外部WebSocket客户端
 * 桥接主系统消息事件与外部消息事件
 */
@Slf4j
@Component
public class ExternalMessageEventListener {


    @Autowired
    private ExternalMessageBridge messageBridge;

    /**
     * 专门监听MessageJsonEvent事件
     */
    @EventListener
    public void handleMessageJsonEvent(MessageJsonEvent event) {
        try {
            if (messageBridge == null) {
                return;
            }
            
            String messageJson = event.getJson();
            if (messageJson == null || messageJson.isEmpty()) {
                return;
            }
            
            // 解析消息JSON
            JSONObject message = JSON.parseObject(messageJson);
            if (message == null) {
                return;
            }
            
            // 获取用户信息
            JSONObject user = message.getJSONObject("user");
            if (user == null) {
                return;
            }
            // 获取消息类型
            String messageType = message.getString("type");
            if (StringUtils.isEmpty(messageType) || !("TEXT".equals(messageType)
                    || "IMAGE".equals(messageType) || "FILE".equals(messageType))) {
                return;
            }
            // 只处理客服发送的消息
            String userType = user.getString("type");
            if (!"AGENT".equals(userType)) {
                return;
            }

            // 获取线程信息
            JSONObject thread = message.getJSONObject("thread");
            if (thread == null) {
                return;
            }

            // 获取线程中的访客信息
            JSONObject threadUser = thread.getJSONObject("user");
            if (threadUser == null) {
                return;
            }

            // 提取关键信息：客服消息内容和访客昵称
            String content = message.getString("content");
            String visitorNickname = threadUser.getString("nickname");
            String threadId = thread.getString("uid");


            // 创建简化的消息对象，只包含关键信息
//            JSONObject simplifiedMessage = new JSONObject();
//            simplifiedMessage.put("content", content);
//            simplifiedMessage.put("visitorNickname", visitorNickname);
//            simplifiedMessage.put("threadId", threadId);

            // 创建外部消息事件，转发给ExternalMessageBridge处理
            messageBridge.sendAgentReply(visitorNickname, content);
            log.debug("已创建ExternalMessageEvent转发客服回复消息, 内容: {}, 访客: {}", content, visitorNickname);
        } catch (Exception e) {
            log.error("处理MessageJsonEvent异常", e);
        }
    }
} 