package com.bytedesk.ai.springai.base;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.bytedesk.ai.robot.RobotConsts;
import com.bytedesk.ai.robot.RobotEntity;
import com.bytedesk.ai.robot.RobotProtobuf;
import com.bytedesk.ai.robot.RobotRestService;
import com.bytedesk.ai.robot_message.RobotMessageCache;
import com.bytedesk.ai.robot_message.RobotMessageRequest;
import com.bytedesk.ai.springai.spring.SpringAIService;
import com.bytedesk.ai.springai.spring.SpringAIVectorService;
import com.bytedesk.core.enums.ClientEnum;
import com.bytedesk.core.message.IMessageSendService;
import com.bytedesk.core.message.MessageExtra;
import com.bytedesk.core.message.MessagePersistCache;
import com.bytedesk.core.message.MessageProtobuf;
import com.bytedesk.core.message.MessageTypeEnum;
import com.bytedesk.core.thread.ThreadRestService;
import com.bytedesk.core.uid.UidUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseSpringAIService implements SpringAIService {

    @Autowired(required = false)
    protected Optional<SpringAIVectorService> springAIVectorService;

    @Autowired
    protected IMessageSendService messageSendService;

    @Autowired
    protected UidUtils uidUtils;

    @Autowired
    protected RobotRestService robotRestService;

    @Autowired
    protected ThreadRestService threadRestService;

    @Autowired
    protected MessagePersistCache messagePersistCache;

    @Autowired
    protected RobotMessageCache robotMessageCache;

    // 可以添加更多自动注入的依赖，而不需要修改子类构造函数

    // 保留一个无参构造函数，或者只接收特定的必需依赖
    protected BaseSpringAIService() {
        // 无参构造函数
    }

    // 可以保留一个带参数的构造函数用于单元测试或特殊情况
    protected BaseSpringAIService(Optional<SpringAIVectorService> springAIVectorService,
            IMessageSendService messageSendService) {
        this.springAIVectorService = springAIVectorService;
        this.messageSendService = messageSendService;
    }

    @Override
    public void sendWebsocketMessage(String query, RobotEntity robot, MessageProtobuf messageProtobuf) {
        Assert.hasText(query, "Query must not be empty");
        Assert.notNull(robot, "RobotEntity must not be null");
        Assert.notNull(messageProtobuf, "MessageProtobuf must not be null");
        Assert.isTrue(springAIVectorService.isPresent(), "SpringAIVectorService must be present");

        String prompt = "";
        if (StringUtils.hasText(robot.getKbUid()) && robot.isKbEnabled()) {
            List<String> contentList = springAIVectorService.get().searchText(query, robot.getKbUid());
            String context = String.join("\n", contentList);
            prompt = buildKbPrompt(robot.getLlm().getPrompt(), query, context);
        } else {
            prompt = robot.getLlm().getPrompt();
        }
        //
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(prompt));
        messages.add(new UserMessage(query));
        //
        Prompt aiPrompt = new Prompt(messages);
        processPrompt(aiPrompt, messageProtobuf);
    }

    @Override
    public void sendSseMessage(String query, RobotProtobuf robot, MessageProtobuf messageProtobufQuery,
            MessageProtobuf messageProtobufReply, SseEmitter emitter) {
        Assert.hasText(query, "Query must not be empty");
        Assert.notNull(emitter, "SseEmitter must not be null");
        // sendSseTypingMessage(messageProtobuf, emitter);
        // 判断是否开启大模型
        if (robot.getLlm().isEnabled()) {
            // 启用大模型
            processLlmResponse(query, robot, messageProtobufQuery, messageProtobufReply, emitter);
        } else {
            // 未开启大模型，关键词匹配，使用搜索
            processSearchResponse(query, robot, messageProtobufQuery, messageProtobufReply, emitter);
        }

    }

    private void processLlmResponse(String query, RobotProtobuf robot, MessageProtobuf messageProtobufQuery,
            MessageProtobuf messageProtobufReply, SseEmitter emitter) {
        //
        String prompt = "";
        if (StringUtils.hasText(robot.getKbUid()) && robot.getIsKbEnabled()) {
            List<String> contentList = springAIVectorService.get().searchText(query, robot.getKbUid());
            if (contentList.isEmpty()) {
                // 直接返回未找到相关问题答案
                String answer = RobotConsts.ROBOT_UNMATCHED;
                processAnswerMessage(answer, robot, messageProtobufQuery, messageProtobufReply, emitter);
                return;
            }
            String context = String.join("\n", contentList);
            // TODO: 根据配置，拉取历史聊天记录
            // String history = "";
            prompt = buildKbPrompt(robot.getLlm().getPrompt(), query, context);
        } else {
            prompt = robot.getLlm().getPrompt();
        }
        // TODO: 返回消息中携带消息搜索结果(来源依据)
        //
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(prompt));
        messages.add(new UserMessage(query));
        log.info("BaseSpringAIService sendSseMemberMessage messages {}", messages);
        //
        Prompt aiPrompt = new Prompt(messages);
        processPromptSSE(aiPrompt, messageProtobufQuery, messageProtobufReply, emitter);
    }

    private void processSearchResponse(String query, RobotProtobuf robot, MessageProtobuf messageProtobufQuery,
            MessageProtobuf messageProtobufReply, SseEmitter emitter) {

        if (StringUtils.hasText(robot.getKbUid()) && robot.getIsKbEnabled()) {
            List<String> contentList = springAIVectorService.get().searchText(query, robot.getKbUid());
            if (contentList.isEmpty()) {
                // 直接返回未找到相关问题答案
                String answer = RobotConsts.ROBOT_UNMATCHED;
                processAnswerMessage(answer, robot, messageProtobufQuery, messageProtobufReply, emitter);
                return;
            } else {
                // 搜索到内容，返回搜索内容
                String answer = String.join("\n", contentList);
                processAnswerMessage(answer, robot, messageProtobufQuery, messageProtobufReply, emitter);
            }
        } else {
            // 未设置知识库
            // 直接返回未找到相关问题答案
            String answer = RobotConsts.ROBOT_UNMATCHED;
            processAnswerMessage(answer, robot, messageProtobufQuery, messageProtobufReply, emitter);
        }
    }

    private void processAnswerMessage(String answer, RobotProtobuf robot, MessageProtobuf messageProtobufQuery,
            MessageProtobuf messageProtobufReply, SseEmitter emitter) {
        messageProtobufReply.setType(MessageTypeEnum.TEXT);
        messageProtobufReply.setContent(answer);
        messageProtobufReply.setClient(ClientEnum.SYSTEM);
        // 保存消息到数据库
        persistMessage(messageProtobufQuery, messageProtobufReply);
        String messageJson = messageProtobufReply.toJson();
        try {
            // 发送SSE事件
            emitter.send(SseEmitter.event()
                    .data(messageJson)
                    .id(messageProtobufReply.getUid())
                    .name("message"));
        } catch (Exception e) {
            log.error("BaseSpringAIService sendSseMemberMessage Error sending SSE event 1：", e);
            emitter.completeWithError(e);
        }
    }

    @Override
    public String generateFaqPairsAsync(String chunk) {
        if (!StringUtils.hasText(chunk)) {
            return "";
        }
        String prompt = RobotConsts.PROMPT_LLM_GENERATE_FAQ_TEMPLATE.replace("{chunk}", chunk);
        return generateFaqPairs(prompt);
    }

    @Override
    public void generateFaqPairsSync(String chunk) {
        Assert.hasText(chunk, "Chunk must not be empty");

        String prompt = RobotConsts.PROMPT_LLM_GENERATE_FAQ_TEMPLATE.replace("{chunk}", chunk);
        int maxRetries = 3;
        int retryCount = 0;
        int retryDelay = 1000;

        while (retryCount < maxRetries) {
            try {
                String result = generateFaqPairs(prompt);
                log.info("FAQ generation result: {}", result);
                return;
            } catch (Exception e) {
                retryCount++;
                if (retryCount == maxRetries) {
                    log.error("Failed to generate FAQ pairs after {} retries", maxRetries, e);
                    throw new RuntimeException("Failed to generate FAQ pairs", e);
                }

                try {
                    Thread.sleep(retryDelay * (1 << (retryCount - 1)));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while retrying", ie);
                }
            }
        }
    }

    @Override
    public void persistMessage(MessageProtobuf messageProtobufQuery, MessageProtobuf messageProtobufReply) {
        Assert.notNull(messageProtobufQuery, "MessageProtobufQuery must not be null");
        Assert.notNull(messageProtobufReply, "MessageProtobufReply must not be null");
        messagePersistCache.pushForPersist(messageProtobufReply.toJson());
        //
        MessageExtra extraObject = MessageExtra.fromJson(messageProtobufReply.getExtra());
        //
        // 记录未找到相关答案的问题到另外一个表，便于梳理问题
        RobotMessageRequest robotMessage = RobotMessageRequest.builder()
                .uid(messageProtobufReply.getUid()) // 使用机器人回复消息作为uid
                .type(messageProtobufQuery.getType().name())
                .status(messageProtobufReply.getStatus().name())
                //
                .topic(messageProtobufQuery.getThread().getTopic())
                .threadUid(messageProtobufQuery.getThread().getUid())
                //
                .content(messageProtobufQuery.getContent())
                .answer(messageProtobufReply.getContent())
                //
                .user(messageProtobufQuery.getUser().toJson())
                .robot(messageProtobufReply.getUser().toJson())
                //
                .isUnAnswered(messageProtobufReply.getContent().equals(RobotConsts.ROBOT_UNMATCHED))
                .orgUid(extraObject.getOrgUid())
                //
                .build();
        robotMessageCache.pushRequest(robotMessage);

    }

    // private void sendSseTypingMessage(MessageProtobuf messageProtobuf, SseEmitter
    // emitter) {
    // //
    // MessageProtobuf clonedMessage = SerializationUtils.clone(messageProtobuf);
    // clonedMessage.setUid(messageProtobuf.getUid());
    // clonedMessage.setType(MessageTypeEnum.TYPING);
    // // clonedMessage.setContent(I18Consts.I18N_TYPING);
    // // clonedMessage.setContent("...");
    // try {
    // emitter.send(SseEmitter.event()
    // .data(JSON.toJSONString(clonedMessage))
    // .id(clonedMessage.getUid())
    // .name("message"));
    // } catch (Exception e) {
    // // TODO: handle exception
    // }
    // }

    public String buildKbPrompt(String systemPrompt, String query, String context) {
        return systemPrompt + "\n" +
                "用户查询: " + query + "\n" +
                "历史聊天记录: " + "\n" +
                "搜索结果: " + context;
    }

    // 抽象方法，由具体实现类提供
    protected abstract void processPrompt(Prompt prompt, MessageProtobuf messageProtobuf);

    protected abstract String processPromptSync(String message);

    protected abstract void processPromptSSE(Prompt prompt, MessageProtobuf messageProtobufQuery,
            MessageProtobuf messageProtobufReply, SseEmitter emitter);

    // 抽象方法，由具体实现类提供
    protected abstract String generateFaqPairs(String prompt);
}