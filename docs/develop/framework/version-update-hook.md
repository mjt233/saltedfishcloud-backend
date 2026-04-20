# 版本更新数据迁移钩子机制

## 1. 简介

咸鱼云提供了一套基于注解的版本更新钩子机制，用于在 **系统核心模块** 或 **插件模块** 的版本发生变化时，自动执行初始化、数据迁移和失败回滚逻辑。

当前系统默认启用了 JPA 的自动建表/改表能力，在 `sfc-core/src/main/config/application.yml` 中可看到：`spring.jpa.hibernate.ddl-auto: update`。

这意味着：

- 启动过程中，JPA / Hibernate 会先对 **JPA 管理的数据表结构** 做自动同步。
- 版本更新钩子是在这之后执行的，属于 **表结构更新后的后置迁移阶段**。
- 因此版本更新钩子更适合做 **历史数据迁移、默认值修正、兼容处理、补充型 SQL**，而不是承担常规 JPA 实体表的主建表职责。

这套机制主要适用于以下场景：

- 新版本发布后，需要修正历史数据、补齐默认值、重建索引。
- 插件首次安装时，需要初始化默认配置、基础数据，或补充非 JPA 管理对象。
- 迁移逻辑较复杂，无法只靠静态 SQL 脚本完成，需要结合 Java 代码处理。
- 某个版本迁移失败后，需要执行补偿或回滚操作。

相关注解位于包：`com.xiaotao.saltedfishcloud.annotations.update`

- `@Updater`
- `@UpdateAction`
- `@RollbackAction`
- `@InitAction`

## 2. 整体执行流程

这套机制由 `com.xiaotao.saltedfishcloud.init.DatabaseUpdater` 在系统启动阶段触发，核心链路如下：

1. Spring Boot 启动，JPA / Hibernate 根据 `ddl-auto: update` 先同步 JPA 管理的数据表结构。
2. Spring 容器完成 Bean 创建。
3. `DefaultVersionUpdateManager` 从容器中收集所有带 `@Updater` 的 Bean。
4. 框架把注解方法转换为 `VersionUpdateHandler`。
5. `DatabaseUpdater` 先执行 **全局作用域** 更新，再执行 **插件作用域** 更新。
6. 如果某个插件还没有版本记录，则执行该插件作用域下的初始化动作。
7. 如果历史版本低于某个 `@UpdateAction` 声明的版本，则执行对应迁移方法。
8. 若更新过程中抛出异常，则执行同版本的 `@RollbackAction`（如果存在），并中断本次更新流程。

> **理解重点**  
> 如果某张表是 JPA 实体表，通常应优先通过实体定义让 JPA 自动完成基础结构同步；版本更新钩子负责处理这之后的数据迁移和补充性结构调整。

## 3. 注解说明

### 3.1 `@Updater`

`@Updater` 用于标记一个“更新器”类。只有满足以下两个条件的类，才会被框架识别：

1. 类上标记了 `@Updater`
2. 该类已经注册为 Spring Bean，例如加上 `@Component`

示例：

```java
@Component
@Updater
public class SystemUpdater {
}
```

#### 作用域

`@Updater` 的 `value` 用于声明更新器作用域：

- 不传值：表示 **系统核心模块的全局作用域**。
- 传入插件名：表示 **某个插件自己的更新作用域**。

例如：

```java
@Component
@Updater("video-enhance")
public class VEUpdater {
}
```

这表示该更新器只会在 `video-enhance` 插件版本变化时参与执行。

> **注意**  
> 插件作用域必须与插件实际 `name` 完全一致，否则该更新器不会被该插件命中。

### 3.2 `@UpdateAction`

`@UpdateAction("版本号")` 用于声明某个版本的升级动作。

```java
@UpdateAction("1.2.0")
public void update120() {
    // 执行 1.2.0 版本迁移
}
```

触发条件：

