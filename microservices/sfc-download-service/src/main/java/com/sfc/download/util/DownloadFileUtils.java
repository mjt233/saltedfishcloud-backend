package com.sfc.download.util;

import com.sfc.common.utils.SfcStringUtils;
import org.springframework.http.HttpMethod;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 离线下载文件工具类。
 *
 * <p>为下载任务处理器提供与「文件名」和「本地临时文件」相关的无状态工具能力：</p>
 * <ul>
 *     <li>从下载 URL 推断候选文件名（URL 解码 + 非法字符清洗）；</li>
 *     <li>清洗文件名中的非法字符、控制字符与平台不可移植字符；</li>
 *     <li>在已有文件名集合下生成不冲突的保存名（重名时追加 {@code .1}、{@code .2}…）；</li>
 *     <li>创建、删除下载过程使用的本地临时文件与临时目录，并尽力清理历史残留；</li>
 *     <li>校验下载 URL 与解析 HTTP 请求方法。</li>
 * </ul>
 *
 * <p>本类不负责发起 HTTP 请求，也不依赖宿主（sfc-core）的任何类型，仅使用 sfc-common 的字符串工具。</p>
 */
public final class DownloadFileUtils {

    /**
     * 离线下载临时目录的目录名，位于 JVM 临时目录（{@code java.io.tmpdir}）之下。
     */
    public static final String TEMP_DIR_NAME = "offline-download";

    /**
     * 单个文件名允许的最大字符数。
     *
     * <p>取值兼顾常见文件系统对单个路径分段 255 字符的限制，以及 core 侧文件名字段的长度上限。</p>
     */
    public static final int MAX_FILE_NAME_LENGTH = 255;

    /**
     * 判定为「可保留扩展名」的后缀最大长度；超过该长度的点后缀视为普通文件名内容，不单独保留。
     */
    private static final int MAX_EXTENSION_LENGTH = 20;

    /**
     * 临时目录残留的默认最长保留时间：超过该时间未被修改的临时目录会在下次创建临时目录时被尽力清理。
     *
     * <p>取较大值（7 天）是为了避免误删其它实例仍在进行中的下载任务临时文件。</p>
     */
    private static final Duration STALE_TEMP_DIR_MAX_AGE = Duration.ofDays(7);

    /**
     * 任务标识在临时目录/文件名中的最大长度，避免任务标识过长撑爆路径。
     */
    private static final int MAX_TASK_FLAG_LENGTH = 32;

    /**
     * 文件名中不允许出现的字符：Windows 保留字符、路径分隔符以及所有控制字符。
     */
    private static final Pattern ILLEGAL_FILE_NAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    /**
     * 文件名末尾不允许出现的字符（Windows 会静默去掉末尾的点和空格）。
     */
    private static final Pattern TRAILING_DOTS_AND_SPACES = Pattern.compile("[. ]+$");

    /**
     * 是否已经执行过「历史临时目录残留」的一次性清理。
     */
    private static final AtomicBoolean STALE_TEMP_DIR_CLEANED = new AtomicBoolean(false);

    /**
     * 工具类禁止实例化。
     */
    private DownloadFileUtils() {
        throw new AssertionError("工具类不允许实例化");
    }

    // ==================== URL 与文件名解析 ====================

    /**
     * 从下载 URL 中推断候选文件名。
     *
     * <p>解析步骤：取 URL 路径最后一段 → URL 解码（如 {@code %E4%B8%AD.txt}）→ 清洗非法字符 →
     * 截断超长文件名。URL 为空、路径为空、最后一段为 {@code "/"}，或清洗后为空时，
     * 回退为当前毫秒时间戳作为文件名（与旧插件行为一致）。</p>
     *
     * @param url 下载地址，允许为 {@code null}
     * @return 可安全用于保存的候选文件名，永不为 {@code null} 且永不为空串
     */
    public static String parseFileNameFromUrl(String url) {
        String lastName = SfcStringUtils.getURLLastName(url);
        if (!SfcStringUtils.hasText(lastName) || "/".equals(lastName) || "\\".equals(lastName)) {
            return getTimestampFileName();
        }
        return sanitizeFileName(decodeUrlComponent(lastName));
    }

