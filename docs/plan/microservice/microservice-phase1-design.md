# 微服务改造 · 档位 1 设计方案（定稿）

> **状态**：Step 1（`sfc-cloud-api` 契约模块）**已实施**；Step 2 起待实施。**阶段 2 的 provider 已二次修订**——"包装 `ClusterService`" → "独立 `cache` 实现" → "**纯内存实现**"，见 §5 决策 5。
> **范围**：仅做服务注册与发现闭环。**不做**跨进程调用（档位 2）、**不做**网关聚合（档位 3）
> **拓扑**：双向注册（咸鱼云与 demo 微服务均注册到注册中心）
> **注册中心**：Nacos 2.x（Docker 部署）
> **核心规则**：**未使用 Nacos ⇒ 未启用微服务模式 ⇒ 完整单体 / 集群状态**，服务注册只在进程内维护、不考虑跨进程（见决策 5）
> **配套文档**：[依赖冲突调试手册](microservice-nacos-dependency-debugging.md)（档位 1 的核心练习） · [阶段 2 provider 修订说明](microservice-stage2-provider-revision-note.md)（决策 5 的完整修订理由与历史）
> **创建日期**：2026-02-12
> **修订日期**：2026-10-03（Step 1 落地；阶段 2 provider 二次修订，见决策 5）

---

## 阅读指引

| 你想了解 | 看第几节 |
|---|---|
| 这件事的目标边界、不做什么 | §1 |
| 整体长什么样 | §2 拓扑 |
| 微服务概念和本项目怎么对上 | §3 概念对照 |
| 要新增哪些接口 | §4 SPI 契约 |
| 三个决策为什么这么定 | §5 决策记录 |
| 具体要动哪些文件、按什么顺序 | §6 实施清单 |
| 有什么坑 | §7 风险 |
| 审阅时该重点看什么 | §8 |

---

## 1. 目标与范围

### 目标

1. 咸鱼云与一个**独立开发的** Spring Boot 微服务，通过 Nacos 完成双向注册与发现
2. 咸鱼云管理端可查看注册中心中的服务实例列表
3. 建立 `ServiceRegistry` / `ServiceDiscovery` SPI 契约，使 `sfc-core` **不依赖 `nacos-client`**（依赖隔离）；standalone 下由进程内 provider 兜底

### 明确不做

| 不做 | 原因 |
|---|---|
| 跨进程调用核心能力（`DiskFileSystem`、`UserService` 等） | 属档位 2。涉及流式语义（`Resource`/`InputStream`/`OutputStream`），是本项目最难的部分 |
| 网关反向代理 `/api/ext/{service}/**` | 属档位 3 |
| 微服务前端资源动态加载 | 属档位 3 |
| 熔断、限流、链路追踪 | 治理能力，档位 1 引入过早 |
| 拆分现有 `sfc-core` | 非目标。`sfc-core` 保持单体 |
| 未启用 Nacos 时的跨进程服务发现 | 未启用微服务模式即完整单体 / 集群状态，不存在异构服务（见决策 5） |

---

## 2. 拓扑

```
            ┌───────────────────────────────────┐
            │          Nacos 2.x                │
            │  namespace: public                 │
            │  group: SFC                        │
            │   ├─ sfc-core         (咸鱼云)      │
            │   └─ sfc-demo-service (独立微服务)  │
            └──────┬──────────────────┬─────────┘
                   │ 注册 / 心跳       │ 注册 / 心跳
                   │ 发现              │ 发现
      ┌────────────▼───────────┐  ┌───▼──────────────────┐
      │  sfc-core              │  │  sfc-demo-service    │
      │  Spring Boot 4.1.0 单体 │  │  独立 Spring Boot 工程 │
      │                        │  │  （仓库外，独立 git）   │
      │  ServiceRegistry SPI   │  │                      │
      │   ├ LocalXxx (进程内)   │  │  仅依赖 nacos-client   │
      │   └ NacosXxx (插件)     │  │  不依赖咸鱼云任何模块   │
      └────────────────────────┘  └──────────────────────┘
```

**关键设计意图**：`sfc-demo-service` **不依赖咸鱼云任何模块**，仅依赖 `nacos-client`。

这直接回答最初的问题——**"后续的其他新的微服务项目单独开发可以吗"**——档位 1 会用实际行动验证：**可以**。

---

## 3. 概念对照：微服务概念 ↔ 本项目已有实现

`ClusterService` 已是一套自研的"微服务"设施，但它只服务**同构**节点，且**键结构里没有服务名的位置**（见决策 5），故档位 1 另建一套注册，与它**职责分离、不合并**：

