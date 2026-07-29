# API Atlas — Backend Knowledge Base

Spring Boot 4.1 + JDK 21 + MyBatis + PageHelper + MySQL + Redis.

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Add REST endpoint | `.../controller/` | New `@RestController`, inject service, return `R<T>` |
| Add MyBatis mapper | `.../mapper/` + `resources/mapper/*.xml` | Interface + XML pair by entity name |
| Add service | `.../service/` | `@Service @Transactional`, constructor injection |
| Add entity | `.../model/` | POJO matching DB table (underscore-to-camel auto) |
| Add DTO | `.../model/` | Separate from entity, request/response objects |
| Add configuration | `.../config/` | `@Configuration`, `@Component`, utility classes |
| Add executor | `.../service/executor/` | Implements query execution (SQL, ES, IBATIS) |
| Add startup validator | `.../run/config/` | `@Component` + `@PostConstruct` for startup checks |
| Add auth endpoint | `.../controller/AuthController.java` | Login (BCrypt + JWT), logout, me |
| Add user CRUD | `.../controller/UserController.java` + `.../service/UserService.java` | Admin-only RBAC |
| Add JWT token service | `.../service/JwtTokenService.java` | RSA256 token generation |
| Add Redis token ops | `.../service/RedisTokenService.java` | JWT jti → TokenSession CRUD |
| Add audit interceptor | `.../config/AuditInterceptor.java` | MyBatis interceptor for audit fields |
| Add data initializer | `.../config/DataInitializer.java` | Seeds admin user on empty DB |
| Encryption | Use `EncryptionUtil` | Never duplicate AES code |
| Security config | `.../config/SecurityConfig.java` | Filter chain, CORS, JWT decoder |
| RSA key config | `.../config/RsaKeyConfig.java` | PEM → RSAPrivateKey/RSAPublicKey |
| Redis config | `.../config/RedisConfig.java` | Jackson-serialized RedisTemplate bean |
| SecurityUtil | `.../config/SecurityUtil.java` | Static helper — current username from context |
| Update DB config | `src/main/resources/application.yml` | Datasource, Redis, MyBatis, PageHelper, atlas.* |
| Local overrides | `src/main/resources/application-local.yml` | Local passwords, keys (gitignored) |

## CONVENTIONS

