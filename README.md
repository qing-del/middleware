# 个人 SaaS 中台项目

基于 Spring Boot 4.x + JDK 21 的多模块中台服务，提供笔记（Markdown）管理、图片对象存储、标签/主题管理、审核工作流、邮件通知，以及基于 Redis Streams 的异步音频生成等能力。

接口分为 **管理端 (`/admin/**`)** 与 **用户端 (`/user/**`)** 两套，分别通过 JWT 拦截器进行鉴权。

---

## 一、环境要求

1. **JDK 21**（父 POM 强制要求）
2. **Maven 3.8+**
3. **MySQL 8.x**，使用 `static/database/createDatabase.sql` 初始化库表
4. **Redis**（音频任务队列依赖 Redis Streams）
5. **阿里云 OSS** 账号及 AccessKey（用于图片对象存储）
6. **SMTP 邮箱**（用于账号激活、管理员群发邮件）
7. **Python FastAPI 音频引擎**（音频功能可选，详见下文 §6）

---

## 二、配置阿里云 OSS 环境变量

OSS 凭证从环境变量读取，**不要写入配置文件**。

Windows（cmd）临时设置：

```cmd
set OSS_ACCESS_KEY_ID=yourAccessKeyId
set OSS_ACCESS_KEY_SECRET=yourAccessKeySecret
```

Windows 永久持久化：

```cmd
setx OSS_ACCESS_KEY_ID "%OSS_ACCESS_KEY_ID%"
setx OSS_ACCESS_KEY_SECRET "%OSS_ACCESS_KEY_SECRET%"
```

验证：

```cmd
echo %OSS_ACCESS_KEY_ID%
echo %OSS_ACCESS_KEY_SECRET%
```

---

## 三、应用配置项

至少需要在运行时配置以下值（生产环境通过环境变量或外部配置覆盖）：

| 配置项 | 说明 |
| --- | --- |
| `jacolp.datasource.host` | MySQL 主机 |
| `jacolp.datasource.port` | MySQL 端口 |
| `jacolp.datasource.database` | 库名 |
| `jacolp.datasource.username` | 数据库账号 |
| `jacolp.datasource.password` | 数据库密码 |
| `jacolp.admin.username` | 内置创建者账号（用于初始化） |
| `jacolp.admin.password` | 内置创建者密码 |
| `jwt.*` | JWT 密钥、过期时长等 |

本地开发可直接使用 `middleware-server/src/main/resources/application-dev.yaml` 中的默认值。

---

## 四、创建者账号初始化（重要）

应用启动时 `DataInitializer` 会自动执行，保证存在一个有效的「创建者」账号：

1. 优先按 `id = 1` 查找用户；
2. 找不到则按 `jacolp.admin.username` 查找；
3. 仍找不到时，按配置插入一条创建者账号（启用状态）；
4. 找到时，强制将其更新为：
   - 配置中的用户名（若与库内不同）
   - 配置中的密码（若变更则重新加密）
   - 创建者角色
   - 启用状态

> 因此修改 `jacolp.admin.username` 或 `jacolp.admin.password` 后再次启动，会同步覆盖数据库中已有的创建者账号信息，请谨慎修改。

---

## 五、构建与启动

项目根目录下：

```bash
mvn clean install
```

启动服务端模块：

```bash
mvn -pl middleware-server spring-boot:run
```

启动成功后：

- Knife4j 接口文档：<http://localhost:8080/doc.html>
- OpenAPI JSON：`/v3/api-docs/admin 端接口`、`/v3/api-docs/user 端接口`

---

## 六、模块结构

| 模块 | 用途 |
| --- | --- |
| `middleware-server` | Spring Boot 主应用：Controller / Service / Mapper / Aspect / 定时任务 |
| `middleware-pojo` | 实体类、DTO、VO 等领域对象 |
| `middleware-common` | 通用工具、异常、常量、`Result` / `PageResult`、枚举 |
| `aliyun-oss-spring-boot-starter` | 阿里云 OSS 自动装配 Starter |
| `flexmark-jacolp-starter` | 基于 flexmark 的 Markdown → HTML 处理 Starter |

---

## 七、用户端接口概览（`/user/**`）

所有用户端接口需通过 `Authorization: Bearer <token>` 携带 JWT 鉴权（登录、注册、激活、邮件激活等公开接口除外）。

### 7.1 用户认证（`/user/user`）
- 用户注册、登录、退出登录
- 通过邮件激活链接或 6 位激活码激活账号
- 查询/更新当前用户信息（昵称、邮箱、密码）
- 软删除当前账户
- 获取用户概览信息（不含资源统计）

### 7.2 邮箱管理（`/user/email`）
- 获取邮箱地址与激活状态
- 重新发送激活邮件

### 7.3 主题管理（`/user/topic`）
- 条件分页查询主题（自己 + 他人已审核）
- 新增 / 修改（排序）/ 批量删除主题
- 发起主题审核申请
- 主题数量统计

