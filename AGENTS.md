# 咸鱼云网盘 AI 编程说明

## 项目说明

这是一个基于 SpringBoot 的网盘项目。

注意：这是一个多模块 Maven 项目（见根 `pom.xml` 的 <modules>），主要模块包括 `sfc-core`、`sfc-api`、`sfc-ext`、`sfc-task`、`sfc-rpc`、`sfc-archive`。扩展插件位于 `sfc-ext` 目录（例如 `sfc-ext-webdav`、`sfc-ext-minio-store` 等）。

## 模块架构

模块构建顺序（也是依赖流向）：

`sfc-api`（基础库，无内部依赖，含模型/异常/校验/常量/BaseRepo） → `sfc-archive/`（压缩引擎） → `sfc-task/`（异步任务框架） → `sfc-rpc/`（RPC 框架） → `sfc-core`（Spring Boot 主应用） → `sfc-ext/`（扩展插件）

- **sfc-api**: 共享模型、`BaseModel`→`AuditModel` 实体基类、`BaseRepo`（`JpaRepository`+`JpaSpecificationExecutor`）、`JsonException`、校验器、注解
- **sfc-core**: 主应用入口 `SaltedfishcloudApplication`，聚合 security/config/controller/service
- **sfc-ext/**: 每个子目录是一个独立 Maven 模块插件，依赖 `sfc-api` 和可选 `sfc-core` API
- **sfc-task**: 内部分 `sfc-task-api` + `sfc-task-core`
- **sfc-archive**: 内部分 `sfc-archive-api` + `sfc-archive-core`
- **sfc-rpc**: 内部分 `sfc-rpc-api` + `sfc-rpc-core`


此外：项目根 `pom.xml` 指定的 Java 版本为 Java 25 — 请在本地构建或运行时确保 JDK 版本兼容（或使用工具链/容器）。

## 编码规范**（重要）**

- 文档化：所有新增方法和字段必须添加 JavaDoc 文档注释
- 异常处理：优先抛出业务自定义异常（JsonException），由全局异常处理器拦截。
- 实体类需要getter或setter时，尽可能使用 Lombok 注解（如 @Data、@Getter、@Setter）来简化代码。
- 涉及批量操作数据的场景，尽可能避免循环处理单条数据和循环单条操作数据库。优先考虑使用 CollectionUtils.partition 对数据拆分批次后，使用 JPA 的批量操作方法。对于批量插入新增实体类，优先考虑使用 DBUtils.batchInsert。

### JPA 及其实体类规范

- JPA Repository层分页查询必须使用`Pageable`参数，返回值为`Page`(org.springframework.data.domain.Page)。
- 具体详细规范见[`generate-jpa-repository`技能](.claude/skills/generate-jpa-repository/SKILL.md)。

### 分页查询

分页查询：controller 和 service 层涉及分页查询数据的，统一使用`CommonPageInfo`(com.xiaotao.saltedfishcloud.model.CommonPageInfo)封装。

示例参考：

- 分页封装示例：`sfc-api/src/main/java/com/xiaotao/saltedfishcloud/utils/PageUtils.java`、`sfc-task/sfc-task-core/src/main/java/com/sfc/task/controller/AsyncTaskController.java`。
- UID 注解示例（Controller 参数）：`sfc-core/src/main/java/com/xiaotao/saltedfishcloud/controller/ResourceController.java` 的多处方法使用 `@UID` 注解来保证 uid 校验。
- 涉及SpringBoot应用参数配置的修改，如果是在`sys.`节点下，需要同步修改`application-develop.yml`、`application-product.yml`和`pre-release/config.yml`中的对应项，不需要修改`application.yml`，`application.yml`下不需要有`sys.`节点。

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
- `@RolesAllowed(SysRole.ADMIN)`: 仅限管理员。
- 默认状态: 所有未标注上述注解的方法，默认要求用户登录

#### UID 安全校验

- 直接参数：Controller 方法中所有 `Long uid` 或 `Integer uid` 参数必须标注 `@UID`。
- 对象封装：若参数为 DTO/Entity 对象且包含 uid 字段，必须在调用 Service 前执行：
  ```java
    UIDValidator.validate(dto.getUid());
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

- 是否遗漏了 @UID 注解？
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