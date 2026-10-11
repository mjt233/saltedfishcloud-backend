package com.sfc.gateway.service;

import com.sfc.gateway.config.SfcGatewayProperties;
import com.sfc.gateway.model.ProxyTarget;
import com.sfc.gateway.support.GatewayErrorCode;
import com.sfc.gateway.support.ProxyRejectException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 代理请求的<b>本地策略判定</b>：回答"这条 {@code /api/ext/**} 请求能不能转、转到哪个服务的哪条路径"。
 *
 * <h3>这些规则原先在 core，现整体下沉到网关</h3>
 * <p>
 * 自"网关独立验签"起，core 不再出现在代理链路上（{@code POST /api/gateway/resolve} 已删除）。
 * 因此下列判定全部由网关自己做，规则逐条从 core 的 {@code GatewayResolveService} 平移过来，
 * 行为与错误码保持不变：
 * </p>
 * <table border="1">
 *     <caption>判定清单</caption>
 *     <tr><th>#</th><th>判定</th><th>失败语义</th></tr>
 *     <tr><td>1</td><td>HTTP 方法白名单</td><td>400 / 7000</td></tr>
 *     <tr><td>2</td><td>路径合法性（<b>只拒绝、不规范化</b>）</td><td>400 / 7000</td></tr>
 *     <tr><td>3</td><td>服务名字符集校验</td><td>400 / 7000</td></tr>
 *     <tr><td>4</td><td>自我转发阻断（core / 网关自身）</td><td>404 / 7001</td></tr>
 *     <tr><td>5</td><td>服务白名单：只信注册中心（SSRF 防线）</td><td>404 / 7001（注册中心不可用为 503 / 7004）</td></tr>
 *     <tr><td>6</td><td>{@code sfc.api.prefix} 解析与 {@code targetPath} 拼接</td><td>404 / 7001（非法不静默回退）</td></tr>
 * </table>
 *
 * <h3>硬规则：判定时看到的路径与转发时用的路径必须是同一个字符串</h3>
 * <p>
 * 因此路径校验<b>只拒绝、不规范化</b>：不做归一化、不补斜杠、不解码再编码。重复斜杠（{@code //}）会被
 * Netty/SCG 在转发时折叠，放行它就必然出现"判定用 A、转发用 B"，所以直接拒绝（预研实测的结论）。
 * 取路径时用 {@code URI.getRawPath()}（原始未解码），余部逐字节保留。
 * </p>
 *
 * <h3>实例查询为什么是阻塞 API 包一层 boundedElastic</h3>
 * <p>
 * 实例来源是 Spring Cloud 的 {@link DiscoveryClient}（即 {@code lb://} 用的同一份发现结果）。
 * 它是阻塞接口，直接放进 Netty 事件循环会阻塞 IO 线程，因此统一调度到
 * {@link Schedulers#boundedElastic()} 上执行。查询<b>不加缓存</b>：实例全部消失时应当立刻表现为
 * 404 / 7001，而不是先在缓存窗口里给调用方一堆 503。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyPolicyService {

    /** 浏览器侧代理入口的固定前缀（与网关路由 {@code Path=/api/ext/**} 对齐） */
    public static final String EXT_PATH_PREFIX = "/api/ext/";

    /** 允许转发的 HTTP 方法（TRACE / CONNECT 等一律拒绝） */
    private static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");

    /** 服务名允许的字符集：字母数字开头，限制长度，避免服务名本身成为注入载体 */
    private static final Pattern SERVICE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");

    /** API 前缀允许的字符集（必须以 / 开头，允许为空段形式的前缀如 /api、/v1/api） */
    private static final Pattern API_PREFIX_PATTERN = Pattern.compile("^/(?:[A-Za-z0-9._~-]+(?:/[A-Za-z0-9._~-]+)*)?$");

    /**
     * 危险百分号编码（按小写比较）。
     * <p>
     * 编码的斜杠与反斜杠（{@code %2f} / {@code %5c}）会绕过"按路径段判定"的所有规则；
     * 编码的点（{@code %2e}）是 {@code ..} 的另一种写法；NUL 与各控制字符用于截断与走私；
     * 编码空格（{@code %20}）同样列入拒绝清单。
     * </p>
     */
    private static final List<String> DANGEROUS_ENCODINGS =
            List.of("%2f", "%5c", "%00", "%2e", "%20", "%0a", "%0d", "%09");

    /** 路径长度上限：真实 API 路径远小于它，超长路径只会是构造出来的 */
    private static final int MAX_PATH_LENGTH = 4096;

    /** 实例 metadata 中声明 API 前缀的键名（微服务自描述，如 demo 的 {@code sfc.api.prefix: /api}） */
    private static final String METADATA_API_PREFIX = "sfc.api.prefix";

    /** 网关配置 */
    private final SfcGatewayProperties properties;

    /** 服务发现（与 {@code lb://} 同源：目标 host / port 只可能来自注册中心） */
    private final DiscoveryClient discoveryClient;

    /**
     * 判定一次代理请求。
     *
     * <p>
     * 纯字符串规则（方法 / 路径 / 服务名 / 自我转发）在调用线程上同步抛出 {@link ProxyRejectException}；
     * 需要查注册中心的步骤（实例列表与 metadata）在 boundedElastic 上异步完成。
     * </p>
     *
     * @param rawPath 网关收到的<b>原始未解码</b>路径（{@code URI.getRawPath()}）
     * @param method  请求方法（大小写不敏感）
     * @return 代理目标；判定失败时以 {@link ProxyRejectException} 结束
     */
    public Mono<ProxyTarget> resolve(String rawPath, String method) {
        String normalizedMethod = method == null ? null : method.trim().toUpperCase(Locale.ROOT);
        validateRawPath(rawPath);

        String serviceName = extractServiceName(rawPath);
        String remainder = extractRemainder(rawPath);

        // 阻断自我转发
        checkNotSelf(serviceName);

        return Mono.fromCallable(() -> {
                    List<ServiceInstance> instances = listInstances(serviceName);
                    String apiPrefix = resolveApiPrefix(serviceName, instances);
                    return new ProxyTarget(serviceName, apiPrefix + remainder);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(target -> {
                    if (log.isDebugEnabled()) {
                        log.debug("[代理判定] {} {} -> lb://{}{}", normalizedMethod, rawPath,
                                target.serviceName(), target.targetPath());
                    }
                });
    }

    /**
     * 路径合法性校验：<b>只拒绝、不规范化</b>。
     *
     * @param rawPath 原始未解码路径
     * @throws ProxyRejectException 路径为空、前缀不符、含危险编码 / 危险字符 / 点段 / 重复斜杠、或超长
     */
    private void validateRawPath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            throw new ProxyRejectException(HttpStatus.BAD_REQUEST.value(), GatewayErrorCode.PATH_INVALID,
                    "缺少请求路径");
        }
        if (rawPath.length() > MAX_PATH_LENGTH) {
            throw new ProxyRejectException(HttpStatus.BAD_REQUEST.value(), GatewayErrorCode.PATH_INVALID, "路径超长");
        }
        if (!rawPath.startsWith(EXT_PATH_PREFIX)) {
            throw new ProxyRejectException(HttpStatus.BAD_REQUEST.value(), GatewayErrorCode.PATH_INVALID,
                    "路径前缀非法：" + rawPath);
        }
        // 重复斜杠：实测网关（Netty/SCG）会把 // 折叠后再转发，判定与转发会因此分叉 —— 直接拒绝
        if (rawPath.contains("//")) {
            throw new ProxyRejectException(HttpStatus.BAD_REQUEST.value(), GatewayErrorCode.PATH_INVALID,
                    "路径存在重复斜杠");
        }
        String lower = rawPath.toLowerCase(Locale.ROOT);
        for (String encoding : DANGEROUS_ENCODINGS) {
            if (lower.contains(encoding)) {
                throw new ProxyRejectException(HttpStatus.BAD_REQUEST.value(), GatewayErrorCode.PATH_INVALID,
                        "路径存在危险编码：" + encoding);
            }
        }
        for (int i = 0; i < rawPath.length(); i++) {
            char c = rawPath.charAt(i);
            // 控制字符与空格、反斜杠：既不合法，也是各类走私 / 穿越构造的常用材料
            if (c <= 0x20 || c == 0x7f || c == '\\') {
                throw new ProxyRejectException(HttpStatus.BAD_REQUEST.value(), GatewayErrorCode.PATH_INVALID,
                        "路径存在非法字符");
            }
        }
        for (String segment : rawPath.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw new ProxyRejectException(HttpStatus.BAD_REQUEST.value(), GatewayErrorCode.PATH_INVALID,
                        "路径存在点段");
            }
        }
        // 百分号必须是合法的转义（两位十六进制）：否则构造下游 URI 时会抛 IllegalArgumentException，
        // 表现为"判定放行、转发 500"——这类问题必须在判定侧就拦掉。
        // ⚠️ 实测补充：绝大多数非法转义（%2 / %zz / %）在更早的 Netty 解析请求行阶段就被拒（空体 400），
        //    本规则因此常表现为"纵深防御"；但只要有一层解析能把它送进来，这里就必须拦住。
        for (int i = 0; i < rawPath.length(); i++) {
            if (rawPath.charAt(i) != '%') {
                continue;
            }
            if (i + 2 >= rawPath.length() || isNotHex(rawPath.charAt(i + 1)) || isNotHex(rawPath.charAt(i + 2))) {
                throw new ProxyRejectException(HttpStatus.BAD_REQUEST.value(), GatewayErrorCode.PATH_INVALID,
                        "路径存在非法的百分号转义");
            }
        }
    }

    /**
     * 判断字符是否<b>不是</b>十六进制数字。
     * <p>做成"非"的语义而不是取反调用：避免"布尔方法总是被取反调用"这类可读性告警。</p>
     *
     * @param c 字符
     * @return 不是十六进制数字时返回 true
     */
    private boolean isNotHex(char c) {
        return !((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'));
    }

    /**
     * 从原始路径中取出服务名（{@code /api/ext/{serviceName}/...} 的第一段）。
     *
     * @param rawPath 原始未解码路径
     * @return 服务名
     * @throws ProxyRejectException 服务名缺失或字符集非法（400 / 7000）
     */
    private String extractServiceName(String rawPath) {
        int start = EXT_PATH_PREFIX.length();
        int end = rawPath.indexOf('/', start);
        String serviceName = end < 0 ? rawPath.substring(start) : rawPath.substring(start, end);
        if (!SERVICE_NAME_PATTERN.matcher(serviceName).matches()) {
            throw new ProxyRejectException(HttpStatus.BAD_REQUEST.value(), GatewayErrorCode.PATH_INVALID,
                    "服务名非法：" + serviceName);
        }
        return serviceName;
    }

    /**
     * 取出服务名之后的路径余部（<b>逐字节保留原始写法</b>，含前导斜杠；无余部时为空串）。
     *
     * @param rawPath 原始未解码路径
     * @return 路径余部
     */
    private String extractRemainder(String rawPath) {
        int start = EXT_PATH_PREFIX.length();
        int end = rawPath.indexOf('/', start);
        return end < 0 ? "" : rawPath.substring(end);
    }

    /**
     * 自我转发阻断：目标不得是 core 自身，也不得是网关自身。
     *
     * <p>前者是提权路径（core 的内部接口本不该经代理到达），后者是回环。</p>
     *
     * @param serviceName 目标服务名
     * @throws ProxyRejectException 目标为自身时抛出 404 / 7001
     */
    private void checkNotSelf(String serviceName) {
        if (serviceName.equals(properties.getCoreService()) || serviceName.equals(properties.getSelfServiceName())) {
            log.warn("[代理判定] 阻断自我转发：serviceName={}（core={} 网关={}）",
                    serviceName, properties.getCoreService(), properties.getSelfServiceName());
            throw new ProxyRejectException(HttpStatus.NOT_FOUND.value(), GatewayErrorCode.SERVICE_NOT_AVAILABLE,
                    "目标服务不存在或不可代理：" + serviceName);
        }
    }

    /**
     * 查询目标服务的健康实例。
     *
     * <p>
     * 注册中心查询失败时映射为 503 / 7004（实例不可达）而不是 500：对调用方而言
     * "服务发现基础设施不可用"与"实例全都连不通"是同一类"稍后重试"的语义。
     * </p>
     *
     * @param serviceName 目标服务名
     * @return 健康实例列表，非空
     * @throws ProxyRejectException 无健康实例（404 / 7001）或注册中心不可用（503 / 7004）
     */
    private List<ServiceInstance> listInstances(String serviceName) {
        List<ServiceInstance> instances;
        try {
            instances = discoveryClient.getInstances(serviceName);
        } catch (Exception e) {
            log.error("[代理判定] 服务发现查询失败：serviceName={}", serviceName, e);
            throw new ProxyRejectException(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    GatewayErrorCode.UPSTREAM_UNAVAILABLE, "服务发现不可用");
        }
        if (instances == null || instances.isEmpty()) {
            throw new ProxyRejectException(HttpStatus.NOT_FOUND.value(), GatewayErrorCode.SERVICE_NOT_AVAILABLE,
                    "目标服务不存在或不可代理：" + serviceName);
        }
        return instances;
    }

    /**
     * 解析微服务自描述的 API 前缀。
     *
     * <p>
     * 取值顺序：任一健康实例 metadata 的 {@code sfc.api.prefix} → 配置的
     * {@code sfc.gateway.default-api-prefix}。metadata 里给了前缀但格式非法时<b>不静默回退</b>，
     * 而是判 404 / 7001：这类问题一旦被掩盖，现象是"某些请求莫名其妙打到错误路径"，
     * 排查成本远高于直接报错。
     * </p>
     *
     * @param serviceName 目标服务名（仅用于日志与报错）
     * @param instances   健康实例列表
     * @return 规范化后的前缀（根路径时为空串）
     * @throws ProxyRejectException metadata 中的前缀非法
     */
    private String resolveApiPrefix(String serviceName, List<ServiceInstance> instances) {
        String declared = null;
        for (ServiceInstance instance : instances) {
            Map<String, String> metadata = instance.getMetadata();
            String value = metadata == null ? null : metadata.get(METADATA_API_PREFIX);
            if (StringUtils.hasText(value)) {
                declared = value.trim();
                break;
            }
        }
        if (declared == null) {
            return normalizePrefix(serviceName, properties.getDefaultApiPrefix(), "配置缺省值");
        }
        return normalizePrefix(serviceName, declared, "实例 metadata");
    }

    /**
     * 规范化 API 前缀：校验字符集与路径段，去掉末尾斜杠，根路径 {@code /} 归一为<b>空串</b>。
     *
     * @param serviceName 目标服务名（用于报错信息）
     * @param prefix      原始前缀
     * @param source      来源说明（实例 metadata / 配置缺省值）
     * @return 规范化后的前缀
     * @throws ProxyRejectException 前缀非法
     */
    private String normalizePrefix(String serviceName, String prefix, String source) {
        if (!StringUtils.hasText(prefix)) {
            throw new ProxyRejectException(HttpStatus.NOT_FOUND.value(), GatewayErrorCode.SERVICE_NOT_AVAILABLE,
                    "服务 " + serviceName + " 的 API 前缀为空（来源：" + source + "）");
        }
        String value = prefix.trim();
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (!API_PREFIX_PATTERN.matcher(value).matches()) {
            throw new ProxyRejectException(HttpStatus.NOT_FOUND.value(), GatewayErrorCode.SERVICE_NOT_AVAILABLE,
                    "服务 " + serviceName + " 的 API 前缀非法：" + prefix + "（来源：" + source + "）");
        }
        // 字符集校验放行了点号（/v1.0 这类前缀合法），因此点段需要单独拒绝
        for (String segment : value.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw new ProxyRejectException(HttpStatus.NOT_FOUND.value(), GatewayErrorCode.SERVICE_NOT_AVAILABLE,
                        "服务 " + serviceName + " 的 API 前缀含点段：" + prefix);
            }
        }
        return "/".equals(value) ? "" : value;
    }
}
