# 微服务改造 · 档位 3 设计方案（前端接入与接口调用）

> **状态**：设计定稿，**尚未产生任何代码改动**。等待审阅。
> **范围**：微服务的前端资源接入 + 接口调用链路
> **依赖**：档位 1 的 `ServiceDiscovery`（必需）。**不依赖档位 2**，可提前实施；其中**资源代理与接口代理由阶段 8 的独立网关 `sfc-gateway` 承担**（见[网关独立化设计](microservice-gateway-design.md)）
> **配套**：[档位 1 设计方案](microservice-phase1-design.md) · [**网关独立化设计**](microservice-gateway-design.md)（**代理层与身份传递以它为准**） · [依赖冲突调试手册](microservice-nacos-dependency-debugging.md) · [方案评估报告](review/microservice-plan-review.md)
> **创建日期**：2026-02-12
> **信任模式（框架期）**：`/api/ext/**` 的用户身份由**网关**注入 `X-Sfc-Uid`，微服务直接信任；**服务间鉴权整体后置**（取舍与债务见[网关设计 §2](microservice-gateway-design.md)）

---

## 阅读指引

| 你想了解 | 看第几节 |
|---|---|
| 目标边界 | §1 |
| 现有前端插件是怎么工作的 | §2（**建议先读，后面全部建立在它之上**） |
| 整体链路长什么样 | §3 |
| 前端产物怎么改成随微服务发布 | §4 |
| 前端怎么发现微服务扩展 | §5 |
| 前端 JS/CSS 从哪加载 | §6 |
| 前端怎么调微服务接口 | §7 |
| **身份怎么传给微服务（框架期信任模式）** | §7.3、[网关独立化设计](microservice-gateway-design.md) |
| **路径映射与放行规则** | §7.6 |
| 错误语义与重试规则 | §7.7 |
| 大文件怎么走（预签名**暂缓**） | §8 |
| 开发期怎么调试 | §9 |
| 有什么硬约束和坑 | §10、§13 |
| 要动哪些文件 | §11 |
| 审阅重点 | §15 |

---

## 1. 目标与范围

### 目标

1. 微服务的**前端产物随微服务自身发布**，不随 `sfc-core`、也不随主前端发布
2. 主前端能**自动发现**已注册的微服务扩展，无需为每个新微服务改代码
3. 微服务的接口经**统一入口网关**（`sfc-gateway`）代理暴露，保证同源、统一鉴权、微服务可内网不可达
4. 大文件流量**不经过**网关进程（预签名直连）——**框架期暂缓**，见 §8

### 明确不做

| 不做 | 原因 |
|---|---|
| 微服务前端仓库完全独立 | 已确认保留现有模式（源码在主前端仓库，仅产物独立）。见 §4.4 |
| 微服务前端作为独立 SPA 页面 / iframe | UMD 外部依赖要求宿主提供全局变量，见 §10 |
| 微服务间的相互调用 | 档位 3 只做「前端 ↔ 微服务」链路 |
| 网关限流、熔断、链路追踪 | 治理能力，本档位不引入 |
| **服务间调用鉴权（令牌 / JWKS / `scope`）** | **整体后置**：框架期采用**信任模式**（网关注入 `X-Sfc-Uid`、微服务直接信任），安全机制另行规划（[网关设计 §2](microservice-gateway-design.md)） |
| **把网关内嵌进 `sfc-core`** | 结构不变（只是把自研转发换成官方转发）；Boot 4.1 与 Spring Cloud 2025.1 矩阵错配；还要给插件类加载器再加一条战线。**代理必须独立成进程** |

---

## 2. 现状：现有前端插件链路

> 本节是后续所有设计的基础。所有路径与行为均已在代码中核实。

### 2.1 构建链路（前端仓库）

```
scripts/build-ext.mjs  <插件id>
  └─ vite build --config build/extension/build.ts   (VITE_EXT_NAME={id})
       └─ build/extension/build-template.ts
            ├─ base:   /ext/sfc-ext-{id}/
            ├─ outDir: public/ext/sfc-ext-{id}/          ← ① 被主前端构建吞掉
            ├─ external: externalLibPath                 ← ② 外部化依赖
            └─ plugins: [vue(), copyToBackendPlugin()]   ← ③ 产物复制进后端仓库
```

**三个耦合点**：

| # | 位置 | 问题 |
|---|---|---|
| ① | `outDir: public/ext/{id}/` | 主前端 `npm run build` 时被一起打进 `dist` → **产物随主服务发布** |
| ② | `external: externalLibPath` | 见 §10，是硬约束不是缺陷 |
| ③ | `copyToBackendPlugin` | 需 `BACKEND_PATH` 环境变量，直接把产物写进**后端仓库** |

`externalLibPath` 的实际内容（`build/extension/external-lib-paths.js`）：

```js
['sfc-common', 'sfc-common/components',
 'sfc-common/utils/SfcUtils/index.js', 'sfc-common/utils/SfcUtils',
 'vue', 'dplayer', 'vuetify', 'vuetify/components', 'qs', 'monaco-editor']
```

### 2.2 运行期加载链路（`sfc-common`）

启动顺序（`sfc-common/core/boot/index.ts`）：

```
… → [验证登录状态] → [加载插件] buildExtensionManager({app, bootContextHandler}).mountAll() → [清理]
                       ↑ 注意：在登录校验【之后】
```

`mountAll()` → `getExtensionResource()` 返回**两组**数据源：

```ts
const defautExtensions = await fetchContent(location.origin + '/static-extension.json')  // ① 清单文件
const pluginResource   = await fetchPluginAutoLoadResource()                             // ② 固定一条
return [...defautExtensions].concat(pluginResource)
```

`mount(extension)` 用 `type` 决定 URL 如何解析：

```ts
if (extension.type == 'extension') {
  tag.src = SfcUtils.getApiUrl(API.sys.getPluginResource(extension.name, jspath))  // /api/plugin/{name}/resource/{path}
} else {
  tag.src = jspath                                                                 // ← 【直接用 URL】
}
```

### 2.3 两个已预留但未启用的通道

这是本方案能小改动落地的根本原因：

