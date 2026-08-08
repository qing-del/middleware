# 03：统一 Python 音频对接文档与当前实现

## 问题

旧版《音频生成业务接口规范》与实际代码存在多处冲突：

- 仍写成仅支持 Redis Stream，遗漏可选 RabbitMQ；
- 回调路径写成不存在的 `/admin/audio/callback/**`，实际为 `/common/audio/callback/**`；
- 未说明必需的 `X-Callback-Token`；
- 把统一响应的成功码写成 `0`，实际 `Result.SUCCESS=1`；
- 状态表遗漏 `RETRIED(-2)`、`CANCELLED(-3)` 和 `audio_size`；
- 队列、开始回调和完成回调遗漏处理轮次 `attempt`。

另有两处同类偏差：2026-07-30 Python 改造说明引用了不存在的迁移脚本，README 仍把架构描述成仅 Redis。

## 修复

- 将《音频生成业务接口规范》升级为 2.0.0，并按实际代码重写队列、状态、用户 API、回调、资源删除和联调验收章节。
- 将 Python 改造说明升级为 1.1.0，补充 `attempt` 的消费与原样回传规则，并改为引用实际存在的迁移文件。
- README 增加 `jacolp.audio.queue-type`，把架构和流程更新为 Redis Stream/RabbitMQ 二选一，并说明 `attempt`。
- 同步修正 OpenAPI `callbackStart` 描述中的“仅 Redis”措辞。

当前文档明确规定：

- 回调路径为 `/common/audio/callback/start` 和 `/common/audio/callback/finish`；
- 两个回调都携带 `X-Callback-Token`；
- `code=1` 才是统一业务成功码，并继续检查 `data`；
- 生成消息、开始回调和完成回调都必须包含同一 `attempt`；
- Redis Stream 与 RabbitMQ 的名称直接与 Java 常量一致。

## 自动化验证

新增 `AudioPythonContractDocumentationTest`，测试从 Java 控制器注解解析真实路径，并直接读取以下实现证据：

- `Result.SUCCESS`；
- 回调 DTO 的 `attempt` 字段；
- Redis Stream 常量；
- RabbitMQ Exchange 和 Queue 常量。

测试逐项比对两份 Python 文档与 README，并禁止旧 `/admin/audio/callback` 和不存在的迁移文件名回归。

执行命令：

```text
mvn -B -Denforcer.skip=true -pl middleware-server -am \
  -Dtest=AudioPythonContractDocumentationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`；26 个 Reactor 项目全部 `SUCCESS`，`BUILD SUCCESS`。

本机使用 JDK 23，项目 Enforcer 要求 JDK 21，因此测试仅跳过 JDK 版本门禁；编译仍使用 Java 21 `release`。

## 提交范围

- `README.md`；
- 两份音频/Python 对接文档；
- 音频回调 OpenAPI 描述；
- 文档一致性测试；
- 本说明文档。
