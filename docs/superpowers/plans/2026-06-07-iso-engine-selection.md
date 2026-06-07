# ISO 解析引擎选择功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 提供基于 sevenzipjbinding 的 ISO 文件系统实现，管理员可通过后台配置选择使用 sevenzipjbinding 或 java-iso-tools 解析 ISO 文件。

**Architecture:** 在现有 `IsoFileSystem` 抽象接口下新增 `SevenZipJBindingIsoFileSystem` 实现，通过 `PxeBootProperty` 配置项控制 `JavaIsoHandler` 动态选择实现。上层 `IsoHandler` 接口和所有消费者代码无需修改。

**Tech Stack:** Java 25, Spring Boot, sevenzipjbinding 16.02-2.01, java-iso-tools 2.1.0

---

## File Structure

### 新增文件
- `sfc-ext/sfc-ext-pxe-boot/src/main/java/com/sfc/pxeboot/server/iso/SevenZipJBindingIsoFileSystem.java` — sevenzipjbinding ISO 文件系统实现

### 修改文件
- `sfc-ext/sfc-ext-pxe-boot/src/main/java/com/sfc/pxeboot/PxeBootProperty.java` — 新增 iso 分组和 isoEngine 配置项
- `sfc-ext/sfc-ext-pxe-boot/src/main/java/com/sfc/pxeboot/server/iso/JavaIsoHandler.java` — 注入配置，动态选择实现

### 不变文件
- `IsoHandler.java` — 接口不变
- `IsoFileSystem.java` — 接口不变
- `IsoFileEntry.java` — 数据模型不变
- `JavaIsoToolsIso9660FileSystem.java` — 现有实现不变
- `PxeBootAutoConfiguration.java` — 无需修改（Spring 自动注入 `JavaIsoHandler`）
- `IsoResourceExtractorService.java` — 依赖接口，无需修改
- `PxeBootController.java` — 依赖接口，无需修改

---

### Task 1: 修改 PxeBootProperty 新增 ISO 配置

**Files:**
- Modify: `sfc-ext/sfc-ext-pxe-boot/src/main/java/com/sfc/pxeboot/PxeBootProperty.java`

- [ ] **Step 1: 添加 iso 分组和配置属性**

在 `@ConfigPropertyEntity` 的 `groups` 数组中新增 `@ConfigPropertiesGroup(id = "iso", name = "ISO 配置")`。

在 `PxeBootProperty` 类末尾（`defaultTimeout` 字段之后）新增字段：

```java
    // ==================== ISO 配置 ====================

    /**
     * ISO 文件解析引擎
     */
    @ConfigProperty(
            value = "iso-engine",
            title = "ISO 解析引擎",
            describe = "选择 ISO 文件解析引擎，sevenzipjbinding 兼容性更好",
            defaultValue = "java-iso-tools",
            group = "iso",
            inputType = "select",
            options = "java-iso-tools=java-iso-tools,sevenzipjbinding=sevenzipjbinding"
    )
    private String isoEngine = "java-iso-tools";
```

- [ ] **Step 2: 验证编译**

使用 MCP `build_project` 编译，确认无编译错误。使用 MCP `get_file_problems` 检查文件无警告。

- [ ] **Step 3: 提交**

```bash
git add sfc-ext/sfc-ext-pxe-boot/src/main/java/com/sfc/pxeboot/PxeBootProperty.java
git commit -m "feat(pxe-boot): PxeBootProperty 新增 ISO 解析引擎配置项"
```

---

### Task 2: 新增 SevenZipJBindingIsoFileSystem 实现

**Files:**
- Create: `sfc-ext/sfc-ext-pxe-boot/src/main/java/com/sfc/pxeboot/server/iso/SevenZipJBindingIsoFileSystem.java`

核心设计：
- `traverse()`: 打开 `IInArchive` 遍历所有条目，转换为 `IsoFileEntry`。`InputStreamSupplier` 独立打开自己的 archive 实例，与 traverse 的 archive 生命周期无关。
- `getResource()`: 返回 `LazySevenZipResource`，`getInputStream()` 时独立打开 archive 并提取文件内容。
- 文件提取使用 `ByteArrayOutputStream` 缓冲（PXE 启动文件如 vmlinuz/initrd 通常 < 500MB）。

- [ ] **Step 1: 创建 SevenZipJBindingIsoFileSystem 完整实现**

