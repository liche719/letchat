package com.letchat.entity.query;

import lombok.Data;

import java.io.Serializable;


/**
 * app发布参数
 */
@Data
public class AppUpdateQuery extends BaseParam implements Serializable {
	private static final long serialVersionUID = 1L;


	/**
	 * 自增ID
	 */
	private Integer id;

	/**
	 * 版本号
	 */
	private String version;

	private String versionFuzzy;

	/**
	 * 更新描述
	 */
	private String updateDesc;

	private String updateDescFuzzy;

	/**
	 * 创建时间
	 */
	private String createTime;

	private String createTimeStart;

	private String createTimeEnd;

	/**
	 * 0:未发布 1:灰度发布 2:全网发布
	 */
	private Integer status;

	/**
	 * 灰度uid
	 */
	private String grayscaleUid;

	private String grayscaleUidFuzzy;

	/**
	 * 文件类型0:本地文件 1:外链
	 */
	private Integer fileType;

	/**
	 * 外链地址
	 */
	private String outerLink;

	private String outerLinkFuzzy;


}
