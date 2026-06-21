package com.sfc.dm.service.identify.provider;

import com.sfc.dm.model.dto.FileTypeInfo;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import com.sfc.dm.service.identify.FileTypeCheckProvider;
import com.sfc.dm.service.identify.metadata.FileMetadataExtractor;
import com.sfc.dm.service.identify.tika.TikaServerManager;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.*;

/**
 * 基于 Tika Server 的统一文件类型识别提供者。
 * <p>
 * 合并了原 DocumentCheckProvider、PlainTextCheckProvider、ExeCheckProvider、
 * MsiCheckProvider、ImageCheckProvider 的功能，所有文件类型检测均由 Tika 完成。
 */
@Slf4j
public class TikaFileTypeProvider implements FileTypeCheckProvider {
    private static final String ID = "tikaFileTypeProvider";
    private static final int PRIORITY = 15;

    // region type constants
    private static final String TYPE_DOCUMENT = "document";
    private static final String TYPE_INSTALLER = "installer";
    private static final String TYPE_EXECUTABLE = "executable";
    private static final String TYPE_IMAGE = "image";
    private static final String TYPE_TEXT = "text";

    // endregion

    /**
     * MIME 类型到 typeId 的精确映射
     */
    private static final Map<String, String> MIME_TO_TYPE = Map.ofEntries(
            // document
            Map.entry("application/pdf", TYPE_DOCUMENT),
            Map.entry("application/msword", TYPE_DOCUMENT),
            Map.entry("application/vnd.ms-excel", TYPE_DOCUMENT),
            Map.entry("application/vnd.ms-powerpoint", TYPE_DOCUMENT),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", TYPE_DOCUMENT),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", TYPE_DOCUMENT),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", TYPE_DOCUMENT),
            Map.entry("application/vnd.oasis.opendocument.text", TYPE_DOCUMENT),
            Map.entry("application/vnd.oasis.opendocument.spreadsheet", TYPE_DOCUMENT),
            Map.entry("application/vnd.oasis.opendocument.presentation", TYPE_DOCUMENT),
            Map.entry("application/rtf", TYPE_DOCUMENT),
            // installer
            Map.entry("application/x-msi", TYPE_INSTALLER),
            Map.entry("application/vnd.ms-msi", TYPE_INSTALLER),
            // executable
            Map.entry("application/x-dosexec", TYPE_EXECUTABLE),
            Map.entry("application/x-executable", TYPE_EXECUTABLE),
            Map.entry("application/x-msdownload", TYPE_EXECUTABLE),
            // text (specific)
            Map.entry("text/html", TYPE_TEXT),
            Map.entry("text/css", TYPE_TEXT),
            Map.entry("text/csv", TYPE_TEXT),
            Map.entry("text/markdown", TYPE_TEXT),
            Map.entry("application/json", TYPE_TEXT),
            Map.entry("application/xml", TYPE_TEXT),
            Map.entry("application/javascript", TYPE_TEXT),
            Map.entry("application/typescript", TYPE_TEXT),
            Map.entry("text/plain", TYPE_TEXT)
    );

    /**
     * 通用 OLE2 MIME 类型，需要通过扩展名消歧
     */
    private static final Map<String, String> OLE2_EXTENSION_DISAMBIGUATION = Map.of(
            ".doc", TYPE_DOCUMENT,
            ".xls", TYPE_DOCUMENT,
            ".ppt", TYPE_DOCUMENT,
            ".msi", TYPE_INSTALLER
    );

    /**
     * MIME 类型到文件扩展名的映射（用于 MIME 决定类型的场景）
     */
    private static final Map<String, String> MIME_TO_EXTENSION = Map.ofEntries(
            Map.entry("application/pdf", ".pdf"),
            Map.entry("application/msword", ".doc"),
            Map.entry("application/vnd.ms-excel", ".xls"),
            Map.entry("application/vnd.ms-powerpoint", ".ppt"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx"),
            Map.entry("application/vnd.oasis.opendocument.text", ".odt"),
            Map.entry("application/vnd.oasis.opendocument.spreadsheet", ".ods"),
            Map.entry("application/vnd.oasis.opendocument.presentation", ".odp"),
            Map.entry("application/rtf", ".rtf"),
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/png", ".png"),
            Map.entry("image/gif", ".gif"),
            Map.entry("image/bmp", ".bmp"),
            Map.entry("image/webp", ".webp"),
            Map.entry("image/tiff", ".tiff"),
            Map.entry("image/heic", ".heic"),
            Map.entry("image/heif", ".heif"),
            Map.entry("image/avif", ".avif"),
            Map.entry("image/jxl", ".jxl"),
            Map.entry("image/apng", ".apng"),
            Map.entry("image/svg+xml", ".svg"),
            Map.entry("image/x-icon", ".ico"),
            Map.entry("text/html", ".html"),
            Map.entry("text/css", ".css"),
            Map.entry("text/csv", ".csv"),
            Map.entry("text/markdown", ".md"),
            Map.entry("application/json", ".json"),
            Map.entry("application/xml", ".xml")
    );

    /**
     * 可执行文件扩展名集合（用于消歧 generic PE MIME）
     */
    private static final Set<String> EXECUTABLE_EXTENSIONS = Set.of(".exe", ".dll", ".sys");

    /**
     * 所有支持的文件扩展名（5 个原始 provider 的并集）
     */
    private static final List<String> EXTENSIONS = List.of(
            // document
            ".docx", ".doc", ".xlsx", ".xls", ".pptx", ".ppt", ".pdf",
            ".odt", ".ods", ".odp",
            // installer
            ".msi",
            // executable
            ".exe", ".dll", ".sys",
            // image
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".tiff", ".tif",
            ".heic", ".heif", ".avif", ".jxl", ".apng", ".svg", ".ico",
            // text
            ".txt", ".csv", ".log", ".md", ".rst", ".org",
            ".json", ".xml", ".yaml", ".yml", ".toml", ".ini", ".cfg", ".conf",
            ".properties", ".env",
            ".html", ".htm", ".css", ".scss", ".less",
            ".java", ".py", ".js", ".ts", ".jsx", ".tsx", ".go", ".rs", ".rb",
            ".php", ".c", ".cpp", ".h", ".hpp", ".cs", ".swift", ".kt", ".scala",
            ".lua", ".r", ".m", ".pl", ".pm", ".sh", ".bash", ".zsh", ".fish",
            ".bat", ".cmd", ".ps1", ".sql", ".gradle", ".groovy", ".cmake",
            ".gitignore", ".dockerignore", ".editorconfig", ".eslintrc", ".prettierrc",
            ".tex", ".sty", ".cls", ".bib", ".vue", ".svelte", ".dart", ".zig",
            ".nim", ".ex", ".exs", ".erl", ".hs", ".ml", ".fs", ".fsx", ".clj",
            ".lisp", ".el", ".asm", ".s", ".S"
    );

    private final TikaServerManager tikaServerManager;
    private final Map<String, FileMetadataExtractor> metadataExtractors;

    /**
     * @param tikaServerManager Tika Server 管理器
     * @param metadataExtractors typeId 到元数据提取器的映射
     */
    public TikaFileTypeProvider(TikaServerManager tikaServerManager,
                                Map<String, FileMetadataExtractor> metadataExtractors) {
        this.tikaServerManager = tikaServerManager;
        this.metadataExtractors = metadataExtractors;
    }

    @Override
    public String getId() { return ID; }

    @Override
    public int getPriority() { return PRIORITY; }

    @Override
    public List<String> getSupportedFileExtensions() { return EXTENSIONS; }

    @Override
    public List<FileTypeInfo> getTypeInfoList() {
        return metadataExtractors.entrySet().stream()
                .map(entry -> new FileTypeInfo(
                        entry.getKey(),
                        entry.getValue().getTypeName(),
                        entry.getValue().getMetadataDefines()
                ))
                .toList();
    }

    @Override
    public FileTypeCheckResultDetail checkFile(File file, boolean extraMetadata) {
        try {
            // 空文件视为纯文本
            if (file.length() == 0) {
                return buildTextDetail();
            }

            String mimeType = tikaServerManager.detect(file);
            if (mimeType == null) {
                return null;
            }

            String fileName = file.getName();
            String[] resolved = resolveTypeIdAndExtension(mimeType, fileName);
            if (resolved == null) {
                return null;
            }

            String typeId = resolved[0];
            String extension = resolved[1];

            FileTypeCheckResultDetail detail = new FileTypeCheckResultDetail();
            detail.setExtension(extension);
            detail.setMimetype(mimeType);
            detail.setTypeId(typeId);
            FileMetadataExtractor extractor = metadataExtractors.get(typeId);
            detail.setTypeName(extractor != null ? extractor.getTypeName() : "未知");

            if (extraMetadata) {
                Map<String, String> metadata = extractMetadataByType(typeId, file);
                if (metadata != null && !metadata.isEmpty()) {
                    detail.setMetadata(metadata);
                }
            }

            return detail;
        } catch (Exception e) {
            log.debug("Tika 文件检测失败: {}", file.getName(), e);
            return null;
        }
    }

    /**
     * 根据 Tika 返回的 MIME 类型和文件名解析 typeId 和扩展名
     * @return [typeId, extension]，无法识别时返回 null
     */
    private String[] resolveTypeIdAndExtension(String mimeType, String fileName) {
        String lowerName = fileName.toLowerCase();
        String ext = getExtensionWithDot(lowerName);

        // 1. 精确 MIME 匹配
        String typeId = MIME_TO_TYPE.get(mimeType);
        if (typeId != null) {
            // executable 类型需要扩展名消歧（多个 MIME 映射到同一 typeId）
            if (TYPE_EXECUTABLE.equals(typeId)) {
                String resolvedExt = (ext != null && EXECUTABLE_EXTENSIONS.contains(ext)) ? ext : ".exe";
                return new String[]{typeId, resolvedExt};
            }
            // installer 也用扩展名
            if (TYPE_INSTALLER.equals(typeId)) {
                return new String[]{typeId, ext != null ? ext : ".msi"};
            }
            String resolvedExt = resolveExtension(typeId, mimeType, ext);
            return new String[]{typeId, resolvedExt};
        }

        // 2. 前缀匹配（image/*, text/*）
        if (mimeType.startsWith("image/")) {
            String resolvedExt = resolveExtension(TYPE_IMAGE, mimeType, ext);
            return new String[]{TYPE_IMAGE, resolvedExt};
        }
        if (mimeType.startsWith("text/")) {
            String resolvedExt = resolveExtension(TYPE_TEXT, mimeType, ext);
            return new String[]{TYPE_TEXT, resolvedExt};
        }

        // 3. 通用 OLE2 消歧（application/vnd.ms-office）
        if ("application/vnd.ms-office".equals(mimeType) && ext != null) {
            String resolvedTypeId = OLE2_EXTENSION_DISAMBIGUATION.get(ext);
            if (resolvedTypeId != null) {
                return new String[]{resolvedTypeId, ext};
            }
        }

        return null;
    }

    /**
     * 根据 typeId、MIME 和文件名扩展名确定最终扩展名
     */
    private String resolveExtension(String typeId, String mimeType, String fileNameExt) {
        // 优先使用文件名扩展名（如果与类型兼容）
        if (fileNameExt != null) {
            return fileNameExt;
        }
        // 从 MIME 推导扩展名
        String mimeExt = MIME_TO_EXTENSION.get(mimeType);
        if (mimeExt != null) {
            return mimeExt;
        }
        // 兜底
        return switch (typeId) {
            case TYPE_TEXT -> ".txt";
            case TYPE_IMAGE -> ".bin";
            default -> ".bin";
        };
    }

    private String getExtensionWithDot(String fileName) {
        if (fileName.startsWith(".")) return fileName;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) return fileName.substring(dotIndex);
        return null;
    }

    /**
     * 根据类型分派元数据提取
     */
    private Map<String, String> extractMetadataByType(String typeId, File file) {
        FileMetadataExtractor extractor = metadataExtractors.get(typeId);
        if (extractor == null) return null;
        return extractor.extract(file);
    }

    /**
     * 构建纯文本检测结果（用于空文件）
     */
    private FileTypeCheckResultDetail buildTextDetail() {
        FileTypeCheckResultDetail detail = new FileTypeCheckResultDetail();
        detail.setExtension(".txt");
        detail.setMimetype("text/plain");
        detail.setTypeId(TYPE_TEXT);
        FileMetadataExtractor textExtractor = metadataExtractors.get(TYPE_TEXT);
        detail.setTypeName(textExtractor != null ? textExtractor.getTypeName() : "纯文本");
        return detail;
    }
}
