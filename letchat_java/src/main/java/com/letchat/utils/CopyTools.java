package com.letchat.utils;

import org.springframework.beans.BeanUtils;

public class CopyTools {

    // 复制并返回目标对象,要求返回对象为目标对象,参数为(Object source, Class targetClass)
    public static <T> T copy(Object source, Class<T> targetClass) {
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, target);
            return target;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


}
