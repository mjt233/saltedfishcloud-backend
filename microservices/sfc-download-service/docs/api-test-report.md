# 接口测试报告：多类型下载改造（HTTP / GIT / MAGNET / TORRENT / ED2K）

> 测试对象：`microservices/sfc-download-service`（多类型下载改造后的构建）
> 测试方式：真实启动 core + 下载服务 + aria2，直接对服务端口发 HTTP 请求（不经网关，用 `X-Sfc-Uid` 模拟网关信任头）
> 报告版本：**v3（D1 / D2 / D4 全部修复后的最终复测）**
> 结论速览：**25 PASS / 0 FAIL / 0 SKIP**（共 25 个用例）。首轮发现的 D1、D2、D4 三个缺陷均已修复并通过复测验证；MAGNET 已能完整下载 129MB 并正确落盘。

---

## 一、测试环境快照

| 组件 | 版本 / 地址 | 说明 |
|---|---|---|
| sfc-core | `http://localhost:8087`，`sfc-core-3.2.0.jar` | cwd=仓库根，SQLite `xyy.db`，存储目录 `./store`，`spring-profile=develop` |
| sfc-download-service | `http://localhost:8092` | 最终复测 jar 构建于 `14:04:57`，进程启动 `14:05:22`；cwd=`%TEMP%\sfc-download-test`（独立空库） |
| aria2 | `1.37.0`，`http://127.0.0.1:6800/jsonrpc` | secret=`testsecret`，`--enable-rpc --rpc-listen-all=false`，启用特性含 BitTorrent |
| 统一配置 | 存于 core 配置表 | `download.aria2.enabled=true`、`rpc-url=http://127.0.0.1:6800/jsonrpc`、`secret=testsecret` |
| 测试账号 | `admin` / `admin666`，uid=`1272361495027515392` | 登录 `POST /api/user/token`（form），管理接口用 **Cookie `token=<jwt>`**（`Authorization` 头无效） |
| JDK | OpenJDK 25.0.2 | |

### 状态码口径说明（重要）

本项目存在**三类非 200 的 HTTP 状态**，其余业务错误均为「HTTP 200 + 响应信封 `code`」：

| 类别 | HTTP 状态 | 触发方式 | 示例 |
|---|---|---|---|
| 请求体反序列化失败 | **400** | `HttpMessageNotReadableException`（如非法枚举值） | T8 `type=FOO` |
| Bean Validation 失败 | **422** | `MethodArgumentNotValidException` / `ConstraintViolationException` | T18 `size=1` |
| 资源级权限不足 | **403** | `SfcJsonException(SfcCommonError.SYSTEM_FORBIDDEN)` —— 唯一带 `SfcErrorInfo` 的构造，异常处理器会真的改写 HTTP 状态 | T24 越权中断 |
| 其余业务异常 | 200（信封 `code` 承载 400/401/404） | `SfcJsonException(code, msg)` | T3 / T5 / T6 / T7 / T11 / T16 |

---

## 二、结果总表

