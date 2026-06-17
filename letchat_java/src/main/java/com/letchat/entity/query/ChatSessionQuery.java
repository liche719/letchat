package com.letchat.entity.query;


import lombok.Data;

/**
 * 会话信息参数
 */
@Data
public class ChatSessionQuery extends BaseParam {


	/**
	 * 会话ID
	 */
	private String sessionId;

	private String sessionIdFuzzy;

	/**
	 * 最后接受的消息
	 */
	private String lastMessage;

	private String lastMessageFuzzy;

	/**
	 * 最后接受消息时间毫秒
	 */
	private Long lastReceiveTime;


}
