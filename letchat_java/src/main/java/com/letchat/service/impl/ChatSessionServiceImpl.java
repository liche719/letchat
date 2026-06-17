package com.letchat.service.impl;

import com.letchat.entity.po.ChatSession;
import com.letchat.entity.query.ChatSessionQuery;
import com.letchat.mappers.ChatSessionMapper;
import com.letchat.service.ChatSessionService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;


/**
 * 会话信息 业务接口实现
 */
@Service("chatSessionService")
public class ChatSessionServiceImpl implements ChatSessionService {

	@Resource
	private ChatSessionMapper<ChatSession, ChatSessionQuery> chatSessionMapper;

}