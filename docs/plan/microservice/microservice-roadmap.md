# 微服务改造推进路线图

> **性质**：有序执行清单 + 排序理由。细节见各阶段对应的设计文档。
> **配套**：[档位 1 设计](microservice-phase1-design.md) · [依赖冲突调试手册](microservice-nacos-dependency-debugging.md) · [档位 3 设计](microservice-phase3-frontend-integration.md) · [阶段 2 provider 修订说明](microservice-stage2-provider-revision-note.md)
> **创建日期**：2026-02-12
> **修订日期**：2026-10-05（**阶段 4 方案调整**：由"仓库外独立 git 仓库 + 裸 `nacos-client`"改为"仓库内 `microservices/` 子目录 + Spring Cloud Alibaba 声明式注册"，见阶段 4 的"修订说明"与"版本矩阵"）

---

## 一、排序原则：每步只引入一个新变量

这是整份路线图唯一的组织原则：

> **任何一步，都必须能在"只多了一个新东西"的前提下被验证。**

因为出问题时，**排查面 = 上一步引入的变量数**。如果一步同时引入了「新模块 + 新中间件 + 新依赖体系」，一旦启动失败，你无法判断是：

- 我的 SPI 设计错了？
- Nacos 配置错了？
- `nacos-client` 依赖冲突？

**三者要分别排查，工作量是乘法关系。**

---

## 二、对你示例顺序的修正

你给的顺序：

```
1. 创建 nacos 适配插件，处理兼容问题
2. 创建 sfc-cloud-api 模块
```

**存在两个问题**：

| # | 问题 | 说明 |
|---|---|---|
| 1 | **依赖方向反了** | `sfc-ext-nacos` 要实现 `sfc-cloud-api` 里的 `ServiceRegistry` SPI。**SPI 必须先存在**，否则插件无从实现 |
| 2 | **一上来就撞最硬的骨头** | Nacos 插件是**唯一的高风险步骤**（依赖冲突）。放第一步意味着：还没有管理端 API、接线与身份解析都没验证过、Nacos 服务端可能还没起——报错时无法区分"是我的接线问题"还是"是依赖的问题" |

**修正后的核心顺序**：

```
契约层 → 进程内 provider（接线与身份解析已验证） → Nacos 插件 → 微服务接入
```

**关键收益**：等做到 Nacos 插件时，除了"Nacos 依赖"这一个变量，**其他全都是已验证过的**。这时任何报错都指向依赖冲突——排查面收敛到 1。

---

## 三、总览

| 阶段 | 内容 | 风险 | 规模 | 依赖 | 里程碑 |
|---|---|---|---|---|---|
| 0 | 环境准备（Nacos 容器） | 低 | 小 | — | |
| 1 | 契约层 `sfc-cloud-api` | **无** | 小 | — | |
| 2 | 进程内 provider 闭环（纯内存，不依赖任何既有组件） | **无** | 小 | 阶段 1 | 管理端可见自身节点 |
| 3 | **Nacos 适配插件** | ⚠️ **高** | 中 | 阶段 1、2、0 | |
| 4 | demo 微服务双向注册（SCA 声明式） | 低 | 小 | 阶段 3 | 🏁 **档位 1 完成** |
| 5 | 内部服务令牌（共享设施） | 中 | 中 | 阶段 4 | |
| 6 | **档位 2**：远程核心能力 | 中高 | **大** | 阶段 5 | 🏁 微服务可读写网盘 |
| 7 | **档位 3**：前端发现 + 资源代理 | 中 | 中 | 阶段 4 | 🏁 前端能加载微服务扩展 |
| 8 | 控制面 API 代理 + 鉴权 | **中高**（安全） | 中 | 阶段 5、7 | |
| 9 | 数据面预签名直连 | 中 | 中 | 阶段 8 | 🏁 **档位 3 完成** |
| 10 | 开发期体验（dev server 直连） | 低 | 小 | 阶段 7 | |

**并行机会**：阶段 0 可与阶段 1、2 并行；阶段 7 可与阶段 6 并行（阶段 7 只依赖阶段 4）。

---

## 阶段 0：环境准备（可与阶段 1/2 并行）

- [x] **0.1** 启动 Nacos 容器（**2026-10-05 实测：采用 `latest` = 3.2.4**，与初稿假设的 2.4.3 不同）

  ```powershell
  docker run -d --name sfc-nacos -p 8080:8080 -p 8848:8848 -p 9848:9848 -e MODE=standalone nacos/nacos-server:latest
  ```

- [x] **0.2** 确认控制台可访问：`http://127.0.0.1:8080/`

**为什么提前做**：

1. 镜像拉取可能耗时，早点开始
2. **尽早暴露 9848 端口这个坑**——Nacos 2.x/3.x 客户端都走 gRPC，端口是 8848 + 1000。只映射 8848 会导致客户端反复重连失败，而那时你很可能误判成"依赖问题"
3. 纯环境操作，零代码风险，随时可弃

> **Nacos 3.x 的三个实测事实**（2026-10-05 核实，直接影响阶段 3 的配置与验证路径）：
> 1. **控制台已独立到 8080**（3.0 起从 `8848/nacos` 迁出），返回 `Nacos Console` SPA；8848 仍是服务端 API（`GET /nacos/v1/ns/operator/metrics` → `{"status":"UP"}`）；9848 仍是客户端 gRPC 端口
> 2. 当前容器**未开启鉴权**（8848 的管理端点在无 token 时返回 200），因此 `sys.service.nacos.username/password` 默认留空即可
> 3. 客户端版本与镜像对齐为 **`nacos-client:3.2.4`**（3.x 客户端已改为自包含 fat jar，见阶段 3 实施记录）

