package com.letchat.entity.dto;

import com.letchat.entity.enums.UserContactStatusEnum;
import lombok.Data;

@Data
public class UserContactSearchResultDto {
    private String contactId;
    private String contactType;
    private String nickName;
    private Integer status;
    private String statusName;
    private Integer sex;
    private String areaName;

    public String getStatusName() {
        UserContactStatusEnum statusEnum = UserContactStatusEnum.getByStatus(status);
        return statusEnum == null ? null : statusEnum.getDesc();
    }

}
