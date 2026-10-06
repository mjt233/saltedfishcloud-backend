# 微服务改造 · 服务鉴权机制设计（定稿）

> **状态**：设计定稿，**尚未产生任何代码改动**。等待审阅。
> **范围**：**跨进程鉴权机制**——令牌形态、密钥来源与生命周期、签发方与校验方、信任边界、失败语义。
> **不含**：服务注册发现（见[档位 1 设计](microservice-phase1-design.md)）、代理转发实现（见[档位 3 设计](microservice-phase3-frontend-integration.md)）。
> **被谁消费**：阶段 5（本机制落地）· 阶段 6（微服务 → core `/api/internal/**`）· 阶段 8（控制面代理签发）· 阶段 9（数据面载体）。
> **一句话结论**：**只保留一种令牌（委托令牌），由 core 用一把自持 RSA 密钥以 RS256 签发、受众绑定、TTL ≤ 60 秒；微服务只用自建匿名 JWKS 端点离线验签，永不持有任何密钥材料。整套机制与 OIDC / OAuth 零耦合。**
> **配套**：[路线图](microservice-roadmap.md) · [档位 3 设计](microservice-phase3-frontend-integration.md) · [拓扑 / 泳道图](review/microservice-auth-topology.md) · [方案评估报告](review/microservice-plan-review.md)
> **创建日期**：2026-10-06
> **修订日期**：2026-10-06（**独立性调整**：上一版复用 OIDC 的 JWK / `JwtDecoder` / `/oauth2/jwks` / `StandardScopes`，现改为**全部自持**——自持密钥、自建 JWKS 端点、独立作用域、无 `@ConditionalOnProperty`。见 §0 第二张对照表）

---

## 阅读指引

| 你想了解 | 看第几节 |
|---|---|
| 相对原方案改了什么 | §0 |
| 一句话结论与可自检的性质 | §1 |
| 为什么否决原"内部服务令牌"方案 | §2 决策 1、决策 2 |
| **为什么与 OIDC/OAuth 解耦、怎么解耦** | §2 决策 8、§6 |
| **密钥从哪来、怎么校验、怎么轮换** | §2 决策 9、§7 |
| 令牌长什么样、活多久、失败怎么办 | §3 |
| 三条链路怎么用同一枚令牌 | §4 |
| 谁负责什么 | §5 |
| 微服务侧必须实现什么 | §8 |
| 安全红线与自建代理必修项 | §9 |
| 承重假设与未决项 | §10 |
| 要动哪些文件 | §11 |
| 审阅重点 | §12 |

---

## 0. 本设计相对前两版的差异

### 0.1 相对最初的"内部服务令牌"方案

原方案：`InternalServiceTokenService` ＋ 两类令牌（`issueForService` 服务身份 / `issueForUser` 用户委托）＋ **一套独立于用户 JWT 的共享对称密钥** ＋ 自定义头 `X-Sfc-Internal-Token`。

| 维度 | 原方案 | **本设计** | 理由 |
|---|---|---|---|
| 令牌类型 | 两类 | **一类：委托令牌** | 服务身份没有真实消费者，且与"uid 只来自令牌"冲突（决策 2） |
| 签名 | **共享对称密钥**（HMAC） | **RS256 ＋ 自持 RSA 密钥** | 对称密钥交给校验方 ⇒ **校验方也能签发**；非对称方案下微服务只拿公钥（决策 1） |
| 传输头 | `X-Sfc-Internal-Token` | **`Authorization: Bearer`** | 与既有 `OidcAccessTokenFilter` / `McpApiKeyFilter` 的 Bearer 约定一致；`Authorization` 已在 CORS 白名单内 |
| 受众绑定 | 未定义 | **`aud` 强制校验（两侧规则不同）** | 防微服务间横向重放（决策 5） |
| 有效期 | "短时效"（未定） | **控制面 ≤ 60 秒每请求现签；数据面 5 分钟** | 短到不需要撤销列表（决策 3、6） |
| 微服务侧 | 各自实现校验 | **线协议 ＋ 可选瘦库（只验签、只读 `sub`）** | 安全关键代码不复制 N 份（§8） |

### 0.2 相对上一版（"复用 OIDC 设施"）

上一版把 JWK、`JwtDecoder`、`/oauth2/jwks`、`StandardScopes`、`ROLE_OAUTH_USER` 全部复用自 OIDC。**该版本有两个已被否决的问题**：①微服务鉴权会随 `sys.oidc.enabled=false` 一起失效；②OIDC 侧的密钥/作用域变更会波及微服务鉴权。

| 维度 | 上一版（复用 OIDC） | **本版（自持）** | 理由 |
|---|---|---|---|
| 签名密钥 | `OidcAuthorizationServerConfig.jwkSource()`，DB 键 `sys.oidc.jwk` | **自持 RSA**，配置 `sys.service.auth.signing-key` | 换 OIDC 密钥不应波及微服务鉴权；OIDC 关闭时仍须可用 |
| 密钥存放 | 数据库配置（自动生成） | **配置文件 ＋ 环境变量**（运维显式提供） | 见决策 9 |
| JWKS 端点 | `/oauth2/jwks`（SAS 提供的 OIDC 端点） | **`GET /api/service-auth/jwks`**（自建、匿名） | 匿名性由我们自己的 `permitAll` 保证，**不必改 OIDC 安全链** |
| 验签器 | OIDC 的 `JwtDecoder` bean | **自持**（jjwt 直接持有公钥/私钥） | 不注入任何 OIDC bean |
| 作用域 | `StandardScopes`（开放平台 scope 值） | **独立 `internal.*`** | 改开放平台 scope 不应波及内部 API |
| 角色 | 授予 `ROLE_OAUTH_USER` | **不授予**（只给用户既有 authorities ＋ `SCOPE_*`） | `ROLE_OAUTH_USER` 是开放平台概念 |
| 开关 | `@ConditionalOnProperty("sys.oidc.enabled")` | **无 OIDC 条件**（独立 `sys.service.auth.enabled`） | 需求 3 |
| 对 OIDC 代码的改动 | 需要（拆 JWK bean、改链放行） | **零改动** | 只在一个**非 OIDC** 类（`SecurityConfig`）里加一行 permitAll |