| 微服务概念 | 存量 `ClusterService`（**保持不动**） | 档位 1 新增（`local` / `nacos`） |
|---|---|---|
| 服务注册 | `registerSelf()` 写 `sfc:cluster:{selfId}` | `local`：写进程内 Map；`nacos`：`registerInstance` |
| 心跳 / 续约 | `ClusterHoldSchedule`（5s）+ 10s TTL | `local`：**无**（进程即生命周期）；`nacos`：客户端内置心跳（见决策 4） |
| 服务发现 | `listNodes()`（`scanKeys`） | `local`：读本进程 Map（**只可能看到自己**）；`nacos`：`selectInstances` |
| 注销 | ❌ 无（等 TTL 过期） | `local`：清 Map；`nacos`：`deregisterInstance` |
| **服务名** | ❌ 无此概念，只有节点 ID | ✅ `ServiceDefinition.serviceName` |
| **命名空间 / 分组** | ❌ 无 | `group` 参与 `local` 的键结构、也是 Nacos 的调用级参数；但**发现侧两者都只能用配置值**——这是 SPI 契约的固有属性（见决策 5） |
| 实例元数据 | `ClusterNodeInfo`（cpu / memory / pid / tempSpace） | `ServiceInstance.metadata`（自由 KV） |
| 健康检查 | 靠 TTL 被动过期 | `local`：无（进程内必然健康）；`nacos`：`ephemeral` 主动心跳 |
| 负载均衡 / 网关 / 熔断 | ❌ 无 | **不在档位 1 范围** |

---

## 4. SPI 契约（定稿）

新增模块 **`sfc-cloud-api`**。**瘦契约模块，零内部依赖、零 Spring 依赖。**

设计原则：**调用方负责解析自身身份，provider 负责注册中心协议**。因此 `register()` 同时接收"我是谁"（`ServiceDefinition`）和"我在哪"（`ServiceInstance`）——provider 无需重复实现地址解析逻辑。

### 4.1 `ServiceInstance`（POJO，跨进程传输）

```java
@Data
@Builder
public class ServiceInstance {
    /** 服务名，如 sfc-core */
    private String serviceName;
    /** 实例唯一标识 */
    private String instanceId;
    /** 对外可达的主机名或 IP */
    private String host;
    /** HTTP 端口 */
    private int port;
    /** 是否健康 */
    private boolean healthy;
    /** 扩展元数据（版本号、协议等） */
    private Map<String, String> metadata;
}
```

### 4.2 `ServiceDefinition`（POJO，自身服务描述）

```java
@Data
@Builder
public class ServiceDefinition {
    /** 服务名，如 sfc-core */
    private String serviceName;
    /** 分组，对应 Nacos group */
    private String group;
    /** 命名空间，对应 Nacos namespace */
    private String namespace;
    /** 扩展元数据 */
    private Map<String, String> metadata;
}
```

> **取舍说明**：这两个模型用**具体 POJO 而非接口**。原因是它们要跨进程序列化，用接口会引入多态序列化（`@JsonTypeInfo`）复杂度。这是"契约设计"的一处真实取舍。

### 4.3 `ServiceRegistry`

```java
public interface ServiceRegistry extends Closeable {
    /** 注册当前实例。重复调用应幂等 */
    void register(ServiceDefinition definition, ServiceInstance instance);

    /** 注销当前实例 */
    void deregister();

    /** 该 provider 当前是否可用（如 Nacos 连接断开时为 false） */
    boolean isAvailable();
}
```

> **为什么 SPI 里没有 `renew()`**：续约语义属于 provider 内部实现细节 —— `local` provider 根本没有续约（进程即生命周期），`nacos` provider 由客户端内置心跳续约。把它暴露到 SPI 会导致调用方必须知道"什么时候该续约"，是错误的分层。**SPI 中保留但无人调用的方法是坏设计**，故不设。

### 4.4 `ServiceDiscovery`

```java
public interface ServiceDiscovery {
    /** 按服务名列出健康实例 */
    List<ServiceInstance> listInstances(String serviceName);

    /** 列出所有已知服务名 */
    List<String> listServiceNames();

    /** 该 provider 当前是否可用 */
    boolean isAvailable();
}
```

### 4.5 Provider 选择机制

**对齐项目既有的 `sys.service.*-provider` 模式**（参见 `RedisRPCInvoker` / `MQRPCInvoker` 的 `@ConditionalOnProperty` 写法）：

| Bean | 生效条件 |
|---|---|
| `LocalServiceRegistry`（单类实现两个 SPI） | `sys.service.registry-provider` = `local`（**`matchIfMissing = true`**） |
| `NacosServiceRegistry` / `NacosServiceDiscovery`（插件内） | `sys.service.registry-provider` = `nacos` |

