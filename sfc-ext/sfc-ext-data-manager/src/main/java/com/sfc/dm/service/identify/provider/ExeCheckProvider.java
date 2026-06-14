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

    private static final byte[] MZ_MAGIC = {0x4D, 0x5A};
    private static final byte[] PE_SIGNATURE = {0x50, 0x45, 0x00, 0x00};

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
            if (!MagicBytesUtils.matchMagic(header, MZ_MAGIC)) return null;

            FileTypeCheckResultDetail detail = new FileTypeCheckResultDetail();
            String ext = getExtensionFromFileName(file.getName());
            detail.setExtension(ext);
            detail.setMimetype("application/x-msdownload");

            if (extraMetadata) {
                Map<String, String> metadata = extractPeMetadata(file);
                if (metadata != null && !metadata.isEmpty()) detail.setMetadata(metadata);
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

    private Map<String, String> extractPeMetadata(File file) throws Exception {
        Map<String, String> metadata = new HashMap<>();

        byte[] peOffsetBytes = MagicBytesUtils.readAt(file, 0x3C, 4);
        int peOffset = ByteBuffer.wrap(peOffsetBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

        byte[] peSignature = MagicBytesUtils.readAt(file, peOffset, 4);
        if (!MagicBytesUtils.matchMagic(peSignature, PE_SIGNATURE)) return metadata;

        int coffOffset = peOffset + 4;
        byte[] coffHeader = MagicBytesUtils.readAt(file, coffOffset, 20);
        ByteBuffer coffBuf = ByteBuffer.wrap(coffHeader).order(ByteOrder.LITTLE_ENDIAN);

        int machine = coffBuf.getShort() & 0xFFFF;
        coffBuf.getShort(); // numberOfSections
        long timeDateStamp = coffBuf.getInt() & 0xFFFFFFFFL;
        coffBuf.position(16);
        int sizeOfOptionalHeader = coffBuf.getShort() & 0xFFFF;
        int characteristics = coffBuf.getShort() & 0xFFFF;

        metadata.put("architecture", switch (machine) {
            case 0x014C -> "x86";
            case 0x8664 -> "x64";
            case 0xAA64 -> "ARM64";
            default -> "Unknown (0x" + Integer.toHexString(machine) + ")";
        });

        metadata.put("compileTime", TIME_FORMATTER.format(Instant.ofEpochSecond(timeDateStamp)));
        metadata.put("isDll", String.valueOf((characteristics & 0x2000) != 0));

        if (sizeOfOptionalHeader > 0) {
            int optOffset = coffOffset + 20;
            byte[] optHeader = MagicBytesUtils.readAt(file, optOffset, Math.min(sizeOfOptionalHeader, 112));
            ByteBuffer optBuf = ByteBuffer.wrap(optHeader).order(ByteOrder.LITTLE_ENDIAN);

            if (optHeader.length >= 16) {
                int majorLinker = optHeader[2] & 0xFF;
                int minorLinker = optHeader[3] & 0xFF;
                metadata.put("linkerVersion", majorLinker + "." + minorLinker);
            }

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
