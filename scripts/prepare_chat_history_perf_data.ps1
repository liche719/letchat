param(
    [ValidateRange(100, 100000)]
    [int]$MessageCount = 20000
)

$ErrorActionPreference = 'Stop'

$mysqlContainer = if ($env:MYSQL_CONTAINER) { $env:MYSQL_CONTAINER } else { 'letchat-test-mysql' }
$messageLimit = $MessageCount - 1

$sql = @"
SET NAMES utf8mb4;
SET @alice = 'U10000100001';
SET @bob = 'U10000100002';
SET @session = MD5(CONCAT(LEAST(@alice, @bob), GREATEST(@alice, @bob)));
SET @base_time = 1700000000000;

DELETE FROM chat_message;

INSERT INTO chat_message (
  session_id,
  message_type,
  message_content,
  send_user_id,
  send_user_nick_name,
  send_time,
  contact_id,
  contact_type,
  status
)
SELECT
  @session,
  2,
  CONCAT('performance-message-', numbers.n),
  IF(MOD(numbers.n, 2) = 0, @alice, @bob),
  IF(MOD(numbers.n, 2) = 0, 'Alice', 'Bob'),
  @base_time + numbers.n,
  IF(MOD(numbers.n, 2) = 0, @bob, @alice),
  0,
  1
FROM (
  SELECT ones.n + tens.n * 10 + hundreds.n * 100 + thousands.n * 1000 + ten_thousands.n * 10000 AS n
  FROM
    (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
  CROSS JOIN
    (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
  CROSS JOIN
    (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) hundreds
  CROSS JOIN
    (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) thousands
  CROSS JOIN
    (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ten_thousands
) numbers
WHERE numbers.n <= $messageLimit;

UPDATE chat_session
SET last_message = CONCAT('performance-message-', $messageLimit),
    last_receive_time = @base_time + $messageLimit
WHERE session_id = @session;

SELECT COUNT(*) AS message_count, MIN(send_time) AS first_send_time, MAX(send_time) AS last_send_time
FROM chat_message
WHERE session_id = @session;
"@

$sql | docker exec -i $mysqlContainer mysql -uroot -p123456 --default-character-set=utf8mb4 letchat
