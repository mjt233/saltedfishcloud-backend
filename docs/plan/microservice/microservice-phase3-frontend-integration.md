# 微服务改造 · 档位 3 设计方案（前端接入与接口调用）

> **状态**：设计定稿，**尚未产生任何代码改动**。等待审阅。
> **范围**：微服务的前端资源接入 + 接口调用链路
> **依赖**：档位 1 的 `ServiceDiscovery`（必需）。**不依赖档位 2**，可提前实施
> **配套**：[档位 1 设计方案](microservice-phase1-design.md) · [依赖冲突调试手册](microservice-nacos-dependency-debugging.md)
> **创建日期**：2026-02-12

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
│   ServiceProxyController    ←─ 反代 + 内部服务令牌 + UID/PATH 校验  │
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
| ② | 静态资源代理 | `@AllowAnonymous` | ✅ 长缓存 + URL 版本号 |
| ③ | 控制面 API 代理 | 需登录 + UID/PATH 校验 | 不缓存 |
| ④ | 大文件预签名 | 一次性 token | 由微服务决定 |

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

### 7.3 鉴权：内部服务令牌

```
前端请求 → core 已通过 JWT 鉴权 + @UID/@ValidPath 校验
        → core 签发短时效内部令牌 { uid, roles, scope, exp }
        → header: X-Sfc-Internal-Token
        → 微服务用共享内部密钥校验
```

**为什么不让微服务直接验用户 JWT**：

| 做法 | 问题 |
|---|---|
| 微服务共享 core 的 JWT 签名密钥 | 密钥扩散面变大，**微服务被攻破 = core 被攻破** |
| 微服务每请求回调 core 校验 | 每个请求多一跳，core 压力翻倍 |
| **内部服务令牌（推荐）** | 独立密钥、短时效、只授予必要 scope |

### 7.4 ⚠️ 安全要点：UID / PATH 校验语义必须保留

`@UID` 与 `@ValidPath` 校验是**在 core 的 Controller 层**生效的。代理路径上如果只是无脑转发：

> 攻击者可以构造 `/api/ext/svc/...?uid=999` 越权访问他人网盘资源。

**因此代理层必须**：
- 对代理请求执行与普通接口一致的 UID/PATH 校验
- 或在内部令牌中固化 uid，微服务**只信令牌里的 uid**，忽略请求参数里的 uid

第二种更稳妥（**信任边界收敛到令牌**），推荐。

### 7.5 流式与故障处理

| 事项 | 要求 |
|---|---|
| 请求/响应体 | **必须流式管道**，禁止 `byte[]` 全量缓冲 |
| 超时 | 控制面短超时（如 30s）；下载类单独配置 |
| 实例选择 | `ServiceDiscovery.listInstances()` + 轮询 |
| 实例失效 | 重试下一个实例（限次数，避免放大） |
| 全部不可用 | 返回 **503**（而非 500），并给出明确的业务错误码 |
| 服务不存在 | 返回 404 |

---

## 8. 数据面：大文件预签名直连

```
① 浏览器 → POST /api/ext/p2p-download/task/downloadUrl   （经 core 代理）
② core   → 鉴权 + 校验文件归属 → 签发一次性 token（含 uid、文件、过期时间）
③ core   → 返回 { url: "http://<微服务可达地址>:<port>/dl/<token>" }
④ 浏览器 → 直连微服务下载（流量不过 core）
```

### 部署前提（必须点明）

> 预签名直连要求**微服务的下载端点对浏览器可达**。若微服务只在 core 可达的容器内网，此方案不成立。

| 部署形态 | 做法 | 评价 |
|---|---|---|
| 局域网 / NAS 同网段 | 直接返回微服务地址 | ✅ 简单 |
| **前置 Nginx / 反代** | **让 Nginx 把 `/dl/{service}/**` 直接路由到微服务**，绕过 core 的 Java 进程 | ✅✅ 最优：core 零带宽零线程占用 |
| 仅 core 可达 | ❌ 预签名不可行 | 退回 §7 的流式代理 |

