package com.letchat.entity.po;

import lombok.Data;

import java.io.Serializable;


/**
 * 会话用户
 */
@Data
public class ChatSessionUser implements Serializable {
	private static final long serialVersionUID = 1L;


	/**
	 * 用户ID
	 */
	private String userId;

	/**
	 * 联系人ID
	 */
	private String contactId;

	/**
	 * 会话ID
	 */
	private String sessionId;

	/**
	 * 联系人名称
	 */
	private String contactName;

	private String lastMessage;

	private Long lastReceiveTime;

	private Integer memberCount;

	private Integer contactType;


    @Override
	public String toString (){
		return "用户ID:"+(userId == null ? "空" : userId)+"，联系人ID:"+(contactId == null ? "空" : contactId)+"，会话ID:"+(sessionId == null ? "空" : sessionId)+"，联系人名称:"+(contactName == null ? "空" : contactName);
	}
}
