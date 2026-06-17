$ErrorActionPreference = 'Stop'

$mysqlContainer = $env:MYSQL_CONTAINER
if ([string]::IsNullOrWhiteSpace($mysqlContainer)) {
    $mysqlContainer = 'letchat-test-mysql'
}

$sql = @'
SET NAMES utf8mb4;

SET @alice = 'U10000100001';
SET @bob = 'U10000100002';
SET @password = MD5('Test@123456');
SET @session = MD5(CONCAT(LEAST(@alice, @bob), GREATEST(@alice, @bob)));

DELETE FROM chat_message;
DELETE FROM chat_session_user;
DELETE FROM chat_session;
DELETE FROM user_contact_apply;
DELETE FROM user_contact;
DELETE FROM group_info;
DELETE FROM user_info_beauty;
DELETE FROM app_update;
DELETE FROM user_info;

INSERT INTO user_info
  (user_id, email, nick_name, join_type, sex, password, personal_signature, status, create_time, last_login_time, area_name, area_code, last_off_time)
VALUES
  (@alice, 'alice@example.com', 'Alice', 1, 1, @password, 'runtime test user alice', 1, NOW(), NULL, 'Guangdong', '440000', 0),
  (@bob, 'bob@example.com', 'Bob', 1, 1, @password, 'runtime test user bob', 1, NOW(), NULL, 'Guangdong', '440000', 0);

INSERT INTO user_contact
  (user_id, contact_id, contact_type, create_time, status, last_update_time)
VALUES
  (@alice, @bob, 0, NOW(), 1, NOW()),
  (@bob, @alice, 0, NOW(), 1, NOW());

INSERT INTO chat_session
  (session_id, last_message, last_receive_time)
VALUES
  (@session, 'runtime seeded session', UNIX_TIMESTAMP(NOW(3)) * 1000);

INSERT INTO chat_session_user
  (user_id, contact_id, session_id, contact_name)
VALUES
  (@alice, @bob, @session, 'Bob'),
  (@bob, @alice, @session, 'Alice');

SELECT 'user_info' AS table_name, COUNT(*) AS count_value FROM user_info
UNION ALL SELECT 'user_contact', COUNT(*) FROM user_contact
UNION ALL SELECT 'chat_session', COUNT(*) FROM chat_session
UNION ALL SELECT 'chat_session_user', COUNT(*) FROM chat_session_user
UNION ALL SELECT 'chat_message', COUNT(*) FROM chat_message;
'@

$sql | docker exec -i $mysqlContainer mysql -uroot -p123456 --default-character-set=utf8mb4 letchat

docker exec letchat-test-redis redis-cli -n 1 FLUSHDB | Out-Null