---

## 阶段 1：契约层 `sfc-cloud-api`

- [x] **1.1** 新建 `sfc-cloud-api` 模块：`pom.xml` + 2 个 POJO（`ServiceInstance` / `ServiceDefinition`）+ 2 个 SPI（`ServiceRegistry` / `ServiceDiscovery`）
- [x] **1.2** root `pom.xml` 的 `<modules>` 注册新模块
- [x] **1.3** `sfc-api` 的 `SysProperties.Service` 新增 `registryProvider`、`serviceName`、`registryGroup`、`advertiseAddress`
  > **实施记录（配置同步范围）**：仅 `registry-provider: local` 写入配置文件，共 **4 处**——文档所列三处（`application-develop.yml`、`application-product.yml`、`pre-release/config.yml`，后者沿用 `${REGISTRY_PROVIDER:local}` 占位符风格）之外，补充了同样存在 `sys.service` 节点的 `application-develop-mysql.yml`；另在 `docs/quick-start/config.md` 的两处示例中同步。其余三个字段保留 Java 默认值（`sfc-core` / `SFC` / 空串）而不落 YAML——与决策 3 的精神一致，避免出现无人绑定的"假配置"。
  >
  > 因此阶段 2.6 的配置同步工作**已部分完成**（provider 开关项），实施时只需确认，不必重复添加。
- [x] **1.4** 编译验证：`mvn -pl sfc-cloud-api -am install`

**为什么排第一**：它是后续所有实现的前置。**纯新增，不碰任何现有代码路径，零风险，且不需要 Nacos。**

> 设计细节见 [档位 1 设计 §4](microservice-phase1-design.md)。

**验证标准**：模块可独立编译安装；现有模块编译不受影响。

---

## 阶段 2：进程内 provider 闭环

- [x] **2.1** `sfc-core` 引入 `sfc-cloud-api` 依赖（版本用 `${cloud.version}`）
- [x] **2.2** `ServiceIdentityResolver` —— 解析本机对外地址与端口（`advertise-address` 配置优先，自动解析兜底）
- [x] **2.3** `LocalServiceRegistry` —— **单类**实现两个 SPI：一个自持 `ConcurrentHashMap`（键 `{group}:{serviceName}`），`register()` 参数真实入库、`deregister()` 只删自己；**不注入任何既有组件**（无 `CacheService` / `MQService` / `ClusterService`），无 TTL、无续约、无索引
- [x] **2.4** `ServiceRegistryInitializer` —— 监听 `ApplicationReadyEvent` 注册（web 容器已启动、端口已确定）
- [x] **2.5** `GET /api/admin/sys/service/list` —— `controller/admin/` 包，`PREFIX = "/api/admin/sys/service"`，类级 `@RolesAllowed({"ADMIN"})`，参数用 `@RequestParam`
- [x] **2.6** 三个配置文件同步 `sys.service.registry-*`（**默认 `registry-provider: local`**）：`application-develop.yml`、`application-product.yml`、`pre-release/config.yml`
- [X] **2.7** 启动验证：**现有功能零回归** + 管理端 API 返回自身节点（含解析出的 host / port）

> **实施记录（2026-10-05）**：2.1~2.6 已落地并通过编译验证——系统 `mvn -pl sfc-core -am -DskipTests compile`（15 个模块 BUILD SUCCESS，`sfc-core` 174 个源文件）与 IDEA 检查均无新增错误/警告；**2.7 的启动验证待使用者在本地执行**（含 `server.port: 0` 真实端口与 `advertise-address` 配置优先两项预检）。
>
> 六处落地细节：
> 1. **`LocalServiceRegistry` 唯一的注入项是配置类 `SysProperties`**（仅为读取 `registry-group`）："发现侧没有 group 参数"是 SPI 契约的固有属性，发现时必须从配置拼键；**未注入任何功能组件**（无 `CacheService` / `MQService`，也不包装 `ClusterService`）。注册侧取 `ServiceDefinition.group`、发现侧取配置——两者不一致时会查不到实例，与 Nacos `DEFAULT_GROUP` 错配同构，已在类 JavaDoc 中写明。
> 2. **端口解析用 `local.server.port`**：该属性由 SpringBoot 的 `ServerPortInfoApplicationContextInitializer`（注册于 `spring-boot-web-server` 的 `spring.factories`，已解包核实）在 web 容器初始化后写入，因此 `server.port: 0` 能取到真实端口，且无需强转 web 容器类型；取不到时依次退回 `server.port` 配置、`0`。
> 3. **fail-fast 已实现**（设计 §7 风险 4 的"可选改进"）：`ServiceRegistryInitializer` 注入 `ObjectProvider<ServiceRegistry>`，当 `registry-provider: nacos` 却没有任何实现时抛出带明确提示的 `JsonException`，替代晦涩的 `NoSuchBeanDefinitionException`。
> 4. **监听写法采用 `@EventListener(ApplicationReadyEvent.class)`**（与本项目既有写法一致），未实现 `ApplicationListener`：后者在 Spring 7 的 `@NullMarked` 语义下会产生"未标注参数覆盖 @NullMarked 参数"警告，而项目尚无使用 jspecify 注解的先例。
> 5. **接口形态**：`GET /list` 单端点，`serviceName` 为可选 `@RequestParam`；不传参时展平所有服务名的实例返回 `List<ServiceInstance>`（实例自带 `serviceName`），阶段 4 的 demo 微服务接入后同一端点即可看到多个服务。
> 6. **配置同步共 5 处**（AGENTS.md 明列 3 处之外）：`application-develop.yml`、`application-develop-mysql.yml`、`application-product.yml`、`pre-release/config.yml`（沿用 `${ENV:default}` 占位符风格）、`docs/quick-start/config.md` 两处示例。本次写入 `service-name` / `registry-group` / `advertise-address`——1.3 时它们无人绑定故未写，现在已有真实消费者；**未写** `sys.service.nacos.*`，留待阶段 3 由插件引入（符合决策 3）。

