# 微服务改造 · 委托令牌线协议（阶段 5 交付）

> **性质**：跨进程**线协议**——把"core 签发、微服务离线验签"这件事写成任何语言都能照着实现的契约。
> **状态**：已随阶段 5 代码落地（core 侧全量 ＋ `sfc-cloud-auth` 只验签瘦库 ＋ demo 微服务接入）。
> **配套**：[服务鉴权机制设计（定稿）](microservice-service-auth-design.md) · [路线图](microservice-roadmap.md) · [拓扑 / 泳道图](review/microservice-auth-topology.md)
> **创建日期**：2026-10-07

---

## 1. 一句话契约

**只有 core 能签发；只有被调方验签；分发给微服务的产物是公钥，不是密钥。**

| # | 性质 | 由什么保证 |
|---|---|---|
| 1 | 微服务**不持有任何密钥材料** | 它只拿 JWKS 里的公钥；公钥只能验签、不能签发 |
| 2 | "微服务被攻破 ≠ core 被攻破" | **算法**保证（RS256 非对称），不由约定保证 |
| 3 | 令牌不能被拿去用于别的服务 | `aud` 受众绑定，两侧规则不同（见 §5） |
| 4 | 不需要撤销列表、不需要内省调用 | TTL ≤ 60 秒 ＋ 每请求现签 |
| 5 | 篡改 uid 越权**没有入口** | uid 只存在于令牌 `sub`；`/api/internal/**` 方法签名里不出现 uid 参数 |

---

## 2. 端点

### 2.1 JWKS 公钥端点

| 项 | 值 |
|---|---|
| 路径 | `GET /api/service-auth/jwks` |
| 鉴权 | **匿名**（core 侧 `SecurityConfig` 的 permitAll 放行） |
| 响应 | RFC 7517 的 **JWK Set** 文档，**不套用** core 的 `{code,data,msg}` 包装 |
| 缓存建议 | 本地缓存公钥，按 `kid` 命中；正常路径**零网络调用** |

响应示例：

```json
{
    "keys": [
        {
            "kty": "RSA",
            "kid": "3uY1...（公钥 SHA-256 指纹的 base64url，无填充）",
            "use": "sig",
            "alg": "RS256",
            "n": "（模数，base64url 无填充）",
            "e": "AQAB"
        }
    ]
}
```

**两种非正常情形**：

| 情形 | 行为 | 为什么这样设计 |
|---|---|---|
| `sys.service.auth.enabled=false` | 返回空集合 `{"keys":[]}` | 功能关闭时端点仍可达，微服务得到的是"没有可用公钥"，而不是一个无法定位的 404 |
| `enabled=true` 但私钥缺失 | **HTTP 500 ＋ 明确消息** | 配置错误必须显式暴露。若静默返回空集合，微服务侧只会看到"验签失败"，无法定位到"core 侧没配密钥" |

> ⚠️ **core 侧没有复用 `/oauth2/jwks`**：那样会让微服务鉴权随 `sys.oidc.enabled` 一起失效，并让 OIDC 侧的密钥变更波及微服务鉴权。两个故障域被刻意分开——验收项之一是 `sys.oidc.enabled=false` 时本条链路**照常工作**。

### 2.2 受保护的内部 API

| 项 | 值 |
|---|---|
| 路径前缀 | `/api/internal/**` |
| 载体 | `Authorization: Bearer <委托令牌>` |
| 不接受的载体 | 任何自定义请求头（早期方案里的 `X-Sfc-Internal-Token` 已废弃） |

---

## 3. 令牌形态

### 3.1 JWT 头部

| 字段 | 值 | 说明 |
|---|---|---|
| `alg` | `RS256` | 固定 |
| `kid` | 公钥的 SHA-256 指纹（base64url 无填充） | **不可配置**，随密钥自动变化；默认必填 |

### 3.2 Claims

| Claim | 取值 | 校验方是否强制校验 |
|---|---|---|
| `iss` | `sfc-core` | ✅ |
| `aud` | 签发时即固定为"**目标微服务名**"；透传时**不改写** | ✅ **必须**，但两侧规则不同（§5） |
| `sub` | 用户 uid 的字符串形式 | ✅ |
| `scope` | 空格分隔，取值 `internal.read` / `internal.write` | ✅（受保护操作） |
| `exp` | 过期时间，控制面 TTL ≤ 60 秒 | ✅ |
| `iat` | 签发时间 | 建议校验 |
| `jti` | 随机串 | 仅用于日志关联，**不用于去重**（无状态设计） |
| `res` / `path` | 数据面（阶段 9）：资源标识 / 路径前缀绑定 | 数据面必需 |

> **刻意不含 `roles`**：core 侧按 `sub` 重新装载用户即得到**最新**角色；令牌里带角色会在用户被降权后继续生效。

### 3.3 有效期

| 载体 | TTL | core 侧配置项 |
|---|---|---|
| 控制面（header） | **≤ 60 秒** | `sys.service.auth.token-ttl-seconds`（core 侧强制约束在 `(0, 60]`，配大了直接启动失败） |
| 数据面（URL query，阶段 9） | 5 分钟 | `sys.service.auth.download-token-ttl-seconds` |

