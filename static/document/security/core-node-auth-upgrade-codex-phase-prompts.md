# CORE NODE 授权升级：阶段 Codex Prompt

> 使用方式：每次只把一个阶段 Prompt 交给 Codex。  
> 所有阶段都只要求 **先规划，不直接实现**。  
> Codex 必须优先读取仓库内的授权升级方案文档，并结合当前代码实际情况输出实施计划。  
> **如果出现关键不确定项、方案与现状冲突、需要新增未约定设计时，立即停止并向我提问，不自行拍板。**
>
> 本轮范围：**仅后端**。  
> `middleware-open-api-agent` 可以提前创建，但只允许做**空模块/占位模块**，本轮不实现 Agent 业务接口。  
> **禁止修改 frontend/**，前端适配放到后续独立计划。

---

## Phase 0：现状核对与实施基线

```text
你现在工作在当前 middleware 仓库。

先在仓库内找到并完整阅读我放入项目的“授权升级方案”文档，然后结合当前代码实际情况，规划 Phase 0：授权升级实施基线。

本阶段只做分析和规划，不修改代码。

重点检查：
- 当前 Maven 多模块结构
- middleware-common-security
- middleware-common-web
- middleware-module-system
- middleware-server
- 当前 SecurityFilterChain
- LegacyJwtAuthenticationFilter
- JwtTokenSupport / JwtProperties
- TokenSessionService / RedisJwtTokenSessionService
- SecurityContextBridge / BaseContext / PermissionContext
- user/admin 登录、logout
- sys_user / sys_role
- static/database/createDatabase.sql
- static/database/migrations
- 现有鉴权相关测试

目标：
1. 对照授权升级方案，列出当前实现与目标之间的差异。
2. 明确哪些现有行为需要 characterization test 冻结。
3. 明确后续各阶段应该修改哪些模块、哪些模块不应该承担授权逻辑。
4. 给出后续实施顺序和依赖关系。
5. 不修改生产代码，不修改数据库，不修改前端。

输出：
- 当前鉴权调用链
- 当前关键类/文件清单
- 与授权升级方案的差异
- 建议补充的基线测试
- 后续阶段依赖关系
- 风险点

如果发现授权升级方案与当前代码存在关键冲突，或者无法确定某项设计，应立即停止，列出问题等我回复，不要自行决定。
```

---

## Phase 1：RBAC / OAuth Client 数据基础 + Agent 空模块占位

```text
请先读取仓库内的授权升级方案文档和 Phase 0 已确认的结论，然后规划 Phase 1。

本阶段只做规划，不修改代码。

目标：
1. 建立新授权体系所需的数据模型和持久化基础。
2. sys_role 增加 rank，后续角色等级不再依赖 role id。
3. sys_user 增加 grant_types。
4. 新增授权方案中约定的：
   - oauth2_registered_client
   - oauth2_authorization_consent
   - sys_permission
   - sys_role_perm
5. 规划对应 Entity / Mapper / XML / Repository / Service 边界。
6. 规划 Client、Role、Permission 的初始化数据。
7. 规划 module:operation 权限规则，以及 wildcard 权限的解析/展开方式。
8. 新增 middleware-open-api-agent 模块，但只能作为空模块占位：
   - 加入 Maven modules
   - 提供最小 pom
   - 可以有空 package / package-info / 占位说明
   - 不允许新增 Agent Controller、业务 API、权限实现
9. 不修改现有 user/admin 登录和 JWT 行为。
10. 不修改 frontend/。

数据库要求：
- 使用 static/database/migrations 新增向前迁移脚本
- 同步 static/database/createDatabase.sql
- 尽量保持 Spring Authorization Server RegisteredClient 模型兼容
- 避免同一配置存在两套真相

请输出：
- 精确文件改动计划
- DDL 设计
- 数据迁移和 seed 方案
- Java 分层设计
- middleware-open-api-agent 空模块结构
- 测试计划
- 实施顺序
- 回滚方案

如果授权升级方案中的字段、Client 配置、权限规则与现有代码无法直接兼容，先停止并向我提问，不要自行新增设计。
```

---

## Phase 2：RS256 / Token / Security Core

```text
请先读取仓库内授权升级方案文档，以及前面阶段已经确认的代码结构，然后规划 Phase 2：新 Security Core。

本阶段只做规划，不修改代码。

目标：
1. 在现有 middleware-common-security 内演进，不新建平行 security 模块。
2. 引入并规划 RS256 Access Token 能力。
3. 规划 RSA private/public key 加载。
4. 规划 JwtEncoder / JwtDecoder。
5. 按授权升级方案生成 Access Token claims：
   - iss
   - sub
   - aud
   - iat
   - exp
   - jti
   - client_id
   - grant_type
   - username
   - roles
   - scope
6. 规划 jti blacklist。
7. 规划 256-bit opaque refresh token 生成与安全存储/索引方式。
8. 规划 CurrentPrincipalAccessor，使现有 BaseContext / PermissionContext 可以逐步兼容新 JwtAuthenticationToken。
9. 保留 legacy JWT 作为过渡，不在本阶段删除。
10. 规划 JWK / public key 暴露所需基础设施。
11. 不修改 frontend/。
12. 不实现 Agent 业务接口。

重点检查：
- middleware-common-security
- middleware-common-web
- JwtTokenSupport
- JwtProperties
- LegacyJwtAuthenticationFilter
- SecurityContextBridge
- SecurityPrincipal / SecurityIdentity
- BaseContext / PermissionContext
- 当前 Redis Token Session 实现
- 当前 SecurityFilterChain

输出：
- 包结构/类设计
- 新旧 Security 共存方式
- RS256 key 配置设计
- JwtEncoder/JwtDecoder 设计
- blacklist / refresh token 设计
- SecurityContext 兼容方案
- 测试矩阵
- 实施顺序
- cutover 前置条件

如果 Spring Boot / Spring Security 当前版本的 API 与方案存在差异，必须先核对当前依赖和官方 API；仍存在关键不确定时立即停止并问我。
```

---

## Phase 3：USER / ADMIN 统一 Token、Refresh、Logout

```text
请先读取仓库内授权升级方案文档，以及 Phase 1、Phase 2 已确认的设计，然后规划 Phase 3。

本阶段只做规划，不修改代码。

目标：
1. 后端建立 Spring Authorization Server。
2. USER 和 ADMIN 统一通过授权体系签发 RS256 access token。
3. 统一 token endpoint。
4. 按授权升级方案实现 user/admin 对应的授权方式。
5. 规划 Client 校验：
   - client_id
   - client secret（如适用）
   - status
   - grant type
   - allowed_ips
6. 权限计算：
   Client allowed scopes ∩ Role permissions
7. 实现 access token + refresh token。
8. refresh token 必须 rotation：
   - 使用旧 refresh 成功后立即失效
   - 返回新的 access + refresh
9. logout 只注销当前 client + user session：
   - access jti blacklist
   - refresh 删除
   - session 删除
10. 现有 /user/user/login、/admin/user/login 可以暂时保留为后端 compatibility adapter，但不得继续自行签发旧 JWT。
11. 本阶段只改后端。
12. 禁止修改 frontend/。
13. 不实现 Agent 业务接口。

重点检查：
- system 模块现有 user/admin 登录 Controller / Service
- PasswordEncoder / BCrypt
- TokenSessionService
- Redis session
- SecurityFilterChain
- 现有登录返回结构
- 现有 admin/user 权限判定

输出：
- Authorization Server SecurityFilterChain 设计
- Resource Server SecurityFilterChain 演进方式
- extension grant / AuthenticationConverter / AuthenticationProvider 设计
- user/admin 登录调用链
- refresh rotation 数据结构
- logout/revoke 调用链
- legacy compatibility adapter 方案
- 精确文件计划
- 测试矩阵
- 上线切换和回滚策略

如果授权升级文档对 grant_type、client authentication 或旧接口兼容方式存在歧义，立即停止向我提问，不要自行决定。
```

---

## Phase 4：Authorization Code + PKCE + Consent

```text
请先读取仓库内授权升级方案文档，以及前面阶段已经实现/确认的授权基础，然后规划 Phase 4。

本阶段只做规划，不修改代码。

目标：
1. 为 CORE AGENT / 第三方授权建立 Authorization Code + PKCE 后端能力。
2. 使用 Spring Authorization Server 标准 authorization_code 流程。
3. code_challenge_method 首期只支持 S256。
4. Authorization Code 一次性使用，并按方案设置 TTL。
5. 使用 oauth2_authorization_consent 保存用户授权。
6. effective scope：
   Client scopes ∩ Role permissions ∩ User consent
7. 支持 auto_approve。
8. 校验 redirect_uri、state、client status、账号 grant_types。
9. 如果授权升级方案要求 IP binding，则规划统一 ClientIpResolver 和 trusted proxy 处理方式。
10. middleware-open-api-agent 仍然保持空模块，不实现任何 Agent API。
11. 不修改 frontend/。
12. 如果需要一个最小授权/consent 页面能力，只规划后端所必需的最小实现，不进行前端 Vue 改造。

重点：
- 优先使用 Spring Authorization Server 自带 Authorization Code / OAuth2AuthorizationService 能力
- 不重复手写一套协议级 auth_code Controller + Redis Map，除非方案明确要求且有充分理由

输出：
- 完整授权时序
- Authorization Server 相关扩展点
- consent 持久化
- PKCE 校验
- auth code 生命周期
- IP binding（如启用）
- 精确文件计划
- 测试矩阵
- 回滚方案

如果当前 Spring Authorization Server 能力、项目文档要求和现有代码之间存在关键冲突，立即停止并向我提问。
```

---

## Phase 5：统一 Resource Server 权限模型与后端鉴权收口

```text
请先读取仓库内授权升级方案文档和前面阶段实现结果，然后规划 Phase 5。

本阶段只做规划，不修改代码。

目标：
1. 将新 RS256 Access Token 正式作为 USER / ADMIN 请求的统一认证来源。
2. 将 roles / scope 正确映射到 Spring Security authorities。
3. 统一 401 / 403 行为。
4. 逐步用 Spring Resource Server 替换 LegacyJwtAuthenticationFilter 对 user/admin 请求的认证职责。
5. 保持 BaseContext / PermissionContext 对现有业务代码兼容。
6. 所有角色高低判断切换到 sys_role.rank。
7. 按授权升级方案落实 username 修改权限约束。
8. scope 权限和业务数据 ownership 必须分离。
9. middleware-open-api-agent 保持空模块，不增加业务接口。
10. 不修改 frontend/。

重点检查：
- 当前 /user/** 和 /admin/** 路由保护方式
- RequireSuperiorRole 等现有角色等级逻辑
- BaseContext / PermissionContext 使用范围
- Spring Method Security 是否需要在本阶段启用
- 当前异常返回格式和测试

输出：
- Resource Server 最终认证链路
- authority 映射规则
- legacy filter 退出顺序
- role.rank 替换清单
- username 权限约束落点
- 兼容方案
- 测试矩阵
- 分批实施顺序
- 回滚方式

如果发现某些业务接口依赖旧 SecurityIdentity 的隐式语义，无法安全替换时先停止并列出具体位置等我确认。
```

---

## Phase 6：Legacy Security Cleanup（后端）

```text
请先读取仓库内授权升级方案文档和 Phase 1-5 的实际实现结果，然后规划 Phase 6：后端 Legacy Security Cleanup。

本阶段只做规划，不修改代码。

前置条件：
- 新 RS256 Authorization Server 已稳定
- USER / ADMIN 登录、refresh、logout 已稳定
- Authorization Code + PKCE 已稳定
- Resource Server 已正式接管 user/admin access token 验证

目标：
1. 删除不再使用的 LegacyJwtAuthenticationFilter。
2. 删除 user/admin HS256 登录 JWT。
3. 删除旧 user/admin secret 配置。
4. 删除旧 Redis “完整 JWT 单 Token Session” key 模型。
5. 清理旧 SecurityIdentity 中不再需要的登录身份语义。
6. 拆分旧 TokenSessionService 中 OAuth Session 与账号激活/验证码职责。
7. 判断 activation token 是否继续保留旧 JWT；如果授权升级方案没有明确要求，不自行重构，先列问题问我。
8. 如果 jjwt 已无用途，再规划移除依赖。
9. middleware-open-api-agent 仍为空模块。
10. 不修改 frontend/。
11. 不删除旧登录兼容 endpoint，除非后端已明确不再依赖并且方案中已确认；前端切换仍属于后续计划。

输出：
- legacy reference inventory
- 可删除项 / 暂时保留项
- commit-by-commit 清理顺序
- 配置清理
- Redis key 清理
- 测试矩阵
- 部署注意事项
- rollback 方案

任何无法确认“是否还有外部调用方依赖”的 endpoint、配置或 Redis key，都不要直接规划删除，先停下来问我。
```

---

## Phase 7：后端授权升级最终验收

```text
请读取仓库内授权升级方案文档，以及 Phase 1-6 的实际实现结果，规划一次“后端授权升级最终验收”。

本阶段只做验证规划，不修改功能代码。

本轮不包含：
- frontend/ 改造
- middleware-open-api-agent 业务接口
- Agent 实际数据读取 API
- OAuth 管理后台，以及仅 creator 管理 registered OAuth client 的 endpoint/DTO、client secret 生命周期、审计和启停安全规则
- OIDC
- 社交登录
- 多设备 Session
- Key Rotation 等下一轮增强能力

请核对以下后端目标是否全部完成：
- RS256 access token
- refresh token rotation
- logout/revoke
- jti blacklist
- Client 配置
- RBAC permission
- role.rank
- user/admin 统一授权体系
- Authorization Code + PKCE
- consent
- auto_approve
- allowed_ips
- SecurityContext / BaseContext / PermissionContext 兼容
- legacy user/admin JWT 已按计划收口
- middleware-open-api-agent 仅为空模块占位

输出：
1. 验收 checklist
2. 自动化测试清单
3. 手工 E2E 测试清单
4. 数据库验证项
5. Redis 验证项
6. 安全边界验证项
7. 仍未完成但明确属于“前端后续计划 / Agent API 后续计划 / OAuth client 管理独立阶段”的事项
8. 是否满足“后端授权升级第一阶段完成”的结论

如果存在任何会影响后端正式切换的未决问题，立即停止并列出问题，不要用假设填补。
```
