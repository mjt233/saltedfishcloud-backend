# MQ Provider Switching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a no-external-dependency `MQService` implementation and switch between `RedisMQService` and the new local implementation through `sys.service.mq-provider`.

**Architecture:** Keep the `MQService` API unchanged and move provider selection into Spring auto-configuration. Add a `LocalMQService` that implements process-local broadcast and queue semantics, while keeping `RedisMQService` as the default provider. The change is intentionally scoped to `MQService`; Redis-backed cache, RPC, and cluster behavior remain untouched.

**Tech Stack:** Java, Spring Boot, `@ConfigurationProperties`, `@ConditionalOnProperty`, concurrent collections, JDK executors, IntelliJ build tooling

---

## File map

- Modify: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/config/SysProperties.java`
  - Add `service.mq-provider` configuration model with default `redis` and JavaDoc.
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/MQAutoConfiguration.java`
  - Own all `MQService` bean registration logic.
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/RedisMQService.java`
  - Remove direct component registration so it is only created by `MQAutoConfiguration`.
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/LocalMQService.java`
  - Provide in-memory broadcast, queue creation, queue destroy, queue subscription, offset handling, and unsubscribe behavior.
- Modify: `sfc-core/src/main/config/application.yml`
  - Add the default/commented `sys.service.mq-provider` entry so users know the switch exists.
- Modify: `sfc-core/src/main/config/application-develop.yml`
  - Add `sys.service.mq-provider` example value for developer deployments.
- Modify: `sfc-core/src/main/config/application-product.yml`
  - Add `sys.service.mq-provider` example value for packaged deployments.

## Constraints

- Do **not** change the `MQService` interface in `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/service/mq/MQService.java`.
- Do **not** broaden scope into cache, RPC, or cluster Redis replacements.
- Do **not** add new test classes unless the user explicitly asks for them. Validate with compilation and existing project checks instead.
- All newly added methods and fields must have JavaDoc comments that match repository conventions.

### Task 1: Add provider configuration and Spring wiring

**Files:**
- Modify: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/config/SysProperties.java`
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/MQAutoConfiguration.java`
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/RedisMQService.java`
- Modify: `sfc-core/src/main/config/application.yml`
- Modify: `sfc-core/src/main/config/application-develop.yml`
- Modify: `sfc-core/src/main/config/application-product.yml`

- [ ] **Step 1: Extend `SysProperties` with `service.mq-provider`**

```java
@Data
public class SysProperties implements InitializingBean {
    @Value("${app.version}")
    private Version version;
    private Store store;
    private Service service = new Service();

    @Data
    public static class Service {
        /**
         * 消息队列服务提供者，可选 redis 或 local。
         */
        private String mqProvider = "redis";
    }
}
```

- [ ] **Step 2: Add `MQAutoConfiguration` and make provider selection explicit**

```java
@Configuration
public class MQAutoConfiguration {
    /**
     * 注册 Redis MQ 实现。
     */
    @Bean
    @ConditionalOnProperty(prefix = "sys.service", name = "mq-provider", havingValue = "redis", matchIfMissing = true)
    public MQService redisMqService(
            RedisTemplate<String, Object> redisTemplate,
            RedisMessageListenerContainer redisMessageListenerContainer,
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> stringStreamMessageListenerContainer
    ) {
        return new RedisMQService(redisTemplate, redisMessageListenerContainer, stringStreamMessageListenerContainer);
    }

    /**
     * 注册本地 MQ 实现。
     */
    @Bean
    @ConditionalOnProperty(prefix = "sys.service", name = "mq-provider", havingValue = "local")
    public MQService localMqService() {
        return new LocalMQService();
    }
}
```

- [ ] **Step 3: Convert `RedisMQService` from component scanning to constructor-based bean creation**

```java
@Slf4j
@RequiredArgsConstructor
public class RedisMQService implements MQService {
    private static final String LOG_PREFIX = "[消息队列]";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> stringStreamMessageListenerContainer;
}
```

- [ ] **Step 4: Add config examples to all shipped YAML files**

```yml
sys:
  service:
     # 可选 redis / local。local 仅支持单 JVM 进程内消息语义
     mq-provider: redis
