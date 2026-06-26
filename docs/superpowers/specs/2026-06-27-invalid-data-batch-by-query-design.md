# 失效数据按条件批量操作 - 设计规格

## 概述

为 `sfc-ext-data-manager` 插件的失效数据模块新增按条件（`InvalidDataQuery`）批量丢弃、发布、取消发布的接口。现有接口仅支持按 ID 列表操作，新接口允许管理员通过查询条件（含 Groovy 脚本过滤）直接批量操作，无需先查询再传 ID。

## 实现范围

- 按条件批量丢弃（`POST /discard/byQuery`）
- 按条件批量发布（`POST /publish/byQuery`）
- 按条件批量取消发布（`POST /unpublish/byQuery`）

## 设计约束

- 禁止循环操作单条数据库数据，使用分批次批量操作
- Service 层封装实现，返回 `BatchResult` 对象
- 接口仅限管理员操作（`@RolesAllowed(SysRole.ADMIN)`）
- 完整支持 `InvalidDataQuery` 的所有字段，包括 `filterScript`（Groovy 脚本过滤）

## API 设计

### 请求体

直接使用 `InvalidDataQuery` 作为请求体，无需新建 DTO：

```json
{
  "status": ["PENDING"],
  "ownerUid": null,
  "minFileSize": null,
  "maxFileSize": 1048576,
  "fileType": ["image"],
  "filterScript": null
}
```

### 端点

| Method | Path | 说明 | 状态约束 |
|--------|------|------|----------|
| POST | `/api/dataManager/invalidData/discard/byQuery` | 按条件批量丢弃 | 强制 PENDING |
| POST | `/api/dataManager/invalidData/publish/byQuery` | 按条件批量发布 | 强制 PENDING |
| POST | `/api/dataManager/invalidData/unpublish/byQuery` | 按条件批量取消发布 | 强制 PUBLISHED |

所有端点返回 `JsonResult<BatchResult>`。

### 状态约束自动注入

Service 层在执行操作前自动覆盖 `query.status`，确保操作安全性：

- `discardByQuery`：强制 `status = [PENDING]`
- `publishByQuery`：强制 `status = [PENDING]`
- `unpublishByQuery`：强制 `status = [PUBLISHED]`

客户端传入的 `status` 字段会被忽略。

## Service 层设计

### 核心流程

```
InvalidDataQuery
    │
    ├─ 有 filterScript?
    │   ├─ Yes → buildDBQuery() → streamAll() → GroovyRecordFilter.filter() → ID列表
    │   └─ No  → buildDBQuery() → findIdsByQuery() → ID列表
    │
    └─ 执行批量操作（复用现有方法）
        ├─ discard(ids)     → 分类型处理（FILE_RECORD直接更新, PHYSICAL_STORAGE先删文件再更新）
        ├─ publish(ids)     → batchUpdateStatus(PENDING → PUBLISHED)
        └─ unpublish(ids)   → batchUpdateStatus(PUBLISHED → PENDING)
```

### 新增方法

在 `InvalidDataService` 中新增：

```java
/**
 * 按条件查询匹配的记录ID列表。
 * 支持 DB 级别筛选条件和可选的 Groovy 脚本过滤。
 *
 * @param query 查询条件
 * @param forcedStatus 强制注入的状态（覆盖 query.status）
 * @return 匹配的记录ID列表
 */
private List<Long> findIdsByQuery(InvalidDataQuery query, InvalidDataStatus forcedStatus)
```

该方法内部逻辑：

1. 覆盖 `query.setStatus(List.of(forcedStatus.name()))`
2. 构建 `JpaLambdaQueryWrapper` 并调用 `applyQueryFilter()`
3. 如果 `filterScript` 非空：
   - 流式查询 + `GroovyRecordFilter.filter()` → 返回 ID 列表
4. 如果 `filterScript` 为空：
   - 直接通过 repo 查询 ID 列表（不加载完整实体）

### 新增 Repo 方法

