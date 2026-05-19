package com.sfc.mcp.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * MCP OAuth 回调静态资源控制器。
 * <p>
 * 将 {@code /api/mcpOAuthCallback} 路径映射到插件 classpath 中
 * {@code static/mcpOAuthCallback} 目录下的静态资源（对应源码目录
 * {@code src/main/assert/static/mcpOAuthCallback}）。
 * 访问根路径时默认返回 {@code index.html}。
 * </p>
 */
@Controller
@RequestMapping("/api/mcpOAuthCallback")
public class McpOAuthCallbackStaticController {

    /** classpath 中静态资源的前缀目录 */
    private static final String CLASSPATH_PREFIX = "static/mcpOAuthCallback";

    /** URL 前缀，用于截取文件相对路径 */
    private static final String URL_PREFIX = "/api/mcpOAuthCallback/";

    /**
     * 处理对 {@code /api/mcpOAuthCallback} 及 {@code /api/mcpOAuthCallback/} 的访问，
     * 默认返回 {@code index.html}。
     *
     * @param response HTTP 响应
     * @throws IOException 读取资源或写入响应时发生的 IO 异常
     */
    @GetMapping({"", "/"})
    @AllowAnonymous
    public ResponseEntity<Resource> serveIndex(HttpServletResponse response) throws IOException {
        return serveResource("index.html", response);
    }

    /**
     * 处理对 {@code /api/mcpOAuthCallback/**} 的静态资源请求。
     * <p>
     * 从 classpath 的 {@code static/mcpOAuthCallback/} 目录下读取对应文件并写入响应。
     * 若路径为空则默认返回 {@code index.html}；若路径包含 {@code ..} 则返回 400。
     * 若文件不存在则返回 404。
     * </p>
     *
     * @param request  HTTP 请求，用于获取请求 URI
     * @param response HTTP 响应
     * @throws IOException 读取资源或写入响应时发生的 IO 异常
     */
    @GetMapping("/**")
    @AllowAnonymous
    public ResponseEntity<Resource> serveStatic(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String requestUri = request.getRequestURI();
        String filePath;
        if (requestUri.startsWith(URL_PREFIX)) {
            filePath = requestUri.substring(URL_PREFIX.length());
        } else {
            filePath = "index.html";
        }
        if (filePath.isEmpty()) {
            filePath = "index.html";
        }
        // 防止路径遍历攻击
        if (filePath.contains("..")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        return serveResource(filePath, response);
    }

    /**
     * 从 classpath 中读取指定文件并写入 HTTP 响应。
     *
     * @param filePath 相对于 {@code static/mcpOAuthCallback/} 的文件路径
     * @param response HTTP 响应
     * @throws IOException 读取资源或写入响应时发生的 IO 异常
     */
    private ResponseEntity<Resource> serveResource(String filePath, HttpServletResponse response) throws IOException {
        ClassPathResource resource = new ClassPathResource(CLASSPATH_PREFIX + "/" + filePath, this.getClass().getClassLoader());
        if (!resource.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        return ResourceUtils.wrapResource(resource);
    }
}

