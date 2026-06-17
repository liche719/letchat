package com.letchat.entity;

import com.letchat.entity.enums.UserContactTypeEnum;

public class Constants {

    public static final Integer REDIS_KEY_EXPIRES_HEART_BEAT = 5;

    public static final Integer REDIS_TIME_1MIN = 60;   //TimeUnit.SECONDS

    public static final Integer REDIS_KEY_EXPIRES_DAY = REDIS_TIME_1MIN * 60 * 24;

    public static final Integer LENGTH_11 = 11;

    public static final Integer LENGTH_20 = 20;


    public static final String REDIS_KEY_CHECK_CODE = "letchat:checkcode:";

    public static final String REDIS_KEY_WS_USER_HEART_BEAT = "letchat:ws:user:heartbeat:";

    public static final String REDIS_KEY_WS_TOKEN = "letchat:ws:token:";
    public static final String REDIS_KEY_WS_TOKEN_USERID = "letchat:ws:token:userid:";

    public static final String ROBOT_UID = UserContactTypeEnum.USER.getPrefix() + "robot";

    public static final String REDIS_KEY_SYS_SETTING = "letchat:syssetting:";

    public static final String FILE_FOLDER_FILE = "/file/";

    public static final String FILE_FOLDER_AVATAR_NAME = "avatar/";

    public static final String IMAGE_SUFFIX = ".png";

    public static final String COVER_IMAGES_SUFFIX = "_cover.png";

    public static final String APPLY_INFO_TEMPLATE = "我是%s";

    public static final String REGEX_PASSWORD = "^(?=.*\\d)(?=.*[a-zA-Z])[\\da-zA-Z~!@#$%^&*_]{8,18}$";

    public static final String APP_UPDATE_FOLDER = "/app/";

    public static final String APP_EXE_SUFFIX = ".exe";

    public static final String APP_NAME = "LetChatSetup";

    // 用户联系人
    public static final String REDIS_KEY_USER_CONTACT = "letchat:ws:user:contact:";

    public static final Long MillisSECONDS_3days_ago = 3 * 24 * 60 * 60 * 1000L;

    public static final String[] IMAGE_SUFFIX_LIST = new String[]{".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp", ".svg", ".ico"};

    public static final String[] VIDEO_SUFFIX_LIST = new String[]{".mp4", ".avi", ".rmvb", ".mov", ".webm"};


    public static final Long FILE_SIZE_MB = 1024 * 1024L;
}