> **取值按"进程内 / 注册中心"命名**：`local` 与既有 `cache-provider: local`、`lock-provider: local`、`mq-provider: local` 同一家族，语义为"仅当前实例内生效"。
>
> **`matchIfMissing = true` 只能出现在 `local` 一侧**：Nacos 侧若也带，两个 Bean 会同时命中 → `NoUniqueBeanDefinitionException`。
>
> ⚠️ **插件的 Bean 由 `AutoConfiguration.imports` 注册，晚于 `@Component` 扫描**：因此 `@Autowired ServiceRegistry` 注入没有问题，但 `@ConditionalOnBean` 之类的**条件评估看不到它**。这正是"core 常驻一个 `local` provider"优于"core 完全没有 provider"的原因——后者会迫使所有消费者改成 `ObjectProvider` 运行时判空。

调用方直接注入 SPI 接口（单 Bean），与 `RpcAutoConfiguration` 注入单个 `RPCInvoker` 的做法一致。

---

## 5. 决策记录

### 决策 1：SPI 放 `sfc-api` 还是新建 `sfc-cloud-api`？ → **已确认：新建 `sfc-cloud-api`**

**决定性理由**（已核实）：

> `sfc-api` 的 `dao/BaseRepo.java` 直接 `extends JpaRepository`；而 root pom 是在 `<dependencies>`（**不是** `dependencyManagement`）中声明 `spring-boot-starter-data-jpa`、`spring-boot-starter-security`、`spring-boot-starter-data-redis`、`redisson-spring-boot-starter`、`spring-boot-starter-websocket`、`okhttp` 等的。因此**所有模块都被动继承整套技术栈**。

微服务若为了拿一个注册发现接口而引入 `sfc-api`，会被拖进整个 JPA + Security + Redis 栈。而**档位 2 必然需要这个瘦契约模块**，档位 1 正是建立它的最佳时机。

**附带收益**：`sfc-cloud-api` 为档位 2 的 `sfc-remote-client` 铺路。

**代价**：微服务侧若要引用它，需先 `mvn install` 到本地仓库。档位 1 中 demo 微服务**不引用**它，故暂无此负担。

**实施记录（2026-10-03）——本决策成立的前提被实测确认，且必须靠"不继承 root pom"才兑现**：

`sfc-api` 自身声明 **0 个依赖**，实际却解析出 **208 个 jar**（JPA/Hibernate、Security、Redis、Redisson、Netty、MySQL、Thymeleaf、kaptcha、springdoc…）——因为 root `pom.xml` 把这些声明在 `<dependencies>` 而**非** `<dependencyManagement>` 中，**所有子模块无条件继承**。

由此得到一条初版文档没写出的必要条件：**新模块若继承 root pom，"瘦契约"目标立刻落空**——微服务引它会被拖进同样 208 个 jar，那还不如把 SPI 直接留在 `sfc-api`。因此：

| 项 | 实施决定 |
|---|---|
| 父 POM | **不声明 `<parent>`**（与 `sfc-ext` 同款——它同样注册在 root `<modules>` 却不继承 root） |
| 自带构建配置 | `release=25`、`encoding=UTF-8`、**`proc=full`**（JDK 23+ 不默认启用 classpath 注解处理器，漏了 lombok 会**静默失效**） |
| 依赖 | 仅 `lombok`（`provided`）；导入 BOM **只为管版本**，不引入任何 jar |
| 版本 | 独立 `1.0.0`，由 root 属性 `cloud.version` 提供给 `sfc-core` / `sfc-ext-nacos` 引用 |
| 实测结果 | 依赖数 **1**（对照 `sfc-api` 的 208）；`javap` 确认 lombok 真实生效、模块可与 root reactor 一同构建 |

### 决策 2：`nacos-client` 如何进入插件？ → **已确认：精确 `exclusions` 优先，残留项局部 shade relocate；调试定位由你亲手完成**

**约束链条**（已逐层核实，详见配套调试手册）。插件依赖要过**两级校验，后果完全不同**：

```
① checkLibDependence()  —— 按【jar 文件名】粗粒度预过滤
     jar 名 + 版本 与宿主/已注册依赖完全相同  → warn「依赖包版本出现重复，已忽略」→ 跳过该 jar
     jar 名相同但版本不同                     → warn「依赖包版本出现冲突」      → 跳过该 jar
   ⚠️ 这一级【不会】导致启动失败。被跳过的 jar 其类由 parent-first 委派给宿主同名 jar 提供。

② ClassUtils.validUrl() —— 逐 .class 硬门禁
     遍历 jar 内所有 .class，对每个类名调用 loader.getResource(name)
     只要【任一类】已能被合并类加载器解析到（即宿主或先前插件已存在该类）
       → 记入 errorList
       → throw IllegalArgumentException("插件依赖无法加载：\n" + failJar)
       → 【插件加载失败 → 应用启动失败】
```

**推论（两类失败模式，性质完全不同）**：

