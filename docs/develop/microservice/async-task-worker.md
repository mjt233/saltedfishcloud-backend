# 微服务异步任务 Worker 接入

> **定位**：让微服务成为咸鱼云异步任务系统的**执行者（Worker）**——耗时任务统一进入 sfc-core 的任务队列，
> 由「具备该任务类型能力」的 core 节点或微服务实例认领执行；任务状态、进度、日志、中断、故障转移全部由 core 统一管理。
>
> SDK 模块：`microservices/sfc-task-worker`（零宿主依赖，不继承 root pom）。
> **宿主 core 也内嵌本 SDK**：`sfc-core` 同时依赖 `sfc-core-task`（调度与控制面）与 `sfc-task-worker`（执行侧），
> 以 `role=CORE` 运行同一套 Worker 实现。微服务接入只是用同一实现以 `role=SERVICE` 加入任务系统，
> 两者的认领、执行、上报、中断与停机行为完全一致。框架总览见 [异步任务](../framework/async-task.md)。

## 1. 工作原理

```
微服务启动 ──► 照常注册到服务注册中心（Nacos / local registry，实例地址 host:port）
                │
                ▼
sfc-core 调度与控制面（async_task_record 任务队列）
                │  按任务类型路由认领（长轮询，只看到自己支持的类型；
                │  满载时只认领紧急任务）
                ▼
Worker（微服务内嵌 / core 内嵌）认领任务 ──► 本地线程池执行 handler ──► 周期上报进度/日志 ──► 上报最终状态
                ▲                                        │
                └──── core 从注册中心查出实例地址，下发中断指令（HTTP）◄┘
```

- **能力声明**：容器中每个 `SfcTaskHandler` Bean 的 `taskType()` 就是该实例支持的任务类型，随 `claim` 请求携带，
  不需要额外注册能力或心跳；
- **存活判定**：core 以「该实例是否还在服务注册中心」判定执行者是否存活——实例消失/注册过期即视为失联，
  其运行中任务会被回收重排；因此 **Worker 必须注册到与 core 相同的服务注册中心**；
- **类型路由**：任务只会被派发给支持该类型的执行者，微服务之间、微服务与 core 之间互不影响；
- **统一管理**：微服务执行的任务在 core 的 `/api/asyncTask/**` 接口、前端任务列表中与 core 内嵌 Worker 执行的任务完全一致；
- **SDK 调用 core**：经 Spring Cloud LoadBalancer 按服务名选取 `sfc-core` 实例（多实例默认轮询），
  每次出站请求重新选路；**必须注册到与 core 相同的注册中心**，不支持直连兜底地址；
- **协议唯一来源**：执行者与调度面之间的报文类型定义在 `com.sfc.cloud.task.worker.protocol`，
  core 的 `TaskWorkerInternalController` / `TaskInterruptRouter` 直接复用同一份类型，微服务侧不要另行定义 DTO。

> **部署前提**：单节点部署可用 `sys.service.registry-provider=local`（进程内注册表）；
> **多个 core 节点部署必须使用 `nacos`**，否则各节点看不到彼此的实例，会把对方执行的任务误判为失联。
> 微服务侧同样需要启用服务发现（Nacos 或 `sfc-local-registry-client`）。

## 2. 与宿主内嵌 Worker 的关系

| 维度 | 宿主 core 内嵌 Worker | 微服务 Worker |
|---|---|---|
| 引入方式 | `sfc-core` 依赖 `sfc-core-task` 与 `sfc-task-worker`，自动装配 | 引入 `sfc-task-worker` 依赖，标注 `@EnableSfcTaskWorker` |
| 任务类型来源 | 容器中的全部 `AsyncTaskFactory` Bean（`AsyncTaskWorkerConfiguration` 桥接为 `SfcTaskHandler`） | 容器中的全部 `SfcTaskHandler` Bean |
| 角色 `sfc.task.worker.role` | 部署配置中显式设为 `CORE` | 默认 `SERVICE`（保持默认即可） |
| 调度面 | 本进程内的调度与控制面，经服务发现 + 内部 HTTP 调用（单机同样需要内部密钥） | core 集群 |
| 执行侧实现 | **同一套**：认领循环、`max-concurrent-tasks` 槽位、线程池、进度/日志上报、中断端点、停机放弃 | 同左 |

