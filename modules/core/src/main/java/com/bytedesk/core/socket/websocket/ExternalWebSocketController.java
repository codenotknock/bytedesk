package com.bytedesk.core.socket.websocket;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bytedesk.core.utils.JsonResult;

/**
 * @author fuzhouling
 * @date 2025/04/16
 * @program bytedesk
 * @description 外部服务WebSocket控制器，提供WebSocket文档和测试接口
 **/
@RestController
@RequestMapping("/api/v1/external")
public class ExternalWebSocketController {

    /**
     * 获取WebSocket接口文档
     */
    @GetMapping("/websocket/docs")
    public ResponseEntity<?> getWebSocketDocs() {
        // 返回WebSocket接口文档
        return ResponseEntity.ok(JsonResult.success(getWebSocketApiDocs()));
    }
    
    /**
     * 获取WebSocket测试页面
     */
    @GetMapping("/websocket/test")
    public String getWebSocketTestPage() {
        // 返回简单的HTML页面用于测试WebSocket连接
        return "<!DOCTYPE html>\n" +
               "<html>\n" +
               "<head>\n" +
               "    <title>微语外部服务WebSocket测试</title>\n" +
               "    <meta charset=\"UTF-8\">\n" +
               "    <style>\n" +
               "        body { font-family: Arial, sans-serif; margin: 20px; }\n" +
               "        .container { max-width: 800px; margin: 0 auto; }\n" +
               "        .form-group { margin-bottom: 15px; }\n" +
               "        label { display: block; margin-bottom: 5px; }\n" +
               "        input, textarea { width: 100%; padding: 8px; box-sizing: border-box; }\n" +
               "        button { padding: 10px 15px; background: #4CAF50; color: white; border: none; cursor: pointer; }\n" +
               "        button:disabled { background: #cccccc; cursor: not-allowed; }\n" +
               "        .messages { border: 1px solid #ddd; padding: 10px; height: 300px; overflow-y: auto; margin-top: 20px; }\n" +
               "        .message { margin-bottom: 10px; padding: 8px; border-radius: 5px; }\n" +
               "        .sent { background-color: #f1f1f1; text-align: right; }\n" +
               "        .received { background-color: #e1ffc7; }\n" +
               "        .status { color: #666; font-style: italic; }\n" +
               "        .debug { font-size: 12px; color: #999; margin-top: 5px; }\n" +
               "    </style>\n" +
               "</head>\n" +
               "<body>\n" +
               "    <div class=\"container\">\n" +
               "        <h1>微语外部服务WebSocket测试</h1>\n" +
               "        \n" +
               "        <div class=\"form-group\">\n" +
               "            <label for=\"serverUrl\">WebSocket服务器地址:</label>\n" +
               "            <input type=\"text\" id=\"serverUrl\" value=\"ws://localhost:8080/api/v1/external/websocket?token=test_token\">\n" +
               "        </div>\n" +
               "        \n" +
               "        <div class=\"form-group\">\n" +
               "            <button id=\"connectBtn\">连接</button>\n" +
               "            <button id=\"disconnectBtn\" disabled>断开</button>\n" +
               "            <button id=\"reconnectBtn\" disabled>重连</button>\n" +
               "        </div>\n" +
               "        \n" +
               "        <div class=\"form-group\">\n" +
               "            <label for=\"userNick\">用户昵称:</label>\n" +
               "            <input type=\"text\" id=\"userNick\" value=\"测试用户\">\n" +
               "        </div>\n" +
               "        \n" +
               "        <div class=\"form-group\">\n" +
               "            <label for=\"message\">消息内容:</label>\n" +
               "            <textarea id=\"message\" rows=\"4\">您好，我有一个问题需要咨询</textarea>\n" +
               "        </div>\n" +
               "        \n" +
               "        <div class=\"form-group\">\n" +
               "            <button id=\"sendBtn\" disabled>发送</button>\n" +
               "        </div>\n" +
               "        \n" +
               "        <div class=\"messages\" id=\"messageContainer\">\n" +
               "            <div class=\"status\">状态: 未连接</div>\n" +
               "        </div>\n" +
               "    </div>\n" +
               "    \n" +
               "    <script>\n" +
               "        let socket;\n" +
               "        let threadId = null;\n" +
               "        let heartbeatInterval;\n" +
               "        let reconnectAttempts = 0;\n" +
               "        const MAX_RECONNECT_ATTEMPTS = 5;\n" +
               "        \n" +
               "        // 更新状态显示\n" +
               "        function updateStatus(status) {\n" +
               "            const container = document.getElementById('messageContainer');\n" +
               "            const statusElement = container.querySelector('.status');\n" +
               "            if (statusElement) {\n" +
               "                statusElement.textContent = '状态: ' + status;\n" +
               "            } else {\n" +
               "                const newStatus = document.createElement('div');\n" +
               "                newStatus.className = 'status';\n" +
               "                newStatus.textContent = '状态: ' + status;\n" +
               "                container.prepend(newStatus);\n" +
               "            }\n" +
               "        }\n" +
               "        \n" +
               "        // 连接服务器\n" +
               "        function connectToServer(isReconnect = false) {\n" +
               "            const serverUrl = document.getElementById('serverUrl').value;\n" +
               "            \n" +
               "            try {\n" +
               "                updateStatus('正在连接...');\n" +
               "                socket = new WebSocket(serverUrl);\n" +
               "                \n" +
               "                socket.onopen = function(event) {\n" +
               "                    reconnectAttempts = 0;\n" +
               "                    updateStatus('已连接');\n" +
               "                    addMessage('系统', '连接成功！', 'received');\n" +
               "                    document.getElementById('connectBtn').disabled = true;\n" +
               "                    document.getElementById('disconnectBtn').disabled = false;\n" +
               "                    document.getElementById('reconnectBtn').disabled = false;\n" +
               "                    document.getElementById('sendBtn').disabled = false;\n" +
               "                    \n" +
               "                    // 连接成功后启动心跳\n" +
               "                    startHeartbeat();\n" +
               "                    \n" +
               "                    // 如果是重连，发送重连请求\n" +
               "                    if (isReconnect && threadId) {\n" +
               "                        const reconnectMessage = {\n" +
               "                            event: 'reconnect',\n" +
               "                            data: {\n" +
               "                                threadId: threadId\n" +
               "                            }\n" +
               "                        };\n" +
               "                        socket.send(JSON.stringify(reconnectMessage));\n" +
               "                    }\n" +
               "                };\n" +
               "                \n" +
               "                socket.onmessage = function(event) {\n" +
               "                    const response = JSON.parse(event.data);\n" +
               "                    console.log('收到消息:', response);\n" +
               "                    \n" +
               "                    // 添加调试信息\n" +
               "                    const debugInfo = document.createElement('div');\n" +
               "                    debugInfo.className = 'debug';\n" +
               "                    debugInfo.textContent = '收到消息: ' + JSON.stringify(response);\n" +
               "                    document.getElementById('messageContainer').appendChild(debugInfo);\n" +
               "                    \n" +
               "                    if (response.event === 'heartbeat') {\n" +
               "                        // 心跳响应，不处理\n" +
               "                        return;\n" +
               "                    }\n" +
               "                    \n" +
               "                    if (response.event === 'connect') {\n" +
               "                        // 连接成功\n" +
               "                        if (response.threadId) {\n" +
               "                            threadId = response.threadId;\n" +
               "                            addMessage('系统', '恢复到之前的会话，ID: ' + threadId, 'received');\n" +
               "                        }\n" +
               "                        return;\n" +
               "                    }\n" +
               "                    \n" +
               "                    if (response.event === 'reconnect') {\n" +
               "                        // 重连成功\n" +
               "                        if (response.threadId) {\n" +
               "                            threadId = response.threadId;\n" +
               "                            addMessage('系统', '重连成功，会话ID: ' + threadId, 'received');\n" +
               "                        } else {\n" +
               "                            addMessage('系统', '重连成功，但没有找到之前的会话', 'received');\n" +
               "                        }\n" +
               "                        return;\n" +
               "                    }\n" +
               "                    \n" +
               "                    if (response.event === 'thread-created') {\n" +
               "                        // 会话创建成功\n" +
               "                        const data = response.data;\n" +
               "                        threadId = data.thread.uid;\n" +
               "                        addMessage('系统', '会话创建成功，ID: ' + threadId, 'received');\n" +
               "                        \n" +
               "                        // 显示欢迎消息\n" +
               "                        if (data.content) {\n" +
               "                            addMessage(data.user.nickname, data.content, 'received');\n" +
               "                        }\n" +
               "                        return;\n" +
               "                    }\n" +
               "                    \n" +
               "                    if (response.event === 'message-sent') {\n" +
               "                        // 消息发送成功\n" +
               "                        if (!threadId && response.threadId) {\n" +
               "                            threadId = response.threadId;\n" +
               "                        }\n" +
               "                        return;\n" +
               "                    }\n" +
               "                    \n" +
               "                    if (response.event === 'agent-reply') {\n" +
               "                        // 客服回复\n" +
               "                        const data = response.data;\n" +
               "                        addMessage(data.user.nickname, data.content, 'received');\n" +
               "                        return;\n" +
               "                    }\n" +
               "                    \n" +
               "                    if (response.event === 'error') {\n" +
               "                        // 错误消息\n" +
               "                        addMessage('系统错误', response.message, 'received');\n" +
               "                        return;\n" +
               "                    }\n" +
               "                    \n" +
               "                    // 其他消息\n" +
               "                    addMessage('系统', JSON.stringify(response), 'received');\n" +
               "                };\n" +
               "                \n" +
               "                socket.onclose = function(event) {\n" +
               "                    stopHeartbeat();\n" +
               "                    updateStatus('连接已关闭');\n" +
               "                    addMessage('系统', '连接已关闭: ' + (event.reason || '未知原因'), 'received');\n" +
               "                    document.getElementById('connectBtn').disabled = false;\n" +
               "                    document.getElementById('disconnectBtn').disabled = true;\n" +
               "                    document.getElementById('reconnectBtn').disabled = false;\n" +
               "                    document.getElementById('sendBtn').disabled = true;\n" +
               "                    \n" +
               "                    // 尝试自动重连\n" +
               "                    if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {\n" +
               "                        reconnectAttempts++;\n" +
               "                        const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000);\n" +
               "                        addMessage('系统', `连接断开，${delay/1000}秒后尝试自动重连(${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})...`, 'received');\n" +
               "                        setTimeout(() => connectToServer(true), delay);\n" +
               "                    }\n" +
               "                };\n" +
               "                \n" +
               "                socket.onerror = function(error) {\n" +
               "                    updateStatus('连接错误');\n" +
               "                    addMessage('系统', '连接错误', 'received');\n" +
               "                };\n" +
               "            } catch (error) {\n" +
               "                updateStatus('连接失败');\n" +
               "                addMessage('系统', '创建WebSocket连接失败: ' + error.message, 'received');\n" +
               "            }\n" +
               "        }\n" +
               "        \n" +
               "        // 开始心跳\n" +
               "        function startHeartbeat() {\n" +
               "            stopHeartbeat(); // 先停止可能存在的心跳\n" +
               "            \n" +
               "            heartbeatInterval = setInterval(function() {\n" +
               "                if (socket && socket.readyState === WebSocket.OPEN) {\n" +
               "                    const heartbeat = {\n" +
               "                        event: 'heartbeat',\n" +
               "                        timestamp: Date.now()\n" +
               "                    };\n" +
               "                    socket.send(JSON.stringify(heartbeat));\n" +
               "                } else {\n" +
               "                    stopHeartbeat();\n" +
               "                }\n" +
               "            }, 30000); // 30秒发送一次心跳\n" +
               "        }\n" +
               "        \n" +
               "        // 停止心跳\n" +
               "        function stopHeartbeat() {\n" +
               "            if (heartbeatInterval) {\n" +
               "                clearInterval(heartbeatInterval);\n" +
               "                heartbeatInterval = null;\n" +
               "            }\n" +
               "        }\n" +
               "        \n" +
               "        // 添加消息到界面\n" +
               "        function addMessage(sender, content, type) {\n" +
               "            const container = document.getElementById('messageContainer');\n" +
               "            const messageDiv = document.createElement('div');\n" +
               "            messageDiv.className = 'message ' + type;\n" +
               "            \n" +
               "            const senderSpan = document.createElement('strong');\n" +
               "            senderSpan.textContent = sender + ': ';\n" +
               "            \n" +
               "            const contentSpan = document.createElement('span');\n" +
               "            contentSpan.textContent = content;\n" +
               "            \n" +
               "            messageDiv.appendChild(senderSpan);\n" +
               "            messageDiv.appendChild(contentSpan);\n" +
               "            container.appendChild(messageDiv);\n" +
               "            \n" +
               "            // 滚动到底部\n" +
               "            container.scrollTop = container.scrollHeight;\n" +
               "        }\n" +
               "        \n" +
               "        // 连接按钮事件\n" +
               "        document.getElementById('connectBtn').addEventListener('click', function() {\n" +
               "            connectToServer();\n" +
               "        });\n" +
               "        \n" +
               "        // 断开按钮事件\n" +
               "        document.getElementById('disconnectBtn').addEventListener('click', function() {\n" +
               "            if (socket) {\n" +
               "                socket.close();\n" +
               "            }\n" +
               "        });\n" +
               "        \n" +
               "        // 重连按钮事件\n" +
               "        document.getElementById('reconnectBtn').addEventListener('click', function() {\n" +
               "            if (socket) {\n" +
               "                socket.close();\n" +
               "            }\n" +
               "            setTimeout(() => connectToServer(true), 500);\n" +
               "        });\n" +
               "        \n" +
               "        // 发送按钮事件\n" +
               "        document.getElementById('sendBtn').addEventListener('click', function() {\n" +
               "            const userNick = document.getElementById('userNick').value;\n" +
               "            const message = document.getElementById('message').value;\n" +
               "            \n" +
               "            if (!socket || socket.readyState !== WebSocket.OPEN) {\n" +
               "                addMessage('系统', '连接未建立，无法发送消息', 'received');\n" +
               "                return;\n" +
               "            }\n" +
               "            \n" +
               "            if (!userNick || !message) {\n" +
               "                addMessage('系统', '用户昵称和消息内容不能为空', 'received');\n" +
               "                return;\n" +
               "            }\n" +
               "            \n" +
               "            const messageId = 'msg_' + Date.now();\n" +
               "            const payload = {\n" +
               "                event: 'user-question',\n" +
               "                data: {\n" +
               "                    userNick: userNick,\n" +
               "                    question: message,\n" +
               "                    format: 'text',\n" +
               "                    messageId: messageId\n" +
               "                }\n" +
               "            };\n" +
               "            \n" +
               "            // 如果已有会话ID，则添加到请求中\n" +
               "            if (threadId) {\n" +
               "                payload.data.threadId = threadId;\n" +
               "            }\n" +
               "            \n" +
               "            socket.send(JSON.stringify(payload));\n" +
               "            addMessage(userNick, message, 'sent');\n" +
               "            \n" +
               "            // 清空消息输入框\n" +
               "            document.getElementById('message').value = '';\n" +
               "        });\n" +
               "    </script>\n" +
               "</body>\n" +
               "</html>";
    }
    
