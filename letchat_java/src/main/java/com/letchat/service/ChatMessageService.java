package com.letchat.service;

import com.letchat.entity.dto.MessageSendDto;
import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.entity.po.ChatMessage;
import com.letchat.entity.query.ChatMessageQuery;
import com.letchat.entity.vo.PaginationResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import com.letchat.storage.StoredObject;


/**
 * 聊天消息表 业务接口
 */
public interface ChatMessageService {

    /**
     * 根据条件查询列表
     */
    List<ChatMessage> findListByParam(ChatMessageQuery param);

    /**
     * 根据条件查询列表
     */
    Integer findCountByParam(ChatMessageQuery param);

    /**
     * 分页查询
     */
    PaginationResultVO<ChatMessage> findListByPage(ChatMessageQuery param);

    PaginationResultVO<ChatMessage> loadChatMessages(String userId, String contactId, Integer pageNo);

    List<ChatMessage> syncMessages(String userId, String contactId, Long afterSequence, Integer limit);

    void acknowledgeDelivered(String userId, String contactId, Long sequenceNo);

    void acknowledgeRead(String userId, String contactId, Long sequenceNo);

    MessageSendDto saveMessage(ChatMessage chatMessage);

    void saveMessageFile(String userId, Long messageId, MultipartFile file, MultipartFile coverFile);

    StoredObject downloadFile(TokenUserInfoDto tokenUserInfoDto, Long fileId, Boolean showcover);
}