**因此 §7 的流式代理实现不能省**——它既是降级路径，也承接控制面本身。

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
| `.../service/registry/InternalServiceTokenService.java` | 新增 | 内部服务令牌签发 / 校验 |
| `sfc-core/pom.xml` | 改 | 加 HTTP 客户端依赖（复用已有的 okhttp 或 `RestTemplate`） |
| 三个配置文件 | 改 | 新增 `sys.service.gateway.*`（超时、启用开关、dev-manifest-url）。**AGENTS.md 要求同步 `application-develop.yml`、`application-product.yml`、`pre-release/config.yml`，不改 `application.yml`** |
| `SecurityConfig.java` | 改 | 放行 `/api/ext/*/static/**` 与 `/api/ext/manifest` |

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
| 内部令牌校验 | 校验 `X-Sfc-Internal-Token` |

---

## 12. 分步实施与验证

**Step 1（后端，可独立验证）**：`ExtensionInfo` DTO + `ServiceExtensionRegistry` + `GET /api/ext/manifest`
- 验证：`registry-provider: cluster` 下返回空数组 `[]`（不破坏现状）

**Step 2（后端）**：`ServiceProxyController` 静态资源代理 + Nacos metadata 解析
- 验证：起一个只提供 `GET /static/hello.js` 的假微服务，确认经代理可获取

**Step 3（前端）**：新增数据源 + 构建输出参数化
- 验证：清单返回后，DevTools Network 能看到 bundle 被加载；**现有 jar 插件加载行为无回归**

**Step 4（控制面）**：控制面代理 + 内部令牌 + UID/PATH 校验
- 验证：越权请求（篡改 uid）必须被拒

**Step 5（数据面）**：预签名下载
- 验证：大文件下载时确认 **core 进程带宽/线程无显著增长**

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
| 1 | **UID/PATH 校验在代理层被绕过** | ⚠️ **越权访问他人网盘数据** | §7.4：信任边界收敛到内部令牌，微服务只信令牌中的 uid |
| 2 | 静态资源长缓存 + 固定文件名 | 微服务升级后前端加载旧版本 | 清单 URL 带 `?v={version}` |
| 3 | 清单端点故障 | 拖垮整个前端启动 | `fetchRemoteExtensionManifest()` 失败降级返回 `[]` |
| 4 | 微服务前端 bundle 加载失败 | 微服务功能不可用 | 复用 `mount()` 已有的 `logWarning`/`logError` 与 Promise reject |
| 5 | 代理无流式 | 大响应 OOM | §7.5：强制流式管道 |
| 6 | 预签名地址浏览器不可达 | 下载失败 | §8：三种部署形态；保留流式代理作为降级 |
| 7 | 多实例 metadata 不一致 | 清单与实际入口不符 | 部署约定：同一服务所有实例 metadata 必须一致 |
| 8 | 前端改动引入回归 | 现有 jar 插件加载异常 | §11.2 全部为增量改动；Step 3 明确回归验证项 |

---

## 14. 学习点

| 学习点 | 在哪被逼出来 |
|---|---|
| **网关模式与同源策略** | §3、§7：为什么前端必须只跟 core 说话 |
| **服务自描述（metadata 驱动）** | §5.3：core 不硬编码任何微服务，靠 Nacos metadata 实现"注册即接入" |
| **鉴权边界与信任传递** | §7.3、§7.4：内部服务令牌、为什么不能共享用户 JWT、UID 校验为何会丢 |
| **控制面 / 数据面分离** | §8：不是所有流量都该过网关，这是架构分层而非实现细节 |
| **缓存失效策略** | §6.3：长缓存 + 固定文件名的经典冲突，用版本化 URL 解决 |
| **浏览器同源模型的细节** | §9：`<script src>` 不受 CORS 限制——知道这个才能设计出"开发期直连"的便利 |
| **模块联邦式的扩展机制** | §2.3：项目已有"数据驱动扩展加载"设计，本档位是把它激活而非新造 |

---

## 15. 审阅时建议重点看这四处

1. **§5.1 为什么不用 `/static-extension.json`**——这是最容易走错的一步。它看起来"现成"，但 `vite.config.ts` 只代理 `/api`，开发模式下打不到后端。请确认你认同改用 `/api/ext/manifest`。
2. **§7.4 UID/PATH 校验语义**——这是**安全红线**。代理层如果只做转发，就会把 core 已有的越权防护整个绕过去。请确认"信任边界收敛到内部令牌"的设计。
3. **§10 UMD 外部依赖约束**——它决定了微服务前端**不能**是独立 SPA 页面，也决定了 iframe 方案的真实成本。这是个容易被忽略但影响架构选择的事实。
4. **§4.4 源码位置的取舍**——保留了主前端仓库的源码位置，只做产物独立。请确认这符合你对"独立发布"的预期。

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
