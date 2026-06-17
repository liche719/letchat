package com.letchat.entity.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class TokenUserInfoDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String token;
    private String userId;
    private String nickName;
    private Boolean admin;
}
