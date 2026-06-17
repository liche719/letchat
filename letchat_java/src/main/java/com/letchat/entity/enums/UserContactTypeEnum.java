package com.letchat.entity.enums;

import com.letchat.utils.StringTools;
import lombok.Getter;

@Getter
public enum UserContactTypeEnum {
    USER(0, "U", "好友"),
    GROUP(1, "G", "群组");
    private Integer type;
    private String prefix;
    private String desc;

    UserContactTypeEnum(Integer type, String prefix, String desc) {
        this.type = type;
        this.prefix = prefix;
        this.desc = desc;
    }

    public static UserContactTypeEnum getByType(String type) {
        if (StringTools.isEmpty(type)) {
            return null;
        }
        for (UserContactTypeEnum typeEnum : UserContactTypeEnum.values()) {
            if (typeEnum.prefix.toString().equals(type)) {
                return typeEnum;
            }
        }
        return null;
    }

    public static UserContactTypeEnum getByPrefix(String prefix) {
        try {
            if (StringTools.isEmpty(prefix) || prefix.trim().isEmpty()) {
                return null;
            }
            prefix = prefix.substring(0, 1);
            for (UserContactTypeEnum typeEnum : UserContactTypeEnum.values()) {
                if (typeEnum.getPrefix().equals(prefix)) {
                    return typeEnum;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }


}
