# 微服务鉴权 · 拓扑 / 泳道图

> **性质**：配套图件。对应的方案结论见[**服务鉴权机制设计**](../microservice-service-auth-design.md)、[路线图阶段 6/8/9](../microservice-roadmap.md)与[档位 3 设计](../microservice-phase3-frontend-integration.md)。
> **方案要点**：`sfc-core` 是**唯一签发方**（RS256 / **自持密钥**）；微服务是**唯一校验方**（公钥取自 **core 自建的匿名 JWKS 端点**，永不持有密钥材料）。整套机制**与 OIDC/OAuth 零耦合**。
> **渲染图**：[microservice-auth-topology-swimlane.png](microservice-auth-topology-swimlane.png)
> **修订日期**：2026-10-06（独立性调整：`/oauth2/jwks` → 自建 `/api/service-auth/jwks`；"复用现有 JWK" → 自持密钥；`/internal/**` → `/api/internal/**`）

---

## ① 部署拓扑

```mermaid
flowchart LR
    B["用户 / 浏览器<br/>主 SPA（由 core 托管）<br/>同源：只跟 core 说话"]

    subgraph CORE["sfc-core —— 唯一入口 / 唯一签发方"]
        direction TB
        AUTH["认证链<br/>JwtAuthenticationFilter<br/>TokenService(Redis) 白名单"]
        PROXY["代理层<br/>ServiceProxyController<br/>/api/ext/** （无 {} 占位符）"]
        INNER["/api/internal 链<br/>自持验签（jjwt）<br/>方法签名无 uid 参数"]
    end

    N["Nacos<br/>服务注册发现 · sfc.* 元数据<br/>不参与鉴权"]
    S["微服务（独立进程 / 唯一校验方）<br/>公钥取自自建 JWKS 端点（本地缓存）<br/>uid ← 令牌 sub"]

    B -->|"① 会话 JWT（同源 /api/**）"| CORE
    CORE <-->|"② 查询实例 / ④ 注册心跳"| N
    CORE -->|"③ HTTP 反代 ＋ Authorization: Bearer 委托JWT（流式管道）"| S
    S -.->|"⑤ 匿名 GET /api/service-auth/jwks（公钥，无需任何凭证）"| CORE
    B -.->|"数据面（阶段 9）：同一枚委托 JWT 换载体直连，不过 core"| S
```

**读图要点**

| # | 关键点 |
|---|---|
| ① | 浏览器只跟 core 说话，**同源**，因此前端无需任何跨域与令牌处理改动 |
| ② ④ | Nacos 只承担注册发现与 `sfc.*` 自描述元数据，**不参与鉴权**（`sys.service.nacos.username/password` 只是注册中心自身的账号） |
| ③ | core 是唯一签发方：令牌在这里现签，微服务只接收 |
| ⑤ | 微服务的公钥来自 **core 自建的匿名端点** —— "密钥怎么安全地分发给微服务"这个问题不存在；且该端点**不依赖 OIDC**，`sys.oidc.enabled=false` 时照常可用 |
| 数据面 | 预签名直连时，浏览器直连微服务；core 只签发、不承载流量（流式代理仍是降级路径） |
| **独立性** | 委托令牌的密钥、JWKS 端点、作用域**全部自持**：不注入任何 OIDC bean、不打 `@ConditionalOnProperty("sys.oidc.*")`、不共用 `/oauth2/jwks` 与 `StandardScopes` |

---

## ② 泳道图（一次控制面请求的完整往返）