    private Object getWebSocketApiDocs() {
        return new Object() {
            public final String websocketEndpoint = "/api/v1/external/websocket?token=YOUR_TOKEN";
            
            public final Object[] eventTypes = new Object[] {
                new Object() {
                    public final String event = "user-question";
                    public final String description = "发送用户问题";
                    public final Object requestFormat = new Object() {
                        public final String event = "user-question";
                        public final Object data = new Object() {
                            public final String userNick = "用户昵称";
                            public final String question = "用户问题内容";
                            public final String format = "文本格式: text或markdown";
                            public final String threadId = "会话ID（可选，首次发送可不传）";
                            public final String customer = "客服ID（可选）";
                            public final String messageId = "消息ID（可选，用于客户端消息确认）";
                        };
                    };
                    public final Object responseFormat = new Object() {
                        public final String event = "message-sent";
                        public final String status = "success";
                        public final String threadId = "会话ID";
                        public final String messageId = "消息ID";
                    };
                },
                new Object() {
                    public final String event = "heartbeat";
                    public final String description = "心跳保持连接";
                    public final Object requestFormat = new Object() {
                        public final String event = "heartbeat";
                        public final long timestamp = 1657123456789L;
                    };
                    public final Object responseFormat = new Object() {
                        public final String event = "heartbeat";
                        public final long timestamp = 1657123456789L;
                    };
                },
                new Object() {
                    public final String event = "reconnect";
                    public final String description = "重新连接会话";
                    public final Object requestFormat = new Object() {
                        public final String event = "reconnect";
                        public final Object data = new Object() {
                            public final String threadId = "会话ID（可选）";
                        };
                    };
                    public final Object responseFormat = new Object() {
                        public final String event = "reconnect";
                        public final String status = "success";
                        public final String sessionId = "会话ID";
                        public final String threadId = "会话ID（如果存在）";
                        public final long timestamp = 1657123456789L;
                    };
                }
            };
            
            public final Object[] serverEvents = new Object[] {
                new Object() {
                    public final String event = "agent-reply";
                    public final String description = "客服回复";
                    public final Object format = new Object() {
                        public final String event = "agent-reply";
                        public final Object data = new Object() {
                            public final Object user = new Object() {
                                public final String nickname = "客服昵称";
                            };
                            public final String content = "回复内容";
                            public final Object thread = new Object() {
                                public final String uid = "会话ID";
                            };
                            public final String type = "消息类型";
                            public final String uid = "消息ID";
                        };
                    };
                },
                new Object() {
                    public final String event = "thread-created";
                    public final String description = "会话创建成功";
                    public final Object format = new Object() {
                        public final String event = "thread-created";
                        public final Object data = new Object() {
                            public final Object user = new Object() {
                                public final String nickname = "客服昵称";
                            };
                            public final String content = "欢迎消息";
                            public final Object thread = new Object() {
                                public final String uid = "会话ID";
                                public final String topic = "会话主题";
                            };
                            public final String type = "消息类型";
                            public final String uid = "消息ID";
                        };
                    };
                },
                new Object() {
                    public final String event = "connect";
                    public final String description = "连接成功";
                    public final Object format = new Object() {
                        public final String event = "connect";
                        public final String status = "success";
                        public final String sessionId = "会话标识";
                        public final String threadId = "之前的会话ID（如果存在）";
                        public final long timestamp = 1657123456789L;
                    };
                },
                new Object() {
                    public final String event = "error";
                    public final String description = "错误消息";
                    public final Object format = new Object() {
                        public final String event = "error";
                        public final String message = "错误信息";
                        public final long timestamp = 1657123456789L;
                    };
                }
            };
        };
    }
} 