# 01：修复音频迁移 `audio_size` 重复新增

## 问题

`20260519_audio_tasks.sql` 的当前建表定义已经包含 `audio_size`，而
`20260730_refactor_mq.sql` 又无条件新增同一列。按迁移目录顺序执行时会报
`Duplicate column name 'audio_size'`，从而中断可靠消息和审核投影相关表的创建。

## 修复

将 `20260730_refactor_mq.sql` 中的无条件 `ALTER TABLE ... ADD COLUMN` 改为基于
`information_schema.columns` 的条件执行：

- 旧环境没有 `audio_size` 时，仍执行 `ALTER TABLE` 以完成升级；
- 新建库、回放库或已升级环境已有该列时，执行无副作用的 `SELECT 1`。

这样保留了对既有数据库的兼容性，同时使当前迁移集可重复执行这一段升级。

## 验证

- 新增 `MigrationScriptTest`，同时读取初始建表脚本和重构迁移脚本，防止后续再次出现“建表已包含列、迁移又无条件新增”的回归。
- 测试断言列存在性判断覆盖当前数据库、目标表和目标列，并且动态 SQL 在 `PREPARE/EXECUTE` 前完成守卫判断。
- 已执行：

  ```text
  mvn -B -Denforcer.skip=true -pl middleware-server -am \
    -Dtest=MigrationScriptTest -Dsurefire.failIfNoSpecifiedTests=false test
  ```

  结果：`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，Reactor `BUILD SUCCESS`。

本机安装的是 JDK 23，而项目 Enforcer 要求 JDK 21，因此仅跳过 JDK 版本门禁；编译仍按项目配置的 Java 21 `release` 执行。

## 提交范围

- `static/database/migrations/20260730_refactor_mq.sql`
- `middleware-server/src/test/java/com/jacolp/middleware/migration/MigrationScriptTest.java`
- 本说明文档