```java
package com.sfc.pxeboot.server.iso;

import lombok.extern.slf4j.Slf4j;
import net.sf.sevenzipjbinding.*;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;

import java.io.*;
import java.nio.file.Path;
import java.util.Date;
import java.util.function.Predicate;

/**
 * 基于 SevenZipJBinding 的 ISO 文件系统实现。
 * <p>封装所有 sevenzipjbinding 依赖，实现 {@link IsoFileSystem} 接口。
 * 支持 ISO9660、UDF 等多种 ISO 格式。</p>
 */
@Slf4j
public class SevenZipJBindingIsoFileSystem implements IsoFileSystem {

    private final File isoFile;

    /**
     * 构造函数。
     *
     * @param isoFile ISO 文件
     */
    public SevenZipJBindingIsoFileSystem(File isoFile) {
        this.isoFile = isoFile;
    }

    @Override
    public void traverse(Predicate<TraversalEntry> visitor) throws IOException {
        log.debug("[PXE-ISO-7Z] 遍历 ISO 所有条目: {}", isoFile);

        try (RandomAccessFileInStream stream = new RandomAccessFileInStream(new RandomAccessFile(isoFile, "r"))) {
            IInArchive archive = SevenZip.openInArchive(null, stream);
            try {
                int itemCount = archive.getNumberOfItems();
                for (int i = 0; i < itemCount; i++) {
                    IsoFileEntry fileEntry = toFileEntry(archive, i);

                    final int index = i;
                    InputStreamSupplier supplier = fileEntry.isFile()
                        ? () -> extractByIndex(index)
                        : null;

                    TraversalEntry traversalEntry = new TraversalEntry(fileEntry, supplier);
                    if (!visitor.test(traversalEntry)) {
                        break;
                    }
                }
            } finally {
                archive.close();
            }
        } catch (SevenZipException e) {
            throw new IOException("遍历 ISO 失败: " + isoFile, e);
        }
    }

    @Override
    public Resource getResource(String filePath) throws IOException {
        if (!exist(filePath)) {
            return null;
        }
        String normalizedPath = normalizePath(filePath);

        log.debug("[PXE-ISO-7Z] 获取 ISO 文件资源: {}, 路径: {}", isoFile, normalizedPath);

        try (RandomAccessFileInStream stream = new RandomAccessFileInStream(new RandomAccessFile(isoFile, "r"))) {
            IInArchive archive = SevenZip.openInArchive(null, stream);
            try {
                int itemCount = archive.getNumberOfItems();
                for (int i = 0; i < itemCount; i++) {
                    IsoFileEntry entry = toFileEntry(archive, i);
                    if (entry.isFile() && entry.getPath().equalsIgnoreCase(normalizedPath)) {
                        String fileName = Path.of(filePath).getFileName().toString();
                        long size = entry.getSize();
                        return new LazySevenZipResource(isoFile, normalizedPath, fileName, size);
                    }
                }
            } finally {
                archive.close();
            }
        } catch (SevenZipException e) {
            throw new IOException("获取 ISO 资源失败: " + isoFile, e);
        }

        return null;
    }

    // ==================== Helper Methods ====================

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String normalized = path.replace("\\", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private IsoFileEntry toFileEntry(IInArchive archive, int index) throws SevenZipException {
        String path = (String) archive.getProperty(index, PropID.PATH);
        Boolean isFolder = (Boolean) archive.getProperty(index, PropID.IS_FOLDER);
        long size = (Long) archive.getProperty(index, PropID.SIZE);
        Date lastModified = (Date) archive.getProperty(index, PropID.LAST_MODIFICATION_TIME);

        if (path == null) {
            path = "";
        }
        path = path.replace("\\", "/");

        boolean isDir = isFolder != null && isFolder;

        // 根目录特殊情况
        if (path.isEmpty() || path.equals("/")) {
            return new IsoFileEntry("/", "/", isDir ? -1 : size,
                lastModified != null ? lastModified.getTime() : 0,
                IsoFileEntry.EntryType.DIRECTORY);
        }

        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        String name = Path.of(path).getFileName().toString();
        IsoFileEntry.EntryType type = isDir ? IsoFileEntry.EntryType.DIRECTORY : IsoFileEntry.EntryType.FILE;

        return new IsoFileEntry(name, path, isDir ? -1 : size,
            lastModified != null ? lastModified.getTime() : 0, type);
    }

    /**
     * 按索引提取文件内容为 InputStream（独立打开 archive，线程安全）。
     */
    private InputStream extractByIndex(int index) throws IOException {
        try {
            RandomAccessFileInStream rafStream = new RandomAccessFileInStream(new RandomAccessFile(isoFile, "r"));
            IInArchive archive = SevenZip.openInArchive(null, rafStream);
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                archive.extractItems(new int[]{index}, false, new IArchiveExtractCallback() {
                    @Override
                    public ISequentialOutStream getStream(int idx, ExtractAskMode extractAskMode) throws SevenZipException {
                        if (extractAskMode != ExtractAskMode.EXTRACT) {
                            return null;
                        }
                        return data -> {
                            baos.write(data);
                            return data.length;
                        };
                    }

                    @Override
                    public void prepareOperation(ExtractAskMode extractAskMode) {
                    }

                    @Override
                    public void setOperationResult(ExtractOperationResult extractOperationResult) throws SevenZipException {
                        if (extractOperationResult != ExtractOperationResult.OK) {
                            throw new SevenZipException("提取失败: " + extractOperationResult);
                        }
                    }

                    @Override
                    public void setCompleted(long completeValue) {
                    }

                    @Override
                    public void setTotal(long total) {
                    }
                });
                return new ByteArrayInputStream(baos.toByteArray());
            } finally {
                archive.close();
                rafStream.close();
            }
        } catch (SevenZipException e) {
            throw new IOException("提取 ISO 文件失败, index=" + index, e);
        }
    }

    // ==================== Inner Classes ====================

    /**
     * 惰性 ISO 文件资源（SevenZipJBinding 版本）。
     * <p>getInputStream() 时才打开 ISO 并提取文件内容。</p>
     */
    private static class LazySevenZipResource extends AbstractResource {
        private final File isoFile;
        private final String filePath;
        private final String fileName;
        private final long size;

        LazySevenZipResource(File isoFile, String filePath, String fileName, long size) {
            this.isoFile = isoFile;
            this.filePath = filePath;
            this.fileName = fileName;
            this.size = size;
        }

        @Override
        public String getFilename() {
            return fileName;
        }

        @NotNull
        @Override
        public InputStream getInputStream() throws IOException {
            RandomAccessFileInStream rafStream = new RandomAccessFileInStream(new RandomAccessFile(isoFile, "r"));
            IInArchive archive = SevenZip.openInArchive(null, rafStream);
            try {
                int itemCount = archive.getNumberOfItems();
                for (int i = 0; i < itemCount; i++) {
                    String path = (String) archive.getProperty(i, PropID.PATH);
                    if (path == null) {
                        path = "";
                    }
                    path = path.replace("\\", "/");
                    if (!path.startsWith("/")) {
                        path = "/" + path;
                    }
                    if (path.endsWith("/")) {
                        path = path.substring(0, path.length() - 1);
                    }

                    if (path.equalsIgnoreCase(filePath)) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        final int index = i;
                        archive.extractItems(new int[]{index}, false, new IArchiveExtractCallback() {
                            @Override
                            public ISequentialOutStream getStream(int idx, ExtractAskMode extractAskMode) throws SevenZipException {
                                if (extractAskMode != ExtractAskMode.EXTRACT) {
                                    return null;
                                }
                                return data -> {
                                    baos.write(data);
                                    return data.length;
                                };
                            }

                            @Override
                            public void prepareOperation(ExtractAskMode extractAskMode) {
                            }

                            @Override
                            public void setOperationResult(ExtractOperationResult extractOperationResult) throws SevenZipException {
                                if (extractOperationResult != ExtractOperationResult.OK) {
                                    throw new SevenZipException("提取失败: " + extractOperationResult);
                                }
                            }

                            @Override
                            public void setCompleted(long completeValue) {
                            }

                            @Override
                            public void setTotal(long total) {
                            }
                        });
                        archive.close();
                        rafStream.close();
                        return new ByteArrayInputStream(baos.toByteArray());
                    }
                }
                archive.close();
                rafStream.close();
                throw new IOException("ISO 中未找到文件: " + filePath);
            } catch (Exception e) {
                try {
                    archive.close();
                } catch (Exception ignored) {
                }
                try {
                    rafStream.close();
                } catch (Exception ignored) {
                }
                throw e;
            }
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public long contentLength() {
            return size;
        }

        @NotNull
        @Override
        public String getDescription() {
            return "ISO entry [" + filePath + "] (7z)";
        }
    }
}
```

