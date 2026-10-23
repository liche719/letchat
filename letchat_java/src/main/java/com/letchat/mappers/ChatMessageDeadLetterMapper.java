package com.letchat.mappers;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface ChatMessageDeadLetterMapper {

    @Insert("""
            INSERT INTO chat_message_dead_letter(event_id, payload, failure_reason, dead_lettered_at)
            VALUES(#{eventId}, #{payload}, #{failureReason}, CURRENT_TIMESTAMP)
            """)
    int insert(@Param("eventId") String eventId,
               @Param("payload") String payload,
               @Param("failureReason") String failureReason);
}
