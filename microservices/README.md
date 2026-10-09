# microservices —— 微服务工程目录

> 本目录用于在**咸鱼云后端仓库内**统一维护各个微服务工程。
> 与仓库内其它目录的本质区别：**这里的工程不继承 root `pom.xml`，也不在 root 的 `<modules>` 中**。

---

## 一、为什么要有这个目录

它回答的是微服务改造最初的那个问题——**"后续的其他新的微服务项目单独开发可以吗"**。

所以这里的工程必须能被**独立地**构建、运行、部署：

| 约束 | 原因 |
|---|---|
| **不继承 root pom** | 咸鱼云 root pom 在 `<dependencies>`（而非 `<dependencyManagement>`）里声明了 JPA / Security / Redis / Redisson 等整套技术栈，任何继承它的模块都会被无条件拖入约 208 个 jar——"独立开发"当场就不成立了 |
| **不进 root `<modules>`** | 避免微服务被卷进咸鱼云的 reactor 构建，"改微服务就要重建宿主"是不该有的耦合 |
| **不依赖咸鱼云任何模块** | 零 `com.xiaotao.saltedfishcloud:*` / `com.sfc.cloud:*` 依赖。这是"独立"的硬标准 |

> 本目录内的工程应作为**独立的 Maven 工程**打开（IDEA 中单独打开 `microservices/` 目录）。
> ⚠️ **2026-10-05 实测**：若 IDEA 没有把它导入为独立工程，编辑器里会对 `org.springframework.boot.*` 甚至 `String`
> 报 "Cannot resolve symbol"——**这是 IDE 索引问题，不是代码问题**（该工程不属于咸鱼云的 IDEA 模块）。
> 以 Maven 构建与实跑结果为准：
>
> ```powershell
> mvn -f microservices/sfc-demo-service/pom.xml clean package -DskipTests   # BUILD SUCCESS
> ```

---

## 二、工程清单

| 工程 | 作用 | 状态 |
|---|---|---|
| [`sfc-demo-service`](sfc-demo-service/) | **微服务示例/探针**：验证"独立微服务 → Nacos 注册 → 咸鱼云能发现"，并提供 `whoami` / echo / stream 等验收探针（身份来自网关注入的 `X-Sfc-Uid`） | 已接入 |
| `sfc-gateway` | **独立网关**（Spring Cloud Gateway Server WebFlux）：整站**唯一入口**，承担入口透传（SPA / `/api/**` / WebSocket）与微服务代理（`/api/ext/**`）。**不是微服务**——它不被任何服务反向调用 | **待建**（[设计已定稿](../docs/plan/microservice/microservice-gateway-design.md)） |

> **网关与微服务的区别**：微服务是"被发现的业务进程"，网关是"入口"。两者都放在本目录，是因为它们**共享同一条构建与版本约定**：独立 POM 边界、不继承 root pom、不进 root `<modules>`、零咸鱼云依赖，且使用同一套 Spring Boot / Spring Cloud 版本矩阵。
>
> ⚠️ **框架期的信任模式**：网关是**唯一身份来源**（从 core 的 `resolve` 取得 uid 后注入 `X-Sfc-Uid`），微服务直接信任、不做任何校验 ⇒ **微服务只允许内网 / 本机暴露**，上公网前必须先完成服务间鉴权（见[路线图阶段 5](../docs/plan/microservice/microservice-roadmap.md)）。

---

## 三、构建与运行

```powershell
# 构建（在仓库根目录执行即可）
mvn -f microservices/sfc-demo-service/pom.xml clean package -DskipTests

# 运行
java -jar microservices/sfc-demo-service/target/sfc-demo-service-1.0.0.jar

# 停止：Ctrl+C（优雅关闭会触发 Nacos 注销）
```

> ⚠️ 单独构建本目录的工程时，**不要**加 `-pl`：它不属于咸鱼云的 reactor，`-f` 指定 pom 即可。

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