    /**
     * 判断下载 URL 是否合法。
     *
     * <p>合法条件：非空白、可被 {@link URI} 解析、协议为 {@code http}/{@code https} 且带有主机名
     * （或至少带有 authority）。</p>
     *
     * @param url 待校验的下载地址，允许为 {@code null}
     * @return 合法返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isValidUrl(String url) {
        if (!SfcStringUtils.hasText(url)) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return false;
            }
            // 优先要求解析出 host；对包含下划线等非常规字符的主机，退化为要求存在 authority
            return uri.getHost() != null || uri.getRawAuthority() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * 解析下载请求方法字符串。
     *
     * <p>为空时默认使用 {@link HttpMethod#GET}；其它取值按 {@link HttpMethod#valueOf(String)} 解析
     * （大小写不敏感）。</p>
     *
     * @param method 请求方法字符串，允许为 {@code null} 或空白
     * @return 解析得到的 HTTP 方法
     * @throws IllegalArgumentException 方法字符串不是合法的 HTTP 方法时抛出
     */
    public static HttpMethod parseHttpMethod(String method) {
        if (!SfcStringUtils.hasText(method)) {
            return HttpMethod.GET;
        }
        try {
            return HttpMethod.valueOf(method.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的下载请求方法: " + method, e);
        }
    }

    // ==================== 文件名清洗与重名处理 ====================

    /**
     * 清洗文件名。
     *
     * <p>处理内容：非法字符（{@code \ / : * ? " < > |}）与控制字符替换为 {@code "_"}；去掉首尾空白；
     * 去掉 Windows 不支持的末尾点与空格；拒绝 {@code "."}、{@code ".."} 等特殊名称；超长名称按
     * {@link #MAX_FILE_NAME_LENGTH} 截断并尽量保留扩展名。清洗后为空时回退为当前毫秒时间戳。</p>
     *
     * @param fileName 原始文件名，允许为 {@code null}
     * @return 清洗后的文件名，永不为 {@code null} 且永不为空串
     */
    public static String sanitizeFileName(String fileName) {
        if (!SfcStringUtils.hasText(fileName)) {
            return getTimestampFileName();
        }
        String sanitized = ILLEGAL_FILE_NAME_CHARS.matcher(fileName).replaceAll("_");
        sanitized = sanitized.trim();
        sanitized = TRAILING_DOTS_AND_SPACES.matcher(sanitized).replaceAll("");
        if (sanitized.isEmpty() || ".".equals(sanitized) || "..".equals(sanitized)) {
            return getTimestampFileName();
        }
        return truncateFileName(sanitized, MAX_FILE_NAME_LENGTH);
    }

    /**
     * 生成以当前毫秒时间戳命名的兜底文件名。
     *
     * @return 当前毫秒时间戳字符串
     */
    public static String getTimestampFileName() {
        return String.valueOf(System.currentTimeMillis());
    }

    /**
     * 基于已有文件名集合生成不冲突的保存名。
     *
     * <p>与旧插件行为一致：目标目录已存在同名文件时，依次追加 {@code ".1"}、{@code ".2"}…，
     * 直到不冲突为止。比较时忽略大小写（兼容 Windows 等大小写不敏感的文件系统）。</p>
     *
     * @param fileName      期望使用的文件名（会先经过 {@link #sanitizeFileName(String)} 清洗）
     * @param existingNames 目标目录下已有的文件名集合，允许为 {@code null} 或空
     * @return 不冲突的文件名
     */
    public static String generateNonConflictName(String fileName, Collection<String> existingNames) {
        String baseName = sanitizeFileName(fileName);
        if (existingNames == null || existingNames.isEmpty()) {
            return baseName;
        }
        Set<String> normalizedNames = existingNames.stream()
                .filter(Objects::nonNull)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        String candidate = baseName;
        int index = 0;
        while (normalizedNames.contains(candidate.toLowerCase(Locale.ROOT))) {
            index++;
            String suffix = "." + index;
            candidate = truncateFileName(baseName, MAX_FILE_NAME_LENGTH - suffix.length()) + suffix;
        }
        return candidate;
    }

    // ==================== 本地临时文件 ====================

    /**
     * 获取（并按需创建）离线下载临时根目录：{@code ${java.io.tmpdir}/offline-download}。
     *
     * @return 已存在的临时根目录
     * @throws IOException 目录创建失败时抛出
     */
    public static Path getTempDir() throws IOException {
        String tmpDir = System.getProperty("java.io.tmpdir");
        Path baseDir = SfcStringUtils.hasText(tmpDir) ? Paths.get(tmpDir) : Paths.get(".");
        Path tempDir = baseDir.resolve(TEMP_DIR_NAME);
        Files.createDirectories(tempDir);
        return tempDir;
    }

    /**
     * 为一次下载任务创建独立的临时目录。
     *
     * <p>目录名为 {@code <任务标识>-<UUID>}，位于 {@link #getTempDir()} 之下，保证同一任务并发执行或
     * 同名文件下载时互不干扰。首次调用时还会顺带尽力清理历史残留临时目录。</p>
     *
     * @param taskFlag 任务标识（通常为下载任务id），允许为 {@code null} 或空白
     * @return 已创建的任务临时目录
     * @throws IOException 目录创建失败时抛出
     */
    public static Path createTaskTempDir(String taskFlag) throws IOException {
        cleanupStaleTempDirsOnce();
        String prefix = normalizeTaskFlag(taskFlag);
        Path taskDir = getTempDir().resolve(prefix + "-" + UUID.randomUUID());
        return Files.createDirectories(taskDir);
    }

    /**
     * 为一次下载任务创建本地临时文件。
     *
     * <p>在 {@link #createTaskTempDir(String)} 创建的任务临时目录下，以清洗后的文件名创建空文件，
     * 供下载主流程流式写入。</p>
     *
     * @param taskFlag 任务标识（通常为下载任务id），允许为 {@code null} 或空白
     * @param fileName 目标文件名，会经过 {@link #sanitizeFileName(String)} 清洗
     * @return 已创建的空临时文件路径
     * @throws IOException 目录或文件创建失败时抛出
     */
    public static Path createTaskTempFile(String taskFlag, String fileName) throws IOException {
        Path taskDir = createTaskTempDir(taskFlag);
        Path tempFile = taskDir.resolve(sanitizeFileName(fileName));
        return Files.createFile(tempFile);
    }

    /**
     * 尽力删除文件或目录（目录会递归删除），不抛出任何异常。
     *
     * <p>用于任务收尾阶段的临时文件清理：删除失败（例如文件仍被占用）只记录日志，不影响任务结果。</p>
     *
     * @param path 待删除的路径，允许为 {@code null}
     */
    public static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                try (Stream<Path> children = Files.list(path)) {
                    children.forEach(DownloadFileUtils::deleteQuietly);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException | RuntimeException e) {
            // 临时文件清理属于 best-effort，不向上抛出
        }
    }