| 通道 | 现状 | 证据 |
|---|---|---|
| **`type: 'static'` 直接 URL 分支** | 代码已实现，但当前只有 `autoLoad.js/css` 用到 | `Extension.ts` `mount()` 的 `else` 分支 |
| **`/static-extension.json` 扩展清单** | 内容为空 `[]`，后端已在 permitAll 列表放行该路径 | `public/static-extension.json` = `[]`；`SecurityConfig` permitAll 含 `/static-extension.json` |

另外 `fetchPluginAutoLoadResource()` 中有一段**被注释掉的代码**：原本调 `listPluginAutoLoadList`，按插件逐个生成 `type: 'extension'` 的条目。说明"清单驱动加载"本就是原设计意图。

> **结论**：前端的扩展加载机制**本来就是数据驱动的**，只是数据源只有"合并后的 autoLoad.js"一条。档位 3 要做的就是**再接入一个数据源**。

---

## 3. 总体设计

```
┌──────────────────────────────────────────────────────────────────┐
│  浏览器（主 SPA，由 sfc-core 托管、经网关同源访问）                   │
└───────┬──────────────────────────────────────┬───────────────────┘
        │ ① 清单（透传）                        │ ④ 控制面 API
        │ GET /api/ext/manifest                 │ /api/ext/{svc}/**
        ▼                                       ▼
┌──────────────────────────────────────────────────────────────────┐
│  sfc-gateway（唯一入口 / 代理层，独立进程）                          │
│   · /api/**、/、WebSocket → 透明透传到 core                        │
│   · /api/ext/** → 先 resolve 取 uid 与目标路径，注入 X-Sfc-Uid 再转发│
└───────┬──────────────────────────────────────┬───────────────────┘
        │ ⑥ resolve（会话校验 ＋ 策略，不签发凭证）│ ③ 转发（lb://）
        ▼                                       ▼
┌──────────────────────────────────────────────────────────────────┐
│  sfc-core（控制面：会话权威 ＋ 策略判定）                            │
│   ServiceExtensionRegistry  ←读─ Nacos 实例 metadata               │
│   /api/ext/manifest · POST /api/gateway/resolve                    │
└──────────────────────────────┬───────────────────────────────────┘
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│  微服务进程（独立发布：后端 + static/ 前端产物）                     │
│   GET /static/**      控制面 API（**信任 `X-Sfc-Uid`**）            │
└──────────────────────────────────────────────────────────────────┘
        ② 资源代理（网关直连微服务）      ⑤ 大文件：框架期走网关流式代理
```

**链路的关键属性**：

| # | 链路 | 鉴权 | 缓存 |
|---|---|---|---|
| ① | 扩展清单 | `@AllowAnonymous`（与 `autoLoad.js` 一致），网关仅透传 | 不缓存 |
| ② | 静态资源代理 | 匿名，但**必须连同路径校验一起放行**（§7.6，**在网关侧**） | ✅ 长缓存 + URL 版本号 |
| ③ | 控制面 API 代理 | **需登录**：网关调 core 的 `resolve` 校验会话与策略；**uid 由 resolve 决定**，网关注入 `X-Sfc-Uid` | 不缓存 |
| ④ | 数据面（预签名直连） | **框架期暂缓**（信任模式下 URL 载体没有安全语义），下载走网关流式代理 | — |
| ⑤ | 入口透传（SPA / `/api/**` / WebSocket） | **由 core 自己判**，网关完全透明 | — |
| ⑥ | `resolve`（网关 → core） | 用户会话；**不签发任何凭证** | 不缓存 |

> **服务间鉴权在框架期整体后置**：不签发令牌、不验签、不做 `aud` / `scope`；取舍、安全债务与部署约束见[网关独立化设计 §2](microservice-gateway-design.md)。

---

## 4. 前端产物：改为随微服务发布

### 4.1 切断耦合点 ①

`outDir` 从 `public/ext/{id}/` 改为**可控输出目录**：

```
outDir: process.env.MICROSERVICE_FRONTEND_OUT ?? `public/ext/${extensionName}`
```

- 微服务构建时设 `MICROSERVICE_FRONTEND_OUT=../my-service/src/main/resources/static` → 产物直接进微服务仓库
- 不设该变量时**保持现有行为**（向后兼容现有 jar 插件）

### 4.2 切断耦合点 ③

把 `copyToBackendPlugin(extensionName)` 改为**按目标选择**：

| 目标 | 行为 |
|---|---|
| 现有 jar 插件（默认） | 保持 `copyToBackendPlugin` 不变 |
| 微服务扩展 | 由 `MICROSERVICE_FRONTEND_OUT` 决定，**不再触碰后端仓库** |

即改造为参数化：插件原本的目标是 `sfc-ext/{id}/src/main/assert/static/`，微服务的目标是自定义路径。

### 4.3 微服务侧打包

```
my-microservice/
└── src/main/resources/
    └── static/
        ├── index.umd.js     ← 前端构建产物，随微服务 jar / 容器镜像发布
        └── style.css
```

微服务只需暴露 `GET /static/**`。**产物从此与 sfc-core、主前端完全解耦。**

### 4.4 为什么不做完全独立仓库

已确认：源码保留在主前端仓库 `sfc-ext/{id}/`。原因是 `sfc-common` **未发布到 npm**：

```json
// sfc-common/package.json
{ "name": "sfc-common", "version": "1.0.0", "main": "index.ts" }
```

它靠仓库内 alias 消费。要让微服务前端完全独立，需先把 `sfc-common` 发私有 registry，或复制类型定义——收益不足以支撑成本。**产物独立已经满足核心诉求**，源码位置等真有多团队时再演进。

---

## 5. 扩展清单：新增 `/api/ext/manifest`

### 5.1 为什么不用 `/static-extension.json`

这条更"现成"，但有一个致命问题——已核实 `vite.config.ts`：

```ts
proxy: { '/api': createProxy() }   // ← 只代理 /api
```

**开发模式下 `/static-extension.json` 由 Vite 的 `public/` 直接返回，打不到后端。** 要用它就必须同时改 `vite.config.ts`，并承担"开发与生产行为不一致"的风险。

