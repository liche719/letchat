package com.letchat.entity.enums;

import lombok.Getter;

@Getter
public enum AppUpdateFileTypeEnum {

    LOCAL(0, "本地"),
    OUTER_LINK(1, "外链");
    private Integer type;
    private String desc;

    AppUpdateFileTypeEnum(Integer type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public static AppUpdateFileTypeEnum getByType(Integer type) {
        for (AppUpdateFileTypeEnum value : AppUpdateFileTypeEnum.values()) {
            if (value.type.equals(type)) {
                return value;
            }
        }
        return null;
    }

}
