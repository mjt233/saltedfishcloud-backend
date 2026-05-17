package com.sfc.mcp.service;

import com.sfc.mcp.model.McpProperty;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

@RequiredArgsConstructor
public class McpUploadService {
    private final McpProperty mcpProperty;

    /**
     * 获取上传文件的提示信息，包含上传接口的URL、请求方法、请求头和请求体格式等说明。
     */
    public String getUploadPrompt(HttpServletRequest request) {
        String uploadUrl = mcpProperty.getOauthUploadApiUrl();
        if (StringUtils.hasText(uploadUrl) && !uploadUrl.startsWith("http://") && !uploadUrl.startsWith("https://")) {
            uploadUrl = UriComponentsBuilder.fromUriString(request.getRequestURL().toString())
                    .replacePath(mcpProperty.getOauthUploadApiUrl())
                    .build()
                    .toString();
        }
        String apiTicket = request.getHeader(HttpHeaders.AUTHORIZATION).split(" ")[1];

        return """
需要通过http请求将文件上传到 %s。该接口要求如下：
## Request Method
POST

## Request Header

- authorization: ApiTicket %s

## Request body

请求体表单类型为 `multipart/form-data`，包含以下字段：
| 字段 | 含义 |
| --- | ----|
| uid | 咸鱼云网盘的用户id，表示上传到哪个用户的网盘中。0表示公共网盘/公共资源。|
| path | 文件所在目录的路径（不包含文件名本身，不需要进行URL编码） |
| file | 要上传的文件 |
               """.formatted(uploadUrl, apiTicket);
    }
}
