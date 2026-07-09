---
name: debug-plugin-startup
description: Use when the saltedfishcloud application fails to start with NoClassDefFoundError, ClassNotFoundException, or other errors related to sfc-ext-* plugin modules and their dependencies
---

# 排查插件模块启动失败与依赖问题

## 概述

咸鱼云的插件模块（`sfc-ext-*`）有独特的类加载机制：`PluginInitializer` 在 Spring 启动初期将应用的 ClassLoader 替换为 `DefaultPluginClassLoader`（合并类加载器），所有 `MERGE` 类型插件的 Jar 包通过 `addURL()` 添加到该加载器中。依赖冲突或 ClassLoader 隔离问题会导致 `NoClassDefFoundError` 等启动失败。

## 诊断步骤

### 1. 分析 ClassLoader 层次

启动时的 ClassLoader 链：

```
AppClassLoader (originLoader / masterLoader)
  └── DefaultPluginClassLoader (mergeClassLoader)
        ├── sfc-ext-music.jar
        ├── sfc-ext-minio-store.jar
        ├── ... (所有 MERGE 类型插件)
        └── ext/lib/{pluginName}/*.jar (解包的外部依赖)
```

关键代码路径（`PluginInitializer.initialize()`，按时间序）：
```
pluginManager.init()
context.setClassLoader(mergeClassLoader)          ← 替换应用 ClassLoader
Thread.currentThread().setContextClassLoader(…)   ← 替换 TCCL
startRegister()                                   ← 多线程注册所有插件
MapperHolder.setTypeFactoryClassLoader(…)         ← 注册完成后才替换 Jackson TypeFactory
```

**任何在此之后执行的代码，类查找都经过 `mergeClassLoader` → `AppClassLoader` 链。**

### 2. 定位 NoClassDefFoundError 的根本原因

NoClassDefFoundError 的含义：类在**编译期**存在，但**运行时**在当前位置的类加载器中不可见。

#### 检查要点：

| 症状 | 可能原因 |
|------|---------|
| 插件引用了 `sfc-core` 内部的类 | `sfc-ext-*` 不应 Maven 依赖 `sfc-core`（只有 `sfc-ext-ai` 例外且声明 `scope=provided`），运行时需通过 sfc-api 接口交互 |
| 第三方的类被 `loadExtraDependencies` 拒绝加载 | `ClassUtils.validUrl()` 检测到类冲突（同路径类已存在于加载器中）→ 日志会打印冲突类列表 |
| 第三方的类版本冲突 | `checkLibDependence()` 检测到同名不同版本的依赖 → 日志会打印冲突信息 |
| `PluginClassLoaderFactory.createPurePluginClassLoader` 读取不到插件信息 | 父加载器为 `null`，仅能找到 `API_ANNOTATIONS_MAP` 中的注解类 |

#### 使用 `mvn dependency:tree` 排查传递依赖

在具体插件模块下执行：
```
mvn dependency:tree -pl sfc-ext-{plugin-name}
```

重点关注意外引入的传递依赖，尤其是那些本应由 `sfc-core` 提供的框架类（Spring、Jackson、OkHttp 等）。

### 3. 检查 Maven 依赖关系

**模块间依赖原则：**

```
sfc-ext-*
  ├── sfc-api          (scope=provided, 父 POM 统一声明)
  ├── Spring Framework  (scope=provided, 由宿主提供)
  ├── Spring Boot       (scope=provided, 由宿主提供)
  ├── Jackson           (scope=provided, 由宿主提供)
  └── 自定义唯一依赖    (scope=compile, 插件特有)
```

**禁止的依赖：** `sfc-core`（除非极其特殊且声明 `scope=provided`，如 `sfc-ext-ai`）。

**常见问题：**
- 插件声明了对 `sfc-core` 的 `compile` 依赖 → 构建后 `plugin-lib/` 目录中会出现 `sfc-core` 的 jar，运行时导致 `loadExtraDependencies` 阶段 `ClassUtils.validUrl()` 检测到冲突
- 插件声明了 `spring-boot-starter-*` 的 `compile` 依赖 → 同上
- 插件没有排除 `sfc-core` 已提供的传递依赖 → 依赖冲突
- **POM 配置陷阱：** `sfc-ext/pom.xml` 中 `maven-dependency-plugin` 的 `<includeScope>` 出现了两次（`compile` 和 `runtime`），Maven 对简单参数取最后一个值，实际生效的是 `runtime`。这意味着仅 `runtime` scope 的依赖会被复制到 `plugin-lib/`。排查时如果发现插件依赖未出现在 `plugin-lib/` 中，需要检查该插件的 `pom.xml` 中的 scope 设置

