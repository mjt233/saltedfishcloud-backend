package com.sfc.demo.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微服务示例：供网关验收使用的探针端点。
 *
 * <h3>身份读取：框架期的「信任模式」</h3>
 * <p>
 * 当前用户 id 来自<b>网关注入</b>的 {@code X-Sfc-Uid} 头，本服务<b>不做任何校验</b>——
 * 服务间调用鉴权整体后置（见 {@code docs/plan/microservice/microservice-roadmap.md} 阶段 5）。
 * </p>
 * <p>
 * ⚠️ <b>硬约束</b>：<b>不得</b>从请求参数里读取任何名为 {@code uid} 的值——否则"篡改 uid 越权"
 * 又会出现一个入口。uid 只能来自网关注入的头。
 * </p>
 * <p>
 * ⚠️ <b>部署约束</b>：信任模式下任何能直连本服务端口的人都能伪造该头，因此本服务
 * <b>只允许内网 / 本机暴露</b>，对外入口只有网关。
 * </p>
 *
 * <h3>探针清单</h3>
 * <table border="1">
 *     <caption>端点与用途</caption>
 *     <tr><th>端点</th><th>用途</th></tr>
 *     <tr><td>{@code GET /api/demo/whoami}</td><td>回显当前 uid（来自网关注入的头）</td></tr>
 *     <tr><td>{@code GET /api/demo/echo}</td><td>回显原始 URI、查询串、Cookie 与关键请求头，验证网关的剥离与覆盖策略</td></tr>
 *     <tr><td>{@code POST /api/demo/echo-body}</td><td>统计请求体字节数与 SHA-256，验证请求体流式转发无截断 / 重复</td></tr>
 *     <tr><td>{@code GET /api/demo/stream?bytes=N}</td><td>流式返回 N 字节，验证响应方向不做全量缓冲</td></tr>
 * </table>
 */
@RestController
@RequestMapping(DemoController.PREFIX)
public class DemoController {

    /**
     * 本服务的业务路径前缀（{@code /api/demo}）。
     * <p>
     * ⚠️ 必须与 {@code application.yml} 中实例 metadata 声明的 {@code sfc.api.prefix: /api} 一致：
     * 网关转发时用的是 core 的 resolve 端点拼好的完整下游路径（{@code sfc.api.prefix} ＋ 路径余部），
     * 微服务这边不能再"少一段"——否则表现为经网关访问一律 404，而直连本服务却正常。
     * </p>
     */
    public static final String PREFIX = "/api/demo";

    /** 网关注入的用户身份头（框架期信任模式下的唯一身份来源） */
    public static final String HEADER_UID = "X-Sfc-Uid";

    /**
     * 本实例的 HTTP 端口。
     * <p>仅用于验收取证：同一服务名起多个实例时，靠它判断"请求落到了哪个实例"（轮询与故障转移）。</p>
     */
    @Value("${server.port:0}")
    private int serverPort;

    /** 流式响应 / 请求体拷贝的缓冲区大小（8KB，与网关侧一致，不随数据量增长） */
    private static final int PIPE_BUFFER_SIZE = 8192;

    /** 会对身份与代理行为产生影响的观察头 */
    private static final List<String> OBSERVED_HEADERS = List.of(
            "host", "x-sfc-uid", "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto",
            "authorization", "token", "te", "connection", "transfer-encoding");

    /**
     * 回显当前用户。
     *
     * @param request HTTP 请求
     * @return 当前 uid 及其来源说明
     */
    @GetMapping("/whoami")
    public Map<String, Object> whoami(HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        String rawUid = request.getHeader(HEADER_UID);
        result.put("uid", rawUid);
        result.put("uidSource", HEADER_UID);
        result.put("rawUidHeader", rawUid);
        result.put("trustMode", true);
        result.put("instancePort", serverPort);
        result.put("note", "uid 只来自网关注入的头；本服务不校验任何凭证（服务间鉴权已后置）");
        return result;
    }

    /**
     * 回显请求的可观测信息：原始 URI、查询串、Cookie 与关键请求头。
     * <p>
     * 这是验收"网关是否剥离了入站身份头 / 会话凭证"、"hop-by-hop 是否被剥离"、
     * "{@code X-Forwarded-*} 是否为覆盖式"的主要取证端点。
     * </p>
     *
     * @param request HTTP 请求
     * @return 观测结果
     */
    @GetMapping("/echo")
    public Map<String, Object> echo(HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", request.getMethod());
        result.put("requestUri", request.getRequestURI());
        result.put("queryString", request.getQueryString());
        result.put("uid", request.getHeader(HEADER_UID));
        result.put("instancePort", serverPort);

        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : OBSERVED_HEADERS) {
            headers.put(name, request.getHeader(name));
        }
        result.put("observedHeaders", headers);

        List<String> cookies = new ArrayList<>();
        Cookie[] requestCookies = request.getCookies();
        if (requestCookies != null) {
            for (Cookie cookie : requestCookies) {
                cookies.add(cookie.getName());
            }
        }
        Collections.sort(cookies);
        result.put("cookieNames", cookies);
        return result;
    }

    /**
     * 统计请求体的字节数与 SHA-256。
     * <p>
     * 用途：验证网关的请求体转发是"逐字节保真"的——截断会让长度变化，重复发送会让摘要变化。
     * </p>
     *
     * @param request HTTP 请求
     * @return 字节数与 SHA-256
     * @throws IOException 读取请求体失败时抛出
     */
    @PostMapping("/echo-body")
    public Map<String, Object> echoBody(HttpServletRequest request) throws IOException {
        MessageDigest digest = sha256();
        long total = 0;
        byte[] buffer = new byte[PIPE_BUFFER_SIZE];
        try (InputStream in = request.getInputStream()) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                total += read;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("receivedBytes", total);
        result.put("sha256", toHex(digest.digest()));
        result.put("uid", request.getHeader(HEADER_UID));
        return result;
    }

    /**
     * 流式返回指定字节数的响应体（内容为可打印字符）。
     * <p>
     * 用途：验证网关与前端链路不做全量缓冲——把响应体调到 1GB 级别，配合网关的
     * {@code -Xmx512m} 运行参数即可证明内存占用与响应体大小无关。
     * </p>
     *
     * @param bytes 响应体字节数
     * @param response HTTP 响应
     * @throws IOException 写出失败时抛出
     */
    @GetMapping("/stream")
    public void stream(@RequestParam(defaultValue = "1048576") int bytes,
                       jakarta.servlet.http.HttpServletResponse response) throws IOException {
        response.setStatus(200);
        response.setContentType("application/octet-stream");
        response.setContentLength(bytes);
        byte[] block = "abcdefghijklmnopqrstuvwxyz0123456789".getBytes(StandardCharsets.US_ASCII);
        try (OutputStream out = response.getOutputStream()) {
            long remaining = bytes;
            while (remaining > 0) {
                int chunk = (int) Math.min(block.length, remaining);
                out.write(block, 0, chunk);
                remaining -= chunk;
            }
            out.flush();
        }
    }

    /**
     * 取 SHA-256 摘要器。
     *
     * @return 摘要器
     */
    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 字节数组转十六进制字符串。
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
