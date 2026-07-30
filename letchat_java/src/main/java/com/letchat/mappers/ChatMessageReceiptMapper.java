package com.letchat.mappers;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface ChatMessageReceiptMapper {

    @Insert("""
            INSERT INTO chat_delivery_cursor(user_id, session_id, delivered_sequence, read_sequence, updated_at)
            VALUES(#{userId}, #{sessionId}, #{sequenceNo}, 0, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
              delivered_sequence = GREATEST(delivered_sequence, #{sequenceNo}),
              updated_at = CURRENT_TIMESTAMP
            """)
    int acknowledgeDelivered(@Param("userId") String userId,
                             @Param("sessionId") String sessionId,
                             @Param("sequenceNo") long sequenceNo);

    @Insert("""
            INSERT INTO chat_delivery_cursor(user_id, session_id, delivered_sequence, read_sequence, updated_at)
            VALUES(#{userId}, #{sessionId}, #{sequenceNo}, #{sequenceNo}, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
              delivered_sequence = GREATEST(delivered_sequence, #{sequenceNo}),
              read_sequence = GREATEST(read_sequence, #{sequenceNo}),
              updated_at = CURRENT_TIMESTAMP
            """)
    int acknowledgeRead(@Param("userId") String userId,
                        @Param("sessionId") String sessionId,
                        @Param("sequenceNo") long sequenceNo);
}
