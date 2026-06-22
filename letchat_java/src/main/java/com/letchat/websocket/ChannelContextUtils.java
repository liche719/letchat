package com.letchat.websocket;

import com.letchat.entity.Constants;
import com.letchat.entity.dto.MessageSendDto;
import com.letchat.entity.dto.WsInitData;
import com.letchat.entity.enums.MessageTypeEnum;
import com.letchat.entity.enums.UserContactApplyStatusEnum;
import com.letchat.entity.enums.UserContactTypeEnum;
import com.letchat.entity.po.ChatMessage;
import com.letchat.entity.po.ChatSessionUser;
import com.letchat.entity.po.UserContactApply;
import com.letchat.entity.po.UserInfo;
import com.letchat.entity.query.ChatMessageQuery;
import com.letchat.entity.query.ChatSessionUserQuery;
import com.letchat.entity.query.UserContactApplyQuery;
import com.letchat.entity.query.UserInfoQuery;
import com.letchat.mappers.ChatMessageMapper;
import com.letchat.mappers.ChatSessionUserMapper;
import com.letchat.mappers.UserContactApplyMapper;
import com.letchat.mappers.UserInfoMapper;
import com.letchat.redis.RedisComponent;
import com.letchat.utils.JsonUtils;
import com.letchat.utils.StringTools;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ChannelContextUtils {

    private static final ConcurrentHashMap<String, Channel> USER_CONTEXT_MAP = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, ChannelGroup> GROUP_CONTEXT_MAP = new ConcurrentHashMap<>();

    private static final AttributeKey<String> USER_ID_ATTRIBUTE = AttributeKey.valueOf("letchat.userId");

    @Resource
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    @Resource
    private ChatSessionUserMapper<ChatSessionUser, ChatSessionUserQuery> chatSessionUserMapper;

    @Resource
    private ChatMessageMapper<ChatMessage, ChatMessageQuery> chatMessageMapper;

    @Resource
    private UserContactApplyMapper<UserContactApply, UserContactApplyQuery> userContactApplyMapper;

    @Resource
    private RedisComponent redisComponent;


    //添加用户连接
    public void addContext(String userId, Channel channel) {
        bindUserChannel(userId, channel);

        List<String> contactIdList = redisComponent.getUserContactList(userId);
        System.out.println("用户" + userId + "的群组：" + contactIdList);
        for (String groupId : contactIdList) {
            if (groupId.startsWith(UserContactTypeEnum.GROUP.getPrefix())) {
                add2Group(groupId, channel);
            }
        }

        redisComponent.saveHeartBeat(userId);

        //更新用户最后连接时间
        UserInfo updateInfo = new UserInfo();
        updateInfo.setLastLoginTime(new Date());
        userInfoMapper.updateByUserId(updateInfo, userId);

        //给用户发送信息
        UserInfo userInfo = userInfoMapper.selectByUserId(userId);
        Long sourceLastOffTime = userInfo.getLastOffTime();
        Long lastOffTime = sourceLastOffTime;
        if (sourceLastOffTime != null && System.currentTimeMillis() - sourceLastOffTime > Constants.MillisSECONDS_3days_ago) {
            lastOffTime = Constants.MillisSECONDS_3days_ago;
        }

        /*
         * 1.查询会话信息 查询用户所有的会话信息
         */
        ChatSessionUserQuery sessionUserQuery = new ChatSessionUserQuery();
        sessionUserQuery.setUserId(userId);
        sessionUserQuery.setOrderBy("last_receive_time desc");
        List<ChatSessionUser> chatSessionUserList = chatSessionUserMapper.selectList(sessionUserQuery);

        WsInitData wsInitData = new WsInitData();
        wsInitData.setChatSessionUserList(chatSessionUserList);

        /*
         * 2.查询聊天消息
         */
        //查询所有联系人
        List<String> groupIdList = contactIdList.stream().filter(item -> item.startsWith(UserContactTypeEnum.GROUP.getPrefix())).collect(Collectors.toList());
        groupIdList.add(userId);
        ChatMessageQuery chatMessageQuery = new ChatMessageQuery();
        chatMessageQuery.setContactIdList(groupIdList);
        chatMessageQuery.setLastReceiveTime(lastOffTime);
        List<ChatMessage> chatMessageList = chatMessageMapper.selectList(chatMessageQuery);
        wsInitData.setChatMessagesList(chatMessageList);

        /*
         *3. 查询申请信息
         */
        UserContactApplyQuery applyQuery = new UserContactApplyQuery();
        applyQuery.setApplyUserId(userId);
        applyQuery.setLastApplyTimeStamp(lastOffTime);
        applyQuery.setStatus(UserContactApplyStatusEnum.INIT.getStatus());
        Integer applyCount = userContactApplyMapper.selectCount(applyQuery);
        wsInitData.setApplyCount(applyCount);

        //发送消息
        MessageSendDto messageSendDto = new MessageSendDto();
        messageSendDto.setMessageType(MessageTypeEnum.INIT.getType());
        messageSendDto.setContactId(userId);
        messageSendDto.setExtendData(wsInitData);

        sendMsg(messageSendDto, userId);


    }

    private void add2Group(String groupId, Channel channel) {
        if (channel == null) {
            return;
        }
        ChannelGroup group = getOrCreateGroup(groupId);
        group.add(channel);
    }

    public void removeContext(Channel channel) {
        String userId = getUserId(channel);
        if (StringTools.isEmpty(userId)) {
            removeChannelFromGroups(channel);
            return;
        }
        boolean removedCurrentChannel = USER_CONTEXT_MAP.remove(userId, channel);
        removeChannelFromGroups(channel);
        if (!removedCurrentChannel) {
            return;
        }
        redisComponent.removeUserHeartBeat(userId);
        //更新用户最后离线时间
        UserInfo updateInfo = new UserInfo();
        updateInfo.setLastOffTime(new Date().getTime());
        userInfoMapper.updateByUserId(updateInfo, userId);
    }

    public void sendMessage(MessageSendDto messageSendDto) {
        UserContactTypeEnum contactTypeEnum = UserContactTypeEnum.getByPrefix(messageSendDto.getContactId());
        switch (contactTypeEnum) {
            case USER:
                send2User(messageSendDto);
                break;
            case GROUP:
                send2Group(messageSendDto);
                break;
        }
    }

    //发送给单个用户
    private void send2User(MessageSendDto messageSendDto) {
        String contactId = messageSendDto.getContactId();
        if (StringTools.isEmpty(contactId)) {
            return;
        }
        sendMsg(messageSendDto, contactId);
        //强制下线
        if (MessageTypeEnum.FORCE_OFF_LINE.getType().equals(messageSendDto.getMessageType())) {
            closeContext(contactId);
        }
    }

    //发送消息
    public void sendMsg(MessageSendDto messageSendDto, String receiveId) {
        Channel userChannel = USER_CONTEXT_MAP.get(receiveId);
        if (userChannel == null) {
            return;
        }
        //相对客户端而言，联系人就是发送人，所以转换一下再发
        if (messageSendDto.getMessageType().equals(MessageTypeEnum.ADD_FRIEND_SELF.getType())) {
            UserInfo userInfo = (UserInfo) messageSendDto.getExtendData();
            messageSendDto.setMessageType(MessageTypeEnum.ADD_FRIEND.getType());
            messageSendDto.setContactId(userInfo.getUserId());
            messageSendDto.setContactName(userInfo.getNickName());
            messageSendDto.setExtendData(null);
        } else {
            messageSendDto.setContactId(messageSendDto.getSendUserId());
            messageSendDto.setContactName(messageSendDto.getSendUserNickName());
        }
        userChannel.writeAndFlush(new TextWebSocketFrame(JsonUtils.convertObj2Json(messageSendDto)));
    }

    public void closeContext(String userId) {
        if (StringTools.isEmpty(userId)) {
            return;
        }
        redisComponent.cleanUserTokenByUserId(userId);
        Channel channel = USER_CONTEXT_MAP.get(userId);
        if (channel == null) {
            return;
        }
        channel.close();
    }

    //发送给群组
    private void send2Group(MessageSendDto messageSendDto) {
        if (StringTools.isEmpty(messageSendDto.getContactId())) {
            return;
        }
        ChannelGroup channelGroup = GROUP_CONTEXT_MAP.get(messageSendDto.getContactId());
        if (channelGroup == null) {
            return;
        }
        channelGroup.writeAndFlush(new TextWebSocketFrame(JsonUtils.convertObj2Json(messageSendDto)));

        //移除群组
        MessageTypeEnum messageTypeEnum = MessageTypeEnum.getByType(messageSendDto.getMessageType());
        if (MessageTypeEnum.LEAVE_GROUP == messageTypeEnum || MessageTypeEnum.REMOVE_GROUP == messageTypeEnum) {
            String userId = (String) messageSendDto.getExtendData();
            redisComponent.removeUserContact(userId, messageSendDto.getContactId());
            Channel channel = USER_CONTEXT_MAP.get(userId);
            if (channel == null) {
                return;
            }
            channelGroup.remove(channel);
        }
        if(MessageTypeEnum.DISSOLUTION_GROUP == messageTypeEnum){
            GROUP_CONTEXT_MAP.remove(messageSendDto.getContactId());
            channelGroup.close();
        }
    }

    //添加用户到群组通道
    public void addUser2Group(String userId, String groupId) {
        // 获取用户对应的Channel
        Channel userChannel = USER_CONTEXT_MAP.get(userId);
        if (userChannel == null) {
            return;
        }

        ChannelGroup group = getOrCreateGroup(groupId);

        // 将用户Channel添加到群组ChannelGroup中
        group.add(userChannel);
    }

    static void clearLocalContexts() {
        USER_CONTEXT_MAP.clear();
        GROUP_CONTEXT_MAP.clear();
    }

    void bindUserChannel(String userId, Channel channel) {
        channel.attr(USER_ID_ATTRIBUTE).set(userId);
        Channel previousChannel = USER_CONTEXT_MAP.put(userId, channel);
        if (previousChannel != null && previousChannel != channel) {
            removeChannelFromGroups(previousChannel);
            previousChannel.close();
        }
    }

    int getGroupConnectionCount(String groupId) {
        ChannelGroup group = GROUP_CONTEXT_MAP.get(groupId);
        return group == null ? 0 : group.size();
    }

    int getOnlineUserCount() {
        return USER_CONTEXT_MAP.size();
    }

    public static String getUserId(Channel channel) {
        Attribute<String> attribute = channel.attr(USER_ID_ATTRIBUTE);
        return attribute.get();
    }

    private ChannelGroup getOrCreateGroup(String groupId) {
        return GROUP_CONTEXT_MAP.computeIfAbsent(groupId, key -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE));
    }

    private void removeChannelFromGroups(Channel channel) {
        GROUP_CONTEXT_MAP.values().forEach(group -> group.remove(channel));
    }
}
