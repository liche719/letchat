package com.letchat.entity.po;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.letchat.entity.enums.DateTimePatternEnum;
import com.letchat.utils.DateUtil;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;


/**
 * 群信息
 */
@Data
public class GroupInfo implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * 群ID
     */
    private String groupId;

    /**
     * 群组名
     */
    private String groupName;

    /**
     * 群主id
     */
    private String groupOwnerId;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 群公告
     */
    private String groupNotice;

    /**
     * 0:直授加入 1：管理员同意后加入
     */
    private Integer joinType;

    /**
     * 状态 1:正常 0:解散
     */
    private Integer status;

    private Integer memberCount;

    /**
     * 群主昵称
     */
    private String groupOwnerNickName;


    @Override
    public String toString() {
        return "群ID:" + (groupId == null ? "空" : groupId) + "，群组名:" + (groupName == null ? "空" : groupName) + "，群主id:" + (groupOwnerId == null ? "空" : groupOwnerId) + "，创建时间:" + (createTime == null ? "空" : DateUtil.format(createTime, DateTimePatternEnum.YYYY_MM_DD_HH_MM_SS.getPattern())) + "，群公告:" + (groupNotice == null ? "空" : groupNotice) + "，0:直授加入 1：管理员同意后加入:" + (joinType == null ? "空" : joinType) + "，状态 1:正常 0:解散:" + (status == null ? "空" : status);
    }
}
