package com.xiaotao.saltedfishcloud.service.third.handler.google;

import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAuthPlatform;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;
import com.xiaotao.saltedfishcloud.service.ProxyInfoService;
import com.xiaotao.saltedfishcloud.service.third.handler.AbstractThirdPartyPlatformHandler;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.PropertyUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

@Component
@Slf4j
public class GoogleThirdPartyPlatformHandler extends AbstractThirdPartyPlatformHandler<GooglePlatformProperty> {

    private final Supplier<List<ConfigNode>> configNodeList = Lazy.of(() -> {
        List<ConfigNode> list = new ArrayList<>(PropertyUtils.getConfigNodeFromEntityClass(GooglePlatformProperty.class).values());
        return Collections.unmodifiableList(list);
    });

    @Autowired
    public GoogleThirdPartyPlatformHandler(ProxyInfoService proxyInfoService) {
        super(proxyInfoService);
    }

    @Override
    public String getType() {
        return "google";
    }

    @Override
    protected GooglePlatformProperty getProperty(ThirdPartyAuthPlatform platform) {
        try {
            if (platform == null || platform.getConfig() == null) {
                return null;
            }
            return MapperHolder.parseJson(platform.getConfig(), GooglePlatformProperty.class);
        } catch (IOException e) {
            throw new RuntimeException("解析Google平台配置失败", e);
        }
    }

    @Override
    protected boolean isConfigurationIncomplete(ThirdPartyAuthPlatform platform,@Nullable GooglePlatformProperty property) {
        return property == null ||
                !StringUtils.hasText(property.getRedirectUrl()) ||
                !StringUtils.hasText(property.getClientId()) ||
                !StringUtils.hasText(property.getClientSecret());
    }

    @Override
    protected String getAuthenticationUrl(ThirdPartyAuthPlatform platform, GooglePlatformProperty property) {
        return "https://accounts.google.com/o/oauth2/auth?client_id=" + property.getClientId()
                + "&redirect_uri=" + URLEncoder.encode(Optional.ofNullable(property.getRedirectUrl()).orElse(""), StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + URLEncoder.encode("https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email openid", StandardCharsets.UTF_8);
    }

    @Override
    protected ThirdPartyPlatformUser handleCallback(ThirdPartyAuthPlatform thirdPartyAuthPlatform, GooglePlatformProperty property, Map<String, Object> platformCallbackParam) throws IOException {
        String code = Objects.requireNonNull(TypeUtils.toString(platformCallbackParam.get("code")), "缺少参数 code");

        // 获取访问令牌
        String accessToken = getAccessToken(thirdPartyAuthPlatform, property, code);
        
        // 获取用户信息
        GoogleUserInfo userInfo = getUserInfo(thirdPartyAuthPlatform, accessToken);

        return ThirdPartyPlatformUser.builder()
                .platformType(getType())
                .thirdPartyUserId(userInfo.getSub())
                .email(userInfo.getEmail())
                .userName(userInfo.getName())
                .avatarUrl(userInfo.getPicture())
                .build();
    }

    /**
     * 获取Google访问令牌
     */
    @SuppressWarnings("unchecked")
    private String getAccessToken(ThirdPartyAuthPlatform platform, GooglePlatformProperty property, String code) throws IOException {
        String url = "https://oauth2.googleapis.com/token";
        
        String requestBody = "client_id=" + property.getClientId() +
                "&client_secret=" + property.getClientSecret() +
                "&code=" + code +
                "&grant_type=authorization_code" +
                "&redirect_uri=" + java.net.URLEncoder.encode(property.getRedirectUrl(), java.nio.charset.StandardCharsets.UTF_8);
        
        try {
            RestClient restClient = getRestClient(platform);
            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
            
            if (response == null || response.containsKey("error")) {
                log.error("Google登录获取accessToken出错: {}", response);
                throw new RuntimeException(response == null ? "获取访问令牌失败" : String.valueOf(response.get("error_description")));
            }
            return response.get("access_token").toString();
        } catch (Exception e) {
            log.error("Google登录获取accessToken异常", e);
            throw new IOException("获取访问令牌失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取Google用户信息
     */
    private GoogleUserInfo getUserInfo(ThirdPartyAuthPlatform platform, String accessToken) throws IOException {
        String url = "https://www.googleapis.com/oauth2/v3/userinfo";
        
        try {
            RestClient restClient = getRestClient(platform);
            return restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(GoogleUserInfo.class);
        } catch (Exception e) {
            log.error("获取Google用户信息异常", e);
            throw new IOException("获取用户信息失败: " + e.getMessage(), e);
        }
    }
    


    @Override
    public ThirdPartyAuthPlatform getDefaultConfig() {
        return ThirdPartyAuthPlatform.builder()
                .type(getType())
                .isAllowRegister(true)
                .isEnable(false)
                .name("Google")
                .icon("mdi-google")
                .build();
    }

    @Override
    public List<ConfigNode> getPlatformConfigNode() {
        return configNodeList.get();
    }
}
