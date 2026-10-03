# 微服务改造推进路线图

> **性质**：有序执行清单 + 排序理由。细节见各阶段对应的设计文档。
> **配套**：[档位 1 设计](microservice-phase1-design.md) · [依赖冲突调试手册](microservice-nacos-dependency-debugging.md) · [档位 3 设计](microservice-phase3-frontend-integration.md)
> **创建日期**：2026-02-12

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
| 2 | **一上来就撞最硬的骨头** | Nacos 插件是**唯一的高风险步骤**（依赖冲突）。放第一步意味着：还没有管理端 API 可验证"发现"是否成功、Nacos 服务端可能还没起、没有 `cluster` provider 作为对照组——报错时无法区分"是我的问题"还是"是依赖的问题" |

**修正后的核心顺序**：

```
契约层 → 自研 provider（可验证的对照组） → Nacos 插件 → 微服务接入
```

**关键收益**：等做到 Nacos 插件时，除了"Nacos 依赖"这一个变量，**其他全都是已验证过的**。这时任何报错都指向依赖冲突——排查面收敛到 1。

---

## 三、总览

| 阶段 | 内容 | 风险 | 规模 | 依赖 | 里程碑 |
|---|---|---|---|---|---|
| 0 | 环境准备（Nacos 容器） | 低 | 小 | — | |
| 1 | 契约层 `sfc-cloud-api` | **无** | 小 | — | |
| 2 | 自研 provider 闭环 | **无** | 中 | 阶段 1 | 管理端可见自身节点 |
| 3 | **Nacos 适配插件** | ⚠️ **高** | 中 | 阶段 1、2、0 | |
| 4 | demo 微服务双向注册 | 低 | 小 | 阶段 3 | 🏁 **档位 1 完成** |
| 5 | 内部服务令牌（共享设施） | 中 | 中 | 阶段 4 | |
| 6 | **档位 2**：远程核心能力 | 中高 | **大** | 阶段 5 | 🏁 微服务可读写网盘 |
| 7 | **档位 3**：前端发现 + 资源代理 | 中 | 中 | 阶段 4 | 🏁 前端能加载微服务扩展 |
| 8 | 控制面 API 代理 + 鉴权 | **中高**（安全） | 中 | 阶段 5、7 | |
| 9 | 数据面预签名直连 | 中 | 中 | 阶段 8 | 🏁 **档位 3 完成** |
| 10 | 开发期体验（dev server 直连） | 低 | 小 | 阶段 7 | |

**并行机会**：阶段 0 可与阶段 1、2 并行；阶段 7 可与阶段 6 并行（阶段 7 只依赖阶段 4）。

---

## 阶段 0：环境准备（可与阶段 1/2 并行）

- [ ] **0.1** 启动 Nacos 2.x 容器

  ```powershell
  docker run -d --name sfc-nacos -p 8848:8848 -p 9848:9848 -e MODE=standalone nacos/nacos-server:v2.4.3
  ```

- [ ] **0.2** 确认控制台可访问可登录：`http://127.0.0.1:8848/nacos`（`nacos` / `nacos`）

**为什么提前做**：

1. 镜像拉取可能耗时，早点开始
2. **尽早暴露 9848 端口这个坑**——Nacos 2.x 客户端走 gRPC，端口是 8848 + 1000。只映射 8848 会导致客户端反复重连失败，而那时你很可能误判成"依赖问题"
3. 纯环境操作，零代码风险，随时可弃

> 镜像 tag 以 Docker Hub 实际可用为准；若无 `v2.4.3` 取 2.4.x 最新。

---

## 阶段 1：契约层 `sfc-cloud-api`

- [x] **1.1** 新建 `sfc-cloud-api` 模块：`pom.xml` + 2 个 POJO（`ServiceInstance` / `ServiceDefinition`）+ 2 个 SPI（`ServiceRegistry` / `ServiceDiscovery`）
- [x] **1.2** root `pom.xml` 的 `<modules>` 注册新模块
- [ ] **1.3** `sfc-api` 的 `SysProperties.Service` 新增 `registryProvider`、`serviceName`、`registryGroup`
- [x] **1.4** 编译验证：`mvn -pl sfc-cloud-api -am install`

