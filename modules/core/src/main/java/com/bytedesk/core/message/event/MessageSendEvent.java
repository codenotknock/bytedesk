/*
 * @Author: jackning 270580156@qq.com
 * @Date: 2024-05-16 10:12:03
 * @LastEditors: jackning 270580156@qq.com
 * @LastEditTime: 2024-05-16 10:12:06
 * @Description: bytedesk.com https://github.com/Bytedesk/bytedesk
 *   Please be aware of the BSL license restrictions before installing Bytedesk IM – 
 *  selling, reselling, or hosting Bytedesk IM as a service is a breach of the terms and automatically terminates your rights under the license.
 *  Business Source License 1.1: https://github.com/Bytedesk/bytedesk/blob/main/LICENSE 
 *  contact: 270580156@qq.com 
 *  联系：270580156@qq.com
 * Copyright (c) 2024 by bytedesk.com, All Rights Reserved. 
 */
package com.bytedesk.core.message.event;

import org.springframework.context.ApplicationEvent;

import lombok.Getter;

/**
 * 消息发送事件
 */
@Getter
public class MessageSendEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    private final String messageJson;

    /**
     * 创建消息发送事件
     * 
     * @param source 事件源
     * @param messageJson 消息JSON字符串
     */
    public MessageSendEvent(Object source, String messageJson) {
        super(source);
        this.messageJson = messageJson;
    }
} 