**为什么排第二**：

1. **给 standalone 一个合法 provider**——否则 `registry-provider: nacos` 而未装插件时没有任何 Bean
2. **不引入任何新依赖，也不依赖任何既有组件**；此时若出问题，**必然是自己的代码**
3. **提前验证接线、身份解析与消费者注入路径**，使阶段 3 只需面对"依赖冲突"这一个新变量

**核心规则**：

> **未使用 Nacos ⇒ 未启用微服务模式 ⇒ 完整单体 / 集群状态。** 服务注册关系只在进程内维护，不考虑跨进程：无 TTL、无续约、无死实例清理——这些都是 **Nacos 存在的意义**，不是内存实现的缺陷。

**取舍记录（2026-10-03 二次修订）**：原方案是基于 `CacheService` 的独立实现（两级 Set 索引 + 5s 续约 + 10s TTL + 真实注销），现改为**纯内存、不依赖任何既有组件**。完整理由与失效的旧论据见[修订说明](microservice-stage2-provider-revision-note.md)。

`ClusterService` **原样保留**，继续服务集群内 RPC（`ClusterService.request()`）与异步任务分发（`ClusterScheduleJobDispatch`）——两者**职责分离，不合并**。

> **如实记录的能力边界**：`local` 无主动健康检查、无命名空间（内存 Map 即一个命名空间）、无变更通知、**无跨进程可见性**（"服务列表"只是进程内视图，集群节点仍看 `GET /api/cluster/listNodes`）。

**⚠️ 已废弃的验证项**：~~手工向 `sfc:cloud:*` 塞入第二服务名 → 见两个服务~~ —— 进程内实现看不到第二个进程；SPI 的多服务名路径由阶段 4 首次真正走通。

**验证标准**：`registry-provider: local` 下启动正常 + 管理端 API 可见自身节点 + `server.port: 0` 时显示真实端口。

---

## 阶段 3：Nacos 适配插件 ⚠️ 唯一高风险步骤

- [x] **3.1** `sfc-ext/pom.xml` 注册模块；`<dependencies>` 加 `sfc-cloud-api`（`provided`，版本 `${cloud.version}`）；⚠️ 该 pom **自带一套属性**（不继承 root），需在其 `<properties>` 中一并补 `cloud.version`
- [x] **3.2** `sfc-ext-nacos/pom.xml` —— `nacos-client` 依赖，**`<exclusions>` 先留空**
- [x] **3.3** `SysNacosProperties`（`@ConfigurationProperties("sys.service.nacos")`）
- [x] **3.4** `NacosServiceRegistry` + `NacosServiceDiscovery`
- [x] **3.5** `NacosAutoConfiguration`（`@ConditionalOnProperty`，`havingValue = "nacos"`）
- [x] **3.6** `plugin-info.json`（`loadType: merge`）+ `AutoConfiguration.imports`
- [x] **3.7** `mvn -pl sfc-ext/sfc-ext-nacos -am compile` 生成 `plugin-lib`
- [x] **3.8** 跑依赖对比脚本，为「版本冲突」条目补 `<exclusion>`
- [x] **3.9** 加入 `plugin.extra-resource`，启动；报「存在以下冲突class」则继续补 `<exclusion>`，**循环直到启动成功**
- [x] **3.10** 切 `sys.service.registry-provider: nacos`，**确认 Nacos 控制台出现 `sfc-core` 实例**
- [x] **3.11**（清单补漏）`sys.service.nacos.*` 配置同步 —— 4 个配置文件 + `docs/quick-start/config.md` 两处示例；阶段 2 实施记录第 6 条已明确"留待阶段 3 由插件引入"