**改用 `/api/ext/manifest`**：天然被现有代理覆盖，开发与生产一致，且语义更准确（扩展清单不是"静态文件"）。

### 5.2 端点设计

```
GET /api/ext/manifest        @AllowAnonymous
```

响应体**严格对齐前端已有的 `ExtensionInfo` 类型**，前端无需新增类型定义：

```json
[
  {
    "name": "p2p-download",
    "type": "static",
    "resource": {
      "js":  ["/api/ext/p2p-download/static/index.umd.js?v=1.0.0"],
      "css": ["/api/ext/p2p-download/static/style.css?v=1.0.0"]
    }
  }
]
```

用 `type: "static"` 使 `mount()` 走**直接用 URL** 的分支。

> **注意 `?v=`**：静态资源走长缓存（§6.3），必须靠 URL 版本号打破缓存。版本号取自 Nacos metadata 的 `sfc.version`，微服务升级后 URL 变化 → 浏览器自动重新拉取。

### 5.3 数据来源：Nacos 实例 metadata

微服务在注册时声明自己（**core 不硬编码任何微服务**）：

```json
{
  "sfc.display.name":  "P2P 下载",
  "sfc.display.icon":  "mdi-download-network",
  "sfc.frontend.js":   "static/index.umd.js",
  "sfc.frontend.css":  "static/style.css",
  "sfc.api.prefix":    "/api",
  "sfc.version":       "1.0.0"
}
```

> Nacos 实例的 `metadata` 是**独立于注册信息的自由 KV 字段**，正是为这类"自描述"设计的。

⚠️ **未启用微服务模式（未用 Nacos）时清单必然为空**：`local` provider 只有进程内视图，注册的只有 `sfc-core` 自身，而 core 不声明任何 `sfc.frontend.*`。这是刻意的，不是缺陷。

`ServiceExtensionRegistry` 的职责：遍历 `ServiceDiscovery.listServiceNames()` → 取每个服务的实例 metadata → 筛出声明了 `sfc.frontend.*` 的 → 组装成清单。

**多实例时取任一健康实例的 metadata**（要求同一服务的所有实例 metadata 一致，这是部署约定）。

### 5.4 前端改动：加一个数据源

`sfc-common/core/serivce/Extension.ts`：

```ts
async getExtensionResource() {
  const defautExtensions = await fetchContent(location.origin + '/static-extension.json')
  const remoteExtensions = await fetchRemoteExtensionManifest()      // ← 新增
  const pluginResource   = await fetchPluginAutoLoadResource()
  return [...defautExtensions, ...remoteExtensions, ...pluginResource]
}
```

外加 `sfc-common/api/sys.ts` 新增一条 API 定义，以及一个小函数 `fetchRemoteExtensionManifest()`（内部做**失败降级：拿不到清单返回 `[]`，不能让微服务故障拖垮整个前端启动**）。

---

## 6. 前端资源加载：网关反向代理

### 6.1 路径规则

```
浏览器:  GET /api/ext/p2p-download/static/index.umd.js
   ↓ 网关解析 serviceName=p2p-download，路径余部=/static/index.umd.js
   ↓ 从注册中心取一个健康实例（lb://p2p-download）
微服务:  GET http://{host}:{port}/static/index.umd.js
```

> **静态代理不经过 core**：它由网关直连微服务（`/static/**` 是随微服务发布的物理目录，**不拼 `sfc.api.prefix`**）。放行该前缀时**必须连同路径校验一起加**（§7.6 硬规则 1）。

### 6.2 为什么不在 core 内联

对比一下"最省事"的做法：把微服务的 JS 内容读进 core，拼接进 `autoLoad.js`（现有 `getMergeAutoLoadResource` 对 jar 插件就是这么做的）。

| | core 内联 | 代理（推荐，由网关承担） |
|---|---|---|
| 前端改动 | **零** | 1 行 |
| core 内存/带宽 | ⚠️ 每次页面加载拉取全部微服务 JS 并拼接 | 仅转发 |
| 浏览器缓存 | ❌ 整包缓存，任一插件变更即全部失效 | ✅ 单文件缓存 |
| sourcemap / DevTools | ❌ 内联破坏 | ✅ 保留 |
| 微服务资源更新 | 需重启 core 感知 | 自然生效 |

**内联方案可作为"零前端改动"的过渡手段**，但代价明显，不推荐长期使用。

### 6.3 缓存与失效

- 代理资源响应设置长缓存（对齐 `SpringConfig` 现有静态资源策略：`maxAge(24h)` + `useLastModified`）
- **但 `index.umd.js` 文件名固定**，长缓存会导致微服务升级后前端加载到旧版本
- **解法**：清单里的 URL 带 `?v={version}`（§5.2）。版本从 Nacos metadata 取

---

## 7. 接口调用：控制面代理

### 7.1 路径规则

```
浏览器:  POST /api/ext/p2p-download/task/create
   ↓    网关把【原始未解码 URI ＋ 原始查询串 ＋ 方法】交给 core 的 resolve 端点
   ↓    core 校验会话与路径合法性、确认服务在册，并拼上 metadata 的 sfc.api.prefix（默认 /api）
   ↓    返回 { uid, serviceName, targetPath }；网关剥离入站身份头并注入 X-Sfc-Uid
微服务:  POST http://{host}:{port}/api/task/create     （信任 X-Sfc-Uid）
```

微服务**不需要知道自己被代理**，内部路径保持完整。

> ⚠️ **判定与转发必须用同一个字符串**：网关交给 `resolve` 的必须是**它收到的原始路径**（WebFlux 的 `URI.getRawPath()`），且**不得**在网关侧先做任何规范化或重编码；要转发的 `targetPath` 也由 core 一并返回。

### 7.2 代理由网关承担（core 内不再有代理控制器）

代理层是**独立进程的 Spring Cloud Gateway**：路由以 SCG 的路由声明（`Path=/api/ext/**` ＋ `lb://`）表达，`serviceName` 取路径第 1 段。由此带来三条与控制器写法有关的结论：

