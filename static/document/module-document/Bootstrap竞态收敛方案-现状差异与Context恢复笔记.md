# Document Bootstrap 竞态收敛方案

## Context Compact 恢复入口

本文是 2026-08-29 对 `D:\Code\middleware` 当前 `feat-document` 实现的只读探查结论。

恢复任务时优先记住以下结论：

> 当前实现已经具备 Redis pending、FLUSH_LOG、immutable Snapshot、MySQL CAS、SYNCING/ACTIVE 和 Yjs 透传等骨架，但尚未实现本轮要求的 `先实时接入 → Redis pending → 同一 MySQL RR ReadView 读取 Meta + OpLog → 前端 Remote Pending Queue`。当前 Bootstrap 存在 Snapshot 与已被 COMPACT 删除的旧 OpLog 混搭，可能形成真实 Update 缺口。

本轮只做代码探查，未修改业务代码；本文件是本轮唯一新增记录。

## 用户要求对照的目标方案

范围是单机版第一版：

- 只部署一台后端服务器，不考虑跨实例实时广播、同步和广播。
- MySQL 中的 Snapshot 指针一定能在 MinIO 读取到对应不可变对象。
- 客户端 JOIN 后立即进入远端 Update 接收状态。
- Bootstrap 返回历史数据期间，客户端持续接收并缓存其他客户端产生的 `CRDT_UPDATE`。
- 目标不是 Exactly Once，而是允许 Snapshot、MySQL OpLog、Redis pending、Live Queue 重复覆盖，不能让某个 Update 在所有来源中都消失。

目标顺序：

```text
1. Session 进入 SYNCING，并建立 Live CRDT_UPDATE 接收
2. 读取 Redis Stream 当前可见的全部 pending Updates
3. 开启 MySQL REPEATABLE_READ 只读事务
4. 在第一次 Meta 查询时建立 ReadView，读取 content_object_key 与 persisted_log_id
5. 在同一个事务 / ReadView 中分页读取 id > persisted_log_id 的 OpLog
6. 结束 RR 事务，不在事务内等待 MinIO 网络 IO
7. 按已取得的 Snapshot 指针读取 MinIO Snapshot
8. 发送 Snapshot + RR OpLog Set + Redis Initial Pending Updates
9. 客户端将三类历史数据和 Bootstrap 期间缓存的 Remote Pending Updates 统一应用到 Y.Doc
10. 完成合并后发送/处理 SYNC_COMPLETE，Session 切换 ACTIVE
```

时间边界：

```text
Tlive <= Tredis <= TreadView
```

FLUSH_LOG 采用：

```text
读取 Redis pending
→ MySQL INSERT
→ MySQL Commit
→ Redis XDEL
```

因此 Redis 初始读取之前被接受但尚未入库的 Update 由 Redis 覆盖；Redis 读取之后产生的 Update 由 Live Update + 前端 Remote Pending Queue 覆盖；Redis 读取时已被删除的 Update 应已能从随后建立的 RR ReadView 中看到。

## 当前实现实际调用链

### 服务端 JOIN / Bootstrap

当前主流程位于：

- `middleware-module-document/middleware-module-document-biz/src/main/java/com/jacolp/document/websocket/DocumentWebSocketHandler.java`
- `middleware-module-document/middleware-module-document-biz/src/main/java/com/jacolp/document/websocket/DocumentBootstrapService.java`

当前顺序：

```text
DocumentWebSocketHandler.handleJoin
  → documentMapper.selectActiveByIdAndTeamId(documentId, userId)
       [先读取 DocumentDO / Snapshot 指针 / persisted_log_id]
  → roomManager.getOrCreate
  → room.join(session, principal)
       [DocumentSessionContext 默认 SYNCING]
  → presenceRegistry.register
  → lifecycleService.reopen
  → 发送 JOIN_ACCEPTED
  → bootstrapService.sendBootstrap(document, session)
       → 读取 MinIO Snapshot
       → 读取 MySQL OpLog: id > document.persistedLogId
       → 读取 Redis Stream 全部 pending
  → room.markActive
  → 发送 SYNC_COMPLETE
```

关键代码事实：

