package com.xiaotao.saltedfishcloud.service.plugin;

import com.xiaotao.saltedfishcloud.common.SystemOverviewItemProvider;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.ext.PluginManager;
import com.xiaotao.saltedfishcloud.ext.PluginService;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import com.xiaotao.saltedfishcloud.model.vo.PluginInfoVo;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PluginServiceImpl implements PluginService, SystemOverviewItemProvider {
    private final static String LOG_PREFIX = "[插件服务]";
    private final PluginManager pluginManager;

    @Autowired
    private SysProperties sysProperties;

    @Override
    public List<PluginInfo> listPlugins() {
        return pluginManager.getAllPlugin().values().stream().map(e -> {
            PluginInfo newObj = new PluginInfo();
            BeanUtils.copyProperties(e, newObj);
            return newObj;
        }).collect(Collectors.toList());
    }

    @Override
    public List<PluginInfo> listAvailablePlugins() throws IOException {
        return pluginManager.listAvailablePlugins();
    }

    @Override
    public void deletePlugin(String name) throws IOException {
        pluginManager.markPluginDelete(name);
    }

    @Override
    public Resource getPluginStaticResource(String name, String path) throws PluginNotFoundException {
        return pluginManager.getPluginResource(name, StringUtils.appendPath("static", path));
    }

    @Override
    public List<ConfigNode> provideItem(Map<String, ConfigNode> existItem) {
        return Collections.singletonList(
                ConfigNode.builder()
                        .name("install-plugins")
                        .title("安装的插件")
                        .nodes(listPlugins().stream().map(e -> new ConfigNode(e.getAlias(), e.getVersion())).collect(Collectors.toList()))
                        .build()
        );
    }

    @Override
    public Resource getMergeAutoLoadResource(String type) {
        // todo 缓存
        StringBuilder sb = new StringBuilder();
        String suffix = "." + type;
        this.listPlugins()
                .stream()
                .filter(e -> e.getAutoLoad() != null && !e.getAutoLoad().isEmpty())
                .forEach(plugin -> {
                    String pluginName = plugin.getName();
                    sb.append("/* ").append(pluginName).append(" */\n");
                    plugin.getAutoLoad()
                            .stream()
                            .filter(e -> e.endsWith(suffix))
                            .forEach(resourceName -> {
                                try (InputStream in = getPluginStaticResource(pluginName, resourceName).getInputStream()) {
                                    sb.append(StreamUtils.copyToString(in, StandardCharsets.UTF_8)).append("\n");
                                } catch (Exception e) {
                                    log.error("{}插件资源{}-{}合并错误：",LOG_PREFIX, pluginName, resourceName, e);
                                }
                            });

                });
        return ResourceUtils.stringToResource(sb.toString())
                .setResponseFilename("autoLoad" + suffix);
    }

    @Override
    public PluginInfoVo uploadPlugin(Resource resource) throws IOException {
        // 临时保存
        long tempId = IdUtil.getId();
        Path tempPath = Paths.get(StringUtils.appendPath(PathUtils.getTempDirectory(), tempId + ".jar"));
        ResourceUtils.saveToFile(resource, tempPath);

        // 解析插件
        PluginInfo pluginInfo = pluginManager.parsePlugin(tempPath.toUri().toURL());
        PluginInfoVo pluginInfoVo = new PluginInfoVo();
        BeanUtils.copyProperties(pluginInfo, pluginInfoVo);
        pluginInfoVo.setTempId(tempId);
        return pluginInfoVo;
    }

    @Override
    public void installPlugin(Long tempId, String fileName) throws IOException {
        Path tempPath = Paths.get(StringUtils.appendPath(PathUtils.getTempDirectory(), tempId + ".jar"));
        if (!Files.exists(tempPath)) {
            throw new IllegalArgumentException("无效的临时id");
        }
        PathResource pathResource = new PathResource(tempPath){
            @Override
            public String getFilename() {
                return fileName;
            }};

        pluginManager.installPlugin(pathResource);
        Files.deleteIfExists(tempPath);
    }
}
