# 微服务概述

咸鱼云的微服务工程统一放在仓库根目录的 `microservices/` 下，用于把部分后端能力拆分为独立进程开发、部署。

这些工程与仓库内其它模块有两个关键区别：

- **不继承 root `pom.xml`**：父 POM 是 `microservices/pom.xml`，而它的父 POM 才是 `spring-boot-starter-parent`，因此不会被拖入咸鱼云宿主约 200+ 个 jar 的技术栈；
- **零宿主依赖**：不依赖任何 `com.xiaotao.saltedfishcloud:*` 坐标。允许的仓库内依赖是**共享库** `com.sfc.cloud:sfc-common`（轻量：常量/信封/异常/工具/分页/远程契约）、`com.sfc.cloud:sfc-common-data`（JPA 基座）、**local 注册中心客户端 SDK** `com.sfc.cloud:sfc-local-registry-client`，以及**内部接口鉴权 SDK** `com.xiaotao:sfc-auth`。⚠️ 这些模块均**位于仓库根目录**且**不继承任何 parent**，自带 BOM；`sfc-common` 还必须保持零 JPA 依赖（网关无 JPA）。

## 工程清单

| 工程 | 作用 |
|---|---|
| [`sfc-common`](../../../sfc-common/) | 共享库（非可运行进程，**位于仓库根目录**）：信任头常量、用户凭证解析、远程调用契约模型，以及 Phase 1 迁出的响应信封 `SfcJsonResult`、异常族、常量、字符串/JSON 工具、分页模型 `SfcPageInfo`/`SfcPageableRequest` |
| [`sfc-common-data`](../../../sfc-common-data/) | 共享库（非可运行进程，**位于仓库根目录**）：JPA 数据访问基座（实体基类、`SfcBaseRepo`、`SfcCrudService(Impl)`、雪花主键、`SfcDBUtils`、`@SfcUID`），身份与事务经 SPI 注入 |
| [`sfc-local-registry-client`](../../../sfc-local-registry-client/) | 共享 SDK（非可运行进程，**位于仓库根目录**）：`spring.cloud.sfc-local.*` 配置下的 local 注册中心注册与发现实现 |
| `sfc-remote-client` | 远程客户端 SDK（库）：调用 sfc-core 网盘文件操作的 OpenFeign 声明式接口 |
| `sfc-task-worker` | 异步任务执行者 SDK（库）：微服务实现 `SfcTaskHandler` 即可接入 sfc-core 统一的异步任务调度（见[接入文档](async-task-worker.md)） |
| `sfc-gateway` | 独立网关（Spring Cloud Gateway Server WebFlux）：整站唯一入口与微服务代理 |
| `sfc-demo-service` | 微服务示例：演示最简工程结构、注册发现、远程调用、接口鉴权与异步任务接入 |
| [`sfc-ai-service`](../../../microservices/sfc-ai-service/) | **AI 助手服务**（端口 `8091`）：原宿主插件 `sfc-ext-ai`，Phase 2 迁为独立微服务——WebSocket 流式 AI 对话 + LLM 提供商/模型 CRUD，**独立 SQLite `ai.db`**，身份取自 `X-Sfc-Uid`，网盘操作经 `sfc-remote-client` 回调 core。前端影响见其[适配清单](../../../microservices/sfc-ai-service/docs/frontend-adaptation-checklist.md) |
| [`sfc-download-service`](../../../microservices/sfc-download-service/) | **离线下载服务**（端口 `8092`）：原宿主插件 `sfc-ext-download` 迁为独立微服务——创建/查询/中断离线下载任务，HTTP/HTTPS 下载到网盘（支持代理、进度、中断），任务经 `sfc-task-worker` 以 `SERVICE` 角色执行；**独立 SQLite `download.db`**，网盘落盘经 `sfc-remote-client` 回调 core；经网关默认动态路由 `/api/service/sfc-download-service/task/download/**` 访问（前端 URL 需手动调整），前端影响见其[适配清单](../../../microservices/sfc-download-service/docs/frontend-adaptation-checklist.md) |

依赖方向：`sfc-common` → `sfc-common-data` / `sfc-auth` / `sfc-remote-client` / `sfc-task-worker` / `sfc-gateway` / `sfc-demo-service` / `sfc-download-service`。其中 `sfc-task-worker` 同时被宿主 `sfc-core-task` 依赖，宿主 core 与微服务共用同一套任务执行侧实现。

## 构建前置

共享库 `sfc-common` / `sfc-common-data`、内部接口鉴权 SDK `sfc-auth` 与 local 注册中心 SDK `sfc-local-registry-client` **都不继承根 pom**，但都已由根 `<modules>` 聚合。因此**推荐从仓库根做单次 reactor 构建**（reactor 内部会互相解析 `target` 产物，无需 `install`）：

```shell
mvn clean package -DskipTests
```

⚠️ **不要**再单独构建 `microservices/pom.xml`：`sfc-common` 已不在其 reactor 内，那样会从**本地仓库**解析，可能拿到旧快照（静默错误）。同理不要用 `-rf` 从中途恢复。

## 学习路径

1. [搭建最简的微服务](minimal-service.md) —— 从零建一个可注册、可被网关访问的服务；
2. [网关路由](gateway-routing.md) —— 网关如何把请求转发到服务接口；
3. [服务发现与注册](service-discovery.md) —— Nacos 的基本配置；
4. [远程调用](remote-call.md) —— 用 OpenFeign 调用 sfc-core；
5. [接口鉴权机制](authentication.md) —— 用户凭证透传与服务间内部接口鉴权；
6. [异步任务 Worker 接入](async-task-worker.md) —— 把微服务的耗时任务纳入 sfc-core 统一异步任务调度。
