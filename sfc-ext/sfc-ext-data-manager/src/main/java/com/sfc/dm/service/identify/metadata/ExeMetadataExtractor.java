package com.sfc.dm.service.identify.metadata;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.service.identify.provider.TikaSupportedFileType;
import com.sfc.dm.service.identify.tika.TikaServerManager;
import com.sfc.dm.service.identify.util.MagicBytesUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Windows 可执行文件元数据提取器，解析 PE 头提取架构、子系统等信息，通过 Tika 提取发布者
 */
@Slf4j
public class ExeMetadataExtractor implements FileMetadataExtractor {
    

    private static final List<FileMetadataDefine> METADATA_DEFINES = List.of(
            new FileMetadataDefine("架构", "architecture", "目标架构（x86/x64）", "span"),
            new FileMetadataDefine("子系统", "subsystem", "子系统类型（CONSOLE/WINDOWS/NATIVE）", "span"),
            new FileMetadataDefine("链接器版本", "linkerVersion", "链接器版本号", "span"),
            new FileMetadataDefine("编译时间", "compileTime", "PE 编译时间戳", "span"),
            new FileMetadataDefine("是否DLL", "isDll", "是否为动态链接库", "span"),
            new FileMetadataDefine("发布者", "publisher", "文件发布者/公司名称", "span")
    );

    private static final byte[] PE_SIGNATURE = {0x50, 0x45, 0x00, 0x00};
    private static final DateTimeFormatter PE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final TikaServerManager tikaServerManager;

    public ExeMetadataExtractor(TikaServerManager tikaServerManager) {
        this.tikaServerManager = tikaServerManager;
    }

    @Override
    public String getTypeId() { return TikaSupportedFileType.EXECUTABLE.getTypeId(); }

    @Override
    public String getTypeName() { return TikaSupportedFileType.EXECUTABLE.getTypeName(); }

    @Override
    public List<FileMetadataDefine> getMetadataDefines() { return METADATA_DEFINES; }

    @Override
    public Map<String, String> extract(File file) {
        Map<String, String> metadata = new HashMap<>();
        try {
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

            metadata.put("compileTime", PE_TIME_FORMATTER.format(Instant.ofEpochSecond(timeDateStamp)));
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
        } catch (Exception e) {
            log.debug("PE 元数据提取失败: {}", file.getName(), e);
        }

        if (tikaServerManager != null) {
            try {
                Map<String, Object> tikaMeta = tikaServerManager.extractMetadata(file, Set.of("Company"));
                if (tikaMeta != null && tikaMeta.containsKey("Company")) {
                    metadata.put("publisher", String.valueOf(tikaMeta.get("Company")));
                }
            } catch (Exception e) {
                log.debug("PE 发布者提取失败: {}", file.getName(), e);
            }
        }

        return metadata;
    }
}
