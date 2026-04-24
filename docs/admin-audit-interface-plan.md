# Admin 端审核接口开发计划书

## 0. 背景与目标

为了统一管理系统的 UGC 内容（主题、标签、图片、笔记），需要开发管理端的审核接口，以完成审核业务闭环。系统目前包含三种审核记录：元数据（主题/标签）、图片、笔记。

本阶段目标：
1. 查看主题/标签审核列表（条件查询）
2. 查看图片审核列表（条件查询）
3. 查看笔记审核列表（条件查询）
4. 批量通过/拒绝审核请求（支持统一备注拒绝原因）

---

## 1. 现状基线与前提

### 1.1 涉及的数据库表
审核表：
- `biz_meta_audit_record` (元数据：主题和标签审核)
- `biz_image_audit_record` (图片审核)
- `biz_note_audit_record` (笔记审核)

被审核的主业务表：
- `biz_topic` / `biz_tag`
- `biz_image`
- `biz_note` 等

### 1.2 关于排序字段的说明
> **【重要提醒】**：需求中明确提出“这里默认使用 `update_time` 降序排序”。
目前 `TableDesign.md` 的三张审核表结构中，仅定义了 `create_time` 和 `review_time`，**缺失 `update_time` 字段**。
**解决建议**：在执行建表/更新 SQL 时，需为这三张审核表补充 `update_time` 字段（`update_time datetime 默认 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`）。
本计划的查询接口设计默认按照 `update_time DESC` 进行排序。

### 1.3 审核核心流转状态
- `status`: `0` 待审核，`1` 已通过，`2` 已拒绝
- `is_pass`: 各个业务表冗余的审核状态，与审核表状态保持对应。

---

## 2. 接口设计

统一前缀建议：`/admin/audit`

### 2.1 查询审核列表接口

为三种审核数据提供独立的查询接口，方便前端分类展示与条件筛选。

#### 2.1.1 查看主题/标签审核列表
- **方法与路径**：`POST /admin/audit/meta/list`
- **请求体 (MetaAuditListDTO)**：
  - `applyType`：可选，1 (主题) 或 2 (标签)
  - `status`：可选，0 (待审), 1 (通过), 2 (拒绝)
  - `applicantUserId`：可选，申请人 ID
  - `pageNum`、`pageSize`：分页参数
- **排序规则**：`update_time DESC`
- **返回字段**：审核记录详情，建议连表或代码组装带出主题/标签的名称内容（`topic_name` / `tag_name`）及申请人信息。

#### 2.1.2 查看图片审核列表
- **方法与路径**：`POST /admin/audit/image/list`
- **请求体 (ImageAuditListDTO)**：
  - `status`：可选
  - `applicantUserId`：可选
  - `pageNum`、`pageSize`
- **排序规则**：`update_time DESC`
- **返回字段**：审核记录详情，需带出图片的 `oss_url` 等以便前端展示预览，以及申请人信息。

#### 2.1.3 查看笔记审核列表
- **方法与路径**：`POST /admin/audit/note/list`
- **请求体 (NoteAuditListDTO)**：
  - `status`：可选
  - `applicantUserId`：可选
  - `pageNum`、`pageSize`
- **排序规则**：`update_time DESC`
- **返回字段**：审核记录详情，需带出笔记的 `title`，以及申请人信息。

---

### 2.2 批量审核操作接口

对于所有的审核操作，统一使用批量处理接口。若前端页面提供“单条审核”按钮，也可通过封装为单元素数组的方式调用此接口。

#### 2.2.1 批量审核主题/标签
- **方法与路径**：`PUT /admin/audit/meta/review/batch`
- **请求体 (AuditBatchReviewDTO)**：
  - `ids`：必填，List 集合，代表需要审核的 ID 列表。
  - `status`：必填，1 (通过) 或 2 (拒绝)
  - `rejectReason`：可选，批量拒绝原因。当 `status=2` 且未传该字段时，系统默认提供文案：“管理员拒绝了你的申请”。

#### 2.2.2 批量审核图片
- **方法与路径**：`PUT /admin/audit/image/review/batch`
- **请求参数**：同上

#### 2.2.3 批量审核笔记
- **方法与路径**：`PUT /admin/audit/note/review/batch`
- **请求参数**：同上

**【处理逻辑说明（通用）】**：
1. 取出所有有效且状态为 `0 (待审核)` 的 ID 记录进行过滤。
2. 批量更新审核表状态，补充 `reviewer_user_id`（当前管理员 ID）和 `review_time`。
   - 若 `status=2`，写入 `reject_reason`。如果前端未传 `rejectReason`，默认填入“管理员拒绝了你的申请”。
3. 提取所有关联的 `target_id / image_id / note_id`，批量更新业务主表的 `is_pass` 状态。
4. **事务关联处理**：整个批量更新审核表与业务主表状态必须在同一事务中进行，确保数据一致性。
5. **返回结果**：返回成功处理的数量（便于前端感知过滤掉了多少无效/已审 ID）。

---

## 3. 代码落地规划

### 3.1 DTO / VO 规划 (middleware-pojo)

- **查询入参**：
  - `MetaAuditListDTO`
  - `ImageAuditListDTO`
  - `NoteAuditListDTO`
- **审核入参**：
  - `AuditBatchReviewDTO`（批量审核，可作为所有审核操作的通用 DTO）
- **返回视图**：
  - `MetaAuditVO`
  - `ImageAuditVO`
  - `NoteAuditVO`
  - *(VO中需要包含额外的冗余字段，如申请人账号、业务关联的名称、图片URL等)*

### 3.2 Service 层规划 (middleware-server)

创建独立的 `AuditService` 或者按领域拆分入对应的业务 Service（考虑到后续可能演进，建议按领域聚合到 `AuditService` 中）。
核心实现中需要注意：
- Mapper 联合查询或内存组装（考虑到 Admin 端 QPS 不高，可使用内存 `in` 查询组装关联的 User、Topic、Image 信息，避免过于复杂的 `JOIN`）。
- **事务 (`@Transactional`)**：所有的 `review/batch` 动作必须加事务保障审核表状态与业务主表状态同时更新成功或回滚。

### 3.3 异常字典建议

- `AUDIT_RECORD_NOT_FOUND`: "审核记录不存在"
- `AUDIT_STATUS_INVALID`: "无效的审核状态"
- `AUDIT_REJECT_REASON_DEFAULT`: "管理员拒绝了你的申请"

---

## 4. 验收标准

1. **结构变更验收**：三张审核表需补充 `update_time` 字段，且能够按照该字段降序返回分页列表。
2. **列表筛选验收**：不同状态（待审/通过/拒绝）及条件（申请人等）下能准确过滤出审核列表，并正确带出关联的详情内容。
3. **批量审核验收**：传入多个 ID 时，能够安全跳过非 `0` 状态的数据，只处理待审数据，且操作处于同一事务闭环中。审核通过/拒绝后，不仅审核表自身状态变更，相关的原业务表（如 `biz_topic`）状态也必须同步变更。
4. **默认拒绝理由验证**：当执行拒绝操作且未传入 `rejectReason` 时，数据库应正确写入“管理员拒绝了你的申请”。