在 `InvalidDataRecordRepo` 中新增基于 Specification 的 ID 查询：

```java
/**
 * 按 Specification 查询匹配的记录ID列表
 */
@Query("SELECT t.id FROM InvalidDataRecord t WHERE t IN :spec")
List<Long> findIdsByQuery(Specification<InvalidDataRecord> spec);
```

或使用 `JpaSpecificationExecutor` 已有的能力，通过现有 `findAll(wrapper.build())` 返回的 ID 提取。

考虑到现有代码使用 `JpaLambdaQueryWrapper` 构建条件，最简方案是：复用现有 `repo.findAll(wrapper.build())` 配合只查 ID 的投影，或直接用已有的 `JpaSpecificationExecutor` 接口配合 `findIds` 投影查询。

**最终选择**：由于 `BaseRepo` 已继承 `JpaSpecificationExecutor`，直接使用 `findAll` 返回的 ID 即可。对于无 filterScript 的场景，可新增一个轻量的 `findIdsBySpec` 方法避免加载完整实体。

### 三个公开方法

```java
public BatchResult discardByQuery(InvalidDataQuery query) {
    List<Long> ids = findIdsByQuery(query, InvalidDataStatus.PENDING);
    return discard(ids);
}

public BatchResult publishByQuery(InvalidDataQuery query) {
    List<Long> ids = findIdsByQuery(query, InvalidDataStatus.PENDING);
    return publish(ids);
}

public BatchResult unpublishByQuery(InvalidDataQuery query) {
    List<Long> ids = findIdsByQuery(query, InvalidDataStatus.PUBLISHED);
    return unpublish(ids);
}
```

## Controller 层设计

在 `InvalidDataController` 中新增 3 个端点，均标注 `@RolesAllowed(SysRole.ADMIN)`：

```java
@PostMapping("discard/byQuery")
@RolesAllowed(SysRole.ADMIN)
public JsonResult<BatchResult> discardByQuery(@RequestBody InvalidDataQuery query) {
    return JsonResultImpl.getInstance(invalidDataService.discardByQuery(query));
}

@PostMapping("publish/byQuery")
@RolesAllowed(SysRole.ADMIN)
public JsonResult<BatchResult> publishByQuery(@RequestBody InvalidDataQuery query) {
    return JsonResultImpl.getInstance(invalidDataService.publishByQuery(query));
}

@PostMapping("unpublish/byQuery")
@RolesAllowed(SysRole.ADMIN)
public JsonResult<BatchResult> unpublishByQuery(@RequestBody InvalidDataQuery query) {
    return JsonResultImpl.getInstance(invalidDataService.unpublishByQuery(query));
}
```

## 复用与不复用

### 复用

- `InvalidDataQuery` — 查询参数 DTO
- `applyQueryFilter()` — DB 条件构建
- `GroovyRecordFilter.filter()` — Groovy 脚本过滤
- `discard(List<Long>)` — 批量丢弃逻辑（含按类型分流、物理文件删除）
- `publish(List<Long>)` / `unpublish(List<Long>)` — 批量状态更新
- `BatchResult` — 返回值

### 不修改

- 现有的按 ID 列表操作接口保持不变
- 现有的 `/discard/all` 接口保持不变

## 安全与边界

- 仅管理员可调用（`@RolesAllowed(SysRole.ADMIN)`）
- 状态强制注入，防止客户端绕过状态限制
- Groovy 脚本有超时限制（10s）和最大结果数限制（10000条）
- 批量 UPDATE 分批执行（每批 200 条），避免长事务
- discard 操作不使用 `@Transactional`，物理文件删除失败不影响其他记录

## 涉及文件

| 文件 | 操作 |
|------|------|
| `InvalidDataRecordRepo.java` | 新增 `findIdsBySpec` 方法 |
| `InvalidDataService.java` | 新增 `discardByQuery`, `publishByQuery`, `unpublishByQuery`, `findIdsByQuery` |
| `InvalidDataController.java` | 新增 3 个端点 |