- 当前作用域已经存在历史版本记录。
- 历史版本 **低于** 注解声明的版本号。

典型用途：

- 为 JPA 新增字段后的历史数据回填默认值。
- 将旧表结构数据迁移到新模型。
- 修正旧版本遗留的配置或状态字段。
- 在方法内部执行补充型 SQL，例如非 JPA 管理对象、额外索引、历史数据修复脚本等。

### 3.3 `@RollbackAction`

`@RollbackAction("版本号")` 用于声明某个升级动作失败后的回滚逻辑。

```java
@RollbackAction("2.7.0")
public void rollback270() {
    // 回滚 2.7.0 迁移
}
```

使用规则：

- 一般与同版本的 `@UpdateAction` 配套使用。
- 当更新流程抛出异常时，框架会调用回滚方法。
- 如果没有声明 `@RollbackAction`，则该版本失败后不会自动执行补偿逻辑。

> **建议**  
> 回滚方法只处理你可以明确恢复的内容，例如删除临时数据、执行回滚 SQL、恢复标记位等。不要在回滚逻辑里继续做高风险的复杂迁移。

### 3.4 `@InitAction`

`@InitAction` 用于声明初始化动作，在作用域 **第一次被系统识别并且没有版本记录** 时执行。

```java
@InitAction
public void init() {
    // 首次加载时执行
}
```

典型场景：

- 插件第一次安装时初始化默认配置。
- 写入插件运行所需的基础数据。
- 为非 JPA 管理对象做一次性初始化。

> **重要说明**  
> 从注解语义上看，`@InitAction` 可以用于“系统/插件首次加载”；但当前 `DatabaseUpdater` 的实际实现里，显式调用初始化处理器的是插件更新流程 `handlePluginUpdate()`。也就是说，当前项目中 `@InitAction` 更适合用于 **插件首次安装初始化**。如果你需要核心系统的首次初始化，不要只依赖全局作用域的 `@InitAction`。

## 4. 方法签名与参数注入规则

### 4.1 无参数方法

```java
@UpdateAction("1.1.1")
public void update111() throws SQLException {
    executeScript("1.1.1");
}
```

### 4.2 接收 `UpdateContext`

```java
@UpdateAction("2.3.3")
public void doUpdate(UpdateContext context) {
    log.info("从{}更新到{}", context.getFrom(), context.getTo());
}
```

`UpdateContext` 类型为：`com.xiaotao.saltedfishcloud.model.UpdateContext`

其中包含：

- `from`：旧版本
- `to`：新版本

### 4.3 参数解析规则

根据 `DefaultVersionUpdateManager.AnnotationUpdaterFactory` 的实现：

- 如果方法没有参数，框架直接调用该方法。
- 如果参数类型是 `UpdateContext`，框架会自动注入上下文。
- 其他类型的参数不会被自动注入，传入值会是 `null`。

因此建议更新方法只使用以下两种签名：

- 无参数
- 单参数 `UpdateContext`

## 5. 框架内部如何组装这些注解

### 5.1 注解转 `VersionUpdateHandler`

`DefaultVersionUpdateManager` 会读取 Spring 容器里所有带 `@Updater` 的 Bean，并扫描其公开方法上的：

- `@UpdateAction`
- `@RollbackAction`
- `@InitAction`

随后把这些方法转换成内部使用的 `VersionUpdateHandler`。

### 5.2 版本分组规则

同一个更新器类中，框架会按版本分组：

- 一个版本最多只能有一个 `@UpdateAction`
- 一个版本最多只能有一个 `@RollbackAction`
- `@InitAction` 会被内部映射为版本 `0.0.0` 的初始化动作

如果同一个更新器类里，为同一版本声明了多个相同类型的动作，启动时会抛出异常。

### 5.3 作用域匹配

框架按作用域筛选可执行的更新器：

- 全局作用域：用于核心系统升级
- 插件作用域：用于单个插件自身升级

插件版本记录使用配置项：`plugin.{pluginName}.version`