角色随认领请求上报并记录到任务记录的 `claimed_role` 字段，供管理接口展示与排障使用；
中断指令的下发方式与角色无关：core 一律按服务发现定位执行者实例地址后走内部 HTTP。

## 3. 快速接入（三步）

### 3.1 引入依赖

```xml
<dependency>
    <groupId>com.sfc.cloud</groupId>
    <artifactId>sfc-task-worker</artifactId>
    <version>1.0.0</version>
</dependency>
```

> SDK 额外依赖 `spring-cloud-commons`（服务发现/注册契约）与 `spring-cloud-starter-loadbalancer`（按服务名选路），
> 版本由 `microservices/pom.xml` 的 Spring Cloud BOM 统一管理。
> 微服务工程需能解析 `sfc-task-worker`（仓库根 reactor 构建时会自动包含；单独构建 `microservices/` 时，
> 请先在仓库根执行 `mvn -pl microservices/sfc-task-worker -am install`）。

### 3.2 开启内部接口鉴权与 Worker

```java
@SpringBootApplication
@EnableDiscoveryClient      // 需要服务发现（Nacos 或 local registry），与 core 使用同一注册中心
@EnableSfcInternalAuth     // 已有则无需重复：用于校验 core 下发的中断指令、签发调用 core 的内部凭证
@EnableSfcTaskWorker       // 启用异步任务 Worker（也可仅依赖 SDK，自动装配文件同样生效）
public class MyServiceApplication { ... }
```

> ⚠️ **内部调用密钥是必配项**：`sfc.internal.key`（环境变量 `SFC_INTERNAL_KEY`）必须与 core 完全一致，
> 且不少于 32 个字符。未配置时本服务无法签发凭证（提交任务/认领上报全部失败），
> Worker 启动自检还会打印 ERROR 并自动禁用认领（应用继续运行）；配置不一致时 core 返回 403。
>
> ⚠️ **负载均衡拦截器、凭证签发器与服务注册信息是必需 Bean**：Worker SDK 直接注入
> `BlockingLoadBalancerInterceptor`（由 `spring-cloud-starter-loadbalancer` 自动装配）、`SfcInternalTokenIssuer`
> （由 `sfc-auth` 自动装配）与 `Registration`（由服务注册实现提供），任一缺失时 Bean 创建失败、
> **应用直接启动失败**（由 Spring Boot 默认策略报错），不再静默降级。请确认已引入
> `spring-cloud-starter-loadbalancer`（本 SDK 已传递引入）并启用服务注册与发现。

### 3.3 实现任务处理器

```java
package com.example.task;

import com.sfc.cloud.task.worker.SfcTaskExecution;
import com.sfc.cloud.task.worker.SfcTaskHandler;
import org.springframework.stereotype.Component;

@Component
public class ReportExportTaskHandler implements SfcTaskHandler {

    @Override
    public String taskType() {
        // 任务类型是全局唯一标识，也是 core 调度路由的依据；提交任务时必须使用同一个值
        return "report-export";
    }

    @Override
    public void execute(SfcTaskExecution execution) throws Exception {
        // 任务参数（提交方传入的字符串，通常是 JSON）
        String params = execution.getParams();
        execution.getProgress().setTotal(100);
        execution.log("开始导出报表：" + params);

        for (int i = 1; i <= 100; i++) {
            // 响应 core 下发的中断指令（也可用 onInterrupt 注册资源清理回调）
            execution.checkInterrupted();
            doSomeWork(i);
            execution.getProgress().setLoaded(i);
        }
        execution.log("导出完成");
    }
}
```

处理器实现约定：

| 能力 | 用法 |
|---|---|
| 读取任务信息 | `getTaskId()` / `getTaskName()` / `getTaskType()` / `getUid()` / `getParams()` |
| 输出日志 | `log("...")` 或 `getLogWriter()`（按行缓冲，SDK 定期批量上报） |
| 更新进度 | `getProgress().setTotal(n)` / `setLoaded(n)` / `addLoaded(n)`；速度由 SDK 计算，约 1 秒上报一次 |
| 响应中断 | 周期性调用 `checkInterrupted()`；或用 `onInterrupt(Runnable)` 注册打断阻塞、关闭资源的回调 |
| 任务失败 | 直接抛出异常，SDK 会上报 FAILED 并携带异常信息与尾部日志 |

> 宿主内嵌执行者还可以通过 `setProgressSource(Supplier<Map<String,Object>>)` 提供包含扩展字段的完整进度快照；
> 微服务处理器无需使用该方法，直接更新 `getProgress()` 即可。