> **为什么不需要撤销列表**：TTL 压到 60 秒以内，且 core **每请求现签、不复用、不缓存**。"长时效令牌如何提前失效"这个需求就不存在了。

---

## 4. 微服务侧必须实现的六件事

| # | 要求 |
|---|---|
| 1 | 从 `Authorization: Bearer` 取令牌，**不做**自定义头 |
| 2 | 用 `GET /api/service-auth/jwks` **离线**验签（RS256）；本地缓存公钥，按 `kid` 命中 |
| 3 | **未知 `kid` 必须回源重取一次**再判定失败（见 §6，这是轮换能否平稳的支点） |
| 4 | **强制**校验 `aud == 自己的服务名`、`exp`、`iss`，以及受保护操作所需的 `scope` |
| 5 | `uid` **只能**来自令牌的 `sub`；业务代码里**不得**读取任何名为 `uid` 的请求参数 |
| 6 | **fail-closed**：除 `/static/**` 与健康检查外，**无令牌一律 401** |

> ⚠️ **第 6 条是硬约束，不是建议**。"微服务可内网不可达"是**目标**，不是**约束**——网络边界一旦被打破（同网段扫描、误配 Nginx、容器网络打通），fail-open 的微服务就等于"任意读"。
>
> 另外：**代理 Controller 禁止使用 core 侧的 `@AllowAnonymous`**。该注解的注册粒度是 controller 方法的 pattern values，代理 Controller 映射的是 `/api/ext/**`，一旦被标注，整个控制面代理会一起 permitAll。

---

## 5. ⚠️ 最容易写错的一处：`aud` 在两个校验方的规则不一样

| 校验方 | 规则 | 为什么 |
|---|---|---|
| **微服务** | `aud` **== 自己的服务名** | 这正是"受众绑定"要防的事：给 A 的令牌不能被 B 接受，阻断**微服务之间的横向重放** |
| **core 的 `/api/internal/**`** | `aud` **∈ 已注册服务名集合 ∪ `allowed-audiences` 白名单**（**不是** `== sfc-core`） | 令牌本就是 core 签发的，它用 `iss` 认领签发权即可；`aud` 在这里表达的是"这枚令牌被签发给链路上的哪个微服务" |

> ⚠️ **请勿把 core 侧这一条"顺手修正"成 `aud == sfc-core`**——那会让"core → 微服务 → core"的透传链路（阶段 6）当场断掉，且症状是"阶段 8 通了、阶段 6 一直 401"这种很难定位的形态。

> **开发验证时的一个坑**：`registry-provider: local` 时进程内注册表**只含 `sfc-core`**，因此面向微服务的令牌必须靠 `allowed-audiences` 白名单放行。这不是可选优化，而是 standalone 模式下能否验证的前提。

---

## 6. 密钥轮换契约

```
替换 core 配置里的密钥
    ⇒ 下一个新签发的令牌带新 kid
    ⇒ 微服务验签时发现「未知 kid」
    ⇒ 回源 /api/service-auth/jwks 一次 ⇒ 取到新公钥 ⇒ 通过
```

| 项 | 约定 |
|---|---|
| `kid` 来源 | **公钥 SHA-256 指纹，不可配置**。若开放配置，"换密钥却忘了改 `kid`"会让微服务认为"kid 未变、缓存仍有效"而不回源，直接验签失败 |
| 密钥数量 | **严格单密钥**（v1，不提供旧公钥位） |
| 集群 | **所有 core 节点必须配同一把密钥**，否则 LB 之后"A 节点签发、B 节点出 JWKS"会验签失败 |
| 强行回源 | 允许削弱为"合并窗口"（`sfc-cloud-auth` 取 1 秒），用于防止用随机 `kid` 构造对 JWKS 端点的请求放大；正常的轮换不会在 1 秒内发生两次 |

---

## 7. 失败语义（统一口径）

| 情形 | 响应 | 说明 |
|---|---|---|
| `sys.service.auth.enabled=false` | **401** | `/api/internal/**` 一律拒绝（**不留主链穿透的口子**） |
| 无令牌 | **401** | fail-closed；**不得**降级为匿名或"信任内网" |
| 验签失败 / 过期 / `iss` 不符 / **`aud` 不符** / 未知 `kid` | **401** | 对外统一为"凭证无效"，不区分具体原因 |
| 令牌有效但 `scope` 不足 | **403** | 与既有 `RequireScopeAspect` 抛 `AccessDeniedException` 的语义对齐 |
| 令牌存在但 core 侧密钥不可用 | **500 ＋ 明确消息** | 配置错误应当显式暴露，而不是静默 401 |
| 服务不存在（core 侧代理，阶段 8） | **404** | `serviceName` 未命中 `ServiceDiscovery` |
| 全部实例不可用（core 侧代理，阶段 8） | **503** | 而非 500 |

