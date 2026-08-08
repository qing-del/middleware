# 04：修复审核迁移将零状态记录全部取消

## 问题

`20260807_sync_audit_submit_cancel.sql` 原先执行：

```sql
UPDATE biz_tag_audit_record SET status = 5 WHERE status = 0;
UPDATE biz_image_audit_record SET status = 5 WHERE status = 0;
```

旧 `status=0` 只表示迁移前的 `WAITING`，不能单凭该值证明申请已经取消。如果所属标签或图片仍为 `audit_status=1-AUDITING`，直接改成 `5-CANCELLED` 会让真实待审申请从管理端消失。脚本随后还删除 `sys_async_command_state`，进一步丢失排查证据。

## 修复

### 1. 先保存可恢复账本

新增 `audit_zero_status_migration_backup`，每条待分类记录保存：

- 类型、审核记录 ID、申请人、目标对象 ID；
- 原状态与目标对象当时的 `audit_status`；
- 最终状态和明确的判定原因；
- 原更新时间和迁移时间。

状态更新只允许从这张账本读取 `resolved_status`，因此每条变更都有依据，并可用 `previous_status` 恢复。

### 2. 根据所属对象分类

对标签和图片使用相同规则：

| 条件 | 结果 | 原因 |
| --- | --- | --- |
| 所属对象为 `AUDITING(1)`、不存在已有 `status=1` 记录，且本条是该对象最新的零状态记录 | `1-AUDITING` | `RESTORED_ACTIVE_APPLICATION` |
| 所属对象不存在 | `5-CANCELLED` | `TARGET_MISSING` |
| 所属对象不在审核中 | `5-CANCELLED` | `TARGET_NOT_AUDITING` |
| 已有活动审核记录 | `5-CANCELLED` | `ACTIVE_APPLICATION_EXISTS` |
| 同一对象更旧的零状态重复记录 | `5-CANCELLED` | `OLDER_WAITING_DUPLICATE` |

这样既恢复真实活动申请，又保证迁移不会为同一对象新造出多个待审记录。

### 3. 保留异步命令证据

不再删除 `sys_async_command_state`。如果表存在，只把表注释标记为归档状态；后续确认稳定后再通过单独、可审查的清理迁移删除。

## 自动化验证

`MigrationScriptTest` 新增审核迁移断言，覆盖：

- 备份表和恢复字段必须存在；
- 标签、图片必须关联所属对象的 `audit_status`；
- 必须排除已有活动申请并选择最新候选；
- 业务表只能从备份账本写入最终状态；
- 禁止恢复旧的无条件 `status=0 -> 5`；
- 禁止直接删除 `sys_async_command_state`。

执行命令：

```text
mvn -B -Denforcer.skip=true -pl middleware-server -am \
  -Dtest=MigrationScriptTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`；26 个 Reactor 项目全部 `SUCCESS`，`BUILD SUCCESS`。

本机使用 JDK 23，项目 Enforcer 要求 JDK 21，因此仅跳过 JDK 版本门禁；编译仍使用 Java 21 `release`。

## 部署后检查

```sql
SELECT target_type, resolved_status, resolution_reason, COUNT(*)
FROM audit_zero_status_migration_backup
GROUP BY target_type, resolved_status, resolution_reason
ORDER BY target_type, resolved_status, resolution_reason;

SELECT COUNT(*) AS unresolved_tag_rows
FROM biz_tag_audit_record
WHERE status = 0;

SELECT COUNT(*) AS unresolved_image_rows
FROM biz_image_audit_record
WHERE status = 0;
```

后两个计数应为 `0`。保留备份表至少一个完整发布周期。

如果旧版 `20260807` 已经在某个环境执行，无条件改写已经丢失了“此前是否为零状态”的信息；不要指望重跑新版脚本自动推断。应先从数据库快照或审计日志恢复原状态，再按本说明人工核对。

## 提交范围

- `static/database/migrations/20260807_sync_audit_submit_cancel.sql`；
- `MigrationScriptTest`；
- 本说明文档。
