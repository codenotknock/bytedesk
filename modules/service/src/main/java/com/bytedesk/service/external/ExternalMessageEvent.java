package com.bytedesk.service.external;

import org.springframework.context.ApplicationEvent;

/**
 * 外部消息事件，用于在服务层内部传递消息
 */
public class ExternalMessageEvent extends ApplicationEvent {
    
    private static final long serialVersionUID = 1L;
    
    private final String type;
    private final String messageJson;
    
    public ExternalMessageEvent(Object source, String type, String messageJson) {
        super(source);
        this.type = type;
        this.messageJson = messageJson;
    }
    
    public String getType() {
        return type;
    }
    
    public String getMessageJson() {
        return messageJson;
    }
} 