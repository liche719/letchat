package com.letchat.entity.enums;

import lombok.Getter;

@Getter
public enum AppUpdateStatusEnum {

    INIT(0, "未发表"),
    GRAYSCALE(1, "灰度发布"),
    ALL(2, "全网发布");

    private Integer status;
    private String desc;

    AppUpdateStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    public static AppUpdateStatusEnum getByStatus(Integer status) {
        for (AppUpdateStatusEnum value : AppUpdateStatusEnum.values()) {
            if (value.status.equals(status)) {
                return value;
            }
        }
        return null;
    }
}
