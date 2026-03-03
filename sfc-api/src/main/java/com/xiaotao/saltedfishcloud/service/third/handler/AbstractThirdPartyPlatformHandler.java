package com.xiaotao.saltedfishcloud.service.third.handler;

import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAuthPlatform;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;
import com.xiaotao.saltedfishcloud.service.ProxyInfoService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyPlatformHandler;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.Proxy;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * 第三方平台登录处理接口模板类
 * @param <T>   第三方平台定制参数配置的实体对象
 */
public abstract class AbstractThirdPartyPlatformHandler<T> implements ThirdPartyPlatformHandler {
    private final ProxyInfoService proxyInfoService;

    protected AbstractThirdPartyPlatformHandler(ProxyInfoService proxyInfoService) {
        this.proxyInfoService = proxyInfoService;
    }

    private final static RestClient defaultRestClient = RestClient.builder()
                .requestFactory(newDefaultRequestFactory(null))
                .build();

    private static SimpleClientHttpRequestFactory newDefaultRequestFactory(Proxy proxy) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(10));
        factory.setConnectTimeout(Duration.ofSeconds(10));
        if (proxy != null) {
            factory.setProxy(proxy);
        }
        return factory;
    }

    /**
     * 获取http客户端，用于给第三方平台进行http请求。如果第三方平台配置了代理节点，则会使用代理节点进行http请求
     */
    protected final RestClient getHttpClient(ThirdPartyAuthPlatform thirdPartyAuthPlatform) {
        Proxy proxy = Optional.ofNullable(thirdPartyAuthPlatform.getProxyId())
                .map(proxyInfoService::findById)
                .map(ProxyInfo::toProxy)
                .orElse(null);
        if (proxy == null) {
            return defaultRestClient;
        }
        return RestClient.builder()
                .requestFactory(newDefaultRequestFactory(proxy))
                .build();
    }

    /**
     * 根据第三方平台配置参数，获取对应的定制配置信息对象
     */
    protected abstract T getProperty(ThirdPartyAuthPlatform platform);

    /**
     * 判断第三方平台配置参数是否为不完整的，如果不完整则不会执行认证逻辑和展示该平台信息
     * @param platform  第三方平台原始配置对象
     * @param property  解析的定制配置对象，见{@link #getProperty(ThirdPartyAuthPlatform)}
     */
    protected abstract boolean isConfigurationIncomplete(ThirdPartyAuthPlatform platform, T property);

    /**
     * 获取跳转第三方平台的登录认证url
     * @param platform  第三方平台原始配置对象
     * @param property  解析的定制配置对象，见{@link #getProperty(ThirdPartyAuthPlatform)}
     */
    protected abstract String getAuthenticationUrl(ThirdPartyAuthPlatform platform, T property);

    @Override
    public String getAuthUrl(ThirdPartyAuthPlatform partyAuthPlatform) {
        T property = getProperty(partyAuthPlatform);
        if (isConfigurationIncomplete(partyAuthPlatform, property)) {
            return null;
        }
        return this.getAuthenticationUrl(partyAuthPlatform, property);
    }

    /**
     * 处理第三方平台认证授权完成后的重定向回调
     * @param thirdPartyAuthPlatform    第三方平台基础配置
     * @param property                  第三方平台的定制配置参数
     * @param platformCallbackParam     第三方平台重定向回调时通过URL传递的 QueryString 参数
     * @return  登录成功，返回第三方授权用户信息
     */
    protected abstract ThirdPartyPlatformUser handleCallback(ThirdPartyAuthPlatform thirdPartyAuthPlatform, T property, Map<String, Object> platformCallbackParam) throws IOException;

    @Override
    public ThirdPartyPlatformUser callback(ThirdPartyAuthPlatform partyAuthPlatform, Map<String, Object> platformCallbackParam) throws IOException {
        T property = getProperty(partyAuthPlatform);
        if (isConfigurationIncomplete(partyAuthPlatform, property)) {
            throw new IllegalArgumentException("第三方平台" + getType() + "登录参数未配置");
        }
        return null;
    }
}