> ⚠️ **红线 6**：`/api/internal/**` 的过滤器链**必须始终注册**（不随 `enabled` 或密钥缺失而消失）。否则请求会落到主链的 `anyRequest().authenticated()`，变成"**任何登录用户都能访问内部 API**"——"降级"变成了"放开"。core 侧的实现是：链始终在，条件不满足就一律 401。
>
> 同理，core 侧的主链入口点是 **403**，而内部链**自带 401 入口点**——401"未认证"与 403"已认证但无权限"不能混用。

---

## 8. 接入方式

### 8.1 使用瘦库 `sfc-cloud-auth`（推荐）

微服务只需 **加一个依赖 ＋ 一段配置**：

```xml
<dependency>
    <groupId>com.xiaotao</groupId>
    <artifactId>sfc-cloud-auth</artifactId>
    <version>1.0.0</version>
</dependency>
```

```yaml
sfc:
    cloud:
        auth:
            enabled: true
            jwks-url: http://127.0.0.1:8087/api/service-auth/jwks
            service-name: sfc-demo-service
            issuer: sfc-core
```

端点侧：

```java
@GetMapping("whoami")
@RequireInternalToken("internal.read")
public Object whoami() {
    // uid 只能来自令牌的 sub
    return Map.of("uid", InternalTokenContext.requireCurrentUid());
}
```

瘦库的边界：

| 做 | 不做 |
|---|---|
| 拉取 / 缓存 JWKS，未知 `kid` 回源一次 | **不持有任何密钥材料**（只有公钥） |
| RS256 离线验签，强制校验 `iss` / `exp` / `aud == 自己` | 不签发、不解析用户会话 JWT |
| fail-closed 过滤器 ＋ 路径规范化 | **不引入 spring-security** |
| `sub` 提取与作用域校验（403） | 不解读任何业务语义 |

### 8.2 自己实现

照 §3 ~ §7 实现即可。**学习价值在线协议这一层，不在重复实现 `OncePerRequestFilter`。**
把校验交给各微服务自己实现的风险是：安全关键逻辑复制 N 份，就有 N 次"忘了校验 `aud` / `exp` / 未知 `kid` 回源"的机会，而这类缺陷**不会在功能测试里暴露**。

---

## 9. 配置项

### 9.1 core 侧（签发方）

```yaml
sys:
    service:
        auth:
            # 是否启用。注意语义：false 表示「关闭」，不是「放开」——/api/internal/** 仍注册链并一律 401
            enabled: false
            # 签名私钥（PKCS#8）。单行 base64(DER) 或多行 PEM 均可
            signing-key: ${SERVICE_AUTH_SIGNING_KEY:}
            # 控制面令牌 TTL（秒），强制约束在 (0, 60]
            token-ttl-seconds: 60
            # 数据面（下载）令牌 TTL（秒）
            download-token-ttl-seconds: 300
            # aud 追加白名单，与「已注册服务名集合」取并集
            allowed-audiences: []
```

### 9.2 微服务侧（校验方）

```yaml
sfc:
    cloud:
        auth:
            # 默认 true（fail-closed）
            enabled: true
            jwks-url: http://<core-host>:<port>/api/service-auth/jwks
            # aud 校验的基准：要求令牌 aud == 本值
            service-name: <本服务的服务名>
            issuer: sfc-core
            public-paths:
                - /static/**
                - /actuator/health
```

### 9.3 密钥生成

```bash
# 1) 生成 PKCS#8 私钥
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out sfc-service-auth.pem
# 2) 转成单行 base64(DER)，便于放进环境变量（Windows 下 base64 -w0 不可用，见下方替代方案）
openssl pkcs8 -topk8 -nocrypt -in sfc-service-auth.pem -outform DER | base64 -w0
```

Windows / 不想手搓时，直接运行 core 侧的 `ServiceAuthKeyGenerator`，它会打印可粘贴的单行 base64 与派生出的 `kid`。

> ⚠️ **私钥不得提交进仓库**。生产环境只通过环境变量 `SERVICE_AUTH_SIGNING_KEY` 注入，`application-product.yml` 里只留空占位符。集群所有 core 节点必须使用同一把密钥。

---

## 10. 验收清单

- [ ] `curl /api/service-auth/jwks` **匿名可达**，返回的公钥能被离线验签通过
- [ ] **`sys.oidc.enabled=false` 时全套功能正常**（独立性的直接检验）
- [ ] `aud` 不匹配 → 401；过期 → 401；`iss` 不符 → 401；无令牌 → 401；`scope` 不足 → 403
- [ ] `enabled=false` 时 `/api/internal/**` 返回 401（**不落到主链**；用一个已登录用户的会话令牌去请求，必须仍是 401）
- [ ] `enabled=true` ＋ 私钥缺失 ＋ `product` → **启动失败**；＋ `develop` → WARN，且 JWKS / 签发请求报明确错误
- [ ] 私钥格式非法 → **任何 profile 都启动失败**
- [ ] 替换密钥后：新令牌的 `kid` 变化，微服务**回源一次**后验签通过
- [ ] 令牌中不存在 `roles`（角色由 core 按 `sub` 实时装载）
- [ ] **core → 微服务 → core 的透传调用成功**（阶段 6 起）
