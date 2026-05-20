package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnsupportedProtocolException;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.resource.FileLinkService;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

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
     * 链接无效提示。
     */
    private static final String INVALID_LINK_MSG = "链接无效或已过期";

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
        Resource resource;
        try {
            // 确保创建链接的会话能够根据该参数读取资源，证明有权操作读取
            resource = resourceService.getResource(resourceRequest);
        } catch (UnsupportedProtocolException e) {
            throw new JsonException(400, "无效的资源协议");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (resource == null) {
            throw new JsonException(FileSystemError.FILE_NOT_FOUND);
        }

        String token = createToken(resourceRequest);
        return appendToken(baseUrl, token);
    }

    @Override
    public Resource parseLink(String url) throws IOException {
        String token = extractToken(url);
        if (!StringUtils.hasText(token)) {
            throw new JsonException(400, INVALID_LINK_MSG);
        }
        ResourceRequest request = parseRequestFromToken(token);

        Resource resource;
        try {
            resource = resourceService.getResource(request);
        } catch (UnsupportedProtocolException e) {
            throw new JsonException(400, INVALID_LINK_MSG);
        }
        if (resource == null) {
            throw new JsonException(400, INVALID_LINK_MSG);
        }
        return resource;
    }

    /**
     * 创建用于下载链接的 JWT 临时授权码。
     *
     * @param resourceRequest 资源请求参数
     * @return 临时授权码
     */
    private String createToken(ResourceRequest resourceRequest) {
        long expireSeconds = getLinkExpireMinutes() * 60L;
        int jwtExpireSeconds = expireSeconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) expireSeconds;
        String payload = MapperHolder.toJsonNoEx(resourceRequest);
        return JwtUtils.generateToken(payload, jwtExpireSeconds);
    }

    /**
     * 从 JWT 临时授权码中解析资源请求参数。
     *
     * @param token 临时授权码
     * @return 资源请求参数
     */
    private ResourceRequest parseRequestFromToken(String token) {
        try {
            String payload = JwtUtils.parse(token);
            if (!StringUtils.hasText(payload)) {
                throw new JsonException(400, INVALID_LINK_MSG);
            }
            return MapperHolder.parseAsJson(payload, ResourceRequest.class);
        } catch (Exception e) {
            throw new JsonException(400, INVALID_LINK_MSG);
        }
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



