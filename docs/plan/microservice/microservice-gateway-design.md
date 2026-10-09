# 微服务改造 · 网关独立化设计（`sfc-gateway`）

> **状态**：设计定稿，**尚未产生代码改动**（`microservices/sfc-gateway` 工程、core 侧 resolve 端点均为待建）
> **性质**：本文件是**代理层**的实现归属设计（拓扑、决策、线协议、filter 清单、验收红线）。前端契约（清单字段、路径形态、错误语义）以[档位 3 设计](microservice-phase3-frontend-integration.md)为准
> **范围**：把代理职责从 `sfc-core` 内部移出，改为**独立进程的 Spring Cloud Gateway**，并由它担任整站**唯一入口**
> **依赖**：阶段 1（SPI 契约，已落地）· 阶段 4（Nacos 双向注册，已落地）
> **安全口径**：**框架期采用信任模式**——服务间鉴权整体后置（阶段 5），不签发令牌、不验签、不做 `aud` / `scope`
> **配套**：[路线图阶段 8](microservice-roadmap.md) · [档位 3 设计](microservice-phase3-frontend-integration.md) · [方案评估报告](review/microservice-plan-review.md) · [档位 1 设计](microservice-phase1-design.md)
> **创建日期**：2026-10-09

---

## 阅读指引

| 你想了解 | 看第几节 |
|---|---|
| 目标边界、不做什么 | §1 |
| 为什么独立、代价是什么、安全债务是什么 | §2 |
| 整体长什么样 | §3 |
| 八个决策为什么这么定 | §4 |
| 网关侧要写哪些 filter | 决策 5 |
| resolve 线协议 | 决策 3.1 |
| 要建哪些文件、按什么顺序 | §5 |
| 怎么判断没做坏 | §6 |
| 有什么坑 | §7 |

---

## 1. 目标与范围

### 目标

1. **`sfc-core` 不再承担任何 HTTP 转发职责**：它只做"控制面"——会话权威、策略判定（路径合法性 / 服务白名单 / 自我转发阻断）
2. 新建独立进程 `microservices/sfc-gateway`（Spring Cloud Gateway **Server WebFlux**），作为**整站唯一入口**：浏览器只与它同源对话
3. 微服务流量的**传输层**（路由、负载均衡、重试、流式管道、头部治理）交给官方库，不再自研
4. **框架期的身份传递采用信任模式**：网关持有唯一身份来源（core 的 resolve 结果），注入 `X-Sfc-Uid`；微服务直接信任

### 明确不做

| 不做 | 原因 |
|---|---|
| 拆分 `sfc-core` 的业务 | 非目标。core 仍是单体业务应用，只是不再兼职网关 |
| **服务间调用鉴权**（令牌 / JWKS / `aud` / `scope`） | **整体后置**（阶段 5）。框架期采用信任模式；安全机制另行规划，问题清单见[路线图阶段 5](microservice-roadmap.md) |
| 限流 / 熔断 / 链路追踪 | 治理能力。SCG 的官方 filter 使它们**随时可加**，但本阶段引入会扩大验收面 |
| 微服务之间的相互调用 | 属档位 2（阶段 6） |
| 数据面预签名直连 | 属阶段 9，**已暂缓**（信任模式下 URL 载体没有安全语义）。框架期下载走网关流式代理 |
| 阶段 7 的扩展清单与静态资源代理 | 属阶段 7。网关**只预留路由位，不提前放行前缀**（决策 7） |
| 把网关做成 Nacos 里的"服务"来被调用 | 网关是入口，不被任何服务反向调用；它只注册自身以便在管理端可见 |

---

## 2. 为什么独立：三个问题、三条代价、一份债务

用户提出的三个诉求，如实分项回答（**不夸大**）：

