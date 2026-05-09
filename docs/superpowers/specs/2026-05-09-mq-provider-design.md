# MQ Provider 可切换设计

## 背景

当前系统通过 `MQService` 对外提供消息广播与消息队列能力，默认实现为 `RedisMQService`。现需要在不改动现有业务调用方式的前提下，新增一个**无任何外部服务依赖**的 `MQService` 实现，并允许通过 `application.yml` 中的 `sys.service.mqProvider` 决定系统使用 `RedisMQService` 还是新的本地实现。

本设计只覆盖 `MQService` provider 切换与本地实现，不扩展到缓存、RPC、集群发现等其他 Redis 依赖。

## 目标

1. 保持现有 `MQService` 接口不变，现有业务调用代码尽量无需修改。
2. 新增一个纯内存、单 JVM 内可运行的本地 `MQService` 实现。
3. 支持通过配置切换 `redis` 与 `local` 两种 provider。
4. 默认行为保持向后兼容，未配置时仍使用 `redis`。

## 非目标

1. 不提供跨节点、跨进程消息投递能力。
2. 不提供消息持久化、高可用、故障恢复语义。
3. 不处理系统中其他 Redis 依赖的移除或替换。

## 配置设计

新增配置项：

```yml
sys:
  service:
    mqProvider: redis
```

配置语义：

- `redis`：使用现有 Redis 实现。
- `local`：使用新的进程内内存实现。
- 未配置时默认 `redis`。

推荐在运行日志中打印当前生效的 provider，便于排查配置问题。

## 装配设计

新增 `MQAutoConfiguration`，统一负责 `MQService` Bean 装配：

- 当 `sys.service.mqProvider=redis` 或未配置时，注册 `RedisMQService`
- 当 `sys.service.mqProvider=local` 时，注册 `LocalMQService`

约束：

1. `RedisMQService` 不再直接使用 `@Service` 暴露为组件，避免与本地实现形成多个 `MQService` Bean。
2. `LocalMQService` 也通过自动配置注册，避免实现类承担装配职责。
3. 任意时刻 Spring 容器中只能存在一个 `MQService` Bean。

该方案与现有项目中 `sys.store.type` + `@ConditionalOnProperty` 的 provider 切换模式保持一致。

## LocalMQService 设计

`LocalMQService` 采用纯内存实现，仅保证**单 JVM 进程内**语义。

### 广播模型

内部维护：

- `topic -> (subscriberId -> consumer)` 的订阅映射

行为规则：

1. `sendBroadcast` 向当前 topic 的所有订阅者投递消息。
2. 广播仅投递给发送时已经存在的订阅者。
3. `unsubscribe` 为幂等操作，重复取消不会报错。

### 队列模型

内部维护：

- `topic -> queueState`
- `queueState` 中保存有序消息记录列表
- `group -> offset` 的消费位点信息
- `subscriberId -> subscriptionMeta` 的订阅元数据

每条消息记录至少包含：

- 记录 id
- topic
- body

记录 id 使用本地递增序列，作为 `AT_CUSTOM` 的定位依据。

### 消费模型

每个消息队列订阅在本地维护独立消费任务，按 group 的 offset 从共享消息列表中推进消费。

行为规则：

1. `createQueue`：创建空队列；若已存在则忽略。
2. `destroyQueue`：删除队列、group 位点以及相关订阅；关联消费任务应停止。
3. `push`：将消息追加到队列末尾，并唤醒等待中的订阅消费任务。
4. `subscribeMessageQueue(..., AT_HEAD, ...)`：从当前队列第一条消息开始消费。
5. `subscribeMessageQueue(..., AT_TAIL, ...)`：只消费订阅建立后的新消息。
6. `subscribeMessageQueue(..., AT_CUSTOM, offsetPoint, ...)`：从指定记录 id 之后开始消费。
7. 当 `AT_CUSTOM` 指定的位点不存在时，显式抛出 `IllegalArgumentException`，不做静默回退。
8. `unsubscribeMessageQueue` 为幂等操作，重复取消不会报错。

### 线程模型

建议使用独立的轻量后台执行器处理队列订阅消费，而不是在 `push` 线程中直接执行所有消费者回调。

原因：

1. 避免生产线程被慢消费者阻塞。
2. 更贴近现有 Redis Stream 消费组按位点推进的模型。
3. 更容易实现订阅取消、队列销毁和等待唤醒。

## 类型与兼容性

`MQService` 已同时支持字符串 topic 和 `MQTopic<T>` 泛型 topic。

本地实现需保持与现有实现一致的对外约定：

1. 字符串 topic 接口继续可用。
2. 泛型 topic 接口继续通过 `MQMessageRecord<T>` 向调用方提供类型化消息。
3. 复杂对象消息仍按当前项目的序列化与反序列化工具链处理，避免调用方出现行为差异。

## 错误处理

1. 非法 offset、空队列定位失败等输入错误应显式抛错。
2. 单个消费者回调异常只记录日志，不应导致整个 topic 或消费线程永久失效。
3. 已销毁队列上的后续消费操作应停止，不再继续分发消息。
4. 对不存在订阅 id 的取消操作保持幂等，不抛错。

## 数据流

### 广播

1. 业务代码调用 `mqService.sendBroadcast(...)`
2. 当前 provider 获取 topic 对应订阅者集合
3. 将消息投递给所有当前订阅者

### 队列

1. 业务代码调用 `mqService.push(...)`
2. provider 将消息追加到 topic 对应队列
3. 唤醒该 topic 下等待中的订阅消费任务
4. 各 group 按自身 offset 顺序消费消息
5. 更新 group offset

## 风险与边界

1. `local` provider 切换后，仅 `MQService` 不再依赖外部 MQ，不代表整个应用可脱离 Redis 启动。
2. 当前系统中的缓存、RPC、集群发现等模块仍可能要求 Redis 可用。
3. `local` provider 更适合单机开发、测试、无分布式要求的运行场景。
4. 若部署为多节点，`local` provider 下节点间广播与队列语义不成立。

## 验证方案

实现后至少验证以下场景：

1. `mqProvider=local` 时成功装配唯一的 `MQService` Bean。
2. `mqProvider=redis` 时保持现有行为不变。
3. 本地广播支持多个订阅者同时接收消息。
4. 本地队列支持按 group 独立消费。
5. `AT_HEAD`、`AT_TAIL`、`AT_CUSTOM` 三种策略行为正确。
6. 取消订阅与销毁队列后不再继续接收消息。

## 推荐实施顺序

1. 新增 MQ provider 配置模型与自动配置类。
2. 调整 `RedisMQService` 的装配方式。
3. 实现 `LocalMQService` 的广播、队列、订阅取消能力。
4. 补充配置文件示例。
5. 编译验证两种 provider 装配路径。
