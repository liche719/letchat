package com.letchat.controller;

import com.letchat.annotation.GlobalInterception;
import com.letchat.entity.Constants;
import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.entity.po.UserInfo;
import com.letchat.entity.vo.ResponseVO;
import com.letchat.entity.vo.UserInfoVO;
import com.letchat.redis.RedisComponent;
import com.letchat.service.UserInfoService;
import com.letchat.utils.CopyTools;
import com.letchat.utils.StringTools;
import com.letchat.websocket.ChannelContextUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.IOException;

@RestController("userInfoController")
@RequestMapping("/userInfo")
public class UserInfoController extends ABaseController {

    @Resource
    private UserInfoService userInfoService;

    @Resource
    private ChannelContextUtils channelContextUtils;

    @Resource
    private RedisComponent redisComponent;


    /**
     * 获取用户信息
     *
     * @param request
     * @return
     */
    @RequestMapping("/getUserInfo")
    @GlobalInterception
    public ResponseVO getUserInfo(HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);
        UserInfo userInfo = userInfoService.getUserInfoByUserId(tokenUserInfoDto.getUserId());
        UserInfoVO userInfoVO = CopyTools.copy(userInfo, UserInfoVO.class);
        userInfoVO.setAdmin(tokenUserInfoDto.getAdmin());
        return getSuccessResponseVO(userInfoVO);
    }


    /**
     * 修改用户信息
     *
     * @param request
     * @param userInfo
     * @param avatarFile
     * @param avatarCover
     * @return
     * @throws IOException
     */
    @RequestMapping("/saveUserInfo")
    @GlobalInterception
    public ResponseVO saveUserInfo(HttpServletRequest request, UserInfo userInfo, MultipartFile avatarFile, MultipartFile avatarCover) throws IOException {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);
        userInfo.setUserId(tokenUserInfoDto.getUserId());
        userInfo.setPassword(null);
        userInfo.setStatus(null);
        userInfo.setCreateTime(null);
        userInfo.setLastLoginTime(null);
        this.userInfoService.updateUserInfo(userInfo, avatarFile, avatarCover);
        return getUserInfo(request);
    }


    /**
     * 修改密码
     *
     * @param request
     * @param password
     * @return
     */
    @RequestMapping("/updatePassword")
    @GlobalInterception
    public ResponseVO updatePassword(HttpServletRequest request,
                                     @NotNull @Pattern(regexp = Constants.REGEX_PASSWORD) String password) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);
        System.out.println(password);
        UserInfo userInfo = new UserInfo();
        userInfo.setPassword(StringTools.encodeMD5(password));
        this.userInfoService.updateUserInfoByUserId(userInfo, tokenUserInfoDto.getUserId());
        channelContextUtils.closeContext(tokenUserInfoDto.getUserId());
        return getSuccessResponseVO(null);
    }


    /**
     * 登出
     *
     * @param request
     * @return
     */
    @RequestMapping("/logout")
    @GlobalInterception
    public ResponseVO logout(HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);
        channelContextUtils.closeContext(tokenUserInfoDto.getUserId());
        redisComponent.clearUserContact(tokenUserInfoDto.getUserId());
        return getSuccessResponseVO("退出登录");
    }


}
