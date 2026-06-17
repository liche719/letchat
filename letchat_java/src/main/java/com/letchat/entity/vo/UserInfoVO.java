package com.letchat.entity.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserInfoVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 性别
     */
    private Integer sex;

    private Integer joinType;

    /**
     * 个性签名
     */
    private String personalSignature;

    private String areaCode;

    private String areaName;

    private String token;

    private Boolean admin;

    private Integer contactStatus;

}