- `DocumentWebSocketHandler.java:181-192` 在 `room.join()` 之前读取 Document Meta。
- `DocumentWebSocketHandler.java:194-204` 注册 presence、发送 JOIN_ACCEPTED、执行 Bootstrap，最后标记服务端 Session ACTIVE 并发送 SYNC_COMPLETE。
- `DocumentBootstrapService.java:50-57` 明确按 Snapshot → MySQL OpLog → Redis pending 发送。
- `DocumentBootstrapService.java:68-76` 通过 `contentObjectKey` 读取 MinIO Snapshot。
- `DocumentBootstrapService.java:80-101` 按 `afterId` 分批读取 OpLog，但没有事务上下文。
- `DocumentBootstrapService.java:104-111` 使用 `readPendingUpdates(documentId, Integer.MAX_VALUE)` 读取 Redis 当前可见 Stream 条目。

### Room / 服务端实时接收

- `DocumentSessionContext.syncStatus` 初始值是 `SYNCING`，只有 `markActive()` 才切换为 `ACTIVE`。
- `DocumentRoom.broadcast()` 遍历所有会话，没有排除 `SYNCING` 会话，因此新 JOIN 会话在 Bootstrap 期间可以接收其他会话的实时 `CRDT_UPDATE`。
- `DocumentWebSocketHandler.requireActiveRoom()` 只允许 `ACTIVE` 会话提交 `CLIENT_UPDATE`，因此 Bootstrap 期间客户端本地更新会被服务端拒绝。
- `acceptClientUpdate()` 的服务端写入顺序是：Redis XADD → 更新 Document Meta 的最后修改字段 → ACK → 广播 → 调度 FLUSH_LOG。

### 前端 Yjs 客户端

主要文件：

- `frontend/src/collaboration/DocumentCollaborationClient.ts`
- `frontend/src/collaboration/documentProtocol.ts`
- `frontend/src/views/user/DocumentEditor.vue`

当前行为：

- `DocumentConnectionState` 有 `synchronizing` / `synced` 状态。
- `synchronized=false` 时不发送本地更新；本地更新会放入 `pendingUpdates`，等待 SYNC_COMPLETE 后发送。
- `pendingUpdates` 是“本地未收到 UPDATE_ACCEPTED 的更新队列”，不是目标方案要求的“远端 Bootstrap 期间 Remote Pending Updates Queue”。
- `handleBinary()` 对 `SNAPSHOT_STATE`、`BOOTSTRAP_UPDATE`、`CRDT_UPDATE` 都立即执行 `Y.applyUpdate()`。
- `SYNC_COMPLETE` 到达后立即设置 `synchronized=true`、状态为 `synced`，然后发送本地 `pendingUpdates` 和 awareness。
- 服务端来源的 Yjs 更新使用 `REMOTE_UPDATE_ORIGIN`，不会再次写回 CLIENT_UPDATE；这一点是正确的。

## 与目标方案的差异矩阵

| 目标要求 | 当前实现 | 判断 |
| --- | --- | --- |
| JOIN 后先建立 Live Update 接收 | `room.join()` 在 `sendBootstrap()` 前执行，Room 广播包含 SYNCING 会话 | 基本满足 |
| Meta 读取不能早于 Live 边界 | Document Meta 在 `room.join()` 前读取 | 不满足 |
| Redis 必须先于 RR Meta/OpLog 读取 | 当前顺序是 Snapshot → OpLog → Redis | 不满足 |
| Meta 与 OpLog 使用同一 RR 事务 | Bootstrap 没有 `TransactionTemplate` / `@Transactional` / 事务依赖 | 不满足，核心缺口 |
| OpLog 分页保持同一个 ReadView | 每页都是普通 Mapper 调用，没有共享事务 | 不满足，核心缺口 |
| Snapshot 指针与 OpLog 属于同一读取视图 | 指针来自 JOIN 前的普通查询，后续日志查询可能看到另一时刻 | 不满足，核心缺口 |
| Bootstrap 期间缓存远端实时 Update | 服务端发送，但前端立即应用 | 不满足 |
| 历史数据与 Remote Queue 合并后再 ACTIVE | 当前没有 Remote Queue；服务端先 `markActive()` 再发送 SYNC_COMPLETE | 部分满足，顺序不完全符合 |
| Redis 读取当前全部可见 pending | `XRANGE` 无界读取，调用上限为 `Integer.MAX_VALUE` | 满足 |
| FLUSH 先 MySQL Commit 后 XDEL | `TransactionTemplate.execute()` 返回后才 XDEL | 满足 |
| OpLog Redis/客户端幂等 | DDL 有 `(document_id, redis_op_id)` 与 `(document_id, client_update_id)` 唯一键 | 满足 |
| immutable Snapshot + MySQL CAS | 每次生成 UUID 对象键，CAS 成功后切指针 | 满足 |
| Yjs 重复/乱序可合并 | 前端和 Merge Service 使用 Yjs `applyUpdate` | 基本满足 |

