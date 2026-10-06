# 微服务改造 · 档位 3 设计方案（前端接入与接口调用）

> **状态**：设计定稿，**尚未产生任何代码改动**。等待审阅。
> **范围**：微服务的前端资源接入 + 接口调用链路
> **依赖**：档位 1 的 `ServiceDiscovery`（必需）。**不依赖档位 2**，可提前实施
> **配套**：[档位 1 设计方案](microservice-phase1-design.md) · [**服务鉴权机制设计**](microservice-service-auth-design.md)（§7.3 / §7.4 / §8 的**机制细节以它为准**） · [依赖冲突调试手册](microservice-nacos-dependency-debugging.md) · [拓扑 / 泳道图](review/microservice-auth-topology.md)
> **创建日期**：2026-02-12
> **修订日期**：2026-10-06（§7.3 的鉴权机制由"内部服务令牌（共享对称密钥 ＋ 自定义头 `X-Sfc-Internal-Token`）"改为"**委托令牌**（RS256 / **自持密钥** / **自建匿名 JWKS 端点** / `Authorization: Bearer`）"，并**与 OIDC/OAuth 解耦**；§7.4 的信任边界表述加强；§7.5 补自建代理必修安全项；**新增 §7.6 路径映射与放行规则**；§8 数据面改为"同一枚令牌换载体"。机制论证见[服务鉴权机制设计](microservice-service-auth-design.md)）

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
| **鉴权机制细节（令牌形态 / claims / 失败语义）** | [服务鉴权机制设计](microservice-service-auth-design.md)、[拓扑 / 泳道图](review/microservice-auth-topology.md) |
| **路径映射与放行规则** | §7.6 |
| 大文件怎么不走 core | §8 |
| 开发期怎么调试 | §9 |
| 有什么硬约束和坑 | §10、§13 |
| 要动哪些文件 | §11 |
| 审阅重点 | §15 |

---

## 1. 目标与范围

### 目标

1. 微服务的**前端产物随微服务自身发布**，不随 `sfc-core`、也不随主前端发布
2. 主前端能**自动发现**已注册的微服务扩展，无需为每个新微服务改代码
3. 微服务的接口经 `sfc-core` **统一代理**暴露，保证同源、统一鉴权、微服务可内网不可达
4. 大文件流量**不经过** core 进程

### 明确不做