1. 本项目 AGENTS.md"避免在 `@RequestMapping` 中使用 `{}` 占位符"的约定**仍适用于 core 的控制器**；代理不再由 core 的控制器承担，因此该约定在代理侧**自然不适用**（路由是声明式配置）。
2. core 侧新增的只有 `resolve` 端点：按项目规范用 `@RequestBody` 接收 `rawPath` / `rawQuery` / `method`，**不解析路径参数**。
3. ⚠️ **core 侧不得再注册任何映射到 `/api/ext/**` 的控制器**。`@AllowAnonymous` 的注册粒度是 **controller 方法的 pattern values**（见 `SecurityConfig.getAnonymousUrls()`）：一旦有控制器映射 `/api/ext/**` 并被标注，**整个 `/api/ext/**` 会一起 permitAll**，§7.4 的约束当场失效。

> 补充：`SpringConfig` 中 `ProtectBlocker` 拦截器作用于 `/api/**`、`SecurityConfig` 的 `anyRequest().authenticated()` 覆盖 core 自己的 API —— **这两条不变**，因为入口透传链路上 core 仍自行鉴权。微服务流量的"是否为登录用户"由网关 → resolve 链路把关。

### 7.3 身份传递：框架期采用**信任模式**（服务间鉴权整体后置）

```
浏览器（已登录） → 网关（唯一入口，自身不判身份）
      → 网关调 core 的 resolve：校验会话 ＋ 路径合法性 ＋ 服务在册
      → 返回 { uid, serviceName, targetPath }
      → 网关【剥离入站 X-Sfc-* 与 Token 头 / token cookie / ?Token=】
      → 网关【注入 X-Sfc-Uid: <uid>】
      → 微服务直接信任该头：读它作为"当前用户"，不校验、不解析任何凭证
```

**这是一个有意为之的阶段性取舍，不是最终信任模型**：

| 项 | 框架期（本阶段） | 安全阶段（阶段 5，另行规划） |
|---|---|---|
| 身份载体 | 明文头 `X-Sfc-Uid` | **可验证**的载体（方案待定） |
| 微服务侧校验 | **无** | 必须 fail-closed |
| 受众绑定 / `scope` | **无** | 必须明确 |
| 部署边界 | **仅内网 / 本机可达**（硬约束） | 仍需要，但不能作为唯一防线 |

> ⚠️ **本期唯一一条不可协商的安全规则**：**网关必须剥离入站的一切身份头**（`X-Sfc-Uid` 及所有 `X-Sfc-*`）。否则任何人都能把自己伪装成任意用户——信任模式当场失效。
>
> ⚠️ **明确记录的安全债务**：任何能直连微服务端口的人都可以伪造 `X-Sfc-Uid`；因此**微服务与 core 的内部接口只允许在内网暴露**，**上公网前必须先完成安全阶段**。

**为什么框架期不顺手做令牌**：一期只搭骨架；令牌 / 验签 / JWKS / `aud` / `scope` 是**一整套机制**，会破坏"每步只引入一个新变量"的原则（变成"同时引入网关 ＋ 密码学设施"），而且跨进程契约需要先冻结，不适合边搭骨架边定。

### 7.4 ⚠️ 安全要点：uid 只来自网关

core 自己的 `/api/**` 上，`@UID` / `@ValidPath` / `@RolesAllowed` / `ProtectBlocker` **原样生效、零改动**（读的都是 SecurityContext）。代理路径上如果只是无脑转发：

> 攻击者可以构造 `/api/ext/svc/...?uid=999` 越权访问他人网盘资源。

**框架期的两条结构性约定**：

| # | 约定 | 为什么是"结构性"的 |
|---|---|---|
| 1 | **`uid` 不是一个参数**：微服务业务代码不得读取任何名为 `uid` 的请求参数；`/api/internal/**` 方法签名里也不出现 uid 参数 | 这个入口**不存在**，而不是"存在但约定不看它" |
| 2 | **`uid` 只由网关注入**：值来自 core 的 resolve（会话判定结果）；网关在写入前**剥离入站的一切 `X-Sfc-*`** | 伪造入口被**移除**，而不是"下游自己判断来源可不可信" |

> 约定 2 是信任模式的全部防线：网关一旦忘记剥离入站头，"信任模式"就退化成"谁都能当任意用户"。因此它必须有一条**实测**验收项（§12 Step 4）。

**框架期明确不做**：微服务侧不做任何身份校验（无令牌、无验签、无 `aud` / `scope`）。这是**有意为之的安全债务**——必须在部署上用"仅内网可达"兜住，并在安全阶段偿还（见[网关独立化设计 §2](microservice-gateway-design.md)）。

### 7.5 流式、故障处理与代理安全

| 事项 | 要求 |
|---|---|
| 请求/响应体 | **必须流式管道**，禁止 `byte[]` 全量缓冲 |
| 超时 | 控制面短超时（如 30s）；下载类单独配置 |
| 实例选择 | 注册中心实例列表 ＋ **轮询**（网关侧用 SCG 的 `lb://` 承担） |
| 实例失效 | 换下一个实例重试（限次数，避免放大）；**只有不携带请求体的 `GET` / `HEAD` 才允许换实例**——请求体是一次性流，带体重试等于让下游收到空请求体而调用方看到"成功" |
| 全部不可用 | 返回 **503**（而非 500），并给出明确的业务错误码 |
| 服务不存在 | 返回 404 |

**代理层的必修安全项**（**由网关实现**；官方库只覆盖传输机制，这些策略不代劳。实现清单见[网关独立化设计 · 决策 5](microservice-gateway-design.md)）：

