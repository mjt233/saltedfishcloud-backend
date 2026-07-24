# SpringBoot 4.1.0 升级执行计划

> **升级路径**：SpringBoot 3.5.8 → 4.1.0（一步到位）  
> **Swagger 迁移策略**：全量改写为 OpenAPI 3 注解  
> **配套文档**：[风险评估报告](./springboot-4.1-upgrade-risk-assessment.md)  
> **创建日期**：2026-07-08

## 验证机制说明

本计划遵循 AGENTS.md 规范：

- **编译验证**：使用 MCP `build_project` 工具，避免命令行 mvn（除非修改了 `pom.xml` 依赖，此时须用系统 `mvn` 命令验证）
- **文件检查**：使用 MCP `get_file_problems` 检查修改文件，修复新产生的警告
- **单元测试**：禁止 `@SpringBootTest`，使用 Mockito 轻量级测试
- **启动验证**：通过运行应用确认 SpringBoot 上下文能正常初始化
- **功能回归**：手动验证核心链路（登录、OIDC、文件操作、WebSocket、分布式锁）

每个任务末尾的「验证」小节列出该任务的验证方式。`✅` 表示验证通过的标准。

---

## Phase 0：准备工作

### Task 0.1：创建升级分支

- [ ] 创建特性分支 `upgrade-springboot-4.1`

```bash
git checkout develop
git pull
git checkout -b upgrade-springboot-4.1
```

- [ ] 确认当前基线可编译可启动，记录升级前状态

**验证**：`build_project` 通过，应用可启动 ✅

---

## Phase 1：依赖与 pom 修复

> 本阶段所有任务修改了 `pom.xml` 依赖，**必须使用系统 `mvn` 命令验证编译**（AGENTS.md 要求）。

### Task 1.1：升级 SpringBoot 主版本并加入 properties-migrator

**Files:**
- Modify: `pom.xml:9`

- [ ] 将 `spring-boot-starter-parent` 版本从 `3.5.8` 改为 `4.1.0`
- [ ] 在 `<dependencies>` 中临时加入 properties-migrator（runtime scope，迁移完成后移除）

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-properties-migrator</artifactId>
    <scope>runtime</scope>
</dependency>
```

- [ ] 执行 `mvn -q -DskipTests validate` 确认 pom 合法

**验证**：`mvn validate` 成功，无依赖解析错误 ✅

> 此时编译必然失败（后续任务逐一修复），本步仅确认 pom 结构合法。

### Task 1.2：修正 spring-context-indexer 版本

**Files:**
- Modify: `pom.xml:199-204`
- Modify: `sfc-ext/pom.xml:95`

- [ ] 移除两处 `<version>6.1.14</version>`，让 BOM 管理（SpringBoot 4.1 提供 7.x）

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context-indexer</artifactId>
    <optional>true</optional>
</dependency>
```

- [ ] `mvn -q -DskipTests compile -pl sfc-core,sfc-ext -am`

**验证**：indexer 依赖版本由 BOM 解析为 7.x，编译阶段不再因 indexer 版本冲突报错 ✅

### Task 1.3：移除旧 JAXB 栈与 jjwt 0.9.0，引入 jjwt 0.12.x

**Files:**
- Modify: `pom.xml:146-180`

- [ ] 删除 `io.jsonwebtoken:jjwt:0.9.0`
- [ ] 删除 `javax.xml.bind:jaxb-api:2.3.0`
- [ ] 删除 `com.sun.xml.bind:jaxb-impl:2.3.0`
- [ ] 删除 `com.sun.xml.bind:jaxb-core:2.3.0`
- [ ] 删除 `javax.activation:activation:1.1.1`
- [ ] 引入 jjwt 0.12.x 三件套（版本交由 BOM 或显式指定）

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

- [ ] `mvn -q -DskipTests compile -pl sfc-api -am`（预计 JwtUtils.java 编译失败，由 Task 2.2 修复）

**验证**：依赖树中不再有 `javax.xml.bind` / `javax.activation` / `jjwt:0.9.0` ✅

### Task 1.4：替换 springfox 为 springdoc-openapi

