package com.letchat.mappers;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ChatMessageSequenceMapper {

    @Insert("""
            INSERT INTO chat_session_sequence(session_id, last_sequence)
            VALUES(#{sessionId}, LAST_INSERT_ID(1))
            ON DUPLICATE KEY UPDATE last_sequence = LAST_INSERT_ID(last_sequence + 1)
            """)
    int allocateNext(@Param("sessionId") String sessionId);

    @Select("SELECT LAST_INSERT_ID()")
    long lastAllocated();
}
