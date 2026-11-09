package com.sfc.download.engine.p2p;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sfc.cloud.task.worker.SfcTaskDetachedException;
import com.sfc.cloud.task.worker.SfcTaskInterruptedException;
import com.sfc.common.constant.ConfigInputType;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.ConfigNode;
import com.sfc.common.utils.SfcMapperHolder;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.config.Aria2Properties;
import com.sfc.download.config.SfcDownloadConfigService;
import com.sfc.download.config.SfcDownloadInternalProperties;
import com.sfc.download.constant.DownloadOptionKeys;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.engine.DownloadArtifact;
import com.sfc.download.engine.DownloadContext;
import com.sfc.download.engine.DownloadEngine;
import com.sfc.download.engine.DownloadType;
import com.sfc.download.model.DownloadTaskParams;
import com.sfc.download.model.property.Aria2Property;
import com.sfc.download.util.DownloadFileUtils;
import com.sfc.download.util.DownloadProxyUtils;
import com.sfc.download.util.DownloadTaskNameUtils;
import com.sfc.download.util.IgnoreSSLHttpRequestFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * P2P 下载引擎：通过 aria2 的 JSON-RPC 接口下载磁力链与种子。
 *
 * <p><b>支持的下载类型：</b>{@link DownloadType#MAGNET}（磁力链）与 {@link DownloadType#TORRENT}
 * （{@code .torrent} 地址或 Base64 种子内容）。两者共用同一个 aria2 引擎，执行时通过
 * {@code DownloadTaskParams#type} 区分本次任务的具体形态——该字段由调用方在创建任务时显式指定，
 * 引擎不做任何猜测。</p>
 *
 * <p><b>部署前提：</b>aria2c 需通过 JSON-RPC 可达，且允许本服务通过 JSON-RPC 指定每个任务的下载目录
 * （{@code dir} 选项）。产物读取有两种部署方式：</p>
 * <ul>
 *   <li><b>同机部署</b>：aria2 与下载服务在同一文件系统下运行，直接使用本服务的任务临时目录；</li>
 *   <li><b>跨容器部署</b>：把同一个宿主目录分别挂载到两个容器，并配置部署参数 {@code shared-dir}
 *       （本服务侧路径）与 {@code remote-dir}（aria2 侧路径）。此时任务目录落在共享工作区
 *       {@code <shared-dir>/offline-download} 下，提交 aria2 时 {@code dir} 使用映射后的 aria2 侧
 *       路径，采集产物时再反向映射回本服务可见路径（见 {@link Aria2Workspace}）。</li>
 * </ul>
 * <p>两者都要求 aria2 实际写入的目录能被本服务读到，否则会因产物采集为空而失败。</p>
 *
 * <p><b>选项安全：</b>用户只能通过 {@link DownloadOptionKeys#ARIA2_OPTIONS} 传入白名单内的 aria2 选项；
 * 涉及本地路径的 {@code dir}、{@code out} 等由服务端强制设置，用户传入会被丢弃，避免越权写盘。</p>
 *
 * <p><b>并发：</b>引擎是 Spring 单例，任务级状态全部保存在 {@link DownloadContext} 与
 * {@link Aria2TaskSession} 中，不持有跨任务可变状态（能力探测缓存除外，它是全局共享且线程安全的）。</p>
 *
 * <p><b>生命周期收口：</b>本引擎只提供「提交 / 轮询 / 采集 / 丢弃结果」等原子能力，
 * <b>不自行清理</b> aria2 任务结果；取消、失败、成功与挂起时的清理策略统一由
 * {@link P2pTaskCoordinator} 按生命周期收口矩阵执行。</p>
 *
 * @see Aria2Client
 * @see P2pTaskCoordinator
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Aria2DownloadEngine implements DownloadEngine {

    /**
     * 日志前缀，与下载任务其它组件保持一致。
     */
    private static final String LOG_PREFIX = DownloadTaskConstants.LOG_PREFIX;

    /**
     * aria2 允许用户透传的选项白名单（小写）。
     *
     * <p>刻意不包含 {@code dir}、{@code out}、{@code torrent-file}、{@code follow-torrent} 等
     * 涉及本地路径或下载行为的选项：这些由服务端强制控制。</p>
     */
    private static final Set<String> ALLOWED_ARIA2_OPTIONS = Set.of(
            "split",
            "max-connection-per-server",
            "min-split-size",
            "bt-tracker",
            "max-download-limit",
            "max-overall-download-limit",
            "continue",
            "user-agent",
            "referer"
    );

    /**
     * 轮询 aria2 状态时请求的字段列表。
     */
    private static final List<String> STATUS_KEYS = List.of(
            "status",
            "completedLength",
            "totalLength",
            "downloadSpeed",
            "errorCode",
            "errorMessage",
            "files",
            "bittorrent",
            // 做种判定：配置 seed-time>0 时，下载完成后 aria2 的任务仍是 active，但 seeder=true
            // （内容已完整、正在做种），必须据此提前判定「下载完成」，否则要等到做种结束才拿到 complete。
            "seeder",
            // 磁力链是「两阶段、两个 gid」：addUri 返回的 gid 只负责下载元数据，完成时通过 followedBy
            // 指向真正的内容下载 gid；following 用于反向排查，numSeeders/connections 用于诊断。
            "followedBy",
            "following",
            "numSeeders",
            "connections"
    );

    /**
     * aria2 状态：下载完成。
     */
    private static final String STATUS_COMPLETE = "complete";

    /**
     * aria2 状态：下载出错。
     */
    private static final String STATUS_ERROR = "error";

    /**
     * aria2 状态：任务已被移除。
     */
    private static final String STATUS_REMOVED = "removed";

    /**
     * 未配置轮询间隔时使用的默认值（毫秒）。
     */
    private static final long DEFAULT_POLL_INTERVAL_MILLIS = 1000L;

    /**
     * 未配置或非法（负数）无进度超时时使用的默认值（秒）；配置为 0 表示不限。
     */
    private static final long DEFAULT_STALL_TIMEOUT_SECONDS = 300L;

    /**
     * 未配置种子大小上限时使用的默认值（MB）。
     */
    private static final long DEFAULT_TORRENT_MAX_SIZE_MB = 2L;

    /**
     * 磁力链「元数据 gid → 内容 gid」的最大跟随层级。
     *
     * <p>正常只有一层（元数据 → 内容）；设置上限是为了防止 aria2 异常行为导致跟随链无限增长。</p>
     */
    private static final int MAX_FOLLOW_DEPTH = 3;

    /**
     * 单次任务最多跟踪的 gid 数量上限。
     *
     * <p>磁力链会从 1 个元数据 gid 跟随出若干内容 gid，超过该数量说明出现异常，按失败处理。</p>
     */
    private static final int MAX_TRACKED_GIDS = 8;

    /**
     * 轮询期间刷新检查点的最小间隔（毫秒）：约 60 秒。
     *
     * <p>设计约定轮询期间可不写检查点（避免 RPC 放大），但每 60 秒刷新一次 {@code updatedAt}，
     * 便于崩溃后判断下载现场的新鲜度。</p>
     */
    private static final long CHECKPOINT_REFRESH_INTERVAL_MILLIS = 60_000L;

    /**
     * aria2 用于标记「这只是一份元数据」的伪文件路径片段。
     *
     * <p>磁力链的元数据阶段会产出一个名为 {@code [METADATA]xxx} 的伪文件，采集产物时必须跳过。</p>
     */
    private static final String METADATA_MARKER = "[METADATA]";

    /**
     * 拉取 {@code .torrent} 文件时的连接超时（毫秒）。
     */
    private static final int TORRENT_CONNECT_TIMEOUT_MILLIS = 10000;

    /**
     * 拉取 {@code .torrent} 文件时的读取超时（毫秒）。
     */
    private static final int TORRENT_READ_TIMEOUT_MILLIS = 30000;

    /**
     * 拉取 {@code .torrent} 文件时，从响应流写入本地临时文件的缓冲区大小（字节）。
     */
    private static final int TORRENT_COPY_BUFFER_SIZE = 8192;

    /**
     * 拉取 {@code .torrent} 文件时，写入任务临时目录的临时文件前缀（后缀复用 {@link #TORRENT_SUFFIX}）。
     */
    private static final String TORRENT_TEMP_FILE_PREFIX = "torrent-";

    /**
     * 字节与兆字节的换算基数。
     */
    private static final long BYTES_PER_MB = 1024L * 1024L;

    /**
     * Base64 编码相对原始字节的膨胀比例分子（编码长度 × 3 / 4 ≈ 原始字节数）。
     */
    private static final long BASE64_DECODE_NUMERATOR = 3L;

    /**
     * Base64 编码相对原始字节的膨胀比例分母。
     */
    private static final long BASE64_DECODE_DENOMINATOR = 4L;

    /**
     * 面向用户的任务名称。
     */
    private static final String TASK_NAME = "P2P 下载";

    /**
     * 磁力链的协议前缀，用于类型推断。
     */
    private static final String MAGNET_PREFIX = "magnet:?";

    /**
     * 种子文件地址的路径后缀，用于类型推断。
     */
    private static final String TORRENT_SUFFIX = ".torrent";

    /**
     * 产物采集失败时，诊断摘要中最多展示的文件条目数。
     */
    private static final int MAX_DIAGNOSTIC_FILES = 5;

    /**
     * 产物采集失败时，诊断摘要中单个文件路径的最大展示长度（字符）。
     */
    private static final int MAX_DIAGNOSTIC_PATH_LENGTH = 160;

    /**
     * 跨容器共享目录模式下，等待 aria2 产物对本服务可见的最大重试次数。
     *
     * <p>aria2 报「完成」与本服务通过另一容器挂载看到文件之间可能短暂不一致（挂载缓存、写回延迟），
     * 因此对暂时不存在的产物做有界重试，避免误判为空产物。</p>
     */
    private static final int ARTIFACT_VISIBILITY_RETRIES = 10;

    /**
     * 跨容器共享目录模式下，等待 aria2 产物可见的重试间隔（毫秒）；10 次 × 200ms ≈ 2 秒。
     */
    private static final long ARTIFACT_VISIBILITY_INTERVAL_MILLIS = 200L;

    /**
     * aria2 JSON-RPC 客户端。
     */
    private final Aria2Client aria2Client;

    /**
     * aria2 部署期配置（仅 RPC 地址、令牌与共享目录；启动时绑定，修改后需重启）。
     */
    private final Aria2Properties aria2Properties;

    /**
     * core 统一配置（aria2 运行参数：P2P 开关、进度轮询间隔、无进度超时与种子体积上限）。
     *
     * <p>按配置缓存 TTL（默认 5 秒）自动刷新，修改后无需重启本服务。</p>
     */
    private final SfcDownloadConfigService configService;

    /**
     * aria2 共享工作区：负责任务目录创建与「本服务 ↔ aria2」双向路径映射。
     */
    private final Aria2Workspace aria2Workspace;

    /**
     * 本服务的内部调优配置（能力探测缓存时长）。
     */
    private final SfcDownloadInternalProperties internalProperties;

    /**
     * P2P 任务协调器（懒加载）。
     *
     * <p>{@link #execute(DownloadContext)} 只作为引擎接口的兼容入口委托给协调器；使用
     * {@link ObjectProvider} 注入以打破「协调器依赖引擎」的构造期循环依赖。</p>
     */
    private final ObjectProvider<P2pTaskCoordinator> coordinatorProvider;

    /**
     * 能力探测结果缓存（全局共享，线程安全）。
     */
    private final AtomicReference<Probe> probeCache = new AtomicReference<>();

    /**
     * 构建拉取 {@code .torrent} 文件用的 HTTP 客户端。
     *
     * <p>与 aria2 的 RPC 客户端分开：种子文件可能来自公网，需要更长的读取超时。客户端按任务快照的
     * 代理构建，HTTP / SOCKS 代理均支持；未配置代理时直连。下载来源不可信，统一忽略证书校验。</p>
     *
     * @param proxy 本次拉取使用的代理，{@code null} 表示直连
     * @return 配置了超时、代理与忽略证书校验的 {@link RestClient}
     */
    private static RestClient buildTorrentClient(Proxy proxy) {
        IgnoreSSLHttpRequestFactory requestFactory = new IgnoreSSLHttpRequestFactory();
        requestFactory.setConnectTimeout(TORRENT_CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(TORRENT_READ_TIMEOUT_MILLIS);
        if (proxy != null) {
            requestFactory.setProxy(proxy);
        }
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code "aria2"}
     */
    @Override
    public String getEngineId() {
        return "aria2";
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code MAGNET} 与 {@code TORRENT}
     */
    @Override
    public Set<DownloadType> supports() {
        return Set.of(DownloadType.MAGNET, DownloadType.TORRENT);
    }

    /**
     * {@inheritDoc}
     *
     * <p>磁力链按 {@code magnet:?} 前缀判断；种子按 {@code http(s)} 地址的路径（去掉查询串与片段）以
     * {@code .torrent} 结尾判断。仅做语法猜测，是否真的有可用 aria2 由能力声明与创建期校验判定。</p>
     */
    @Override
    public boolean matchesSource(DownloadType type, String source) {
        if (!SfcStringUtils.hasText(source)) {
            return false;
        }
        String value = source.trim();
        if (type == DownloadType.MAGNET) {
            return value.regionMatches(true, 0, MAGNET_PREFIX, 0, MAGNET_PREFIX.length());
        }
        if (type == DownloadType.TORRENT) {
            if (!DownloadFileUtils.startsWithHttpScheme(value)) {
                return false;
            }
            return DownloadFileUtils.stripQueryAndFragment(value)
                    .toLowerCase(Locale.ROOT)
                    .endsWith(TORRENT_SUFFIX);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code "P2P 下载"}
     */
    @Override
    public String taskName() {
        return TASK_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * <p>暴露 {@link DownloadOptionKeys#ARIA2_OPTIONS}：取值为 JSON 对象文本（键为 aria2 原始选项名、
     * 值为选项值），服务端只放行白名单内的键。</p>
     *
     * @return 选项元数据列表
     */
    @Override
    public List<ConfigNode> options() {
        ConfigNode aria2Options = ConfigNode.builder()
                .name(DownloadOptionKeys.ARIA2_OPTIONS)
                .title("aria2 选项")
                .inputType(ConfigInputType.TEXTAREA)
                .sort(10)
                .describe("JSON 对象文本，例如 {\"split\":\"16\"}；键为 aria2 原生选项名，值统一按字符串传入。"
                        + "服务端只放行白名单键：split、max-connection-per-server、min-split-size、bt-tracker、"
                        + "max-download-limit、max-overall-download-limit、continue、user-agent、referer；"
                        + "dir/out 等涉及本地路径的选项由服务端强制控制，用户传入会被丢弃")
                .build();
        return List.of(aria2Options);
    }

    /**
     * {@inheritDoc}
     *
     * <p>判定顺序：</p>
     * <ol>
     *   <li>core 统一配置中的 {@code download.aria2.enabled} 为 false → 提示到 core 后台插件配置开启；</li>
     *   <li>共享目录配置非法 → 返回「共享目录不可用 + 原因」；</li>
     *   <li>调用 {@code aria2.getVersion} 探活，失败 → 返回「RPC 不可达 + 地址 + 原因」。</li>
     * </ol>
     *
     * <p>开关读取统一配置快照（TTL 内缓存，core 不可用时沿用最后一次成功值或注解默认值）；探活结果按
     * {@code sfc.download.capability.cache-seconds} 缓存，避免每次创建任务与查询能力都打一次 RPC。</p>
     *
     * @return 不可用原因；aria2 已启用且共享目录与 RPC 均正常时返回 {@code null}
     */
    @Override
    public String unavailableReason() {
        if (!Boolean.TRUE.equals(configService.getAria2().getEnabled())) {
            return "aria2 未启用：请在 sfc-core 后台「插件配置 → 离线下载 → aria2 P2P 下载」中开启"
                    + "「启用 P2P 下载」，并配置 aria2 RPC 地址与令牌";
        }

        long now = System.currentTimeMillis();
        Probe cached = probeCache.get();
        if (cached != null && cached.expireAt() > now) {
            return cached.reason();
        }

        String reason = probe();
        long cacheSeconds = Math.max(1L, internalProperties.getCapability().getCacheSeconds());
        probeCache.set(new Probe(reason, System.currentTimeMillis() + cacheSeconds * 1000L));
        return reason;
    }

    /**
     * {@inheritDoc}
     *
     * <p>只做「提交前即可判定」的校验，不发起网络请求：</p>
     * <ul>
     *   <li>{@link DownloadOptionKeys#ARIA2_OPTIONS} 必须是合法的 JSON 对象文本；</li>
     *   <li>Base64 种子内容不得超过 {@code download.aria2.torrent-max-size}（MB，core 统一配置）。</li>
     * </ul>
     *
     * @param params 下载任务参数
     * @throws SfcJsonException 选项形状非法或种子体积超限时抛出（HTTP 400 语义）
     */
    @Override
    public void validate(DownloadTaskParams params) {
        if (params == null) {
            throw new SfcJsonException(400, "下载任务参数不能为空");
        }
        // 形状校验：非法结构在这里就拒绝，避免执行期才发现
        readAria2Options(params);

        if (!SfcStringUtils.hasText(params.torrentBase64)) {
            return;
        }
        long maxBytes = maxTorrentBytes(configService.getAria2());
        long actualBytes = estimateBase64Bytes(params.torrentBase64);
        if (actualBytes > maxBytes) {
            throw new SfcJsonException(400, "种子文件超过上限 " + (maxBytes / BYTES_PER_MB) + " MB");
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>兼容入口：</b>P2P 下载的完整编排（检查点、续传接管、做种登记与生命周期收口）由
     * {@link P2pTaskCoordinator} 负责，本方法仅作为 {@link DownloadEngine#execute(DownloadContext)}
     * 的兼容入口委托给协调器。任务处理器对 MAGNET / TORRENT 直接调用协调器，不经过本方法；本引擎
     * <b>不再自行清理</b> aria2 任务结果——清理必须由收口矩阵决定（挂起保留现场、做种保留文件）。</p>
     *
     * @param context 执行上下文
     * @return 下载产物列表
     */
    @Override
    public List<DownloadArtifact> execute(DownloadContext context) {
        return coordinatorProvider.getObject().start(context).artifacts();
    }

    // ==================== 会话式执行（供 P2P 协调器使用） ====================

    /**
     * 新建并提交 aria2 下载任务，返回本次任务的会话。
     *
     * <p>负责：装配 aria2 选项（强制 {@code dir} / {@code seed-time} / {@code continue=true}）→
     * 提交磁力链或种子拿到首个 gid → 注册中断回调（对会话内全部 gid 执行 {@code forceRemove}）。
     * 检查点由调用方（{@link P2pTaskCoordinator}）维护。</p>
     *
     * @param context 执行上下文
     * @return 已提交的 aria2 任务会话
     * @throws SfcJsonException 参数、种子获取或 RPC 失败时抛出
     */
    public Aria2TaskSession submitSession(DownloadContext context) {
        DownloadTaskParams params = context.getParams();
        Path tempDir = context.getTempDir();
        // 任务开始时取一次统一配置快照，保证本次任务的轮询/超时/种子体积上限使用同一份配置
        Aria2Property aria2Config = configService.getAria2();
        log.debug("{}aria2 运行参数: downloadId={}, enabled={}, pollInterval={}ms, stallTimeout={}, torrentMaxSize={}MB",
                LOG_PREFIX, context.getDownloadId(), Boolean.TRUE.equals(aria2Config.getEnabled()),
                aria2Config.getPollInterval(), describeStallTimeout(aria2Config.getStallTimeout()),
                aria2Config.getTorrentMaxSize());
        Map<String, String> options = buildAria2Options(context, tempDir, aria2Config);
        String aria2Dir = options.get("dir");
        // 跨容器部署时排障最关键的信息就是这层映射，执行开始即输出一次到任务日志
        context.log("aria2 下载目录映射: " + aria2Workspace.describeMapping());

        String gid = submit(context, params, options, aria2Config);
        Aria2TaskSession session = new Aria2TaskSession(gid);
        context.log("已提交 aria2 下载任务: gid=" + gid);
        log.info("{}aria2 任务已提交: downloadId={}, gid={}, type={}, aria2Dir={}, localDir={}",
                LOG_PREFIX, context.getDownloadId(), gid, params.type, aria2Dir, tempDir);
        // 中断时可能已经跟随出内容 gid，因此这里遍历「当下」的全部 gid（并发安全集合），
        // 而不是只移除最初提交的那个元数据 gid
        context.registerCanceller(() -> session.gids().forEach(aria2Client::forceRemove));
        return session;
    }

    /**
     * 依据检查点中已跟踪的 gid 重建会话（接管场景，不重新提交 aria2）。
     *
     * <p>重建后由 {@link P2pTaskCoordinator} 调用 {@link #awaitCompletion(DownloadContext,
     * Aria2TaskSession, java.util.function.Consumer)} 继续轮询；中断回调同样覆盖会话内全部 gid。</p>
     *
     * @param context      执行上下文
     * @param trackedGids  检查点记录的全部 gid，不能为空
     * @return 重建的会话
     * @throws SfcJsonException gid 列表为空时抛出
     */
    public Aria2TaskSession resumeSession(DownloadContext context, List<String> trackedGids) {
        Aria2TaskSession session = Aria2TaskSession.fromTracked(trackedGids);
        if (session == null) {
            throw new SfcJsonException(500, "接管 aria2 任务失败: 检查点中没有可用的 gid");
        }
        context.log("已接管既有 aria2 任务: gid=" + String.join(",", session.gids()));
        log.info("{}接管既有 aria2 任务: downloadId={}, gids={}",
                LOG_PREFIX, context.getDownloadId(), session.gids());
        context.registerCanceller(() -> session.gids().forEach(aria2Client::forceRemove));
        return session;
    }

    /**
     * 过滤出仍然存在于 aria2 中的 gid，用于判断「gid 是否丢失」。
     *
     * <p>aria2 已不认识的 gid（协议返回错误码）视为丢失并跳过；<b>传输层失败</b>
     * （{@link Aria2RpcException#getCode()} 为 {@link Aria2RpcException#UNKNOWN_CODE}，即 aria2 不可达）
     * 直接向上抛出——此时不能把 gid 当成丢失并重新提交，否则会在 aria2 实际仍在下载时重复提交。</p>
     *
     * @param trackedGids 检查点记录的全部 gid，允许为空
     * @return 仍然存在的 gid 列表（保持原顺序）
     * @throws Aria2RpcException aria2 不可达时抛出
     */
    public List<String> filterAliveGids(List<String> trackedGids) {
        if (trackedGids == null || trackedGids.isEmpty()) {
            return List.of();
        }
        List<String> alive = new ArrayList<>(trackedGids.size());
        for (String gid : trackedGids) {
            if (!SfcStringUtils.hasText(gid)) {
                continue;
            }
            String normalized = gid.trim();
            try {
                Map<String, Object> status = aria2Client.tellStatus(normalized, List.of("status"));
                String state = text(status.get("status"));
                if (STATUS_REMOVED.equals(state) || STATUS_ERROR.equals(state)) {
                    log.debug("{}检查点中的 aria2 gid 已结束（removed/error），按丢失处理: gid={}, status={}",
                            LOG_PREFIX, normalized, state);
                    continue;
                }
                alive.add(normalized);
            } catch (Aria2RpcException e) {
                if (e.getCode() == Aria2RpcException.UNKNOWN_CODE) {
                    // aria2 不可达：不能据此判定 gid 丢失，交给调用方按失败处理
                    throw e;
                }
                log.debug("{}检查点中的 aria2 gid 已不存在，按丢失处理: gid={}, cause={}",
                        LOG_PREFIX, normalized, describe(e));
            }
        }
        return alive;
    }

    /**
     * 轮询 aria2 直到任务结束（带检查点刷新回调）。
     *
     * @param context         执行上下文
     * @param session         本次任务会话
     * @param sessionListener 会话状态变化回调（gid 跟随、约 60 秒一次的心跳刷新），允许为 {@code null}
     * @return 内容 gid 的完成状态快照
     */
    public Map<String, Object> awaitCompletion(DownloadContext context, Aria2TaskSession session,
                                               Consumer<Aria2TaskSession> sessionListener) {
        return awaitCompletion(context, session, configService.getAria2(), sessionListener);
    }

    /**
     * 采集 aria2 完成状态中的本地产物（任务临时目录取自执行上下文）。
     *
     * @param context 执行上下文
     * @param status  完成状态快照
     * @return 产物列表（至少一个文件，否则抛异常）
     * @throws SfcJsonException 没有任何可用文件时抛出（HTTP 502 语义）
     */
    public List<DownloadArtifact> collectArtifacts(DownloadContext context, Map<String, Object> status) {
        List<DownloadArtifact> artifacts = collectArtifacts(context, context.getTempDir(), status);
        context.log("aria2 下载完成，文件数 " + artifacts.size());
        return artifacts;
    }

    /**
     * 丢弃 aria2 侧的任务结果（生命周期收口：取消 / 下载失败 / 成功但无做种保留）。
     *
     * <p>先 {@code forceRemove}（对仍在 active 的下载有效）再 {@code removeDownloadResult}
     * （对已终止的任务有效），两者都是 best-effort；顺序不能颠倒——否则 active 任务会继续往目录写文件，
     * 导致目录删不掉、且同一 infohash 无法立即重试。</p>
     *
     * @param gids 需要清理的 gid 集合，允许为 {@code null}
     */
    public void discardTaskResults(Collection<String> gids) {
        if (gids == null || gids.isEmpty()) {
            return;
        }
        for (String gid : gids) {
            if (!SfcStringUtils.hasText(gid)) {
                continue;
            }
            aria2Client.forceRemove(gid);
            aria2Client.removeDownloadResult(gid);
        }
    }

    // ==================== 提交阶段 ====================

    /**
     * 按任务形态提交给 aria2，并返回 GID。
     *
     * <p>磁力链走 {@code aria2.addUri}；种子优先使用任务参数中的 Base64 内容，
     * 没有时先按 URL 拉取 {@code .torrent} 再以 Base64 提交。具体形态<b>只取任务参数中显式指定的
     * {@code type}</b>，不做任何猜测或降级。</p>
     *
     * @param context     执行上下文
     * @param params      任务参数
     * @param options     已装配好的 aria2 选项
     * @param aria2Config 本次任务使用的 aria2 运行参数快照（提供种子体积上限）
     * @return aria2 分配的 GID
     * @throws SfcJsonException 类型不是 MAGNET / TORRENT 或拉取种子失败时抛出
     */
    private String submit(DownloadContext context, DownloadTaskParams params, Map<String, String> options,
                          Aria2Property aria2Config) {
        if (params.type == DownloadType.TORRENT) {
            String base64 = SfcStringUtils.hasText(params.torrentBase64)
                    ? params.torrentBase64.trim()
                    : fetchTorrentBase64(context, params.url, aria2Config);
            return aria2Client.addTorrent(base64, options);
        }
        if (params.type == DownloadType.MAGNET) {
            return aria2Client.addUri(List.of(params.url.trim()), options);
        }
        String typeCode = params.type == null ? "未指定" : params.type.getCode();
        throw new SfcJsonException(400, "aria2 引擎仅支持 MAGNET / TORRENT，当前下载类型: " + typeCode);
    }

    /**
     * 按 URL 拉取 {@code .torrent} 文件内容并编码为 Base64。
     *
     * <p>响应体流式写入任务临时目录下的临时文件，写入过程中强制
     * {@code download.aria2.torrent-max-size}（core 统一配置）上限；校验通过后读取文件内容并编码为
     * Base64，临时文件在方法返回前删除。代理取自任务参数的代理快照（HTTP / SOCKS 均支持），
     * 未配置代理时直连。</p>
     *
     * @param context     执行上下文
     * @param url         种子文件地址
     * @param aria2Config 本次任务使用的 aria2 运行参数快照（提供种子体积上限）
     * @return 种子内容的 Base64 编码
     * @throws SfcJsonException 地址为空、拉取失败、内容为空、超过体积上限或临时文件读写失败时抛出
     */
    private String fetchTorrentBase64(DownloadContext context, String url, Aria2Property aria2Config) {
        if (!SfcStringUtils.hasText(url)) {
            throw new SfcJsonException(400, "种子下载需要 .torrent 文件地址或种子文件内容（torrentBase64）");
        }
        DownloadTaskParams params = context.getParams();
        Proxy proxy = DownloadProxyUtils.toProxy(params);
        String proxyDescription = DownloadProxyUtils.describe(params, proxy);
        String target = url.trim();
        context.log("开始获取种子文件: " + target + "，代理: " + proxyDescription);
        context.checkInterrupted();

        long maxBytes = maxTorrentBytes(aria2Config);
        Path torrentFile;
        try {
            torrentFile = Files.createTempFile(context.getTempDir(), TORRENT_TEMP_FILE_PREFIX, TORRENT_SUFFIX);
        } catch (IOException e) {
            throw new SfcJsonException(500, "创建种子文件临时文件失败: " + describe(e));
        }
        try {
            fetchTorrentFile(context, buildTorrentClient(proxy), target, torrentFile, maxBytes);
            context.checkInterrupted();
            byte[] content = Files.readAllBytes(torrentFile);
            if (content.length == 0) {
                throw new SfcJsonException(502, "获取到的种子文件内容为空: " + target);
            }
            context.log("种子文件获取成功: " + content.length + " 字节");
            return Base64.getEncoder().encodeToString(content);
        } catch (IOException e) {
            throw new SfcJsonException(500, "读写种子文件临时文件失败: " + describe(e));
        } catch (RestClientException | IllegalArgumentException e) {
            throw new SfcJsonException(502, "获取种子文件失败（" + proxyDescription + "）: " + describe(e));
        } finally {
            DownloadFileUtils.deleteQuietly(torrentFile);
        }
    }

    /**
     * 流式拉取种子文件并写入本地临时文件，读取过程中强制体积上限。
     *
     * <p>状态码非 2xx 时按 HTTP 502 语义失败；超过上限时立即中止读取与写盘。响应对象由
     * {@link RestClient.RequestHeadersSpec#exchange} 在回调结束后统一关闭。</p>
     *
     * @param context    执行上下文（用于响应中断）
     * @param client     已按任务代理构建的 HTTP 客户端
     * @param target     种子文件地址
     * @param targetFile 写入的本地临时文件
     * @param maxBytes   允许的最大字节数
     * @throws SfcJsonException           状态码非 2xx 或内容超过上限时抛出
     * @throws SfcTaskInterruptedException 拉取过程中任务被中断时抛出
     */
    private static void fetchTorrentFile(DownloadContext context, RestClient client, String target,
                                         Path targetFile, long maxBytes) {
        client.get().uri(target).exchange((request, response) -> {
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new SfcJsonException(502, "获取种子文件失败: HTTP " + response.getStatusCode().value());
            }
            long total = 0L;
            byte[] buffer = new byte[TORRENT_COPY_BUFFER_SIZE];
            try (InputStream in = response.getBody(); OutputStream out = Files.newOutputStream(targetFile)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    context.checkInterrupted();
                    total += read;
                    if (total > maxBytes) {
                        throw new SfcJsonException(400, "种子文件超过上限 " + (maxBytes / BYTES_PER_MB) + " MB");
                    }
                    out.write(buffer, 0, read);
                }
            }
            return null;
        });
    }

    // ==================== 选项装配 ====================

    /**
     * 装配本次任务提交给 aria2 的选项。
     *
     * <p>服务端强制项（在用户选项之后覆盖，保证不被用户透传覆盖）：</p>
     * <ul>
     *   <li>{@code dir}：任务临时目录映射后的 aria2 侧目录；</li>
     *   <li>{@code seed-time}：做种保留时长（{@link Aria2Property#resolveSeedTimeMinutes()}，默认 10080 分钟；
     *       {@code 0} 表示不做种、完成即停止）；</li>
     *   <li>{@code continue=true}：保证同目录续传（gid 丢失时在同一目录重新提交可续传）；</li>
     *   <li>{@code bt-save-metadata=false}。</li>
     * </ul>
     * <p>用户选项仅白名单键生效；代理仅在 HTTP 类型代理下透传。</p>
     *
     * <p><b>刻意不设置 {@code follow-torrent}：</b>磁力链依赖 aria2 的「元数据 → 内容」自动跟进，
     * 把它设为 {@code false} 会导致任务在拿到元数据后就 complete 但零产物，详见方法内注释。</p>
     *
     * @param context     执行上下文
     * @param tempDir     任务临时目录
     * @param aria2Config 本次任务使用的 aria2 运行参数快照（提供做种保留时长）
     * @return aria2 选项键值对
     */
    private Map<String, String> buildAria2Options(DownloadContext context, Path tempDir, Aria2Property aria2Config) {
        Map<String, String> options = new LinkedHashMap<>();
        // 刻意不设置 follow-torrent：磁力链的流程是「先通过 DHT 拿到元数据（本质就是一个 torrent），
        // 再据其下载内容」，完全依赖 aria2 的「元数据 → 内容」自动跟进行为。实测把它设为 false 会让
        // aria2 拿到元数据后就认为任务结束（status=complete 但没有任何内容文件），最终产物采集为空、
        // 任务以「aria2 已完成但没有产生可用的文件」失败。.torrent 文件场景由本引擎显式调用 addTorrent，
        // 不依赖该选项，因此这里交给 aria2 的默认值即可。

        // 用户选项：仅白名单生效，先应用，随后由服务端强制项覆盖
        readAria2Options(context.getParams()).forEach((key, value) -> {
            String normalizedKey = key.toLowerCase(Locale.ROOT);
            if (ALLOWED_ARIA2_OPTIONS.contains(normalizedKey)) {
                options.put(normalizedKey, value);
            } else {
                log.debug("{}忽略不在白名单内的 aria2 选项: downloadId={}, key={}",
                        LOG_PREFIX, context.getDownloadId(), key);
            }
        });

        // 强制项：下载目录按共享目录映射换算成 aria2 侧路径；未启用共享目录时即本服务任务临时目录
        options.put("dir", aria2Workspace.toAria2Dir(tempDir));
        options.put("seed-time", String.valueOf(resolveSeedTimeMinutes(aria2Config)));
        // 同目录续传：接管/重提时必须强制为 true，否则 aria2 会重新从零下载
        options.put("continue", "true");
        options.put("bt-save-metadata", "false");

        applyProxy(context, options);
        return options;
    }

    /**
     * 解析本次任务生效的做种保留时长（分钟）。
     *
     * <p>统一使用 {@link Aria2Property#resolveSeedTimeMinutes()}（{@code null}/{@code <=0} → 0，上限 1 年），
     * 与做种生命周期计算 {@code seedEndAt} 的取值保持一致。</p>
     *
     * @param aria2Config aria2 运行参数快照，允许为 {@code null}
     * @return 生效的做种保留时长（分钟）
     */
    private static int resolveSeedTimeMinutes(Aria2Property aria2Config) {
        return aria2Config == null ? 0 : aria2Config.resolveSeedTimeMinutes();
    }

    /**
     * 把创建任务时快照的 HTTP 代理透传给 aria2。
     *
     * <p>aria2 的下载连接不支持 SOCKS 代理，遇到 SOCKS 时记录警告并忽略（不影响任务本身）。</p>
     *
     * @param context 执行上下文
     * @param options 待补充的 aria2 选项
     */
    private void applyProxy(DownloadContext context, Map<String, String> options) {
        DownloadTaskParams params = context.getParams();
        if (!SfcStringUtils.hasText(params.proxyAddress) || params.proxyPort == null) {
            return;
        }
        String proxyType = SfcStringUtils.hasText(params.proxyType)
                ? params.proxyType.trim().toUpperCase(Locale.ROOT)
                : "HTTP";
        if ("HTTP".equals(proxyType) || "HTTPS".equals(proxyType)) {
            String proxy = "http://" + params.proxyAddress.trim() + ":" + params.proxyPort;
            options.put("all-proxy", proxy);
            context.log("aria2 使用代理: " + params.proxyAddress.trim() + ":" + params.proxyPort);
            return;
        }
        log.warn("{}aria2 下载不支持 SOCKS 代理，本次忽略代理设置: downloadId={}, proxyType={}",
                LOG_PREFIX, context.getDownloadId(), proxyType);
        context.log("aria2 下载不支持 SOCKS 代理，本次忽略代理设置");
    }

    /**
     * 读取任务参数中的 aria2 透传选项并做形状校验。
     *
     * <p>{@code aria2Options} 的取值约定为 JSON 对象文本，例如 <code>{"split":"16"}</code>；
     * 空白文本表示不使用任何透传选项。</p>
     *
     * @param params 任务参数
     * @return 选项键值对（值已宽松转为字符串）；未提供时返回空 Map
     * @throws SfcJsonException {@code aria2Options} 不是字符串或不是合法的 JSON 对象文本时抛出（HTTP 400 语义）
     */
    private static Map<String, String> readAria2Options(DownloadTaskParams params) {
        if (params.options == null) {
            return Map.of();
        }
        Object raw = params.options.get(DownloadOptionKeys.ARIA2_OPTIONS);
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof String rawText)) {
            throw new SfcJsonException(400, "aria2Options 必须是 JSON 对象文本");
        }
        if (!SfcStringUtils.hasText(rawText)) {
            return Map.of();
        }
        Map<String, Object> parsed;
        try {
            parsed = SfcMapperHolder.parseJsonToMap(rawText);
        } catch (JsonProcessingException e) {
            throw new SfcJsonException(400, "aria2Options 必须是合法的 JSON 对象文本: " + e.getOriginalMessage());
        }
        if (parsed == null || parsed.isEmpty()) {
            return Map.of();
        }
        Map<String, String> options = new LinkedHashMap<>(parsed.size());
        parsed.forEach((key, value) -> {
            if (key != null && value != null) {
                options.put(key, String.valueOf(value));
            }
        });
        return options;
    }

    // ==================== 轮询与产物采集 ====================

    /**
     * 轮询 aria2 直到任务结束，期间持续上报进度并做停滞检测。
     *
     * <p><b>磁力链的两阶段跟随：</b>磁力链的任务被 aria2 拆成两个 gid——{@code addUri} 返回的 gid 只负责
     * 通过 DHT 下载元数据（通常几秒即 complete，产物是一个 {@code [METADATA]xxx} 伪文件），真正的内容下载
     * 由 aria2 分配给一个新的 gid，只能从元数据 gid 的 {@code followedBy} 字段取得。因此本方法维护一个
     * 「当前需要等待的 gid 集合」，元数据 gid 完成后丢弃它、改为跟踪 {@code followedBy} 指向的内容 gid，
     * 只有全部 gid 都终止才算任务结束。</p>
     *
     * <p><b>检查点回调：</b>{@code sessionListener} 在两类时机被调用——gid 集合变化（磁力链跟随出内容 gid）
     * 与约 {@value #CHECKPOINT_REFRESH_INTERVAL_MILLIS} 毫秒一次的轮询心跳；由协调器据此保存检查点。</p>
     *
     * <p><b>挂起：</b>每轮轮询开始前检查 {@link DownloadContext#isDetachRequested()}，已请求挂起时抛
     * {@link SfcTaskDetachedException}，由协调器保存检查点后向上传递（aria2 与本地目录保持不动）。</p>
     *
     * @param context         执行上下文
     * @param session         本次任务的会话（含已跟踪的全部 gid）
     * @param aria2Config     本次任务使用的 aria2 运行参数快照
     * @param sessionListener 会话状态变化回调，允许为 {@code null}
     * @return 内容 gid 的完成状态快照
     * @throws SfcTaskInterruptedException 任务被中断时抛出
     * @throws SfcTaskDetachedException    执行者请求挂起时抛出
     * @throws SfcJsonException           aria2 报错、跟随层级异常、停滞超时（配置为 0 即不限，不会触发）或
     *                                    RPC 失败时抛出
     */
    private Map<String, Object> awaitCompletion(DownloadContext context, Aria2TaskSession session,
                                                Aria2Property aria2Config,
                                                Consumer<Aria2TaskSession> sessionListener) {
        long pollIntervalMillis = positive(aria2Config.getPollInterval(), DEFAULT_POLL_INTERVAL_MILLIS);
        Long stallTimeoutMillis = resolveStallTimeoutMillis(aria2Config.getStallTimeout());
        long lastProgressAt = System.currentTimeMillis();
        long lastLoadedSum = -1L;
        long lastCheckpointRefreshAt = System.currentTimeMillis();
        int followDepth = 0;

        // 当前仍需等待的 gid；磁力链拿到元数据后会被 followedBy 指向的内容 gid 整体替换
        Set<String> targets = new LinkedHashSet<>(session.pollTargets());

        // 任务展示名只需上报一次：aria2 返回的文件清单是完整的（磁力链元数据阶段的伪文件会被过滤）
        boolean taskNameReported = false;

        while (true) {
            context.checkInterrupted();
            if (context.isDetachRequested()) {
                // 挂起：aria2 与本地目录保持不动，检查点由协调器在异常传播路径上保存
                throw new SfcTaskDetachedException("执行者停机，aria2 下载任务已挂起");
            }

            Set<String> nextTargets = new LinkedHashSet<>();
            List<String> followed = new ArrayList<>();
            List<String> fileNames = new ArrayList<>();
            Map<String, Object> finishedStatus = null;
            long loadedSum = 0L;
            long totalSum = 0L;

            for (String gid : targets) {
                Map<String, Object> status;
                try {
                    status = aria2Client.tellStatus(gid, STATUS_KEYS);
                } catch (Aria2RpcException e) {
                    if (context.isCancelled()) {
                        throw new SfcTaskInterruptedException("aria2 下载任务已被中断", e);
                    }
                    // 已被清理的 gid 视为已终止，避免死循环
                    log.debug("{}轮询 aria2 任务状态失败，按已终止处理: downloadId={}, gid={}, cause={}",
                            LOG_PREFIX, context.getDownloadId(), gid, describe(e));
                    continue;
                }
                fileNames.addAll(extractFileNames(status));
                applyInfohash(session, status);

                String state = text(status.get("status"));
                if (STATUS_ERROR.equals(state)) {
                    throw new SfcJsonException(502, "aria2 下载失败: " + describeStatus(status));
                }
                if (STATUS_REMOVED.equals(state)) {
                    if (context.isCancelled()) {
                        throw new SfcTaskInterruptedException("aria2 下载任务已被中断");
                    }
                    continue;
                }
                if (STATUS_COMPLETE.equals(state)) {
                    List<String> followedBy = readGidList(status.get("followedBy"));
                    if (!followedBy.isEmpty()) {
                        // 元数据阶段结束：丢弃当前 gid，改为跟踪它指向的内容 gid
                        followed.addAll(followedBy);
                        continue;
                    }
                    finishedStatus = status;
                    // 真正产出文件的 gid：种子任务即提交 gid；磁力链为跟随出的内容 gid
                    session.trackContentGid(gid);
                    continue;
                }
                if (isSeedingAfterDownloadCompleted(status)) {
                    // 内容已下载完成，aria2 正处于做种阶段：按完成处理，不阻塞上传与做种登记。
                    // 注意这里不能把 gid 放进 nextTargets，否则会一直等 aria2 转 complete（默认 seed-time=10080
                    // 分钟，等于任务永远卡在 DOWNLOADING）。
                    session.trackContentGid(gid);
                    loadedSum += number(status.get("completedLength"), 0L);
                    totalSum += number(status.get("totalLength"), 0L);
                    if (finishedStatus == null) {
                        finishedStatus = status;
                    }
                    continue;
                }

                // active / waiting / paused：仍在下载，累加进度
                nextTargets.add(gid);
                loadedSum += number(status.get("completedLength"), 0L);
                totalSum += number(status.get("totalLength"), 0L);
            }

            if (!taskNameReported) {
                // 文件清单一旦可用即可确定任务展示名：种子提交后的首轮状态就能拿到，
                // 磁力链则要等元数据阶段结束、转入内容 gid 后才有真实文件
                String taskName = DownloadTaskNameUtils.buildDisplayName(fileNames);
                if (SfcStringUtils.hasText(taskName)) {
                    context.updateTaskName(taskName);
                    taskNameReported = true;
                }
            }

            if (!followed.isEmpty()) {
                followDepth++;
                if (followDepth > MAX_FOLLOW_DEPTH || session.gids().size() + followed.size() > MAX_TRACKED_GIDS) {
                    throw new SfcJsonException(502, "aria2 元数据跟随层级异常（层级=" + followDepth
                            + "，已跟踪 gid=" + session.gids().size() + "，新增=" + followed.size() + "）");
                }
                followed.forEach(gid -> {
                    session.track(gid);
                    session.trackContentGid(gid);
                });
                targets = new LinkedHashSet<>(followed);
                // 阶段切换时进度会跳变（元数据只有几十 KB，内容有上百 MB）：
                // 重置基线，下一轮按内容 gid 的 totalLength 重新计算，避免出现「已超过 100%」
                context.getExecution().getProgress().setLoaded(0L);
                context.getExecution().getProgress().setTotal(-1L);
                lastLoadedSum = -1L;
                lastProgressAt = System.currentTimeMillis();
                context.log("磁力链元数据已就绪，转入内容下载: gid=" + String.join(",", followed));
                log.info("{}磁力链元数据已就绪，转入内容下载: downloadId={}, 新 gid={}",
                        LOG_PREFIX, context.getDownloadId(), followed);
                notifySessionChanged(sessionListener, session);
                lastCheckpointRefreshAt = System.currentTimeMillis();
                continue;
            }

            if (nextTargets.isEmpty()) {
                if (finishedStatus == null) {
                    throw new SfcJsonException(502, "aria2 任务结束但没有返回完成状态: gids=" + session.gids());
                }
                applyInfohash(session, finishedStatus);
                if (session.contentGids().isEmpty()) {
                    // 兜底：正常路径已在 complete / 做种完成分支登记内容 gid；这里保证下游（做种登记）一定能拿到
                    session.trackContentGid(session.primaryGid());
                }
                log.info("{}aria2 任务判定完成: downloadId={}, 完成状态={}, gids={}, 内容gid={}, 做种中={}",
                        LOG_PREFIX, context.getDownloadId(), text(finishedStatus.get("status")),
                        session.gids(), session.contentGids(), isSeedingAfterDownloadCompleted(finishedStatus));
                long total = number(finishedStatus.get("totalLength"), 0L);
                if (total > 0L) {
                    context.getExecution().getProgress().setLoaded(total);
                    context.getExecution().getProgress().setTotal(total);
                }
                notifySessionChanged(sessionListener, session);
                return finishedStatus;
            }

            targets = nextTargets;
            context.getExecution().getProgress().setLoaded(loadedSum);
            if (totalSum > 0L) {
                context.getExecution().getProgress().setTotal(totalSum);
            }
            if (loadedSum > lastLoadedSum) {
                lastLoadedSum = loadedSum;
                lastProgressAt = System.currentTimeMillis();
            }
            if (stallTimeoutMillis != null
                    && System.currentTimeMillis() - lastProgressAt > stallTimeoutMillis) {
                throw new SfcJsonException(504,
                        "aria2 下载超时: " + (stallTimeoutMillis / 1000L) + " 秒无进度（可能没有可用节点）");
            }
            long now = System.currentTimeMillis();
            if (now - lastCheckpointRefreshAt >= CHECKPOINT_REFRESH_INTERVAL_MILLIS) {
                notifySessionChanged(sessionListener, session);
                lastCheckpointRefreshAt = now;
            }
            sleep(context, pollIntervalMillis);
        }
    }

    /**
     * 判断一个 aria2 下载是否「内容已下载完成、正在做种」。
     *
     * <p>配置了 {@code seed-time>0}（默认 10080 分钟）时，aria2 下载完成后 gid <b>不会</b>变成
     * {@code complete}，而是保持 {@code active} 并置 {@code seeder=true}。此时产物已经完整，必须按
     * 「下载完成」处理；否则任务会一直等待 {@code complete}（直到做种结束），表现为任务卡在
     * {@code phase=DOWNLOADING}，最终撞无进度超时失败。</p>
     *
     * <p>判定同时要求 {@code completedLength >= totalLength}，避免把「已连接做种者但尚未下完」的
     * 中间状态误判为完成（{@code totalLength <= 0} 时无法确认，返回 {@code false}）。</p>
     *
     * @param status aria2 状态快照，允许为 {@code null}
     * @return 内容已完整且正在做种时返回 {@code true}
     */
    private static boolean isSeedingAfterDownloadCompleted(Map<String, Object> status) {
        if (status == null) {
            return false;
        }
        String state = text(status.get("status"));
        if (!"active".equals(state)) {
            return false;
        }
        if (!"true".equalsIgnoreCase(text(status.get("seeder")))) {
            return false;
        }
        long total = number(status.get("totalLength"), 0L);
        long completed = number(status.get("completedLength"), 0L);
        return total > 0L && completed >= total;
    }

    /**
     * 通知会话状态变化（回调为空时忽略）。
     *
     * @param sessionListener 会话状态变化回调，允许为 {@code null}
     * @param session         本次任务会话
     */
    private static void notifySessionChanged(Consumer<Aria2TaskSession> sessionListener,
                                             Aria2TaskSession session) {
        if (sessionListener == null) {
            return;
        }
        sessionListener.accept(session);
    }

    /**
     * 从 aria2 状态快照中提取 torrent infohash 并写入会话。
     *
     * @param session 本次任务会话
     * @param status  aria2 状态快照，允许为 {@code null}
     */
    private static void applyInfohash(Aria2TaskSession session, Map<String, Object> status) {
        if (status == null || !(status.get("bittorrent") instanceof Map<?, ?> bittorrent)) {
            return;
        }
        String infohash = text(bittorrent.get("infoHash"));
        if (SfcStringUtils.hasText(infohash)) {
            session.updateInfohash(infohash.trim().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * 从 aria2 状态字段中读取 gid 列表（{@code followedBy} / {@code following}）。
     *
     * @param value 原始字段值，通常是 {@code List<String>}
     * @return gid 列表；字段缺失或不是列表时返回空列表
     */
    private static List<String> readGidList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> gids = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            String gid = text(item);
            if (SfcStringUtils.hasText(gid)) {
                gids.add(gid.trim());
            }
        }
        return gids;
    }

    /**
     * 从 aria2 状态快照中提取本次任务的文件名列表。
     *
     * <p>只取 {@code files[].path} 的文件名部分（落盘前保存在任务临时目录的绝对路径），并跳过：
     * 空路径、磁力链元数据阶段的 {@code [METADATA]} 伪文件、以及明确标记为未选中的文件。
     * 返回顺序即 aria2（种子内）的文件顺序，供任务展示名按「最多前三个」拼接。</p>
     *
     * @param status aria2 的状态快照，允许为 {@code null}
     * @return 文件名列表；没有可用文件时返回空列表
     */
    private static List<String> extractFileNames(Map<String, Object> status) {
        if (status == null || !(status.get("files") instanceof List<?> files) || files.isEmpty()) {
            return List.of();
        }
        List<String> fileNames = new ArrayList<>(files.size());
        for (Object item : files) {
            if (!(item instanceof Map<?, ?> fileInfo)) {
                continue;
            }
            String path = text(fileInfo.get("path"));
            if (!SfcStringUtils.hasText(path) || path.contains(METADATA_MARKER)) {
                continue;
            }
            if (!isSelectedFile(fileInfo)) {
                continue;
            }
            fileNames.add(path.trim());
        }
        return fileNames;
    }

    /**
     * 判断 aria2 文件条目是否被选中下载。
     *
     * <p>aria2 未返回 {@code selected} 字段时按选中处理；只有明确为 {@code false} 才跳过，
     * 避免因不同 aria2 版本的字段差异漏掉真实文件。</p>
     *
     * @param fileInfo aria2 状态快照中的单个文件条目
     * @return 参与下载（应计入任务名）时返回 {@code true}
     */
    private static boolean isSelectedFile(Map<?, ?> fileInfo) {
        String selected = text(fileInfo.get("selected"));
        return !SfcStringUtils.hasText(selected) || !"false".equalsIgnoreCase(selected.trim());
    }

    /**
     * 从 aria2 的状态快照中采集本地产物。
     *
     * <p>aria2 的 {@code files[].path} 是 aria2 侧的绝对路径，且一定位于本次任务映射后的
     * {@code dir} 之下；这里先按共享目录映射换算为本服务可见路径，再相对任务临时目录计算产物路径
     * 交给落盘服务。未启用共享目录时映射为恒等变换，与改造前行为一致。</p>
     *
     * <p><b>跨容器可见性：</b>共享目录模式下，aria2 报完成与本服务看到文件之间可能有短暂延迟，
     * 因此对暂时不存在的产物做约 2 秒的有界重试；重试期间响应中断。</p>
     *
     * @param context 执行上下文
     * @param tempDir 任务临时目录
     * @param status  完成状态快照
     * @return 产物列表
     * @throws SfcJsonException 没有任何可用文件时抛出（HTTP 502 语义）
     */
    private List<DownloadArtifact> collectArtifacts(DownloadContext context, Path tempDir, Map<String, Object> status) {
        Object filesRaw = status.get("files");
        if (!(filesRaw instanceof List<?> files) || files.isEmpty()) {
            throw new SfcJsonException(502, "aria2 已完成但没有产生文件（" + describeArtifactStatus(status) + "）");
        }

        List<DownloadArtifact> artifacts = new ArrayList<>(files.size());
        for (Object item : files) {
            if (!(item instanceof Map<?, ?> fileInfo)) {
                continue;
            }
            String path = text(fileInfo.get("path"));
            if (!SfcStringUtils.hasText(path)) {
                continue;
            }
            if (path.contains(METADATA_MARKER)) {
                // aria2 用 [METADATA] 前缀表示「这只是一份元数据」，不是真正的内容文件
                log.debug("{}跳过 aria2 元数据伪文件: downloadId={}, path={}",
                        LOG_PREFIX, context.getDownloadId(), path);
                continue;
            }
            Path localPath = aria2Workspace.toLocalArtifactPath(path);
            if (localPath == null) {
                // 不在共享目录映射前缀内、或包含越界片段：跳过并记录 WARN，便于排查挂载/配置问题
                log.warn("{}跳过无法映射到本服务路径的 aria2 产物: downloadId={}, aria2Path={}, 映射={}",
                        LOG_PREFIX, context.getDownloadId(), path, aria2Workspace.describeMapping());
                continue;
            }
            if (aria2Workspace.isSharedEnabled() && !Files.exists(localPath)) {
                localPath = awaitArtifactVisible(context, localPath);
            }
            if (!Files.isRegularFile(localPath)) {
                log.debug("{}跳过不存在的 aria2 产物: downloadId={}, path={}",
                        LOG_PREFIX, context.getDownloadId(), localPath);
                continue;
            }
            try {
                artifacts.add(DownloadArtifact.of(tempDir, localPath));
            } catch (IllegalArgumentException e) {
                // 产物不在任务临时目录下属于异常情况（例如用户篡改了 dir），跳过并记录
                log.warn("{}跳过不在任务临时目录下的 aria2 产物: downloadId={}, path={}, cause={}",
                        LOG_PREFIX, context.getDownloadId(), localPath, e.getMessage());
            }
        }
        if (artifacts.isEmpty()) {
            throw new SfcJsonException(502, "aria2 已完成但没有产生可用的文件（" + describeArtifactStatus(status) + "）");
        }
        return artifacts;
    }

    /**
     * 在共享目录模式下等待产物文件可见（约 2 秒的有界重试）。
     *
     * <p>跨容器共享卷时，aria2 报「完成」与本服务通过另一挂载点看到文件之间可能存在短暂延迟
     * （挂载缓存、写回延迟等）；这里做 10 次 × 200ms 的重试，期间响任务中断。</p>
     *
     * @param context 执行上下文（用于响应中断）
     * @param path    期望可见的本地产物路径
     * @return 重试结束后的路径（可能仍不存在，由调用方按「文件不存在」跳过）
     * @throws SfcTaskInterruptedException 等待期间任务被中断时抛出
     */
    private static Path awaitArtifactVisible(DownloadContext context, Path path) {
        for (int attempt = 0; attempt < ARTIFACT_VISIBILITY_RETRIES; attempt++) {
            if (Files.exists(path)) {
                return path;
            }
            context.checkInterrupted();
            sleep(context, ARTIFACT_VISIBILITY_INTERVAL_MILLIS);
        }
        return path;
    }

    /**
     * 生成「任务已完成但采集不到产物」时的诊断摘要。
     *
     * <p>把 aria2 报告的种子名、文件数以及前若干个 {@code path}/{@code length} 拼进异常消息，
     * 便于一眼区分「aria2 确实没产出任何文件」与「产出了但路径没匹配上（例如落在任务临时目录之外）」；
     * 共享目录模式下再追加当前目录映射，方便排查跨容器挂载配置。</p>
     *
     * @param status aria2 的完成状态快照
     * @return 单行诊断摘要，永不为 {@code null}
     */
    private String describeArtifactStatus(Map<String, Object> status) {
        StringBuilder builder = new StringBuilder();
        builder.append("种子名=").append(bittorrentName(status));
        Object filesRaw = status.get("files");
        if (!(filesRaw instanceof List<?> files)) {
            builder.append("，aria2 未返回 files 字段");
            appendMappingIfShared(builder);
            return builder.toString();
        }
        builder.append("，aria2 报告文件数=").append(files.size());
        int limit = Math.min(files.size(), MAX_DIAGNOSTIC_FILES);
        for (int index = 0; index < limit; index++) {
            if (files.get(index) instanceof Map<?, ?> fileInfo) {
                builder.append("，[").append(index).append("] path=")
                        .append(abbreviate(text(fileInfo.get("path")), MAX_DIAGNOSTIC_PATH_LENGTH))
                        .append(" length=").append(text(fileInfo.get("length")));
            }
        }
        if (files.size() > limit) {
            builder.append("，……其余 ").append(files.size() - limit).append(" 个文件省略");
        }
        appendMappingIfShared(builder);
        return builder.toString();
    }

    /**
     * 共享目录模式下把当前路径映射追加到诊断摘要中。
     *
     * @param builder 诊断摘要构建器
     */
    private void appendMappingIfShared(StringBuilder builder) {
        if (aria2Workspace.isSharedEnabled()) {
            builder.append("，目录映射=").append(aria2Workspace.describeMapping());
        }
    }

    /**
     * 从状态快照中提取种子的信息名（{@code bittorrent.info.name}）。
     *
     * @param status aria2 的状态快照
     * @return 种子信息名；未返回时返回 {@code "(未返回)"}
     */
    private static String bittorrentName(Map<String, Object> status) {
        if (status.get("bittorrent") instanceof Map<?, ?> bittorrent
                && bittorrent.get("info") instanceof Map<?, ?> info) {
            String name = text(info.get("name"));
            if (SfcStringUtils.hasText(name)) {
                return name;
            }
        }
        return "(未返回)";
    }

    /**
     * 截断过长的诊断文本。
     *
     * @param value     原始文本，允许为 {@code null}
     * @param maxLength 最大展示长度（字符）
     * @return 截断后的文本；超长时追加省略号
     */
    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "…";
    }

    // ==================== 能力探测 ====================

    /**
     * 执行一次 aria2 探活：先校验共享目录配置，再调用 RPC {@code getVersion}。
     *
     * @return 不可用原因；可用时返回 {@code null}
     */
    private String probe() {
        String invalidWorkspace = aria2Workspace.validate();
        if (SfcStringUtils.hasText(invalidWorkspace)) {
            log.debug("{}aria2 共享目录配置无效: cause={}", LOG_PREFIX, invalidWorkspace);
            return "aria2 共享目录不可用：" + invalidWorkspace;
        }
        String rpcUrl = SfcStringUtils.hasText(aria2Properties.getRpcUrl())
                ? aria2Properties.getRpcUrl().trim()
                : Aria2Properties.DEFAULT_RPC_URL;
        try {
            String version = aria2Client.getVersion();
            log.debug("{}aria2 探活成功: rpcUrl={}, version={}, mapping={}",
                    LOG_PREFIX, rpcUrl, version, aria2Workspace.describeMapping());
            return null;
        } catch (RuntimeException e) {
            log.debug("{}aria2 探活失败: rpcUrl={}, mapping={}, cause={}",
                    LOG_PREFIX, rpcUrl, aria2Workspace.describeMapping(), e.toString());
            return "aria2 RPC 不可达（" + rpcUrl + "）：" + describe(e)
                    + "；目录映射=" + aria2Workspace.describeMapping();
        }
    }

    // ==================== 通用辅助 ====================

    /**
     * 计算种子文件允许的最大字节数。
     *
     * @param config aria2 运行参数（core 统一配置快照）
     * @return 最大字节数
     */
    private static long maxTorrentBytes(Aria2Property config) {
        long maxMb = config.getTorrentMaxSize() == null || config.getTorrentMaxSize() <= 0
                ? DEFAULT_TORRENT_MAX_SIZE_MB
                : config.getTorrentMaxSize();
        return maxMb * BYTES_PER_MB;
    }

    /**
     * 解析无进度超时（秒）为毫秒值。
     *
     * <p>配置为 {@code 0} 表示<b>不限</b>，返回 {@code null}（调用方跳过停滞检测）；{@code null} 或负数
     * 视为未配置 / 非法值，回落到 {@link #DEFAULT_STALL_TIMEOUT_SECONDS}；正数按秒换算为毫秒。</p>
     *
     * @param seconds 配置的无进度超时（秒），允许为 {@code null}
     * @return 超时毫秒数；{@code null} 表示不做无进度检测
     */
    private static Long resolveStallTimeoutMillis(Integer seconds) {
        if (seconds == null || seconds < 0) {
            return DEFAULT_STALL_TIMEOUT_SECONDS * 1000L;
        }
        return seconds == 0 ? null : seconds * 1000L;
    }

    /**
     * 把无进度超时配置转换为便于日志展示的文本。
     *
     * @param seconds 配置的无进度超时（秒），允许为 {@code null}
     * @return 「不限」、默认值说明或「N 秒」
     */
    private static String describeStallTimeout(Integer seconds) {
        if (seconds == null || seconds < 0) {
            return DEFAULT_STALL_TIMEOUT_SECONDS + "s（默认）";
        }
        return seconds == 0 ? "不限" : seconds + "s";
    }

    /**
     * 估算 Base64 内容对应的原始字节数。
     *
     * @param base64 Base64 文本
     * @return 估算的原始字节数
     */
    private static long estimateBase64Bytes(String base64) {
        return base64.trim().length() * BASE64_DECODE_NUMERATOR / BASE64_DECODE_DENOMINATOR;
    }

    /**
     * 把配置中的数值转为正数，非正数时回落到默认值。
     *
     * @param value        配置值，允许为 {@code null}
     * @param defaultValue 默认值
     * @return 正数结果
     */
    private static long positive(Integer value, long defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    /**
     * 可中断的休眠。
     *
     * @param context 执行上下文
     * @param millis  休眠毫秒数
     * @throws SfcTaskInterruptedException 休眠期间线程被中断且任务已取消时抛出
     */
    private static void sleep(DownloadContext context, long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            context.checkInterrupted();
            throw new SfcTaskInterruptedException("aria2 下载任务已被中断", e);
        }
    }

    /**
     * 把 aria2 状态字段宽松转为字符串。
     *
     * @param value 原始值
     * @return 字符串值；入参为 {@code null} 时返回 {@code null}
     */
    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 把 aria2 状态字段宽松转为长整数（aria2 的数字字段多为字符串）。
     *
     * @param value        原始值
     * @param defaultValue 无法转换时的默认值
     * @return 长整数值
     */
    private static long number(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && SfcStringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 生成 aria2 错误状态的可读描述。
     *
     * @param status 状态快照
     * @return 形如 {@code [errorCode] errorMessage} 的描述
     */
    private static String describeStatus(Map<String, Object> status) {
        String message = text(status.get("errorMessage"));
        String code = text(status.get("errorCode"));
        StringBuilder builder = new StringBuilder();
        if (SfcStringUtils.hasText(code)) {
            builder.append('[').append(code.trim()).append("] ");
        }
        builder.append(SfcStringUtils.hasText(message) ? message.trim() : "未知错误");
        return builder.toString();
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

    /**
     * 能力探测结果缓存条目。
     *
     * @param reason   不可用原因；{@code null} 表示可用
     * @param expireAt 过期时间（毫秒时间戳）
     */
    private record Probe(String reason, long expireAt) {
    }
}
