# verify —— sfc-gateway 验收脚本

> 这些脚本是[预研实测报告](../../../docs/plan/microservice/microservice-gateway-spike-report.md)与[设计验收红线](../../../docs/plan/microservice/microservice-gateway-design.md)的**可复现证据**：报告里的每个数字都能用它们重跑出来。

## 前置

```powershell
# 1) 注册中心
docker start nacos-standalone          # Nacos 3.x：控制台 8080、服务端 API 8848、gRPC 9848

# 2) 宿主（SQLite、无 Redis 的 develop profile）
java -jar sfc-core/target/sfc-core-3.1.2.jar

# 3) 微服务（起两个实例以验证轮询与故障转移）
java -jar microservices/sfc-demo-service/target/sfc-demo-service-1.0.0.jar
java -jar microservices/sfc-demo-service/target/sfc-demo-service-1.0.0.jar --server.port=8091

# 4) 网关：⚠️ 必须先注入验签密钥（＝ core 的 sys.safe.token），否则启动失败
#    取法见 sfc-gateway/README.md §二；下面这条用管理员接口取（需先登录）
$token = ((Invoke-WebRequest "$CoreBase/api/user/token" -Method POST -Body @{user='admin';passwd='admin666'}).Content | ConvertFrom-Json).data
$env:SFC_JWT_SECRET = ((Invoke-WebRequest "$CoreBase/api/admin/sys/config/config/sys.safe.token" -Headers @{Token=$token}).Content | ConvertFrom-Json).data

java -Xmx512m -jar microservices/sfc-gateway/target/sfc-gateway-1.0.0.jar
```

> ⚠️ **已知边界（本轮暂不处理）**：网关不管控请求体大小，但**请求体方向的内存占用与体量同阶**。
> `-Xmx512m`（`MaxDirectMemorySize` 默认同值）下实测：65/128/256MB 完整通过，**≥512MB 必失败**
> （`OutOfDirectMemoryError` → 500/0），客户端限速 50MB/s 也一样。跑大体积档位请加
> `-XX:MaxDirectMemorySize=2g`；详见[设计文档 风险 12](../../../docs/plan/microservice/microservice-gateway-design.md)。

## 脚本

### `token-passthrough.ps1`

**8.8 的验收主脚本**（本地验签 ＋ 凭证原值透传）：无凭证 / 签名被篡改 / 已过期 → `403/7005`；三个入口（`Token` 头 / `token` Cookie / `?Token=`）**分别单独**使用都能通过；`Token` 头与 `?Token=` 在下游**逐字节一致**；伪造 `X-Sfc-Uid` 被覆盖；路径矩阵（`//`、`%2f`、`%2e%2e`、`..`、`%20`）→ `400/7000`；目标 `sfc-core` / `sfc-gateway` / 不存在的服务 → `404/7001`。

```powershell
pwsh -File verify/token-passthrough.ps1      # 退出码 = 失败用例数（0 为全部通过）
```

> 脚本自己会登录 core 并用管理员接口读取 `sys.safe.token`（用于构造**已过期**凭证）；凭证值不会被打印。
> ⚠️ Cookie 用例只验证"仍到达下游"（`cookieNames` 含 `token`），**不回声其值**——回声凭证会违反"微服务不得打印凭证"这条部署约束。
> ⚠️ 非法百分号转义（`%2` / `%zz` / `%`）在 **Netty 解析请求行**时即被拒（空体 400），脚本按"400 且无响应体"断言，不与 7000 契约混淆。

### `redline-15-core-regression.ps1`

core 自身 API **零回归**：把同一批请求分别打到网关（8081）与直连 core（8087），逐项比对**状态码 / Content-Type / 字节数 / 响应体 SHA-256**；另验登录与 `Set-Cookie` 透传。

```powershell
pwsh -File verify/redline-15-core-regression.ps1      # 退出码 = 不一致的用例数（0 为全一致）
```

### `ws-probe.mjs`

WebSocket 探针（Node 24 内建 `WebSocket`，无需安装依赖）：

```powershell
node verify/ws-probe.mjs "ws://127.0.0.1:8081/api/ext/sfc-demo-service/demo/ws?Token=<token>" ping   # echo 模式：收齐 hello+echo 即成功
node verify/ws-probe.mjs "ws://127.0.0.1:8081/api/ws?Token=<token>" x --expect=open                  # open 模式：只要求握手成功并保持
```

| 退出码 | 含义 |
|---|---|
| 0 | 达标（echo 模式收齐 2 条 / open 模式撑过保持窗口） |
| 1 | 连接错误（含握手被拒：未登录、服务不存在等 fail-closed 场景） |
| 2 | 超时 |
| 3 | 未达标即被关闭 |

### `body-passthrough.ps1`

**网关不限请求体大小**的反向证据：大请求体在 `Content-Length` 与 **chunked** 两种传输方式下都必须**完整**到达下游。下游 `echo-body` 回传收到的字节数与 SHA-256，与源文件逐字节比对（截断会改长度、缓冲/重复会改摘要）。

```powershell
pwsh -File verify/body-passthrough.ps1                # 默认 65MB（回归档位）
pwsh -File verify/body-passthrough.ps1 -SizesMb 128,256
```

> 为什么两种传输方式都要测：官方 `RequestSize` **只拦 `Content-Length`，对 chunked 完全不拦**（预研报告 §3.6）。若将来有人重新引入"网关侧上限"，chunked 这条会最先暴露不一致。
> 退出码 = 失败项数（0 为全部完整通过）。

## 临时产物

`.tmp/`（大体积测试二进制）与 `.token`（会话令牌）**不入库**，已在仓库根 `.gitignore` 中忽略。
