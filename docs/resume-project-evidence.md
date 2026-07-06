# Letchat 简历证据链与面试讲法

这份文档用于把 Letchat 从“即时通信练手项目”整理成可投递、可追问、可复现的 Java 后端项目。重点不是夸大用户量，而是讲清楚长连接、消息可靠性、会话一致性、异步落库和运行验证。

## 一句话定位

Letchat 是一个即时通信系统，覆盖账号登录、联系人、私聊、群聊、Netty WebSocket 实时推送、离线初始化、RabbitMQ 异步落库和多节点消息广播。后端重点解决用户长连接状态维护、消息投递与落库解耦、群组会话一致性，以及旧项目在新 JDK 下的运行兼容问题。

## 项目背景

聊天系统看起来是“发消息”，但后端真正要处理的是：

1. 用户上线后需要一次性拿到会话列表、离线消息和好友申请数量，否则首屏数据不完整。
2. 实时推送不能被数据库写入阻塞，否则慢数据库会直接影响消息到达体验。
3. 私聊、群聊、多节点部署时，消息需要找到在线连接并投递到正确节点。
4. 老项目依赖在新 JDK 下可能启动失败，必须通过真实运行验证发现问题。

## 我的职责

- 负责即时通信后端核心链路：WebSocket 接入、用户连接绑定、心跳维护、在线状态清理和会话初始化。
- 负责消息发送链路：好友/群成员校验、sessionId 生成、消息清洗、实时推送、RabbitMQ 异步落库。
- 负责多节点推送：通过 Redisson Pub/Sub 广播消息，各节点再按本地 Channel 完成个人/群组投递。
- 负责运行修复和验证：修复 Java 21 下 OkHttp、Redisson FST、验证码、注册密码哈希等问题，并补充 Docker 化 smoke/并发脚本。
- 负责前端补全：基于后端接口重做 Vue 3 登录、注册、会话、联系人、好友申请、个人资料和消息发送界面。

## 核心难点与方案

### 1. WebSocket 长连接与首屏初始化

问题：用户上线后只建立连接还不够，前端还需要会话列表、离线消息、好友申请数量。串行调多个接口会造成首屏慢和数据缺口。

方案：

- 握手成功后将 `userId -> Channel` 写入本地并发 Map，并把用户加入对应群组 ChannelGroup。
- Redis 记录心跳和联系人缓存，连接断开时清理在线状态并更新最后离线时间。
- 上线时聚合会话列表、最近离线消息和好友申请数量，封装为 `WsInitData` 主动推送给客户端。

代码证据：

- `letchat_java/src/main/java/com/letchat/websocket/netty/HandlerWebSocket.java`
- `letchat_java/src/main/java/com/letchat/websocket/netty/HandlerHeartBeat.java`
- `letchat_java/src/main/java/com/letchat/websocket/ChannelContextUtils.java`
- `letchat_java/src/main/java/com/letchat/redis/RedisComponent.java`

### 2. 实时推送与异步落库解耦

问题：如果发消息时同步写库，数据库慢或失败会影响实时到达；如果只推送不落库，聊天记录和会话列表又不可靠。

方案：

- 发送前校验好友或群成员关系，按私聊/群聊生成稳定 sessionId。
- 清洗消息内容后先通过 WebSocket 实时推送，同时投递 RabbitMQ 做异步落库。
- 消费者解析消息后在事务内更新会话表和消息表，成功后手动 ack，失败时按策略 nack/retry。

代码证据：

- `letchat_java/src/main/java/com/letchat/service/impl/ChatMessageServiceImpl.java`
- `letchat_java/src/main/java/com/letchat/mq/ChatMessageProducer.java`
- `letchat_java/src/main/java/com/letchat/mq/ChatMessageConsumer.java`
- `letchat_java/src/main/java/com/letchat/config/RabbitMQConfig.java`

### 3. 多节点推送与群组一致性

问题：多节点部署时，目标用户可能连接在另一台机器；群聊还要处理成员变更后的误推送问题。

方案：

- 通过 Redisson Topic 发布消息，所有节点订阅同一个 topic。
- 每个节点收到广播后只向本地在线 Channel 投递，避免跨节点直接操作连接。
- 用户加入、退出、解散群时同步维护 ChannelGroup 和 Redis 联系人缓存，降低退群后仍收到消息的风险。

代码证据：

- `letchat_java/src/main/java/com/letchat/websocket/MessageHandler.java`
- `letchat_java/src/main/java/com/letchat/websocket/ChannelContextUtils.java`
- `letchat_java/src/main/java/com/letchat/service/impl/GroupInfoServiceImpl.java`
- `letchat_java/src/main/java/com/letchat/redis/RedisConfig.java`

### 4. 前端接口适配与状态体验

问题：原前端页面不完整，接口返回格式、登录态、WebSocket 消息、发送失败重试和联系人状态没有形成统一体验。

方案：

