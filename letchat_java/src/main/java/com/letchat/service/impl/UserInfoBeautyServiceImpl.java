package com.letchat.service.impl;

import com.letchat.entity.enums.BeautyAccountStatusEnum;
import com.letchat.entity.enums.PageSize;
import com.letchat.entity.enums.ResponseCodeEnum;
import com.letchat.entity.po.UserInfo;
import com.letchat.entity.po.UserInfoBeauty;
import com.letchat.entity.query.SimplePage;
import com.letchat.entity.query.UserInfoBeautyQuery;
import com.letchat.entity.query.UserInfoQuery;
import com.letchat.entity.vo.PaginationResultVO;
import com.letchat.exception.BusinessException;
import com.letchat.mappers.UserInfoBeautyMapper;
import com.letchat.mappers.UserInfoMapper;
import com.letchat.service.UserInfoBeautyService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;


/**
 * 靓号表 业务接口实现
 */
@Service("userInfoBeautyService")
public class UserInfoBeautyServiceImpl implements UserInfoBeautyService {

    @Resource
    private UserInfoBeautyMapper<UserInfoBeauty, UserInfoBeautyQuery> userInfoBeautyMapper;

    @Resource
    private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;


    /**
     * 根据条件查询列表
     */
    @Override
    public List<UserInfoBeauty> findListByParam(UserInfoBeautyQuery param) {
        return this.userInfoBeautyMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(UserInfoBeautyQuery param) {
        return this.userInfoBeautyMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<UserInfoBeauty> findListByPage(UserInfoBeautyQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<UserInfoBeauty> list = this.findListByParam(param);
        PaginationResultVO<UserInfoBeauty> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 根据Id删除
     */
    @Override
    public void deleteUserInfoBeautyById(Integer id) {
        //删除靓号之前，判断靓号是否已经使用
        UserInfoBeauty dbInfo = this.userInfoBeautyMapper.selectById(id);
        if (BeautyAccountStatusEnum.USED.getStatus().equals(dbInfo.getStatus())) {
            throw new BusinessException("靓号已使用,无法删除");
        }
        this.userInfoBeautyMapper.deleteById(id);
    }

    @Override
    public void saveAccount(UserInfoBeauty beauty) {
        if (beauty.getId() != null) {
            UserInfoBeauty dbInfo = this.userInfoBeautyMapper.selectById(beauty.getId());
            if (BeautyAccountStatusEnum.USED.getStatus().equals(dbInfo.getStatus())) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
        }

        UserInfoBeauty dbInfo = this.userInfoBeautyMapper.selectByEmail(beauty.getEmail());
        //新增的时候判断邮箱是否存在
        if (beauty.getId() == null && dbInfo != null) {
            throw new BusinessException("靓号邮箱已经存在");
        }

        //修改时判断邮箱是否存在，⭐
        if (beauty.getId() != null && dbInfo != null && dbInfo.getId() != null && !beauty.getId().equals(dbInfo.getId())) {
            throw new BusinessException("靓号邮箱已经存在");
        }

        //判断靓号是否存在
        dbInfo = this.userInfoBeautyMapper.selectByUserId(beauty.getUserId());
        if (beauty.getId() == null && dbInfo != null) {
            throw new BusinessException("靓号已经存在");
        }
        if (beauty.getId() != null && dbInfo != null && dbInfo.getId() != null && !beauty.getId().equals(dbInfo.getId())) {
            throw new BusinessException("靓号已经存在");
        }

        //判断邮箱是否已经注册
        UserInfo userInfo = this.userInfoMapper.selectByEmail(beauty.getEmail());
        if (userInfo != null) {
            throw new BusinessException("靓号邮箱已经注册");
        }
        userInfo = this.userInfoMapper.selectByUserId(beauty.getUserId());
        if (userInfo != null) {
            throw new BusinessException("靓号已经注册");
        }
        if (beauty.getId() != null) {
            this.userInfoBeautyMapper.updateById(beauty, beauty.getId());
        }else{
            beauty.setStatus(BeautyAccountStatusEnum.NO_USE.getStatus());
            this.userInfoBeautyMapper.insert(beauty);
        }


    }


}