# Letchat 简历证据链与面试讲法

这份文档用于把 Letchat 从“即时通信练手项目”整理成可投递、可追问、可复现的 Java 后端项目。重点不是夸大用户量，而是讲清楚长连接、消息可靠性、会话顺序、离线同步和运行验证。

## 一句话定位

Letchat 是一个模块化单体即时通信系统，覆盖账号登录、联系人、私聊、群聊、Netty WebSocket 通知、离线同步、RabbitMQ 可靠投递和 MinIO 文件消息。后端重点解决用户长连接状态维护、消息持久化与通知的一致性、会话内顺序和跨节点通知。

## 项目背景

聊天系统看起来是“发消息”，但后端真正要处理的是：

1. 用户上线后需要一次性拿到会话列表、离线消息和好友申请数量，否则首屏数据不完整。
2. 消息不能在数据库提交前通知对方，否则事务回滚会产生“看得到却查不到”的幽灵消息。
3. 私聊、群聊、多节点部署时，消息需要找到在线连接并投递到正确节点。
4. 老项目依赖在新 JDK 下可能启动失败，必须通过真实运行验证发现问题。

## 我的职责

- 负责即时通信后端核心链路：WebSocket 接入、用户连接绑定、心跳维护、在线状态清理和会话初始化。
- 负责消息发送链路：好友/群成员校验、sessionId 生成、会话序号原子分配、消息与 Outbox 同事务写入、RabbitMQ 可靠发布和 WebSocket 通知。
- 负责多节点推送：通过 Redisson Pub/Sub 广播消息，各节点再按本地 Channel 完成个人/群组投递。
- 负责安全和可靠性治理：使用 BCrypt 与安全随机令牌改造账号链路，补齐接口鉴权和文件访问校验，并为 RabbitMQ 发布与消费增加 Confirm、幂等、有限重试和 DLQ。
- 负责前端补全：基于后端接口重做 Vue 3 登录、注册、会话、联系人、好友申请、个人资料、群创建与成员管理、消息历史分页和媒体文件下载界面。

## 核心难点与方案

### 1. WebSocket 长连接与首屏初始化

问题：用户上线后只建立连接还不够，前端还需要会话列表、离线消息、好友申请数量。串行调多个接口会造成首屏慢和数据缺口。

方案：

- 握手成功后将 `userId -> Channel` 写入本地并发 Map，并把用户加入对应群组 ChannelGroup。
- 客户端每 25 秒发送心跳，服务端在 75 秒读空闲后关闭连接，Redis 在线状态 TTL 为 90 秒，连接断开时清理状态并更新最后离线时间。
- Netty 服务通过 `SmartLifecycle` 管理端口绑定和关闭，绑定失败会阻止应用以错误状态继续启动。
- 上线时聚合会话列表、最近离线消息和好友申请数量，封装为 `WsInitData` 主动推送给客户端。

代码证据：

- `letchat_java/src/main/java/com/letchat/websocket/netty/HandlerWebSocket.java`
- `letchat_java/src/main/java/com/letchat/websocket/netty/HandlerHeartBeat.java`
- `letchat_java/src/main/java/com/letchat/websocket/ChannelContextUtils.java`
- `letchat_java/src/main/java/com/letchat/redis/RedisComponent.java`

### 2. 可靠消息落库与异步通知

问题：只要数据库提交和消息发布分属两个独立系统，就存在“消息已提交但通知丢失”或“通知已到达但事务回滚”的窗口；简单的先推送再异步落库还会产生幽灵消息。

方案：

- 发送前校验好友或群成员关系，按私聊/群聊生成稳定 sessionId。
- 发送前校验关系并生成稳定 `sessionId`，以 MySQL 连接级 `LAST_INSERT_ID` 原子分配会话内 `sequenceNo`。
- 在一个 HTTP 事务内写入消息、会话摘要和 Outbox；事务提交后由调度器 claim 待发布事件，等待 Publisher Confirm，失败后有限重试并支持超时回收。
- 消费者以 `eventId` 写入 Inbox 幂等表，成功后才向 WebSocket 通知并手动 ack；重复投递不会重复通知，最终失败进入 DLQ。
- `clientMessageId` 用于客户端重试幂等，消息表采用纯插入，Flyway V5 移除了会把同毫秒合法消息误判重复的旧消息指纹唯一索引。

代码证据：

- `letchat_java/src/main/java/com/letchat/service/impl/ChatMessageServiceImpl.java`
- `letchat_java/src/main/java/com/letchat/mq/ChatMessageProducer.java`
- `letchat_java/src/main/java/com/letchat/mq/ChatMessageOutboxDispatcher.java`
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

### 4. 账号与文件安全治理

问题：旧账号只使用 MD5，登录令牌可预测，聊天、上传和历史接口鉴权不完整，文件读取还存在越权与目录穿越风险。

方案：

- 新密码使用 BCrypt；旧 MD5 账号仍可登录，并在校验成功后自动升级为 BCrypt，避免一次性迁移中断使用。
- 登录令牌改为 256 位安全随机值，新登录会清理旧令牌，缺失或失效令牌统一返回登录超时状态。
- 补齐聊天发送、上传和历史接口鉴权；私聊文件允许发送者与接收者访问，头像路径规范化后再校验根目录。

