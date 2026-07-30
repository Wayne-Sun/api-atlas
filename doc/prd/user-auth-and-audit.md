# API Atlas — User Authentication & Audit Module PRD

## 1. Overview

API Atlas 的用户认证与审计模块为平台提供身份认证、基于角色的访问控制（RBAC）、操作审计追踪以及用户管理功能。该模块确保所有 API 请求都经过身份验证，敏感操作受角色权限控制，所有数据变更留下审计痕迹。

**目标用户：** 平台管理员（管理用户、审计操作）和普通开发者（使用平台功能）。

**模块范围：** 用户认证（JWT 登录/登出/令牌撤销）、用户管理（CRUD、RBAC）、操作审计（MyBatis 拦截器自动填充审计字段）、前端登录页与用户管理页。

**版本迭代说明：** 本 PRD 涵盖两个工作阶段——**Phase 1 (user-auth-and-audit)**：初始完整功能实现；**Phase 2 (user-auth-fixes)**：代码审查后的问题修复与质量加固。

---

## 2. Phase 1 — 初始实现 (user-auth-and-audit)

### 2.1 用户认证

#### 2.1.1 认证机制

基于 **JWT (RS256) + Redis 令牌撤销** 的无状态认证体系：

| 组件 | 技术选型 | 说明 |
|------|---------|------|
| 认证框架 | Spring Security + OAuth2 Resource Server | 使用 `oauth2ResourceServer().jwt()` 解码 JWT |
| 签名算法 | RSA-256 (RS256) | 非对称密钥，私钥签名 / 公钥验签 |
| 令牌格式 | JWT (access token + refresh token) | access token 含 jti (UUID)、username、role 等 claims |
| 令牌生命周期 | access token: 1800s (30min), refresh token: 604800s (7天) | 通过 `atlas.jwt.access-token-expiration` 配置 |
| 会话存储 | Redis (`token:{jti}` → TokenSession) | 令牌撤销用，TTL 自动过期 |
| 密码加密 | BCrypt (BCryptPasswordEncoder) | 存储密码哈希，永不暴露原文 |

#### 2.1.2 认证流程

```
客户端                    服务端
  │                        │
  │  POST /api/auth/login   │
  │  { username, password } │
  │ ──────────────────────→ │  1. 查询用户 (UserService.getUserByUsername)
  │                        │  2. 校验密码 (BCryptPasswordEncoder.matches)
  │                        │  3. 检查状态 (DISABLED → 401)
  │                        │  4. 生成 JWT (access + refresh)
  │                        │  5. 保存 TokenSession 到 Redis
  │ ←────────────────────── │  6. 返回 { accessToken, refreshToken, user }
  │  { accessToken, ... }   │
  │                        │
  │  GET /api/auth/me       │
  │  Authorization: Bearer  │
  │ ──────────────────────→ │  1. TokenValidationFilter 检查 Redis 中 jti 存在
  │                        │  2. JwtDecoder 解析并验证签名
  │                        │  3. 返回当前用户信息
  │ ←────────────────────── │
  │  POST /api/auth/logout  │
  │ ──────────────────────→ │  1. 从 SecurityContext 获取 jti
  │                        │  2. Redis 中删除 token:{jti}
  │ ←────────────────────── │  3. 令牌立即失效
```

#### 2.1.3 令牌撤销机制

每次请求通过 `TokenValidationFilter` (OncePerRequestFilter) 检查 Redis 中 `token:{jti}` 是否存在：

- **存在** → 请求放行
- **不存在**（已登出或被撤销）→ 返回 401 `{"code":401,"msg":"Token has been revoked"}`
- **Redis 不可用** → 降级放行（`log.warn` 记录），不影响服务可用性

### 2.2 用户管理 (RBAC)

#### 2.2.1 角色模型

| 角色 | 标识 | 权限 |
|------|------|------|
| 管理员 | `ADMIN` | 用户管理 CRUD、数据源和接口管理的全部操作 |
| 普通用户 | `USER` | 数据源和接口管理的全部操作（不含用户管理） |

角色通过 JWT 的 `role` claim 传输，Spring Security 通过 `JwtAuthenticationConverter` 映射为 `ROLE_ADMIN` / `ROLE_USER`。

