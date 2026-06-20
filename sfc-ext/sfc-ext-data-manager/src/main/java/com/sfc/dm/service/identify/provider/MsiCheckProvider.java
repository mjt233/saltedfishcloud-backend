package com.sfc.dm.service.identify.provider;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.model.dto.FileTypeInfo;
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

    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] MSI_CLSID = {
            (byte) 0x84, 0x10, 0x0C, 0x00, 0x00, 0x00, 0x00, 0x00,
            (byte) 0xC0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x46
    };

    private static final List<String> EXTENSIONS = List.of(".msi");

    @Override
    public String getId() { return ID; }
    @Override
    public List<FileTypeInfo> getTypeInfoList() {
        return List.of(new FileTypeInfo(TYPE_ID, TYPE_NAME, List.of(
                new FileMetadataDefine("产品名称", "productName", "MSI 产品名称", "span"),
                new FileMetadataDefine("产品版本", "productVersion", "MSI 产品版本", "span"),
                new FileMetadataDefine("制造商", "manufacturer", "MSI 制造商", "span"),
                new FileMetadataDefine("架构", "architecture", "目标平台架构", "span")
        )));
    }
    @Override
    public List<String> getSupportedFileExtensions() { return EXTENSIONS; }
    @Override
    public int getPriority() { return PRIORITY; }

    @Override
    public FileTypeCheckResultDetail checkFile(File file, boolean extraMetadata) {
        try {
            byte[] header = MagicBytesUtils.readHeader(file, 8);
            if (!MagicBytesUtils.matchMagic(header, OLE2_MAGIC)) return null;

            byte[] clsid = MagicBytesUtils.readAt(file, 0x50, 16);
            if (!Arrays.equals(clsid, MSI_CLSID)) return null;

            FileTypeCheckResultDetail detail = new FileTypeCheckResultDetail();
            detail.setExtension(".msi");
            detail.setMimetype("application/x-msi");

            if (extraMetadata) {
                Map<String, String> metadata = extractMsiMetadata(file);
                if (metadata != null && !metadata.isEmpty()) detail.setMetadata(metadata);
            }

            return detail;
        } catch (Exception e) {
            log.debug("MSI 检测失败: {}", file.getName(), e);
            return null;
        }
    }

    private Map<String, String> extractMsiMetadata(File file) {
        Map<String, String> metadata = new HashMap<>();
        try {
            byte[] fileSample = MagicBytesUtils.readAt(file, 0, (int) Math.min(file.length(), 65536));
            String sampleStr = new String(fileSample, StandardCharsets.ISO_8859_1);

            extractProperty(metadata, sampleStr, "ProductName");
            extractProperty(metadata, sampleStr, "ProductVersion");
            extractProperty(metadata, sampleStr, "Manufacturer");

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