| 维度 | 结论 | 依据 |
|---|---|---|
| **更清晰的项目结构** | ✅ **有条件成立** | 清晰来自"**独立进程 ＋ 边界重划**"（core = 控制面策略与判定；网关 = 数据面传输），而不是来自换库。若把 SCG 以依赖形式内嵌进 core，结构一点没变，只是把自研转发器换成官方转发器 |
| **更强的扩展性** | ✅ **成立** | 路由配置化；限流（`RequestRateLimiter`）、熔断（`CircuitBreaker`）、重试、可观测（actuator / metrics）、动态路由都是官方现成件。自研转发器每加一项能力都是一段新代码 |
| **更好的稳定性** | ⚠️ **部分成立（必须说清）** | 官方库覆盖的是 **HTTP 传输机制**（连接管理、流式管道、头部语义、负载均衡、重试时机）。而**真正容易出事的是策略**（身份头剥离、凭证剥离、`X-Forwarded-*` 覆盖、hop-by-hop 剥离、自我转发阻断、路径"只拒绝不规范化"、带请求体不重试）——**一条都不在 SCG 的职责里**，仍要自己写 filter。准确表述是"**传输层不再是自己写的代码**"，不是"转发逻辑变成配置" |

**三条代价**（显式接受）：

| # | 代价 | 说明与对策 |
|---|---|---|
| 1 | **所有流量多一跳** | 含 SPA 静态资源、1GB 下载、大文件上传。8.1 预研必须给出吞吐对照 |
| 2 | **网关成为可用性单点** | 网关挂 = 整站不可用。入口切换（8.6）前保留"浏览器直连 core:8087"的回退路径 |
| 3 | **新增一条信任边 ＋ 版本矩阵分叉** | 网关 → core 的 resolve 调用（内网）；网关用 Boot 4.0.8，core 用 Boot 4.1.0，两者只通过 HTTP 协议耦合、**不共享任何 jar** |

**一份安全债务（框架期必须显式记账）**：

| # | 债务 | 到期条件 |
|---|---|---|
| 1 | 微服务只信任明文头 `X-Sfc-Uid`：**任何能直连微服务端口的人都能伪造任意用户** | 安全阶段（阶段 5）换成**可验证**的载体 |
| 2 | microservice 侧**没有任何权限校验**；core 的 `/api/internal/**` 同样无服务身份校验 | 同上 |
| 3 | 预签名直连（阶段 9）**暂缓**——没有可验证载体时，URL 里的"凭证"没有意义 | 随安全阶段一起做 |

> **硬约束**：**微服务与 core 的内部接口只允许内网 / 本机可达，网关是唯一对外入口；上公网前必须先完成阶段 5。**

> **与[方案评估报告 §2](review/microservice-plan-review.md) 的关系**：该报告当时判定"当下不需要独立网关"，触发条件是四条（多副本需前置 LB、限流熔断、多团队、TLS 终结）。本次触发它的**不是**那四条，而是"官方库稳定性 ＋ 职责分离"。报告 §2 已按本文件更新，其余论证仍成立。

---

## 3. 拓扑

```
                              浏览器（同源：只跟网关说话）
                                          │
                        ┌─────────────────▼──────────────────┐
                        │        sfc-gateway :8080           │
                        │  Spring Cloud Gateway 5.0 (WebFlux)│
                        │  · 无会话逻辑、无业务策略、不签发凭证  │
                        └──┬───────────────┬──────────────┬──┘
   ①/②/③ 透传（含 WebSocket）│      ④ /api/ext/{svc}/**       │ ⑤ resolve
                        │               │              │
                        ▼               │              ▼
        ┌───────────────────────────────┼──────────────────────────────┐
        │                     sfc-core :8087                             │
        │  会话权威（HS256 JWT ＋ Redis 白名单）                            │
        │  策略：路径规则 / 服务名白名单 / 自我转发阻断                       │
        │  既有安全设施：@UID @ValidPath @RolesAllowed（**零改动**）         │
        └───────┬───────────────────────────────────────┬────────────────┘
                │ 注册 / 心跳 / 发现（Nacos，group SFC）    │
                ▼                                        ▼
        ┌──────────────────────────────────────────────────────────────┐
        │  微服务（独立进程）：lb://{serviceName}                          │
        │  信任 X-Sfc-Uid ← 网关注入（框架期）                             │
        └──────────────────────────────────────────────────────────────┘
```

**六条链路**

