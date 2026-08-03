# API Atlas — Knowledge Base

Split full-stack scaffold: Spring Boot 4.1 + JDK 21 + MyBatis + PageHelper + MySQL + Redis (backend/), Vue 3 + Naive UI + Vite + TypeScript + Pinia (frontend/).

## ✅ 已实施计划总览（全部完成）

项目通过 9 份计划逐步迭代完成，目前所有 Checkbox 均已勾选，全部验收通过。

| 计划 | 规模 | 核心内容 | 状态 |
|------|------|---------|------|
| **1. datasource-interface** | 27 impl + 8 verify + 3 final | 完整 DataSource / Interface CRUD、动态客户端生命周期、4 种查询执行器、前端全页面、PRD | ✅ |
| **2. fullstack-code-audit** | 14 修复 | 密码解密 BUG、@Transactional、资源泄漏、日志、DTO 校验、前端错误处理 | ✅ |
| **3. code-review-fixes** | 21 修复 + 3 doc | R.total、EncryptionUtil 抽取、锁策略、AES 去重、ES HTTPS、前端确认框/loading | ✅ |
| **4. fix-circular-dependency** | 1 修复 | `@Lazy` 解决 `DataSourceClientManager` 循环依赖 | ✅ |
| **5. frontend-issues-fix-plan** | 29 问题修复 | 运行时 BUG、类型安全（移除 any）、表单校验、测试、404 路由、引擎限制放宽 | ✅ |
| **6. test-coverage-and-optimization** | 7 任务 | H2 测试基础设施 + 后端 Service/Mapper 测试 + Naive UI 按需加载（56% 体积缩减）+ Vitest | ✅ |
| **7. perf-audit-optimize** | 12 任务 | P0: JdbcTemplate 缓存/IBATIS synchronized -> computeIfAbsent; P1: pageSize上限/日志级别; P2: SecureRandom/HikariCP 可配置 | ✅ |
| **8. config-security** | 3 任务 | 从 application.yml 移除硬编码数据库密码、application-local.yml、文档 | ✅ |
| **9. remaining-fixes** | 6 修复 | 接口删除按钮、分页事件名、getById store、cascade delete、SQL 日志 SLF4J | ✅ |

**最终验证指标：**
- 后端测试：52 tests, 0 Failures, 0 Errors
- 前端测试：25 tests, 3 files, all passed
- Naive UI chunk: 309 kB（从 711 kB，缩减 56%）
- 前端 `npm run build`: 通过
- 后端 `mvn compile`: BUILD SUCCESS

## Structure

