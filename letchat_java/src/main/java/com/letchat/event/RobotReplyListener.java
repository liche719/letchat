package com.letchat.event;

import com.letchat.ai.KimiAIService;
import com.letchat.entity.dto.SysSettingDto;
import com.letchat.entity.enums.MessageTypeEnum;
import com.letchat.entity.po.ChatMessage;
import com.letchat.redis.RedisComponent;
import com.letchat.service.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class RobotReplyListener {

    private final KimiAIService kimiAIService;
    private final RedisComponent redisComponent;
    private final ChatMessageService chatMessageService;

    public RobotReplyListener(KimiAIService kimiAIService,
                              RedisComponent redisComponent,
                              ChatMessageService chatMessageService) {
        this.kimiAIService = kimiAIService;
        this.redisComponent = redisComponent;
        this.chatMessageService = chatMessageService;
    }

    @Async("robotReplyExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void reply(RobotReplyRequested event) {
        SysSettingDto settings = redisComponent.getSysSetting();
        String reply;
        try {
            reply = kimiAIService.getAiReplyWithContext(event.sessionId(), event.messageContent());
        } catch (Exception exception) {
            log.error("Robot AI reply failed, sessionId={}", event.sessionId(), exception);
            reply = "AI 回复暂时不可用，请稍后再试。";
        }
        ChatMessage message = new ChatMessage()
                .setSendUserId(settings.getRobotUid())
                .setSendUserNickName(settings.getRobotNickName())
                .setContactId(event.userId())
                .setMessageType(MessageTypeEnum.CHAT.getType())
                .setMessageContent(reply);
        chatMessageService.saveMessage(message);
    }
}