> **实施记录（2026-10-05）**：3.1~3.11 **全部完成并实测通过**。依赖冲突面为**零**，但"启动成功"花了三轮，每一轮的根因都不是依赖冲突——这才是本阶段真正的内容。
>
> **一、依赖冲突的实际结论**
> 1. `nacos-client-3.2.4.jar` 是一个 14.27MB / 5311 类的**自包含 fat jar**（grpc / protobuf / netty / jackson 等已 relocate 到 `com.alibaba.nacos` 之下，`nacos-api` / `nacos-common` **不再作为独立 artifact**）。因此 grpc、protobuf、guava、netty 这些"教科书级冲突源"**根本不在比对范围内**；插件依赖最终只有 14 个 jar。
> 2. 父模块导入的 `spring-boot-dependencies:4.1.0` BOM 会把 nacos 声明的版本**对齐到与宿主完全相同**（jackson 2.21.2→2.21.4、snakeyaml 2.0→2.6、micrometer 1.15.11→1.17.0）。所以这些 jar 若不排除只会被判为「完全重复(无害)」而静默跳过，**排除与否运行期类路径等价**；排除换来零告警日志，并消除"宿主版本变化后退化为类级冲突"的隐患。
> 3. 静态预检方法（复刻 `checkLibDependence` + `ClassUtils.validUrl` 语义，**启动前**即可得两级结论）：宿主 204 jar × 插件 `plugin-lib` 14 jar × 另外 6 个已加载插件的类集做**精确类名交集** → **第一级零命中、第二级（宿主）零命中、第二级（其它插件）零命中**。启动日志亦确认：nacos 插件**零依赖告警**。
>
> **二、第一轮启动失败：插件把类"暴露"给了宿主的条件判定（本轮最有价值的一课）**
> - **现象**：`NoClassDefFoundError: io/micrometer/core/instrument/MeterRegistry`，Spring 在 `TomcatMetricsAutoConfiguration` 上 `Lookup method resolution failed`。
> - **根因**：宿主有 **5 个**自动配置类以类级 `@ConditionalOnClass(MeterRegistry.class)` 为开关（Tomcat / WebMvc / DataSourcePool / Hibernate / DataRepository 的 metrics），它们本应因"宿主没有 micrometer-core"而全部跳过。插件一旦把 `micrometer-core` 摆上**合并类加载器**，条件判定（走能看到插件 jar 的加载器）就通过；而这些类自身由 **AppClassLoader** 定义，解析方法签名时又找不到该类 → 崩。**条件判定与类内省走的是两个加载器，这是不对称的来源。**
> - **错误修法（已实测否决）**：在插件里排除 `micrometer-core`。nacos-client 的 naming 主路径（`ServiceInfoHolder` → `FailoverReactor`）真的需要 `MeterRegistry`，排除后创建 `NamingService` 立即 `NoClassDefFoundError`。
> - **正解**：**宿主提供 + 插件排除**。`sfc-core/pom.xml` 显式加入 `io.micrometer:micrometer-core`（BOM 管理 → 1.17.0，与插件原本要带的那份完全一致），插件侧保持排除 → 全局只有一份且可被宿主加载器解析。宿主没有 actuator，不会创建 `MeterRegistry` Bean，上述自动配置的方法级 `@ConditionalOnBean` 仍会跳过，**不引入任何指标行为**。
> - **可复用的判定方法**：拿插件依赖的包名去扫宿主的 `spring-boot-*` jar，凡"同时含 `ConditionalOnClass` 与插件包名"的类即风险点。本次扫描只有 `io/micrometer/core` 命中（5 个类），prometheus / apache.hc / HdrHistogram / nacos 均无风险。
>
> **三、第二轮失败：`java -cp` 跑不起来（插件系统的 classpath 形态要求）**
> - **现象**：`MalformedURLException: no !/ in spec` → `[插件初始化]插件信息初始化失败`。
> - **根因**：`PluginInitializer` 会扫描 classpath 上**所有** `plugin-info.json`。`sfc-task-core-1.0.0.jar` 自带该文件，走的是 `jar:` 分支 → `URI.create("jar:file:...").toURL()` 要求 URL 里含 `!/`，扁平 jar 不满足。**IDEA 运行时这些模块是目录**（走 `file:` 分支），所以 IDE 里不会遇到。
> - **结论**：本项目的应用只有两种受支持的运行形态——**IDEA 直接 Run**，或 **`java -jar` 打包产物**（内部是 `jar:nested:`）。**不要用 `-cp <扁平 jar 列表>` 手搓运行**。
>
> **四、第三轮失败：`spring.profiles.active` 是构建期写死的**
> - **现象**：`No qualifying bean of type 'com.sfc.cloud.registry.ServiceDiscovery'`，且日志里插件清单只剩 `[sfc-task,sys]`（没有任何 `从额外资源路径加载插件`）。
> - **根因**：`application.yml` 里是 `spring.profiles.active: ^spring-profile^`，由 Maven 资源过滤在**构建期**替换；`sfc-core/pom.xml` 的 `product` profile 是 `activeByDefault`，而 `develop` **必须显式选**（IDEA 里默认选的就是它）。我执行的一次 `mvn -pl sfc-core -am compile` 把 `target/classes/application.yml` 刷成了 `active: product`，于是 develop 的 `plugin.extra-resource` 全部消失；此时又用 `--sys.service.registry-provider=nacos` 覆盖，`LocalServiceRegistry` 被关掉、插件又没加载 → 没有 `ServiceDiscovery` Bean。
> - **结论**：命令行构建请用 `mvn -P develop ...`；用 `java` 直接跑类目录时显式加 `--spring.profiles.active=develop`。
>
> **五、发现侧的真实 bug：`selectInstances(subscribe=false)` 恒为空**
> - **现象**：实例已注册（Nacos 服务端 `GET /nacos/v3/client/ns/instance/list` 可见、`healthy=true`），但 `GET /api/admin/sys/service/list` 返回 `[]`。
> - **定位**：写了最小客户端探针，在**全新客户端**上逐个调用。结果：`selectInstances(name, group, false, true)` → **`[]`**（且不会退化为服务端查询，2 秒后仍为空）；`selectInstances(..., true, true)` → 正确；**`getAllInstances(name, group)` → 冷启动即正确且不产生订阅状态**。
> - **修正**：`listInstances` 改用 `getAllInstances` 直连查询，并在客户端侧按 `healthy + enabled` 过滤（`getAllInstances` 不过滤健康状态）——正好匹配 SPI"查询一次（无变更通知）"的语义。`listServiceNames()`（`getServicesOfServer`）本身是对的，未改。
>
> **六、其余落地细节**
> 1. **namespace 只能来自插件配置**：`NamingService` 没有 per-call 的 namespace 参数，故 `ServiceDefinition.namespace` 在 nacos provider 中被忽略（`group` 反之取 `definition.group`）。默认值**留空**而非 `public`（public 的 ID 是空串）。实例 `ephemeral=true`；`isAvailable()` 以 `getServerStatus()` 是否 `UP` 判定；`close()` 先注销再关客户端，`destroy()` 委托 `close()`。
> 2. **配置同步共 5 处**：`application-develop.yml`、`application-develop-mysql.yml`、`application-product.yml`、`pre-release/config.yml`（`${NACOS_*:默认}` 占位符风格）、`docs/quick-start/config.md` 两处示例。`plugin.extra-resource` 只在两个 develop 配置中加入。
> 3. **两个工具链坑已补进调试手册 §7.3 / §7.4**：`copy-dependencies` 不清理 `outputDirectory`（改 exclusions 必须 `clean`）；单独构建插件不带 `-am` 会拿 `~/.m2` 里的旧 `sfc-api` 编译（报"找不到符号 `getRegistryGroup`"）。
>
> **七、验证证据（3.9 / 3.10）**
> - `provider=local`：`Started SaltedfishcloudApplication in 22.333 seconds`；`[服务注册] 进程内注册表已登记 key=SFC:sfc-core instanceId=XIAOTAO-AMD-YES:8087`；API 返回该实例。
> - `provider=nacos`：`Nacos 客户端初始化完成 serverAddr=127.0.0.1:8848 namespace=<public> cluster=DEFAULT 鉴权=未配置` → `Nacos 注册完成 serviceName=sfc-core group=SFC instance=XIAOTAO-AMD-YES:8087 ephemeral=true`；Nacos 服务端返回 `{"instanceId":"XIAOTAO-AMD-YES#8087#DEFAULT#SFC@@sfc-core","healthy":true,"ephemeral":true}`；**API 两种端点形态均返回该实例且 instanceId 是 Nacos 生成值**（证明经 Nacos 往返，而非本地表）。
> - **配置已回退为默认 `registry-provider: local`**（验证是通过 `--sys.service.registry-provider=nacos` 命令行覆盖完成的，不依赖改文件）。
> - **既存告警（与本插件无关，未处理）**：`sfc-ext-mcp` 自身的 `jackson-dataformat-yaml-3.1.4` 与宿主 2.21.4 冲突、`commons-io`/`jspecify`/`jackson-core` 完全重复——都是该插件既有的依赖状况。

