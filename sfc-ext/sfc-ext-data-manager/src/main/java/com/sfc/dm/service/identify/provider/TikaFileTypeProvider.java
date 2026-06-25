package com.sfc.dm.service.identify.provider;

import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import com.sfc.dm.model.dto.FileTypeInfo;
import com.sfc.dm.service.identify.FileTypeCheckProvider;
import com.sfc.dm.service.identify.metadata.FileMetadataExtractor;
import com.sfc.dm.service.identify.tika.TikaServerManager;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.Map;

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

    private final TikaServerManager tikaServerManager;
    private final Map<String, FileMetadataExtractor> metadataExtractors;

    /**
     * @param tikaServerManager  Tika Server 管理器
     * @param metadataExtractors typeId 到元数据提取器的映射
     */
    public TikaFileTypeProvider(TikaServerManager tikaServerManager,
                                Map<String, FileMetadataExtractor> metadataExtractors) {
        this.tikaServerManager = tikaServerManager;
        this.metadataExtractors = metadataExtractors;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public List<String> getSupportedFileExtensions() {
        return TikaSupportedFileType.allExtensions();
    }

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
     *
     * @return [typeId, extension]，无法识别时返回 null
     */
    private String[] resolveTypeIdAndExtension(String mimeType, String fileName) {
        String lowerName = fileName.toLowerCase();
        String ext = getExtensionWithDot(lowerName);

        // 1. 精确 MIME 匹配
        TikaSupportedFileType fileType = TikaSupportedFileType.byMimeType(mimeType);
        if (fileType != null) {
            String typeId = fileType.getTypeId();
            if (fileType == TikaSupportedFileType.EXECUTABLE) {
                String resolvedExt = (ext != null && fileType.getExtensions().contains(ext)) ? ext : ".exe";
                return new String[]{typeId, resolvedExt};
            }
            if (fileType == TikaSupportedFileType.INSTALLER) {
                return new String[]{typeId, ext != null ? ext : ".msi"};
            }
            String resolvedExt = resolveExtension(fileType, mimeType, ext);
            return new String[]{typeId, resolvedExt};
        }

        // 2. 前缀匹配（image/*, text/*）
        if (mimeType.startsWith("image/")) {
            String resolvedExt = resolveExtension(TikaSupportedFileType.IMAGE, mimeType, ext);
            return new String[]{TikaSupportedFileType.IMAGE.getTypeId(), resolvedExt};
        }
        if (mimeType.startsWith("text/")) {
            String resolvedExt = resolveExtension(TikaSupportedFileType.TEXT, mimeType, ext);
            return new String[]{TikaSupportedFileType.TEXT.getTypeId(), resolvedExt};
        }

        // 3. 通用 OLE2 消歧（application/vnd.ms-office）
        if ("application/vnd.ms-office".equals(mimeType) && ext != null) {
            TikaSupportedFileType resolved = TikaSupportedFileType.byOle2Extension(ext);
            if (resolved != null) {
                return new String[]{resolved.getTypeId(), ext};
            }
        }

        return null;
    }

    /**
     * 根据 typeId、MIME 和文件名扩展名确定最终扩展名
     */
    private String resolveExtension(TikaSupportedFileType fileType, String mimeType, String fileNameExt) {
        if (fileNameExt != null) {
            return fileNameExt;
        }
        String mimeExt = TikaSupportedFileType.extensionForMime(mimeType);
        if (mimeExt != null) {
            return mimeExt;
        }
        return switch (fileType) {
            case TEXT -> ".txt";
            case IMAGE -> ".bin";
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
        detail.setTypeId(TikaSupportedFileType.TEXT.getTypeId());
        FileMetadataExtractor textExtractor = metadataExtractors.get(TikaSupportedFileType.TEXT.getTypeId());
        detail.setTypeName(textExtractor != null ? textExtractor.getTypeName() : "纯文本");
        return detail;
    }
}