> **净效果**：整条链路**不依赖任何 OIDC/OAuth 运行时设施**——`sys.oidc.enabled=false` 时照常工作，OIDC 侧的任何改动都不会波及微服务鉴权。

---

## 1. 结论与关键性质

**core 是唯一签发方；微服务是唯一校验方；两者之间的能力不对称由算法保证，不由约定保证。**

| # | 性质 | 为什么重要 |
|---|---|---|
| 1 | 分发给微服务的只有**公钥**（JWKS），它只能验签、不能签发 | "微服务被攻破 ≠ core 被攻破"**在结构上**成立，而不是靠"我们约定好" |
| 2 | 令牌**受众绑定**（`aud` ＝ 签发时固定的目标服务名） | 拿到给 A 的令牌无法用于 B，微服务之间的横向重放窗口关闭 |
| 3 | TTL ≤ 60 秒且**每请求现签** | 不需要撤销列表、不需要内省调用 |
| 4 | uid **只存在于令牌里** | 越权防线不依赖"微服务是否老实忽略了参数"（决策 5） |
| 5 | **与 OIDC/OAuth 零耦合**：自持密钥、自建 JWKS、独立作用域、无 OIDC 条件注解 | 故障域隔离 ＋ 演进隔离：关掉或替换 OIDC 不波及微服务鉴权 |
| 6 | 授权模型复用 `@RequireScope` 的**机制**（`SCOPE_*` authority 检查），但**作用域值独立** | 不重复实现 40 行切面，也不与开放平台 scope 语义绑定（决策 8） |

> **可自检的一句话**：全链路里出现"签发"的只有 core，出现"验签"的只有被调方；没有任何一个组件同时具备两种能力——除了 core 自己。

---

## 2. 决策记录

### 决策 1：否决"共享对称内部密钥" → **已确认：否决**

原方案自身给出的否决理由是正确的（[档位 3 §7.3](microservice-phase3-frontend-integration.md)）：**不共享用户 JWT 密钥**，因为"密钥扩散面变大，微服务被攻破 = core 被攻破"。但它随后提出的替代品是"core 签发、**微服务用共享内部密钥校验**"——**与它刚否决的方案同构，而且更糟**：

| # | 问题 | 说明 |
|---|---|---|
| 1 | **校验方获得了签发能力** | HMAC 密钥既能验也能签。任何持有该密钥的微服务都能伪造 `sub = 任意 uid` 的委托令牌——"微服务被攻破 = core 被攻破"依然成立，只是换了把钥匙 |
| 2 | 违反最小权限 | 校验方需要的能力是**验签**，实际拿到的是**签名** |
| 3 | 密钥分发无解 | 用户的 JWT 密钥是首次启动**随机生成 32 位、存进数据库 config**（[`SysSafeConfigConfiguration.initJwtSecret()`](../../../sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/SysSafeConfigConfiguration.java)）。那"独立的内部密钥"从哪来？写进 N 个微服务的配置文件？从 core 拉取（需要"已鉴权的接口"→ 循环依赖）？放数据库（微服务要访问 core 的库）？ |

**采用方案**：一把**自持的 RSA 密钥**（RS256），core 持私钥签发；微服务通过**自建匿名 JWKS 端点**取公钥离线验签。分发给微服务的产物从"签名密钥"变成"公钥"——**分发它没有安全后果，因此"密钥怎么安全送过去"这个问题直接消失**。

### 决策 2：删除"服务身份令牌" → **已确认：不实现**

原方案把"档位 2：微服务 → core"的令牌类型定为**服务身份**（`issueForService(serviceName)`）。三条理由说明这一类不该存在：

| # | 理由 |
|---|---|
| 1 | **没有真实消费者**。阶段 6 的调用是**代表用户**读他的文件，而服务身份里没有 uid ⇒ core 无从知道"读谁的文件"。两种出路都不成立：让微服务把 uid 放请求参数（违反红线）、或让 core 用服务身份换用户身份（uid 仍来自参数，红线照样被绕过）。**档位 2 真正需要的仍然是用户委托令牌** |
| 2 | **与既有授权体系不通**。core 侧拿到"我是 sfc-demo-service"这个身份，既不是 `ADMIN` 也不是某个 uid ⇒ [`UIDValidator`](../../../sfc-api/src/main/java/com/xiaotao/saltedfishcloud/validator/UIDValidator.java) 等既有校验全部失效，等于要在 core 内部**再建一套平行授权模型** |
| 3 | **重演了本路线图自己否决过的模式**。[决策 5](microservice-phase1-design.md) 否决"基于 `CacheService` 的 provider"的理由是"常驻成本，却没有真实消费者"。同一个标准用在这里：**没有真实消费者的令牌类型不该预留** |

> **若未来确有"无用户上下文的微服务自发调用"**（例如微服务自身的定时任务要写盘）：那它需要的是**系统主体**（等价 ADMIN 或专用 system uid），不是"serviceName 身份"；且必须显式声明"它只能以哪个 uid 行动"。**本机制不为它预留字段**——需要时另立设计。

### 决策 3：令牌形态 → **RS256 / 受众绑定 / TTL ≤ 60s / 每请求现签**

见 §3。

**为什么不需要撤销列表**：TTL ≤ 60 秒，且 core **每请求现签、不复用、不缓存**。撤销列表存在的意义是"长时效令牌如何提前失效"；把时效压到 60 秒以内，这个需求就不存在了。（代价：每请求多一次签名运算，量级为微秒。）

**为什么每请求都现签而不是复用**：复用需要缓存键（uid ＋ aud ＋ scope）与失效逻辑，收益是省下一次签名——用复杂度换微秒，不划算。

### 决策 4：校验方用什么 → **微服务自建 JWKS 离线验签；零回调、零共享秘密**