## 4. 配置项（前缀 `sfc.task.worker`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用 Worker（关闭后不认领任务，也不暴露中断端点） |
| `core-service-id` | `sfc-core` | core 在服务注册中心中的服务名，SDK 经负载均衡按该服务名选取 core 实例 |
| `role` | `SERVICE` | 本实例的执行者角色：`CORE` = core 节点内嵌 Worker，`SERVICE` = 微服务实例；留空按 `SERVICE` 处理 |
| `claim-wait-seconds` | `30` | 认领任务的长轮询等待秒数，≤0 表示不等待；满载时 SDK 自动缩短为不超过 3 秒 |
| `max-concurrent-tasks` | CPU 核数 | 普通任务的最大并发槽位；达到上限后只认领紧急任务（紧急任务不受该上限限制） |

共享密钥（来自 sfc-auth SDK，必须与 core 完全一致）：

| 配置项 | 环境变量 | 说明 |
|---|---|---|
| `sfc.internal.key` | `SFC_INTERNAL_KEY` | 服务间内部调用共享密钥（≥32 字符）。**必配**：未配置时无法签发/校验内部凭证，Worker 自检失败并禁用认领 |

> `role` 随认领请求上报，仅用于任务记录的角色标注与排障；微服务保持默认 `SERVICE` 即可。
> 仅 core 节点（`sfc-core` 的部署配置）应显式设置 `role: CORE`。
>
> 执行者身份（`serviceId` 与 `workerId=host:port`）直接取自服务注册信息（`Registration`，必需 Bean），不提供配置项。
>
> SDK 仅在 **Servlet Web 应用**（Spring MVC）中自动装配；非 Web 应用不会启动 Worker。

## 5. 提交任务

### 5.1 微服务侧提交（推荐）

注入 SDK 提供的 `SfcTaskWorkerClient`：

```java
import com.sfc.cloud.task.worker.SfcTaskWorkerClient;
import com.sfc.cloud.task.worker.protocol.TaskSubmitParam;

@RequiredArgsConstructor
@Service
public class ReportService {
    private final SfcTaskWorkerClient taskWorkerClient;

    public Long export(Long uid, String reportParam) {
        return taskWorkerClient.submit(new TaskSubmitParam(
                "报表导出",              // 任务名称
                "report-export",        // 任务类型，必须与某个执行者的 taskType 一致
                reportParam,            // 任务参数
                uid,                    // 任务归属用户id
                50,                     // CPU 开销指数
                false,                  // 是否临时任务（true：不参与故障转移重试）
                true                    // 是否立即执行（true：紧急优先级，执行者满载也优先执行）
        ));
    }
}
```

### 5.2 core 侧提交

core 内部代码直接注入 `AsyncTaskManager`：

```java
AsyncTaskRecord task = AsyncTaskRecord.builder()
        .name("报表导出")
        .taskType("report-export")
        .params(reportParam)
        .cpuOverhead(50)
        .build();
// 或使用 createTask(AsyncTaskCreateParam) 指定 immediate / isTemp
asyncTaskManager.submitAsyncTask(task);
```

## 6. 查询任务状态与进度

微服务自己维护的任务状态镜像（如业务表里的状态/已下载量/总大小字段）需要定期与 core 的任务记录对齐：

```java
import com.sfc.cloud.task.worker.SfcTaskWorkerClient;
import com.sfc.cloud.task.worker.protocol.TaskStateInfo;

List<Long> taskIds = taskRecords.stream().map(MyTask::getTaskId).toList();
List<TaskStateInfo> states = taskWorkerClient.queryTasks(taskIds);
for (TaskStateInfo state : states) {
    // state.taskId()     任务id
    // state.status()     0 等待中 / 1 运行中 / 2 执行完成 / 3 任务失败 / 4 已取消 / 5 任务离线
    // state.loaded()     已完成的量；无进度快照时为 null
    // state.total()      目标总量；无进度快照时为 null，-1 表示总量未知
    // state.speed()      速度（单位由任务实现自行约定）；无进度快照时为 null
    // state.finishDate() 完成/失败/取消时间；任务未结束时为 null
}
```

约束：

- **内部接口**：`POST /api/internal/task/query` 由 sfc-auth 校验内部调用凭证，调用方必须与 core 配置同一份
  `sfc.internal.key`，并注册到同一服务注册中心；
