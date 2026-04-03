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

## Security Notes

1. Do not keep default admin credentials in production.
2. Use secure secrets for all JWT and datasource settings.
3. Restrict database permissions for the runtime account.