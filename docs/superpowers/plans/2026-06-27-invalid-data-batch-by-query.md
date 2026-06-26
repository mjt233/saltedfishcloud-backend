# 失效数据按条件批量操作 - 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为失效数据模块新增 3 个按条件（`InvalidDataQuery`）批量操作接口：丢弃、发布、取消发布，支持 Groovy 脚本过滤。

**Architecture:** 在 `InvalidDataService` 新增 `findIdsByQuery` 私有方法统一查询 ID 列表（支持 DB 条件 + Groovy 过滤），三个公开方法复用现有 `discard`/`publish`/`unpublish`。Controller 新增 3 个 `/byQuery` 端点。

**Tech Stack:** Java 25, Spring Boot, JPA, Groovy

---

## 文件结构总览

```
sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/
├── service/
│   └── InvalidDataService.java       (修改：新增 4 个方法)
└── controller/
    └── InvalidDataController.java    (修改：新增 3 个端点)
```

不新增文件，不修改 Repo 层。

---

## Task 1: InvalidDataService 新增 findIdsByQuery 私有方法

**Files:**
- Modify: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/InvalidDataService.java`

- [ ] **Step 1: 在 `InvalidDataService` 类中添加 `findIdsByQuery` 方法**

在 `applyQueryFilter` 方法之前（约第 575 行前）添加：

```java
/**
 * 按条件查询匹配的记录ID列表。
 * 支持 DB 级别筛选条件和可选的 Groovy 脚本过滤。
 * <p>当 query 包含 filterScript 时，先通过 DB 条件流式查询，
 * 再经 Groovy 脚本过滤返回匹配的 ID 列表；否则直接流式提取 ID。</p>
 *
 * @param query         查询条件
 * @param forcedStatus  强制注入的状态（覆盖 query.status）
 * @return 匹配的记录ID列表
 */
private List<Long> findIdsByQuery(InvalidDataQuery query, InvalidDataStatus forcedStatus) {
    query.setStatus(List.of(forcedStatus.name()));
    JpaLambdaQueryWrapper<InvalidDataRecord> wrapper = JpaLambdaQueryWrapper.get(InvalidDataRecord.class);
    applyQueryFilter(wrapper, query);

    if (query.getFilterScript() != null && !query.getFilterScript().isBlank()) {
        try (Stream<InvalidDataRecord> stream = repo.streamAll(wrapper.build())) {
            return groovyRecordFilter.filter(stream, query.getFilterScript());
        }
    }

    try (Stream<InvalidDataRecord> stream = repo.streamAll(wrapper.build())) {
        return stream.map(InvalidDataRecord::getId).toList();
    }
}
```

- [ ] **Step 2: 编译验证**

使用 MCP `build_project` 对项目编译。
Expected: 编译成功，无错误。

---

## Task 2: InvalidDataService 新增三个公开方法

**Files:**
- Modify: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/InvalidDataService.java`

- [ ] **Step 1: 添加 `discardByQuery` 方法**

在 `discardAll()` 方法之后（约第 422 行后）添加：

```java
/**
 * 按条件批量丢弃。
 * <p>根据 InvalidDataQuery 查询条件（支持 Groovy 脚本过滤）筛选出 PENDING 状态的记录，
 * 执行批量丢弃操作。状态强制为 PENDING，客户端传入的 status 字段会被忽略。</p>
 *
 * @param query 查询条件
 * @return 批量操作结果
 */
public BatchResult discardByQuery(InvalidDataQuery query) {
    List<Long> ids = findIdsByQuery(query, InvalidDataStatus.PENDING);
    return discard(ids);
}
```

- [ ] **Step 2: 添加 `publishByQuery` 方法**

在 `discardByQuery` 方法之后添加：

```java
/**
 * 按条件批量发布为可认领。
 * <p>根据 InvalidDataQuery 查询条件（支持 Groovy 脚本过滤）筛选出 PENDING 状态的记录，
 * 执行批量发布操作。状态强制为 PENDING，客户端传入的 status 字段会被忽略。</p>
 *
 * @param query 查询条件
 * @return 批量操作结果
 */
public BatchResult publishByQuery(InvalidDataQuery query) {
    List<Long> ids = findIdsByQuery(query, InvalidDataStatus.PENDING);
    return publish(ids);
}
```