**Files:**
- Modify: `pom.xml:118-122`

- [ ] 删除 `io.springfox:springfox-boot-starter:3.0.0`
- [ ] 引入 springdoc-openapi（版本交由 BOM 或显式指定）

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
```

> 注：springdoc 的版本需选择兼容 SpringBoot 4.1 的版本（springdoc 2.6+ 支持 SpringBoot 3.x，对 4.1 的支持需确认最新版本号，执行时查询 springdoc 官方最新版）。

- [ ] `mvn -q -DskipTests dependency:tree | grep -E "springfox|springdoc"` 确认替换生效

**验证**：依赖树无 springfox，有 springdoc ✅

### Task 1.5：升级 Redisson 与 redisson-spring-data

**Files:**
- Modify: `pom.xml:66-74`

- [ ] 升级 `redisson-spring-boot-starter` 到支持 SpringBoot 4.1 的版本（执行时查询 Redisson 官方最新版，预计 3.50+）
- [ ] 将 `redisson-spring-data-33` 改为 `redisson-spring-data-35`（匹配 Spring Data Redis 4.x，命名待执行时按 Redisson 官方确认）

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.50.0</version>
</dependency>
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-data-35</artifactId>
    <version>3.50.0</version>
</dependency>
```

> ⚠ 版本号为预估，**执行时必须查询 Redisson 官方确认**对 SpringBoot 4.1 / Spring Data Redis 4.x 的兼容版本。

- [ ] 确认 `LockProviderAutoConfigurationImportFilter.java:22-23` 中排除的自动配置类名（`RedissonAutoConfiguration` / `RedissonAutoConfigurationV2`）在新版 Redisson 中仍存在，若改名需同步更新

**验证**：`mvn dependency:tree` 显示 redisson 新版本；redisson-spring-data-33 不再出现 ✅

### Task 1.6：升级 Lombok

**Files:**
- Modify: `pom.xml:40,130-134`

- [ ] 升级 `lombok.version` 到支持 Java 25 + SpringBoot 4.1 的版本（执行时查询 Lombok 官方最新版，预计 1.18.46+）

- [ ] 确认 `maven-compiler-plugin` 的 `<proc>full</proc>`（`pom.xml:284`）仍适用，必要时调整

**验证**：`mvn -q -DskipTests compile` Lombok 注解处理无报错 ✅

### Task 1.7：修正 sfc-ext 硬编码依赖版本

**Files:**
- Modify: `sfc-ext/pom.xml:65`（jackson-databind 2.17.2）
- Modify: `sfc-ext/pom.xml:89`（junit-jupiter 5.8.1）

- [ ] 移除 `jackson-databind` 显式版本（交由 BOM，或迁移到 Jackson 3 的 `tools.jackson.databind`）
- [ ] 升级 `junit-jupiter` 到 5.13+（或移除显式版本交由 BOM）

- [ ] `mvn -q -DskipTests compile -pl sfc-ext -am`

**验证**：sfc-ext 模块编译无依赖版本冲突 ✅

### Task 1.8：修正 maven-resources-plugin 极旧版本

**Files:**
- Modify: `sfc-task/sfc-task-core/pom.xml:49`

- [ ] 将 `maven-resources-plugin` 版本从 `2.7` 升级到 `3.5.0`（与其他模块 3.3.1 对齐或更新）

- [ ] `mvn -q -DskipTests validate -pl sfc-task/sfc-task-core`

**验证**：plugin 解析成功 ✅

### Task 1.9：统一 Spring AI 版本并确认兼容性

**Files:**
- Modify: `sfc-ext/sfc-ext-mcp/pom.xml:23`
- Modify: `sfc-ext/sfc-ext-ai/pom.xml:54`

- [ ] 查询 Spring AI 官方确认支持 SpringBoot 4.1 的版本
- [ ] 统一 `sfc-ext-mcp` 的 `spring-ai-starter-mcp-server-webmvc` 与 `sfc-ext-ai` 的 `spring-ai-bom` 到同一版本线

- [ ] `mvn -q -DskipTests compile -pl sfc-ext/sfc-ext-mcp,sfc-ext/sfc-ext-ai -am`