- **批量上限 500**：`taskIds` 不能为空且单次最多 500 个，超出时 core 返回 400，建议按本地分页/批次查询；
- **只返回存在的任务**：core 中不存在的 taskId 会被忽略，结果**不保证**与入参顺序一致，调用方应按
  `taskId` 对齐并对缺失项按「任务已不存在」处理；
- **进度是快照**：进度来自 core 的进度快照缓存（执行者约 1 秒上报一次、快照 10 秒过期），只有运行中的任务
  通常有值；任务结束后不再保证有进度，最终体量以任务结果/业务表为准；
- **fail-soft 建议**：查询失败（如 core 暂时不可用，异常码 503）时只记录 WARN 并回退本地镜像，不要阻塞
  列表查询本身；
- **多 core 部署**：需使用共享缓存（`cache-provider=redis`），否则可能查不到其他 core 节点接收的进度快照；
  任务状态来自共享数据库，不受影响。

## 7. 取消任务

```java
// 取消等待中或运行中的任务；已结束（完成/失败/取消）的任务会返回 400
taskWorkerClient.cancel(taskId);
```

约束：

- **语义**：等待中的任务由 core 直接置为已取消，不会再被执行者认领；运行中的任务由 core 从注册中心查出
  执行者 `host:port` 后下发中断指令，最终状态由执行者上报（CANCEL）或由 core 侧判定；
- **尽力而为**：中断能否立刻生效取决于处理器是否响应 `checkInterrupted()` / `onInterrupt(...)`；
  处理器不响应时只能等待任务自然结束；
- **错误映射**：`taskId` 为空、任务不存在或不在可中断状态（已完成/已失败/已取消）时抛出
  `SfcJsonException(400)`；执行者已失联或中断指令下发失败时抛出 `SfcJsonException(409)`；
  无可用 core 实例或网络失败时抛出 `SfcJsonException(503)`；
- **幂等建议**：对已结束任务调用会得到 400，调用方应按「本地状态已是终态则直接返回」做幂等处理；
- 取消不会删除任务记录、运行日志与历史日志。

## 8. 运行机制

| 环节 | 行为 |
|---|---|
| 启动自检 | `SmartLifecycle` 在 Web 容器启动后拉起 Worker：先检查内部接口鉴权密钥是否已配置、是否启用服务发现，任一不满足即打印 ERROR 并禁用认领（应用继续运行）；负载均衡拦截器、凭证签发器与服务注册信息缺失则应用在启动阶段直接失败（Spring Boot 依赖注入兜底）。认领线程随后等待「本实例已在服务注册中心可见」，最多 60 秒，超时打印 ERROR 并停止认领 |
| 认领 | 单线程长轮询 `claim`，携带 `serviceId`、`workerId`（`host:port`）、`role`（配置项，默认 `SERVICE`）、支持的任务类型；无任务时最多等待 `claim-wait-seconds` |
| 紧急车道 | 满载（运行中任务数 + 已认领未开始任务数 ≥ `max-concurrent-tasks`）时以 `urgentOnly=true` 只认领紧急任务，且不占用并发槽位；等容量释放后回到普通车道 |
| 执行 | 弹性线程池（核心线程数 = `max-concurrent-tasks`，上限 1024）：普通任务由认领槽位限流，紧急任务可超出槽位立即执行；任务开始前上报 `report/start`（任务已被取消时跳过执行） |
| 进度上报 | 约 1 秒节流上报；速度由 SDK 根据两次采样差值计算。任务上报的进度快照若包含扩展字段，会作为协议字段 `extensions` 一并上报 |
| 日志上报 | 执行上下文按行缓冲日志（单任务最多缓存 5000 行，超出丢弃最旧行并在日志中提示），每个上报周期按批提交（单批最多 500 行、每周期最多 10 批）；实时监听通过 core 的 MQ 主题推送 |
| 结束上报 | 结束前补发一次最终进度快照（保证完成态进度与 total 一致），再上报 `report/finish`（FINISH/FAILED/CANCEL）+ 尚未上报的尾部日志（最多 5000 行） |
| 可靠上报 | 开始/结束/放弃上报失败时自动重试（最多 3 次，退避 500/1000ms），降低网络瞬断导致任务状态卡在运行中的风险；进度与日志允许随下一次周期重发 |
| 中断 | core 从服务注册中心查出本实例的 `host:port`，向 `http://host:port/api/internal/task/interrupt` 下发中断指令；SDK 触发中断标记与回调，任务尚未开始执行时先暂存指令、开始执行后立即生效；中断幂等且尽力而为 |
| 优雅停机 | 停止认领 → 对运行中任务上报 `report/abandon`（普通任务回到队列，临时任务置失败）→ 最多等待数秒让执行线程退出；实例从注册中心消失后，core 的健康检查会兜底回收漏报的任务 |

