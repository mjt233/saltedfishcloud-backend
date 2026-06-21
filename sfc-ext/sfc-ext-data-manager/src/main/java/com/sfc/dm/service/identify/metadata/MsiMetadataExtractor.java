package com.sfc.dm.service.identify.metadata;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.service.identify.util.MagicBytesUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MSI 安装包元数据提取器，从 OLE2 二进制数据中提取产品名称、版本、制造商、发布者、架构
 */
@Slf4j
public class MsiMetadataExtractor implements FileMetadataExtractor {
    private static final String TYPE_ID = "installer";
    private static final String TYPE_NAME = "安装包";

    private static final List<FileMetadataDefine> METADATA_DEFINES = List.of(
            new FileMetadataDefine("产品名称", "productName", "MSI 产品名称", "span"),
            new FileMetadataDefine("产品版本", "productVersion", "MSI 产品版本", "span"),
            new FileMetadataDefine("制造商", "manufacturer", "MSI 制造商", "span"),
            new FileMetadataDefine("发布者", "publisher", "MSI 发布者", "span"),
            new FileMetadataDefine("架构", "architecture", "目标平台架构", "span")
    );

    @Override
    public String getTypeId() { return TYPE_ID; }

    @Override
    public String getTypeName() { return TYPE_NAME; }

    @Override
    public List<FileMetadataDefine> getMetadataDefines() { return METADATA_DEFINES; }

    @Override
    public Map<String, String> extract(File file) {
        Map<String, String> metadata = new HashMap<>();
        try {
            byte[] fileSample = MagicBytesUtils.readAt(file, 0, (int) Math.min(file.length(), 65536));
            String sampleStr = new String(fileSample, StandardCharsets.ISO_8859_1);

            extractProperty(metadata, sampleStr, "ProductName");
            extractProperty(metadata, sampleStr, "ProductVersion");
            extractProperty(metadata, sampleStr, "Manufacturer");
            extractProperty(metadata, sampleStr, "Publisher");

            if (!metadata.containsKey("publisher") && metadata.containsKey("manufacturer")) {
                metadata.put("publisher", metadata.get("manufacturer"));
            }

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

    private void extractProperty(Map<String, String> metadata, String content, String propertyName) {
        int idx = content.indexOf(propertyName);
        if (idx > 0 && idx < content.length() - propertyName.length() - 50) {
            String after = content.substring(idx + propertyName.length());
            StringBuilder value = new StringBuilder();
            for (int i = 4; i < Math.min(after.length(), 100); i++) {
                char c = after.charAt(i);
                if (c >= 32 && c < 127) {
                    value.append(c);
                } else if (!value.isEmpty()) {
                    break;
                }
            }
            if (!value.isEmpty()) {
                String key = switch (propertyName) {
                    case "ProductName" -> "productName";
                    case "ProductVersion" -> "productVersion";
                    case "Manufacturer" -> "manufacturer";
                    case "Publisher" -> "publisher";
                    default -> propertyName;
                };
                metadata.put(key, value.toString().trim());
            }
        }
    }
}