| 失败模式 | 触发条件 | 表现 |
|---|---|---|
| **启动失败** | 某 jar 中**任一类**与宿主类重复（`validUrl` 命中） | 日志出现「存在以下冲突class」，进程起不来 |
| **运行期崩溃** | 某 jar 被 ① 静默跳过，宿主版本 API 与 nacos-client 期望**不兼容** | 编译通过、启动成功，调用时报 `NoSuchMethodError` / `NoSuchFieldError` / `ClassNotFoundException` |

**这解释了为什么 `exclusions` 仍然必要**：被跳过的 jar 会退回宿主版本。若宿主版本 API 兼容则无事；若不兼容，就会在**运行期**才暴露。而"名字不撞但类撞"的情况会直接**启动失败**。

**方案对比**：

| 方案 | 说明 | 结论 |
|---|---|---|
| **A. 精确 `<exclusions>`** | 排除宿主已有的库，运行时由 parent-first 委派到宿主版本 | ✅ **首选**。需用 `mvn dependency:tree` 逐项核实 |
| **B. `maven-shade-plugin` fat-jar + relocate** | 把依赖打进单 jar 并重定位包名 | ⚠️ **兜底**。需额外处理 `META-INF/services`、`META-INF/nacos` 等资源；nacos-client 内部反射/SPI 加载的类易被漏改 |
| **C. `delayLoadLib` 延迟加载** | `plugin-info.json` 中声明 | ❌ **解决不了版本冲突**。`initDelayLoadLib()` 走同一个 `loadExtraDependencies()`，冲突检查照常执行，仅影响加载**时机** |

> **⚠️ 最常见误解**：`delayLoadLib` 不是依赖冲突的解药。这一点只有读源码才知道。

**分工**：脚手架（插件模块、pom 骨架、实现类）由我搭建；`exclusions` 清单的**逐个调试定位由你完成**——这正是学习点 #1 的核心练习，配套手册提供了完整方法论。

### 决策 3：Nacos 连接参数的配置归属 → **已确认**

| 参数 | 归属 | 理由 |
|---|---|---|
| `registry-provider`、`service-name`、`registry-group` | `SysProperties.Service`（`sfc-api`） | 与 provider 无关的通用概念，**不耦合 Nacos** |
| `server-addr`、`namespace`、账号密码 | `sfc-ext-nacos` 插件自己的 `@ConfigurationProperties("sys.service.nacos")` | 避免 `sfc-api` 耦合 Nacos 概念；不装插件时这些配置无意义 |

**替代路径（未采用）**：走插件自身的 `config-properties.json` + `ConfigService.bindPropertyEntity()`（参见 `McpAutoConfiguration`）。优点是可在后台动态修改；缺点是配置存于数据库，**启动早期不可用**——而注册中心连接参数必须在启动时立即生效。

### 决策 4：续约机制放在哪里 → **已收敛为：档位 1 没有任何续约**

| Provider | 续约机制 | 是否新增 Spring 定时任务 |
|---|---|---|
| `local` | **无** —— 进程即生命周期，无 TTL、无调度 | ❌ |
| `nacos` | nacos-client 对 `ephemeral` 实例**内置**心跳 | ❌ 客户端自管 |

**原则**：**续约不许泄漏到 SPI 之外**（这正是 §4.3 拒绝在 SPI 中设 `renew()` 的同一条理由）。

**结论**：档位 1 **不新增任何定时任务**。`ClusterHoldSchedule` 保持原样运行——它服务于 `ClusterService.listNodes()`（集群内 RPC / 异步任务仍需），与 SPI 的职责不同。

### 决策 5：自研 provider 怎么写？ → **已确认：纯内存实现，不依赖任何系统既有组件**（2026-10-03 二次修订）

**规则**：

> **未使用 Nacos ⇒ 未启用微服务模式 ⇒ 系统处于完整单体 / 集群状态。此状态下的服务注册关系只在进程内维护，完全不考虑跨进程。**

**决策依据**：未启用微服务模式时不存在异构服务，"发现"没有对象；跨进程可见性、TTL、续约、死实例清理都是 **Nacos 存在的意义**，不是内存实现的缺陷。

**替代方案对比**：

| 方案 | 结论 |
|---|---|
| A. 包装 `ClusterService` | ❌ 违反"不依赖既有组件"；且 `register()` 的实例参数只能被静默忽略、无注销方法、键结构里没有服务名的位置 |
| B. 基于 `CacheService` 独立实现 | ❌ 两级 Set 索引 + 5s 续约 + 10s TTL 是常驻成本，却**没有真实消费者**——能写 `sfc:cloud:*` 的只有另一个 `sfc-core` 节点，而它已由 `ClusterService` 覆盖。详见[修订说明](microservice-stage2-provider-revision-note.md) |
| C. **纯内存实现（采用）** | ✅ 约 60 行、零既有依赖、零跨进程语义；standalone 下行为与 `cache-provider` 取值完全解耦 |

