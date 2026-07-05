# 文件类型识别 Provider 扩展实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `sfc-ext-data-manager` 新增 6 个文件类型识别 Provider（文档、压缩包、ISO、纯文本、EXE、MSI），并引入优先级机制解决多 Provider 冲突。

**Architecture:** 在 `FileTypeCheckProvider` 接口新增 `getPriority()` 默认方法实现优先级排序；6 个新 Provider 各自使用 magic bytes 快速预判断 + Tika 元数据提取；现有 3 个 Provider 迁移到 `provider` 子包。

**Tech Stack:** Java 25, Spring Boot, Apache Tika 2.9.2, Apache Commons Compress 1.26.1, Lombok

---

## 文件结构总览

```
sfc-ext-data-manager/src/main/java/com/sfc/dm/
├── service/identify/
│   ├── FileTypeCheckProvider.java          (修改：新增 getPriority())
│   ├── FileTypeChecker.java                (不变)
│   ├── FileTypeCheckerImpl.java            (修改：排序逻辑)
│   ├── ImageCheckProvider.java             (删除：移至 provider/)
│   ├── AudioCheckProvider.java             (删除：移至 provider/)
│   ├── VideoCheckProvider.java             (删除：移至 provider/)
│   ├── util/
│   │   ├── MagicBytesUtils.java            (新建)
│   │   └── EncodingDetector.java           (新建)
│   └── provider/
│       ├── ImageCheckProvider.java          (新建：从上层移入)
│       ├── AudioCheckProvider.java          (新建：从上层移入)
│       ├── VideoCheckProvider.java          (新建：从上层移入)
│       ├── DocumentCheckProvider.java       (新建)
│       ├── ArchiveCheckProvider.java        (新建)
│       ├── IsoCheckProvider.java            (新建)
│       ├── PlainTextCheckProvider.java      (新建)
│       ├── ExeCheckProvider.java            (新建)
│       └── MsiCheckProvider.java            (新建)
├── config/
│   └── DataManagerAutoConfiguration.java    (修改：新增 CommonProviderConfiguration)
└── pom.xml                                  (修改：新增依赖)
```

---

## Task 1: 添加 Maven 依赖

**Files:**
- Modify: `sfc-ext/sfc-ext-data-manager/pom.xml`

- [ ] **Step 1: 在 pom.xml 中新增 Tika 和 Commons Compress 依赖**

在 `<dependencies>` 节点末尾添加：

```xml
<!-- Apache Tika - 文件类型检测与元数据提取 -->
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
<!-- Apache Commons Compress - 压缩包处理 -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-compress</artifactId>
    <version>1.26.1</version>
</dependency>
```

- [ ] **Step 2: 编译验证依赖可用**

Run: 使用 MCP `build_project` 对 `sfc-ext-data-manager` 模块编译
Expected: 编译成功，无依赖解析错误

- [ ] **Step 3: Commit**

```bash
git add sfc-ext/sfc-ext-data-manager/pom.xml
git commit -m "deps(data-manager): add tika and commons-compress dependencies"
```

---

## Task 2: 新增 MagicBytesUtils 工具类

**Files:**
- Create: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/util/MagicBytesUtils.java`

- [ ] **Step 1: 创建 MagicBytesUtils**

```java
package com.sfc.dm.service.identify.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * 文件头 magic bytes 读取与匹配工具类
 */
public class MagicBytesUtils {

    private MagicBytesUtils() {}