| # | 链路 | 谁处理 | 鉴权 |
|---|---|---|---|
| ① | `/`、`/index.*`、`/assets/**`、`/static/**`、`/ext/**`、`/oauth*` → core | 网关透明透传 | 由 **core** 自己判（网关不参与） |
| ② | `/api/**`（core 业务 API）→ core | 网关透明透传 | 由 **core** 自己判（网关不参与） |
| ③ | WebSocket：`/api/ws`、`/api/ai/wschat`、`/api/webrtc/ws/*`、`/api/webshell/*` → core | 网关透传 upgrade | 由 core 自己判 |
| ④ | `/api/ext/{serviceName}/**` → 微服务 | 网关先 ⑤ 再转发 | 网关调 ⑤ 拿会话结论；身份以 `X-Sfc-Uid` 注入 |
| ⑤ | `POST /api/gateway/resolve` → core | **core**（会话 ＋ 策略） | 用户会话（**不签发任何凭证**） |
| ⑥ | 阶段 7 的清单与静态资源 | 清单透传到 core；静态直连微服务 | 匿名（静态需路径校验） |

**关键性质**：链路 ①②③ 上，网关是**完全透明**的——不解析身份、不注入身份头、不改写业务语义。因此 core 的 `@UID` / `@ValidPath` / `ProtectBlocker` / OIDC 全部**原样生效、零改动**，"信任边界连续"这一条没有因为引入网关而丢。

---

## 4. 决策记录

### 决策 1：网关形态 → **SCG Server WebFlux**（`spring-cloud-starter-gateway-server-webflux`）

| 变体 | 结论 | 理由 |
|---|---|---|
| **Server WebFlux（采用）** | ✅ | 唯一入口必须转发 WebSocket（链路 ③ 有 4 个 WS 端点），而[官方文档](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/how-it-works.html)中 MVC 变体**没有 WebSocket 转发能力**；WebFlux 是 SCG 主线，过滤器生态最全；流式（Netty）是默认行为，正好命中"禁止 `byte[]` 全量缓冲"的硬约束 |
| Server WebMVC | ❌ | Servlet / WebMvc.fn 模型与项目一致、上手成本低，但**不支持 WebSocket 转发** ⇒ 在本拓扑下直接不可用 |
| Proxy Exchange（`spring-cloud-gateway-proxyexchange-webmvc`） | ❌ | 它是 `ProxyExchange<byte[]>` 的注解式用法，与"必须流式、禁止全量缓冲"的硬约束冲突 |

> **reactive 栈的隔离性**：WebFlux / Reactor 只存在于网关进程内，`sfc-core` 仍是纯 Servlet 单体。这是"独立进程"顺带换来的好处——两套编程模型互不污染。

### 决策 2：工程位置与版本边界 → **`microservices/sfc-gateway`，独立 POM 边界**

| 项 | 取值 | 依据 |
|---|---|---|
| 位置 | `microservices/sfc-gateway` | 与 [microservices/README.md](../../../microservices/README.md) 的约定一致：仓库内统一维护、独立构建独立运行 |
| 父 POM | `spring-boot-starter-parent:4.0.8`，**不继承 root pom**、**不进 root `<modules>`** | 继承 root pom 会被拖入约 208 个 jar（决策见[档位 1 设计 §5 决策 1](microservice-phase1-design.md)） |
| Spring Boot | **4.0.8** | 与 `sfc-demo-service` 同版本 |
| Spring Cloud | **2025.1.0**（Oakwood） | 官方矩阵基线 `spring-boot.version = 4.0.0`；对应 SCG **5.0.0** |
| Spring Cloud Alibaba | **2025.1.0.0** | 提供 `nacos-discovery` starter（`lb://` 的服务实例来源） |
| 咸鱼云依赖 | **零** | 沿用 demo 的验证方式：`mvn dependency:list` 中 `com.xiaotao` / `sfc-*` 命中 0 条 |

> **一处既存偏差（不顺手改）**：`microservices/sfc-demo-service` 目前**已经**出现在 root pom 的 `<modules>` 里，与 [microservices/README.md](../../../microservices/README.md) 声明的约定不符。demo 是**保留的微服务示例**，其归属按现状维持；网关**按 README 的约定执行**（不进 root `<modules>`）。

### 决策 3：身份传递 → **信任模式：网关是唯一身份来源，微服务零校验**

网关**不签发凭证、不持有密钥、不做验签**。它向 core 的 resolve 端点询问"这个请求是谁、要转到哪里"，然后注入一个明文身份头。

