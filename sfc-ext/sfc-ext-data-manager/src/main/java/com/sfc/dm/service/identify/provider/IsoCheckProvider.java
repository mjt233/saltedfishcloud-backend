package com.sfc.dm.service.identify.provider;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.model.dto.FileTypeInfo;
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

    private static final byte[] ISO9660_SIGNATURE = {0x43, 0x44, 0x30, 0x30, 0x31};
    private static final long PVD_OFFSET = 0x8000L;
    private static final int SIGNATURE_OFFSET_IN_PVD = 1;
    private static final int VOLUME_LABEL_OFFSET_IN_PVD = 40;
    private static final int VOLUME_LABEL_LENGTH = 32;
    private static final int ROOT_DIR_RECORD_OFFSET = 156;
    private static final int DIR_RECORD_LENGTH = 34;

    private static final List<String> EXTENSIONS = List.of(".iso");

    @Override
    public String getId() { return ID; }
    @Override
    public List<FileTypeInfo> getTypeInfoList() {
        return List.of(new FileTypeInfo(TYPE_ID, TYPE_NAME, List.of(
                new FileMetadataDefine("卷标", "volumeLabel", "ISO 卷标名称", "span"),
                new FileMetadataDefine("文件大小", "fileSize", "ISO 文件大小", "span"),
                new FileMetadataDefine("文件列表", "fileList", "前5个文件名（JSON数组）", "span")
        )));
    }
    @Override
    public List<String> getSupportedFileExtensions() { return EXTENSIONS; }
    @Override
    public int getPriority() { return PRIORITY; }

    @Override
    public FileTypeCheckResultDetail checkFile(File file, boolean extraMetadata) {
        try {
            byte[] pvdHeader = MagicBytesUtils.readAt(file, PVD_OFFSET + SIGNATURE_OFFSET_IN_PVD, 5);
            if (!MagicBytesUtils.matchMagic(pvdHeader, ISO9660_SIGNATURE)) return null;

            FileTypeCheckResultDetail detail = new FileTypeCheckResultDetail();
            detail.setExtension(".iso");
            detail.setMimetype("application/x-iso9660-image");

            if (extraMetadata) {
                Map<String, String> metadata = extractMetadata(file);
                if (metadata != null && !metadata.isEmpty()) detail.setMetadata(metadata);
            }

            return detail;
        } catch (Exception e) {
            log.debug("ISO 检测失败: {} {}", file.getName(), e.getMessage());
            return null;
        }
    }

    private Map<String, String> extractMetadata(File file) throws IOException {
        Map<String, String> metadata = new HashMap<>();

        byte[] labelBytes = MagicBytesUtils.readAt(file, PVD_OFFSET + VOLUME_LABEL_OFFSET_IN_PVD, VOLUME_LABEL_LENGTH);
        String volumeLabel = new String(labelBytes, StandardCharsets.US_ASCII).trim();
        if (!volumeLabel.isEmpty()) metadata.put("volumeLabel", volumeLabel);

        metadata.put("fileSize", String.valueOf(file.length()));

        byte[] rootDirRecord = MagicBytesUtils.readAt(file, PVD_OFFSET + ROOT_DIR_RECORD_OFFSET, DIR_RECORD_LENGTH);
        int rootLba = ByteBuffer.wrap(rootDirRecord, 2, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        int rootSize = ByteBuffer.wrap(rootDirRecord, 10, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();

        List<String> fileList = new ArrayList<>();
        byte[] rootDirContent = MagicBytesUtils.readAt(file, (long) rootLba * 2048, Math.min(rootSize, 20480));
        parseDirectoryRecords(rootDirContent, fileList);

        metadata.put("fileList", fileList.toString());
        return metadata;
    }

    private void parseDirectoryRecords(byte[] data, List<String> fileList) {
        int offset = 0;
        while (offset < data.length - 33) {
            int recordLen = data[offset] & 0xFF;
            if (recordLen == 0) {
                offset = ((offset / 2048) + 1) * 2048;
                if (offset >= data.length) break;
                continue;
            }
            int nameLen = data[offset + 32] & 0xFF;
            if (nameLen > 0 && nameLen < data.length - offset - 33) {
                byte[] nameBytes = new byte[nameLen];
                System.arraycopy(data, offset + 33, nameBytes, 0, nameLen);
                String name = new String(nameBytes, StandardCharsets.US_ASCII);
                if (!".".equals(name) && !"..".equals(name) && !"\0".equals(name)) {
                    int semicolonIdx = name.indexOf(';');
                    if (semicolonIdx > 0) name = name.substring(0, semicolonIdx);
                    if (name.endsWith(".")) name = name.substring(0, name.length() - 1);
                    if (!name.isEmpty() && fileList.size() < MAX_FILE_LIST_SIZE) fileList.add(name);
                }
            }
            offset += recordLen;
            if (fileList.size() >= MAX_FILE_LIST_SIZE) break;
        }
    }
}
