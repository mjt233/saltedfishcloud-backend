# 微服务改造 · 8.1 网关预研实测报告（spike）

> **状态**：✅ 全部 8 项实测完成，**结论：方案成立**（WebSocket 透传这一"最高不确定性"项通过）
> **日期**：2026-10-09
> **修订**：2026-10-11 —— **§3.9 的凭证处置结论已被 8.8 反转**（由"三入口全部剥离"改为"原值透传"），该节的表格保留为历史记录，并已就地标注；新增的取证见[网关独立化设计 §5.8.8](microservice-gateway-design.md) 与 [`verify/token-passthrough.ps1`](../../../microservices/sfc-gateway/verify/)
> **被测对象**：`microservices/sfc-gateway`（Spring Cloud Gateway 5.0.0 · Server WebFlux）
> **配套**：[网关独立化设计](microservice-gateway-design.md) · [路线图阶段 8](microservice-roadmap.md) · [档位 3 设计](microservice-phase3-frontend-integration.md)
> **复现脚本**：[`microservices/sfc-gateway/verify/`](../../../microservices/sfc-gateway/verify/)

---

## 一、结论速览

| # | 实测项 | 结论 | 关键证据 |
|---|---|---|---|
| 1 | `/api/**` 完整透传（状态码 / `Set-Cookie` / 重定向 / Cookie 保真） | ✅ 通过 | 13 个用例经网关与直连 **状态码 / 字节数 / SHA-256 全一致**；登录 `Set-Cookie` 原样透传 |
| 2 | **WebSocket 透传** | ✅ **通过（最高风险项解除）** | 双向帧级验证：服务端主动推 `hello` ＋ 客户端发消息收 `echo`；core `/api/ws` 握手保持 |
| 3 | `lb://` ＋ Nacos 双实例轮询 | ✅ 通过 | 6 次请求严格交替 `8091 / 8090` |
| 4 | 流式（1GB 响应 / 8MB 上传） | ✅ 通过 | 1GB 在网关 `-Xmx512m` 下 200、内存稳定 290~320MB；8MB 上传 SHA-256 一致 |
| 5 | `Retry` 语义（含陈旧连接自愈） | ✅ 通过 | 一死一活：GET 10/10 成功（换实例重试）· POST 3/6 失败（**不重试**，符合契约） |
| 6 | 请求体上限（含 chunked） | ⚠️ **官方 filter 不够** ⇒ 自建拦截曾实测通过，**但最终决策：网关不管控**（filter 已删除） | 官方 `RequestSize` 对 chunked **放行 65MB**；自建 `ServerHttpRequestDecorator` 增量拦截可使其变 **413 / 7003（0.09s）**——但该 filter 现已移除，见 §3.6 的"后续决策" |
| 7 | 原始路径保真（`%2e%2e` / `%2f` / `%20`） | ⚠️ **发现网关会折叠 `//`，已用"只拒绝"兜住** | 见 §三.7 |
| 8 | 大文件经网关的吞吐对照 | ✅ 已量化 | 1GB：直连 533~566 MB/s → 经网关 **296~338 MB/s**（约 **-42%**） |

**一句话结论**：官方库确实只覆盖"传输机制"——WS、流式、负载均衡、重试开箱可用；而**路径规范化、错误码映射、凭证处置、身份头覆盖**这四件事**一件都没代劳**，全部按设计决策 5 自己实现；请求体上限则是另一种情况：**官方不代劳，而最终决策是"我们也不做"**（见 §3.6 与 §四）。

---

## 二、测试环境与方法

| 项 | 取值 |
|---|---|
| JDK | 25.0.2（Oracle） |
| 网关 | Boot **4.0.8** · Spring Cloud **2025.1.0** · SCG **5.0.0**（Server WebFlux）· SCA **2025.1.0.0** · 启动参数 `-Xmx512m` |
| 宿主 `sfc-core` | Boot **4.1.0**（预研时的宿主版本；2026-10 已对齐到 **4.0.8**），8087，`develop` profile（SQLite、Redis 关闭、`registry-provider: nacos`） |
| 微服务 | `sfc-demo-service` 8080… 实为 **8090** 与 **8091** 两个实例，同一服务名注册到 Nacos（group `SFC`） |
| 注册中心 | Nacos 3.x（docker 容器 `nacos-standalone`，8848/9848；控制台 8080） |
| 客户端 | `curl` 8.13 · Node 24 内建 `WebSocket`（无需安装依赖） |
| 网关端口 | **8081**（设计文档原写 8080，与本机 Nacos 控制台冲突，已改） |

