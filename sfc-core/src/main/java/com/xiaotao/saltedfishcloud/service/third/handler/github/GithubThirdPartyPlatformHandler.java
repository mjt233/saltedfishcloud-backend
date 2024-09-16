package com.xiaotao.saltedfishcloud.service.third.handler.github;

import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAuthPlatform;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyPlatformHandler;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyPlatformManager;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.PropertyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

@Component
@Slf4j
public class GithubThirdPartyPlatformHandler implements ThirdPartyPlatformHandler, InitializingBean {
    @Autowired
    private ThirdPartyPlatformManager thirdPartyPlatformManager;

    @Autowired
    private RestTemplate restTemplate;

    private final Supplier<List<ConfigNode>> configNodeList = Lazy.of(() -> {
        List<ConfigNode> list = new ArrayList<>(PropertyUtils.getConfigNodeFromEntityClass(GithubPlatformProperty.class).values());
        return Collections.unmodifiableList(list);
    });

    @Override
    public void afterPropertiesSet() throws Exception {
        thirdPartyPlatformManager.registerPlatformHandler(this);
    }

    @Override
    public String getType() {
        return "github";
    }

    @Override
    public String getAuthUrl(ThirdPartyAuthPlatform partyAuthPlatform) {
        try {
            GithubPlatformProperty property = getProperty(partyAuthPlatform);
            return "https://github.com/login/oauth/authorize?scope=user:email&client_id=" + property.getClientId();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ThirdPartyPlatformUser callback(ThirdPartyAuthPlatform partyAuthPlatform, Map<String, Object> platformCallbackParam) throws IOException {
        if(!platformCallbackParam.containsKey("code")) {
            throw new IllegalArgumentException("缺少参数code");
        }
        String accessToken = getAccessToken(getProperty(partyAuthPlatform), platformCallbackParam.get("code").toString());
        GithubUserInfo githubUserInfo = getUserInfo(accessToken);
        return ThirdPartyPlatformUser.builder()
                .platformType(getType())
                .thirdPartyUserId(githubUserInfo.getId().toString())
                .email(githubUserInfo.getEmail())
                .userName(githubUserInfo.getLogin())
                .build();
    }

    private GithubPlatformProperty getProperty(ThirdPartyAuthPlatform platform) throws IOException {
        if (platform.getConfig() == null || platform.getConfig().isBlank() || platform.getConfig().equals("{}")) {
            throw new IllegalArgumentException("Github平台参数未配置，请联系系统管理员");
        }
        return MapperHolder.parseJson(platform.getConfig(), GithubPlatformProperty.class);
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

    private GithubUserInfo getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "token " + accessToken);
        headers.add("accept", "application/json");
        return restTemplate.exchange(
                "https://api.github.com/user",
                HttpMethod.GET,
                new HttpEntity<>(new HashMap<>(), headers),
                GithubUserInfo.class
        ).getBody();
    }

    @SuppressWarnings("unchecked")
    private String getAccessToken(GithubPlatformProperty property, String code) {
        Map<String, Object> param = new HashMap<>();
        param.put("client_id", property.getClientId());
        param.put("client_secret", property.getClientSecret());
        param.put("code", code);
        HttpHeaders headers = new HttpHeaders();
        headers.add("accept", "application/json");

        Map<String, Object> res = (Map<String, Object>)restTemplate.exchange(
                "https://github.com/login/oauth/access_token",
                HttpMethod.POST,
                new HttpEntity<>(param, headers),
                Map.class
        ).getBody();
        if (res == null || res.containsKey("error")) {
            log.error("Github登录获取accessToken出错: {}", res);
            throw new RuntimeException(res == null ? "" : String.valueOf(res.get("error_description")));
        }
        return res.get("access_token").toString();
    }

    @Override
    public List<ConfigNode> getPlatformConfigNode() {
        return configNodeList.get();
    }
}
