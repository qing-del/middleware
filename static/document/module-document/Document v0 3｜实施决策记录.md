# Document v0.3｜实施决策记录

> 记录范围：本文件记录实现过程中无法由仓库或 v0.3 基线唯一确定、但为保证开发连续性而作出的决策。它是对 Implementation Plan 的补充，不替代该计划。
>
> 临时授权：用户于 2026-08-23 指示，在北京时间 2026-08-24 05:00 前，遇到不确定项不再阻塞；实施者应先在本文件记录推荐决策和影响，再按推荐方案继续。该授权到期后，恢复 Goal Prompt 中的「不确定即 BLOCKED」规则。

## D-001：第一版的文档归属范围

- **背景**：仓库不存在 Team、组织或成员关系模型，无法安全校验客户端给出的 `teamId`。
- **决策**：第一版采用个人文档域；服务端将 `teamId` 恒等于当前认证主体的 `owner_user_id`（`CurrentPrincipal.userId`）。客户端不得提交或覆盖该字段。
- **影响**：HTTP、WebSocket、Redis Room Meta、MySQL 查询和 Elasticsearch 读写都必须从已认证用户派生范围；所有者以外的用户不能访问文档。后续引入团队时，需要成员关系解析器，并明确 `team_id` 的迁移/兼容策略，不能直接开放客户端传参。
- **依据**：用户于 2026-08-23 确认。

## D-002：MinIO 与 Elasticsearch 的通用接入

- **背景**：仓库已有 OSS 风格的基础设施模块，但 MinIO 和 Elasticsearch 没有对应的通用自动配置；用户已提供本地 `jacolp` 配置。
- **决策**：新增 framework 层的 MinIO 与 Elasticsearch starter/auto-configuration，由业务模块按需注入客户端；业务模块不持有连接创建逻辑。
- **影响**：部署环境通过配置切换地址、凭证、索引和 bucket；本地 `application-home.yaml` 仅作运行配置，不纳入 Git。Elasticsearch Java Client 与集群使用的 8.17.x 保持兼容。
- **依据**：用户于 2026-08-23 要求按 OSS 风格模块通用化。

## D-003：Document 持久化表的第一版约束

- **背景**：现有仓库 MyBatis / migration 风格使用 MySQL 自增主键及 `datetime(3)` 审计时间，且更新日志需要支持 Redis 到 MySQL 的至少一次重放。
- **决策**：`biz_document` 和 `document_op_log` 使用自增主键；`document_op_log.client_update_id` 非空，并同时以 `(document_id, redis_op_id)`、`(document_id, client_update_id)` 建立唯一约束。
- **影响**：刷盘 worker 可在写库成功、删除 Redis Stream 条目失败后安全重放；同一客户端更新不会重复持久化。表定义同时写入初始化 SQL 与独立 migration，并由测试校验一致性。

## D-004：Redis 待刷盘数据的编码边界

- **背景**：Yjs update 是任意二进制，不能经过字符集转换；Redis 需要保存房间元数据和可恢复的待刷盘操作。
- **决策**：使用 Spring Data Redis 原始连接 API 保存 Stream 中的 `update` 字节；Room Meta 使用 `document:meta:{documentId}`，更新流使用 `document:updates:{documentId}`。Redis 不保存文档正文。
- **影响**：Java 层、后续 WebSocket codec 和 Yjs Merge Client 必须维持字节透明；持久正文只在快照阶段进入 MinIO。

## D-005：Yjs Merge Service 的仓库与部署边界

- **背景**：v0.3 要求独立、无状态的 TypeScript Yjs Merge Service，但当前仓库只有 Java 后端的 Docker/Compose 部署规范，没有既有 Node/TypeScript 服务约定。配置中的 Merge Service 地址指向数据中心机器 `192.168.31.100:3100`。
- **决策**：在本仓库创建 `services/yjs-merge-service` 作为独立 Node/TypeScript 服务，包含自己的 `package.json`、TypeScript 构建配置、测试和 Dockerfile；不修改根 `docker-compose.yml`，因为它描述的是现有 Java 应用部署，且 Merge Service 的目标运行位置是独立的数据中心机器。该服务以独立镜像/容器部署，Java 侧仅通过 `jacolp.yjs-merge-service.base-url` 调用。
- **影响**：服务只暴露 `POST /internal/yjs/merge`，输入/输出使用 Base64，服务不连接 MySQL、Redis 或 MinIO，也不实现权限、房间或 WebSocket。部署时需在数据中心机器为其提供 Node 运行时或容器运行时、端口 3100、健康检查与反向代理/网络访问；Java 侧 Base URL 必须按环境配置。
- **备选方案及未选原因**：不建服务、仅保留 Java client 会使快照合并流程无法运行；将服务加入根 compose 会把当前 Java 应用部署拓扑与数据中心存储服务拓扑耦合，且没有仓库依据支持这种耦合。
- **依据**：用户于 2026-08-23 授权在指定时限内按推荐方案继续，不再因该不确定项阻塞。

## D-006：浏览器 WebSocket 的 JWT 握手传递

- **背景**：仓库没有浏览器 WebSocket 的 JWT 鉴权样例。现有 access token 保存于前端本地存储，浏览器原生 `WebSocket` API 不能设置 `Authorization` 请求头；将 token 写入 URL query 会增加访问日志、代理日志和监控系统泄露凭证的风险。
- **决策**：`/ws/document` 的握手使用现有 RS256 access token，客户端通过标准 `Sec-WebSocket-Protocol` 头传递唯一子协议 `bearer.<JWT>`（浏览器代码形态为 `new WebSocket(url, ['bearer.' + accessToken])`）。服务端 `HandshakeInterceptor` 使用现有 `JwtDecoder` 验签、现有 JWT claims validator 和 `SecurityContextCurrentPrincipalAccessor.fromJwt` 生成 `CurrentPrincipal`，再将该主体写入 WebSocket attributes。它不接受 query token、Cookie token 或另一套 document token。
- **影响**：握手失败必须直接拒绝；每个 JOIN 都从握手主体导出个人范围并校验 `document.team_id == principal.userId`，客户端提交的身份/范围一律忽略。WebSocket endpoint 的允许 Origin 复用 `jacolp.web.cors.allowed-origin-patterns`，部署环境应将该现有配置收紧为实际前端域名；不新增另一套 Origin 配置。
- **兼容性**：这是浏览器侧将“同一份既有 JWT”传入握手的传输约定，而非新增登录或 token 签发协议。后续前端接入时仅需按上述子协议建连；本轮不修改 `frontend/`。
- **依据**：用户于 2026-08-23 授权在指定时限内按推荐方案继续。

## D-007：`UPDATE_ACCEPTED` 的请求关联 ID

- **背景**：v0.3 同时规定“所有控制消息至少携带 `requestId`”，但 `UPDATE_ACCEPTED` 示例仅列出 `clientUpdateId` 和 `redisOpId`。CLIENT_UPDATE 是二进制帧，没有独立的 Text Frame `requestId`。
- **决策**：ACK 仍携带 `requestId`，并将它设置为 CLIENT_UPDATE header 中的同一个 UUID，即 `requestId == clientUpdateId`；同时保留 `clientUpdateId` 字段以表达其幂等语义。JOIN/LEAVE/SYNC 等 Text request 的响应使用其原始 `requestId`。
- **影响**：客户端对单次更新可以只用一个稳定 UUID 完成重发去重与 ACK 关联；服务端 codec 对所有控制帧统一要求 UUID requestId，避免出现两种相关性规则。
- **依据**：用户于 2026-08-23 授权在指定时限内按推荐方案继续。