**硬约束**：

- 不注入 `CacheService` / `MQService`、不包装 `ClusterService`，只用一个自持 `Map`
- **质量底线**：`register(definition, instance)` 的参数必须真实入库、`deregister()` 只删自己 —— 不得重演方案 A "接口是假的"的问题

**键结构**：

```text
{group}:{serviceName}   →   Map<instanceId, ServiceInstance>
```

`namespace` **不参与**键结构（内存 Map 即单一命名空间）—— 这是**刻意**的，不是遗漏。保留 `group` 是为了让"**发现侧没有 group 参数**（`ServiceDiscovery` 只收服务名）"这条契约的固有属性在代码里可见。

**能力边界（如实记录）**：`local` **无主动健康检查、无命名空间、无变更通知、无跨进程可见性** —— "服务列表"只是进程内视图，集群节点仍看 `GET /api/cluster/listNodes`。

> **完整的历史修订理由**（原 `cache` 方案为何被否、哪些旧论据被推翻）见[阶段 2 provider 修订说明](microservice-stage2-provider-revision-note.md)。

### 决策 6：身份解析与注册时机 → **已确认**

**身份解析**（`ServiceIdentityResolver`）：

| 项 | 取值 | 理由 |
|---|---|---|
| `host` | `sys.service.advertise-address` **优先**；为空则退回 `InetAddress.getLocalHost().getHostName()` | 多网卡 / 容器 / NAS 环境下自动解析出的主机名可能不可达；且 `ClusterNodeInfo.ip` 是**所有网卡地址用 `;` 拼接**（源码实测），不能直接用于注册 |
| `port` | `server.port`；**若为 `0`**（随机端口）则从 web 容器读取实际端口 | `server.port` 是项目既有做法（`application.yml` 中为 8087） |
| `instanceId` | `{host}:{port}` | 与 Nacos `ephemeral` 实例的标识形态同构；**进程重启后复用同一个键**，避免"自己看见两个自己"的 10s 窗口 |

> `advertise-address` 是初版文档的一处遗漏：§7 风险 3 承诺了它，但 §6 的 1.3 字段清单里没有，现已补入。

**注册时机**：`ServiceRegistryInitializer` 监听 **`ApplicationReadyEvent`**，而非 `InitializingBean`。

- 现有 `ClusterServiceImpl` 在 `afterPropertiesSet()`（Bean 初始化早期）就注册，**它能这么做是因为只读配置属性 `server.port`**
- 一旦要支持 `server.port=0` 随机端口，就必须等 web 容器起来才能拿到真实端口 → 时机只能推到 `ApplicationReadyEvent`
- 代价：启动完成到注册之间有一个极短窗口，可接受

**注销时机**：仅 `nacos` provider 需要 —— 实现 `DisposableBean`（或 `@PreDestroy`）→ 关闭时 `deregister()`。

> ⚠️ **不要把注销只寄托在 `Closeable.close()` 上**：`@Component` 扫描的 Bean，Spring 不保证推断并调用 `close()`。显式生命周期回调才是可靠出口。（`local` provider 无需注销：进程退出即清空。）

---

## 6. 实施清单与推荐顺序

> 以下只列**要动哪些文件**与**每步验证什么**，不含代码实现。

### 顺序设计意图

**Step 1~4 可以在完全不接触 Nacos 的情况下完成并验证**，对现有功能零影响。Step 5 起才引入 Nacos 与依赖冲突挑战。

### Step 1：新建 `sfc-cloud-api` 模块 ✅ **已完成（2026-10-03）**

| 文件 | 动作 |
|---|---|
| `sfc-cloud-api/pom.xml` | 新增 |
| `sfc-cloud-api/src/main/java/com/sfc/cloud/model/ServiceInstance.java` | 新增 |
| `sfc-cloud-api/src/main/java/com/sfc/cloud/model/ServiceDefinition.java` | 新增 |
| `sfc-cloud-api/src/main/java/com/sfc/cloud/registry/ServiceRegistry.java` | 新增 |
| `sfc-cloud-api/src/main/java/com/sfc/cloud/registry/ServiceDiscovery.java` | 新增 |
| `pom.xml`（root） | `<modules>` 中注册新模块 |

> 包名沿用 `com.sfc.*`（与 `sfc-rpc` 的 `com.sfc.rpc`、`sfc-task` 的 `com.sfc.task` 一致），而非 `com.xiaotao.saltedfishcloud.*`。

**验证**：`mvn -pl sfc-cloud-api -am install` 成功。**已通过**：依赖数 1（lombok `provided`）、root reactor 构建顺序中该模块排首位、`sfc-api` 回归构建不受影响。

