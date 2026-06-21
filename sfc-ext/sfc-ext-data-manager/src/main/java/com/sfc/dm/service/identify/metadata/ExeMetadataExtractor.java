package com.sfc.dm.service.identify.metadata;

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
import java.util.Map;
import java.util.Set;

/**
 * Windows 可执行文件元数据提取器，解析 PE 头提取架构、子系统等信息，通过 Tika 提取发布者
 */
@Slf4j
public class ExeMetadataExtractor implements FileMetadataExtractor {
    private static final byte[] PE_SIGNATURE = {0x50, 0x45, 0x00, 0x00};
    private static final DateTimeFormatter PE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final TikaServerManager tikaServerManager;

    public ExeMetadataExtractor(TikaServerManager tikaServerManager) {
        this.tikaServerManager = tikaServerManager;
    }

    @Override
    public Map<String, String> extract(File file, String mimeType) {
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
