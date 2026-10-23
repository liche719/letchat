# 已知取舍与边界

这份文档记录 LetChat 当前版本**故意不做**或**做不到**的事情。目的不是自曝其短，而是让面试被追问时有一个诚实的答案：哪些是设计选择，哪些是待办。

## 1. 系统消息不进 Outbox，也不分配会话序号

建群、退群、解散、加好友这类系统消息走的是"直接推送"通道：写库后立刻通过 WebSocket 通知，不写 outbox、不分配 `sequence_no`。

- **影响**：离线期间产生的系统消息，用 `syncMessages(afterSequence)` 补拉是拿不到的；推送失败也没有重试当保障。
- **为什么暂时这样做**：系统消息不参与"会话内严格顺序"的语义，强行塞进 outbox 需要先定义它们在会话中的序号地位，属于模型层面的改动。
- **要做的话**：把系统消息当作普通消息写入 `chat_message`（加 `system` 类型），复用序号分配与 outbox 投递，`syncMessages` 天然覆盖。

## 2. 用户消息与系统消息走两套通道

`ChatMessageServiceImpl.saveMessage` 才走"事务写库 + outbox + 确认 + 幂等消费"这条完整链路；系统消息只走实时推送。读代码时容易被 `messageHandler.sendMessage` 的两处调用混淆——一处是消费端在事务内推送（保证推送失败可回滚重投），另一处是系统消息的即时推送。

## 3. 死信重放是手工的

`chat_message_dead_letter` 只负责"落库 + 打指标"，没有提供重放工具。补偿方式是按 `event_id` 查表、重新投递到 `chat.message.exchange`（或直接调 `saveMessage`）。真正要工程化，需要写一个带审计的重放脚本。

## 4. 重试次数与去重窗口的边界

- 消费端重投次数由 `letchat.mq.max-retries`（默认 3）控制，超过即进死信；
- inbox 去重记录保留 `letchat.retention.days`（默认 7 天）。如果一条消息在队列里滞留超过 7 天又被重投，去重记录已被清理，会出现重复推送——理论上可能，实际不会发生（TTL 远小于 7 天）。

## 5. 历史消息分页仍是 offset 分页

`loadChatMessage` 用 `pageNo` + `(session_id, sequence_no)` 联合索引做 `LIMIT offset, size`。深页会退化成扫描 offset 行，不过 `SimplePage` 已经把 `pageNo` 收敛到 `pageTotal`、`pageSize` 收敛到 100，最坏情况仍受会话总消息数约束。要做彻底，应改成以 `sequence_no` 为游标的翻页（`syncMessages` 已经是这种写法）。

## 6. 多实例部署的前提

- 长连接状态在单个 JVM 的 `ConcurrentHashMap` 里，多节点之间靠 Redisson Topic 广播；
- 在线状态、ticket 在 Redis，单点故障会影响新连接建立（已过期连接不受影响）；
- 没有做"节点下线时把连接迁移"——用户会重连到其它节点。
