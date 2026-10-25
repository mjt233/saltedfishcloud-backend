# microservices —— 微服务工程目录

> 本目录用于在**咸鱼云后端仓库内**统一维护各个微服务工程。
> 与仓库内其它目录的本质区别：**这里的工程不继承 root `pom.xml`**——父 POM 是本目录的 [`pom.xml`](pom.xml)，而它的父 POM 才是 `spring-boot-starter-parent`（`<relativePath/>` 留空）。因此**不会被拖入咸鱼云那套约 208 个 jar 的技术栈**。
> 模块依赖方向（也是构建顺序）：`sfc-common`（**已于 Phase 1 上提到仓库根目录** `../sfc-common`，宿主与微服务共用）→ `sfc-remote-client`（回调 core 的 OpenFeign SDK）、`sfc-gateway`、`sfc-demo-service`。
> ⚠️ **现状校正（2026-10-11）**：本目录的工程**当前已在 root `pom.xml` 的 `<modules>` 中**（`microservices` 作为聚合器被引入），也就是说根目录执行 `mvn package` 会一并构建它们。这与本节早先"不进 root `<modules>`"的约定不同——属于**构建便利与"独立开发"之间的取舍现状**，本文档如实记录；"独立开发"的硬标准仍由下面两条保证：**不继承 root pom**、**零宿主依赖**（两者都可逐条验证）。
> 若要恢复"完全不进 reactor"，只需从 root `<modules>` 移除 `microservices` 一项，各工程的独立构建命令（见 §三）无需改动。

---

## 一、为什么要有这个目录

它回答的是微服务改造最初的那个问题——**"后续的其他新的微服务项目单独开发可以吗"**。

所以这里的工程必须能被**独立地**构建、运行、部署：

| 约束 | 原因 |
|---|---|
| **不继承 root pom** | 咸鱼云 root pom 在 `<dependencies>`（而非 `<dependencyManagement>`）里声明了 JPA / Security / Redis / Redisson 等整套技术栈，任何继承它的模块都会被无条件拖入约 208 个 jar——"独立开发"当场就不成立了 |
| **零宿主依赖** | 不依赖任何**咸鱼云宿主侧**模块（`com.xiaotao.saltedfishcloud:*`）。允许的仓库内依赖只有两个**共享库**：`com.sfc.cloud:sfc-common`（轻量：常量/信封/异常/工具/分页，**已于 Phase 1 上提到仓库根目录**）与 `com.sfc.cloud:sfc-common-data`（JPA 基座，根目录）。<br>⚠️ **核验方式必须改**：不能再对 `microservices/pom.xml` 单独跑 `dependency:tree` —— `sfc-common` 已不在该 reactor 内，那样会从本地仓库解析到**旧快照**（见 §三 的警告）。请在**仓库根**执行 `mvn dependency:tree -pl :sfc-remote-client` 等，或直接全量构建 |
| ~~不进 root `<modules>`~~ → **现状：已进**（2026-10-11 校正） | 早先的约定是"避免微服务被卷进 reactor 构建"。当前本目录**整个聚合工程**（`microservices` 及其三个模块）都在 root `<modules>` 中，好处是根目录一次 `mvn package` 就能产出全部产物；代价是"改微服务要重建宿主"这一耦合回来了。**若在意后者，从 root `<modules>` 移除 `microservices` 一项即可**——本目录的独立构建命令（§三）与之无关 |

> 本目录内的工程应作为**独立的 Maven 工程**打开（IDEA 中单独打开 `microservices/` 目录）。
> ⚠️ **2026-10-05 实测**：若 IDEA 没有把它导入为独立工程，编辑器里会对 `org.springframework.boot.*` 甚至 `String`
> 报 "Cannot resolve symbol"——**这是 IDE 索引问题，不是代码问题**（该工程不属于咸鱼云的 IDEA 模块）。
> 以 Maven 构建与实跑结果为准：
>
> ```powershell
> mvn -f microservices/pom.xml clean package -DskipTests   # BUILD SUCCESS（4 个模块）
> ```

---

