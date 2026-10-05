package com.sfc.ext.nacos.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.sfc.ext.nacos.NacosServiceDiscovery;
import com.sfc.ext.nacos.NacosServiceRegistry;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Nacos provider 的自动配置入口（经 {@code META-INF/spring/...AutoConfiguration.imports} 注册）。
 * <p>
 * <b>激活条件</b>：{@code sys.service.registry-provider: nacos}（<b>不</b>设 {@code matchIfMissing}）。
 * 未配置该项或配置为 {@code local} 时，本配置整体不生效——此时由 {@code sfc-core} 的
 * {@code LocalServiceRegistry} 兜底，两者<b>不会同时存在</b>（否则 {@code ServiceRegistry} /
 * {@code ServiceDiscovery} 各有两个 Bean，注入点会歧义）。
 * <p>
 * <b>fail-fast</b>：客户端初始化失败直接抛 {@link JsonException} 中止启动，而不是留下"启动了但没注册"的假成功状态
 * ——调试手册已明确"启动成功不是通过标准"。
 */
@Configuration
@Slf4j
@ConditionalOnProperty(prefix = "sys.service", name = "registry-provider", havingValue = "nacos")
@EnableConfigurationProperties(SysNacosProperties.class)
public class NacosAutoConfiguration {

    /**
     * Nacos 客户端。
     * <p>
     * {@code destroyMethod = ""} 关闭 Spring 的销毁方法推断：客户端生命周期由
     * {@link NacosServiceRegistry#close()} 统一管理，以保证"先注销实例、再关闭客户端"的顺序。
     *
     * @param properties 插件配置
     * @return Nacos 命名服务客户端
     * @throws JsonException 客户端创建失败时抛出
     */
    @Bean(destroyMethod = "")
    public NamingService namingService(SysNacosProperties properties) {
        try {
            NamingService namingService = NacosFactory.createNamingService(buildClientProperties(properties));
            log.info("[服务注册] Nacos 客户端初始化完成 serverAddr={} namespace={} cluster={} 鉴权={}",
                    properties.getServerAddr(),
                    hasText(properties.getNamespace()) ? properties.getNamespace() : "<public>",
                    properties.getClusterName(),
                    hasText(properties.getUsername()) ? "已配置" : "未配置");
            return namingService;
        } catch (NacosException e) {
            String msg = "Nacos 客户端初始化失败：serverAddr=" + properties.getServerAddr()
                    + "。请检查 sys.service.nacos.* 配置与 Nacos 服务端可达性"
                    + "（Nacos 2.x/3.x 客户端需 8848 与 9848 两个端口均可达）。原因：" + e.getMessage();
            log.error("[服务注册] {}", msg, e);
            throw new JsonException(msg);
        }
    }

    /**
     * Nacos 服务注册 provider。
     *
     * @param namingService Nacos 客户端
     * @param properties    插件配置
     * @return 服务注册实现
     */
    @Bean
    public NacosServiceRegistry nacosServiceRegistry(NamingService namingService, SysNacosProperties properties) {
        return new NacosServiceRegistry(namingService, properties);
    }

    /**
     * Nacos 服务发现 provider。
     *
     * @param namingService Nacos 客户端
     * @param sysProperties 系统配置（仅用于读取 {@code sys.service.registry-group}）
     * @return 服务发现实现
     */
    @Bean
    public NacosServiceDiscovery nacosServiceDiscovery(NamingService namingService, SysProperties sysProperties) {
        return new NacosServiceDiscovery(namingService, sysProperties);
    }

    /**
     * 组装 Nacos 客户端连接参数。
     * <p>
     * namespace / 账号密码只在非空时才写入：留空表示使用 public 命名空间与匿名访问。
     *
     * @param properties 插件配置
     * @return Nacos 客户端参数
     */
    private static Properties buildClientProperties(SysNacosProperties properties) {
        Properties nacosProperties = new Properties();
        nacosProperties.put(PropertyKeyConst.SERVER_ADDR, properties.getServerAddr());
        if (hasText(properties.getNamespace())) {
            nacosProperties.put(PropertyKeyConst.NAMESPACE, properties.getNamespace().trim());
        }
        if (hasText(properties.getUsername())) {
            nacosProperties.put(PropertyKeyConst.USERNAME, properties.getUsername().trim());
        }
        if (hasText(properties.getPassword())) {
            nacosProperties.put(PropertyKeyConst.PASSWORD, properties.getPassword());
        }
        return nacosProperties;
    }

    /**
     * 判断字符串非 null 且含非空白字符。
     *
     * @param str 待判断字符串
     * @return 非空白时返回 true
     */
    private static boolean hasText(String str) {
        return str != null && !str.isBlank();
    }
}
