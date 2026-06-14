package com.sfc.dm.service.identify.provider;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import com.sfc.dm.service.identify.FileTypeCheckProvider;
import com.sfc.dm.service.identify.tika.TikaServerManager;
import com.sfc.dm.service.identify.util.MagicBytesUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 文档文件类型识别提供者，支持 Office 文档（docx/doc/xlsx/xls/pptx/ppt）、PDF、ODF 格式
 */
@Slf4j
public class DocumentCheckProvider implements FileTypeCheckProvider {
    private final TikaServerManager tikaServerManager;
    private static final String ID = "documentCheckProvider";
    private static final String TYPE_NAME = "文档";
    private static final String TYPE_ID = "document";
    private static final int PRIORITY = 10;

    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    private static final List<String> EXTENSIONS = List.of(
            ".docx", ".doc", ".xlsx", ".xls", ".pptx", ".ppt", ".pdf",
            ".odt", ".ods", ".odp"
    );

    public DocumentCheckProvider(TikaServerManager tikaServerManager) {
        this.tikaServerManager = tikaServerManager;
    }

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

            if (MagicBytesUtils.matchMagic(header, PDF_MAGIC)) {
                detectedExt = ".pdf";
                mimeType = "application/pdf";
            } else if (MagicBytesUtils.matchMagic(header, OLE2_MAGIC)) {
                String[] ole2Result = detectOle2SubType(file);
                if (ole2Result != null) {
                    detectedExt = ole2Result[0];
                    mimeType = ole2Result[1];
                }
            } else if (MagicBytesUtils.matchMagic(header, ZIP_MAGIC)) {
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

    private String[] detectOle2SubType(File file) {
        String lowerName = file.getName().toLowerCase();
        if (lowerName.endsWith(".doc")) return new String[]{".doc", "application/msword"};
        if (lowerName.endsWith(".xls")) return new String[]{".xls", "application/vnd.ms-excel"};
        if (lowerName.endsWith(".ppt")) return new String[]{".ppt", "application/vnd.ms-powerpoint"};
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
        } catch (Exception ignored) {}
        return null;
    }

    private String[] detectZipSubType(File file) {
        try (ZipFile zipFile = new ZipFile(file)) {
            if (zipFile.getEntry("[Content_Types].xml") != null) {
                return detectOfficeOpenXmlSubType(zipFile, file.getName());
            }
            ZipEntry mimetypeEntry = zipFile.getEntry("mimetype");
            if (mimetypeEntry != null) {
                return detectOdfSubType(zipFile, mimetypeEntry);
            }
        } catch (IOException e) {
            log.debug("ZIP 内部结构检测失败: {}", file.getName(), e);
        }
        return null;
    }

    private String[] detectOfficeOpenXmlSubType(ZipFile zipFile, String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".docx")) return new String[]{".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"};
        if (lowerName.endsWith(".xlsx")) return new String[]{".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"};
        if (lowerName.endsWith(".pptx")) return new String[]{".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"};
        if (zipFile.getEntry("word/document.xml") != null) return new String[]{".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"};
        if (zipFile.getEntry("xl/workbook.xml") != null) return new String[]{".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"};
        if (zipFile.getEntry("ppt/presentation.xml") != null) return new String[]{".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"};
        return null;
    }

    private String[] detectOdfSubType(ZipFile zipFile, ZipEntry mimetypeEntry) {
        try (InputStream is = zipFile.getInputStream(mimetypeEntry)) {
            byte[] buf = new byte[128];
            int len = is.read(buf);
            String mimetype = new String(buf, 0, len).trim();
            return switch (mimetype) {
                case "application/vnd.oasis.opendocument.text" -> new String[]{".odt", mimetype};
                case "application/vnd.oasis.opendocument.spreadsheet" -> new String[]{".ods", mimetype};
                case "application/vnd.oasis.opendocument.presentation" -> new String[]{".odp", mimetype};
                default -> null;
            };
        } catch (IOException e) { return null; }
    }

    private String detectByTika(File file) {
        if (tikaServerManager == null) return null;
        return tikaServerManager.detect(file);
    }

    private Map<String, String> extractMetadata(File file) {
        if (tikaServerManager == null) return null;
        Map<String, String> raw = tikaServerManager.extractMetadata(file, Set.of(
                "title", "creator", "xmpTPg:NPages", "dcterms:created"
        ));
        if (raw == null) return null;

        // 将 Tika 返回的原始 key 映射为业务 key
        Map<String, String> metadata = new HashMap<>();
        if (raw.containsKey("title")) metadata.put("title", raw.get("title"));
        if (raw.containsKey("creator")) metadata.put("author", raw.get("creator"));
        if (raw.containsKey("xmpTPg:NPages")) metadata.put("pageCount", raw.get("xmpTPg:NPages"));
        if (raw.containsKey("dcterms:created")) metadata.put("createdDate", raw.get("dcterms:created"));
        return metadata.isEmpty() ? null : metadata;
    }
}