**为什么排第一**：它是后续所有实现的前置。**纯新增，不碰任何现有代码路径，零风险，且不需要 Nacos。**

> 设计细节见 [档位 1 设计 §4](microservice-phase1-design.md)。

**验证标准**：模块可独立编译安装；现有模块编译不受影响。

---

## 阶段 2：自研 provider 闭环

- [ ] **2.1** `sfc-core` 引入 `sfc-cloud-api` 依赖
- [ ] **2.2** `ServiceIdentityResolver` —— 解析本机对外地址与端口（复用 `ClusterServiceImpl.getSelf()` 既有逻辑）
- [ ] **2.3** `ClusterServiceRegistry` + `ClusterServiceDiscovery` —— 包装现有 `ClusterService`，**不改变其现有行为**
- [ ] **2.4** `ServiceRegistryInitializer` —— 启动时注册
- [ ] **2.5** `GET /api/admin/sys/service/list` —— `controller/admin/` 包，`PREFIX = "/api/admin/sys/service"`，类级 `@RolesAllowed({"ADMIN"})`，参数用 `@RequestParam`
- [ ] **2.6** 三个配置文件同步 `sys.service.registry-*`（**默认 `registry-provider: cluster`**）：`application-develop.yml`、`application-product.yml`、`pre-release/config.yml`
- [ ] **2.7** 启动验证：**现有功能零回归** + `GET /api/admin/sys/service/list` 返回自身节点

**为什么排第二**：

1. **建立可验证的"发现"能力**——后续 Nacos 插件要用同一套 API 验证，形成**对照组**
2. **不引入任何新依赖**，纯用项目已有的 `ClusterService`
3. 此时若出问题，**必然是自己的代码**，与外部依赖无关

> **如实记录的能力边界**：`ClusterService` 没有服务名概念，所以 `ClusterServiceDiscovery` 只能服务配置的单一服务名。这不是缺陷，而是"自研 provider 能力边界"的体现——也正是 Nacos 存在的意义。

**验证标准**：`registry-provider: cluster` 下启动正常 + 管理端 API 可见自身节点。

---

## 阶段 3：Nacos 适配插件 ⚠️ 唯一高风险步骤

- [ ] **3.1** `sfc-ext/pom.xml` 注册模块；`<dependencies>` 加 `sfc-cloud-api`（`provided`，镜像 `sfc-api` 写法）
- [ ] **3.2** `sfc-ext-nacos/pom.xml` —— `nacos-client` 依赖，**`<exclusions>` 先留空**
- [ ] **3.3** `SysNacosProperties`（`@ConfigurationProperties("sys.service.nacos")`）
- [ ] **3.4** `NacosServiceRegistry` + `NacosServiceDiscovery`
- [ ] **3.5** `NacosAutoConfiguration`（`@ConditionalOnProperty`，`havingValue = "nacos"`）
- [ ] **3.6** `plugin-info.json`（`loadType: merge`）+ `AutoConfiguration.imports`
- [ ] **3.7** `mvn -pl sfc-ext/sfc-ext-nacos -am compile` 生成 `plugin-lib`
- [ ] **3.8** 跑依赖对比脚本，为「版本冲突」条目补 `<exclusion>`
- [ ] **3.9** 加入 `plugin.extra-resource`，启动；报「存在以下冲突class」则继续补 `<exclusion>`，**循环直到启动成功**
- [ ] **3.10** 切 `sys.service.registry-provider: nacos`，**确认 Nacos 控制台出现 `sfc-core` 实例**

**为什么排第三**：

1. **前置全部就绪**——SPI 已存在、`cluster` provider 已可作对照、Nacos 服务端已起
2. 因此排查面**收敛到依赖冲突这一个变量**
3. 避免了"代码问题 / 配置问题 / 依赖问题"三者混淆

**⚠️ 三个必须知道的操作要点**（详见 [调试手册 §7](microservice-nacos-dependency-debugging.md)）：

