package com.sfc.download.task;

import com.sfc.cloud.exception.SfcRemoteApiException;
import com.sfc.cloud.model.SfcFileInfo;
import com.sfc.cloud.remote.client.SfcCoreFileClient;
import com.sfc.cloud.task.worker.SfcTaskExecution;
import com.sfc.cloud.task.worker.SfcTaskHandler;
import com.sfc.cloud.task.worker.SfcTaskInterruptedException;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.SfcMapperHolder;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.model.DownloadTaskParams;
import com.sfc.download.repo.DownloadTaskRepo;
import com.sfc.download.util.DownloadFileUtils;
import com.sfc.download.util.IgnoreSSLHttpRequestFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 离线下载任务处理器（任务类型 {@code offline-download}）。
 *
 * <p>由 sfc-task-worker SDK 在认领到 {@link DownloadTaskConstants#TASK_TYPE} 类型的任务后调用
 * {@link #execute(SfcTaskExecution)}，整体流程：</p>
 * <ol>
 *     <li>解析任务参数 {@link DownloadTaskParams}，并把本地任务状态镜像置为运行中；</li>
 *     <li>通过 {@link SfcCoreFileClient#list(long, String)} 校验保存目录是否存在；</li>
 *     <li>按参数快照组装请求头、代理与超时时间，使用信任所有证书的请求工厂发起下载请求；</li>
 *     <li>以流式方式把响应写入本地临时文件，逐块上报进度并响应中断（中断时关闭下载流）；</li>
 *     <li>在目标目录中做重名规避后把临时文件上传到 core 网盘，最后回写完成状态；</li>
 *     <li>异常时把本地状态镜像为 CANCEL / FAILED，并在 {@code finally} 中清理临时文件。</li>
 * </ol>
 *
 * <p><b>并发说明：</b>本处理器是 Spring 单例，会被 Worker 线程池并发调用，因此自身不持有任何
 * 任务级可变状态；一次执行的全部可变状态都保存在每次新建的 {@link DownloadContext} 中。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DownloadTaskHandler implements SfcTaskHandler {

    /**
     * 日志前缀：应用日志与任务运行日志共用，便于按前缀检索。
     */
    private static final String LOG_PREFIX = DownloadTaskConstants.LOG_PREFIX;

    /**
     * 默认 User-Agent。
     *
     * <p>部分站点会直接拒绝没有 User-Agent 的请求，因此默认携带一个常见的浏览器 UA；
     * 调用方通过 {@link DownloadTaskParams#headers} 传入同名请求头时可以覆盖它。</p>
     */
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Safari/537.36";

    /**
     * 流式下载的缓冲区大小（字节）。
     */
    private static final int BUFFER_SIZE = 8192;

    /**
     * 参数未指定或非法时使用的默认连接超时（毫秒）。
     */
    private static final int DEFAULT_CONNECT_TIMEOUT = 10000;

    /**
     * 参数未指定或非法时使用的默认读取超时（毫秒）。
     */
    private static final int DEFAULT_READ_TIMEOUT = 60000;

    /**
     * 代理端口合法范围下限。
     */
    private static final int MIN_PROXY_PORT = 1;

    /**
     * 代理端口合法范围上限。
     */
    private static final int MAX_PROXY_PORT = 65535;

    /**
     * 写入 {@code download_task.message} 的错误信息最大长度，避免超长堆栈撑爆数据库行。
     */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    /**
     * 下载任务仓储：回写本地任务状态镜像（状态、大小、文件名、结束时间、失败信息）。
     */
    private final DownloadTaskRepo downloadTaskRepo;

    /**
     * core 网盘文件客户端：校验保存目录、列出目录文件名、上传下载结果。
     */
    private final SfcCoreFileClient fileClient;

    /**
     * 本处理器支持的任务类型。
     *
     * @return {@link DownloadTaskConstants#TASK_TYPE}
     */
    @Override
    public String taskType() {
        return DownloadTaskConstants.TASK_TYPE;
    }

    /**
     * 执行一个离线下载任务。
     *
     * <p>异常映射约定：</p>
     * <ul>
     *     <li>中断（{@link SfcTaskInterruptedException}，或中断指令导致流读取失败）→ 本地状态置为
     *         {@link DownloadTaskConstants.Status#CANCEL} 后原样抛出，由 SDK 上报 CANCEL；</li>
     *     <li>其它异常 → 本地状态置为 {@link DownloadTaskConstants.Status#FAILED} 并记录错误信息后
     *         原样抛出，由 SDK 上报 FAILED；</li>
     *     <li>无论成功失败，{@code finally} 中都会删除本地临时文件。</li>
     * </ul>
     *
     * @param execution 任务执行上下文（任务参数、日志、进度与中断协作）
     * @throws SfcTaskInterruptedException 任务被中断时抛出（由 SDK 上报为 CANCEL）
     * @throws RuntimeException           其它执行失败（由 SDK 上报为 FAILED）
     */
    @Override
    public void execute(SfcTaskExecution execution) {
        DownloadTaskParams params = parseParams(execution);
        Long downloadId = params.downloadId;
        Long taskId = execution.getTaskId();
        long startNanos = System.nanoTime();
        DownloadContext context = new DownloadContext(execution, params);
        try {
            execution.log(LOG_PREFIX + "开始执行离线下载任务: " + params);
            log.info("{}开始执行离线下载任务: downloadId={}, taskId={}, uid={}, url={}, savePath={}, method={}, proxy={}",
                    LOG_PREFIX, downloadId, taskId, params.uid, params.url, params.savePath, params.method,
                    SfcStringUtils.hasText(params.proxy) ? params.proxy : "直连");
            downloadTaskRepo.updateStatus(downloadId, DownloadTaskConstants.Status.RUNNING);
            context.run();
            log.info("{}离线下载任务执行完成: downloadId={}, taskId={}, 文件={}, 大小={}字节, 总耗时={}ms",
                    LOG_PREFIX, downloadId, taskId, context.fileName, context.loadedBytes(), elapsedMillis(startNanos));
        } catch (SfcTaskInterruptedException e) {
            execution.log(LOG_PREFIX + "任务已被取消: " + describe(e));
            log.info("{}离线下载任务已取消: downloadId={}, taskId={}, 原因={}, 总耗时={}ms",
                    LOG_PREFIX, downloadId, taskId, describe(e), elapsedMillis(startNanos));
            updateCancelStatusQuietly(execution, downloadId);
            throw e;
        } catch (Exception e) {
            if (execution.isInterruptRequested()) {
                // 中断回调关闭下载流会让阻塞中的 read 抛 IOException，这里统一还原为「取消」语义
                String reason = describe(e);
                execution.log(LOG_PREFIX + "任务因中断而终止: " + reason);
                log.info("{}离线下载任务因中断而终止: downloadId={}, taskId={}, 原因={}, 总耗时={}ms",
                        LOG_PREFIX, downloadId, taskId, reason, elapsedMillis(startNanos));
                updateCancelStatusQuietly(execution, downloadId);
                throw new SfcTaskInterruptedException("离线下载任务被中断: " + reason, e);
            }
            String message = describe(e);
            execution.log(LOG_PREFIX + "任务执行失败: " + message);
            // 异常堆栈由 Worker SDK 统一以 ERROR 级别输出，这里只补一条带任务上下文的应用日志
            log.warn("{}离线下载任务执行失败: downloadId={}, taskId={}, 原因={}, 总耗时={}ms",
                    LOG_PREFIX, downloadId, taskId, message, elapsedMillis(startNanos));
            updateFailureQuietly(execution, downloadId, message);
            throw e;
        } finally {
            context.cleanup();
        }
    }

    // ==================== 参数解析与状态回写 ====================

    /**
     * 解析任务参数。
     *
     * <p>只做「必须能拿到任务主键」的最小校验：参数为空、JSON 非法、缺少 {@code downloadId}
     * 时直接抛 {@link SfcJsonException}（此时无法回写本地状态镜像）；其余参数校验放在
     * {@link DownloadContext#run()} 中执行，以便失败时能把错误写回任务记录。</p>
     *
     * @param execution 任务执行上下文
     * @return 解析后的任务参数
     * @throws SfcJsonException 参数为空、格式非法或缺少 {@code downloadId} 时抛出
     */
    private DownloadTaskParams parseParams(SfcTaskExecution execution) {
        String rawParams = execution.getParams();
        if (!SfcStringUtils.hasText(rawParams)) {
            throw new SfcJsonException(400, "离线下载任务参数为空");
        }
        DownloadTaskParams params;
        try {
            params = SfcMapperHolder.parseJson(rawParams, DownloadTaskParams.class);
        } catch (IOException e) {
            throw new SfcJsonException(400, "离线下载任务参数解析失败: " + e.getMessage());
        }
        if (params == null) {
            throw new SfcJsonException(400, "离线下载任务参数为空");
        }
        if (params.downloadId == null) {
            throw new SfcJsonException(400, "离线下载任务参数缺少 downloadId");
        }
        return params;
    }

    /**
     * 静默把本地任务状态回写为「已取消」：回写失败只写运行日志，不覆盖原始异常。
     *
     * @param execution  任务执行上下文
     * @param downloadId 下载任务 id
     */
    private void updateCancelStatusQuietly(SfcTaskExecution execution, Long downloadId) {
        try {
            downloadTaskRepo.updateStatus(downloadId, DownloadTaskConstants.Status.CANCEL);
        } catch (RuntimeException e) {
            execution.log(LOG_PREFIX + "回写本地任务状态失败: " + describe(e));
        }
    }

    /**
     * 静默回写本地任务失败信息：回写失败只写运行日志，不覆盖原始异常。
     *
     * @param execution  任务执行上下文
     * @param downloadId 下载任务 id
     * @param message    错误信息
     */
    private void updateFailureQuietly(SfcTaskExecution execution, Long downloadId, String message) {
        try {
            downloadTaskRepo.updateFailure(downloadId, DownloadTaskConstants.Status.FAILED,
                    truncateMessage(message), new Date());
        } catch (RuntimeException e) {
            execution.log(LOG_PREFIX + "回写本地任务失败信息失败: " + describe(e));
        }
    }

    /**
     * 把异常转换为可读的简短描述。
     *
     * @param e 异常
     * @return 异常描述，永不为 {@code null}
     */
    private static String describe(Throwable e) {
        if (e == null) {
            return "未知错误";
        }
        String message = e.getMessage();
        if (!SfcStringUtils.hasText(message) && e.getCause() != null) {
            message = e.getCause().getMessage();
        }
        return SfcStringUtils.hasText(message) ? message : e.getClass().getSimpleName();
    }

    /**
     * 截断错误信息到数据库可接受的长度，并避免把代理对（surrogate pair）从中间切开。
     *
     * @param message 原始错误信息，允许为 {@code null}
     * @return 截断后的错误信息
     */
    private static String truncateMessage(String message) {
        if (message == null || message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        int end = MAX_ERROR_MESSAGE_LENGTH;
        if (Character.isHighSurrogate(message.charAt(end - 1))) {
            end--;
        }
        return message.substring(0, end) + "...";
    }

    /**
     * 计算从指定纳秒时间戳到当前时刻的耗时。
     *
     * @param startNanos 起始时间（取自 {@link System#nanoTime()}）
     * @return 耗时毫秒数
     */
    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    /**
     * 单次下载任务的执行上下文。
     *
     * <p>保存本次执行的全部可变状态（代理、RestTemplate、文件名、临时文件、活动输入流），
     * 使单例处理器可以被 Worker 线程池并发复用。</p>
     */
    private final class DownloadContext {

        /**
         * 任务执行上下文
         */
        private final SfcTaskExecution execution;

        /**
         * 任务参数
         */
        private final DownloadTaskParams params;

        /**
         * 下载任务主键
         */
        private final Long downloadId;

        /**
         * 当前活动中的响应输入流；中断回调通过关闭它来打断阻塞中的 {@code read}
         */
        private final AtomicReference<InputStream> activeInputStream = new AtomicReference<>();

        /**
         * 本次下载使用的请求方法
         */
        private HttpMethod method;

        /**
         * 本次下载使用的代理，{@code null} 表示直连
         */
        private Proxy proxy;

        /**
         * 本次下载使用的 RestTemplate（已配置超时、代理与忽略 SSL 校验）
         */
        private RestTemplate restTemplate;

        /**
         * 下载文件的候选文件名（URL 解析结果，响应头提供文件名时会被覆盖）
         */
        private String fileName;

        /**
         * 本地临时文件路径（下载过程中创建，任务结束时删除）
         */
        private Path tempFile;

        /**
         * 构造单次任务执行上下文。
         *
         * @param execution 任务执行上下文
         * @param params    任务参数
         */
        private DownloadContext(SfcTaskExecution execution, DownloadTaskParams params) {
            this.execution = execution;
            this.params = params;
            this.downloadId = params.downloadId;
        }

        /**
         * 获取本次任务已接收的字节数。
         *
         * @return 已下载字节数
         */
        private long loadedBytes() {
            return execution.getProgress().getLoaded();
        }

        /**
         * 执行下载主流程：参数校验 → 保存目录校验 → 请求装配 → 流式下载 → 上传落盘。
         */
        private void run() {
            execution.checkInterrupted();
            validateParams();
            validateSavePath();
            initProxy();
            initRestTemplate();
            // 先注册中断回调再发起请求：中断指令到达时关闭下载流，打断阻塞中的读取
            execution.onInterrupt(this::handleInterrupt);
            // 中断可能发生在回调注册之前（onInterrupt 会立即执行一次回调），这里再检查一次，
            // 避免已经取消的任务仍然发起下载请求
            execution.checkInterrupted();
            download();
            // 下载完成后、上传前再检查一次，避免把已被取消的文件写入网盘
            execution.checkInterrupted();
            save();
        }

        /**
         * 校验任务参数中与下载直接相关的字段。
         */
        private void validateParams() {
            if (!SfcStringUtils.hasText(params.url) || !DownloadFileUtils.isValidUrl(params.url)) {
                throw new SfcJsonException(400, "无效的下载URL: " + params.url);
            }
            if (!SfcStringUtils.hasText(params.savePath)) {
                throw new SfcJsonException(400, "无效的保存路径: " + params.savePath);
            }
            try {
                this.method = DownloadFileUtils.parseHttpMethod(params.method);
            } catch (IllegalArgumentException e) {
                throw new SfcJsonException(400, e.getMessage());
            }
        }

        /**
         * 通过 core 的内部文件接口校验保存目录是否存在。
         *
         * @throws SfcJsonException 保存目录不存在或无权访问时抛出
         */
        private void validateSavePath() {
            log("校验保存目录: uid=" + params.uid + ", path=" + params.savePath);
            try {
                fileClient.list(params.uid, params.savePath).requireSuccess();
            } catch (SfcRemoteApiException e) {
                throw new SfcJsonException(400, "无效的保存路径:" + params.savePath);
            }
            log("保存目录校验通过");
            log.info("{}保存目录校验通过: downloadId={}, uid={}, path={}",
                    LOG_PREFIX, downloadId, params.uid, params.savePath);
        }

        /**
         * 根据创建任务时快照的代理信息组装 {@link Proxy}。
         *
         * <p>未指定代理名称/地址时按直连处理；指定了代理但快照字段缺失或端口非法时按参数错误处理。</p>
         *
         * @throws SfcJsonException 代理配置不完整或类型非法时抛出
         */
        private void initProxy() {
            if (!SfcStringUtils.hasText(params.proxyAddress)) {
                if (SfcStringUtils.hasText(params.proxy)) {
                    throw new SfcJsonException(400, "无效的代理配置: 缺少代理地址快照（" + params.proxy + "）");
                }
                log.info("{}未配置代理，下载请求使用直连: downloadId={}", LOG_PREFIX, downloadId);
                return;
            }
            if (params.proxyPort == null || params.proxyPort < MIN_PROXY_PORT || params.proxyPort > MAX_PROXY_PORT) {
                throw new SfcJsonException(400, "无效的代理端口: " + params.proxyPort);
            }
            Proxy.Type proxyType = parseProxyType(params.proxyType);
            this.proxy = new Proxy(proxyType, new InetSocketAddress(params.proxyAddress, params.proxyPort));
            log("使用代理: " + (SfcStringUtils.hasText(params.proxy) ? params.proxy + " - " : "")
                    + proxyType + "://" + params.proxyAddress + ":" + params.proxyPort);
            log.info("{}使用代理: downloadId={}, 名称={}, 类型={}, 地址={}:{}",
                    LOG_PREFIX, downloadId,
                    SfcStringUtils.hasText(params.proxy) ? params.proxy : "-",
                    proxyType, params.proxyAddress, params.proxyPort);
        }

        /**
         * 解析代理协议类型。
         *
         * @param proxyType 代理类型字符串（来自 core 代理快照，取值 {@code HTTP}/{@code SOCKS}）
         * @return {@link Proxy#type()} 对应的类型
         * @throws SfcJsonException 类型无法识别时抛出
         */
        private Proxy.Type parseProxyType(String proxyType) {
            if (!SfcStringUtils.hasText(proxyType)) {
                return Proxy.Type.HTTP;
            }
            return switch (proxyType.trim().toUpperCase(Locale.ROOT)) {
                case "SOCKS", "SOCKS5" -> Proxy.Type.SOCKS;
                case "HTTP", "HTTPS" -> Proxy.Type.HTTP;
                default -> throw new SfcJsonException(400, "无效的代理类型: " + proxyType);
            };
        }

        /**
         * 创建配置好超时、代理与忽略 SSL 校验的 {@link RestTemplate}。
         */
        private void initRestTemplate() {
            IgnoreSSLHttpRequestFactory factory = new IgnoreSSLHttpRequestFactory();
            factory.setConnectTimeout(params.connectTimeout > 0 ? params.connectTimeout : DEFAULT_CONNECT_TIMEOUT);
            factory.setReadTimeout(params.readTimeout > 0 ? params.readTimeout : DEFAULT_READ_TIMEOUT);
            if (proxy != null) {
                factory.setProxy(proxy);
            }
            this.restTemplate = new RestTemplate(factory);
        }

        /**
         * 发起下载请求，并在响应到达后把响应体流式写入本地临时文件。
         */
        private void download() {
            HttpHeaders headers = buildHeaders();
            this.fileName = DownloadFileUtils.parseFileNameFromUrl(params.url);
            log("开始下载: method=" + method + ", 初始文件名=" + fileName);
            log.info("{}发起下载请求: downloadId={}, method={}, url={}, connectTimeout={}ms, readTimeout={}ms",
                    LOG_PREFIX, downloadId, method, params.url,
                    params.connectTimeout > 0 ? params.connectTimeout : DEFAULT_CONNECT_TIMEOUT,
                    params.readTimeout > 0 ? params.readTimeout : DEFAULT_READ_TIMEOUT);
            restTemplate.execute(params.url, method, restTemplate.httpEntityCallback(new HttpEntity<>(headers)),
                    response -> {
                        receive(response);
                        return null;
                    });
        }

        /**
         * 组装下载请求头：默认 User-Agent + 任务参数中的自定义请求头（同名时以自定义值为准）。
         *
         * @return 请求头
         */
        private HttpHeaders buildHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT);
            if (params.headers != null && !params.headers.isEmpty()) {
                params.headers.forEach((name, value) -> {
                    if (SfcStringUtils.hasText(name) && value != null) {
                        headers.set(name, value);
                    }
                });
            }
            return headers;
        }

        /**
         * 处理下载响应：探测文件名与大小、创建临时文件、流式接收响应体。
         *
         * @param response 下载响应
         * @throws IOException 读取响应或写入临时文件失败时抛出
         */
        private void receive(ClientHttpResponse response) throws IOException {
            applyResponseFileName(response.getHeaders());
            long totalSize = response.getHeaders().getContentLength();
            execution.getProgress().setTotal(totalSize);
            execution.getProgress().setLoaded(0L);
            downloadTaskRepo.updateSizeAndName(downloadId, totalSize, fileName);
            log("响应已到达: 文件名=" + fileName
                    + ", 大小=" + (totalSize >= 0 ? totalSize + " 字节" : "未知"));
            log.info("{}下载响应到达: downloadId={}, 状态码={}, 文件名={}, 声明大小={}",
                    LOG_PREFIX, downloadId, response.getStatusCode().value(), fileName,
                    totalSize >= 0 ? totalSize + "字节" : "未知");

            try {
                this.tempFile = DownloadFileUtils.createTaskTempFile(String.valueOf(downloadId), fileName);
            } catch (IOException e) {
                throw new IOException("创建本地临时文件失败: " + e.getMessage(), e);
            }
            log("创建本地临时文件: " + tempFile.toAbsolutePath());
            log.debug("{}已创建本地临时文件: downloadId={}, path={}",
                    LOG_PREFIX, downloadId, tempFile.toAbsolutePath());

            long receiveStartNanos = System.nanoTime();
            try (InputStream body = response.getBody(); OutputStream out = Files.newOutputStream(tempFile)) {
                activeInputStream.set(body);
                copyStream(body, out);
            } finally {
                activeInputStream.set(null);
            }

            long loaded = execution.getProgress().getLoaded();
            if (totalSize >= 0 && loaded != totalSize) {
                log("[警告] 已下载大小与响应声明大小不一致: 声明=" + totalSize + ", 实际=" + loaded
                        + "，文件可能不完整");
            }
            log("文件接收完成: " + loaded + " 字节");
            log.info("{}文件接收完成: downloadId={}, 字节数={}, 耗时={}ms",
                    LOG_PREFIX, downloadId, loaded, elapsedMillis(receiveStartNanos));
        }

        /**
         * 从响应头中探测文件名，探测成功时覆盖 URL 解析出的候选文件名。
         *
         * @param headers 响应头
         */
        private void applyResponseFileName(HttpHeaders headers) {
            String responseFileName = extractResponseFileName(headers);
            if (responseFileName == null) {
                log("响应头未提供文件名，使用 URL 解析结果: " + fileName);
                return;
            }
            this.fileName = DownloadFileUtils.sanitizeFileName(responseFileName);
            log("从响应头探测到文件名: " + fileName);
        }

        /**
         * 从 {@code Content-Disposition} 响应头中解析文件名（null 安全，解析失败按无文件名处理）。
         *
         * @param headers 响应头
         * @return 文件名，未提供或解析失败时返回 {@code null}
         */
        private String extractResponseFileName(HttpHeaders headers) {
            try {
                ContentDisposition disposition = headers.getContentDisposition();
                String name = disposition.getFilename();
                return SfcStringUtils.hasText(name) ? name : null;
            } catch (RuntimeException e) {
                log("解析响应头 Content-Disposition 失败，忽略服务端文件名: " + describe(e));
                return null;
            }
        }

        /**
         * 把响应体逐块复制到临时文件，同时累加进度并检查中断。
         *
         * <p>中断指令到达时中断回调会关闭输入流，使阻塞中的 {@code read} 抛出 {@link IOException}；
         * 每块写入后再调用 {@link SfcTaskExecution#checkInterrupted()} 保证能及时以中断语义退出。</p>
         *
         * @param in  响应输入流
         * @param out 临时文件输出流
         * @throws IOException 读取或写入失败时抛出
         */
        private void copyStream(InputStream in, OutputStream out) throws IOException {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
                execution.getProgress().addLoaded(len);
                execution.checkInterrupted();
            }
        }

        /**
         * 把下载完成的临时文件上传到 core 网盘，并回写完成状态。
         *
         * <p>上传前重新列出目标目录，存在同名文件时按 {@code .1}、{@code .2}… 规则生成新文件名。</p>
         */
        private void save() {
            long loaded = execution.getProgress().getLoaded();
            log("开始保存文件到网盘: uid=" + params.uid + ", path=" + params.savePath);
            log.info("{}开始保存文件到网盘: downloadId={}, uid={}, path={}, 文件名={}, 大小={}字节",
                    LOG_PREFIX, downloadId, params.uid, params.savePath, fileName, loaded);
            String finalName = DownloadFileUtils.generateNonConflictName(fileName, listExistingNames());
            if (!finalName.equals(fileName)) {
                log("目标目录存在同名文件，保存文件名调整为: " + finalName);
                log.debug("{}目标目录存在同名文件，保存文件名调整: downloadId={}, 原文件名={}, 调整后={}",
                        LOG_PREFIX, downloadId, fileName, finalName);
            }
            long uploadStartNanos = System.nanoTime();
            SfcJsonResult<Long> uploadResult = fileClient.upload(
                    new FileSystemResource(tempFile), params.uid, params.savePath, finalName, null);
            uploadResult.requireSuccess();
            downloadTaskRepo.updateFinish(downloadId, DownloadTaskConstants.Status.FINISH,
                    loaded, loaded, finalName, new Date());
            this.fileName = finalName;
            log("文件保存成功: " + finalName + "（" + loaded + " 字节）");
            log.info("{}文件保存成功: downloadId={}, 文件名={}, 大小={}字节, 上传耗时={}ms",
                    LOG_PREFIX, downloadId, finalName, loaded, elapsedMillis(uploadStartNanos));
        }

        /**
         * 列出目标目录下已有的文件名，用于重名规避。
         *
         * @return 已有文件名列表，目录为空时返回空列表
         */
        private List<String> listExistingNames() {
            SfcJsonResult<List<SfcFileInfo>> listResult =
                    fileClient.list(params.uid, params.savePath).requireSuccess();
            List<SfcFileInfo> fileInfos = listResult.getData();
            if (fileInfos == null || fileInfos.isEmpty()) {
                return List.of();
            }
            return fileInfos.stream()
                    .map(SfcFileInfo::getName)
                    .filter(Objects::nonNull)
                    .toList();
        }

        /**
         * 处理中断指令：关闭当前活动的下载流，让阻塞中的读取尽快失败退出。
         */
        private void handleInterrupt() {
            log("收到下载中断指令，正在关闭下载流");
            log.info("{}收到下载中断指令，正在关闭下载流: downloadId={}", LOG_PREFIX, downloadId);
            closeActiveStream();
        }

        /**
         * 关闭当前活动的下载流（若有），关闭异常忽略。
         */
        private void closeActiveStream() {
            InputStream in = activeInputStream.getAndSet(null);
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    log("关闭下载流失败: " + describe(e));
                }
            }
        }

        /**
         * 任务收尾清理：关闭可能残留的下载流，删除本地临时文件及其任务临时目录。
         */
        private void cleanup() {
            closeActiveStream();
            if (tempFile == null) {
                return;
            }
            Path taskDir = tempFile.getParent();
            log("清理本地临时文件: " + tempFile.toAbsolutePath());
            log.info("{}清理本地临时文件: downloadId={}, path={}",
                    LOG_PREFIX, downloadId, tempFile.toAbsolutePath());
            DownloadFileUtils.deleteQuietly(tempFile);
            DownloadFileUtils.deleteQuietly(taskDir);
            tempFile = null;
        }

        /**
         * 输出带统一前缀的任务运行日志（经 {@link SfcTaskExecution#log(String)} 上报到 core，前端任务详情可见）。
         *
         * <p>注意：本方法输出的是「任务运行日志」，与 SLF4J 应用日志（本类各处的 {@code log.info} /
         * {@code log.debug}）是两套独立输出，前者面向任务使用者，后者面向服务运维排障。</p>
         *
         * @param message 日志内容
         */
        private void log(String message) {
            execution.log(LOG_PREFIX + message);
        }
    }
}
