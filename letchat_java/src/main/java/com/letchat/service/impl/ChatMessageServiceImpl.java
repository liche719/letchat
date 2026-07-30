package com.letchat.service.impl;

import com.letchat.config.RabbitMQConfig;
import com.letchat.entity.Constants;
import com.letchat.entity.dto.MessageSendDto;
import com.letchat.entity.dto.SysSettingDto;
import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.entity.enums.*;
import com.letchat.entity.po.ChatMessage;
import com.letchat.entity.po.ChatMessageOutbox;
import com.letchat.entity.po.ChatSession;
import com.letchat.entity.po.UserContact;
import com.letchat.entity.query.ChatMessageQuery;
import com.letchat.entity.query.ChatSessionQuery;
import com.letchat.entity.query.SimplePage;
import com.letchat.entity.query.UserContactQuery;
import com.letchat.entity.vo.PaginationResultVO;
import com.letchat.exception.BusinessException;
import com.letchat.event.RobotReplyRequested;
import com.letchat.mappers.ChatMessageMapper;
import com.letchat.mappers.ChatMessageOutboxMapper;
import com.letchat.mappers.ChatMessageSequenceMapper;
import com.letchat.mappers.ChatMessageReceiptMapper;
import com.letchat.mappers.ChatSessionMapper;
import com.letchat.mappers.UserContactMapper;
import com.letchat.redis.RedisComponent;
import com.letchat.service.ChatMessageService;
import com.letchat.storage.FileStorage;
import com.letchat.storage.StoredObject;
import com.letchat.utils.CopyTools;
import com.letchat.utils.DateUtil;
import com.letchat.utils.JsonUtils;
import com.letchat.utils.MessageIdGenerator;
import com.letchat.utils.StringTools;
import com.letchat.websocket.MessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;


/**
 * 聊天消息表 业务接口实现
 */
@Service("chatMessageService")
@Slf4j
public class ChatMessageServiceImpl implements ChatMessageService {

    @Resource
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> chatMessageMapper;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private MessageHandler messageHandler;

    @Resource
    private ChatSessionMapper<ChatSession, ChatSessionQuery> chatSessionMapper;

    @Resource
    private UserContactMapper<UserContact, UserContactQuery> userContactMapper;

    @Resource
    private ChatMessageOutboxMapper chatMessageOutboxMapper;

    @Resource
    private ChatMessageSequenceMapper chatMessageSequenceMapper;

    @Resource
    private ChatMessageReceiptMapper chatMessageReceiptMapper;