    /**
     * 读取文件头部指定长度的字节
     * @param file 目标文件
     * @param length 需要读取的字节数
     * @return 文件头字节数组，文件不足指定长度时返回实际读取的字节
     * @throws IOException 读取失败时抛出
     */
    public static byte[] readHeader(File file, int length) throws IOException {
        byte[] header = new byte[length];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            int read = raf.read(header, 0, length);
            if (read < length) {
                byte[] result = new byte[read];
                System.arraycopy(header, 0, result, 0, read);
                return result;
            }
        }
        return header;
    }

    /**
     * 从指定偏移位置读取文件内容
     * @param file 目标文件
     * @param offset 起始偏移量
     * @param length 需要读取的字节数
     * @return 读取到的字节数组
     * @throws IOException 读取失败时抛出
     */
    public static byte[] readAt(File file, long offset, int length) throws IOException {
        byte[] buffer = new byte[length];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);
            int read = raf.read(buffer, 0, length);
            if (read < length) {
                byte[] result = new byte[read];
                System.arraycopy(buffer, 0, result, 0, read);
                return result;
            }
        }
        return buffer;
    }

    /**
     * 检查 header 字节数组在指定偏移处是否匹配 magic 字节
     * @param header 文件头字节数组
     * @param magic 待匹配的 magic 字节序列
     * @param offset 起始偏移量
     * @return 是否匹配
     */
    public static boolean matchMagic(byte[] header, byte[] magic, int offset) {
        if (header == null || header.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (header[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查文件头是否以指定 magic 字节开头（偏移为 0）
     * @param header 文件头字节数组
     * @param magic 待匹配的 magic 字节序列
     * @return 是否匹配
     */
    public static boolean matchMagic(byte[] header, byte[] magic) {
        return matchMagic(header, magic, 0);
    }
}
```

- [ ] **Step 2: 编译验证**

使用 MCP `build_project` 编译，确认无错误。

- [ ] **Step 3: Commit**

```bash
git add sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/util/MagicBytesUtils.java
git commit -m "feat(data-manager): add MagicBytesUtils for file header detection"
```

---

## Task 3: 新增 EncodingDetector 工具类

**Files:**
- Create: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/util/EncodingDetector.java`

- [ ] **Step 1: 创建 EncodingDetector**

```java
package com.sfc.dm.service.identify.util;

import java.io.*;
import java.nio.charset.Charset;

/**
 * 文本文件编码检测工具，基于 BOM 标记和字节统计
 */
public class EncodingDetector {

    private EncodingDetector() {}

    /**
     * 检测文件编码
     * @param file 待检测文件
     * @return 编码名称（如 UTF-8、GBK、ISO-8859-1）
     */
    public static String detect(File file) {
        try {
            byte[] header = MagicBytesUtils.readHeader(file, 3);
            if (header.length >= 3
                    && (header[0] & 0xFF) == 0xEF
                    && (header[1] & 0xFF) == 0xBB
                    && (header[2] & 0xFF) == 0xBF) {
                return "UTF-8 BOM";
            }
            if (header.length >= 2
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xFE) {
                return "UTF-16 LE";
            }
            if (header.length >= 2
                    && (header[0] & 0xFF) == 0xFE
                    && (header[1] & 0xFF) == 0xFF) {
                return "UTF-16 BE";
            }
        } catch (IOException ignored) {
        }

        // 读取前 8KB 进行编码推断
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[8192];
            int len = bis.read(buf);
            if (len <= 0) {
                return "UTF-8";
            }
            return guessEncoding(buf, len);
        } catch (IOException e) {
            return "Unknown";
        }
    }

    /**
     * 根据字节特征推断编码
     */
    private static String guessEncoding(byte[] buf, int len) {
        boolean hasHighByte = false;
        boolean validUtf8 = true;
        int i = 0;

        while (i < len) {
            int b = buf[i] & 0xFF;
            if (b <= 0x7F) {
                i++;
            } else if ((b & 0xE0) == 0xC0) {
                if (i + 1 >= len || (buf[i + 1] & 0xC0) != 0x80) {
                    validUtf8 = false;
                    break;
                }
                i += 2;
                hasHighByte = true;
            } else if ((b & 0xF0) == 0xE0) {
                if (i + 2 >= len || (buf[i + 1] & 0xC0) != 0x80 || (buf[i + 2] & 0xC0) != 0x80) {
                    validUtf8 = false;
                    break;
                }
                i += 3;
                hasHighByte = true;
            } else if ((b & 0xF8) == 0xF0) {
                if (i + 3 >= len || (buf[i + 1] & 0xC0) != 0x80
                        || (buf[i + 2] & 0xC0) != 0x80 || (buf[i + 3] & 0xC0) != 0x80) {
                    validUtf8 = false;
                    break;
                }
                i += 4;
                hasHighByte = true;
            } else {
                validUtf8 = false;
                break;
            }
        }

        if (validUtf8 && !hasHighByte) {
            return "ASCII";
        }
        if (validUtf8) {
            return "UTF-8";
        }
        // 有高位字节但非 UTF-8，尝试 GBK
        return "GBK";
    }
}
```

- [ ] **Step 2: 编译验证**

使用 MCP `build_project` 编译，确认无错误。

- [ ] **Step 3: Commit**

```bash
git add sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/util/EncodingDetector.java
git commit -m "feat(data-manager): add EncodingDetector for text file encoding detection"
```

---

## Task 4: 修改 FileTypeCheckProvider 接口 — 新增 getPriority()

**Files:**
- Modify: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/FileTypeCheckProvider.java`

- [ ] **Step 1: 在接口末尾新增 getPriority() 默认方法**

在 `checkFile` 方法后添加：

```java
/**
 * 获取 Provider 的优先级，数值越小优先级越高。
 * 当多个 Provider 都能识别同一文件时，优先使用高优先级的 Provider。
 * @return 优先级数值
 */
default int getPriority() {
    return Integer.MAX_VALUE;
}
```

- [ ] **Step 2: 编译验证**

使用 MCP `build_project` 编译，确认现有 Provider 无需修改即可编译通过。

- [ ] **Step 3: Commit**

```bash
git add sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/FileTypeCheckProvider.java
git commit -m "feat(data-manager): add getPriority() default method to FileTypeCheckProvider"
```

---

## Task 5: 修改 FileTypeCheckerImpl — 按优先级排序

**Files:**
- Modify: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/FileTypeCheckerImpl.java`

- [ ] **Step 1: 修改 setProviders() 方法，在注入后按优先级排序**

将现有的 `setProviders` 方法：

```java
@Autowired(required = false)
public void setProviders(List<FileTypeCheckProvider> providers) {
    if (providers != null) {
        providers.forEach(this::addProvider);
    }
}
```

替换为：

```java
@Autowired(required = false)
public void setProviders(List<FileTypeCheckProvider> providers) {
    if (providers != null) {
        providers.stream()
                .sorted(Comparator.comparingInt(FileTypeCheckProvider::getPriority))
                .forEach(this::addProvider);
    }
}
```

在文件头部添加 import：

```java
import java.util.Comparator;
```

- [ ] **Step 2: 编译验证**

使用 MCP `build_project` 编译，确认无错误。

- [ ] **Step 3: Commit**

```bash
git add sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/FileTypeCheckerImpl.java
git commit -m "refactor(data-manager): sort providers by priority in FileTypeCheckerImpl"
```

---

## Task 6: 迁移现有 Provider 到 provider 子包

**Files:**
- Create: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/provider/ImageCheckProvider.java`
- Create: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/provider/AudioCheckProvider.java`
- Create: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/provider/VideoCheckProvider.java`
- Delete: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/ImageCheckProvider.java`
- Delete: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/AudioCheckProvider.java`
- Delete: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/VideoCheckProvider.java`
- Modify: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/config/DataManagerAutoConfiguration.java`

- [ ] **Step 1: 创建 provider/ImageCheckProvider.java**

将原 `ImageCheckProvider.java` 的内容复制到 `provider/ImageCheckProvider.java`，修改包名：

```java
package com.sfc.dm.service.identify.provider;
```

其余代码不变。

- [ ] **Step 2: 创建 provider/AudioCheckProvider.java**

同上，修改包名为 `com.sfc.dm.service.identify.provider`。

- [ ] **Step 3: 创建 provider/VideoCheckProvider.java**

同上，修改包名为 `com.sfc.dm.service.identify.provider`。

- [ ] **Step 4: 更新 DataManagerAutoConfiguration 的 import**

修改 `DataManagerAutoConfiguration.java` 中的 import 语句：

```java
// 删除旧 import
import com.sfc.dm.service.identify.AudioCheckProvider;
import com.sfc.dm.service.identify.ImageCheckProvider;
import com.sfc.dm.service.identify.VideoCheckProvider;

// 替换为新 import
import com.sfc.dm.service.identify.provider.AudioCheckProvider;
import com.sfc.dm.service.identify.provider.ImageCheckProvider;
import com.sfc.dm.service.identify.provider.VideoCheckProvider;
```

- [ ] **Step 5: 删除旧的 Provider 文件**

删除以下 3 个文件：
- `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/ImageCheckProvider.java`
- `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/AudioCheckProvider.java`
- `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/VideoCheckProvider.java`

- [ ] **Step 6: 编译验证**

使用 MCP `build_project` 编译，确认迁移后编译通过。

- [ ] **Step 7: Commit**

```bash
git add sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/provider/
git add sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/ImageCheckProvider.java
git add sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/AudioCheckProvider.java
git add sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/VideoCheckProvider.java
git add sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/config/DataManagerAutoConfiguration.java
git commit -m "refactor(data-manager): move existing providers to provider sub-package"
```

---

## Task 7: 新增 DocumentCheckProvider

**Files:**
- Create: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/provider/DocumentCheckProvider.java`

- [ ] **Step 1: 创建 DocumentCheckProvider**

```java
package com.sfc.dm.service.identify.provider;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import com.sfc.dm.service.identify.FileTypeCheckProvider;
import com.sfc.dm.service.identify.util.MagicBytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 文档文件类型识别提供者，支持 Office 文档（docx/doc/xlsx/xls/pptx/ppt）、PDF、ODF 格式
 */
@Slf4j
public class DocumentCheckProvider implements FileTypeCheckProvider {
    private static final String ID = "documentCheckProvider";
    private static final String TYPE_NAME = "文档";
    private static final String TYPE_ID = "document";
    private static final int PRIORITY = 10;

    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46}; // %PDF
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    private static final List<String> EXTENSIONS = List.of(
            ".docx", ".doc", ".xlsx", ".xls", ".pptx", ".ppt", ".pdf",
            ".odt", ".ods", ".odp"
    );

    // OLE2 内部流名，用于区分 doc/xls/ppt
    private static final Set<String> OLE2_DOC_STREAMS = Set.of("WordDocument", "WordDocument");
    private static final Set<String> OLE2_XLS_STREAMS = Set.of("Workbook", "Book");
    private static final Set<String> OLE2_PPT_STREAMS = Set.of("PowerPoint Document", "Current User");

    @Override
    public String getId() { return ID; }

    @Override
    public String getTypeName() { return TYPE_NAME; }

    @Override
    public String getTypeId() { return TYPE_ID; }

    @Override
    public List<String> getSupportedFileExtensions() { return EXTENSIONS; }

    @Override
    public int getPriority() { return PRIORITY; }

    @Override
    public List<FileMetadataDefine> getMetadataDefines() {
        return List.of(
                new FileMetadataDefine("标题", "title", "文档标题", "span"),
                new FileMetadataDefine("作者", "author", "文档作者", "span"),
                new FileMetadataDefine("页数", "pageCount", "文档页数", "span"),
                new FileMetadataDefine("创建时间", "createdDate", "文档创建时间", "span")
        );
    }

    @Override
    public FileTypeCheckResultDetail checkFile(File file, boolean extraMetadata) {
        try {
            byte[] header = MagicBytesUtils.readHeader(file, 8);
            if (header.length < 4) {
                return null;
            }

            String detectedExt = null;
            String mimeType = null;

            // PDF 检测
            if (MagicBytesUtils.matchMagic(header, PDF_MAGIC)) {
                detectedExt = ".pdf";
                mimeType = "application/pdf";
            }
            // OLE2 检测（doc/xls/ppt，排除 MSI 由 MsiCheckProvider 处理）
            else if (MagicBytesUtils.matchMagic(header, OLE2_MAGIC)) {
                String[] ole2Result = detectOle2SubType(file);
                if (ole2Result != null) {
                    detectedExt = ole2Result[0];
                    mimeType = ole2Result[1];
                }
            }
            // ZIP 检测（docx/xlsx/pptx/odt/ods/odp）
            else if (MagicBytesUtils.matchMagic(header, ZIP_MAGIC)) {
                String[] zipResult = detectZipSubType(file);
                if (zipResult != null) {
                    detectedExt = zipResult[0];
                    mimeType = zipResult[1];
                }
            }

            if (detectedExt == null) {
                return null;
            }

            FileTypeCheckResultDetail detail = new FileTypeCheckResultDetail();
            detail.setExtension(detectedExt);
            detail.setMimetype(mimeType);

            if (extraMetadata) {
                Map<String, String> metadata = extractMetadata(file);
                if (metadata != null && !metadata.isEmpty()) {
                    detail.setMetadata(metadata);
                }
            }

            return detail;
        } catch (Exception e) {
            log.debug("文档检测失败: {}", file.getName(), e);
            return null;
        }
    }

    /**
     * 检测 OLE2 格式的具体子类型（doc/xls/ppt）
     * @return [extension, mimetype] 或 null
     */
    private String[] detectOle2SubType(File file) {
        String lowerName = file.getName().toLowerCase();
        if (lowerName.endsWith(".doc")) {
            return new String[]{".doc", "application/msword"};
        }
        if (lowerName.endsWith(".xls")) {
            return new String[]{".xls", "application/vnd.ms-excel"};
        }
        if (lowerName.endsWith(".ppt")) {
            return new String[]{".ppt", "application/vnd.ms-powerpoint"};
        }
        // 扩展名不明确时，尝试通过 Tika 检测
        try {
            String tikaType = detectByTika(file);
            if (tikaType != null) {
                return switch (tikaType) {
                    case "application/msword" -> new String[]{".doc", tikaType};
                    case "application/vnd.ms-excel" -> new String[]{".xls", tikaType};
                    case "application/vnd.ms-powerpoint" -> new String[]{".ppt", tikaType};
                    default -> null;
                };
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 检测 ZIP 格式的具体子类型（docx/xlsx/pptx/odt/ods/odp）
     * @return [extension, mimetype] 或 null
     */
    private String[] detectZipSubType(File file) {
        try (ZipFile zipFile = new ZipFile(file)) {
            // 检查是否为 Office Open XML
            if (zipFile.getEntry("[Content_Types].xml") != null) {
                return detectOfficeOpenXmlSubType(zipFile, file.getName());
            }
            // 检查是否为 ODF
            ZipEntry mimetypeEntry = zipFile.getEntry("mimetype");
            if (mimetypeEntry != null) {
                return detectOdfSubType(zipFile, mimetypeEntry);
            }
        } catch (IOException e) {
            log.debug("ZIP 内部结构检测失败: {}", file.getName(), e);
        }
        return null;
    }

    /**
     * 根据 Office Open XML 内容判断具体子类型
     */
    private String[] detectOfficeOpenXmlSubType(ZipFile zipFile, String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".docx")) {
            return new String[]{".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"};
        }
        if (lowerName.endsWith(".xlsx")) {
            return new String[]{".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"};
        }
        if (lowerName.endsWith(".pptx")) {
            return new String[]{".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"};
        }
        // 检查内部目录结构判断类型
        if (zipFile.getEntry("word/document.xml") != null) {
            return new String[]{".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"};
        }
        if (zipFile.getEntry("xl/workbook.xml") != null) {
            return new String[]{".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"};
        }
        if (zipFile.getEntry("ppt/presentation.xml") != null) {
            return new String[]{".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"};
        }
        return null;
    }

    /**
     * 根据 ODF mimetype 内容判断具体子类型
     */
    private String[] detectOdfSubType(ZipFile zipFile, ZipEntry mimetypeEntry) {
        try (InputStream is = zipFile.getInputStream(mimetypeEntry)) {
            byte[] buf = new byte[128];
            int len = is.read(buf);
            String mimetype = new String(buf, 0, len).trim();
            return switch (mimetype) {
                case "application/vnd.oasis.opendocument.text" ->
                        new String[]{".odt", mimetype};
                case "application/vnd.oasis.opendocument.spreadsheet" ->
                        new String[]{".ods", mimetype};
                case "application/vnd.oasis.opendocument.presentation" ->
                        new String[]{".odp", mimetype};
                default -> null;
            };
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 使用 Tika 检测文件 MIME 类型
     */
    private String detectByTika(File file) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        metadata.set(org.apache.tika.metadata.TikaCoreProperties.RESOURCE_NAME_KEY, file.getName());
        try (InputStream is = new FileInputStream(file)) {
            parser.getDetector().detect(is, metadata);
            return metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE);
        }
    }

    /**
     * 使用 Tika Parser 提取文档元数据
     */
    private Map<String, String> extractMetadata(File file) {
        Map<String, String> metadata = new HashMap<>();
        try {
            AutoDetectParser parser = new AutoDetectParser();
            ContentHandler handler = new BodyContentHandler(-1);
            org.apache.tika.metadata.Metadata tikaMetadata = new org.apache.tika.metadata.Metadata();
            tikaMetadata.set(org.apache.tika.metadata.TikaCoreProperties.RESOURCE_NAME_KEY, file.getName());
            ParseContext context = new ParseContext();
            try (InputStream is = new FileInputStream(file)) {
                parser.parse(is, handler, tikaMetadata, context);
            }

            String title = tikaMetadata.get(org.apache.tika.metadata.TikaCoreProperties.TITLE);
            if (title != null) metadata.put("title", title);

            String author = tikaMetadata.get(org.apache.tika.metadata.TikaCoreProperties.CREATOR);
            if (author != null) metadata.put("author", author);

            String pageCount = tikaMetadata.get("xmpTPg:NPages");
            if (pageCount != null) metadata.put("pageCount", pageCount);

            String created = tikaMetadata.get(org.apache.tika.metadata.TikaCoreProperties.CREATED);
            if (created != null) metadata.put("createdDate", created);
        } catch (Exception e) {
            log.debug("Tika 文档元数据提取失败: {}", file.getName(), e);
        }
        return metadata.isEmpty() ? null : metadata;
    }
}
```

- [ ] **Step 2: 编译验证**

使用 MCP `build_project` 编译，确认无错误。

- [ ] **Step 3: Commit**

```bash
git add sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/provider/DocumentCheckProvider.java
git commit -m "feat(data-manager): add DocumentCheckProvider for office/pdf/odf detection"
```

---

## Task 8: 新增 ArchiveCheckProvider

**Files:**
- Create: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/provider/ArchiveCheckProvider.java`

- [ ] **Step 1: 创建 ArchiveCheckProvider**

```java
package com.sfc.dm.service.identify.provider;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import com.sfc.dm.service.identify.FileTypeCheckProvider;
import com.sfc.dm.service.identify.util.MagicBytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 压缩包文件类型识别提供者，支持 zip/rar/7z/tar/gz/bz2/xz 格式
 */
@Slf4j
public class ArchiveCheckProvider implements FileTypeCheckProvider {
    private static final String ID = "archiveCheckProvider";
    private static final String TYPE_NAME = "压缩包";
    private static final String TYPE_ID = "archive";
    private static final int PRIORITY = 50;
    private static final int MAX_FILE_LIST_SIZE = 5;

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] RAR_MAGIC = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07};
    private static final byte[] RAR5_MAGIC = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00};
    private static final byte[] SEVENZ_MAGIC = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
    private static final byte[] TAR_MAGIC = {0x75, 0x73, 0x74, 0x61, 0x72}; // "ustar" at offset 257
    private static final byte[] GZ_MAGIC = {0x1F, (byte) 0x8B};
    private static final byte[] BZ2_MAGIC = {0x42, 0x5A, 0x68}; // "BZh"
    private static final byte[] XZ_MAGIC = {(byte) 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00};

    private static final List<String> EXTENSIONS = List.of(
            ".zip", ".rar", ".7z", ".tar", ".gz", ".bz2", ".xz"
    );

    @Override
    public String getId() { return ID; }

    @Override
    public String getTypeName() { return TYPE_NAME; }

    @Override
    public String getTypeId() { return TYPE_ID; }

    @Override
    public List<String> getSupportedFileExtensions() { return EXTENSIONS; }

    @Override
    public int getPriority() { return PRIORITY; }

    @Override
    public List<FileMetadataDefine> getMetadataDefines() {
        return List.of(
                new FileMetadataDefine("文件数量", "fileCount", "压缩包内文件总数", "span"),
                new FileMetadataDefine("压缩方式", "compressionMethod", "压缩算法类型", "span"),
                new FileMetadataDefine("文件列表", "fileList", "前5个文件名（JSON数组）", "span")
        );
    }

    @Override
    public FileTypeCheckResultDetail checkFile(File file, boolean extraMetadata) {
        try {
            byte[] header = MagicBytesUtils.readHeader(file, 8);
            if (header.length < 2) {
                return null;
            }

            String ext = null;
            String mimeType = null;
            String compressionMethod = null;

            if (MagicBytesUtils.matchMagic(header, ZIP_MAGIC)) {
                ext = ".zip";
                mimeType = "application/zip";
                compressionMethod = "DEFLATE";
            } else if (MagicBytesUtils.matchMagic(header, RAR5_MAGIC)) {
                ext = ".rar";
                mimeType = "application/x-rar-compressed";
                compressionMethod = "RAR5";
            } else if (MagicBytesUtils.matchMagic(header, RAR_MAGIC)) {
                ext = ".rar";
                mimeType = "application/x-rar-compressed";
                compressionMethod = "RAR4";
            } else if (MagicBytesUtils.matchMagic(header, SEVENZ_MAGIC)) {
                ext = ".7z";
                mimeType = "application/x-7z-compressed";
                compressionMethod = "LZMA2";
            } else if (MagicBytesUtils.matchMagic(header, GZ_MAGIC)) {
                ext = ".gz";
                mimeType = "application/gzip";
                compressionMethod = "GZIP";
            } else if (MagicBytesUtils.matchMagic(header, BZ2_MAGIC)) {
                ext = ".bz2";
                mimeType = "application/x-bzip2";
                compressionMethod = "BZIP2";
            } else if (MagicBytesUtils.matchMagic(header, XZ_MAGIC)) {
                ext = ".xz";
                mimeType = "application/x-xz";
                compressionMethod = "LZMA";
            } else {
                // tar 的 magic 在偏移 257 处
                byte[] tarHeader = MagicBytesUtils.readAt(file, 257, 5);
                if (MagicBytesUtils.matchMagic(tarHeader, TAR_MAGIC)) {
                    ext = ".tar";
                    mimeType = "application/x-tar";
                    compressionMethod = "TAR";
                }
            }

            if (ext == null) {
                return null;
            }

            FileTypeCheckResultDetail detail = new FileTypeCheckResultDetail();
            detail.setExtension(ext);
            detail.setMimetype(mimeType);

            if (extraMetadata) {
                Map<String, String> metadata = new HashMap<>();
                metadata.put("compressionMethod", compressionMethod);
                if (".gz".equals(ext)) {
                    metadata.put("fileCount", "1");
                } else {
                    try {
                        Map<String, String> archiveMeta = extractArchiveMetadata(file, ext);
                        metadata.putAll(archiveMeta);
                    } catch (Exception e) {
                        log.debug("压缩包元数据提取失败: {}", file.getName(), e);
                    }
                }
                detail.setMetadata(metadata);
            }

            return detail;
        } catch (Exception e) {
            log.debug("压缩包检测失败: {}", file.getName(), e);
            return null;
        }
    }

    /**
     * 提取压缩包元数据（文件数量和文件列表）
     */
    private Map<String, String> extractArchiveMetadata(File file, String ext) throws Exception {
        Map<String, String> metadata = new HashMap<>();
        List<String> fileList = new ArrayList<>();
        int fileCount = 0;

        if (".zip".equals(ext)) {
            try (ZipFile zipFile = new ZipFile(file)) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.isDirectory()) {
                        fileCount++;
                        if (fileList.size() < MAX_FILE_LIST_SIZE) {
                            fileList.add(entry.getName());
                        }
                    }
                }
            }
        } else {
            // 使用 commons-compress 处理其他格式
            try (InputStream fis = new FileInputStream(file);
                 InputStream bis = new BufferedInputStream(fis)) {

                InputStream decompressed = bis;
                // 对于压缩的 tar，先解压
                if (".gz".equals(ext) || ".bz2".equals(ext) || ".xz".equals(ext)) {
                    CompressorInputStream cis = new CompressorStreamFactory()
                            .createCompressorInputStream(bis);
                    decompressed = cis;
                }

                try (ArchiveInputStream ais = new ArchiveStreamFactory()
                        .createArchiveInputStream(decompressed)) {
                    ArchiveEntry entry;
                    while ((entry = ais.getNextEntry()) != null) {
                        if (!entry.isDirectory()) {
                            fileCount++;
                            if (fileList.size() < MAX_FILE_LIST_SIZE) {
                                fileList.add(entry.getName());
                            }
                        }
                    }
                }
            }
        }

        metadata.put("fileCount", String.valueOf(fileCount));
        metadata.put("fileList", fileList.toString());
        return metadata;
    }
}
```

- [ ] **Step 2: 编译验证**

使用 MCP `build_project` 编译，确认无错误。

- [ ] **Step 3: Commit**

```bash
git add sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/provider/ArchiveCheckProvider.java
git commit -m "feat(data-manager): add ArchiveCheckProvider for zip/rar/7z/tar/gz/bz2/xz detection"
```

---

## Task 9: 新增 IsoCheckProvider

**Files:**
- Create: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/provider/IsoCheckProvider.java`