**方法**：先用一条**临时直连路由**（`Path=/api/demo/**` → `lb://sfc-demo-service`，绕过 resolve）把官方库的真实语义逐条测出来；正式代理分支落地后，**再用同一条契约复测关键项**（§三.5、§三.6、§三.9 中的"经代理分支"数据即此）。预研路由在 8.4 落地时已删除。

---

## 三、逐项取证

### 3.1 `/api/**` 完整透传

`verify/redline-15-core-regression.ps1`：对 13 个用例分别取"状态码 / `Content-Type` / 字节数 / 响应体 SHA-256"，经网关（8081）与直连（8087）逐项比对。

```
用例                     路径                                            网关         直连         一致
首页（SPA index.html）   /                                               200 1204B    200 1204B    ✓
SPA 主 bundle            /assets/index-BywmENye.js                       200 7529743B 200 7529743B ✓
SPA 样式                 /assets/index-CIFkUeNq.css                      200 1131526B 200 1131526B ✓
插件前端资源（/ext/**）  /ext/sfc-ext-ai/index.umd.js                    200 51923B   200 51923B   ✓
OAuth 授权页             /oauth/index.html                               200 422B     200 422B     ✓
站点 logo                /logo.png                                       403 104B     403 104B     ✓
favicon                  /favicon.ico                                    200 4286B    200 4286B    ✓
扩展清单占位文件         /static-extension.json                          200 2B       200 2B       ✓
core 业务 API（管理员）  /api/admin/sys/service/list                     200 1028B    200 1028B    ✓
core 业务 API（未登录）  /api/admin/sys/service/list                     403 104B     403 104B     ✓
匿名 API                 /api/hello/feature                              200 1246B    200 1246B    ✓
OIDC JWKS                /oauth2/jwks                                    200 429B     200 429B     ✓
OIDC 授权端点（未登录）  /oauth2/authorize?response_type=code&…          403 104B     403 104B     ✓
```

- **登录**：`POST /api/user/token` 经网关 200 且能解析出 token；`?getCookie=1` 的 `Set-Cookie: token=…; Path=/; HttpOnly; Secure; SameSite=Lax` **原样透传**。
- **多入口会话**：`Token` 头 / `token` Cookie / `?Token=` 三种入口经网关均可建立会话（§三.9 当时验证的是"后两种在代理分支上被剥离"——⚠️ **该行为已被 8.8 反转为"原值透传"**）。
- **`Host` / `X-Forwarded-*`**：下游看到的 `Host` 是**实例地址**（由反应式 HTTP 客户端按目标 URL 写入）；`X-Forwarded-*` 由网关覆盖式写入（见 §三.9）。
- **OIDC discovery 随入口自适应**：经网关 `issuer=http://127.0.0.1:8081`，直连 `issuer=http://127.0.0.1:8087`。这是正确行为——浏览器只访问网关时，授权端点/`jwks_uri`/`userinfo` 必须都指向网关。core 侧**无需**改 `server.forward-headers-strategy`（其 `server.tomcat.remoteip.remote-ip-header: X-Real-IP` 已启用容器级转发头处理，网关也覆盖写入了 `X-Real-IP`）。

### 3.2 WebSocket 透传 ✅（最高不确定性项）

| 场景 | 目标 | 结果 |
|---|---|---|
| 直连微服务 | `ws://127.0.0.1:8090/api/demo/ws` | ✅ `hello:port=8090` ＋ `echo:ping-direct` |
| **经网关（`lb://`）** | `ws://127.0.0.1:8081/api/demo/ws` | ✅ `hello` ＋ `echo:ping-via-gateway`（**双向帧都过**） |
| 直连 core | `ws://127.0.0.1:8087/api/ws?Token=…` | ✅ 握手成功并保持连接 |
| **经网关（core 透传链路）** | `ws://127.0.0.1:8081/api/ws?Token=…` | ✅ 握手成功并保持连接 |
| 经网关未带凭证 | 同上 | ✅ 被拒（关闭码 1002） |
| **经代理分支（`/api/ext/**`）＋ 身份注入** | `ws://127.0.0.1:8081/api/ext/sfc-demo-service/demo/ws?Token=…` | ✅ `hello:…:uid=1272361495027515392` ＋ `echo:ping-proxy:uid=1272361495027515392` |
| 经代理分支未登录 / 服务不存在 | `…/api/ext/**` | ✅ 握手均被拒（fail-closed） |