> **实施时的两处偏差（已落地）**：
> 1. 两个 POJO 除 `@Data @Builder` 外**必须**补 `@NoArgsConstructor @AllArgsConstructor`——lombok 的 `@Builder` 会生成全参构造器，从而不再生成默认无参构造器，反序列化时需要它。
> 2. `pom.xml` **不声明 `<parent>`**（理由见决策 1 的实施记录）；模块版本为独立的 `1.0.0`，由 root 属性 `cloud.version` 提供给下游引用。

### Step 2：扩展 `SysProperties`

| 文件 | 动作 |
|---|---|
| `sfc-api/.../config/SysProperties.java` | `Service` 内部类新增 `registryProvider`、`serviceName`、`registryGroup` 三个字段 |

**验证**：`mvn -pl sfc-api install` 成功。

### Step 3：`sfc-core` 进程内 provider + 管理端 API

| 文件 | 动作 | 职责 |
|---|---|---|
| `sfc-core/pom.xml` | 引入 `sfc-cloud-api`（`${cloud.version}`） | — |
| `.../service/registry/LocalServiceRegistry.java` | 新增 | **单类**实现两个 SPI：一个自持 `ConcurrentHashMap`（键 `{group}:{serviceName}`），`register()` 参数真实入库、`deregister()` 只删自己，`isAvailable()` 恒 `true`；**不注入任何既有组件** |
| `.../service/registry/ServiceIdentityResolver.java` | 新增 | 解析本机对外地址 + 端口，生成 `ServiceInstance`（见决策 6） |
| `.../service/registry/ServiceRegistryInitializer.java` | 新增 | 监听 `ApplicationReadyEvent`，调用 `register(definition, instance)` |
| `.../controller/admin/ServiceRegistryController.java` | 新增 | `PREFIX = "/api/admin/sys/service"`，类级 `@RolesAllowed({"ADMIN"})`，`GET /list`，参数用 `@RequestParam` |

**验证**：
- `mvn -pl sfc-core -am install` 成功
- 以 `registry-provider: local` 启动 → **现有功能零回归**（`GET /api/cluster/listNodes` 照常返回集群节点）
- `GET /api/admin/sys/service/list` 返回**自身 1 条**，host / port 与预期一致
- `server.port: 0` 时管理端显示**真实**绑定端口（身份解析的证明，也是阶段 3 风险 3 的预检）

> ⚠️ 原"手工向 `sfc:cloud:*` 塞入第二服务名 → 见两个服务"的验证**已废弃**：进程内实现看不到第二个进程。SPI 的多服务名路径由阶段 4 的 demo 微服务首次真正走通。

### Step 4：三个配置文件同步（AGENTS.md 强制要求）

`sys.` 节点下的新增项**必须同步**这三处，**不改** `application.yml`：

| 文件 | 动作 |
|---|---|
| `sfc-core/src/main/config/application-develop.yml` | 新增 `sys.service.registry-*` |
| `sfc-core/src/main/config/application-product.yml` | 同上 |
| `pre-release/config.yml` | 同上 |

配置内容（默认值保持 `local`，**保证不装插件时项目照常可用**）：

```yaml
sys:
  service:
    # 服务注册 provider：local（进程内，默认，未启用微服务模式）或 nacos（需 sfc-ext-nacos 插件）
    registry-provider: local
    # 注册到注册中心的自身服务名
    service-name: sfc-core
    # 注册中心分组
    registry-group: SFC
    # 本机对外可达地址（host/IP，不含端口）。多网卡/容器/NAS 环境下自动解析可能不可达，此时显式指定（见决策 6）
    advertise-address: ""
    # Nacos provider 专属配置，由 sfc-ext-nacos 插件定义，仅在 registry-provider 为 nacos 时生效
    nacos:
      server-addr: 127.0.0.1:8848
      # 命名空间 ID。留空表示 public 命名空间（它的 ID 是空串，不要写 public）
      namespace: ""
      # 鉴权用户名与密码，服务端未开启鉴权时留空
      username: ""
      password: ""
      # 集群名，用于同一服务内实例的再分组
      cluster-name: DEFAULT
```

> **关于 `sys.service.nacos.*` 的出现时机（实施修正）**：按决策 3 的精神，Nacos 参数归属插件，故阶段 1 与阶段 2 **都没有**写入这些键——它们由阶段 3 随插件一起引入，实际写入 4 个配置文件 + `docs/quick-start/config.md` 两处示例。实测的 Nacos 3.2.4 **未开启鉴权**，因此账号密码默认留空；`namespace` 默认**留空**而非 `public`（public 命名空间的 ID 是空串）。

### Step 5：准备 Nacos 服务端

```powershell
docker run -d --name sfc-nacos -p 8080:8080 -p 8848:8848 -p 9848:9848 -e MODE=standalone nacos/nacos-server:latest
```