#### 2.2.2 用户状态

| 状态 | 说明 |
|------|------|
| `ENABLED` | 正常用户，可登录和使用平台 |
| `DISABLED` | 已停用，登录时返回 401 "Account disabled" |

#### 2.2.3 用户管理 API

| Method | Path | 权限 | 说明 |
|--------|------|------|------|
| `POST` | `/api/auth/login` | 公开 | 用户登录 |
| `POST` | `/api/auth/logout` | 认证 | 用户登出（撤销令牌） |
| `GET` | `/api/auth/me` | 认证 | 获取当前用户信息 |
| `GET` | `/api/users` | ADMIN | 列表查询（支持分页） |
| `POST` | `/api/users` | ADMIN | 新增用户 |
| `GET` | `/api/users/{id}` | ADMIN | 查询用户详情 |
| `PUT` | `/api/users/{id}` | ADMIN | 编辑用户（displayName, role） |
| `DELETE` | `/api/users/{id}` | ADMIN | 删除用户 |
| `PATCH` | `/api/users/{id}/status` | ADMIN | 启用/停用用户（ENABLED/DISABLED） |

### 2.3 操作审计追踪

#### 2.3.1 审计字段

所有数据表统一增加以下审计字段：

| 字段 | 类型 | INSERT 行为 | UPDATE 行为 |
|------|------|-------------|-------------|
| `created_by` | `VARCHAR(50)` | 设置当前用户名 | 不修改 |
| `created_at` | `DATETIME` | 设置 `LocalDateTime.now()` | 不修改 |
| `last_modified_by` | `VARCHAR(50)` | 设置当前用户名 | 更新为当前用户名 |
| `last_modified_at` | `DATETIME` | 设置 `LocalDateTime.now()` | 更新为当前时间 |
| `updated_at` | `DATETIME` | — | 设置为当前时间（原有字段） |

#### 2.3.2 实现方式：MyBatis 拦截器

**`AuditInterceptor`** 通过 MyBatis `Interceptor` 接口拦截所有 `INSERT` 和 `UPDATE` 语句：

1. 拦截 `Executor.update()` 方法
2. 检查参数类型（Entity / List / Map）
3. 通过 `MetaObject` 反射设置审计字段值
4. 当前用户名通过 `SecurityUtil.getCurrentUsername()` 从 SecurityContextHolder 获取
5. 无认证上下文时（如 DataInitializer 批量导入）使用 `"SYSTEM"`

```java
// 核心逻辑示意
Object parameter = invocation.getArgs()[0]; // MyBatis 3.5+ 参数位置
if (parameter == null) return invocation.proceed();

// 处理 Entity 参数
MetaObject metaObject = MetaObject.forObject(parameter, ...);
String username = SecurityUtil.getCurrentUsername();
LocalDateTime now = LocalDateTime.now();

if (sqlCommandType == SqlCommandType.INSERT) {
    safeSetValue(metaObject, "createdBy", username);
    safeSetValue(metaObject, "createdAt", now);
    safeSetValue(metaObject, "lastModifiedBy", username);
    safeSetValue(metaObject, "lastModifiedAt", now);
} else if (sqlCommandType == SqlCommandType.UPDATE) {
    safeSetValue(metaObject, "lastModifiedBy", username);
    safeSetValue(metaObject, "lastModifiedAt", now);
    safeSetValue(metaObject, "updatedAt", now); // 兼容原有字段名
}
```

#### 2.3.3 受审计的数据表

| 表名 | 审计字段状态 |
|------|-------------|
| `sys_user` | ✅ 完整审计（INSERT 包含所有审计列） |
| `data_source` | ✅ 完整审计 |
| `api_interface` | ✅ 完整审计 |
| `interface_param` | ✅ 完整审计 |

### 2.4 密钥管理与配置

#### 2.4.1 RSA 密钥对

JWT 使用 RSA-256 非对称签名：

- **私钥** (`atlas.jwt.private-key`): 用于签发 JWT，PEM 格式
- **公钥** (`atlas.jwt.public-key`): 用于验证 JWT，PEM 格式
- **配置位置**: `application-local.yml`（gitignored，不提交版本库）
- **启动校验**: `RsaKeyConfig` 在 `@PostConstruct` 阶段检查密钥是否为默认值，若为默认值则阻断启动

