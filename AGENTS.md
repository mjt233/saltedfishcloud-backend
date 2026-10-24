# 咸鱼云网盘 AI 编程说明

## 项目说明

这是一个基于 SpringBoot 的网盘项目。

注意：这是一个多模块 Maven 项目（见根 `pom.xml` 的 <modules>），主要模块包括 `sfc-core`、`sfc-api`、`sfc-common`、`sfc-common-data`、`sfc-core-task`、`sfc-auth`、`sfc-ext`，微服务模块位于 `microservices/`（含异步任务执行者 SDK `sfc-task-worker`）。扩展插件位于 `sfc-ext` 目录（例如 `sfc-ext-webdav`、`sfc-ext-minio-store` 等）。

## 模块架构

模块构建顺序（也是依赖流向）：

`sfc-common`（轻量共享库） → `sfc-common-data`（JPA 基座） → `sfc-api`（宿主基础库：含 `com.sfc.archive` / `com.sfc.task` / `com.sfc.rpc` 的契约与模型） → `sfc-auth`（内部接口鉴权 SDK）/ `sfc-task-worker`（异步任务执行侧 SDK，位于 `microservices/`） → `sfc-core-task`（异步任务调度与控制面，依赖执行侧 SDK） → `sfc-core`（Spring Boot 主应用：依赖 `sfc-core-task` 与 `sfc-task-worker`，内嵌 `CORE` 角色的 Worker） → `sfc-ext/`（扩展插件）

- **sfc-common**: 宿主与微服务**共用**的轻量共享库：`SfcJsonResult` 响应信封、`SfcJsonException`/`SfcErrorInfo`/`SfcCommonError`、`SfcUserConstants`/`SfcSysRole`、`SfcStringUtils`/`SfcMapperHolder`/`SfcTypeUtils`、`SfcPageInfo`/`SfcPageableRequest`/`SfcResult`，以及网关信任头与凭证解析。**不继承根 pom**（自带 BOM，编译基线 4.0.8），**且必须保持零 JPA 依赖**——网关（WebFlux）也依赖它
  - ⚠️ **`SfcJsonResult` 是由宿主的「接口 `JsonResult` + 实现 `JsonResultImpl`」合并成的具体类**：Jackson 会把所有 `getXxx()` 当成可序列化属性，因此凡是非字段访问器的 getter（`isSuccess` / `getDataOrThrow` / `getJsonStr`）**必须标 `@JsonIgnore`**。漏标会导致响应体多出冗余字段，且失败响应序列化时 `getDataOrThrow()` 抛异常 → 客户端只拿到 `{}`
  - ⚠️ **`SfcTypeUtils.convert` 有一段宿主扩展点**：宿主原实现里「字符串 + 目标类型标注 `@ConfigPropertyEntity` → Jackson 反序列化」的分支是**配置绑定的必需路径**，现改为 SPI（`SfcTypeUtils.setStringObjectConverter`），由宿主在 `SfcSharedBaseBridgeConfiguration` 里以 **static `BeanFactoryPostProcessor`** 注册（必须早于任何常规 Bean 实例化，否则 `sysCommonConfig` 之类的配置 Bean 创建时拿不到）