## 二、工程清单

| 工程 | 作用 | 状态 |
|---|---|---|
| [`sfc-common`](../sfc-common/) | **共享库**（不是可运行进程，**位于仓库根目录**）：跨进程实体模型 `UserPrincipal`、用户凭证解析与本地验签 `SecureUtils`、网关与微服务的信任头常量 `SfcTrustHeaders`、远程调用契约模型，以及 Phase 1 从宿主迁出的响应信封 `SfcJsonResult`、异常族、常量、字符串/JSON 工具与分页模型 `SfcPageInfo`/`SfcPageableRequest`。被宿主（`sfc-api`）与下面三个工程共同引用。**零 JPA 依赖**（网关无 JPA） | 已接入（**Phase 1 上提**） |
| [`sfc-common-data`](../sfc-common-data/) | **共享库**（不是可运行进程，**位于仓库根目录**）：在 `sfc-common` 之上叠加 JPA/Spring Data 的数据访问基座 `SfcBaseModel`→`SfcAuditModel`、`SfcBaseRepo`、`SfcCrudService`/`SfcCrudServiceImpl`、雪花主键、`SfcDBUtils`、`SfcClassUtils`、`@SfcUID`。身份与事务经 SPI（`CurrentUserProvider` / `SfcTransactionManagerProvider`）注入 | 已接入（**Phase 1 新增**） |
| [`sfc-remote-client`](sfc-remote-client/) | **远程客户端 SDK**（库，不是可运行进程）：OpenFeign 声明式接口，让微服务直接调用 core 的用户网盘文件操作（列出/删除/保存/复制/移动/重命名），凭证按「信任模式」自动透传。对应[路线图阶段 6.2](../../docs/plan/microservice/microservice-roadmap.md) | 已接入 |
| [`sfc-demo-service`](sfc-demo-service/) | **微服务示例/探针**：验证"独立微服务 → Nacos 注册 → 咸鱼云能发现"，并提供 `whoami` / `echo` / `echo-body` / `stream` / `ws` 等验收探针（身份来自网关注入的 `X-Sfc-Uid`） | 已接入 |
| [`sfc-ai-service`](sfc-ai-service/) | **AI 助手服务**（端口 `8091`）：原宿主插件 `sfc-ext-ai`，Phase 2 迁为**独立微服务**。提供 WebSocket 流式 AI 对话（`/api/ai/wschat`）与 LLM 提供商/模型 CRUD（`/api/ai/provider/*`、`/api/ai/model/*`）。**独立 SQLite 数据库 `ai.db`**（与宿主 `xyy.db` 分离）；身份取自网关注入的 `X-Sfc-Uid`；网盘操作经 `sfc-remote-client` 回调 core。**已不再是插件**：不再被 core 加载、不再有 `plugin-lib`。前端影响见其[适配清单](sfc-ai-service/docs/frontend-adaptation-checklist.md) | **已迁入**（Phase 2） |
| [`sfc-download-service`](sfc-download-service/) | **离线下载服务**（端口 `8092`）：原宿主插件 `sfc-ext-download` 迁为**独立微服务**。提供下载任务创建/列表/中断与代理节点查询，下载执行经 `sfc-task-worker`（任务类型 `offline-download`，角色 `SERVICE`），网盘落盘经 `sfc-remote-client` 回调 core。**独立 SQLite 数据库 `download.db`**（历史数据不迁移）；前端资源经 core 的 `plugin/autoLoad.js` 合并加载；服务经网关**默认动态路由** `/api/service/sfc-download-service/task/download/**` 访问（前端 URL 手动调整）。详见其 [README](sfc-download-service/README.md) 与[前端适配清单](sfc-download-service/docs/frontend-adaptation-checklist.md) | **已迁入** |
| [`sfc-gateway`](sfc-gateway/) | **独立网关**（Spring Cloud Gateway Server WebFlux）：整站**唯一入口**，承担入口透传（SPA / `/api/**` / WebSocket / OIDC）与微服务代理（`/api/ext/**`）。**不是微服务**——它不被任何服务反向调用 | **已接入**（[设计](../../docs/plan/microservice/microservice-gateway-design.md) · [预研实测报告](../../docs/plan/microservice/microservice-gateway-spike-report.md)） |

