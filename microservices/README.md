# microservices —— 微服务工程目录

> 本目录用于在**咸鱼云后端仓库内**统一维护各个微服务工程。
> 与仓库内其它目录的本质区别：**这里的工程不继承 root `pom.xml`**——父 POM 是本目录的 [`pom.xml`](pom.xml)，而它的父 POM 才是 `spring-boot-starter-parent`（`<relativePath/>` 留空）。因此**不会被拖入咸鱼云那套约 208 个 jar 的技术栈**。
> 模块依赖方向（也是构建顺序）：`sfc-common`（共享模型 / 工具 / 信任头常量 / 远程调用契约模型）→ `sfc-remote-client`（回调 core 的 OpenFeign SDK）、`sfc-gateway`、`sfc-demo-service`。
> ⚠️ **现状校正（2026-10-11）**：本目录的工程**当前已在 root `pom.xml` 的 `<modules>` 中**（`microservices` 作为聚合器被引入），也就是说根目录执行 `mvn package` 会一并构建它们。这与本节早先"不进 root `<modules>`"的约定不同——属于**构建便利与"独立开发"之间的取舍现状**，本文档如实记录；"独立开发"的硬标准仍由下面两条保证：**不继承 root pom**、**零宿主依赖**（两者都可逐条验证）。
> 若要恢复"完全不进 reactor"，只需从 root `<modules>` 移除 `microservices` 一项，各工程的独立构建命令（见 §三）无需改动。

---

## 一、为什么要有这个目录

它回答的是微服务改造最初的那个问题——**"后续的其他新的微服务项目单独开发可以吗"**。

所以这里的工程必须能被**独立地**构建、运行、部署：

| 约束 | 原因 |
|---|---|
| **不继承 root pom** | 咸鱼云 root pom 在 `<dependencies>`（而非 `<dependencyManagement>`）里声明了 JPA / Security / Redis / Redisson 等整套技术栈，任何继承它的模块都会被无条件拖入约 208 个 jar——"独立开发"当场就不成立了 |
| **零宿主依赖** | 不依赖任何**咸鱼云宿主侧**模块（`com.xiaotao.saltedfishcloud:*` 及宿主契约模块 `sfc-cloud-api`）。**唯一允许的仓库内依赖是本目录下自建**的共享模块 `com.sfc.cloud:sfc-common`。核验方式：`mvn -f microservices/pom.xml dependency:tree`，除 `com.sfc.cloud:sfc-common` 外不应命中任何仓库内坐标 |
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
| [`sfc-common`](sfc-common/) | **共享库**（不是可运行进程）：跨进程实体模型 `UserPrincipal`、用户凭证解析与本地验签 `SecureUtils`、网关与微服务的信任头常量 `SfcTrustHeaders`，以及远程调用契约模型（`com.sfc.cloud.remote.model`：响应信封、文件模型、请求参数）。被下面三个工程以 jar 形式引用，**版本/依赖管理全部继承本目录的 `pom.xml`** | 已接入 |
| [`sfc-remote-client`](sfc-remote-client/) | **远程客户端 SDK**（库，不是可运行进程）：OpenFeign 声明式接口，让微服务直接调用 core 的用户网盘文件操作（列出/删除/保存/复制/移动/重命名），凭证按「信任模式」自动透传。对应[路线图阶段 6.2](../../docs/plan/microservice/microservice-roadmap.md) | 已接入 |
| [`sfc-demo-service`](sfc-demo-service/) | **微服务示例/探针**：验证"独立微服务 → Nacos 注册 → 咸鱼云能发现"，并提供 `whoami` / `echo` / `echo-body` / `stream` / `ws` 等验收探针（身份来自网关注入的 `X-Sfc-Uid`） | 已接入 |
| [`sfc-gateway`](sfc-gateway/) | **独立网关**（Spring Cloud Gateway Server WebFlux）：整站**唯一入口**，承担入口透传（SPA / `/api/**` / WebSocket / OIDC）与微服务代理（`/api/ext/**`）。**不是微服务**——它不被任何服务反向调用 | **已接入**（[设计](../../docs/plan/microservice/microservice-gateway-design.md) · [预研实测报告](../../docs/plan/microservice/microservice-gateway-spike-report.md)） |

