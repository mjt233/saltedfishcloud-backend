package com.xiaotao.saltedfishcloud.service.plugin;

import com.sfc.cloud.auth.SfcInternalTokenIssuer;
import com.sfc.cloud.auth.client.SfcInternalTokenRequestInitializer;
import com.sfc.cloud.constants.SfcServiceMetaKey;
import com.sfc.cloud.data.id.SfcIdUtil;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.ConfigNode;
import com.sfc.common.model.PluginInfo;
import com.sfc.common.utils.SfcStringUtils;
import com.xiaotao.saltedfishcloud.common.SystemOverviewItemProvider;
import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.ext.PluginManager;
import com.xiaotao.saltedfishcloud.ext.PluginProperty;
import com.xiaotao.saltedfishcloud.ext.PluginService;
import com.xiaotao.saltedfishcloud.model.vo.PluginInfoVo;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PluginServiceImpl implements PluginService, SystemOverviewItemProvider {
    private final static String LOG_PREFIX = "[插件服务]";
    private final PluginManager pluginManager;
    private final DiscoveryClient discoveryClient;
    private final PluginProperty pluginProperty;
    private final RestClient restClient;

    public PluginServiceImpl(PluginManager pluginManager,
                             DiscoveryClient discoveryClient,
                             ObjectProvider<SfcInternalTokenIssuer> issuerProvider,
                             PluginProperty pluginProperty) {
        this.pluginManager = pluginManager;
        this.discoveryClient = discoveryClient;
        this.pluginProperty = pluginProperty;
        this.restClient = RestClient.builder()
                .requestInitializers(initializers -> {
                    SfcInternalTokenIssuer issuer = issuerProvider.getIfAvailable();
                    if (issuer != null) {
                        initializers.add(new SfcInternalTokenRequestInitializer(issuer));
                    }
                })
                .build();
    }

    @Override
    public Resource getPluginFile(String name) throws IOException {
        PluginInfo pluginInfo = pluginManager.getPluginInfo(name);
        if (pluginInfo == null) {
            throw new SfcJsonException("插件不存在");
        }
        return new UrlResource(pluginInfo.getUrl());
    }

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
        return pluginManager.getPluginResource(name, SfcStringUtils.appendPath("static", path));
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

        // 获取所有随sfc-core主进程加载的插件中，筛选出配置了自动加载的插件
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


        // 获取在微服务网络中注册的所有插件服务
        discoveryClient.getServices()
                .stream()
                .map(serviceName -> discoveryClient.getInstances(serviceName).stream().findAny())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(serviceInstance -> {
                    // 未在服务发现的元数据中声明自动加载端点的跳过处理
                    String autoload = Optional
                            .ofNullable(serviceInstance.getMetadata())
                            .orElseGet(Collections::emptyMap)
                            .get(SfcServiceMetaKey.PLUGIN_AUTOLOAD);
                    if (!StringUtils.hasText(autoload)) {
                        return;
                    }
                    Arrays.stream(autoload.split(","))
                            .filter(e -> e.endsWith(suffix))
                            .forEach(endpoint -> {
                                try {
                                    String uri = new URI("http", null, serviceInstance.getHost(), serviceInstance.getPort(), endpoint, null, null).toString();
                                    String body = restClient.get()
                                            .uri(uri)
                                            .retrieve()
                                            .toEntity(String.class)
                                            .getBody();
                                    mergeResult.append("/* ").append(serviceInstance.getServiceId()).append(" */\n");
                                    mergeResult.append(body).append("\n");
                                } catch (Exception e) {
                                    log.error("{}从微服务插件{}拉取资源{}失败", LOG_PREFIX, serviceInstance.getServiceId(), endpoint, e);
                                }
                            });
                });
        return ResourceUtils.stringToResource(mergeResult.toString())
                .setResponseFilename(fileName);
    }

    @Override
    public PluginInfoVo uploadPlugin(Resource resource) throws IOException {
        // 临时保存
        long tempId = SfcIdUtil.getId();
        Path tempPath = Paths.get(SfcStringUtils.appendPath(PathUtils.getTempDirectory(), tempId + ".jar"));
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
        Path tempPath = Paths.get(SfcStringUtils.appendPath(PathUtils.getTempDirectory(), tempId + ".jar"));
        if (!Files.exists(tempPath)) {
            throw new IllegalArgumentException("无效的临时id");
        }
        Resource pathResource = new FileSystemResource(tempPath){
            @Override
            public String getFilename() {
                return fileName;
            }};

        pluginManager.installPlugin(pathResource);
        Files.deleteIfExists(tempPath);
    }
}