> **网关与微服务的区别**：微服务是"被发现的业务进程"，网关是"入口"。两者都放在本目录，是因为它们**共享同一条构建与版本约定**：独立 POM 边界（不继承 root pom）、零宿主依赖，且使用同一套 Spring Boot / Spring Cloud / SCA 版本矩阵（由本目录的 `pom.xml` 单点声明）。
>
> ⚠️ **框架期的信任模式（2026-10-11 起：网关本地验签）**：网关是**唯一身份来源**——它用**环境变量 `SFC_JWT_SECRET`**（＝ core 的 `sys.safe.token`）**本地验签**用户凭证，把 uid 注入 `X-Sfc-Uid`，并把**凭证原值透传**给下游；微服务直接信任该头、不做校验，如需"登出即失效"级别判定则用透传的凭证自行完整校验。头名常量统一取自 `sfc-common` 的 `com.sfc.cloud.trust.SfcTrustHeaders`（网关注入、微服务读取，两侧都不写字面量）。因此：**微服务只允许内网 / 本机暴露**、**不得打印凭证**，上公网前必须先完成服务间鉴权（见[路线图阶段 5](../docs/plan/microservice/microservice-roadmap.md) 与[技术债务清单](../docs/plan/todo.md)）。
>
> 🔒 **内部接口的一层兜底（2026-10-15，同日改为 SDK + 自签 JWT）**：core 的 `/api/internal/**`（当前即 `sfc-remote-client` 访问的网盘文件 V2 接口）**只允许微服务间调用**——各服务用同一份共享密钥 **`SFC_INTERNAL_KEY`**（配置项 `sfc.internal.key`）**为每次调用自签 HS256 JWT**，放在请求头 **`X-Sfc-Internal-Token`** 里（密钥本身不上线传输）；core 侧校验「签名 + 有效期（含时钟偏移）+ 必需声明」，未配置密钥时 fail-closed 全部拒绝；网关（`sfc-gateway`）对该前缀**直接拒绝**，服务间调用走 `lb://sfc-core` 直连、不经网关。签发/校验与两端适配全部由独立 SDK **[`sfc-auth`](../sfc-auth/README.md)**（仓库根目录）实现。注意：仍是**共享密钥**模型——不区分服务身份、**不做防重放**（调小 `token-ttl` 是唯一缓解手段）、轮换需全部重启，属于框架期兜底，**不能替代**阶段 5 的服务间鉴权。
>
> ⚠️ **构建前置**：`sfc-auth` 与 `sfc-local-registry-client` 位于**仓库根 reactor**、不继承根 pom，因此单独构建本目录前需先 `mvn -pl sfc-auth,sfc-local-registry-client install`（或改从仓库根用 `-pl microservices/...` 构建）；否则本目录的 reactor 找不到这两个 SDK。

---

## 三、构建与运行