- [ ] **Step 1: 创建 IsoCheckProvider**

```java
package com.sfc.dm.service.identify.provider;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import com.sfc.dm.service.identify.FileTypeCheckProvider;
import com.sfc.dm.service.identify.util.MagicBytesUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * ISO 镜像文件类型识别提供者，通过 ISO9660 签名检测并提取卷标和文件列表
 */
@Slf4j
public class IsoCheckProvider implements FileTypeCheckProvider {
    private static final String ID = "isoCheckProvider";
    private static final String TYPE_NAME = "光盘镜像";
    private static final String TYPE_ID = "disk-image";
    private static final int PRIORITY = 30;
    private static final int MAX_FILE_LIST_SIZE = 5;

    /** ISO9660 Primary Volume Descriptor 签名 "CD001" */
    private static final byte[] ISO9660_SIGNATURE = {0x43, 0x44, 0x30, 0x30, 0x31}; // "CD001"

    /** Primary Volume Descriptor 起始偏移 */
    private static final long PVD_OFFSET = 0x8000L;
    /** 签名在 PVD 内的偏移 */
    private static final int SIGNATURE_OFFSET_IN_PVD = 1;
    /** 卷标识在 PVD 内的偏移 */
    private static final int VOLUME_LABEL_OFFSET_IN_PVD = 40;
    /** 卷标识长度 */
    private static final int VOLUME_LABEL_LENGTH = 32;
    /** 根目录记录在 PVD 内的偏移 */
    private static final int ROOT_DIR_RECORD_OFFSET = 156;
    /** 目录记录长度 */
    private static final int DIR_RECORD_LENGTH = 34;

    private static final List<String> EXTENSIONS = List.of(".iso");

    @Override
    public String getId() { return ID; }

    @Override
    public String getTypeName() { return TYPE_NAME; }

    @Override
    public String getTypeId() { return TYPE_ID; }

    @Override
    public List<String> getSupportedFileExtensions() { return EXTENSIONS; }

    @Override
    public int getPriority() { return PRIORITY; }

    @Override
    public List<FileMetadataDefine> getMetadataDefines() {
        return List.of(
                new FileMetadataDefine("卷标", "volumeLabel", "ISO 卷标名称", "span"),
                new FileMetadataDefine("文件大小", "fileSize", "ISO 文件大小", "span"),
                new FileMetadataDefine("文件列表", "fileList", "前5个文件名（JSON数组）", "span")
        );
    }

    @Override
    public FileTypeCheckResultDetail checkFile(File file, boolean extraMetadata) {
        try {
            // 读取 PVD 起始处的签名
            byte[] pvdHeader = MagicBytesUtils.readAt(file, PVD_OFFSET + SIGNATURE_OFFSET_IN_PVD, 5);
            if (!MagicBytesUtils.matchMagic(pvdHeader, ISO9660_SIGNATURE)) {
                return null;
            }

            FileTypeCheckResultDetail detail = new FileTypeCheckResultDetail();
            detail.setExtension(".iso");
            detail.setMimetype("application/x-iso9660-image");

            if (extraMetadata) {
                Map<String, String> metadata = extractMetadata(file);
                if (metadata != null && !metadata.isEmpty()) {
                    detail.setMetadata(metadata);
                }
            }

            return detail;
        } catch (Exception e) {
            log.debug("ISO 检测失败: {}", file.getName(), e);
            return null;
        }
    }

    /**
     * 提取 ISO 元数据：卷标和文件列表
     */
    private Map<String, String> extractMetadata(File file) throws IOException {
        Map<String, String> metadata = new HashMap<>();

        // 读取卷标
        byte[] labelBytes = MagicBytesUtils.readAt(file, PVD_OFFSET + VOLUME_LABEL_OFFSET_IN_PVD, VOLUME_LABEL_LENGTH);
        String volumeLabel = new String(labelBytes, StandardCharsets.US_ASCII).trim();
        if (!volumeLabel.isEmpty()) {
            metadata.put("volumeLabel", volumeLabel);
        }

        metadata.put("fileSize", String.valueOf(file.length()));

        // 读取根目录记录
        byte[] rootDirRecord = MagicBytesUtils.readAt(file, PVD_OFFSET + ROOT_DIR_RECORD_OFFSET, DIR_RECORD_LENGTH);
        // 根目录记录结构：[1字节长度][1字节扩展属性长度][8字节LBA位置][8字节大小][7字节日期][1字节标志][...]
        int rootLba = ByteBuffer.wrap(rootDirRecord, 2, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        int rootSize = ByteBuffer.wrap(rootDirRecord, 10, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();

        // 读取根目录内容并解析文件列表
        List<String> fileList = new ArrayList<>();
        byte[] rootDirContent = MagicBytesUtils.readAt(file, (long) rootLba * 2048, Math.min(rootSize, 20480));
        parseDirectoryRecords(rootDirContent, fileList);

        metadata.put("fileList", fileList.toString());
        return metadata;
    }

    /**
     * 解析 ISO9660 目录记录，提取文件名
     */
    private void parseDirectoryRecords(byte[] data, List<String> fileList) {
        int offset = 0;
        while (offset < data.length - 33) {
            int recordLen = data[offset] & 0xFF;
            if (recordLen == 0) {
                // 跳到下一个扇区
                offset = ((offset / 2048) + 1) * 2048;
                if (offset >= data.length) break;
                continue;
            }

            int nameLen = data[offset + 32] & 0xFF;
            if (nameLen > 0 && nameLen < data.length - offset - 33) {
                byte[] nameBytes = new byte[nameLen];
                System.arraycopy(data, offset + 33, nameBytes, 0, nameLen);
                String name = new String(nameBytes, StandardCharsets.US_ASCII);

                // 跳过 "." 和 ".." 目录项
                if (!".".equals(name) && !"..".equals(name) && !"\0".equals(name)) {
                    // 移除版本号后缀 ";1"
                    int semicolonIdx = name.indexOf(';');
                    if (semicolonIdx > 0) {
                        name = name.substring(0, semicolonIdx);
                    }
                    // 移除尾部的点
                    if (name.endsWith(".")) {
                        name = name.substring(0, name.length() - 1);
                    }
                    if (!name.isEmpty() && fileList.size() < MAX_FILE_LIST_SIZE) {
                        fileList.add(name);
                    }
                }
            }

            offset += recordLen;
            if (fileList.size() >= MAX_FILE_LIST_SIZE) break;
        }
    }
}
```