- 统一 `ApiResponse`、用户、联系人、消息、WebSocket 初始化数据类型。
- Pinia 管理用户 token、用户信息、WebSocket 连接状态、重连和心跳。
- 聊天 Store 统一联系人过滤、未读数、乐观发送、失败重试、文件消息和 WebSocket init/message 合并。
- 重做登录、注册、聊天主界面、联系人详情、好友申请、用户搜索和个人资料弹窗。

代码证据：

- `letchat_desktop/letchat-vue3/src/stores/user.ts`
- `letchat_desktop/letchat-vue3/src/stores/chat.ts`
- `letchat_desktop/letchat-vue3/src/views/Chat.vue`
- `letchat_desktop/letchat-vue3/src/components/MessageBubble.vue`
- `letchat_desktop/letchat-vue3/src/utils/request.ts`

## 已验证结果

- 前端构建：`npm run build` 通过。
- 后端测试：`mvn test` 通过，当前为 `7 tests, 0 failures, 0 errors`。
- 运行 smoke：本地 Docker 测试环境覆盖 OpenAPI、鉴权、注册后登录、Alice/Bob WebSocket 初始化、Alice -> Bob 实时私聊、MQ 消费落库、聊天记录拉取和队列排空。
- 并发验证：联系人列表和聊天记录读取接口共 100 次请求、20 并发，成功率 100%，P95 约 106ms。

说明：这些结果来自本地 Docker 测试环境和小规模样本，适合证明核心链路可运行，不代表生产容量上限。

## 简历可用表达

### 精简版

Letchat 即时通信系统：负责基于 Netty WebSocket 的长连接接入、用户连接绑定、心跳检测、在线状态维护和首屏会话初始化；通过 Redis 缓存联系人与在线状态，通过 RabbitMQ 将实时推送与消息落库解耦，消费者手动 ack 并在事务内更新会话和消息表；补充 Docker 化 smoke/并发验证脚本，验证核心聊天链路可复现运行。

### 项目条目版

- 基于 Netty WebSocket 搭建长连接接入层，完成登录凭证校验、用户连接绑定、心跳检测和断开清理，并通过 Redis 维护登录态、联系人缓存和在线状态。
- 设计上线初始化流程，聚合会话列表、离线消息和好友申请数量后主动推送，减少聊天页多接口串行加载导致的数据缺口。
- 设计私聊/群聊消息链路，发送前校验关系并生成 sessionId，消息清洗后通过 WebSocket 实时推送，RabbitMQ 异步落库，消费者事务内更新会话与消息表。
- 通过 Redisson Pub/Sub 支持多节点消息广播，各节点按本地 Channel 完成个人/群组投递；群成员变更时同步维护 ChannelGroup 和联系人缓存。
- 修复 Java 21 下 OkHttp、Redisson FST、验证码脚本引擎和注册密码双重 MD5 等运行问题；补充 smoke/并发脚本验证核心链路。
- 基于 Vue 3 + Pinia + Element Plus 补全聊天前端，实现登录注册、联系人、好友申请、消息列表、乐观发送、失败重试和文件消息交互。

## 面试讲法

### 项目整体怎么讲

这个项目我会重点讲即时通信后端，而不是只说“做了聊天功能”。核心链路是：HTTP 登录后拿 token，WebSocket 握手时校验登录态并绑定 Channel；用户上线时主动推送初始化数据；发送消息时先校验关系和生成 sessionId，再 WebSocket 实时推送，RabbitMQ 异步落库；如果是多节点部署，就通过 Redisson Pub/Sub 广播到各节点，每个节点再投递给本地在线连接。

### 为什么要用 MQ

聊天消息既要实时到达，也要可靠落库。同步落库会让数据库延迟影响消息体验，所以我把实时推送和落库拆开：发送链路先校验和推送，同时把消息投到 MQ；消费者再在事务内更新会话和消息。这样用户侧能尽快收到消息，数据库失败也可以通过 MQ 消费日志、ack/nack 和重试策略排查。

### 怎么证明不是只写功能

我补了运行验证脚本和结果：本地 Docker 启动 MySQL、Redis、RabbitMQ，smoke 覆盖注册登录、双用户 WebSocket、实时消息、MQ 落库、聊天记录拉取和队列排空；并发脚本对联系人和聊天记录读取接口做 100 次请求、20 并发，成功率 100%。这能证明核心链路不是只能在代码里看，而是能跑、能复现、能解释。

## 下一轮补强建议

1. 给消息消费增加死信队列和失败原因持久化，让可靠性链路更完整。
2. 给 WebSocket 连接数、消息发送成功率、MQ 消费耗时补基础监控日志或指标。
3. 把群组成员变更和消息误推送补成自动化测试，提升群聊一致性可信度。
4. 整理本地演示文档，包含启动、注册双用户、建立 WebSocket、发送消息、查看 DB/MQ 的步骤。
