# 微语外部服务WebSocket集成指南

本文档介绍如何通过WebSocket将外部服务与微语客服系统进行集成，实现实时消息交互。

## 1. 概述

微语提供WebSocket接口，允许外部服务通过WebSocket连接直接与微语系统进行实时消息交互。这种方式相比REST API有以下优势：

- **实时性更强**：消息可以立即推送，无需轮询
- **资源消耗更低**：避免频繁的HTTP连接建立和断开
- **双向通信**：客户端和服务器都可以随时发送消息
- **适合高并发**：能够处理大量用户的并发连接

## 2. 连接地址

WebSocket连接地址：`ws://your-server:port/api/v1/external/websocket?token=YOUR_TOKEN`

- 测试环境：`ws://localhost:9003/api/v1/external/websocket?token=YOUR_TOKEN`
- 生产环境：`wss://your-domain.com/api/v1/external/websocket?token=YOUR_TOKEN`

> 注意：生产环境强烈建议使用WSS（WebSocket Secure）协议以确保通信安全。

## 3. 认证方式

WebSocket连接需要提供认证token，有以下两种方式：

1. **URL参数方式**：在WebSocket连接URL中添加`token`参数
   ```
   ws://your-server:port/api/v1/external/websocket?token=YOUR_TOKEN
   ```

2. **请求头方式**：在WebSocket连接请求头中添加`token`字段
   ```
   headers: {
     'token': 'YOUR_TOKEN'
   }
   ```

## 4. 消息格式

所有消息都采用JSON格式，包含`event`字段指明消息类型。

### 4.1 客户端发送消息格式

#### 发送用户问题
```json
{
  "event": "user-question",
  "data": {
    "userNick": "用户昵称",
    "question": "用户问题内容",
    "format": "text",
    "threadId": "会话ID（可选，首次发送可不传）",
    "customer": "客服ID（可选）"
  }
}
```

#### 心跳消息
```json
{
  "event": "heartbeat",
  "timestamp": 1657123456789
}
```

### 4.2 服务器返回消息格式

#### 客服回复
```json
{
  "event": "agent-reply",
  "data": {
    "userNick": "用户昵称",
    "agentName": "客服名称",
    "content": "回复内容",
    "threadId": "会话ID",
    "messageId": "消息ID",
    "timestamp": 1657123456789,
    "type": "消息类型"
  }
}
```

#### 会话创建成功
```json
{
  "event": "thread-created",
  "data": {
    "thread": {
      "uid": "会话ID",
      "topic": "会话主题",
      "type": "会话类型"
    }
  }
}
```

#### 错误消息
```json
{
  "event": "error",
  "message": "错误信息"
}
```

## 5. 典型交互流程

1. **建立连接**：外部服务与微语WebSocket服务器建立连接
2. **发送用户问题**：外部服务发送用户问题到微语
3. **接收会话创建消息**：首次发送问题时，微语会创建会话并返回会话信息
4. **接收客服回复**：微语将客服的回复通过WebSocket推送给外部服务
5. **保持心跳**：定期发送心跳消息保持连接活跃
6. **关闭连接**：不再需要时关闭连接

## 6. 代码示例

### 6.1 Python示例（使用socketio库）