    /**
     * 尽力清理临时根目录下超过指定时长未修改的残留目录/文件。
     *
     * <p>用于服务重启后回收上次异常退出遗留的临时文件。清理过程完全 best-effort：任何单个条目
     * 的删除失败都会被忽略，不影响调用方。</p>
     *
     * @param maxAge 最大保留时长；为 {@code null} 或负数时使用默认值 7 天
     */
    public static void cleanupStaleTempDirs(Duration maxAge) {
        Duration age = (maxAge == null || maxAge.isNegative()) ? STALE_TEMP_DIR_MAX_AGE : maxAge;
        Path tempDir;
        try {
            tempDir = getTempDir();
        } catch (IOException e) {
            return;
        }
        long threshold = System.currentTimeMillis() - age.toMillis();
        try (Stream<Path> children = Files.list(tempDir)) {
            children.filter(child -> isStale(child, threshold))
                    .forEach(DownloadFileUtils::deleteQuietly);
        } catch (IOException | RuntimeException e) {
            // best-effort：清理失败不影响下载流程
        }
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 每个 JVM 生命周期内只执行一次历史临时目录清理。
     */
    private static void cleanupStaleTempDirsOnce() {
        if (!STALE_TEMP_DIR_CLEANED.compareAndSet(false, true)) {
            return;
        }
        try {
            cleanupStaleTempDirs(STALE_TEMP_DIR_MAX_AGE);
        } catch (RuntimeException e) {
            // best-effort：清理失败不影响下载流程
        }
    }

    /**
     * 判断临时目录下的条目是否已过期。
     *
     * @param path      待判断的路径
     * @param threshold 过期时间阈值（毫秒时间戳）
     * @return 最后修改时间早于阈值返回 {@code true}
     */
    private static boolean isStale(Path path, long threshold) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis() < threshold;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 对 URL 路径最后一段做 URL 解码。
     *
     * <p>解码失败（非法的百分号转义）时原样返回；由于 {@link URLDecoder} 会把 {@code "+"} 解释为空格，
     * 而路径段中的 {@code "+"} 通常就是普通字符，因此解码前先把 {@code "+"} 转义为 {@code "%2B"}。</p>
     *
     * @param raw 原始 URL 路径段
     * @return 解码后的字符串
     */
    private static String decodeUrlComponent(String raw) {
        try {
            return URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    /**
     * 规范化任务标识：清洗非法字符、截断过长标识，为空时使用 {@code "task"}。
     *
     * @param taskFlag 原始任务标识
     * @return 可安全用作目录名前缀的任务标识
     */
    private static String normalizeTaskFlag(String taskFlag) {
        if (!SfcStringUtils.hasText(taskFlag)) {
            return "task";
        }
        String normalized = ILLEGAL_FILE_NAME_CHARS.matcher(taskFlag.trim()).replaceAll("_");
        if (normalized.isEmpty()) {
            return "task";
        }
        return normalized.length() > MAX_TASK_FLAG_LENGTH
                ? normalized.substring(0, MAX_TASK_FLAG_LENGTH)
                : normalized;
    }

    /**
     * 按最大长度截断文件名，尽量保留末尾扩展名。
     *
     * @param fileName  待截断的文件名
     * @param maxLength 最大字符数
     * @return 截断后的文件名
     */
    private static String truncateFileName(String fileName, int maxLength) {
        if (maxLength <= 0 || fileName.length() <= maxLength) {
            return fileName;
        }
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && fileName.length() - dotIndex <= MAX_EXTENSION_LENGTH) {
            extension = fileName.substring(dotIndex);
        }
        int baseLength = maxLength - extension.length();
        if (baseLength <= 0) {
            return cutAtCodePointBoundary(fileName, maxLength);
        }
        return cutAtCodePointBoundary(fileName, baseLength) + extension;
    }

    /**
     * 在不超过指定长度的前提下截取字符串，并避免把代理对（surrogate pair）从中间切开。
     *
     * @param value     原始字符串
     * @param maxLength 最大字符数
     * @return 截取后的字符串
     */
    private static String cutAtCodePointBoundary(String value, int maxLength) {
        int end = Math.min(maxLength, value.length());
        if (end > 0 && end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }
}
