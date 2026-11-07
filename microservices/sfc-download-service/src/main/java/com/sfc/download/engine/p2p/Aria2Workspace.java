package com.sfc.download.engine.p2p;

import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.config.Aria2Properties;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.util.DownloadFileUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * aria2 共享工作区：跨容器场景下本服务与 aria2 之间的目录映射与任务目录创建。
 *
 * <p><b>解决的问题：</b>aria2 只能在它自己的文件系统里写文件，而本服务需要读取产物并上传网盘。
 * 因此要么两者同机（直接使用本服务的任务临时目录），要么把同一个宿主目录分别挂载到两个容器，
 * 再通过 {@code shared-dir}（本服务侧路径）与 {@code remote-dir}（aria2 侧路径）描述这层映射。</p>
 *
 * <p><b>目录布局：</b>共享模式下的任务目录为
 * {@code <sharedDir>/offline-download/<任务标识>-<UUID>}，其中 {@code offline-download} 复用
 * {@link DownloadFileUtils#TEMP_DIR_NAME}，与未启用共享目录时的本服务临时目录同名。提交 aria2 时
 * {@code dir} 使用映射后的 aria2 侧路径；采集产物时再把 aria2 报告的路径反向映射回本服务可见路径。</p>
 *
 * <p><b>降级语义：</b>未配置 {@code shared-dir} 时全部方法退化为改造前的行为（本服务 JVM 临时目录 +
 * 本机绝对路径），因此 HTTP / Git 等其它下载类型与未接入共享卷的部署完全不受影响。</p>
 *
 * <p><b>配置校验：</b>由 {@link #validate()} 统一给出可读的中文原因（不抛异常），供能力探测与启动
 * 日志使用；配置非法只让 MAGNET / TORRENT 能力不可用，不影响服务启动与其它下载类型。</p>
 *
 * @see Aria2Properties
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Aria2Workspace {

    /**
     * 日志前缀，与下载任务其它组件保持一致。
     */
    private static final String LOG_PREFIX = DownloadTaskConstants.LOG_PREFIX;

    /**
     * 共享根目录下用于存放离线下载任务的子目录名，复用本服务临时目录的目录名。
     */
    private static final String SHARED_DIR_NAME = DownloadFileUtils.TEMP_DIR_NAME;

    /**
     * 共享工作区残留清理是否已执行（每个 JVM 生命周期只执行一次）。
     */
    private static final AtomicBoolean SHARED_STALE_CLEANED = new AtomicBoolean(false);

    /**
     * aria2 部署期配置（仅 RPC 地址、令牌与共享目录）。
     */
    private final Aria2Properties aria2Properties;

    /**
     * 输出生效的 aria2 部署配置，并在共享目录配置非法时给出可读的 WARN 提示。
     *
     * <p>只输出部署期字段（rpc-url、令牌掩码、共享目录映射）；P2P 开关、进度轮询间隔、无进度超时与
     * 种子体积上限属于 core 统一配置，由 {@link com.sfc.download.config.SfcDownloadConfigService}
     * 按 TTL 懒加载，<b>不在启动期访问 core</b>，避免 core 未就绪时拖慢启动或产生误导性告警。</p>
     *
     * <p>日志只输出令牌是否已配置（{@code ***} / {@code (未设置)}），绝不输出令牌明文。共享目录配置
     * 非法不影响服务启动，只会让 MAGNET / TORRENT 能力在能力声明与创建任务阶段被判定为不可用。</p>
     */
    @PostConstruct
    public void logEffectiveConfiguration() {
        String secret = SfcStringUtils.hasText(aria2Properties.getSecret()) ? "***" : "(未设置)";
        log.info("{}aria2 部署配置生效: rpcUrl={}, secret={}, mapping={}（P2P 开关 / 轮询间隔 / 无进度超时 / "
                        + "种子体积上限由 core 统一配置维护）",
                LOG_PREFIX, effectiveRpcUrl(), secret, describeMapping());
        log.info("{}aria2 运行参数（download.aria2.*）已迁移至 core 统一配置，部署环境变量 "
                        + "SFC_DOWNLOAD_ARIA2_ENABLED / POLL_INTERVAL / STALL_TIMEOUT / TORRENT_MAX_SIZE 不再生效；"
                        + "P2P 开关请在 core 后台「插件配置 → 离线下载 → aria2 P2P 下载」中设置",
                LOG_PREFIX);
        String invalid = validate();
        if (SfcStringUtils.hasText(invalid)) {
            log.warn("{}aria2 共享目录配置无效（MAGNET/TORRENT 能力将不可用，不影响服务启动）: {}",
                    LOG_PREFIX, invalid);
        }
    }

    /**
     * 是否配置了共享目录（{@code shared-dir} 或 {@code remote-dir} 任一非空白）。
     *
     * <p>用于启动日志与排障输出；注意「已配置」不等于「配置有效」，有效性由 {@link #validate()} 判定。
     * </p>
     *
     * @return 任一共享目录配置非空白时返回 {@code true}
     */
    public boolean isSharedEnabled() {
        return SfcStringUtils.hasText(sharedDirText()) || SfcStringUtils.hasText(remoteDirText());
    }

    /**
     * 校验共享目录配置。
     *
     * <p>校验规则：</p>
     * <ul>
     *   <li>两个目录都未配置 → 返回 {@code null}（未启用，不视为错误）；</li>
     *   <li>只配置了 {@code remote-dir} 而 {@code shared-dir} 为空 → 配置不完整；</li>
     *   <li>{@code shared-dir} 必须是绝对路径、存在、是目录且可写（提示检查共享卷挂载与权限）；</li>
     *   <li>{@code remote-dir} 若配置，必须是 POSIX 绝对路径（以 {@code /} 开头）。</li>
     * </ul>
     *
     * @return 不可用原因（中文）；配置可用或未配置时返回 {@code null}
     */
    public String validate() {
        String sharedDir = sharedDirText();
        String remoteDir = remoteDirText();
        boolean hasShared = SfcStringUtils.hasText(sharedDir);
        boolean hasRemote = SfcStringUtils.hasText(remoteDir);
        if (!hasShared && !hasRemote) {
            return null;
        }
        if (!hasShared) {
            return "共享目录配置不完整：已配置 remote-dir=" + normalizeRemoteDir(remoteDir)
                    + " 但 shared-dir 为空（两者需成对配置，remote-dir 留空表示与 shared-dir 相同）";
        }
        Path root;
        try {
            root = Path.of(sharedDir.trim());
        } catch (InvalidPathException e) {
            return "shared-dir 不是合法路径: " + sharedDir;
        }
        if (!root.isAbsolute()) {
            return "shared-dir 必须是绝对路径: " + sharedDir;
        }
        root = root.normalize();
        if (!Files.exists(root)) {
            return "shared-dir 不存在: " + root + "（请检查容器共享卷是否已挂载）";
        }
        if (!Files.isDirectory(root)) {
            return "shared-dir 不是目录: " + root;
        }
        if (!Files.isWritable(root)) {
            return "shared-dir 不可写: " + root + "（请检查共享卷挂载与目录权限）";
        }
        if (hasRemote) {
            String normalizedRemote = normalizeRemoteDir(remoteDir);
            if (!normalizedRemote.startsWith("/")) {
                return "remote-dir 必须是 POSIX 绝对路径（以 / 开头，例如 /downloads）: " + remoteDir;
            }
        }
        return null;
    }

    /**
     * 为一次 P2P 下载任务创建任务目录。
     *
     * <p>未启用共享目录（{@code shared-dir} 为空）时回退到
     * {@link DownloadFileUtils#createTaskTempDir(String)}，行为与改造前完全一致；启用时先校验共享目录
     * 配置，校验通过后在 {@code <sharedDir>/offline-download} 下创建 {@code <任务标识>-<UUID>} 目录，
     * 并在本 JVM 首次创建时顺带清理一次共享工作区中超过 7 天的历史残留。</p>
     *
     * <p><b>不自动创建共享根目录：</b>共享目录配置非法（例如漏挂共享卷导致 {@code shared-dir} 不存在、
     * 不可写，或 {@code remote-dir} 不是 POSIX 绝对路径）时直接抛 {@link IOException}，
     * <b>不会</b>创建 {@code shared-dir} 本身，避免把「漏挂载」静默补成一个空目录、掩盖部署错误。</p>
     *
     * @param taskFlag 任务标识（通常为下载任务 id），允许为 {@code null} 或空白
     * @return 已创建的任务目录
     * @throws IOException 共享目录配置非法或目录创建失败时抛出
     */
    public Path createTaskDir(String taskFlag) throws IOException {
        String sharedDir = sharedDirText();
        if (!SfcStringUtils.hasText(sharedDir)) {
            return DownloadFileUtils.createTaskTempDir(taskFlag);
        }
        // 先校验再创建：shared-dir 根目录缺失/不可写时必须失败，不能静默补建（否则漏挂共享卷会被掩盖）
        String invalid = validate();
        if (SfcStringUtils.hasText(invalid)) {
            throw new IOException("共享目录不可用: " + invalid);
        }
        Path workspace;
        try {
            workspace = workspaceRoot();
        } catch (InvalidPathException e) {
            throw new IOException("共享目录 shared-dir 不是合法路径: " + sharedDir, e);
        }
        Files.createDirectories(workspace);
        cleanupSharedStaleDirsOnce(workspace);
        return DownloadFileUtils.createTaskTempDir(workspace, taskFlag);
    }

    /**
     * 把本服务的任务目录映射为 aria2 侧的下载目录（提交任务时作为强制 {@code dir} 选项）。
     *
     * <p>未启用共享目录时返回本服务绝对路径的规范化字符串（与改造前一致）；启用时返回
     * {@code <remote-dir>/<任务目录相对共享根的路径>}，路径分隔符统一为 {@code '/'}。</p>
     *
     * @param localTaskDir 本服务的任务目录
     * @return aria2 侧目录字符串
     */
    public String toAria2Dir(Path localTaskDir) {
        Path normalizedLocal = localTaskDir.toAbsolutePath().normalize();
        if (!SfcStringUtils.hasText(sharedDirText())) {
            // 未启用共享目录：aria2 与下载服务同机，直接使用本服务可见的绝对路径
            return normalizedLocal.toString();
        }
        try {
            Path root = sharedRoot();
            String relative = toPosixPath(root.relativize(normalizedLocal));
            if (!SfcStringUtils.hasText(relative) || ".".equals(relative) || isParentRelative(relative)) {
                log.warn("{}aria2 任务目录不在共享目录下，回退为本地绝对路径: taskDir={}, sharedDir={}",
                        LOG_PREFIX, normalizedLocal, sharedDirText());
                return normalizedLocal.toString();
            }
            return joinPosix(effectiveRemoteDir(), relative);
        } catch (IllegalArgumentException e) {
            // 例如 Windows 下任务目录与共享目录不在同一盘符，无法计算相对路径
            log.warn("{}计算 aria2 共享目录映射失败，回退为本地绝对路径: taskDir={}, sharedDir={}, cause={}",
                    LOG_PREFIX, normalizedLocal, sharedDirText(), e.toString());
            return normalizedLocal.toString();
        }
    }

    /**
     * 把 aria2 报告的产物路径反向映射为本服务可见的本地路径。
     *
     * <p>未启用共享目录时直接返回 {@code Path.of(raw)}（与改造前一致）；启用时要求路径以
     * {@code <remote-dir>/} 为前缀（{@code remote-dir} 留空时按 {@code shared-dir} 的同路径处理），
     * 取相对部分后按 {@link Path} 分段解析到共享根之下，避免 Windows 盘符解析问题。</p>
     *
     * <p>以下情况返回 {@code null}（由调用方记录 WARN 并跳过该产物，不影响其它产物）：路径不匹配映射
     * 前缀、相对部分包含 {@code ..} 越界片段、解析结果不在共享根之下、路径语法非法。</p>
     *
     * @param aria2Path aria2 状态中 {@code files[].path} 报告的产物路径
     * @return 本服务可见的本地路径；无法映射时返回 {@code null}
     */
    public Path toLocalArtifactPath(String aria2Path) {
        if (!SfcStringUtils.hasText(aria2Path)) {
            return null;
        }
        String raw = aria2Path.trim();
        if (!SfcStringUtils.hasText(sharedDirText())) {
            // 未启用共享目录：aria2 与下载服务同机，报告的就是本服务可见路径
            return Path.of(raw);
        }
        try {
            Path root = sharedRoot();
            String relative = stripRemotePrefix(raw.replace('\\', '/'), effectiveRemoteDir());
            if (relative == null) {
                return null;
            }
            Path local = root;
            for (String segment : relative.split("/")) {
                if (segment.isEmpty() || ".".equals(segment)) {
                    continue;
                }
                if ("..".equals(segment)) {
                    return null;
                }
                local = local.resolve(segment);
            }
            local = local.normalize();
            return local.startsWith(root) ? local : null;
        } catch (IllegalArgumentException e) {
            // InvalidPathException 也是 IllegalArgumentException：路径语法非法时按「无法映射」处理
            return null;
        }
    }

    /**
     * 获取「本服务 ↔ aria2」目录映射的可读描述，用于启动日志与任务日志排障。
     *
     * @return 未启用时为「未启用共享目录（使用本服务临时目录）」；启用时为
     *         「本服务=/shared/download ↔ aria2=/downloads」形式；配置不完整/非法时给出对应提示
     */
    public String describeMapping() {
        String sharedDir = sharedDirText();
        String remoteDir = remoteDirText();
        if (!SfcStringUtils.hasText(sharedDir) && !SfcStringUtils.hasText(remoteDir)) {
            return "未启用共享目录（使用本服务临时目录）";
        }
        if (!SfcStringUtils.hasText(sharedDir)) {
            return "共享目录配置不完整（shared-dir 为空，remote-dir=" + normalizeRemoteDir(remoteDir) + "）";
        }
        try {
            return "本服务=" + toPosixPath(sharedRoot()) + " ↔ aria2=" + effectiveRemoteDir();
        } catch (InvalidPathException e) {
            return "共享目录配置无效（shared-dir=" + sharedDir + "）";
        }
    }

    // ==================== 内部辅助 ====================

    /**
     * 获取配置的 {@code shared-dir}（已去除首尾空白）。
     *
     * @return 共享目录配置文本；未配置时返回空串
     */
    private String sharedDirText() {
        return aria2Properties.getSharedDir() == null ? "" : aria2Properties.getSharedDir().trim();
    }

    /**
     * 获取配置的 {@code remote-dir}（已去除首尾空白）。
     *
     * @return aria2 侧目录配置文本；未配置时返回空串
     */
    private String remoteDirText() {
        return aria2Properties.getRemoteDir() == null ? "" : aria2Properties.getRemoteDir().trim();
    }

    /**
     * 获取生效的 RPC 地址（留空时回落到默认地址），仅用于启动日志。
     *
     * @return 生效的 RPC 地址
     */
    private String effectiveRpcUrl() {
        String rpcUrl = aria2Properties.getRpcUrl();
        return SfcStringUtils.hasText(rpcUrl) ? rpcUrl.trim() : Aria2Properties.DEFAULT_RPC_URL;
    }

    /**
     * 获取共享根目录（本服务侧，绝对路径、已规范化）。
     *
     * @return 共享根目录
     * @throws InvalidPathException {@code shared-dir} 不是合法路径时抛出
     */
    private Path sharedRoot() {
        return Path.of(sharedDirText()).toAbsolutePath().normalize();
    }

    /**
     * 获取共享工作区目录：{@code <sharedDir>/offline-download}。
     *
     * @return 共享工作区目录
     * @throws InvalidPathException {@code shared-dir} 不是合法路径时抛出
     */
    private Path workspaceRoot() {
        return sharedRoot().resolve(SHARED_DIR_NAME);
    }

    /**
     * 获取生效的 aria2 侧根目录：配置了 {@code remote-dir} 时取其归一化结果，否则与
     * {@code shared-dir} 同路径（转为 POSIX 形式）。
     *
     * @return aria2 侧根目录（POSIX 形式）
     */
    private String effectiveRemoteDir() {
        String remoteDir = remoteDirText();
        if (SfcStringUtils.hasText(remoteDir)) {
            return normalizeRemoteDir(remoteDir);
        }
        return toPosixPath(sharedRoot());
    }

    /**
     * 本 JVM 首次创建共享工作区任务目录时，清理一次共享工作区中超过 7 天未修改的残留。
     *
     * @param workspace 共享工作区目录
     */
    private static void cleanupSharedStaleDirsOnce(Path workspace) {
        if (!SHARED_STALE_CLEANED.compareAndSet(false, true)) {
            return;
        }
        // 传 null 表示使用工具类的默认最长保留时长（7 天）
        DownloadFileUtils.cleanupStaleTempDirs(workspace, null);
    }

    /**
     * 归一化 aria2 侧目录：反斜杠转正斜杠、去掉首尾空白与多余的尾部斜杠（根路径 {@code "/"} 保留）。
     *
     * @param dir 原始目录文本
     * @return 归一化后的 POSIX 风格目录
     */
    private static String normalizeRemoteDir(String dir) {
        String normalized = dir.trim().replace('\\', '/');
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 把路径转换为 POSIX 风格字符串（分隔符统一为 {@code '/'}）。
     *
     * @param path 路径
     * @return POSIX 风格路径字符串
     */
    private static String toPosixPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    /**
     * 拼接 aria2 侧目录与相对路径，避免出现重复斜杠。
     *
     * @param base     已归一化的 aria2 侧根目录
     * @param relative 相对路径（使用 {@code '/'} 分隔）
     * @return 拼接后的路径
     */
    private static String joinPosix(String base, String relative) {
        return base.endsWith("/") ? base + relative : base + "/" + relative;
    }

    /**
     * 去掉路径的 aria2 侧根目录前缀。
     *
     * @param normalizedAria2Path 已把反斜杠归一化为正斜杠的 aria2 路径
     * @param remoteBase          已归一化的 aria2 侧根目录
     * @return 相对部分；路径不在该前缀之下时返回 {@code null}
     */
    private static String stripRemotePrefix(String normalizedAria2Path, String remoteBase) {
        String prefix = remoteBase.endsWith("/") ? remoteBase : remoteBase + "/";
        if (!normalizedAria2Path.startsWith(prefix)) {
            return null;
        }
        return normalizedAria2Path.substring(prefix.length());
    }

    /**
     * 判断相对路径是否越出共享根（以 {@code ..} 开头）。
     *
     * @param relative 相对路径
     * @return 越界返回 {@code true}
     */
    private static boolean isParentRelative(String relative) {
        return "..".equals(relative) || relative.startsWith("../");
    }
}