**为什么排第三**：

1. **前置全部就绪**——SPI 已存在、`local` provider 的接线与身份解析已跑通、Nacos 服务端已起
2. 因此排查面**收敛到依赖冲突这一个变量**
3. 避免了"代码问题 / 配置问题 / 依赖问题"三者混淆

**⚠️ 五个必须知道的操作要点**（详见 [调试手册 §7](microservice-nacos-dependency-debugging.md)）：

| 要点 | 后果 |
|---|---|
| 插件一旦进 `plugin.extra-resource`，依赖校验**无条件执行**，与 `registry-provider` 取值无关 | 会困惑于"我明明没启用 Nacos 为什么启动失败" |
| 改了 `<exclusions>` **必须重新 `mvn compile`** | IDE 的 Build 不触发 `copy-dependencies`，会以为改动无效 |
| 而且**必须 `clean`**：`copy-dependencies` 不清理 `outputDirectory` | 被排除的 jar 仍留在 `plugin-lib` 参与校验，会以为"排除项根本没生效" |
| 单独构建插件**必须带 `-am`**：否则 `sfc-api` 取 `~/.m2` 里的旧 jar | 报"找不到符号 `getRegistryGroup`"这类错，**看起来像自己的代码错了** |
| **"启动成功"不是通过标准** | 粗粒度版本冲突只产生 warn 并静默跳过 jar，问题会在运行期才暴露 |

**规模**：代码量不大（中），但**调试迭代可能占整个项目最多的时间**。

**验证标准**：见 [调试手册 §8 完成标准](microservice-nacos-dependency-debugging.md)。

---

## 阶段 4：demo 微服务双向注册 🏁 档位 1 完成

> **方案修订（2026-10-05）**：本节原方案是「**独立 git 仓库** + 仅依赖**裸 `nacos-client`**」，现按实际讨论改为
> 「**仓库内 `microservices/` 子目录** + **Spring Cloud Alibaba 声明式注册**」，并以该方案为准。
> 两处改动的理由见下方"修订说明"，本次实际执行的版本矩阵见"版本矩阵"。

- [x] **4.1** 新建 demo 微服务工程 `microservices/sfc-demo-service`：**独立 POM 边界**（不继承 root pom、不加入 root `<modules>`）、**零咸鱼云依赖**
- [x] **4.2** `application.yml` 配置服务名 `sfc-demo-service`、分组 `SFC`、命名空间 public（空串）、Nacos 地址 `127.0.0.1:8848`、端口 `8090`，启动
- [x] **4.3** 验证 Nacos 中出现**两个**实例（`sfc-core` 与 `sfc-demo-service`）
- [x] **4.4** 验证 `GET /api/admin/sys/service/list` 返回两个服务
- [x] **4.5** 停掉 demo 服务，确认实例从列表消失（**实测 ≤5s**，无需等心跳超时，见实施记录）