**两条重要结论**：

1. **不需要为 WS 单独配 `ws://` / `lb:ws://` 路由**：路由 URI 为 `http://`（或 `lb://`）时，SCG 依据 `Upgrade: websocket` 自动完成升级转发（设计决策 5 第 2 项里"先试 `http://` 是否自动升级"的疑问，实测答案为"是"）。
2. 代理分支上的 WS 同样能拿到网关注入的 `X-Sfc-Uid`——`ServerWs`/`HandshakeInterceptor` 场景的身份传递成立（对 demo 的 Spring `WebSocketConfigurer` 用握手拦截器读取；注意 **Spring 的 `ServerEndpointExporter` 不会使用注解上声明的 `jakarta` configurator**，用它读不到请求头，这一点在实现探针时踩过）。

**未实测的 3 个 WS 端点**：`/api/ai/wschat`（`sfc-ext-ai`，本次明确不碰且 dev 未启用）、`/api/webrtc/ws/{peerId}`、`/api/webshell/{sessionId}`（两个插件在 dev 的 `plugin.extra-resource` 中默认关闭）。它们与 `/api/ws` 走**同一条路由、同一套升级机制**，且目标同为 Tomcat；机制风险已由上述两组证据关闭，剩余的是"某个插件端点自身是否可用"，不属于网关范畴。

### 3.3 `lb://` ＋ Nacos 双实例轮询 ✅

```
req1..req6 -> port = 8091, 8090, 8091, 8090, 8091, 8090     # 严格交替
```

经**代理分支**复测同样交替（`instancePort` 在响应体里可辨）。

### 3.4 流式：1GB 响应 ＋ 8MB 上传 ✅

| 场景 | 结果 |
|---|---|
| 1GB 响应（直连路由，`-Xmx512m`） | `http=200 bytes=1073741824 time=3.59s`，采样 WorkingSet **306~321MB**，进程存活 |
| 1GB 响应（**代理分支**，`-Xmx512m`） | `http=200 bytes=1073741824 time=3.51s`，采样 **290~303MB**，进程存活 |
| 8MB 上传（Content-Length） | 直连/网关：`receivedBytes=8388608`，SHA-256 与本地一致 |
| 8MB 上传（**代理分支**） | `receivedBytes=8388608`，SHA-256 一致，且 `uid` 为真实用户 |

**内存与响应体大小无关** ⇒ Netty 流式管道是默认行为，无需额外配置。

### 3.5 `Retry` 语义与陈旧连接 ✅

杀掉落在一个实例（另一实例存活，且**当天注册中心尚未摘除死实例**的窗口内）：

| 方法 | 结果 | 解释 |
|---|---|---|
| `GET` × 10 | **10/10 成功** | `Retry methods=GET,HEAD` 换实例重试生效 |
| `POST`（带 4KB 请求体）× 6 | 3× `200` ＋ 3× **`503 / 7004`** | **写请求不重试**（契约要求），且失败被正确映射为 503/7004 |

**陈旧连接（决策 5 第 10 条的实测答案）**：

```
right after warmup: established 8081->8090 = 1     # 池中 1 条连接
after 75s idle     : established 8081->8090 = 0     # 对端（Tomcat keep-alive 超时）已关闭
first POST after idle -> 200                        # 不可重试的写请求也照样成功
```

⇒ 池中"被对端回收的连接"会被识别并丢弃，**不需要**（也无法）关闭客户端库的连接层自愈。**不要为了"防重放"去关它**——真正防重放的是"带体不重试"。

### 3.6 请求体上限 ⚠️ 官方 filter 不够（**最终决策：网关不做**）

| 路径 | 官方 `RequestSize`（临时路由实测） | 自定义增量拦截（当时的实现，**现已删除**） |
|---|---|---|
| 带 `Content-Length` 的 65MB | **413**（0.10s，未读体） | **413 / 7003**（0.11s） |
| **chunked 65MB** | **200 —— 完整转发 68157440 字节到下游** ❌ | **413 / 7003**（0.09s）✅ |

**根因**：官方 `RequestSize` 只比对声明的 `Content-Length`；chunked 请求没有该头，于是整段被放行——而"大请求打满入口"恰恰最可能用 chunked。
**当时的处置**：`ProxyRequestBodyLimitFilter` —— `Content-Length` 走快路径直接拒，chunked 用 `ServerHttpRequestDecorator` 包一层体流**边流边计数**，超限立即归还缓冲并中断（**不缓冲整体**）。该 filter **只作用于 `/api/ext/**`**，以免截断 core 的 8GB multipart 上传。

