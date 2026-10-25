# sfc-download-service 独立联调验证报告（T7）

> **任务**：`task-10` [T7] 独立联调验证：构建、启动、API、任务流转、代理、契约、前端资源、回归
> **执行人**：integration-verifier（独立联调成员，与编码成员分离）
> **日期**：2026-10-25 13:15 ~ 13:40（T7）；2026-10-25 13:39 ~ 13:50（T9 / FIX-2 聚焦复验）（+08:00）
> **仓库**：`C:\Users\xiaotao\code\saltedfishcloud-backend`
> **结论**：**T7 验收通过**。全量构建成功；core / sfc-download-service / 网关三进程联调通过；E1–E10 全部验证完毕。发现并修复 **1 个集成缺陷**（运行中任务无法中断，FAIL → FIXED，已复验）；**无 BLOCKED 项**；另有若干环境限制/已知行为记录于 §7。
>
> **T9（FIX-2）聚焦复验结论**：原 §7-3 的「**执行期** 8 并发落盘」风险已由 FIX-2 加固并复验 **PASS（8/8 FINISH、0 SQLITE_BUSY）**（§6A，实验 D）；但按 task-14 口径的「8 并发**同时提交**」仍 **FAIL**——暴露 2 项未关闭风险（并发提交 SQLite 写冲突 + 冷启动并发 Feign 解码竞态，见 §7-9 / §7-10），已如实记录，**未自行修改代码**。
>
> **T10（FIX-3）聚焦复验结论**：FIX-3（Hikari 单写连接）后，**§7-9 并发创建写冲突已关闭**——A2 热态 8 并发创建连续 **2 轮 8/8 code=200、8/8 FINISH、task_id 均非空、文件 SHA256 全匹配**，服务侧 `download_task` 相关 `SQLITE_BUSY` = **0**；A1 冷启动并发**响应层 SQLITE_BUSY=0**（服务 `download_task` busy=0），但仍存在 **Feign 冷启动解码竞态（§7-10，6/8）**，热态不复现，建议启动预热/首批串行。另发现 **core 侧 develop SQLite 偶发 `file_table` 锁**（D 回归 3 轮中 1 轮 1/8 失败，属 T7 §7-3 的 core 侧残余、非 FIX-3 回退，生产 MySQL 预期无）——详见 §6B。

---

## 1. 结论摘要

| 类别 | 数量 | 说明 |
|---|---|---|
| PASS | 26 | 构建、启动、注册、E1–E5、E6b/c、E7–E10、边界与回归 |
| FAIL → FIXED | 1 | **FIX-1**：core→微服务 Worker 中断指令缺内部鉴权头，运行中任务无法取消（§6，已修复并复验） |
| FAIL | 0 | （T7 范围） |
| BLOCKED | 0 | — |
| 已知限制/风险 | 10 | 记录于 §7（§7-9 已由 FIX-3 关闭；§7-3/§7-10 为残余/未关闭项） |
| **T9 FIX-2 复验** | **执行期 PASS / 并发提交 FAIL** | 8 并发落盘（顺序提交 + 8 并发执行）**8/8 FINISH、0 SQLITE_BUSY** → 原 §7-3 关闭；「8 并发同时提交」仍失败，暴露 §7-9/§7-10 两项未关闭风险，详见 §6A |
| **T10 FIX-3 复验** | **§7-9 已关闭 / §7-10 仍在（仅冷启动）** | Hikari 单连接后：A2 热态并发创建连续 **2 轮 8/8（code=200、FINISH、task_id 非空、SHA256 全匹配）**，服务侧 `download_task` busy=**0**；A1 冷启动响应层 busy=0，但 Feign 冷启动竞态 6/8（热态 8/8）；D 回归 2/3 轮 8/8（首轮 1/8 失败为 core 侧 `file_table` 锁）→ 详见 §6B |

**关键结论**

1. **全量构建通过**：仓库根 `mvn -B clean package -DskipTests` → 38/38 模块 `BUILD SUCCESS`（1 分 40 秒），含新增 `sfc-download-service`，且移除旧插件后 reactor 完整。
2. **独立微服务形态成立**：`sfc-download-service` 以独立进程启动（8092），经 core 本地注册中心完成注册（metadata 携带 `sfc.plugin.autoload`），任务 Worker 以 `role=SERVICE` 认领 `offline-download` 类型。
3. **端到端任务流转正常**：创建 → 认领 → 下载 → 进度上报 → 上传落盘 → 状态回写全链路通过；文件 **SHA256 与源文件完全一致**；重名自动 `.1`；中文文件名、Content-Disposition（ASCII / RFC5987）解析正确。
4. **中断能力完整**（修复后）：运行中任务 DELETE 返回 `code=200`、终态 `CANCEL(4)`、任务日志出现「收到下载中断指令」、被中断任务不落盘；等待中任务立即置 CANCEL；已完成/不存在任务的错误语义正确。
5. **前端资源迁移成功**：core 的 `/api/plugin/autoLoad.js|css` 已合并本服务 bundle（`/* sfc-download-service */`），旧插件 `sfc-ext-download` 不再出现于启动日志、autoLoad、release 产物或任何功能配置。
6. **代理链路生效**：管理员创建的公共代理可经服务/网关列出；无效代理名返回 `code=400`；带有效代理但代理不可达的任务在日志中明确出现「使用代理: … HTTP://127.0.0.1:18099」并失败，证明代理配置被实际应用。

---

## 2. 环境与前置

| 项目 | 值（验证时实测） |
|---|---|
| OS | Windows（`C:\Users\xiaotao`） |
| JDK | OpenJDK **25.0.2**（build 25.0.2+10-69），`JAVA_HOME=C:\Program Files\OpenJDK\jdk-25.0.2` |
| Maven | **3.9.15**（`C:\DATA\soft\apache-maven-3.9.15`，与 JDK 25 匹配） |
| Python | 3.14.0（仅用于测试下载源、控制器脚本；stdlib `sqlite3/hmac/urllib`） |
| 仓库根数据库 | `xyy.db`（1.37 MB）存在；`store/`、`localcache.data` 存在 |
| 端口 | 8087（core）/ 8092（download-service）/ 9090（gateway）/ 18081（测试下载源）验证前均空闲；8848(Nacos)、6379(Redis) 在监听但 develop 模式未使用 |
| 内存/磁盘 | 空闲内存约 68.9 GB / C 盘空闲约 220 GB |
| 构建串行锁 | 机器级 FS 锁 `%TEMP%\sfc-backend-build.lock`（New-Item 建目录成功=持锁，finally 删除） |
| core 会话密钥 | `xyy.db` 表 `config.item_key='sys.safe.token'`（32 字符），仅以只读方式取出注入环境变量，**不落日志、不打印原文** |
| 服务间密钥 | `SFC_INTERNAL_KEY`（32 字符，取仓库 `.run/主程序.run.xml` 中同一份开发密钥），**未打印原文** |
| 管理员登录态 | 从仓库文档 `docs/quick-start/index.md` 解析默认管理员账号并 `POST /api/user/token` 获取 Token，**未打印密码/Token 原文** |

