package com.letchat.service;

import com.letchat.entity.po.UserInfoBeauty;
import com.letchat.entity.query.UserInfoBeautyQuery;
import com.letchat.entity.vo.PaginationResultVO;

import java.util.List;


/**
 * 靓号表 业务接口
 */
public interface UserInfoBeautyService {

    /**
     * 根据条件查询列表
     */
    List<UserInfoBeauty> findListByParam(UserInfoBeautyQuery param);

    /**
     * 根据条件查询列表
     */
    Integer findCountByParam(UserInfoBeautyQuery param);

    /**
     * 分页查询
     */
    PaginationResultVO<UserInfoBeauty> findListByPage(UserInfoBeautyQuery param);

    /**
     * 根据Id删除
     */
    void deleteUserInfoBeautyById(Integer id);

    void saveAccount(UserInfoBeauty beauty);
}