| 不做 | 原因 |
|---|---|
| 微服务前端仓库完全独立 | 已确认保留现有模式（源码在主前端仓库，仅产物独立）。见 §4.4 |
| 微服务前端作为独立 SPA 页面 / iframe | UMD 外部依赖要求宿主提供全局变量，见 §10 |
| 微服务间的相互调用 | 档位 3 只做「前端 ↔ 微服务」链路 |
| 网关限流、熔断、链路追踪 | 治理能力，本档位不引入 |

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
│  浏览器（主 SPA，由 sfc-core 托管）                                 │
└───────┬──────────────────────────────────────┬───────────────────┘
        │ ① 清单                                │ ④ 控制面 API
        │ GET /api/ext/manifest                 │ /api/ext/{svc}/**
        ▼                                       ▼
┌──────────────────────────────────────────────────────────────────┐
│  sfc-core（唯一入口 / 网关）                                       │
│   ServiceExtensionRegistry  ←读─ Nacos 实例 metadata               │
│   ServiceProxyController    ←─ 反代 + 委托令牌（现签）+ 信任边界       │
└───────┬──────────────────────────────────────┬───────────────────┘
        │ ② 资源代理                            │ ③ 转发
        │ /api/ext/{svc}/static/**              │
        ▼                                       ▼
┌──────────────────────────────────────────────────────────────────┐
│  微服务进程（独立发布：后端 + static/ 前端产物）                     │
│   GET /static/**      控制面 API                                   │
└──────────────────────────────┬───────────────────────────────────┘
                               │ ⑤ 大文件预签名直连（不过 core）
                               ▲
                        浏览器 ─┘
```

**四条链路的关键属性**：

| # | 链路 | 鉴权 | 缓存 |
|---|---|---|---|
| ① | 扩展清单 | `@AllowAnonymous`（与 `autoLoad.js` 一致） | 不缓存 |
| ② | 静态资源代理 | 匿名，但**必须做路径规范化 ＋ 前缀精确匹配**（§7.6） | ✅ 长缓存 + URL 版本号 |
| ③ | 控制面 API 代理 | 需登录；core 现签**委托令牌**（`aud` ＝ 目标服务名、TTL ≤ 60s）；**uid 只来自令牌的 `sub`** | 不缓存 |
| ④ | 大文件预签名 | **同一枚委托令牌换载体**（query；TTL **5 分钟** ＋ 资源绑定；**不是"一次性"**） | 由微服务决定 |

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

## 6. 前端资源加载：core 反向代理

### 6.1 路径规则

```
浏览器:  GET /api/ext/p2p-download/static/index.umd.js
   ↓ core 解析 serviceName=p2p-download，路径余部=/static/index.umd.js
   ↓ 从 ServiceDiscovery 取一个健康实例
微服务:  GET http://{host}:{port}/static/index.umd.js
```

### 6.2 为什么不在 core 内联

对比一下"最省事"的做法：把微服务的 JS 内容读进 core，拼接进 `autoLoad.js`（现有 `getMergeAutoLoadResource` 对 jar 插件就是这么做的）。

| | core 内联 | 代理（推荐） |
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
   ↓     strip 前缀 /api/ext/p2p-download，拼上 metadata 的 sfc.api.prefix（默认 /api）
微服务:  POST http://{host}:{port}/api/task/create
```

微服务**不需要知道自己被代理**，内部路径保持完整。

### 7.2 Controller 写法（AGENTS.md 合规）

AGENTS.md 要求"避免在 `@RequestMapping` 等注解中使用路径参数（`{param}` 形式）"。而反向代理天然需要通配。

**解法：用 `/**` 通配，serviceName 从请求 URI 解析，不使用 `{}` 占位符**：

```java
@RestController
public class ServiceProxyController {
    @RequestMapping("/api/ext/**")     // ← 无 {} 占位符
    public void proxy(HttpServletRequest req, HttpServletResponse resp) {
        // 从 req.getRequestURI() 解析 serviceName 与剩余路径
    }
}
```

这样既满足代理需求，又符合项目接口规范。

> 补充：`SpringConfig` 中 `ProtectBlocker` 拦截器作用于 `/api/**`，因此 `/api/ext/**` 会经过它；`SecurityConfig` 的 `anyRequest().authenticated()` 也会覆盖它——**这正是我们想要的**（控制面必须鉴权）。静态资源代理路径要单独放行（§3 表格 ②）。

### 7.3 鉴权：委托令牌（core 唯一签发，微服务 JWKS 离线验签）

```
前端请求 → core 已完成 JWT 鉴权
        → core 每请求现签「委托令牌」（RS256，自持密钥 sys.service.auth.signing-key）
          { iss, aud = 目标服务名, sub = uid, scope = internal.*, exp ≤ 60s, jti }
        → header: Authorization: Bearer <委托令牌>
        → 微服务离线验签：公钥取自 core 的自建匿名端点 GET /api/service-auth/jwks
          （本地缓存，按 kid 命中；未知 kid 必须回源一次）
          校验 iss / exp / scope，且 aud 必须包含自己（aud == 自己的服务名）
          → uid 只取 sub（不读任何请求参数）
```

**为什么不让微服务直接验用户 JWT**：

| 做法 | 问题 |
|---|---|
| 微服务共享 core 的 JWT 签名密钥 | 用户会话令牌是 **30 天长效**；密钥扩散面变大，**微服务被攻破 = core 被攻破** |
| 微服务每请求回调 core 校验 | 每个请求多一跳，core 压力翻倍；并把 core 的可用性变成微服务的前置依赖 |
| 微服务持有**共享对称**内部密钥 | ❌ **同样是"校验方也能签发"** —— 任何微服务都能伪造 `sub` 为任意 uid 的令牌，与上一条等价，只是换了把钥匙（HMAC 密钥既能验也能签） |
| **委托令牌：非对称签名，微服务只拿公钥（采用）** | core 持有**自持私钥**，微服务只拿**公钥**（自建匿名 JWKS 端点）⇒ **"只有 core 能签发"由算法保证**；且分发公钥没有安全后果，"密钥怎么送到微服务"这个问题直接消失 |
| ~~复用 OIDC 的 JWK 与 `/oauth2/jwks`~~ | ❌ 已否决：那样 `sys.oidc.enabled=false` 时微服务鉴权一起失效，且 OIDC 侧换密钥/改 scope 会波及微服务鉴权（详见[服务鉴权机制设计 · 决策 8](microservice-service-auth-design.md)） |

> **⚠️ 这里最容易走错的一步**：把"内部令牌"实现成**共享对称密钥**（HMAC）。它在功能上完全跑得通，而且"独立于用户 JWT 密钥"听起来很安全——**但它让每个微服务都获得了签发任意用户令牌的能力**。
> 判断标准只有一条：**校验方手里拿到的材料，能不能用来签发？** 能，就是错的。

机制细节（claims 表、TTL、失败语义、密钥配置、`/api/internal/**` 落地方式）见[服务鉴权机制设计](microservice-service-auth-design.md)。

> **⚠️ 一处不能想当然的地方：`aud` 在两个校验方的规则不同**
> 微服务侧是 `aud == 自己的服务名`（这正是防横向重放的那一条）；但 **core 的 `/api/internal/**` 侧校验的是 `aud ∈ 已注册服务名集合`**——因为令牌本就是 core 签发的，而且它要被**透传回 core**。若在 core 侧写成 `== sfc-core`，"core → 微服务 → core"的链路（阶段 6）会直接断掉。

> **⚠️ 与 OIDC/OAuth 解耦（本轮新增的硬要求）**：委托令牌的密钥、JWKS 端点、作用域**全部自持**，**不依赖** `sys.oidc.*` / `/oauth2/jwks` / `JwtDecoder` / `StandardScopes`。验收方式：**`sys.oidc.enabled=false` 时本链路必须照常工作**。

### 7.4 ⚠️ 安全要点：信任边界（uid 只来自令牌）

`@UID` 与 `@ValidPath` 校验是**在 core 的 Controller 层**生效的，读的是 SecurityContext。代理路径上如果只是无脑转发：

> 攻击者可以构造 `/api/ext/svc/...?uid=999` 越权访问他人网盘资源。

**两侧的约定（本轮表述已加强）**：

| 表述 | 内容 | 性质 |
|---|---|---|
| 弱表述（原方案） | 在令牌中固化 uid，微服务**只信令牌里的 uid，忽略请求参数里的 uid** | **约定式安全**——依赖每个微服务都正确地"忽略"某个参数 |
| **强表述（采用）** | **uid 根本不是一个参数**：微服务业务代码不得读取任何名为 `uid` 的请求参数；core 的 `/api/internal/**` 方法签名里也不出现 uid 参数，一律取自令牌的 `sub` | **结构性安全** |

**为什么强表述重要**：弱表述下，"篡改 uid 越权"是一个**存在但被约定绕过**的入口——只要有一个微服务、一个接口、一次重构忘了忽略它，就是越权；而且这类缺陷**不会在功能测试里暴露**。强表述下这个入口**不存在**。

配套硬约束：微服务**无令牌一律 401（fail-closed）**，**不把"内网不可达"当作安全前提**（它是目标，不是约束）。

### 7.5 流式、故障处理与代理安全

| 事项 | 要求 |
|---|---|
| 请求/响应体 | **必须流式管道**，禁止 `byte[]` 全量缓冲 |
| 超时 | 控制面短超时（如 30s）；下载类单独配置 |
| 实例选择 | `ServiceDiscovery.listInstances()` + 轮询 |
| 实例失效 | 重试下一个实例（限次数，避免放大） |
| 全部不可用 | 返回 **503**（而非 500），并给出明确的业务错误码 |
| 服务不存在 | 返回 404 |

**自建代理的必修安全项**（选择自建代理 = 自己承担网关的安全细节，原评估报告 🟠-2）：

| 细节 | 风险 | 处理 |
|---|---|---|
| hop-by-hop 头（`Connection`、`TE`、`Transfer-Encoding`、`Keep-Alive` 等） | 请求走私（request smuggling） | 转发前按 RFC 7230 **剥离** |
| `Host` / `X-Forwarded-For` / `X-Forwarded-Host` | 下游服务自我认知错误；伪造来源 IP | core **重写** `Host`，**追加**（而非信任）`X-Forwarded-*` |
| 请求体大小上限 | 恶意大请求打满 core | 控制面设上限（数据面已走预签名，不过 core） |
| 目标 URL 构造 | **SSRF** | `serviceName` **必须**命中 `ServiceDiscovery` 已知服务（§7.5 的"服务不存在 → 404"**就是这条防线**，实现处应写明） |
| 静态放行前缀匹配 | 绕过鉴权直达业务 API | 路径规范化（§7.6 硬规则 1） |

### 7.6 路径映射与放行规则（一张表说清 ＋ 两条硬规则）

**映射规则**：

| 浏览器请求 | core 的处理 | 转发到微服务 | 鉴权 |
|---|---|---|---|
| `GET /api/ext/manifest` | 清单端点（**不走代理**） | — | 匿名 |
| `GET /api/service-auth/jwks` | **服务鉴权公钥端点**（阶段 5 提供，不走代理） | — | **匿名（必须）** |
| `/api/ext/{svc}/static/**` | 静态资源代理：`serviceName` ＝ 路径第 1 段，**余部直接映射**（**不**拼 `sfc.api.prefix`） | `/static/**` | 匿名（＋ 路径规范化） |
| 其余 `/api/ext/{svc}/**` | 控制面代理：strip 前缀后，**拼上 metadata 的 `sfc.api.prefix`**（默认 `/api`） | `{prefix}/**` | 需登录 ＋ 委托令牌 |
| `/dl/...`（阶段 9） | **不经过 core**（Nginx 或直连） | 微服务下载端点 | 委托令牌（query） |

> 两条规则**不对称**（静态直连 `/static/**`，接口要拼 `sfc.api.prefix`）是**刻意**的：静态产物是随微服务发布的**物理目录**，而接口前缀是微服务的**自描述**。

**两条必须写死的硬规则**：

| # | 规则 | 不这么做的后果 |
|---|---|---|
| 1 | 放行前缀匹配前**必须做路径规范化**（拒绝 `.`、`..`、`%2e` 及多重编码） | "放行 `/static/**`"会因为 `/api/ext/svc/static/../api/task/create` 这类构造，变成"**放行任意路径**" |
| 2 | **代理 Controller 禁止使用 `@AllowAnonymous`** | `@AllowAnonymous` 的注册粒度是 **controller 方法的 pattern values**（见 `SecurityConfig.getAnonymousUrls()`）。代理 Controller 映射的是 `/api/ext/**`，一旦被标注，**整个控制面代理会一起 permitAll**，§7.4 的红线当场失效。静态放行必须走 `SecurityConfig` 的显式 `requestMatchers` |

---

## 8. 数据面：大文件预签名直连

```
① 浏览器 → POST /api/ext/p2p-download/task/downloadUrl   （经 core 代理，携带委托令牌）
② core   → 鉴权 + 校验文件归属 → 签发「数据面委托令牌」
            （与 §7.3 同一枚令牌，只是换了载体：aud = 目标服务名、资源绑定、TTL 5 分钟）
③ core   → 返回 { url: "http://<微服务可达地址>:<port>/dl/<token>" }
④ 浏览器 → 直连微服务下载（流量不过 core）
⑤ 微服务 → 用「同一套 JWKS 验签逻辑」校验 query 里的令牌（不新增校验实现）
```

**这不是第二套机制，而是同一枚令牌的第三种载体**：

| | 控制面（§7） | 内部回调（档位 2） | 数据面（本节） |
|---|---|---|---|
| 载体 | `Authorization: Bearer` | `Authorization: Bearer`（透传） | URL query |
| TTL | ≤ 60 秒 | 随原令牌 | 5 分钟（`download-token-ttl-seconds` 可配） |
| 额外绑定 | — | — | 资源标识（文件/任务） |
| 签发时机 | 每请求现签 | 不签发 | 生成下载地址时签发 |

> **⚠️ "一次性 token"必须收回或补状态**：原文写的是"签发**一次性** token"，但机制里没有任何 nonce / 状态存储——签出来的 JWT 在有效期内**可以无限次重放**。
> **建议改为"短时效 ＋ 资源绑定"**（与既有文件临时链接的语义一致）；若确实需要一次性，微服务侧必须补 nonce 存储与原子消费，多一个有状态组件。详见[服务鉴权机制设计 · 决策 6](microservice-service-auth-design.md)。

> **URL 载体的固有风险**：query 里的令牌会经 `Referer`、浏览器历史、Nginx access log 与各类代理日志泄漏 ⇒ 必须**短 TTL ＋ 资源绑定 ＋ 只读 scope**，且**不得**复用控制面用的高权限 scope。

### 部署前提（必须点明）

> 预签名直连要求**微服务的下载端点对浏览器可达**。若微服务只在 core 可达的容器内网，此方案不成立。

| 部署形态 | 做法 | 评价 |
|---|---|---|
| 局域网 / NAS 同网段 | 直接返回微服务地址 | ✅ 简单 |
| **前置 Nginx / 反代** | **让 Nginx 把 `/dl/{service}/**` 直接路由到微服务**，绕过 core 的 Java 进程 | ✅✅ 最优：core 零带宽零线程占用 |
| 仅 core 可达 | ❌ 预签名不可行 | 退回 §7 的流式代理 |

**因此 §7 的流式代理实现不能省**——它既是降级路径，也承接控制面本身。

> **⚠️ URL 模板必须统一（原评估报告 🟠-3）**：两种部署形态下的 URL 形态不同——直连是 `/dl/{token}`（无服务名段），Nginx 路由是 `/dl/{service}/{token}`（含服务名段）。
> 这**不是**两套签名逻辑，只是**同一个配置项**（如 `sys.service.gateway.download-url-template`）切换的拼接模板。**不要**在代码里写死两套分支。
> 实现时机：阶段 9（见[路线图 9.4](microservice-roadmap.md)）。

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

### 11.1 后端 `sfc-core`

| 文件 | 动作 | 职责 |
|---|---|---|
| `.../model/ExtensionInfo.java` + `ExtensionResource.java` | 新增 | 前端契约 DTO，字段严格对齐 TS 的 `ExtensionInfo` |
| `.../service/registry/ServiceExtensionRegistry.java` | 新增 | 从 Nacos metadata 聚合扩展清单 |
| `.../controller/ext/ServiceExtensionController.java` | 新增 | `GET /api/ext/manifest`，`@AllowAnonymous` |
| `.../controller/ext/ServiceProxyController.java` | 新增 | `@RequestMapping("/api/ext/**")`，无 `{}` 占位符 |
| `.../service/auth/ServiceAuthProperties.java` | 新增 | 绑定 `sys.service.auth.*`：私钥解析（base64(DER)/PEM）、格式校验、profile 判定 |
| `.../service/auth/DelegationTokenIssuer.java` | 新增 | 委托令牌**签发**（**自持 RSA ＋ jjwt**，RS256、`kid` ＝ 公钥指纹、`aud` 绑定、TTL ≤ 60s）——见[服务鉴权机制设计](microservice-service-auth-design.md) |
| `.../service/auth/DelegationTokenVerifier.java` | 新增 | 委托令牌**校验**（**自持**验签，不经 OIDC 的 `JwtDecoder`：`iss` / `exp` / `scope`，`aud ∈ 已注册服务名`） |
| `.../constant/ServiceAuthScopes.java` | 新增 | 独立作用域常量（`internal.read` / `internal.write`），**不复用** `StandardScopes` |
| `.../controller/ServiceAuthJwksController.java` | 新增 | `GET /api/service-auth/jwks`，**匿名**，返回公钥数组 |
| `.../config/security/ServiceAuthSecurityConfig.java` | 新增 | `/api/internal/**` 过滤器链，**始终注册**，**无任何 OIDC 条件注解** |
| `sfc-core/pom.xml` | 改 | 加 HTTP 客户端依赖（复用已有的 okhttp 或 `RestTemplate`） |
| 三个配置文件 | 改 | 新增 `sys.service.gateway.*`（超时、启用开关、dev-manifest-url；阶段 9 的下载 URL 模板）与 `sys.service.auth.*`（阶段 5 的服务鉴权配置）。**AGENTS.md 要求同步 `application-develop.yml`、`application-product.yml`、`pre-release/config.yml`，不改 `application.yml`** |
| `SecurityConfig.java` | 改 | 放行 `/api/service-auth/jwks`（**匿名，必须是 public**）与 `/api/ext/manifest`、`/api/ext/*/static/**`；静态放行**必须做路径规范化**，且**代理 Controller 禁止用 `@AllowAnonymous`**（`/api/ext/**` 会整体 permitAll，见 §7.6 硬规则 2）。注意这是**非 OIDC 类**——本机制对 OIDC 代码零改动 |

> `ExtensionInfo` 放在 `sfc-core` 而非 `sfc-cloud-api`：它是**前端契约**，不是注册中心 SPI，放瘦契约模块会污染其边界。

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
| Nacos metadata | 声明 `sfc.*` 自描述字段 |
| 鉴权校验 | 从 `Authorization: Bearer` 取**委托令牌** → 公钥取自 **`GET /api/service-auth/jwks`**（本地缓存，按 `kid` 命中；**未知 `kid` 必须回源一次**）→ 校 `iss` / `aud == 自己` / `exp` / `scope`；**uid 只取 `sub`**；**无令牌一律 401（fail-closed）**。可用瘦库 `sfc-cloud-auth`（内部 jjwt，只验签） |
| 静态放行 | 仅 `/static/**` 与健康检查匿名；放行前**必须路径规范化**（见 §7.6 硬规则 1） |

---

## 12. 分步实施与验证

**Step 1（后端，可独立验证）**：`ExtensionInfo` DTO + `ServiceExtensionRegistry` + `GET /api/ext/manifest`
- 验证：`registry-provider: local` 下返回空数组 `[]`（不破坏现状——`local` provider 只会返回 `sfc-core` 自身，而 core 没有扩展清单）

**Step 2（后端）**：`ServiceProxyController` 静态资源代理 + Nacos metadata 解析
- 验证：起一个只提供 `GET /static/hello.js` 的假微服务，确认经代理可获取

**Step 3（前端）**：新增数据源 + 构建输出参数化
- 验证：清单返回后，DevTools Network 能看到 bundle 被加载；**现有 jar 插件加载行为无回归**

**Step 4（控制面）**：控制面代理 + **委托令牌现签** + 信任边界（§7.4 / §7.6）
- 验证：`curl /api/service-auth/jwks` **匿名可达**，返回的公钥能被离线验签通过
- 验证：**四条拒绝路径必须逐条实测** —— ①无令牌 → 401；②令牌 `aud` 不符 → 401；③令牌已过期 → 401；④`scope` 不足 → 403。另需验证"篡改 uid 的越权请求必然被拒"（因为 uid 根本不是参数）
- 验证：**`sys.oidc.enabled=false` 时本链路照常工作**（独立性验收）；**`sys.service.auth.enabled=false` 时 `/api/internal/**` 返回 401**（不落到主链）
- 验证：静态放行的路径规范化（`/api/ext/svc/static/../api/...` 必须被拒）

**Step 5（数据面）**：数据面令牌（**同一枚委托令牌换载体**）+ 预签名下载
- 验证：大文件下载时确认 **core 进程带宽/线程无显著增长**；query 里的令牌被篡改/过期时微服务返回 401

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
| 1 | **越权访问他人网盘数据**（代理层只转发、或 uid 来自请求参数） | ⚠️ **数据越权** | §7.4：**uid 根本不是参数**——只取令牌 `sub`；`/api/internal/**` 方法签名里不出现 uid 参数 |
| 2 | 静态资源长缓存 + 固定文件名 | 微服务升级后前端加载旧版本 | 清单 URL 带 `?v={version}` |
| 3 | 清单端点故障 | 拖垮整个前端启动 | `fetchRemoteExtensionManifest()` 失败降级返回 `[]` |
| 4 | 微服务前端 bundle 加载失败 | 微服务功能不可用 | 复用 `mount()` 已有的 `logWarning`/`logError` 与 Promise reject |
| 5 | 代理无流式 | 大响应 OOM | §7.5：强制流式管道 |
| 6 | 预签名地址浏览器不可达 | 下载失败 | §8：三种部署形态；保留流式代理作为降级 |
| 7 | 多实例 metadata 不一致 | 清单与实际入口不符 | 部署约定：同一服务所有实例 metadata 必须一致 |
| 8 | 前端改动引入回归 | 现有 jar 插件加载异常 | §11.2 全部为增量改动；Step 3 明确回归验证项 |
| 9 | ⚠️ **微服务 fail-open**（无令牌即放行，依赖"内网不可达"） | 内网边界一旦被打破就是**任意读** | §7.4 硬约束：**无令牌一律 401**；把"内网不可达"当**目标**而非约束 |
| 10 | ⚠️ **静态放行被路径穿越绕过** | "放行 `/static/**`" 变成"放行任意路径" | §7.6 硬规则 1：放行前**路径规范化**；**代理 Controller 禁用 `@AllowAnonymous`**（硬规则 2） |
| 11 | **微服务漏校验 `aud`** | 给 A 的令牌被重放给 B（跨服务越权） | 提供"只验签瘦库"＋线协议文档；验收项含"`aud` 不符必须 401" |
| 12 | **私钥被提交进仓库**（配置式密钥的固有风险） | 私钥泄漏 ⇒ 任何人可伪造委托令牌 | 生产**只用环境变量注入**；`application-product.yml` 只留 `${SERVICE_AUTH_SIGNING_KEY:}` 占位符；部署说明写明（[服务鉴权机制设计 §10](microservice-service-auth-design.md) 风险 1） |
| 13 | **集群各 core 节点密钥不一致** | LB 之后"A 节点签发、B 节点出 JWKS"验签失败 | 部署约束：所有节点配**同一把**密钥；验收项含"任一节点签发、任一节点出 JWKS 均能验签通过" |
| 14 | 数据面 URL 经 `Referer` / 日志泄漏 | 令牌被他人使用 | 短 TTL（**5 分钟**）＋ 资源绑定 ＋ **只读 scope**；不复用控制面高权限 scope |

---

## 14. 学习点

| 学习点 | 在哪被逼出来 |
|---|---|
| **网关模式与同源策略** | §3、§7：为什么前端必须只跟 core 说话 |
| **服务自描述（metadata 驱动）** | §5.3：core 不硬编码任何微服务，靠 Nacos metadata 实现"注册即接入" |
| **鉴权边界与信任传递** | §7.3、§7.4：委托令牌、为什么"共享对称密钥"等于把**签发权**也交给了校验方、为什么要把"忽略 uid 参数"升级成"uid 不是参数" |
| **非对称签名的"能力不对称"** | §7.3：公钥可以随便分发、私钥不能——"密钥怎么安全送给微服务"这个问题因此**消失**，而不是被解决 |
| **解耦做没做到，如何验证** | §7.3：`sys.oidc.enabled=false` 时本链路必须照常工作——一个开关就能检验"解耦"是真做到了还是只写在文档里 |
| **控制面 / 数据面分离** | §8：不是所有流量都该过网关，这是架构分层而非实现细节 |
| **缓存失效策略** | §6.3：长缓存 + 固定文件名的经典冲突，用版本化 URL 解决 |
| **浏览器同源模型的细节** | §9：`<script src>` 不受 CORS 限制——知道这个才能设计出"开发期直连"的便利 |
| **模块联邦式的扩展机制** | §2.3：项目已有"数据驱动扩展加载"设计，本档位是把它激活而非新造 |

---

## 15. 审阅时建议重点看这五处

1. **§5.1 为什么不用 `/static-extension.json`**——这是最容易走错的一步。它看起来"现成"，但 `vite.config.ts` 只代理 `/api`，开发模式下打不到后端。请确认你认同改用 `/api/ext/manifest`。
2. **§7.3 的鉴权机制选型**——这是**安全主线**。请特别确认这一条判断：**微服务持有"共享对称内部密钥"= 微服务也能签发任意用户的令牌**，因此必须用非对称签名（core 持私钥、微服务只拿公钥）。机制细节见[服务鉴权机制设计](microservice-service-auth-design.md)。
3. **§7.4 / §7.6 的信任边界与放行规则**——§7.4 把"忽略 uid 参数"升级为"**uid 不是参数**"；§7.6 有两条硬规则（路径规范化、代理 Controller 禁用 `@AllowAnonymous`），其中第二条若不遵守，**整个控制面代理会一起 permitAll**。
4. **§10 UMD 外部依赖约束**——它决定了微服务前端**不能**是独立 SPA 页面，也决定了 iframe 方案的真实成本。这是个容易被忽略但影响架构选择的事实。
5. **§4.4 源码位置的取舍**——保留了主前端仓库的源码位置，只做产物独立。请确认这符合你对"独立发布"的预期。

---

## 16. 与 jar 插件的对比

| 维度 | jar 插件 | 微服务扩展 |
|---|---|---|
| 前端产物位置 | `sfc-ext/{id}/src/main/assert/static/` → 打进插件 jar | 微服务自己的 `static/` |
| 前端资源供给 | `GET /api/plugin/{name}/resource/**`（classpath） | `GET /api/ext/{svc}/static/**`（HTTP 代理） |
| 前端发现方式 | `autoLoad.js` 内联拼接 | `/api/ext/manifest` 清单 + 直接 URL |
| 后端位置 | 同进程（merge classloader） | 独立进程 |
| 后端接口路径 | 直接注册进 core 的 `RequestMappingHandlerMapping` | 经 core 反向代理 |
| 能力上限 | 可注入任意 Spring Bean，含 core 内部服务 | 只能通过内部 API 访问 core 能力（档位 2） |
| 前端框架契约 | `window.context` / `SfcUtils` / `bootContext` | **完全相同** ✅ |
| 独立发布 | ❌ 需部署进 core 的 `ext/` | ✅ 独立发布独立重启 |
| 故障隔离 | ❌ 插件崩溃拖垮 core | ✅ 进程隔离 |
| 升级是否需重启 core | ❌ 需要 | ✅ 不需要 |

> **刻意的设计选择**：前端契约（`window.context`、`bootContext.addProcessor`、`sfc-common` 外部化）**在两种形态间完全一致**。开发者学一次，两边都能用；已有插件前端代码稍作调整即可迁移到进程外。