**验证身份/调用方式说明**

- 直连 8092：使用信任头 `X-Sfc-Uid: <adminUid>`（微服务身份只取该头，不做校验）。
- 经网关 9090：使用真实用户 Token（网关本地验签后注入 `X-Sfc-Uid`）——这是推荐路径，E2/E3/E6/E7 主流程均经网关。
- 直连 core `/api/internal/**`：按 `sfc-auth` 约定用 `SFC_INTERNAL_KEY` 现签 HS256 JWT（`iss/sub/jti/iat/exp`，头名 `X-Sfc-Internal-Token`），用于测试数据准备与落盘核验，全程未打印凭证。
- 测试下载源：仓库外临时目录 `%TEMP%\sfc-t7\src_server.py`（Python `ThreadingHTTPServer`，127.0.0.1:18081），提供确定性 1 MiB 文件（SHA256 `06b7bbfb7824aa03382051691630eb26de85102d1b08a81e907ec0744cd8a286`）、4 MiB/6.4s 慢速流、中文名文件、Content-Disposition 两种形态、0 字节文件。

---

## 3. 构建（A）

**命令**（持 FS 锁执行）

```powershell
# 取锁：New-Item -ItemType Directory %TEMP%\sfc-backend-build.lock
mvn -B clean package -DskipTests
# finally 释放锁
```

**结果**：`BUILD SUCCESS`，**38/38 模块全部 SUCCESS**，Total time **01:40 min**，`MVN_EXIT=0`。

```
[INFO] Reactor Summary:
[INFO] sfc-auth 1.0.0 ..................................... SUCCESS [  2.564 s]
...
[INFO] sfc-remote-client 1.0.0 ............................ SUCCESS [  1.121 s]
[INFO] sfc-gateway 1.0.0 .................................. SUCCESS [  1.994 s]
[INFO] sfc-demo-service 1.0.0 ............................. SUCCESS [  1.416 s]
[INFO] sfc-ai-service 1.0.0 ............................... SUCCESS [  3.285 s]
[INFO] sfc-download-service 1.0.0 ......................... SUCCESS [  1.980 s]
[INFO] sfc-core 3.2.0 ..................................... SUCCESS [  8.319 s]
[INFO] BUILD SUCCESS
[INFO] Total time:  01:40 min
```

**产物**（修复后最终版本；sfc-core antrun 会同步覆盖 `release/sfc-core.jar`、`release/config.yml` 等 gitignored 产物）

| 产物 | 时间戳（+08:00） | 大小 |
|---|---|---|
| `sfc-core/target/sfc-core-3.2.0.jar` | 2026-10-25 13:29:38 | 156,085,299 B |
| `microservices/sfc-download-service/target/sfc-download-service-1.0.0.jar` | 2026-10-25 13:29:29 | 107,067,209 B |
| `microservices/sfc-task-worker/target/sfc-task-worker-1.0.0.jar` | 2026-10-25 13:29:20 | 69,700 B |

> 说明：`sfc-ext` 聚合模块在 package 阶段有一条 antrun 警告「Could not find …
> sfc-ext-3.2.0.jar to copy」，属**既有**聚合 POM 行为（该聚合模块本身不打 jar），构建继续且成功，与本次迁移无关。
> 修复轮增量构建：`mvn -q -pl :sfc-core,:sfc-download-service -am -DskipTests package` → `EXIT_CODE=0`，24s；修复已随 fat jar 内嵌 `sfc-task-worker` 生效（两侧 SHA256 一致）。

---

## 4. 进程启动与服务注册（B/C/D）

| 进程 | 启动命令（关键参数） | 端口 | 启动耗时 | 结果 |
|---|---|---|---|---|
| sfc-core | `java -Xmx1G -jar sfc-core/target/sfc-core-3.2.0.jar --spring.profiles.active=develop`，cwd=仓库根，`SFC_INTERNAL_KEY` | 8087 | 12.0s | PASS |
| sfc-download-service | `java -jar microservices/sfc-download-service/target/sfc-download-service-1.0.0.jar`，cwd=**仓库外临时目录**（避免 `download.db` 污染仓库），同一 `SFC_INTERNAL_KEY` | 8092 | 5.0s | PASS |
| sfc-gateway | `java -Xmx512m -jar microservices/sfc-gateway/target/sfc-gateway-1.0.0.jar`，`SFC_JWT_SECRET`=core `sys.safe.token`（从 xyy.db 只读注入） | 9090 | 2.6s | PASS |

**core 启动证据（节选）**

```
[插件初始化]启动时加载的插件清单：[pxe-boot,data-manager,video-enhance,sys,local-mq,sfc-ext-mcp]
[任务Worker]启动完成：role=CORE, serviceId=sfc-core, 任务类型=[invalid-data-detect, file-copy, file-type-check, test, archive-extractor, video-convert, archive-compress], 最大并发=64
[任务Worker]身份确认且已在服务注册中心可见：serviceId=sfc-core, workerId=xyy.com:8087（第 1 次检查），开始认领任务
Started SaltedfishcloudApplication in 12.026 seconds
```

**download-service 启动/注册证据（节选）**

```
[任务Worker]已注册任务类型: [offline-download]
Tomcat started on port 8092 (http) with context path '/'
[SFC local registry]注册成功 serviceId=sfc-download-service instanceId=172.25.32.1:8092 ttl=30s
[任务Worker]启动完成：role=SERVICE, serviceId=sfc-download-service, 任务类型=[offline-download], 最大并发=8, sfc-core=服务发现(sfc-core)
Started DownloadServiceApplication in 4.977 seconds
[任务Worker]身份确认且已在服务注册中心可见：serviceId=sfc-download-service, workerId=172.25.32.1:8092（第 1 次检查），开始认领任务
```

**注册中心查询**（core `GET /api/local-registry/instances?serviceId=sfc-download-service`，请求头 `X-Sfc-Local-Registry-Password`）

```json
[{"serviceId":"sfc-download-service","instanceId":"172.25.32.1:8092","host":"172.25.32.1","port":8092,
  "secure":false,"metadata":{"sfc.plugin.autoload":"/static/index.umd.js,/static/style.css"}}]
```

服务清单：`['sfc-core', 'sfc-gateway', 'sfc-download-service']` → **注册/发现 PASS**。

