package com.letchat.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.letchat.entity.Constants;
import lombok.Data;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SysSettingDto implements Serializable {
    private static final long serialVersionUID = 1L;

    //最大群组数
    private Integer maxGroupCount = 5;
    //最大群组成员数
    private Integer maxGroupMemberCount = 500;
    //最大图片大小
    private Integer maxImageSize = 2;
    //最大视频大小
    private Integer maxVideoSize = 5;
    //最大文件大小
    private Integer maxFileSize = 5;
    //机器人id
    private String robotUid = Constants.ROBOT_UID;
    //机器人昵称
    private String robotNickName = "聊天ai机器人";
    //机器人欢迎语
    private String robotWelcome = "欢迎来和我聊天噢~";

}
