package com.letchat.controller;

import com.letchat.annotation.GlobalInterception;
import com.letchat.entity.enums.ResponseCodeEnum;
import com.letchat.entity.po.GroupInfo;
import com.letchat.entity.query.GroupInfoQuery;
import com.letchat.entity.vo.PaginationResultVO;
import com.letchat.entity.vo.ResponseVO;
import com.letchat.exception.BusinessException;
import com.letchat.service.GroupInfoService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.constraints.NotEmpty;

@RestController("adminGroupController")
@RequestMapping("/admin")
public class AdminGroupController extends ABaseController {

    @Resource
    private GroupInfoService groupInfoService;


    @RequestMapping("/loadGroup")
    @GlobalInterception(checkAdmin = true)
    public ResponseVO loadGroup(GroupInfoQuery query) {
        query.setOrderBy("create_time desc");
        query.setQueryMemberCount(true);
        query.setQueryGroupOwnerNickName(true);
        PaginationResultVO<GroupInfo> resultVO = groupInfoService.findListByPage(query);
        return getSuccessResponseVO(resultVO);
    }
    

    @RequestMapping("/dissolutionGroup")
    @GlobalInterception(checkAdmin = true)
    public ResponseVO dissolutionGroup(@NotEmpty String groupId) {
        GroupInfo groupInfo = groupInfoService.getGroupInfoByGroupId(groupId);
        if (groupInfo == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        groupInfoService.dissolutionGroup(groupInfo.getGroupOwnerId(), groupId);
        return getSuccessResponseVO(null);
    }


}
