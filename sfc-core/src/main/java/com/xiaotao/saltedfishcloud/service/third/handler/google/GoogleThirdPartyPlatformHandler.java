package com.xiaotao.saltedfishcloud.service.third.handler.google;

import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAuthPlatform;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyPlatformHandler;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.PropertyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

@Component
@Slf4j
public class GoogleThirdPartyPlatformHandler implements ThirdPartyPlatformHandler {

    @Autowired
    private RestTemplate restTemplate;

    private final Supplier<List<ConfigNode>> configNodeList = Lazy.of(() -> {
        List<ConfigNode> list = new ArrayList<>(PropertyUtils.getConfigNodeFromEntityClass(GooglePlatformProperty.class).values());
        return Collections.unmodifiableList(list);
    });

    @Override
    public String getType() {
        return "google";
    }

    @Override
    public String getAuthUrl(ThirdPartyAuthPlatform partyAuthPlatform) {
        try {
            GooglePlatformProperty property = getProperty(partyAuthPlatform);
            return "https://accounts.google.com/o/oauth2/auth?client_id=" + property.getClientId()
                    + "&redirect_uri=" + URLEncoder.encode(Optional.ofNullable(property.getRedirectUrl()).orElse(""), StandardCharsets.UTF_8)
                    + "&response_type=code"
                    + "&scope=" + URLEncoder.encode("https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email openid", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ThirdPartyPlatformUser callback(ThirdPartyAuthPlatform partyAuthPlatform, Map<String, Object> platformCallbackParam) throws IOException {
        // 验证参数
        GooglePlatformProperty property = getProperty(partyAuthPlatform);
        String code = Objects.requireNonNull(platformCallbackParam.get("code"), "服务器未收到 Google 的授权码code").toString();
        String scope = Objects.requireNonNull(platformCallbackParam.get("scope"), "服务器未收到 Google 的授权范围scope").toString();
        if (!scope.contains("email") || !scope.contains("profile")) {
            throw new IllegalArgumentException("未授权 email 或 profile 权限");
        }

        // 获取访问令牌
        String accessToken = getAccessToken(property, code);
        
        // 获取用户信息
        GoogleUserInfo userInfo = getUserInfo(accessToken);
        
        return ThirdPartyPlatformUser.builder()
                .platformType(getType())
                .thirdPartyUserId(userInfo.getSub())
                .email(userInfo.getEmail())
                .userName(userInfo.getName())
                .build();
    }


    /**
     * 获取Google访问令牌
     */
    @SuppressWarnings("unchecked")
    private String getAccessToken(GooglePlatformProperty property, String code) throws IOException {
        String url = "https://oauth2.googleapis.com/token";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        String requestBody = "client_id=" + property.getClientId() +
                "&client_secret=" + property.getClientSecret() +
                "&code=" + code +
                "&grant_type=authorization_code" +
                "&redirect_uri=" + java.net.URLEncoder.encode(property.getRedirectUrl(), java.nio.charset.StandardCharsets.UTF_8);
        
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        
        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
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
    private GoogleUserInfo getUserInfo(String accessToken) throws IOException {
        String url = "https://www.googleapis.com/oauth2/v3/userinfo";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        try {
            return restTemplate.exchange(url, HttpMethod.GET, request, GoogleUserInfo.class).getBody();
        } catch (Exception e) {
            log.error("获取Google用户信息异常", e);
            throw new IOException("获取用户信息失败: " + e.getMessage(), e);
        }
    }
    
    private boolean isNotConfig(ThirdPartyAuthPlatform platform) {
        return platform.getConfig() == null || platform.getConfig().isBlank() || platform.getConfig().equals("{}");
    }
    private GooglePlatformProperty getProperty(ThirdPartyAuthPlatform platform) throws IOException {
        if (this.isNotConfig(platform)) {
            throw new IllegalArgumentException("Google平台参数未配置，请联系系统管理员");
        }
        return MapperHolder.parseJson(platform.getConfig(), GooglePlatformProperty.class);
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
