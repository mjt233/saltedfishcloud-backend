package com.sfc.download.engine.p2p;

import com.sfc.common.utils.SfcMapperHolder;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.config.SfcDownloadConfigService;
import com.sfc.download.model.property.Aria2Property;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * aria2 JSON-RPC 客户端。
 *
 * <p>只负责「把 JSON-RPC 2.0 请求发出去、把 {@code result} 拿回来」，不含任何下载业务语义；
 * 下载流程编排在 {@link Aria2DownloadEngine} 中。</p>
 *
 * <p><b>配置读取：</b>RPC 地址与令牌来自 {@code sfc-core} 的统一配置（{@link Aria2Property}），
 * 每次调用都通过 {@link SfcDownloadConfigService} 现取，因此管理员在 core 后台修改 RPC 地址后
 * 无需重启本服务（受配置缓存 TTL 约束）。</p>
 *
 * <p><b>令牌约定：</b>aria2 的 {@code --rpc-secret} 通过 JSON-RPC 参数列表首项
 * {@code "token:<secret>"} 传递；未配置令牌时不传该项。</p>
 *
 * <p><b>并发：</b>本类是 Spring 单例且无可变状态，可被多个任务线程并发调用。</p>
 *
 * <p><b>传输层说明（踩坑记录，勿改）：</b>本类刻意<b>不使用</b> Spring 的 {@code RestClient}，原因有三：</p>
 * <ul>
 *   <li><b>不要用 {@code RestClient.body(Map)}</b>：请求体会以流式（{@code Transfer-Encoding: chunked}）发出，
 *       aria2 的 JSON-RPC 服务器不支持 chunked 请求体，会直接返回 {@code -32700 Parse error}；</li>
 *   <li><b>不要依赖响应 {@code Content-Type} 选消息转换器</b>：aria2 返回
 *       {@code application/octet-stream} 且不带 charset，{@code body(Map.class)} 甚至
 *       {@code body(String.class)} 都会抛「Error while extracting response for type ...」；</li>
 *   <li><b>不要用 {@code exchange(...)}</b>：实测在本项目环境下会触发连接被中止（{@code WSAECONNABORTED}）。</li>
 * </ul>
 * <p>因此统一使用 JDK {@link HttpClient}：请求体先序列化为 {@code byte[]} 并用
 * {@code BodyPublishers.ofByteArray} 发布（长度已知，必定携带 {@code Content-Length}，不会 chunked），
 * 响应以 {@code BodyHandlers.ofString(UTF_8)} 读取，全程不经过任何 Spring 消息转换器。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Aria2Client {

    /**
     * 建立 RPC 连接的超时时间（毫秒）。
     *
     * <p>刻意取得较短：能力探测与进度轮询都在任务关键路径上，不能因 aria2 未启动而长时间阻塞。</p>
     */
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;

    /**
     * 单次 RPC 请求的读取超时时间（毫秒）。
     */
    private static final int READ_TIMEOUT_MILLIS = 10000;

    /**
     * 配置中未填写 RPC 地址时使用的默认地址（与 aria2c 默认端口一致）。
     */
    private static final String DEFAULT_RPC_URL = "http://127.0.0.1:6800/jsonrpc";

    /**
     * aria2 RPC 令牌参数前缀。
     */
    private static final String TOKEN_PREFIX = "token:";

    /**
     * JSON-RPC 协议版本。
     */
    private static final String JSONRPC_VERSION = "2.0";

    /**
     * 异常消息中原始响应体的最大展示长度（字符）。
     *
     * <p>aria2 的错误响应可能较长，全量写进任务失败信息会污染数据库与前端展示，故截断后只保留前缀。</p>
     */
    private static final int MAX_RESPONSE_PREVIEW_LENGTH = 512;

    /**
     * 统一配置访问入口（每次调用现取 aria2 配置，不缓存配置对象）。
     */
    private final SfcDownloadConfigService configService;

    /**
     * 共享的 JDK HTTP 客户端（线程安全，可被并发复用）。
     *
     * <p>连接超时在此处统一配置；单次请求的读取超时在 {@link #call(String, List)} 里按请求设置。</p>
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MILLIS))
            .build();

    /**
     * 探测 aria2 是否可用，并取得其版本号。
     *
     * <p>能力声明与创建任务的快速失败都依赖本方法：调用 {@code aria2.getVersion}，
     * 能拿到版本号即说明 RPC 地址、令牌与 aria2 进程都正常。</p>
     *
     * @return aria2 版本号；协议未返回版本号时返回 {@code "unknown"}
     * @throws Aria2RpcException RPC 不可达、令牌错误或返回格式非法时抛出
     */
    public String getVersion() {
        Object result = call("aria2.getVersion", List.of());
        if (result instanceof Map<?, ?> versionInfo) {
            Object version = versionInfo.get("version");
            if (version != null) {
                return String.valueOf(version);
            }
        }
        return "unknown";
    }

    /**
     * 提交一个或多个 URI（磁力链、HTTP/FTP 直链等）给 aria2 下载。
     *
     * @param uris    下载地址列表，不能为空
     * @param options aria2 下载选项（键为 aria2 原始选项名），允许为 {@code null}
     * @return aria2 分配的 GID（下载任务唯一标识）
     * @throws Aria2RpcException 提交失败或返回的 GID 非法时抛出
     */
    public String addUri(List<String> uris, Map<String, String> options) {
        List<Object> params = new ArrayList<>(2);
        params.add(uris);
        params.add(options == null ? Map.of() : options);
        return requireGid(call("aria2.addUri", params));
    }

    /**
     * 提交 Base64 编码的种子文件内容给 aria2 下载。
     *
     * @param torrentBase64 种子文件内容的 Base64 编码，不能为空
     * @param options       aria2 下载选项（键为 aria2 原始选项名），允许为 {@code null}
     * @return aria2 分配的 GID（下载任务唯一标识）
     * @throws Aria2RpcException 提交失败或返回的 GID 非法时抛出
     */
    public String addTorrent(String torrentBase64, Map<String, String> options) {
        List<Object> params = new ArrayList<>(3);
        params.add(torrentBase64);
        params.add(List.of());
        params.add(options == null ? Map.of() : options);
        return requireGid(call("aria2.addTorrent", params));
    }

    /**
     * 查询下载任务状态。
     *
     * @param gid  下载任务 GID
     * @param keys 需要返回的字段名列表，为空表示返回全部字段
     * @return 状态字段的键值对（字段值为 aria2 原始形态：数字多为字符串）
     * @throws Aria2RpcException 查询失败或返回格式非法时抛出
     */
    public Map<String, Object> tellStatus(String gid, List<String> keys) {
        List<Object> params = new ArrayList<>(2);
        params.add(gid);
        params.add(keys == null ? List.of() : keys);
        Object result = call("aria2.tellStatus", params);
        if (result instanceof Map<?, ?> statusMap) {
            Map<String, Object> status = new LinkedHashMap<>(statusMap.size());
            statusMap.forEach((key, value) -> {
                if (key != null) {
                    status.put(String.valueOf(key), value);
                }
            });
            return status;
        }
        throw new Aria2RpcException(Aria2RpcException.UNKNOWN_CODE, "aria2 返回的 tellStatus 结果格式非法");
    }

    /**
     * 强制移除下载任务（尽力而为）。
     *
     * <p>用于任务中断：aria2 会立即停止该 GID 的下载。任务已经结束时 aria2 会返回错误，
     * 这里按「已结束」处理，不向上抛出，避免中断流程被二次失败掩盖。</p>
     *
     * @param gid 下载任务 GID
     */
    public void forceRemove(String gid) {
        List<Object> params = new ArrayList<>(1);
        params.add(gid);
        try {
            call("aria2.forceRemove", params);
        } catch (RuntimeException e) {
            log.debug("aria2.forceRemove 失败（任务可能已结束）: gid={}, cause={}", gid, e.toString());
        }
    }

    /**
     * 清理 aria2 侧已结束任务的结果记录（尽力而为）。
     *
     * <p>aria2 会把已结束的下载结果保留在内存中，长期运行会累积；任务收尾时调用本方法释放。</p>
     *
     * @param gid 下载任务 GID
     */
    public void removeDownloadResult(String gid) {
        List<Object> params = new ArrayList<>(1);
        params.add(gid);
        try {
            call("aria2.removeDownloadResult", params);
        } catch (RuntimeException e) {
            log.debug("aria2.removeDownloadResult 失败（结果可能已被清理）: gid={}, cause={}", gid, e.toString());
        }
    }

    // ==================== 内部实现 ====================

    /**
     * 发送一次 JSON-RPC 调用并返回 {@code result} 节点。
     *
     * <p><b>传输方式：</b>请求体先用 {@link SfcMapperHolder#toJsonNoEx(Object)} 序列化为字节数组，
     * 再以 {@link HttpRequest.BodyPublishers#ofByteArray(byte[])} 发布——长度已知，必定携带
     * {@code Content-Length}，不会退化为 chunked（aria2 的 JSON-RPC 服务器不支持 chunked 请求体）；
     * 响应以 {@code BodyHandlers.ofString(UTF_8)} 读取，全程不经过任何 Spring 消息转换器。
     * 详见类注释中的「传输层说明」。</p>
     *
     * @param method JSON-RPC 方法名（如 {@code aria2.addUri}）
     * @param params 方法参数（不含令牌项，本方法会自动补齐）
     * @return {@code result} 节点，可能为 {@code null}
     * @throws Aria2RpcException 传输失败、返回空响应、响应无法解析或协议返回 {@code error} 时抛出
     */
    private Object call(String method, List<Object> params) {
        Aria2Property config = configService.getAria2();
        String rpcUrl = SfcStringUtils.hasText(config.getRpcUrl())
                ? config.getRpcUrl().trim()
                : DEFAULT_RPC_URL;

        List<Object> rpcParams = new ArrayList<>(params.size() + 1);
        if (SfcStringUtils.hasText(config.getSecret())) {
            rpcParams.add(TOKEN_PREFIX + config.getSecret().trim());
        }
        rpcParams.addAll(params);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", JSONRPC_VERSION);
        payload.put("id", UUID.randomUUID().toString());
        payload.put("method", method);
        payload.put("params", rpcParams);

        byte[] requestBody = SfcMapperHolder.toJsonNoEx(payload).getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> httpResponse;
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(rpcUrl))
                    .timeout(Duration.ofMillis(READ_TIMEOUT_MILLIS))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();
            httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Aria2RpcException(Aria2RpcException.UNKNOWN_CODE, "aria2 RPC 请求被中断", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new Aria2RpcException(Aria2RpcException.UNKNOWN_CODE,
                    "aria2 RPC 请求失败（" + rpcUrl + "）: " + describe(e), e);
        }

        String responseText = httpResponse.body();
        if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
            throw new Aria2RpcException(Aria2RpcException.UNKNOWN_CODE,
                    "aria2 RPC 返回 HTTP " + httpResponse.statusCode() + ": " + abbreviate(responseText));
        }
        if (!SfcStringUtils.hasText(responseText)) {
            throw new Aria2RpcException(Aria2RpcException.UNKNOWN_CODE, "aria2 RPC 返回空响应");
        }

        Map<String, Object> response;
        try {
            response = SfcMapperHolder.parseJsonToMap(responseText);
        } catch (Exception e) {
            throw new Aria2RpcException(Aria2RpcException.UNKNOWN_CODE,
                    "aria2 RPC 响应解析失败: " + describe(e) + "，原始响应: " + abbreviate(responseText), e);
        }

        Object error = response.get("error");
        if (error instanceof Map<?, ?> errorInfo) {
            throw new Aria2RpcException(toInt(errorInfo.get("code")),
                    String.valueOf(errorInfo.get("message")));
        }
        return response.get("result");
    }

    /**
     * 从 RPC 返回的 {@code result} 节点中提取 GID。
     *
     * @param result {@code result} 节点
     * @return GID 字符串
     * @throws Aria2RpcException 结果不是非空字符串时抛出
     */
    private static String requireGid(Object result) {
        if (result instanceof String gid && SfcStringUtils.hasText(gid)) {
            return gid.trim();
        }
        throw new Aria2RpcException(Aria2RpcException.UNKNOWN_CODE,
                "aria2 未返回有效的下载任务标识: " + result);
    }

    /**
     * 把任意对象宽松转换为错误码整数。
     *
     * @param value 原始值（aria2 通常返回数字或数字字符串）
     * @return 错误码；无法转换时返回 {@link Aria2RpcException#UNKNOWN_CODE}
     */
    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return Aria2RpcException.UNKNOWN_CODE;
            }
        }
        return Aria2RpcException.UNKNOWN_CODE;
    }

    /**
     * 截断过长的原始响应文本，避免把整段 JSON 写进任务失败信息与数据库。
     *
     * @param text 原始文本，允许为 {@code null}
     * @return 截断后的文本；超过 {@value #MAX_RESPONSE_PREVIEW_LENGTH} 字符时追加省略标记
     */
    private static String abbreviate(String text) {
        if (text == null || text.length() <= MAX_RESPONSE_PREVIEW_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_RESPONSE_PREVIEW_LENGTH) + "...(共 " + text.length() + " 字符)";
    }

    /**
     * 把异常转换为可读的简短描述。
     *
     * @param e 异常，允许为 {@code null}
     * @return 异常描述，永不为 {@code null}
     */
    private static String describe(Throwable e) {
        if (e == null) {
            return "未知错误";
        }
        String message = e.getMessage();
        if (SfcStringUtils.hasText(message)) {
            return message;
        }
        Throwable cause = e.getCause();
        if (cause != null && SfcStringUtils.hasText(cause.getMessage())) {
            return cause.getMessage();
        }
        return e.getClass().getSimpleName();
    }
}