---

## 5. 功能验证逐项（E1–E10）

### 5.1 总览

| 编号 | 验证项 | 结论 | 关键证据 |
|---|---|---|---|
| E1 | 直连 `GET :8092/api/task/download` | **PASS** | HTTP 200，`{"content":[],"totalCount":"0","totalPage":"0"}` |
| E2 | 网关动态路由 `GET :9090/api/service/sfc-download-service/task/download` | **PASS** | HTTP 200，同分页结构 |
| E2b | 网关匿名请求列表 | **PASS（预期拒绝）** | HTTP 422 `getAllTask.uid: 无权操作资源` |
| E3 | 创建下载任务（经网关，真实登录态） | **PASS** | HTTP 200，返回任务 id 字符串 |
| E4 | 状态/进度观察（服务列表校准 + core getProg） | **PASS** | RUNNING(1) → FINISH(2)；getProg `loaded=131072,total=4194304` |
| E5 | 落盘大小一致性 + 摘要校验 | **PASS** | core 目录 `test-1mb.bin size=1048576`；下载回读 SHA256 与源一致 |
| E5b | 重名 `.1` / 中文文件名 / Content-Disposition | **PASS** | `test-1mb.bin.1`、`中文文件.txt`、`from-header.bin`、`from-header-测试.bin` |
| E6a | 中断**运行中**任务 | **FAIL → FIXED** | 修复前 code=409；修复后 code=200 → 终态 CANCEL(4)，见 §6 |
| E6b | 中断**等待中**任务 | **PASS** | DELETE 200；0.3~0.4s 内本地镜像立即 CANCEL(4) |
| E6c | 已完成任务再中断 / 不存在任务 | **PASS** | 业务码 400「只能对下载中或等待中的下载任务操作」/ 404「下载任务不存在」 |
| E7 | 代理：列表 / 无效代理 / 代理生效 | **PASS** | 列表含测试代理；无效代理 code=400；任务日志「使用代理: … HTTP://127.0.0.1:18099」 |
| E8 | JSON 契约 | **PASS** | id/taskId/size/loaded/speed 均字符串；`asyncTaskRecord.status` 数字；createdAt/finishAt 存在；分页 content/totalCount/totalPage |
| E9 | 前端资源 autoLoad | **PASS** | `autoLoad.js` 含 `/* sfc-download-service */` 与 bundle；`autoLoad.css` 含本服务样式 |
| E10 | 旧插件下线 + 任务仅被本服务认领 | **PASS** | core 启动清单/日志/autoLoad/release 均无 `sfc-ext-download`；32 条任务记录 30 条 `claimedService=sfc-download-service, claimedRole=SERVICE`，2 条未认领（等待中被取消） |

### 5.2 详细证据

#### E1/E2 列表接口与网关路由

```text
GET http://127.0.0.1:8092/api/task/download?uid=<adminUid>&page=1&size=10&type=ALL
Header: X-Sfc-Uid: <adminUid>
→ HTTP=200 code=200 data={"content": [], "totalCount": "0", "totalPage": "0"}

GET http://127.0.0.1:9090/api/service/sfc-download-service/task/download?uid=<adminUid>&page=1&size=10&type=ALL
Header: Token: <用户登录 Token（不记录原文）>
→ HTTP=200 code=200 data={"content": [], "totalCount": "0", "totalPage": "0"}
```

> 网关改写规则实测验证：`/api/service/{serviceId}` + 原路径去掉 `/api` 前缀（`/task/download`）→ 下游 `/api/task/download`；早期一次误用 `/api/task/download` 直连网关命中 core 静态资源 404 属测试路径拼写问题，非网关缺陷。

#### E3/E4/E5 创建 → 进度 → 落盘

```text
POST http://127.0.0.1:9090/api/service/sfc-download-service/task/download
Body: {"url":"http://127.0.0.1:18081/test-1mb.bin","uid":<adminUid>,"savePath":"/t7-verify"}
→ HTTP=200 code=200 data="1311723465945907200"

列表轮询（直连服务）：
snapshot[0] status=1 loaded=0 size=0                    ← 认领后立即 RUNNING
snapshot[1] status=2 loaded=1048576 size=1048576 name=test-1mb.bin   ← 完成
```

落盘核验（core 内部接口）：

```text
GET  /api/internal/file/v2/list?uid=<adminUid>&path=/t7-verify
→ [{"name":"test-1mb.bin","size":1048576,"type":2}]

GET  /api/internal/file/v2/download?uid=<adminUid>&path=/t7-verify/test-1mb.bin
→ HTTP=200 bytes=1048576
sha256_got      = 06b7bbfb7824aa03382051691630eb26de85102d1b08a81e907ec0744cd8a286
sha256_expected = 06b7bbfb7824aa03382051691630eb26de85102d1b08a81e907ec0744cd8a286   ✅ 一致
```

任务运行日志（core `GET /api/asyncTask/getLog`，节选）：

```
[Download]开始执行离线下载任务: DownloadTaskParams{url='http://127.0.0.1:18081/test-1mb.bin', downloadId=..., uid=..., savePath='/t7-verify'}
[Download]保存目录校验通过
[Download]开始下载: method=GET, 初始文件名=test-1mb.bin
[Download]响应已到达: 文件名=test-1mb.bin, 大小=1048576 字节
[Download]文件接收完成: 1048576 字节
[Download]开始保存文件到网盘: uid=..., path=/t7-verify
[Download]文件保存成功: test-1mb.bin（1048576 字节）
[Download]清理本地临时文件: ...\offline-download\...\test-1mb.bin
[任务Worker]执行完成
```

> 临时文件创建与 finally 清理均从日志确认，未出现残留（验证结束时 `%TEMP%\offline-download` 为空）。

进度接口（core `POST /api/asyncTask/getProg`，运行中任务）：

```json
[{"taskId":"1311726205556228096","record":{"lastUpdateTime":"1792906363983",
  "loaded":"131072","speed":"0","total":"4194304"}}]
```

#### E5b 文件名规则

| 场景 | 请求 URL | 最终文件名 | 大小 |
|---|---|---|---|
| 同名第二次下载 | `/test-1mb.bin` | **`test-1mb.bin.1`** | 1048576 |
| 中文文件名（原始 UTF-8 URL） | `/中文文件.txt` | **`中文文件.txt`** | 67 |
| ASCII Content-Disposition | `/disposition` | **`from-header.bin`** | 102400 |
| RFC5987 中文 Content-Disposition | `/disposition-utf8` | **`from-header-测试.bin`** | 102400 |

#### E6 中断语义（修复后）

运行中任务：