```powershell
# 前置：内部接口鉴权 SDK 与 local 注册中心 SDK 在【仓库根 reactor】且不继承根 pom，先装进本地仓库
mvn -pl sfc-auth,sfc-local-registry-client install -DskipTests

# ✅ 推荐（也是当前唯一可靠的方式）：从【仓库根 reactor】构建。
#    共享库 sfc-common / sfc-common-data 现在是根目录下的独立模块，只有根 reactor 才包含它们；
#    根 <modules> 已包含 microservices，因此这一条会按依赖顺序把宿主与微服务一起构建。
mvn clean package -DskipTests

# 只构建微服务侧（仍是根 reactor；-pl 里写微服务模块的坐标）
mvn package -DskipTests -pl :sfc-remote-client -am
mvn package -DskipTests -pl :sfc-gateway -am
mvn package -DskipTests -pl :sfc-demo-service -am

# ⚠️⚠️ 不要再对 `microservices/pom.xml` 单独构建（`mvn -f microservices/pom.xml …`）：
#    Phase 1 把 sfc-common 上提到了仓库根目录，它**已不在本目录的 reactor 内**。
#    那样构建时 Maven 会从**本地仓库**解析 `com.sfc.cloud:sfc-common`，
#    而本地仓库里可能是一份**旧快照**（沙箱/CI 下还可能根本不存在）——
#    表现为"构建成功但跑的是旧共享库"，属于静默错误，比直接失败更危险。
#    同理 `-am` 也不会再把 sfc-common 拉进 reactor。
# ⚠️ 也不要用 `-rf` 从中途恢复根 reactor 构建：跳过的模块会退化为从本地仓库解析共享库。

# 运行
#   网关必须先注入验签密钥：用 core 的会话密钥（DB 配置项 sys.safe.token）覆盖 SFC_JWT_SECRET，
#   否则网关【启动即失败】（fail-fast）。取法与管理端配置页见网关 README §二。
$env:SFC_JWT_SECRET = '<core 的 sys.safe.token 值>'
java -jar microservices/sfc-demo-service/target/sfc-demo-service-1.0.0.jar
java -Xmx512m -jar microservices/sfc-gateway/target/sfc-gateway-1.0.0.jar

# 停止：Ctrl+C（优雅关闭会触发 Nacos 注销）
```

> ⚠️ **入口是网关**：浏览器只访问 `http://127.0.0.1:8081/`（网关），再由它透传到 `sfc-core:8087` 与微服务。
> 运行网关前请确认 Nacos 已启动、`sfc-core` 与目标微服务已注册（`group=SFC`、`namespace=public`），且已注入 `SFC_JWT_SECRET`。
> 网关自身配置见其 [`application.yml`](sfc-gateway/src/main/resources/application.yml) 与 [README](sfc-gateway/README.md)。

> ⚠️ **共享库版本必须与代码同源**：`sfc-common` / `sfc-common-data` 的版本由根 `pom.xml` 的 `sfc.common.version` 单点声明（当前 `1.0.0`），`sfc-ext/pom.xml` 因不继承根 pom 而自带同名属性。改动共享库后请**从根 reactor 全量构建**，不要依赖 `install` 到本地仓库的旧副本。

---

## 四、验证方式（阶段 4 的 4.3 / 4.4 / 4.5）

```powershell
# 4.3 两个实例都在 Nacos 里（服务端 API，Nacos 3.x 路径）
curl "http://127.0.0.1:8848/nacos/v3/client/ns/instance/list?serviceName=sfc-core&groupName=SFC&namespaceId=public"
curl "http://127.0.0.1:8848/nacos/v3/client/ns/instance/list?serviceName=sfc-demo-service&groupName=SFC&namespaceId=public"

# 4.4 咸鱼云管理端能看到两个服务（需管理员登录后的 token）
#     GET /api/admin/sys/service/list

# 4.5 停掉 demo 后，实例会在【数秒内】消失——2026-10-05 实测 ≤5s。
#     临时实例走客户端 gRPC 长连接，"连接断开"本身就是下线信号，
#     不必等 15s 心跳超时 / 30s ipDeleteTimeout
```

**排查顺序很重要**：**先查 Nacos 服务端、再查咸鱼云管理端**。
前者为空 = "没注册上"；前者有、后者为空 = "查不到"（十有八九是 `group` / `namespace` 两侧不一致）。

---

## 五、两条容易踩的约定

1. **`group` 与 `namespace` 必须与咸鱼云侧收敛为同一值**
   - 咸鱼云侧：`spring.cloud.nacos.discovery.group` / `.namespace`
   - 微服务侧：`spring.cloud.nacos.discovery.group` / `.namespace`
   - 本档位取值：`group: SFC`、命名空间 **public（其 ID 是空串，不要写 `public`）**
   - 不一致的现象：**注册成功但互相看不见**

2. **服务端未开启鉴权时，不要填账号密码**
   服务器未启用鉴权时留空即可；Nacos 3.x 的控制台在 **8080**，服务端 API 仍在 **8848**，客户端 gRPC 端口是 **9848**（必须暴露）。