```

- [ ] **Step 5: Rebuild the touched files**

```text
IDEA-build_project(
  projectPath="C:\\Users\\xiaotao\\code\\saltedfishcloud-backend",
  filesToRebuild=[
    "sfc-api/src/main/java/com/xiaotao/saltedfishcloud/config/SysProperties.java",
    "sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/MQAutoConfiguration.java",
    "sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/RedisMQService.java"
  ],
  timeout=120000
)
```

Expected: no compilation errors and only one `MQService` bean path available per property value.

- [ ] **Step 6: Commit the wiring change**

```bash
git add sfc-api/src/main/java/com/xiaotao/saltedfishcloud/config/SysProperties.java sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/MQAutoConfiguration.java sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/RedisMQService.java sfc-core/src/main/config/application.yml sfc-core/src/main/config/application-develop.yml sfc-core/src/main/config/application-product.yml
git commit -m "feat: add mq provider configuration"
```

### Task 2: Implement local broadcast and subscription lifecycle

**Files:**
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/LocalMQService.java`

- [ ] **Step 1: Add the class skeleton and state holders**

```java
@Slf4j
public class LocalMQService implements MQService {
    private static final String LOG_PREFIX = "[本地消息队列]";
    private final AtomicLong subscriberIdGenerator = new AtomicLong(1);
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Consumer<MQMessage>>> broadcastSubscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, SubscriptionType> subscriptionTypes = new ConcurrentHashMap<>();

    /**
     * 订阅类型。
     */
    private enum SubscriptionType {
        BROADCAST,
        QUEUE
    }
}
```

- [ ] **Step 2: Implement string-topic broadcast send/subscribe/unsubscribe**

```java
@Override
public void sendBroadcast(String topic, Object msg) {
    MQMessage mqMessage = MQMessage.builder().topic(topic).body(msg).build();
    broadcastSubscribers.getOrDefault(topic, new ConcurrentHashMap<>())
        .values()
        .forEach(consumer -> safeConsumeBroadcast(topic, consumer, mqMessage));
}

@Override
public long subscribeBroadcast(String topic, Consumer<MQMessage> consumer) {
    long subscriberId = subscriberIdGenerator.getAndIncrement();
    broadcastSubscribers.computeIfAbsent(topic, key -> new ConcurrentHashMap<>()).put(subscriberId, consumer);
    subscriptionTypes.put(subscriberId, SubscriptionType.BROADCAST);
    return subscriberId;
}

@Override
public void unsubscribe(Long id) {
    broadcastSubscribers.values().forEach(subscribers -> subscribers.remove(id));
    subscriptionTypes.remove(id);
}
```

- [ ] **Step 3: Implement typed broadcast adapters on top of the string-topic methods**

```java
@Override
public <T> void sendBroadcast(MQTopic<T> topic, T msg) {
    sendBroadcast(topic.getTopic(), msg);
}

@Override
@SuppressWarnings("unchecked")
public <T> long subscribeBroadcast(MQTopic<T> topic, Consumer<MQMessageRecord<T>> consumer) {
    Class<?> clazz = ClassUtils.getTypeParameterBySuperClass(topic);
    return subscribeBroadcast(topic.getTopic(), message -> consumer.accept(
        new MQMessageRecord<>(message.getTopic(), (T) TypeUtils.convert(clazz, message.getBody()))
    ));
}
```

- [ ] **Step 4: Add exception-safe broadcast dispatch helper**

```java
/**
 * 安全执行广播消费函数。
 */
private void safeConsumeBroadcast(String topic, Consumer<MQMessage> consumer, MQMessage message) {
    try {
        consumer.accept(message);
    } catch (Throwable throwable) {
        log.error("{}广播主题 {} 消费失败", LOG_PREFIX, topic, throwable);
    }
}
```

- [ ] **Step 5: Rebuild the local implementation**

```text
IDEA-build_project(
  projectPath="C:\\Users\\xiaotao\\code\\saltedfishcloud-backend",
  filesToRebuild=[
    "sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/LocalMQService.java"
  ],
  timeout=120000
)
```

Expected: `LocalMQService` compiles and the generic broadcast overloads resolve without changing call sites.

- [ ] **Step 6: Commit the broadcast implementation**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/LocalMQService.java
git commit -m "feat: add local mq broadcast support"
```

### Task 3: Implement local queue storage, offsets, and queue subscriptions

**Files:**
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/LocalMQService.java`

- [ ] **Step 1: Add queue state, message record model, and queue subscriber metadata**

