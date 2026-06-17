package com.letchat.entity.query;


import lombok.Data;

/**
 * 靓号表参数
 */
@Data
public class UserInfoBeautyQuery extends BaseParam {


    /**
     * 自增id
     */
    private Integer id;

    /**
     * 邮箱
     */
    private String email;

    private String emailFuzzy;

    /**
     * 用户id
     */
    private String userId;

    private String userIdFuzzy;

    /**
     * 0：未使用 1：已使用
     */
    private Integer status;

}