**验证**：两个 AI 扩展模块编译通过 ✅

### Task 1.10：移除 jakarta.mail 显式版本

**Files:**
- Modify: `pom.xml:231-235`

- [ ] 移除 `com.sun.mail:jakarta.mail:2.0.1` 的显式版本（交由 BOM），或确认与 `spring-boot-starter-mail` 不冲突后保留

- [ ] `mvn dependency:tree | grep mail` 确认无版本冲突

**验证**：mail 依赖单一，无冲突 ✅

---

## Phase 2：代码适配（让编译通过）

### Task 2.1：swagger 注解全量改写为 OpenAPI 3（18 文件 / 72 处注解）

**Files:**
- Modify: 18 个含 swagger 注解的文件（见风险评估报告 2.1 节代表性文件清单）

**注解映射表：**

| Swagger 2（旧） | OpenAPI 3（新） |
|---|---|
| `@Api(tags = {"xxx"})`（类级） | `@Tag(name = "xxx")` |
| `@ApiOperation(value = "x")` | `@Operation(summary = "x")` |
| `@ApiModel(description = "x")` | `@Schema(description = "x")` |
| `@ApiModelProperty(value = "x")` | `@Schema(description = "x")` |
| `@ApiParam(value = "x", required = true)` | `@Parameter(description = "x", required = true)` |
| `@ApiImplicitParam` / `@ApiImplicitParams` | `@Parameters` / `@Parameter` |

**import 替换：**

```
旧: import io.swagger.annotations.*;
新: import io.swagger.v3.oas.annotations.*;
    import io.swagger.v3.oas.annotations.media.Schema;
    import io.swagger.v3.oas.annotations.Parameter;
    import io.swagger.v3.oas.annotations.Parameters;
    import io.swagger.v3.oas.annotations.Operation;
    import io.swagger.v3.oas.annotations.tags.Tag;
```

- [ ] 用 grep 定位所有 `io.swagger.annotations` import：`grep -rn "io.swagger.annotations" --include="*.java"`
- [ ] 逐一改写 18 个文件的 import 与注解（按映射表）
- [ ] 如有自定义 Swagger 配置类（Docket Bean），改为 springdoc 的 `GroupedOpenApi` Bean（本项目无 Docket Bean，确认即可）
- [ ] 可选：在 `application.yml` 配置 springdoc 文档路径（默认 `/swagger-ui.html`）

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs
```

- [ ] `build_project` 编译验证
- [ ] `get_file_problems` 检查改动的文件

**验证**：全项目编译通过，无 `io.swagger.annotations` 残留 import ✅

### Task 2.2：重写 JwtUtils（jjwt 0.12.x API）

**Files:**
- Modify: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/utils/JwtUtils.java`

**API 变化：**

| 旧（0.9.0） | 新（0.12.x） |
|---|---|
| `Jwts.builder().signWith(SignatureAlgorithm.HS256, SECRET)` | `Jwts.builder().signWith(key)`（key 为 `Keys.hmacShaKeyFor(SECRET.getBytes())`） |
| `Jwts.parser().setSigningKey(SECRET).parseClaimsJws(token).getBody()` | `Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()` |
| `SignatureAlgorithm.HS256` | 通过 `Keys.hmacShaKeyFor` 隐式指定算法 |

- [ ] 改写 `JwtUtils.java:38-42` 的签发逻辑
- [ ] 改写 `JwtUtils.java:62,79` 的解析逻辑
- [ ] 将 SECRET 转为 `SecretKey`：`Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))`
- [ ] 确认 SECRET 长度满足 HS256 要求（≥32 字节，否则用 HS512 或补长）
- [ ] `build_project` 编译验证
- [ ] `get_file_problems` 检查 JwtUtils.java

**验证**：JwtUtils 编译通过；用 Mockito 单元测试验证 Token 签发/解析往返一致 ✅

> 遵循 AGENTS.md：测试类不使用 `@SpringBootTest`，用 Mockito 构造纯单元测试。

### Task 2.3：@EntityScan 包名迁移

