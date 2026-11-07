# sfc-auth — 内部接口（服务间调用）鉴权 SDK

> **定位**：`/api/internal/**` 这类**只允许微服务间调用**的接口，用"共享密钥 + 每请求自签 JWT"来证明调用方是集群内的服务。
> 它**不是**完整的服务间鉴权（无按服务分发密钥、无 `aud`/`scope`、无 JWK 轮换），完整方案见[微服务路线图阶段 5](../../docs/plan/microservice/microservice-roadmap.md)。

## 一、工作方式

```
部署侧配置（core 与每个微服务同一份）：SFC_INTERNAL_KEY   ← 配置项 sfc.internal.key，值=共享密钥，永不上线传输
调用：请求头 X-Sfc-Internal-Token: <每次调用新签发的 HS256 JWT>
服务端：/api/internal/** 校验「签名 + exp（含时钟偏移）+ 必需声明」→ 不符 403
网关：/api/internal/** 一律 403（不进路由链），并剥离客户端自带的 X-Sfc-Internal-Token
```

JWT 声明：`iss`=调用方服务名、`sub`=`sfc-internal`（与用户会话凭证区分）、`jti`=16 字节随机值（**仅作追踪标识**）、`iat`/`exp`（默认 5 分钟）。

## 二、配置项（前缀 `sfc.internal`）

| 配置项 | 环境变量 | 默认 | 说明 |
|---|---|---|---|
| `key` | `SFC_INTERNAL_KEY` | 空 | 共享密钥。**留空 = fail-closed**（服务端拒绝全部内部接口，客户端无法签发）；不足 32 字符 = 启动失败 |
| `service-name` | — | `spring.application.name` | 写入 JWT 的 `iss`，便于服务端审计/白名单 |
| `token-ttl` | — | `5m` | 凭证有效期（**无防重放**，这是当前唯一的暴露窗口缓解手段） |
| `clock-skew` | — | `30s` | 允许的时钟偏移 |
| `server-enabled` | — | `true` | 是否在本进程启用服务端校验过滤器（默认开启，避免"忘了配 = 门开着"） |
| `allowed-issuers` | — | 空 | 调用方服务名白名单；空 = 不校验 |

## 三、代码结构

| 包 / 类 | 角色 |
|---|---|
| `SfcInternalAuthConstants` | 契约常量的**唯一来源**（头名、路径前缀、JWT `sub`、统一拒绝响应体） |
| `SfcInternalAuthKey` | 密钥状态（含"未配置"的 fail-closed 表达与强度校验） |
| `SfcInternalTokenIssuer` / `SfcInternalTokenVerifier` | 签发 / 校验（校验不抛异常，以 `Failure` 表达原因） |
| `SfcInternalAuthFilter` | 服务端 Servlet 过滤器（`HIGHEST_PRECEDENCE`，拦在 Spring Security 之前） |
| `SfcInternalAuthAutoConfiguration` | 装配密钥/签发器/校验器 + 服务端过滤器（WebFlux 进程自动跳过） |
| `client.SfcInternalTokenFeignInterceptor` / `client.SfcInternalTokenRequestInitializer` | 客户端适配器。**不自动注册**：请只挂在面向 core 的客户端上（例如 `@FeignClient(configuration = ...)`），不要做成全局拦截器 |

## 四、已知局限（有意为之）

- **静态共享密钥**：不区分服务身份（持有密钥的任何进程都能以任意 `iss` 签名）；`allowed-issuers` 只是声明校验，不是身份认证；
- **不做防重放**（有意为之）：同一凭证在其有效期内可被重复使用（含被窃取后重放）。要防重放需要跨请求的一次性记录（全局去重表），那是服务间鉴权阶段的题目；当前可用的缓解手段只有**调小 `token-ttl`**；
- **轮换密钥需要 core 与所有微服务同时重启**。

以上都需要服务间鉴权阶段（阶段 5）替代。

## 五、构建注意（跨 reactor）

本模块在**仓库根 reactor** 中，且**不继承**根 pom（避免消费方被拖入 200+ jar 的宿主技术栈，与 `sfc-common` 同一约定）。

- 根目录构建：`mvn package` 会一并构建，`sfc-core` 正常引用；
- **microservices 单独构建**（`mvn -f microservices/pom.xml package`）会把它当作外部依赖，需先 `mvn -pl sfc-auth install`
  （或改从根目录用 `-pl` 构建 microservices 的目标模块）。