> ⚠️ **必须同时暴露 9848 端口**（= 8848 + 1000，gRPC 通信端口）。只映射 8848 会导致客户端反复重连失败——这是最高频的部署坑，Nacos 2.x / 3.x 都一样。
>
> **Nacos 3.x 的控制台已迁到 8080**（不再是 `8848/nacos`）：控制台 `http://127.0.0.1:8080/`，服务端 API 仍在 8848。
> 版本以 Docker Hub 实际可用为准；本次实测 `latest` = **3.2.4**，客户端相应使用 `nacos-client:3.2.4`。

**验证**：控制台可访问。

### Step 6：新建 `sfc-ext-nacos` 插件 ⚠️ **本档位的挑战所在**

| 文件 | 动作 |
|---|---|
| `sfc-ext/pom.xml` | `<modules>` 中注册；`<dependencies>` 加 `sfc-cloud-api`（`provided`，版本 `${cloud.version}`）；⚠️ 该 pom **自带一套属性**（不继承 root），需在其 `<properties>` 中一并补 `cloud.version` |
| `sfc-ext-nacos/pom.xml` | 新增；`nacos-client` 依赖 + `<exclusions>`（**实测无需逐条试错**：BOM 已把版本对齐到与宿主一致，条目与依据见路线图阶段 3 实施记录） |
| `.../config/SysNacosProperties.java` | 新增，`@ConfigurationProperties(prefix = "sys.service.nacos")` |
| `.../NacosServiceRegistry.java` | 新增，实现 SPI |
| `.../NacosServiceDiscovery.java` | 新增，实现 SPI |
| `.../NacosAutoConfiguration.java` | 新增，`@Configuration` + `@ConditionalOnProperty` |
| `src/main/resources/plugin-info.json` | 新增，`loadType: merge` |
| `src/main/resources/META-INF/spring/...AutoConfiguration.imports` | 新增 |

**⚠️ 两个必须知道的操作要点**：

1. **在 `exclusions` 调通之前，不要把 `sfc-ext-nacos` 加进 `application-develop.yml` 的 `plugin.extra-resource`**。原因：插件一旦被加载，其 `plugin-lib/` 依赖会**无条件**进入冲突校验，**即使 `registry-provider` 仍是 `local`**，启动照样失败。
2. **开发模式下必须对插件模块执行 `mvn compile`**。`plugin-lib/` 目录由 `maven-dependency-plugin:copy-dependencies` 在 `compile` 阶段生成到 `target/classes/plugin-lib`，而开发模式正是从 `target/classes` 加载插件的。不编译就没有 `plugin-lib`，依赖不会被加载。

**验证（Step 6 的完整迭代循环）**：

```
加入 extra-resource → 启动
  │
  ├─ 报「存在以下冲突class」→ 把该 jar 补进 <exclusion> → mvn compile → 重启（循环）
  │
  └─ 启动成功
       └─ 检查所有「依赖包版本出现冲突」warn，逐条判断"退回宿主版本是否 API 兼容"
            ├─ 兼容   → 该条无需处理
            └─ 不兼容 → 该 jar 不能排除 → 转 shade relocate
  │
  └─ 【最后一步，不可省略】切换 registry-provider: nacos
       → 确认 Nacos 控制台出现 sfc-core 实例
       → 这是唯一能发现"运行期 NoSuchMethodError"的验证
```

> 注意"启动成功"**不是**通过标准。第一级版本冲突只产生 warn 并静默跳过 jar，进程照样起来——问题会在运行期才暴露。

详细方法论见 [依赖冲突调试手册](microservice-nacos-dependency-debugging.md)。

**通过标准**：
- [ ] 咸鱼云启动成功，且 Nacos 控制台出现 `sfc-core` 实例
- [ ] 日志中**无**「存在以下冲突class」、无「插件依赖无法加载」（这两条=启动失败）
- [ ] 日志中的「依赖包版本出现冲突」警告**逐条评估过**——每条都确认"退回宿主版本后 API 兼容"。这条是警告而非失败，但却是运行期崩溃的预警信号
- [ ] 在 Nacos 中能看到并查询到 `sfc-core` 实例（证明 nacos-client 在运行期确实工作，而非只是启动没报错）

### Step 7：独立 demo 微服务

| 位置 | 动作 |
|---|---|
| `C:\Users\xiaotao\code\sfc-demo-service` | 新建**独立 git 仓库**的 Spring Boot 工程 |

仅依赖 `nacos-client` + `spring-boot-starter-web`，**不依赖咸鱼云任何模块**。以独立仓库形态存在，真实模拟"后续新微服务单独开发"。

**验证**：
- [ ] 启动后 Nacos 控制台出现 `sfc-demo-service` 实例
- [ ] `GET /api/admin/sys/service/list` 返回**两个**服务
- [ ] 停掉 demo 服务，等待心跳超时后该实例从列表消失

