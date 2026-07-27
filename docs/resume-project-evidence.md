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

### 2. 实时推送与异步落库解耦

问题：如果发消息时同步写库，数据库慢或失败会影响实时到达；如果只推送不落库，聊天记录和会话列表又不可靠。

方案：

- 发送前校验好友或群成员关系，按私聊/群聊生成稳定 sessionId。
- 清洗消息内容后先通过 WebSocket 实时推送，同时投递 RabbitMQ 做异步落库。
- 发布端等待 Broker Confirm，失败最多重试 3 次；消费者在事务内更新会话表和消息表，成功后手动 ack。
- 消费端通过消息指纹幂等过滤重复消息，异常消息有限重试后进入 DLQ，重发成功后才确认原消息。

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

- 私聊和群聊统一生成稳定 `sessionId`，查询层按 `sessionId` 过滤，并使用 `session_id, send_time` 联合索引完成倒序分页。
- 固定 2 万条单会话消息、40 次请求、8 并发、3 次预热，在同一机器和中间件上分别运行旧版本与新版本，各执行三轮并取中位数。
- 吞吐从 24.36 提升至 244.18 QPS，p95 从 370.18ms 降至 42.96ms，平均响应体从 6384.73KB 降至 6.54KB。

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
- 后端测试：`mvn test` 通过，当前共 29 项测试，覆盖密码兼容迁移、安全令牌、聊天与群组接口鉴权、群主权限、Mapper SQL 回归、会话分页、WebSocket 心跳与群组连接、Publisher Confirm 和重试边界。
- 运行 smoke：本地 Docker 测试环境覆盖 OpenAPI、鉴权、注册后登录、Alice/Bob WebSocket 初始化、Alice -> Bob 实时私聊、MQ 消费落库、聊天记录拉取和队列排空。
- RabbitMQ 实际验证：向 Broker 连续发布 2 条相同消息，数据库最终只保留 1 条且主队列排空；非法消息被拒绝后进入 DLQ。
- 群聊实际验证：使用一次性测试群完成创建、群成员添加、群消息异步持久化、移除成员后的详情拒绝和群解散；脚本在结束时自动解散测试群。
- 并发验证：联系人列表和聊天记录读取接口共 100 次请求、20 并发，成功率 100%，本次实测整体 p95 为 217.72ms；其中联系人 p95 为 222.14ms，聊天记录 p95 为 208.78ms。
- 心跳验证：WebSocket 握手与发送心跳后的 Redis TTL 均为 89 秒，符合 25 秒客户端心跳、75 秒服务端读空闲和 90 秒在线状态 TTL 的配置。
- 历史查询对照：2 万条单会话消息下三轮中位数由 24.36 提升至 244.18 QPS，p95 由 370.18ms 降至 42.96ms。

说明：这些结果来自固定的本地 Docker 环境，用于验证改造前后的相对收益和核心链路可运行性，不代表生产容量上限。

## 简历可用表达

### 精简版

Letchat 即时通信系统：负责基于 Netty WebSocket 的长连接接入、用户连接绑定、心跳检测、在线状态维护和首屏会话初始化；通过 Redis 缓存联系人与在线状态，通过 RabbitMQ 将实时推送与消息落库解耦，消费者手动 ack 并在事务内更新会话和消息表；补充 Docker 化 smoke/并发验证脚本，验证核心聊天链路可复现运行。

### 项目条目版

- 基于 Netty WebSocket 完成握手鉴权、用户连接绑定、群组连接管理和断开清理，以 25 秒客户端心跳、75 秒服务端读空闲和 90 秒 Redis TTL 管理在线状态，并通过 `SmartLifecycle` 管理服务启停。
- 采用 WebSocket 实时推送与 RabbitMQ 异步落库解耦，发布端等待 Broker Confirm 并最多重试 3 次，消费端通过手动 ack、事务、消息指纹幂等和 DLQ 处理异常消息。
- 新密码使用 BCrypt，兼容旧 MD5 账号并在登录后自动升级；使用 256 位安全随机令牌，补齐聊天和文件接口鉴权、访问身份校验与路径规范化。
- 将私聊历史由双向全量查询和内存排序改为按 `sessionId` 联合索引分页；2 万条单会话消息下三轮吞吐中位数由 24.36 提升至 244.18 QPS，p95 由 370.18ms 降至 42.96ms。
- 补齐群聊产品闭环：群主创建/编辑群、添加/移除成员和解散群，普通成员可退出；前端只展示当前角色可执行的操作，服务端再次校验群主身份避免越权。
- 修复上传文件使用相对路径时被 Tomcat 解析到临时目录的问题，将群头像、用户头像、机器人头像和聊天媒体统一锚定到绝对存储根目录；同时修正群头像封面命名，使写入与下载路径一致。

## 面试讲法

### 项目整体怎么讲

这个项目我会重点讲即时通信后端，而不是只说“做了聊天功能”。核心链路是：HTTP 登录后拿 token，WebSocket 握手时校验登录态并绑定 Channel；用户上线时主动推送初始化数据；发送消息时先校验关系和生成 sessionId，再 WebSocket 实时推送，RabbitMQ 异步落库；如果是多节点部署，就通过 Redisson Pub/Sub 广播到各节点，每个节点再投递给本地在线连接。

### 为什么要用 MQ

聊天消息既要实时到达，也要可靠落库。同步落库会让数据库延迟影响消息体验，所以我把实时推送和落库拆开：发送链路先校验和推送，同时把消息投到 MQ；消费者再在事务内更新会话和消息。这样用户侧能尽快收到消息，数据库失败也可以通过 MQ 消费日志、ack/nack 和重试策略排查。

### 怎么证明不是只写功能

我补了运行验证和对照压测：本地 Docker 启动 MySQL、Redis、RabbitMQ，smoke 覆盖注册登录、双用户 WebSocket、实时消息、MQ 落库、聊天记录拉取和队列排空；固定 2 万条单会话消息，在同一环境分别运行旧全量查询与新分页查询，三轮中位数从 24.36 提升到 244.18 QPS，p95 从 370.18ms 降到 42.96ms。测试数据、脚本、原始结果和计算口径都保留在仓库中。

## 下一轮补强建议

1. 给 WebSocket 连接数、消息投递成功率、Publisher Confirm 和 MQ 消费耗时补 Micrometer 指标。
2. 在两个后端节点上执行真实的 Redisson Pub/Sub 跨节点推送和节点重启验证。
3. 将 Spring Boot 2.6 升级到受支持版本，并逐步移除 Fastjson 1 等旧依赖。
4. 为群成员变更、文件消息和消费者 DLQ 增加容器级集成测试。
