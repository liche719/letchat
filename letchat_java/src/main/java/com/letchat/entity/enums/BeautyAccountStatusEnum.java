package com.letchat.entity.enums;

import lombok.Getter;

@Getter
public enum BeautyAccountStatusEnum {

    NO_USE(0, "未使用"),
    USED(1, "已使用");
    private Integer status;
    private String desc;

    BeautyAccountStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    public static BeautyAccountStatusEnum getByStatus(Integer status) {
        for (BeautyAccountStatusEnum value : BeautyAccountStatusEnum.values()) {
            if (value.status.equals(status)) {
                return value;
            }
        }
        return null;
    }

}
