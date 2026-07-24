---
name: generate-service
description: 为实体类(Entity)创建一个对应的Service接口和实现类，需要继承CrudService和CrudServiceImpl 
---

Service 接口和实现类的规则如下所示。该模式的基类代码位于 `sfc-api` 模块，因此所有模块均可使用此模式。

## 1. Service 接口

- 接口命名为 `{实体类名}Service`，如实体类为 `ProxyInfo`，接口则为 `ProxyInfoService`
- 接口需要继承 `com.xiaotao.saltedfishcloud.service.CrudService<T>`，其中 `T` 为实体类
- 接口中只需声明 **自定义业务方法**，基础 CRUD 方法已由 `CrudService` 提供

```java
package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;

public interface ProxyInfoService extends CrudService<ProxyInfo> {
    boolean testProxy(Long proxyId, int timeout, boolean useCache);
}
```

## 2. Service 实现类

- 实现类命名为 `{实体类名}ServiceImpl`
- 需要继承 `com.xiaotao.saltedfishcloud.service.CrudServiceImpl<T, R>`
  - `T` — 实体类（需继承 `AuditModel`）
  - `R` — JPA Repository（需继承 `BaseRepo<T>`）
- 需要实现对应的 Service 接口
- 添加 `@Service` 注解（推荐添加，部分模块也可省略）
- 继承的 `repository` 字段可直接用于调用自定义 Repository 方法

```java
package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.dao.jpa.ProxyInfoRepo;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import org.springframework.stereotype.Service;

@Service
public class ProxyInfoServiceImpl extends CrudServiceImpl<ProxyInfo, ProxyInfoRepo> implements ProxyInfoService {

    @Override
    public boolean testProxy(Long proxyId, int timeout, boolean useCache) {
        ProxyInfo proxyInfo = repository.getReferenceById(proxyId);
        // 自定义业务逻辑...
        return true;
    }
}
```

## 3. 包位置规则

根据实体类所在的 Maven 模块确定 Service 接口和实现类的存放位置。

### 3.1 模块为 `sfc-api`

接口和实现类都放 `sfc-api` 模块下，不区分接口和实现类的包路径。实现类会被 `sfc-core` 的组件扫描自动发现。

- 接口: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/service/{EntityName}Service.java`
- 实现: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/service/{EntityName}ServiceImpl.java`

### 3.2 模块为 `sfc-core`

- 接口: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/service/`（接口放在 sfc-api 中供其他模块引用）
- 实现: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/{EntityName}ServiceImpl.java`（实现放在 sfc-core 中）

### 3.3 扩展模块（`sfc-ext/*`）

接口和实现类都放在扩展模块自身目录下。接口放在 `service` 包，实现类放在 `service.impl` 子包。

- 接口: `sfc-ext/{module}/src/main/java/{basePkg}/service/{EntityName}Service.java`
- 实现: `sfc-ext/{module}/src/main/java/{basePkg}/service/impl/{EntityName}ServiceImpl.java`

示例：实体类 `com.sfc.webshell.model.po.ShellExecuteRecord`
- 接口: `com.sfc.webshell.service.ShellExecuteRecordService`
- 实现: `com.sfc.webshell.service.impl.ShellExecuteRecordServiceImpl`

## 4. 权限处理

Service 实现类在以下场景需要注意权限校验：

### 重写 `saveWithOwnerPermissions` 或 `deleteWithOwnerPermissions`

当需要额外限制时重写这些方法，基类已提供基础的持有者权限验证。

### 自定义方法中的权限校验

使用 `UIDValidator.validateWithException(uid, ...)` 进行权限校验：
- `true` — 禁止非管理员操作公共数据（uid=0）
- `false` — 允许所有用户访问公共数据，但禁止访问非本人数据

### `findById` 重写时添加权限

```java
@Override
public T findById(Long id) {
    T entity = super.findById(id);
    if (entity != null) {
        UIDValidator.validateWithException(entity.getUid(), true);
    }
    return entity;
}
```

## 5. 注意事项

- 使用 `@Slf4j` 注解可以方便地进行日志记录
- 使用 `@Transactional(rollbackFor = Exception.class)` 确保写操作的事务性
- 使用 `@Cacheable` / `@CacheEvict` 注解实现缓存，重写 `save` 等方法时务必同步处理缓存
- 对于批量插入的场景，优先使用基类提供的 `batchInsert` 而非 `batchSave`（性能更优）
- 实现类中可直接访问 `repository` 字段调用 Repository 的自定义查询方法

## 6. 常见错误

### 泛型参数错误

```java
// ❌ 错误：第二个泛型参数遗漏
public class ProxyInfoServiceImpl extends CrudServiceImpl<ProxyInfo> implements ProxyInfoService

// ✅ 正确：需要同时指定实体类和 Repository 类型
public class ProxyInfoServiceImpl extends CrudServiceImpl<ProxyInfo, ProxyInfoRepo> implements ProxyInfoService
```

### 实体类未继承 AuditModel

```java
// ❌ 错误：CrudServiceImpl 要求 T extends AuditModel
public class PlainEntity { ... }

// ✅ 正确：实体类需要继承 AuditModel
public class PlainEntity extends AuditModel { ... }
```

### 接口/实现包位置不对导致 sfc-core 无法扫描

sfc-api 模块中的 Service 实现类会被 sfc-core 的组件扫描自动发现。如果 Service 接口放在 sfc-api 但实现类放在了 sfc-core 以外的模块，需要通过 `@ComponentScan` 或其他方式确保 Spring 能扫描到。
