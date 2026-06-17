package com.letchat.controller;

import com.letchat.annotation.GlobalInterception;
import com.letchat.entity.po.UserInfo;
import com.letchat.entity.query.UserInfoQuery;
import com.letchat.entity.vo.PaginationResultVO;
import com.letchat.entity.vo.ResponseVO;
import com.letchat.service.UserInfoService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@RestController("adminUserInfoController")
@RequestMapping("/admin")
public class AdminUserInfoController extends ABaseController {

    @Resource
    private UserInfoService userInfoService;


    @RequestMapping("/loadUser")
    @GlobalInterception(checkAdmin = true)
    public ResponseVO loadUser(UserInfoQuery userInfoQuery) {
        userInfoQuery.setOrderBy("create_time desc");
        PaginationResultVO<UserInfo> resultVO = userInfoService.findListByPage(userInfoQuery);
        return getSuccessResponseVO(resultVO);
    }


    @RequestMapping("/updateUserStatus")
    @GlobalInterception(checkAdmin = true)
    public ResponseVO updateUserStatus(@NotNull Integer status, @NotEmpty String userId) {
        userInfoService.updateUserStatus(status, userId);
        return getSuccessResponseVO(null);
    }


    @RequestMapping("/forceOffLine")
    @GlobalInterception(checkAdmin = true)
    public ResponseVO forceOffLine(@NotEmpty String userId) {

        return getSuccessResponseVO(null);
    }


}
