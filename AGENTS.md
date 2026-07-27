# API Atlas — Knowledge Base

Split full-stack scaffold: Spring Boot 4.1 + JDK 21 + MyBatis + PageHelper (backend/), Vue 3 + Naive UI + Vite + TypeScript (frontend/).

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
├── backend/                   # Spring Boot 4.1, Maven, Java 21
│   ├── pom.xml                # Dependencies: web, mybatis 4.0.1, pagehelper 4.1.1, mysql
│   └── src/main/
│       ├── java/com/api/atlas/
│       │   ├── ApiAtlasApplication.java   # @SpringBootApplication entry
│       │   ├── controller/                # REST controllers
│       │   ├── service/                   # Business logic + executors
│       │   │   └── executor/              # DatabaseQueryExecutor, ElasticsearchQueryExecutor
│       │   ├── mapper/                    # MyBatis mapper interfaces
│       │   ├── model/                     # Entity/DTO classes
│       │   └── config/                    # @Configuration, EncryptionUtil, factories
│       └── resources/
│           ├── application.yml            # MySQL, MyBatis, PageHelper
│           └── mapper/                    # MyBatis XML mappers
├── frontend/                  # Vue 3, Vite 8, TypeScript 6, Naive UI 2.44
│   ├── src/
│   │   ├── main.ts            # App entry — createApp + NaiveUI (tree-shaken)
│   │   ├── App.vue            # Root component, NConfigProvider wrapper
│   │   ├── layouts/           # BaseLayout.vue — sidebar + header + router-view
│   │   ├── views/             # datasource/, interface/ pages
│   │   ├── stores/            # Pinia composition stores
│   │   ├── router/            # Hash-mode router, parent routes with BaseLayout
│   │   ├── utils/             # Axios request utility, Naive UI component register
│   │   └── components/        # Reusable components
│   ├── index.html
│   ├── vite.config.ts         # @ alias, vue + devtools plugins, proxy
│   ├── tsconfig*.json         # TS 6, node24 target, strict: true
│   └── package.json
├── doc/prd/                   # PRD 文档
└── .gitignore                 # 3-tier: root (global) / backend (Java) / frontend (Node)
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
| Update DB config | `backend/src/main/resources/application.yml` | Datasource, MyBatis, PageHelper, executor |
| Add Vue page | `frontend/src/views/` | New .vue, add route in `router/index.ts` |
| Add Pinia store | `frontend/src/stores/` | Composition API, `export const useXxxStore = defineStore('xxx', () => {...})` |
| Add API utility | `frontend/src/utils/request.ts` | Axios instance with interceptors |
| Global frontend config | `frontend/vite.config.ts` | Aliases, plugins, proxy |
| Frontend dependencies | `frontend/package.json` | Vue 3.5, Naive UI 2.44 |

## CONVENTIONS

### Backend: Package Layering

- **controller/** — REST endpoints only. Delegate to service, return `R<T>`. No business logic.
- **service/** — Business logic + `@Transactional` boundary. Constructor injection. No request/response objects (DTOs in → entities out).
- **mapper/** — MyBatis interfaces. One per entity. XML in `resources/mapper/`.
- **model/** — Entity + DTO classes. No JPA annotations (MyBatis-only). DTOs are separate from entities.
- **config/** — @Configuration, @Component, utility classes. Includes factory registry for multi-datasource support.
- **service/executor/** — Query executors (DatabaseQueryExecutor for SQL/IBATIS, ElasticsearchQueryExecutor for ES|QL/Query DSL).

### Backend: DataSource Type System
- DataSource type 使用 **String 而非 Java Enum**，通过 `DataSourceFactoryRegistry` + `ConcurrentHashMap<String, DataSourceFactory<?>>` 实现可扩展工厂模式。
- 新增数据源类型只需实现 `DataSourceFactory<T>` 接口并注册到 registry，无需修改现有代码。

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

### Frontend: Vue 3 + TypeScript
- ALWAYS use `<script setup lang="ts">` — no Options API.
- Define props with `defineProps<{...}>()` and emits with `defineEmits<[...]>`.
- Use `ref`/`reactive` for state, `computed` for derived state.
- Type all reactive variables explicitly: `const x = ref<string>('')`.

### Frontend: Naive UI Usage
- Naive UI 使用 **`create()` API 按需注册组件**（不再是全局 `app.use(naive)`），在 `src/utils/naive-ui.ts` 中维护组件列表。
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

### Frontend: Axios + Request Utility
- Single Axios instance in `src/utils/request.ts` with `baseURL: '/api'`, `timeout: 30000`.
- Response interceptor: unwraps `R<T>` envelope — returns `response.data` (the `R` object), handles global error display via `message.error()`.
- Stores call `request.get/post/put/delete` and read `res.data.data` for payload, `res.data.total || 0` for pagination total.
- NEVER access `res.data.data.data` — the interceptor already returns the R envelope.
- Component catch blocks: `console.warn('Operation failed:', e)` + comment `// handled by interceptor`.

### Frontend: Routing
- Hash mode (`createWebHashHistory`) — no server config needed.
- Parent-child routes: `/datasource` and `/interface` are parent routes using `BaseLayout.vue`, with children for sub-pages.
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
| **Util / Config** | 纯单元测试 | Mockito (无 Spring 上下文) | `src/test/java/.../config/` |

- 测试数据库：H2 in-memory (MySQL 模式), `application-test.yml`
- 测试 Schema：`schema-test.sql`（自动初始化）
- 运行命令：`mvn test -Dspring.profiles.active=test`
- Naming: `{MethodName}_{Scenario}_Returns{Expected}` (e.g., `delete_OnlineInterface_ThrowsIllegalStateException`)
- Mock external clients (Elasticsearch, remote datasources) with Mockito — never connect to real instances in tests.
- PageHelper tests: verify `PageHelper.startPage()` is called before `selectList()`.
- Test exception paths: verify typed exceptions are thrown with proper messages.
- Mock 静态方法（如 `EncryptionUtil`）使用 `Mockito.mockStatic()`（`@BeforeEach` 初始化，`@AfterEach` 关闭）。

### Frontend

| 层 | 测试类型 | 框架 | 位置 |
|----|---------|------|------|
| **Store** | 纯逻辑单元测试 | Vitest + `vi.mock('@/utils/request')` | `src/stores/__tests__/*.spec.ts` |
| **Util** | 纯逻辑单元测试 | Vitest | `src/utils/__tests__/*.spec.ts` |
| **Component (.vue)** | 组件挂载测试 | Vitest + `@vue/test-utils` | `src/components/__tests__/*.spec.ts` |

- 框架：Vitest v4.x + `@vue/test-utils` + `jsdom`
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

# 启动后端
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## NOTES

- MySQL expected at `localhost:3306`, database `api_atlas`, user `root`/`root` (default) or `api_atlas` (docker).
- Spring Boot 4.1 requires Java 17+ (JDK 21 confirmed). Maven 3.9+.
- PageHelper 4.1.1 is compatible with MyBatis-Sprint-Boot 4.0.1 and Spring Boot 4.x.
- Naive UI v2.44.1 is a large library — production build chunk was ~309 kB after tree-shaking (from ~711 kB raw).
- Type aliases in `com.api.atlas.model` — MyBatis XML can use short entity names.
- Underscore-to-camel auto-enabled via MyBatis config.
- `as any` and `@ts-ignore` are banned in TypeScript — use proper types or `unknown` with type guards.
- The database password in `application.yml` is externalized via `SPRING_DATASOURCE_PASSWORD` env var. Never hardcode in git-tracked files.
