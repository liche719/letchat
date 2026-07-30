package com.letchat.controller;

import com.letchat.annotation.GlobalInterception;
import com.letchat.entity.Constants;
import com.letchat.entity.vo.CheckCodeVO;
import com.letchat.entity.vo.ResponseVO;
import com.letchat.entity.vo.UserInfoVO;
import com.letchat.entity.vo.WebSocketTicketVO;
import com.letchat.exception.BusinessException;
import com.letchat.redis.RedisComponent;
import com.letchat.service.UserInfoService;
import com.wf.captcha.SpecCaptcha;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;

@RestController("accountController")
@RequestMapping("/account")
@Validated
@Slf4j
public class AccountController extends ABaseController {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private UserInfoService userInfoService;

    @Resource
    private RedisComponent redisComponent;

    @Value("${letchat.websocket.ticket-ttl-seconds:30}")
    private long webSocketTicketTtlSeconds;


    @RequestMapping("/checkCode")
    public ResponseVO checkCode() {
        SpecCaptcha captcha = new SpecCaptcha(100, 42, 4); //创建验证码，图片宽高
        String code = captcha.text();
        String checkCodeKey = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey, code, Constants.REDIS_TIME_1MIN * 5L, TimeUnit.SECONDS);
        String checkCodeBase64 = captcha.toBase64();
        CheckCodeVO checkCodeVO = new CheckCodeVO();
        checkCodeVO.setCheckCode(checkCodeBase64);
        checkCodeVO.setCheckCodeKey(checkCodeKey);
        log.debug("Generated captcha, checkCodeKey={}", checkCodeKey);
        return getSuccessResponseVO(checkCodeVO);
    }


    @RequestMapping("/register")
    public ResponseVO register(@NotEmpty String checkCodeKey,
                               @NotEmpty @Email String email,
                               @NotEmpty @Pattern(regexp = Constants.REGEX_PASSWORD) String password,
                               @NotEmpty String nickName,
                               @NotEmpty String checkCode) {
        try {
            if (!checkCode.equalsIgnoreCase(redisTemplate.opsForValue().get(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey))) {
                throw new BusinessException("图片验证码不正确");
            }
            userInfoService.register(email, nickName, password);
            return getSuccessResponseVO(null);
        } finally {
            redisTemplate.delete(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey);
        }
    }


    @RequestMapping("/login")
    public ResponseVO login(String checkCodeKey,
                            @NotEmpty @Email String email,
                            @NotEmpty String password,
                            @NotEmpty String checkCode) {
        try {
            if (!checkCode.equalsIgnoreCase(redisTemplate.opsForValue().get(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey))) {
                throw new BusinessException("图片验证码不正确");
            }
            UserInfoVO userInfoVO = userInfoService.login(email, password);
            return getSuccessResponseVO(userInfoVO);
        } finally {
            redisTemplate.delete(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey);
        }
    }


    @RequestMapping("/getSysSetting")
    @GlobalInterception
    public ResponseVO login() {
        return getSuccessResponseVO(redisComponent.getSysSetting());
    }

    @RequestMapping("/wsTicket")
    @GlobalInterception
    public ResponseVO<WebSocketTicketVO> createWebSocketTicket(HttpServletRequest request) {
        String ticket = redisComponent.createWebSocketTicket(getTokenUserInfo(request));
        return getSuccessResponseVO(new WebSocketTicketVO(ticket, Math.max(1, webSocketTicketTtlSeconds)));
    }
}
