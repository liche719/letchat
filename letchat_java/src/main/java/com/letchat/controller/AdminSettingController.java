package com.letchat.controller;

import com.letchat.annotation.GlobalInterception;
import com.letchat.config.AppConfig;
import com.letchat.entity.Constants;
import com.letchat.entity.dto.SysSettingDto;
import com.letchat.entity.vo.ResponseVO;
import com.letchat.redis.RedisComponent;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;

@RestController("adminSettingController")
@RequestMapping("/admin")
public class AdminSettingController extends ABaseController {

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private AppConfig appConfig;


    @RequestMapping("/getSysSetting")
    @GlobalInterception(checkAdmin = true)
    public ResponseVO getSysSetting() {
        SysSettingDto sysSettingDto = redisComponent.getSysSetting();
        return getSuccessResponseVO(sysSettingDto);
    }


    @RequestMapping("/saveSysSetting")
    @GlobalInterception(checkAdmin = true)
    public ResponseVO saveSysSetting(SysSettingDto sysSettingDto, MultipartFile robotFile, MultipartFile robotCover) throws IOException {
        if (robotFile != null) {
            String baseFolder = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE;
            File targetFileFolder = new File(baseFolder + Constants.FILE_FOLDER_AVATAR_NAME);
            if (!targetFileFolder.exists()) {
                targetFileFolder.mkdirs();
            }
            String filePath = targetFileFolder.getPath() + "/" + Constants.ROBOT_UID + Constants.IMAGE_SUFFIX;
            robotFile.transferTo(new File(filePath));
            robotCover.transferTo(new File(filePath + Constants.COVER_IMAGES_SUFFIX));
        }
        redisComponent.saveSysSetting(sysSettingDto);
        return getSuccessResponseVO(null);
    }


}
