package com.letchat.entity.po;

import lombok.Data;

import java.io.Serializable;


/**
 * 靓号表
 */
@Data
public class UserInfoBeauty implements Serializable {

    private static final long serialVersionUID = 1L;


    /**
     * 自增id
     */
    private Integer id;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 用户id
     */
    private String userId;

    /**
     * 0：未使用 1：已使用
     */
    private Integer status;


    @Override
    public String toString() {
        return "自增id:" + (id == null ? "空" : id) + "，邮箱:" + (email == null ? "空" : email) + "，用户id:" + (userId == null ? "空" : userId) + "，0：未使用 1：已使用:" + (status == null ? "空" : status);
    }
}