```
api-atlas/
├── backend/                        # Spring Boot 4.1, Maven, Java 21
│   ├── pom.xml                     # Dependencies: web, mybatis 4.0.1, pagehelper 4.1.1, mysql, postgresql, elasticsearch 8.16, security, oauth2-resource-server, redis, h2 (test)
│   └── src/main/
│       ├── java/com/api/atlas/
│       │   ├── ApiAtlasApplication.java   # @SpringBootApplication entry
│       │   ├── config/                    # @Configuration, @Component, utility, interceptors
│       │   │   ├── AuditInterceptor.java       # MyBatis interceptor — auto-populates createdBy/updatedAt
│       │   │   ├── DatabaseClientFactory.java  # JDBC/HikariCP client factory
│       │   │   ├── DataInitializer.java        # CommandLineRunner — seeds admin user on first start
│       │   │   ├── DataSourceFactory.java      # Generic factory interface for multi-datasource
│       │   │   ├── DataSourceFactoryConfig.java # Factory config
│       │   │   ├── DataSourceFactoryRegistry.java # ConcurrentHashMap-based registry
│       │   │   ├── ElasticsearchClientFactory.java
│       │   │   ├── EncryptionConfig.java        # AES SecretKey bean
│       │   │   ├── EncryptionUtil.java          # AES/GCM/NoPadding utility
│       │   │   ├── GlobalExceptionHandler.java  # @RestControllerAdvice
│       │   │   ├── RedisConfig.java             # RedisTemplate bean for TokenSession
│       │   │   ├── RsaKeyConfig.java            # RSA key beans from @Value PEM strings
│       │   │   ├── SecurityConfig.java          # Spring Security filter chain + CORS + JWT decoder
│       │   │   ├── SecurityUtil.java            # Static helper — current username from context
│       │   │   └── TokenValidationFilter.java   # OncePerRequestFilter — validates JWT jti in Redis
│       │   ├── controller/              # REST controllers
│       │   │   ├── AuthController.java       # POST /login, POST /logout, GET /me
│       │   │   ├── DataSourceController.java
│       │   │   ├── InterfaceController.java
│       │   │   └── UserController.java        # CRUD users (admin-only)
│       │   ├── mapper/                  # MyBatis mapper interfaces
│       │   │   ├── ApiInterfaceMapper.java
│       │   │   ├── DataSourceMapper.java
│       │   │   ├── InterfaceParamMapper.java
│       │   │   └── UserMapper.java
│       │   ├── model/                   # Entity/DTO classes
│       │   │   ├── ApiInterface.java, ApiInterfaceCreateDTO.java, ApiInterfaceUpdateDTO.java
│       │   │   ├── DataSource.java, DataSourceCreateDTO.java, DataSourceUpdateDTO.java
│       │   │   ├── InterfaceParam.java
│       │   │   ├── LoginRequest.java, LoginResponse.java
│       │   │   ├── ParamDef.java
│       │   │   ├── R.java                      # Unified response envelope
│       │   │   ├── StatusUpdateDTO.java
│       │   │   ├── TokenSession.java
│       │   │   ├── User.java, UserCreateDTO.java, UserInfoDTO.java, UserUpdateDTO.java
│       │   │   └── ...
│       │   ├── run/config/               # Startup validators
│       │   │   └── RedisStartupValidator.java   # @PostConstruct — warns if Redis unavailable
│       │   └── service/                  # Business logic + executors
│       │       ├── executor/
│       │       │   ├── DatabaseQueryExecutor.java       # SQL + IBATIS execution
│       │       │   ├── ElasticsearchQueryExecutor.java  # ES|QL + Query DSL execution
│       │       │   └── QueryResult.java                 # Execution result model
│       │       ├── ApiInterfaceService.java
│       │       ├── DataSourceClientManager.java
│       │       ├── DataSourceEventPublisher.java
│       │       ├── DataSourceService.java
│       │       ├── JwtTokenService.java           # RSA256 JWT generation
│       │       ├── ParamExtractor.java
│       │       ├── RedisTokenService.java          # JWT jti → TokenSession CRUD in Redis
│       │       └── UserService.java
│       └── src/main/resources/
│           ├── application.yml                    # MySQL, Redis, MyBatis, PageHelper, atlas.* config
│           ├── application-local.yml              # Local overrides (gitignored)
│           ├── schema.sql                         # DB init schema
│           └── mapper/                            # MyBatis XML mappers
│               ├── ApiInterfaceMapper.xml
│               ├── DataSourceMapper.xml
│               ├── InterfaceParamMapper.xml
│               └── UserMapper.xml
├── frontend/                       # Vue 3, Vite 8, TypeScript 6, Naive UI 2.44
│   ├── src/
│   │   ├── main.ts                  # App entry — createApp + Pinia + router + naive-ui plugin
│   │   ├── App.vue                  # Root component, NConfigProvider wrapper
│   │   ├── layouts/
│   │   │   └── BaseLayout.vue       # NLayout + sidebar + header + router-view
│   │   ├── views/
│   │   │   ├── datasource/
│   │   │   │   ├── index.vue        # List page
│   │   │   │   ├── Editor.vue       # Create/Edit form
│   │   │   │   └── __tests__/
│   │   │   ├── interface/
│   │   │   │   ├── index.vue        # List page
│   │   │   │   ├── Editor.vue       # Create/Edit form
│   │   │   │   ├── TestView.vue     # Query test runner
│   │   │   │   └── __tests__/
│   │   │   ├── login/index.vue     # Login page
│   │   │   ├── user/
│   │   │   │   ├── index.vue        # User management (admin-only)
│   │   │   │   ├── components/UserFormModal.vue
│   │   │   │   └── ...
│   │   │   └── NotFound.vue         # 404 catch-all
│   │   ├── stores/
│   │   │   ├── auth.ts              # Auth state, login/logout, token, currentUser
│   │   │   ├── datasource.ts
│   │   │   ├── interface.ts
│   │   │   ├── user.ts              # User CRUD (admin)
│   │   │   └── __tests__/           # Store unit tests
│   │   ├── router/
│   │   │   ├── index.ts             # Hash-mode routes + auth guard (beforeEach)
│   │   │   └── __tests__/           # Router/guard tests
│   │   └── utils/
│   │       ├── request.ts           # Axios instance + auth interceptor
│   │       ├── naive-ui.ts          # Tree-shaken Naive UI plugin (create() API)
│   │       └── __tests__/           # Util tests
│   ├── vitest.config.ts             # Vitest v4 config, jsdom, @ alias
│   ├── vite.config.ts               # @ alias, vue + devtools plugins, proxy
│   ├── tsconfig*.json               # TS 6, node24 target, strict: true
│   └── package.json
├── doc/prd/
│   └── api-atlas-datasource-interface.md
├── .omo/                            # OpenCode plans, evidence, notepads
└── .gitignore                       # 3-tier: root (global) / backend (Java) / frontend (Node)
```

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Add REST endpoint | `backend/.../controller/` | New class, `@RestController`, return `R<T>` |
| Add MyBatis mapper | `backend/.../mapper/` + `resources/mapper/*.xml` | Interface + XML pair |
| Add service | `backend/.../service/` | `@Service @Transactional`, constructor injection |
| Add executor | `backend/.../service/executor/` | One per query type (SQL, IBATIS, ES|QL, QueryDSL) |
| Add entity/DTO | `backend/.../model/` | POJO for entity, separate DTOs with validation |
| Add configuration | `backend/.../config/` | `@Configuration`, `@Component`, utility, factory |
| Add startup validator | `backend/.../run/config/` | `@Component` + `@PostConstruct` for startup checks |
| Add security config | `backend/.../config/SecurityConfig.java` | Spring Security filter chain, CORS, JWT decoder |
| Add JWT token service | `backend/.../service/JwtTokenService.java` | RSA256 token generation (access + refresh) |
| Add Redis token ops | `backend/.../service/RedisTokenService.java` | JWT jti → TokenSession CRUD |
| Add auth endpoint | `backend/.../controller/AuthController.java` | Login, logout, me |
| Add user management | `backend/.../controller/UserController.java` + `service/UserService.java` | Admin-only CRUD |
| Add audit interceptor | `backend/.../config/AuditInterceptor.java` | MyBatis interceptor — auto-fills createdBy/updatedAt |
| Add data initializer | `backend/.../config/DataInitializer.java` | Seeds admin user on empty DB |
| Update DB config | `backend/src/main/resources/application.yml` | Datasource, Redis, MyBatis, PageHelper, atlas.* |
| Update local overrides | `backend/src/main/resources/application-local.yml` | Local passwords, keys (gitignored) |
| Add Vue page | `frontend/src/views/` | New .vue, add route in `router/index.ts` |
| Add login page | `frontend/src/views/login/index.vue` | No BaseLayout wrapper, public route |
| Add user page | `frontend/src/views/user/` | Admin-only, with `UserFormModal.vue` component |
| Add Pinia store | `frontend/src/stores/` | Composition API, `export const useXxxStore = defineStore('xxx', () => {...})` |
| Add auth store | `frontend/src/stores/auth.ts` | Token, currentUser, login/logout, isAuthenticated, isAdmin |
| Add API utility | `frontend/src/utils/request.ts` | Axios instance with Bearer token + interceptors |
| Add Naive UI plugin | `frontend/src/utils/naive-ui.ts` | Tree-shaken `create()` component list |
| Global frontend config | `frontend/vite.config.ts` | Aliases, plugins, proxy |
| Frontend test config | `frontend/vitest.config.ts` | jsdom, @ alias, coverage |
| Frontend dependencies | `frontend/package.json` | Vue 3.5, Naive UI 2.44, TypeScript 6, Vite 8, Vitest 4 |
| Route guard | `frontend/src/router/index.ts` | `beforeEach` — auth check + admin check |

