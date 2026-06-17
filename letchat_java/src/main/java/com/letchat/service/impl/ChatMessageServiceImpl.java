package com.letchat.service.impl;

import com.alibaba.fastjson.JSON;
import com.letchat.ai.KimiAIService;
import com.letchat.config.AppConfig;
import com.letchat.config.RabbitMQConfig;
import com.letchat.entity.Constants;
import com.letchat.entity.dto.MessageSendDto;
import com.letchat.entity.dto.SysSettingDto;
import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.entity.enums.*;
import com.letchat.entity.po.ChatMessage;
import com.letchat.entity.po.ChatSession;
import com.letchat.entity.po.UserContact;
import com.letchat.entity.query.ChatMessageQuery;
import com.letchat.entity.query.ChatSessionQuery;
import com.letchat.entity.query.SimplePage;
import com.letchat.entity.query.UserContactQuery;
import com.letchat.entity.vo.PaginationResultVO;
import com.letchat.exception.BusinessException;
import com.letchat.mappers.ChatMessageMapper;
import com.letchat.mappers.ChatSessionMapper;
import com.letchat.mappers.UserContactMapper;
import com.letchat.mq.ChatMessageProducer;
import com.letchat.redis.RedisComponent;
import com.letchat.service.ChatMessageService;
import com.letchat.utils.CopyTools;
import com.letchat.utils.DateUtil;
import com.letchat.utils.StringTools;
import com.letchat.websocket.MessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;


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
    private AppConfig appConfig;

    @Resource
    private UserContactMapper<UserContact, UserContactQuery> userContactMapper;

    @Resource
    private ChatMessageProducer<ChatMessage> chatMessageProducer;

    @Resource
    private KimiAIService kimiAIService;

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

        //TODO CHAT类型初始也为0？
        Integer status = MessageTypeEnum.MEDIA_CHAT == messageTypeEnum ? MessageStatusEnum.SENDING.getStatus() : MessageStatusEnum.SENT.getStatus();
        chatMessage.setStatus(status);

        String messageContent = StringTools.cleanHtmlTag(chatMessage.getMessageContent());
        chatMessage.setMessageContent(messageContent);

        //记录消息表
        chatMessage.setSendUserId(sendUserId);
        chatMessage.setContactType(userContactTypeEnum.getType());

        // 发送MQ，异步保存消息
        chatMessageProducer.saveChatMessage(chatMessage);

        // 发送WebSocket消息
        MessageSendDto messageSendDto = CopyTools.copy(chatMessage, MessageSendDto.class);

        if (Constants.ROBOT_UID.equals(contactId)) {
            SysSettingDto sysSettingDto = redisComponent.getSysSetting();
            ChatMessage robotChatMessage = new ChatMessage();
            robotChatMessage.setSendUserId(sysSettingDto.getRobotUid());
            robotChatMessage.setSendUserNickName(sysSettingDto.getRobotNickName());
            robotChatMessage.setContactId(sendUserId);
            robotChatMessage.setMessageType(MessageTypeEnum.CHAT.getType());

            // 👇 调用 Kimi 获取 AI 回复
            try {
                String aiReply = kimiAIService.getAiReplyWithContext(sessionId, messageContent);
                robotChatMessage.setMessageContent(aiReply);
            } catch (Exception e) {
                log.error("调用 Kimi 失败", e);
                robotChatMessage.setMessageContent("AI 回复失败，请稍后再试。");
            }
            saveMessage(robotChatMessage);
        } else {
            //发送消息
            messageHandler.sendMessage(messageSendDto);
        }
        return messageSendDto;
    }

    @Override
    public void saveMessageFile(String userId, Long messageId, MultipartFile file, MultipartFile coverFile) {
        ChatMessage chatMessage = chatMessageMapper.selectByMessageId(messageId);
        if (chatMessage == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
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
        String fileRealName = messageId + fileExName;
        String month = DateUtil.format(new Date(chatMessage.getSendTime()), DateTimePatternEnum.YYYYMM.getPattern());
        File folder = new File(appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + month);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File uploadFile = new File(folder.getPath() + "/" + fileRealName);
        try {
            file.transferTo(uploadFile);
            coverFile.transferTo(new File(folder.getPath() + "/" + messageId + Constants.COVER_IMAGES_SUFFIX + fileExName));
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
    public File downloadFile(TokenUserInfoDto userInfoDto, Long messageId, Boolean showCover) {
        ChatMessage message = chatMessageMapper.selectByMessageId(messageId);
        String contactId = message.getContactId();
        UserContactTypeEnum contactTypeEnum = UserContactTypeEnum.getByPrefix(contactId);

        if (UserContactTypeEnum.USER == contactTypeEnum && !userInfoDto.getUserId().equals(message.getContactId())) {
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

        String month = DateUtil.format(new Date(message.getSendTime()), DateTimePatternEnum.YYYYMM.getPattern());
        File folder = new File(appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + month);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        String fileName = message.getFileName();
        String fileExtName = StringTools.getFileSuffix(fileName);
        String fileRealName = messageId + fileExtName;
        if (showCover != null && showCover) {
            fileRealName = messageId + Constants.COVER_IMAGES_SUFFIX + fileExtName;
        }
        File file = new File(folder.getPath() + "/" + fileRealName);
        if (!file.exists()) {
            log.error("文件不存在:{}", messageId);
            throw new BusinessException(ResponseCodeEnum.CODE_602);
        }
        return file;
    }
}