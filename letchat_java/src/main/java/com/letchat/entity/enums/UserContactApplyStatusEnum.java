package com.letchat.entity.enums;

import com.letchat.utils.StringTools;
import lombok.Getter;

@Getter
public enum UserContactApplyStatusEnum {
    INIT(0, "待处理"),
    PASS(1, "同意"),
    REJECT(2, "拒绝"),
    BLACKLIST(3, "拉黑");

    private Integer status;
    private String desc;

    UserContactApplyStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    public static UserContactApplyStatusEnum getByStatus(String status) {
        try {
            if (StringTools.isEmpty(status)) {
                return null;
            }
            return UserContactApplyStatusEnum.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static UserContactApplyStatusEnum getByStatus(Integer status) {
        for (UserContactApplyStatusEnum value : UserContactApplyStatusEnum.values()) {
            if (value.getStatus().equals(status)) {
                return value;
            }
        }
        return null;
    }
}