代码证据：

- `letchat_java/src/main/java/com/letchat/security/PasswordManager.java`
- `letchat_java/src/main/java/com/letchat/security/SecureTokenGenerator.java`
- `letchat_java/src/main/java/com/letchat/controller/ABaseController.java`
- `letchat_java/src/main/java/com/letchat/controller/ChatController.java`

### 5. 聊天历史分页与索引优化

问题：旧私聊历史接口分别查询两个方向的全部消息，在 Java 内合并和排序，不仅没有真正分页，还重复计算总数；会话历史增长后响应体和内存开销线性增加。

方案：

- 私聊和群聊统一生成稳定 `sessionId`，查询层按 `sessionId` 过滤，并使用 `uk_session_sequence(session_id, sequence_no)` 联合索引完成倒序分页。
- 固定 2 万条单会话消息，以 16 并发、每轮 5,000 次正式请求和 100 次预热运行三轮，取中位数。
- 三轮共 15,000 次正式请求全部成功；中位数为 190.57 QPS，p95 为 157.29ms，单页平均响应体为 7.71KB，索引扫描仅读取所需的 20 行。

证据：

- `letchat_java/src/main/java/com/letchat/service/impl/ChatMessageServiceImpl.java`
- `letchat_java/src/main/resources/com/letchat/mappers/ChatMessageMapper.xml`
- `scripts/prepare_chat_history_perf_data.ps1`
- `scripts/ChatHistoryHttpBenchmark.java`
- `docs/chat-history-performance.md`

### 6. 前端接口适配与状态体验

问题：原前端页面不完整，接口返回格式、登录态、WebSocket 消息、发送失败重试和联系人状态没有形成统一体验。

方案：

- 统一 `ApiResponse`、用户、联系人、消息、WebSocket 初始化数据类型。
- Pinia 管理用户 token、用户信息、WebSocket 连接状态、重连和心跳。
- 聊天 Store 统一联系人过滤、未读数、乐观发送、失败重试、文件消息和 WebSocket init/message 合并。
- 重做登录、注册、聊天主界面、联系人详情、好友申请、用户搜索和个人资料弹窗。
- 将已有群组后端能力补入前端：创建/编辑群、成员添加与移除、退出与解散均按群主权限展示；历史消息支持向前分页且保持滚动位置，文件消息通过携带登录令牌的 Blob 请求下载。

代码证据：

- `letchat_desktop/letchat-vue3/src/stores/user.ts`
- `letchat_desktop/letchat-vue3/src/stores/chat.ts`
- `letchat_desktop/letchat-vue3/src/views/Chat.vue`
- `letchat_desktop/letchat-vue3/src/components/MessageBubble.vue`
- `letchat_desktop/letchat-vue3/src/utils/request.ts`
- `letchat_desktop/letchat-vue3/src/components/GroupEditorDialog.vue`
- `letchat_desktop/letchat-vue3/src/components/ContactInfoDialog.vue`

## 已验证结果

- 前端构建：`npm run build` 通过。
- 后端测试：`mvn clean verify` 通过，39 项单元测试覆盖账号、群组授权、序号分配、Outbox 发布、Inbox 幂等和可靠性边界；3 项依赖 Docker named-pipe 的 Testcontainers 集成测试在当前环境跳过。
- 运行 smoke：本地 Docker 测试环境覆盖 OpenAPI、鉴权、注册后登录、Alice/Bob WebSocket 初始化、Alice -> Bob 消息写入、通知到达、聊天记录拉取和队列排空。
- 写入可靠性验证：16 并发发送 400 条同会话消息，HTTP 全部成功；消息、已发布 Outbox 和 Inbox 均新增 400 条，消费者未出现重复通知。
- 群聊实际验证：使用一次性测试群完成创建、群成员添加、群消息持久化、移除成员后的详情拒绝和群解散；脚本在结束时自动解散测试群。
- 心跳验证：WebSocket 握手与发送心跳后的 Redis TTL 均为 89 秒，符合 25 秒客户端心跳、75 秒服务端读空闲和 90 秒在线状态 TTL 的配置。
- 历史查询验证：2 万条单会话消息下，16 并发三轮共 15,000 次正式请求全部成功；中位数 190.57 QPS，p95 157.29ms。

说明：这些结果来自固定的本地 Docker 环境，用于验证改造前后的相对收益和核心链路可运行性，不代表生产容量上限。

## 简历可用表达

### 精简版

Letchat 即时通信系统：负责基于 Netty WebSocket 的长连接接入、用户连接绑定、心跳检测、在线状态维护和首屏会话初始化；通过 HTTP 事务将消息、会话摘要和 Outbox 一并落库，RabbitMQ Confirm 后由 Inbox 幂等消费者触发 WebSocket 通知；补充 Docker 化 smoke/并发验证脚本，验证核心聊天链路可复现运行。

