package com.sfc.dm.service.identify.provider;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.model.dto.FileTypeInfo;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import com.sfc.dm.service.identify.FileTypeCheckProvider;
import com.sfc.dm.service.identify.util.EncodingDetector;
import com.sfc.dm.service.identify.util.MagicBytesUtils;
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
            ".txt", ".csv", ".log", ".md", ".rst", ".org",
            ".json", ".xml", ".yaml", ".yml", ".toml", ".ini", ".cfg", ".conf",
            ".properties", ".env",
            ".html", ".htm", ".css", ".scss", ".less", ".svg",
            ".java", ".py", ".js", ".ts", ".jsx", ".tsx", ".go", ".rs", ".rb",
            ".php", ".c", ".cpp", ".h", ".hpp", ".cs", ".swift", ".kt", ".scala",
            ".lua", ".r", ".m", ".pl", ".pm", ".sh", ".bash", ".zsh", ".fish",
            ".bat", ".cmd", ".ps1", ".sql", ".gradle", ".groovy", ".cmake",
            ".gitignore", ".dockerignore", ".editorconfig", ".eslintrc", ".prettierrc",
            ".tex", ".sty", ".cls", ".bib", ".vue", ".svelte", ".dart", ".zig",
            ".nim", ".ex", ".exs", ".erl", ".hs", ".ml", ".fs", ".fsx", ".clj",
            ".lisp", ".el", ".asm", ".s", ".S"
    );

    @Override
    public String getId() { return ID; }
    @Override
    public List<FileTypeInfo> getTypeInfoList() {
        return List.of(new FileTypeInfo(TYPE_ID, TYPE_NAME, List.of(
                new FileMetadataDefine("编码", "encoding", "文件编码格式", "span"),
                new FileMetadataDefine("行数", "lineCount", "文件行数", "span"),
                new FileMetadataDefine("文件大小", "fileSize", "文件大小（字节）", "span")
        )));
    }
    @Override
    public List<String> getSupportedFileExtensions() { return EXTENSIONS; }
    @Override
    public int getPriority() { return PRIORITY; }

    @Override
    public FileTypeCheckResultDetail checkFile(File file, boolean extraMetadata) {
        String ext = getExtensionWithDot(file.getName());
        boolean hasKnownExt = ext != null && EXTENSIONS.contains(ext);

        // 无扩展名或未知扩展名时，通过内容探测判断是否为文本文件
        if (!hasKnownExt) {
            if (!looksLikeText(file)) {
                return null;
            }
            ext = ".txt";
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

    private String getExtensionWithDot(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.startsWith(".")) return lower;
        int dotIndex = lower.lastIndexOf('.');
        if (dotIndex > 0) return lower.substring(dotIndex);
        return null;
    }

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

    private int countLines(File file) throws IOException {
        int lines = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (reader.readLine() != null) lines++;
        }
        return lines;
    }

    /**
     * 通过内容探测判断文件是否为纯文本。
     * 检查前 8KB 是否为有效 UTF-8/ASCII 且不含 null 字节。
     */
    private boolean looksLikeText(File file) {
        try {
            byte[] header = MagicBytesUtils.readHeader(file, 8192);
            if (header.length == 0) {
                return true;
            }

            boolean hasNullByte = false;
            boolean validUtf8 = true;
            int i = 0;

            while (i < header.length) {
                int b = header[i] & 0xFF;
                if (b == 0) {
                    hasNullByte = true;
                    break;
                }
                if (b <= 0x7F) {
                    i++;
                } else if ((b & 0xE0) == 0xC0) {
                    if (i + 1 >= header.length || (header[i + 1] & 0xC0) != 0x80) {
                        validUtf8 = false;
                        break;
                    }
                    i += 2;
                } else if ((b & 0xF0) == 0xE0) {
                    if (i + 2 >= header.length || (header[i + 1] & 0xC0) != 0x80 || (header[i + 2] & 0xC0) != 0x80) {
                        validUtf8 = false;
                        break;
                    }
                    i += 3;
                } else if ((b & 0xF8) == 0xF0) {
                    if (i + 3 >= header.length || (header[i + 1] & 0xC0) != 0x80
                            || (header[i + 2] & 0xC0) != 0x80 || (header[i + 3] & 0xC0) != 0x80) {
                        validUtf8 = false;
                        break;
                    }
                    i += 4;
                } else {
                    validUtf8 = false;
                    break;
                }
            }

            return !hasNullByte && validUtf8;
        } catch (Exception e) {
            return false;
        }
    }
}
