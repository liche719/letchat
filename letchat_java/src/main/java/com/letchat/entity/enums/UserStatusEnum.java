package com.letchat.entity.enums;

import lombok.Getter;

@Getter
public enum UserStatusEnum {

    DISABLE(0, "禁用"),
    ENABLE(1, "启用");

    private Integer status;
    private String desc;

    UserStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    public static UserStatusEnum getByStatus(Integer status) {
        for (UserStatusEnum value : UserStatusEnum.values()) {
            if (value.status.equals(status)) {
                return value;
            }
        }
        return null;
    }
}
