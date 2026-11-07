package com.sfc.download.controller;

import com.sfc.cloud.remote.client.SfcCoreConfigFeignClient;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.ConfigNode;
import com.sfc.common.model.PluginConfigNodeInfo;
import com.sfc.common.model.PluginInfo;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.PropertyUtils;
import com.sfc.common.utils.SfcMapperHolder;
import com.sfc.common.utils.SfcStringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 微服务元信息控制器：向 {@code sfc-core} 暴露本服务的配置面板声明。
 *
 * <p><b>调用链：</b>本服务在注册中心 metadata 中声明
 * {@code sfc.plugin.info=/api/meta/pluginInfo}；core 的
 * {@code ConfigController#listServicesPluginConfig()} 发现该 metadata 后，通过负载均衡
 * 直接请求本接口，拿到 {@link PluginConfigNodeInfo} 后在管理员后台渲染「插件配置」面板。
 * 管理员保存时写入 core 的系统配置表，本服务再经
 * {@link com.sfc.download.config.SfcDownloadConfigService} 读取。</p>
 *
 * <p><b>⚠️ 响应契约：</b>本接口必须返回<b>裸对象</b> {@link PluginConfigNodeInfo}，
 * <b>不能</b>套 {@link SfcJsonResult} 信封——core 侧使用
 * {@code restClient...toEntity(PluginConfigNodeInfo.class)} 直接反序列化，
 * 套了信封会导致字段全部取不到、面板空白。这是与其它业务接口不同的一点。</p>
 *
 * <p><b>失败语义：</b>core 配置读取失败时仍然返回节点（值留空，前端回落到各节点的
 * {@code defaultValue} 展示），不让整个配置面板 500；只有元信息文件缺失这类
 * 部署错误才抛业务异常。</p>
 */
@Slf4j
@RestController
@RequestMapping(MetaController.PREFIX)
@RequiredArgsConstructor
public class MetaController {

    /**
     * 接口前缀，与注册中心 metadata {@code sfc.plugin.info} 的声明保持一致。
     */
    public static final String PREFIX = "/api/meta";

    /**
     * 日志前缀。
     */
    private static final String LOG_PREFIX = "[Download][Meta]";

    /**
     * 配置面板声明文件。
     */
    private static final String CONFIG_PROPERTIES_FILE = "config-properties.json";

    /**
     * 服务元信息文件。
     */
    private static final String PLUGIN_INFO_FILE = "plugin-info.json";

    /**
     * core 系统配置客户端（用于把已保存的值回填进面板节点）。
     */
    private final SfcCoreConfigFeignClient configFeignClient;

    /**
     * 获取本服务的配置面板声明（含当前已保存的值）。
     *
     * @return 插件配置节点信息（裸对象，未套响应信封）
     * @throws SfcJsonException 元信息文件缺失或解析失败时抛出（HTTP 500 语义）
     */
    @GetMapping("pluginInfo")
    public PluginConfigNodeInfo pluginInfo() {
        List<ConfigNode> groups;
        PluginInfo pluginInfo;
        try {
            groups = SfcMapperHolder.parseJsonToList(readResource(CONFIG_PROPERTIES_FILE), ConfigNode.class);
            pluginInfo = SfcMapperHolder.parseJson(readResource(PLUGIN_INFO_FILE), PluginInfo.class);
        } catch (IOException e) {
            log.error("{}读取配置面板元信息失败", LOG_PREFIX, e);
            throw new SfcJsonException(500, "读取配置面板元信息失败: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("{}解析配置面板元信息失败", LOG_PREFIX, e);
            throw new SfcJsonException(500, "解析配置面板元信息失败: " + e.getMessage());
        }

        // typeRef 解引用：把 config-properties.json 里的 typeRef 展开成真正的字段节点
        PropertyUtils.dereferenceNodes(groups);
        // 回填 core 中已保存的值（失败只降级，不影响面板本身）
        fillValues(groups);

        PluginConfigNodeInfo info = new PluginConfigNodeInfo();
        info.setGroups(groups);
        info.setName(pluginInfo.getName());
        info.setAlias(pluginInfo.getAlias());
        info.setIcon(pluginInfo.getIcon());
        return info;
    }

    /**
     * 读取 classpath 下的文本资源。
     *
     * @param fileName 资源文件名（相对 classpath 根）
     * @return 资源文本内容（UTF-8）
     * @throws IOException 资源不存在或读取失败时抛出
     */
    private String readResource(String fileName) throws IOException {
        ClassPathResource resource = new ClassPathResource(fileName);
        if (!resource.exists()) {
            throw new IOException("classpath 下缺少资源文件: " + fileName);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
    }

    /**
     * 把 core 中已保存的配置值回填到面板节点上。
     *
     * <p>只回填「存在且非空」的值；未保存过的配置项保持 {@code null}，由前端展示各节点的
     * {@code defaultValue}。core 调用失败时记录 WARN 并直接返回，面板仍可展示与编辑。</p>
     *
     * @param groups 配置节点组
     */
    private void fillValues(List<ConfigNode> groups) {
        List<ConfigNode> flattenNodes = PropertyUtils.flattenNodes(groups);
        List<String> keyNames = flattenNodes.stream()
                .map(ConfigNode::getName)
                .filter(SfcStringUtils::hasText)
                .distinct()
                .toList();
        if (keyNames.isEmpty()) {
            return;
        }
        Map<String, String> values;
        try {
            SfcJsonResult<Map<String, String>> result = configFeignClient.batchGetConfig(keyNames);
            if (result == null) {
                log.warn("{}core 配置接口返回空响应，配置面板将展示默认值", LOG_PREFIX);
                return;
            }
            result.requireSuccess();
            values = result.getData() == null ? Map.of() : result.getData();
        } catch (RuntimeException e) {
            log.warn("{}读取 core 配置值失败，配置面板将展示默认值: {}", LOG_PREFIX, e.toString());
            return;
        }
        for (ConfigNode node : flattenNodes) {
            String name = node.getName();
            if (!SfcStringUtils.hasText(name)) {
                continue;
            }
            String value = values.get(name);
            if (value != null) {
                node.setValue(value);
            }
        }
    }
}