| 细节 | 风险 | 处理 |
|---|---|---|
| **入站 `X-Sfc-*` 身份头** | **任何人都能伪造 uid**（信任模式下最严重的一条） | 转发前**全部剥离**，只注入网关自己写入的 `X-Sfc-Uid` |
| 会话凭证三入口（`Token` 头 / `token` cookie / `?Token=`） | 30 天长效的用户凭证外泄 ⇒ 微服务及其 access log 拿到通往 core 的钥匙 | 三个入口**全部剥离**（微服务不需要、也不该拿到它） |
| hop-by-hop 头（`Connection`、`TE`、`Transfer-Encoding`、`Keep-Alive` 等） | 请求走私（request smuggling） | 转发前按 RFC 7230 **剥离** |
| `Host` / `X-Forwarded-For` / `X-Forwarded-Host` | 下游服务自我认知错误；伪造来源 IP | **网关是最外层**：重写 `Host`，**覆盖式**写入 `X-Forwarded-*`（丢弃入站值） |
| 请求体大小上限 | 恶意大请求打满入口 | 控制面设上限（数据面走预签名，不过网关） |
| 目标 URL 构造 | **SSRF** | `serviceName` **必须**命中 `ServiceDiscovery` 已知服务，且**不得**是 `sfc-core` / 网关自身（"服务不存在 → 404"**就是这条防线**） |
| 静态放行前缀匹配 | 绕过鉴权直达业务 API | 放行必须**连同路径校验一起**加（§7.6 硬规则 1） |
| 陈旧连接 | 连接池里被对端回收的连接会让**下一个完全正常的请求**失败 | **不要关闭客户端库的连接层重试**；防重放由"请求体不可重放"独立保证。Reactor Netty 下的对应行为需在 8.1 预研中实测（见[网关设计 · 决策 5](microservice-gateway-design.md)） |

### 7.6 路径映射与放行规则（一张表说清 ＋ 三条硬规则）

**映射规则**：

| 浏览器请求 | 网关的处理 | core 的角色 | 转发到微服务 | 鉴权 |
|---|---|---|---|---|
| `GET /api/ext/manifest` | 透传（**不走代理**） | 清单端点（阶段 7 提供） | — | 匿名 |
| `/api/ext/{svc}/static/**` | 静态资源代理（阶段 7）：`serviceName` ＝ 路径第 1 段，**余部直接映射**（**不**拼 `sfc.api.prefix`）＋ 路径校验 | 不参与 | `/static/**` | 匿名（＋ 路径校验） |
| 其余 `/api/ext/{svc}/**` | 调 core 的 `resolve` 拿到 `{uid, targetPath}` → **剥离入站身份头** → 注入 `X-Sfc-Uid` → `lb://` 转发 | **会话校验 ＋ 路径与服务白名单** | `{prefix}/**` | **需登录** |
| `/dl/...`（阶段 9） | **⏸ 暂缓**：框架期下载走网关流式代理 | — | — | — |

> 两条规则**不对称**（静态直连 `/static/**`，接口要拼 `sfc.api.prefix`）是**刻意**的：静态产物是随微服务发布的**物理目录**，而接口前缀是微服务的**自描述**。

**三条必须写死的硬规则**：

