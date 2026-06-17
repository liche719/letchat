package com.letchat.entity.dto;

import com.letchat.utils.StringTools;
import lombok.Data;

import java.io.Serializable;

@Data
public class MessageSendDto<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    // 消息id
    private Long messageId;
    // 会话id
    private String sessionId;
    //发送人
    private String sendUserId;
    //发送人昵称
    private String sendUserNickName;
    // 接收人
    private String contactId;
    // 接收人昵称
    private String contactName;
    // 消息内容
    private String messageContent;
    // 最后一条消息
    private String lastMessage;
    // 消息类型
    private Integer messageType;
    // 发送时间
    private Long sendTime;
    // 联系人类型
    private Integer contactType;
    // 扩展数据
    private T extendData;
    // 消息状态 0:未读 1:已发送 对于文件是异步上传，所以使用状态处理
    private Integer status;
    // 文件信息
    private Long fileSize;
    private String fileName;
    private Integer fileType;
    //群人数
    private Integer memberCount;


    public String getLastMessage() {
        if (StringTools.isEmpty(lastMessage)) {
            return messageContent;
        }
        return lastMessage;
    }

}