### Package Layering
- **controller/** — REST endpoints only. Delegate to service, return `R<T>`. One `@RequestMapping` class per domain.
- **service/** — Business logic + `@Transactional` boundary. Constructor injection. No request/response objects in signatures (DTOs in → entities out).
- **mapper/** — MyBatis interfaces. One per entity. XML SQL in `resources/mapper/`.
- **model/** — Entity + DTO classes. POJOs, no JPA annotations. Underscore-to-camel auto-enabled via MyBatis config.
- **config/** — `@Configuration`, `@Component`, utility classes. Includes factory registry for multi-datasource support.
- **run/config/** — Startup-time components (Redis connectivity check), separated to avoid auto-scanning with main config beans.
- **service/executor/** — Query executors (DatabaseQueryExecutor for SQL/IBATIS, ElasticsearchQueryExecutor for ES|QL/Query DSL).

### Dependency Injection
- ALWAYS constructor injection. NEVER `@Autowired` on fields.
- `@Service` + `@Transactional(readOnly = true)` at class level for read-write services.
- Write methods override with `@Transactional(readOnly = false)` or just `@Transactional`.

### R Envelope
- ALL controller methods return `R<T>` from `com.api.atlas.model.R`.
- Paginated results: `R.ok(list, pageInfo)`.
- Frontend reads `res.data.data` for payload, `res.data.total` for total.

### DTO / Entity Separation
- **CreateDTO** — Input for creation. Validation annotations (`@NotBlank`, `@NotNull`, `@Email`).
- **UpdateDTO** — Input for updates. Same pattern, nullable fields.
- **Entity** — DB-mapped fields only. No validation annotations.
- Never reuse DTOs as entities or vice versa.
- Use `@NotBlank` for String fields, not `@Size(min=1)`.

### Encryption
- ALL AES/GCM/NoPadding goes through `EncryptionUtil.encrypt()`/`decrypt()`.
- `SecretKey` is a Spring bean from `EncryptionConfig`. Injected via constructor.
- Algorithm: AES/GCM/NoPadding, 12-byte IV, 128-bit tag, IV prepended to ciphertext, Base64-encoded.

### DataSource Factory Pattern
- `DataSourceFactory<T>` generic interface with `createClient()` and `destroyClient()` methods.
- `DataSourceFactoryRegistry` holds `ConcurrentHashMap<String, DataSourceFactory<?>>` for type-safe factory lookup.
- `DataSourceClientManager` manages lifecycle: enable/disable datasources, registers beans in Spring context via `GenericApplicationContext`.
- Thread safety: use `ConcurrentHashMap.computeIfAbsent()` for lock objects, NOT `synchronized(String.intern())`.

### Executor Patterns
- `DatabaseQueryExecutor` — executes SQL (JdbcTemplate) and IBATIS (MyBatis XMLBuilder dynamic parse + `SqlSessionFactory`).
  - SQL: manual `PreparedStatement` + LIMIT/OFFSET pagination. **Not using PageHelper**.
  - IBATIS: in-memory pagination with `maxMemoryRows` upper bound.
- `ElasticsearchQueryExecutor` — executes ES|QL and Query DSL via `esClient`.
  - ES|QL: `${param}` → `?` position replacement → `esClient.sql().query()`.
  - Query DSL: Jackson `JsonNode` field replacement for `${param}` → `esClient.search()`, auto-strips `index` from body.
- Executors are independent classes selected by `queryType` runtime switch; they do NOT share a common interface.

### Exception Handling
- `GlobalExceptionHandler` catches known types:
  - `MethodArgumentNotValidException` / `IllegalArgumentException` → 400
  - `NoSuchElementException` → 404
  - `DuplicateKeyException` / `IllegalStateException` → 409
  - `DatasourceUnavailableException` → 503
- Service methods throw typed exceptions. Never `catch (Exception e)` generically.
- Encryption errors wrap with original exception class name.
- All exception handlers log with `logger.error()` — never silent handling.

### JWT Authentication
- `SecurityConfig` sets up Spring Security:
  - Stateless session (`SessionCreationPolicy.STATELESS`).
  - CORS allows `localhost:5173`.
  - JWT decoder (RS256) via `NimbusJwtDecoder.withPublicKey()`.
  - `/api/auth/login` and `OPTIONS /**` are public.
  - `TokenValidationFilter` added after `BasicAuthenticationFilter` — validates jti exists in Redis.
- `AuthController`:
  - `/login`: BCrypt password check → generate access+refresh tokens → save TokenSession to Redis → return LoginResponse.
  - `/logout`: Read jti from JWT → remove from Redis.
  - `/me`: Return current user info (via `UserInfoDTO` — never expose password hash).
- `JwtTokenService`:
  - RSA256 signing via `NimbusJwtEncoder` with `ImmutableJWKSet`.
  - `generateAccessToken(username, role)`: subject=username, jti=UUID, claim `role`, 1800s TTL.
  - `generateRefreshToken(username)`: subject=username, jti=UUID, 604800s TTL.
- `RedisTokenService`:
  - `token:{jti}` → TokenSession (user info + role + createdAt).
  - TTL auto-expiry via `redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS)`.
- `TokenValidationFilter` (`OncePerRequestFilter`):
  - Checks Redis for token jti on every authenticated request.
  - Token not found → 401 with `"Token has been revoked"`.
  - Redis unavailable → warn and allow through (degraded mode).
- `RsaKeyConfig`: Loads PEM strings from `atlas.jwt.private-key` / `atlas.jwt.public-key` via `@Value`, validates at startup.

### Redis
- `RedisConfig`: Provides `redisTokenTemplate` bean (`@Qualifier("redisTokenTemplate")`).
  - Key serializer: `StringRedisSerializer`.
  - Value serializer: `JacksonJsonRedisSerializer<>(TokenSession.class)`.
- `RedisStartupValidator` (in `run/config/`): `@PostConstruct` pings Redis; warns if unavailable (does NOT block startup).
- Redis unavailability is tolerated — only token revocation degrades.
- Configured via `spring.data.redis.*` (default localhost:6379, timeout 2s, lettuce pool 8/4/1).

### Audit Interceptor
- `AuditInterceptor`: MyBatis `@Interceptor` on `Executor.update()`.
- Auto-populates:
  - INSERT: `createdBy`, `createdAt`, `lastModifiedBy`, `updatedAt`, `lastModifiedAt`.
  - UPDATE: `lastModifiedBy`, `updatedAt`, `lastModifiedAt`.
- Username from `SecurityUtil.getCurrentUsername()` (falls back to `"SYSTEM"`).
- Handles entity, List, and `@Param` Map parameter types.
- `safeSetValue()` — sets field only if setter exists (graceful for entities with different field names like User's `lastModifiedAt`).
- Failures are logged and swallowed — never block DB operations.

### User Management
- `UserService`: CRUD + `getUserByUsername()` for login.
- `UserController`: `@PreAuthorize("hasRole('ADMIN')")` on all endpoints.
- `DataInitializer` (`@Profile("!test")`, `CommandLineRunner`):
  - Creates admin user on first startup (empty DB).
  - Password from `atlas.admin.default-password` (must not be `"CHANGE_ME"`).
  - Falls back to random UUID password (logged as warning).
- `UserInfoDTO` used for API responses — password field is NEVER exposed.
- User entity supports `ENABLED` / `DISABLED` status.

### Interface Status State Machine
```
PENDING_TEST ──→ ONLINE ──→ OFFLINE
      ↑            ↑            │
      └────────────┴────────────┘  (ONLINE ↔ OFFLINE allowed)
```
- DISABLED datasource → interfaces cannot be tested or brought ONLINE.
- Delete checks non-ONLINE status.
- Datasource disable → `DataSourceEventPublisher` auto-sets interfaces to OFFLINE.
- Cascade delete: delete `interface_param` first, then `api_interface`.

### SecurityUtil
- Static utility class (`SecurityUtil.getCurrentUsername()`).
- Returns current authenticated username, or `"SYSTEM"` when no auth context.
- Not a Spring Bean — used directly from static context (e.g., AuditInterceptor).

## TESTING SCOPE

新功能（Controller / Service / Mapper / DTO / Executor / Config）完成后必须编写对应测试。

### Layer Test Rules

| 层 | 测试类型 | 框架 | 位置 |
|----|---------|------|------|
| **Mapper** | 集成测试 | `@MybatisTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@ActiveProfiles("test")` | `src/test/java/.../mapper/` |
| **Service** | 纯单元测试 | `@ExtendWith(MockitoExtension.class)` + Mockito | `src/test/java/.../service/` |
| **Controller** | 集成测试 | `@SpringBootTest` + `@AutoConfigureMockMvc` | `src/test/java/.../controller/` |
| **Config / Util** | 纯单元测试 | Mockito (无 Spring 上下文) | `src/test/java/.../config/` |

### 测试配置

- 测试配置文件：`src/test/resources/application-test.yml`（H2 内存数据库，MySQL 模式）
- 测试 schema：`src/test/resources/schema-test.sql`（自动初始化）
- 运行命令：`mvn test -Dspring.profiles.active=test`
- H2 依赖已在 `pom.xml` 中配置（scope: test）

### Service 测试规范（纯 Mockito）

```
@ExtendWith(MockitoExtension.class)
class XxxServiceTest {
    @Mock private XxxMapper xxxMapper;
    @Mock private SecretKey secretKey;  // 如果有加密
    @InjectMocks private XxxService xxxService;
}
```

- 使用 `Mockito.mockStatic()` 对 `EncryptionUtil` 等静态方法做 mock（`@BeforeEach` 初始化，`@AfterEach` 关闭）
- `PageHelper.startPage()` 无需 mock，可在单元测试中真实运行
- `thenReturn(existing, updated)` 处理多次调用的链式 mock

### Mapper 测试规范（@MybatisTest + H2）

```java
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class XxxMapperTest {
    @Autowired private XxxMapper xxxMapper;
}
```

- 每个测试方法插入独立数据，H2 自动回滚
- 使用 `System.nanoTime()` 生成唯一名称，避免唯一键冲突
- 测试所有 CRUD 操作：insert → selectById → selectList → updateById → deleteById

### Controller 测试规范（@SpringBootTest + MockMvc）

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class XxxControllerTest {
    @Autowired private MockMvc mockMvc;
}
```

- 使用 `@WithMockUser` 或提供 JWT token 以通过 Security 过滤链
- 测试 HTTP status、响应 body、业务异常映射

### 方法命名规范

```
{MethodName}_{Scenario}_Returns{Expected}
```

示例：
- `create_ValidDTO_ReturnsDataSourceWithEncryptedPassword`
- `getById_NonExistingId_ThrowsNoSuchElementException`
- `delete_HasInterfaces_ThrowsIllegalStateException`

### 最低覆盖要求

- **Service**: 每个公开方法至少一个 happy-path 测试 + 一个异常路径测试（如 `NoSuchElementException`、`IllegalStateException`）
- **Mapper**: 每个公开方法至少一个基本 CRUD 测试
- **Util**: 每个公开方法至少 3 个场景（正常输入、边界输入、异常输入）
- **Controller**: 每个端点至少一个 happy-path 测试 + 一个错误场景测试

### 验收标准

```bash
# 全部后端测试
mvn test -Dspring.profiles.active=test
# 应看到 BUILD SUCCESS，Tests run > 0，Failures: 0，Errors: 0
```

## ANTI-PATTERNS

- Do NOT use `synchronized(String.intern())` — use `ConcurrentHashMap.computeIfAbsent()` with explicit lock objects.
- Do NOT `catch (Exception e)` broadly — always catch specific exception types.
- Do NOT duplicate AES cipher code — always use `EncryptionUtil`.
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

## COMMANDS

```bash
cd backend
mvn spring-boot:run                 # Start server on :8080
mvn compile                         # Compile only
mvn test -Dspring.profiles.active=test  # Run tests
mvn clean compile                   # Fresh compile
```

### Local Development

本地 MySQL 由已存在的 Docker 容器 `api-atlas-mysql` 提供，启动前必须检查容器状态：

```bash
# 1. 检查容器是否已运行
docker ps --filter name=api-atlas-mysql --format '{{.Names}}' || echo "WARN: container NOT running"
docker start api-atlas-mysql

# 2. Redis（可选 — 不启动则 token 撤销功能降级）
redis-cli ping || echo "WARN: Redis not running — token revocation will be degraded"

# 3. 启动 with local profile (uses application-local.yml, gitignored)
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

> **注意**: 容器 `api-atlas-mysql` 是预先存在的，启动后端前应先确认容器正在运行，绝对不可自动创建新容器。
> **Security**: The database password in `application.yml` is externalized via `SPRING_DATASOURCE_PASSWORD` env var. Never hardcode credentials in version-control-tracked files. JWT RSA keys live in `application-local.yml`.

## NOTES

- MySQL: running Docker container `api-atlas-mysql` at `localhost:3306`, database `api_atlas`, user `api_atlas`. 该容器为预先创建，启动后端前必须确认。
- Redis expected at `localhost:6379` — token session store. If unavailable, token revocation is degraded (non-blocking).
- Spring Boot 4.1 requires Java 17+ (JDK 21 confirmed). Maven 3.9+.
- PageHelper 4.1.1 is compatible with MyBatis-Spring-Boot 4.0.1 and Spring Boot 4.x.
- Type aliases: `com.api.atlas.model` — MyBatis XML can use short entity names.
- Underscore-to-camel auto-enabled via MyBatis config.
- Dependencies include MySQL, PostgreSQL, and Elasticsearch 8.16 — all runtime-optional except MySQL (primary) and Redis (token store).
