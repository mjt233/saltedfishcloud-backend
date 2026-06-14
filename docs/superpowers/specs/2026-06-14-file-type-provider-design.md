# 文件类型识别 Provider 扩展设计

## 概述

为 `sfc-ext-data-manager` 模块新增 6 个文件类型识别 Provider，覆盖文档、压缩包、ISO 镜像、纯文本、EXE、MSI 文件类型。使用 Apache Tika 作为主要依赖库，结合 magic bytes 快速预判断策略。

## 背景

现有系统（`FileTypeCheckProvider` 接口 + `FileTypeCheckerImpl`）支持图片、音频、视频 3 种类型，全部依赖 FFMpeg。需要扩展更多文件类型的识别能力。

### 现有系统关键点

- **接口**：`FileTypeCheckProvider` 定义了 `getId()`、`getTypeName()`、`getTypeId()`、`getSupportedFileExtensions()`、`getMetadataDefines()`、`checkFile(File, boolean)` 方法
- **注册**：Spring `@Autowired` 自动注入所有 Provider Bean
- **检测**：两阶段策略 — 扩展名快速匹配 → 全量遍历
- **问题**：无优先级机制，当多个 Provider 匹配同一文件时结果不确定

## 设计决策

### 1. 优先级机制

在 `FileTypeCheckProvider` 接口中新增 `default int getPriority()` 方法，数值越小优先级越高。使用 `default` 方法保持向后兼容，现有 Provider 无需修改。

```java
default int getPriority() {
    return Integer.MAX_VALUE;
}
```

`FileTypeCheckerImpl` 在 `setProviders()` 注入时按 priority 排序，后续匹配逻辑不变。

### 2. 依赖选择

使用 Apache Tika 作为主要依赖：
- `tika-core`：文件类型检测（基于 magic bytes）
- `tika-parsers-standard-package`：元数据提取（内含 POI、PDFBox 等）

附加依赖：
- `commons-compress`：处理 tar/bz2/xz/7z 等压缩格式

### 3. 检测策略

**magic bytes 优先**：先读取文件头部字节进行快速判断，仅在需要元数据时调用 Tika 完整解析。这满足"如果通过依赖库识别需要读取完整文件，尽可能先手动判断文件头"的要求。

### 4. 冲突处理

当多个 Provider 都能识别同一文件时（如 docx 既是 ZIP 又是文档），按 `getPriority()` 数值小的优先。专用类型优先于通用类型。

## Provider 优先级

| Provider | typeId | Priority | 理由 |
|----------|--------|----------|------|
| `DocumentCheckProvider` | `document` | 10 | docx 优先于 archive |
| `MsiCheckProvider` | `installer` | 20 | MSI 优先于 archive（同为 OLE2 格式） |
| `IsoCheckProvider` | `disk-image` | 30 | ISO 优先于通用二进制 |
| `ExeCheckProvider` | `executable` | 40 | EXE 优先于通用二进制 |
| `ArchiveCheckProvider` | `archive` | 50 | 压缩包检测较为通用 |
| `PlainTextCheckProvider` | `text` | 100 | 文本检测最宽松，放最后 |
| Image/Audio/Video（现有） | — | 1000 | 保留原有优先级 |

## 新增 Provider 详情

### DocumentCheckProvider

- **typeId**: `document`
- **priority**: 10
- **扩展名**: `.docx`, `.doc`, `.xlsx`, `.xls`, `.pptx`, `.ppt`, `.pdf`, `.odt`, `.ods`, `.odp`
- **检测策略**:
  1. 读取文件头 8 字节
  2. OLE2 签名（`D0 CF 11 E0 A1 B1 1A E1`）→ 检查是否为 doc/xls/ppt（排除 MSI）
  3. PDF 签名（`25 50 44 46`）→ 确认为 PDF
  4. ZIP 签名（`50 4B 03 04`）→ 检查内部结构：
     - 包含 `[Content_Types].xml` → Office Open XML（docx/xlsx/pptx）
     - 包含 `mimetype` 文件 → ODF（odt/ods/odp）
- **元数据**（通过 Tika Parser 提取）:
  - `title` — 文档标题
  - `author` — 作者
  - `pageCount` — 页数
  - `createdDate` — 创建时间

### ArchiveCheckProvider

- **typeId**: `archive`
- **priority**: 50
- **扩展名**: `.zip`, `.rar`, `.7z`, `.tar`, `.gz`, `.bz2`, `.xz`
- **检测策略**: magic bytes 快速判断
  - `.zip` → `50 4B 03 04`
  - `.rar` → `52 61 72 21 1A 07`
  - `.7z` → `37 7A BC AF 27 1C`
  - `.tar` → `75 73 74 61 72`（偏移 257）
  - `.gz` → `1F 8B`
  - `.bz2` → `42 5A 68`
  - `.xz` → `FD 37 7A 58 5A 00`
- **元数据**:
  - `fileCount` — 文件总数
  - `compressionMethod` — 压缩方式
  - `fileList` — 前 5 个文件名（JSON 数组字符串）

### IsoCheckProvider

- **typeId**: `disk-image`
- **priority**: 30
- **扩展名**: `.iso`
- **检测策略**: 偏移 `0x8001` 处检查 `CD001` 签名
- **元数据**:
  - `volumeLabel` — 卷标
  - `fileSize` — 文件大小
  - `fileList` — 前 5 个文件名（JSON 数组字符串）

### PlainTextCheckProvider

