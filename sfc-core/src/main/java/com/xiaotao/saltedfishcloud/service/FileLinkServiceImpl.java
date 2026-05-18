package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnsupportedProtocolException;
import com.xiaotao.saltedfishcloud.helper.RedisKeyGenerator;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.resource.FileLinkService;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 文件临时链接服务实现。
 */
@Service
@RequiredArgsConstructor
public class FileLinkServiceImpl implements FileLinkService {
    /**
     * 临时授权码在 URL Query 中使用的参数名。
     */
    public static final String TOKEN_QUERY_PARAM = "token";

    /**
     * 临时授权码长度。
     */
    private static final int TOKEN_LENGTH = 8;

    /**
     * 创建缓存 key 的最大重试次数。
     */
    private static final int MAX_RETRY = 20;

    /**
     * 缓存服务。
     */
    private final CacheService cacheService;

    /**
     * 系统资源服务。
     */
    private final ResourceService resourceService;

    /**
     * 配置服务。
     */
    private final ConfigService configService;

    @Override
    public String createLink(String baseUrl, ResourceRequest resourceRequest) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new JsonException(400, "baseUrl不能为空");
        }
        if (resourceRequest == null) {
            throw new JsonException(400, "resourceRequest不能为空");
        }

        String token = registerToken(resourceRequest);
        return appendToken(baseUrl, token);
    }

    @Override
    public Resource parseLink(String url) throws IOException {
        String token = extractToken(url);
        if (!StringUtils.hasText(token)) {
            throw new JsonException(400, "链接无效或已过期");
        }

        ResourceRequest request = cacheService.get(RedisKeyGenerator.getFileLinkKey(token));
        if (request == null) {
            throw new JsonException(400, "链接无效或已过期");
        }

        Resource resource;
        try {
            resource = resourceService.getResource(request);
        } catch (UnsupportedProtocolException e) {
            throw new JsonException(400, "链接无效或已过期");
        }
        if (resource == null) {
            throw new JsonException(400, "链接无效或已过期");
        }
        return resource;
    }

    /**
     * 注册临时授权码并将资源请求参数写入缓存。
     *
     * @param resourceRequest 资源请求参数
     * @return 临时授权码
     */
    private String registerToken(ResourceRequest resourceRequest) {
        long expireMinutes = getLinkExpireMinutes();
        for (int i = 0; i < MAX_RETRY; i++) {
            String token = StringUtils.getRandomString(TOKEN_LENGTH);
            boolean success = cacheService.setIfAbsent(
                    RedisKeyGenerator.getFileLinkKey(token),
                    resourceRequest,
                    expireMinutes,
                    TimeUnit.MINUTES
            );
            if (success) {
                return token;
            }
        }
        throw new JsonException(500, "创建临时链接失败，请稍后重试");
    }

    /**
     * 为基础 URL 追加临时授权码参数。
     *
     * @param baseUrl 基础 URL
     * @param token   临时授权码
     * @return 完整 URL
     */
    private String appendToken(String baseUrl, String token) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .replaceQueryParam(TOKEN_QUERY_PARAM)
                .queryParam(TOKEN_QUERY_PARAM, token)
                .build()
                .toUriString();
    }

    /**
     * 从完整 URL 中提取临时授权码。
     *
     * @param url 完整 URL
     * @return 临时授权码
     */
    private String extractToken(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }

        try {
            return UriComponentsBuilder.fromUriString(url)
                    .build()
                    .getQueryParams()
                    .getFirst(TOKEN_QUERY_PARAM);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取临时链接有效期。
     *
     * @return 有效期（分钟）
     */
    private long getLinkExpireMinutes() {
        Long expireMinutes = configService.getConfig(SysCommonConfig::getFileLinkExpireMinutes, 10L);
        if (expireMinutes == null || expireMinutes <= 0) {
            return 120L;
        }
        return expireMinutes;
    }
}