> **⚠️ 后续决策（现行方案）：整套拦截连同 `ProxyRequestBodyLimitFilter` 一起删除——网关不再管控请求体大小。**
>
> 理由：网关的定位是**流式通道**，不该在唯一入口上维护一套与业务无关的体量策略；"多大算大"应由承载业务的服务自己决定（core 的 multipart 上限是 8GB）。技术可行（本轮已实现并取证）不等于职责归属正确。
>
> 本节的两组实测数字**仍然有效且必须保留**：它们记录了"官方 `RequestSize` 不拦 chunked"这一事实——**将来谁再想在网关侧做体量限制，会踩到同一个坑**。
>
> 决策与职责划分见[网关独立化设计 · 决策 5 第 8 条](microservice-gateway-design.md)；要挡打满入口的大请求，请用专业边缘（nginx `client_max_body_size` / Ingress / WAF）。
>
> **删除后的复测（同一轮）**：65 / 128 / 256MB 在 `Content-Length` 与 chunked 两种传输方式下均**完整到达下游**（字节数与 SHA-256 一致）；但 **≥512MB 在 `-Xmx512m` 下必失败**（`OutOfDirectMemoryError: used 512MB == max` → 500/0），客户端**限速 50MB/s 也失败** ⇒ 请求体方向的**内存占用与体量同阶**，是流式管道的既有边界（非本次删除引入，但删除后才可达）。⏸ 本轮暂不处理，记账见[设计文档 风险 12](microservice-gateway-design.md)。

### 3.7 原始路径保真：**网关会折叠 `//`** ⚠️

`curl --path-as-is` 逐条比对（直连 vs 经网关）：

| 构造 | 直连 | 经网关 | 观察 |
|---|---|---|---|
| `/api/demo//echo` | 404 | **200**，下游 `requestUri=/api/demo/echo` | ⚠️ **网关把重复斜杠折叠了** |
| `/api/demo/./echo` | 404 | 404 | 一致 |
| `/api/demo/../echo` | 404 | 404（SCG 无路由匹配） | 一致 |
| `/api/demo/%2e%2e/echo` | 404 | 404 | 一致 |
| `/api/demo/a%20b/echo` | 404 | 404 | 一致 |
| `/api/demo/a%2Fb/echo` | 400 | 400 | 一致（容器拒编码斜杠） |
| `/api/demo/%00echo` · `a%5Cb` | 400 | 400 | 一致 |
| `?x=%2e%2e&y=a%2Fb&z=a%20b` | — | — | **查询串逐字节保留**（含大小写与百分号写法） |

**处置**：路径规则从"拒绝点段、编码斜杠…"**扩展为同时拒绝 `//`**（400 / 7000）。理由是硬规则"判定与转发必须用同一个字符串"：既然网关会把 `//` 折叠，那么只要**放行**它就必然出现"判定用 A、转发用 B"；**拒绝**它，不变式就重新成为结构性事实。这条规则已落地（8.8 起实现点为网关的 `ProxyPolicyService.validateRawPath`，规则与原 core 版本逐条一致）。
**另一个边界**：超长路径（>4096）在进入任何网关逻辑之前就被 Netty 以 **414** 拒绝（不在 7000 契约内，属 HTTP 传输层硬限制，已在文档记录）。

### 3.8 吞吐对照（代价 1 的量化）

同一 1GB 响应体，各跑两次：

| 路径 | 耗时 | 吞吐 |
|---|---|---|
| 直连微服务 | 1.90s / 1.92s | **533 / 566 MB/s** |
| 经网关（直连路由） | 3.46s / 3.24s | 310 / 331 MB/s |
| 经网关（代理分支） | 3.17s / 3.20s | 338 / 336 MB/s |

⇒ 多一跳在本机环回上带来约 **-40%** 的吞吐（1GB 多约 1.3 秒）。**这是接受的代价**（设计 §2 代价 1）；真实环境受磁盘/网络带宽约束时差距会缩小。必要时由暂缓的阶段 9（数据面旁路）处理。

### 3.9 代理分支的信任边界与头部治理（8.5 的关键验收）

