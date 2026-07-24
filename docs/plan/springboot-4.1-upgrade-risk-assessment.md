# SpringBoot 4.1.0 升级风险评估报告

> **升级路径**：SpringBoot 3.5.8 → 4.1.0（一步到位）  
> **评估日期**：2026-07-08  
> **项目基线**：Java 25、多模块 Maven（sfc-core / sfc-api / sfc-ext / sfc-task / sfc-rpc / sfc-archive）  
> **信息来源**：[Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)、[Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)、[Spring Boot 4.1 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes)、[Spring Boot 4.1.0 Configuration Changelog](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1.0-Configuration-Changelog) 及项目源码静态分析

---

## 1. 升级概览

本次为**跨主版本升级**，需同时消化 4.0 的破坏性变更（模块化重构、Spring Security 7、Hibernate 7、Jackson 3）与 4.1 的增量变更。

| 依赖 | 当前版本 | 目标版本 |
|---|---|---|
| SpringBoot | 3.5.8（`pom.xml:9`） | 4.1.0 |
| Spring Framework | 6.x | 7.0.8 |
| Spring Security | 6.x | 7.1.0 |
| Spring Data | 3.x | 2026.0.0 |
| Hibernate | 6.x | 7.1 |
| Jackson | 2.x | 3.0（首选） |
| Tomcat | 10.x | 11.0（Servlet 6.1 / Jakarta EE 11 基线） |
| Java | 25 ✅ | 25（满足 ≥17 要求） |

---

## 2. 🔴 极高风险阻断点（不修复无法启动/编译）

### 2.1 Springfox 3.0.0 → 必须替换为 springdoc-openapi

- **现状**：`pom.xml:119-122` 硬编码 `io.springfox:springfox-boot-starter:3.0.0`。代码使用 `io.swagger.annotations.*`（Swagger 2 注解），**18 个文件、约 72 处注解**（`@Api`/`@ApiOperation`/`@ApiModel`/`@ApiModelProperty`/`@ApiParam`）。无自定义 `Docket` Bean，纯依赖自动配置。
- **问题**：springfox 已停止维护，与 SpringBoot 3.x 即不兼容，与 4.x 完全无法启动。
- **代表性文件**：
  - `sfc-core/.../controller/OAuthController.java:34-35`（14 处 @ApiOperation）
  - `sfc-core/.../controller/open/OpenApiDiskFileController.java:26-28`
  - `sfc-core/.../controller/FileController.java:33-34`
  - `sfc-api/.../model/param/LogRecordQueryParam.java:4-5`
  - `sfc-ext/sfc-ext-mcp/.../controller/McpApiKeyController.java:10-11`
  - `sfc-archive/sfc-archive-core/.../controller/ArchiveController.java:28-29`
- **修复方案**：迁移到 `springdoc-openapi-starter-webmvc-ui`，注解从 `io.swagger.annotations`（Swagger 2）批量改写为 `io.swagger.v3.oas.annotations`（OpenAPI 3）。本次升级**最大单项工作量**，建议独立分支/独立 commit 完成。

### 2.2 jjwt 0.9.0 → 必须升级到 0.12.x（API 完全重写）

- **现状**：`pom.xml:148-151` 硬编码 `io.jsonwebtoken:jjwt:0.9.0`。仅 `sfc-api/.../utils/JwtUtils.java` 使用：
  - 行 38-42：`Jwts.builder()...signWith(SignatureAlgorithm.HS256, SECRET)`
  - 行 62、79：`Jwts.parser()` 解析
- **问题**：0.9.0 依赖 `javax.xml.bind`（Java 9+ 需手动补 JAXB），在 Java 25 上靠补丁运行；0.12.x API 完全重写（`Jwts.builder().signWith(key)`、`Jwts.parser().verifyWith(key).build()`）。
- **连带清理**：升级后可移除 `pom.xml:161-180` 的 `jaxb-api` / `jaxb-impl` / `jaxb-core` / `activation`（这四个即为 jjwt 0.9.0 补的 JAXB 栈）。

### 2.3 spring-context-indexer 6.1.14 硬编码（Spring 6 vs Spring 7 冲突）

- **现状**：`pom.xml:199-204` 与 `sfc-ext/pom.xml:95` 均硬编码 `spring-context-indexer:6.1.14`。
- **问题**：SpringBoot 4.1 使用 Spring Framework 7.0.8，6.1.14 的 indexer 与 7.x 不兼容。
- **修复**：移除显式版本，交由 BOM 管理（SpringBoot 4.1 提供 7.x 版本）。

