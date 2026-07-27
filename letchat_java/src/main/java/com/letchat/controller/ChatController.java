package com.letchat.controller;

import com.letchat.annotation.GlobalInterception;
import com.letchat.config.AppConfig;
import com.letchat.entity.Constants;
import com.letchat.entity.dto.MessageSendDto;
import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.entity.enums.MessageTypeEnum;
import com.letchat.entity.enums.ResponseCodeEnum;
import com.letchat.entity.po.ChatMessage;
import com.letchat.entity.vo.PaginationResultVO;
import com.letchat.entity.vo.ResponseVO;
import com.letchat.exception.BusinessException;
import com.letchat.service.ChatMessageService;
import com.letchat.utils.StringTools;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

@RestController("chatController")
@RequestMapping("/chat")
@Slf4j
@Validated
public class ChatController extends ABaseController {

    @Resource
    private AppConfig appConfig;

    @Resource
    private ChatMessageService chatMessageService;

    /**
     * 发送消息 TODO 使用RabbitMQ
     */
    @RequestMapping("/sendMessage")
    @GlobalInterception
    public ResponseVO sendMessage(HttpServletRequest request,
                                  @NotEmpty String contactId,
                                  @NotEmpty @Size(max = 500) String messageContent,
                                  @NotNull Integer messageType,
                                  Long fileSize,
                                  String fileName,
                                  Integer fileType) {
        log.info("调用发消息接口");
        MessageTypeEnum messageTypeEnum = MessageTypeEnum.getByType(messageType);
        if (null == messageTypeEnum || !ArrayUtils.contains(new Integer[]{MessageTypeEnum.CHAT.getType(), MessageTypeEnum.MEDIA_CHAT.getType()}, messageType)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSendUserId(tokenUserInfoDto.getUserId());
        chatMessage.setSendUserNickName(tokenUserInfoDto.getNickName());
        chatMessage.setContactId(contactId);
        chatMessage.setMessageContent(messageContent);
        if (MessageTypeEnum.MEDIA_CHAT.getType().equals(messageType)) {
            chatMessage.setFileSize(fileSize);
            chatMessage.setFileName(fileName);
            chatMessage.setFileType(fileType);
        }
        chatMessage.setMessageType(messageType);
        MessageSendDto messageSendDto = chatMessageService.saveMessage(chatMessage);
        return getSuccessResponseVO(messageSendDto);
    }


    /**
     * 上传文件
     *
     * @param request
     * @param messageId
     * @param file
     * @param coverFile
     * @return
     */
    @RequestMapping("/uploadFile")
    @GlobalInterception
    public ResponseVO uploadFile(HttpServletRequest request,
                                 @NotNull Long messageId,
                                 @NotNull MultipartFile file,
                                 @NotNull MultipartFile coverFile) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);
        chatMessageService.saveMessageFile(tokenUserInfoDto.getUserId(), messageId, file, coverFile);
        return getSuccessResponseVO(null);
    }

    /**
     * 加载联系人列表
     *
     * @param request
     * @param contactId
     * @param pageNo
     * @return
     */
    @RequestMapping("/loadChatMessage")
    @GlobalInterception
    public ResponseVO loadChatMessage(HttpServletRequest request,
                                      @NotEmpty String contactId,
                                      Integer pageNo) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);
        PaginationResultVO<ChatMessage> result = chatMessageService.loadChatMessages(
                tokenUserInfoDto.getUserId(),
                contactId,
                pageNo
        );
        return getSuccessResponseVO(result);
    }

    /**
     * 下载文件
     *
     * @param request
     * @param response
     * @param fileId
     * @param showCover
     */
    @RequestMapping("/downloadFile")
    public void downloadFile(HttpServletRequest request,
                             HttpServletResponse response,
                             @NotEmpty String fileId,
                             @NotNull Boolean showCover) throws IOException {
        File file;
        if (StringTools.isNumber(fileId)) {
            TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);
            file = chatMessageService.downloadFile(tokenUserInfoDto, Long.parseLong(fileId), showCover);
        } else {
            file = resolveAvatarFile(fileId, showCover);
        }

        response.setContentType("application/x-msdownload;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;");
        response.setContentLengthLong(file.length());
        try (FileInputStream in = new FileInputStream(file);
             OutputStream out = response.getOutputStream()) {
            byte[] byteData = new byte[1024];
            int len;
            while ((len = in.read(byteData)) != -1) {
                out.write(byteData, 0, len);
            }
            out.flush();
        }
    }

    private File resolveAvatarFile(String fileId, boolean showCover) {
        File avatarFolder = new File(
                appConfig.getProjectFolderPath().toString()
                        + Constants.FILE_FOLDER_FILE
                        + Constants.FILE_FOLDER_AVATAR_NAME
        );
        String fileName = fileId + Constants.IMAGE_SUFFIX;
        if (showCover) {
            fileName += Constants.COVER_IMAGES_SUFFIX;
        }
        Path avatarRoot = avatarFolder.toPath().toAbsolutePath().normalize();
        Path avatarPath = avatarRoot.resolve(fileName).normalize();
        if (!avatarPath.startsWith(avatarRoot)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        File avatar = avatarPath.toFile();
        if (!avatar.isFile()) {
            throw new BusinessException(ResponseCodeEnum.CODE_602);
        }
        return avatar;
    }

}
