# Letchat 聊天历史查询性能证据

## 测试目标

验证私聊历史从双向全量查询和 Java 内存合并排序，改为按 `sessionId` 联合索引分页后的端到端收益。该结果用于比较同一环境下的相对变化，不作为生产容量上限。

## 固定环境

- 日期：2026-07-27
- 操作系统：Windows 11 Home China 10.0.26200
- CPU：Intel Core Ultra 9 185H，16 核，22 逻辑处理器
- 内存：31.5GB
- Java：Oracle JDK 21.0.6
- Docker Desktop Engine：29.4.3
- MySQL：8.0.32
- Redis：6.2
- RabbitMQ：3.13-management
- 基线源码：Git 提交 `c3d7f2d4b96c07b470de85855c50867fd751778f`

两个后端进程运行在同一台机器，连接同一组 Docker 中间件。基线进程使用端口 7170，优化后进程使用端口 7070。

## 数据与请求口径

- 单个 Alice 与 Bob 私聊会话写入 20,000 条消息，两个发送方向各 10,000 条。
- 每个版本执行 40 次历史查询请求，并发数为 8，正式计时前预热 3 次。
- 每个版本连续执行 3 轮，简历使用 3 轮结果的中位数。
- 请求路径为 `POST /api/chat/loadChatMessage`，参数为 `contactId=U10000100002&pageNo=1`。
- 压测器使用 JDK 21 `HttpClient`，校验 HTTP 状态和业务响应码，并统计完整响应体读取时间。

## 原始结果

| 版本 | 轮次 | QPS | 平均耗时 ms | p50 ms | p95 ms | 最大耗时 ms | 平均响应体 KB |
|---|---:|---:|---:|---:|---:|---:|---:|
| 旧全量实现 | 1 | 22.83 | 333.97 | 292.91 | 514.14 | 517.00 | 6384.73 |
| 旧全量实现 | 2 | 26.79 | 289.59 | 282.33 | 345.71 | 357.84 | 6384.73 |
| 旧全量实现 | 3 | 24.36 | 314.02 | 322.95 | 370.18 | 371.24 | 6384.73 |
| `sessionId` 分页 | 1 | 244.18 | 30.17 | 28.22 | 56.79 | 57.28 | 6.54 |
| `sessionId` 分页 | 2 | 278.04 | 27.83 | 26.86 | 34.09 | 34.52 | 6.54 |
| `sessionId` 分页 | 3 | 216.90 | 34.67 | 33.45 | 42.96 | 47.63 | 6.54 |

所有 240 次正式请求均成功，失败数为 0。

## 中位数与收益

| 指标 | 旧全量实现 | `sessionId` 分页 | 变化 |
|---|---:|---:|---:|
| QPS | 24.36 | 244.18 | 提升 902.38%，约 10.02 倍 |
| p95 | 370.18ms | 42.96ms | 降低 88.40% |
| 平均响应体 | 6384.73KB | 6.54KB | 降低 99.90% |

## 原因分析

旧私聊接口分别按发送人和接收人查询两个方向的全部记录，将两份列表合并后在 Java 内排序，返回数据没有真正分页，分页总数还重复累计接收方向的数量。在 20,000 条历史下，每次请求返回约 6.23MB JSON。

优化后先通过两个用户编号生成稳定 `sessionId`，数据库使用 `session_id, send_time` 联合索引倒序扫描并只返回当前页 20 条。`EXPLAIN ANALYZE` 显示分页查询实际扫描 20 行，单次索引扫描约 0.08ms；旧两个方向各返回 10,000 行，数据库查询本身分别约 22.09ms 和 17.14ms，之后仍需 Java 合并、排序与序列化。

## 复现入口

```powershell
docker compose -p letchat -f docker-compose-test.yml up -d
.\scripts\prepare_test_data.ps1
.\scripts\prepare_chat_history_perf_data.ps1 -MessageCount 20000
java .\scripts\ChatHistoryHttpBenchmark.java `
  --base-url http://localhost:7070 `
  --token YOUR_TEST_TOKEN `
  --requests 40 `
  --concurrency 8 `
  --warmup 3 `
  --timeout-seconds 180
```

测试令牌通过固定测试账号登录接口获取，不写入仓库。对照基线使用上述 Git 提交构建，并以相同配置启动到 7170 端口。
