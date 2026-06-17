package com.letchat.controller;

import com.letchat.annotation.GlobalInterception;
import com.letchat.config.AppConfig;
import com.letchat.entity.Constants;
import com.letchat.entity.enums.AppUpdateFileTypeEnum;
import com.letchat.entity.po.AppUpdate;
import com.letchat.entity.vo.AppUpdateVO;
import com.letchat.entity.vo.ResponseVO;
import com.letchat.service.AppUpdateService;
import com.letchat.utils.CopyTools;
import com.letchat.utils.StringTools;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.io.File;
import java.util.Arrays;

@RestController("updateController")
@RequestMapping("/update")
public class UpdateController extends ABaseController {

    @Resource
    private AppUpdateService appUpdateService;

    @Resource
    private AppConfig appConfig;


    /**
     * 检查更新
     * @param version
     * @return
     */
    @RequestMapping("/checkVersion")
    @GlobalInterception
    public ResponseVO checkVersion(String version, String uid) {
        if (StringTools.isEmpty(version)) {
            return getServerErrorResponseVO(null);
        }
        AppUpdate appUpdate = appUpdateService.getLatestAppUpdate(version, uid);
        if (appUpdate == null) {
            return getSuccessResponseVO(null);
        }
        AppUpdateVO updateVO = CopyTools.copy(appUpdate, AppUpdateVO.class);
        if (AppUpdateFileTypeEnum.LOCAL.getType().equals(appUpdate.getFileType())) {
            File file = new File(appConfig.getProjectFolder() + Constants.APP_UPDATE_FOLDER + appUpdate.getId() + Constants.APP_EXE_SUFFIX);
            updateVO.setSize(file.length());
        }
        updateVO.setUpdateList(Arrays.asList(appUpdate.getUpdateDescArray()));
        updateVO.setFileName(Constants.APP_NAME + appUpdate.getVersion() + Constants.APP_EXE_SUFFIX);
        return getSuccessResponseVO(updateVO);
    }


}
