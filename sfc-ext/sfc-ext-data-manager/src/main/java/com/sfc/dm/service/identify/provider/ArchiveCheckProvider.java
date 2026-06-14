package com.sfc.dm.service.identify.provider;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import com.sfc.dm.service.identify.FileTypeCheckProvider;
import com.sfc.dm.service.identify.util.MagicBytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.*;
import java.util.*;
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
    private static final byte[] TAR_MAGIC = {0x75, 0x73, 0x74, 0x61, 0x72};
    private static final byte[] GZ_MAGIC = {0x1F, (byte) 0x8B};
    private static final byte[] BZ2_MAGIC = {0x42, 0x5A, 0x68};
    private static final byte[] XZ_MAGIC = {(byte) 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00};

    private static final List<String> EXTENSIONS = List.of(".zip", ".rar", ".7z", ".tar", ".gz", ".bz2", ".xz");

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
            if (header.length < 2) return null;

            String ext = null, mimeType = null, compressionMethod = null;

            if (MagicBytesUtils.matchMagic(header, ZIP_MAGIC)) { ext = ".zip"; mimeType = "application/zip"; compressionMethod = "DEFLATE"; }
            else if (MagicBytesUtils.matchMagic(header, RAR5_MAGIC)) { ext = ".rar"; mimeType = "application/x-rar-compressed"; compressionMethod = "RAR5"; }
            else if (MagicBytesUtils.matchMagic(header, RAR_MAGIC)) { ext = ".rar"; mimeType = "application/x-rar-compressed"; compressionMethod = "RAR4"; }
            else if (MagicBytesUtils.matchMagic(header, SEVENZ_MAGIC)) { ext = ".7z"; mimeType = "application/x-7z-compressed"; compressionMethod = "LZMA2"; }
            else if (MagicBytesUtils.matchMagic(header, GZ_MAGIC)) { ext = ".gz"; mimeType = "application/gzip"; compressionMethod = "GZIP"; }
            else if (MagicBytesUtils.matchMagic(header, BZ2_MAGIC)) { ext = ".bz2"; mimeType = "application/x-bzip2"; compressionMethod = "BZIP2"; }
            else if (MagicBytesUtils.matchMagic(header, XZ_MAGIC)) { ext = ".xz"; mimeType = "application/x-xz"; compressionMethod = "LZMA"; }
            else {
                byte[] tarHeader = MagicBytesUtils.readAt(file, 257, 5);
                if (MagicBytesUtils.matchMagic(tarHeader, TAR_MAGIC)) { ext = ".tar"; mimeType = "application/x-tar"; compressionMethod = "TAR"; }
            }

            if (ext == null) return null;

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
                        log.debug("压缩包元数据提取失败: {} {}", file.getName(), e.getMessage());
                    }
                }
                detail.setMetadata(metadata);
            }

            return detail;
        } catch (Exception e) {
            log.debug("压缩包检测失败: {} {}", file.getName(), e.getMessage());
            return null;
        }
    }

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
                        if (fileList.size() < MAX_FILE_LIST_SIZE) fileList.add(entry.getName());
                    }
                }
            }
        } else {
            try (InputStream fis = new FileInputStream(file); InputStream bis = new BufferedInputStream(fis)) {
                InputStream decompressed = bis;
                if (".gz".equals(ext) || ".bz2".equals(ext) || ".xz".equals(ext)) {
                    decompressed = new CompressorStreamFactory().createCompressorInputStream(bis);
                }
                try (InputStream src = decompressed; ArchiveInputStream<ArchiveEntry> ais = new ArchiveStreamFactory().createArchiveInputStream(src)) {
                    ArchiveEntry entry;
                    while ((entry = ais.getNextEntry()) != null) {
                        if (!entry.isDirectory()) {
                            fileCount++;
                            if (fileList.size() < MAX_FILE_LIST_SIZE) fileList.add(entry.getName());
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