```text
POST 创建慢速任务（4 MiB / 约 6.4s）→ taskId=1311726052258611200
等待 status=1(RUNNING)
DELETE :9090/api/service/sfc-download-service/task/download?taskId=1311726052258611200
→ HTTP=200 code=200 msg=ok   （耗时 0.15s）
终态 status=4(CANCEL)；name/size 保持快照
任务日志：
  [任务Worker]收到任务中断指令，正在通知执行逻辑尽快停止
  [Download]收到下载中断指令，正在关闭下载流
  [Download]任务已被取消: 任务[...]已被中断
  [任务Worker]任务[...]已被中断
core 异步任务记录：{"status":4,"claimedService":"sfc-download-service","claimedRole":"SERVICE"}
落盘副作用：无新增文件（中断发生在上传前，目录文件集合不变）
```

等待中任务（直连 DELETE）：

```text
并发提交 9 个慢速任务（max-concurrent-tasks=8）→ 第 9 个 status=0(WAITING)
DELETE :8092/api/task/download?taskId=1311726057170141184
→ HTTP=200 code=200
删除后 0.4s 本地列表 status=4(CANCEL)   ← 本地镜像立即置取消
```

已完成任务/不存在任务：

```text
DELETE 已完成任务 → HTTP=200 code=400 msg="只能对下载中或等待中的下载任务操作"
DELETE taskId=999999999999999999 → HTTP=200 code=404 msg="下载任务不存在: 999999999999999999"
```

> 说明：业务异常（`new SfcJsonException(code,msg)`）的 HTTP 状态为 200、信封 `code` 为 400/404，与宿主
> `ControllerAdvice` 既有语义一致（只有携带 `errorInfo` 的异常才改 HTTP 状态），**不是缺陷**。

#### E7 代理

```text
1) 管理员建公共代理：POST /api/proxy/save
   {"name":"t7-test-proxy","type":"HTTP","address":"127.0.0.1","port":18099,"isProtect":false,"uid":0}
   → HTTP=200 code=200，id=1311724083376816128
2) 直连列表  GET :8092/api/task/download/proxy            → names=['t7-test-proxy']
3) 网关列表  GET :9090/api/service/sfc-download-service/task/download/proxy → names=['t7-test-proxy']
4) 无效代理名创建任务（proxy=no-such-proxy）→ HTTP=200 code=400 msg="无效的代理：no-such-proxy"
5) 有效代理但代理端口不可达 → 任务 FAILED，日志：
   [Download]使用代理: t7-test-proxy - HTTP://127.0.0.1:18099
   [Download]任务执行失败: I/O error on GET request ...: Connection refused: getsockopt
6) 删除测试代理 DELETE /api/proxy?proxyId=... → code=200；再查列表 names=[]
```

#### E8 JSON 契约（列表行原始 JSON 类型）

```text
id=str, taskId=str, size=str, loaded=str, speed=str         ✅ 64 位 ID/大小均字符串（防前端精度丢失）
asyncTaskRecord.status=int                                  ✅ 数字
createdAt 存在（兼容别名，=createAt）、finishAt 存在         ✅
分页字段：content / totalCount / totalPage                  ✅
行字段全集：asyncTaskRecord, createAt, createdAt, createdBy, finishAt, id, loaded, message,
            name, proxy, savePath, size, speed, status, taskId, uid, updateAt, url
```

> 注：`createAt`（实体审计字段）与 `createdAt`（前端兼容别名）同时存在，均为时间字符串；`finishAt` 未完成时为 null。

#### E9 前端资源合并

```text
GET http://127.0.0.1:8087/api/plugin/autoLoad.js   → HTTP=200 len=329737
  含 "/* sfc-download-service */"           ✅
  含本服务 bundle 标记「注册离线下载插件」    ✅（出现 1 次，未重复）
  含旧插件标记 sfc-ext-download              ❌（无）
  本地插件注释：/* pxe-boot */, /* data-manager */, /* video-enhance */, /* sys */, /* sfc-ext-mcp */, /* sfc-download-service */

GET http://127.0.0.1:8087/api/plugin/autoLoad.css  → HTTP=200 len=5419
  含本服务样式作用域 data-v-a6138650         ✅
  含旧插件标记                               ❌（无）
```

> core 日志中「从微服务插件…拉取资源…失败」计数为 **0**，说明 autoLoad 通过服务发现+负载均衡拉取微服务静态资源链路正常。

#### E10 旧插件下线与认领归属

```text
core 启动日志 grep "sfc-ext-download" 命中数 = 0
core 启动插件清单 = [pxe-boot, data-manager, video-enhance, sys, local-mq, sfc-ext-mcp]（无 download）
release/ext-available 下无 *download* 产物；sfc-ext/pom.xml 无 sfc-ext-download 模块；develop/develop-mysql 配置无该插件项
autoLoad.js/css 均无旧插件内容
任务认领统计（core /api/asyncTask/listRecord，taskType=offline-download，共 32 条）：
  claimedService: sfc-download-service = 30, None = 2（等待中即被取消、从未认领）
  claimedRole:    SERVICE = 30,               None = 2
  → 无任何记录被 CORE 角色或其他服务认领 ✅
仓库残留引用：仅迁移说明注释/文档（pom 注释、README、JavaDoc、application.yml 注释）；
  另有 .idea/compiler.xml、encodings.xml 仍列旧模块路径——`.idea` 已被 .gitignore:17 忽略，属 IDE 本地元数据，不影响构建/运行。
```

---

## 6. 缺陷记录：FIX-1（FAIL → FIXED）

**标题**：core → 微服务 Worker 的中断指令未携带内部鉴权头，导致「运行中任务」无法取消