```java
private final ConcurrentHashMap<String, QueueState> queueStates = new ConcurrentHashMap<>();
private final ConcurrentHashMap<Long, QueueSubscription> queueSubscriptions = new ConcurrentHashMap<>();
private final ExecutorService queueExecutor = Executors.newCachedThreadPool();

/**
 * 本地队列消息记录。
 */
private record LocalQueueMessage(String id, String topic, Object body) {}

/**
 * 队列状态。
 */
private static class QueueState {
    private final List<LocalQueueMessage> messages = new ArrayList<>();
    private final Map<String, Integer> groupOffsets = new ConcurrentHashMap<>();
    private final Object monitor = new Object();
}
```

- [ ] **Step 2: Implement queue create, destroy, and push**

```java
@Override
public void createQueue(String topic) {
    queueStates.computeIfAbsent(topic, key -> new QueueState());
}

@Override
public void destroyQueue(String topic) {
    QueueState queueState = queueStates.remove(topic);
    if (queueState == null) {
        return;
    }
    queueSubscriptions.entrySet().removeIf(entry -> {
        boolean match = topic.equals(entry.getValue().topic());
        if (match) {
            entry.getValue().cancel();
            subscriptionTypes.remove(entry.getKey());
        }
        return match;
    });
    synchronized (queueState.monitor) {
        queueState.monitor.notifyAll();
    }
}

@Override
public void push(String topic, Object message) {
    QueueState queueState = queueStates.computeIfAbsent(topic, key -> new QueueState());
    synchronized (queueState.monitor) {
        queueState.messages.add(new LocalQueueMessage(String.valueOf(queueState.messages.size() + 1), topic, message));
        queueState.monitor.notifyAll();
    }
}
```

- [ ] **Step 3: Implement offset resolution and queue subscription startup**

```java
private int resolveStartOffset(QueueState queueState, MQOffsetStrategy offsetStrategy, @Nullable String offsetPoint) {
    if (offsetStrategy == MQOffsetStrategy.AT_HEAD) {
        return 0;
    }
    if (offsetStrategy == MQOffsetStrategy.AT_TAIL) {
        return queueState.messages.size();
    }
    for (int i = 0; i < queueState.messages.size(); i++) {
        if (queueState.messages.get(i).id().equals(offsetPoint)) {
            return i + 1;
        }
    }
    throw new IllegalArgumentException("无效的 offsetPoint: " + offsetPoint);
}

@Override
public long subscribeMessageQueue(String topic, String group, MQOffsetStrategy offsetStrategy, @Nullable String offsetPoint, Consumer<MQMessage> consumer) {
    QueueState queueState = queueStates.computeIfAbsent(topic, key -> new QueueState());
    long subscriberId = subscriberIdGenerator.getAndIncrement();
    int startOffset = resolveStartOffset(queueState, offsetStrategy, offsetPoint);
    queueState.groupOffsets.put(group, startOffset);
    QueueSubscription subscription = new QueueSubscription(subscriberId, topic, group, consumer, queueState);
    queueSubscriptions.put(subscriberId, subscription);
    subscriptionTypes.put(subscriberId, SubscriptionType.QUEUE);
    queueExecutor.submit(subscription::run);
    return subscriberId;
}
```

- [ ] **Step 4: Implement queue consumer loop, typed queue adapter, and unsubscribe**