#### 2.4.2 AES 加密密钥

数据源密码使用 AES/GCM/NoPadding 加密，密钥通过 `atlas.encryption.secret-key` 配置（Base64 编码，32 字节）。

### 2.5 安全配置架构

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    // 过滤器链：
    // 1. CORS (allow localhost:5173)
    // 2. CSRF disabled (stateless API)
    // 3. SessionCreationPolicy.STATELESS
    // 4. POST /api/auth/login + OPTIONS → permitAll
    // 5. 其余请求 → authenticate
    // 6. OAuth2 Resource Server → JWT decoder
    // 7. TokenValidationFilter (验证 Redis 中 jti 有效性)
}
```

### 2.6 实现文件清单

#### 2.6.1 后端新增文件

| 文件 | 包/路径 | 职责 |
|------|---------|------|
| `SecurityConfig.java` | `config/` | Spring Security 过滤链、CORS、JWT decoder |
| `RsaKeyConfig.java` | `config/` | 加载 RSA 公私钥，启动时校验 |
| `TokenValidationFilter.java` | `config/` | 每次请求验证 Redis jti 有效性 |
| `SecurityUtil.java` | `config/` | 静态工具类获取当前用户名 |
| `RedisConfig.java` | `config/` | RedisTemplate 配置（String → TokenSession） |
| `AuditInterceptor.java` | `config/` | MyBatis 拦截器自动填充审计字段 |
| `RedisStartupValidator.java` | `run/config/` | 启动时检查 Redis 连通性（非阻塞警告） |
| `AuthController.java` | `controller/` | 登录 / 登出 / 当前用户信息 |
| `UserController.java` | `controller/` | 用户 CRUD（管理员） |
| `UserService.java` | `service/` | 用户业务逻辑 + BCrypt 密码管理 |
| `JwtTokenService.java` | `service/` | RSA256 JWT 生成（access + refresh token） |
| `RedisTokenService.java` | `service/` | Redis 中对 TokenSession 的 CRUD 操作 |
| `UserMapper.java` | `mapper/` | MyBatis 用户映射接口 |
| `User.java` | `model/` | 用户实体 |
| `UserCreateDTO.java` | `model/` | 创建用户 DTO（含 `@NotBlank` 校验） |
| `UserUpdateDTO.java` | `model/` | 更新用户 DTO |
| `UserInfoDTO.java` | `model/` | 用户信息响应 DTO（不含密码） |
| `LoginRequest.java` | `model/` | 登录请求 DTO |
| `LoginResponse.java` | `model/` | 登录响应 DTO |
| `TokenSession.java` | `model/` | Redis 中存储的会话信息 |
| `UserMapper.xml` | `resources/mapper/` | MyBatis XML 映射 |
| `application-local.yml` | `resources/` | 本地配置覆盖（gitignored） |

#### 2.6.2 后端修改文件

| 文件 | 变更内容 |
|------|---------|
| `pom.xml` | 新增 `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-data-redis`, `spring-security-oauth2-jose` |
| `application.yml` | 新增 `atlas.jwt.*`, `atlas.encryption.*`, `atlas.admin.default-password`, `spring.data.redis.*` 配置 |
| `schema.sql` | 新增 `sys_user` 表；`data_source`, `api_interface`, `interface_param` 增加审计字段 |
| `DataSource.java` | 新增 `createdBy`, `createdAt`, `lastModifiedBy`, `updatedAt` 字段 |
| `ApiInterface.java` | 新增 `createdBy`, `createdAt`, `lastModifiedBy`, `updatedAt` 字段 |
| `InterfaceParam.java` | 新增 `createdBy`, `createdAt`, `lastModifiedBy`, `updatedAt` 字段 |
| `DataSourceService.java` | 移除手动设置 `createdAt`/`updatedAt` 的代码（拦截器接管） |
| `ApiInterfaceService.java` | 同上 |
| `GlobalExceptionHandler.java` | 新增 401/403 异常处理 |

#### 2.6.3 前端新增/修改文件

| 文件 | 变更类型 | 职责 |
|------|---------|------|
| `stores/auth.ts` | 新增 | 认证状态管理（token, currentUser, login, logout, fetchMe） |
| `stores/user.ts` | 新增 | 用户管理状态管理（list, create, update, delete） |
| `views/login/index.vue` | 新增 | 登录页面 |
| `views/user/index.vue` | 新增 | 用户管理列表页 |
| `views/user/components/UserFormModal.vue` | 新增 | 用户表单弹窗 |
| `router/index.ts` | 修改 | 新增路由 + auth guard |
| `layouts/BaseLayout.vue` | 修改 | 头部显示用户信息 + 条件渲染用户管理菜单 |
| `utils/request.ts` | 修改 | 增加 Bearer token 拦截器 + 401 处理 |
| `App.vue` | 修改 | 全局 `initFromStorage()` 调用 |
| `main.ts` | 修改 | 挂载 auth store |

### 2.7 数据库 Schema 变更

#### 2.7.1 新增 `sys_user` 表

```sql
CREATE TABLE sys_user (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100) NOT NULL DEFAULT '',
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(50)  NULL,
    created_at      DATETIME     NOT NULL,
    last_modified_by VARCHAR(50) NULL,
    last_modified_at DATETIME    NULL
);
```

#### 2.7.2 现有表增加审计字段

```sql
ALTER TABLE data_source
    ADD COLUMN created_by       VARCHAR(50) NULL AFTER password,
    ADD COLUMN last_modified_by VARCHAR(50) NULL AFTER created_at;

