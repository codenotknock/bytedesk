package com.bytedesk.service.external;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.bytedesk.core.thread.ThreadProtobuf;
import com.bytedesk.core.topic.TopicRequest;
import com.bytedesk.core.topic.TopicUtils;
import com.bytedesk.core.topic.TopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bytedesk.core.message.IMessageSendService;
import com.bytedesk.core.message.MessageProtobuf;
import com.bytedesk.service.visitor.VisitorRequest;
import com.bytedesk.service.visitor.VisitorRestService;

import lombok.extern.slf4j.Slf4j;

/**
 * 外部消息适配器
 * 使用适配器模式而不是直接实现接口，避免循环依赖
 */
@Slf4j
@Service
public class ExternalMessageAdapter {

    // 默认技能组ID，如果未指定则使用此技能组接待
    private static final String DEFAULT_WORKGROUP_UID = "201";
    
    // 默认组织UID
    private static final String DEFAULT_ORG_UID = "df_org_uid";
    
    // 默认访客头像
    private static final String DEFAULT_VISITOR_AVATAR = "https://cdn.weiyuai.cn/avatars/visitor_default_avatar.png";
    
    // 存储用户名和线程ID的映射: username -> threadId
    private final Map<String, String> usernameThreadMap = new ConcurrentHashMap<>();
    
    @Autowired
    private IMessageSendService messageSendService;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Autowired
    private VisitorRestService visitorRestService;

    @Autowired
    private TopicService topicService;
    
    /**
     * 处理用户问题
     */
    public void handleUserQuestion(WebSocketSession session, JSONObject data, String username) {
        try {
            if (data == null) {
                sendErrorMessage(session, "消息格式错误: 缺少data字段");
                return;
            }
            
            String userNick = data.getString("userNick");
            String question = data.getString("question");
            String customerId = data.getString("customer");
            String threadId = data.getString("threadId");
//            String threadId = "1629641261449472";
            String messageId = data.getString("messageId"); // 可选，用于客户端消息确认
            String workgroupId = data.getString("workgroupId"); // 可选，指定技能组ID
            String orgUid = data.getString("orgUid") != null ? data.getString("orgUid") : "1628329459318912";
            String userAvatar = data.getString("userAvatar") != null ? data.getString("userAvatar") : DEFAULT_VISITOR_AVATAR;
            
            if (userNick == null || question == null) {
                sendErrorMessage(session, "消息格式错误: 缺少必要字段");
                return;
            }
            
            // 如果没有指定技能组ID，使用默认技能组
            if (workgroupId == null || workgroupId.isEmpty()) {
                workgroupId = DEFAULT_WORKGROUP_UID;
            }
            
            // 生成唯一用户ID (如果没有提供)
            String userUid = customerId != null ? customerId : "visitor_" + System.currentTimeMillis();
            
            // 1. 首先尝试创建或更新访客记录
            initializeVisitor(userUid, userNick, userAvatar, session);
            MessageProtobuf threadAndSendFirstMessage = null;
            // 2. 根据是否有threadId决定创建新会话或在现有会话发送消息
            if (threadId == null || threadId.isEmpty()) {
                // 创建新的会话
                threadId = String.valueOf(System.currentTimeMillis());
                usernameThreadMap.put(username, threadId);
                
                // 3. 创建新会话并发送首条消息
                threadAndSendFirstMessage = createThreadAndSendFirstMessage(username, userNick, question, threadId, userUid,
                        workgroupId, messageId, customerId, orgUid,
                        userAvatar, session);
            }
            // 4. 在现有会话中发送消息
            ThreadProtobuf thread = threadAndSendFirstMessage.getThread();
            sendVisitorMessage(username, userNick, question, thread.getUid(), userUid,
                    workgroupId, messageId, customerId, orgUid, userAvatar,
                    session);

            // 1. 通过事件发布，触发消息分发流程



        } catch (Exception e) {
            log.error("处理用户问题异常", e);
            try {
                sendErrorMessage(session, "处理用户问题异常: " + e.getMessage());
            } catch (IOException ex) {
                log.error("发送错误消息失败", ex);
            }
        }
    }
    
    /**
     * 初始化访客信息
     */
    private void initializeVisitor(String visitorUid, String nickname, String avatar, WebSocketSession session) {
        try {
            // 创建访客请求
            VisitorRequest visitorRequest = new VisitorRequest();
            visitorRequest.setUid(visitorUid);
            visitorRequest.setOrgUid(DEFAULT_ORG_UID);
            visitorRequest.setNickname(nickname);
            visitorRequest.setAvatar(avatar);
//            visitorRequest.setBrowser(JSON.toJSONString(session.getHandshakeHeaders().getFirst("User-Agent")));
//            visitorRequest.setDevice("{\"name\":\"Windows\",\"version\":\"10\"}");
            visitorRequest.setClient("WEB_VISITOR");

            // 调用访客服务初始化或更新访客
            visitorRestService.create(visitorRequest);
        } catch (Exception e) {
            log.error("初始化访客信息失败", e);
            // 不中断流程，即使访客记录创建失败
        }
    }
    
