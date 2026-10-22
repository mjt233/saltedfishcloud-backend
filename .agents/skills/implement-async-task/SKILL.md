---
name: implement-async-task
description: 实现一个异步任务
---

实现与调用异步任务时，请先阅读项目文档 [`docs/develop/framework/async-task.md`](../../../docs/develop/framework/async-task.md)，根据任务归属选择对应方式：

## 一、任务在 core 宿主内执行

1. **创建任务执行类**：实现 `com.sfc.task.AsyncTask` 接口（`execute`、`interrupt`、`isRunning`、`getParams`、`getProgress`）。
2. **创建任务工厂**：实现 `com.sfc.task.AsyncTaskFactory` 接口，`getTaskType()` 返回该类任务的唯一标识（同时是调度路由依据）。
3. **注册工厂**：优先使用 `@Component` 注解自动注册到 Spring 容器。
4. **提交任务**：注入 `AsyncTaskManager`，构造 `AsyncTaskRecord` 后调用 `asyncTaskManager.submitAsyncTask(asyncTaskRecord)` 提交；需要以指定用户提交时可用 `submitTaskForUser(uid, param)`。

## 二、任务在微服务内执行

1. 微服务引入 `com.sfc.cloud:sfc-task-worker` 依赖并标注 `@EnableSfcTaskWorker`。
2. 实现 `SfcTaskHandler` 接口：`taskType()` 返回任务类型，`execute(ctx)` 中通过 ctx 输出日志、更新进度并响应中断。
3. 任务类型会随 Worker 心跳自动注册到 core，core 侧用同名 `taskType` 提交任务即可被该微服务认领执行。
4. 详细配置、SDK 运行机制与示例见 [`docs/develop/microservice/async-task-worker.md`](../../../docs/develop/microservice/async-task-worker.md)。

> 注意：执行者只认领自己声明支持的任务类型；提交任务前应确认对应类型的执行者已上线（管理员可调用 `/api/asyncTask/listWorker` 查看）。