### 2.4 Spring Boot 4 模块化重构（starter 大规模拆分/重命名）

SpringBoot 4 采用新模块化设计，大量功能现在需要显式声明对应 starter。与本项直接相关的重命名/新增：

| 旧 starter | 新 starter | 说明 |
|---|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` | 旧名保留但废弃 |
| `spring-boot-starter-oauth2-authorization-server` | `spring-boot-starter-security-oauth2-authorization-server` | 旧名保留但废弃 |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` | 范围澄清 |
| `spring-boot-starter-oauth2-client` | `spring-boot-starter-security-oauth2-client` | — |
| `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` | — |

- **测试影响**：`@WithMockUser` / `@WithUserDetails`（来自 `spring-security-test`）现需 `spring-boot-starter-security-test` 才能正常工作。
- **过渡方案**：可用 `spring-boot-starter-classic`（含全部模块、排除传递依赖）作为过渡，先修复编译，再逐步切换到细粒度 starter。

---

## 3. 🟠 高风险项（需验证/重写部分代码）

### 3.1 Spring Authorization Server 并入 Spring Security 7

- **现状**：`sfc-core/pom.xml:123-125` 声明 `spring-security-oauth2-authorization-server`（未指定版本，由 BOM 管理）。项目有 **4 个 SecurityFilterChain**：
  1. `sfc-core/.../config/security/SecurityConfig.java:65` — `@Order(2)` 主链，`@EnableWebSecurity`、`@EnableMethodSecurity(jsr250Enabled=true)`
  2. `sfc-core/.../config/security/oidc/OidcAuthorizationServerConfig.java:110` — `@Order(HIGHEST_PRECEDENCE)` 授权服务器链
  3. `sfc-core/.../config/security/oidc/OidcResourceServerConfig.java:56` — `@Order(1)` `/api/openApi/**` 资源服务器链
  4. `sfc-ext/sfc-ext-mcp/.../McpAutoConfiguration.java:75` — MCP 端点链
