-- 生产库是「空库 → Flyway 顺序执行」建出来的，与开发时用的老 schema（docs/mysql/sql/letchat-test-schema.sql）
-- 存在两处结构差异，都会在真实部署时暴露：
--   1) V1 的 chat_message.message_id 没有 AUTO_INCREMENT，而 6 处插入语句（注册机器人会话、加好友、
--      建群、退群、解散）不写 message_id，STRICT 模式下报 1364，注册事务整体回滚。
--   2) V1 缺少热路径索引：WS 上线初始化（chat_message 按 contact_id + send_time）、好友申请列表、
--      「我的群」列表在老 schema 里都有索引，V1 里没有。
-- 这里统一按需对齐：老库已满足条件时全部走 no-op，空库则补齐。

-- 1) message_id 自增
SET @needs_auto_increment := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'chat_message'
    AND column_name = 'message_id'
    AND extra NOT LIKE '%auto_increment%'
);
SET @align_auto_increment := IF(@needs_auto_increment > 0,
  'ALTER TABLE chat_message MODIFY COLUMN message_id BIGINT NOT NULL AUTO_INCREMENT',
  'SELECT 1');
PREPARE align_auto_increment_stmt FROM @align_auto_increment;
EXECUTE align_auto_increment_stmt;
DEALLOCATE PREPARE align_auto_increment_stmt;

-- 2) WS 上线初始化与历史文件消息查询
SET @has_contact_send_time := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'chat_message'
    AND index_name = 'idx_contact_send_time'
);
SET @add_contact_send_time := IF(@has_contact_send_time > 0,
  'SELECT 1',
  'ALTER TABLE chat_message ADD INDEX idx_contact_send_time (contact_id, send_time)');
PREPARE add_contact_send_time_stmt FROM @add_contact_send_time;
EXECUTE add_contact_send_time_stmt;
DEALLOCATE PREPARE add_contact_send_time_stmt;

-- 3) 好友申请列表：按接收人 + 状态 + 时间倒序
SET @has_apply_receive := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'user_contact_apply'
    AND index_name = 'idx_apply_receive'
);
SET @add_apply_receive := IF(@has_apply_receive > 0,
  'SELECT 1',
  'ALTER TABLE user_contact_apply ADD INDEX idx_apply_receive (receive_user_id, status, last_apply_time)');
PREPARE add_apply_receive_stmt FROM @add_apply_receive;
EXECUTE add_apply_receive_stmt;
DEALLOCATE PREPARE add_apply_receive_stmt;

-- 4) 「我创建的群」列表
SET @has_group_owner := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'group_info'
    AND index_name = 'idx_group_owner'
);
SET @add_group_owner := IF(@has_group_owner > 0,
  'SELECT 1',
  'ALTER TABLE group_info ADD INDEX idx_group_owner (group_owner_id)');
PREPARE add_group_owner_stmt FROM @add_group_owner;
EXECUTE add_group_owner_stmt;
DEALLOCATE PREPARE add_group_owner_stmt;
