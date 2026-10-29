package com.sfc.download.util;

import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.constant.DownloadTaskConstants;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.transport.URIish;
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
 *     <li>校验下载来源协议（HTTP/HTTPS、磁力链、ed2k、Git 仓库、.torrent 种子地址）与解析 HTTP 请求方法；</li>
 *     <li>为下载引擎「按下载地址推断类型」提供来源语法判断（协议前缀、路径后缀、SCP 风格 Git 地址）。</li>
 * </ul>
 *
 * <p>本类不负责发起 HTTP 请求，也不依赖宿主（sfc-core）的任何类型；除本模块的日志前缀常量外，
 * 只使用 sfc-common 的字符串工具。</p>
 */
@Slf4j
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
     * SCP 风格的 Git 地址，例如 {@code git@github.com:owner/repo.git}。
     *
     * <p>用于来源语法识别与校验：SCP 风格地址没有 scheme，JGit 的 {@code URIish} 可直接解析，
     * 走 SSH 传输（由 {@link #isSshGitUrl(String)} 统一判定）。</p>
     */
    private static final Pattern SCP_LIKE_GIT = Pattern.compile("^[\\w.\\-]+@[\\w.\\-]+:.+$");

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
     * <p>语义与 {@link #isHttpUrl(String)} 完全一致，保留本方法是为了兼容既有调用方。</p>
     *
     * @param url 待校验的下载地址，允许为 {@code null}
     * @return 合法返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isValidUrl(String url) {
        return isHttpUrl(url);
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

    // ==================== 下载来源协议校验 ====================

    /**
     * 判断是否为合法的 HTTP/HTTPS 地址。
     *
     * <p>合法条件：非空白、可被 {@link URI} 解析、协议为 {@code http}/{@code https} 且带有主机名
     * （或至少带有 authority）。</p>
     *
     * @param url 待校验的下载地址，允许为 {@code null}
     * @return 合法返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isHttpUrl(String url) {
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
     * 判断是否为合法的磁力链（{@code magnet:?} 且携带 {@code xt=} 参数）。
     *
     * @param source 待校验的磁力链，允许为 {@code null}
     * @return 合法返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isValidMagnet(String source) {
        if (!SfcStringUtils.hasText(source)) {
            return false;
        }
        String value = source.trim();
        return value.regionMatches(true, 0, "magnet:?", 0, "magnet:?".length())
                && value.toLowerCase(Locale.ROOT).contains("xt=");
    }

    /**
     * 判断是否为合法的 ed2k 链接（{@code ed2k://} 前缀且后半段非空）。
     *
     * @param source 待校验的 ed2k 链接，允许为 {@code null}
     * @return 合法返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isValidEd2k(String source) {
        if (!SfcStringUtils.hasText(source)) {
            return false;
        }
        String value = source.trim();
        int prefixLength = "ed2k://".length();
        return value.regionMatches(true, 0, "ed2k://", 0, prefixLength) && value.length() > prefixLength;
    }

    /**
     * 判断是否为合法的 Git 仓库地址。
     *
     * <p>接受三类协议：{@code http/https}、{@code git://} 与 SSH（{@code ssh://} 或 SCP 风格
     * {@code user@host:path}）；{@code file://}、{@code ext::} 等本地协议一律返回 {@code false}，
     * 由调用方给出更具体的错误提示。</p>
     *
     * @param url 待校验的仓库地址，允许为 {@code null}
     * @return 合法返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isValidGitUrl(String url) {
        if (!SfcStringUtils.hasText(url)) {
            return false;
        }
        String value = url.trim();
        if (startsWithGitProtocol(value)) {
            return hasAuthority(value);
        }
        if (isSshGitUrl(value)) {
            return true;
        }
        return isHttpUrl(value);
    }

    /**
     * 判断是否为 SSH 形式的 Git 仓库地址。
     *
     * <p>两种形式：</p>
     * <ul>
     *   <li>{@code ssh://[user@]host[:port]/path}：要求 scheme 为 {@code ssh}、解析出主机且路径非空；</li>
     *   <li>SCP 风格 {@code user@host:path}：按 {@link #isScpLikeGit(String)} 的语法特征匹配。</li>
     * </ul>
     *
     * <p>本方法只做语法判断，不发起网络请求；地址合法性由创建任务阶段的来源校验使用。</p>
     *
     * @param url 仓库地址，允许为 {@code null}
     * @return 是 SSH 形式返回 {@code true}
     */
    public static boolean isSshGitUrl(String url) {
        if (!SfcStringUtils.hasText(url)) {
            return false;
        }
        String value = url.trim();
        if (value.toLowerCase(Locale.ROOT).startsWith("ssh://")) {
            try {
                URI uri = new URI(value);
                String path = uri.getPath();
                return "ssh".equalsIgnoreCase(uri.getScheme())
                        && uri.getHost() != null
                        && SfcStringUtils.hasText(path)
                        && !"/".equals(path);
            } catch (URISyntaxException e) {
                return false;
            }
        }
        return isScpLikeGit(value);
    }

    /**
     * 判断地址是否以 {@code git://} 协议开头（大小写不敏感）。
     *
     * <p>用于创建任务阶段判断「该地址是否支持代理」：{@code git://} 是裸 TCP 协议，
     * JGit 不提供代理支持，因此带代理的任务会被明确拒绝而不是静默失效。</p>
     *
     * @param url 地址，允许为 {@code null}
     * @return 以 {@code git://} 开头返回 {@code true}
     */
    public static boolean startsWithGitProtocol(String url) {
        return SfcStringUtils.hasText(url) && url.trim().toLowerCase(Locale.ROOT).startsWith("git://");
    }

    /**
     * 解析 SSH 地址中的用户名。
     *
     * <p>支持两种形式：{@code ssh://user@host/path} 取 URI 的 user 段；SCP 风格
     * {@code user@host:path} 取 {@code @} 之前的部分。非 SSH 地址返回 {@code null}。</p>
     *
     * @param url 仓库地址，允许为 {@code null}
     * @return 用户名；地址未携带用户名或不是 SSH 地址时返回 {@code null}
     */
    public static String parseSshUsername(String url) {
        if (!SfcStringUtils.hasText(url)) {
            return null;
        }
        String value = url.trim();
        if (value.toLowerCase(Locale.ROOT).startsWith("ssh://")) {
            try {
                String userInfo = new URI(value).getUserInfo();
                if (!SfcStringUtils.hasText(userInfo)) {
                    return null;
                }
                int colon = userInfo.indexOf(':');
                String user = colon >= 0 ? userInfo.substring(0, colon) : userInfo;
                return SfcStringUtils.hasText(user) ? user : null;
            } catch (URISyntaxException e) {
                return null;
            }
        }
        if (isScpLikeGit(value)) {
            String user = value.substring(0, value.indexOf('@'));
            return SfcStringUtils.hasText(user) ? user : null;
        }
        return null;
    }

    /**
     * 判断 JGit transport 的地址是否属于允许的 Git 传输协议。
     *
     * <p>用于<b>子模块</b>的安全校验：子模块地址来自仓库内的 {@code .gitmodules}，不受创建任务时的
     * 来源校验约束，必须在每次建立 transport 时再校验一次，避免通过恶意子模块地址让服务端访问
     * {@code file://} 本地路径（读取服务器本地仓库）或执行 {@code ext::} 远程助手命令。</p>
     *
     * <p>允许：{@code http}/{@code https}/{@code ssh}/{@code git}，以及带用户名的 SCP 风格
     * {@code user@host:path}（JGit 解析后 scheme 为空）；其余（{@code file:}、{@code ext::}、
     * 本地绝对路径、未知协议）一律拒绝。</p>
     *
     * @param uri JGit 解析后的地址，允许为 {@code null}
     * @return 允许返回 {@code true}
     */
    public static boolean isAllowedGitTransportUri(URIish uri) {
        if (uri == null) {
            return false;
        }
        String scheme = uri.getScheme();
        if (!SfcStringUtils.hasText(scheme)) {
            // SCP 风格（git@host:path）没有 scheme：要求有用户名、主机与非空相对路径
            String path = uri.getPath();
            return uri.getUser() != null
                    && uri.getHost() != null
                    && SfcStringUtils.hasText(path)
                    && !path.startsWith("/");
        }
        return switch (scheme.toLowerCase(Locale.ROOT)) {
            case "http", "https", "ssh", "git" -> uri.getHost() != null;
            default -> false;
        };
    }

    /**
     * 判断是否为指向 {@code .torrent} 文件的 HTTP/HTTPS 地址。
     *
     * <p>只按 URL 路径判断，忽略查询串与片段（例如 {@code /a.torrent?token=x} 仍判定为合法）。</p>
     *
     * @param url 待校验的地址，允许为 {@code null}
     * @return 是 .torrent 文件地址返回 {@code true}
     */
    public static boolean isTorrentUrl(String url) {
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
            String path = uri.getPath();
            return path != null && path.toLowerCase(Locale.ROOT).endsWith(".torrent");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * 判断下载来源是否以 {@code http://} 或 {@code https://} 开头。
     *
     * <p>只做语法前缀判断（大小写不敏感），<b>不校验地址是否合法</b>：用于「按下载地址推断类型」时
     * 给出类型候选，真正的合法性由 {@link #isHttpUrl(String)} 在创建任务阶段校验。</p>
     *
     * @param source 下载来源，允许为 {@code null}
     * @return 以 http/https 协议开头返回 {@code true}
     */
    public static boolean startsWithHttpScheme(String source) {
        if (!SfcStringUtils.hasText(source)) {
            return false;
        }
        String value = source.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("http://") || value.startsWith("https://");
    }

    /**
     * 去掉地址的查询串与片段，便于按路径后缀判断下载类型。
     *
     * <p>例如 {@code https://host/a.torrent?token=x#frag} → {@code https://host/a.torrent}。
     * 入参为 {@code null} 时返回空串。</p>
     *
     * @param url 原始地址，允许为 {@code null}
     * @return 只保留 scheme + authority + path 的部分
     */
    public static String stripQueryAndFragment(String url) {
        if (url == null) {
            return "";
        }
        int cut = url.length();
        int queryIndex = url.indexOf('?');
        if (queryIndex >= 0) {
            cut = queryIndex;
        }
        int fragmentIndex = url.indexOf('#');
        if (fragmentIndex >= 0 && fragmentIndex < cut) {
            cut = fragmentIndex;
        }
        return url.substring(0, cut);
    }

    /**
     * 判断是否为 SCP 风格的 Git 地址，例如 {@code git@github.com:owner/repo.git}。
     *
     * <p>SCP 风格地址没有 scheme，JGit 的 {@code URIish} 可直接解析并走 SSH 传输；
     * 本方法只做语法识别，真正的协议校验由 {@link #isSshGitUrl(String)} 与
     * {@link com.sfc.download.engine.DownloadSourceResolver} 完成。</p>
     *
     * @param source 下载来源，允许为 {@code null}
     * @return 是 SCP 风格地址返回 {@code true}
     */
    public static boolean isScpLikeGit(String source) {
        return SfcStringUtils.hasText(source) && SCP_LIKE_GIT.matcher(source.trim()).matches();
    }

    /**
     * 判断 URI 是否带有主机名或 authority。
     *
     * @param rawUrl 待解析的地址
     * @return 带有 host 或 authority 返回 {@code true}
     */
    private static boolean hasAuthority(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            return uri.getHost() != null || uri.getRawAuthority() != null;
        } catch (URISyntaxException e) {
            return false;
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
     * <p>用于任务收尾阶段的临时文件清理与下载产物清理。删除前会先清除只读属性：Windows 上 JGit 会把
     * Git 对象包（{@code pack-*.pack} / {@code pack-*.idx}）标记为只读，直接删除会抛
     * {@code AccessDeniedException}，导致子文件残留、父目录删不掉（例如 {@code keep-git-dir=false} 时
     * 网盘里仍残留 {@code .git}，以及临时目录无法回收）。其它平台上清除只读对可写文件是无害的空操作。</p>
     *
     * <p>删除失败不会向上抛出，但会记录一条 WARN 日志（含路径与异常类型），避免出现「静默残留」而难以定位。</p>
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
            // 目录分支：先递归删除子项（子项中的只读文件会在其自身的调用里被清除只读），
            // 子项删完后目录才可能为空，再走下面的「清只读 + 删除」
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                try (Stream<Path> children = Files.list(path)) {
                    children.forEach(DownloadFileUtils::deleteQuietly);
                }
            }
            clearReadOnly(path);
            Files.deleteIfExists(path);
        } catch (IOException | RuntimeException e) {
            // best-effort：清理失败不向上抛出，但必须留下可检索的日志（Windows 只读 pack 文件残留问题
            // 曾因完全静默而难以定位）
            log.warn("{}删除文件/目录失败，已跳过: path={}, cause={}: {}",
                    DownloadTaskConstants.LOG_PREFIX, path, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * 尽力清除只读属性。
     *
     * <p>Windows 上 JGit 会把 Git 对象包（{@code pack-*.pack} / {@code pack-*.idx}）标记为只读，
     * 直接删除会抛 {@code AccessDeniedException}；其它平台上该调用对可写文件是无害的空操作。
     * 清除失败不抛出：后续的删除结果才是最终判据。</p>
     *
     * @param path 目标路径
     */
    private static void clearReadOnly(Path path) {
        try {
            path.toFile().setWritable(true);
        } catch (RuntimeException ignored) {
            // best-effort：清不掉就继续尝试删除，最终由 deleteIfExists 的结果决定
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
            // best-effort：清理失败不影响下载流程，但保留调试日志便于排查
            log.debug("{}清理历史临时目录失败: tempDir={}, cause={}: {}",
                    DownloadTaskConstants.LOG_PREFIX, tempDir, e.getClass().getSimpleName(), e.getMessage());
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
            // best-effort：清理失败不影响下载流程，但保留调试日志便于排查
            log.debug("{}一次性清理历史临时目录失败: cause={}: {}",
                    DownloadTaskConstants.LOG_PREFIX, e.getClass().getSimpleName(), e.getMessage());
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