> ⚠️ **本节是 2026-10-09 的历史取证，其中"凭证三入口"一条已被 8.8 反转**：当时刻意剥离，现在刻意透传（理由见[设计决策 3.3](microservice-gateway-design.md)）。**表中其余结论（身份头剥离、`X-Forwarded-*` 覆盖、hop-by-hop 剥离）至今有效**；凭证透传的新取证见 §3.10。

一条请求同时带齐"伪造材料"：

```bash
curl -H "Token: <真实 token>" -H "Cookie: token=leaked-session; other=1" \
     -H "X-Sfc-Uid: 999" -H "X-Forwarded-For: 1.2.3.4" -H "X-Forwarded-Proto: https" -H "TE: trailers" \
     "http://127.0.0.1:8081/api/ext/sfc-demo-service/demo/echo?Token=leaked-in-query&keep=1"
```

微服务实际观测到（**2026-10-09 版本**）：

```json
{"requestUri":"/api/demo/echo","queryString":"keep=1","uid":"1272361495027515392",
 "observedHeaders":{"host":"192.168.5.105:8091","x-sfc-uid":"1272361495027515392",
   "x-forwarded-for":"127.0.0.1","x-forwarded-host":"127.0.0.1:8081","x-forwarded-proto":"http",
   "token":null,"te":null,"connection":null,"transfer-encoding":null},
 "cookieNames":["other"]}
```

| 规则 | 结果 |
|---|---|
| 伪造 `X-Sfc-Uid: 999` | ✅ 被剥离，微服务只看到真实 uid（红线 2）· **至今有效** |
| `?uid=999` | ✅ 无效（uid 不是参数）· **至今有效** |
| ~~`Token` 头 / `token` Cookie / `?Token=`~~ | ⛔ **已反转（8.8）**：当时"三者全部不出现在微服务侧"；现在是**三者原值透传**（同一 `echo` 端点会看到 `"token":"<原值>"`、`queryString` 里保留 `Token=…`、`cookieNames` 含 `token`） |
| 伪造 `X-Forwarded-For` / `-Proto` | ✅ 被覆盖为网关观测值（`127.0.0.1` / `http`），`X-Forwarded-Host` 为浏览器原始 Host · **至今有效** |
| hop-by-hop（`TE` / `Connection` / `Transfer-Encoding`） | ✅ 未出现在下游 · **至今有效** |

### 3.10 8.8 独立验签与凭证透传（2026-10-11 取证）

| # | 用例 | 结果 |
|---|---|---|
| 1 | 无凭证 / 签名被篡改（末位改动）/ 自造的已过期凭证 | ✅ 全部 **403 / 7005** |
| 2 | 合法凭证 → 下游 `uid` ＝ 真实登录用户 | ✅ |
| 3 | `Token` 头 / `token` Cookie / `?Token=` **分别单独**使用 | ✅ 三者都能通过鉴权（证明网关确实从每个入口取值，顺序同 core） |
| 4 | `Token` 头与 `?Token=` 在下游的原值 | ✅ **逐字节一致**（`observedHeaders.token` 与 `queryString`） |
| 5 | `token` 与其余 Cookie 在下游 | ✅ 均在（**只验"仍在"，不回声 Cookie 值**——回声凭证会违反"微服务不得打印凭证"这条部署约束） |
| 6 | 密钥缺失时启动 | ✅ **启动失败**，日志点名 `SFC_JWT_SECRET` |
| 7 | 路径矩阵（`//`、`%2f`、`%2e%2e`、`..`、`%20`） | ✅ 全部 **400 / 7000**；⚠️ 非法百分号转义（`%2` / `%zz` / `%`）在 **Netty 层**即被拒（空体 400，见[设计 风险 17](microservice-gateway-design.md)） |
| 8 | 目标 `sfc-core` / `sfc-gateway` / 不存在的服务 | ✅ 全部 **404 / 7001** |
| 9 | 两实例轮询 → 强杀其一 → 两实例全消失 | ✅ 5/5 交替；强杀后 10/10 成功；全消失后 **0.1s** 即 404/7001（无缓存窗口） |
| 10 | WebSocket 经代理分支：带 `?Token=` / 不带 | ✅ 带凭证握手成功且帧内 uid 正确；不带凭证被拒（fail-closed） |
| 11 | core 透传链路零回归（13 用例） | ✅ 状态码 / 字节数 / SHA-256 全一致，登录与 `Set-Cookie` 正常 |

