package com.letchat.service.impl;

import com.letchat.entity.Constants;
import com.letchat.entity.dto.MessageSendDto;
import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.entity.enums.*;
import com.letchat.entity.po.GroupInfo;
import com.letchat.entity.po.UserContact;
import com.letchat.entity.po.UserContactApply;
import com.letchat.entity.po.UserInfo;
import com.letchat.entity.query.*;
import com.letchat.entity.vo.PaginationResultVO;
import com.letchat.exception.BusinessException;
import com.letchat.mappers.GroupInfoMapper;
import com.letchat.mappers.UserContactApplyMapper;
import com.letchat.mappers.UserContactMapper;
import com.letchat.mappers.UserInfoMapper;
import com.letchat.service.UserContactApplyService;
import com.letchat.utils.StringTools;
import com.letchat.websocket.MessageHandler;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;


/**
 * 联系人申请 业务接口实现
 */
@Service("userContactApplyService")
public class UserContactApplyServiceImpl implements UserContactApplyService {

    @Resource
    private UserContactApplyMapper<UserContactApply, UserContactApplyQuery> userContactApplyMapper;

    @Resource
    private UserContactMapper<UserContact, UserContactQuery> userContactMapper;

    @Resource
    private UserContactServiceImpl userContactService;

    @Resource
    private MessageHandler messageHandler;

    @Resource
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    @Resource
    private GroupInfoMapper<GroupInfo, GroupInfoQuery> groupInfoMapper;