| 项 | 内容 |
|---|---|
| 归属模块 | `microservices/sfc-task-worker`（执行侧 SDK，core 与微服务共用） |
| 文件 | `microservices/sfc-task-worker/src/main/java/com/sfc/cloud/task/worker/SfcTaskWorkerClient.java`（约第 181 行） |
| 引入原因 | `coreRestClient` 配置了 `SfcInternalTokenRequestInitializer`，但回调具体 worker 实例的 `workerRestClient` 漏配，导致 `POST {worker}/api/internal/task/interrupt` 无 `X-Sfc-Internal-Token` |
| 复现步骤 | 1) 启动 core+download-service+网关；2) `POST .../task/download` 创建慢速下载；3) 待 `status=1` 后 `DELETE .../task/download?taskId=<id>` |
| 期望 | DELETE `code=200`；终态 `CANCEL(4)`；日志出现「收到下载中断指令」 |
| 实际（修复前） | DELETE `code=409`「调用 sfc-core 内部接口[/api/internal/task/cancel]失败：取消失败：任务中断失败：执行者可能已失联或中断指令下发失败」；任务继续执行至完成 `status=2` |
| 关键日志（修复前） | service：`SfcInternalAuthFilter: [内部接口] 拒绝非服务间调用：reason=MISSING_TOKEN(...), uri=/api/internal/task/interrupt, remoteAddr=172.25.32.1`；core：`向worker[http://172.25.32.1:8092]下发任务[...]的中断指令失败: 403 Forbidden`；`TaskInterruptRouter: ...中断指令失败，任务将保持运行` |
| 修复 | `workerRestClient` 追加 `.requestInitializer(new SfcInternalTokenRequestInitializer(issuer))`，保持绝对地址/不经负载均衡语义（修复由 task-protocol-dev 在 task-12 完成） |
| 修复构建 | `mvn -q -pl :sfc-core,:sfc-download-service -am -DskipTests package` → `EXIT_CODE=0`（24s）；新 jar：core 13:29:38 / service 13:29:29 / task-worker 13:29:20，且 fat jar 内嵌 worker 与 target 一致 |
| **复验结果** | **PASS**：DELETE `HTTP=200 code=200`（0.15s）→ 终态 `status=4(CANCEL)`；日志含「收到下载中断指令 / 任务已被取消」；无落盘；core 记录 `status=4, claimedRole=SERVICE`。等待中任务中断、跨 uid 权限等关联用例同步复验通过 |

---

## 6A. FIX-2 聚焦复验：SQLite 并发写加固（T9 / task-14）

### 6A.1 背景与修复内容

- **T7 §7-3 记录**：8 个慢速任务几乎同时落盘时，2/8 失败于 `SQLITE_BUSY: The database file is locked`。
- **FIX-2（task-13，service-core-dev）**：`microservices/sfc-download-service/src/main/resources/application.yml` 的
  `spring.datasource.url` 改为 `jdbc:sqlite:./download.db?journal_mode=WAL&busy_timeout=5000`（仅配置改动，无 Java 改动）。
- **新 jar**：`microservices/sfc-download-service/target/sfc-download-service-1.0.0.jar`，**2026-10-25 13:38:11**，107,067,660 B。

### 6A.2 复验环境

core（T7 构建的 13:29 jar，`--spring.profiles.active=develop`，local registry，8087）+ 新服务 jar（8092，cwd=仓库外临时目录，同一 `SFC_INTERNAL_KEY`）+ Python 本地 HTTP 源（127.0.0.1:18081，提供 8 个不同 4MiB 文件与 6.4s 慢速流）。提交使用管理员 uid 的信任头直连 8092。

### 6A.3 配置生效确认

- 启动日志：`Database JDBC URL [jdbc:sqlite:./download.db?journal_mode=WAL&busy_timeout=5000]`；
- 工作目录生成 `download.db` + `download.db-wal` + `download.db-shm`；只读探测 `PRAGMA journal_mode` → **wal**；
- 无 datasource/驱动参数报错，服务正常注册，Worker `role=SERVICE` 正常认领。

### 6A.4 四组实验与结果

| 实验 | 场景 | 结果 |
|---|---|---|
| **D（关键）** | 顺序提交 8 个慢速任务（4MiB / 约 6.4s，间隔 0.2s）→ 8 任务并发执行、**同刻收尾**（精确复现 T7 §7-3 场景） | **PASS**：**8/8 FINISH、0 FAILED**；观测最大并发 `RUNNING=8`；执行期 SQLITE_BUSY 增量 **service=0 / core=0**；目录 8 个文件均 4194304 B，名称 `slow.bin[.1-.7]` 符合重名规则 |
| C | 顺序提交 8 个快速任务（4MiB，间隔 0.3s）→ 并发执行 | PASS：8/8 FINISH；8 文件 SHA256 全部匹配；执行期 SQLITE_BUSY 增量 0 |
| A2 | **8 并发同时提交**（服务已预热） | **FAIL：5/8 成功**；3 个 HTTP 500 `CannotAcquireLockException ... [SQLITE_BUSY] ... [update download_task set ...]` |
| A1 | **8 并发同时提交**（冷启动后首批请求，重启服务后未做任何用户请求） | **FAIL：1/8 成功**；3 个 `feign.codec.DecodeException: 'messageConverters' must not be empty`；4 个 `SQLITE_BUSY(update download_task)`。服务日志：DecodeException=9 行、CannotAcquireLock=12 行、SQLITE_BUSY=32 行；同时段 core 日志 SQLITE_BUSY 4 次（均在 `update async_task_record`） |

**A1/A2 失败 SQL 形态**：

```text
org.springframework.dao.CannotAcquireLockException: could not execute batch
  [[SQLITE_BUSY] The database file is locked (database is locked)]
  [update download_task set create_at=?,created_by=?,finish_at=?,loaded=?,message=?,name=?,proxy=?,save_path=?,size=?,status=?,task_id=?,uid=?,update_at=?,url=? where id=?]
```

**副作用（一致性风险）**：并发提交失败的请求中，部分已写入 `download_task` 行并成功提交 core 异步任务——任务随后正常执行、文件落盘，但**客户端收到 500**，且该行 `task_id` 为 NULL（列表 `asyncTaskRecord=null`）。复验时 `task_id IS NULL` 且 `status=2`、文件已落盘的行共 **10 条**。

**FIX-1 不回退抽查**：运行中任务 DELETE `HTTP=200 code=200` → 终态 `CANCEL(4)`、日志含「收到下载中断指令」；列表接口 `HTTP=200`，`content/totalCount/totalPage` 正常 → **PASS**。

### 6A.5 结论

1. **对 T7 §7-3 的原风险场景（执行期 8 并发落盘）**：**FIX-2 有效，该风险关闭**（8/8 FINISH、0 SQLITE_BUSY、文件大小正确）。
2. **对 task-14 的「8 并发同时提交」口径**：**未通过**（冷/热态均失败），根因两类：
   - **服务侧 `download.db` 写冲突**：失败 SQL 为 `[update download_task set ...]`（Hibernate merge = 同事务 SELECT 后 UPDATE）。WAL 下「读快照升级为写」的冲突会产生 `SQLITE_BUSY_SNAPSHOT`，**busy_timeout 不生效**，因此 FIX-2 的参数无法覆盖该路径；建议对 `CannotAcquireLockException/SQLiteException busy` 做有限重试（如 3 次/100ms 退避），或把「落库 + 回写 taskId」收敛为单事务且避免 merge，或串行化写操作。
   - **冷启动并发 Feign 解码竞态（新发现）**：`feign.codec.DecodeException: 'messageConverters' must not be empty`（`SfcCoreFileClient.list` / 管理员回源同时触发并降级），热态不复现；建议核查 Spring Cloud OpenFeign + Boot 4 下 `SpringDecoder` 的 messageConverters 初始化线程安全，发布前可加启动预热调用规避。