| 做法 | 结论 |
|---|---|
| 微服务共享 core 的**用户 JWT 签名密钥** | ❌ 用户会话令牌是 30 天长效，扩散面不可接受 |
| 微服务每请求**回调 core** 校验 | ❌ 每个请求多一跳、core 压力翻倍；并把 core 的可用性变成微服务的前置依赖 |
| 微服务持有**共享对称**的内部密钥 | ❌ 校验方也能签发（决策 1） |
| **JWKS 公钥离线验签（采用）** | ✅ 零回调、零共享秘密；公钥可缓存；只需处理 `kid` 变化 |

**端点归属**：**自建** `GET /api/service-auth/jwks`，**匿名**（由 `SecurityConfig` 的 permitAll 放行）。**不复用 `/oauth2/jwks`**——那样既会与 OIDC 的开关和安全链耦合，又要去改 SAS 的安全配置。

**缓存策略**：微服务本地缓存 JWKS，按 `kid` 查找；**未知 `kid` 必须回源重取一次**再判定失败（这条是轮换能否平稳的支点，见决策 9）。正常路径下**没有任何网络调用**。

**验证通过后的主体装载**：core 侧按 `sub` 装载用户并构造 `UserPrincipal` ⇒ 用户被删除 / 禁用时令牌自然失效，**免费继承了既有的用户有效性语义**。

### 决策 5：信任边界 → **uid 只来自令牌；`/api/internal/**` 的方法签名里不出现 uid 参数**

原方案的表述是"微服务**只信令牌中的 uid，忽略请求参数中的 uid**"。这是一条**弱表述**：它依赖每个微服务都正确地"忽略"某个参数，属于**约定式安全**。本设计把它加强为结构性约束：

> **`/api/internal/**` 的方法签名里根本不出现 uid 参数** —— core 侧通过 `SecureUtils.getCurrentUid()` 取当前主体，而该主体来自令牌的 `sub`。

于是"篡改 uid 越权"这类攻击**没有可篡改的入口**，而不是"有一个入口但我们约定不看它"。

**core 侧落地方式**（复用既有**非 OIDC** 设施，零新建授权模型）：

| 步 | 做法 |
|---|---|
| 1 | 新增一条独立过滤器链（写法可参照 [`OidcResourceServerConfig`](../../../sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcResourceServerConfig.java)，但**不复用其任何 bean**）：`securityMatcher("/api/internal/**")`，Order 高于主链 |
| 2 | 链内用**自持的** `DelegationTokenVerifier` 验签（jjwt 直接持公钥，不经 OIDC 的 `JwtDecoder`） |
| 3 | 校验 `iss` ＝ core、`exp`、`scope`，以及 **`aud` ∈ 已注册服务名集合**（**不是** `== sfc-core`，理由见 ⚠️） |
| 4 | 按 `sub` 装载用户 → `UserPrincipal.from(user)` → 授予用户既有 authorities ＋ `SCOPE_*`（**不授予 `ROLE_OAUTH_USER`**）→ 设入 SecurityContext |

**由此得到的关键收益**：`@UID`、`@ValidPath`、`@RolesAllowed`、`@RequireScope`、`ProtectBlocker` **全部原样生效，零改动**——因为它们读的都是 SecurityContext，而不是参数。

> **⚠️ 最容易写错的一处：`aud` 在两个校验方的规则不一样**
>
> | 校验方 | 规则 | 为什么 |
> |---|---|---|
> | **微服务** | **`aud` ＝＝ 自己的服务名** | 这正是"受众绑定"要防的事：给 A 的令牌不能被 B 接受（阻断**微服务之间的横向重放**） |
> | **core 的 `/api/internal/**`** | **`aud` ∈ 已注册服务名集合**（而非 `＝＝ sfc-core`） | 令牌本就是 core 签发的，它用 `iss` 认领签发权即可；`aud` 在这里表达的是"这枚令牌被签发给链路上的哪个微服务"。**若写成 `aud ＝＝ sfc-core`，"core → 微服务 → core"的透传链路（阶段 6 / 决策 7）会当场断掉** |
>
> **请勿把这一条"顺手修正"成 `aud ＝＝ sfc-core`**——那会让阶段 6 的链路失效，且症状是"阶段 8 通了、阶段 6 一直 401"这种很难定位的形态。

### 决策 6：数据面（阶段 9）＝ **同一枚令牌换载体；取 A 方案（短时效 ＋ 资源绑定）**

| | 控制面（阶段 8） | 内部回调（阶段 6） | 数据面（阶段 9） |
|---|---|---|---|
| 载体 | `Authorization: Bearer` | `Authorization: Bearer`（**透传**，不重签） | URL query（`?token=`） |
| 签发时机 | 每请求现签 | 不签发 | 由 core 在生成下载地址时签发 |
| TTL | ≤ 60 秒 | 随原令牌 | **5 分钟**（`download-token-ttl-seconds`，可配） |
| 额外绑定 | — | — | **`res`（资源标识）**，可选 `path`（路径前缀） |
| scope | `internal.read` / `internal.write` | 原样 | **只读**（不复用控制面高权限 scope） |

> **已确认：不承诺"一次性"**。原方案的"一次性 token"没有 nonce/状态存储支撑，属于空头承诺。取 A 方案后，契约里**明确写"有效期内可重放"**，与[既有文件临时链接](../../../sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/FileLinkServiceImpl.java)的语义一致。
> （B 方案"真一次性"需要微服务侧加 nonce 存储与原子消费，**本期不做**。）

> **URL 载体的固有风险**：query 里的令牌会经 `Referer`、浏览器历史、Nginx access log 与各类代理日志泄漏 ⇒ 必须**短 TTL ＋ 资源绑定 ＋ 只读 scope**。

### 决策 7：阶段 6 的令牌来源 → **唯一签发方的必然结果：调用链必须由 core 起头**

