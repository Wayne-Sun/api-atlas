# API Atlas — Backend Knowledge Base

Spring Boot 4.1 + JDK 21 + MyBatis + PageHelper + MySQL.

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
| Update DB config | `src/main/resources/application.yml` | Datasource, MyBatis, PageHelper |
| Encryption | Use `EncryptionUtil` | Never duplicate AES code |
| Elasticsearch | `.../config/ElasticsearchClientFactory.java` | Protocol configurable via `atlas.elasticsearch.protocol` |

## CONVENTIONS

### Package Layering
- **controller/** — REST endpoints only. Delegate to service, return `R<T>`. One `@RequestMapping` class per domain.
- **service/** — Business logic + `@Transactional` boundary. Constructor injection. No request/response objects in signatures (DTOs in → entities out).
- **mapper/** — MyBatis interfaces. One per entity. XML SQL in `resources/mapper/`.
- **model/** — Entity + DTO classes. POJOs, no JPA annotations. Underscore-to-camel auto-enabled via MyBatis config.
- **config/** — `@Configuration`, `@Component`, utility classes. Includes factory registry for multi-datasource support.

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
- `DatabaseQueryExecutor` — executes MyBatis dynamic SQL with in-memory pagination (PageHelper not applicable to dynamic XML).
- `ElasticsearchQueryExecutor` — executes Query DSL via low-level RestClient, strips `index` from body.
- `ElasticsearchQueryExecutor` — executes IBATIS dynamic XML via SqlSessionFactory, warns if rows exceed `maxMemoryRows` config.
- All executors implement common interface, selected by `queryType` at runtime.

## TESTING SCOPE

新功能（Controller / Service / Mapper / DTO / Executor / Config）完成后必须编写对应测试。

### Layer Test Rules

| 层 | 测试类型 | 框架 | 位置 |
|----|---------|------|------|
| **Mapper** | 集成测试 | `@MybatisTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@ActiveProfiles("test")` | `src/test/java/.../mapper/` |
| **Service** | 纯单元测试 | `@ExtendWith(MockitoExtension.class)` + Mockito | `src/test/java/.../service/` |
| **Controller** | 集成测试 | `@SpringBootTest` + `@AutoConfigureMockMvc` | `src/test/java/.../controller/` |
| **Util / Config** | 纯单元测试 | Mockito (无 Spring 上下文) | `src/test/java/.../config/` |

### 测试配置

- 测试配置文件：`src/test/resources/application-test.yml`（H2 内存数据库，MySQL 模式）
- 测试 schema：`src/test/resources/schema-test.sql`（自动初始化）
- Mapper 测试目录：`src/test/resources/mapper/`（空目录，仅用于类路径）
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

### 验收标准

```bash
# 全部后端测试
mvn test -Dspring.profiles.active=test
# 应看到 BUILD SUCCESS，Tests run > 0，Failures: 0，Errors: 0
```

## ANTI-PATTERNS

- Do NOT use `synchronized(String.intern())` — use `ConcurrentHashMap.computeIfAbsent()` with explicit lock objects.
- Do NOT `catch (Exception e)` broadly — always catch specific exception types. `catch (Exception)` loses the specific type and hides bugs.
- Do NOT duplicate AES cipher code — always use `EncryptionUtil`.
- Do NOT downcast `ApplicationContext` to `GenericApplicationContext` in constructor — accept the concrete type directly.
- Do NOT hardcode protocol strings in client factories — use `@Value("${...}")` for configurable properties.
- Do NOT use `@Autowired` on fields — always constructor injection.
- Do NOT add `@Transactional` to controller methods — it belongs in the service layer.
- Do NOT use `@Size(min = 1)` on String fields — use `@NotBlank`.
- Do NOT commit default encryption keys — `EncryptionConfig.validateKey()` enforces this.

## COMMANDS

```bash
cd backend
mvn spring-boot:run     # Start server on :8080
mvn compile             # Compile only  
mvn test                # Run tests
mvn clean compile       # Fresh compile
```

### Local Development

本地 MySQL 由已存在的 Docker 容器 `api-atlas-mysql` 提供，启动前必须检查容器状态：

```bash
# 1. 检查容器是否已运行（若未运行则提示启动，未经允许不可创建新容器）
docker ps --filter name=api-atlas-mysql --format '{{.Names}}' || echo "WARN: container 'api-atlas-mysql' is NOT running — start it with: docker start api-atlas-mysql"

# 2. 启动 with local profile (uses application-local.yml, gitignored)
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run

# 或直接设置密码
SPRING_DATASOURCE_PASSWORD=api_atlas@2026 mvn spring-boot:run
```

> **注意**: 容器 `api-atlas-mysql` 是预先存在的，启动后端前应先确认容器正在运行，绝对不可自动创建新容器。
> **Security**: The database password in `application.yml` is externalized via
> `SPRING_DATASOURCE_PASSWORD` env var. Never hardcode credentials in version-control-tracked files.

## NOTES

- MySQL: running Docker container `api-atlas-mysql` at `localhost:3306`, database `api_atlas`, user `api_atlas`. 该容器为预先创建，启动后端前必须确认
- Spring Boot 4.1 requires Java 17+ (JDK 21 confirmed). Maven 3.9+.
- PageHelper 4.1.1 is compatible with MyBatis-Spring-Boot 4.0.1 and Spring Boot 4.x.
- Type aliases: `com.api.atlas.model` — MyBatis XML can use short entity names.