## 关键 Update 缺口竞态

这是当前实现与新方案之间最重要的差异，不只是“读取顺序不同”。

设当前有效状态为：

```text
Snapshot = S0
persisted_log_id = 10
document_op_log 中存在 Update U(id=11)
Redis 中没有 U（U 已经 FLUSH 并 XDEL）
```

可能发生以下顺序：

```text
JOIN 线程：selectActiveByIdAndTeamId() 读到 S0 / persisted_log_id=10

COMPACT 线程：
  读取 S0 + U(11)
  写入不可变 Snapshot S1
  CAS 更新 biz_document: persisted_log_id=11, content_object_key=S1
  删除 document_op_log 中 id <= 11

JOIN 线程：
  使用之前拿到的旧 DocumentDO 读取 S0
  查询 document_op_log WHERE id > 10，U(11) 已被删除，结果为空
  读取 Redis，U 也不存在
```

此时新 JOIN 客户端拿到：

```text
S0 + 空 OpLog + 空 Redis
```

而 U 在 JOIN 前产生，因此也没有 Live 广播覆盖它。Update U 在所有恢复来源中都缺失。

即使把 Meta 查询挪到 `room.join()` 之后，只要没有 RR ReadView，COMPACT 仍可能在 Meta 查询之后推进指针并删除旧日志、而 JOIN 的后续 OpLog 查询看不到已删除记录。

目标方案的 RR 逻辑是：

- 如果 RR Meta 读取发生在 COMPACT 前，则同一 ReadView 的 OpLog 查询仍能看到 U，即使之后 U 被删除。
- 如果 RR Meta 读取发生在 COMPACT 后，则读取到 S1 / persisted_log_id=11，不再需要 U。
- 因此不会出现“旧 Snapshot 指针 + 新世界已删除的 OpLog”混搭。

## 已实现且可复用的部分

### Redis pending 与 FLUSH_LOG

`DocumentRedisRepository` 已经具备：

- 二进制安全的 Redis Stream XADD。
- 按 Stream 顺序读取并保留 Redis entry ID。
- XDEL 指定已落库 entry。
- Redis pending 数量和恢复扫描。

`DocumentFlushLogService` 已经具备：

```text
read Redis batch
→ TransactionTemplate 内 insertBatchIgnoringDuplicates
→ TransactionTemplate 返回（MySQL 已提交）
→ deletePendingUpdates / XDEL
```

这正是目标方案依赖的“Redis 删除前已进入 MySQL”语义，不需要推翻。

### Snapshot / COMPACT

`DocumentCompactService` 已经具备：

- 从当前 `content_object_key` 和 `persisted_log_id` 开始读取 OpLog。
- 通过独立 Yjs Merge Service 合并 Snapshot + OpLog。
- 每次写入新 UUID 对象，不覆盖旧 Snapshot。
- 通过 `updateSnapshotPointerIfPersistedLogId()` 做 CAS。
- CAS 失败时不删除日志，loser 对象允许成为 orphan。
- CAS 成功后删除已被 Snapshot 覆盖的 OpLog，删除失败不会回退指针。

这些机制与新方案兼容；真正缺的是 Bootstrap 读取方的 RR ReadView。

### 协议和状态

现有协议已经有：

- `CLIENT_UPDATE`
- `CRDT_UPDATE`
- `SNAPSHOT_STATE`
- `BOOTSTRAP_UPDATE`
- `AWARENESS`
- `SYNC_COMPLETE`

因此不一定需要重新设计整个协议。较小的方向是利用现有 `SYNC_COMPLETE` 作为前端 Remote Queue 的结束边界，并调整服务端 Bootstrap 数据读取和前端帧处理。

## 当前测试覆盖情况

已有测试覆盖：

