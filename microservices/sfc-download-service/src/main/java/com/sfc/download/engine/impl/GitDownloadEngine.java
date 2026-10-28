package com.sfc.download.engine.impl;

import com.sfc.cloud.task.worker.SfcTaskInterruptedException;
import com.sfc.cloud.task.worker.SfcTaskProgress;
import com.sfc.common.constant.ConfigInputType;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.ConfigNode;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.config.SfcDownloadConfigService;
import com.sfc.download.constant.DownloadOptionKeys;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.engine.DownloadArtifact;
import com.sfc.download.engine.DownloadContext;
import com.sfc.download.engine.DownloadEngine;
import com.sfc.download.engine.DownloadType;
import com.sfc.download.model.DownloadTaskParams;
import com.sfc.download.model.property.GitProperty;
import com.sfc.download.util.DownloadFileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Git 仓库克隆引擎（下载类型 {@link DownloadType#GIT}）。
 *
 * <p>使用 JGit 纯 Java 实现，<b>不依赖部署机安装 git 命令行</b>，因此
 * {@link #unavailableReason()} 恒为 {@code null}。把远端仓库克隆到任务临时目录下的
 * {@code <仓库名>} 子目录，随后采集其中全部普通文件作为产物；落盘服务据此在网盘上还原
 * {@code <仓库名>/...} 的目录结构。</p>
 *
 * <p><b>产物形态：</b>产物的 {@code relativePath} 形如 {@code my-repo/src/main/java/A.java}。
 * 落盘服务会自动把「全部产物的公共首层目录」（此处即 {@code my-repo}）识别为网盘上的顶层目录。</p>
 *
 * <p><b>空目录不会被保留：</b>落盘只上传文件，Git 仓库中的空目录（Git 本身也不跟踪空目录）
 * 不会出现在网盘上。</p>
 *
 * <p><b>进度语义：</b>JGit 以「工作单元（work unit）」而非字节数汇报进度，本引擎把各阶段
 * （枚举对象、压缩、接收、解析差异…）的工作量累加为总量、把已完成量累加为已下载量，
 * 因此进度条比例是准确的，但<b>单位不是字节</b>；任务的 {@code size} 由任务处理器在落盘后
 * 按实际上传字节数回写。</p>
 *
 * <p><b>并发说明：</b>本类是 Spring 单例，会被 Worker 线程池并发调用，自身不持有任何任务级
 * 可变状态：一次执行的全部状态都保存在每次新建的 {@link CloneSession} 中。</p>
 *
 * @see DownloadEngine
 * @see GitProperty
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitDownloadEngine implements DownloadEngine {

    /**
     * 应用日志前缀：与任务运行日志共用同一前缀，便于按前缀检索。
     */
    private static final String LOG_PREFIX = DownloadTaskConstants.LOG_PREFIX;

    /**
     * 无法从仓库地址解析出名称时使用的兜底目录名。
     */
    private static final String DEFAULT_REPO_NAME = "repo";

    /**
     * 克隆产物中版本库元数据目录的名称。
     */
    private static final String GIT_DIR_NAME = ".git";

    /**
     * 仓库地址的 Git 后缀。
     */
    private static final String GIT_SUFFIX = ".git";

    /**
     * 未提供令牌用户名时使用的默认用户名。
     *
     * <p>GitHub / GitLab 等平台在使用访问令牌作为密码时，用户名可任意填写；
     * 这里取通用的 {@code git}，避免要求调用方额外提供。</p>
     */
    private static final String DEFAULT_TOKEN_USERNAME = "git";

    /**
     * Git 配置未给出连接超时时使用的默认值（秒）。
     */
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 30;

    /**
     * 写入任务失败信息前的最大长度，避免超长堆栈撑爆数据库行。
     */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 512;

    /**
     * 统一配置访问入口：每次执行时现取 {@link GitProperty}，保证 core 后台修改后能生效。
     */
    private final SfcDownloadConfigService configService;

    /**
     * {@inheritDoc}
     *
     * @return {@code "git"}
     */
    @Override
    public String getEngineId() {
        return "git";
    }

    /**
     * 本引擎支持的下载类型。
     *
     * @return 仅包含 {@link DownloadType#GIT} 的不可变集合
     */
    @Override
    public Set<DownloadType> supports() {
        return Set.of(DownloadType.GIT);
    }

    /**
     * {@inheritDoc}
     *
     * <p>按 Git 来源特征判断：{@code git://} / {@code ssh://} 前缀、SCP 风格 {@code git@host:path}，
     * 或 {@code http(s)} 地址的路径（去掉查询串与片段）以 {@code .git} 结尾。SSH 形式在这里也会被识别为
     * Git（便于前端选中 Git 后收到更准确的提示），真正的协议限制由创建任务阶段的来源校验执行。</p>
     */
    @Override
    public boolean matchesSource(DownloadType type, String source) {
        if (type != DownloadType.GIT || !SfcStringUtils.hasText(source)) {
            return false;
        }
        String value = source.trim();
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("git://") || lower.startsWith("ssh://") || DownloadFileUtils.isScpLikeGit(value)) {
            return true;
        }
        if (!DownloadFileUtils.startsWithHttpScheme(value)) {
            return false;
        }
        return DownloadFileUtils.stripQueryAndFragment(value)
                .toLowerCase(java.util.Locale.ROOT)
                .endsWith(GIT_SUFFIX);
    }

    /**
     * {@inheritDoc}
     *
     * <p>JGit 纯 Java 实现，无外部程序依赖，因此始终可用。</p>
     *
     * @return 恒为 {@code null}
     */
    @Override
    public String unavailableReason() {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * @return 固定返回「Git 克隆」
     */
    @Override
    public String taskName() {
        return "Git 克隆";
    }

    /**
     * {@inheritDoc}
     *
     * <p>Git 克隆属于网络 IO 密集型任务，沿用默认的 CPU 开销指数。</p>
     *
     * @return 默认 CPU 开销指数
     */
    @Override
    public int cpuOverhead() {
        return DownloadEngine.super.cpuOverhead();
    }

    /**
     * {@inheritDoc}
     *
     * <p>默认值取自统一配置（{@code download.git.*}），因此能力声明接口展示的默认值与任务实际
     * 执行时的取值一致；访问令牌属于凭证，按「打码」声明。</p>
     */
    @Override
    public List<ConfigNode> options() {
        GitProperty config = currentConfig();
        return List.of(
                option(DownloadOptionKeys.GIT_BRANCH, "分支/标签", ConfigInputType.TEXT, 10)
                        .describe("留空表示使用仓库默认分支")
                        .build(),
                option(DownloadOptionKeys.GIT_DEPTH, "克隆深度", ConfigInputType.TEXT, 20)
                        .defaultValue(config.getDefaultDepth() == null ? "0" : String.valueOf(config.getDefaultDepth()))
                        .describe("整数；0 表示全量克隆，大于 0 时执行浅克隆")
                        .build(),
                option(DownloadOptionKeys.GIT_SUBMODULES, "克隆子模块", ConfigInputType.SWITCH, 30)
                        .defaultValue(String.valueOf(Boolean.FALSE))
                        .describe("是否一并克隆仓库的子模块")
                        .build(),
                option(DownloadOptionKeys.STRIP_GIT_DIR, "删除 .git 目录", ConfigInputType.SWITCH, 40)
                        .defaultValue(String.valueOf(!Boolean.TRUE.equals(config.getKeepGitDir())))
                        .describe("网盘中只保留工作区文件，不保留版本库元数据")
                        .build(),
                option(DownloadOptionKeys.GIT_USERNAME, "用户名", ConfigInputType.TEXT, 50)
                        .describe("私有仓库的 Basic Auth 用户名，与访问令牌成对使用")
                        .build(),
                option(DownloadOptionKeys.GIT_TOKEN, "访问令牌", ConfigInputType.TEXT, 60)
                        .isMask(true)
                        .describe("私有仓库的访问令牌或密码，敏感信息，不会回显")
                        .build()
        );
    }

    /**
     * 创建一个选项节点的构建器，统一填好键名、展示名、输入控件与排序。
     *
     * @param name      选项键名，即任务参数 {@code options} 中的 key
     * @param title     面向用户的展示名
     * @param inputType 输入控件类型，取值见 {@link ConfigInputType}
     * @param sort      排序值，同一引擎内按升序展示
     * @return 选项节点构建器，调用方补充默认值与说明后 {@code build()}
     */
    private static ConfigNode.ConfigNodeBuilder option(String name, String title, String inputType, int sort) {
        return ConfigNode.builder()
                .name(name)
                .title(title)
                .inputType(inputType)
                .sort(sort);
    }

    /**
     * {@inheritDoc}
     *
     * <p>只校验本引擎专属的 {@code options}；仓库地址的协议白名单与格式校验由
     * {@link com.sfc.download.engine.DownloadEngineRegistry} 统一完成，此处不重复。</p>
     */
    @Override
    public void validate(DownloadTaskParams params) {
        if (params == null) {
            throw new SfcJsonException(400, "下载参数不能为空");
        }
        // 解析一次即完成全部取值校验，避免「创建期放过、执行期才炸」
        parseOptions(params, currentConfig());
    }

    /**
     * {@inheritDoc}
     *
     * <p>每个任务创建一个独立的 {@link CloneSession} 承载本次执行的可变状态。</p>
     */
    @Override
    public List<DownloadArtifact> execute(DownloadContext context) {
        return new CloneSession(context).run();
    }

    // ==================== 选项解析 ====================

    /**
     * 读取当前 Git 统一配置快照。
     *
     * <p>统一配置服务保证不返回 {@code null}；这里再做一次兜底，避免配置实现异常导致空指针。</p>
     *
     * @return Git 配置，永不为 {@code null}
     */
    private GitProperty currentConfig() {
        GitProperty config = configService.getGit();
        return config == null ? new GitProperty() : config;
    }

    /**
     * 解析并校验任务选项。
     *
     * <p>{@code options} 是松散的 {@code Map<String, Object>}，取值可能来自 JSON 反序列化后的
     * 任意类型，因此统一按「宽松转换」读取：数字接受 {@link Number} 与数字字符串，布尔接受
     * {@link Boolean} 与 {@code "true"/"false"}，其余类型给出明确的 400 提示。</p>
     *
     * @param params 下载任务参数
     * @param config Git 统一配置（提供默认值）
     * @return 解析后的克隆选项
     * @throws SfcJsonException 任一选项取值非法时抛出（HTTP 400 语义）
     */
    private CloneOptions parseOptions(DownloadTaskParams params, GitProperty config) {
        Map<String, Object> options = params == null ? null : params.options;

        String branch = readString(options, DownloadOptionKeys.GIT_BRANCH, "分支");
        Integer depth = readInteger(options, DownloadOptionKeys.GIT_DEPTH, "克隆深度", config.getDefaultDepth());
        if (depth == null) {
            depth = 0;
        }
        if (depth < 0) {
            throw new SfcJsonException(400, "参数 克隆深度(gitDepth) 不能为负数: " + depth);
        }
        Boolean submodules = readBoolean(options, DownloadOptionKeys.GIT_SUBMODULES, "克隆子模块", Boolean.FALSE);
        Boolean stripGitDir = readBoolean(options, DownloadOptionKeys.STRIP_GIT_DIR, "删除 .git 目录", null);
        // 优先级：任务选项 > 服务端配置的反值；都没有时默认删除 .git
        boolean strip = stripGitDir != null
                ? stripGitDir
                : !Boolean.TRUE.equals(config.getKeepGitDir());

        String username = readString(options, DownloadOptionKeys.GIT_USERNAME, "用户名");
        String token = readString(options, DownloadOptionKeys.GIT_TOKEN, "访问令牌");
        if (SfcStringUtils.hasText(token) && !SfcStringUtils.hasText(username)) {
            username = DEFAULT_TOKEN_USERNAME;
        }
        if (SfcStringUtils.hasText(username) && !SfcStringUtils.hasText(token)) {
            throw new SfcJsonException(400, "参数 用户名(gitUsername) 与 访问令牌(gitToken) 必须成对提供");
        }
        return new CloneOptions(branch, depth, Boolean.TRUE.equals(submodules), strip, username, token);
    }

    /**
     * 读取字符串选项。
     *
     * @param options     选项表，允许为 {@code null}
     * @param key         选项键
     * @param displayName 面向用户的选项名称，用于错误提示
     * @return 去空白后的字符串；未提供或为空白时返回 {@code null}
     */
    private static String readString(Map<String, Object> options, String key, String displayName) {
        Object value = options == null ? null : options.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return SfcStringUtils.hasText(text) ? text.trim() : null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            // 宽松处理：标量值直接转字符串，避免前端把数字/布尔传成字符串之外的类型时失败
            return String.valueOf(value);
        }
        throw new SfcJsonException(400, "参数 " + displayName + "(" + key + ") 取值非法: " + value);
    }

    /**
     * 读取整数选项。
     *
     * @param options      选项表，允许为 {@code null}
     * @param key          选项键
     * @param displayName  面向用户的选项名称，用于错误提示
     * @param defaultValue 未提供时使用的默认值
     * @return 解析后的整数；未提供时返回 {@code defaultValue}
     * @throws SfcJsonException 取值无法转换为整数时抛出（HTTP 400 语义）
     */
    private static Integer readInteger(Map<String, Object> options, String key, String displayName,
                                       Integer defaultValue) {
        Object value = options == null ? null : options.get(key);
        return convertInteger(key, displayName, value, defaultValue);
    }

    /**
     * 把任意取值宽松转换为整数。
     *
     * @param key          选项键（用于错误提示）
     * @param displayName  面向用户的选项名称
     * @param value        原始取值，允许为 {@code null}
     * @param defaultValue 取值为空时的默认值
     * @return 解析后的整数；取值为空时返回 {@code defaultValue}
     * @throws SfcJsonException 取值无法转换为整数时抛出
     */
    private static Integer convertInteger(String key, String displayName, Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            if (!SfcStringUtils.hasText(text)) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                throw new SfcJsonException(400, "参数 " + displayName + "(" + key + ") 取值非法: " + text);
            }
        }
        throw new SfcJsonException(400, "参数 " + displayName + "(" + key + ") 取值非法: " + value);
    }

    /**
     * 读取布尔选项。
     *
     * @param options      选项表，允许为 {@code null}
     * @param key          选项键
     * @param displayName  面向用户的选项名称，用于错误提示
     * @param defaultValue 未提供时使用的默认值
     * @return 解析后的布尔值；未提供时返回 {@code defaultValue}
     * @throws SfcJsonException 取值无法转换为布尔值时抛出（HTTP 400 语义）
     */
    private static Boolean readBoolean(Map<String, Object> options, String key, String displayName,
                                       Boolean defaultValue) {
        Object value = options == null ? null : options.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            if (!SfcStringUtils.hasText(text)) {
                return defaultValue;
            }
            String normalized = text.trim();
            if ("true".equalsIgnoreCase(normalized)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(normalized)) {
                return Boolean.FALSE;
            }
            throw new SfcJsonException(400, "参数 " + displayName + "(" + key + ") 取值非法: " + text);
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        throw new SfcJsonException(400, "参数 " + displayName + "(" + key + ") 取值非法: " + value);
    }

    // ==================== 仓库名解析 ====================

    /**
     * 从仓库地址解析用于本地目录名与网盘顶层目录名的仓库名。
     *
     * <p>取地址最后一段并去掉 {@code .git} 后缀，再按文件系统规则清洗；无法解析时回退
     * {@value #DEFAULT_REPO_NAME}。</p>
     *
     * @param url 仓库地址，允许为 {@code null}
     * @return 可安全用作目录名的仓库名，永不为 {@code null} 且永不为空白
     */
    private static String resolveRepoName(String url) {
        if (!SfcStringUtils.hasText(url)) {
            return DEFAULT_REPO_NAME;
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        int separator = trimmed.lastIndexOf('/');
        String lastName = separator < 0 ? trimmed : trimmed.substring(separator + 1);
        if (lastName.toLowerCase(java.util.Locale.ROOT).endsWith(GIT_SUFFIX)) {
            lastName = lastName.substring(0, lastName.length() - GIT_SUFFIX.length());
        }
        if (!SfcStringUtils.hasText(lastName)) {
            return DEFAULT_REPO_NAME;
        }
        String sanitized = DownloadFileUtils.sanitizeFileName(lastName);
        return SfcStringUtils.hasText(sanitized) ? sanitized : DEFAULT_REPO_NAME;
    }

    /**
     * 对仓库地址做脱敏，去掉可能内嵌的 {@code user:password@} 用户信息后再写日志。
     *
     * @param url 仓库地址，允许为 {@code null}
     * @return 脱敏后的地址，永不为 {@code null}
     */
    private static String maskUrl(String url) {
        if (!SfcStringUtils.hasText(url)) {
            return "";
        }
        String trimmed = url.trim();
        int schemeIndex = trimmed.indexOf("://");
        if (schemeIndex < 0) {
            return trimmed;
        }
        int authorityStart = schemeIndex + 3;
        int pathStart = trimmed.indexOf('/', authorityStart);
        if (pathStart < 0) {
            pathStart = trimmed.length();
        }
        int atIndex = trimmed.indexOf('@', authorityStart);
        if (atIndex < 0 || atIndex > pathStart) {
            return trimmed;
        }
        return trimmed.substring(0, authorityStart) + "***@" + trimmed.substring(atIndex + 1);
    }

    // ==================== 单次克隆执行 ====================

    /**
     * 单次 Git 克隆执行的可变状态承载者。
     *
     * <p>引擎本身是无状态的单例，每次任务执行都新建一个本类实例，因此不存在跨任务的状态污染。</p>
     */
    private final class CloneSession {

        /**
         * 任务执行上下文（进度、日志、中断协作与临时目录）。
         */
        private final DownloadContext context;

        /**
         * 任务参数。
         */
        private final DownloadTaskParams params;

        /**
         * 本次执行读取到的 Git 配置快照。
         */
        private final GitProperty config;

        /**
         * 本次执行的克隆选项（已通过 {@link #parseOptions} 校验）。
         */
        private final CloneOptions options;

        /**
         * 构造单次克隆执行上下文。
         *
         * @param context 任务执行上下文
         */
        private CloneSession(DownloadContext context) {
            this.context = context;
            this.params = context.getParams();
            this.config = currentConfig();
            this.options = parseOptions(params, config);
        }

        /**
         * 执行克隆并采集产物。
         *
         * @return 产物列表，至少包含一个文件
         * @throws SfcTaskInterruptedException 任务被中断时抛出
         * @throws SfcJsonException           克隆失败时抛出
         */
        private List<DownloadArtifact> run() {
            String url = params.url == null ? null : params.url.trim();
            String repoName = resolveRepoName(url);
            Path root = context.getTempDir();
            Path target = root.resolve(repoName);

            context.log("开始克隆 Git 仓库: " + repoName
                    + "（分支=" + (options.branch() == null ? "默认" : options.branch())
                    + ", 深度=" + (options.depth() > 0 ? String.valueOf(options.depth()) : "全量") + "）");
            log.info("{}开始克隆 Git 仓库: downloadId={}, 仓库={}, 分支={}, 深度={}, 子模块={}, 删除.git={}",
                    LOG_PREFIX, context.getDownloadId(), maskUrl(url),
                    options.branch() == null ? "默认" : options.branch(), options.depth(),
                    options.submodules(), options.stripGitDir());

            long startNanos = System.nanoTime();
            cloneRepository(url, target);

            if (options.stripGitDir()) {
                removeGitDir(target);
            }

            List<DownloadArtifact> artifacts = collectArtifacts(root);
            if (artifacts.isEmpty()) {
                throw new SfcJsonException(500, "Git 克隆完成但未采集到任何文件: " + repoName);
            }
            context.log("Git 克隆完成: 文件数=" + artifacts.size()
                    + ", 耗时=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos) + "ms");
            log.info("{}Git 克隆完成: downloadId={}, 仓库={}, 文件数={}, 耗时={}ms",
                    LOG_PREFIX, context.getDownloadId(), repoName, artifacts.size(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
            return artifacts;
        }

        /**
         * 调用 JGit 完成克隆。
         *
         * @param url    仓库地址
         * @param target 本地目标目录（不存在时由 JGit 创建）
         * @throws SfcTaskInterruptedException 任务被中断时抛出
         * @throws SfcJsonException           克隆失败时抛出
         */
        private void cloneRepository(String url, Path target) {
            try (Git ignored = buildCloneCommand(url, target).call()) {
                // Git 实例只用于持有底层 Repository 的资源，克隆结束后即可关闭
            } catch (SfcTaskInterruptedException e) {
                throw e;
            } catch (GitAPIException e) {
                context.checkInterrupted();
                throw translate(e);
            } catch (RuntimeException e) {
                // 中断回调或进度回调可能以运行时异常的形式逃逸，统一按「取消」语义还原
                context.checkInterrupted();
                throw translate(e);
            }
        }

        /**
         * 组装 JGit 克隆命令。
         *
         * @param url    仓库地址
         * @param target 本地目标目录
         * @return 已按任务选项与统一配置配置好的克隆命令
         */
        private org.eclipse.jgit.api.CloneCommand buildCloneCommand(String url, Path target) {
            org.eclipse.jgit.api.CloneCommand command = Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(target.toFile())
                    .setProgressMonitor(new CloneProgressMonitor(context))
                    .setTimeout(resolveTimeoutSeconds());
            if (options.branch() != null) {
                command.setBranch(options.branch());
            }
            if (options.depth() > 0) {
                command.setDepth(options.depth());
            }
            if (options.submodules()) {
                command.setCloneSubmodules(true);
            }
            if (SfcStringUtils.hasText(options.token())) {
                command.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(options.username(), options.token()));
            }
            return command;
        }

        /**
         * 解析本次克隆使用的连接超时（秒）。
         *
         * @return 连接超时秒数，配置缺失或非法时使用默认值
         */
        private int resolveTimeoutSeconds() {
            Integer timeout = config.getConnectTimeout();
            return timeout == null || timeout <= 0 ? DEFAULT_CONNECT_TIMEOUT_SECONDS : timeout;
        }

        /**
         * 删除克隆产物中的 {@code .git} 目录（尽力而为，失败只记录日志）。
         *
         * @param target 仓库本地目录
         */
        private void removeGitDir(Path target) {
            Path gitDir = target.resolve(GIT_DIR_NAME);
            if (Files.notExists(gitDir)) {
                return;
            }
            context.log("删除版本库元数据目录: " + GIT_DIR_NAME);
            log.info("{}删除克隆产物的 .git 目录: downloadId={}, path={}",
                    LOG_PREFIX, context.getDownloadId(), gitDir.toAbsolutePath());
            DownloadFileUtils.deleteQuietly(gitDir);
            if (Files.exists(gitDir)) {
                log.warn("{}删除 .git 目录未完全成功（可能被占用），产物中仍会包含版本库元数据: path={}",
                        LOG_PREFIX, gitDir.toAbsolutePath());
            }
        }

        /**
         * 递归采集任务临时目录下的全部普通文件作为产物。
         *
         * @param root 任务临时目录
         * @return 产物列表（按相对路径排序，保证上传顺序稳定）
         * @throws SfcJsonException 遍历失败时抛出
         */
        private List<DownloadArtifact> collectArtifacts(Path root) {
            List<DownloadArtifact> artifacts = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .sorted()
                        .forEach(file -> artifacts.add(DownloadArtifact.of(root, file)));
            } catch (IOException e) {
                throw new SfcJsonException(500, "遍历克隆产物失败: " + truncate(describe(e)));
            }
            return artifacts;
        }

        /**
         * 把克隆异常翻译为面向用户的业务异常。
         *
         * @param e 原始异常
         * @return 携带可读原因的业务异常（HTTP 502 语义）
         */
        private SfcJsonException translate(Throwable e) {
            return new SfcJsonException(502, "Git 克隆失败: " + truncate(describe(e)));
        }
    }

    // ==================== 进度 ====================

    /**
     * 把 JGit 的「工作单元」进度桥接到 Worker 的任务进度上。
     *
     * <p>JGit 的克隆过程分为多个阶段，每个阶段都会调用一次 {@link #beginTask(String, int)}
     * 汇报本阶段的总工作量，随后通过 {@link #update(int)} 汇报增量。本实现把各阶段总量与已完成量
     * 分别累加，因此进度比例准确；但单位是工作单元而非字节。</p>
     *
     * <p>中断出口：{@link #update(int)} 在每次进度汇报时调用
     * {@link DownloadContext#checkInterrupted()}，这是取消 Git 克隆最及时的检查点
     * （克隆的阻塞点在 JGit 内部的网络读取上，无法从外部关闭）。</p>
     */
    private static final class CloneProgressMonitor implements ProgressMonitor {

        /**
         * 任务执行上下文。
         */
        private final DownloadContext context;

        /**
         * 构造进度监视器。
         *
         * @param context 任务执行上下文
         */
        private CloneProgressMonitor(DownloadContext context) {
            this.context = context;
        }

        /**
         * 提交阶段开始前的回调，本实现不做处理。
         *
         * @param totalTasks 提交阶段的总任务数
         */
        @Override
        public void start(int totalTasks) {
            // 无需处理：总量按各阶段 beginTask 累加
        }

        /**
         * 单个阶段开始：把本阶段的工作量累加到任务总进度上。
         *
         * @param title     阶段名称
         * @param totalWork 本阶段工作单元总数，小于等于 0 表示未知
         */
        @Override
        public void beginTask(String title, int totalWork) {
            context.log("Git 阶段开始: " + describeTask(title, totalWork));
            if (totalWork <= 0) {
                return;
            }
            SfcTaskProgress progress = context.getExecution().getProgress();
            long current = progress.getTotal();
            progress.setTotal(current < 0 ? totalWork : current + totalWork);
        }

        /**
         * 阶段进度推进：累加已完成量并检查中断。
         *
         * @param completed 本次新增的已完成工作单元数
         * @throws SfcTaskInterruptedException 任务已被中断时抛出
         */
        @Override
        public void update(int completed) {
            if (completed > 0) {
                context.getExecution().getProgress().addLoaded(completed);
            }
            context.checkInterrupted();
        }

        /**
         * 单个阶段结束，本实现不做处理。
         */
        @Override
        public void endTask() {
            // 无需处理：总量与已完成量已分别累加
        }

        /**
         * 是否请求取消。
         *
         * <p>返回 {@code false}：JGit 在收到取消后会静默结束但不会抛出可识别的信号，
         * 因此取消统一由 {@link #update(int)} 中的 {@link DownloadContext#checkInterrupted()}
         * 以中断异常的方式退出。</p>
         *
         * @return 恒为 {@code false}
         */
        @Override
        public boolean isCancelled() {
            return false;
        }

        /**
         * 是否显示耗时统计，本实现不做处理（进度已通过任务进度上报给 core，无需二次输出）。
         *
         * @param enabled 是否显示耗时统计
         */
        @Override
        public void showDuration(boolean enabled) {
            // 无需处理
        }

        /**
         * 生成阶段日志文本。
         *
         * @param title     阶段名称
         * @param totalWork 阶段工作单元总数
         * @return 可读的阶段描述
         */
        private static String describeTask(String title, int totalWork) {
            if (!SfcStringUtils.hasText(title)) {
                return "总量=" + totalWork;
            }
            return title + "（总量=" + (totalWork > 0 ? totalWork : "未知") + "）";

        }
    }

    // ==================== 对外数据结构与工具 ====================

    /**
     * 一次 Git 克隆的解析后选项。
     *
     * @param branch      分支或标签名，{@code null} 表示使用仓库默认分支
     * @param depth       克隆深度，{@code 0} 表示全量克隆
     * @param submodules  是否克隆子模块
     * @param stripGitDir 是否在克隆完成后删除 {@code .git} 目录
     * @param username    私有仓库用户名，可为 {@code null}
     * @param token       私有仓库访问令牌，可为 {@code null}
     */
    private record CloneOptions(String branch, int depth, boolean submodules, boolean stripGitDir,
                                String username, String token) {
    }

    /**
     * 提取异常链最内层的可读消息。
     *
     * @param e 异常，允许为 {@code null}
     * @return 可读消息，永不为 {@code null}
     */
    private static String describe(Throwable e) {
        if (e == null) {
            return "未知错误";
        }
        Throwable current = e;
        String message = e.getMessage();
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            if (SfcStringUtils.hasText(current.getMessage())) {
                message = current.getMessage();
            }
        }
        if (!SfcStringUtils.hasText(message)) {
            message = e.getClass().getSimpleName();
        }
        return message;
    }

    /**
     * 截断错误信息，并避免把代理对（surrogate pair）从中间切开。
     *
     * @param message 原始消息，允许为 {@code null}
     * @return 截断后的消息
     */
    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        int end = MAX_ERROR_MESSAGE_LENGTH;
        if (Character.isHighSurrogate(message.charAt(end - 1))) {
            end--;
        }
        return message.substring(0, end) + "...";
    }
}