> **实施记录（2026-10-05）：4.1~4.5 全部完成并实测通过。** 本阶段唯一的新变量是"**第二个进程 / 第二个服务名**"，
> 排查面收敛到 1，实际也**没有出现任何意外**——与阶段 3 的三轮启动失败形成鲜明对照，这正是"每步只引入一个新变量"要换来的结果。
>
> **一、落地细节**
> 1. **工程位置**：`microservices/sfc-demo-service`（本仓库内）。pom 里 **`<relativePath/>` 必须显式留空**——工程位于仓库内，
>    Maven 默认会按 `../pom.xml` 向上找到**咸鱼云的 root pom**，这是"把工程放进仓库"新引入的一个坑。
> 2. **依赖规模对照**：本工程解析出 **62 个 jar**（`spring-boot 4.0.8` / `spring-cloud-commons 5.0.0` / `nacos-client 3.1.1`）；
>    对照 `sfc-api` 的 **208 个**、`sfc-cloud-api` 的 **1 个**。
>    **零咸鱼云依赖**——`mvn dependency:list | Select-String 'com.xiaotao|sfc-*'` 命中 **0 条**。这条即"新微服务可独立开发"的硬证据。
> 3. **注册完全声明式**：工程内**没有任何一行注册代码**，日志 `nacos registry, SFC sfc-demo-service 192.168.5.105:8090 register finished` 由 starter 打出。
> 4. **环境坑（值得记下）**：Maven 首次下载新依赖需要写 `~/.m2/repository`（在**工作区之外**），受限于文件策略会被拒；
>    完整构建用时 2 分 41 秒（依赖走本机 Nexus 镜像 `nexus.xiaotao2333.top:344`）。
>
> **二、验证证据**
> - **4.3** Nacos 服务端 `GET /nacos/v3/client/ns/instance/list` 同时返回两个实例：
>   `XIAOTAO-AMD-YES#8087#DEFAULT#SFC@@sfc-core` 与 `192.168.5.105#8090##SFC@@sfc-demo-service`，两者均 `healthy=true`、`ephemeral=true`。
>   **两边地址形态不同是预期的**：core 用主机名（`ServiceIdentityResolver` 的行为），SCA 用网卡 IP。
> - **4.4** `GET /api/admin/sys/service/list` 返回**两个服务**——本档位**首次真正走通 SPI 的多服务名路径**
>   （`listServiceNames()` + `listInstances()`），并证明**手写的 `getAllInstances` 查询能看见 SCA 声明式注册的实例**（双向互认成立）。
> - **4.5** 强制结束 demo 进程（模拟崩溃、**不做优雅注销**）后，实例在 **≤5s 内**从服务端消失，管理端随即只剩 `sfc-core` 一个服务。
>   ⚠️ **比预期快得多**：临时实例走客户端 gRPC 长连接，**连接断开本身就是下线信号**，不必等 15s 心跳超时 / 30s `ipDeleteTimeout`。
>   本节标题里"等待心跳超时"的表述据此修正。
>
> **三、一处留给阶段 7 的对照发现**
> 两边的实例 metadata **形态不同**：core 是**空 `{}`**（`ServiceRegistryInitializer` 传 `metadata = null`）；
> SCA 注册的实例自带 `preserved.register.source: SPRING_CLOUD` 与 `IPv6: [...]`。
> 因此阶段 7 从 metadata 聚合扩展清单时，**不能假设 metadata 里只有自己写的键**，必须按 `sfc.` 前缀过滤。

**为什么在这里**：这是档位 1 的收口，也是**第一个自然停下来喘息的检查点**。

### 修订说明：与原方案的两处偏差

| # | 原方案                                             | 现方案                                                                            | 理由                                                                                                                   |
|---|-------------------------------------------------|--------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| 1 | 本仓库工作区外的**独立 git 仓库**                           | 本仓库内 `microservices/sfc-demo-service`                                          | 微服务工程统一在仓库内维护，便于与本套文档同步演进。**"独立开发"的验证意图不变**，改由**独立的 POM 边界**证明：不继承 root pom、不进 root `<modules>`、零咸鱼云依赖               |
| 2 | 仅依赖裸 `nacos-client` + `spring-boot-starter-web` | Spring Cloud Alibaba **声明式注册**（`spring-cloud-starter-alibaba-nacos-discovery`） | 覆盖"框架托管"这条**主流接入路径**，并与咸鱼云侧"手写 SPI provider + 裸 `nacos-client`"形成**对照**：同一个注册中心、两种接入方式，**双向互认**即同时证明了 SPI 抽象与协议实现都正确 |

> **第 2 条的顺带收益**：原方案下 demo 与 core 用的是同一个客户端库，验证的是"配置对不对"；
> 现方案下两边是**两套不同的实现**（SCA 托管 vs 手写），能发现的问题面更大——例如 group 大小写、心跳续约方式、实例元数据形态的差异。

### 版本矩阵（2026-10-05 实测 · Maven Central 元数据 + SCA 官方 Release Notes）

| 组件 | 取值 | 依据 |
|---|---|---|
| Spring Cloud Alibaba | **2025.1.0.0** | Maven Central 上的 `latest`（2026-02-06 发布） |
| Spring Cloud | **2025.1.0** | SCA 官方矩阵声明的配对版本（starter 实测依赖 `spring-cloud-commons:5.0.0`） |
| Spring Boot（**demo 自用**） | **4.0.8** | SCA 2025.1.0.0 的官方矩阵是 **Boot 4.0.x** |
| nacos-client | **3.1.1**（由 SCA 管理，不覆盖） | starter 的 pom 实测；3.x 客户端与服务端 3.2.4 同代兼容 |