令牌只能由 core 签发（决策 1）。那么在阶段 6（此时**还没有**阶段 8 的代理层）微服务怎么拿到令牌？——**它拿不到，除非 core 主动给它**。这不是缺陷，而是"唯一签发方"的必然推论，而且是一条**想要的性质**：

> **微服务无法自发访问 core 的文件能力。** "微服务后台任务随便读盘"在结构上不可能发生；若确有这种需求，必须显式建模为系统主体（决策 2 的补充说明）。

| 项 | 做法 |
|---|---|
| 校验路径 | 与阶段 8 完全相同：`/api/internal/**` 过滤器链 ＋ 自持验签 ＋ 按 `sub` 装载主体（决策 5） |
| 令牌来源 | core 侧一个**管理员限定**的最小签发入口（`@RolesAllowed(ADMIN)`），由它签发令牌并发起对微服务的调用 |
| 与阶段 8 的关系 | **同一个签发器**（`DelegationTokenIssuer`）；阶段 8 只是把签发时机从"管理员手动触发"改为"每请求现签"，**不产生第二条签名路径** |
| 待确认 | 该入口的确切形态（自定义内部端点 / RFC 8693 token exchange 端点）。**约束**：管理员限定、复用同一签发器、不得引入第二种令牌 |

### 决策 8：与 OIDC / OAuth 解耦 → **自持密钥 ＋ 自建端点 ＋ 独立作用域 ＋ 无 OIDC 条件注解**

**目标（用户明确要求）**：不要与 `sfc-core` 中 OIDC/OAuth 相关功能耦合，尽可能独立。落到三条硬规则：

| # | 规则 | 检验方式 |
|---|---|---|
| 1 | **不注入任何 OIDC bean**：`JWKSource` / `JwtEncoder` / `JwtDecoder` / `RegisteredClientRepository` / `OAuth2AuthorizationService` 一个都不用 | 新代码里 `grep` 不到这些类型 |
| 2 | **不打 OIDC 的条件注解**：新配置类不出现 `@ConditionalOnProperty("sys.oidc.*")` | `sys.oidc.enabled=false` 时功能必须完好 |
| 3 | **不共用 OIDC 的密钥与端点**：密钥是自持的 `sys.service.auth.signing-key`，JWKS 走自建的 `/api/service-auth/jwks` | 改动 / 关闭 OIDC 不影响微服务鉴权 |

**唯一刻意保留的"机制级复用"**：`@RequireScope` 注解 ＋ [`RequireScopeAspect`](../../../sfc-core/src/main/java/com/xiaotao/saltedfishcloud/aspect/RequireScopeAspect.java)。
它只做一件事——检查 SecurityContext 里有没有 `SCOPE_<value>` authority，**没有任何 OAuth 运行时依赖**。重复实现这 40 行才是新的技术债。
**但作用域的「值」完全独立**：新建 `ServiceAuthScopes`（`internal.read` / `internal.write`），**不复用** [`StandardScopes`](../../../sfc-api/src/main/java/com/xiaotao/saltedfishcloud/constant/StandardScopes.java) 里的开放平台 scope 值——避免"同一个字符串在两套体系里含义不同/一起变更"。

**明确不依赖的清单**（这份清单同时是"独立性"的证据）：

| 不依赖 | 原因 |
|---|---|
| `OidcAuthorizationServerConfig` / `jwkSource()` / `JwtDecoder` bean | 换 OIDC 密钥不应波及微服务鉴权 |
| `/oauth2/jwks` 与 Spring Authorization Server 的安全链 | 匿名性由我们自己保证，且不必改 OIDC 安全配置 |
| `OidcAccessTokenFilter` / `OidcResourceServerConfig` / `/api/openApi/**` 那套 | 那是开放平台对外授权，与内部服务调用无关 |
| `StandardScopes` / `SysRole.OAUTH_USER` | 开放平台的作用域与角色语义 |
| `sys.oidc.*` 全部配置项 | 本机制有自己的 `sys.service.auth.*` |

### 决策 9：密钥来源与生命周期 → **配置文件 ＋ 环境变量；严格单密钥；`kid` 取指纹**

**形状**（完整片段见 §7）：

```yaml
sys:
  service:
    auth:
      enabled: false                              # 默认关闭，见下方说明
      signing-key: ${SERVICE_AUTH_SIGNING_KEY:}   # PKCS#8；单行 base64(DER) 或多行 PEM
```

| 项 | 决定 | 理由 |
|---|---|---|
| 来源 | **配置文件 ＋ 环境变量占位符** | 运维显式掌控；`${ENV:}` 是 Spring 原生能力，项目既有配置已大量使用该风格 |
| 格式 | PKCS#8；**单行 base64(DER)** 或 **多行 PEM** 都接受 | base64 单行对环境变量友好（PEM 多行在环境变量里要处理换行，很别扭） |
| 公钥 | **由私钥派生**，不单独配置 | 少一个可能配错的项；JWKS 只发布公钥 |
| `kid` | **＝ 公钥 SHA-256 指纹，不可配置** | ⚠️ 若开放配置，**换密钥却忘了改 `kid`** 会让微服务认为"kid 未变、缓存仍有效"而不回源，直接验签失败。指纹随密钥自动变化，轮换路径才成立 |
| 密钥数量 | **严格单密钥**（v1，不提供旧公钥位） | 轮换靠"未知 `kid` 回源"完成，见下方"轮换路径" |
| 集群 | **所有 core 节点必须配同一把密钥** | 否则 LB 之后"A 节点签发、B 节点出 JWKS"会验签失败。**这是配置式密钥相对数据库式密钥的运维代价，必须写进部署说明** |

**启动 / 运行校验矩阵**：

| 情形 | 行为 |
|---|---|
| `enabled: false` | 不签发；JWKS 返回**空集合**；`/api/internal/**` 链**仍注册但一律 401** |
| `enabled: true` ＋ 私钥缺失 ＋ profile ∈ {`develop`, `develop-mysql`} | 启动 **WARN**（明确提示"委托令牌不可用"）；**真正用到私钥时立即抛明确错误**（JWKS 请求、签发调用），错误信息含 `openssl` 生成命令 |
| `enabled: true` ＋ 私钥缺失 ＋ 其它 profile（含 `product`、**含未知 profile**） | **启动失败** ＋ 明确消息 ＋ 生成命令 |
| 私钥存在但**格式非法**（非 RSA / 位数 < 2048 / 解码失败） | **一律启动失败**，不分 profile |

