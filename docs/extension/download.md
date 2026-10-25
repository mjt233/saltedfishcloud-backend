# 离线下载 download（微服务版）

## 简介

离线下载能力已从「宿主插件」改造为**独立微服务** `microservices/sfc-download-service`（原 `sfc-ext-download` 插件已从仓库删除）。
该服务提供基于 HTTP/HTTPS 协议的离线下载：把远程文件下载到用户网盘指定目录，支持代理节点、实时进度与任务中断。

- 服务端口：`8092`
- 数据存储：本服务独立 SQLite `download.db`（历史插件数据在宿主 `xyy.db`，**不迁移**）
- 任务执行：以 `SERVICE` 角色接入 `sfc-core` 的统一异步任务系统（任务类型 `offline-download`）
- 访问入口：经 `sfc-gateway` 的默认动态路由 `/api/service/sfc-download-service/task/download/**`
- 部署：`microservices/sfc-download-service/README.md`；前端适配：其 `docs/frontend-adaptation-checklist.md`
- 微服务总览：`docs/develop/microservice/index.md`

## 主要功能

- 通过 URL 创建下载任务，文件自动保存到网盘指定目录
- 支持 HTTP 代理下载（代理节点仍在宿主侧的代理管理中配置，创建任务时按名称解析并快照）
- 查看下载进度（实时速度、已下载量、总大小）与任务状态
- 中断等待中/运行中的下载任务
- 按用户查询下载任务列表，并可按状态筛选（ALL / DOWNLOADING / FINISH / FAILED）
- 自动处理文件名冲突（重名文件追加 `.1`、`.2` 等后缀）
- 前端插件资源（`index.umd.js` / `style.css`）随服务发布，仍由 `sfc-core` 的 `plugin/autoLoad.js` 合并加载

## 配置与部署

1. 启动前置：`sfc-core` 已运行；服务与 core 配置**同一份** `SFC_INTERNAL_KEY`（≥32 字符）。
2. 启动服务：`java -jar microservices/sfc-download-service/target/sfc-download-service-1.0.0.jar`
   （建议 cwd 放在仓库外用目录，避免 `download.db` 落在源码仓库中）。
3. 删除旧的插件 jar：运行目录 `ext/sfc-ext-download.jar`（以及 `release/ext-available/` 下同名产物）必须清理，
   否则 core 仍会加载旧插件，造成重复执行者和前端资源重复加载。
4. 前端把接口地址调整为 `/api/service/sfc-download-service/task/download/**`（旧 `/api/task/download/**` 会 404）。

## 已知问题与说明

- **代理契约**：旧版前端创建表单提交的是代理「名称」，而旧插件后端按「id」解析，且 `/api/task/download/proxy`
  端点在旧插件中并不存在（前端拿不到代理列表）。微服务版已按现有前端契约在后端适配：服务提供 `/proxy` 列表接口，
  创建任务时按名称解析代理并快照地址/端口。
- **历史数据不迁移**：插件期下载记录在宿主 `xyy.db`，微服务使用独立 `download.db`，不会自动出现；如需保留请人工迁移。
- **任务不重试/不续传**：执行失败即失败（由异步任务框架的故障转移策略决定是否重新排队，重新执行会从头下载）。
- 本服务信任网关注入的 `X-Sfc-Uid`，只允许内网/本机暴露，对外入口只有网关。