3. 另一项一致性建议：提交失败时应回滚/标记本地行，避免「客户端 500，但任务执行且文件落盘、task_id 为空」。
4. 本复验**未修改任何代码**；两项未关闭风险已记录为 §7-9 / §7-10，由 Lead 决定是否另立 FIX-3。

### 6A.6 复现命令（节选）

```powershell
# 冷态：重启 sfc-download-service 后不做任何用户请求，立即 8 并发创建任务
# （savePath 需先经 core 内部 mkdirs 创建；URL 用不同文件避免重名）
1..8 | ForEach-Object -Parallel {
  Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8092/api/task/download' `
    -Headers @{ 'X-Sfc-Uid' = '<adminUid>'; 'Content-Type' = 'application/json' } `
    -Body (@{ url = "http://127.0.0.1:18081/file-$_.bin"; uid = <adminUid>; savePath = '/t9-cold' } | ConvertTo-Json)
} -ThrottleLimit 8

# 热态：先 GET 一次列表预热，再重复上面的 8 并发 POST
# 顺序提交 + 8 并发执行（T7 §7-3 复现场景）：for 循环每 0.2s POST 一次 /slow.bin，共 8 个
```

---

## 6B. FIX-3 聚焦复验：SQLite 单写连接（T10 / task-16）

### 6B.1 背景与修复内容

- **T9 未关闭风险 §7-9**：8 并发同时创建任务时，服务 `download.db` 在 Hibernate merge（`update download_task`）路径触发 `SQLITE_BUSY_SNAPSHOT`（WAL 下 `busy_timeout` 不生效），热态 5/8、冷态 1/8 失败。
- **FIX-3（task-15，service-core-dev）**：`application.yml` 的 `spring.datasource` 增加
  `hikari.maximum-pool-size=1`、`minimum-idle=1`（SQLite 单写连接），WAL + busy_timeout 保持不变，无 Java 改动。
- **新 jar**：`microservices/sfc-download-service/target/sfc-download-service-1.0.0.jar`，**2026-10-25 13:49:54**，107,068,162 B。

### 6B.2 配置与启动确认

- fat jar 内 `BOOT-INF/classes/application.yml` 实测包含：

```yaml
url: "jdbc:sqlite:./download.db?journal_mode=WAL&busy_timeout=5000"
hikari:
  maximum-pool-size: 1
  minimum-idle: 1
```

- 启动日志：`Database JDBC URL [jdbc:sqlite:./download.db?journal_mode=WAL&busy_timeout=5000]`、`HikariPool-1 - Starting... / Start completed`，无 datasource 报错；服务正常注册，Worker `role=SERVICE` 正常认领。与 service-core-dev 冒烟一致（其 DEBUG 日志实证 `maximumPoolSize=1`、`Pool stats (total=1/1)`）。

### 6B.3 复验环境

core（T7 构建 13:29 jar，develop/local registry，8087）+ FIX-3 新服务 jar（8092，cwd=仓库外临时目录，同一 `SFC_INTERNAL_KEY`）+ Python 本地 HTTP 源（18081，8 个 1MiB 区分文件 + 4MiB/6.4s 慢速流）。提交使用管理员 uid 信任头直连 8092。

### 6B.4 结果

| 用例 | 场景 | 结果 |
|---|---|---|
| **A2 热态（2 轮）** | 预热后 8 个请求**同时创建**（savePath=`/t9-a2`、`/t9-a2b`，1MiB×8 不同文件） | **PASS**：两轮均 **8/8 code=200**、**8/8 FINISH**；每轮 8 个文件均 1048576 B 且 **SHA256 全部匹配**；**任务行 `task_id` 均非空**（无 T9 的 `task_id=NULL` 残留）；服务侧 `download_task` 相关 `SQLITE_BUSY` = **0** |
| **A1 冷启动首批并发** | 服务重启后未收到任何用户请求，立即 8 并发创建（`/t9-a1`） | 2/8 成功；6/8 失败于 **Feign `DecodeException: 'messageConverters' must not be empty`**（§7-10，符合预期）；**响应层 SQLITE_BUSY=0**，服务 `download_task` 相关 busy=0；已提交 2 任务 FINISH=2/2 |
| **A1 附：热态对照** | 同一服务（已预热）再 8 并发创建（`/t9-a1w`） | **8/8 code=200、8/8 FINISH**，Feign 竞态 0 → 复现「仅冷启动首批」 |
| **D 回归（3 轮）** | 顺序提交 8 个慢速任务 → 8 并发执行、同刻收尾 | 第 2/3 轮 **8/8 FINISH、0 busy**；**第 1 轮 7/8**（1 个失败 + 残留 1 个 `.tmp` 文件），失败原因经日志定位为 **core 侧** `SQLITE_BUSY [delete from file_table ...]`（core `xyy.db` 非 WAL，FIX-3 范围外，见 §7-11） |
| **运行中中断** | 创建慢速任务→RUNNING→DELETE | **PASS**：DELETE `code=200` → 终态 `CANCEL(4)`，任务日志含「收到下载中断指令」（FIX-1 未回退） |
| **列表接口** | `GET /api/task/download?...` | PASS：HTTP 200，`content/totalCount/totalPage` 正常 |

**日志定量（本次 T10 服务日志）**：

```text
SVC: SQLITE_BUSY 行数 = 32，其中 download_task 相关 = 0，async_task_record 相关 = 31（core 500 回显，Reporter 重试后成功）
     DecodeException 行数 = 12（均集中在 A1 冷启动并发窗口）
CORE: SQLITE_BUSY 行数 = 128，其中 file_table = 2（D 第 1 轮失败根因），async_task_record = 62（claim/report 竞争，服务端重试成功）
```

### 6B.5 结论

1. **§7-9（并发创建写冲突）已关闭**：Hikari 单写连接消除了服务 `download.db` 的 INSERT/merge 并发冲突；A2 连续 2 轮 8/8 成功、`task_id` 均非空、文件完整，T9 的「失败但任务仍执行」一致性副作用未再出现。
2. **§7-10（冷启动并发 Feign 解码竞态）仍在**：A1 冷启动首批并发仍 6/8 失败，热态对照 8/8；属 Feign 解码器初始化竞态，与 FIX-3 无关。建议：**启动预热**（服务就绪后先发一次简单请求，如内部自检/探活经 Feign 的调用）或**首批请求串行/限流**；根因建议在 OpenFeign/Boot 4 适配层修复。
3. **core 侧 develop SQLite 残余风险（§7-11）**：D 回归 3 轮中 1 轮出现 core `delete from file_table` 的 `SQLITE_BUSY`，导致 1/8 任务失败并在用户目录残留 1 个 `.tmp` 文件（已清理）。该路径属 core 的 `xyy.db`（develop SQLite，未启用 WAL/重试），**非 FIX-2/FIX-3 回退**；生产 `application-product.yml` 使用 MySQL，预期不复现。建议：发布前在 MySQL 配置下复跑 8 并发同刻收尾；如需 SQLite 高并发，core 侧也需 WAL + busy_timeout/有限重试，并清理上传失败残留 `.tmp`。
4. 本复验**未修改业务代码**，仅更新本报告。

