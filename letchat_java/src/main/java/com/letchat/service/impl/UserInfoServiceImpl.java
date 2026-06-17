package com.letchat.service.impl;

import com.letchat.config.AppConfig;
import com.letchat.entity.Constants;
import com.letchat.entity.dto.MessageSendDto;
import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.entity.enums.*;
import com.letchat.entity.po.UserContact;
import com.letchat.entity.po.UserInfo;
import com.letchat.entity.po.UserInfoBeauty;
import com.letchat.entity.query.SimplePage;
import com.letchat.entity.query.UserContactQuery;
import com.letchat.entity.query.UserInfoQuery;
import com.letchat.entity.vo.PaginationResultVO;
import com.letchat.entity.vo.UserInfoVO;
import com.letchat.exception.BusinessException;
import com.letchat.mappers.UserContactMapper;
import com.letchat.mappers.UserInfoBeautyMapper;
import com.letchat.mappers.UserInfoMapper;
import com.letchat.redis.RedisComponent;
import com.letchat.service.ChatSessionUserService;
import com.letchat.service.UserContactService;
import com.letchat.service.UserInfoService;
import com.letchat.utils.StringTools;
import com.letchat.websocket.MessageHandler;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


/**
 * 用户信息 业务接口实现
 */
@Service("userInfoService")
public class UserInfoServiceImpl implements UserInfoService {

    @Resource
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

    @Resource
    private UserContactMapper<UserContact, UserContactQuery> userContactMapper;

    @Resource
    private UserInfoBeautyMapper<UserInfoBeauty, UserInfoQuery> userInfoBeautyMapper;

    @Resource
    private AppConfig appconfig;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private UserContactService userContactService;

    @Resource
    private MessageHandler messageHandler;

