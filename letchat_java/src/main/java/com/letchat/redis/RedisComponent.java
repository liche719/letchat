package com.letchat.redis;

import com.letchat.entity.Constants;
import com.letchat.entity.dto.SysSettingDto;
import com.letchat.entity.dto.TokenUserInfoDto;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class RedisComponent {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    public Long getUserHeartBeat(String userId) {
        return (Long) redisTemplate.opsForValue().get(Constants.REDIS_KEY_WS_USER_HEART_BEAT + userId);
    }

    public void saveHeartBeat(String userId) {
        redisTemplate.opsForValue().set(Constants.REDIS_KEY_WS_USER_HEART_BEAT + userId, System.currentTimeMillis(), Constants.REDIS_KEY_EXPIRES_HEART_BEAT, TimeUnit.SECONDS);
    }

    public void removeUserHeartBeat(String userId) {
        redisTemplate.delete(Constants.REDIS_KEY_WS_USER_HEART_BEAT + userId);
    }

    public void saveTokenUserInfoDto(TokenUserInfoDto tokenUserInfoDto) {
        redisTemplate.opsForValue().set(Constants.REDIS_KEY_WS_TOKEN + tokenUserInfoDto.getToken(), tokenUserInfoDto, Constants.REDIS_KEY_EXPIRES_DAY * 2, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(Constants.REDIS_KEY_WS_TOKEN_USERID + tokenUserInfoDto.getUserId(), tokenUserInfoDto.getToken(), Constants.REDIS_KEY_EXPIRES_DAY * 2, TimeUnit.SECONDS);
    }

    public TokenUserInfoDto getTokenUserInfoDto(String token) {
        return (TokenUserInfoDto) redisTemplate.opsForValue().get(Constants.REDIS_KEY_WS_TOKEN + token);
    }

    public TokenUserInfoDto getTokenUserInfoDtoByUserId(String userId) {
        String token = (String) redisTemplate.opsForValue().get(Constants.REDIS_KEY_WS_TOKEN_USERID + userId);
        return getTokenUserInfoDto(token);
    }

    public void cleanUserTokenByUserId(String userId) {
        String token = (String) redisTemplate.opsForValue().get(Constants.REDIS_KEY_WS_TOKEN_USERID + userId);
        if (token == null) {
            return;
        }
        redisTemplate.delete(Constants.REDIS_KEY_WS_TOKEN + token);
        redisTemplate.delete(Constants.REDIS_KEY_WS_TOKEN_USERID + userId);
    }

    public void saveSysSetting(SysSettingDto sysSettingDto) {
        redisTemplate.opsForValue().set(Constants.REDIS_KEY_SYS_SETTING, sysSettingDto);
    }

    public SysSettingDto getSysSetting() {
        SysSettingDto sysSettingDto = (SysSettingDto) redisTemplate.opsForValue().get(Constants.REDIS_KEY_SYS_SETTING);
        sysSettingDto = sysSettingDto == null ? new SysSettingDto() : sysSettingDto;
        return sysSettingDto;
    }

    //清空联系人
    public void clearUserContact(String userId) {
        redisTemplate.delete(Constants.REDIS_KEY_USER_CONTACT + userId);
    }

    //批量添加联系人
    public void addUserContactBatch(String userId, List<String> contactIdList) {
        for (String contactId : contactIdList) {
            redisTemplate.opsForList().rightPush(Constants.REDIS_KEY_USER_CONTACT + userId, contactId);
        }
    }

    //添加联系人
    public void addUserContact(String userId, String contactId) {
        List<String> contactIdList = getUserContactList(userId);
        if (contactIdList.contains(contactId)) {
            return;
        }
        redisTemplate.opsForList().rightPush(Constants.REDIS_KEY_USER_CONTACT + userId, contactId);
    }

    public List<String> getUserContactList(String userId) {
        return (List<String>) (List<?>) redisTemplate.opsForList().range(Constants.REDIS_KEY_USER_CONTACT + userId, 0, -1);
    }

    public void removeUserContact(String userId, String contactId) {
        redisTemplate.opsForList().remove(Constants.REDIS_KEY_USER_CONTACT + userId, 1, contactId);

    }
}
