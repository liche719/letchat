package com.letchat.controller;

import com.letchat.annotation.GlobalInterception;
import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.entity.dto.UserContactSearchResultDto;
import com.letchat.entity.enums.PageSize;
import com.letchat.entity.enums.ResponseCodeEnum;
import com.letchat.entity.enums.UserContactStatusEnum;
import com.letchat.entity.enums.UserContactTypeEnum;
import com.letchat.entity.po.UserContact;
import com.letchat.entity.po.UserInfo;
import com.letchat.entity.query.UserContactApplyQuery;
import com.letchat.entity.query.UserContactQuery;
import com.letchat.entity.vo.PaginationResultVO;
import com.letchat.entity.vo.ResponseVO;
import com.letchat.entity.vo.UserInfoVO;
import com.letchat.exception.BusinessException;
import com.letchat.service.UserContactApplyService;
import com.letchat.service.UserContactService;
import com.letchat.service.UserInfoService;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@RestController("userContactController")
@RequestMapping("/contact")
public class UserContactController extends ABaseController {

    @Resource
    private UserContactService userContactService;

    @Resource
    private UserInfoService userInfoService;

    @Resource
    private UserContactApplyService userContactApplyService;


    /**
     * 搜索联系人
     */
    @RequestMapping("/search")
    @GlobalInterception
    public ResponseVO search(HttpServletRequest request, @NotEmpty String contactId) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);
        UserContactSearchResultDto resultDto = userContactService.searchContact(tokenUserInfoDto.getUserId(), contactId);
        return getSuccessResponseVO(resultDto);
    }


    /**
     * 申请添加联系人
     */
    @RequestMapping("/applyAdd")
    @GlobalInterception
    public ResponseVO applyAdd(HttpServletRequest request, @NotEmpty String contactId, String applyInfo) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);
        Integer joinType = userContactApplyService.applyAdd(tokenUserInfoDto, contactId, applyInfo);
        return getSuccessResponseVO(joinType);
    }


    /**
     * 加载申请
     */
    @RequestMapping("/loadApply")
    @GlobalInterception
    public ResponseVO loadApply(HttpServletRequest request, Integer pageNo) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);

        UserContactApplyQuery applyQuery = new UserContactApplyQuery();
        applyQuery.setOrderBy("last_apply_time desc");
        applyQuery.setReceiveUserId(tokenUserInfoDto.getUserId());
        applyQuery.setPageNo(pageNo);
        applyQuery.setPageSize(PageSize.SIZE15.getSize());
        applyQuery.setQueryContactInfo(true);
        PaginationResultVO resultVO = userContactApplyService.findListByPage(applyQuery);
        return getSuccessResponseVO(resultVO);
    }


    /**
     * 处理申请
     */
    @RequestMapping("/dealWithApply")
    @GlobalInterception
    public ResponseVO dealWithApply(HttpServletRequest request, @NotNull Integer applyId, @NotEmpty Integer status) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);
        this.userContactApplyService.dealWithApply(tokenUserInfoDto.getUserId(), applyId, status);
        return getSuccessResponseVO(null);
    }


    /**
     * 加载联系人列表
     */
    @RequestMapping("/loadContact")
    @GlobalInterception
    public ResponseVO loadContact(HttpServletRequest request, @NotNull String contactType) {
        UserContactTypeEnum contactTypeEnum = UserContactTypeEnum.getByType(contactType);
        if (null == contactTypeEnum) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);

        UserContactQuery contactQuery = new UserContactQuery();
        contactQuery.setUserId(tokenUserInfoDto.getUserId());
        contactQuery.setContactType(contactTypeEnum.getType());
        if (UserContactTypeEnum.USER.equals(contactTypeEnum)) {
            contactQuery.setQueryContactUserInfo(true);
        } else if (UserContactTypeEnum.GROUP.equals(contactTypeEnum)) {
            contactQuery.setQueryGroupInfo(true);
        }
        contactQuery.setOrderBy("last_update_time desc");
        contactQuery.setStatusArray(new Integer[]{
                UserContactStatusEnum.FRIEND.getStatus(),
                UserContactStatusEnum.DEL_BE.getStatus(),
                UserContactStatusEnum.BLACKLIST_BE.getStatus()
        });

        List<UserContact> contactList = userContactService.findListByParam(contactQuery);
        return getSuccessResponseVO(contactList);
    }


    /**
     * 获取联系人信息，不一定是好友
     *
     * @param request
     * @param contactId
     * @return
     */
    @RequestMapping("/getContactInfo")
    @GlobalInterception
    public ResponseVO getContactInfo(HttpServletRequest request, @NotNull String contactId) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);
        UserInfo userInfo = userInfoService.getUserInfoByUserId(contactId);
        UserInfoVO userInfoVO = new UserInfoVO();
        BeanUtils.copyProperties(userInfo, userInfoVO, UserInfoVO.class);
        UserContact userContact = userContactService.getUserContactByUserIdAndContactId(tokenUserInfoDto.getUserId(), contactId);
        if (userContact != null) {
            userInfoVO.setContactStatus(UserContactStatusEnum.FRIEND.getStatus());
        }
        return getSuccessResponseVO(userInfoVO);
    }


    /**
     * 获取联系人信息，一定是好友
     *
     * @param request
     * @param contactId
     * @return
     */
    @RequestMapping("/getContactUserInfo")
    @GlobalInterception
    public ResponseVO getContactUserInfo(HttpServletRequest request, @NotNull String contactId) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);

        UserContact userContact = userContactService.getUserContactByUserIdAndContactId(tokenUserInfoDto.getUserId(), contactId);
        if (null == userContact || !ArrayUtils.contains(new Integer[]{
                UserContactStatusEnum.FRIEND.getStatus(),
                UserContactStatusEnum.DEL_BE.getStatus(),
                UserContactStatusEnum.BLACKLIST_BE.getStatus(),
        }, userContact.getStatus())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        UserInfo userInfo = userInfoService.getUserInfoByUserId(contactId);
        UserInfoVO userInfoVO = new UserInfoVO();
        BeanUtils.copyProperties(userInfo, userInfoVO, UserInfoVO.class);
        return getSuccessResponseVO(userInfoVO);
    }


    @RequestMapping("/delContact")
    @GlobalInterception
    public ResponseVO delContact(HttpServletRequest request, @NotNull String contactId) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);
        userContactService.removeUserContact(tokenUserInfoDto.getUserId(), contactId, UserContactStatusEnum.DEL);
        return getSuccessResponseVO(null);
    }


    @RequestMapping("/addContact2BlackList")
    @GlobalInterception
    public ResponseVO addContact2BlackList(HttpServletRequest request, @NotNull String contactId) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);
        userContactService.removeUserContact(tokenUserInfoDto.getUserId(), contactId, UserContactStatusEnum.BLACKLIST);
        return getSuccessResponseVO(null);
    }


}
