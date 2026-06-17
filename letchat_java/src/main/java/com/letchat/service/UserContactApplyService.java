package com.letchat.service;

import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.entity.po.UserContactApply;
import com.letchat.entity.query.UserContactApplyQuery;
import com.letchat.entity.vo.PaginationResultVO;

import java.util.List;


/**
 * 联系人申请 业务接口
 */
public interface UserContactApplyService {

    /**
     * 根据条件查询列表
     */
    List<UserContactApply> findListByParam(UserContactApplyQuery param);

    /**
     * 根据条件查询列表
     */
    Integer findCountByParam(UserContactApplyQuery param);

    /**
     * 分页查询
     */
    PaginationResultVO<UserContactApply> findListByPage(UserContactApplyQuery param);

    /**
     * 根据ApplyId查询对象
     */
    UserContactApply getUserContactApplyByApplyId(Integer applyId);

    Integer applyAdd(TokenUserInfoDto tokenUserInfoDto, String contactId, String applyInfo);

    void dealWithApply(String userId, Integer applyId, Integer status);


}