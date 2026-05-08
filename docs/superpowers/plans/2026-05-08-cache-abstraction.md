# 统一缓存接口抽象 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 封装 CacheService + DistributedLock + LockFactory 接口，全量迁移所有 RedisTemplate 直接调用，统一 Key 命名。

**Architecture:** 在 sfc-api 定义接口（CacheService、DistributedLock、LockFactory），在 sfc-core 提供 Redis 实现（RedisCacheServiceImpl 包装 RedisTemplate、RedisLockFactory 包装 RedissonClient）。各业务类通过构造器注入接口而非具体实现。

**Tech Stack:** Spring Boot 3.5.8, Spring Data Redis, Redisson 3.37.0

---

## 文件结构

### 新建文件

| 文件 | 模块 | 职责 |
|------|------|------|
| `sfc-api/.../cache/CacheService.java` | sfc-api | 缓存接口 |
| `sfc-api/.../cache/DistributedLock.java` | sfc-api | 分布式锁接口 |
| `sfc-api/.../cache/LockFactory.java` | sfc-api | 锁工厂接口 |
| `sfc-api/.../cache/CacheKeyPrefixes.java` | sfc-api | Key 前缀常量 |
| `sfc-core/.../cache/RedisCacheServiceImpl.java` | sfc-core | CacheService 的 Redis 实现 |
| `sfc-core/.../cache/RedisDistributedLock.java` | sfc-core | DistributedLock 的 Redisson 实现 |
| `sfc-core/.../cache/RedisLockFactory.java` | sfc-core | LockFactory 的 Redisson 实现 |

### 修改文件

| 文件 | 变更内容 |
|------|---------|
| `sfc-core/.../config/RedisConfig.java` | 新增 CacheService、LockFactory Bean |
| `sfc-api/.../helper/RedisKeyGenerator.java` | 统一 key 前缀为 `sfc:` |
| `sfc-api/.../utils/LockUtils.java` | 从依赖 RedissonClient 改为依赖 LockFactory |
| `sfc-core/.../dao/redis/TokenServiceImpl.java` | RedisTemplate → CacheService |
| `sfc-core/.../service/user/UserServiceImp.java` | RedisTemplate → CacheService |
| `sfc-core/.../service/ThirdPartyAppTokenServiceImpl.java` | RedisTemplate → CacheService |
| `sfc-core/.../service/third/ThirdPartyPlatformManagerImpl.java` | RedisTemplate → CacheService |
| `sfc-core/.../controller/OAuthController.java` | RedisTemplate → CacheService |
| `sfc-core/.../service/wrap/WrapServiceImpl.java` | RedisTemplate → CacheService |
| `sfc-ext/.../service/QuickShareService.java` | RedisTemplate → CacheService |
| `sfc-ext/.../service/VideoService.java` | RedisTemplate → CacheService |
| `sfc-core/.../service/ClusterServiceImpl.java` | RedisTemplate → CacheService |
| `sfc-core/.../breakpoint/manager/impl/DefaultTaskManager.java` | RedisTemplate → CacheService |
| `sfc-task/.../prog/ProgressDetectorImpl.java` | RedisTemplate → CacheService |
| `sfc-task/.../schedule/AsyncTaskScheduleChecker.java` | RedisTemplate + RedisDao → CacheService |
| `sfc-core/.../service/thumbnail/ThumbnailServiceImpl.java` | RedissonClient → LockFactory |
| `sfc-core/.../service/file/impl/filesystem/DefaultFileSystem.java` | RedissonClient → LockFactory |
| `sfc-task/.../config/AsyncTaskAutoConfiguration.java` | 移除备用 redisTemplate Bean |

### 删除文件

| 文件 | 原因 |
|------|------|
| `sfc-api/.../dao/redis/RedisDao.java` | 功能合并到 CacheService |
| `sfc-core/.../dao/redis/RedisDaoImpl.java` | 功能合并到 RedisCacheServiceImpl |
| `sfc-core/.../src/test/.../RedisDaoTest.java` | 测试目标已删除 |
| `sfc-task/.../receiver/RedisTaskReceiver.java` | 已废弃 |

---

## Task 1: 定义 CacheService 接口

