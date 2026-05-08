# 统一缓存接口抽象设计

## 背景

项目中 `RedisTemplate` 被 15+ 个类直接注入使用，没有统一的缓存抽象层。这导致：

- 对 Redis 的直接依赖过深，无法切换缓存实现
- Key 命名风格混乱（`xyy:`、`xyy::`、`cluster::`、`async_task_hold_` 等）
- `RedisDao` 仅封装了 SCAN 和 Lua 自减两个方法，不构成通用抽象

本次重构目标：定义通用缓存接口 + 分布式锁接口，将所有直接 `RedisTemplate` 调用迁移至新接口，为后续切换缓存实现奠定基础。

## 设计决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 抽象范围 | KV 缓存 + 分布式锁 | Stream/Pub/Sub/Lua 等深度依赖 Redis 语义，不在本次范围内 |
| 锁实现 | 基于现有 Redisson | 项目已引入 Redisson，复用最可靠 |
| 接口位置 | sfc-api 模块 | 各模块只依赖接口，实现放在 sfc-core |
| 迁移策略 | 一步到位全量迁移 | 避免长期维护两套调用方式 |
| 设计模式 | 两接口模式（CacheService + LockFactory） | 职责单一，锁和缓存独立演进 |

## 接口设计

### CacheService

定义在 `sfc-api` 模块，封装通用 KV 和 Set 缓存操作：

```java
public interface CacheService {
    // --- Value 操作 ---
    <T> T get(String key);
    void set(String key, Object value);
    void set(String key, Object value, long ttl, TimeUnit unit);
    boolean setIfAbsent(String key, Object value, long ttl, TimeUnit unit);
    <T> T getAndSet(String key, Object value);
    Boolean delete(String key);
    Long delete(Collection<String> keys);

    // --- Set 操作 ---
    Long sAdd(String key, Object... values);
    Boolean sIsMember(String key, Object value);
    Long sRemove(String key, Object... values);
    <T> Set<T> sMembers(String key);

    // --- TTL 管理 ---
    Boolean expire(String key, long ttl, TimeUnit unit);
    Long getExpire(String key);
    Boolean hasKey(String key);

    // --- 原子操作 ---
    Long decrementAndGet(String key, long delta, long min);

    // --- Key 扫描 ---
    Set<String> scanKeys(String pattern);
}
```

### DistributedLock

```java
public interface DistributedLock {
    boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;
    void unlock();
    boolean isLocked();
}
```

### LockFactory

```java
public interface LockFactory {
    DistributedLock getLock(String key);
}
```

## Redis 实现

### 实现类结构

```
sfc-core/src/main/java/com/xiaotao/saltedfishcloud/cache/
├── RedisCacheServiceImpl.java    // CacheService 实现
├── RedisLockFactory.java         // LockFactory 实现
└── RedisDistributedLock.java     // DistributedLock 实现
```

### RedisCacheServiceImpl

- 注入 `RedisTemplate<String, Object>`
- Value 操作委托 `redisTemplate.opsForValue()`
- Set 操作委托 `redisTemplate.opsForSet()`
- `decrementAndGet` 复用现有 Lua 脚本逻辑
- `scanKeys` 复用现有 SCAN 命令逻辑

### RedisDistributedLock

- 包装 `RedissonClient.getLock(key)` 返回的 `RLock`
- `tryLock`/`unlock`/`isLocked` 直接委托给 RLock

### RedisLockFactory

- 注入 `RedissonClient`
- `getLock(key)` 返回 `RedisDistributedLock` 实例

### Spring 配置

在现有 `RedisConfig` 中新增两个 `@Bean`：

```java
@Bean
public CacheService cacheService(RedisTemplate<String, Object> redisTemplate) {
    return new RedisCacheServiceImpl(redisTemplate);
}

@Bean
public LockFactory lockFactory(RedissonClient redissonClient) {
    return new RedisLockFactory(redissonClient);
}
```

## 移除 RedisDao

- `RedisDao` 接口（sfc-api）和 `RedisDaoImpl`（sfc-core）**直接删除**
- `scanKeys`、`decrementAndGet` 方法完全合并到 `CacheService`
- 原来注入 `RedisDao` 的类改为注入 `CacheService`

## Key 命名统一

统一格式：`{sfc}:{module}:{业务标识}`