ALTER TABLE api_interface
    ADD COLUMN created_by       VARCHAR(50) NULL AFTER page_size,
    ADD COLUMN last_modified_by VARCHAR(50) NULL AFTER created_at;

ALTER TABLE interface_param
    ADD COLUMN created_by       VARCHAR(50) NULL,
    ADD COLUMN created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN last_modified_by VARCHAR(50) NULL,
    ADD COLUMN last_modified_at DATETIME NULL,
    ADD COLUMN updated_at       DATETIME NULL;
```

---

## 3. Phase 2 — 问题修复 (user-auth-fixes)

### 3.1 审查发现的问题汇总

Phase 1 实现后，通过 Prometheus 规划审查 + Momus 计划评审 + Oracle 独立审查三轮质量检查，发现以下问题：

| # | 优先级 | 类别 | 问题描述 | 文件 |
|---|--------|------|---------|------|
| 1 | 🔴 P0 | 功能缺陷 | `UserMapper.xml` INSERT 缺少审计列，审计字段不持久化 | `UserMapper.xml` |
| 2 | 🔴 P0 | 功能缺陷 | `initFromStorage()` 未被调用，前端刷新后用户管理菜单消失 | `stores/auth.ts`, `router/index.ts` |
| 3 | 🟠 P1 | 功能缺陷 | `UserFormModal` 编辑模式显示状态选择器但不发送状态变更 | `UserFormModal.vue` |
| 4 | 🟠 P1 | 缺少校验 | `updateStatus` 端点未对 status 值做枚举校验 | `UserController.java` |
| 5 | 🟠 P1 | 审计缺口 | `updateStatus` 在全部 3 个 Mapper 中缺少审计字段 | `UserMapper.xml`, `DataSourceMapper.xml`, `ApiInterfaceMapper.xml` |
| 6 | 🟡 P2 | 代码冗余 | `JwtKeyValidator` 与 `RsaKeyConfig` 重复的启动校验 | `JwtKeyValidator.java` |
| 7 | 🟡 P2 | 日志级别 | `AuthorizationDeniedException` (403) 使用 ERROR 级别 | `GlobalExceptionHandler.java` |
| 8 | 🟡 P2 | 无效注解 | `UserController.update()` 上 `@Valid` 无实际作用（DTO 无校验注解） | `UserController.java` |

### 3.2 修复方案

#### 🔴 P0-1: UserMapper INSERT 缺失审计列

**问题：** `AuditInterceptor` 虽然通过 `MetaObject.setValue()` 在 Java 对象上设置了审计字段，但 INSERT SQL 语句未包含这些列，导致数据不持久化。

**修复：**
```xml
<!-- 修复前 -->
INSERT INTO sys_user (username, password, display_name, role, status)
VALUES (#{username}, #{password}, #{displayName}, #{role}, #{status})

<!-- 修复后 -->
INSERT INTO sys_user (username, password, display_name, role, status,
    created_by, created_at, last_modified_by, last_modified_at)
VALUES (#{username}, #{password}, #{displayName}, #{role}, #{status},
    #{createdBy}, #{createdAt}, #{lastModifiedBy}, #{lastModifiedAt})
```

#### 🔴 P0-2: 路由守卫 hydration 问题

**问题：** 页面刷新时，`router.beforeEach` 先于 `App.vue` 的 `onMounted` 执行。`currentUser` 为 null → `isAdmin` 为 false → `/user` 路由被错误重定向。

**修复链路：**

1. **`router/index.ts`** — 在路由守卫的 `isAdmin` 检查前，当 `token` 存在但 `currentUser` 为 null 时调用 `fetchMe()`：
   ```
   刷新 /user
     → token 存在, currentUser = null
     → await authStore.fetchMe()  （新增）
     → currentUser 填充, isAdmin 正确
     → 路由守卫继续，管理员正常进入 /user
   ```

2. **`App.vue`** — `onMounted` 中保留 `initFromStorage()` 作为兜底（处理非路由守卫触发的恢复场景）

#### 🟠 P1-3: UserFormModal 状态选择器

**方案 A（已采纳）：** 编辑模式移除状态选择器，仅保留创建模式下的状态字段。管理员通过列表页的启用/停用按钮（PATCH `/{id}/status`）管理用户状态。

#### 🟠 P1-4: updateStatus 端点校验

**问题：** `PATCH /{id}/status` 直接接受任意字符串，未校验仅允许 `ENABLED`/`DISABLED`。

**修复：** 增加手动枚举校验：
```java
@PatchMapping("/{id}/status")
public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
    String status = body.get("status");
    if (!List.of("ENABLED", "DISABLED").contains(status)) {
        return R.error(400, "Invalid status value, must be ENABLED or DISABLED");
    }
    userService.updateStatus(id, status);
    return R.ok(null);
}
```

#### 🟠 P1-5: updateStatus 审计字段补齐

**问题：** `updateStatus` 在三个 Mapper 中直接 `SET status = #{status}`，绕过审计拦截器（因为 `AuditInterceptor` 对 `Map<String, Object>` 参数中的 String/Number/Boolean 值不做处理）。

**修复：** 改用 `updateById` 模式，先查询实体再更新：

| 文件 | 修复方式 |
|------|---------|
| `UserMapper.xml` `updateStatus` | 改为调用 `updateById`，设置完整字段 |
| `DataSourceMapper.xml` `updateStatus` | 同上 |
| `ApiInterfaceMapper.xml` `updateStatus` | 同上 |

或者 Service 层手动调用：`user.setStatus(status)`, `user.setLastModifiedBy(username)`, `user.setLastModifiedAt(now)` 然后 `updateById(user)`。

#### 🟡 P2-6: 删除 JwtKeyValidator

`JwtKeyValidator.java` 中的 `@PostConstruct validateKey()` 与 `RsaKeyConfig.java` 中的完全相同，删除冗余类，保留 `RsaKeyConfig` 中的校验。

#### 🟡 P2-7: 403 日志级别降级

`GlobalExceptionHandler` 中 `AuthorizationDeniedException` 改为 `log.warn()`（预期行为，非系统错误）。

#### 🟡 P2-8: 移除无效 @Valid

`UserController.update()` 上的 `@Valid` 注解移除（`UserUpdateDTO` 无校验注解，`@Valid` 不触发任何验证）。

### 3.3 修复文件清单

| # | 文件 | 变更内容 |
|---|------|---------|
| 1 | `UserMapper.xml` | INSERT 增加 4 个审计列 |
| 2 | `router/index.ts` | 路由守卫中增加 `await authStore.fetchMe()` |
| 3 | `App.vue` | `onMounted` 调用 `initFromStorage()` |
| 4 | `UserFormModal.vue` | 编辑模式移除状态选择器 |
| 5 | `UserController.java` | `updateStatus` 增加枚举校验；`update()` 移除 `@Valid` |
| 6 | `UserMapper.xml` | `updateStatus` 增加审计字段 |
| 7 | `DataSourceMapper.xml` | `updateStatus` 增加审计字段 |
| 8 | `ApiInterfaceMapper.xml` | `updateStatus` 增加审计字段 |
| 9 | `JwtKeyValidator.java` | 删除冗余文件 |
| 10 | `GlobalExceptionHandler.java` | `AuthorizationDeniedException` 日志级别降为 WARN |

---

## 4. 前端设计

### 4.1 路由与权限守卫

```
Hash 路由模式，beforeEach 守卫逻辑：

请求进入 → 判断目标路由
  ├─ /login → 放行（公开）
  ├─ 未认证 → 重定向到 /login
  ├─ 已认证，currentUser 为空 → 调用 fetchMe() 填充
  ├─ requiresAdmin + !isAdmin → 重定向到 /datasource
  └─ 其他 → 放行
```

### 4.2 页面规格

#### 登录页 (`/login`)

| 元素 | 规格 |
|------|------|
| 布局 | 独立于 BaseLayout 的居中卡片 |
| 表单 | 用户名 + 密码输入框 |
| 提交 | POST /api/auth/login → 成功跳转 /datasource |
| 错误处理 | 显示 NMessage.error() 错误提示 |
| 已登录跳转 | 若 token 存在则自动跳转 /datasource |

#### 用户管理页 (`/user`)

| 元素 | 规格 |
|------|------|
| 访问控制 | `meta.requiresAdmin: true`，非管理员自动跳转 |
| 数据表 | 列：用户名、显示名、角色(NTag)、状态(NSwitch)、创建时间、操作 |
| 新增 | 弹出 UserFormModal，输入 username/password/displayName/role |
| 编辑 | 弹出 UserFormModal（不显示 status），提交 displayName/role |
| 状态切换 | NSwitch → 确认 → PATCH /{id}/status |
| 删除 | NPopconfirm 确认 → DELETE /{id} |
| Loading | `deletingId` ref 模式，逐行 loading 状态 |
| 分页 | NDataTable 分页，读取 `res.data.total \|\| 0` |

### 4.3 Auth Store 设计

```typescript
// stores/auth.ts — Composition API
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const currentUser = ref<UserInfo | null>(null)
  const loading = ref(false)

  const isAuthenticated  = computed(() => !!token.value)
  const isAdmin          = computed(() => currentUser.value?.role === 'ADMIN')

  async function login(username: string, password: string) { ... }
  async function logout() { ... }
  async function fetchMe() { ... }           // GET /api/auth/me
  function initFromStorage() { ... }         // 页面刷新恢复
  function clearStore() { ... }
})
```

### 4.4 请求拦截器

`utils/request.ts`:

```typescript
// 请求拦截器
config.headers.Authorization = `Bearer ${localStorage.getItem('token')}`

// 响应拦截器
if (res.data.code >= 400) {
  message.error(res.data.message)
  if (res.data.code === 401) { /* 清理 token + 跳转 /login */ }
  return Promise.reject(new Error(res.data.message))
}
```

---

## 5. 测试策略

### 5.1 后端测试

| 测试文件 | 类型 | 覆盖内容 |
|---------|------|---------|
| `UserMapperTest.java` | @MybatisTest + H2 | 用户 CRUD、审计字段持久化、状态变更审计 |
| `UserServiceTest.java` | Mockito 纯单元测试 | 用户创建/更新/删除/状态变更业务逻辑 |
| `JwtTokenServiceTest.java` | Mockito 纯单元测试 | JWT 生成、解析、签名验证 |
| `RedisTokenServiceTest.java` | Mockito 纯单元测试 | TokenSession 存/取/删/存在检查 |
| `AuthControllerTest.java` | @SpringBootTest + MockMvc | 登录成功/失败/禁用账户/登出/获取当前用户 |
| `UserControllerTest.java` | @SpringBootTest + MockMvc | 用户 CRUD 端点（admin-only 验证） |
| `SecurityConfigTest.java` | @SpringBootTest + MockMvc | 公开端点、认证端点、CORS 头 |
| `AuthFlowIntegrationTest.java` | @SpringBootTest | 完整认证流程（登录 → 访问受保护资源 → 登出 → 访问被拒绝） |

### 5.2 前端测试

| 测试文件 | 类型 | 覆盖内容 |
|---------|------|---------|
| `stores/__tests__/auth.spec.ts` | Vitest + vi.mock(request) | login/logout/fetchMe/initFromStorage/clearStore |
| `stores/__tests__/user.spec.ts` | Vitest + vi.mock(request) | fetchUsers/createUser/updateUser/deleteUser |
| `router/__tests__/guard.spec.ts` | Vitest + vue-router mock | 未认证重定向、admin 路由守卫、token 恢复 |
| `router/__tests__/router.spec.ts` | Vitest + vue-router mock | 路由定义完整性 |

### 5.3 验收标准

```bash
# 后端全部测试
cd backend && mvn test -Dspring.profiles.active=test
# => Tests run: 52, Failures: 0, Errors: 0

# 前端全部测试
cd frontend && npm run test:run
# => Test Files: 3 passed, Tests: 25 passed

# 编译验证
cd backend && mvn compile                        # BUILD SUCCESS
cd frontend && npm run build                      # 构建成功
```

---

## 6. 非功能需求

### 6.1 Redis 降级策略

Redis 不可用时：
- **登录**：正常（不依赖 Redis）
- **令牌验证**：`TokenValidationFilter` 降级放行（`log.warn` 记录），所有请求通过
- **登出**：无法撤销令牌（Redis 不可用），下次 Redis 恢复时已过期的 TTL 自动清理

### 6.2 密码安全

- 存储：BCrypt 哈希（不可逆），永不存储明文
- 传输：HTTPS（生产环境）、JWT 承载凭据
- API 响应：`UserInfoDTO` 不含 password 字段
- 初始管理员密码：随机 UUID 生成，首次启动日志输出

### 6.3 密钥安全

- RSA 密钥对：配置在 `application-local.yml`（gitignored），生产环境可通过环境变量注入
- AES 加密密钥：同上，`EncryptionConfig.validateKey()` 在启动时阻止默认密钥运行
- 生产环境禁止使用示例/默认密钥

### 6.4 审计完整性

- 所有 INSERT/UPDATE 操作通过 MyBatis 拦截器自动填充审计字段
- 状态变更操作（PATCH status）使用 `updateById` 模式确保审计字段更新
- 无认证上下文时（如 DataInitializer）使用 `"SYSTEM"` 标识系统操作

---

## 7. 依赖变更

### 7.1 后端 (`pom.xml`)

| 依赖 | GroupId | ArtifactId | 说明 |
|------|---------|-----------|------|
| Spring Security | `org.springframework.boot` | `spring-boot-starter-security` | 认证与授权框架 |
| OAuth2 Resource Server | `org.springframework.boot` | `spring-boot-starter-oauth2-resource-server` | JWT 解码 |
| Redis | `org.springframework.boot` | `spring-boot-starter-data-redis` | Redis 操作 |
| JWT Jose | `org.springframework.security` | `spring-security-oauth2-jose` | JWT 签名/验签 |

### 7.2 前端 (`package.json`)

无新增依赖（Vue Router、Axios 已在 Phase 0 存在，Pinia 已在项目中）。

---

## 8. 实施增量

### 8.1 统计

| 指标 | Phase 1 | Phase 2 | 合计 |
|------|---------|---------|------|
| 后端新增文件 | 20 | 0 | 20 |
| 后端修改文件 | 8 | 8 | 16 |
| 后端测试文件 | 8 | 0 | 8 |
| 前端新增文件 | 5 | 0 | 5 |
| 前端修改文件 | 4 | 3 | 7 |
| 前端测试文件 | 4 | 0 | 4 |
| 排查并修复问题 | — | 8 | 8 |
| **总计改动行数** | ~4412 | 少量 | ~4412+ |

### 8.2 Git 提交历史

```
37ca5e7  feat: integrate auth/security infrastructure, audit interceptor,
          user management, RBAC, and Redis token revocation
          [user-auth-and-audit 阶段 — 69 files, +4412 lines]

603833a  feat: initial scaffold - Spring Boot 4.1 + Vue 3 full-stack
          API management platform
          [基础脚手架]
```

Phase 2 (user-auth-fixes) 的修复内容已合入 `37ca5e7` 之后的增量提交中，未单独标记提交。