    @Resource
    private FileStorage fileStorage;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Resource
    private MessageIdGenerator messageIdGenerator;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<ChatMessage> findListByParam(ChatMessageQuery param) {
        return this.chatMessageMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(ChatMessageQuery param) {
        return this.chatMessageMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<ChatMessage> findListByPage(ChatMessageQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<ChatMessage> list = this.findListByParam(param);
        PaginationResultVO<ChatMessage> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    @Override
    public PaginationResultVO<ChatMessage> loadChatMessages(String userId, String contactId, Integer pageNo) {
        String sessionId = resolveSessionId(userId, contactId);
        ChatMessageQuery query = new ChatMessageQuery();
        query.setSessionId(sessionId);
        query.setPageNo(pageNo == null ? 1 : pageNo);
        query.setPageSize(PageSize.SIZE20.getSize());
        query.setOrderBy("sequence_no desc");
        return findListByPage(query);
    }

    @Override
    public List<ChatMessage> syncMessages(String userId, String contactId, Long afterSequence, Integer limit) {
        String sessionId = resolveSessionId(userId, contactId);
        long cursor = afterSequence == null ? 0 : Math.max(0, afterSequence);
        int pageSize = limit == null ? 100 : Math.max(1, Math.min(500, limit));
        return chatMessageMapper.selectAfterSequence(sessionId, cursor, pageSize);
    }

    @Override
    public void acknowledgeDelivered(String userId, String contactId, Long sequenceNo) {
        if (sequenceNo == null || sequenceNo < 1) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        chatMessageReceiptMapper.acknowledgeDelivered(userId, resolveSessionId(userId, contactId), sequenceNo);
    }

    @Override
    public void acknowledgeRead(String userId, String contactId, Long sequenceNo) {
        if (sequenceNo == null || sequenceNo < 1) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        chatMessageReceiptMapper.acknowledgeRead(userId, resolveSessionId(userId, contactId), sequenceNo);
    }

    private String resolveSessionId(String userId, String contactId) {
        UserContactTypeEnum contactType = UserContactTypeEnum.getByPrefix(contactId);
        if (contactType == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        List<String> contactIds = redisComponent.getUserContactList(userId);
        if (contactIds == null || !contactIds.contains(contactId)) {
            throw new BusinessException(UserContactTypeEnum.USER == contactType
                    ? ResponseCodeEnum.CODE_902
                    : ResponseCodeEnum.CODE_903);
        }
        return UserContactTypeEnum.USER == contactType
                ? StringTools.getChatSessionId4User(new String[]{userId, contactId})
                : StringTools.getChatSessionId4Group(contactId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageSendDto saveMessage(ChatMessage chatMessage) {
        String sendUserId = chatMessage.getSendUserId();
        String contactId = chatMessage.getContactId();
        //不是机器人回复，判断好友状态
        if (!Constants.ROBOT_UID.equals(sendUserId)) {
            List<String> contactIdList = redisComponent.getUserContactList(sendUserId);
            if (!contactIdList.contains(chatMessage.getContactId())) {
                UserContactTypeEnum userContactStatusEnum = UserContactTypeEnum.getByPrefix(chatMessage.getContactId());
                if (UserContactTypeEnum.USER == userContactStatusEnum) {
                    throw new BusinessException(ResponseCodeEnum.CODE_902);
                } else {
                    throw new BusinessException(ResponseCodeEnum.CODE_903);
                }
            }
        }

        // 保存消息逻辑（从原saveMessage方法复制）
        String sessionId = null;
        UserContactTypeEnum userContactTypeEnum = UserContactTypeEnum.getByPrefix(contactId);

        // 存sessionId
        if (UserContactTypeEnum.USER == userContactTypeEnum) {
            sessionId = StringTools.getChatSessionId4User(new String[]{sendUserId, contactId});
        } else {
            sessionId = StringTools.getChatSessionId4Group(contactId);
        }
        chatMessage.setSessionId(sessionId);

        // 消息发送时间
        Long curTime = System.currentTimeMillis();
        chatMessage.setSendTime(curTime);

        // 消息状态
        MessageTypeEnum messageTypeEnum = MessageTypeEnum.getByType(chatMessage.getMessageType());
        if (null == messageTypeEnum || !ArrayUtils.contains(new Integer[]{MessageTypeEnum.CHAT.getType(), MessageTypeEnum.MEDIA_CHAT.getType()}, chatMessage.getMessageType())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        // 媒体消息在二进制文件上传完成前保持发送中状态。
        Integer status = MessageTypeEnum.MEDIA_CHAT == messageTypeEnum ? MessageStatusEnum.SENDING.getStatus() : MessageStatusEnum.SENT.getStatus();
        chatMessage.setStatus(status);

        String messageContent = StringTools.cleanHtmlTag(chatMessage.getMessageContent());
        chatMessage.setMessageContent(messageContent);

        //记录消息表
        chatMessage.setSendUserId(sendUserId);
        chatMessage.setContactType(userContactTypeEnum.getType());
        if (chatMessage.getMessageId() == null) {
            chatMessage.setMessageId(messageIdGenerator.nextId());
        }

        if (StringTools.isEmpty(chatMessage.getClientMessageId())) {
            chatMessage.setClientMessageId(UUID.randomUUID().toString());
        } else {
            ChatMessage existing = chatMessageMapper.selectByClientMessageId(chatMessage.getClientMessageId());
            if (existing != null) {
                if (!sendUserId.equals(existing.getSendUserId())) {
                    throw new BusinessException(ResponseCodeEnum.CODE_600);
                }
                return CopyTools.copy(existing, MessageSendDto.class);
            }
        }

        chatMessageSequenceMapper.allocateNext(sessionId);
        chatMessage.setSequenceNo(chatMessageSequenceMapper.lastAllocated());

        ChatMessage concurrentRetry = insertMessageOrGetExisting(chatMessage);
        if (concurrentRetry != null) {
            return CopyTools.copy(concurrentRetry, MessageSendDto.class);
        }
        chatSessionMapper.updateSummaryIfNewer(
                sessionId,
                buildLastMessage(chatMessage),
                chatMessage.getSendTime(),
                chatMessage.getSequenceNo()
        );

        chatMessageOutboxMapper.insert(new ChatMessageOutbox()
                .setEventId(chatMessage.getClientMessageId())
                .setSessionId(sessionId)
                .setRoutingKey(RabbitMQConfig.routingKeyForSession(sessionId))
                .setPayload(JsonUtils.convertObj2Json(chatMessage)));

        // 发送WebSocket消息
        MessageSendDto messageSendDto = CopyTools.copy(chatMessage, MessageSendDto.class);

        if (Constants.ROBOT_UID.equals(contactId)) {
            eventPublisher.publishEvent(new RobotReplyRequested(sessionId, sendUserId, messageContent));
        }
        return messageSendDto;
    }

    private ChatMessage insertMessageOrGetExisting(ChatMessage chatMessage) {
        try {
            chatMessageMapper.insertMessage(chatMessage);
            return null;
        } catch (DuplicateKeyException exception) {
            ChatMessage existing = chatMessageMapper.selectByClientMessageId(chatMessage.getClientMessageId());
            if (existing != null && chatMessage.getSendUserId().equals(existing.getSendUserId())) {
                return existing;
            }
            throw exception;
        }
    }

    @Override
    public void saveMessageFile(String userId, Long messageId, MultipartFile file, MultipartFile coverFile) {
        ChatMessage chatMessage = chatMessageMapper.selectByMessageId(messageId);
        if (chatMessage == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_602);
        }
        if (!userId.equals(chatMessage.getSendUserId())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        SysSettingDto sysSettingDto = redisComponent.getSysSetting();
        String fileSuffix = StringTools.getFileSuffix(file.getOriginalFilename());
        if (!StringTools.isEmpty(fileSuffix) && ArrayUtils.contains(Constants.IMAGE_SUFFIX_LIST, fileSuffix.toUpperCase()) && file.getSize() > sysSettingDto.getMaxImageSize() * Constants.FILE_SIZE_MB) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        } else if (!StringTools.isEmpty(fileSuffix) && ArrayUtils.contains(Constants.VIDEO_SUFFIX_LIST, fileSuffix.toUpperCase()) && file.getSize() > sysSettingDto.getMaxVideoSize() * Constants.FILE_SIZE_MB) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        } else if (!StringTools.isEmpty(fileSuffix) && !ArrayUtils.contains(Constants.IMAGE_SUFFIX_LIST, fileSuffix.toUpperCase()) && !ArrayUtils.contains(Constants.VIDEO_SUFFIX_LIST, fileSuffix.toUpperCase()) && file.getSize() > sysSettingDto.getMaxFileSize() * Constants.FILE_SIZE_MB) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        String fileName = file.getOriginalFilename();
        String fileExName = StringTools.getFileSuffix(fileName);
        String month = DateUtil.format(new Date(chatMessage.getSendTime()), DateTimePatternEnum.YYYYMM.getPattern());
        String objectKey = messageObjectKey(month, messageId, fileExName, false);
        String coverObjectKey = messageObjectKey(month, messageId, fileExName, true);
        try {
            fileStorage.put(objectKey, file.getInputStream(), file.getSize(), file.getContentType());
            fileStorage.put(coverObjectKey, coverFile.getInputStream(), coverFile.getSize(), coverFile.getContentType());
        } catch (IOException e) {
            log.error("上传文件失败", e);
            throw new BusinessException("上传文件失败");
        }

        ChatMessage uploadInfo = new ChatMessage();
        uploadInfo.setStatus(MessageStatusEnum.SENT.getStatus());
        uploadInfo.setFileSize(file.getSize());
        uploadInfo.setFileName(fileName);
        uploadInfo.setFileType(MessageTypeEnum.MEDIA_CHAT.getType());
        ChatMessageQuery chatMessageQuery = new ChatMessageQuery();
        chatMessageQuery.setMessageId(messageId);
        chatMessageMapper.updateByParam(uploadInfo, chatMessageQuery);

        MessageSendDto messageSendDto = new MessageSendDto();
        messageSendDto.setStatus(MessageStatusEnum.SENT.getStatus());
        messageSendDto.setMessageId(messageId);
        messageSendDto.setContactId(chatMessage.getContactId());
        messageSendDto.setMessageType(MessageTypeEnum.FILE_UPLOAD.getType());
        messageHandler.sendMessage(messageSendDto);
    }

    @Override
    public StoredObject downloadFile(TokenUserInfoDto userInfoDto, Long messageId, Boolean showCover) {
        ChatMessage message = chatMessageMapper.selectByMessageId(messageId);
        if (message == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_602);
        }
        String contactId = message.getContactId();
        UserContactTypeEnum contactTypeEnum = UserContactTypeEnum.getByPrefix(contactId);

        if (UserContactTypeEnum.USER == contactTypeEnum
                && !userInfoDto.getUserId().equals(message.getSendUserId())
                && !userInfoDto.getUserId().equals(message.getContactId())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        if (UserContactTypeEnum.GROUP == contactTypeEnum) {
            UserContactQuery userContactQuery = new UserContactQuery();
            userContactQuery.setUserId(userInfoDto.getUserId());
            userContactQuery.setContactType(UserContactTypeEnum.GROUP.getType());
            userContactQuery.setContactId(contactId);
            userContactQuery.setStatus(UserContactStatusEnum.FRIEND.getStatus());
            Integer contactCount = userContactMapper.selectCount(userContactQuery);
            if (contactCount == 0) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
        }

        if (contactTypeEnum == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        String month = DateUtil.format(new Date(message.getSendTime()), DateTimePatternEnum.YYYYMM.getPattern());
        String fileExtName = StringTools.getFileSuffix(message.getFileName());
        return fileStorage.get(messageObjectKey(month, messageId, fileExtName, Boolean.TRUE.equals(showCover)));
    }

    private String messageObjectKey(String month, Long messageId, String extension, boolean cover) {
        String suffix = cover ? Constants.COVER_IMAGES_SUFFIX + extension : extension;
        return "messages/" + month + "/" + messageId + suffix;
    }

    private String buildLastMessage(ChatMessage chatMessage) {
        if (UserContactTypeEnum.GROUP == UserContactTypeEnum.getByPrefix(chatMessage.getContactId())) {
            return chatMessage.getSendUserNickName() + ": " + chatMessage.getMessageContent();
        }
        return chatMessage.getMessageContent();
    }
}