#### 决策 3.1：resolve 线协议

```
POST /api/gateway/resolve
  用户会话凭证：Token 头 / token cookie / ?Token=     ← 复用 SecureUtils.getToken 的三个既有入口
  Body: { "rawPath": "/api/ext/sfc-demo-service/demo/whoami",
          "rawQuery": "keep=1", "method": "GET" }

200 → { "uid": 1272361495027515392,
        "serviceName": "sfc-demo-service",
        "targetPath": "/api/demo/whoami" }            ← 拼接 sfc.api.prefix 后的完整下游路径
```

| 失败情形 | HTTP | 业务码 |
|---|---|---|
| `sys.service.gateway.enabled=false` | 404 | 7002 |
| 无有效会话 | 403 | 7005 |
| 路径非法（见决策 4） | 400 | 7000 |
| 服务未注册 / 无健康实例 / **目标为 core 或网关自身** | 404 | 7001 |

**三条实现约束**：

1. **`rawPath` 必须是网关收到的原始未解码 URI**（WebFlux 侧取 `URI.getRawPath()`）。理由是[档位 3 §7.6](microservice-phase3-frontend-integration.md) 那条不变式：**判定时看到的路径与转发时用的路径必须是同一个字符串**。core 把 `targetPath` 一并返回，网关不再自行拼路径。
2. **resolve 只回答"是谁、转到哪"，不返回任何凭证**——框架期不存在凭证。
3. **端点本身要求有效用户会话**：未登录一律 403/7005（用户鉴权仍然保留；被后置的只是**服务间**鉴权）。

#### 决策 3.2：为什么不让网关自己验会话

| 做法 | 否决理由 |
|---|---|
| 网关验 HS256 签名 ＋ 查 Redis 白名单 | ①必须与 core **共享用户 JWT 密钥**，等于把会话签发能力复制给第二个进程；②会话校验栈被复制成两份，任何一侧改动都可能造成"网关放行、core 拒绝"的不一致 |
| 复用 OIDC 的 `/oauth2/introspect` | 把服务鉴权重新挂回 OIDC 设施：`sys.oidc.enabled=false` 时一起失效（阶段 5 已明确否决这条路） |

#### 决策 3.3：`X-Sfc-Uid` 的唯一注入点

| 规则 | 说明 |
|---|---|
| 网关**剥离入站的一切 `X-Sfc-*`**，然后写入自己的 `X-Sfc-Uid` | 这是信任模式的**唯一防线**：不剥离就等于"谁都能当任意用户" |
| 值只能来自 resolve 的返回（即 core 的会话判定） | 网关**不得**从任何请求参数 / 头 / 路径里取 uid |
| 头名常量只有一份定义（`SfcTrustHeaders`，放 `sfc-cloud-api`） | 避免网关与微服务两侧字符串漂移 |
| 微服务侧**不做任何校验**（有意为之） | 债务与到期条件见 §2 |

### 决策 4：职责划分 → **策略上收 core、转发下沉网关**

| 关注点 | 归属 | 说明 |
|---|---|---|
| 会话有效性、`uid` | **core** | resolve 内用既有过滤器链判定 |
| 路径合法性（三条拒绝规则） | **core** | 判定与转发用同一个字符串（决策 3.1 约束 1） |
| 服务名白名单（SSRF 防线） | **core** | 只信注册中心；目标为 core / 网关自身一律 404 |
| **身份凭证的签发与校验** | **框架期不做** | 后置到安全阶段（阶段 5） |
| 路由、负载均衡、超时、重试 | **网关** | SCG `lb://` ＋ 官方 filter |
| 流式管道（请求 / 响应两个方向） | **网关** | Netty 默认行为 |
| 头部治理（身份头、凭证、hop-by-hop、`Host`、`X-Forwarded-*`） | **网关** | 见决策 5 |
| 请求体上限 | **网关** | 控制面不承载大文件 |
| 对外的错误语义（7000~7005） | **core 定语义、网关做映射** | core 返回判定结果，网关把 HTTP 状态码与业务码写回调用方 |

> **分工原则一句话**：**"能不能转"由 core 说，"怎么转"由网关做。** 网关没有任何业务判断，也不持有任何凭证——它被攻破不等于 core 被攻破（但**微服务被攻破就是任意用户**，这是框架期债务 1）。