- **关键 API 使用**：`JWKSource<SecurityContext>`、`OAuth2AuthorizationServerConfigurer.authorizationServer()`、`ClientSecretAuthenticationProvider`、`AuthorizationServerSettings`、`OAuth2AuthorizationService` / `OAuth2AuthorizationConsentService` / `RegisteredClientRepository` 自定义实现、`.oauth2ResourceServer(Customizer.withDefaults())`。
- **变化**：
  - Authorization Server **已并入 Spring Security 7.0**（不再独立项目）
  - 版本覆盖属性 `spring-authorization-server.version` **失效** → 改用 `spring-security.version`
  - starter 需重命名（见 2.4）
  - **Spring Security 7 的 SecurityFilterChain / HttpSecurity / OAuth2 API 变化不在 Boot 迁移指南内**，必须额外查阅 [Spring Security 7.0 迁移指南](https://docs.spring.io/spring-security/reference/7.0/migration/)
- **回归重点**：OIDC 授权服务器全链路（登录、Token 签发、资源服务器校验）+ `@RolesAllowed` 共 88 处（`jsr250Enabled=true` 启用 JSR-250）。

### 3.2 Redisson 兼容性（redisson-spring-data-33）

- **现状**：`pom.xml:66-74` 硬编码 `redisson-spring-boot-starter:3.37.0` + `redisson-spring-data-33:3.37.0`。代码用于分布式锁：
  - `sfc-core/.../cache/RedisLockFactory.java:4,19,23` — 注入 `RedissonClient`
  - `sfc-core/.../config/LockProviderAutoConfigurationImportFilter.java:22-23` — `AutoConfigurationImportFilter` 在 `lock-provider=local` 时排除 `RedissonAutoConfiguration` / `RedissonAutoConfigurationV2`
- **问题**：`redisson-spring-data-33` 对应 Spring Data Redis 3.3 线。SpringBoot 4.1 升级到 Spring Data Redis 4.x（Spring Data 2026.0.0）。迁移指南**完全未提及 Redisson**。
- **修复**：需改用 `redisson-spring-data-35`（或更新命名），并升级 Redisson 到支持 SpringBoot 4.1 的版本（可能需 3.50+）。**必须实测 Redisson 对 SpringBoot 4.1 的兼容性**——第三方库，无官方背书。

### 3.3 Hibernate 7 内部 API 直接使用

- **现状**：项目直接使用 Hibernate 内部 API：
  - `sfc-api/.../utils/identifier/SystemUuidGenerator.java:3-7,16-17` — `org.hibernate.engine.spi.SharedSessionContractImplementor`、`org.hibernate.generator.EventType`、`GeneratorCreationContext`、`org.hibernate.id.factory.spi.CustomIdGeneratorCreationContext`、`org.hibernate.id.uuid.UuidGenerator`、`@org.hibernate.annotations.UuidGenerator`
  - `sfc-api/.../utils/identifier/SnowFlakeIdGenerator.java:4-5` — `SharedSessionContractImplementor`、`IdentifierGenerator`
  - `sfc-api/.../utils/JpaProxyUtils.java:3` — `org.hibernate.proxy.HibernateProxy`、`LazyInitializationException`
  - `sfc-ext/sfc-ext-data-manager/.../repo/InvalidDataRecordRepoImpl.java:4,11,17,22` — `jakarta.persistence.EntityManager` + `org.hibernate.jpa.AvailableHints`
  - `sfc-ext/sfc-ext-download/.../model/po/DownloadTaskInfo.java:9-10` — `@NotFound`、`NotFoundAction`
- **问题**：Hibernate 7 对 `org.hibernate.*` 内部 API 有较大重构（`generator` 包、`SharedSessionContractImplementor`、`hibernate.jpa.AvailableHints` 等）。迁移指南未展开 Hibernate 7 API 变化，**必须查阅 Hibernate 7 迁移文档**验证自定义 ID 生成器（`@IdGeneratorType`）实现。
- **配置注意**：`application.yml:13-25` 的 `enable_lazy_load_no_trans`、`order_inserts` 等属性可能在新版 Hibernate 中改名/弃用。

### 3.4 Jackson 2 → Jackson 3（首选）

- **变化**：
  - 包名 `com.fasterxml.jackson` → `tools.jackson`（`jackson-annotations` 例外，仍用 `com.fasterxml.jackson.annotation`）
  - `@JsonComponent` → `@JacksonComponent`，`@JsonMixin` → `@JacksonMixin`
  - `spring.jackson.read/write.*` → `spring.jackson.json.read/write.*`
  - `spring.jackson.parser.*`（有等效 `JsonReadFeature` 的）→ `spring.jackson.json.read` 属性
  - SpringBoot 4 会自动注册 classpath 上**所有** Jackson 模块（3.x 只注册知名模块），如需旧行为设 `spring.jackson.find-and-add-modules=false`
- **现状**：`sfc-ext/pom.xml:65` 硬编码 `jackson-databind:2.17.2`（与 SpringBoot 4.1 的 Jackson 2.19+ 冲突）。
- **过渡方案**：若无法立即迁移到 Jackson 3，可加 `spring-boot-jackson2` 模块（已废弃形态，临时用），属性走 `spring.jackson2.*`。
- **待确认**：需进一步 grep 确认是否有自定义 `@JsonComponent` / `@JsonMixin` / `ObjectMapper` bean 使用。

### 3.5 Spring AI 版本不一致与兼容性

- **现状**：
  - `sfc-ext/sfc-ext-mcp/pom.xml:23` 用 `spring-ai-starter-mcp-server-webmvc:1.1.6`
  - `sfc-ext/sfc-ext-ai/pom.xml:54` 用 `spring-ai-bom:2.0.0`
  - **两个版本不一致**
- **问题**：Spring AI 与 SpringBoot 4.1 的兼容性需确认（Spring AI 通常跟随特定 SpringBoot 版本）。
- **修复**：统一 Spring AI 版本，确认其支持 SpringBoot 4.1。

### 3.6 Lombok 与 Java 25 / SpringBoot 4.1

- **现状**：`pom.xml:40,132` 硬编码 Lombok 1.18.42，`maven-compiler-plugin` 用 `<proc>full</proc>`（`pom.xml:284`）。
- **问题**：需确认 1.18.42 支持 Java 25 的注解处理与 SpringBoot 4.1 编译。可能需升级到更新版本。

---

## 4. 🟡 中风险项（构建/测试层面）

| 项 | 现状 | 处理 |
|---|---|---|
| `@EntityScan` 包名变更 | `SaltedfishcloudApplication.java:44` 及各 ext 模块 | 改为 `org.springframework.boot.persistence.autoconfigure.EntityScan` |
| `junit-jupiter 5.8.1` | `sfc-ext/pom.xml:89` | 过旧，SpringBoot 4.1 用 5.13+，需升级 |
| `maven-resources-plugin 2.7` | `sfc-task/sfc-task-core/pom.xml:49` | 极旧，其他模块用 3.3.1，需统一升级 |
| `com.sun.mail:jakarta.mail:2.0.1` | `pom.xml:231-235` | 旧 Jakarta Mail 2.0，SpringBoot 4.1 自带管理；显式声明可能冲突，建议移除显式版本 |
| JSpecify nullability | SpringBoot 4 新增注解 | 若启用 null checker 或 Kotlin，可能编译失败；`org.springframework.lang` nullable 注解需迁移到 JSpecify |
| `spring.jackson.find-and-add-modules` | 默认行为变更 | SpringBoot 4 自动注册 classpath 所有 Jackson 模块，如需旧行为设 `false` |
| Logback 默认字符集 | — | 默认改为 UTF-8（console/file），一般无影响 |
| Liveness/Readiness 探针 | — | 默认启用（项目未用 actuator starter，影响小） |
| `HttpMessageConverters` 弃用 | — | 若有自定义 `HttpMessageConverter` bean，改用 `ClientHttpMessageConvertersCustomizer` / `ServerHttpMessageConvertersCustomizer` |
| 经典 uber-jar loader 移除 | — | 移除构建文件中任何 `loaderImplementation>CLASSIC` 配置 |

---

## 5. 🟢 低风险/已就绪

- **javax → jakarta 迁移**：✅ 代码已全部使用 `jakarta.*`。残留的 `javax.sql` / `javax.imageio` / `javax.net.ssl` / `javax.xml.parsers` / `javax.xml.namespace` 均为 **JDK 自带模块**，在 Java 25 仍保留 `javax.*` 命名，**无需迁移**。
- **WebSocket `@ServerEndpoint`**：3 处端点（`WebSocketHandler.java:32`、`WebRTCSignalingHandler.java:22`、`WebShellEndpointHandler.java:25`）使用 `jakarta.websocket`，SpringBoot 4.1 保留 `spring-boot-starter-websocket`，Jakarta WebSocket 2.2。风险中等偏低，需实测 Tomcat 11 兼容。
- **Actuator/监控**：未声明 actuator starter，无自定义监控代码（`MeterRegistry` / `@Timed` / `@Endpoint` 0 匹配），影响极低。
- **`spring.data.jpa.repositories.bootstrap-mode`**：配置和代码中均无引用，4.1 对该属性的调整无影响。

---

## 6. 需要调整的配置（属性变更）

### 6.1 配置文件清单

| 文件路径 | 用途 |
|---|---|
| `sfc-core/src/main/config/application.yml` | 主配置（含 `sys.oidc` 节点） |
| `sfc-core/src/main/config/application-develop.yml` | develop profile（SQLite） |
| `sfc-core/src/main/config/application-develop-mysql.yml` | develop-mysql profile |
| `sfc-core/src/main/config/application-product.yml` | product profile |
| `pre-release/config.yml` | 生产部署用户配置（环境变量化） |
| `sfc-ext/sfc-ext-mcp/src/main/resources/application.yml` | MCP 插件配置（spring.ai.mcp） |
| `sfc-task/sfc-task-core/src/test/resources/application-test.yml` | 任务模块测试配置 |

### 6.2 4.0 破坏性属性变更（命中检查）

| 旧属性 | 新属性 | 本项目是否用到 |
|---|---|---|
| `spring.dao.exceptiontranslation.enabled` | `spring.persistence.exceptiontranslation.enabled` | 否（默认） |
| `spring.session.redis.*` | `spring.session.data.redis.*` | 否（未用 Spring Session） |
| `management.tracing.enabled` | `management.tracing.export.enabled` | 否（未用 tracing） |
| `spring.devtools.livereload.enabled` | 默认改为 `false`（4.1 进一步废弃） | 否（未用 devtools） |
| `spring.jackson.read/write.*` | `spring.jackson.json.read/write.*` | 否（未配置 jackson 属性） |
| `spring.data.mongodb.*` | `spring.mongodb.*` | 否（未用 MongoDB） |
| `spring-authorization-server.version`（Maven 属性） | `spring-security.version` | 否（未覆盖版本） |

**结论**：配置文件中**几乎没有命中 4.0 的属性重命名**（配置精简，未用 MongoDB/Session/tracing/devtools）。

### 6.3 4.1 配置变更（4.0.7 → 4.1.0）

- **移除**：`logging.file.clean-history-on-start` / `max-history` / `max-size` / `total-size-cap` / `pattern.rolling-file-name` → 改用 `logging.logback.rollingpolicy.*`（项目未配置 logback 滚动，无影响）。
- **新增**（可选使用）：`spring.jpa.bootstrap`（异步后台引导 EMF）、`spring.datasource.connection-fetch`（lazy 连接获取）、`spring.http.clients.cookie-handling`、`server.compression.additional-mime-types`。
- **默认值变更**：`spring.elasticsearch.socket-keep-alive` 默认 `true`（未用 ES，无影响）。

### 6.4 强烈推荐：使用 properties-migrator

升级时临时加入（runtime scope），启动时自动分析并临时迁移属性、打印诊断。**迁移完成后必须移除**：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-properties-migrator</artifactId>
    <scope>runtime</scope>
</dependency>
```

### 6.5 sys.* 节点

自定义 `sys.*` 节点（`sys.oidc.*`、`sys.service.*`、`sys.store.*` 等）不受 SpringBoot 升级影响，无需修改。按 AGENTS.md 规范，`sys.` 节点修改需同步 `application-develop.yml`、`application-product.yml`、`pre-release/config.yml`，不改 `application.yml`。

---

## 7. 硬编码依赖版本汇总（需在 pom.xml 调整）

| 依赖 | 当前版本 | 位置 | 处理 |
|---|---|---|---|
| spring-boot-starter-parent | 3.5.8 | `pom.xml:9` | → 4.1.0 |
| springfox-boot-starter | 3.0.0 | `pom.xml:119-122` | **删除**，换 springdoc-openapi |
| jjwt | 0.9.0 | `pom.xml:148-151` | → 0.12.x（拆为 jjwt-api / impl / jackson） |
| jaxb-api / impl / core + activation | 2.3.0 / 1.1.1 | `pom.xml:161-180` | **删除**（jjwt 升级后不需要） |
| spring-context-indexer | 6.1.14 | `pom.xml:202`、`sfc-ext/pom.xml:95` | 移除版本，交由 BOM |
| redisson-spring-boot-starter | 3.37.0 | `pom.xml:68` | 升级到支持 Boot 4.1 的版本 |
| redisson-spring-data-33 | 3.37.0 | `pom.xml:73` | → redisson-spring-data-35（待确认） |
| lombok | 1.18.42 | `pom.xml:40,132` | 升级到支持 Java 25 的版本 |
| jackson-databind | 2.17.2 | `sfc-ext/pom.xml:65` | 移除版本，交由 BOM（或迁移 Jackson 3） |
| junit-jupiter | 5.8.1 | `sfc-ext/pom.xml:89` | 升级到 5.13+ |
| maven-resources-plugin | 2.7 | `sfc-task/sfc-task-core/pom.xml:49` | → 3.5.0+ |
| com.sun.mail:jakarta.mail | 2.0.1 | `pom.xml:233` | 移除显式版本，交由 BOM |
| spring-ai-bom | 2.0.0 | `sfc-ext/sfc-ext-ai/pom.xml:54` | 统一并确认兼容 |
| spring-ai-starter-mcp-server-webmvc | 1.1.6 | `sfc-ext/sfc-ext-mcp/pom.xml:23` | 统一并确认兼容 |

---

## 8. 风险优先级总览

| 等级 | 项 |
|---|---|
| 🔴 极高 | springfox 替换、jjwt 升级、spring-context-indexer 版本、模块化 starter 重构 |
| 🟠 高 | Authorization Server 并入 Security 7、Redisson 兼容、Hibernate 内部 API、Jackson 2→3、Spring AI 版本、Lombok |
| 🟡 中 | @EntityScan 包名、junit-jupiter、maven-resources-plugin、jakarta.mail、JSpecify nullability、HttpMessageConverters |
| 🟢 低 | javax 残留（已迁移）、WebSocket、Actuator |

配置层面好消息：yml 配置精简，**几乎没有命中 4.0/4.1 的属性重命名**，主要工作集中在**依赖与代码**层面。

---

## 9. 必须额外查阅的外部迁移文档

SpringBoot 迁移指南只是冰山一角，以下框架级破坏性变更**不在 Boot 指南内**，本项目强相关：

1. **[Spring Security 7.0 迁移指南](https://docs.spring.io/spring-security/reference/7.0/migration/)** — 最重要（4 个 SecurityFilterChain、OAuth2 Authorization Server、88 处 @RolesAllowed）
2. **Hibernate 7 迁移文档** — 自定义 ID 生成器（`@IdGeneratorType`、`IdentifierGenerator`、`SharedSessionContractImplementor`）
3. **[Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)**
4. **[Spring Data 2026.0 Release Notes](https://github.com/spring-projects/spring-data-bom/releases/tag/2026.0.0)**
5. **Redisson 官方** — 对 SpringBoot 4.1 / Spring Data Redis 4.x 的兼容版本