| Key | 用途 | 原前缀 |
|-----|------|--------|
| `sfc:token:{userId}` | 用户 Token | `xyy:token::` |
| `sfc:user:mailValidate:{email}` | 邮箱验证码 | `xyy:mailValidate:` |
| `sfc:user:regMail:{email}` | 注册邮件码 | `xyy:regMail:` |
| `sfc:wrap:{id}` | 打包下载 | `xyy:wrap:` |
| `sfc:breakpoint:{uid}` | 断点续传任务 | `xyy:breakpoint::` |
| `sfc:breakpoint:finish:{uid}` | 已完成分片 | `xyy:breakpoint::finish::` |
| `sfc:task:progress:{taskId}` | 任务进度 | `xyy::progress::` |
| `sfc:task:hold:{taskId}` | 异步任务执行权 | `async_task_hold_` |
| `sfc:cluster:{nodeId}` | 集群节点 | `cluster::` |
| `sfc:oauth:authCode:{code}` | OAuth 授权码 | `oauthApp::authCode::` |
| `sfc:oauth:disabledTicket:{ticket}` | OAuth Ticket 黑名单 | `oauthApp::disabledTicket::` |
| `sfc:third:action:{userId}` | 第三方登录行为 | `third_action::` |
| `sfc:share:{code}` | 快速分享 | `quick_share::` |
| `sfc:video:progress:{id}` | 视频播放进度 | `watch_progress::` |

Key 前缀统一放在常量类 `CacheKeyPrefixes` 中。

## 全量迁移

### 组 1：纯 KV 缓存 → CacheService

| 类 | 模块 | 替换方式 |
|---|------|---------|
| `TokenServiceImpl` | sfc-core | `RedisTemplate` → `CacheService` |
| `UserServiceImp` | sfc-core | `RedisTemplate` → `CacheService` |
| `ThirdPartyAppTokenServiceImpl` | sfc-core | `RedisTemplate` → `CacheService` |
| `ThirdPartyPlatformManagerImpl` | sfc-core | `RedisTemplate` → `CacheService` |
| `OAuthController` | sfc-core | `RedisTemplate` → `CacheService` |
| `WrapServiceImpl` | sfc-core | `RedisTemplate` → `CacheService` |
| `QuickShareService` | sfc-ext-quick-share | `RedisTemplate` → `CacheService` |
| `VideoService` | sfc-ext-video-enhance | `RedisTemplate` → `CacheService` |
| `ProgressDetectorImpl` | sfc-task-core | `RedisTemplate` → `CacheService` |
| `ClusterServiceImpl` | sfc-core | `RedisTemplate` → `CacheService` |

### 组 2：KV + Set 操作 → CacheService

| 类 | 模块 | 替换方式 |
|---|------|---------|
| `DefaultTaskManager` | sfc-core | `opsForValue` → CacheService.get/set，`opsForSet` → CacheService.sAdd/sIsMember/sMembers |

### 组 3：分布式锁 → LockFactory

| 类 | 模块 | 替换方式 |
|---|------|---------|
| `AsyncTaskScheduleChecker` | sfc-task-core | `setIfAbsent` 竞争改为 `LockFactory.getLock().tryLock()` |
| `ThumbnailServiceImpl` | sfc-core | `RedissonClient.getLock()` → `LockFactory.getLock()` |
| `DefaultFileSystem` | sfc-core | `RedissonClient.getLock()` → `LockFactory.getLock()` |
| `LockUtils` | sfc-api | `RedissonClient` 工具类 → 改为基于 `LockFactory` |

### 不迁移

| 类 | 原因 |
|---|------|
| `RedisMQService` | 基于 Redis Stream，无法抽象为通用缓存接口 |
| `RedisRPCManager` | 基于 Pub/Sub + List，RPC 模块内部实现 |
| `RedisTaskReceiver`（已废弃） | 建议直接删除 |

## 实施顺序

1. 在 sfc-api 中定义 `CacheService`、`DistributedLock`、`LockFactory` 接口
2. 在 sfc-api 中定义 `CacheKeyPrefixes` 常量类
3. 在 sfc-core 中实现 `RedisCacheServiceImpl`、`RedisDistributedLock`、`RedisLockFactory`
4. 在 `RedisConfig` 中注册新 Bean
5. 删除 `RedisDao` 接口和 `RedisDaoImpl` 实现
6. 按组 1 → 组 2 → 组 3 顺序逐类迁移
7. 删除 `RedisTaskReceiver`（已废弃）
8. 验证：编译通过 + 现有功能正常