⚠️ **一个必须知道的错位**：咸鱼云宿主用的是 **Spring Boot 4.1.0**，**不在 SCA 的支持矩阵内**。
demo 是**独立微服务**，因此不对齐宿主版本，而是取 SCA 矩阵内的 Boot 4.0.x——这本身就是"微服务可以独立演进"的一个具体体现。

### 必须遵守的收敛约定（对应评审报告 🟡-1）

> **`group` 与 `namespace` 必须在所有参与者之间保持一致**：本阶段取 `group: SFC`、命名空间 public（**它的 ID 是空串**）。

原因是**发现侧只能用它自己的配置去查询**（`NacosServiceDiscovery.configuredGroup()` 读 `sys.service.registry-group`），
而 **SCA 的注册侧用 `spring.cloud.nacos.discovery.group`**。两侧取值不一致时，现象是"**注册成功但互相看不见**"
——先查 Nacos 服务端 API、再查 core 的管理端 API，就能把"没注册上"与"查不到"区分开（本阶段的验证顺序即按此设计）。

- [x] 🚩 **暂停检查点 1（2026-10-05 达成）**：此时你已完整走通"服务注册 → 服务发现 → 服务治理基础设施"，并且**验证了原问题的核心答案**——"后续新微服务单独开发可以吗"→ 可以，demo 服务一行咸鱼云代码都没引（实测**零咸鱼云依赖**、独立 POM 边界、独立 Spring Boot 版本）。

---

## 阶段 5：内部服务令牌（档位 2 与 3 的共享设施）

- [ ] **5.1** `InternalServiceTokenService` —— 签发与校验
- [ ] **5.2** 两类令牌：`issueForService(serviceName)`（服务身份）、`issueForUser(uid, roles, scope)`（用户委托）
- [ ] **5.3** 密钥配置（独立于用户 JWT 密钥）

**为什么单列一个阶段**：档位 2 和档位 3 都需要它，但**方向相反**：

| 场景 | 方向 | 令牌类型 |
|---|---|---|
| 档位 2：微服务调 core 能力 | 微服务 → core | 服务身份 |
| 档位 3：core 调微服务 API | core → 微服务 | 用户委托（含 uid） |

底层机制相同（core 签发、携带、被调方校验），**做一次即可复用**。放在分岔点之前，避免两边各实现一遍。

---

## 阶段 6：档位 2 —— 远程核心能力 ⭐ 学习价值最高

- [ ] **6.1** core 暴露内部 API（`/internal/**`），**首批只做只读、无流式的方法**
- [ ] **6.2** 新建 `sfc-remote-client` SDK 模块
- [ ] **6.3** `RemoteDiskFileSystem implements DiskFileSystem` —— 让微服务侧代码与本地实现**同构、零改造**
- [ ] **6.4** 流式接口专项设计（`getResource` / `saveFileByStream` / `newOutputStream`）
- [ ] **6.5** 微服务引入 SDK，实际调用验证

**为什么推荐排在档位 3 之前**：

1. **它决定微服务的"业务能力边界"**——如果核心能力调不通，前端接上去也只是个空壳
2. 它的传输层与鉴权设施**可被档位 3 的代理层直接复用**
3. 它是难度最高、学习价值最大的一块（跨进程流式语义），**越早暴露越好**

**"首批只做只读、无流式"是刻意的**：先把跨进程调用的骨架和鉴权打通，再逐步啃流式这个硬骨头。见 [档位 1 设计 §1 的非目标划分思路](microservice-phase1-design.md)。

> ⚠️ **免责提醒**：`DiskFileSystem` 的流式语义（`Resource` / `InputStream` / `OutputStream`）跨进程**无法直接序列化**，必须另开传输通道。这是"拆"的代价的具象体现，也是本项目最难的一块。规划时要给它留出充足时间。

- [ ] 🚩 **暂停检查点 2**：微服务能真正读写网盘文件。

---

## 阶段 7：档位 3 上半 —— 前端发现与资源代理

- [ ] **7.1** `ExtensionInfo` / `ExtensionResource` DTO（字段严格对齐前端 TS 类型）
- [ ] **7.2** `ServiceExtensionRegistry` —— 从 Nacos 实例 metadata 聚合扩展清单
- [ ] **7.3** `GET /api/ext/manifest`（`@AllowAnonymous`）
- [ ] **7.4** `ServiceProxyController` —— `@RequestMapping("/api/ext/**")`，**无 `{}` 占位符**（AGENTS.md 合规）
- [ ] **7.5** `SecurityConfig` 放行 `/api/ext/manifest` 与 `/api/ext/*/static/**`
- [ ] **7.6** 缓存策略 + 清单 URL 带 `?v={version}`
- [ ] **7.7** 前端：`sfc-common/api/sys.ts` 加 API 定义
- [ ] **7.8** 前端：`Extension.ts` 的 `getExtensionResource()` 增加数据源 + **失败降级返回 `[]`**
- [ ] **7.9** demo 微服务提供 `static/hello.js`（先手写文件验证链路）
- [ ] **7.10** 验证：DevTools Network 看到 `hello.js` 被加载

**为什么在这里**：双向注册（阶段 4）已打通，"能被发现"→"能被前端使用"是自然延伸。且**本阶段不依赖档位 2**，可与阶段 6 并行。

> 详见 [档位 3 设计 §5、§6](microservice-phase3-frontend-integration.md)。