### 4. 理解插件注册流程

```
PluginInitializer.initialize()
  ├─ pluginManager.init()               → 扫描 META-INF 注册初始依赖
  ├─ context.setClassLoader(mergeLoader)→ 替换 ClassLoader
  ├─ deletePlugin()                     → 删除被标记的插件
  ├─ initBuildInPlugin()                → 注册系统内置插件
  ├─ upgrade()                          → 执行插件升级替换
  ├─ getPluginFromClassPath()           → 发现 classpath 中的插件
  │   ├─ 目录模式 → DirPathClassLoader
  │   ├─ jar:nested: → 提取内嵌 jar 包
  │   └─ jar: → UrlResource + null loader
  ├─ getPluginFromExtraResource()       → 开发模式插件
  └─ startRegister()                    → 多线程注册
       └─ register(resource, loader)
            ├─ 读取 plugin-info.json             → 解析 PluginInfo
            ├─ 过滤 delayLoadLib                 → 排除延迟加载的依赖
            ├─ getPluginDependencies()           → 扫描 plugin-lib/
            ├─ checkLibDependence()              → 版本冲突检测
            ├─ ClassUtils.validUrl()             → 类冲突检测
            ├─ loadExtraDependencies()           → 加载通过校验的依赖
            └─ loader.loadFromUrl(pluginUrl)     → 将插件 Jar 添加到 ClassLoader
```

### 5. 分析 ClassUtils.validUrl 冲突检测

`ClassUtils.validUrl(loader, url)` 打开外部依赖 Jar，枚举所有 `.class` 条目，调用 `loader.getResource(name)` 检查该条目是否已被加载。如果返回非空（即已有同路径类存在），则标记为冲突。

```java
// 冲突检测逻辑
loader.getResource("com/example/Foo.class") != null  → 冲突
```

**解决冲突：** 调整依赖版本、排除重复的传递依赖或移除冲突的第三方库。

## 验证修改

1. **编译验证**：`mvn clean compile -DskipTests` —— 必须全模块编译（插件也属于 Maven 模块）

2. **启动验证**：使用 IntelliJ 运行 `SaltedfishcloudApplication`，观察控制台日志：
   - `[插件初始化]启动时加载的插件清单：[...]` —— 插件是否被正确发现
   - `[插件系统]验证来自{插件名}的依赖，共N个` —— 外部依赖是否被识别
   - 任何 ERROR 级别的日志提及冲突或加载失败

3. **预期正常启动时间**：约 20 秒

4. **关注错误日志**：系统启动失败后会尝试启动紧急模式（`EmergencyApplication`），此时需要在之前的日志中找到真正的错误原因

## 常见错误模式

| 错误 | 根因 | 修复方向 |
|------|------|---------|
| `NoClassDefFoundError: com/xiaotao/saltedfishcloud/service/...` | 插件引用了 sfc-core 内部类 | 通过 sfc-api 接口解耦 |
| `PluginDependenceConflictException` | 同依赖不同版本冲突 | 统一版本或排除多余依赖 |
| `ClassUtils.validUrl` 返回冲突列表 | 插件依赖的 Jar 包含与已加载类同路径的类 | 排除或调整该依赖 |
| `plugin-info.json` 解析失败 | 格式错误或类加载器无权访问 | 检查文件格式和 `createPurePluginClassLoader` |

## 关键文件速查

| 文件 | 作用 |
|------|------|
| `sfc-core/.../init/PluginInitializer.java` | 启动初期替换 ClassLoader 并注册所有插件 |
| `sfc-core/.../ext/DefaultPluginManager.java` | 插件生命周期管理、依赖加载 |
| `sfc-core/.../ext/DefaultPluginClassLoader.java` | 合并类加载器实现 |
| `sfc-core/.../ext/PluginClassLoaderFactory.java` | 创建纯类加载器用于读取插件信息 |
| `sfc-api/.../utils/ClassUtils.java` | `validUrl()` 冲突检测 |
| `sfc-api/.../ext/DirPathClassLoader.java` | 目录模式类加载器（开发模式） |
| `sfc-ext/pom.xml` | 插件父 POM，声明所有 provided 依赖 |