- [ ] **Step 3: 添加 `unpublishByQuery` 方法**

在 `publishByQuery` 方法之后添加：

```java
/**
 * 按条件批量取消发布。
 * <p>根据 InvalidDataQuery 查询条件（支持 Groovy 脚本过滤）筛选出 PUBLISHED 状态的记录，
 * 执行批量取消发布操作。状态强制为 PUBLISHED，客户端传入的 status 字段会被忽略。</p>
 *
 * @param query 查询条件
 * @return 批量操作结果
 */
public BatchResult unpublishByQuery(InvalidDataQuery query) {
    List<Long> ids = findIdsByQuery(query, InvalidDataStatus.PUBLISHED);
    return unpublish(ids);
}
```

- [ ] **Step 4: 编译验证**

使用 MCP `build_project` 对项目编译。
Expected: 编译成功，无错误。

- [ ] **Step 5: 使用 `get_file_problems` 检查修改的文件**

使用 MCP `get_file_problems` 检查 `InvalidDataService.java`。
Expected: 无新增警告。如有警告则修复。

---

## Task 3: InvalidDataController 新增三个端点

**Files:**
- Modify: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/controller/InvalidDataController.java`

- [ ] **Step 1: 添加 `discardByQuery` 端点**

在 `discardAll()` 方法之后（约第 220 行后）添加：

```java
/**
 * 按条件批量丢弃
 */
@PostMapping("discard/byQuery")
@RolesAllowed(SysRole.ADMIN)
public JsonResult<BatchResult> discardByQuery(@RequestBody InvalidDataQuery query) {
    return JsonResultImpl.getInstance(invalidDataService.discardByQuery(query));
}
```

- [ ] **Step 2: 添加 `publishByQuery` 端点**

在 `discardByQuery` 端点之后添加：

```java
/**
 * 按条件批量发布为可认领
 */
@PostMapping("publish/byQuery")
@RolesAllowed(SysRole.ADMIN)
public JsonResult<BatchResult> publishByQuery(@RequestBody InvalidDataQuery query) {
    return JsonResultImpl.getInstance(invalidDataService.publishByQuery(query));
}
```

- [ ] **Step 3: 添加 `unpublishByQuery` 端点**

在 `publishByQuery` 端点之后添加：

```java
/**
 * 按条件批量取消发布
 */
@PostMapping("unpublish/byQuery")
@RolesAllowed(SysRole.ADMIN)
public JsonResult<BatchResult> unpublishByQuery(@RequestBody InvalidDataQuery query) {
    return JsonResultImpl.getInstance(invalidDataService.unpublishByQuery(query));
}
```

- [ ] **Step 4: 编译验证**

使用 MCP `build_project` 对项目编译。
Expected: 编译成功，无错误。

- [ ] **Step 5: 使用 `get_file_problems` 检查修改的文件**

使用 MCP `get_file_problems` 检查 `InvalidDataController.java`。
Expected: 无新增警告。如有警告则修复。

---

## Task 4: 全量编译验证与最终检查

**Files:**
- 无新增修改

- [ ] **Step 1: 使用 MCP `build_project` 全量编译验证**

对整个项目执行编译。
Expected: 编译成功，无错误。

- [ ] **Step 2: 检查清单**

逐项确认：
- [ ] `@UID` 注解：本功能无 uid 参数，无需添加
- [ ] 公共资源写入管理员校验：端点已标注 `@RolesAllowed(SysRole.ADMIN)`
- [ ] JavaDoc：所有新增方法和字段已添加 JavaDoc
- [ ] 状态强制注入：service 层已自动覆盖 query.status
- [ ] 分批批量操作：复用现有 `discard()`/`publish()`/`unpublish()` 的分批策略（200条/批）
- [ ] 未修改现有接口：纯新增，不影响已有功能