- [ ] **Step 2: 验证编译**

使用 MCP `build_project` 编译 sfc-ext-pxe-boot 模块，确认无编译错误。使用 MCP `get_file_problems` 检查文件无警告。

- [ ] **Step 3: 提交**

```bash
git add sfc-ext/sfc-ext-pxe-boot/src/main/java/com/sfc/pxeboot/server/iso/SevenZipJBindingIsoFileSystem.java
git commit -m "feat(pxe-boot): 新增 SevenZipJBindingIsoFileSystem 实现"
```

---

### Task 3: 修改 JavaIsoHandler 支持动态选择

**Files:**
- Modify: `sfc-ext/sfc-ext-pxe-boot/src/main/java/com/sfc/pxeboot/server/iso/JavaIsoHandler.java`

- [ ] **Step 1: 修改 JavaIsoHandler**

完整替换文件内容。主要改动：添加 `PxeBootProperty` 构造注入，`createFileSystem()` 根据配置动态选择实现。

```java
package com.sfc.pxeboot.server.iso;

import com.sfc.pxeboot.PxeBootProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * ISO 处理器。
 * <p>将 Spring {@link Resource} 转换为本地文件后委托给 {@link IsoFileSystem}。
 * 根据 {@link PxeBootProperty#getIsoEngine()} 配置动态选择 ISO 解析引擎。</p>
 */
@Slf4j
public class JavaIsoHandler implements IsoHandler {

    private final PxeBootProperty pxeBootProperty;

    /**
     * 构造函数。
     *
     * @param pxeBootProperty PXE 启动配置
     */
    public JavaIsoHandler(PxeBootProperty pxeBootProperty) {
        this.pxeBootProperty = pxeBootProperty;
    }

    @Override
    public List<String> listFiles(Resource isoResource, String pathWithinIso) throws IOException {
        IsoFileSystem fs = createFileSystem(isoResource);
        return fs.listFiles(pathWithinIso).stream()
            .map(IsoFileEntry::getName)
            .toList();
    }

    @Override
    public Resource getResource(Resource isoResource, String pathWithinIso) throws IOException {
        IsoFileSystem fs = createFileSystem(isoResource);
        return fs.getResource(pathWithinIso);
    }

    @Override
    public List<IsoFileEntry> findFilesByPattern(Resource isoResource, String pattern, String basePath) throws IOException {
        IsoFileSystem fs = createFileSystem(isoResource);
        return fs.findFilesByPattern(pattern, basePath);
    }

    /**
     * 根据配置创建对应的 IsoFileSystem 实现。
     *
     * @param isoResource ISO 文件资源
     * @return IsoFileSystem 实例
     * @throws IOException 如果资源不存在或不是本地文件
     */
    private IsoFileSystem createFileSystem(Resource isoResource) throws IOException {
        File isoFile = getLocalIsoFile(isoResource);
        String engine = pxeBootProperty.getIsoEngine();

        if ("sevenzipjbinding".equalsIgnoreCase(engine)) {
            log.debug("[PXE-ISO] 使用 SevenZipJBinding 引擎: {}", isoFile);
            return new SevenZipJBindingIsoFileSystem(isoFile);
        }

        log.debug("[PXE-ISO] 使用 java-iso-tools 引擎: {}", isoFile);
        return new JavaIsoToolsIso9660FileSystem(isoFile);
    }

    /**
     * 从 Resource 获取 ISO 文件的本地 File 对象。
     *
     * @param isoResource ISO 文件资源
     * @return 本地文件
     * @throws IOException              如果资源不存在
     * @throws IllegalArgumentException 如果文件不是本地存储的
     */
    private File getLocalIsoFile(Resource isoResource) throws IOException {
        if (!isoResource.exists()) {
            throw new IOException("ISO 文件不存在: " + isoResource.getDescription());
        }

        try {
            if (isoResource instanceof PathResource pathResource) {
                return Path.of(pathResource.getPath()).toFile();
            }
            if (isoResource.isFile()) {
                return Path.of(isoResource.getURI()).toFile();
            }
        } catch (Exception e) {
            log.warn("[PXE-ISO] 获取本地路径失败: {}", e.getMessage());
        }

        throw new IllegalArgumentException("ISO 文件必须存储在本地文件系统，当前存储不支持直接访问");
    }
}
```

- [ ] **Step 2: 验证编译**

使用 MCP `build_project` 编译，确认无编译错误。使用 MCP `get_file_problems` 检查文件无警告。

- [ ] **Step 3: 提交**

```bash
git add sfc-ext/sfc-ext-pxe-boot/src/main/java/com/sfc/pxeboot/server/iso/JavaIsoHandler.java
git commit -m "feat(pxe-boot): JavaIsoHandler 支持根据配置动态选择 ISO 引擎"
```

---

### Task 4: 整体编译验证

- [ ] **Step 1: 全量编译验证**

使用 MCP `build_project`（rebuild=true）执行全量编译，确认所有模块无编译错误。

- [ ] **Step 2: 检查所有修改文件**

使用 MCP `get_file_problems` 检查以下文件：
- `PxeBootProperty.java`
- `SevenZipJBindingIsoFileSystem.java`
- `JavaIsoHandler.java`

- [ ] **Step 3: 最终提交（如有修复）**

如有警告修复，提交修复代码。
