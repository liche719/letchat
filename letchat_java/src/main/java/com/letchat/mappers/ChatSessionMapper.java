package com.letchat.mappers;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 会话信息 数据库操作接口
 */
public interface ChatSessionMapper<T,P> extends BaseMapper<T,P> {

	/**
	 * 根据SessionId更新
	 */
	 Integer updateBySessionId(@Param("bean") T t,@Param("sessionId") String sessionId);


	/**
	 * 根据SessionId删除
	 */
	 Integer deleteBySessionId(@Param("sessionId") String sessionId);


	/**
	 * 根据SessionId获取对象
	 */
	 T selectBySessionId(@Param("sessionId") String sessionId);

    @Update("""
            UPDATE chat_session
            SET last_message = #{lastMessage},
                last_receive_time = #{lastReceiveTime},
                last_sequence_no = #{sequenceNo}
            WHERE session_id = #{sessionId}
              AND (last_sequence_no IS NULL OR last_sequence_no < #{sequenceNo})
            """)
    int updateSummaryIfNewer(@Param("sessionId") String sessionId,
                             @Param("lastMessage") String lastMessage,
                             @Param("lastReceiveTime") Long lastReceiveTime,
                             @Param("sequenceNo") Long sequenceNo);


}