- [ ] **Step 2: 编译验证**

使用 MCP `build_project` 编译，确认无错误。

- [ ] **Step 3: Commit**

```bash
git add sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/provider/IsoCheckProvider.java
git commit -m "feat(data-manager): add IsoCheckProvider for ISO9660 image detection"
```

---

## Task 10: 新增 PlainTextCheckProvider

**Files:**
- Create: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/provider/PlainTextCheckProvider.java`

- [ ] **Step 1: 创建 PlainTextCheckProvider**

```java
package com.sfc.dm.service.identify.provider;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import com.sfc.dm.service.identify.FileTypeCheckProvider;
import com.sfc.dm.service.identify.util.EncodingDetector;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.*;

/**
 * 纯文本文件类型识别提供者，覆盖常见文本格式和代码文件
 */
@Slf4j
public class PlainTextCheckProvider implements FileTypeCheckProvider {
    private static final String ID = "plainTextCheckProvider";
    private static final String TYPE_NAME = "纯文本";
    private static final String TYPE_ID = "text";
    private static final int PRIORITY = 100;

    private static final List<String> EXTENSIONS = List.of(
            // 文本
            ".txt", ".csv", ".log", ".md", ".rst", ".org",
            // 数据
            ".json", ".xml", ".yaml", ".yml", ".toml", ".ini", ".cfg", ".conf",
            ".properties", ".env",
            // Web
            ".html", ".htm", ".css", ".scss", ".less", ".svg",
            // 代码
            ".java", ".py", ".js", ".ts", ".jsx", ".tsx", ".go", ".rs", ".rb",
            ".php", ".c", ".cpp", ".h", ".hpp", ".cs", ".swift", ".kt", ".scala",
            ".lua", ".r", ".m", ".pl", ".pm", ".sh", ".bash", ".zsh", ".fish",
            ".bat", ".cmd", ".ps1", ".sql", ".gradle", ".groovy", ".cmake",
            // 配置
            ".gitignore", ".dockerignore", ".editorconfig", ".eslintrc", ".prettierrc",
            // 其他
            ".tex", ".sty", ".cls", ".bib", ".vue", ".svelte", ".dart", ".zig",
            ".nim", ".ex", ".exs", ".erl", ".hs", ".ml", ".fs", ".fsx", ".clj",
            ".lisp", ".el", ".asm", ".s", ".S"
    );