例如 `video-enhance` 插件对应：

```text
plugin.video-enhance.version
```

## 6. 系统核心模块迁移示例

系统核心模块通常使用默认全局作用域：

```java
@Component
@Updater
@Slf4j
public class SystemUpdater {

    @UpdateAction("2.6.6")
    public void update266() {
        // 迁移旧数据
    }

    @UpdateAction("2.7.0")
    public void update270() {
        // 在 JPA 完成表结构同步后，继续执行补充型结构调整和数据迁移
    }

    @RollbackAction("2.7.0")
    public void rollback270() {
        // 回滚 2.7.0 版本迁移
    }
}
```

适合放在系统更新器里的逻辑包括：

- 核心表结构升级后的数据修正
- 主系统配置项迁移
- 核心模块兼容性转换逻辑

## 7. 插件迁移示例

下面是一个插件更新器的典型写法，和 `video-enhance` 插件中的 `VEUpdater` 一致：

```java
@Component
@Updater("video-enhance")
@Slf4j
public class VEUpdater {
    @Autowired
    private DataSource dataSource;

    private void executeScript(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("ve-sql/" + name + ".sql", this.getClass().getClassLoader())
            );
        }
    }

    @UpdateAction("1.1.1")
    public void createTaskTable111() throws SQLException {
        executeScript("1.1.1");
    }

    @UpdateAction("1.2.0")
    public void createTaskTable120() throws SQLException {
        executeScript("1.2.0");
    }
}
```

这个示例说明了：

1. 插件更新器要声明 `@Updater("插件名")`
2. 迁移逻辑既可以直接写 Java，也可以在方法内部执行补充型 SQL 脚本
3. 只要插件历史版本低于 `1.1.1` 或 `1.2.0`，对应方法就会参与执行

> **说明**  
> 这里的 SQL 示例更适合用于插件的补充型迁移场景，例如额外对象初始化、特殊 DDL、历史数据修复等。若某张表本身由 JPA 实体管理，通常应优先让 JPA 在启动阶段完成结构同步。

如果你希望插件首次安装时自动初始化，可以补充：

```java
@InitAction
public void init() throws SQLException {
    executeScript("init");
}
```

## 8. 与 SQL 脚本更新的关系

系统内还存在一套基于 SQL 文件的版本更新机制：

- `DatabaseUpdater` 会自动扫描 `classpath:/sql/*.*.*.sql`
- 文件名中的版本号会被解析为更新版本
- 这些 SQL 文件会被注册为全局作用域的更新处理器

但要注意：在当前系统默认配置下，JPA 会先自动同步 JPA 管理的数据表结构，因此这里的 SQL 更新机制更适合作为 **补充型迁移手段**，而不是常规实体表结构升级的主入口。

通常可以这样理解：

- **JPA / Hibernate**：负责 JPA 实体表的基础结构同步
- **版本更新 SQL / 注解钩子**：负责结构同步之后的补充处理、特殊 DDL 和数据迁移

### 8.1 适合交给 JPA 的内容

例如：

- 实体新增普通字段
- 实体对应表的基础建表
- 随实体映射产生的常规列更新

### 8.2 适合放到版本更新 SQL 或钩子里的内容

例如：

- 非 JPA 管理对象的初始化或更新
- JPA 不方便表达的特殊 DDL
- 历史数据修复、补数、数据搬迁
- 依赖 Spring Bean 或业务服务参与的迁移逻辑

例如 `SystemUpdater` 中的 `2.7.0` 升级，就是典型的 **JPA 表结构同步之后，再结合 SQL / Java 继续做数据迁移** 的方案。

## 9. 开发建议

### 9.1 一个版本只做一件清晰的迁移工作

建议每个 `@UpdateAction("x.y.z")` 只对应一组明确、可追踪的迁移动作，不要把多个无关改动混在一起。

### 9.2 迁移逻辑尽量幂等

