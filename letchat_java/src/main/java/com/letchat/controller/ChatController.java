package com.letchat.controller;

import com.letchat.config.AppConfig;
import com.letchat.entity.Constants;
import com.letchat.entity.dto.MessageSendDto;
import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.entity.enums.MessageTypeEnum;
import com.letchat.entity.enums.PageSize;
import com.letchat.entity.enums.ResponseCodeEnum;
import com.letchat.entity.enums.UserContactTypeEnum;
import com.letchat.entity.po.ChatMessage;
import com.letchat.entity.query.ChatMessageQuery;
import com.letchat.entity.vo.PaginationResultVO;
import com.letchat.entity.vo.ResponseVO;
import com.letchat.exception.BusinessException;
import com.letchat.redis.RedisComponent;
import com.letchat.service.impl.ChatMessageServiceImpl;
import com.letchat.utils.StringTools;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.List;

@RestController("chatController")
@RequestMapping("/chat")
@Slf4j
public class ChatController extends ABaseController {

    @Resource
    private AppConfig appConfig;

    @Resource
    private ChatMessageServiceImpl chatMessageService;

    @Resource
    private RedisComponent redisComponent;

    /**
     * 发送消息 TODO 使用RabbitMQ
     */
    @RequestMapping("/sendMessage")
    public ResponseVO sendMessage(HttpServletRequest request,
                                  @NotEmpty String contactId,
                                  @NotEmpty @Max(500) String messageContent,
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
    public ResponseVO loadChatMessage(HttpServletRequest request,
                                      @NotEmpty String contactId,
                                      Integer pageNo) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);

        // 验证联系人权限
        List<String> contactIdList = redisComponent.getUserContactList(tokenUserInfoDto.getUserId());
        if (!contactIdList.contains(contactId)) {
            UserContactTypeEnum userContactStatusEnum = UserContactTypeEnum.getByPrefix(contactId);
            if (UserContactTypeEnum.USER == userContactStatusEnum) {
                throw new BusinessException(ResponseCodeEnum.CODE_902);
            } else {
                throw new BusinessException(ResponseCodeEnum.CODE_903);
            }
        }

        // 查询聊天记录（前端有数据库存之前的聊天记录）
//        ChatMessageQuery query = new ChatMessageQuery();
//        query.setContactId(contactId);
//        query.setPageNo(pageNo != null ? pageNo : 1);
//        query.setPageSize(PageSize.SIZE20.getSize());
//        query.setOrderBy("send_time desc");
//
//        PaginationResultVO<ChatMessage> result = chatMessageService.findListByPage(query);


        // 查询聊天记录
        PaginationResultVO<ChatMessage> result = null;
        if (UserContactTypeEnum.USER == UserContactTypeEnum.getByPrefix(contactId)) {
            // 私聊
            ChatMessageQuery query1 = new ChatMessageQuery();
            query1.setContactId(contactId);
            query1.setSendUserId(tokenUserInfoDto.getUserId());
            List<ChatMessage> list1 = chatMessageService.findListByParam(query1);

            ChatMessageQuery query2 = new ChatMessageQuery();
            query2.setContactId(tokenUserInfoDto.getUserId());
            query2.setSendUserId(contactId);
            List<ChatMessage> list2 = chatMessageService.findListByParam(query2);
            list1.addAll(list2);
            // 按sendTime时间戳排序
            list1.sort((o1, o2) -> o2.getSendTime().compareTo(o1.getSendTime()));
            result = new PaginationResultVO<>(list1.size() + list2.size(), PageSize.SIZE20.getSize(), pageNo, 20, list1);
        } else {
            // 群聊
            ChatMessageQuery query = new ChatMessageQuery();
            query.setContactId(contactId);
            query.setPageNo(pageNo != null ? pageNo : 1);
            query.setPageSize(PageSize.SIZE20.getSize());
            query.setOrderBy("send_time desc");
            result = chatMessageService.findListByPage(query);
        }

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
                             @NotEmpty Boolean showCover) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfo(request);
        OutputStream out = null;
        FileInputStream in = null;
        try {
            File file = null;
            if (!StringTools.isNumber(fileId)) {
                String avatarFolderName = Constants.FILE_FOLDER_FILE + Constants.FILE_FOLDER_AVATAR_NAME;
                String avatarPath = appConfig.getProjectFolder() + avatarFolderName + fileId + Constants.IMAGE_SUFFIX;
                if (showCover) {
                    avatarPath = avatarPath + Constants.COVER_IMAGES_SUFFIX;
                }
                file = new File(avatarPath);
                if (!file.exists()) {
                    throw new BusinessException(ResponseCodeEnum.CODE_602);
                }
            } else {
                file = chatMessageService.downloadFile(tokenUserInfoDto, Long.parseLong(fileId), showCover);
            }

            response.setContentType("application/x-msdownload;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;");
            response.setContentLengthLong(file.length());
            in = new FileInputStream(file);
            byte[] byteData = new byte[1024];
            out = response.getOutputStream();
            int len;
            while ((len = in.read(byteData)) != -1) {
                out.write(byteData, 0, len);
            }
            out.flush();
        } catch (Exception e) {
            log.error("下载文件异常", e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception e) {
                    log.error("IO异常", e);
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                    log.error("IO异常", e);
                }
            }
        }
    }


}