| 编号 | 用例 | 期望 | 实际 | 结论 |
|---|---|---|---|---|
| T1 | 服务启动与注册 | 5 类引擎注册；core 可见实例 | 日志 `下载引擎注册完成，已支持类型: [HTTP, GIT, MAGNET, TORRENT, ED2K]`；注册中心返回 `sfc-download-service / 192.168.137.1:8092` | **PASS** |
| T2 | `GET /capabilities` 结构与可用性 | HTTP/GIT/MAGNET/TORRENT 可用、ED2K 不可用且带原因；GIT 6 个选项；`aria2Options.type=OBJECT` | 完全一致 | **PASS** |
| T3 | `GET /capabilities` 未登录 | 401 | HTTP 200，信封 `{"code":401,"msg":"请先登录"}` | **PASS**（口径见表前说明） |
| T4 | `GET /capabilities?refresh=true` | 200，与 T2 一致 | 一致 | **PASS** |
| T5 | `type=ED2K` 创建 | 400 + 明确文案；**不产生异步任务** | 信封 `code=400`、文案一致；core 异步任务数 `135 → 135` | **PASS** |
| T6 | `type=GIT` + `file:///C:/Windows` | 400「不支持本地文件协议」 | 一致 | **PASS** |
| T7 | `type=GIT` + `ssh://git@github.com/a/b.git` | 400「本期不支持 SSH 协议」 | 一致 | **PASS** |
| T8 | `type=FOO` | 400 且可读（非 500） | HTTP **400**，`请求体格式错误: 不支持的下载类型: FOO` | **PASS** |
| T9 | 磁力链**不传 type** | 自动识别为 MAGNET | 落库 `type=MAGNET`，任务创建成功 | **PASS** |
| T10 | 旧请求体（仅 url/uid/savePath） | 行为不变 | 创建成功，按 HTTP 处理 | **PASS** |
| T11 | 非法 `savePath=/not-exist-dir-xyz` | 400「无效的保存路径」 | 信封 `code=400`，文案一致 | **PASS** |
| T12 | 统一配置 `aria2.enabled=false` 热生效 | >5s 后不可用；创建磁力链 400 | 8s 后 `available=false`，reason=`aria2 未启用：请在 core 后台…`；创建返回 `code=400` 同文案 | **PASS** |
| T13 | 恢复 `enabled=true` | 恢复可用 | 35s 后 `available=true` | **PASS** |
| T14 | 配置面板结构 | 2 组 / 9 项 / secret 掩码 | `download-aria2,download-git`、9 项、`secret.mask=true` | **PASS** |
| T15 | 任务列表结构 | `SfcPageInfo`；含 `type`；ID/数值为字符串 | `totalCount/totalPage` 正常；`id`/`size` 均为 String | **PASS** |
| T16 | 中断不存在任务 | 404 | HTTP 200，信封 `code=404` | **PASS**（口径） |
| T17 | `GET /proxy` 回归 | 200 | 200，1 个代理 | **PASS** |
| T18 | 分页 `size=1` | 422/400 | HTTP **422**，`getAllTask.size: 最小不能小于5` | **PASS** |
| T19 | HTTP 端到端 + 重名规避 | 完成、名字/大小正确；同名二次下载加后缀 | `status=2`、`README.md`、`3808`；二次 `README.md.1`、`3808` | **PASS** |
| T20 | GIT 端到端（默认参数，`.git` 应被删除） | 完成；顶层目录内**无 `.git`**；无临时目录残留 | 首轮 FAIL（D1）→ **修复后 PASS**：`status=2`、`Hello-World/` 仅 `README`、`.git` 不存在、残留 0（R1） | **PASS** |
| T21 | 运行中中断 | `status=4`；临时目录无残留 | `status=4`；无残留 | **PASS** |
| T22 | TORRENT 端到端（公开种子） | 提交→进度→完成→落盘 | `status=2`、`loaded=size=129302391`；`Sintel/` 11 个文件、总大小 129,302,391 | **PASS** |
| **T23** | **MAGNET 端到端** | 完成并落盘；出现阶段切换日志；进度不超 100% | 首轮 FAIL（D2）→ **修复后 PASS**：`status=2`、`Sintel`、`129302391`；日志 `磁力链元数据已就绪，转入内容下载: 新 gid=[b71645635c93bb7e]`；进度单调上升且始终 ≤ size（R2） | **PASS** |
| T24 | 越权中断他人任务 | 403 | `X-Sfc-Uid: 2` 中断他人任务 → **HTTP 403**，`{"businessCode":4001,"code":403,"msg":"权限不足"}`；本人对已完成任务 → HTTP 200 + `code=400`（状态校验） | **PASS** |
| **T25** | **清理与重试（D4）** | 中断后 aria2 无残留、可立即用同一 infohash 重建 | 中断 → `status=4`；`aria2.tellActive` 由 1 变 0；临时目录 0 残留；**同一 infohash 立即重提成功**（`code=200`、新 gid `1e140673dce69696`），不再出现 `[12] already registered`（R4） | **PASS** |

> **关于「文件数」**：`sintel.torrent` 的实际条目是 **11 个**（9 个 `.srt` 字幕 + `Sintel.mp4` + `poster.jpg`），T22 / T23 / T25 的复测均一致；此前测试要求里写的「12 个」与实际种子内容不符，以 11 为准。

---

## 三、缺陷列表（全部已修复）

