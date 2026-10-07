package com.sfc.cloud.auth;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * 免鉴权放行路径的匹配器。
 * <p>
 * <b>为什么必须先规范化再做前缀匹配</b>：如果直接对原始路径做 {@code startsWith("/static/")}，
 * 那么 {@code /static/../api/task/create} 这类构造会命中放行规则，
 * "放行 {@code /static/**}"就变成了<b>放行任意路径</b>——红线当场失效。
 * </p>
 *
 * <h3>规范化步骤</h3>
 * <ol>
 *     <li><b>反复 URL 解码</b>直到稳定（最多 5 轮），让多重编码（{@code %252e%252e}）也暴露出来；</li>
 *     <li>反斜杠统一为斜杠；剥离矩阵参数（{@code ;jsessionid=...}）；</li>
 *     <li>按段折叠 {@code .} 与 {@code ..}；<b>一旦试图越出根就判定为不安全</b>。</li>
 * </ol>
 * <p>
 * 无法安全规范化的路径、以及解码失败的路径，一律<b>视为受保护</b>（fail-closed 方向），
 * 而不是"匹配不上就放行"。匹配本身是大小写敏感的前缀 / 精确匹配。
 * </p>
 */
final class PublicPathMatcher {

    /** URL 解码的最大轮数，用于击穿多重编码 */
    private static final int MAX_DECODE_ROUNDS = 5;

    private final List<String> normalizedPatterns;

    /**
     * 构造匹配器。
     *
     * @param patterns 配置中的放行路径模式，支持 {@code /**} 后缀与精确路径
     */
    PublicPathMatcher(List<String> patterns) {
        this.normalizedPatterns = (patterns == null ? List.<String>of() : patterns).stream()
                .map(PublicPathMatcher::normalize)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 判断请求路径是否命中放行规则。
     *
     * @param rawPath 原始请求路径（通常是 {@code request.getRequestURI()}）
     * @return 命中放行规则时返回 {@code true}
     */
    boolean isPublic(String rawPath) {
        String path = normalize(rawPath);
        if (path == null) {
            // 无法安全规范化 ⇒ 不放行
            return false;
        }
        return normalizedPatterns.stream().anyMatch(pattern -> matches(pattern, path));
    }

    /**
     * 规范化一个路径。
     *
     * @param rawPath 原始路径
     * @return 规范化后的绝对路径；无法安全规范化时返回 {@code null}
     */
    static String normalize(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return null;
        }
        String path = rawPath;
        for (int round = 0; round < MAX_DECODE_ROUNDS; round++) {
            String decoded;
            try {
                decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                // 非法百分号转义：不猜、不放行
                return null;
            }
            if (decoded.equals(path)) {
                break;
            }
            path = decoded;
        }
        if (path.indexOf('\0') >= 0) {
            return null;
        }
        path = path.replace('\\', '/');
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.split("/", -1)) {
            // 剥离矩阵参数（;jsessionid=xxx），避免其影响前缀匹配
            int semicolon = segment.indexOf(';');
            String cleaned = semicolon >= 0 ? segment.substring(0, semicolon) : segment;
            if (cleaned.isEmpty() || ".".equals(cleaned)) {
                continue;
            }
            if ("..".equals(cleaned)) {
                if (segments.isEmpty()) {
                    // 试图越出根 ⇒ 不安全
                    return null;
                }
                segments.removeLast();
                continue;
            }
            segments.addLast(cleaned);
        }
        return "/" + String.join("/", segments);
    }

    /**
     * 单条模式的匹配。
     *
     * @param pattern 已规范化的模式
     * @param path    已规范化的请求路径
     * @return 命中时返回 {@code true}
     */
    private static boolean matches(String pattern, String path) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            if (prefix.isEmpty()) {
                return true;
            }
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
        return pattern.equals(path);
    }
}