> **三个刻意的取舍**
>
> 1. **`enabled` 默认 `false`**：否则按上表，`application-product.yml` 里那个空占位符会让 **product 直接起不来**——这是本次改造不想要的副作用。默认关闭 = 不阻断既有部署；一旦显式启用，fail-fast 立刻生效。
> 2. **开发模式的判定用"白名单"而非"排除 product"**：只有 `develop` / `develop-mysql` 算开发模式，**其余一切（含 profile 拼错）一律 fail-fast**。偏向严格。
> 3. **"没配"才分 profile，"配错了"一律失败**：格式非法在任何 profile 下都启动失败——否则 develop 会得到一个"能启动但一用就错"的假象。

> **⚠️ `/api/internal/**` 的链必须"始终注册"**：不能因为 `enabled=false` 或缺密钥就不注册这条链。否则请求会落到主链的 `anyRequest().authenticated()`，变成"**任何登录用户都能访问内部 API**"（用自己的 uid 读自己的文件）——一个很隐蔽的口子。正确做法：**链始终在，条件不满足就一律 401**。

**密钥生成**（文档提供命令，另附一个可选生成器）：

```bash
# 1) 生成 PKCS#8 私钥
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out sfc-service-auth.pem
# 2) 转成单行 base64(DER)，便于放进环境变量
openssl pkcs8 -topk8 -nocrypt -in sfc-service-auth.pem -outform DER | base64 -w0
```
> 另提供 `ServiceAuthKeyGenerator`（约 30 行，`main` 方法，直接打印可粘贴的 base64），避免手搓与平台差异（Windows 下 `base64 -w0` 不可用）。

**轮换路径**（单密钥下如何不断服）：

```
替换配置里的密钥 → 下一个新签发的令牌带新 kid
   → 微服务验签时发现「未知 kid」→ 回源 /api/service-auth/jwks 一次 → 取到新公钥 → 通过
```
> 因此"**未知 `kid` 必须回源一次再判定失败**"从一条实现建议**升级为契约硬要求 ＋ 验收项**——它是单密钥方案能否平稳轮换的唯一支点。

---

## 3. 令牌形态

### 3.1 Claims

| Claim | 取值 | 校验方是否强制校验 |
|---|---|---|
| `iss` | `sfc-core` | ✅ |
| `aud` | **签发时即固定为"目标微服务名"**；**透传时不改写** | ✅ **必须**，但**两侧规则不同**：微服务侧 `== 自己`，core 侧 `∈ 已注册服务名`（决策 5 的 ⚠️） |
| `sub` | 用户 uid（字符串形式） | ✅ |
| `scope` | 取自 `ServiceAuthScopes`：`internal.read` / `internal.write` | ✅（受保护操作） |
| `exp` | 见 §3.2 | ✅ |
| `iat` | 签发时间 | 建议 |
| `jti` | 随机串 | 仅用于日志关联，**不用于去重**（无状态） |
| `res` / `path` | 数据面：资源标识 / 路径前缀绑定 | 数据面必需 |

> **不含 `roles`**：core 侧按 `sub` 重新装载用户即得到**最新**角色；令牌里带角色会在用户被降权后继续生效。

### 3.2 有效期

| 载体 | TTL | 配置项 |
|---|---|---|
| 控制面（header） | **≤ 60 秒** | `token-ttl-seconds` |
| 数据面（query） | **5 分钟** | `download-token-ttl-seconds` |

### 3.3 失败语义（统一口径，避免各微服务自行发挥）

| 情形 | 响应 | 说明 |
|---|---|---|
| **`sys.service.auth.enabled=false`** | **401** | `/api/internal/**` 一律拒绝（不留主链穿透的口子） |
| 无令牌 | **401** | fail-closed；**不得**降级为匿名或"信任内网" |
| 验签失败 / 过期 / `iss` 不符 / **`aud` 不符** | **401** | 对外统一为"凭证无效"，不区分具体原因 |
| 令牌有效但 `scope` 不足 | **403** | 与既有 `RequireScopeAspect` 抛 `AccessDeniedException` 的语义对齐 |
| 私钥/公钥不可用（develop 未配密钥） | **500 ＋ 明确消息** | 配置错误应当显式暴露，而不是静默 401 |
| 服务不存在（core 侧代理） | **404** | `serviceName` 未命中 `ServiceDiscovery` |
| 全部实例不可用（core 侧代理） | **503** | 而非 500 |

---

## 4. 三条链路一览

| # | 链路 | 令牌流向 | 载体 | 阶段 | 图内编号 |
|---|---|---|---|---|---|
| ① | 浏览器 → core → 微服务 | core **现签** | `Authorization: Bearer` | 8 | ④⑤⑥⑦⑧ |
| ② | 微服务 → core `/api/internal/**` | **透传**同一枚（不重签、不改写） | `Authorization: Bearer` | 6 | ⑪⑫⑬ |
| ③ | 浏览器 → 微服务（数据面） | core 在生成下载地址时签发 | URL query | 9 | 数据面旁路 |
| — | 微服务 → core 取公钥 | — | `GET /api/service-auth/jwks`（**匿名**） | 5 | ⑤ |

> 完整时序见[拓扑 / 泳道图](review/microservice-auth-topology.md)。

---

## 5. 职责边界