    @Resource
    private ChatSessionUserService chatSessionUserService;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<UserInfo> findListByParam(UserInfoQuery param) {
        return this.userInfoMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(UserInfoQuery param) {
        return this.userInfoMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<UserInfo> findListByPage(UserInfoQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<UserInfo> list = this.findListByParam(param);
        PaginationResultVO<UserInfo> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }


    /**
     * 根据UserId获取对象
     */
    @Override
    public UserInfo getUserInfoByUserId(String userId) {
        return this.userInfoMapper.selectByUserId(userId);
    }

    /**
     * 根据UserId修改
     */
    @Override
    public Integer updateUserInfoByUserId(UserInfo bean, String userId) {
        return this.userInfoMapper.updateByUserId(bean, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(String email, String nickName, String password) {
        UserInfo userInfo = userInfoMapper.selectByEmail(email);
        if (userInfo != null) {
            throw new BusinessException("邮箱账号已存在");
        }

        String userId = StringTools.getUserId();
        UserInfoBeauty beautyAccount = userInfoBeautyMapper.selectByEmail(email);
        Boolean userBeautyAccount = null != beautyAccount && BeautyAccountStatusEnum.NO_USE.getStatus().equals(beautyAccount.getStatus());
        if (userBeautyAccount) {
            userId = UserContactTypeEnum.USER.getPrefix() + userId + beautyAccount.getUserId();
        }
        Date curData = new Date();
        userInfo = new UserInfo();
        userInfo.setUserId(userId).setNickName(nickName).setEmail(email).setPassword(StringTools.encodeMD5(password)).setCreateTime(curData).setStatus(UserStatusEnum.ENABLE.getStatus()).setLastOffTime(curData.getTime()).setJoinType(JoinTypeEnum.APPLY.getType());
        userInfoMapper.insert(userInfo);

        if (userBeautyAccount) {
            UserInfoBeauty updateBeauty = new UserInfoBeauty();
            updateBeauty.setStatus(BeautyAccountStatusEnum.USED.getStatus());
            userInfoBeautyMapper.updateById(updateBeauty, beautyAccount.getId());
        }

        //创建机器人好友
        userContactService.addContact4Robot(userId);

    }

    @Override
    public UserInfoVO login(String email, String password) {
        UserInfo userInfo = userInfoMapper.selectByEmail(email);
        if (null == userInfo || !userInfo.getPassword().equals(password)) {
            throw new BusinessException("账号或密码错误");
        }
        if (UserStatusEnum.DISABLE.getStatus().equals(userInfo.getStatus())) {
            throw new BusinessException("账号被禁用");
        }

        //查询联系人
        UserContactQuery contactQuery = new UserContactQuery();
        contactQuery.setUserId(userInfo.getUserId());
        contactQuery.setStatus(UserContactStatusEnum.FRIEND.getStatus());
        List<UserContact> contactList = userContactMapper.selectList(contactQuery);
        List<String> contactIdList = contactList.stream().map(UserContact::getContactId).collect(Collectors.toList());
        redisComponent.clearUserContact(userInfo.getUserId());
        if (!contactIdList.isEmpty()) {
            redisComponent.addUserContactBatch(userInfo.getUserId(), contactIdList);
        }

        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto(userInfo);

        Long lastHeartBeat = redisComponent.getUserHeartBeat(userInfo.getUserId());
        if (null != lastHeartBeat) {
            throw new BusinessException("此账号已在别处登录，请退出后再登录");
        }

        //保存登录信息到redis
        String token = StringTools.encodeMD5(tokenUserInfoDto.getUserId() + StringTools.getRandomString(Constants.LENGTH_20));
        tokenUserInfoDto.setToken(token);
        redisComponent.saveTokenUserInfoDto(tokenUserInfoDto);

        UserInfoVO userInfoVO = new UserInfoVO();
        BeanUtils.copyProperties(userInfo, userInfoVO, UserInfoVO.class);
        userInfoVO.setToken(tokenUserInfoDto.getToken());
        userInfoVO.setAdmin(tokenUserInfoDto.getAdmin());

        return userInfoVO;
    }

    private TokenUserInfoDto getTokenUserInfoDto(UserInfo userInfo) {
        TokenUserInfoDto tokenUserInfoDto = new TokenUserInfoDto();

        tokenUserInfoDto.setUserId(userInfo.getUserId());
        tokenUserInfoDto.setNickName(userInfo.getNickName());

        String adminEmails = appconfig.getAdminEmails();
        if (!StringTools.isEmpty(adminEmails) && ArrayUtils.contains(adminEmails.split(","), userInfo.getEmail())) {
            tokenUserInfoDto.setAdmin(true);
        } else {
            tokenUserInfoDto.setAdmin(false);
        }
        return tokenUserInfoDto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserInfo(UserInfo userInfo, MultipartFile avatarFile, MultipartFile avatarCover) throws IOException {
        if (avatarFile != null) {
            String baseFolder = appconfig.getProjectFolder() + Constants.FILE_FOLDER_FILE;
            File targetFileFolder = new File(baseFolder + Constants.FILE_FOLDER_AVATAR_NAME);
            if (!targetFileFolder.exists()) {
                targetFileFolder.mkdirs();
            }
            String filePath = targetFileFolder.getPath() + "/" + userInfo.getUserId() + Constants.IMAGE_SUFFIX;
            avatarFile.transferTo(new File(filePath));
            avatarCover.transferTo(new File(filePath + Constants.COVER_IMAGES_SUFFIX));
        }
        UserInfo dbInfo = this.userInfoMapper.selectByUserId(userInfo.getUserId());
        this.userInfoMapper.updateByUserId(userInfo, userInfo.getUserId());
        String contactNameUpdate = null;
        if (!dbInfo.getNickName().equals(userInfo.getNickName())) {
            contactNameUpdate = userInfo.getNickName();

            //更新token中的昵称
            TokenUserInfoDto tokenUserInfoDto = redisComponent.getTokenUserInfoDtoByUserId(userInfo.getUserId());
            tokenUserInfoDto.setNickName(contactNameUpdate);
            redisComponent.saveTokenUserInfoDto(tokenUserInfoDto);

            chatSessionUserService.updateContactName(userInfo.getUserId(), contactNameUpdate);
        }


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Integer status, String userId) {
        UserStatusEnum userStatusEnum = UserStatusEnum.getByStatus(status);
        if (null == userStatusEnum) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setStatus(status);
        this.userInfoMapper.updateByUserId(userInfo, userId);
    }

    @Override
    public void forceOffLine(String userId) {
        MessageSendDto messageSendDto = new MessageSendDto();
        messageSendDto.setContactId(userId);
        messageSendDto.setContactType(UserContactTypeEnum.USER.getType());
        messageSendDto.setMessageType(MessageTypeEnum.FORCE_OFF_LINE.getType());
        messageHandler.sendMessage(messageSendDto);
    }

}