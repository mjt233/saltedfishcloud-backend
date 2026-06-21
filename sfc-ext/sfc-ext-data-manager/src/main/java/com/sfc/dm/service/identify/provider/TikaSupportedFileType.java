package com.sfc.dm.service.identify.provider;

import lombok.Getter;

import java.util.*;

/**
 * Tika 文件类型识别支持的文件类型定义枚举。
 * <p>
 * 作为 {@link TikaFileTypeProvider} 的唯一数据源，将分散在多个 Map/Set 中的
 * MIME 映射、扩展名映射、类型标识集中到每个枚举常量中，并通过静态初始化块
 * 自动推导出反向查找表（MIME→typeId、MIME→extension、OLE2 消歧等），
 * 消除数据冗余与不一致风险。
 */
@Getter
public enum TikaSupportedFileType {
    /**
     * 文档类型（Office、PDF、RTF 等）。
     */
    DOCUMENT("document",
            List.of(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.ms-excel",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.oasis.opendocument.text",
                    "application/vnd.oasis.opendocument.spreadsheet",
                    "application/vnd.oasis.opendocument.presentation",
                    "application/rtf"
            ),
            List.of(
                    ".docx", ".doc", ".xlsx", ".xls", ".pptx", ".ppt", ".pdf",
                    ".odt", ".ods", ".odp"
            ),
            Map.ofEntries(
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
                    Map.entry("application/rtf", ".rtf")
            ),
            Set.of(".doc", ".xls", ".ppt")
    ),

    /**
     * 安装包类型（MSI）。
     */
    INSTALLER("installer",
            List.of("application/x-msi", "application/vnd.ms-msi"),
            List.of(".msi"),
            Map.ofEntries(
                    Map.entry("application/x-msi", ".msi"),
                    Map.entry("application/vnd.ms-msi", ".msi")
            ),
            Set.of(".msi")
    ),

    /**
     * 可执行文件类型（exe/dll/sys）。
     */
    EXECUTABLE("executable",
            List.of("application/x-dosexec", "application/x-executable", "application/x-msdownload"),
            List.of(".exe", ".dll", ".sys"),
            Map.of(),
            Set.of()
    ),

    /**
     * 图片类型（通过 image/* 前缀匹配，无需精确 MIME 条目）。
     */
    IMAGE("image",
            List.of(),
            List.of(
                    ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".tiff", ".tif",
                    ".heic", ".heif", ".avif", ".jxl", ".apng", ".svg", ".ico"
            ),
            Map.ofEntries(
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
                    Map.entry("image/x-icon", ".ico")
            ),
            Set.of()
    ),

    /**
     * 纯文本 / 代码类型。
     */
    TEXT("text",
            List.of(
                    "text/html",
                    "text/css",
                    "text/csv",
                    "text/markdown",
                    "application/json",
                    "application/xml",
                    "application/javascript",
                    "application/typescript",
                    "text/plain"
            ),
            List.of(
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
            ),
            Map.ofEntries(
                    Map.entry("text/html", ".html"),
                    Map.entry("text/css", ".css"),
                    Map.entry("text/csv", ".csv"),
                    Map.entry("text/markdown", ".md"),
                    Map.entry("application/json", ".json"),
                    Map.entry("application/xml", ".xml")
            ),
            Set.of()
    );

    /** 类型标识，如 "document"、"image" */
    private final String typeId;

    /** 精确匹配的 MIME 列表 */
    private final List<String> mimeTypes;

    /** 声明支持的扩展名列表（用于扩展名筛选和 EXECUTABLE 消歧） */
    private final List<String> extensions;

    /** MIME 到主扩展名的映射（用于无文件名扩展名时推导） */
    private final Map<String, String> mimeToExtension;

    /**
     * OLE2 通用 MIME（{@code application/vnd.ms-office}）消歧时
     * 本类型接受的扩展名集合。
     */
    private final Set<String> ole2DisambiguationExtensions;

    TikaSupportedFileType(String typeId, List<String> mimeTypes, List<String> extensions,
                          Map<String, String> mimeToExtension,
                          Set<String> ole2DisambiguationExtensions) {
        this.typeId = typeId;
        this.mimeTypes = mimeTypes;
        this.extensions = extensions;
        this.mimeToExtension = mimeToExtension;
        this.ole2DisambiguationExtensions = ole2DisambiguationExtensions;
    }

    // ===== 派生查找表（静态初始化） =====

    /** MIME → 文件类型 */
    private static final Map<String, TikaSupportedFileType> MIME_TO_TYPE;

    /** MIME → 主扩展名 */
    private static final Map<String, String> MIME_TO_EXTENSION;

    /** OLE2 消歧：扩展名 → 文件类型 */
    private static final Map<String, TikaSupportedFileType> OLE2_EXT_TO_TYPE;

    /** 所有声明支持的扩展名 */
    private static final List<String> ALL_EXTENSIONS;

    static {
        Map<String, TikaSupportedFileType> mimeToType = new HashMap<>();
        Map<String, String> mimeToExt = new HashMap<>();
        Map<String, TikaSupportedFileType> ole2ExtToType = new HashMap<>();
        List<String> allExts = new ArrayList<>();

        for (TikaSupportedFileType ft : values()) {
            for (String mime : ft.mimeTypes) {
                mimeToType.put(mime, ft);
            }
            mimeToExt.putAll(ft.mimeToExtension);
            for (String ext : ft.ole2DisambiguationExtensions) {
                ole2ExtToType.put(ext, ft);
            }
            allExts.addAll(ft.extensions);
        }

        MIME_TO_TYPE = Map.copyOf(mimeToType);
        MIME_TO_EXTENSION = Map.copyOf(mimeToExt);
        OLE2_EXT_TO_TYPE = Map.copyOf(ole2ExtToType);
        ALL_EXTENSIONS = List.copyOf(allExts);
    }

    /**
     * 根据 MIME 精确匹配文件类型。
     *
     * @param mimeType MIME 类型
     * @return 匹配的文件类型，或 {@code null}
     */
    public static TikaSupportedFileType byMimeType(String mimeType) {
        return MIME_TO_TYPE.get(mimeType);
    }

    /**
     * 根据 MIME 获取主扩展名。
     *
     * @param mimeType MIME 类型
     * @return 扩展名（含点号），或 {@code null} 表示无已知映射
     */
    public static String extensionForMime(String mimeType) {
        return MIME_TO_EXTENSION.get(mimeType);
    }

    /**
     * OLE2 通用 MIME（{@code application/vnd.ms-office}）消歧。
     * <p>
     * Tika 对 OLE2 格式可能仅返回通用 MIME，需通过文件扩展名进一步区分
     * 实际类型（如 .doc → DOCUMENT、.msi → INSTALLER）。
     *
     * @param ext 文件扩展名（含点号）
     * @return 消歧后的文件类型，或 {@code null}
     */
    public static TikaSupportedFileType byOle2Extension(String ext) {
        return OLE2_EXT_TO_TYPE.get(ext);
    }

    /**
     * 获取所有声明支持的扩展名。
     *
     * @return 不可变列表
     */
    public static List<String> allExtensions() {
        return ALL_EXTENSIONS;
    }

    /**
     * 根据 typeId 获取枚举常量。
     *
     * @param typeId 类型标识（如 "document"、"image"）
     * @return 匹配的枚举常量，或 {@code null}
     */
    public static TikaSupportedFileType byTypeId(String typeId) {
        for (TikaSupportedFileType ft : values()) {
            if (ft.typeId.equals(typeId)) {
                return ft;
            }
        }
        return null;
    }
}