    /**
     * 根据条件查询列表
     */
    @Override
    public List<UserContactApply> findListByParam(UserContactApplyQuery param) {
        return this.userContactApplyMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(UserContactApplyQuery param) {
        return this.userContactApplyMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<UserContactApply> findListByPage(UserContactApplyQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<UserContactApply> list = this.findListByParam(param);
        PaginationResultVO<UserContactApply> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 根据ApplyId获取对象
     */
    @Override
    public UserContactApply getUserContactApplyByApplyId(Integer applyId) {
        return this.userContactApplyMapper.selectByApplyId(applyId);
    }

    /**
     * 申请添加好友
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer applyAdd(TokenUserInfoDto tokenUserInfoDto, String contactId, String applyInfo) {
        UserContactTypeEnum typeEnum = UserContactTypeEnum.getByPrefix(contactId);
        if (null == typeEnum) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        //申请人
        String applyUserId = tokenUserInfoDto.getUserId();
        //默认申请信息
        applyInfo = StringTools.isEmpty(applyInfo) ? String.format(Constants.APPLY_INFO_TEMPLATE, tokenUserInfoDto.getNickName()) : applyInfo;

        long curTime = System.currentTimeMillis();

        Integer joinType = null;
        String receiverUserId = contactId;

        // 如果拉黑则无法添加
        UserContact userContact = userContactService.getUserContactByUserIdAndContactId(receiverUserId, applyUserId);
        if (userContact != null && ArrayUtils.contains(new Integer[]{UserContactStatusEnum.BLACKLIST_BE.getStatus(), UserContactStatusEnum.BLACKLIST_BE_FIRST.getStatus()}, userContact.getStatus())) {
            throw new BusinessException("对方已将你拉黑，无法添加");
        }

        if (UserContactTypeEnum.GROUP == typeEnum) {
            GroupInfo groupInfo = groupInfoMapper.selectByGroupId(contactId);
            if (groupInfo == null || GroupStatusEnum.DISSOLUTION.getStatus().equals(groupInfo.getStatus())) {
                throw new BusinessException("群聊不存在或已解散");
            }
            joinType = groupInfo.getJoinType();
            receiverUserId = groupInfo.getGroupOwnerId();
        } else {
            UserInfo userInfo = userInfoMapper.selectByUserId(contactId);
            if (userInfo == null) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
            joinType = userInfo.getJoinType();
            receiverUserId = userInfo.getUserId();
        }

        //直接加入，不用记录申请记录
        if (JoinTypeEnum.JOIN.getType().equals(joinType)) {
            userContactService.addContact(applyUserId, receiverUserId, contactId, typeEnum.getType(), applyInfo);
            return joinType;
        }

        //查询对方是否已向自己发送好友申请，如果对方已发送，则直接成为好友
        UserContactApply userAlreadyApply = this.userContactApplyMapper.selectByApplyUserIdAndReceiveUserIdAndContactId(receiverUserId, applyUserId, applyUserId);
        if (null != userAlreadyApply) {
            //相当于我通过好友发送的申请
            userContactService.addContact(userAlreadyApply.getApplyUserId(), userAlreadyApply.getReceiveUserId(), userAlreadyApply.getContactId(), typeEnum.getType(), userAlreadyApply.getApplyInfo());
            return joinType;
        }


        UserContactApply dbApply = this.userContactApplyMapper.selectByApplyUserIdAndReceiveUserIdAndContactId(applyUserId, receiverUserId, contactId);
        UserContactApply contactApply = new UserContactApply();
        if (null == dbApply) {
            contactApply.setApplyUserId(applyUserId);
            contactApply.setContactType(typeEnum.getType());
            contactApply.setReceiveUserId(receiverUserId);
            contactApply.setLastApplyTime(curTime);
            contactApply.setContactId(contactId);
            contactApply.setStatus(UserContactApplyStatusEnum.INIT.getStatus());
            contactApply.setApplyInfo(applyInfo);
            this.userContactApplyMapper.insert(contactApply);
        } else {
            //更新状态
            contactApply.setLastApplyTime(curTime);
            contactApply.setApplyInfo(applyInfo);
            this.userContactApplyMapper.updateByApplyId(contactApply, dbApply.getApplyId());
        }

        //发送ws消息
        if (dbApply == null || UserContactApplyStatusEnum.INIT.getStatus().equals(dbApply.getStatus())) {
            MessageSendDto messageSendDto = new MessageSendDto();
            messageSendDto.setMessageType(MessageTypeEnum.CONTACT_APPLY.getType());
            messageSendDto.setMessageContent(applyInfo);
            messageSendDto.setContactId(receiverUserId);
            messageHandler.sendMessage(messageSendDto);
        }

        return joinType;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dealWithApply(String userId, Integer applyId, Integer status) {
        UserContactApplyStatusEnum statusEnum = UserContactApplyStatusEnum.getByStatus(status);
        if (statusEnum == null || UserContactApplyStatusEnum.INIT == statusEnum) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        UserContactApply applyInfo = this.getUserContactApplyByApplyId(applyId);
        if (applyInfo == null || !userId.equals(applyInfo.getReceiveUserId())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        UserContactApply updateInfo = new UserContactApply();
        updateInfo.setStatus(status);
        updateInfo.setLastApplyTime(System.currentTimeMillis());

        UserContactApplyQuery applyQuery = new UserContactApplyQuery();
        applyQuery.setApplyId(applyId);
        applyQuery.setStatus(UserContactApplyStatusEnum.INIT.getStatus()); //防止 被恶意修改

        Integer count = userContactApplyMapper.updateByApplyId(updateInfo, applyId);
        if (count == 0) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        if (UserContactApplyStatusEnum.PASS.getStatus().equals(status)) {
            userContactService.addContact(applyInfo.getApplyUserId(), applyInfo.getReceiveUserId(), applyInfo.getContactId(), applyInfo.getContactType(), applyInfo.getApplyInfo());
            return;
        }

        if (UserContactApplyStatusEnum.REJECT == statusEnum) {
            //只需要把申请记录的status修改为拒绝
            updateInfo.setStatus(UserContactApplyStatusEnum.REJECT.getStatus());
        }

        if (UserContactApplyStatusEnum.BLACKLIST == statusEnum) {
            Date curDate = new Date();
            UserContact userContact = new UserContact();
            userContact.setUserId(applyInfo.getApplyUserId());
            userContact.setContactId(applyInfo.getContactId());
            userContact.setContactType(applyInfo.getContactType());
            userContact.setCreateTime(curDate);
            userContact.setStatus(UserContactStatusEnum.BLACKLIST_BE_FIRST.getStatus());
            userContact.setLastUpdateTime(curDate);
            userContactMapper.insertOrUpdate(userContact);
        }
    }

}