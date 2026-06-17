package com.letchat.entity.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class AppUpdateVO implements Serializable {
    private static final long serialVersionUID = 1L;


    private Integer id;

    /**
     * 版本号
     */
    private String version;

    /**
     * 更新内容
     */
    private List<String> updateList;

    private Long size;

    private String fileName;

    private String fileType;

    private String outerLink;



}
