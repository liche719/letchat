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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
            Path avatarFolder = new File(
                    appConfig.getProjectFolderPath().toString()
                            + Constants.FILE_FOLDER_FILE
                            + Constants.FILE_FOLDER_AVATAR_NAME
            ).toPath().toAbsolutePath().normalize();
            Files.createDirectories(avatarFolder);
            Path avatarPath = avatarFolder.resolve(Constants.ROBOT_UID + Constants.IMAGE_SUFFIX);
            robotFile.transferTo(avatarPath.toFile());
            Path coverPath = avatarFolder.resolve(Constants.ROBOT_UID + Constants.IMAGE_SUFFIX + Constants.COVER_IMAGES_SUFFIX);
            if (robotCover == null || robotCover.isEmpty()) {
                Files.copy(avatarPath, coverPath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                robotCover.transferTo(coverPath.toFile());
            }
        }
        redisComponent.saveSysSetting(sysSettingDto);
        return getSuccessResponseVO(null);
    }


}