```python
import socketio
import time
import json
import logging

class BytedeskClient:
    def __init__(self, server_url, token, customer_id=None, platform=None):
        self.server_url = server_url
        self.token = token
        self.customer_id = customer_id
        self.platform = platform
        self.connected = False
        self.reconnect_count = 0
        self.max_reconnect_attempts = 10
        self.should_reconnect = True
        self.logger = logging.getLogger("BytedeskClient")
        
        # 创建WebSocket客户端
        self.ws = None
        
    def connect(self):
        """连接到微语WebSocket服务器"""
        import websocket
        
        # 定义WebSocket事件处理函数
        def on_message(ws, message):
            try:
                data = json.loads(message)
                event_type = data.get("event")
                
                if event_type == "heartbeat":
                    # 心跳响应，不做特殊处理
                    return
                    
                elif event_type == "agent-reply":
                    # 客服回复
                    self.logger.info(f"收到客服回复: {data}")
                    # 处理客服回复...
                    
                elif event_type == "thread-created":
                    # 会话创建成功
                    self.logger.info(f"会话创建成功: {data}")
                    # 保存会话ID...
                    
                elif event_type == "error":
                    # 错误消息
                    self.logger.error(f"收到错误消息: {data}")
                    
                else:
                    self.logger.info(f"收到未知消息类型: {data}")
                    
            except Exception as e:
                self.logger.error(f"处理消息异常: {e}")
        
        def on_error(ws, error):
            self.logger.error(f"WebSocket错误: {error}")
            self.connected = False
            
        def on_close(ws, close_status_code, close_msg):
            self.logger.info(f"WebSocket连接关闭: {close_msg}")
            self.connected = False
            
            # 如果需要自动重连
            if self.should_reconnect:
                self._reconnect()
        
        def on_open(ws):
            self.logger.info("WebSocket连接已建立")
            self.connected = True
            self.reconnect_count = 0
            
            # 启动心跳
            self._start_heartbeat()
        
        # 构建连接URL
        url = f"{self.server_url}?token={self.token}"
        
        # 创建WebSocket连接
        self.ws = websocket.WebSocketApp(
            url,
            on_open=on_open,
            on_message=on_message,
            on_error=on_error,
            on_close=on_close
        )
        
        # 启动WebSocket连接（在新线程中）
        import threading
        wst = threading.Thread(target=self.ws.run_forever)
        wst.daemon = True
        wst.start()
        
        return True
    
    def _reconnect(self):
        """重连逻辑"""
        if self.reconnect_count < self.max_reconnect_attempts:
            self.reconnect_count += 1
            delay = min(30, 2 ** self.reconnect_count)  # 指数退避策略
            
            self.logger.info(f"尝试重连 ({self.reconnect_count}/{self.max_reconnect_attempts}) {delay}秒后...")
            time.sleep(delay)
            
            self.connect()
        else:
            self.logger.warning(f"达到最大重连次数 ({self.max_reconnect_attempts})，停止重连")
    
    def _start_heartbeat(self):
        """启动心跳"""
        def send_heartbeat():
            while self.connected:
                try:
                    if self.ws and self.connected:
                        heartbeat = {
                            "event": "heartbeat",
                            "timestamp": int(time.time() * 1000)
                        }
                        self.ws.send(json.dumps(heartbeat))
                except Exception as e:
                    self.logger.error(f"发送心跳异常: {e}")
                
                # 30秒发送一次心跳
                time.sleep(30)
        
        import threading
        heart_thread = threading.Thread(target=send_heartbeat)
        heart_thread.daemon = True
        heart_thread.start()
    
    def send_user_question(self, user_nick, question, thread_id=None, format="text"):
        """发送用户问题"""
        if not self.connected or not self.ws:
            self.logger.warning("WebSocket未连接，无法发送消息")
            return False
        
        try:
            # 构建消息
            payload = {
                "event": "user-question",
                "data": {
                    "userNick": user_nick,
                    "question": question,
                    "format": format
                }
            }
            
            # 添加会话ID（如果有）
            if thread_id:
                payload["data"]["threadId"] = thread_id
                
            # 添加客服ID（如果有）
            if self.customer_id:
                payload["data"]["customer"] = self.customer_id
            
            # 发送消息
            self.ws.send(json.dumps(payload))
            return True
        except Exception as e:
            self.logger.error(f"发送用户问题异常: {e}")
            return False
    
    def disconnect(self):
        """断开连接"""
        self.should_reconnect = False
        if self.ws:
            self.ws.close()
            self.connected = False
```

### 6.2 JavaScript示例