> **网关与微服务的区别**：微服务是"被发现的业务进程"，网关是"入口"。两者都放在本目录，是因为它们**共享同一条构建与版本约定**：独立 POM 边界（不继承 root pom）、零宿主依赖，且使用同一套 Spring Boot / Spring Cloud / SCA 版本矩阵（由本目录的 `pom.xml` 单点声明）。
>
> ⚠️ **框架期的信任模式（2026-10-11 起：网关本地验签）**：网关是**唯一身份来源**——它用**环境变量 `SFC_JWT_SECRET`**（＝ core 的 `sys.safe.token`）**本地验签**用户凭证，把 uid 注入 `X-Sfc-Uid`，并把**凭证原值透传**给下游；微服务直接信任该头、不做校验，如需"登出即失效"级别判定则用透传的凭证自行完整校验。头名常量统一取自 `sfc-common` 的 `com.sfc.cloud.trust.SfcTrustHeaders`（网关注入、微服务读取，两侧都不写字面量）。因此：**微服务只允许内网 / 本机暴露**、**不得打印凭证**，上公网前必须先完成服务间鉴权（见[路线图阶段 5](../docs/plan/microservice/microservice-roadmap.md) 与[技术债务清单](../docs/plan/todo.md)）。
>
> 🔒 **内部接口的一层兜底（2026-10-15，同日改为 SDK + 自签 JWT）**：core 的 `/api/internal/**`（当前即 `sfc-remote-client` 访问的网盘文件 V2 接口）**只允许微服务间调用**——各服务用同一份共享密钥 **`SFC_INTERNAL_KEY`**（配置项 `sfc.internal.key`）**为每次调用自签 HS256 JWT**，放在请求头 **`X-Sfc-Internal-Token`** 里（密钥本身不上线传输）；core 侧校验「签名 + 有效期（含时钟偏移）+ 必需声明」，未配置密钥时 fail-closed 全部拒绝；网关（`sfc-gateway`）对该前缀**直接拒绝**，服务间调用走 `lb://sfc-core` 直连、不经网关。签发/校验与两端适配全部由独立 SDK **[`sfc-auth`](../sfc-auth/README.md)**（仓库根目录）实现。注意：仍是**共享密钥**模型——不区分服务身份、**不做防重放**（调小 `token-ttl` 是唯一缓解手段）、轮换需全部重启，属于框架期兜底，**不能替代**阶段 5 的服务间鉴权。
>
> ⚠️ **构建前置**：`sfc-auth` 位于**仓库根 reactor**、不继承根 pom，因此单独构建本目录前需先 `mvn -pl sfc-auth install`（或改从仓库根用 `-pl microservices/...` 构建）；否则本目录的 reactor 找不到 `com.xiaotao:sfc-auth`。

---

## 三、构建与运行

```powershell
# 前置：内部接口鉴权 SDK 在【仓库根 reactor】且不继承根 pom，先装进本地仓库
mvn -pl sfc-auth install -DskipTests

# 构建（在仓库根目录执行即可）——推荐：聚合器一次构建全部模块（含共享库 sfc-common）
mvn -f microservices/pom.xml clean package -DskipTests

# 只构建其中一个工程：-am 会把它的上游 sfc-common 一并放进 reactor
mvn -f microservices/pom.xml clean package -DskipTests -pl :sfc-remote-client -am
mvn -f microservices/pom.xml clean package -DskipTests -pl :sfc-gateway -am
mvn -f microservices/pom.xml clean package -DskipTests -pl :sfc-demo-service -am

# ⚠️ 不要再对子模块直接 `-f microservices/sfc-gateway/pom.xml`：
#    两个工程现在都依赖共享库 sfc-common，单项目构建会要求它已在本地仓库（需先执行 install）。

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

> ⚠️ 用 `-pl` 时，模块坐标是相对**本目录的聚合器**（`mvn -f microservices/pom.xml … -pl :sfc-gateway -am`）；**不要**在仓库根目录写 `-pl microservices/sfc-gateway`——从 root reactor 看它的坐标是 `microservices/sfc-gateway`，且会把咸鱼云宿主的整套依赖一起拉进构建。

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
   - 咸鱼云侧：`sys.service.registry-group`（发现侧**只能用它自己的配置查询**）
   - 微服务侧：`spring.cloud.nacos.discovery.group` / `.namespace`（注册侧）
   - 本档位取值：`group: SFC`、命名空间 **public（其 ID 是空串，不要写 `public`）**
   - 不一致的现象：**注册成功但互相看不见**

2. **服务端未开启鉴权时，不要填账号密码**
   服务器未启用鉴权时留空即可；Nacos 3.x 的控制台在 **8080**，服务端 API 仍在 **8848**，客户端 gRPC 端口是 **9848**（必须暴露）。
