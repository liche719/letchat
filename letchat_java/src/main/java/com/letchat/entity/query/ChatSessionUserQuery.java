package com.letchat.entity.query;


import lombok.Data;

/**
 * 会话用户参数
 */
@Data
public class ChatSessionUserQuery extends BaseParam {


	/**
	 * 用户ID
	 */
	private String userId;

	private String userIdFuzzy;

	/**
	 * 联系人ID
	 */
	private String contactId;

	private String contactIdFuzzy;

	/**
	 * 会话ID
	 */
	private String sessionId;

	private String sessionIdFuzzy;

	/**
	 * 联系人名称
	 */
	private String contactName;

	private String contactNameFuzzy;


}