    /**
     * 创建欢迎消息和第一个访客问题
     */
    private MessageProtobuf createThreadAndSendFirstMessage(String username, String userNick, String question,
                                              String threadId, String userUid, String workgroupId,
                                              String messageId, String customerId, String orgUid, 
                                              String userAvatar, WebSocketSession session) {
        try {
            // 创建访客请求对象
            VisitorRequest visitorRequest = new VisitorRequest();
            visitorRequest.setUid(userUid);
            visitorRequest.setNickname(userNick);
            visitorRequest.setAvatar(userAvatar);
            visitorRequest.setContent(question);  // 使用BaseRequest中的content字段存储问题
            visitorRequest.setType(workgroupId);  // 使用BaseRequest中的type字段存储技能组ID
            visitorRequest.setOrgUid(orgUid);
            visitorRequest.setType("0");
            visitorRequest.setSid("1628329459318912");
//            visitorRequest.setSid("1628320097632384");
//            visitorRequest.setDevice("web");
            visitorRequest.setClient("WEB_VISITOR");
            visitorRequest.setForceAgent(true);
//            visitorRequest.setBrowser(session.getHandshakeHeaders().getFirst("User-Agent"));
            // 将额外信息存储在extra字段中
            JSONObject extraJson = new JSONObject();
            extraJson.put("threadId", threadId);
            if (customerId != null) {
                extraJson.put("customerId", customerId);
            }
            extraJson.put("source", "external_websocket");
            visitorRequest.setExtra(extraJson.toString());
            
            // 使用策略模式创建线程和消息
            MessageProtobuf messageProtobuf = visitorRestService.requestThread(visitorRequest);
//            TopicRequest request = TopicRequest.builder()
//                    .userUid(messageProtobuf.getThread().getUid())
//                    .build();
//            String topic = messageProtobuf.getThread().getTopic();
//            request.getTopics().add(topic);
//            request.getTopics().add(TopicUtils.formatTopicInternal(topic));
//            topicService.create(request);
            
            // 如果消息创建成功，回复会话创建成功
            if (session != null && session.isOpen()) {
                JSONObject response = new JSONObject();
                response.put("event", "thread-created");
                response.put("data",messageProtobuf.getContent());
                response.put("username", username);
                if (messageId != null) {
                    response.put("messageId", messageId);
                }
                session.sendMessage(new TextMessage(response.toJSONString()));
            }
            
            log.info("通过策略模式创建访客线程和消息成功");
            return messageProtobuf;
        } catch (Exception e) {
            log.error("创建欢迎消息和第一个访客问题异常", e);
            throw new RuntimeException("创建会话失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建客服用户JSON
     */
    private JSONObject createAgentJson() {
        JSONObject agentJson = new JSONObject();
        agentJson.put("uid", "system_agent");
        agentJson.put("nickname", "客服助手");
        agentJson.put("avatar", "https://cdn.weiyuai.cn/avatars/agent_default_avatar.png");
        agentJson.put("type", "AGENT");
        agentJson.put("extra", "{}");
        return agentJson;
    }
    
    /**
     * 发送访客问题消息
     */
    private void sendVisitorMessage(String username, String userNick, String question, String threadId, 
                                   String userUid, String workgroupId, String messageId, 
                                   String customerId, String orgUid, String userAvatar,
                                   WebSocketSession session) {
        try {
            // 创建访客请求对象
            VisitorRequest visitorRequest = new VisitorRequest();
            visitorRequest.setUid(userUid);
            visitorRequest.setNickname(userNick);
            visitorRequest.setAvatar(userAvatar);
            visitorRequest.setContent(question);  // 使用BaseRequest中的content字段存储问题
            visitorRequest.setType(workgroupId);  // 使用BaseRequest中的type字段存储技能组ID
            visitorRequest.setOrgUid(orgUid);
//            visitorRequest.setDevice("web");
            
            // 将额外信息存储在extra字段中
            JSONObject extraJson = new JSONObject();
            extraJson.put("threadId", threadId);
            if (customerId != null) {
                extraJson.put("customerId", customerId);
            }
            extraJson.put("messageId", messageId);
            extraJson.put("source", "external_websocket");
            visitorRequest.setExtra(extraJson.toString());
            
            // TODO: 实现通过策略模式发送消息的方法
            // 当前使用原有方式发送消息，但应该改为使用策略模式
            
            // 构建主题
            String topic = String.format("org/workgroup/%s/%s", workgroupId, userUid);
            
            // 创建访客用户信息
            JSONObject userJson = new JSONObject();
            userJson.put("uid", userUid);
            userJson.put("nickname", userNick);
            userJson.put("avatar", userAvatar);
            userJson.put("type", "VISITOR");
            userJson.put("extra", "{}");
            
            // 创建线程信息
            JSONObject threadJson = new JSONObject();
            threadJson.put("uid", threadId);
            threadJson.put("topic", topic);
            threadJson.put("type", "WORKGROUP");
            threadJson.put("status", "CHATTING");
            threadJson.put("user", userJson);
            threadJson.put("extra", "{}");
            
            // 创建访客消息
            JSONObject visitorMessageJson = new JSONObject();
            visitorMessageJson.put("uid", messageId != null ? messageId : UUID.randomUUID().toString().replace("-", ""));
            visitorMessageJson.put("type", "TEXT");
            visitorMessageJson.put("content", question);
            visitorMessageJson.put("status", "SENDING");
            visitorMessageJson.put("createdAt", LocalDateTime.now().toString());
            visitorMessageJson.put("client", null);
            visitorMessageJson.put("thread", threadJson);
            visitorMessageJson.put("user", userJson);
            visitorMessageJson.put("extra", createExtraJson(orgUid, workgroupId, customerId, "external_websocket"));
            
            log.info("发送访客消息: {}", visitorMessageJson.toString());
            
            // 1. 通过事件发布，触发消息分发流程
            //eventPublisher.publishEvent(new ExternalMessageEvent(this, "visitor_message", visitorMessageJson.toString()));
            
            // 2. 通过消息发送服务发送消息
            messageSendService.sendJsonMessage(visitorMessageJson.toString());
            
            // 回复消息发送成功
            if (session != null && session.isOpen()) {
                JSONObject response = new JSONObject();
                response.put("event", "message-sent");
                response.put("status", "success");
                response.put("threadId", threadId);
                response.put("message", visitorMessageJson); // 返回完整消息对象
                if (messageId != null) {
                    response.put("messageId", messageId);
                }
                session.sendMessage(new TextMessage(response.toJSONString()));
            }
        } catch (Exception e) {
            log.error("发送访客问题消息异常", e);
            throw new RuntimeException("发送消息失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建额外信息JSON
     */
    private String createExtraJson(String orgUid, String workgroupId, String customerId, String source) {
        JSONObject extraInfo = new JSONObject();
        extraInfo.put("orgUid", orgUid);
        extraInfo.put("workgroupId", workgroupId);
        if (customerId != null) {
            extraInfo.put("customerId", customerId);
        }
        extraInfo.put("source", source);
        return extraInfo.toJSONString();
    }
    
    /**
     * 处理重连
     */
    public void handleReconnect(WebSocketSession session, JSONObject json, String username) {
        try {
            JSONObject data = json.getJSONObject("data");
            String threadId = null;
            
            // 获取客户端提供的会话ID
            if (data != null) {
                threadId = data.getString("threadId");
                // 如果提供了用户名，更新用户名映射
                String providedUsername = data.getString("username");
                if (providedUsername != null && !providedUsername.isEmpty()) {
                    // 转移旧用户名的会话ID到新用户名
                    String existingThreadId = usernameThreadMap.get(username);
                    if (existingThreadId != null) {
                        usernameThreadMap.put(providedUsername, existingThreadId);
                    }
                    
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
        } catch (Exception e) {
            log.error("处理重连异常", e);
            try {
                sendErrorMessage(session, "处理重连异常: " + e.getMessage());
            } catch (IOException ex) {
                log.error("发送错误消息失败", ex);
            }
        }
    }
    
    /**
     * 处理心跳
     */
    public void handleHeartbeat(WebSocketSession session, JSONObject json) {
        try {
            JSONObject response = new JSONObject();
            response.put("event", "heartbeat");
            response.put("timestamp", System.currentTimeMillis());
            
            session.sendMessage(new TextMessage(response.toJSONString()));
        } catch (IOException e) {
            log.error("发送心跳响应异常", e);
        }
    }
    
    /**
     * 发送错误消息
     */
    public void sendErrorMessage(WebSocketSession session, String errorMessage) throws IOException {
        JSONObject response = new JSONObject();
        response.put("event", "error");
        response.put("message", errorMessage);
        response.put("timestamp", System.currentTimeMillis());
        
        session.sendMessage(new TextMessage(response.toJSONString()));
    }
    
    /**
     * 保存会话映射
     */
    public void saveThreadMapping(String username, String threadId) {
        usernameThreadMap.put(username, threadId);
    }
    
    /**
     * 获取会话ID
     */
    public String getThreadIdByUsername(String username) {
        return usernameThreadMap.get(username);
    }
} 