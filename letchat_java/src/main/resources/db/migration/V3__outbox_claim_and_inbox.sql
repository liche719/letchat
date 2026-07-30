ALTER TABLE chat_message_outbox
  ADD COLUMN claim_token varchar(64) DEFAULT NULL AFTER status,
  ADD COLUMN claimed_at datetime DEFAULT NULL AFTER claim_token,
  ADD KEY idx_outbox_claim (claim_token),
  ADD KEY idx_outbox_stale (status, claimed_at);

CREATE TABLE chat_message_inbox (
  event_id varchar(64) NOT NULL,
  consumed_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
