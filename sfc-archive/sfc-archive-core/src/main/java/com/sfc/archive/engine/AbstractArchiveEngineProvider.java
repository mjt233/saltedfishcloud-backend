package com.sfc.archive.engine;

import com.sfc.archive.ArchiveEngineProvider;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.CompressionLevel;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;

import java.util.Collection;
import java.util.Collections;

/**
 * 压缩引擎提供者抽象基类，封装公共默认行为。
 */
public abstract class AbstractArchiveEngineProvider implements ArchiveEngineProvider {
    /**
     * 规范化属性，补齐默认值。
     *
     * @param property 原始属性
     * @return 可用属性
     */
    protected ArchiveEngineProperty normalizeProperty(ArchiveEngineProperty property) {
        if (property == null) {
            return ArchiveEngineProperty.builder().build();
        }
        if (property.getCompressionLevel() == null) {
            property.setCompressionLevel(CompressionLevel.NORMAL);
        }
        if (property.getEncoding() == null || property.getEncoding().isEmpty()) {
            property.setEncoding(SpringContextUtils.getContext().getBean(SysProperties.class).getStore().getArchiveEncoding());
        }
        return property;
    }

    @Override
    public boolean supportEncrypt() {
        return false;
    }

    @Override
    public boolean supportDecrypt() {
        return false;
    }

    @Override
    public Collection<String> getSupportedCompressExtensions() {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getSupportedDecompressExtensions() {
        return Collections.emptyList();
    }
}