## CONVENTIONS

### Backend: Package Layering

- **controller/** — REST endpoints only. Delegate to service, return `R<T>`. No business logic.
- **service/** — Business logic + `@Transactional` boundary. Constructor injection. No request/response objects (DTOs in → entities out).
- **mapper/** — MyBatis interfaces. One per entity. XML in `resources/mapper/`.
- **model/** — Entity + DTO classes. No JPA annotations (MyBatis-only). DTOs are separate from entities.
- **config/** — @Configuration, @Component, utility classes. Includes factory registry for multi-datasource support, encryption, security, Redis.
- **run/config/** — Startup-time components (Redis connectivity check).
- **service/executor/** — Query executors (DatabaseQueryExecutor for SQL/IBATIS, ElasticsearchQueryExecutor for ES|QL/Query DSL).

### Backend: DataSource Type System
- DataSource type 使用 **String 而非 Java Enum**，通过 `DataSourceFactoryRegistry` + `ConcurrentHashMap<String, DataSourceFactory<?>>` 实现可扩展工厂模式。
- 新增数据源类型只需实现 `DataSourceFactory<T>` 接口并注册到 registry，无需修改现有代码。
- 已注册类型：MySQL/PostgreSQL (`DatabaseClientFactory`)、Elasticsearch (`ElasticsearchClientFactory`)、MongoDB (`MongoClientFactory`)。MongoDB 工厂用 `@Value` 注入超时（`atlas.mongodb.connect-timeout-ms` / `server-selection-timeout-ms` / `socket-timeout-ms`，默认 5000/5000/60000），`mongodb://` 或 `mongodb+srv://` 前缀的 host 整体透传为连接串，否则拼接 `mongodb://` URI 并对用户名/密码做百分号编码（`URLEncoder` + `+` → `%20`）；`MongoClients.create(settings)` 为惰性连接，创建不触网。

### Backend: Dynamic Client Lifecycle
- `DataSourceClientManager` 管理 DataSource/ES 客户端的启用/禁用生命周期。
- 启用时通过 `((GenericApplicationContext) context).registerBean(...)` 将 `DataSource` 注册到 Spring 容器。
- 禁用时调用 `HikariDataSource.close()` 关闭连接池并移除 Bean。
- 关键依赖 `List<DataSourceEventPublisher>` 使用 `@Lazy` 注入以打破循环依赖。
- 线程安全使用 `ConcurrentHashMap.computeIfAbsent()` + 专用锁对象，严禁 `synchronized(String.intern())`。

### Backend: Query Executors

| 查询类型 | 执行器 | 实现方式 |
|---------|--------|---------|
| **SQL** (`${param}`) | `DatabaseQueryExecutor` | JdbcTemplate + 手动 `PreparedStatement` 参数绑定 + LIMIT/OFFSET 分页，**不使用 PageHelper** |
| **IBATIS** (`#{param}`) | `DatabaseQueryExecutor` | MyBatis XMLBuilder 动态解析 + `SqlSessionFactory`，in-memory 分页 + `maxMemoryRows` 上限守护 |
| **ES|QL** | `ElasticsearchQueryExecutor` | `${param}` → `?` 位置替换 → `esClient.sql().query()` |
| **Query DSL** | `ElasticsearchQueryExecutor` | Jackson `JsonNode` 逐字段替换 `${param}` → `esClient.search()`，自动从 body 中剥离 `index` |
| **MONGO_FIND** | `MongoQueryExecutor` | Jackson 3 (`tools.jackson`) 树遍历，类型化 `${param}` 替换（数字/布尔/null → 类型化节点，混合文本字符串插值，缺失参数 → null）→ `find(filter)` + `projection`/`sort`，`$skip`/`$limit` 分页 + `countDocuments` total；`MongoException` → `RuntimeException` 包装（含 datasourceId） |
| **MONGO_AGG** | `MongoQueryExecutor` | `aggregate(pipeline)`（`org.bson.Document`），追加 `$skip`/`$limit` 分页 + `$count` total，拒绝 `$out`/`$merge` 写阶段；`MongoException` → `RuntimeException` 包装（含 datasourceId） |

### Backend: R Envelope
- All controller methods return `R<T>`.
- Use `R.ok(data)` for success, `R.ok(list, pageInfo)` for paginated results, `R.error(msg)` for failure.
- `R.total` field is set from `PageInfo.getTotal()` — never manually set.
- Frontend always reads `res.data.data` for payload, `res.data.total || 0` for total.

### Backend: Encryption Standards
- ALL AES/GCM/NoPadding encryption must go through `EncryptionUtil.encrypt()`/`decrypt()`.
- SecretKey is injected as a Spring bean from `EncryptionConfig`. Never create SecretKey inline.
- Use `@Value("${atlas.encryption.secret-key}")` in EncryptionConfig only.
- Algorithm: AES/GCM/NoPadding, 12-byte IV (random per encryption), IV prepended to ciphertext, Base64-encoded.
- `new SecureRandom()` (not `SecureRandom.getInstanceStrong()` to avoid blocking on /dev/random).
- `EncryptionConfig.validateKey()` blocks startup if key is the default placeholder.

### Backend: Exception Handling
- GlobalExceptionHandler catches known types:
  - `MethodArgumentNotValidException` / `IllegalArgumentException` → 400
  - `NoSuchElementException` → 404
  - `DuplicateKeyException` / `IllegalStateException` → 409
  - `DatasourceUnavailableException` → 503
- Service methods throw typed exceptions. Never `catch (Exception e)` generically — always catch specific types.
- Encryption errors wrap with original exception class name: `"Encryption failed [" + e.getClass().getSimpleName() + "]: " + e.getMessage()`
- All exception handlers log the exception with `logger.error()` — never silent handling.

### Backend: DTO / Entity Separation
- **CreateDTO** — Input for creation. Jakarta validation annotations (`@NotBlank`, `@NotNull`, `@Size`, `@Email`).
- **UpdateDTO** — Input for updates. Can have partial/nullable fields (service merges with existing entity).
- **Entity** — DB-mapped fields only. No validation annotations.
- Never reuse DTOs as entities or vice versa.
- Use `@NotBlank` for String fields, not `@Size(min=1)`.

### Backend: Interface Status State Machine
```
PENDING_TEST ──→ ONLINE ──→ OFFLINE
      ↑            ↑            │
      └────────────┴────────────┘  (ONLINE ↔ OFFLINE allowed)
```
- DISABLED 数据源的接口无法测试/上线。
- 删除接口时校验非 ONLINE 状态。
- 数据源禁用时通过 `DataSourceEventPublisher` 将关联接口自动设为 OFFLINE。
- Cascade delete: 删除接口时先删 `interface_param`，再删 `api_interface`。

### Backend: JWT Authentication
- `SecurityConfig` 配置 Spring Security: stateless session, CORS (localhost:5173), JWT decoder (RS256).
- `/api/auth/login` 公开; 其余端点需要认证。
- `AuthController`: `/login` 验证用户名密码 (BCrypt)，返回 JWT access token (1800s) + refresh token (604800s); `/logout` 从 Redis 移除 jti; `/me` 返回当前用户信息。
- `JwtTokenService`: RSA256 签名, jti (UUID), claim `role`。
- `RedisTokenService`: `token:{jti}` → TokenSession, TTL 自动过期。
- `TokenValidationFilter`: 每次请求验证 Redis 中存在对应 jti (实现登出/撤销); Redis 不可用时降级放行。
- 密码加密: `BCryptPasswordEncoder`（AuthController 和 DataInitializer 中使用）。
- `RsaKeyConfig`: 从 `atlas.jwt.private-key` / `atlas.jwt.public-key` 加载 RSA key。

### Backend: Redis
- `RedisConfig`: `redisTokenTemplate` bean (String → TokenSession), Jackson JSON 序列化。
- `RedisStartupValidator` (`run/config/`): 启动时 ping Redis, 不可用则 warn (不阻塞启动)。
- Redis 不可用时 Token 撤销功能降级 (TokenValidationFilter 放行)。
- 配置: `spring.data.redis.*` (默认 localhost:6379)。

### Backend: Audit Interceptor
- `AuditInterceptor`: MyBatis 拦截器，自动填充 `createdBy`/`createdAt` (INSERT) 和 `lastModifiedBy`/`updatedAt`/`lastModifiedAt` (UPDATE)。
- 通过 `SecurityUtil.getCurrentUsername()` 获取当前用户名; 无认证上下文时使用 "SYSTEM"。
- 支持普通 entity 参数、List、Map (`@Param`) 三种形式。
- 使用 `safeSetValue` 优雅处理字段缺失（如 User entity 使用 `lastModifiedAt` 而非 `updatedAt`）。

### Backend: User Management
- `UserService`: CRUD + `getUserByUsername()`（登录用）。
- `UserController`: CRUD 端点 (admin-only, `@PreAuthorize("hasRole('ADMIN')")`)。
- `DataInitializer` (`@Profile("!test")`): 首次启动时创建 admin 用户；密码来自 `atlas.admin.default-password` (可配置，随机 fallback)。
- 密码永远不暴露给客户端 (`UserInfoDTO` 不含 password 字段)。
- User entity 支持 `ENABLED` / `DISABLED` status。

### Backend: SecurityUtil
- 静态工具类：`SecurityUtil.getCurrentUsername()` 返回当前认证用户名，无上下文时返回 `"SYSTEM"`。
- 用于 `AuditInterceptor` 和业务层中需要获取当前操作者的场景。
- 不是 Spring Bean — 直接静态调用。

### Frontend: Vue 3 + TypeScript
- ALWAYS use `<script setup lang="ts">` — no Options API.
- Define props with `defineProps<{...}>()` and emits with `defineEmits<[...]>`.
- Use `ref`/`reactive` for state, `computed` for derived state.
- Type all reactive variables explicitly: `const x = ref<string>('')`.

### Frontend: Naive UI Usage
- Naive UI 使用 **`create()` API 按需注册组件**（不再是全局 `app.use(naive)`），在 `src/utils/naive-ui.ts` 中维护组件列表。
- **`main.ts` 中只 `app.use(naiveUiPlugin)`**，不单独注册任何组件。
- 每个 .vue 文件仍需显式 `import { NButton } from 'naive-ui'`（tree-shaking 需要）。
- 使用 `h()` render 函数（而非 template）实现 DataTable 列渲染、自定义触发器。
- `NPopconfirm` 用于删除确认：`h(NPopconfirm, { onPositiveClick: () => handleDelete(row) }, { trigger: () => h(NButton, ...), default: () => '确定删除？' })`。
- `NDataTable` 的 `empty` prop 传 render function：`:empty="() => h(NEmpty, { description: '暂无数据' })"`（不可直接传 VNode）。
- `createDiscreteApi()` 在 `request.ts` 中独立使用，与 `create()` 兼容。

### Frontend: Pinia Stores
- Composition API style: `export const useXxxStore = defineStore('xxx', () => { ... })`.
- State: `ref` for primitives/arrays, `reactive` for objects.
- Actions: `async function` returning typed results.
- Getters: `computed` for derived state.
- Export everything in return object.
- Loading state bound to `NDataTable :loading`.

### Frontend: Auth Store & Flow
- `auth.ts` store: `token` (localStorage), `currentUser`, `isAuthenticated` (computed from token), `isAdmin` (computed from role)。
- `login()`: POST /api/auth/login → 保存 token 到 localStorage + store → 路由跳转到 /datasource。
- `logout()`: POST /api/auth/logout → 清理 token → 路由跳转到 /login。
- `fetchMe()`: GET /api/auth/me → 更新 currentUser (页面刷新后恢复 session)。
- Token 持久化: localStorage `token` key; request interceptor 自动添加 `Authorization: Bearer {token}`。
- 401 响应: response interceptor 自动清理 token 并重定向到 /login。

### Frontend: Axios + Request Utility
- Single Axios instance in `src/utils/request.ts` with `baseURL: '/api'`, `timeout: 30000`.
- Request interceptor: attaches `Bearer` token from localStorage.
- Response interceptor: 检查 `body.code >= 400` 时显示错误消息 (`NMessage.error()`) 并 reject; 401 时清理 token 并跳转登录。
- Stores call `request.get/post/put/delete` and read `res.data.data` for payload, `res.data.total || 0` for pagination total.
- NEVER access `res.data.data.data` — the interceptor already returns the R envelope.
- Component catch blocks: `console.warn('Operation failed:', e)` + comment `// handled by interceptor`.

### Frontend: Routing
- Hash mode (`createWebHashHistory`) — no server config needed.
- Auth guard in `router.beforeEach`: 未认证 → 重定向到 /login; token 存在但 currentUser 为空 → 调用 `fetchMe()`; admin-only 路由 check `isAdmin`。
- Public routes: `/login` (no auth check).
- Parent-child routes: `/datasource`, `/interface`, `/user` are parent routes using `BaseLayout.vue`, with children for sub-pages.
- Route definitions in `router/index.ts` with lazy imports: `component: () => import('@/views/...')`.
- 404 catch-all route `/:pathMatch(.*)*` → `NotFound.vue`.

### Frontend: Pagination
- `NDataTable` pagination prop is a **plain JS object** — Vue template kebab-to-camel conversion does NOT apply:
  - ✅ `'onUpdate:pageSize': (size: number) => { ... }` (quoted)
  - ✅ `'onUpdate:page': (page: number) => { ... }` (quoted)
  - ❌ `onUpdatePageSize` / `onUpdate:pageSize` (unquoted) — will NOT work
- Frontend stores use `res.data.total || 0` for total count.
- NEVER fall back to `res.data.pageSize`.

### Frontend: Error Handling Patterns
- Global: Axios interceptor `message.error()` for API errors.
- Component-level: `try { ... } catch (e) { console.warn('描述:', e) }` — never empty catch.
- Delete operations: `deletingId` ref + `loading: deletingId === row.id` + `finally { deletingId = null }`.
- Global Vue errors: `app.config.errorHandler` in `main.ts`.

## TESTING STANDARDS

### Backend

| 层 | 测试类型 | 框架 | 位置 |
|----|---------|------|------|
| **Mapper** | 集成测试 | `@MybatisTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@ActiveProfiles("test")` | `src/test/java/.../mapper/` |
| **Service** | 纯单元测试 | `@ExtendWith(MockitoExtension.class)` + Mockito | `src/test/java/.../service/` |
| **Controller** | 集成测试 | `@SpringBootTest` + `@AutoConfigureMockMvc` | `src/test/java/.../controller/` |
| **Config / Util** | 纯单元测试 | Mockito (无 Spring 上下文) | `src/test/java/.../config/` |

- 测试数据库：H2 in-memory (MySQL 模式), `application-test.yml`
- 测试 Schema：`schema-test.sql`（自动初始化）
- 运行命令：`mvn test -Dspring.profiles.active=test`
- Naming: `{MethodName}_{Scenario}_Returns{Expected}` (e.g., `delete_OnlineInterface_ThrowsIllegalStateException`)
- Mock external clients (Elasticsearch, remote datasources) with Mockito — never connect to real instances in tests.
- PageHelper tests: verify `PageHelper.startPage()` is called before `selectList()`.
- Test exception paths: verify typed exceptions are thrown with proper messages.
- Mock 静态方法（如 `EncryptionUtil`）使用 `Mockito.mockStatic()`（`@BeforeEach` 初始化，`@AfterEach` 关闭）。
- Controller 集成测试需要 `@WithMockUser` 或提供 JWT token 来通过 Security 过滤链。

### Frontend

| 层 | 测试类型 | 框架 | 位置 |
|----|---------|------|------|
| **Store** | 纯逻辑单元测试 | Vitest + `vi.mock('@/utils/request')` | `src/stores/__tests__/*.spec.ts` |
| **Util** | 纯逻辑单元测试 | Vitest | `src/utils/__tests__/*.spec.ts` |
| **Component (.vue)** | 组件挂载测试 | Vitest + `@vue/test-utils` | `src/views/*/__tests__/*.spec.ts` |
| **Router / Guard** | 纯逻辑单元测试 | Vitest + `vue-router` mock | `src/router/__tests__/*.spec.ts` |

- 框架：Vitest v4.x + `@vue/test-utils` + `jsdom`
- 配置文件：`vitest.config.ts`（别名 `@` → `src/`，环境 `jsdom`，V8 coverage）
- 运行命令：`npm run test:run` (CI) 或 `npm test` (watch)
- Store 测试：`setActivePinia(createPinia())` each `beforeEach`, mock request utility
- Util 测试：覆盖正常/边界/异常输入
- 方法命名：`it('action name - scenario - expected')`

## ANTI-PATTERNS

- Do NOT add sample/demo code — this is a scaffold.
- Do NOT mix frontend/backend concerns across module boundaries.
- Do NOT commit `.env` files with credentials (`.env.*.local` is gitignored).
- Do NOT use `as any` or `@ts-ignore` in frontend — fix the type properly.
- Do NOT use `synchronized(String.intern())` — use `ConcurrentHashMap.computeIfAbsent()` with explicit lock objects.
- Do NOT `catch (Exception e)` broadly — always catch specific exception types.
- Do NOT duplicate AES/GCM cipher code — always use `EncryptionUtil`.
- Do NOT downcast `ApplicationContext` to `GenericApplicationContext` in constructor — accept the concrete type directly.
- Do NOT hardcode protocol strings in client factories — use `@Value("${...}")` for configurable properties.
- Do NOT use `@Autowired` on fields — always constructor injection.
- Do NOT add `@Transactional` to controller methods — it belongs in the service layer.
- Do NOT use `@Size(min = 1)` on String fields — use `@NotBlank`.
- Do NOT commit default encryption keys — `EncryptionConfig.validateKey()` enforces this.
- Do NOT hardcode JWT RSA keys in code — read from `atlas.jwt.*` properties (validated by `RsaKeyConfig`).
- Do NOT expose password hashes in API responses — use `UserInfoDTO` instead of `User` entity.
- Do NOT use `PasswordEncoder` in controllers directly — delegate to service layer.
- Do NOT block startup on Redis unavailability — `RedisStartupValidator` only warns; `TokenValidationFilter` degrades gracefully.
- Do NOT put admin user seeding in SQL schema — use `DataInitializer` (respects profile and configurable password).
- Do NOT ignore MyBatis interceptor failures — `AuditInterceptor` logs and swallows to avoid blocking DB operations.
- Do NOT use Options API — use `<script setup lang="ts">`.
- Do NOT use `res.data.total || res.data.pageSize || 0` — always `res.data.total || 0`.
- Do NOT leave empty catch blocks — always `console.warn('Operation failed:', e)`.
- Do NOT hardcode API URLs in components — use the `request` utility.
- Do NOT use `onUpdatePageSize` in NDataTable pagination JS object — use `'onUpdate:pageSize': (size) => {}` (quoted).
- Do NOT import Naive UI `as any` globals — use per-component imports + `create()` API.

## COMMANDS

```bash
# Backend
cd backend && mvn spring-boot:run         # Start server on :8080
cd backend && mvn compile                 # Compile only
cd backend && mvn test -Dspring.profiles.active=test  # Full test suite
cd backend && mvn clean compile           # Fresh compile

# Backend with local profile
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run

# Frontend
cd frontend && npm run dev                # Vite dev server (default :5173)
cd frontend && npm run build              # Type-check + production build
cd frontend && npm run type-check         # vue-tsc only
cd frontend && npm run test:run           # Vitest (CI mode)
cd frontend && npm test                   # Vitest (watch mode)
```

### Local Development Prerequisites
```bash
# Docker MySQL 容器（预存在，不得自动创建）
docker ps --filter name=api-atlas-mysql --format '{{.Names}}' || echo "WARN: container NOT running"
docker start api-atlas-mysql

# Local Redis（可选 — 不启动则 token 撤销功能降级）
redis-cli ping || echo "WARN: Redis not running — token revocation will be degraded"

# 启动后端
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## NOTES

- MySQL expected at `localhost:3306`, database `api_atlas`, user `root`/`root` (default) or `api_atlas` (docker).
- Redis expected at `localhost:6379` — token session store. If unavailable, token revocation is degraded (non-blocking).
- Spring Boot 4.1 requires Java 17+ (JDK 21 confirmed). Maven 3.9+.
- PageHelper 4.1.1 is compatible with MyBatis-Sprint-Boot 4.0.1 and Spring Boot 4.x.
- Naive UI v2.44.1 is a large library — production build chunk was ~309 kB after tree-shaking (from ~711 kB raw).
- Type aliases in `com.api.atlas.model` — MyBatis XML can use short entity names.
- Underscore-to-camel auto-enabled via MyBatis config.
- `as any` and `@ts-ignore` are banned in TypeScript — use proper types or `unknown` with type guards.
- The database password in `application.yml` is externalized via `SPRING_DATASOURCE_PASSWORD` env var. Never hardcode in git-tracked files.
- JWT RSA keys (public/private) must be configured via `atlas.jwt.public-key` / `atlas.jwt.private-key` in `application-local.yml` (not in the tracked `application.yml`).
- `run/config/` package is for startup validators and lifecycle hooks that should not be auto-scanned with main config beans.
- Dependencies include MySQL, PostgreSQL, and Elasticsearch 8.16 — all runtime-optional except MySQL (primary) and Redis (token store).