```java
private final class QueueSubscription {
    private final long subscriberId;
    private final String topic;
    private final String group;
    private final Consumer<MQMessage> consumer;
    private final QueueState queueState;
    private volatile boolean cancelled;

    private QueueSubscription(long subscriberId, String topic, String group, Consumer<MQMessage> consumer, QueueState queueState) {
        this.subscriberId = subscriberId;
        this.topic = topic;
        this.group = group;
        this.consumer = consumer;
        this.queueState = queueState;
    }

    private void run() {
        while (!cancelled) {
            LocalQueueMessage message = nextMessage();
            if (message == null) {
                waitForMore();
                continue;
            }
            try {
                consumer.accept(MQMessage.builder().topic(message.topic()).body(message.body()).build());
            } catch (Throwable throwable) {
                log.error("{}队列主题 {} 组 {} 消费失败", LOG_PREFIX, topic, group, throwable);
            }
        }
    }

    private LocalQueueMessage nextMessage() {
        synchronized (queueState.monitor) {
            Integer offset = queueState.groupOffsets.getOrDefault(group, 0);
            if (offset >= queueState.messages.size()) {
                return null;
            }
            LocalQueueMessage message = queueState.messages.get(offset);
            queueState.groupOffsets.put(group, offset + 1);
            return message;
        }
    }

    private void waitForMore() {
        synchronized (queueState.monitor) {
            if (cancelled || !queueStates.containsKey(topic)) {
                return;
            }
            try {
                queueState.monitor.wait(1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void cancel() {
        cancelled = true;
        synchronized (queueState.monitor) {
            queueState.monitor.notifyAll();
        }
    }
}

@Override
@SuppressWarnings("unchecked")
public <T> long subscribeMessageQueue(MQTopic<T> topic, String group, MQOffsetStrategy offsetStrategy, @Nullable String offsetPoint, Consumer<MQMessageRecord<T>> consumer) {
    Class<?> clazz = ClassUtils.getTypeParameterBySuperClass(topic);
    return subscribeMessageQueue(topic.getTopic(), group, offsetStrategy, offsetPoint, message -> consumer.accept(
        new MQMessageRecord<>(message.getTopic(), (T) TypeUtils.convert(clazz, message.getBody()))
    ));
}

@Override
public void unsubscribeMessageQueue(Long id) {
    QueueSubscription subscription = queueSubscriptions.remove(id);
    if (subscription != null) {
        subscription.cancel();
    }
    subscriptionTypes.remove(id);
}
```

- [ ] **Step 5: Rebuild the queue implementation**

```text
IDEA-build_project(
  projectPath="C:\\Users\\xiaotao\\code\\saltedfishcloud-backend",
  filesToRebuild=[
    "sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/LocalMQService.java"
  ],
  timeout=120000
)
```

Expected: queue overloads compile, offset strategy branches are exhaustive, and there are no missing imports or unchecked API mismatches beyond the intentional typed adapters.

- [ ] **Step 6: Commit the queue implementation**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/LocalMQService.java
git commit -m "feat: add local mq queue support"
```

### Task 4: Final cleanup, smoke validation, and documentation sync

**Files:**
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/MQAutoConfiguration.java`
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/LocalMQService.java`
- Modify: `sfc-core/src/main/config/application.yml`
- Modify: `sfc-core/src/main/config/application-develop.yml`
- Modify: `sfc-core/src/main/config/application-product.yml`

- [ ] **Step 1: Add provider startup logging and tighten JavaDoc**

```java
@Bean
@ConditionalOnProperty(prefix = "sys.service", name = "mq-provider", havingValue = "local")
public MQService localMqService() {
    log.info("[消息队列]启用本地 MQ Provider: local");
    return new LocalMQService();
}
```

- [ ] **Step 2: Make queue shutdown explicit so queue worker threads do not leak**

```java
@PreDestroy
public void shutdown() {
    queueSubscriptions.values().forEach(QueueSubscription::cancel);
    queueExecutor.shutdownNow();
}
```

- [ ] **Step 3: Rebuild the full project to catch cross-module wiring regressions**

```text
IDEA-build_project(
  projectPath="C:\\Users\\xiaotao\\code\\saltedfishcloud-backend",
  rebuild=true,
  timeout=240000
)
```

Expected: project rebuild completes without `MQService` bean conflicts or constructor injection failures.

- [ ] **Step 4: Smoke-check both provider settings by reviewing the packaged config**

```text
Review these files after the rebuild:
- sfc-core/src/main/config/application.yml
- sfc-core/src/main/config/application-develop.yml
- sfc-core/src/main/config/application-product.yml
Confirm the comment says local is single-JVM only and the default stays redis.
```

- [ ] **Step 5: Commit the final polish**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/MQAutoConfiguration.java sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/LocalMQService.java sfc-core/src/main/config/application.yml sfc-core/src/main/config/application-develop.yml sfc-core/src/main/config/application-product.yml
git commit -m "chore: finalize mq provider switching"
```

## Spec coverage check

- Provider configuration (`sys.service.mq-provider`): covered in Task 1.
- Conditional bean registration and unique `MQService` bean: covered in Task 1 and Task 4.
- New local in-memory provider: covered in Task 2 and Task 3.
- Broadcast behavior: covered in Task 2.
- Queue behavior with `AT_HEAD` / `AT_TAIL` / `AT_CUSTOM`: covered in Task 3.
- Explicit scope boundary and config documentation: covered in Task 1 and Task 4.
- Validation that the change does not silently broaden into other Redis replacements: preserved by the constraints section and full rebuild in Task 4.