> **上报目的地**：Worker 的每次内部调用都以服务名作为 URL 的 host（默认 `http://sfc-core`），
> 由 Spring Cloud LoadBalancer 官方拦截器在请求发出前改写为真实实例地址（多实例默认轮询）。
> 因此多 core 部署需要共享缓存（`cache-provider=redis`）与共享 MQ（`mq-provider=redis`），
> 否则运行中日志和进度快照只落在接收上报的那个 core 节点上，从其他节点查询可能缺失（历史日志在任务结束时统一落库，不受影响）。

## 9. 示例与验证

`sfc-demo-service` 内置了完整示例：

- 处理器：`com.sfc.demo.task.DemoSleepTaskHandler`（任务类型 `demo-sleep`，参数支持毫秒数或 JSON `{millis, steps}`）；
- 提交接口：`POST /api/demo/task/submit?uid=0&millis=5000&steps=10&immediate=false&isTemp=false&taskType=demo-sleep`，返回任务id；
- 启动 demo 服务与 sfc-core（**使用同一注册中心**，并配置同一份 `SFC_INTERNAL_KEY`）后：
  1. 管理员调用 `GET /api/asyncTask/listWorker` 应能看到 `sfc-core` 与 `sfc-demo-service` 的实例
     （当前节点 `sfc-core` 会标注 `role=CORE` 并给出支持的任务类型，`sfc-demo-service` 的角色与类型为空）；
  2. 调用 `POST /api/demo/task/submit` 提交任务；
  3. 通过 `GET /api/asyncTask/listRecord`、`POST /api/asyncTask/getProg`、`GET /api/asyncTask/getLog` 观察任务被本服务认领执行、进度与日志正常上报；
  4. 调用 `POST /api/asyncTask/interrupt` 可中断任务，任务状态变为已取消；
  5. 传 `immediate=true` 可验证紧急任务在满载时仍被执行；传 `taskType=test` 可验证 core 内嵌 Worker 同样能认领并执行任务。

## 10. 注意事项

- **任务类型是全局路由键**：多个执行者声明同一类型时，任务会被任意一个认领（可用于同类任务的横向扩容）；
  不同业务务必使用不同的任务类型前缀（如 `ai-xxx`、`report-xxx`）避免误认领；同一实例内类型重复会在启动时报错；
- **任务参数由提交方与处理器自行约定**，建议使用 JSON 字符串并保持向后兼容；
- **内部协议不要自行复制**：`/api/internal/task/**` 的报文类型统一使用 `com.sfc.cloud.task.worker.protocol`，
  core 侧复用同一份定义，自行维护镜像 DTO 会因字段漂移导致解析失败；
- **Worker 必须对 core 可达且可被发现**：core 通过注册中心里的实例地址回调中断，容器/多网卡环境请正确配置
  `spring.cloud.nacos.discovery.ip` 等注册地址，保证 core 能访问到该地址；
- **实例必须在注册中心可见后才能认领**：SDK 已内置等待与自检，若日志出现「等待 60 秒后仍未在服务注册中心中发现本实例」，
  说明注册中心配置有误（服务名、namespace、group 与 core 不一致等）；
- **内部密钥必配**：`sfc.internal.key`（`SFC_INTERNAL_KEY`）未配置时 Worker 自动禁用认领；密钥不一致时 core 侧返回 403。
  该要求对单机部署的 core 同样适用（core 内嵌 Worker 也走内部 HTTP）；
- **多 core 部署**：必须使用 `nacos` 作为注册中心，并使用共享缓存与共享 MQ，否则跨节点查询运行日志/进度可能缺失；
- `/api/internal/**` 属于内部接口前缀，网关会直接拒绝外部访问，微服务与 core 只应部署在内网；
- 中断是「尽力而为」：处理器应周期性调用 `checkInterrupted()` 或注册中断回调，否则 core 只能等待任务自然结束。