| 组件 | 职责 | **明确不做** |
|---|---|---|
| **core（唯一签发方）** | 认证用户；执行 `@UID` / `@ValidPath`；按目标服务**现签**委托令牌；`/api/internal/**` 验签并按 `sub` 装载主体 | 不把用户会话令牌交给任何下游；不缓存/复用委托令牌 |
| **core 代理层** | 选实例、注入令牌、流式转发、503/404、剥离 hop-by-hop 头 | 不解释微服务的业务语义；不做请求体的全量缓冲 |
| **微服务（唯一校验方）** | JWKS 离线验签；校验 `iss`/`aud`/`exp`/`scope`；**未知 `kid` 回源一次**；**只有 `sub` 能当 uid**；**无令牌一律 401** | **不持有任何密钥材料**、不签发、不解析用户会话 JWT |
| **Nacos** | 注册发现；`sfc.*` 自描述元数据 | **不参与鉴权**（`sys.service.nacos.username/password` 只是注册中心自身的账号） |
| **浏览器** | 携带会话 JWT 调 core；按清单加载资源 | 不接触任何内部令牌 |

---

## 6. 与既有设施的关系（**不耦合**）

### 6.1 复用的（都是**非 OIDC** 的通用设施）

| 设施 | 位置 | 用途 |
|---|---|---|
| `UserService` | `sfc-core` | 按 `sub` 装载用户 → `UserPrincipal` |
| `ServiceDiscovery.listServiceNames()` | 阶段 1~3 已建成 | core 侧 `aud` 白名单的默认来源（＋ `allowed-audiences` 追加） |
| `@RequireScope` ＋ [`RequireScopeAspect`](../../../sfc-core/src/main/java/com/xiaotao/saltedfishcloud/aspect/RequireScopeAspect.java) | `sfc-api` / `sfc-core` | **机制**复用：检查 `SCOPE_*` authority（作用域**值**独立，见决策 8） |
| `SecurityConfig` 的 permitAll 列表 | `sfc-core`（**非 OIDC 类**） | 放行 JWKS 端点（**唯一一处对既有类的改动**） |
| `jjwt 0.12.6` | 根 `pom.xml` 已声明 | 签发与验签（不引入新依赖；也不引入 nimbus / SAS 客户端） |

### 6.2 明确**不**依赖的（独立性证据）

| 不依赖 | 影响 |
|---|---|
| `OidcAuthorizationServerConfig` / `jwkSource()` / `JwtDecoder` bean | 换 OIDC 密钥不波及微服务鉴权 |
| `/oauth2/jwks` ＋ Spring Authorization Server 安全链 | 匿名性自己保证，**OIDC 安全配置零改动** |
| `OidcAccessTokenFilter` / `OidcResourceServerConfig` / `/api/openApi/**` | 开放平台对外授权，与内部服务调用无关 |
| `StandardScopes` / `SysRole.OAUTH_USER` | 开放平台的作用域与角色语义 |
| `sys.oidc.*` 全部配置项 | 本机制有自己的 `sys.service.auth.*` |

> **验收方式**：`sys.oidc.enabled=false` 时，委托令牌的签发、JWKS 端点、`/api/internal/**` 调用**必须全部正常工作**——这条是独立性要求的直接检验。

---

## 7. 配置

### 7.1 配置片段

```yaml
sys:
  service:
    auth:
      # 委托令牌机制开关。
      # 注意语义：enabled=false 表示「关闭」，不是「放开」——/api/internal/** 仍注册链并一律 401。
      # 默认关闭：功能未落地期间不阻断任何既有部署的启动。
      enabled: false
      # 签名私钥（PKCS#8）。单行 base64(DER) 或多行 PEM 均可。
      # 生产环境必须用环境变量注入，不要把私钥提交进仓库。
      signing-key: ${SERVICE_AUTH_SIGNING_KEY:}
      # 控制面令牌 TTL（秒）
      token-ttl-seconds: 60
      # 数据面（下载）令牌 TTL（秒）
      download-token-ttl-seconds: 300
      # aud 追加白名单。默认取 ServiceDiscovery 的已注册服务名；这里用于补充/测试
      allowed-audiences: []
```

### 7.2 配置同步范围（AGENTS.md 强制）

`sys.` 节点下的新增项必须同步，**不改 `application.yml`**：

| 文件 | 注意 |
|---|---|
| `sfc-core/src/main/config/application-develop.yml` | 可写 `enabled: true` ＋ 留空私钥（开发模式只 WARN，便于联调） |
| `sfc-core/src/main/config/application-develop-mysql.yml` | 同上 |
| `sfc-core/src/main/config/application-product.yml` | **`enabled: false`**（默认）＋ `${SERVICE_AUTH_SIGNING_KEY:}` 占位符 |
| `pre-release/config.yml` | 沿用 `${ENV:默认}` 占位符风格 |
| `docs/quick-start/config.md` | 两处示例同步 |

> ⚠️ **不要把真实私钥写进任何配置文件**；`application-product.yml` 里只能是占位符。

---

## 8. 微服务侧契约

### 8.1 必须实现的六件事

| # | 要求 |
|---|---|
| 1 | 从 `Authorization: Bearer` 取令牌；**不做**自定义头 |
| 2 | 用 `GET /api/service-auth/jwks`（**匿名**）**离线**验签（RS256）；本地缓存公钥，按 `kid` 命中 |
| 3 | **未知 `kid` 必须回源重取一次**再判定失败（轮换的支点，决策 9） |
| 4 | **强制**校验 `aud == 自己的服务名`、`exp`、`iss`，以及受保护操作所需的 `scope` |
| 5 | `uid` **只能**来自令牌的 `sub`；业务代码里**不得**读取任何名为 `uid` 的请求参数 |
| 6 | **fail-closed**：除 `/static/**` 与健康检查外，**无令牌一律 401** |

> ⚠️ **第 6 条是硬约束，不是建议**。[档位 3 §1](microservice-phase3-frontend-integration.md) 把"微服务可内网不可达"写成了**目标**——但**目标不是约束**。网络边界一旦被打破（同网段扫描、误配 Nginx、容器网络打通），fail-open 的微服务就等于"任意读"。**不得把内网不可达当作安全前提。**

### 8.2 静态资源放行（两个必须写死的规则）

