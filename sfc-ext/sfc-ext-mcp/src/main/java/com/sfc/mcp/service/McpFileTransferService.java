package com.sfc.mcp.service;

import com.sfc.mcp.model.McpProperty;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * MCP 文件传输提示服务。
 * <p>
 * 构建上传和下载文件的 HTTP 请求指引，供 MCP Prompt 引导 AI Agent 使用。
 */
@RequiredArgsConstructor
public class McpFileTransferService {
    private final McpProperty mcpProperty;

    /**
     * 获取上传文件的提示信息，包含上传接口的URL、请求方法、请求头和请求体格式等说明。
     *
     * @param request 当前 HTTP 请求，用于提取 Authorization 头和构建完整 URL
     * @return 上传指引文本
     */
    public String getUploadPrompt(HttpServletRequest request) {
        String uploadUrl = resolveUrl(request, mcpProperty.getOauthUploadApiUrl());
        String tokenValue = extractToken(request);

        return """
需要通过http请求将文件上传到 %s。该接口要求如下：
## Request Method
POST

## Request Header

- authorization: Bearer %s

## Request body

请求体表单类型为 `multipart/form-data`，包含以下字段：
| 字段 | 含义 |
| --- | ---- |
| uid | 咸鱼云网盘的用户id，表示上传到哪个用户的网盘中。0表示公共网盘/公共资源。|
| path | 文件所在目录的路径（不包含文件名本身，不需要进行URL编码） |
| file | 要上传的文件 |
               """.formatted(uploadUrl, tokenValue);
    }

    /**
     * 获取下载文件的提示信息，包含下载接口的URL、请求方法、请求头和请求参数格式等说明。
     *
     * @param request 当前 HTTP 请求，用于提取 Authorization 头和构建完整 URL
     * @return 下载指引文本
     */
    public String getDownloadPrompt(HttpServletRequest request) {
        String downloadUrl = resolveUrl(request, mcpProperty.getOauthDownloadApiUrl());
        String tokenValue = extractToken(request);

        return """
需要通过http请求从 %s 下载文件。该接口要求如下：
## Request Method
GET

## Request Header

- authorization: Bearer %s

## Request Query Parameters

| 字段 | 含义 |
| --- | ---- |
| uid | 咸鱼云网盘的用户id，表示从哪个用户的网盘下载。0表示公共网盘/公共资源。|
| path | 文件的完整路径（包含文件名），以 / 开头，例如 /document.pdf |

## 示例

curl -X GET "%s?uid=0&path=/document.pdf" -H "Authorization: Bearer %s" -o document.pdf
               """.formatted(downloadUrl, tokenValue, downloadUrl, tokenValue);
    }

    /**
     * 将配置的相对路径解析为完整 URL。
     * <p>
     * 如果配置值已是绝对路径（http/https 开头），直接返回；
     * 否则基于当前请求 URL 拼接。
     */
    private String resolveUrl(HttpServletRequest request, String configuredUrl) {
        if (StringUtils.hasText(configuredUrl) && !configuredUrl.startsWith("http://") && !configuredUrl.startsWith("https://")) {
            return UriComponentsBuilder.fromUriString(request.getRequestURL().toString())
                    .replacePath(configuredUrl)
                    .build()
                    .toString();
        }
        return configuredUrl;
    }

    /**
     * 从请求的 Authorization 头中提取 Bearer token 值。
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        return bearerToken != null ? bearerToken.substring("Bearer ".length()) : "";
    }
}
