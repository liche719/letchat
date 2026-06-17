package com.letchat.service;

import com.letchat.entity.po.AppUpdate;
import com.letchat.entity.query.AppUpdateQuery;
import com.letchat.entity.vo.PaginationResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;


/**
 * app发布 业务接口
 */
public interface AppUpdateService {

    /**
     * 根据条件查询列表
     */
    List<AppUpdate> findListByParam(AppUpdateQuery param);

    /**
     * 根据条件查询列表
     */
    Integer findCountByParam(AppUpdateQuery param);

    /**
     * 分页查询
     */
    PaginationResultVO<AppUpdate> findListByPage(AppUpdateQuery param);

    /**
     * 根据Id删除
     */
    Integer deleteAppUpdateById(Integer id);

    void saveUpdate(AppUpdate appUpdate, MultipartFile file) throws IOException;

    void postUpdate(Integer id, Integer status, String grayscaleUid);

    AppUpdate getLatestAppUpdate(String appVersion, String uid);
}