### D1（已修复，仅 Windows）Git 产物未删除 `.git` + 临时目录泄漏
**根因**：JGit 在 Windows 把 pack 文件标为只读；`DownloadFileUtils.deleteQuietly` 直接 `deleteIfExists`，遇 `AccessDeniedException` 被静默吞掉，子文件删不掉导致 `.git` 与任务临时目录都残留。
**修复**：删除前递归清除只读属性；删除失败由静默改为 `WARN`。
**验证**：R1 → **PASS**。

### D2（已修复）MAGNET 任务「已完成但无产物」
**根因**：磁力链在 aria2 中是**两阶段、两个 gid**。`addUri` 返回的 gid A 只下载元数据（约 5 秒 `complete`，`files=[[METADATA]<name>]`），内容下载由 aria2 分配给新 gid B，仅通过 `tellStatus(A).followedBy=[B]` 暴露。引擎只轮询 gid A，见其 complete 就采集产物 → 只拿到元数据伪文件 → 判「已完成但无产物」，而 gid B 仍在后台下载。
**修复**：`awaitCompletion` 维护待等待 gid 集合，遇 `complete` 且 `followedBy` 非空时改跟踪内容 gid（跟随层级 ≤3、跟踪 gid ≤8）；`collectArtifacts` 过滤 `[METADATA]` 伪文件。
**验证**：R2 → **PASS**（完整下载 129MB）。

### D3（契约说明，非缺陷）业务异常使用「HTTP 200 + 信封 code」
见 §一「状态码口径说明」。前端必须以信封 `code` 判定结果。

### D4（已修复）超时/异常退出后未强制移除 aria2 下载
**根因**：`finally` 只调 `removeDownloadResult`（对 `active` 下载无效），只有「用户中断」路径通过 canceller 调了 `forceRemove`；超时/异常路径没有 → aria2 残留活动下载、同一 infohash 报 `[12] already registered`、且服务端临时目录因 aria2 仍在写盘而删不掉。
**修复**：`finally` 对每个已跟踪 gid 先 `forceRemove` 再 `removeDownloadResult`；中断回调同样遍历当时全部 gid。
**验证**：R4 → **PASS**。

---

## 四、缺陷修复验证（修复前 → 修复后）

### D1 / R1 —— GIT 端到端

| 阶段 | 证据 |
|---|---|
| 修复前 | 网盘 `Hello-World/` 内含 `.git` 目录；临时目录残留 2 个只读 pack 文件；日志 `删除 .git 目录未完全成功（可能被占用）`；克隆文件数=3 |
| **修复后（PASS）** | `status=2`、`name=Hello-World`、`size=14`；网盘 `Hello-World/`：**子目录=[]、文件=[README]**；该任务临时目录残留 **0**；`删除文件/目录失败，已跳过` 命中 **0** 条；日志 `删除克隆产物的 .git 目录` → `Git 克隆完成: 文件数=1` |

### D2 / R2 —— MAGNET 端到端

| 阶段 | 证据 |
|---|---|
| 修复前 | `status=3`，`message=aria2 已完成但没有产生可用的文件（种子名=(未返回)，aria2 报告文件数=1，[0] path=[METADATA]Sintel length=20242）`；此时 aria2 中内容 gid 仍在正常下载 |
| **修复后（PASS）** | 请求 `{"url":"magnet:?xt=urn:btih:08ada5a7…&dn=Sintel","uid":…,"savePath":"/","type":"MAGNET"}`（另有一次省略 `type` 验证自动识别为 MAGNET）<br>日志：`磁力链元数据已就绪，转入内容下载: downloadId=1312097194219667456, 新 gid=[b71645635c93bb7e]`<br>进度采样（任务侧 loaded / aria2 侧 completed，两者始终一致且 ≤ size）：<br>`t+60s 311296/129302391 conn=25 seeders=1`<br>`t+150s 12943360/129302391 conn=11 seeders=8`<br>`t+240s 28278784/129302391 conn=8 seeders=7`<br>`t+330s 66240375/129302391 conn=44 seeders=18`<br>完成：`status=2`、`name=Sintel`、`size=129302391`、`loaded=129302391`；aria2 `tellActive` 为空<br>落盘：`Sintel/` **11 个文件、总大小 129,302,391 字节**（与 T22 完全一致：9 字幕 + `Sintel.mp4` 129,241,752 + `poster.jpg` 46,115）<br>日志：`文件保存成功: 保存名=Sintel, 大小=129302391字节`<br>临时目录残留 **0** |