| 规则 | 原因 |
|---|---|
| 放行路径必须做**规范化**（拒绝 `.` / `..` / `%2e` 及多重编码）后再做前缀匹配 | 否则"放行 `/static/**`"会因为 `/api/ext/svc/static/../api/task/create` 这类构造变成"放行任意路径" |
| **代理 Controller 禁止使用 `@AllowAnonymous`** | `@AllowAnonymous` 的注册粒度是 **controller 方法的 pattern values**（见 [`SecurityConfig.getAnonymousUrls()`](../../../sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/SecurityConfig.java)）。代理 Controller 映射的是 `/api/ext/**`，一旦被标注，**整个控制面代理会一起 permitAll**，红线当场失效 |

### 8.3 建议提供"只验签瘦库"（可选，但强烈建议）

把"拉取 JWKS ＋ 缓存 ＋ 未知 `kid` 回源 ＋ 验签 ＋ 提取 `sub`"做成一个**只读、零内部依赖**的小库 `sfc-cloud-auth`，微服务只需加一个 `@RequireInternalToken` 注解。

- **内部用 jjwt 0.12.6**（与根 pom 同版本，微服务只多 3 个小 jar：`jjwt-api` / `jjwt-impl` / `jjwt-jackson`），**不引入 spring-security**；
- **为什么不破坏"零咸鱼云依赖"这个卖点**：[档位 1 决策 1 的实施记录](microservice-phase1-design.md)已实测证明——**契约库可以做到 1 个依赖**（对照 `sfc-api` 的 208 个）；
- **为什么不把校验交给各微服务自己实现**：安全关键逻辑复制 N 份，就有 N 次"忘了校验 `aud` / `exp` / 未知 kid 回源"的机会，而这类缺陷**不会在功能测试里暴露**；
- **同时必须给出线协议**（header、JWKS URL、claims 表、失败语义）：不想引库的人可以照文档自己实现。**学习价值在线协议这一层，不在重复实现 `OncePerRequestFilter`。**

---

## 9. 安全红线

### 9.1 六条红线

| # | 红线 | 违反后果 |
|---|---|---|
| 1 | 不把用户会话令牌（30 天）交给任何微服务 | 令牌扩散，微服务被攻破即等同 core 被攻破 |
| 2 | 不引入任何**共享对称密钥**（含"内部服务密钥"） | 校验方获得签发能力，决策 1 的全部问题重现 |
| 3 | 委托令牌**必须**有 `aud` 且校验方**必须**比对（两侧规则见决策 5） | 微服务间横向重放 |
| 4 | uid **只**来自令牌 `sub`；`/api/internal/**` 方法签名里不出现 uid 参数 | 篡改参数越权访问他人网盘数据 |
| 5 | 微服务**无令牌一律 401**（fail-closed） | 内网被突破即任意读 |
| 6 | `/api/internal/**` 的过滤器链**必须始终注册**（不随 `enabled` 或密钥缺失而消失） | 请求落到主链，**任何登录用户都能访问内部 API** |

### 9.2 自建代理的必修项

| 细节 | 风险 | 处理 |
|---|---|---|
| hop-by-hop 头（`Connection`、`TE`、`Transfer-Encoding`、`Keep-Alive` 等） | 请求走私（request smuggling） | 转发前按 RFC 7230 剥离 |
| `Host` / `X-Forwarded-For` / `X-Forwarded-Host` | 下游服务自我认知错误；伪造来源 IP | core **重写** `Host`，**追加**（而非信任）`X-Forwarded-*` |
| 请求体大小上限 | 恶意大请求打满 core | 控制面设上限（数据面已走预签名，不过 core） |
| 目标 URL 构造 | **SSRF** | `serviceName` **必须**命中 `ServiceDiscovery` 已知服务（既有的"服务不存在 → 404"就是这条防线） |
| 静态放行前缀匹配 | 绕过鉴权直达业务 API | 路径规范化（§8.2） |
| 转发路径 | 大响应 OOM | **强制流式管道**，禁止 `byte[]` 全量缓冲 |

---

## 10. 风险与未决项

| # | 项 | 类型 | 处理 |
|---|---|---|---|
| 1 | **私钥要写进配置体系** → 可能被误提交进仓库 | ⚠️ 运维纪律 | 生产只用环境变量注入；`application-product.yml` 只留占位符；`.gitignore` 与部署文档写明 |
| 2 | **集群各节点密钥必须一致**（配置式密钥的固有代价） | ⚠️ 部署约束 | 写进部署说明；验收项含"LB 后任一节点签发、任一节点出 JWKS 均能验签通过" |
| 3 | 单密钥轮换依赖"未知 `kid` 回源" | 已设计 | 契约硬要求 ＋ 验收项（决策 9） |
| 4 | 透传链路的 `aud` 规则被误写成 `== sfc-core` | 实施错误 | 决策 5 的 ⚠️ 说明；验收项加"**core → 微服务 → core 的透传调用必须成功**" |
| 5 | 微服务侧漏校验 `aud` / 漏做未知 `kid` 回源 | 人为 | 提供瘦库（§8.3）＋ 线协议文档 ＋ 验收项 |
| 6 | 数据面 URL 泄漏（Referer / 日志） | 固有 | 短 TTL ＋ 资源绑定 ＋ 只读 scope（决策 6） |
| 7 | 阶段 6 最小签发入口的确切形态未定 | 未决 | 约束：管理员限定、复用同一签发器、不引入第二种令牌（决策 7） |
| 8 | 多实例 metadata 不一致 | 部署约定 | 沿用[档位 3 §5.3](microservice-phase3-frontend-integration.md) 的约定 |

> **本轮已消除的原承重假设**：上一版把"`/oauth2/jwks` 是否匿名可访问"列为必须实测的前提。改为**自建端点**后，匿名性由我们自己的 `permitAll` 决定——**这条假设不复存在**。

---

## 11. 实施清单（阶段 5）