- `DocumentBootstrapServiceTest`：验证 Snapshot、MySQL OpLog、Redis pending 的当前发送顺序和二进制透传。
- `DocumentWebSocketHandlerTest`：验证 JOIN、SYNC_COMPLETE、Redis 接收后 ACK、同一连接文档隔离。
- `DocumentFlushLogServiceTest`：验证 MySQL 写入先于 Redis 删除，以及数据库失败时保留 Redis。
- `DocumentCompactServiceTest`：验证 immutable Snapshot、CAS loser 和日志清理失败语义。
- `services/yjs-merge-service/src/test`：验证 Yjs 重复更新、乱序更新和 Snapshot + 增量更新合并。

未覆盖的关键测试：

- JOIN Bootstrap 与 COMPACT CAS/OpLog 删除并发时，旧 ReadView 是否仍能读到旧日志。
- Meta 与 OpLog 是否真正处于同一个 MySQL RR 事务。
- Redis 初始读取之后到 Bootstrap 完成期间，前端是否缓存所有 `CRDT_UPDATE`。
- Remote Queue 在 Snapshot、OpLog、Redis 历史数据之后是否统一应用并最终收敛。
- 没有发现前端针对 `DocumentCollaborationClient` 的自动化测试文件。

现有 Bootstrap 单测实际上固定了旧顺序：

```text
Snapshot → Durable OpLog → Redis pending
```

它不能证明新方案的竞态性质。

## 后续改造边界（仅记录，不代表本轮已实施）

需要重点改造的不是 Redis/MinIO 基础设施，而是以下三处：

1. Bootstrap 服务需要在 Session 已注册到 Room 后读取 Redis pending，然后开启一个配置为 `REPEATABLE_READ` 的只读事务。
2. Document Meta 与 OpLog 分页读取必须由同一个事务回调承载，并把 `content_object_key`、`persisted_log_id` 和日志列表作为一个读取结果返回；事务结束后再读 MinIO。
3. 前端需要单独维护 Remote Pending Updates Queue：`SYNCING` 期间收到 `CRDT_UPDATE` 先缓存；收到全部 Bootstrap 帧后，按 Snapshot / OpLog / Redis / Remote Queue 的逻辑并集应用；完成后再进入 synced/ACTIVE。

需要保留的语义：

- 服务端只在 Redis XADD 成功后 ACK 和广播。
- Bootstrap 期间服务端继续向 SYNCING 会话广播远端 Update。
- 客户端 Bootstrap 期间禁止本地编辑/发送 CLIENT_UPDATE。
- Yjs 负责重复、乱序和并发 Update 的幂等合并。
- FLUSH_LOG 仍然先 MySQL Commit，再 XDEL Redis。
- COMPACT 继续使用 immutable MinIO object + MySQL persisted_log_id CAS。

## 关键文件索引

| 主题 | 文件 |
| --- | --- |
| JOIN、SYNCING/ACTIVE、实时广播入口 | `middleware-module-document/middleware-module-document-biz/src/main/java/com/jacolp/document/websocket/DocumentWebSocketHandler.java` |
| Bootstrap 当前读取顺序 | `middleware-module-document/middleware-module-document-biz/src/main/java/com/jacolp/document/websocket/DocumentBootstrapService.java` |
| Session 状态 | `middleware-module-document/middleware-module-document-biz/src/main/java/com/jacolp/document/websocket/DocumentSessionContext.java` |
| Room 广播 | `middleware-module-document/middleware-module-document-biz/src/main/java/com/jacolp/document/websocket/DocumentRoom.java` |
| Redis Stream | `middleware-module-document/middleware-module-document-biz/src/main/java/com/jacolp/document/infrastructure/redis/DocumentRedisRepository.java` |
| FLUSH_LOG | `middleware-module-document/middleware-module-document-biz/src/main/java/com/jacolp/document/application/flush/DocumentFlushLogService.java` |
| COMPACT / Snapshot CAS | `middleware-module-document/middleware-module-document-biz/src/main/java/com/jacolp/document/application/compact/DocumentCompactService.java` |
| Snapshot MinIO 读写 | `middleware-module-document/middleware-module-document-biz/src/main/java/com/jacolp/document/application/compact/DocumentSnapshotStorage.java` |
| OpLog 查询 SQL | `middleware-module-document/middleware-module-document-biz/src/main/resources/mapper/document/DocumentOpLogMapper.xml` |
| Document Meta / Snapshot 指针 SQL | `middleware-module-document/middleware-module-document-biz/src/main/resources/mapper/document/DocumentMapper.xml` |
| 前端协作状态和 Yjs 帧处理 | `frontend/src/collaboration/DocumentCollaborationClient.ts` |
| 前端协议帧定义 | `frontend/src/collaboration/documentProtocol.ts` |
| Bootstrap 当前单测 | `middleware-module-document/middleware-module-document-biz/src/test/java/com/jacolp/document/websocket/DocumentBootstrapServiceTest.java` |
| COMPACT 当前单测 | `middleware-module-document/middleware-module-document-biz/src/test/java/com/jacolp/document/application/compact/DocumentCompactServiceTest.java` |
| FLUSH 当前单测 | `middleware-module-document/middleware-module-document-biz/src/test/java/com/jacolp/document/application/flush/DocumentFlushLogServiceTest.java` |

