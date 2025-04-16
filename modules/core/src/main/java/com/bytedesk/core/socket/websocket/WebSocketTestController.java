package com.bytedesk.core.socket.websocket;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @author fuzhouling
 * @date 2025/04/16
 * @program bytedesk
 * @description WebSocket测试控制器
 **/
@Controller
public class WebSocketTestController {

    /**
     * WebSocket测试页面
     */
    @GetMapping("/websocket-test")
    public String websocketTest() {
        return "websocket-test";
    }
} 