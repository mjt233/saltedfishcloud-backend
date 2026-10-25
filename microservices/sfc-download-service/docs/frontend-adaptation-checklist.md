# 前端适配清单（sfc-ext-download 插件 → sfc-download-service 微服务）

> 面向：调用离线下载接口的前端工程。
> 背景：后端 `sfc-ext-download` 已从宿主插件改造为独立微服务 `microservices/sfc-download-service`，
> 按约定**不新增网关显式路由**，前端使用网关默认动态路由访问；前端 URL 由前端侧手动调整。

---

## 一、必须改：API 基址

旧地址（插件期，core 直接提供）：

```
/api/task/download
/api/task/download/proxy
```

新地址（网关默认动态路由：`/api/service/{serviceId}/**` 剥离前缀并补 `/api` 后转发）：

```
/api/service/sfc-download-service/task/download
/api/service/sfc-download-service/task/download/proxy
```

- 前端原来把 `/task/download` 作为接口前缀的地方，改为 `/service/sfc-download-service/task/download`
  （若前端 SDK 会自动补 `/api`，则只需把插件前缀改为 `/service/sfc-download-service/task/download`）。
- 过渡期：未修改前，旧地址 `/api/task/download/**` 会落到 core 的兜底路由并返回 **404**（core 已不再加载该插件），属预期。
- 请求方法、参数、请求体均不变：
  - `POST`：`{url, uid, savePath, headers?, method?, proxy?, connectTimeout?, readTimeout?}`
  - `GET`：`uid, page, size, type(ALL/DOWNLOADING/FINISH/FAILED)`
  - `DELETE`：`taskId`（多余的 `uid` 参数会被忽略）
  - `GET /proxy`：代理节点列表（本前端此前调用的端点当时后端不存在，现在已补齐）

## 二、不用改：响应结构与字段

| 项 | 说明 |
|---|---|
| 响应信封 | 仍为 `{code, businessCode, msg, data}`。业务异常与宿主一致：HTTP 状态可能仍为 200，业务结果以信封 `code` 为准（与 core 现有接口行为一致） |
| 分页结构 | ⚠️ **字段有变化**：旧插件直接返回 Spring `Page` JSON（`content` / `totalElements` / `totalPages` / …）；新版统一返回 `SfcPageInfo`（`content` / `totalCount` / `totalPage`）。`content` 字段名不变，若前端只用 `data.content` 则无需改动；若用了 `totalElements`/`totalPages`，请改为 `totalCount`/`totalPage` |
| 列表项字段 | `id / uid / url / proxy / message / loaded / size / speed / name / savePath / createdAt / finishAt / taskId / asyncTaskRecord{id,status}` 均保留 |
| 状态语义 | `asyncTaskRecord.status`：0 等待、1 运行、2 完成、3 失败、4 取消、5 离线（与 core 一致） |
| 任务状态筛选 | `type` 取值与含义不变 |

⚠️ 与宿主统一的序列化约定：**ID 字段与数值字段在 JSON 中为字符串**
（`id`、`taskId`、`asyncTaskRecord.id`、`size`、`loaded`、`speed`、`totalCount`、`totalPage` 等），
`asyncTaskRecord.status` 为数字。这与 core 现有 `JacksonConfig` 的行为一致，前端原有数值兼容逻辑可继续工作；
但请勿用 `=== 0` 这类严格比较判断字符串数值。

## 三、前端资源加载（无需改）

- `index.umd.js` / `style.css` 随本服务发布，仍然通过 core 的 `GET /api/plugin/autoLoad.js` 合并加载
  （core 按注册中心 metadata `sfc.plugin.autoload` 从服务 `/static/**` 拉取），主前端加载链路不变。
- 本服务**不再提供** `plugin-info.json` / 配置面板：离线下载没有插件配置项，管理员后台不会出现对应配置节点。
- 前端构建方面（前端仓库侧人工处理）：旧构建脚本把产物复制到 `sfc-ext/sfc-ext-download/src/main/assert/static/`，
  该目录已随插件删除；需改为输出到 `microservices/sfc-download-service/src/main/resources/static/`。

## 四、数据与部署

1. **历史数据不迁移**：下载任务改存本服务独立 SQLite `download.db`；`xyy.db` 中的历史下载记录不会自动出现，
   需保留请人工导出/导入。
2. **部署必须删除旧插件 jar**：运行目录 `ext/sfc-ext-download.jar`（及 `release/ext-available/` 同名产物）不删除会导致
   core 继续加载旧插件（重复任务执行者 + 前端资源重复加载）。
3. `SFC_INTERNAL_KEY`：core 与 sfc-download-service 必须配置同一份（≥32 字符），否则任务提交/进度校准/中断不可用。
4. 离线下载服务只允许内网/本机暴露，对外入口只有网关；前端不要直连 8092。

## 五、联调前置

| 依赖 | 要求 |
|---|---|
| `sfc-core` | 已启动并注册（local registry 或 Nacos，group=SFC/namespace 空串） |
| `sfc-download-service` | 已启动（8092）并注册；Worker 日志显示 `offline-download` 类型已声明 |
| `sfc-gateway` | 已启动；默认动态路由生效即可（无需为本服务加显式路由） |
| 登录态 | 创建/列表/中断需要登录用户；`uid=0`（公共网盘）写入仅管理员 |