| 要点 | 后果 |
|---|---|
| 插件一旦进 `plugin.extra-resource`，依赖校验**无条件执行**，与 `registry-provider` 取值无关 | 会困惑于"我明明没启用 Nacos 为什么启动失败" |
| 改了 `<exclusions>` **必须重新 `mvn compile`** | IDE 的 Build 不触发 `copy-dependencies`，会以为改动无效 |
| **"启动成功"不是通过标准** | 粗粒度版本冲突只产生 warn 并静默跳过 jar，问题会在运行期才暴露 |

**规模**：代码量不大（中），但**调试迭代可能占整个项目最多的时间**。

**验证标准**：见 [调试手册 §8 完成标准](microservice-nacos-dependency-debugging.md)。

---

## 阶段 4：demo 微服务双向注册 🏁 档位 1 完成

- [ ] **4.1** 新建**独立 git 仓库**的 demo 微服务工程（如 `C:\Users\xiaotao\code\sfc-demo-service`），**仅依赖 `nacos-client` + `spring-boot-starter-web`，不依赖咸鱼云任何模块**
- [ ] **4.2** 配置服务名、分组、Nacos 地址，启动
- [ ] **4.3** 验证 Nacos 控制台出现**两个**实例
- [ ] **4.4** 验证 `GET /api/admin/sys/service/list` 返回两个服务
- [ ] **4.5** 停掉 demo 服务，等待心跳超时后确认实例从列表消失

**为什么在这里**：这是档位 1 的收口，也是**第一个自然停下来喘息的检查点**。

- [ ] 🚩 **暂停检查点 1**：此时你已完整走通"服务注册 → 服务发现 → 服务治理基础设施"，并且**验证了原问题的核心答案**——"后续新微服务单独开发可以吗"→ 可以，demo 服务一行咸鱼云代码都没引。

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
| 🚩 **1** | 服务注册发现全链路、SPI 抽象设计、类加载器与依赖冲突、**新微服务完全独立开发** | 阶段 4 |
| 🚩 **2** | 跨进程调用存量能力、内部 API 契约设计、SDK 代理模式、**流式语义的代价** | 阶段 6 |
| 🚩 **3** | 网关模式、信任边界与鉴权传递、控制面/数据面分离、前端模块化扩展 | 阶段 9 |

**每个检查点都是可交付、可验证、可长期停下的状态。** 不必追求一次走完。

---

## 五、三条最容易走错的地方

| # | 错误 | 后果 | 正确做法 |
|---|---|---|---|
| 1 | 把 Nacos 插件放第一步 | 同时面对"代码 / 配置 / 依赖"三个未知，排查量乘算 | 先在阶段 2 建立 `cluster` 对照组 |
| 2 | 依赖冲突调试期间把插件留在 `extra-resource` | 误以为"没启用 Nacos 就不会受影响" | 调通前不加入；且记得改 `<exclusions>` 后重新 `mvn compile` |
| 3 | 代理层跳过 UID / PATH 校验 | **越权访问他人网盘数据** | 信任边界收敛到内部令牌（阶段 8.5） |

---

## 六、规模感知

| 阶段 | 代码量 | 调试/踩坑 | 说明 |
|---|---|---|---|
| 0 | — | 小 | 环境操作 |
| 1 | 小 | **无** | 纯新增 |
| 2 | 中 | 小 | 复用 `ClusterService` |
| 3 | 中 | ⚠️ **可能最大** | 依赖冲突迭代，**预留充足时间** |
| 4 | 小 | 小 | 独立小工程 |
| 5 | 中 | 中 | 安全设施 |
| 6 | **大** | **大** | 流式语义是最大难点 |
| 7 | 中 | 中 | 前后端各改少量 |
| 8 | 中 | 中 | 安全敏感，需仔细验证 |
| 9 | 中 | 中 | 依赖部署形态 |
| 10 | 小 | 小 | — |

**时间分布的非直觉之处**：阶段 3 代码量只有"中"，但**调试时间可能占全程最多**——因为依赖冲突是试错式的。而阶段 6 代码量与难度都是"大"，是真正需要设计能力的地方。