```javascript
class BytedeskClient {
    constructor(serverUrl, token, options = {}) {
        this.serverUrl = serverUrl;
        this.token = token;
        this.customerId = options.customerId;
        this.platform = options.platform;
        this.socket = null;
        this.connected = false;
        this.reconnectCount = 0;
        this.maxReconnectAttempts = 10;
        this.shouldReconnect = true;
        this.threadId = null;
        
        // 回调函数
        this.onConnected = options.onConnected || (() => {});
        this.onDisconnected = options.onDisconnected || (() => {});
        this.onError = options.onError || (() => {});
        this.onAgentReply = options.onAgentReply || (() => {});
        this.onThreadCreated = options.onThreadCreated || (() => {});
    }
    
    connect() {
        try {
            // 构建WebSocket URL
            const url = `${this.serverUrl}?token=${this.token}`;
            
            // 创建WebSocket连接
            this.socket = new WebSocket(url);
            
            // 连接成功
            this.socket.onopen = (event) => {
                console.log('WebSocket连接已建立');
                this.connected = true;
                this.reconnectCount = 0;
                
                // 启动心跳
                this.startHeartbeat();
                
                // 触发连接成功回调
                this.onConnected();
            };
            
            // 接收消息
            this.socket.onmessage = (event) => {
                try {
                    const response = JSON.parse(event.data);
                    const eventType = response.event;
                    
                    switch (eventType) {
                        case 'heartbeat':
                            // 心跳响应，不做特殊处理
                            break;
                            
                        case 'agent-reply':
                            // 客服回复
                            console.log('收到客服回复:', response);
                            this.onAgentReply(response.data);
                            break;
                            
                        case 'thread-created':
                            // 会话创建成功
                            console.log('会话创建成功:', response);
                            this.threadId = response.data.thread.uid;
                            this.onThreadCreated(response.data);
                            break;
                            
                        case 'error':
                            // 错误消息
                            console.error('收到错误消息:', response);
                            this.onError(response.message);
                            break;
                            
                        default:
                            console.log('收到未知消息类型:', response);
                    }
                } catch (error) {
                    console.error('处理消息异常:', error);
                }
            };
            
            // 连接关闭
            this.socket.onclose = (event) => {
                console.log('WebSocket连接已关闭:', event.reason);
                this.connected = false;
                
                // 触发断开连接回调
                this.onDisconnected();
                
                // 如果需要自动重连
                if (this.shouldReconnect) {
                    this.reconnect();
                }
            };
            
            // 连接错误
            this.socket.onerror = (error) => {
                console.error('WebSocket连接错误:', error);
                this.onError('WebSocket连接错误');
            };
            
            return true;
        } catch (error) {
            console.error('创建WebSocket连接失败:', error);
            return false;
        }
    }
    
    reconnect() {
        if (this.reconnectCount < this.maxReconnectAttempts) {
            this.reconnectCount++;
            const delay = Math.min(30, Math.pow(2, this.reconnectCount));
            
            console.log(`尝试重连 (${this.reconnectCount}/${this.maxReconnectAttempts}) ${delay}秒后...`);
            
            setTimeout(() => {
                this.connect();
            }, delay * 1000);
        } else {
            console.warn(`达到最大重连次数 (${this.maxReconnectAttempts})，停止重连`);
        }
    }
    
    startHeartbeat() {
        // 清除可能存在的旧心跳
        if (this.heartbeatInterval) {
            clearInterval(this.heartbeatInterval);
        }
        
        // 设置新的心跳
        this.heartbeatInterval = setInterval(() => {
            if (this.connected && this.socket && this.socket.readyState === WebSocket.OPEN) {
                const heartbeat = {
                    event: 'heartbeat',
                    timestamp: Date.now()
                };
                
                this.socket.send(JSON.stringify(heartbeat));
            }
        }, 30000); // 30秒发送一次心跳
    }
    
    sendUserQuestion(userNick, question, options = {}) {
        if (!this.connected || !this.socket || this.socket.readyState !== WebSocket.OPEN) {
            console.warn('WebSocket未连接，无法发送消息');
            return false;
        }
        
        try {
            // 构建消息
            const payload = {
                event: 'user-question',
                data: {
                    userNick: userNick,
                    question: question,
                    format: options.format || 'text'
                }
            };
            
            // 添加会话ID（如果有）
            if (options.threadId || this.threadId) {
                payload.data.threadId = options.threadId || this.threadId;
            }
            
            // 添加客服ID（如果有）
            if (options.customerId || this.customerId) {
                payload.data.customer = options.customerId || this.customerId;
            }
            
            // 发送消息
            this.socket.send(JSON.stringify(payload));
            return true;
        } catch (error) {
            console.error('发送用户问题异常:', error);
            return false;
        }
    }
    
    disconnect() {
        this.shouldReconnect = false;
        
        if (this.heartbeatInterval) {
            clearInterval(this.heartbeatInterval);
            this.heartbeatInterval = null;
        }
        
        if (this.socket) {
            this.socket.close();
            this.connected = false;
        }
    }
}
```

## 7. 注意事项

1. **保持心跳**：每30秒发送一次心跳消息，避免连接被服务器关闭
2. **重连机制**：实现指数退避的重连策略，避免频繁重连给服务器造成压力
3. **安全性**：生产环境使用WSS协议和IP白名单限制
4. **会话管理**：保存会话ID，后续发送消息时带上会话ID
5. **错误处理**：妥善处理各种异常情况，避免程序崩溃

## 8. 测试工具

微语提供了WebSocket测试页面，方便调试和测试：

- 测试页面地址：`http://your-server:port/api/v1/external/websocket/test`
- API文档地址：`http://your-server:port/api/v1/external/websocket/docs`

## 9. 常见问题

1. **连接被频繁断开**
   - 检查心跳是否正常发送
   - 检查网络环境是否稳定
   - 检查服务器是否限制了连接时长

2. **无法收到客服回复**
   - 确认会话ID是否正确
   - 检查客服是否在线
   - 确认消息是否成功发送

3. **消息发送失败**
   - 检查WebSocket连接状态
   - 检查消息格式是否正确
   - 检查token是否有效

## 10. 联系支持

如果您在集成过程中遇到任何问题，请联系微语技术支持：

- 邮箱：support@bytedesk.com
- 技术支持群：微信扫码加入 

## 11. 文件结构说明

我们为微语系统添加了以下文件，实现了WebSocket功能：

1. **ExternalWebSocketHandler.java**：WebSocket处理器，处理外部服务的连接和消息
   - 处理连接建立、消息接收、连接关闭等事件
   - 实现了消息路由和转发功能
   - 处理心跳和用户问题等不同类型的消息

2. **WebSocketConfig.java**：WebSocket配置类
   - 配置WebSocket端点和拦截器
   - 实现握手拦截器进行token验证
   - 设置允许的来源和其他WebSocket相关配置

3. **ExternalMessageListener.java**：消息监听器
   - 监听客服回复消息事件
   - 通过WebSocket将客服回复推送给外部服务
   - 处理不同类型的消息和会话状态

4. **ExternalWebSocketController.java**：控制器
   - 提供WebSocket文档和测试接口
   - 返回API文档和测试页面
   - 帮助开发者测试和集成WebSocket功能

5. **README.md**：接口说明文档
   - 详细介绍WebSocket接口的使用方法
   - 提供代码示例和最佳实践
   - 说明常见问题和注意事项 