### 决策 5：网关侧必须自实现的 filter（SCG 不代劳的部分）

| # | 规则 | 实现方式 |
|---|---|---|
| 1 | **剥离入站一切 `X-Sfc-*` 身份头** | 自定义 filter（**信任模式的唯一防线**） |
| 2 | 注入 `X-Sfc-Uid: <resolve 返回的 uid>` | 官方 `SetRequestHeader`（值来自 resolve 结果） |
| 3 | 剥离 `Token` 请求头 | 官方 `RemoveRequestHeader=Token` |
| 4 | 剥离 `?Token=` 查询参数 | 官方 `RemoveRequestParameter=token` |
| 5 | 剥离 `token` cookie（保留其余 cookie） | **自定义 filter**（官方无"删单个 cookie"的 filter） |
| 6 | hop-by-hop 头剥离、`Host` 重写 | 先依赖 SCG / Reactor Netty 的默认行为，**再用实测确认**——不得沿用"以前的 HTTP 客户端是这样"的假设 |
| 7 | `X-Forwarded-For` / `-Host` / `-Proto` **覆盖式**写入 | 自定义 filter（**丢弃入站值**，"追加"与"不信任"在语义上矛盾） |
| 8 | 请求体上限（含 chunked 增量拦截） | 官方 `RequestSize` ＋ 实测确认是否为增量而非缓冲 |
| 9 | 只对 `GET` / `HEAD` 换实例重试 | 官方 `Retry`（`methods=GET,HEAD`）；写请求只打一个实例——请求体是一次性流，带体重试等于让下游收到空请求体而调用方看到"成功" |
| 10 | 连接层陈旧连接自愈 | **不要关闭客户端库的连接层重试**：连接池里被对端按 keep-alive 回收的连接，会让**下一个完全正常的请求**直接失败（OkHttp 的官方 javadoc 就把 `Stale pooled connections` 列为该开关默认覆盖的问题之一）；防重放应由"请求体不可重放"独立保证。**Reactor Netty 下的对应行为必须实测** |
| 11 | 错误语义映射（404 / 503 / 413 / 403 / 400） | 自定义 filter ＋ `ErrorWebExceptionHandler`，业务码与[档位 3 §7.7](microservice-phase3-frontend-integration.md) 一致 |
| 12 | 原始 `rawPath` / `rawQuery` 转交 resolve | 自定义 filter；**网关不得对路径做任何规范化后再判定或转发** |

### 决策 6：网关自身的服务注册 → **注册，但不可被代理**

网关启动时以 `sfc-gateway` 服务名注册到 Nacos（group `SFC`，同 demo），目的只是让管理端 `GET /api/admin/sys/service/list` 能看到完整拓扑。

⚠️ **随之而来的两条规则**：

1. core 的 resolve 必须把"目标服务名 ∈ {`sys.service.service-name`, 网关服务名}"一律判 404——否则 `/api/ext/sfc-gateway/**` 会形成回环；
2. 网关自身不得挂载任何业务端点（除健康检查）。

### 决策 7：阶段 7 / 9 的预留位 → **只留位置，不提前放行**

| 未来链路 | 在网关中的位置 | 本阶段做什么 |
|---|---|---|
| `/api/ext/manifest`（阶段 7） | 透传到 core | 只写路由规则，**不实现** |
| `/api/ext/{svc}/static/**`（阶段 7） | 直连微服务 `/static/**`（**不拼 `sfc.api.prefix`**），匿名 ＋ 路径校验 | 只留路由位。**在静态代理真正实现之前，不得把该前缀加入任何放行规则**——先放行后补校验 = 留出未被消费的攻击面 |
| `/dl/**`（阶段 9） | **⏸ 暂缓**：框架期下载走网关流式代理 | 不写路由 |

### 决策 8：core 侧代理代码的处置 → **core 内不保留任何转发实现**

框架期只新增"策略类"（`GET /api/gateway/resolve` 的实现：路径校验、服务白名单、自我转发阻断、metadata 前缀解析）与配置类，**不新增、也不保留**任何转发器、代理控制器或实例轮询逻辑——转发与负载均衡全部由网关承担。

---

