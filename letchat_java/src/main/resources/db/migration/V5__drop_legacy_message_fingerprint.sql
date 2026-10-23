-- 老库（docs/mysql/sql/letchat-test-schema.sql 初始化出来的库）存在 uk_message_fingerprint，
-- 而 Flyway 基线 V1 从未创建过这个索引。
-- 直接 DROP 会让「空库按 V1..V5 顺序初始化」在 V5 报 1091 Unknown key，Flyway 失败，应用起不来。
-- 因此这里先查 information_schema，存在才删；空库执行时是 no-op。
--
-- 升级提示：本文件内容改过，已经执行过旧版 V5 的库会因 checksum 不一致而校验失败，
-- 先执行一次 flyway repair（或 DELETE FROM flyway_schema_history WHERE version = '5';）再启动即可。
SET @fingerprint_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'chat_message'
    AND index_name = 'uk_message_fingerprint'
);
SET @drop_fingerprint := IF(@fingerprint_exists > 0,
  'ALTER TABLE chat_message DROP INDEX uk_message_fingerprint',
  'SELECT 1');
PREPARE drop_fingerprint_stmt FROM @drop_fingerprint;
EXECUTE drop_fingerprint_stmt;
DEALLOCATE PREPARE drop_fingerprint_stmt;
