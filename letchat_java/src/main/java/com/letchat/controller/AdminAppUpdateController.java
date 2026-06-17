package com.letchat.controller;

import com.letchat.annotation.GlobalInterception;
import com.letchat.entity.po.AppUpdate;
import com.letchat.entity.query.AppUpdateQuery;
import com.letchat.entity.vo.ResponseVO;
import com.letchat.service.AppUpdateService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.IOException;

@RestController("adminAppUpdateController")
@RequestMapping("/admin")
public class AdminAppUpdateController extends ABaseController {

    @Resource
    private AppUpdateService appUpdateService;


    @RequestMapping("/loadUpdateList")
    @GlobalInterception(checkAdmin = true)
    public ResponseVO loadUpdateList(AppUpdateQuery query) {
        query.setOrderBy("id desc");
        return getSuccessResponseVO(appUpdateService.findListByPage(query));
    }


    @RequestMapping("/saveUpdate")
    @GlobalInterception(checkAdmin = true)
    public ResponseVO saveUpdate(Integer id,
                                 @NotEmpty String version,
                                 @NotEmpty String updateDesc,
                                 @NotEmpty Integer fileType,
                                 String outerLink,
                                 MultipartFile file) throws IOException {
        System.out.println("外链：" + outerLink);
        AppUpdate appUpdate = new AppUpdate();
        appUpdate.setId(id);
        appUpdate.setVersion(version);
        appUpdate.setUpdateDesc(updateDesc);
        appUpdate.setFileType(fileType);
        appUpdate.setOuterLink(outerLink);
        appUpdateService.saveUpdate(appUpdate, file);
        return getSuccessResponseVO(null);
    }


    @RequestMapping("/delUpdate")
    @GlobalInterception(checkAdmin = true)
    public ResponseVO delUpdate(@NotNull Integer id) {
        appUpdateService.deleteAppUpdateById(id);
        return getSuccessResponseVO(null);
    }


    @RequestMapping("/postUpdate")
    @GlobalInterception(checkAdmin = true)
    public ResponseVO postUpdate(@NotNull Integer id, @NotNull Integer status, String grayscaleUid) {
        appUpdateService.postUpdate(id, status, grayscaleUid);
        return getSuccessResponseVO(null);
    }


}