**Files:**
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/SaltedfishcloudApplication.java:44`
- Modify: 各 ext 模块中使用 `@EntityScan` 的类（grep 定位）

- [ ] grep 定位：`grep -rn "import org.springframework.boot.autoconfigure.domain.EntityScan\|import org.springframework.boot.domain.EntityScan" --include="*.java"`
- [ ] 将旧 import `org.springframework.boot.autoconfigure.domain.EntityScan` 改为 `org.springframework.boot.persistence.autoconfigure.EntityScan`
- [ ] `build_project` 编译验证

**验证**：无旧包名 EntityScan 引用，编译通过 ✅

### Task 2.4：Jackson 相关适配

**Files:**
- 全局 grep：`grep -rn "@JsonComponent\|@JsonMixin\|HttpMessageConverters\|JsonObjectSerializer\|JsonValueDeserializer\|Jackson2ObjectMapperBuilderCustomizer" --include="*.java"`

- [ ] 若有 `@JsonComponent` → 改为 `@JacksonComponent`
- [ ] 若有 `@JsonMixin` → 改为 `@JacksonMixin`
- [ ] 若有自定义 `HttpMessageConverter` bean → 改用 `ClientHttpMessageConvertersCustomizer` / `ServerHttpMessageConvertersCustomizer`
- [ ] 若有自定义 `ObjectMapper` bean → 改为定义 `JsonMapper` bean（Jackson 3）
- [ ] 若短期内无法迁移 Jackson 3，临时加 `spring-boot-jackson2` 模块过渡

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-jackson2</artifactId>
</dependency>
```

- [ ] `build_project` 编译验证

**验证**：编译通过；启动后 JSON 序列化正常 ✅

### Task 2.5：Hibernate 自定义 ID 生成器适配

**Files:**
- Modify: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/utils/identifier/SystemUuidGenerator.java`
- Modify: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/utils/identifier/SnowFlakeIdGenerator.java`
- Modify: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/annotations/id/SystemUuidGenerator.java`
- Modify: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/annotations/id/SnowFlakeIdGenerator.java`
- Modify: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/utils/JpaProxyUtils.java`
- Modify: `sfc-ext/sfc-ext-data-manager/src/main/java/.../repo/InvalidDataRecordRepoImpl.java`

- [ ] 查阅 Hibernate 7 迁移文档，确认以下 API 是否变化：
  - `org.hibernate.engine.spi.SharedSessionContractImplementor`
  - `org.hibernate.generator.EventType` / `GeneratorCreationContext`
  - `org.hibernate.id.factory.spi.CustomIdGeneratorCreationContext`
  - `org.hibernate.id.uuid.UuidGenerator` / `@org.hibernate.annotations.UuidGenerator`
  - `org.hibernate.id.IdentifierGenerator`
  - `org.hibernate.proxy.HibernateProxy` / `LazyInitializationException`
  - `org.hibernate.jpa.AvailableHints`
  - `@NotFound` / `NotFoundAction`
- [ ] 按 Hibernate 7 新 API 调整实现
- [ ] `build_project` 编译验证
- [ ] `get_file_problems` 检查修改文件

**验证**：编译通过；启动后 JPA 实体保存能正常生成 ID（UUID / 雪花）✅

### Task 2.6：Spring Security 7 / Authorization Server 适配

**Files:**
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/SecurityConfig.java`
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcAuthorizationServerConfig.java`
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcResourceServerConfig.java`
- Modify: `sfc-ext/sfc-ext-mcp/src/main/java/.../McpAutoConfiguration.java`

