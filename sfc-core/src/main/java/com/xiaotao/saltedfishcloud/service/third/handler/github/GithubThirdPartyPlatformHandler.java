package com.xiaotao.saltedfishcloud.service.third.handler.github;

import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAuthPlatform;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;
import com.xiaotao.saltedfishcloud.service.ProxyInfoService;
import com.xiaotao.saltedfishcloud.service.third.handler.AbstractThirdPartyPlatformHandler;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.PropertyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Lazy;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

@Component
@Slf4j
public class GithubThirdPartyPlatformHandler extends AbstractThirdPartyPlatformHandler<GithubPlatformProperty> {

    @Autowired
    public GithubThirdPartyPlatformHandler(ProxyInfoService proxyInfoService) {
        super(proxyInfoService);
    }

    private final Supplier<List<ConfigNode>> configNodeList = Lazy.of(() -> {
        List<ConfigNode> list = new ArrayList<>(PropertyUtils.getConfigNodeFromEntityClass(GithubPlatformProperty.class).values());
        return Collections.unmodifiableList(list);
    });

    @Override
    public String getType() {
        return "github";
    }

    @Override
    protected String getAuthenticationUrl(ThirdPartyAuthPlatform platform, GithubPlatformProperty property) {
        return "https://github.com/login/oauth/authorize?scope=user:email&client_id=" + property.getClientId();
    }

    @Override
    protected ThirdPartyPlatformUser handleCallback(ThirdPartyAuthPlatform thirdPartyAuthPlatform, GithubPlatformProperty property, Map<String, Object> platformCallbackParam) throws IOException {
        String code = platformCallbackParam.get("code").toString();
        String accessToken = getAccessToken(thirdPartyAuthPlatform, property, code);
        GithubUserInfo githubUserInfo = getUserInfo(thirdPartyAuthPlatform, accessToken);
        return ThirdPartyPlatformUser.builder()
                .platformType(getType())
                .thirdPartyUserId(githubUserInfo.getId().toString())
                .email(githubUserInfo.getEmail())
                .userName(githubUserInfo.getLogin())
                .avatarUrl(githubUserInfo.getAvatarUrl())
                .build();
    }

    @Override
    protected boolean isConfigurationIncomplete(ThirdPartyAuthPlatform platform,@Nullable GithubPlatformProperty property) {
        return property == null || property.getClientId() == null || property.getClientId().isBlank()
                || property.getClientSecret() == null || property.getClientSecret().isBlank();
    }
    
    @Override
    protected GithubPlatformProperty getProperty(ThirdPartyAuthPlatform platform) {
        try {
            if (platform == null || platform.getConfig() == null) {
                return null;
            }
            return MapperHolder.parseJson(platform.getConfig(), GithubPlatformProperty.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ThirdPartyAuthPlatform getDefaultConfig() {
        return ThirdPartyAuthPlatform.builder()
                .type(getType())
                .isAllowRegister(true)
                .isEnable(false)
                .name("Github")
                .icon("mdi-github")
                .build();
    }

    private GithubUserInfo getUserInfo(ThirdPartyAuthPlatform thirdPartyAuthPlatform, String accessToken) {
        RestClient restClient = getHttpClient(thirdPartyAuthPlatform);
        return restClient.get()
                .uri("https://api.github.com/user")
                .header("Authorization", "token " + accessToken)
                .header("accept", "application/json")
                .retrieve()
                .body(GithubUserInfo.class);
    }

    @SuppressWarnings("unchecked")
    private String getAccessToken(ThirdPartyAuthPlatform thirdPartyAuthPlatform, GithubPlatformProperty property, String code) {
        Map<String, Object> param = new HashMap<>();
        param.put("client_id", property.getClientId());
        param.put("client_secret", property.getClientSecret());
        param.put("code", code);
            
        RestClient restClient = getHttpClient(thirdPartyAuthPlatform);
        Map<String, Object> res = restClient.post()
                .uri("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(param)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    // 如果 GitHub 返回了 4xx 或 5xx，这里可以捕获
                    log.error("GitHub OAuth 响应错误状态码: {}", resp.getStatusCode());
                })
                .body(Map.class);
            
        if (res == null || res.containsKey("error")) {
            log.error("Github 登录获取 accessToken 出错：{}", res);
            throw new RuntimeException(res == null ? "" : String.valueOf(res.get("error_description")));
        }
        return res.get("access_token").toString();
    }

    @Override
    public List<ConfigNode> getPlatformConfigNode() {
        return configNodeList.get();
    }
}