### 7.4 标签管理（`/user/tag`）
- 查询当前用户标签列表
- 条件分页查询标签（自己 + 他人已审核）
- 新增 / 批量新增 / 批量删除标签
- 绑定 / 解绑标签与笔记
- 发起标签审核申请
- 标签数量统计

### 7.5 图片管理（`/user/image`）
- 条件分页查询图片（自己 + 他人已公开）
- 上传 / 查看详情 / 删除图片
- 修改图片元信息（名称、所属主题）
- 替换图片源文件
- 发起图片审核申请
- 图片数量统计

### 7.6 笔记管理（`/user/note`）
- 上传 Markdown 笔记，自动扫描标签、图片、双链引用并建立映射，返回缺失项
- 修改笔记源文件：先生成 Diff 与临时版本，再通过「确认/取消」接口决定是否覆盖
- 修改笔记元信息（描述、主题）
- 条件分页查询、全文搜索、详情查看
- Markdown → HTML 转换、删除转换缓存
- 设置发布 / 下架状态（要求审核通过且关联完整）
- 查询变更 Diff 详情、笔记 Markdown 源内容、转换后的 HTML 内容
- 发起笔记审核申请、删除笔记
- 笔记数量统计（总数 / 公开 / 已通过）

### 7.7 笔记关联管理（`/user/note/relation`）
- 查询笔记的标签、图片、双链笔记映射
- 绑定 / 解绑标签、图片、双链笔记映射
- 校验笔记关联完整性，自动流转笔记状态

### 7.8 音频生成（`/user/audio`）
- 提交文本转语音任务（指定语速、背景音类型、噪音因子），异步执行
- 查询任务状态与结果链接
- 分页查询当前用户的任务列表

---

## 八、管理端接口概览（`/admin/**`）

所有管理端接口需要管理员或创建者角色 JWT。

### 8.1 用户管理（`/admin/user`）
- 管理员登录、退出登录
- 获取当前用户信息、按 ID 查询用户、分页查询用户列表
- 新增 / 修改 / 批量删除用户
- 封禁 / 解封账号
- 修改用户信息时需通过 `@RequireSuperiorRole` 角色层级校验

### 8.2 主题管理（`/admin/topic`）
- 分页查询主题、按 ID 查询主题详情
- 批量删除主题（存在引用即拒绝）

### 8.3 标签管理（`/admin/tag`）
- 分页查询标签、修改标签名称、批量删除标签

### 8.4 图片管理（`/admin/image`）
- 分页查询图片、修改图片元信息
- 设置图片公开状态、迁移图片到云存储
- 查询图片关联的笔记、批量删除图片（存在引用即拒绝）
- 审核图片（已合并到统一审核流程，开发时不建议直接调用）

### 8.5 笔记管理（`/admin/note`）
- 分页查询笔记、查询笔记详情
- 修改笔记元信息、批量删除笔记
- 转换 Markdown 为 HTML、删除转换缓存
- 获取笔记 Markdown 源内容、打开他人笔记
- 强制设置笔记状态（预留接口，当前未实现）

### 8.6 审核管理（`/admin/audit`）
- 分页查询笔记 / 主题-标签 / 图片审核记录（按状态、申请人筛选）
- 批量审核笔记 / 主题-标签 / 图片（通过或拒绝，需附拒绝原因）

### 8.7 邮件管理（`/admin/email`）
- 发送自定义邮件（单用户或按角色群发，支持纯文本与 HTML）

### 8.8 音频任务（`/admin/audio`）
- 分页查询全平台音频任务列表

---

## 九、音频生成业务架构

音频生成采用 **Java（生产者）→ MySQL（任务兜底）→ Redis Streams（队列派发）→ Python FastAPI（消费者/音频引擎）→ Nginx（资源代理）** 的异步链路。

### 9.1 流程概览

1. 用户调用 `POST /user/audio/generate` 提交文本、语速、背景音类型、噪音因子。
2. Java 端落库 `audio_tasks`（状态 `0-待处理`），并将精简任务（`taskId / userId / speed / noiseType / noiseFactor / text`）推入 Redis Stream `stream:audio:tasks`。
3. Python 消费者从 Consumer Group 读取任务：
   - 回调 `POST /admin/audio/callback/start` 通知 Java 任务进入处理中（状态 `1`）；
   - 调用 Edge-TTS 生成人声 → 通过 FFmpeg `volume` 滤镜挂载 `noiseFactor` 后与背景噪音混音 → 输出 `final_{taskId}.mp3`；
   - 将文件移至 Nginx 暴露目录（如 `/data/drills/audio/`）；
   - 回调 `POST /admin/audio/callback/finish` 提交结果（成功 `2` / 失败 `-1`）；
   - 若 Java 响应 `data == false`，Python **必须立即 `os.remove` 删除本地文件**回收空间。
4. 用户轮询 `GET /user/audio/status/{taskId}` 获取最终状态与下载链接。

### 9.2 背景音类型字典

