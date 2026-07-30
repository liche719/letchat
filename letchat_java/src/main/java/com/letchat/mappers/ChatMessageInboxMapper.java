package com.letchat.mappers;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface ChatMessageInboxMapper {

    @Insert("""
            INSERT IGNORE INTO chat_message_inbox(event_id, consumed_at)
            VALUES(#{eventId}, CURRENT_TIMESTAMP)
            """)
    int insertIfAbsent(@Param("eventId") String eventId);
}
