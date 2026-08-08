# 06：修复无 OSS 凭证环境的定时保活异常

## 问题

`AliyunOSSClientKeepLiveTask` 原先只受 `jacolp.aliyun.oss.enabled=true` 控制，而该配置默认开启。阿里云 SDK 的环境变量凭证提供器会延迟到真正发出请求时才读取凭证，因此应用可以成功创建 OSS Client，但保活任务一执行就会抛出：

```text
com.aliyun.oss.common.auth.InvalidCredentialsException:
Access key id should not be null or empty.
```

在本地、测试或不使用图片上传的部署中，这会持续污染日志；把异常捕获后继续定时请求也不能解决缺少凭证的问题。

## 修复

保活任务改为同时满足以下两个开关才注册：

- `jacolp.aliyun.oss.enabled=true`：OSS 总开关；
- `jacolp.aliyun.oss.keep-live-enabled=true`：保活专用开关。

保活专用开关默认值为 `false`，Docker Compose 通过环境变量 `OSS_KEEP_LIVE_ENABLED` 显式传入。这样：

- 未配置 AccessKey 的环境不会创建保活任务，也不会产生定时异常；
- OSS Client 和正常的按需上传/删除能力没有被整体关闭；
- 确实需要周期保活的部署可设置 `OSS_KEEP_LIVE_ENABLED=true`，继续使用原来的 45 秒周期；
- OSS 总开关关闭时，保活专用开关不能越权启动任务。

README 的定时任务说明同步记录了这一行为。

## 自动化验证

新增 `AliyunOSSClientKeepLiveTaskTest`，使用真实 Spring 条件装配流程验证：

1. 只开启 OSS 总开关时，不存在保活任务 Bean；
2. 两个开关同时开启时，只创建一个保活任务 Bean；
3. OSS 总开关关闭时，即使保活开关开启也不创建任务；
4. 应用配置与 Compose 都把保活默认设置为关闭。

针对性测试命令：

```text
mvn -B -Denforcer.skip=true \
  -pl middleware-module-media/middleware-module-media-biz -am \
  -Dtest=AliyunOSSClientKeepLiveTaskTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`，18 个 Reactor 项目全部 `SUCCESS`，`BUILD SUCCESS`。

媒体模块及其依赖全量测试：

```text
mvn -B -Denforcer.skip=true \
  -pl middleware-module-media/middleware-module-media-biz -am test
```

结果：媒体业务模块 `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`，18 个 Reactor 项目全部 `SUCCESS`，`BUILD SUCCESS`。

## 真实运行验证

重新打包服务后，在未设置 `OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET` 和 `OSS_KEEP_LIVE_ENABLED` 的环境使用 `home` 配置启动：

- Spring Boot 成功启动并监听 8080；
- MySQL 与 RabbitMQ 正常连接；
- 启动后继续观察超过一次原任务的立即调度时点，没有出现 `InvalidCredentialsException`；
- 验证结束后通过 `Ctrl+C` 优雅关闭。

对照修复前的相同启动方式，服务一启动就会由 `scheduling-1` 线程调用 `AliyunOSSClient.keepLive` 并输出完整异常栈；修复后该任务 Bean 默认不存在。

## 运维说明

只有在 OSS AccessKey 已正确注入、并且确实需要定时访问以维持网络链路时，才设置：

```text
OSS_KEEP_LIVE_ENABLED=true
```

该开关不代替 `OSS_ACCESS_KEY_ID` 和 `OSS_ACCESS_KEY_SECRET`，启用保活前仍必须配置有效凭证。

## 提交范围

- `AliyunOSSClientKeepLiveTask`；
- `middleware-server/src/main/resources/application.yaml`；
- `docker-compose.yml`；
- `README.md`；
- `AliyunOSSClientKeepLiveTaskTest`；
- 本说明文档。
