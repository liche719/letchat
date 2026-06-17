package com.letchat.entity.enums;

import lombok.Getter;

@Getter
public enum GroupAddOrRemoveEnum {
    ADD(0, "添加"),
    REMOVE(1, "移除");

    private Integer status;
    private String desc;

    GroupAddOrRemoveEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    public static GroupAddOrRemoveEnum getByStatus(Integer status) {
        for (GroupAddOrRemoveEnum value : GroupAddOrRemoveEnum.values()) {
            if (value.getStatus().equals(status)) {
                return value;
            }
        }
        return null;
    }
}
