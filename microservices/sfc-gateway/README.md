# sfc-gateway —— 咸鱼云独立网关

> **它是什么**：整站**唯一入口**。Spring Cloud Gateway 5.0（**Server WebFlux**）独立进程。
> **设计依据**：[`docs/plan/microservice/microservice-gateway-design.md`](../../docs/plan/microservice/microservice-gateway-design.md) · 实测报告：[`microservice-gateway-spike-report.md`](../../docs/plan/microservice/microservice-gateway-spike-report.md) · 债务清单：[`docs/plan/todo.md`](../../docs/plan/todo.md)

---

## 一、它做什么 / 不做什么

| 做 | 说明 |
|---|---|
| **透传** | `/`、`/assets/**`、`/ext/**`、`/static/**`、`/oauth*`、`/api/**`（含 WebSocket）、`/oauth2/**`、`/.well-known/**` → `sfc-core`。**完全透明**：不解析身份、不注入身份头、不改写业务语义 |
| **代理** | `/api/ext/**` → **本地验签**用户凭证（密钥来自环境变量）→ **本地判定**（路径 / 方法 / 服务名白名单 / 自我转发阻断 / `sfc.api.prefix`）→ 剥离入站身份头 → 注入 `X-Sfc-Uid` → **凭证原值透传** → 以 `lb://{serviceName}` 转发 |
| **头部治理** | 覆盖式 `X-Forwarded-*` / `X-Real-IP`、剥离入站一切 `X-Sfc-*`；**凭证三入口只读不剥离**（`Token` 头 / `token` Cookie / `?Token=` 原样透传给下游） |
| **自己的错误语义** | `400/7000` 路径方法非法 · `403/7005` 凭证缺失/验签失败/已过期 · `404/7001` 服务不存在或目标为自身 · `503/7004` 实例不可达 · `413/7003`（仅把**框架层抛出的** 413 归一化成契约形态） |

| 不做 | 原因 |
|---|---|
| 签发凭证 | 签发方只有 core（`JwtUtils` / `TokenService`）。网关只**验证**，不签发、不改写凭证 |
| 校验 Redis 会话白名单 | 网关只做"验签 ＋ 过期"；完整校验（含白名单）由拿到凭证原值的下游完成——**这是记账中的债务**（见[债务清单](../../docs/plan/todo.md) 第 3 条） |
| 感知密钥在线变更 | 只读启动时的环境变量，core 在线改密钥后必须重启网关并重新注入（债务第 2 条） |
| **管控请求体大小**（统一上限 / 计数拦截 / 截断） | 请求体多大由承载业务的服务自己决定（core 的 multipart 上限是 8GB）；网关不做计数、不做截断。挡大请求请用专业边缘（nginx `client_max_body_size` / Ingress / WAF）。⚠️ 已知边界：请求体方向的内存占用与体量同阶，`-Xmx512m` 下 ≥512MB 会失败（见[设计文档 风险 12](../../docs/plan/microservice/microservice-gateway-design.md)） |
| 被任何服务反向调用 | 它是入口。虽然注册到 Nacos（便于管理端看拓扑），但网关自己的判定会把目标为 `sfc-gateway` 的请求判 404（回环阻断） |

---

## 二、构建与运行

```powershell
# 构建（在仓库根目录执行；本工程不继承 root pom）
mvn -f microservices/sfc-gateway/pom.xml clean package -DskipTests

# ⚠️ 运行前必须注入验签密钥：取 core 的会话密钥（DB 配置项 sys.safe.token 的值）
#    · 取法一：管理端【系统设置 → 常规 → 系统安全密钥】
#    · 取法二：GET /api/admin/sys/config/config/sys.safe.token（带管理员 Token 头）
#    · 取法三：直接读 core 的配置表 config 中 item_key='sys.safe.token' 的 item_value
#    缺失或 UTF-8 长度 < 32 字节 ⇒ 网关【启动失败】（这是有意的 fail-fast）
$env:SFC_JWT_SECRET = '<core 的 sys.safe.token 值>'

# 运行（示例给 512MB 堆：足以证明 1GB 级下载是流式的）
java -Xmx512m -jar microservices/sfc-gateway/target/sfc-gateway-1.0.0.jar

# 健康检查
curl http://127.0.0.1:8081/actuator/health
```

