-- 死信落地表。
-- 原来 chat.message.dlq 只有队列、没有消费者：消息进 DLQ 后既无人处理也没有任何记录，
-- 出问题只能翻日志。这里把死信持久化，配合人工重放，避免"消息静默消失"。
CREATE TABLE chat_message_dead_letter (
  id bigint NOT NULL AUTO_INCREMENT,
  event_id varchar(64) NOT NULL,
  payload text NOT NULL,
  failure_reason varchar(512) DEFAULT NULL,
  dead_lettered_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_dead_letter_time (dead_lettered_at),
  KEY idx_dead_letter_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