### Step 8：编译验证（全程遵守 AGENTS.md）

| 手段 | 适用 |
|---|---|
| **系统 `mvn`** | **凡是改动了 `pom.xml` 就必须用**——本档位 Step 1、2、3、6 均涉及 |
| MCP `build_project` | 常规编译验证 |
| MCP `get_file_problems` | 检查修改文件，修复新产生的警告 |
| 单元测试 | **不生成**——AGENTS.md 静默原则，除非你明确要求 |

---

## 7. 风险

| # | 风险 | 影响 | 对策 |
|---|---|---|---|
| 1 | `nacos-client` 传递依赖与宿主重叠 | **两类后果**：类级重复→启动失败；jar 被静默跳过后宿主版本 API 不兼容→运行期 `NoSuchMethodError` | 决策 2 方案 A；配套调试手册 |
| 2 | Nacos 2.x gRPC 端口 9848 未暴露 | 客户端反复重连失败 | Docker 命令显式 `-p 9848:9848` |
| 3 | 咸鱼云注册自身时的地址解析 | 注册到注册中心的地址不可达 | `sys.service.advertise-address` **配置优先**、自动解析兜底（见决策 6）。注意 `ClusterNodeInfo` 的 `ip` 字段是把所有网卡地址用 `;` 拼接的，**不能直接用于注册**，必须单独取单一可达地址。**standalone 下可用管理端 API 提前预检** |
| 4 | `registry-provider: nacos` 但插件未安装 | 无 `ServiceRegistry` Bean → 启动失败 | 与既有 `mq-provider: local` 需要 `sfc-ext-local-mq` 的行为一致，属预期；默认值保持 `local`。可选改进：由 initializer 抛带明确提示的异常 |
| 5 | **排除过度** 与 **排除不足** 是双向风险 | 排除过度 → 宿主无此类 → `ClassNotFoundException`；排除不足 → 类级冲突启动失败 | 两者都只能通过**启动 + 实际调用**验证发现，**不能只看编译结果**。见调试手册 |

---

## 8. 审阅时建议重点看这四处

1. **§4 SPI 契约**——这是档位 1 唯一需要长期维护的东西。接口一旦定错，档位 2、3 都要跟着改。特别看 `register()` 为何同时要 `ServiceDefinition` 和 `ServiceInstance`，以及为何**故意不提供 `renew()`**。
2. **§5 决策 1 与其实施记录**——`sfc-api` 的依赖污染是本项目一个不太显眼但影响深远的事实（实测 **208 个 jar**）。它会反复影响后续所有"微服务要不要引这个模块"的判断；实施记录给出了本题目前唯一的解（**不继承 root pom**）。
3. **§5 决策 5 的规则**——"未使用 Nacos ⇒ 未启用微服务模式"意味着 standalone 下的注册表**看不到第二个进程**。请确认你接受这一能力边界（换来的是约 60 行零依赖的 provider，与一个常驻成本为零的默认状态）。
4. **§6 Step 6 的两个操作要点**——尤其是"插件放进 `extra-resource` 后，即使 provider 是 `local` 也会触发冲突校验"。这一点若不提前知道，会浪费很多时间在"我明明没启用 Nacos 为什么启动失败"上。

---

## 9. 附：本档位对应的学习点

| 学习点 | 在哪个 Step 被逼出来 |
|---|---|
| **类加载器与依赖隔离** | Step 6：parent-first 委派 + `checkLibDependence()` 硬校验，必须手工设计 `exclusions` |
| **SPI 抽象设计** | Step 1 + §4：自己定义契约而非用框架给定接口；契约的价值是**依赖隔离**（core 不依赖 `nacos-client`），而非 provider 互换 |
| **契约保真度 vs 复用存量** | Step 3 + 决策 5：包装 `ClusterService` 可以让代码编译通过，却会让 SPI 参数沦为摆设——"接口是假的"的典型样本。简化成内存实现后，这条底线依然要守 |
| **Nacos 服务模型** | Step 5~7：namespace / group / serviceName / instance / ephemeral / metadata |
| **何时"不"引入注册中心** | 决策 5：未启用微服务模式时不引入注册中心——把"能力边界"识别成"职责边界" |
| **缓存型注册表的一致性**（源码研读项，非本档位实现） | 教材：`ClusterServiceImpl` + `ClusterHoldSchedule`——TTL 键、5s 续约、无二级索引；以及"Set 成员不随键的 TTL 过期而消失"这一经典陷阱。原 `cache` 方案曾以它为实现目标，现已改为源码研读 |
| **契约与实现分离** | §5 决策 3：契约层只放抽象概念，实现相关配置留在实现方 |
| **存量系统演进的取舍** | 全程：每一步都在"复用既有机制"与"引入新机制"之间做选择 |
