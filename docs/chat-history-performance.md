# LetChat 聊天性能与可靠性验证

本文记录本地开发机上的可复现验证结果。它用于说明优化前后的实现差异和回归门槛，不代表生产容量承诺。

## 环境

- 验证日期：2026-07-30
- 操作系统：Windows 11 Home China 10.0.26200
- CPU：Intel Core Ultra 9 185H，16 核、22 逻辑处理器
- 内存：31.5 GB
- Java：Oracle JDK 21.0.6
- Docker Desktop Engine：29.4.3
- MySQL：8.0.32
- Redis：6.2
- RabbitMQ：3.13-management
- 应用与 Docker 中间件运行在同一台机器

## 聊天历史读取

### 口径

- 一个 Alice 与 Bob 私聊会话预置 20,000 条文本消息，两个方向各 10,000 条。
- 请求为 `POST /api/chat/loadChatMessage`，参数为 `contactId=U10000100002&pageNo=1`。
- 使用 JDK 21 `HttpClient`，每轮预热 100 次，然后以 16 并发发起 5,000 次正式请求。
- 压测器同时校验 HTTP 200 和业务响应码 200，并计入完整响应体读取时间。
- 共执行 3 轮；中位数用于描述该口径下的稳定表现。

### 当前结果

| 轮次 | 成功 | 失败 | QPS | 平均耗时 ms | p50 ms | p95 ms | p99 ms | 最大耗时 ms | 平均响应体 KB |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 5,000 | 0 | 172.54 | 92.58 | 82.89 | 171.56 | 225.68 | 333.73 | 7.71 |
| 2 | 5,000 | 0 | 190.57 | 83.86 | 73.70 | 157.29 | 250.91 | 427.45 | 7.71 |
| 3 | 5,000 | 0 | 214.98 | 74.31 | 64.30 | 139.46 | 205.47 | 589.56 | 7.71 |
| 中位数 | 5,000 | 0 | 190.57 | 83.86 | 73.70 | 157.29 | 225.68 | 427.45 | 7.71 |

15,000 次正式请求全部成功。测试窗口中 JVM 进程 CPU 时间增加约 114.92 秒，工作集从约 532.75 MiB 到 591.34 MiB；该内存差包含 JVM 预热和缓存影响，不作为峰值内存指标。

`EXPLAIN ANALYZE` 确认查询通过 `uk_session_sequence(session_id, sequence_no)` 反向索引扫描，只读取当前页所需的 20 行：

```text
-> Limit: 20 row(s) (actual time=0.124..0.140 rows=20 loops=1)
    -> Index lookup on chat_message using uk_session_sequence (reverse)
```

### 实现演进

旧实现分别查询两个发送方向的全部消息，再在 Java 内合并、排序并返回，20,000 条历史下响应体约为 6.23 MiB。当前实现使用稳定 `sessionId`、数据库分页和 `sequence_no DESC` 排序，因此不再引入多余的 `send_time` 排序，也不再做内存全量合并。

`sequence_no` 是会话内单调且唯一的顺序字段。Flyway V4 为历史数据补齐序号并回填会话水位；读取、离线同步和已读回执都以该序号为边界。

## 写入与可靠通知

### 口径

- 使用同一 Alice 到 Bob 的会话，16 并发提交 400 条具有唯一 `clientMessageId` 的文本消息。
- 每次 HTTP 成功后，事务内应完成消息、会话摘要和 Outbox 写入。
- RabbitMQ 发布确认成功后，消费者以 `eventId` 写入 Inbox 幂等表并发送 WebSocket 通知。
- 只有 `chat_message`、已发布 Outbox 和 Inbox 三者的增量都为 400 时才视为通过。

### 结果

| 指标 | 结果 |
|---|---:|
| HTTP 成功率 | 400 / 400，100% |
| 写入 QPS | 18.03 |
| p50 | 739.02 ms |
| p95 | 1,675.13 ms |
| p99 | 2,554.41 ms |
| 消息表增量 | 400 |
| 已发布 Outbox 增量 | 400 |
| Inbox 增量 | 400 |

此写入场景刻意让 16 个请求争用同一个会话的严格顺序，因此性能受同会话顺序锁与本机 Docker I/O 约束。不同会话会按 4 个 RabbitMQ 分片队列并行消费；该结果不应用于宣称系统整体写入容量。

### 已验证的保护措施

- 消息 HTTP 事务只落库与写 Outbox，不在提交前直接发送 WebSocket，避免幽灵消息。
- Outbox 使用 Publisher Confirm、有限重试、原子 claim token 和超时回收。
- Inbox 使用 `eventId` 唯一键，重复 RabbitMQ 投递不会重复推送。
- `clientMessageId` 用于客户端重试幂等；消息本体使用纯插入，不再用静默覆盖的 upsert。
- 序号通过 MySQL 连接级 `LAST_INSERT_ID` 原子分配，避免并发请求读到同一个会话序号。
- Flyway V5 移除了旧的消息指纹唯一索引。该索引曾把同一毫秒内的两条合法消息误判为重复。

## 复现步骤

```powershell
docker compose -p letchat -f docker-compose-test.yml up -d
.\scripts\prepare_test_data.ps1
.\scripts\prepare_chat_history_perf_data.ps1 -MessageCount 20000

# 先通过测试账号登录获取 token，再运行读取压测
java .\scripts\ChatHistoryHttpBenchmark.java `
  --base-url http://localhost:7070 `
  --token YOUR_TEST_TOKEN `
  --requests 5000 `
  --concurrency 16 `
  --warmup 100 `
  --timeout-seconds 180

# 写入链路压测
java .\scripts\ChatSendHttpBenchmark.java `
  --base-url http://localhost:7070 `
  --token YOUR_TEST_TOKEN `
  --requests 400 `
  --concurrency 16 `
  --timeout-seconds 180
```

令牌只通过本地测试账号登录获得，不写入仓库。执行写入压测后，使用 MySQL 查询消息、Outbox 与 Inbox 的增量，确认三者一致。
