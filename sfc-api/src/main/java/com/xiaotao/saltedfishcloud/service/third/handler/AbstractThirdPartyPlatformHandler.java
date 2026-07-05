package com.xiaotao.saltedfishcloud.service.third.handler;

import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAuthPlatform;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;
import com.xiaotao.saltedfishcloud.service.ProxyInfoService;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorage;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorageInject;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyPlatformHandler;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.StreamCopyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamSource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * 第三方平台登录处理接口模板类
 *
 * @param <T> 第三方平台定制参数配置的实体对象
 */
@Slf4j
public abstract class AbstractThirdPartyPlatformHandler<T> implements ThirdPartyPlatformHandler {
    private final ProxyInfoService proxyInfoService;

    /**
     * 第三方平台头像缓存附属存储。
     */
    @AttachStorageInject(value = "third_platform_avatar", name = "第三方平台头像缓存", description = "第三方登录头像缓存")
    private AttachStorage thirdPlatformAvatarStorage;

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
    protected final RestClient getRestClient(ThirdPartyAuthPlatform thirdPartyAuthPlatform) {
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
     *
     * @param platform 第三方平台原始配置对象
     * @param property 解析的定制配置对象，见{@link #getProperty(ThirdPartyAuthPlatform)}
     */
    protected abstract boolean isConfigurationIncomplete(ThirdPartyAuthPlatform platform, @Nullable T property);

    /**
     * 获取跳转第三方平台的登录认证url
     *
     * @param platform 第三方平台原始配置对象
     * @param property 解析的定制配置对象，见{@link #getProperty(ThirdPartyAuthPlatform)}
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
     *
     * @param thirdPartyAuthPlatform 第三方平台基础配置
     * @param property               第三方平台的定制配置参数
     * @param platformCallbackParam  第三方平台重定向回调时通过URL传递的 QueryString 参数
     * @return 登录成功，返回第三方授权用户信息
     */
    protected abstract ThirdPartyPlatformUser handleCallback(ThirdPartyAuthPlatform thirdPartyAuthPlatform, T property, Map<String, Object> platformCallbackParam) throws IOException;

    @Override
    public ThirdPartyPlatformUser callback(ThirdPartyAuthPlatform partyAuthPlatform, Map<String, Object> platformCallbackParam) throws IOException {
        T property = getProperty(partyAuthPlatform);
        if (isConfigurationIncomplete(partyAuthPlatform, property)) {
            throw new IllegalArgumentException("第三方平台" + getType() + "登录参数未配置");
        }
        ThirdPartyPlatformUser user = handleCallback(partyAuthPlatform, property, platformCallbackParam);
        if (user != null) {
            // 缓存用户头像
            Optional.ofNullable(cacheAvatar(partyAuthPlatform, user))
                    .filter(s -> !s.isEmpty())
                    .ifPresent(user::setAvatarUrl);
        }
        return user;
    }


    /**
     * 缓存用户头像，将头像数据缓存到网盘的存储系统并转为base64
     *
     * @param platform 第三方平台配置对象
     * @param user     第三方平台用户对象，包含头像URL
     * @return 头像的base64。如果无法生成或生成失败返回null即可
     */
    protected String cacheAvatar(ThirdPartyAuthPlatform platform, ThirdPartyPlatformUser user) {
        String avatarUrl = user.getAvatarUrl();
        if (!StringUtils.hasText(avatarUrl) || avatarUrl.startsWith("data:")) {
            return null;
        }

        try {
            String cacheFilePath = SecureUtils.getMd5(avatarUrl);
            InputStreamSource inputStreamSource;
            if (!thirdPlatformAvatarStorage.exist(cacheFilePath)) {
                // 未缓存头像，从原始URL读取数据后存入

                getRestClient(platform)
                        .get()
                        .uri(avatarUrl)
                        .exchange((req, resp) -> {
                            if (resp.getStatusCode().is2xxSuccessful()) {
                                thirdPlatformAvatarStorage.saveFile(cacheFilePath, out -> {
                                    try (InputStream is = resp.getBody()) {
                                        return new StreamCopyResult(StreamUtils.copy(is, out), null);
                                    }
                                });
                                return true;
                            }
                            return false;
                        });

            }
            inputStreamSource = thirdPlatformAvatarStorage.getFile(cacheFilePath).orElse(null);
            if (inputStreamSource == null) {
                return null;
            }
            try (InputStream is = inputStreamSource.getInputStream()) {
                String res = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(StreamUtils.copyToByteArray(is));
                is.close();
                return res;
            }
        } catch (Exception e) {
            log.error("缓存第三方平台头像失败", e);
            return null;
        }
    }
}