## 5. 实施清单（对应[路线图阶段 8](microservice-roadmap.md) 的 8.1~8.7）

### 8.1 预研实测（spike，不碰生产代码）

新建最小工程，逐条取证并出报告 `microservice-gateway-spike-report.md`：

| # | 实测项 | 为什么必须实测 |
|---|---|---|
| 1 | `/api/**` 完整透传（Cookie / `Set-Cookie` / 状态码 / 重定向保真） | 唯一入口的前提 |
| 2 | **WebSocket 透传**（4 个端点全测） | 最高不确定性：先试 `http://` 目标带 `Upgrade` 是否自动升级；若否，为 WS 路径单配 `ws://` / `lb:ws://` 路由 |
| 3 | `lb://sfc-demo-service` ＋ 两个实例的轮询 | 验证 SCA discovery ＋ loadbalancer 的组合 |
| 4 | **流式**：1GB 响应体在 `-Xmx512m` 下不 OOM；8MB 上传 SHA-256 一致 | 硬约束"禁止全量缓冲"的直接验证 |
| 5 | `Retry` 语义：只对 GET/HEAD；**陈旧连接**在 Reactor Netty 下是否自愈 | 决策 5 第 9 / 10 条 |
| 6 | 请求体上限（含 chunked 增量拦截） | 决策 5 第 8 条 |
| 7 | `%2e%2e` / `%2f` / `%20` 等构造的行为，与 core 的 `StrictHttpFirewall` 协同 | 路径保真 |
| 8 | 大文件经网关的吞吐对照 | 代价 1 的量化 |

> **不合格就停在这里**：回退成本 = 删掉一个独立工程。这正符合路线图"每步只引入一个新变量"的原则。

### 8.2 网关工程骨架

`microservices/sfc-gateway`：`pom.xml`（版本矩阵见决策 2）＋ 启动类 ＋ `application.yml` ＋ 路由（`lb://` ＋ 两条透传路由）＋ 健康检查。

### 8.3 core 侧 resolve 端点

新增 `controller/GatewayResolveController`（`POST /api/gateway/resolve`）＋ `service/gateway/GatewayResolveService`（策略）＋ `GatewayResolveProperties`（`sys.service.gateway.enabled` / `default-api-prefix`）。**不新增任何签发、密钥或验签代码。**

### 8.4 网关侧路由与 filter

按决策 5 的 12 条实现；`/api/ext/**` 路由走"resolve → 剥离入站身份头 → 注入 `X-Sfc-Uid` → `lb://` 转发"。错误语义、业务码、重试规则与[档位 3 §7.7](microservice-phase3-frontend-integration.md) 对齐。

### 8.5 信任边界不变式（不可协商）

`uid` **只来自 resolve**；网关**剥离入站 `X-Sfc-*`**；微服务不读任何名为 `uid` 的参数；篡改 `?uid=999` 的请求必须无效。

### 8.6 入口切换（浏览器只访问网关）

core 侧需要一并确认：`server.forward-headers-strategy`（网关在 core 前面，绝对 URL 与重定向要正确）、OIDC 重定向地址、Cookie 域与端口行为；网关侧需要确认：超时、上传大小不得截断（multipart 上限仍在 core）。

**验证**：SPA 全功能回归 —— 登录、上传 / 下载、播放、WebSocket 通知、jar 插件加载、OIDC 登录；外加"死实例 2 秒内从 503 转为 404"（**网关不得给实例列表加缓存**）。

### 8.7 收尾

确认 core 内无任何转发实现（决策 8）＋ 配置同步（`application-develop.yml` / `application-develop-mysql.yml` / `application-product.yml` / `pre-release/config.yml` / `docs/quick-start/config.md`）＋ 文档同步（本文件、路线图、档位 3、评估报告）。

---

## 6. 验收红线（逐条不许回退）

