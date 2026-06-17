package com.letchat.entity.po;

import com.letchat.entity.enums.UserContactApplyStatusEnum;
import lombok.Data;

import java.io.Serializable;


/**
 * 联系人申请
 */
@Data
public class UserContactApply implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 自增ID
     */
    private Integer applyId;

    /**
     * 申请人id
     */
    private String applyUserId;

    /**
     * 接收人ID
     */
    private String receiveUserId;

    /**
     * 联系人类型 0:好友 1:群组
     */
    private Integer contactType;

    /**
     * 联系人群组ID
     */
    private String contactId;

    /**
     * 最后申请时间
     */
    private Long lastApplyTime;

    /**
     * 状态0:待处理 1:已同意 2:已拒绝 3:已拉黑
     */
    private Integer status;

    /**
     * 申请信息
     */
    private String applyInfo;

    private String contactName;

    private String statusName;


    public String getStatusName() {
        UserContactApplyStatusEnum statusEnum = UserContactApplyStatusEnum.getByStatus(status);
        return statusEnum == null ? null : statusEnum.getDesc();
    }


    public String toString() {
        return "自增ID:" + (applyId == null ? "空" : applyId) + "，申请人id:" + (applyUserId == null ? "空" : applyUserId) + "，接收人ID:" + (receiveUserId == null ? "空" : receiveUserId) + "，联系人类型 0:好友 1:群组:" + (contactType == null ? "空" : contactType) + "，联系人群组ID:" + (contactId == null ? "空" : contactId) + "，最后申请时间:" + (lastApplyTime == null ? "空" : lastApplyTime) + "，状态0:待处理 1:已同意 2:已拒绝 3:已拉黑:" + (status == null ? "空" : status) + "，申请信息:" + (applyInfo == null ? "空" : applyInfo);
    }
}