> 可复现：`pwsh -File microservices/sfc-gateway/verify/token-passthrough.ps1`（23 项断言，退出码 = 失败数）。

---

## 四、预研带来的三处设计修订

| # | 原计划 | 实测后的修订 | 影响面 |
|---|---|---|---|
| 1 | 网关路由只列 `/`、`/api/**`、静态与 `/api/ext/**` | **必须补 `/oauth2/**`、`/connect/**`、`/.well-known/**`**：漏掉时同一请求"直连 403、经网关 404"，OIDC 授权码/设备码/discovery/退出端点会整体 404 | 路由声明（已落地） |
| 2 | 请求体上限用官方 `RequestSize` filter | **第一轮**：改为自定义增量拦截（官方不拦 chunked），只作用于 `/api/ext/**`；**第二轮（现行）**：整个移除 —— **网关不管控请求体大小**（§3.6 后续决策） | 第一轮新增 `ProxyRequestBodyLimitFilter`；第二轮**删除该 filter 与 `sfc.gateway.max-request-size` 配置项** |
| 3 | 路径校验"拒绝点段与编码斜杠" | **追加"拒绝重复斜杠"**：网关会折叠 `//`，不拒绝就破坏"判定＝转发同一字符串" | 路径规则（8.8 起实现点为网关 `ProxyPolicyService`） |

另有两处实现层面的"实测才知道"：

- **Spring Framework 7 的 `HttpHeaders` 不再实现 `Map`**（`keySet()` → `headerNames()`），且 **`ServerHttpRequest.Builder` 移除了 `body(...)`**（改体流要用 `ServerHttpRequestDecorator`）；Boot 4 的自动配置提供的是 **Jackson 3 的 `JsonMapper`**（不是 Jackson 2 的 `ObjectMapper`）。
- **SCG 自己有一个 `GatewayProperties` bean**：网关侧自定义属性类若同名，启动即抛 `BeanDefinitionOverrideException`（已改名 `SfcGatewayProperties`）。
- JDK 23+ **不再默认启用注解处理**：不写 `<proc>full</proc>`，Lombok 会静默失效（报错是"找不到符号 getXxx()"）。网关工程不继承 root pom，因此自己配了一遍。

---

## 五、未实测项（如实记录）

| 项 | 原因 | 何时能闭 |
|---|---|---|
| `/api/ai/wschat`、`/api/webrtc/ws/*`、`/api/webshell/*` 三个 WS 端点 | 所在插件在 dev 未启用；`sfc-ext-ai` 本次明确不碰 | 阶段 7 或需要时启用对应插件后复测（机制已由 §3.2 关闭） |
| 浏览器内的点击级回归（登录/上传下载/播放/通知/插件页） | 本仓库无浏览器驱动，前端也未带 Playwright/Puppeteer | **由用户按清单在浏览器确认**（见 §六） |
| 超长路径（>4096）的契约化错误码 | Netty 在网关逻辑之前以 414 拒绝 | 保持现状（传输层硬限制），已在风险中记录 |
| 生产形态 TLS 终结 / 多副本部署 | 超出框架期范围 | 部署阶段 |

---

## 六、给下一步的输入

1. **入口切换（8.6）的浏览器确认清单**：打开 `http://127.0.0.1:8081/` → 登录（`admin / admin666`）→ 文件列表 → 上传/下载 → 播放 → WebSocket 通知（约 5s 内不应断连）→ 带插件的功能页（如视频增强）→ 若启用 OIDC，走一遍授权码流程。
   **已由脚本闭合的部分**：首页与全部静态资源字节一致、登录与 `Set-Cookie` 透传、`/api/**` 等价、`/api/ws` 握手、OIDC 端点与 discovery 走位。
2. **信任模式的部署约束**（必须在部署文档中重申）：微服务与 core 的内部接口**只允许内网 / 本机可达**；网关是唯一对外入口；**上公网前必须先完成服务间鉴权（阶段 5）**。
   8.8 补充两条：① 网关的验签密钥 `SFC_JWT_SECRET` 必须与 core 的 `sys.safe.token` 保持一致（漂移症状是"登录正常、`/api/ext/**` 全量 403/7005"）；② 凭证原值会透传到微服务，**微服务不得打印 `Token` 头 / `token` Cookie / `?Token=`**。
3. **预研路由已删除**：正式代理分支上线后，`Path=/api/demo/** → lb://sfc-demo-service` 这条"匿名直达微服务"的临时路由已移除（残留即为漏洞）。
