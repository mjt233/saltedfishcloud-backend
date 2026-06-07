# ISO 解析引擎选择功能设计

## 背景

当前 PXE Boot 插件使用 `java-iso-tools` 库解析 ISO 文件。该库对某些 ISO 格式（如 UDF）兼容性不佳，导致部分 ISO 文件无法正确读取。`sevenzipjbinding` 库基于 7-Zip 原生绑定，格式兼容性更优。项目 pom.xml 中已引入 `sevenzipjbinding` 依赖但未使用。

## 目标

- 提供基于 `sevenzipjbinding` 的 `IsoFileSystem` 实现，作为 `java-iso-tools` 的替代方案
- 管理员可通过后台配置页面选择使用哪种 ISO 解析引擎
- 配置变更后重启生效

## 架构设计

### 现有抽象层（无需修改）

代码已有清晰的分层抽象：

```
IsoHandler (接口, Spring Resource)
  └─ JavaIsoHandler (实现, Resource → File 转换)
       └─ IsoFileSystem (接口, File 级操作)
            ├─ JavaIsoToolsIso9660FileSystem (现有实现)
            └─ SevenZipJBindingIsoFileSystem (新增实现)
```

### 改动范围

#### 1. 新增枚举 `IsoEngineType`

路径：`com.sfc.pxeboot.model.enums.IsoEngineType`

```java
public enum IsoEngineType {
    JAVA_ISO_TOOLS,
    SEVENZIPJBINDING
}
```

#### 2. `PxeBootProperty` 新增配置项

- 新增 `iso` 配置分组
- 新增 `isoEngine` 属性，类型为 `String`，默认值 `java-iso-tools`

```java
@ConfigPropertiesGroup(id = "iso", name = "ISO 配置")

@ConfigProperty(value = "iso-engine", title = "ISO 解析引擎",
    describe = "选择 ISO 文件解析引擎，sevenzipjbinding 兼容性更好",
    defaultValue = "java-iso-tools", group = "iso",
    inputType = "select",
    options = "java-iso-tools=java-iso-tools,sevenzipjbinding=sevenzipjbinding")
private String isoEngine = "java-iso-tools";
```

#### 3. 新增 `SevenZipJBindingIsoFileSystem`

路径：`com.sfc.pxeboot.server.iso.SevenZipJBindingIsoFileSystem`

实现 `IsoFileSystem` 接口的两个抽象方法：

- `traverse(Predicate<TraversalEntry>)` — 使用 `SevenZip.openInArchive()` 遍历 ISO 条目
- `getResource(String)` — 返回延迟加载的 `Resource`，按需从 ISO 中提取文件

关键实现细节：
- 使用 `IInArchive` 接口访问 ISO 内容
- 通过 `SevenZip.getArchiveFileNames()` 获取文件列表
- 通过 `SevenZip.extractItems()` 按索引提取文件
- `LazySevenZipResource` 内部类实现延迟加载，`getInputStream()` 时才打开 ISO

#### 4. 修改 `JavaIsoHandler`

- 注入 `PxeBootProperty`
- `createFileSystem()` 方法根据 `isoEngine` 配置选择实现：
  - `java-iso-tools` → `new JavaIsoToolsIso9660FileSystem(isoFile)`
  - `sevenzipjbinding` → `new SevenZipJBindingIsoFileSystem(isoFile)`

#### 5. `PxeBootAutoConfiguration` 无需修改

`JavaIsoHandler` 已通过 `@Import` 注册，注入 `PxeBootProperty` 由 Spring 自动完成。

### 不变的部分

- `IsoHandler` 接口 — 不变
- `IsoFileSystem` 接口 — 不变
- `IsoFileEntry` — 不变
- `IsoResourceExtractorService` — 不变（依赖 `IsoHandler` 接口）
- `PxeBootController` — 不变（依赖 `IsoHandler` 接口）
- `java-iso-tools` 依赖 — 保留，两种实现共存

### 配置生效方式

配置通过 `ConfigService.bindPropertyEntity()` 绑定，`JavaIsoHandler` 每次操作时读取当前配置值。修改配置后需重启服务生效。
