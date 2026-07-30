package com.letchat.mappers;

import com.letchat.entity.po.ChatMessageOutbox;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChatMessageOutboxMapper {

    @Insert("""
            INSERT INTO chat_message_outbox(
                event_id, session_id, routing_key, payload, status, retry_count, next_retry_time, created_at
            ) VALUES(
                #{eventId}, #{sessionId}, #{routingKey}, #{payload}, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            """)
    int insert(ChatMessageOutbox outbox);

    @Update("""
            UPDATE chat_message_outbox
            SET status = 3, claim_token = #{claimToken}, claimed_at = CURRENT_TIMESTAMP
            WHERE status = 0 AND next_retry_time <= CURRENT_TIMESTAMP
            ORDER BY created_at
            LIMIT #{limit}
            """)
    int claimPending(@Param("claimToken") String claimToken, @Param("limit") int limit);

    @Select("""
            SELECT event_id AS eventId,
                   session_id AS sessionId,
                   routing_key AS routingKey,
                   payload,
                   status,
                   retry_count AS retryCount,
                   claim_token AS claimToken,
                   claimed_at AS claimedAt,
                   next_retry_time AS nextRetryTime,
                   created_at AS createdAt,
                   published_at AS publishedAt,
                   last_error AS lastError
            FROM chat_message_outbox
            WHERE status = 3 AND claim_token = #{claimToken}
            ORDER BY created_at
            """)
    List<ChatMessageOutbox> findClaimed(@Param("claimToken") String claimToken);

    @Update("""
            UPDATE chat_message_outbox
            SET status = 0, claim_token = NULL, claimed_at = NULL
            WHERE status = 3 AND claimed_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL #{timeoutSeconds} SECOND)
            """)
    int releaseStaleClaims(@Param("timeoutSeconds") long timeoutSeconds);

    @Update("""
            UPDATE chat_message_outbox
            SET status = 1, claim_token = NULL, claimed_at = NULL,
                published_at = CURRENT_TIMESTAMP, last_error = NULL
            WHERE event_id = #{eventId} AND status = 3 AND claim_token = #{claimToken}
            """)
    int markPublished(@Param("eventId") String eventId, @Param("claimToken") String claimToken);

    @Update("""
            UPDATE chat_message_outbox
            SET retry_count = retry_count + 1,
                status = CASE WHEN retry_count + 1 >= #{maxRetries} THEN 2 ELSE 0 END,
                claim_token = NULL,
                claimed_at = NULL,
                next_retry_time = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL #{delaySeconds} SECOND),
                last_error = LEFT(#{error}, 1000)
            WHERE event_id = #{eventId} AND status = 3 AND claim_token = #{claimToken}
            """)
    int markFailed(@Param("eventId") String eventId,
                   @Param("claimToken") String claimToken,
                   @Param("maxRetries") int maxRetries,
                   @Param("delaySeconds") long delaySeconds,
                   @Param("error") String error);
}
