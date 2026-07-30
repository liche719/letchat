UPDATE chat_message message
JOIN (
  SELECT legacy.message_id,
         COALESCE(existing.max_sequence, 0)
           + ROW_NUMBER() OVER (
               PARTITION BY legacy.session_id
               ORDER BY legacy.send_time, legacy.message_id
             ) AS generated_sequence
  FROM chat_message legacy
  LEFT JOIN (
    SELECT session_id, MAX(sequence_no) AS max_sequence
    FROM chat_message
    WHERE sequence_no IS NOT NULL
    GROUP BY session_id
  ) existing ON existing.session_id = legacy.session_id
  WHERE legacy.sequence_no IS NULL
) ranked ON ranked.message_id = message.message_id
SET message.sequence_no = ranked.generated_sequence;

INSERT IGNORE INTO chat_session_sequence(session_id, last_sequence)
SELECT session_id, MAX(sequence_no)
FROM chat_message
WHERE sequence_no IS NOT NULL
GROUP BY session_id;

UPDATE chat_session_sequence sequence
JOIN (
  SELECT session_id, MAX(sequence_no) AS max_sequence
  FROM chat_message
  WHERE sequence_no IS NOT NULL
  GROUP BY session_id
) latest ON latest.session_id = sequence.session_id
SET sequence.last_sequence = GREATEST(sequence.last_sequence, latest.max_sequence);

UPDATE chat_session session
JOIN chat_session_sequence sequence ON sequence.session_id = session.session_id
SET session.last_sequence_no = GREATEST(COALESCE(session.last_sequence_no, 0), sequence.last_sequence);