### 项目条目版

- 基于 Netty WebSocket 完成握手鉴权、用户连接绑定、群组连接管理和断开清理，以 25 秒客户端心跳、75 秒服务端读空闲和 90 秒 Redis TTL 管理在线状态，并通过 `SmartLifecycle` 管理服务启停。
- 在 HTTP 事务内写入消息、会话摘要和 Outbox，调度器以 claim token、Publisher Confirm、有限重试和超时回收发布事件；消费者以 `eventId` 写 Inbox 幂等并在手动 ack 后触发 WebSocket 通知。
- 新密码使用 BCrypt，兼容旧 MD5 账号并在登录后自动升级；使用 256 位安全随机令牌，补齐聊天和文件接口鉴权、访问身份校验与路径规范化。
- 将私聊历史由双向全量查询和内存排序改为按 `sessionId`、`sequenceNo` 联合索引分页；2 万条单会话消息下 16 并发三轮共 15,000 次请求全部成功，中位数为 190.57 QPS，p95 为 157.29ms。
- 补齐群聊产品闭环：群主创建/编辑群、添加/移除成员和解散群，普通成员可退出；前端只展示当前角色可执行的操作，服务端再次校验群主身份避免越权。
- 修复上传文件使用相对路径时被 Tomcat 解析到临时目录的问题，将群头像、用户头像、机器人头像和聊天媒体统一锚定到绝对存储根目录；同时修正群头像封面命名，使写入与下载路径一致。

## 面试讲法

### 项目整体怎么讲

这个项目我会重点讲即时通信后端，而不是只说“做了聊天功能”。核心链路是：HTTP 登录后拿 token，WebSocket 用 30 秒一次性 ticket 握手并绑定 Channel；用户上线时主动推送初始化数据；发送消息时校验关系、生成 `sessionId` 并分配 `sequenceNo`，在同一事务中写消息、会话摘要和 Outbox；Outbox 收到 RabbitMQ Confirm 后，消费者以 Inbox 幂等消费并触发 WebSocket 通知。多节点场景通过 Redisson Pub/Sub 广播到各节点，再由节点向本地 Channel 投递。

### 为什么要用 MQ

聊天消息既要可靠落库，也要在提交后通知在线用户。最初“先推送再异步落库”的方案会产生幽灵消息，所以改为事务内写消息、会话摘要和 Outbox。提交后才异步发布，发布端通过 Confirm、有限重试和超时回收确保持续投递；消费者用 `eventId` 写 Inbox 幂等，再发 WebSocket。这样即使 RabbitMQ 重投也不会重复通知，服务在消息提交后短暂故障也能由 Outbox 补偿。

### 怎么证明不是只写功能

我补了运行验证和压测：本地 Docker 启动 MySQL、Redis、RabbitMQ 和 MinIO，smoke 覆盖注册登录、双用户 WebSocket、消息入库、Outbox 发布、Inbox 幂等通知、历史拉取和队列排空；16 并发读取 2 万条单会话历史，三轮共 15,000 次正式请求全部成功，中位数 190.57 QPS，p95 157.29ms。测试数据、脚本、原始结果和计算口径都保留在仓库中。

### 高频追问与回答要点

#### Outbox 为什么不完全等于分布式事务

Outbox 只保证业务数据和待发布事件在同一个本地事务中提交，消除了“数据库成功但应用根本没有记录要发什么”的窗口。它不保证 RabbitMQ 一定立即可用，所以仍需要 Confirm、重试、claim 和回收。它以最终通知替代跨 MySQL 与 RabbitMQ 的两阶段提交，换取实现复杂度和可用性的平衡。

#### 如何保证同一会话消息顺序

发送时以会话为粒度用 MySQL 连接级 `LAST_INSERT_ID` 原子递增分配 `sequenceNo`。消息、会话摘要和 Outbox 共用该序号，读取、离线同步和已读回执都以 `sequenceNo` 为游标，因此同会话存在严格顺序；不同会话不互相阻塞。

#### 为什么还需要 Inbox

RabbitMQ 是至少一次投递，消费者在写库或网络抖动后可能收到重复事件。Inbox 用 `eventId` 唯一键记录已处理事件，在同一事务内完成去重和状态写入；重复事件不会再次触发 WebSocket 通知。

#### 长期 token 为什么不能放在 WebSocket URL

URL 可能出现在访问日志、浏览器历史、代理日志和监控链路中。服务端先对已鉴权的 HTTP 请求签发 30 秒、一次性消费的 WebSocket ticket，握手只携带 ticket；连接建立后立即失效，降低令牌泄露的暴露面。

## 下一轮补强建议

1. 给 WebSocket 连接数、消息投递成功率、Publisher Confirm 和 MQ 消费耗时补 Micrometer 指标。
2. 在两个后端节点上执行真实的 Redisson Pub/Sub 跨节点推送和节点重启验证。
3. 持续检查并移除不再必要的旧依赖，保持 Spring Boot 3.3 的安全补丁更新。
4. 为群成员变更、文件消息和消费者 DLQ 增加容器级集成测试。