- **sfc-common-data**: 在 `sfc-common` 之上叠加 JPA/Spring Data 的共享数据访问基座：`SfcBaseModel`→`SfcAuditModel` 实体基类、`SfcBaseRepo`（`JpaRepository`+`JpaSpecificationExecutor`）、`SfcCrudService`/`SfcCrudServiceImpl`、雪花主键（`SfcSnowFlakeIdGenerator`/`SfcIdUtil`）、`SfcDBUtils`、`SfcClassUtils`、`@SfcUID` 权限校验。**同样不继承根 pom**。身份与事务通过 SPI（`CurrentUserProvider` / `SfcTransactionManagerProvider`）注入，静态持有者由各端启动时注册（宿主侧见 `SfcSharedBaseBridgeConfiguration`）
- **sfc-api**: 宿主的共享模型、`DiskFileSystem` 抽象、校验器、注解、`SecureUtils`（Spring Security 身份）；基座类已于 Phase 1 迁至 `sfc-common` / `sfc-common-data`
- **sfc-core-task**: 异步任务框架的**调度与控制面**（包名保持 `com.sfc.task`）：类型路由认领调度、执行者健康检查与故障转移、执行上报落库、中断路由、运行日志缓冲与进度查询、微服务内部协议控制器（`/api/internal/task/**`）、前端管理接口 `/api/asyncTask/**`。**不包含执行侧**——认领循环、线程池执行、进度/日志上报、中断端点、优雅停机均由 `sfc-task-worker` SDK 提供；本模块依赖该 SDK，内部协议报文直接复用其 `protocol` 包。契约与模型仍在 `sfc-api`；`sfc-core` 依赖本模块并通过 `@EnableAsyncTask` 引入
- **sfc-task-worker**（`microservices/sfc-task-worker`）: 异步任务**执行侧 SDK**（Spring Boot Starter，零宿主依赖、不继承根 pom，仅依赖 `sfc-common` 与 `sfc-auth`）：Worker 运行时（服务发现定位 core、长轮询认领、`max-concurrent-tasks` 槽位控制、线程池执行、约 1 秒节流进度上报、运行日志批量上报、入站中断端点、停机放弃任务）、内部协议 DTO（`/api/internal/task/**` 的唯一协议定义）、执行者身份 `SfcTaskWorkerIdentity`（serviceId + `host:port`）、用于提交任务与下发中断的 `SfcTaskWorkerClient`。**宿主 core 与微服务共用同一实现**，区别仅在 `sfc.task.worker.role`（`CORE` / `SERVICE`）与任务类型来源
- **sfc-core**: 主应用入口 `SaltedfishcloudApplication`，聚合 security/config/controller/service；依赖 `sfc-core-task` 与 `sfc-task-worker`，由 `com.xiaotao.saltedfishcloud.task.worker.AsyncTaskWorkerConfiguration` 把容器中的 `AsyncTaskFactory` 桥接为 SDK 的 `SfcTaskHandler`，以 `CORE` 角色内嵌 Worker 参与任务执行
- **sfc-auth**: 内部接口（服务间调用）鉴权 SDK：共享密钥（`sfc.internal.key`）、HS256 JWT 自签与校验、Servlet 过滤器与 Feign/RestClient 适配器（不做防重放）。**不继承根 pom**（避免消费方被拖入宿主整套技术栈），同时被 `sfc-core`、`sfc-core-task`、`sfc-task-worker` 与 `microservices/` 侧依赖；单独构建 `microservices/` 前需先 `mvn -pl sfc-auth install`
- **sfc-ext/**: 每个子目录是一个独立 Maven 模块插件，依赖 `sfc-api` 和可选 `sfc-core` API。⚠️ `sfc-ext/pom.xml` 以 `provided` 引用 `sfc-api`，而 provided 的传递依赖不会向下传递，因此**新增共享库依赖时必须在 `sfc-ext/pom.xml` 显式声明**（`sfc-common` / `sfc-common-data` 已声明）
- **sfc-rpc / sfc-archive / 异步任务**: `com.sfc.rpc` / `com.sfc.archive` 的契约与模型位于 `sfc-api`、实现位于 `sfc-core`（不单独成模块）；**异步任务**的契约与模型在 `sfc-api`（`com.sfc.task`）、调度与控制面在独立模块 `sfc-core-task`（同包名 `com.sfc.task`）、执行侧在 `microservices/sfc-task-worker`（core 内嵌 Worker 与微服务共用；`/api/internal/task/**` 协议以 SDK 的 `com.sfc.cloud.task.worker.protocol` 为唯一来源）。所有任务统一「入队 → 按类型认领 → 执行」，执行者（core 节点与微服务实例）的能力与存活**统一以服务注册中心为准**（多 core 节点须使用 `registry-provider=nacos`），文档见 [异步任务](docs/develop/framework/async-task.md)

此外：项目根 `pom.xml` 指定的 Java 版本为 Java 25 — 请在本地构建或运行时确保 JDK 版本兼容（或使用工具链/容器）。

## 微服务机制

项目存在微服务架构支持，如果涉及到微服务的创建，请参考文档 [docs/develop/microservice/index.md](./docs/develop/microservice/index.md)。

## 编码规范**（重要）**

- 文档化：所有新增方法和字段必须添加 JavaDoc 文档注释
- 异常处理：优先抛出业务自定义异常（`SfcJsonException`，位于 `sfc-common`），由全局异常处理器拦截。
- 实体类需要getter或setter时，尽可能使用 Lombok 注解（如 @Data、@Getter、@Setter）来简化代码。
- 涉及批量操作数据的场景，尽可能避免循环处理单条数据和循环单条操作数据库。优先考虑使用 CollectionUtils.partition 对数据拆分批次后，使用 JPA 的批量操作方法。对于批量插入新增实体类，优先考虑使用 `SfcDBUtils.batchInsert`。

### JPA 及其实体类规范

- JPA Repository层分页查询必须使用`Pageable`参数，返回值为`Page`(org.springframework.data.domain.Page)。
- 具体详细规范见[`generate-jpa-repository`技能](.claude/skills/generate-jpa-repository/SKILL.md)。

### 分页查询

分页查询：controller 和 service 层涉及分页查询数据的，统一使用`SfcPageInfo`(com.sfc.cloud.model.SfcPageInfo)封装。

示例参考：

- 分页封装示例：`sfc-api/src/main/java/com/xiaotao/saltedfishcloud/utils/PageUtils.java`、`sfc-core-task/src/main/java/com/sfc/task/controller/AsyncTaskController.java`。
- UID 注解示例（Controller 参数）：`sfc-core/src/main/java/com/xiaotao/saltedfishcloud/controller/ResourceController.java` 的多处方法使用 `@SfcUID` 注解来保证 uid 校验。
- sfc-core模块中涉及SpringBoot应用参数配置的修改，如果是在`sys.`节点下，需要同步修改`application-develop.yml`、`application-product.yml`和`pre-release/config.yml`中的对应项，不需要修改`application.yml`，`application.yml`下不需要有`sys.`节点。

### Controller 接口规范

Controller 接口遵循以下约束：

- 请求方法尽可能只允许使用 `GET` 和 `POST`。
- 避免在 `@RequestMapping`、`@GetMapping`、`@PostMapping` 等注解中使用路径参数（即 `{param}` 形式的占位符），统一使用 `@RequestParam` 或 `@RequestBody` 接收参数。

## 权限与安全

### 网盘数据安全

| 资源类型 | 用户 ID (UID) | 读取权限            | 写入权限         |
|------|-------------|-----------------|--------------|
| 公共资源 | 0           | 所有人 (匿名/用户/管理员) | 仅管理员 (ADMIN) |
| 私人资源 | > 0         | 资源所有者 或 管理员     | 资源所有者 或 管理员  |

### Controller 鉴权注解

业务层面分3个权限等级：

- `@AllowAnonymous`: 允许免登录访问。
- `@RolesAllowed(SfcSysRole.ADMIN)`: 仅限管理员。
- 默认状态: 所有未标注上述注解的方法，默认要求用户登录

#### UID 安全校验

- 直接参数：Controller 方法中所有 `Long uid` 或 `Integer uid` 参数必须标注 `@SfcUID`。
- 对象封装：若参数为 DTO/Entity 对象且包含 uid 字段，必须在调用 Service 前执行：
  ```java
    SfcUIDValidator.validateWithException(dto.getUid(), false);
  ```

#### PATH 安全校验

- 同 `UID 安全校验`，对Controller的参数或封装对象进行校验，需要添加`@ValidPath`注解。
- Controller中对于对象封装的path字段，则使用`ValidPathValidator.valid(java.lang.CharSequence)`

## 工作流与验证机制

### 行为约束

- 禁止直接修复与用户要求无关的顺手发现的bug。发现与本次任务无关的bug应反馈给用户。

### 编译与验证

- 使用MCP的 build_project 对代码进行编译验证，避免使用命令行执行mvn
- 使用MCP的 get_file_problems 对修改的文件进行检查，修复新产生的警告问题
- 如果修改了pom.xml的依赖信息，必须使用系统的`mvn`命令进行编译验证。

### 单元测试原则

- 静默原则：除非用户明确要求（例如：“请为该功能编写测试”），否则严禁自动生成测试类。
- 测试类禁止使用`@SpringBootTest`注解，使用 Mockito 等轻量级测试框架进行单元测试。

### 检查清单

- 是否遗漏了 @SfcUID 注解？
- 公共资源（uid=0）的写入逻辑是否做了管理员校验？
- 是否已经通过 build_project 验证？
- 是否已使用 get_file_problems 检查修改的文件，修复了新产生的警告问题？
- JavaDoc 是否已补全？
- 批量操作是否有使用 CollectionUtils.partition 拆分批次批量处理？

- 是否确认本地/CI 的 JDK 版本满足项目要求（根 pom 指定 Java 25）？
- 是否注意到这是多模块项目（必要时在模块级别运行编译或打包）？

### Git 提交规范

- 当用户要求你将文件提交到Git时，请参考[Git 提交规范](docs/develop/framework/git-commit-convention.md)
- 除非用户明确要求后续执行的修改都自动提交git，否则禁止在执行完任务后立即提交git。请等待用户的明确指示后再执行git提交。