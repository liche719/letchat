package com.letchat.mappers;

import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 聊天消息表 数据库操作接口
 */
public interface ChatMessageMapper<T,P> extends BaseMapper<T,P> {

    Integer insertMessage(@Param("bean") T t);

	/**
	 * 根据MessageId更新
	 */
	 Integer updateByMessageId(@Param("bean") T t,@Param("messageId") Long messageId);


	/**
	 * 根据MessageId删除
	 */
	 Integer deleteByMessageId(@Param("messageId") Long messageId);


	/**
	 * 根据MessageId获取对象
	 */
	 T selectByMessageId(@Param("messageId") Long messageId);

    T selectByClientMessageId(@Param("clientMessageId") String clientMessageId);


	/**
	 * 根据消息指纹获取对象，用于MQ重投幂等判断
	 */
	 T selectByMessageFingerprint(@Param("bean") T t);

    List<T> selectAfterSequence(@Param("sessionId") String sessionId,
                                @Param("afterSequence") long afterSequence,
                                @Param("limit") int limit);


}