虽然正常情况下同一版本迁移不会重复执行，但迁移中断、人工修复、版本记录异常等情况仍可能出现。幂等设计可以显著降低风险。

### 9.3 复杂迁移优先拆成“JPA 结构同步、补充型调整、数据搬迁”三步

例如：

1. 先通过实体定义让 JPA 自动完成基础表结构同步
2. 如仍需特殊 DDL，再执行补充型 SQL 或钩子逻辑
3. 最后通过 Java 逻辑扫描旧数据并完成数据迁移

### 9.4 失败时直接抛异常

不要吞异常。迁移失败时应直接抛出异常，让系统中断本次更新流程，并触发已有的回滚逻辑。

### 9.5 写足日志

迁移通常发生在启动阶段，排查成本高。建议记录：

- 开始迁移的版本区间
- 迁移的数据量
- 关键步骤耗时
- 回滚是否成功

### 9.6 谨慎依赖多个版本迁移之间的执行顺序

`VersionUpdateManager` 接口注释把待执行更新器描述为“按更新版本升序排序”，但当前 `DefaultVersionUpdateManager#getNeedUpdateHandlerList` 的实现细节需要以实际代码为准。

因此建议：

- 每个版本迁移尽量保持独立
- 不要强依赖多个 `@UpdateAction` 之间的隐式先后关系
- 如果你的迁移必须严格按顺序执行，开发前先确认当前项目实现

## 10. 编写更新器的检查清单

在提交代码前，建议逐项检查：

- 更新器类是否已经注册为 Spring Bean（如加上 `@Component`）？
- `@Updater` 的作用域是否正确？插件场景下是否与插件 `name` 一致？
- 迁移方法是否只使用了无参或 `UpdateContext`？
- 新增版本迁移是否补充了足够日志？
- 是否需要为高风险迁移补充 `@RollbackAction`？
- 迁移逻辑是否避免吞异常？
- 涉及 SQL 脚本时，脚本路径是否会随模块或插件一起打包？
- SQL / 钩子逻辑是否与 JPA 自动 DDL 的结果重复或冲突？
- 插件首次初始化逻辑是否放在 `@InitAction` 中？

## 11. 推荐实践模板

### 11.1 系统模块模板

```java
@Component
@Updater
@Slf4j
public class MySystemUpdater {

    @UpdateAction("3.0.0")
    public void update300(UpdateContext context) {
        log.info("开始执行系统迁移：{} -> {}", context.getFrom(), context.getTo());
        // do update
    }

    @RollbackAction("3.0.0")
    public void rollback300(UpdateContext context) {
        log.warn("执行系统迁移回滚：{} -> {}", context.getFrom(), context.getTo());
        // do rollback
    }
}
```

### 11.2 插件模块模板

```java
@Component
@Updater("your-plugin-name")
@Slf4j
public class MyPluginUpdater {

    @InitAction
    public void init() {
        // 插件首次安装初始化
    }

    @UpdateAction("1.0.1")
    public void update101(UpdateContext context) {
        log.info("插件更新：{} -> {}", context.getFrom(), context.getTo());
        // do update
    }
}
```

## 12. 总结

`com.xiaotao.saltedfishcloud.annotations.update` 提供的是一套面向 **系统升级** 与 **插件升级** 的数据迁移钩子机制。

使用这套机制时，建议牢记以下原则：

- 更新器类必须是 Spring Bean，并使用 `@Updater` 标记
- 系统升级使用默认全局作用域，插件升级使用插件 `name` 作为作用域
- 普通升级逻辑写在 `@UpdateAction`
- 插件首次初始化逻辑写在 `@InitAction`
- 高风险迁移最好配套 `@RollbackAction`
- 复杂迁移推荐使用“JPA 先同步结构，SQL / Java 再做补充迁移”的组合方式

这样就可以在系统或插件升级时，更安全地完成数据库结构调整、历史数据修复和初始化操作。