| 标识符 (`noiseType`) | 展示名称 | FFmpeg 底层滤波参数 |
| --- | --- | --- |
| `PURE` | 纯净（无背景音） | （空，跳过混音） |
| `WHITE_NOISE` | 白噪音 | `anoisesrc=c=white:r=44100` |
| `PINK_NOISE` | 粉色噪音 | `anoisesrc=c=pink:r=44100` |
| `BROWN_NOISE` | 褐噪音 | `anoisesrc=c=brown:r=44100` |
| `CAFE` | 咖啡厅 | `anoisesrc=c=pink:r=44100,lowpass=f=2000` |
| `AIRPORT` | 飞机场 | `anoisesrc=c=brown:r=44100,lowpass=f=500,tremolo=f=0.5:d=0.3` |
| `SUBWAY` | 地铁车厢 | `anoisesrc=c=brown:r=44100,lowpass=f=300,tremolo=f=4.0:d=0.8` |

### 9.3 参数范围

| 参数 | 类型 | 范围 | 默认 / 说明 |
| --- | --- | --- | --- |
| `speed` | Float | `0.5 ~ 3.0` | 常用：1.0 / 1.25 / 1.5 / 2.0；Python 端会转换为 Edge-TTS 的 `--rate` 值 |
| `noiseFactor` | Float | `0.0 ~ 2.0` | 默认 `0.5`，建议 0.2~0.5，避免盖过人声 |

### 9.4 任务状态

`0`-排队中、`1`-合成中、`2`-已完成、`-1`-失败。

完整接口规范见 [音频生成业务接口规范](static/document/音频生成业务接口规范.md)。

---

## 十、核心架构要点

### 10.1 路由与鉴权
- `/admin/**` 经 `JwtTokenAdminInterceptor`；`/user/**` 经 `JwtTokenUserInterceptor`。
- 拦截器解析 token → `JwtUtil.parseJWT` 校验 → 用户 ID 写入 `BaseContext`（ThreadLocal），`afterCompletion` 中清理。

### 10.2 角色层级
- 层级：CREATOR(1) > ADMIN(2) > USER(3) > LEVEL_1_VIP(4)
- `@RequireSuperiorRole` 由 `SuperiorRoleAspect` 拦截，规则：操作者的 `roleId` 必须严格小于目标用户的 `roleId`。

### 10.3 审核工作流
`isPass` 字段：`0`-待审核、`1`-已通过、`2`-已拒绝。

### 10.4 软删除
- `biz_note` 及其映射表（`biz_note_tag_mapping`、`biz_note_image_mapping`、`biz_note_each_mapping`）使用 `is_deleted`。
- `biz_topic`、`biz_tag`、`biz_image` 为物理删除。
- `sys_user` 通过 `status` 控制账户状态（`0`-禁用、`1`-正常、`2`-未激活）。

### 10.5 存储配额（`@StorageHandler`）
- UPLOAD / MODIFY：先做一致性查询（笔记 + 图片实际占用），再配额校验，业务执行后写回。
- DELETE / BATCH_DELETE：业务代码通过 `StorageUpdateContext`（ThreadLocal）传递删除的字节数，由切面统一更新。
- 使用用户级 `ReentrantLock`（300ms 超时），`CleanAspectLockTask` 周期清理空闲锁。

### 10.6 关键 AOP 注解

| 注解 | 用途 |
| --- | --- |
| `@StorageHandler(operationType)` | 存储配额校验与更新（UPLOAD / MODIFY / DELETE / BATCH_DELETE） |
| `@ImageLimit` | 图片大小（5MB）、格式（jpg/jpeg/png/gif/webp/svg/bmp）校验 |
| `@NoteFileLimit` | 笔记文件大小（300KB）、格式（`.md`）校验 |
| `@RequireSuperiorRole` | 角色层级校验 |
| `@RequireValidRole` | 角色存在性与数据完整性校验 |

### 10.7 定时任务

| 任务 | 默认周期 | 用途 |
| --- | --- | --- |
| `AliyunOSSClientKeepLiveTask` | 45 s | 阿里云 OSS 客户端保活 |
| `ImageDeleteTask` | 60 min | 处理图片删除死信队列 |
| `NoteCleanupTask` | 24 h | 物理清理已软删除的笔记映射记录 |
| `CleanAspectLockTask` | 60 min | 清理存储切面中的空闲用户锁 |

---

## 十一、详细接口文档

`static/document/` 目录提供按端拆分的详细接口规范：

- [`admin 端接口.md`](static/document/admin%20端接口.md)
- [`user 端接口.md`](static/document/user%20端接口.md)
- [`音频生成业务接口规范.md`](static/document/音频生成业务接口规范.md)

---

## 十二、安全建议

1. 生产环境**严禁**使用默认管理员账号密码（`jacolp.admin.username` / `jacolp.admin.password`）。
2. JWT 密钥、数据库密码、SMTP 凭证、OSS AccessKey 均通过环境变量或密钥管理服务下发，避免明文入库。
3. 数据库运行账号按最小权限原则授权，禁用 DROP / GRANT 等高危权限。
4. 音频回调接口（`/admin/audio/callback/**`）仅供内网调用，需在网关 / 防火墙层面限制来源。
