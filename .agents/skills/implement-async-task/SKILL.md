---
name: implement-async-task
description: 实现一个异步任务
---

实现与调用异步任务时，请阅读项目文档 `docs/develop/framework/async-task.md`，按照文档中的说明完成以下步骤：

1. **创建任务执行类**：实现 `com.sfc.task.AsyncTask` 接口，包含 `execute`、`interrupt`、`isRunning`、`getParams`、`getProgress` 方法。
2. **创建任务工厂**：实现 `com.sfc.task.AsyncTaskFactory` 接口，`getTaskType()` 返回该类任务的唯一标识字符串。
3. **注册工厂**：优先使用 `@Component` 注解自动注册到 Spring 容器。
4. **提交任务**：在 Service 中注入 `AsyncTaskManager`，构造 `AsyncTaskRecord` 后调用 `asyncTaskManager.submitAsyncTask(asyncTaskRecord)` 提交。