---

## 阶段 8：控制面 API 代理 + 鉴权 ⚠️ 安全敏感

- [ ] **8.1** 控制面转发（必须**流式管道**，禁止 `byte[]` 全量缓冲）
- [ ] **8.2** 接入阶段 5 的内部服务令牌
- [ ] **8.3** 实例选择 + 轮询负载均衡 + 故障重试
- [ ] **8.4** 全部实例不可用 → 返回 **503**（而非 500）
- [ ] **8.5** ⚠️ **UID / PATH 校验语义**：信任边界收敛到内部令牌，**微服务只信令牌中的 uid，忽略请求参数中的 uid**
- [ ] **8.6** 验证：**篡改 uid 的越权请求必须被拒**

**⚠️ 安全红线**：`@UID` / `@ValidPath` 校验是在 core 的 Controller 层生效的。代理路径上如果只做转发，攻击者构造 `/api/ext/svc/...?uid=999` 就能越权访问他人网盘数据。**这一步不能省，也不能"先跑通再补"。**

---

## 阶段 9：数据面预签名直连 🏁 档位 3 完成

- [ ] **9.1** 预签名 URL 签发（含 uid、文件、过期时间的一次性 token）
- [ ] **9.2** 微服务侧下载端点
- [ ] **9.3** （可选，最优）配置 Nginx 把 `/dl/{service}/**` 直接路由到微服务，**绕过 core 的 Java 进程**
- [ ] **9.4** 验证：大文件下载时确认 **core 进程带宽 / 线程无显著增长**

**部署前提**：预签名要求**微服务的下载端点对浏览器可达**。若微服务只在 core 可达的容器内网，此方案不成立——**阶段 8 的流式代理就是降级路径**，所以它不能省。

---

## 阶段 10：开发期体验

- [ ] **10.1** 清单支持开发期开关，指向微服务前端的 Vite dev server
- [ ] **10.2** 验证：微服务前端改代码，刷新主页面即生效，**不碰主前端仓库与后端**

**原理**：`mount()` 创建的是普通 `<script src>`（无 `crossorigin`），而**普通 script / link 不受 CORS 限制**。

---

## 四、何时可以停下来

这是个学习项目，**不是必须一次做完**。三个天然检查点：

| 检查点 | 完成后你掌握了什么 | 对应阶段 |
|---|---|---|
| 🚩 **1** ✅ 2026-10-05 | 服务注册发现全链路、SPI 抽象设计、类加载器与依赖冲突、**新微服务完全独立开发** | 阶段 4 |
| 🚩 **2** | 跨进程调用存量能力、内部 API 契约设计、SDK 代理模式、**流式语义的代价** | 阶段 6 |
| 🚩 **3** | 网关模式、信任边界与鉴权传递、控制面/数据面分离、前端模块化扩展 | 阶段 9 |

**每个检查点都是可交付、可验证、可长期停下的状态。** 不必追求一次走完。

---

## 五、四条最容易走错的地方

| # | 错误 | 后果 | 正确做法 |
|---|---|---|---|
| 1 | 把 Nacos 插件放第一步 | 同时面对"代码 / 配置 / 依赖"三个未知，排查量乘算 | 先在阶段 2 把接线、身份解析与消费者注入路径验证掉（`registry-provider: local`） |
| 2 | 依赖冲突调试期间把插件留在 `extra-resource` | 误以为"没启用 Nacos 就不会受影响" | 调通前不加入；且记得改 `<exclusions>` 后重新 `mvn compile` |
| 3 | 代理层跳过 UID / PATH 校验 | **越权访问他人网盘数据** | 信任边界收敛到内部令牌（阶段 8.5） |
| 4 | 把 `local` provider 当成 `ClusterService` 的替代品，或试图"统一"两者 | **集群内 RPC 与异步任务分发失效**（`ClusterService.request()`、`ClusterScheduleJobDispatch` 仍依赖 `sfc:cluster:*`） | 职责分离而非替换：`ClusterService` 服务集群内**同构**节点（RPC / 任务），`local` provider 只服务进程内 SPI 调用 |
| 5 | 只顾注册侧配置，忘了**发现侧只能用它自己的配置查询** | "注册成功但互相看不见"，会误判成插件或注册中心的 bug | 所有参与者收敛到同一 `group` / `namespace`（本档位：`SFC` / public）；排查顺序**先 Nacos 服务端、再 core 管理端** |

---

## 六、规模感知

| 阶段 | 代码量 | 调试/踩坑 | 说明 |
|---|---|---|---|
| 0 | — | 小 | 环境操作 |
| 1 | 小 | **无** | 纯新增 |
| 2 | 小 | 无 | 纯内存 Map + 身份解析 + 管理端 API，零既有依赖 |
| 3 | 中 | ⚠️ **可能最大** | 依赖冲突迭代，**预留充足时间** |
| 4 | 小 | 小 | 独立小工程 |
| 5 | 中 | 中 | 安全设施 |
| 6 | **大** | **大** | 流式语义是最大难点 |
| 7 | 中 | 中 | 前后端各改少量 |
| 8 | 中 | 中 | 安全敏感，需仔细验证 |
| 9 | 中 | 中 | 依赖部署形态 |
| 10 | 小 | 小 | — |

**时间分布的非直觉之处**：阶段 3 代码量只有"中"，但**调试时间可能占全程最多**——因为依赖冲突是试错式的。而阶段 6 代码量与难度都是"大"，是真正需要设计能力的地方。