| # | 文件 / 动作 | 职责 |
|---|---|---|
| 5.1 | 本文档（设计定稿） | 契约冻结：微服务是**独立仓库 / 独立进程**，契约必须先冻结 |
| 5.2 | `.../service/auth/ServiceAuthProperties.java`（新增） | `@ConfigurationProperties("sys.service.auth")`；私钥解析（base64/PEM）、格式校验、profile 判定 |
| 5.3 | `.../service/auth/ServiceAuthKeyGenerator.java`（新增，可选） | `main` 方法生成并打印可直接粘贴的 base64 私钥 |
| 5.4 | `.../service/auth/DelegationTokenIssuer.java`（新增） | 自持 RSA 签发（jjwt），`kid` ＝ 公钥指纹 |
| 5.5 | `.../service/auth/DelegationTokenVerifier.java`（新增） | 自持验签 ＋ 校验 `iss` / `exp` / `scope`，**`aud ∈ 已注册服务名 ∪ 白名单`**（不是 `== sfc-core`） |
| 5.6 | `.../constant/ServiceAuthScopes.java`（新增） | `internal.read` / `internal.write` |
| 5.7 | `.../controller/ServiceAuthJwksController.java`（新增） | `GET /api/service-auth/jwks`，**匿名**，返回公钥数组 |
| 5.8 | `.../config/security/ServiceAuthSecurityConfig.java`（新增） | `/api/internal/**` 链，**始终注册**，无任何 `@ConditionalOnProperty` |
| 5.9 | `SecurityConfig.java`（改 1 行） | permitAll 加 JWKS 路径（**非 OIDC 类**） |
| 5.10 | 4 个配置 ＋ `docs/quick-start/config.md` | 同步 §7.1 的键 |
| 5.11 | `sfc-cloud-auth` 瘦库 ＋ 线协议文档 | 只验签、只读 `sub`；内部 jjwt；形态照抄 `sfc-cloud-api`（不继承 root pom） |

**明确不做**：`InternalServiceTokenService`、`issueForService`、任何形式的共享对称密钥、任何自定义令牌请求头、对 `/oauth2/jwks` 或其它 OIDC 端点的依赖。

**验证标准**：

- [ ] `curl /api/service-auth/jwks` **匿名可达**，返回的公钥能被离线验签通过
- [ ] **`sys.oidc.enabled=false` 时全套功能正常**（独立性的直接检验）
- [ ] `aud` 不匹配 → 401；过期 → 401；`scope` 不足 → 403；无令牌 → 401
- [ ] `enabled=false` 时 `/api/internal/**` 返回 401（**不落到主链**）
- [ ] `enabled=true` ＋ 私钥缺失 ＋ `product` → **启动失败**；＋ `develop` → WARN，且 JWKS/签发请求报明确错误
- [ ] 私钥格式非法 → **任何 profile 都启动失败**
- [ ] 替换密钥后：新令牌的 `kid` 变化，微服务**回源一次**后验签通过
- [ ] 令牌中不存在 `roles`（角色由 core 按 `sub` 实时装载）
- [ ] **core → 微服务 → core 的透传调用成功**

---

## 12. 审阅时建议重点看这五处

1. **决策 8（与 OIDC/OAuth 解耦）**（§2）——这是本轮最大的方向变更。请确认清单 §6.2 里"明确不依赖"的那五项确实都不需要，以及唯一保留的"机制级复用"（`@RequireScope` 切面 ＋ 独立作用域值）是你接受的程度。
2. **决策 9 的三个刻意取舍**（§2）——`enabled` 默认 `false`、开发模式用**白名单**判定、`kid` **不可配置**。特别是第三个：它去掉了一个配置项，换来"换密钥时不会因忘记改 kid 而验签失败"。
3. **决策 5 的 ⚠️（`aud` 两侧规则不同）**（§2）——写错会让阶段 6 全 401 且难以定位。
4. **决策 6 的"非一次性"**（§2）——取 A 方案后契约里明确写"有效期内可重放"。请确认这与你的预期一致。
5. **§9.1 红线 6**（`/api/internal/**` 链始终注册）——这是本轮新识别出的一个隐蔽口子（链不注册 ⇒ 落到主链 ⇒ 任何登录用户可访问内部 API）。

---

## 13. 学习点

| 学习点 | 在哪被逼出来 |
|---|---|
| **对称 vs 非对称：能力不对称** | 决策 1：为什么"共享密钥"在结构上无法表达"只有一方能签发" |
| **最小权限落到密钥分发上** | 决策 1：校验方需要验签，不应拿到签名能力 |
| **密钥分发的引导问题** | 决策 1 第 3 条：密钥怎么送到校验方？为什么公钥分发没有这个问题 |
| **受众绑定与令牌重放** | 决策 3 / §3.1：`aud` 是"令牌可否被拿去用在别处"的答案 |
| **无状态设计的边界条件** | 决策 3：把 TTL 压到 60 秒，就能取消撤销列表——"不需要"比"实现了"更好 |
| **约定式安全 vs 结构性安全** | 决策 5："忽略参数里的 uid"（约定）→"uid 根本不是参数"（结构） |
| **解耦的代价与收益量化** | 决策 8：多一把密钥、多一个端点，换来故障域与演进隔离——并且能用一个开关（`sys.oidc.enabled=false`）直接检验 |
| **配置项设计中的失败模式消除** | 决策 9：`kid` 取指纹而非可配，是"删掉一个配置项以消除一类故障"的典型 |
| **fail-fast 与 fail-safe 的边界** | 决策 9 校验矩阵："没配"分 profile，"配错了"一律失败；未知 profile 按严格处理 |
| **默认值与部署安全的相互作用** | 决策 9：`enabled` 默认 `false` 才不至于让 `product` 因一个空占位符而起不来 |
| **能力边界的识别** | 决策 7：唯一签发方 ⇒ 微服务不能自发访问 core——把约束识别成性质 |
| **隐蔽的"降级即放开"陷阱** | §9.1 红线 6：过滤器链不注册 ⇒ 落到主链 ⇒ 登录用户即可访问内部 API |
