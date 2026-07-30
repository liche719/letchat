ALTER TABLE chat_message
  ADD COLUMN client_message_id varchar(64) DEFAULT NULL AFTER message_id,
  ADD COLUMN sequence_no bigint DEFAULT NULL AFTER client_message_id,
  ADD UNIQUE KEY uk_client_message_id (client_message_id),
  ADD UNIQUE KEY uk_session_sequence (session_id, sequence_no);

ALTER TABLE chat_session
  ADD COLUMN last_sequence_no bigint DEFAULT NULL AFTER last_receive_time;

CREATE TABLE chat_session_sequence (
  session_id varchar(64) NOT NULL,
  last_sequence bigint NOT NULL,
  PRIMARY KEY (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE chat_message_outbox (
  event_id varchar(64) NOT NULL,
  session_id varchar(64) NOT NULL,
  routing_key varchar(64) NOT NULL,
  payload json NOT NULL,
  status tinyint NOT NULL DEFAULT 0,
  retry_count int NOT NULL DEFAULT 0,
  next_retry_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at datetime DEFAULT NULL,
  last_error varchar(1000) DEFAULT NULL,
  PRIMARY KEY (event_id),
  KEY idx_outbox_pending (status, next_retry_time, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE chat_delivery_cursor (
  user_id varchar(20) NOT NULL,
  session_id varchar(64) NOT NULL,
  delivered_sequence bigint NOT NULL DEFAULT 0,
  read_sequence bigint NOT NULL DEFAULT 0,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