### 6B.6 复现命令（节选）

```powershell
# A2 热态：先 GET 一次列表预热，再 8 并发 POST（URL=/file-N.bin，savePath=/t9-a2）
1..8 | ForEach-Object -Parallel {
  Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8092/api/task/download' `
    -Headers @{ 'X-Sfc-Uid' = '<adminUid>'; 'Content-Type' = 'application/json' } `
    -Body (@{ url = "http://127.0.0.1:18081/file-$_.bin"; uid = <adminUid>; savePath = '/t9-a2' } | ConvertTo-Json)
} -ThrottleLimit 8

# A1 冷启动：重启服务 -> 等到端口监听/注册日志 -> 不做任何用户请求，立即执行上面的 8 并发 POST
# D 回归：for 循环每 0.15s POST 一次 /slow.bin（savePath=/t9-dN），共 8 个
```

---

## 7. 边界、已知限制与遗留风险

以下项中 §7-1~§7-8 为 T7 记录的既有行为/环境限制（不阻断验收）；**§7-9 已由 FIX-3 关闭**；**§7-10 / §7-11 为仍未关闭的残余风险**（冷启动 Feign 竞态、core 侧 develop SQLite 偶发锁），建议发布前处理或明确接受。

| # | 项 | 说明 / 建议 |
|---|---|---|
| 1 | **预编码 URL 二次编码（既有行为，非回归）** | URL 若已是百分号编码（如 `.../%E4%B8%AD%E6%96%87.txt`），`RestTemplate` 会再次编码为 `%25E4...` 导致下游 404。旧插件同样使用 `restTemplate.execute(String url,…)`，属**既有行为**；建议前端传原始/未编码 URL（新前端 `create` 已对 url 做 `decodeURIComponent`）。后续可改用 `URI.create()` + `EncodingMode.VALUES_ONLY` 优化。 |
| 2 | **0 字节文件被 core 上传接口拒绝（既有边界）** | 任务最终 `status=3`，错误「调用 sfc-core 失败[http=400]：文件为空」——core 既有行为，服务已正确透传错误，符合预期。 |
| 3 | **develop SQLite 并发写（FIX-2/FIX-3 复验后状态：服务侧已关闭 / core 侧残余）** | **服务 `download.db` 已关闭**：「执行期 8 并发落盘」由 FIX-2 关闭（§6A 实验 D 8/8 FINISH、0 busy）；「8 并发同时创建」由 FIX-3 关闭（§6B A2 连续 2 轮 8/8、0 busy、task_id 非空）。**core `xyy.db` 仍有残余（§7-11）**：D 回归 3 轮中 1 轮出现 `delete from file_table` 的 `SQLITE_BUSY`，导致 1/8 失败并在用户目录残留 `.tmp`；生产 `application-product.yml` 使用 MySQL，预期不复现，发布前建议在 product(MySQL) 配置下复跑一次 8 并发同刻收尾。 |
| 4 | **gzip 场景 Content-Length 不一致** | 仅告警不失败（实现已有「[警告] 已下载大小与响应声明大小不一致」日志）；本次未专项构造 gzip 用例，建议后续补充。 |
| 5 | **信任头安全边界** | 微服务从 `X-Sfc-Uid` 取身份、不做校验（框架期信任模式）：服务只允许内网/本机暴露，公网入口必须经网关。管理员判定有 60s 正向缓存（按 uid），直连伪造信任头可能在窗口内获得管理员语义——属既定威胁模型的已知约束。 |
| 6 | **匿名/权限错误码形态** | 网关匿名请求列表返回 HTTP 422（Bean Validation `@SfcUID`）；业务异常为 HTTP 200 + 信封 `code=400/404`。均与宿主既有语义一致，前端按信封 `code` 判定即可。 |
| 7 | **数据不迁移** | 旧插件 `xyy.db.download_task` 历史数据不迁移（验证时该表 0 行，与服务独立 `download.db` 无关）；部署需删除运行目录旧插件 jar（`ext/sfc-ext-download.jar`）并保证前端 API 基址切换。 |
| 8 | IDE 元数据 | `.idea/compiler.xml`、`.idea/encodings.xml` 仍引用已删除的 `sfc-ext-download` 路径（已 gitignore），仅影响本地 IDEA 索引，可后续手动清理。 |
| **9** | **并发提交失败但任务仍执行（一致性风险，已关闭：FIX-3）** | T9 发现的问题（8 并发提交部分返回 500，但 `download_task` 已插入、core 任务已提交、任务执行落盘、`task_id=NULL`）经 **FIX-3（Hikari 单写连接）**复验已消失：§6B A2 连续 **2 轮 8/8 code=200、8/8 FINISH、task_id 均非空、文件 SHA256 全匹配**，服务侧 `download_task` 相关 `SQLITE_BUSY` = 0。**该项关闭。** |
| **10** | **冷启动并发 Feign 解码竞态（未关闭；仅冷启动首批并发）** | 服务重启后若**首批**请求即为并发（如 8 个同时创建），A1 复验 6/8 失败于 `feign.codec.DecodeException: 'messageConverters' must not be empty`（同时管理员回源也触发并降级）；**热态对照 8/8、不复现**。FIX-3 不改变该行为（属 OpenFeign 解码器初始化竞态，与 SQLite 无关）。缓解：服务就绪后**启动预热**（先发一次简单请求），或首批请求串行/限流；根因建议在 OpenFeign/Boot 4 适配层修复。 |
| **11** | **core 侧 develop SQLite 偶发文件写锁（残余，未关闭）** | T10 的 D 回归 3 轮中 1 轮出现 1/8 任务失败：core `SQLITE_BUSY [delete from file_table where uid=? and node=? and name in (?)]`（core `xyy.db` 非 WAL），失败还在用户目录残留 1 个 `.tmp` 文件（已清理）。属 **core 侧既有行为**（非本次迁移/FIX-2/FIX-3 回退）；生产 `application-product.yml` 使用 MySQL，预期不复现。建议：发布前在 product(MySQL) 配置下复跑 8 并发同刻收尾；若 SQLite 需高并发，core 侧也需 WAL + busy_timeout/有限重试，并清理上传失败残留 `.tmp`。 |