### D4 / R4 —— 清理与重试

| 阶段 | 证据 |
|---|---|
| 修复前 | 任务因「300 秒无进度」失败后，`aria2.tellActive` 仍残留 `gid=1e1d6f22a5edc3b8 status=active`；同一 infohash 重提直接失败：`aria2 下载失败: [12] InfoHash 08ada5a7… is already registered.`；临时目录残留 6 个文件 |
| **修复后（PASS）** | 提交后 4 秒执行中断：任务 `status=4`；`aria2.tellActive` **1 → 0**；该任务临时目录残留 **0**；<br>**同一 infohash 立即重提**：`code=200`，成功拿到新 gid `1e140673dce69696`，**未再出现** `[12] already registered`；再次中断亦正常（`HTTP 200`） |

### R3 —— TORRENT 回归（gz 跟随改造后的回归面）

| 阶段 | 证据 |
|---|---|
| 修复后（PASS） | `type=TORRENT` + `https://webtorrent.io/torrents/sintel.torrent`：`t+15s 22724608` → `t+90s 129302391` → `t+105s status=2`；`Sintel/` **11 个文件、总大小 129,302,391 字节**；临时目录残留 0 |

---

## 五、覆盖缺口与未验证项

| 项 | 原因 |
|---|---|
| `download_task.type` 历史数据（null）→ `HTTP` 兜底 | 测试实例为全新空库，无历史行；代码路径存在但未实测 |
| GIT 的 `gitBranch` / `gitDepth`（浅克隆）/ `gitSubmodules` / 私有仓库凭证 | 仅验证默认参数路径 |
| `torrentBase64`（不经 URL 的种子内容） | 未构造样本；`TORRENT` 走 URL、`MAGNET` 走磁力链均已完成端到端 |
| SOCKS 代理被忽略的告警、aria2 白名单选项透传 | 未构造用例 |
| 超大文件（>32GiB，core multipart 上限） | 环境不允许 |
| 网关链路（`/api/service/sfc-download-service/**`） | 直接对 8092 测试，未启动 sfc-gateway |
| 多文件落盘失败时不回滚 | 未构造中途失败场景 |
| gid 跟随的「层级上限 3 / gid 上限 8」边界 | 现有资源只触发 1 层跟随，未构造超限场景 |

---

## 六、环境恢复记录

| 动作 | 说明 |
|---|---|
| 删除网盘测试产物 | 删除 `README.md`、`README.md.1`、`Hello-World/`、`Sintel/`（含复测期间多次生成的 `Sintel/`）。根目录最终恢复为测试前 5 个文件：`774D….jpg`、`性格测试小档案.md`、`Qwen_image….png`、`guyizhaocha.mp4`、`All.md5`；无遗留目录 |
| 运行中任务 | 全部终态；R4 的两次中断任务均为 `status=4` |
| 统一配置 | `download.aria2.enabled` 保持 `true`（与测试前一致），其余 key 未改动 |
| aria2 | 结束时 `tellActive` = 0，并已 `purgeDownloadResult` 清理结果缓存 |
| 临时目录 | `%TEMP%\offline-download` 残留 **0**（复测中出现的残留均已清只读后删除） |
| 下载服务 | 使用协调者重启后的最终构建（jar `14:04:57` / 进程 `14:05:22`），复测期间未重启 |

---

## 七、总体评价

- **25 个用例全部通过**，D1 / D2 / D4 三个缺陷均已完成「发现 → 定位 → 修复 → 复测」闭环。
- **D2 是最有价值的一次定位**：最初的怀疑项（`follow-torrent`）被 A/B 实验证伪，最终通过 aria2 RPC 直接观测到「元数据 gid → `followedBy` 内容 gid」的两阶段机制，修复后磁力链可完整下载 129MB 并正确落盘。TORRENT 与 MAGNET 两条 P2P 路径均验证通过且互不回归。
- **D4 修复合同时消除了三类连带问题**：aria2 残留任务、同链无法重试、临时目录删不掉。
- 其余能力（HTTP、GIT、统一配置热生效、能力声明、中断、列表、权限、重名规避、目录树落盘）均按设计工作。
- 建议后续补齐 §五 的覆盖缺口，尤其是 GIT 的 `gitBranch/gitDepth/子模块/凭证` 与 `torrentBase64` 路径。
