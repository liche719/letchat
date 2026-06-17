package com.letchat.entity.query;

import lombok.Data;


/**
 * 参数
 */
@Data
public class GroupInfoQuery extends BaseParam {


    /**
     * 群ID
     */
    private String groupId;

    private String groupIdFuzzy;

    /**
     * 群组名
     */
    private String groupName;

    private String groupNameFuzzy;

    /**
     * 群主id
     */
    private String groupOwnerId;

    private String groupOwnerIdFuzzy;

    /**
     * 创建时间
     */
    private String createTime;

    private String createTimeStart;

    private String createTimeEnd;

    /**
     * 群公告
     */
    private String groupNotice;

    private String groupNoticeFuzzy;

    /**
     * 0:直授加入 1：管理员同意后加入
     */
    private Integer joinType;

    /**
     * 状态 1:正常 0:解散
     */
    private Integer status;

    private Boolean queryGroupOwnerNickName;

    private Boolean queryMemberCount;



}
