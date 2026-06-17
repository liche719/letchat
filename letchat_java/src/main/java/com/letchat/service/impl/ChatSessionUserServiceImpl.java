package com.letchat.service.impl;

import com.letchat.entity.dto.MessageSendDto;
import com.letchat.entity.enums.MessageTypeEnum;
import com.letchat.entity.enums.UserContactStatusEnum;
import com.letchat.entity.enums.UserContactTypeEnum;
import com.letchat.entity.po.ChatSessionUser;
import com.letchat.entity.po.UserContact;
import com.letchat.entity.query.ChatSessionUserQuery;
import com.letchat.entity.query.UserContactQuery;
import com.letchat.mappers.ChatSessionUserMapper;
import com.letchat.mappers.UserContactMapper;
import com.letchat.service.ChatSessionUserService;
import com.letchat.websocket.MessageHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;


/**
 * 会话用户 业务接口实现
 */
@Service("chatSessionUserService")
public class ChatSessionUserServiceImpl implements ChatSessionUserService {

    @Resource
    private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> chatSessionUserMapper;

    @Resource
    private MessageHandler messageHandler;

    @Resource
    private UserContactMapper<UserContact, UserContactQuery> userContactMapper;


    @Override
    public void updateContactName(String contactId, String contactNameUpdate) {

        ChatSessionUser chatSessionUser = new ChatSessionUser();
        chatSessionUser.setContactName(contactNameUpdate);

        ChatSessionUserQuery chatSessionUserQuery = new ChatSessionUserQuery();
        chatSessionUserQuery.setContactId(contactId);
        this.chatSessionUserMapper.updateByParam(chatSessionUser, chatSessionUserQuery);

        UserContactTypeEnum contactType = UserContactTypeEnum.getByPrefix(contactId);

        if (contactType == UserContactTypeEnum.GROUP) {
            MessageSendDto messageSendDto = new MessageSendDto();
            messageSendDto.setContactType(contactType.getType());
            messageSendDto.setContactId(contactId);
            messageSendDto.setContactName(contactNameUpdate);
            messageSendDto.setMessageType(MessageTypeEnum.CONTACT_NAME_UPDATE.getType());
            messageHandler.sendMessage(messageSendDto);
        } else {
            UserContactQuery userContactQuery = new UserContactQuery();
            userContactQuery.setContactType(UserContactTypeEnum.USER.getType());
            userContactQuery.setContactId(contactId);
            userContactQuery.setStatus(UserContactStatusEnum.FRIEND.getStatus());
            List<UserContact> userContactList = userContactMapper.selectList(userContactQuery);
            for (UserContact userContact : userContactList) {
                MessageSendDto messageSendDto = new MessageSendDto();
                messageSendDto.setContactType(contactType.getType());
                messageSendDto.setContactId(userContact.getUserId());
                messageSendDto.setContactName(contactNameUpdate);
                messageSendDto.setMessageType(MessageTypeEnum.CONTACT_NAME_UPDATE.getType());
                messageSendDto.setSendUserId(contactId);
                messageSendDto.setSendUserNickName(contactNameUpdate);
                messageHandler.sendMessage(messageSendDto);
            }
        }
    }

}