| # | 验收项 | 实现点 |
|---|---|---|
| 1 | 用户经网关调 demo 接口 200，返回的 `uid` 等于当前登录用户 | 网关 ④ ＋ core resolve |
| 2 | **伪造 `X-Sfc-Uid: 999` 的入站请求 → 微服务收到的仍是真实 uid** | 网关 filter 1 / 2（决策 5） |
| 3 | `?uid=999` 不生效 | 结构性约束：uid 不是参数 |
| 4 | 未登录 → 403/7005；`enabled=false` → 404/7002；服务不存在 / 目标为自身 → 404/7001；全实例不可用 → 503/7004 | core 判定 ＋ 网关映射 |
| 5 | 目标为 `sfc-core` / `sfc-gateway` → 404（提权与回环双阻断） | core resolve |
| 6 | 路径穿越 / 编码斜杠 / 编码空格的拒绝规则与错误码 | core 路径校验（只拒绝、不规范化） |
| 7 | `Token` 头 / `token` cookie / `?Token=` 三者均不出现在微服务侧 | 网关 filter 3~5 |
| 8 | 伪造 `X-Forwarded-For` 被覆盖为网关观测值 | 网关 filter 7 |
| 9 | hop-by-hop 头不出现在下游 | 网关 filter 6 |
| 10 | 请求体超限 413（带 `Content-Length` 与 chunked 两条路径） | 网关 filter 8 |
| 11 | 8MB 上传字节数与 SHA-256 一致 | 网关流式 |
| 12 | 1GB 响应体在 `-Xmx512m` 下 200 且不 OOM | 网关流式 |
| 13 | 两实例轮询交替；强杀其一后请求全部成功 | SCG `lb` ＋ `Retry` |
| 14 | 带请求体的请求不重试（只尝试 1 个实例） | `Retry methods=GET,HEAD` |
| 15 | **core 自身 API 零回归**：`@UID` / `@ValidPath` / `@RolesAllowed` / 登录 / 上传下载 / WebSocket / jar 插件 | 网关对 ① ② ③ 完全透明 |

---

## 7. 风险

| # | 风险 | 影响 | 对策 |
|---|---|---|---|
| 1 | **WebSocket 透传在 SCG 下的实际行为未验证** | 唯一入口方案当场不成立 | 8.1 第 2 项先实测；不合格则退回"网关只接管 `/api/ext/**` ＋ 前置 Nginx 分流"，WS 仍直连 core |
| 2 | 网关单点 | 整站不可用 | 入口切换前保留直连 core 的回退路径；部署文档写明 |
| 3 | 大文件多一跳 | 吞吐下降 | 8.1 第 8 项量化；必要时阶段 9 数据面旁路（暂缓项之一） |
| 4 | **信任模式被越界使用**（微服务端口暴露到公网 / 同网段） | **任意用户越权** | 部署约束写进文档（§2）；**上公网前必须先完成阶段 5** |
| 5 | **网关忘记剥离入站 `X-Sfc-*`** | 信任模式当场失效 | 决策 5 第 1 条；§6 验收第 2 条专门取证 |
| 6 | 版本矩阵分叉（网关 4.0.8 / core 4.1.0） | 升级节奏不同步 | 两者只通过 HTTP 协议耦合，**不共享任何 jar** |
| 7 | 两套"代理"心智模型残留 | 后续维护者误以为 core 仍能转发 | 决策 8：core 内不保留任何转发实现；文档显式写明 |
| 8 | 静态资源放行被路径穿越绕过 | "放行 `/static/**`"变成"放行任意路径" | 阶段 7 落地时**连同路径校验一起加**（决策 7） |

---

## 8. 学习点

| 学习点 | 在哪被逼出来 |
|---|---|
| **网关模式与同源策略** | 唯一入口是"同源"这个浏览器约束的直接结果，而不是架构偏好 |
| **策略 / 传输分离** | 决策 4：把"能不能转"与"怎么转"分开，才能既用官方库又不把判定逻辑搬出信任边界 |
| **信任边界必须是结构性的** | 决策 3.3：先把它做成"**唯一注入点**"，再补"**入站同类头全剥离**"——两条缺一不可 |
| **技术债要显式记账** | §2 的"一份安全债务"：写明债务、到期条件与部署兜底，而不是含糊的"以后再说" |
| **官方库的边界** | §2：官方库覆盖传输机制，不覆盖你的策略。判断"引入官方库能省掉什么"时，必须逐条对照自己的规则清单 |
| **成对取舍的记录方式** | 与[评估报告 §2](review/microservice-plan-review.md) 的"当时不需要、现在为什么需要"形成完整决策链 |
