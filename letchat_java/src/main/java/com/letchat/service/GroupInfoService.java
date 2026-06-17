package com.letchat.service;

import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.entity.enums.MessageTypeEnum;
import com.letchat.entity.po.GroupInfo;
import com.letchat.entity.query.GroupInfoQuery;
import com.letchat.entity.vo.PaginationResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;


/**
 * 业务接口
 */
public interface GroupInfoService {

    /**
     * 根据条件查询列表
     */
    List<GroupInfo> findListByParam(GroupInfoQuery param);

    /**
     * 根据条件查询列表
     */
    Integer findCountByParam(GroupInfoQuery param);

    /**
     * 分页查询
     */
    PaginationResultVO<GroupInfo> findListByPage(GroupInfoQuery param);

    /**
     * 根据GroupId查询对象
     */
    GroupInfo getGroupInfoByGroupId(String groupId);

    void saveGroup(GroupInfo groupInfo, MultipartFile avatarFile, MultipartFile avatarCover) throws IOException;

    void dissolutionGroup(String groupOwnerId, String groupId);

    void addOrRemoveGroupUser(TokenUserInfoDto tokenUserInfoDto, String groupId, String selectContacts, Integer opType);

    void leaveGroup(String userId, String groupId, MessageTypeEnum messageTypeEnum);

}