## 当前结论

```text
结构/组件完成度：较高
目标方案的关键一致性完成度：未达标
阻断性缺口：RR ReadView、Meta+OpLog 同事务、前端 Remote Pending Queue
是否存在理论上的 Update 缺口：存在
本轮是否修改代码：否
```

## 实施执行记录（2026-08-29）

本节记录后续实现过程中的固定边界、执行顺序、每个 commit 的职责和验证结果。发生 Context Compact 后，应先读取本节，再查看各 commit 的实际差异；不要仅凭当前代码表面顺序判断 Bootstrap 是否已经满足无缺口语义。

### 本阶段唯一目标

先完成“客户端 JOIN 成功后，服务端接受到的新 Update 不丢失”的最小闭环：

```text
Tlive = room.join() 成功，Session 开始处于 SYNCING 且能接收 Room 广播
Tredis = Redis Stream 当前可见 pending 读取完成
TreadView = MySQL RR 事务第一次 Bootstrap Meta 一致性读取建立 ReadView
```

必须最终满足：

```text
Tlive <= Tredis <= TreadView
```

Update 覆盖关系：

```text
JOIN 前产生的 Update → Redis Initial Pending 或 RR ReadView 下的 OpLog
Tlive 之后产生的 Update → 服务端 CRDT_UPDATE → 前端 Remote Pending Queue
Redis / MySQL / Live Queue 重复出现 → 交给 Yjs 幂等合并，不手工按 eventId 去重
```

### 目标执行时序

```text
JOIN 请求
  → 权限校验（不能把这里返回的旧 Snapshot 指针作为 Bootstrap 来源）
  → room.join()
       → Session=SYNCING
       → 开始接收 CRDT_UPDATE 广播
  → JOIN_ACCEPTED
  → 读取 Redis 当前全部 pending
  → 开启 MySQL REPEATABLE_READ 只读事务
       → 第一次读取 Bootstrap Meta：content_object_key / persisted_log_id
       → 在同一事务内分页读取 id > persisted_log_id 的 OpLog
  → 结束 RR 事务
  → 事务外读取不可变 MinIO Snapshot
  → 发送 Snapshot → OpLog → Redis Initial Pending
  → 发送 SYNC_COMPLETE（所有 Bootstrap 帧已排入该 Session 的发送序列）
  → 前端应用 Snapshot / Bootstrap Updates / Remote Pending Queue
  → 前端完成最终 Y.Doc 构建后切换 synced，并发送本地 pendingUpdates
```

读取顺序和发送顺序是两个概念。Redis 必须先读，但 Redis 数据不必先发；发送仍保持 Snapshot、OpLog、Redis 的历史构建顺序即可。

### 前端实现固定约束

- `pendingUpdates` 继续表示本地尚未收到 `UPDATE_ACCEPTED` 的更新，不能与远端队列混用。
- 新增的 Remote Pending Queue 只保存服务端发来的 `CRDT_UPDATE`，同步期间不能直接应用到 Y.Doc。
- 为忠实实现“收到完整 Bootstrap 后最终构建”，Bootstrap 阶段的 `SNAPSHOT_STATE` 和 `BOOTSTRAP_UPDATE` 也应先进入本轮 Bootstrap 累积器；`SYNC_COMPLETE` 到达后按 Snapshot、Bootstrap Updates、Remote Updates 顺序应用。
- 所有服务端来源都使用 `REMOTE_UPDATE_ORIGIN`，不能触发新的 `CLIENT_UPDATE`。
- 收到 `SYNC_COMPLETE` 时必须先完成最终 Yjs 合并，再设置 `synchronized=true`、状态为 `synced`，最后发送本地 `pendingUpdates` 和 awareness。
- 重连建立新 Bootstrap 时，接收缓存按连接尝试重新初始化；本地 `pendingUpdates` 不能清空。任何缓存超限都只能失败并重新恢复，不能静默丢弃 Update。
- `AWARENESS` 不属于文档恢复链路，本阶段继续即时处理。

