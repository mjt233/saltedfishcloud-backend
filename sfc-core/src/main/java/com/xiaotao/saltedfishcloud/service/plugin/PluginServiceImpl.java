package com.xiaotao.saltedfishcloud.service.plugin;

import com.xiaotao.saltedfishcloud.common.SystemOverviewItemProvider;
import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.ext.PluginManager;
import com.xiaotao.saltedfishcloud.ext.PluginProperty;
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
    private PluginProperty pluginProperty;

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

        String suffix = "." + type;
        String fileName = "autoLoad" + suffix;

        // 若关闭了自动加载资源，则返回空字符串
        if(Boolean.FALSE.equals(pluginProperty.getUseAutoLoadResource())) {
            return ResourceUtils.stringToResource("").setResponseFilename(fileName);
        }

        // todo 缓存
        StringBuilder mergeResult = new StringBuilder();

        // 获取所有插件，筛选出配置了自动加载的插件
        this.listPlugins()
                .stream()
                .filter(e -> e.getAutoLoad() != null && !e.getAutoLoad().isEmpty())
                .forEach(plugin -> {
                    // 遍历插件，读取插件名称，作为注释添加到autoLoad.js(.css)
                    String pluginName = plugin.getName();
                    mergeResult.append("/* ").append(pluginName).append(" */\n");

                    // 遍历插件配置的自动加载静态文件，按后缀名匹配
                    plugin.getAutoLoad()
                            .stream()
                            .filter(e -> e.endsWith(suffix))
                            // 读取每个静态文件内容，追加合并内容
                            .forEach(resourceName -> {
                                try (InputStream in = getPluginStaticResource(pluginName, resourceName).getInputStream()) {
                                    mergeResult.append(StreamUtils.copyToString(in, StandardCharsets.UTF_8)).append("\n");
                                } catch (Exception e) {
                                    log.error("{}插件资源{}-{}合并错误：",LOG_PREFIX, pluginName, resourceName, e);
                                }
                            });

                });
        return ResourceUtils.stringToResource(mergeResult.toString())
                .setResponseFilename(fileName);
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
