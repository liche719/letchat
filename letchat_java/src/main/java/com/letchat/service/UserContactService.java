package com.letchat.service;

import com.letchat.entity.dto.UserContactSearchResultDto;
import com.letchat.entity.enums.UserContactStatusEnum;
import com.letchat.entity.po.UserContact;
import com.letchat.entity.query.UserContactQuery;

import java.util.List;


/**
 * 联系人 业务接口
 */
public interface UserContactService {

    /**
     * 根据条件查询列表
     */
    List<UserContact> findListByParam(UserContactQuery param);

    /**
     * 根据条件查询列表
     */
    Integer findCountByParam(UserContactQuery param);

    /**
     * 根据UserIdAndContactId查询对象
     */
    UserContact getUserContactByUserIdAndContactId(String userId, String contactId);

    UserContactSearchResultDto searchContact(String userId, String contactId);

    void addContact(String applyUserId, String receiveUserId, String contactId, Integer contactType, String applyInfo);

    void removeUserContact(String userId, String contactId, UserContactStatusEnum statusEnum);

    void addContact4Robot(String userId);

}