### 后端实现固定约束

- `DocumentRoom.broadcast()` 必须继续向 `SYNCING` 会话转发 `CRDT_UPDATE`；不能加“只发 ACTIVE 会话”的过滤。
- JOIN 前的数据库查询最多承担权限/存在性判断；它返回的 `content_object_key`、`persisted_log_id` 不能进入 Bootstrap。
- `DocumentBootstrapService` 应改为以 `documentId + userId` 读取 Bootstrap 数据，而不是信任 Handler 在 JOIN 前拿到的 `DocumentDO`。
- Redis pending 必须完整读取后，才能开启 Bootstrap RR 事务。
- Meta 和 OpLog 的所有分页查询必须在同一个显式 `REPEATABLE_READ`、只读事务中完成；事务结束后才允许 MinIO 网络 IO 和 WebSocket 发送。
- FLUSH_LOG 继续保持 `MySQL Commit → Redis XDEL`，COMPACT 继续保持 immutable Snapshot + MySQL CAS。
- 本阶段先使用现有 `SYNC_COMPLETE` 作为“服务端 Bootstrap 帧发送结束”边界。服务端严格等客户端最终合并后再切 ACTIVE 的 `SYNC_READY` 握手另起 commit，不与本阶段混合。

### Commit 边界和恢复状态

每个功能 commit 必须只解决一个时序点，并在同一个 commit 中加入直接相关测试或验证；不要把协议握手、COMPACT 策略、跨实例广播等内容混入。

| 顺序 | 计划 commit | 单一职责 | 完成判定 |
| --- | --- | --- | --- |
| 0 | `docs(document): record bootstrap convergence execution plan` | 保存本实施计划和恢复信息 | 本节已写入，业务代码未改 |
| 1 | `feat(document): buffer bootstrap and remote updates before sync` | 前端 Bootstrap 累积器、Remote Pending Queue、最终 Yjs 构建 | 同步期间不直接应用远端 CRDT；完成帧后一次性合并 |
| 2 | `refactor(document): establish live bootstrap boundary after join` | Bootstrap 不再使用 JOIN 前旧指针；锁定 SYNCING 转发行为 | 新会话加入 Room 后的广播可被接收，旧 DocumentDO 不再控制 Snapshot/OpLog 读取 |
| 3 | `feat(document): read redis pending before bootstrap metadata` | 调整读取顺序为 Live → Redis → MySQL Meta | 可验证 Redis 读取发生在 Bootstrap Meta 读取之前 |
| 4 | `feat(document): read bootstrap history in one repeatable read view` | 同一 RR 事务读取 Meta 和所有分页 OpLog | COMPACT 并发推进指针、删除旧日志时，不出现旧 Snapshot + 缺失 OpLog |

当前执行状态：

```text
Commit 0：待提交
Commit 1：待实施
Commit 2：待实施
Commit 3：待实施
Commit 4：待实施
```

### 验证重点

- 前端：`CRDT_UPDATE` 在 Snapshot 前、Snapshot 与 OpLog 之间、Redis Bootstrap 期间到达时，都必须在最终构建中出现；Y.Doc 更新来源不能回流为 CLIENT_UPDATE。
- 后端：一个新会话停留在 `SYNCING` 时，另一个 ACTIVE 会话产生的 Update 必须能被新会话收到；新会话在 Bootstrap 完成前发送 CLIENT_UPDATE 仍应被拒绝。
- Bootstrap：使用调用顺序测试验证 Redis 先于 Meta；使用事务回调验证 Meta 与 OpLog 分页在同一事务中。
- 并发：最终需要补充 JOIN Bootstrap 与 COMPACT CAS/日志删除并发场景，验证旧 ReadView 仍能读到其对应的 OpLog。
- 当前 `frontend/package.json` 没有自动化测试脚本，前端每个 commit 至少执行 `npm run build`；若引入测试运行器，另行作为独立基础设施 commit，不混入恢复逻辑。

### 当前执行日志

| 日期 | 阶段 | 结果 | 备注 |
| --- | --- | --- | --- |
| 2026-08-29 | 计划基线 | 进行中 | 本文新增实施约束；尚未修改业务代码 |
