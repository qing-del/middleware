# 02：修复音频超时重试的旧工作进程回写竞态

## 问题

超时扫描原先只执行 `retry_time + 1`，随后把扫描时读取到的旧对象重新入队：

- `PROCESSING` 任务没有重置为 `PENDING`，而开始回调只允许 `PENDING -> PROCESSING`；
- 开始、完成回调的 CAS 只比较任务状态，没有区分处理轮次；
- 旧工作进程可能在新一轮开始后，以相同的 `PROCESSING` 状态写入过期结果；
- 队列消息和 Python 回调均没有携带可用于隔离轮次的标识。

## 修复

复用数据库已有的非空 `retry_time` 作为任务处理轮次 `attempt`：

1. 超时重试通过单条 SQL 原子执行 `status = PENDING`、`retry_time + 1`，并清理上一轮可能留下的结果字段；更新条件仍包含状态、重试上限和超时边界，多个调度实例只有一个能成功。
2. 更新成功后重新查询数据库记录，投递包含最新状态和轮次的对象，不再投递扫描快照。
3. Redis Stream 与 RabbitMQ 生成消息都增加 `attempt`；新任务为 `0`，每次自动超时重试递增。
4. `/common/audio/callback/start` 和 `/common/audio/callback/finish` 请求增加必填、非负的 `attempt`。
5. 所有回调 SQL CAS 同时比较 `status` 和 `retry_time`。成功回调在扣减存储配额前也先比较轮次，避免过期回调产生额度副作用。

因此，旧工作进程持有的 `attempt=N` 在数据库进入 `N+1` 后始终无法更新任务：无论新一轮尚未开始还是已经进入 `PROCESSING`，轮次条件都会拒绝它。

## 对 Python 工作进程的影响

工作进程必须原样保存队列消息中的 `attempt`，并在开始和完成回调中回传。不能只根据 `taskId` 生成回调。完整接口文档会在本修复系列下一版本统一更新。

## 自动化验证

针对性命令：

```text
mvn -B -Denforcer.skip=true \
  -pl middleware-module-audio/middleware-module-audio-biz -am \
  -Dtest=AudioTaskRetryTaskTest,AudioTaskServiceImplTest,AudioTaskPublisherAttemptTest,AudioTaskMapperContractTest,AudioCallbackControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：`Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`，Reactor `BUILD SUCCESS`。

随后执行音频模块及其依赖的全部测试：

```text
mvn -B -Denforcer.skip=true \
  -pl middleware-module-audio/middleware-module-audio-biz -am test
```

结果：音频模块 `Tests run: 27, Failures: 0, Errors: 0, Skipped: 0`；其依赖模块测试也全部通过，9 个 Reactor 项目均为 `SUCCESS`。

覆盖点包括：

- 超时的 `PROCESSING` 任务只投递数据库重新读取的 `attempt=1` 记录；
- Redis 新任务默认投递 `attempt=0`；
- RabbitMQ 重试任务投递准备后的轮次；
- 开始回调把轮次加入 CAS；
- 旧轮次成功回调在存储配额扣减前被拒绝；
- Mapper XML 必须重置状态、递增轮次，并在回调 CAS 中比较轮次。

本机使用 JDK 23，项目 Enforcer 要求 JDK 21，因此测试仅跳过 JDK 版本门禁；源码和测试仍以 Java 21 `release` 编译。

## 提交范围

- 音频回调 DTO、服务、Mapper、超时任务和两种队列发布器；
- 音频模块对应的单元测试与 SQL 合约测试；
- 本说明文档。