```mermaid
sequenceDiagram
    participant B as 用户 / 浏览器
    box sfc-core（唯一签发方 / 网关，与浏览器同源）
    participant A as 认证 · 鉴权
    participant P as 代理层（现签令牌）
    participant I as /api/internal 链
    end
    participant N as Nacos（不参与鉴权）
    participant S as 微服务（唯一校验方）

    B->>A: ① POST /api/ext/{svc}/task/create ＋ 会话 JWT（Token header / cookie）
    Note over A: ② JwtAuthenticationFilter：验签 ＋ TokenService(Redis) 白名单<br/>→ SecurityContext(UserPrincipal)
    Note over A: ③ 登录态 / 保护态校验（anyRequest().authenticated() · ProtectBlocker）<br/>代理路径不校验 uid 参数 —— 越权防线不在参数上，而在「uid 只存在于令牌里」
    A-->>B: 未通过 → 403 拒绝访问
    B->>P: ④ 命中 ServiceProxyController（/api/ext/** 通配，无 {} 占位符）
    P->>N: ⑤ getAllInstances(svc, SFC)
    N-->>P: ⑥ 健康实例 [host:port]（空 → 404；全不可用 → 503）
    Note over P: ⑦ 现签委托 JWT（RS256 / 自持密钥）<br/>iss / aud=svc / sub=uid / scope=internal.* / exp ≤ 60s / jti<br/>uid 只来自 SecurityContext · 每请求现签
    P->>S: ⑧ POST http://{host}:{port}/api/task/create<br/>Authorization: Bearer 委托JWT<br/>剥离 hop-by-hop 头 · 重写 Host / X-Forwarded-* · 请求体流式
    Note over S: ⑨ 公钥取自 /api/service-auth/jwks（本地缓存；未知 kid 必须回源一次）<br/>校验 iss · aud == 自己 · exp · scope<br/>无令牌 / 不通过 → 401，scope 不足 → 403（fail-closed）
    Note over S: ⑩ 取 uid ← sub（不读参数 uid）→ 执行业务逻辑
    S->>I: ⑪ 透传同一枚委托 JWT（不签发、不改写、不在签名链上）
    Note over I: ⑫ 自持验签（jjwt，同一把密钥）→ 按 sub 装载 UserPrincipal<br/>方法签名里没有 uid 参数 → @UID / @RequireScope 原样生效
    I-->>S: ⑬ 业务数据（流式）
    S-->>P: ⑭ 业务响应（流式，禁止 byte[] 全量缓冲）
    P-->>B: ⑮ 流式回写浏览器 ＋ 状态码
```

**阶段对应**：④⑤⑥⑧⑨⑩ = 阶段 8（控制面代理 ＋ 令牌签发）；⑪⑫⑬ = 阶段 6（档位 2 远程核心能力）；数据面旁路 = 阶段 9；⑤（拓扑图中那条匿名取公钥的边）= 阶段 5。

---

## ③ 关键性质（可据此自检）

1. **签发能力与校验能力不对称** —— 由算法（RS256 / JWKS）保证，**不由约定保证**。微服务永不持有密钥材料，因此"微服务被攻破 ≠ core 被攻破"真正成立（而不是换一把对称密钥继续共享）。
2. **全局唯一签发方是 core**。微服务只验签、只读 `sub`，不在签名链上 —— 不存在"服务身份令牌"这一类，也不存在共享对称密钥。
3. 收敛为一个令牌概念 · **一把自持密钥** · 一个校验规则（JWKS）· 一个授权模型（`@RequireScope` 机制 ＋ 独立的 `internal.*` 作用域）。
4. **无令牌一律 401（fail-closed）** —— 不把"微服务内网不可达"当作安全前提。
5. 数据面（阶段 9）不是第二套机制：它只是**同一枚委托令牌换了个载体**（query 而非 header），并额外做资源绑定与更长的有效期（5 分钟，非"一次性"）。
6. ⚠️ **`aud` 在两个校验方的规则不同**：微服务侧是 `aud == 自己`（图内 ⑨）；而 core 的 `/api/internal/**` 侧是 `aud ∈ 已注册服务名`（图内 ⑫）——令牌要被**透传回 core**，写成 `== sfc-core` 会让阶段 6 的链路直接断掉。详见[服务鉴权机制设计 · 决策 5](../microservice-service-auth-design.md)。
7. **与 OIDC/OAuth 零耦合**：自持密钥 ＋ 自建匿名端点 ＋ 独立作用域 ＋ 无 OIDC 条件注解。**验收方式**：`sys.oidc.enabled=false` 时，本图整条链路必须照常工作。
8. ⚠️ **`/api/internal/**` 的过滤器链必须始终注册**：`sys.service.auth.enabled=false` 或缺密钥时**一律 401**，不能让它落到主链（否则任何登录用户都能访问内部 API）。
