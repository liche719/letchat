package com.letchat.entity.vo;

import com.letchat.entity.po.GroupInfo;
import com.letchat.entity.po.UserContact;
import lombok.Data;

import java.util.List;

@Data
public class GroupInfoVO {
    private GroupInfo groupInfo;
    private List<UserContact> userContactList;
}
