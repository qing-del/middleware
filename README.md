# Middleware Project Usage Guide

This project is a multi-module Spring Boot middleware service.

## Prerequisites

1. JDK 17+ (or the version required by your local Maven build).
2. Maven 3.8+.
3. MySQL running and initialized with the schema in `init/createDatabase.sql`.

## Configure Aliyun OSS Environment Variables

The OSS utility reads credentials from environment variables.

1. Set environment variables for the current terminal session:

```cmd
set OSS_ACCESS_KEY_ID=yourAccessKeyId
set OSS_ACCESS_KEY_SECRET=yourAccessKeySecret
```

2. Persist variables for future terminals (Windows):

```cmd
setx OSS_ACCESS_KEY_ID "%OSS_ACCESS_KEY_ID%"
setx OSS_ACCESS_KEY_SECRET "%OSS_ACCESS_KEY_SECRET%"
```

3. Verify variables:

```cmd
echo %OSS_ACCESS_KEY_ID%
echo %OSS_ACCESS_KEY_SECRET%
```

## Configure Application Properties

At minimum, ensure these values are available in your runtime configuration:

- `jacolp.datasource.host`
- `jacolp.datasource.port`
- `jacolp.datasource.database`
- `jacolp.datasource.username`
- `jacolp.datasource.password`
- `jacolp.admin.username`
- `jacolp.admin.password`

For local development, default values are provided in `middleware-server/src/main/resources/application-dev.yaml`.

## Creator Account Initialization (Important)

On application startup, `DataInitializer` runs automatically and ensures a Creator account exists and is valid.

Initialization logic:

1. Try to load user with `id = 1`.
2. If not found, try to load user by `jacolp.admin.username`.
3. If still not found, insert a new user as Creator (enabled status).
4. If found, force-update the user to:
	- use configured admin username (if different),
	- use configured admin password (re-encoded if changed),
	- use Creator role,
	- use enabled status.

Because of this behavior, changing `jacolp.admin.username` or `jacolp.admin.password` may modify the existing Creator/admin account data on next startup.

## Run

From the project root:

```bash
mvn clean install
```

Then start the server module:

```bash
mvn -pl middleware-server spring-boot:run
```

---

## User API Overview

All user endpoints are under `/user` and require JWT authentication via `Authorization: Bearer <token>` header.

### Topic API (`/user/topic`)
- 条件查询主题列表（自己 + 别人已审核的主题）
- 发起主题审核申请

### Tag API (`/user/tag`)
- 条件查询标签列表（自己 + 别人已审核的标签）
- 发起标签审核申请
- 查询/创建/删除自己的标签
- 绑定/解除标签与笔记或主题的关联

### Image API (`/user/image`)
- 条件查询图片列表（自己 + 别人已公开的图片）
- 发起图片审核申请
- 上传/查看/删除自己的图片

### Note API (`/user/note`)
- 条件查询笔记列表（自己 + 别人已发布的笔记）
- 发起笔记审核申请
- 创建/查询/更新/删除自己的笔记
- 全文搜索笔记

---

## Security Notes

1. Do not keep default admin credentials in production.
2. Use secure secrets for all JWT and datasource settings.
3. Restrict database permissions for the runtime account.