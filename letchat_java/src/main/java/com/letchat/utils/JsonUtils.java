package com.letchat.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.letchat.entity.enums.ResponseCodeEnum;
import com.letchat.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private JsonUtils() {
    }

    public static String convertObj2Json(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException exception) {
            log.error("Failed to serialize object to JSON", exception);
            throw new BusinessException(ResponseCodeEnum.CODE_601);
        }
    }

    public static <T> T convertJson2Obj(String json, Class<T> classz) {
        try {
            return OBJECT_MAPPER.readValue(json, classz);
        } catch (Exception e) {
            log.error("convertJson2Obj异常, json:{}", json);
            throw new BusinessException(ResponseCodeEnum.CODE_601);
        }
    }

    public static <T> List<T> convertJsonArray2List(String json, Class<T> classz) {
        try {
            JavaType listType = OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, classz);
            return OBJECT_MAPPER.readValue(json, listType);
        } catch (Exception e) {
            log.error("convertJsonArray2List,json:{}", json, e);
            throw new BusinessException(ResponseCodeEnum.CODE_601);
        }
    }
}