---

## 8. 清理与环境恢复

| 项目 | 处理 |
|---|---|
| 进程 | core(PID 46096)、download-service(PID 38056)、gateway(PID 43160)、Python 测试源(PID 24688) 全部 Stop；8087/8092/9090/18081 均确认 FREE |
| 构建锁 | `%TEMP%\sfc-backend-build.lock` 已释放（不存在） |
| 测试数据 | 管理员网盘 `/t7-verify` 下 **22 个测试文件**经 core 内部接口删除，目录 `t7-verify` 已删除；根目录复查无残留 |
| 代理数据 | 测试代理 `t7-test-proxy` 已删除，代理列表为空 |
| 临时文件 | `%TEMP%\offline-download` 无残留；`download.db` 仅存在于服务临时工作目录（未污染仓库）；临时凭证文件在清理阶段删除 |
| 仓库 | **仅新增本报告文件**；无业务代码修改、无 git commit；运行时产物 `xyy.db`/`localcache.data`/`log/` 均为 gitignore 的本地运行结果 |

> **T9（FIX-2 聚焦复验）清理补充**：core(PID 22428)/download-service(PID 35188)/Python 测试源(PID 9988) 全部停止，8087/8092/18081 确认 FREE；测试目录 `/t7-verify`(4 文件)、`/t7-verify2`(8)、`/t7-verify3`(8)、`/t7-verify4`(8)、`/t9-cold`(5)、`/t9-warm`(0) 及目录本身全部删除（根目录复查无残留）；代理列表为空；`%TEMP%\offline-download` 无残留；FS 构建锁已释放；临时工作区（含凭证文件）已删除。

> **T10（FIX-3 聚焦复验）清理补充**：core(PID 2796)/download-service(PID 15676)/Python 测试源(PID 36020) 全部停止，8087/8092/18081 确认 FREE；测试目录 `/t9-a1`(2)、`/t9-a1w`(8)、`/t9-a2`(8)、`/t9-a2b`(8)、`/t9-d`(6，含 1 个 core 失败残留 `.tmp`)、`/t9-d1`(8)、`/t9-d2`(8)、`/t9-int`(0) 及目录本身全部删除（根目录复查无残留）；`%TEMP%\offline-download` 无残留；FS 构建锁已释放；临时工作区（含凭证文件）已删除；无 git commit、无业务代码修改。

---

## 附录 A：验证命令摘要（可复现）

```powershell
# 1) 构建（持 FS 锁）
New-Item -ItemType Directory "$env:TEMP\sfc-backend-build.lock" | Out-Null
mvn -B clean package -DskipTests        # 期望 BUILD SUCCESS / 38 modules
Remove-Item "$env:TEMP\sfc-backend-build.lock" -Recurse -Force

# 2) core
$env:SFC_INTERNAL_KEY = '<32 字符开发密钥>'
java -Xmx1G -jar sfc-core/target/sfc-core-3.2.0.jar --spring.profiles.active=develop   # cwd=仓库根

# 3) download-service（cwd 建议为仓库外目录）
java -jar microservices/sfc-download-service/target/sfc-download-service-1.0.0.jar

# 4) gateway
$env:SFC_JWT_SECRET = '<xyy.db config.sys.safe.token>'
java -Xmx512m -jar microservices/sfc-gateway/target/sfc-gateway-1.0.0.jar
```

```bash
# 5) 主要接口
POST   http://127.0.0.1:9090/api/service/sfc-download-service/task/download      # 创建（body 含 url/uid/savePath）
GET    http://127.0.0.1:9090/api/service/sfc-download-service/task/download?uid=..&page=1&size=10&type=ALL
GET    http://127.0.0.1:9090/api/service/sfc-download-service/task/download/proxy
DELETE http://127.0.0.1:9090/api/service/sfc-download-service/task/download?taskId=<id>
GET    http://127.0.0.1:8087/api/plugin/autoLoad.js
GET    http://127.0.0.1:8087/api/local-registry/instances?serviceId=sfc-download-service   # 头 X-Sfc-Local-Registry-Password
GET    http://127.0.0.1:8087/api/asyncTask/listRecord?uid=..&taskType=offline-download&page=0&size=50
GET    http://127.0.0.1:8087/api/asyncTask/getLog?taskId=<asyncTaskId>
POST   http://127.0.0.1:8087/api/asyncTask/getProg      # body: [<asyncTaskId>]
```

```bash
# 6) core 内部接口（直连，X-Sfc-Internal-Token 为按 sfc-auth 约定现签的 HS256 JWT）
GET    /api/internal/file/v2/list?uid=<uid>&path=/t7-verify
POST   /api/internal/file/v2/mkdirs?uid=<uid>&path=/&name=t7-verify
POST   /api/internal/file/v2/delete?uid=<uid>&path=/t7-verify   # body: ["<name>", ...]
GET    /api/internal/file/v2/download?uid=<uid>&path=<path>     # 用于 SHA256 校验
```

---

## 附录 B：验证覆盖对照（任务书 → 实际）

| 任务书要求 | 覆盖情况 |
|---|---|
| A 全量构建 | ✅ 仓库根 `mvn clean package -DskipTests`，38/38 SUCCESS |
| B 启动 core（local registry + SFC_INTERNAL_KEY + develop） | ✅ §4 |
| C 启动服务 + 注册 + Worker 认领线程 | ✅ §4 |
| D 启动网关（SFC_JWT_SECRET 从 xyy.db 读取） | ✅ §4（未 BLOCKED） |
| E1/E2 直连与网关列表 | ✅ §5.2 |
| E3 创建任务（可控下载源） | ✅ Python 本地 HTTP 源，§2 |
| E4 状态/进度（服务列表 + core listRecord/getProg） | ✅ §5.2 |
| E5 落盘（大小一致 / 重名 .1） | ✅ 大小 + SHA256 + `.1` |
| E6 中断（运行中/等待中） | ✅（运行中曾 FAIL，修复后 FIXED）§6 |
| E7 代理（列表/无效代理/生效） | ✅ §5.2 |
| E8 JSON 契约 | ✅ §5.2 |
| E9 前端资源 autoLoad | ✅ §5.2 |
| E10 旧插件下线 + 认领归属 | ✅ §5.2 |
| F 回归 | ✅ core 启动/接口/其他插件 autoLoad 正常；无异常 ERROR（并发 SQLite 场景见 §7-3） |

---

*本报告仅记录验证结论与证据，不含任何凭证原文（Token / 密钥 / 密码均未落盘或打印）。*
