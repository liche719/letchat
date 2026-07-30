package com.letchat.aspect;

import com.letchat.annotation.GlobalInterception;
import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.entity.enums.ResponseCodeEnum;
import com.letchat.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;

@Aspect
@Component("globalOperationAspect")
@Slf4j
public class GlobalOperationAspect {

    @Before("@annotation(com.letchat.annotation.GlobalInterception)")
    public void interceptorDo(JoinPoint point) {
        try {
            Method method = ((MethodSignature) point.getSignature()).getMethod();
            GlobalInterception interceptor = method.getAnnotation(GlobalInterception.class);
            if (interceptor == null) {
                return;
            }
            if (interceptor.checkLogin() || interceptor.checkAdmin()) {
                checkLogin(interceptor.checkAdmin());
            }
        } catch (BusinessException e) {
            log.error("全局拦截异常", e);
            throw e;
        } catch (Exception e) {
            log.error("全局拦截异常", e);
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        } catch (Throwable e) {
            log.error("全局拦截异常", e);
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        }
    }

    private void checkLogin(Boolean checkAdmin) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TokenUserInfoDto tokenUserInfoDto)) {
            throw new BusinessException(ResponseCodeEnum.CODE_901);
        }
        if (checkAdmin && !Boolean.TRUE.equals(tokenUserInfoDto.getAdmin())) {
            throw new BusinessException(ResponseCodeEnum.CODE_404);
        }
    }

}
