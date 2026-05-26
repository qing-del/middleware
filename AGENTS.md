# AGENTS.md

此文件为 Codex (Codex.ai/code) 在本代码库中工作时提供指导。

## 构建与运行

```bash
# 构建所有模块
mvn clean install

# 运行服务器
mvn -pl middleware-server spring-boot:run
```

## 前置条件

- JDK 21
- Maven 3.8+
- MySQL (使用 `init/createDatabase.sql` 初始化)
- 通过环境变量配置阿里云OSS凭证：
  - `OSS_ACCESS_KEY_ID`
  - `OSS_ACCESS_KEY_SECRET`

## 项目结构

### 多模块 Maven 布局

| 模块 | 用途 |
|--------|---------|
| `middleware-server` | Spring Boot 应用程序 (Controllers, Services, Mappers, Aspects, Tasks) |
| `middleware-pojo` | 实体类, DTOs, VOs, Domain 对象 |
| `middleware-common` | 工具类, 异常类, 常量, Result/PageResult, 枚举 |
| `aliyun-oss-spring-boot-starter` | 阿里云OSS集成 |
| `flexmark-jacolp-starter` | Markdown 处理 |

### 三层架构

```
Controller (com.jacolp.controller.admin / user)
  → Service (com.jacolp.service / com.jacolp.service.impl)
    → Mapper (com.jacolp.mapper + MyBatis XML)
```

### Service 层分工

管理端和用户端业务逻辑分离为两套 Service：

- `XxxService` — 管理端/共享业务逻辑（如 `ImageService`、`NoteService`）
- `UserXxxService` — 用户端专属业务逻辑（如 `UserImageService`、`UserNoteService`）

当前 Service 接口：`UserService`, `TopicService`, `TagService`, `ImageService`, `NoteService`, `UserImageService`, `UserTagService`, `UserNoteService`, `AuditService`

## 关键架构模式

### 路由分割

- `/admin/**` — 管理端接口 (需要 `JwtTokenAdminInterceptor`)
- `/user/**` — 用户端接口 (需要 `JwtTokenUserInterceptor`)
- 拦截器排除登录/注册接口的认证检查

### 认证流程

1. JWT 拦截器从请求头提取令牌
2. 通过 `JwtUtil.parseJWT()` 验证令牌
3. 用户ID存储在 `BaseContext` (ThreadLocal) 中
4. 在 `afterCompletion` 中调用 `BaseContext.remove()`

### 角色层次

- `RequireSuperiorRole` 注解触发 `SuperiorRoleAspect`
- 角色层级：CREATOR(1L) > ADMIN(2L) > USER(3L) > LEVEL_1_VIP(4L)
- 只有创建者 (roleId=1) 或管理员 (roleId=2) 可以修改低权限用户
- 操作规则：修改者的 roleId 必须严格小于目标的 roleId

### 审核工作流 (isPass 字段)

- `0` — 待审核
- `1` — 已通过
- `2` — 已拒绝

### 软删除模式

- `biz_note` 及其映射表 (`biz_note_tag_mapping`, `biz_note_image_mapping`, `biz_note_each_mapping`) 使用 `is_deleted` 标志 (0=正常, 1=已删除)
- `biz_topic`、`biz_tag`、`biz_image` **无** `is_deleted` 字段，删除为物理删除
- `sys_user` 使用 `status` 字段控制账户状态 (0=禁用, 1=正常, 2=未激活)

### AOP 切面与注解

| 注解 | 切面 | 用途 |
|------|------|------|
| `@StorageHandler(operationType)` | `StorageHandlerAspect` @Order(2) | 存储配额校验与更新，支持 UPLOAD/MODIFY/DELETE/BATCH_DELETE 四种操作类型 |
| `@ImageLimit` | `ImageLimitAspect` @Order(1) | 校验图片文件大小(5MB)、格式(jpg/jpeg/png/gif/webp/svg/bmp)、非空 |
| `@NoteFileLimit` | `NoteSizeLimitAspect` @Order(1) | 校验笔记文件大小(300KB)、格式(.md) |
| `@RequireSuperiorRole` | `SuperiorRoleAspect` | 角色层级校验 |
| `@RequireValidRole` | `RoleValidationAspect` | 角色存在性与数据完整性校验 |

