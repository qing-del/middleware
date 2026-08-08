# 05：修复容器健康检查永久失败

## 问题

`docker-compose.yml` 原先通过容器内的 `curl` 请求 `/actuator/health`，但运行镜像是 `eclipse-temurin:21-jre-alpine`：

- 镜像没有安装 `curl`，健康检查命令无法执行；
- 服务端没有引入 Actuator，目标端点不存在；
- 即便直接使用聚合健康端点，数据库、Redis 或 RabbitMQ 短暂故障也可能把仍可运行的进程误判为死亡。

因此 Compose 中的 `depends_on: condition: service_healthy` 无法可靠成立，容器也可能被错误重启。

## 修复

1. `middleware-server` 引入 `spring-boot-starter-actuator`。
2. 只暴露 `health` 端点，禁止返回组件明细，减少运行环境信息泄露。
3. 开启健康探针，Compose 改为请求 `/actuator/health/liveness`。
4. 健康检查使用 Alpine BusyBox 自带的 `wget`，不额外扩大镜像。

存活探针只判断 JVM/Spring 应用是否仍然存活。数据库、缓存或队列的可用性应由 readiness 或独立监控判断，不应触发进程重启循环。

## 自动化验证

新增 `ContainerHealthcheckContractTest`，断言：

- 服务端确实引入 Actuator；
- 仅暴露健康端点，并启用探针、隐藏详情；
- Compose 使用 `wget --spider` 请求 liveness；
- Docker 运行镜像仍为 Alpine，且不再依赖缺失的 `curl`。

执行命令：

```text
mvn -B -Denforcer.skip=true -pl middleware-server -am \
  -Dtest=ContainerHealthcheckContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，26 个 Reactor 项目全部 `SUCCESS`，`BUILD SUCCESS`。

完整打包验证：

```text
mvn -B -Denforcer.skip=true -pl middleware-server -am -DskipTests package
```

结果：26 个 Reactor 项目全部 `SUCCESS`，生成包含 Actuator 的可执行 JAR。

Compose 语法验证：

```text
docker compose config --quiet
```

结果：退出码 `0`。未提供本地环境变量时 Docker Compose 会输出变量为空的警告，但配置可正常解析。

## 真实运行验证

使用 `home` 配置启动打包后的服务，然后请求实际端点：

```text
GET http://localhost:8080/actuator/health/liveness
HTTP 200
{"status":"UP"}
```

服务在验证后通过 `Ctrl+C` 正常关闭，Tomcat、RabbitMQ 监听容器和 HikariCP 均执行了优雅停止。

## 提交范围

- `middleware-server/pom.xml`；
- `middleware-server/src/main/resources/application.yaml`；
- `docker-compose.yml`；
- `ContainerHealthcheckContractTest`；
- 本说明文档。