**前置条件**：

1. Nacos 已启动且 `sfc-core`、目标微服务已注册（`group=SFC`、`namespace=public`）；
2. **已注入 `SFC_JWT_SECRET`**，且与 core 的 `sys.safe.token` **一致**（漂移的症状是"登录正常、`/api/ext/**` 全量 403/7005"）；
3. core **必须**注册到 Nacos：网关照 `lb://{sfc.gateway.core-service}` 解析实例地址（不支持固定 http 地址直连）。

**版本矩阵**（与 `sfc-demo-service` 同源；**刻意不对齐**宿主 Boot 4.1.0）：Boot **4.0.8** · Spring Cloud **2025.1.0** · SCG **5.0.0** · SCA **2025.1.0.0** · jjwt **0.12.6**（与 core 同版本，保证验签口径一致），Java 25。零咸鱼云依赖（验证：`mvn dependency:tree` 中无 `com.xiaotao` / `com.sfc.cloud`）。

---

## 三、配置（本工程自己的 `application.yml`，不使用 `sys.` 前缀）

| 键 | 默认 | 说明 |
|---|---|---|
| `server.port` | `8081` | ⚠️ 不是设计文档最初写的 8080：本机 Nacos 3.x 控制台占用了 8080 |
| `spring.application.name` | `sfc-gateway` | 也是"回环阻断"要匹配的名字（见 `sfc.gateway.self-service-name`） |
| `sfc.gateway.core-service` | `sfc-core` | core 在注册中心里的**服务名**（透传路由的目标 ＋ 自我转发阻断的目标） |
| `sfc.gateway.self-service-name` | `${spring.application.name}` | 只用于自我转发阻断（目标为网关自身 → 404/7001） |
| `sfc.gateway.proxy-route-id` | `micro-proxy` | 只有该路由走本地验签、身份注入与改道 |
| `sfc.gateway.default-api-prefix` | `/api` | 实例 metadata 未声明 `sfc.api.prefix` 时的缺省下游前缀（原在 core 的 `sys.service.gateway.default-api-prefix`） |
| `sfc.gateway.jwt-secret` | `${SFC_JWT_SECRET:}` | **验签密钥**：无默认值，缺失即启动失败。**不要**把它写进任何入库的配置文件 |
| — | — | **没有**请求体大小配置项：网关不管控请求体大小（见"不做"表）。曾有的 `sfc.gateway.max-request-size` 与 `ProxyRequestBodyLimitFilter` 已按决策删除 |
| ~~`sfc.gateway.resolve-path` / `resolve-*-timeout-ms`~~ | — | **已删除（8.8）**：网关不再调用 core 的 resolve 端点 |
| `spring.cloud.loadbalancer.cache.ttl` | `5s` | 实例列表缓存（Spring Cloud 默认 35s）。缓存窗口内已死实例仍可能被选中 ⇒ 不重试的写请求得到 `503/7004`，可重试的 `GET/HEAD` 换实例自愈；**判定侧的实例查询走 `DiscoveryClient`，实测两实例全消失后 0.1s 内即 404/7001** |

路由声明在 `spring.cloud.gateway.server.webflux.routes`（**Spring Cloud 2025 起 WebFlux 服务端的前缀从 `spring.cloud.gateway.*` 迁到了 `...server.webflux.*`**）。`order` 顺序即优先级：`core-manifest(1)` < `micro-proxy(2)` < `core-api(10)` < `core-static(11)` < `core-oidc(12)`。

---

## 四、⚠️ 部署约束（框架期信任模式，不可含糊）

