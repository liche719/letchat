package com.letchat.mappers;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface ChatMessageInboxMapper {

    @Insert("""
            INSERT IGNORE INTO chat_message_inbox(event_id, consumed_at)
            VALUES(#{eventId}, CURRENT_TIMESTAMP)
            """)
    int insertIfAbsent(@Param("eventId") String eventId);

    /**
     * 分批删除超过保留期的去重记录。
     *
     * <p>只删「早就消费完」的 event_id：去重窗口远小于保留期，
     * 因此不会因为清理而导致重复消费。
     */
    @Delete("""
            DELETE FROM chat_message_inbox
            WHERE consumed_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL #{days} DAY)
            LIMIT #{limit}
            """)
    int deleteConsumedBefore(@Param("days") int days, @Param("limit") int limit);
}
