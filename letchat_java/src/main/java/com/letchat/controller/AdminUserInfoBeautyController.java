package com.letchat.controller;

import com.letchat.annotation.GlobalInterception;
import com.letchat.entity.po.UserInfoBeauty;
import com.letchat.entity.query.UserInfoBeautyQuery;
import com.letchat.entity.vo.PaginationResultVO;
import com.letchat.entity.vo.ResponseVO;
import com.letchat.service.UserInfoBeautyService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;

@RestController("adminUserInfoBeautyController")
@RequestMapping("/admin")
public class AdminUserInfoBeautyController extends ABaseController {

    @Resource
    private UserInfoBeautyService userInfoBeautyService;


    @RequestMapping("/loadBeautyAccountList")
    @GlobalInterception(checkAdmin = true)
    public ResponseVO loadBeautyAccountList(UserInfoBeautyQuery query) {
        query.setOrderBy("id desc");
        PaginationResultVO<UserInfoBeauty> resultVO = userInfoBeautyService.findListByPage(query);
        return getSuccessResponseVO(resultVO);
    }


    @RequestMapping("/saveBeautyAccount")
    @GlobalInterception(checkAdmin = true)
    public ResponseVO loadBeautyAccountList(UserInfoBeauty beauty) {
        userInfoBeautyService.saveAccount(beauty);
        return getSuccessResponseVO(null);
    }


    @RequestMapping("/delBeautyAccount")
    @GlobalInterception(checkAdmin = true)
    public ResponseVO delBeautyAccount(@NotNull Integer id) {
        userInfoBeautyService.deleteUserInfoBeautyById(id);
        return getSuccessResponseVO(null);
    }



}