    @Override
    public String getId() { return ID; }

    @Override
    public String getTypeName() { return TYPE_NAME; }

    @Override
    public String getTypeId() { return TYPE_ID; }

    @Override
    public List<String> getSupportedFileExtensions() { return EXTENSIONS; }

    @Override
    public int getPriority() { return PRIORITY; }

    @Override
    public List<FileMetadataDefine> getMetadataDefines() {
        return List.of(
                new FileMetadataDefine("编码", "encoding", "文件编码格式", "span"),
                new FileMetadataDefine("行数", "lineCount", "文件行数", "span"),
                new FileMetadataDefine("文件大小", "fileSize", "文件大小（字节）", "span")
        );
    }

    @Override
    public FileTypeCheckResultDetail checkFile(File file, boolean extraMetadata) {
        String ext = getExtensionWithDot(file.getName());
        if (ext == null || !EXTENSIONS.contains(ext)) {
            return null;
        }

        FileTypeCheckResultDetail detail = new FileTypeCheckResultDetail();
        detail.setExtension(ext);
        detail.setMimetype(getMimeType(ext));

        if (extraMetadata) {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("fileSize", String.valueOf(file.length()));

            try {
                String encoding = EncodingDetector.detect(file);
                metadata.put("encoding", encoding);
            } catch (Exception e) {
                log.debug("编码检测失败: {}", file.getName(), e);
            }

            try {
                int lineCount = countLines(file);
                metadata.put("lineCount", String.valueOf(lineCount));
            } catch (Exception e) {
                log.debug("行数统计失败: {}", file.getName(), e);
            }

            detail.setMetadata(metadata);
        }

        return detail;
    }