1. 微服务与 core 的内部接口**只允许内网 / 本机可达**，**网关是唯一对外入口**；
2. 微服务只信任明文头 `X-Sfc-Uid`，**任何能直连微服务端口的人都能伪造任意用户**；
3. **密钥一致性靠部署保证**：`SFC_JWT_SECRET` ↔ core 的 `sys.safe.token`；改密钥后必须重启网关；
4. **凭证原值会透传到微服务**：微服务**不得**把 `Token` 头 / `token` Cookie / `?Token=` 写进日志、监控采样或响应体；
5. **上公网前必须先完成服务间鉴权**（[路线图阶段 5](../../docs/plan/microservice/microservice-roadmap.md)），届时把它换成**可验证**的载体与 fail-closed 校验。完整债务见 [`docs/plan/todo.md`](../../docs/plan/todo.md)。

---

## 五、代理分支的处理链（全在网关进程内，**不再调用 core**）

| 步骤 | 实现点 |
|---|---|
| ① 取凭证（`Token` 头 → `?Token=` → `token` Cookie，顺序与 core 一致） | [`TokenExtractor`](src/main/java/com/sfc/gateway/auth/TokenExtractor.java) |
| ② 本地验签（`Keys.hmacShaKeyFor` ＋ `Jwts.parser().verifyWith`，与 core 同 API 同版本） | [`GatewayTokenVerifier`](src/main/java/com/sfc/gateway/auth/GatewayTokenVerifier.java) |
| ③ 本地判定（方法 / 路径 / 服务名 / 自我转发阻断 / 实例列表 / `sfc.api.prefix`） | [`ProxyPolicyService`](src/main/java/com/sfc/gateway/service/ProxyPolicyService.java) |
| ④ 注入 `X-Sfc-Uid` ＋ 凭证原值透传 ＋ 改道 `lb://` | [`ProxyRequestFilter`](src/main/java/com/sfc/gateway/filter/ProxyRequestFilter.java)（order 10100） |
| ⑤ 入站 `X-Sfc-*` 无条件剥离（**所有路由**） | [`TrustHeaderSanitizeFilter`](src/main/java/com/sfc/gateway/filter/TrustHeaderSanitizeFilter.java)（order -99） |
| ⑥ `X-Forwarded-*` / `X-Real-IP` 覆盖 | [`XForwardedOverrideFilter`](src/main/java/com/sfc/gateway/filter/XForwardedOverrideFilter.java)（order -100） |
| ⑦ 转发阶段异常 → 契约形态错误 | [`GatewayErrorWebExceptionHandler`](src/main/java/com/sfc/gateway/handler/GatewayErrorWebExceptionHandler.java)（order -2） |

> **已删除**：`POST /api/gateway/resolve` 及其声明式客户端（`CoreResolveApi` / `GatewayResolveClient` / `CoreResolveClientConfig`）与 `Resolve*` 模型——网关与 core 之间**不再有代理相关的 HTTP 调用**。历史理由与改判记录见[网关独立化设计 · 决策 3](../../docs/plan/microservice/microservice-gateway-design.md)。

---

## 六、验收脚本

见 [`verify/`](verify/)：

| 脚本 | 覆盖 |
|---|---|
| `token-passthrough.ps1` | **8.8 验收主脚本**：本地验签拒绝矩阵（无凭证 / 篡改 / 过期）、三入口分别可用、**凭证原值透传**（头与查询串逐字节）、伪造身份头被覆盖、路径矩阵 400/7000、自我转发与不存在服务 404/7001（23 项断言，退出码 = 失败数） |
| `ws-probe.mjs` | WebSocket 握手与双向帧（代理分支与 core 透传链路） |
| `redline-15-core-regression.ps1` | core 自身 API 零回归：经网关与直连逐项等价对照 |
| `body-passthrough.ps1` | 请求体不限/不截断：`Content-Length` 与 chunked 两种传输方式的字节数与 SHA-256 比对 |