- [ ] 查阅 [Spring Security 7.0 迁移指南](https://docs.spring.io/spring-security/reference/7.0/migration/) 全部破坏性变更
- [ ] 确认 `SecurityFilterChain` / `HttpSecurity` lambda API 变化并调整
- [ ] 确认 `OAuth2AuthorizationServerConfigurer.authorizationServer()` 在 Authorization Server 2.x（并入 Security 7）的新用法
- [ ] 确认 `ClientSecretAuthenticationProvider`、`AuthorizationServerSettings` 构造签名变化
- [ ] 确认 `DaoAuthenticationProvider` 构造签名（项目已用新式 `DaoAuthenticationProvider(userDetailsService)`）
- [ ] 确认 `@EnableMethodSecurity(jsr250Enabled=true)` 与 `@RolesAllowed` 在 Security 7 的行为
- [ ] 确认自定义 `JwtLoginFilter` / `JwtAuthenticationFilter` / `OidcAccessTokenFilter` 的基类 API 变化
- [ ] 确认 `Md5PasswordEncoder`（`SecurityConfig.java:46`）是否仍可用（建议迁移到 `BCryptPasswordEncoder`，但属功能变更需单独评估）
- [ ] `build_project` 编译验证
- [ ] `get_file_problems` 检查修改文件

**验证**：编译通过；启动后无 Security 配置错误 ✅

### Task 2.7：starter 重命名（可选，先用 classic 过渡）

> 若 Phase 2 编译问题过多，可先用 `spring-boot-starter-classic` 过渡，本任务延后。

**Files:**
- Modify: `pom.xml`（根 dependencies）
- Modify: `sfc-core/pom.xml`

- [ ] `spring-boot-starter-web` → `spring-boot-starter-webmvc`
- [ ] `spring-boot-starter-aop` → `spring-boot-starter-aspectj`
- [ ] `spring-boot-starter-oauth2-authorization-server`（sfc-core）→ `spring-boot-starter-security-oauth2-authorization-server`
- [ ] 测试依赖若有 `spring-security-test`，改为 `spring-boot-starter-security-test`
- [ ] `build_project` 编译验证

**验证**：编译通过；启动正常 ✅

### Task 2.8：移除 uber-jar classic loader 配置（如有）

**Files:**
- 全局 grep：`grep -rn "CLASSIC\|loaderImplementation" --include="pom.xml"`

- [ ] 若存在 `<loaderImplementation>CLASSIC</loaderImplementation>`，移除
- [ ] `mvn -q -DskipTests package`

**验证**：打包成功，无 loader 警告 ✅

---

## Phase 3：配置调整

### Task 3.1：检查 application*.yml 属性兼容性

**Files:**
- `sfc-core/src/main/config/application.yml`
- `sfc-core/src/main/config/application-develop.yml`
- `sfc-core/src/main/config/application-develop-mysql.yml`
- `sfc-core/src/main/config/application-product.yml`
- `pre-release/config.yml`
- `sfc-ext/sfc-ext-mcp/src/main/resources/application.yml`

- [ ] 启动应用，观察 properties-migrator 输出的诊断日志
- [ ] 按诊断修复任何被重命名/移除的属性（风险评估显示本项目几乎未命中重命名，预期工作量小）
- [ ] 检查 `application.yml:13-25` 的 `spring.jpa.properties.hibernate.*`：
  - `enable_lazy_load_no_trans` 是否仍有效（Hibernate 7 可能弃用）
  - `order_inserts`、`jdbc.batch_size` 是否仍有效
- [ ] 确认 `management.health.mail.enabled`、`management.endpoints.web.exposure.include` 仍有效

**验证**：启动日志无 properties-migrator 警告 ✅

### Task 3.2：移除 properties-migrator

**Files:**
- Modify: `pom.xml`

- [ ] 在所有配置修复完成后，移除 `spring-boot-properties-migrator` 依赖
- [ ] `mvn -q -DskipTests validate`

**验证**：依赖中无 migrator；应用仍正常启动 ✅

---

## Phase 4：验证

### Task 4.1：全量编译验证

- [ ] `build_project`（全项目）
- [ ] 修复所有新产生的编译错误与警告
- [ ] 对所有修改过的文件执行 `get_file_problems`，修复警告

**验证**：`build_project` 返回无错误 ✅

### Task 4.2：应用启动验证

- [ ] 使用 develop profile（SQLite）启动应用
- [ ] 确认 SpringBoot 上下文初始化成功，无启动失败
- [ ] 确认 Tomcat 11 正常监听
- [ ] 确认 JPA / Hibernate 正常初始化（DDL-auto=update）
- [ ] 确认 Redis / Redisson 连接正常（若启用）
- [ ] 确认 WebSocket 端点注册正常（`/api/ws`、`/api/webrtc/ws/{peerId}`、`/api/webshell/{sessionId}`）

**验证**：应用启动到 `Started SaltedfishcloudApplication` ✅

### Task 4.3：核心功能回归

手动验证以下链路（develop profile）：

- [ ] **用户登录**：用户名密码登录，签发 JWT（验证 jjwt 升级后 Token 正常）
- [ ] **JWT 鉴权**：携带 JWT 访问需登录接口，鉴权通过
- [ ] **OIDC 授权服务器**（若 `sys.oidc.enabled=true`）：
  - 授权码流程完整走通
  - Token 签发与校验
  - 资源服务器 `/api/openApi/**` 鉴权
- [ ] **`@RolesAllowed` 鉴权**：管理员接口访问权限正常（用管理员/普通用户分别验证）
- [ ] **文件操作**：上传、下载、剪切、重命名（验证 JPA / 文件系统正常）
- [ ] **WebSocket**：`/api/ws` 连接与消息推送
- [ ] **分布式锁**（若 Redis 启用）：触发需加锁的操作，验证 Redisson 锁正常
- [ ] **Swagger UI**：访问 `/swagger-ui.html`，确认 OpenAPI 3 文档正常展示（验证 springdoc 迁移）
- [ ] **压缩**：压缩/解压操作（sfc-archive）
- [ ] **扩展插件**：按需验证各 sfc-ext 插件功能

**验证**：上述链路全部通过 ✅

### Task 4.4：生产 profile 验证

- [ ] 使用 product profile（MySQL）启动
- [ ] 确认 MySQL 连接、JPA DDL、Redis 连接正常
- [ ] 确认 `pre-release/config.yml` 配置生效

**验证**：product profile 启动正常 ✅

### Task 4.5：单元测试验证

- [ ] 执行现有单元测试：`mvn test`
- [ ] 确认无 `@SpringBootTest`（AGENTS.md 规范）
- [ ] 修复因升级导致的测试失败

**验证**：`mvn test` 全绿 ✅

---

## 任务依赖与建议执行顺序

```
Phase 0 → Phase 1（1.1→1.2→1.3→1.4→1.5→1.6→1.7→1.8→1.9→1.10）
        → Phase 2（2.1, 2.2, 2.3, 2.4, 2.5, 2.6 可并行，2.7/2.8 收尾）
        → Phase 3（3.1→3.2）
        → Phase 4（4.1→4.2→4.3→4.4→4.5）
```

- Phase 1 内部建议按顺序执行（1.1 先建立 BOM 基线）
- Phase 2 各任务相互独立，可并行/分人处理
- 2.1（swagger）与 2.6（Security）工作量最大，建议优先投入

---

## 风险与回滚

- **回滚策略**：每个 Phase 完成后提交一次 git commit，便于出错时回退到上一阶段
- **阻断点**：若 Task 1.5（Redisson）或 2.6（Security 7）发现不兼容且无法短期修复，考虑降级方案（如 Redisson 暂用本地锁、OIDC 暂时关闭）
- **外部文档依赖**：Task 2.5（Hibernate）与 2.6（Security）必须先查阅对应官方迁移文档，不可凭经验臆测

---

## 检查清单（AGENTS.md 规范）

- [ ] 是否已通过 `build_project` 验证？
- [ ] 修改过的文件是否已用 `get_file_problems` 检查并修复警告？
- [ ] 修改 `pom.xml` 依赖是否已用系统 `mvn` 命令验证？
- [ ] JavaDoc 是否已补全（新增/修改的方法与字段）？
- [ ] `@UID` 注解是否遗漏（Controller 的 `Long uid` / `Integer uid` 参数）？
- [ ] 公共资源（uid=0）写入是否做了管理员校验？
- [ ] 批量操作是否使用 `CollectionUtils.partition` 拆分批次？
- [ ] 测试类是否未使用 `@SpringBootTest`？
- [ ] JDK 版本满足 Java 25 要求？
- [ ] 多模块项目是否在模块级别验证过编译？