- **typeId**: `text`
- **priority**: 100
- **扩展名**: 60+ 种，全面覆盖文本和代码文件
  - 文本: `.txt`, `.csv`, `.log`, `.md`, `.rst`, `.org`
  - 数据: `.json`, `.xml`, `.yaml`, `.yml`, `.toml`, `.ini`, `.cfg`, `.conf`, `.properties`, `.env`
  - Web: `.html`, `.htm`, `.css`, `.scss`, `.less`, `.svg`
  - 代码: `.java`, `.py`, `.js`, `.ts`, `.jsx`, `.tsx`, `.go`, `.rs`, `.rb`, `.php`, `.c`, `.cpp`, `.h`, `.hpp`, `.cs`, `.swift`, `.kt`, `.scala`, `.lua`, `.r`, `.m`, `.pl`, `.pm`, `.sh`, `.bash`, `.zsh`, `.fish`, `.bat`, `.cmd`, `.ps1`, `.sql`, `.gradle`, `.groovy`, `.cmake`
  - 配置: `.gitignore`, `.dockerignore`, `.editorconfig`, `.eslintrc`, `.prettierrc`
- **检测策略**: 扩展名匹配 → 直接确认为文本
- **元数据**:
  - `encoding` — 文件编码（UTF-8/GBK/ISO-8859-1 等）
  - `lineCount` — 行数
  - `fileSize` — 文件大小

### ExeCheckProvider

- **typeId**: `executable`
- **priority**: 40
- **扩展名**: `.exe`, `.dll`, `.sys`
- **检测策略**:
  1. 读取前 2 字节判断 `MZ`（`4D 5A`）签名
  2. 从偏移 `0x3C` 读取 PE 头偏移，验证 `PE\0\0`（`50 45 00 00`）签名
- **元数据**:
  - `architecture` — `x86` / `x64`
  - `subsystem` — `CONSOLE` / `WINDOWS` / `NATIVE` 等
  - `linkerVersion` — 链接器版本
  - `compileTime` — 编译时间戳
  - `isDll` — 是否为 DLL

### MsiCheckProvider

- **typeId**: `installer`
- **priority**: 20
- **扩展名**: `.msi`
- **检测策略**:
  1. 读取前 8 字节判断 OLE2 签名（`D0 CF 11 E0 A1 B1 1A E1`）
  2. 检查 CLSID 是否为 MSI 的 `{000C1084-0000-0000-C000-000000000046}`
  3. 读取 MSI Property 表提取属性
- **元数据**:
  - `productName` — 产品名称
  - `productVersion` — 产品版本
  - `manufacturer` — 制造商
  - `architecture` — `x86` / `x64` / `ARM64`

## 接口变更

### FileTypeCheckProvider

新增一个 `default` 方法：

```java
/**
 * 获取 Provider 的优先级，数值越小优先级越高。
 * 当多个 Provider 都能识别同一文件时，优先使用高优先级的 Provider。
 */
default int getPriority() {
    return Integer.MAX_VALUE;
}
```

### FileTypeCheckerImpl

1. `setProviders()` 注入后按 `getPriority()` 排序
2. Phase 1（扩展名匹配）和 Phase 2（全量遍历）都使用排序后的列表

## 新增依赖

在 `sfc-ext-data-manager/pom.xml` 中新增：

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.2</version>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>2.9.2</version>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-compress</artifactId>
    <version>1.26.1</version>
</dependency>
```

## 文件结构

```
sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/
├── FileTypeCheckProvider.java          (接口，新增 getPriority())
├── FileTypeChecker.java                (不变)
├── FileTypeCheckerImpl.java            (重构匹配逻辑)
├── util/
│   ├── MagicBytesUtils.java            (新增，magic bytes 读取工具)
│   └── EncodingDetector.java           (新增，文本编码检测工具)
└── provider/
    ├── ImageCheckProvider.java          (从上层移入)
    ├── AudioCheckProvider.java          (从上层移入)
    ├── VideoCheckProvider.java          (从上层移入)
    ├── DocumentCheckProvider.java       (新增)
    ├── ArchiveCheckProvider.java        (新增)
    ├── IsoCheckProvider.java            (新增)
    ├── PlainTextCheckProvider.java      (新增)
    ├── ExeCheckProvider.java            (新增)
    └── MsiCheckProvider.java            (新增)
```

## Spring 配置

在 `DataManagerAutoConfiguration` 中新增：

```java
@Configuration
public static class CommonProviderConfiguration {
    @Bean
    public DocumentCheckProvider documentCheckProvider() { ... }
    @Bean
    public ArchiveCheckProvider archiveCheckProvider() { ... }
    @Bean
    public IsoCheckProvider isoCheckProvider() { ... }
    @Bean
    public PlainTextCheckProvider plainTextCheckProvider() { ... }
    @Bean
    public ExeCheckProvider exeCheckProvider() { ... }
    @Bean
    public MsiCheckProvider msiCheckProvider() { ... }
}
```

这些 Provider 不依赖 FFMpeg，无需 `@ConditionalOnClass` 条件注解。

## 共享工具类

### MagicBytesUtils

```java
public class MagicBytesUtils {
    /** 读取文件头部 N 字节 */
    public static byte[] readHeader(File file, int length);

    /** 检查 header 在 offset 处是否匹配 magic */
    public static boolean matchMagic(byte[] header, byte[] magic, int offset);
}
```

### EncodingDetector

```java
public class EncodingDetector {
    /** 检测文件编码（基于 BOM 和字节统计） */
    public static String detect(File file);
}
```
