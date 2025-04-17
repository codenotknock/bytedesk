package com.bytedesk.service.external;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bytedesk.core.message.event.MessageJsonEvent;
import com.bytedesk.core.message.event.MessageSendEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * 监听消息事件，转发给外部WebSocket客户端
 * 桥接主系统消息事件与外部消息事件
 */
@Slf4j
@Component
public class ExternalMessageEventListener {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ExternalMessageBridge messageBridge;
    
    /**
     * 监听系统消息事件，处理来自客服的回复
     * 特别是来自MessageSendEvent和MessageJsonEvent的事件
     */
//    EventListener
    public void handleMessageEvent(Object event) {
        try {
            // 检查是否配置了消息桥接器
            if (messageBridge == null) {
                log.debug("未找到消息桥接器");
                return;
            }
            
            if (event instanceof ExternalMessageEvent) {
                // 已经由ExternalMessageBridge.handleExternalMessageEvent处理了
                return;
            }
            
            log.debug("接收到系统消息事件: {}", event.getClass().getSimpleName());
            
            // 尝试通过反射获取消息内容
            // String messageJson = extractMessageJson(event);
             String messageJson = (String) event;

            // 解析消息JSON
            JSONObject message = JSON.parseObject(messageJson);
            if (message == null) {
                return;
            }
            
            // 获取线程信息
            JSONObject thread = message.getJSONObject("thread");
            if (thread == null) {
                return;
            }
            
            // 获取用户信息
            JSONObject user = message.getJSONObject("user");
            if (user == null) {
                return;
            }
            
            // 只处理工作组类型的消息且不是访客发送的
            String threadType = thread.getString("type");
            String userType = user.getString("type");
            
            if ("WORKGROUP".equals(threadType) && !"VISITOR".equals(userType)) {
                // 创建外部消息事件，转发给ExternalMessageBridge处理
                applicationContext.publishEvent(new ExternalMessageEvent(this, "system_message", messageJson));
                log.debug("已创建ExternalMessageEvent转发系统消息");
            }
        } catch (Exception e) {
            log.error("处理消息事件异常", e);
        }
    }
    
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
            
            // 创建外部消息事件，转发给ExternalMessageBridge处理
            applicationContext.publishEvent(new ExternalMessageEvent(this, "message_json", messageJson));
            log.debug("已创建ExternalMessageEvent转发MessageJsonEvent");
        } catch (Exception e) {
            log.error("处理MessageJsonEvent异常", e);
        }
    }
    
    /**
     * 专门监听MessageSendEvent事件
     */
    @EventListener
    public void handleMessageSendEvent(MessageSendEvent event) {
        try {
            if (messageBridge == null || event.getMessageJson() == null) {
                return;
            }
            
            // 将MessageProtobuf转为JSON
            String messageJson = JSON.toJSONString(event.getMessageJson());
            
            // 创建外部消息事件，转发给ExternalMessageBridge处理
            applicationContext.publishEvent(new ExternalMessageEvent(this, "message_send", messageJson));
            log.debug("已创建ExternalMessageEvent转发MessageSendEvent");
        } catch (Exception e) {
            log.error("处理MessageSendEvent异常", e);
        }
    }
    
    /**
     * 尝试从事件对象中提取消息JSON
     */
    private String extractMessageJson(Object event) {
        try {
            // 尝试直接将事件转换为字符串
            if (event instanceof String) {
                return (String) event;
            }
            
            // 尝试获取event.message或event.json
            try {
                // 尝试event.getMessage()
                java.lang.reflect.Method getMessageMethod = event.getClass().getMethod("getMessage");
                Object message = getMessageMethod.invoke(event);
                
                // 如果message是字符串，直接返回
                if (message instanceof String) {
                    return (String) message;
                }
                
                // 尝试将message转换为JSON字符串
                if (message != null) {
                    // 如果有toJson方法
                    try {
                        java.lang.reflect.Method toJsonMethod = message.getClass().getMethod("toJson");
                        Object jsonResult = toJsonMethod.invoke(message);
                        if (jsonResult instanceof String) {
                            return (String) jsonResult;
                        }
                    } catch (Exception ex) {
                        // 忽略此异常，继续尝试下一个方法
                    }
                    
                    // 使用FastJSON尝试将对象转换为JSON
                    return JSON.toJSONString(message);
                }
            } catch (Exception ex) {
                // 忽略此异常，继续尝试下一个方法
            }
            
            // 尝试event.getJson()
            try {
                java.lang.reflect.Method getJsonMethod = event.getClass().getMethod("getJson");
                Object json = getJsonMethod.invoke(event);
                if (json instanceof String) {
                    return (String) json;
                }
            } catch (Exception ex) {
                // 忽略此异常，继续尝试下一个方法
            }
            
            // 最后尝试直接使用FastJSON将整个事件对象转换为JSON
            return JSON.toJSONString(event);
        } catch (Exception e) {
            log.error("提取消息JSON失败", e);
            return null;
        }
    }
} 