package com.letchat.entity.dto;

import com.letchat.entity.po.ChatMessage;
import com.letchat.entity.po.ChatSessionUser;
import lombok.Data;

import java.util.List;

@Data
public class WsInitData {

    private List<ChatSessionUser> chatSessionUserList;

    private List<ChatMessage> chatMessagesList;

    private Integer applyCount;


}
