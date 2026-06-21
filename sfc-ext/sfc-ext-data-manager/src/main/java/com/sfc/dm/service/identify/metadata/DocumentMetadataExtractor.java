package com.sfc.dm.service.identify.metadata;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.service.identify.tika.TikaServerManager;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文档文件元数据提取器，通过 Tika 提取标题、作者、页数、创建时间
 */
@Slf4j
public class DocumentMetadataExtractor implements FileMetadataExtractor {
    private static final String TYPE_ID = "document";
    private static final String TYPE_NAME = "文档";

    private static final List<FileMetadataDefine> METADATA_DEFINES = List.of(
            new FileMetadataDefine("标题", "title", "文档标题", "span"),
            new FileMetadataDefine("作者", "author", "文档作者", "span"),
            new FileMetadataDefine("页数", "pageCount", "文档页数", "span"),
            new FileMetadataDefine("创建时间", "createdDate", "文档创建时间", "span")
    );

    private final TikaServerManager tikaServerManager;

    public DocumentMetadataExtractor(TikaServerManager tikaServerManager) {
        this.tikaServerManager = tikaServerManager;
    }

    @Override
    public String getTypeId() { return TYPE_ID; }

    @Override
    public String getTypeName() { return TYPE_NAME; }

    @Override
    public List<FileMetadataDefine> getMetadataDefines() { return METADATA_DEFINES; }

    @Override
    public Map<String, String> extract(File file) {
        if (tikaServerManager == null) return null;
        Map<String, Object> raw = tikaServerManager.extractMetadata(file, Set.of(
                "title", "creator", "xmpTPg:NPages", "dcterms:created"
        ));
        if (raw == null) return null;

        Map<String, String> metadata = new HashMap<>();
        if (raw.containsKey("title")) metadata.put("title", String.valueOf(raw.get("title")));
        if (raw.containsKey("creator")) metadata.put("author", String.valueOf(raw.get("creator")));
        if (raw.containsKey("xmpTPg:NPages")) metadata.put("pageCount", String.valueOf(raw.get("xmpTPg:NPages")));
        if (raw.containsKey("dcterms:created")) metadata.put("createdDate", String.valueOf(raw.get("dcterms:created")));
        return metadata.isEmpty() ? null : metadata;
    }
}
