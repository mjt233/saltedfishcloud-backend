package com.sfc.archive.config;

import com.sfc.archive.ArchiveEngineManager;
import com.sfc.archive.ArchiveEngineProvider;
import com.sfc.archive.model.ArchiveEngineFeatureDetail;
import com.xiaotao.saltedfishcloud.constant.FeatureName;
import com.xiaotao.saltedfishcloud.service.hello.FeatureProvider;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 归档引擎特性提供者，用于向系统暴露当前可用压缩引擎信息。
 */
public class ArchiveEngineFeatureProvider implements FeatureProvider {
    /**
     * 压缩引擎管理器。
     */
    private final ArchiveEngineManager archiveEngineManager;

    /**
     * 构造特性提供者。
     *
     * @param archiveEngineManager 压缩引擎管理器
     */
    public ArchiveEngineFeatureProvider(ArchiveEngineManager archiveEngineManager) {
        this.archiveEngineManager = archiveEngineManager;
    }

    /**
     * 注册动态特性。
     *
     * @param helloService 系统特性注册入口
     */
    @Override
    public void registerFeature(HelloService helloService) {
        helloService.setFeature(FeatureName.ARCHIVE_ENGINE_LIST, this::buildArchiveEngineFeatureDetail);
    }

    /**
     * 构建压缩引擎特性详情。
     *
     * @return 引擎信息列表
     */
    private List<ArchiveEngineFeatureDetail> buildArchiveEngineFeatureDetail() {
        return archiveEngineManager.getEngineProviders().stream()
                .sorted(Comparator.comparing(ArchiveEngineProvider::getId))
                .map(this::toEngineDetail)
                .collect(Collectors.toList());
    }

    /**
     * 将引擎提供者转换为可序列化的特性详情。
     *
     * @param provider 引擎提供者
     * @return 引擎特性详情
     */
    private ArchiveEngineFeatureDetail toEngineDetail(ArchiveEngineProvider provider) {
        return new ArchiveEngineFeatureDetail(
                provider.getId(),
                provider.getName(),
                toExtensionList(provider.getSupportedCompressExtensions()),
                toExtensionList(provider.getSupportedDecompressExtensions())
        );
    }

    /**
     * 将扩展名集合转换为可安全序列化的列表。
     *
     * @param extensions 原始扩展名集合
     * @return 扩展名列表
     */
    private List<String> toExtensionList(Collection<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(extensions);
    }
}

