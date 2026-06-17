package com.letchat.service.impl;

import com.letchat.config.AppConfig;
import com.letchat.entity.Constants;
import com.letchat.entity.enums.AppUpdateFileTypeEnum;
import com.letchat.entity.enums.AppUpdateStatusEnum;
import com.letchat.entity.enums.PageSize;
import com.letchat.entity.enums.ResponseCodeEnum;
import com.letchat.entity.po.AppUpdate;
import com.letchat.entity.query.AppUpdateQuery;
import com.letchat.entity.query.SimplePage;
import com.letchat.entity.vo.PaginationResultVO;
import com.letchat.exception.BusinessException;
import com.letchat.mappers.AppUpdateMapper;
import com.letchat.service.AppUpdateService;
import com.letchat.utils.StringTools;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;


/**
 * app发布 业务接口实现
 */
@Service("appUpdateService")
public class AppUpdateServiceImpl implements AppUpdateService {

    @Resource
    private AppUpdateMapper<AppUpdate, AppUpdateQuery> appUpdateMapper;

    @Resource
    private AppConfig appConfig;


    /**
     * 根据条件查询列表
     */
    @Override
    public List<AppUpdate> findListByParam(AppUpdateQuery param) {
        return this.appUpdateMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(AppUpdateQuery param) {
        return this.appUpdateMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<AppUpdate> findListByPage(AppUpdateQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<AppUpdate> list = this.findListByParam(param);
        PaginationResultVO<AppUpdate> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 根据Id删除
     */
    @Override
    public Integer deleteAppUpdateById(Integer id) {
        AppUpdate db = this.appUpdateMapper.selectById(id);
        if (!AppUpdateStatusEnum.INIT.getStatus().equals(db.getStatus())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        return this.appUpdateMapper.deleteById(id);
    }

    @Override
    public void saveUpdate(AppUpdate appUpdate, MultipartFile file) throws IOException {
        AppUpdateFileTypeEnum fileTypeEnum = AppUpdateFileTypeEnum.getByType(appUpdate.getFileType());
        if (null == fileTypeEnum) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        if (appUpdate.getId() != null) {
            AppUpdate db = this.appUpdateMapper.selectById(appUpdate.getId());
            if (!AppUpdateStatusEnum.INIT.getStatus().equals(db.getStatus())) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
        }

        AppUpdateQuery updateQuery = new AppUpdateQuery();
        updateQuery.setOrderBy("id desc");
        updateQuery.setSimplePage(new SimplePage(0, 1));
        List<AppUpdate> appUpdateList = this.appUpdateMapper.selectList(updateQuery);

        if (!appUpdateList.isEmpty()) {
            AppUpdate dbLastest = appUpdateList.get(0);

            // 版本规则：
            // * 新增版本要大于最高版本
            // * 最高版本修改: 不能和已有版本相同，不能小于已有版本
            // * 非最高版本修改: 不能和已有版本相同，不能大于最高版本

            // 新增
            if (appUpdate.getId() == null && !v1Gtv2(appUpdate.getVersion(), dbLastest.getVersion())) {
                throw new BusinessException("新增版本必须大于历史版本");
            }

            // 修改最高版本
            if (appUpdate.getId() != null && appUpdate.getId().equals(dbLastest.getId()) && !v1Gtv2(appUpdate.getVersion(), dbLastest.getVersion())) {
                // 不能小于历史版本
                throw new BusinessException("修改版本必须大于历史版本");

            }

            // 修改非最高版本
            AppUpdate versionDb = appUpdateMapper.selectByVersion(appUpdate.getVersion());
            if (appUpdate.getId() != null && versionDb != null && !appUpdate.getId().equals(versionDb.getId())) {
                throw new BusinessException("该版本已存在");
            }
            if (appUpdate.getId() != null && !appUpdate.getId().equals(dbLastest.getId()) && v1Gtv2(appUpdate.getVersion(), dbLastest.getVersion())) {
                throw new BusinessException("当前版本不能大于最高版本");
            }
        }

        if (appUpdate.getId() == null) {  //新增
            appUpdate.setCreateTime(new Date());
            appUpdate.setStatus(AppUpdateStatusEnum.INIT.getStatus());
            appUpdateMapper.insert(appUpdate);
        } else {  //修改
            appUpdateMapper.updateById(appUpdate, appUpdate.getId());
        }
        if (file != null) {
            File folder = new File(appConfig.getProjectFolder() + Constants.APP_UPDATE_FOLDER);
            if (!folder.exists()) {
                folder.mkdirs();
            }
            file.transferTo(new File(folder.getAbsoluteFile() + "/" + appUpdate.getId() + Constants.APP_EXE_SUFFIX));
        }
    }

    //版本号的比较，1.0.2 > 1.0.1
    //版本号里面只含两个"."
    //若 version1 > version2, return true
    private Boolean v1Gtv2(String version1, String version2) {
        String[] v1 = version1.split("\\.");
        String[] v2 = version2.split("\\.");
        int maxLength = Math.max(v1.length, v2.length);
        for (int i = 0; i < maxLength; i++) {
            // 获取当前段的值，如果超出数组长度则默认为0
            int num1 = i < v1.length ? Integer.parseInt(v1[i]) : 0;
            int num2 = i < v2.length ? Integer.parseInt(v2[i]) : 0;

            if (num1 > num2) {
                return true;
            } else if (num1 < num2) {
                return false;
            }
            // 相等则继续比较下一段
        }
        // 所有段都相等
        return false;
    }


    @Override
    public void postUpdate(Integer id, Integer status, String grayscaleUid) {
        AppUpdateStatusEnum statusEnum = AppUpdateStatusEnum.getByStatus(status);
        if (null == statusEnum) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (AppUpdateStatusEnum.GRAYSCALE == statusEnum && StringTools.isEmpty(grayscaleUid)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (AppUpdateStatusEnum.GRAYSCALE != statusEnum) {
            grayscaleUid = "";
        }
        AppUpdate appUpdate = new AppUpdate();
        appUpdate.setStatus(status);
        appUpdate.setGrayscaleUid(grayscaleUid);
        appUpdateMapper.updateById(appUpdate, id);
    }

    @Override
    public AppUpdate getLatestAppUpdate(String appVersion, String uid) {
        AppUpdate appUpdate = appUpdateMapper.selectLatestUpdate(appVersion, uid);
        return appUpdate;
    }

}