    /**
     * 获取文件扩展名（包含点号），兼容无扩展名的配置文件
     */
    private String getExtensionWithDot(String fileName) {
        String lower = fileName.toLowerCase();
        // 特殊处理以点开头的隐藏文件（如 .gitignore）
        if (lower.startsWith(".")) {
            return lower;
        }
        int dotIndex = lower.lastIndexOf('.');
        if (dotIndex > 0) {
            return lower.substring(dotIndex);
        }
        return null;
    }

    /**
     * 根据扩展名获取 MIME 类型
     */
    private String getMimeType(String ext) {
        return switch (ext) {
            case ".json" -> "application/json";
            case ".xml", ".svg" -> "application/xml";
            case ".html", ".htm" -> "text/html";
            case ".css", ".scss", ".less" -> "text/css";
            case ".js", ".jsx" -> "application/javascript";
            case ".ts", ".tsx" -> "application/typescript";
            case ".csv" -> "text/csv";
            case ".md" -> "text/markdown";
            default -> "text/plain";
        };
    }

    /**
     * 统计文件行数
     */
    private int countLines(File file) throws IOException {
        int lines = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (reader.readLine() != null) {
                lines++;
            }
        }
        return lines;
    }
}
```

- [ ] **Step 2: 编译验证**

使用 MCP `build_project` 编译，确认无错误。

- [ ] **Step 3: Commit**

```bash
git add sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/provider/PlainTextCheckProvider.java
git commit -m "feat(data-manager): add PlainTextCheckProvider for text/code file detection"
```

---

## Task 11: 新增 ExeCheckProvider

**Files:**
- Create: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/provider/ExeCheckProvider.java`

- [ ] **Step 1: 创建 ExeCheckProvider**