### 存储配额机制 (`@StorageHandler`)

- UPLOAD/MODIFY：先查 note+image 两表算实际使用量（一致性检查），再配额校验，业务操作后基于实际值直接运算写回
- DELETE：业务代码通过 `StorageUpdateContext` ThreadLocal 传递被删文件大小，切面在业务执行后从用户表取缓存值直接减去文件大小写回
- BATCH_DELETE：通过 ThreadLocal 传递 `Map<userId, deltaBytes>`，批量加锁→批量查询→直接运算→批量更新
- 使用用户级 `ReentrantLock`（ConcurrentHashMap），锁超时 300ms，`CleanAspectLockTask` 定时清理空闲锁

### ThreadLocal 上下文

| 类 | 用途 | 生命周期 |
|----|------|----------|
| `BaseContext` | 当前登录用户ID | 拦截器设置，afterCompletion 清除 |
| `StorageUpdateContext` | 业务代码向切面传递存储变更数据 (`Map<Long, Long>`) | 切面 finally 中清除 |
| `NoteImageResolveContext` | 笔记图片解析时的当前笔记ID | 业务方法内清除 |

### 用户端统计接口

各资源独立的统计端点，前端概览页并行调用：

| 端点 | 返回类型 | 统计内容 |
|------|----------|----------|
| `GET /user/user/overview` | `UserOverviewVO` | 用户基本信息（不含资源统计） |
| `GET /user/topic/stats` | `TopicStatsVO` | 主题总数 |
| `GET /user/tag/stats` | `TagStatsVO` | 标签总数 |
| `GET /user/image/stats` | `ImageStatsVO` | 图片总数 |
| `GET /user/note/stats` | `NoteStatsVO` | 笔记总数、公开笔记数、已通过审核笔记数 |

### 定时任务

| 任务 | 默认周期 | 用途 |
|------|----------|------|
| `AliyunOSSClientKeepLiveTask` | 45s | 保活阿里云 OSS 客户端 |
| `ImageDeleteTask` | 60min | 处理图片删除死信队列 |
| `NoteCleanupTask` | 24h | 物理删除软删除的笔记映射记录 |
| `CleanAspectLockTask` | 60min | 清理 StorageHandlerAspect 中的空闲用户锁 |

## OpenAPI 文档

- Knife4j UI 可通过 `/doc.html` 访问
- 文档按包分组: `admin` 和 `user` 组
- 控制器使用 `@Tag`, `@Operation`, `@Schema` 注解标注API元数据
- Controller Bean 命名约定：`@RestController("User-XxxController")` / `@RestController("Admin-XxxController")`

## 关键实体字段

| 实体 | 重要字段 | 软删除 |
|------|----------|--------|
| `NoteEntity` | `isPublished`, `isMissingInfo`, `isPass`, `isDeleted`, `storageType` | 是 |
| `ImageEntity` | `isPublic`, `isPass`, `storageType` | 否(物理删除) |
| `TagEntity` | `isPass` | 否(物理删除) |
| `TopicEntity` | `isPass` | 否(物理删除) |

## Mapper 查询约定

- 简单查询（如 COUNT、单表按 ID/UserId 查询）使用 `@Select` 注解
- 复杂查询（多条件、动态 SQL、JOIN）使用 MyBatis XML 映射文件
- 当前 Mapper 接口：`UserMapper`, `RoleMapper`, `TopicMapper`, `TagMapper`, `ImageMapper`, `NoteMapper`, `NoteContextMapper`, `NoteConvertMapper`, `NoteChangeDiffMapper`, `NoteTagMappingMapper`, `NoteImageMappingMapper`, `NoteEachMappingMapper`, `ImageAuditMapper`, `MetaAuditMapper`, `NoteAuditMapper`, `ImageDeleteDeadLetterMapper`

## 配置

- 主配置: `middleware-server/src/main/resources/application.yaml`
- 开发环境覆盖: `application-dev.yaml`
- 环境变量占位符: `${jacolp.datasource.*}`, `${jwt.*}`, 等。
