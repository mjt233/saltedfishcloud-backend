# 微服务异步任务 Worker 接入

> **定位**：让微服务也能成为咸鱼云异步任务系统的**执行者（Worker）**——耗时任务统一进入 sfc-core 的任务队列，
> 由「具备该任务类型能力」的 core 节点或微服务实例认领执行；任务状态、进度、日志、中断、故障转移全部由 core 统一管理。
>
> SDK 模块：`microservices/sfc-task-worker`（零宿主依赖，不继承 root pom）。
> core 侧框架说明见 [异步任务](../framework/async-task.md)。

## 1. 工作原理

```
微服务启动 ──► 注册能力（taskType 列表 + 负载 + 回调地址）──► 之后每 10s 心跳续约
                │
sfc-core 任务队列（async_task_record）
                │  按任务类型路由认领（长轮询，只看到自己支持的类型）
                ▼
微服务认领任务 ──► 本地线程池执行 handler ──► 周期上报进度/日志 ──► 上报最终状态
                ▲                                   │
                └──── core 下发中断指令（HTTP 回调） ◄┘
```

- **能力声明**：容器中每个 `SfcTaskHandler` Bean 的 `taskType()` 都会随注册（心跳）上报给 core；
- **类型路由**：任务只会被派发给声明支持该类型的执行者，微服务之间、微服务与 core 之间互不影响；
- **统一管理**：微服务执行的任务在 core 的 `/api/asyncTask/**` 接口、前端任务列表中与 core 本地任务完全一致；
- **故障转移**：微服务实例停止心跳超过 30 秒即视为失联，其运行中任务会被 core 回收并重新排队。

## 2. 快速接入（三步）

### 2.1 引入依赖

```xml
<dependency>
    <groupId>com.sfc.cloud</groupId>
    <artifactId>sfc-task-worker</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 微服务工程需能解析 `sfc-task-worker`（仓库根 reactor 构建时会自动包含；单独构建 `microservices/` 时，
> 请先在仓库根执行 `mvn -pl sfc-task-worker,sfc-auth install` 或从仓库根用 `-pl` 构建目标模块）。

### 2.2 开启内部接口鉴权与 Worker

```java
@SpringBootApplication
@EnableSfcInternalAuth   // 已有则无需重复：用于校验 core 下发的中断指令、签发调用 core 的内部凭证
@EnableSfcTaskWorker     // 启用异步任务 Worker
public class MyServiceApplication { ... }
```

### 2.3 实现任务处理器

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

## 3. 配置项（前缀 `sfc.task.worker`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用 Worker（关闭后不注册、不认领、不暴露中断端点） |
| `core-base-url` | `http://127.0.0.1:8087` | sfc-core 内部接口基址 |
| `worker-id` | 自动生成 | Worker 唯一标识，默认 `服务名:主机:端口:随机后缀`；多实例部署保持默认 |
| `service-name` | `spring.application.name` | 所属服务名（随注册上报，便于管理端识别） |
| `callback-base-url` | 自动探测 | 本实例对 core 的回调地址（不含路径）；core 通过 `{callback-base-url}/api/internal/task/interrupt` 下发中断。容器/多网卡环境建议显式配置为 core 可达的地址 |
| `max-load` | CPU 核数 × 100 | 最大负载（100 = 1 个 CPU 核心算力），随心跳上报 |
| `heartbeat-interval` | `10s` | 心跳（重新注册）间隔；必须明显小于 core 侧 30s 的注册有效期 |
| `claim-wait-seconds` | `30` | 认领任务的长轮询等待秒数，≤0 表示不等待 |
| `max-concurrent-tasks` | CPU 核数 | 最大并发执行任务数，满载后暂停认领 |

共享密钥（来自 sfc-auth SDK，必须与 core 一致）：

| 配置项 | 环境变量 | 说明 |
|---|---|---|
| `sfc.internal.key` | `SFC_INTERNAL_KEY` | 服务间内部调用共享密钥（≥32 字符）；未配置时无法签发/校验内部凭证 |

## 4. 提交任务

### 4.1 微服务侧提交（推荐）

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
                false                   // 是否临时任务
        ));
    }
}
```

### 4.2 core 侧提交

core 内部代码直接注入 `AsyncTaskManager`：

```java
AsyncTaskRecord task = AsyncTaskRecord.builder()
        .name("报表导出")
        .taskType("report-export")
        .params(reportParam)
        .cpuOverhead(50)
        .build();
asyncTaskManager.submitAsyncTask(task);
```

## 5. 运行机制

| 环节 | 行为 |
|---|---|
| 启动 | 注册当前 Worker（能力/负载/回调地址）；注册失败不阻断应用启动，由心跳自动重试 |
| 心跳 | 每 `heartbeat-interval` 重新注册一次（幂等），同步最新负载与任务类型 |
| 认领 | 单线程长轮询 `claim`，只请求自己支持的任务类型；无任务时最多等待 `claim-wait-seconds`；满载时暂停认领 |
| 执行 | 固定大小线程池（`max-concurrent-tasks`），任务开始前上报 `report/start`（任务已被取消时跳过执行） |
| 上报 | 约 1 秒周期上报进度与日志；结束时上报 `report/finish`（FINISH/FAILED/CANCEL）+ 尾部日志 |
| 中断 | core 向 `callback-base-url` 发送 `POST /api/internal/task/interrupt`，SDK 触发中断标记与回调；任务尚未开始执行时会先登记，开始执行后立即生效 |
| 优雅停机 | 停止认领 → 注销 Worker → 对运行中任务上报 `report/abandon`（任务回到队列）→ 最多等待数秒让执行线程退出 |

## 6. 示例与验证

`sfc-demo-service` 内置了完整示例：

- 处理器：`com.sfc.demo.task.DemoSleepTaskHandler`（任务类型 `demo-sleep`）；
- 提交接口：`POST /api/demo/task/submit?uid=0&millis=5000&steps=10`，返回任务id；
- 启动 demo 服务与 sfc-core（配置同一份 `SFC_INTERNAL_KEY`）后：
  1. 管理员调用 `GET /api/asyncTask/listWorker` 应能看到 `sfc-demo-service` 的 Worker（类型含 `demo-sleep`）；
  2. 调用 `POST /api/demo/task/submit` 提交任务；
  3. 通过 `GET /api/asyncTask/listRecord`、`POST /api/asyncTask/getProg`、`GET /api/asyncTask/getLog` 观察任务被本服务认领执行、进度与日志正常上报；
  4. 调用 `POST /api/asyncTask/interrupt` 可中断任务，任务状态变为已取消。

## 7. 注意事项

- **任务类型是全局路由键**：多个执行者声明同一类型时，任务会被任意一个认领（可用于同类任务的横向扩容）；
  不同业务务必使用不同的任务类型前缀（如 `ai-xxx`、`report-xxx`）避免误认领；
- **任务参数由提交方与处理器自行约定**，建议使用 JSON 字符串并保持向后兼容；
- **执行者的能力随心跳生效**：新部署的微服务最多约 10 秒后才会被调度到；下线后其任务最多约 30 秒被判定失联并回收；
- `/api/internal/**` 属于内部接口前缀，网关会直接拒绝外部访问，微服务与 core 只应部署在内网；
- 中断是「尽力而为」：处理器应周期性调用 `checkInterrupted()` 或注册中断回调，否则 core 只能等待任务自然结束。