```java
package com.sfc.dm.service.identify.provider;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import com.sfc.dm.service.identify.FileTypeCheckProvider;
import com.sfc.dm.service.identify.util.MagicBytesUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Windows 可执行文件类型识别提供者，通过 PE 头解析检测 exe/dll/sys 文件
 */
@Slf4j
public class ExeCheckProvider implements FileTypeCheckProvider {
    private static final String ID = "exeCheckProvider";
    private static final String TYPE_NAME = "可执行文件";
    private static final String TYPE_ID = "executable";
    private static final int PRIORITY = 40;

    /** MZ 签名 */
    private static final byte[] MZ_MAGIC = {0x4D, 0x5A};
    /** PE 签名 */
    private static final byte[] PE_SIGNATURE = {0x50, 0x45, 0x00, 0x00}; // "PE\0\0"

    private static final List<String> EXTENSIONS = List.of(".exe", ".dll", ".sys");

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public String getId() { return ID; }

    @Override
    public String getTypeName() { return TYPE_NAME; }

    @Override
    public String getTypeId() { return TYPE_ID; }

    @Override
    public List<String> getSupportedFileExtensions() { return EXTENSIONS; }

    @Override
    public int getPriority() { return PRIORITY; }

    @Override
    public List<FileMetadataDefine> getMetadataDefines() {
        return List.of(
                new FileMetadataDefine("架构", "architecture", "目标架构（x86/x64）", "span"),
                new FileMetadataDefine("子系统", "subsystem", "子系统类型（CONSOLE/WINDOWS/NATIVE）", "span"),
                new FileMetadataDefine("链接器版本", "linkerVersion", "链接器版本号", "span"),
                new FileMetadataDefine("编译时间", "compileTime", "PE 编译时间戳", "span"),
                new FileMetadataDefine("是否DLL", "isDll", "是否为动态链接库", "span")
        );
    }

    @Override
    public FileTypeCheckResultDetail checkFile(File file, boolean extraMetadata) {
        try {
            byte[] header = MagicBytesUtils.readHeader(file, 2);
            if (!MagicBytesUtils.matchMagic(header, MZ_MAGIC)) {
                return null;
            }

            FileTypeCheckResultDetail detail = new FileTypeCheckResultDetail();
            String ext = getExtensionFromFileName(file.getName());
            detail.setExtension(ext);
            detail.setMimetype(getMimeType(ext));

            if (extraMetadata) {
                Map<String, String> metadata = extractPeMetadata(file);
                if (metadata != null && !metadata.isEmpty()) {
                    detail.setMetadata(metadata);
                }
            }

            return detail;
        } catch (Exception e) {
            log.debug("EXE 检测失败: {}", file.getName(), e);
            return null;
        }
    }

    private String getExtensionFromFileName(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".dll")) return ".dll";
        if (lower.endsWith(".sys")) return ".sys";
        return ".exe";
    }

    private String getMimeType(String ext) {
        return switch (ext) {
            case ".dll" -> "application/x-msdownload";
            case ".sys" -> "application/x-msdownload";
            default -> "application/x-msdownload";
        };
    }

    /**
     * 提取 PE 头元数据
     */
    private Map<String, String> extractPeMetadata(File file) throws Exception {
        Map<String, String> metadata = new HashMap<>();

        // 读取 PE 头偏移（位于 MZ 头偏移 0x3C 处，4 字节小端序）
        byte[] peOffsetBytes = MagicBytesUtils.readAt(file, 0x3C, 4);
        int peOffset = ByteBuffer.wrap(peOffsetBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

        // 验证 PE 签名
        byte[] peSignature = MagicBytesUtils.readAt(file, peOffset, 4);
        if (!MagicBytesUtils.matchMagic(peSignature, PE_SIGNATURE)) {
            return metadata;
        }

        // COFF 头起始位置
        int coffOffset = peOffset + 4;

        // 读取 COFF 头
        byte[] coffHeader = MagicBytesUtils.readAt(file, coffOffset, 20);
        ByteBuffer coffBuf = ByteBuffer.wrap(coffHeader).order(ByteOrder.LITTLE_ENDIAN);

        int machine = coffBuf.getShort() & 0xFFFF;
        int numberOfSections = coffBuf.getShort() & 0xFFFF;
        long timeDateStamp = coffBuf.getInt() & 0xFFFFFFFFL;
        // 跳过 PointerToSymbolTable (4) 和 NumberOfSymbols (4)
        coffBuf.position(16);
        int sizeOfOptionalHeader = coffBuf.getShort() & 0xFFFF;
        int characteristics = coffBuf.getShort() & 0xFFFF;

        // 架构判断
        metadata.put("architecture", switch (machine) {
            case 0x014C -> "x86";
            case 0x8664 -> "x64";
            case 0xAA64 -> "ARM64";
            default -> "Unknown (0x" + Integer.toHexString(machine) + ")";
        });

        // 编译时间
        metadata.put("compileTime", TIME_FORMATTER.format(Instant.ofEpochSecond(timeDateStamp)));

        // 是否为 DLL
        metadata.put("isDll", String.valueOf((characteristics & 0x2000) != 0));

        // 读取 Optional Header（如果存在）
        if (sizeOfOptionalHeader > 0) {
            int optOffset = coffOffset + 20;
            byte[] optHeader = MagicBytesUtils.readAt(file, optOffset, Math.min(sizeOfOptionalHeader, 112));
            ByteBuffer optBuf = ByteBuffer.wrap(optHeader).order(ByteOrder.LITTLE_ENDIAN);

            int magic = optBuf.getShort() & 0xFFFF;
            // PE32 = 0x10B, PE32+ = 0x20B
            boolean isPE32Plus = (magic == 0x20B);

            if (optHeader.length >= 16) {
                // 链接器版本在偏移 2（MajorLinkerVersion）和 3（MinorLinkerVersion）
                int majorLinker = optHeader[2] & 0xFF;
                int minorLinker = optHeader[3] & 0xFF;
                metadata.put("linkerVersion", majorLinker + "." + minorLinker);
            }

            // 子系统在 PE32 偏移 68，PE32+ 偏移 68
            if (optHeader.length >= 70) {
                int subsystem = optBuf.getShort(68) & 0xFFFF;
                metadata.put("subsystem", switch (subsystem) {
                    case 1 -> "NATIVE";
                    case 2 -> "WINDOWS_GUI";
                    case 3 -> "WINDOWS_CUI";
                    case 5 -> "OS2_CUI";
                    case 7 -> "POSIX_CUI";
                    case 9 -> "WINDOWS_CE_GUI";
                    case 10 -> "EFI_APPLICATION";
                    case 11 -> "EFI_BOOT_SERVICE_DRIVER";
                    case 12 -> "EFI_RUNTIME_DRIVER";
                    case 13 -> "EFI_ROM";
                    case 14 -> "XBOX";
                    case 16 -> "WINDOWS_BOOT_APPLICATION";
                    default -> "Unknown (" + subsystem + ")";
                });
            }
        }

        return metadata;
    }
}
```

- [ ] **Step 2: 编译验证**

使用 MCP `build_project` 编译，确认无错误。

- [ ] **Step 3: Commit**

```bash
git add sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/provider/ExeCheckProvider.java
git commit -m "feat(data-manager): add ExeCheckProvider for PE executable detection"
```

---

## Task 12: 新增 MsiCheckProvider

**Files:**
- Create: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/provider/MsiCheckProvider.java`

- [ ] **Step 1: 创建 MsiCheckProvider**

```java
package com.sfc.dm.service.identify.provider;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import com.sfc.dm.service.identify.FileTypeCheckProvider;
import com.sfc.dm.service.identify.util.MagicBytesUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * MSI 安装包类型识别提供者，通过 OLE2 复合文档格式检测 MSI 文件并提取属性
 */
@Slf4j
public class MsiCheckProvider implements FileTypeCheckProvider {
    private static final String ID = "msiCheckProvider";
    private static final String TYPE_NAME = "安装包";
    private static final String TYPE_ID = "installer";
    private static final int PRIORITY = 20;

    /** OLE2 复合文档签名 */
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    /** MSI 的 CLSID: {000C1084-0000-0000-C000-000000000046} */
    private static final byte[] MSI_CLSID = {
            (byte) 0x84, 0x10, 0x0C, 0x00, 0x00, 0x00, 0x00, 0x00,
            (byte) 0xC0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x46
    };

    private static final List<String> EXTENSIONS = List.of(".msi");

    @Override
    public String getId() { return ID; }

    @Override
    public String getTypeName() { return TYPE_NAME; }

    @Override
    public String getTypeId() { return TYPE_ID; }

    @Override
    public List<String> getSupportedFileExtensions() { return EXTENSIONS; }

    @Override
    public int getPriority() { return PRIORITY; }

    @Override
    public List<FileMetadataDefine> getMetadataDefines() {
        return List.of(
                new FileMetadataDefine("产品名称", "productName", "MSI 产品名称", "span"),
                new FileMetadataDefine("产品版本", "productVersion", "MSI 产品版本", "span"),
                new FileMetadataDefine("制造商", "manufacturer", "MSI 制造商", "span"),
                new FileMetadataDefine("架构", "architecture", "目标平台架构", "span")
        );
    }

    @Override
    public FileTypeCheckResultDetail checkFile(File file, boolean extraMetadata) {
        try {
            byte[] header = MagicBytesUtils.readHeader(file, 8);
            if (!MagicBytesUtils.matchMagic(header, OLE2_MAGIC)) {
                return null;
            }

            // 检查 CLSID 是否为 MSI（位于 OLE2 头偏移 0x50 处，16 字节）
            byte[] clsid = MagicBytesUtils.readAt(file, 0x50, 16);
            if (!Arrays.equals(clsid, MSI_CLSID)) {
                return null;
            }

            FileTypeCheckResultDetail detail = new FileTypeCheckResultDetail();
            detail.setExtension(".msi");
            detail.setMimetype("application/x-msi");

            if (extraMetadata) {
                Map<String, String> metadata = extractMsiMetadata(file);
                if (metadata != null && !metadata.isEmpty()) {
                    detail.setMetadata(metadata);
                }
            }

            return detail;
        } catch (Exception e) {
            log.debug("MSI 检测失败: {}", file.getName(), e);
            return null;
        }
    }