**Files:**
- Create: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/cache/CacheService.java`

- [ ] **Step 1: 创建 CacheService 接口**

```java
package com.xiaotao.saltedfishcloud.cache;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface CacheService {
    // --- Value 操作 ---
    <T> T get(String key);
    void set(String key, Object value);
    void set(String key, Object value, long ttl, TimeUnit unit);
    boolean setIfAbsent(String key, Object value, long ttl, TimeUnit unit);
    Object getAndSet(String key, Object value);
    Object getAndSet(String key, Object value, long ttl, TimeUnit unit);
    boolean delete(String key);
    long delete(Collection<String> keys);

    // --- Set 操作 ---
    long sAdd(String key, Object... values);
    boolean sIsMember(String key, Object value);
    long sRemove(String key, Object... values);
    <T> Set<T> sMembers(String key);

    // --- List 操作 ---
    <T> List<T> range(String key, long start, long end);

    // --- TTL 管理 ---
    boolean expire(String key, long ttl, TimeUnit unit);
    long getExpire(String key);
    boolean hasKey(String key);

    // --- 原子操作 ---
    Long decrementAndGet(String key, int step, int min);

    // --- Key 扫描 ---
    Set<String> scanKeys(String pattern);
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-api -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-api/src/main/java/com/xiaotao/saltedfishcloud/cache/CacheService.java
git commit -m "feat: 定义 CacheService 缓存接口"
```

---

## Task 2: 定义 DistributedLock 和 LockFactory 接口

**Files:**
- Create: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/cache/DistributedLock.java`
- Create: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/cache/LockFactory.java`

- [ ] **Step 1: 创建 DistributedLock 接口**

```java
package com.xiaotao.saltedfishcloud.cache;

import java.util.concurrent.TimeUnit;

public interface DistributedLock {
    boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;
    void lock();
    void unlock();
    boolean isLocked();
}
```

- [ ] **Step 2: 创建 LockFactory 接口**

```java
package com.xiaotao.saltedfishcloud.cache;

public interface LockFactory {
    DistributedLock getLock(String key);
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn compile -pl sfc-api -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add sfc-api/src/main/java/com/xiaotao/saltedfishcloud/cache/
git commit -m "feat: 定义 DistributedLock 和 LockFactory 接口"
```

---

## Task 3: 定义 CacheKeyPrefixes 常量类

**Files:**
- Create: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/cache/CacheKeyPrefixes.java`

- [ ] **Step 1: 创建 CacheKeyPrefixes**

```java
package com.xiaotao.saltedfishcloud.cache;

public final class CacheKeyPrefixes {
    public static final String TOKEN = "sfc:token:";
    public static final String MAIL_VALIDATE = "sfc:user:mailValidate:";
    public static final String REG_MAIL = "sfc:user:regMail:";
    public static final String WRAP = "sfc:wrap:";
    public static final String BREAKPOINT_META = "sfc:breakpoint:";
    public static final String BREAKPOINT_FINISH = "sfc:breakpoint:finish:";
    public static final String TASK_PROGRESS = "sfc:task:progress:";
    public static final String TASK_HOLD = "sfc:task:hold:";
    public static final String CLUSTER = "sfc:cluster:";
    public static final String OAUTH_AUTH_CODE = "sfc:oauth:authCode:";
    public static final String OAUTH_DISABLED_TICKET = "sfc:oauth:disabledTicket:";
    public static final String OAUTH_CALLBACK = "sfc:oauth:callback:";
    public static final String THIRD_ACTION = "sfc:third:action:";
    public static final String QUICK_SHARE = "sfc:share:";
    public static final String WATCH_PROGRESS = "sfc:video:progress:";

    private CacheKeyPrefixes() {}
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-api -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-api/src/main/java/com/xiaotao/saltedfishcloud/cache/CacheKeyPrefixes.java
git commit -m "feat: 定义 CacheKeyPrefixes 统一 key 前缀常量"
```

---

## Task 4: 实现 RedisCacheServiceImpl

**Files:**
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/cache/RedisCacheServiceImpl.java`

- [ ] **Step 1: 创建实现类**

从 `RedisDaoImpl` 迁移 `scanKeys` 和 `decrementAndGet` 的实现逻辑，其余方法委托给 `RedisTemplate`。

```java
package com.xiaotao.saltedfishcloud.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisCacheServiceImpl implements CacheService {
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    @Override
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, ttl, unit);
    }

    @Override
    public boolean setIfAbsent(String key, Object value, long ttl, TimeUnit unit) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, ttl, unit);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public Object getAndSet(String key, Object value) {
        return redisTemplate.opsForValue().getAndSet(key, value);
    }

    @Override
    public Object getAndSet(String key, Object value, long ttl, TimeUnit unit) {
        return redisTemplate.opsForValue().getAndSet(key, value, ttl, unit);
    }

    @Override
    public boolean delete(String key) {
        Boolean result = redisTemplate.delete(key);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public long delete(Collection<String> keys) {
        Long result = redisTemplate.delete(keys);
        return result == null ? 0 : result;
    }

    @Override
    public long sAdd(String key, Object... values) {
        Long result = redisTemplate.opsForSet().add(key, values);
        return result == null ? 0 : result;
    }

    @Override
    public boolean sIsMember(String key, Object value) {
        Boolean result = redisTemplate.opsForSet().isMember(key, value);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public long sRemove(String key, Object... values) {
        Long result = redisTemplate.opsForSet().remove(key, values);
        return result == null ? 0 : result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Set<T> sMembers(String key) {
        return (Set<T>) redisTemplate.opsForSet().members(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> range(String key, long start, long end) {
        return (List<T>) redisTemplate.opsForList().range(key, start, end);
    }

    @Override
    public boolean expire(String key, long ttl, TimeUnit unit) {
        Boolean result = redisTemplate.expire(key, ttl, unit);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public long getExpire(String key) {
        Long result = redisTemplate.getExpire(key);
        return result == null ? -2 : result;
    }

    @Override
    public boolean hasKey(String key) {
        Boolean result = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public Long decrementAndGet(String key, int step, int min) {
        RedisScript<Long> script = RedisScript.of(
            new ClassPathResource("/lua/decrementAndGet.lua"), Long.class);
        return redisTemplate.execute(script, Collections.singletonList(key), step, min);
    }

    @Override
    public Set<String> scanKeys(String pattern) {
        ScanOptions opts = ScanOptions.scanOptions().match(pattern).count(1000).build();
        Set<String> res = new HashSet<>();
        return redisTemplate.execute((RedisCallback<Set<String>>) e -> {
            e.scan(opts).forEachRemaining(r -> res.add(new String(r)));
            return res;
        });
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/cache/RedisCacheServiceImpl.java
git commit -m "feat: 实现 RedisCacheServiceImpl"
```

---

## Task 5: 实现 RedisDistributedLock 和 RedisLockFactory

**Files:**
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/cache/RedisDistributedLock.java`
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/cache/RedisLockFactory.java`

- [ ] **Step 1: 创建 RedisDistributedLock**

```java
package com.xiaotao.saltedfishcloud.cache;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class RedisDistributedLock implements DistributedLock {
    private final RLock rLock;

    @Override
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        return rLock.tryLock(waitTime, leaseTime, unit);
    }

    @Override
    public void lock() {
        rLock.lock();
    }

    @Override
    public void unlock() {
        rLock.unlock();
    }

    @Override
    public boolean isLocked() {
        return rLock.isLocked();
    }
}
```

- [ ] **Step 2: 创建 RedisLockFactory**

```java
package com.xiaotao.saltedfishcloud.cache;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisLockFactory implements LockFactory {
    private final RedissonClient redissonClient;

    @Override
    public DistributedLock getLock(String key) {
        return new RedisDistributedLock(redissonClient.getLock(key));
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn compile -pl sfc-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/cache/RedisDistributedLock.java
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/cache/RedisLockFactory.java
git commit -m "feat: 实现 RedisDistributedLock 和 RedisLockFactory"
```

---

## Task 6: 在 RedisConfig 中注册新 Bean

**Files:**
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/RedisConfig.java`

- [ ] **Step 1: 添加 CacheService 和 LockFactory Bean**

在 `RedisConfig` 类末尾（`redisMessageListenerContainer` 方法之后）添加：

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

添加必要的 import：
```java
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.cache.LockFactory;
import com.xiaotao.saltedfishcloud.cache.RedisCacheServiceImpl;
import com.xiaotao.saltedfishcloud.cache.RedisLockFactory;
import org.redisson.api.RedissonClient;
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/RedisConfig.java
git commit -m "feat: 在 RedisConfig 中注册 CacheService 和 LockFactory Bean"
```

---

## Task 7: 更新 RedisKeyGenerator 统一 key 前缀

**Files:**
- Modify: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/helper/RedisKeyGenerator.java`

- [ ] **Step 1: 重写 RedisKeyGenerator 使用 CacheKeyPrefixes**

将整个文件替换为：

```java
package com.xiaotao.saltedfishcloud.helper;

import com.xiaotao.saltedfishcloud.cache.CacheKeyPrefixes;
import com.xiaotao.saltedfishcloud.service.mail.MailValidateType;

public class RedisKeyGenerator {
    /**
     * 生成打包下载的RedisKey
     * @param wid   打包码
     */
    public static String getWrapKey(String wid) {
        return CacheKeyPrefixes.WRAP + wid;
    }

    /**
     * 获取邮件注册码key
     * @param email 邮箱
     */
    public static String getRegCodeKey(String email) {
        return CacheKeyPrefixes.REG_MAIL + email;
    }

    /**
     * 获取用户邮箱验证key
     * @param uid       用户ID
     * @param email     待验证邮箱地址
     * @param type      验证类型
     * @return          Redis key，Redis值为验证码
     */
    public static String getUserEmailValidKey(long uid, String email, MailValidateType type) {
        return CacheKeyPrefixes.MAIL_VALIDATE + uid + ":" + type + ":" + email;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-api -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-api/src/main/java/com/xiaotao/saltedfishcloud/helper/RedisKeyGenerator.java
git commit -m "refactor: RedisKeyGenerator 使用 CacheKeyPrefixes 统一 key 前缀"
```

---

## Task 8: 迁移 TokenServiceImpl

**Files:**
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/dao/redis/TokenServiceImpl.java`

- [ ] **Step 1: 替换 RedisTemplate 为 CacheService**

关键变更点：
- 字段 `RedisTemplate<String, String> redisTemplate` → `CacheService cacheService`
- `redisTemplate.opsForValue().set(key, token, 2, TimeUnit.DAYS)` → `cacheService.set(key, token, 2, TimeUnit.DAYS)`
- `redisTemplate.opsForValue().get(key)` → `cacheService.get(key)`
- `redisTemplate.delete(key)` → `cacheService.delete(key)`
- `redisDao.scanKeys(prefix)` → `cacheService.scanKeys(prefix)`
- 删除 `RedisDao redisDao` 字段，其功能由 `CacheService` 承接
- Key 前缀 `xyy::token::` → `CacheKeyPrefixes.TOKEN`

具体修改：

1. 将类头部的字段声明从：
```java
private final RedisTemplate<String, String> redisTemplate;
private final RedisDao redisDao;
```
改为：
```java
private final CacheService cacheService;
```

2. 添加 import：
```java
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.cache.CacheKeyPrefixes;
```

3. 删除旧 import：
```java
import org.springframework.data.redis.core.RedisTemplate;
import com.xiaotao.saltedfishcloud.dao.redis.RedisDao;
```

4. 更新 `TokenService` 接口中的 `getTokenKey` 静态方法，将 key 前缀改为 `CacheKeyPrefixes.TOKEN`。

5. 将所有 `redisTemplate.opsForValue().set/get/delete` 调用替换为 `cacheService.set/get/delete`。

6. 将 `redisDao.scanKeys()` 调用替换为 `cacheService.scanKeys()`。

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/dao/redis/TokenServiceImpl.java
git add sfc-api/src/main/java/com/xiaotao/saltedfishcloud/dao/redis/TokenService.java
git commit -m "refactor: TokenServiceImpl 迁移到 CacheService"
```

---

## Task 9: 迁移 UserServiceImp

**Files:**
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/user/UserServiceImp.java`

- [ ] **Step 1: 替换 RedisTemplate 为 CacheService**

关键变更点：
- 字段 `RedisTemplate<String, Object> redisTemplate` → `CacheService cacheService`
- 所有 `redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(15))` → `cacheService.set(key, value, 15, TimeUnit.MINUTES)`
- 所有 `redisTemplate.opsForValue().get(key)` → `cacheService.<String>get(key)`
- 所有 `redisTemplate.delete(key)` → `cacheService.delete(key)`
- Key 已通过 `RedisKeyGenerator` 生成（Task 7 已更新前缀），无需额外修改

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/user/UserServiceImp.java
git commit -m "refactor: UserServiceImp 迁移到 CacheService"
```

---

## Task 10: 迁移 ThirdPartyAppTokenServiceImpl

**Files:**
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/ThirdPartyAppTokenServiceImpl.java`

- [ ] **Step 1: 替换 RedisTemplate 为 CacheService**

关键变更点：
- 字段 `RedisTemplate<String, Object> redisTemplate` → `CacheService cacheService`
- `getAuthorizationCodeCacheKey` 方法中 key 前缀 `"oauthApp::authCode::"` → `CacheKeyPrefixes.OAUTH_AUTH_CODE`
- `"oauthApp::disabledTicket::"` → `CacheKeyPrefixes.OAUTH_DISABLED_TICKET`
- `opsForValue().set(key, value, Duration.ofMinutes(15))` → `cacheService.set(key, value, 15, TimeUnit.MINUTES)`
- `opsForValue().get(key)` → `cacheService.get(key)`
- `redisTemplate.delete(key)` → `cacheService.delete(key)`

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/ThirdPartyAppTokenServiceImpl.java
git commit -m "refactor: ThirdPartyAppTokenServiceImpl 迁移到 CacheService"
```

---

## Task 11: 迁移 ThirdPartyPlatformManagerImpl

**Files:**
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/third/ThirdPartyPlatformManagerImpl.java`

- [ ] **Step 1: 替换 RedisTemplate 为 CacheService**

关键变更点：
- 字段 `RedisTemplate<String, Object> redisTemplate` → `CacheService cacheService`
- 常量 `ACTION_RECORD_KEY = "third_action::"` → `ACTION_RECORD_KEY = CacheKeyPrefixes.THIRD_ACTION`
- `opsForValue().set(key, value, Duration.ofMinutes(10))` → `cacheService.set(key, value, 10, TimeUnit.MINUTES)`
- `opsForValue().get(key)` → `cacheService.get(key)`
- `redisTemplate.delete(key)` → `cacheService.delete(key)`

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/third/ThirdPartyPlatformManagerImpl.java
git commit -m "refactor: ThirdPartyPlatformManagerImpl 迁移到 CacheService"
```

---

## Task 12: 迁移 OAuthController

**Files:**
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/controller/OAuthController.java`

- [ ] **Step 1: 替换 RedisTemplate 为 CacheService**

关键变更点：
- 字段 `RedisTemplate<String, Object> redisTemplate` → `CacheService cacheService`
- key 前缀 `"oauth::cb::"` → `CacheKeyPrefixes.OAUTH_CALLBACK`
- `opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(20))` → `cacheService.setIfAbsent(key, "1", 20, TimeUnit.SECONDS)`

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/controller/OAuthController.java
git commit -m "refactor: OAuthController 迁移到 CacheService"
```

---

## Task 13: 迁移 WrapServiceImpl

**Files:**
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/wrap/WrapServiceImpl.java`

- [ ] **Step 1: 替换 RedisTemplate 为 CacheService**

关键变更点：
- 字段 `RedisTemplate<String, Object> redisTemplate` → `CacheService cacheService`
- `opsForValue().set(key, wrapInfo, Duration.ofMinutes(30))` → `cacheService.set(key, wrapInfo, 30, TimeUnit.MINUTES)`
- `opsForValue().get(key)` → `cacheService.get(key)`
- Key 已通过 `RedisKeyGenerator.getWrapKey()` 生成（Task 7 已更新前缀）

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/wrap/WrapServiceImpl.java
git commit -m "refactor: WrapServiceImpl 迁移到 CacheService"
```

---

## Task 14: 迁移扩展模块（QuickShareService + VideoService）

**Files:**
- Modify: `sfc-ext/sfc-ext-quick-share/src/main/java/com/sfc/quickshare/service/QuickShareService.java`
- Modify: `sfc-ext/sfc-ext-video-enhance/src/main/java/com/saltedfishcloud/ext/ve/service/VideoService.java`

- [ ] **Step 1: 迁移 QuickShareService**

关键变更点：
- 字段 `RedisTemplate<String, Object> redisTemplate` → `CacheService cacheService`
- key 前缀 `"quick_share::"` → `CacheKeyPrefixes.QUICK_SHARE`
- `opsForValue().setIfAbsent(key, id, duration)` → `cacheService.setIfAbsent(key, id, property.getEffectiveDuration(), TimeUnit.MINUTES)`
- `opsForValue().get(key)` → `cacheService.get(key)`

- [ ] **Step 2: 迁移 VideoService**

关键变更点：
- 字段 `RedisTemplate<String, Object> redisTemplate` → `CacheService cacheService`
- key 前缀 `"watch_progress::"` → `CacheKeyPrefixes.WATCH_PROGRESS`
- `opsForValue().set(key, time, Duration.ofDays(7))` → `cacheService.set(key, time, 7, TimeUnit.DAYS)`
- `opsForValue().get(key)` → `cacheService.get(key)`

- [ ] **Step 3: 编译验证**

Run: `mvn compile -pl sfc-ext/sfc-ext-quick-share,sfc-ext/sfc-ext-video-enhance -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add sfc-ext/sfc-ext-quick-share/src/main/java/com/sfc/quickshare/service/QuickShareService.java
git add sfc-ext/sfc-ext-video-enhance/src/main/java/com/saltedfishcloud/ext/ve/service/VideoService.java
git commit -m "refactor: QuickShareService 和 VideoService 迁移到 CacheService"
```

---

## Task 15: 迁移 ClusterServiceImpl

**Files:**
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/ClusterServiceImpl.java`

- [ ] **Step 1: 替换 RedisTemplate 和 RedisDao 为 CacheService**

关键变更点：
- 字段 `RedisTemplate<String, Object> redisTemplate` 和 `RedisDao redisDao` → `CacheService cacheService`
- key 前缀 `"cluster::"` → `CacheKeyPrefixes.CLUSTER`
- `registerSelf()` 中的两步操作（`setIfAbsent` + `expire`）合并为原子操作：
  ```java
  // 旧代码:
  redisTemplate.opsForValue().setIfAbsent(key, selfNode, Duration.ofSeconds(10));
  redisTemplate.expire(key, Duration.ofSeconds(10));
  
  // 新代码:
  cacheService.setIfAbsent(key, selfNode, 10, TimeUnit.SECONDS);
  cacheService.expire(key, 10, TimeUnit.SECONDS);
  ```
- `fetchNodes()` 中的 `redisDao.scanKeys("cluster::*")` → `cacheService.scanKeys(CacheKeyPrefixes.CLUSTER + "*")`
- `redisTemplate.opsForValue().get(key)` → `cacheService.get(key)`

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/ClusterServiceImpl.java
git commit -m "refactor: ClusterServiceImpl 迁移到 CacheService"
```

---

## Task 16: 迁移 DefaultTaskManager

**Files:**
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/breakpoint/manager/impl/DefaultTaskManager.java`

- [ ] **Step 1: 替换 RedisTemplate 为 CacheService**

关键变更点：
- 字段 `RedisTemplate<String, Object> redisTemplate` → `CacheService cacheService`
- key 前缀:
  - `"xyy::breakpoint::"` → `CacheKeyPrefixes.BREAKPOINT_META`
  - `"xyy::breakpoint::finish::"` → `CacheKeyPrefixes.BREAKPOINT_FINISH`
- `opsForValue().set(key, metadata, Duration.ofDays(7))` → `cacheService.set(key, metadata, 7, TimeUnit.DAYS)`
- `opsForValue().get(key)` → `cacheService.get(key)`
- `opsForSet().add(key, partIndex)` → `cacheService.sAdd(key, partIndex)`
- `opsForSet().members(key)` → `cacheService.sMembers(key)`
- `redisTemplate.delete(key)` → `cacheService.delete(key)`

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/breakpoint/manager/impl/DefaultTaskManager.java
git commit -m "refactor: DefaultTaskManager 迁移到 CacheService"
```

---

## Task 17: 迁移 ProgressDetectorImpl

**Files:**
- Modify: `sfc-task/sfc-task-core/src/main/java/com/sfc/task/prog/ProgressDetectorImpl.java`

- [ ] **Step 1: 替换 RedisTemplate 为 CacheService**

关键变更点：
- 字段 `RedisTemplate<String, Object> redisTemplate` → `CacheService cacheService`
- key 前缀 `"xyy::progress::"` → `CacheKeyPrefixes.TASK_PROGRESS`
- `redisTemplate.opsForValue().set(key, record, Duration.ofSeconds(10))` → `cacheService.set(key, record, 10, TimeUnit.SECONDS)`
- `redisTemplate.opsForValue().get(key)` → `cacheService.get(key)`
- `redisTemplate.delete(id)` → 注意：原代码此处传入的是原始 id 而非 key，迁移时修正为 `cacheService.delete(getRecordKey(id))`

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-task/sfc-task-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-task/sfc-task-core/src/main/java/com/sfc/task/prog/ProgressDetectorImpl.java
git commit -m "refactor: ProgressDetectorImpl 迁移到 CacheService"
```

---

## Task 18: 迁移 AsyncTaskScheduleChecker

**Files:**
- Modify: `sfc-task/sfc-task-core/src/main/java/com/sfc/task/schedule/AsyncTaskScheduleChecker.java`

- [ ] **Step 1: 替换 RedisTemplate 和 RedisDao 为 CacheService**

关键变更点：
- 字段 `RedisTemplate<String, Object> redisTemplate` 和 `RedisDao redisDao` → `CacheService cacheService`
- key 前缀 `"async_task_hold_"` → `CacheKeyPrefixes.TASK_HOLD`
- `redisTemplate.delete(key)` → `cacheService.delete(key)`
- `redisTemplate.opsForValue().hasKey(key)` → `cacheService.hasKey(key)`
- `redisTemplate.opsForValue().get(key)` → `cacheService.get(key)`
- `redisTemplate.expire(key, Duration.ofMinutes(2))` → `cacheService.expire(key, 2, TimeUnit.MINUTES)`
- `redisTemplate.opsForValue().setIfAbsent(key, nodeId, duration)` → `cacheService.setIfAbsent(key, nodeId, duration.toMillis(), TimeUnit.MILLISECONDS)`
- `redisDao.scanKeys(HOLD_KEY_PREFIX)` → `cacheService.scanKeys(CacheKeyPrefixes.TASK_HOLD + "*")`

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-task/sfc-task-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-task/sfc-task-core/src/main/java/com/sfc/task/schedule/AsyncTaskScheduleChecker.java
git commit -m "refactor: AsyncTaskScheduleChecker 迁移到 CacheService"
```

---

## Task 19: 迁移 LockUtils

**Files:**
- Modify: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/utils/LockUtils.java`

- [ ] **Step 1: 替换 RedissonClient 为 LockFactory**

将整个文件替换为：

```java
package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.cache.DistributedLock;
import com.xiaotao.saltedfishcloud.cache.LockFactory;
import com.xiaotao.saltedfishcloud.function.IOExceptionRunnable;
import com.xiaotao.saltedfishcloud.function.IOExceptionSupplier;
import lombok.experimental.UtilityClass;

import java.io.IOException;

@UtilityClass
public class LockUtils {
    public static void execute(String lockKey, IOExceptionRunnable task) {
        execute(lockKey, () -> {
            task.run();
            return null;
        });
    }

    public static <T> T execute(String lockKey, IOExceptionSupplier<T> task) {
        LockFactory lockFactory = SpringContextUtils.getContext().getBean(LockFactory.class);
        DistributedLock lock = lockFactory.getLock(lockKey);
        try {
            lock.lock();
            return task.get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-api -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-api/src/main/java/com/xiaotao/saltedfishcloud/utils/LockUtils.java
git commit -m "refactor: LockUtils 从 RedissonClient 迁移到 LockFactory"
```

---

## Task 20: 迁移 ThumbnailServiceImpl

**Files:**
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/thumbnail/ThumbnailServiceImpl.java`

- [ ] **Step 1: 替换 RedissonClient 为 LockFactory**

关键变更点：
- 字段 `RedissonClient redisson` → `LockFactory lockFactory`
- 添加 import: `import com.xiaotao.saltedfishcloud.cache.DistributedLock;` 和 `import com.xiaotao.saltedfishcloud.cache.LockFactory;`
- 删除 import: `import org.redisson.api.RLock;` 和 `import org.redisson.api.RedissonClient;`
- `RLock lock = redisson.getLock(fileIdentify)` → `DistributedLock lock = lockFactory.getLock(fileIdentify)`
- `lock.lock()` / `lock.unlock()` 保持不变（DistributedLock 接口有相同方法）

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/thumbnail/ThumbnailServiceImpl.java
git commit -m "refactor: ThumbnailServiceImpl 从 RedissonClient 迁移到 LockFactory"
```

---

## Task 21: 迁移 DefaultFileSystem

**Files:**
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/file/impl/filesystem/DefaultFileSystem.java`

- [ ] **Step 1: 替换 RedissonClient 为 LockFactory**

关键变更点（6 处使用）：
- 字段 `@Autowired private RedissonClient redisson` → `@Autowired private LockFactory lockFactory`
- 添加 import: `import com.xiaotao.saltedfishcloud.cache.DistributedLock;` 和 `import com.xiaotao.saltedfishcloud.cache.LockFactory;`
- 删除 import: `import org.redisson.api.RLock;` 和 `import org.redisson.api.RedissonClient;`
- 所有 `redisson.getLock(key)` → `lockFactory.getLock(key)`
- 所有 `RLock` 类型声明 → `DistributedLock`
- `lock.lock()` / `lock.unlock()` 保持不变
- `saveFileByStream()` 和 `batchSaveFiles()` 中的 `LockUtils.execute()` 调用无需修改（Task 19 已更新 LockUtils）

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/file/impl/filesystem/DefaultFileSystem.java
git commit -m "refactor: DefaultFileSystem 从 RedissonClient 迁移到 LockFactory"
```

---

## Task 22: 删除 RedisDao 和已废弃代码

**Files:**
- Delete: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/dao/redis/RedisDao.java`
- Delete: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/dao/redis/RedisDaoImpl.java`
- Delete: `sfc-core/src/test/java/com/xiaotao/saltedfishcloud/dao/redis/RedisDaoTest.java`
- Delete: `sfc-task/sfc-task-core/src/main/java/com/sfc/task/receiver/RedisTaskReceiver.java`

- [ ] **Step 1: 确认无残留引用**

Run: `grep -r "RedisDao" --include="*.java" sfc-core/src sfc-api/src sfc-task/ sfc-ext/`
Expected: 无结果（所有引用已在之前的迁移任务中移除）

- [ ] **Step 2: 确认 RedisTaskReceiver 无残留引用**

Run: `grep -r "RedisTaskReceiver" --include="*.java" sfc-task/`
Expected: 无结果

- [ ] **Step 3: 删除文件**

```bash
rm sfc-api/src/main/java/com/xiaotao/saltedfishcloud/dao/redis/RedisDao.java
rm sfc-core/src/main/java/com/xiaotao/saltedfishcloud/dao/redis/RedisDaoImpl.java
rm sfc-core/src/test/java/com/xiaotao/saltedfishcloud/dao/redis/RedisDaoTest.java
rm sfc-task/sfc-task-core/src/main/java/com/sfc/task/receiver/RedisTaskReceiver.java
```

- [ ] **Step 4: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "chore: 删除 RedisDao、RedisDaoImpl、RedisDaoTest 和已废弃的 RedisTaskReceiver"
```

---

## Task 23: 清理 AsyncTaskAutoConfiguration

**Files:**
- Modify: `sfc-task/sfc-task-core/src/main/java/com/sfc/task/config/AsyncTaskAutoConfiguration.java`

- [ ] **Step 1: 移除备用 redisTemplate Bean**

删除以下代码块（第36-47行）：
```java
@Autowired
private RedisConnectionFactory redisConnectionFactory;

@Bean
@ConditionalOnMissingBean(RedisTemplate.class)
public RedisTemplate<String, Object> redisTemplate() {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new GenericJackson2JsonRedisSerializer(MapperHolder.mapper));
    template.setConnectionFactory(redisConnectionFactory);
    return template;
}
```

清理不再需要的 import：
```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl sfc-task/sfc-task-core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add sfc-task/sfc-task-core/src/main/java/com/sfc/task/config/AsyncTaskAutoConfiguration.java
git commit -m "chore: 移除 AsyncTaskAutoConfiguration 中的备用 redisTemplate Bean"
```

---

## Task 24: 全量编译验证

- [ ] **Step 1: 全量编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: 检查残留的 RedisTemplate 直接使用**

Run: `grep -r "RedisTemplate" --include="*.java" sfc-core/src sfc-api/src sfc-task/ sfc-ext/ | grep -v "RedisConfig\|CacheConfig\|RedisMQService\|RedisRPCManager\|AsyncTaskAutoConfiguration\|target/"`
Expected: 无结果（除不迁移的类外）

- [ ] **Step 3: 检查残留的 RedissonClient 直接使用**

Run: `grep -r "RedissonClient" --include="*.java" sfc-core/src sfc-api/src sfc-task/ | grep -v "RedisLockFactory\|target/"`
Expected: 无结果

- [ ] **Step 4: 检查残留的旧 key 前缀**

Run: `grep -rn "\"xyy:" --include="*.java" sfc-core/src sfc-api/src sfc-task/ sfc-ext/`
Expected: 无结果

- [ ] **Step 5: 提交（如果有遗漏修复）**

如果有遗漏修复，提交修复：
```bash
git add -A
git commit -m "fix: 修复迁移遗漏"
```

---

## 验证清单

完成所有任务后，运行以下验证：

1. `mvn compile -q` — 全量编译通过
2. `grep -r "import org.redisson.api" --include="*.java" sfc-core/src sfc-api/src sfc-task/` — 仅 RedisCacheServiceImpl 等实现类中有 Redisson import
3. `grep -r "RedisTemplate" --include="*.java" sfc-core/src sfc-api/src sfc-task/ sfc-ext/` — 仅 RedisConfig、CacheConfig、RedisMQService、RedisRPCManager 中有
4. 启动应用验证功能正常