| # | 规则 | 不这么做的后果 |
|---|---|---|
| 1 | 路径**只拒绝、不规范化**：编码的 `/`、`\`、`NUL`（`%2f` / `%5c` / `%00`）、解码后等于 `.` / `..` 的路径段、解码后含控制字符的路径段——一律 **400**，**不做"规范化后再放行"** | "放行 `/static/**`"会因为 `/api/ext/svc/static/../api/task/create` 这类构造，变成"**放行任意路径**"；"先规范化再判断"还有第二个隐患：**校验时看到的路径**与**转发时用的路径**会变成两个字符串 |
| 2 | **core 侧不得注册映射到 `/api/ext/**` 的控制器**；`@AllowAnonymous` 只允许标在**具体路径**的端点上（如 `/api/ext/manifest`） | `@AllowAnonymous` 的注册粒度是 **controller 方法的 pattern values**（见 `SecurityConfig.getAnonymousUrls()`）。若存在映射 `/api/ext/**` 的控制器并被标注，**整个 `/api/ext/**` 会一起 permitAll**；静态放行必须在**网关侧**做，而不是把前缀塞进 core 的 permitAll |
| 3 | **判定与转发必须用同一个字符串**：网关交给 `resolve` 的是它收到的**原始未解码 URI**，要转发的目标路径也由 core 一并返回；网关不得自行规范化、拼接或重编码 | 否则规则 1 失效——校验的是 A 路径，转发的是 B 路径 |

### 7.7 错误语义与重试规则（对外契约）

| 情形 | HTTP | 业务码 | 说明 |
|---|---|---|---|
| 功能未启用（`sys.service.gateway.enabled=false`） | **404** | 7002 | 端点**仍然注册**，显式报错而不是"不注册让它落到主链"（"关闭"不等于"放开"） |
| 未登录 / 会话无效 | **403** | 7005 | 没有登录用户 ⇒ 没有被委托的主体，不存在"匿名代理"形态 |
| 路径 / 方法非法 | **400** | 7000 | 见 §7.6 硬规则 1 |
| 服务未注册 / 无健康实例 | **404** | 7001 | **这条就是 SSRF 防线**：目标 host/port 只可能来自注册中心 |
| 目标为 `sfc-core` 或网关自身 | **404** | 7001 | 前者是提权路径，后者是回环 |
| 请求体超限 | **413** | 7003 | chunked 请求体在**读取过程中**拦截，而不是先整体读进内存 |
| 实例仍在册但全部连不通（重试耗尽） | **503** | 7004 | 与 404 的区别是"注册中心视图"vs"实际连通性"；实例列表**不得加缓存**，否则这个窗口会被放大 |

**重试规则**：只有 **`GET` / `HEAD`** 会在失败后换下一个实例。写请求只尝试 **1 个**实例——请求体是一次性流，不可重放，带体重试等于让下游收到**空请求体**而调用方看到"成功"。（实现见[网关独立化设计 · 决策 5](microservice-gateway-design.md)）

---

## 8. 数据面：大文件预签名直连 —— ⏸ **暂缓（记录在案）**

> **本阶段暂缓。** 预签名直连的载体（URL query 里的凭证）在**信任模式**下没有安全语义——没有可验证的载体，预签名 URL 就退化成"带 uid 的明文链接"。因此框架期的下载路径统一走**网关的流式代理**（§7），预签名直连随**安全阶段**一起重新规划。

**届时要处理的设计要点（暂缓项清单，框架期不实现）**：

| # | 项 | 说明 |
|---|---|---|
| 1 | 数据面凭证的载体与生命周期 | 短时效 ＋ **资源绑定**；**不得**复用控制面的高权限凭证 |
| 2 | 微服务侧下载端点的校验方式 | 与安全阶段选定的身份载体一致，**不新增第二套校验实现** |
| 3 | 部署形态 | 局域网直连 / Nginx 把 `/dl/{service}/**` 路由到微服务（绕过 Java 进程）/ 仅网关可达（退回 §7 的流式代理） |
| 4 | URL 模板统一（原评估报告 🟠-3） | 直连 `/dl/{token}` 与 Nginx `/dl/{service}/{token}` 由**同一个配置项**切换，不写死两套拼接逻辑 |
| 5 | 验证 | 大文件下载时后端进程带宽 / 线程无显著增长 |

> **不变量**：**§7 的流式代理不能省**——它既是本阶段的降级路径，也承接控制面本身（阶段 8 实现）。

---

## 9. 开发期：Vite dev server 直连

`mount()` 创建的是**普通 `<script src>`**（无 `crossorigin` 属性）和 `<link rel=stylesheet>`。

> **普通 script / link 不受 CORS 限制**——只有 `fetch`/`XHR` 以及带 `crossorigin` 的请求才受限。

所以开发期清单可以直接指向微服务前端的 Vite dev server：

```json
{
  "name": "p2p-download",
  "type": "static",
  "resource": {
    "js":  ["http://localhost:5174/index.umd.js"],
    "css": ["http://localhost:5174/style.css"]
  }
}
```

**收益**：微服务前端开发者用自己的 Vite dev server **热更新**调试，**不碰主前端仓库、不碰后端、不重新构建 core**。

实现方式：清单端点支持一个开发期开关（如 `sys.service.gateway.dev-manifest-url`），或微服务在开发模式下把 `sfc.frontend.*` 声明为 dev server 地址。

---

## 10. 硬约束：UMD 外部依赖必须由宿主提供

`build-template.ts` 的 `globals` 映射：

```js
{ vue:'Vue', vuetify:'Vuetify', dplayer:'DPlayer',
  'sfc-common':'SfcCommon', qs:'qs',
  'sfc-common/components':'Components',
  'sfc-common/utils/SfcUtils':'SfcUtils', ... }
```

微服务前端产物是 **UMD + 外部化依赖**，运行期**依赖宿主页面提供这些全局变量**（`core/boot` 把它们挂在 `window` 上）。

**推论**：

| 形态 | 结果 |
|---|---|
| 集成进主 SPA（`type: 'static'` 注入 script） | ✅ 全局变量齐全 |
| 作为**独立 SPA 页面**打开 | ❌ `Vue` / `SfcCommon` / `Vuetify` 全 undefined，**直接报错** |
| iframe 嵌入 | ⚠️ 需在 iframe 页面内**手动注入**这些全局变量，否则同样报错 |

**这从技术上决定了"集成进主 SPA"是自然选择**，也是 iframe 方案的真实成本。

---

## 11. 文件级实施清单

> 只列**要动哪些文件**与**每步验证什么**，不含代码实现。

### 11.1 控制面 `sfc-core`（只做策略与会话判定，**不再有任何转发**）

| 文件 | 动作 | 职责 |
|---|---|---|
| `.../model/ExtensionInfo.java` + `ExtensionResource.java` | 新增 | 前端契约 DTO，字段严格对齐 TS 的 `ExtensionInfo` |
| `.../service/registry/ServiceExtensionRegistry.java` | 新增 | 从 Nacos metadata 聚合扩展清单 |
| `.../controller/ext/ServiceExtensionController.java` | 新增 | `GET /api/ext/manifest`，`@AllowAnonymous` |
| `.../controller/GatewayResolveController.java` | 新增 | `POST /api/gateway/resolve`：`{rawPath, rawQuery, method}` → `{uid, serviceName, targetPath}`；**不签发任何凭证** |
| `.../service/gateway/GatewayResolveService.java` | 新增 | **策略**：路径合法性（§7.6 硬规则 1）、服务白名单（SSRF 防线）、**自我转发阻断**（含网关自身）、metadata 前缀解析 |
| `.../service/gateway/GatewayResolveProperties.java` | 新增 | 绑定 `sys.service.gateway.*`（`enabled` / `default-api-prefix`） |
| `sfc-cloud-api`（`com.sfc.cloud.trust.SfcTrustHeaders`） | 新增 | 头名常量（`X-Sfc-Uid`）：网关写入、微服务读取，只此一份定义 |
| 配置文件（4 处 ＋ `docs/quick-start/config.md`） | 改 | 新增 `sys.service.gateway.*`。**AGENTS.md 要求同步 `application-develop.yml`、`application-develop-mysql.yml`、`application-product.yml`、`pre-release/config.yml`，不改 `application.yml`** |
| `SecurityConfig.java` | 改 | 放行 `/api/ext/manifest`（阶段 7）；**`/api/ext/**` 不进 core 的 permitAll**——代理已移出 core，且 core 侧不得注册映射该前缀的控制器（§7.6 硬规则 2） |
| `sfc-core/pom.xml` | — | **无需新增 HTTP 客户端**：转发下沉到网关后，core 不再发起代理请求 |

> `ExtensionInfo` 放在 `sfc-core` 而非 `sfc-cloud-api`：它是**前端契约**，不是注册中心 SPI，放瘦契约模块会污染其边界。

### 11.1b 网关工程 `microservices/sfc-gateway`（独立进程，**不继承 root pom**）

| 项 | 内容 |
|---|---|
| 版本矩阵 | Boot **4.0.8** ＋ Spring Cloud **2025.1.0** ＋ SCG **5.0.0（Server WebFlux）** ＋ SCA **2025.1.0.0**（与 demo 同源；**刻意不对齐宿主 4.1.0**） |
| 依赖 | 零咸鱼云依赖（验证方式同 demo：`dependency:list` 中 `com.xiaotao` / `sfc-*` 命中 0 条） |
| 路由 | ① `/`、`/index.*`、`/assets/**`、`/static/**`、`/ext/**`、`/oauth*` → core；② `/api/**`（含 WebSocket）→ core；③ `/api/ext/manifest` → core；④ `/api/ext/{svc}/static/**`（阶段 7）→ 微服务；⑤ `/api/ext/**` → resolve ＋ `lb://` |
| 自定义 filter | **入站 `X-Sfc-*` 剥离**、注入 `X-Sfc-Uid`、会话凭证三入口剥离、`X-Forwarded-*` 覆盖式、resolve 调用、错误语义映射（见[网关独立化设计 · 决策 5](microservice-gateway-design.md)） |
| 配置 | 网关自有配置（core 地址、监听端口、超时、请求体上限、重试次数）放在**本工程自己的 `application.yml`**，不使用 `sys.` 前缀 |

### 11.2 前端仓库（小范围）

| 文件 | 动作 |
|---|---|
| `sfc-common/api/sys.ts` | 新增 `getExtensionManifest()` |
| `sfc-common/core/serivce/Extension.ts` | `getExtensionResource()` 增加数据源 + `fetchRemoteExtensionManifest()`（含失败降级） |
| `build/extension/build-template.ts` | `outDir` 支持 `MICROSERVICE_FRONTEND_OUT` 覆盖；插件目标可切换 |
| `build/plugins/copy-to-backend-plugin.ts` | 参数化，支持"不复制进后端仓库"模式 |
| `scripts/build-ext.mjs` | 支持把产物输出到微服务侧目录 |
| `.env.example` | 新增 `MICROSERVICE_FRONTEND_OUT` 说明 |

**改动性质**：全部是**增量 + 向后兼容**，现有 jar 插件的构建与加载行为不变。

### 11.3 微服务侧

| 项 | 内容 |
|---|---|
| 前端产物 | 构建输出到 `src/main/resources/static/`，随自身发布 |
| 静态服务 | 暴露 `GET /static/**` |
| Nacos metadata | 声明 `sfc.*` 自描述字段（`sfc.api.prefix` / `sfc.version` / `sfc.frontend.*`） |
| **身份读取** | 从 **`X-Sfc-Uid` 头**读取当前用户（框架期**信任模式**：不校验、不解析任何凭证）；**不得**读取请求参数里的 `uid`；头名常量取自 `SfcTrustHeaders` |
| 静态放行 | 仅 `/static/**` 与健康检查匿名；放行前**必须做路径校验**（见 §7.6 硬规则 1） |
| 安全债务 | 微服务**不做任何来源校验** ⇒ **只能在内网 / 本机暴露**（见 §7.3 与[网关设计 §2](microservice-gateway-design.md)） |

---

## 12. 分步实施与验证

**Step 1（后端，可独立验证）**：`ExtensionInfo` DTO + `ServiceExtensionRegistry` + `GET /api/ext/manifest`
- 验证：`registry-provider: local` 下返回空数组 `[]`（不破坏现状——`local` provider 只会返回 `sfc-core` 自身，而 core 没有扩展清单）

**Step 2（网关 ＋ core）**：`/api/ext/{svc}/static/**` 静态资源代理（网关直连微服务 `/static/**`）＋ `resolve` 端点（会话校验 ＋ 策略判定）
- 验证：起一个只提供 `GET /static/hello.js` 的假微服务，确认经网关可获取，且**路径校验与放行同时生效**

**Step 3（前端）**：新增数据源 + 构建输出参数化
- 验证：清单返回后，DevTools Network 能看到 bundle 被加载；**现有 jar 插件加载行为无回归**

**Step 4（控制面代理 ＋ 信任模式）**：`/api/ext/**` 经 resolve 后转发 ＋ 注入 `X-Sfc-Uid`（§7.3 / §7.4 / §7.6）
- 验证：**伪造 `X-Sfc-Uid: 999` 的入站请求 → 微服务收到的仍是真实登录用户的 uid**（本期最关键的一条）
- 验证：`?uid=999` 无效；未登录 → **403 / 7005**；服务不存在、目标为 `sfc-core` 或网关自身 → **404 / 7001**
- 验证：路径**只拒绝不规范化**（`/api/ext/svc/static/../api/...` 必须被拒，且**校验路径与转发路径是同一个字符串**）
- 验证：**core 自身 API 回归**——`@UID` / `@ValidPath` / `@RolesAllowed` / 登录 / 上传下载 / WebSocket / jar 插件全部正常

**Step 5（数据面）**：**⏸ 暂缓**（见 §8）；框架期下载走网关流式代理
- 验证：大文件下载经网关时，后端进程**带宽 / 线程无异常增长**、无 OOM

**Step 6**：开发期 dev server 直连
- 验证：微服务前端改代码，刷新主页面即生效

### 编译验证（遵守 AGENTS.md）

| 手段 | 适用 |
|---|---|
| **系统 `mvn`** | 改动了 `pom.xml` 时必须用 |
| MCP `build_project` | 常规编译验证 |
| MCP `get_file_problems` | 检查修改文件，修复新产生的警告 |
| 单元测试 | **不生成**——静默原则，除非明确要求 |

---

## 13. 风险

| # | 风险 | 影响 | 对策 |
|---|---|---|---|
| 1 | **伪造身份头越权**（网关未剥离入站 `X-Sfc-*`，或微服务直接信任来源 IP） | ⚠️ **任意用户越权**（信任模式下最严重的一条） | §7.4 约定 2：网关**剥离全部入站身份头**后重写 `X-Sfc-Uid`；Step 4 有专门验收项 |
| 2 | 静态资源长缓存 + 固定文件名 | 微服务升级后前端加载旧版本 | 清单 URL 带 `?v={version}` |
| 3 | 清单端点故障 | 拖垮整个前端启动 | `fetchRemoteExtensionManifest()` 失败降级返回 `[]` |
| 4 | 微服务前端 bundle 加载失败 | 微服务功能不可用 | 复用 `mount()` 已有的 `logWarning`/`logError` 与 Promise reject |
| 5 | 代理无流式 | 大响应 OOM | §7.5 强制流式管道；8.1 预研实测 1GB 响应 |
| 6 | 网关成为可用性单点 | 整站不可用 | 部署文档写明；入口切换前保留"浏览器直连 core:8087"的回退路径 |
| 7 | 多实例 metadata 不一致 | 清单与实际入口不符 | 部署约定：同一服务所有实例 metadata 必须一致 |
| 8 | 前端改动引入回归 | 现有 jar 插件加载异常 | §11.2 全部为增量改动；Step 3 明确回归验证项 |
| 9 | ⚠️ **信任模式越过内网边界**（微服务端口被公网 / 同网段直连） | **任意用户越权、任意读** | **硬约束：微服务与 core 的内部接口只允许内网可达**；**上公网前必须先完成安全阶段**（阶段 5） |
| 10 | ⚠️ **静态放行被路径穿越绕过** | "放行 `/static/**`" 变成"放行任意路径" | §7.6 硬规则 1（只拒绝、不规范化）＋ 硬规则 2（core 不得注册映射 `/api/ext/**` 的控制器）＋ 硬规则 3（校验与转发必须是同一个字符串） |
| 11 | **网关忘记剥离会话凭证** | 30 天长效用户凭证泄漏到微服务及其 access log | §7.5：`Token` 头 / `token` cookie / `?Token=` 三入口全部剥离，Step 4 逐条取证 |
| 12 | 所有流量多一跳（含大文件） | 吞吐下降 | 8.1 预研给出吞吐对照；必要时阶段 9 数据面旁路（**暂缓项**之一） |
| 13 | 把"信任模式"当成最终方案 | 安全债务被遗忘 | 阶段 5 明确标注 🔒 后置并列出必须回答的问题；路线图检查点 4 = 安全阶段完成 |
| 14 | 数据面 URL 载体（阶段 9，**暂缓**） | 若在无载体保障下启用，等于"带 uid 的明文链接" | §8 暂缓项清单；框架期下载走网关流式代理 |

---

## 14. 学习点

| 学习点 | 在哪被逼出来 |
|---|---|
| **网关模式与同源策略** | §3、§7：为什么前端**只跟网关**说话——同源是浏览器约束的直接结果，不是架构偏好 |
| **策略 / 传输分离** | §7.1、§7.2：core 判"能不能转"、网关做"怎么转"，才能既用官方库又不把判定逻辑搬出信任边界 |
| **信任边界必须是"结构性"的** | §7.4：先把它从"约定不看某个参数"升级为"**入口不存在**"，再把它从"任何来源都能写 uid"升级为"**只有网关注入**" |
| **技术债要显式记账** | §7.3、§8：信任模式与预签名暂缓都写成"**债务 ＋ 到期条件**"，而不是含糊的"以后再说" |
| **服务自描述（metadata 驱动）** | §5.3：core 不硬编码任何微服务，靠 Nacos metadata 实现"注册即接入" |
| **控制面 / 数据面分离** | §8：不是所有流量都该过网关；但**暂缓不等于取消**——降级路径（流式代理）必须先有 |
| **缓存失效策略** | §6.3：长缓存 + 固定文件名的经典冲突，用版本化 URL 解决 |
| **浏览器同源模型的细节** | §9：`<script src>` 不受 CORS 限制——知道这个才能设计出"开发期直连"的便利 |
| **模块联邦式的扩展机制** | §2.3：项目已有"数据驱动扩展加载"设计，本档位是把它激活而非新造 |

---

## 15. 审阅时建议重点看这五处

1. **§5.1 为什么不用 `/static-extension.json`**——这是最容易走错的一步。它看起来"现成"，但 `vite.config.ts` 只代理 `/api`，开发模式下打不到后端。请确认你认同改用 `/api/ext/manifest`。
2. **§7.3 的信任模式取舍**——请确认你接受这一期**不做**服务间鉴权：身份是明文头、微服务不校验、**只允许内网暴露**，且**上公网前必须先完成安全阶段**（阶段 5 已列出重新规划时必须回答的问题）。
3. **§7.4 / §7.6 的两条结构性约定与三条硬规则**——其中"**网关必须剥离入站身份头**"是信任模式的唯一防线；"core 不得注册映射 `/api/ext/**` 的控制器"若不遵守，**整个 `/api/ext/**` 会一起 permitAll**。
4. **§10 UMD 外部依赖约束**——它决定了微服务前端**不能**是独立 SPA 页面，也决定了 iframe 方案的真实成本。这是个容易被忽略但影响架构选择的事实。
5. **§4.4 源码位置的取舍**——保留了主前端仓库的源码位置，只做产物独立。请确认这符合你对"独立发布"的预期。

---

## 16. 与 jar 插件的对比

| 维度 | jar 插件 | 微服务扩展 |
|---|---|---|
| 前端产物位置 | `sfc-ext/{id}/src/main/assert/static/` → 打进插件 jar | 微服务自己的 `static/` |
| 前端资源供给 | `GET /api/plugin/{name}/resource/**`（classpath） | `GET /api/ext/{svc}/static/**`（网关 HTTP 代理） |
| 前端发现方式 | `autoLoad.js` 内联拼接 | `/api/ext/manifest` 清单 + 直接 URL |
| 后端位置 | 同进程（merge classloader） | 独立进程 |
| 后端接口路径 | 直接注册进 core 的 `RequestMappingHandlerMapping` | 经 `sfc-gateway` 反向代理（`X-Sfc-Uid` 标识用户） |
| 能力上限 | 可注入任意 Spring Bean，含 core 内部服务 | 只能通过内部 API 访问 core 能力（档位 2） |
| 前端框架契约 | `window.context` / `SfcUtils` / `bootContext` | **完全相同** ✅ |
| 独立发布 | ❌ 需部署进 core 的 `ext/` | ✅ 独立发布独立重启 |
| 故障隔离 | ❌ 插件崩溃拖垮 core | ✅ 进程隔离 |
| 升级是否需重启 core | ❌ 需要 | ✅ 不需要 |

> **刻意的设计选择**：前端契约（`window.context`、`bootContext.addProcessor`、`sfc-common` 外部化）**在两种形态间完全一致**。开发者学一次，两边都能用；已有插件前端代码稍作调整即可迁移到进程外。