    /**
     * 提取 MSI 元数据（简化版，通过读取 Property 流）
     * 完整的 MSI 解析非常复杂，这里仅提取基本属性
     */
    private Map<String, String> extractMsiMetadata(File file) {
        Map<String, String> metadata = new HashMap<>();

        try {
            // 读取 OLE2 头中的扇区大小
            byte[] sectorSizeBytes = MagicBytesUtils.readAt(file, 0x1E, 2);
            int sectorSizePow = ByteBuffer.wrap(sectorSizeBytes).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            int sectorSize = 1 << sectorSizePow;

            // 读取 FAT 扇区位置
            byte[] fatSectorBytes = MagicBytesUtils.readAt(file, 0x44, 4);
            int fatSector = ByteBuffer.wrap(fatSectorBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

            // 读取目录流起始扇区
            byte[] dirSectorBytes = MagicBytesUtils.readAt(file, 0x30, 4);
            int dirSector = ByteBuffer.wrap(dirSectorBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

            // 读取目录扇区内容
            long dirOffset = (long) (dirSector + 1) * sectorSize;
            // 确保偏移合理
            if (dirOffset < 0 || dirOffset > file.length() - 128) {
                return metadata;
            }
            byte[] dirData = MagicBytesUtils.readAt(file, dirOffset, Math.min(sectorSize, 4096));

            // 在目录中查找 "Property" 流
            String[] propertyNames = {"ProductVersion", "Manufacturer", "ProductCode"};
            // 简化处理：从文件中搜索 Property 表字符串
            byte[] fileSample = MagicBytesUtils.readAt(file, 0, (int) Math.min(file.length(), 65536));
            String sampleStr = new String(fileSample, StandardCharsets.ISO_8859_1);

            // 尝试提取 ProductName、ProductVersion、Manufacturer
            extractProperty(metadata, sampleStr, "ProductName");
            extractProperty(metadata, sampleStr, "ProductVersion");
            extractProperty(metadata, sampleStr, "Manufacturer");

            // 架构推断：检查文件名或内容中的特征
            if (sampleStr.contains("x64") || sampleStr.contains("AMD64") || sampleStr.contains("amd64")) {
                metadata.put("architecture", "x64");
            } else if (sampleStr.contains("ARM64") || sampleStr.contains("arm64")) {
                metadata.put("architecture", "ARM64");
            } else {
                metadata.put("architecture", "x86");
            }
        } catch (Exception e) {
            log.debug("MSI 元数据提取失败: {}", file.getName(), e);
        }

        return metadata.isEmpty() ? null : metadata;
    }

    /**
     * 从 MSI 文件内容中提取属性值（简化方法）
     * MSI Property 表中的属性以 Unicode 字符串对形式存储
     */
    private void extractProperty(Map<String, String> metadata, String content, String propertyName) {
        // MSI 属性存储为 Unicode 字符串，这里尝试从可见字符串中提取
        // 这是一个简化的实现，完整解析需要解析 MSI 数据库表结构
        int idx = content.indexOf(propertyName);
        if (idx > 0 && idx < content.length() - propertyName.length() - 50) {
            // 跳过属性名后面的二进制数据，尝试找到可读的值
            String after = content.substring(idx + propertyName.length());
            StringBuilder value = new StringBuilder();
            for (int i = 4; i < Math.min(after.length(), 100); i++) {
                char c = after.charAt(i);
                if (c >= 32 && c < 127) {
                    value.append(c);
                } else if (value.length() > 0) {
                    break;
                }
            }
            if (value.length() > 0) {
                String key = switch (propertyName) {
                    case "ProductName" -> "productName";
                    case "ProductVersion" -> "productVersion";
                    case "Manufacturer" -> "manufacturer";
                    default -> propertyName;
                };
                metadata.put(key, value.toString().trim());
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

使用 MCP `build_project` 编译，确认无错误。

- [ ] **Step 3: Commit**

```bash
git add sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/service/identify/provider/MsiCheckProvider.java
git commit -m "feat(data-manager): add MsiCheckProvider for MSI installer detection"
```

---

## Task 13: 更新 DataManagerAutoConfiguration 注册新 Provider

**Files:**
- Modify: `sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/config/DataManagerAutoConfiguration.java`

- [ ] **Step 1: 新增 CommonProviderConfiguration 内部类和 import**

在 `DataManagerAutoConfiguration` 中添加新 Provider 的 import：

```java
import com.sfc.dm.service.identify.provider.DocumentCheckProvider;
import com.sfc.dm.service.identify.provider.ArchiveCheckProvider;
import com.sfc.dm.service.identify.provider.IsoCheckProvider;
import com.sfc.dm.service.identify.provider.PlainTextCheckProvider;
import com.sfc.dm.service.identify.provider.ExeCheckProvider;
import com.sfc.dm.service.identify.provider.MsiCheckProvider;
```

在类末尾（`FFMpegProviderConfiguration` 之后）添加：

```java
/**
 * 通用文件类型识别提供者配置（不依赖 FFMpeg）
 */
@Configuration
public static class CommonProviderConfiguration {
    /**
     * 注册文档文件类型识别提供者
     */
    @Bean
    public DocumentCheckProvider documentCheckProvider() {
        return new DocumentCheckProvider();
    }

    /**
     * 注册压缩包文件类型识别提供者
     */
    @Bean
    public ArchiveCheckProvider archiveCheckProvider() {
        return new ArchiveCheckProvider();
    }

    /**
     * 注册 ISO 镜像文件类型识别提供者
     */
    @Bean
    public IsoCheckProvider isoCheckProvider() {
        return new IsoCheckProvider();
    }

    /**
     * 注册纯文本文件类型识别提供者
     */
    @Bean
    public PlainTextCheckProvider plainTextCheckProvider() {
        return new PlainTextCheckProvider();
    }

    /**
     * 注册可执行文件类型识别提供者
     */
    @Bean
    public ExeCheckProvider exeCheckProvider() {
        return new ExeCheckProvider();
    }

    /**
     * 注册 MSI 安装包文件类型识别提供者
     */
    @Bean
    public MsiCheckProvider msiCheckProvider() {
        return new MsiCheckProvider();
    }
}
```

- [ ] **Step 2: 编译验证**

使用 MCP `build_project` 编译整个项目，确认所有新 Provider 正确注册。

- [ ] **Step 3: Commit**

```bash
git add sfc-ext/sfc-ext-data-manager/src/main/java/com/sfc/dm/config/DataManagerAutoConfiguration.java
git commit -m "feat(data-manager): register 6 new file type providers in auto-configuration"
```

---

## Task 14: 最终编译验证与文件检查

- [ ] **Step 1: 全量编译验证**

使用 MCP `build_project` 对整个项目编译，确认无错误。

- [ ] **Step 2: 检查新增文件的问题**

使用 MCP `get_file_problems` 检查以下新增文件：
- `DocumentCheckProvider.java`
- `ArchiveCheckProvider.java`
- `IsoCheckProvider.java`
- `PlainTextCheckProvider.java`
- `ExeCheckProvider.java`
- `MsiCheckProvider.java`
- `MagicBytesUtils.java`
- `EncodingDetector.java`

修复所有警告问题。

- [ ] **Step 3: 最终 Commit（如有修复）**

```bash
git add -A
git commit -m "fix(data-manager): fix warnings in new file type providers"
```
