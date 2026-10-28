package com.sfc.download.engine.impl;

import com.sfc.cloud.task.worker.SfcTaskInterruptedException;
import com.sfc.cloud.task.worker.SfcTaskProgress;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.engine.DownloadArtifact;
import com.sfc.download.engine.DownloadContext;
import com.sfc.download.engine.DownloadEngine;
import com.sfc.download.engine.DownloadType;
import com.sfc.download.model.DownloadTaskParams;
import com.sfc.download.util.DownloadFileUtils;
import com.sfc.download.util.IgnoreSSLHttpRequestFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP/HTTPS 下载引擎（下载类型 {@link DownloadType#HTTP}）。
 *
 * <p>把远端文件以流式方式写入任务临时目录，产出一个单文件产物；代理、请求头、超时、忽略证书校验
 * 等能力与原任务处理器保持一致。</p>
 *
 * <p><b>并发说明：</b>本类是 Spring 单例，会被 Worker 线程池并发调用，因此自身不持有任何任务级可变状态：
 * 一次执行的全部可变状态（请求方法、代理、RestTemplate、文件名、临时文件、活动输入流）都保存在
 * 每次新建的 {@link Session} 实例中。</p>
 *
 * <p><b>职责边界：</b>引擎只负责「把文件下载到本地临时目录」，不做保存路径校验（由任务处理器完成），
 * 也不回写数据库、不上传网盘（由落盘服务完成）。</p>
 */
@Slf4j
@Component
public class HttpDownloadEngine implements DownloadEngine {

    /**
     * 应用日志前缀：与任务运行日志共用同一前缀，便于按前缀检索。
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
     * {@inheritDoc}
     *
     * @return {@code "http"}
     */
    @Override
    public String getEngineId() {
        return "http";
    }

    /**
     * 本引擎支持的下载类型。
     *
     * @return 仅包含 {@link DownloadType#HTTP} 的不可变集合
     */
    @Override
    public Set<DownloadType> supports() {
        return Set.of(DownloadType.HTTP);
    }

    /**
     * {@inheritDoc}
     *
     * <p>只按「以 {@code http://} 或 {@code https://} 开头」判断；以 {@code .torrent} / {@code .git}
     * 结尾的 HTTP 地址由其它引擎在类型推断阶段优先认领（见
     * {@link com.sfc.download.engine.DownloadEngineRegistry#detect(String)} 的判定顺序）。</p>
     */
    @Override
    public boolean matchesSource(DownloadType type, String source) {
        return type == DownloadType.HTTP && DownloadFileUtils.startsWithHttpScheme(source);
    }

    /**
     * {@inheritDoc}
     *
     * <p>只校验与 HTTP 方式自身相关的参数（请求方法、保存路径）；下载地址的协议合法性由
     * {@link com.sfc.download.engine.DownloadEngineRegistry} 统一校验，此处不重复。</p>
     */
    @Override
    public void validate(DownloadTaskParams params) {
        if (params == null) {
            throw new SfcJsonException(400, "下载参数不能为空");
        }
        if (!SfcStringUtils.hasText(params.savePath)) {
            throw new SfcJsonException(400, "无效的保存路径: " + params.savePath);
        }
        try {
            DownloadFileUtils.parseHttpMethod(params.method);
        } catch (IllegalArgumentException e) {
            throw new SfcJsonException(400, e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>每个任务创建一个独立的 {@link Session} 承载本次执行的可变状态，下载完成后返回单文件产物。</p>
     */
    @Override
    public List<DownloadArtifact> execute(DownloadContext context) {
        return new Session(context).run();
    }

    /**
     * 单次 HTTP 下载的执行会话。
     *
     * <p>保存本次执行的全部可变状态，使单例引擎可以被 Worker 线程池并发复用。</p>
     */
    private static final class Session {

        /**
         * 下载执行上下文（进度、日志、中断与临时目录）。
         */
        private final DownloadContext context;

        /**
         * 本次任务的下载参数。
         */
        private final DownloadTaskParams params;

        /**
         * 下载任务主键，仅用于应用日志定位。
         */
        private final Long downloadId;

        /**
         * 当前活动中的响应输入流；中断回调通过关闭它来打断阻塞中的 {@code read}。
         */
        private final AtomicReference<InputStream> activeInputStream = new AtomicReference<>();

        /**
         * 本次下载使用的请求方法。
         */
        private HttpMethod method;

        /**
         * 本次下载使用的代理，{@code null} 表示直连。
         */
        private Proxy proxy;

        /**
         * 本次下载使用的 RestTemplate（已配置超时、代理与忽略 SSL 校验）。
         */
        private RestTemplate restTemplate;

        /**
         * 下载文件的候选文件名（URL 解析结果，响应头提供文件名时会被覆盖）。
         */
        private String fileName;

        /**
         * 本地临时文件路径（下载过程中创建，任务结束时由处理器清理）。
         */
        private Path tempFile;

        /**
         * 构造单次执行会话。
         *
         * @param context 下载执行上下文
         */
        private Session(DownloadContext context) {
            this.context = context;
            this.params = context.getParams();
            this.downloadId = context.getDownloadId();
        }

        /**
         * 执行下载主流程：参数校验 → 代理装配 → 请求装配 → 注册中断回调 → 流式下载 → 产出产物。
         *
         * @return 单文件产物列表
         */
        private List<DownloadArtifact> run() {
            validateParams();
            initProxy();
            initRestTemplate();
            // 先注册中断回调再发起请求：中断指令到达时关闭下载流，打断阻塞中的读取
            context.registerCanceller(this::handleInterrupt);
            // 中断可能发生在回调注册之前（注册时会立即执行一次回调），这里再检查一次，
            // 避免已经取消的任务仍然发起下载请求
            context.checkInterrupted();
            download();
            // 下载完成后、交给落盘之前再检查一次，避免把已被取消的文件写入网盘
            context.checkInterrupted();
            if (tempFile == null) {
                throw new SfcJsonException(500, "下载未产生任何文件: " + params.url);
            }
            return List.of(DownloadArtifact.of(context.getTempDir(), tempFile));
        }

        /**
         * 校验本次执行必需的参数。
         *
         * @throws SfcJsonException 地址为空、保存路径为空或请求方法非法时抛出
         */
        private void validateParams() {
            if (!SfcStringUtils.hasText(params.url)) {
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
         * 根据创建任务时快照的代理信息组装 {@link Proxy}。
         *
         * <p>未指定代理名称/地址时按直连处理；指定了代理但快照字段缺失或端口非法时按参数错误处理。</p>
         *
         * @throws SfcJsonException 代理配置不完整、端口非法或类型无法识别时抛出
         */
        private void initProxy() {
            if (!SfcStringUtils.hasText(params.proxyAddress)) {
                if (SfcStringUtils.hasText(params.proxy)) {
                    throw new SfcJsonException(400, "无效的代理配置: 缺少代理地址快照（" + params.proxy + "）");
                }
                context.log("未配置代理，下载请求使用直连");
                log.info("{}未配置代理，下载请求使用直连: downloadId={}", LOG_PREFIX, downloadId);
                return;
            }
            if (params.proxyPort == null || params.proxyPort < MIN_PROXY_PORT || params.proxyPort > MAX_PROXY_PORT) {
                throw new SfcJsonException(400, "无效的代理端口: " + params.proxyPort);
            }
            Proxy.Type proxyType = parseProxyType(params.proxyType);
            this.proxy = new Proxy(proxyType, new InetSocketAddress(params.proxyAddress, params.proxyPort));
            context.log("使用代理: " + (SfcStringUtils.hasText(params.proxy) ? params.proxy + " - " : "")
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
         *
         * @throws SfcJsonException           响应状态码非 2xx 时抛出（HTTP 502 语义）
         * @throws SfcTaskInterruptedException 下载过程中任务被中断时抛出
         */
        private void download() {
            HttpHeaders headers = buildHeaders();
            this.fileName = DownloadFileUtils.parseFileNameFromUrl(params.url);
            context.log("开始下载: method=" + method + ", 初始文件名=" + fileName);
            log.info("{}发起下载请求: downloadId={}, method={}, url={}, connectTimeout={}ms, readTimeout={}ms",
                    LOG_PREFIX, downloadId, method, params.url,
                    params.connectTimeout > 0 ? params.connectTimeout : DEFAULT_CONNECT_TIMEOUT,
                    params.readTimeout > 0 ? params.readTimeout : DEFAULT_READ_TIMEOUT);
            try {
                restTemplate.execute(params.url, method, restTemplate.httpEntityCallback(new HttpEntity<>(headers)),
                        response -> {
                            receive(response);
                            return null;
                        });
            } catch (RestClientResponseException e) {
                throw new SfcJsonException(502,
                        "下载失败: HTTP " + e.getStatusCode().value() + " " + e.getStatusText());
            } catch (ResourceAccessException e) {
                // 中断回调关闭下载流会让阻塞中的 read 失败并被包装为资源访问异常，这里统一还原为「取消」语义
                if (context.isCancelled()) {
                    throw new SfcTaskInterruptedException("下载任务被中断", e);
                }
                throw e;
            }
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
         * @throws IOException                读取响应或写入临时文件失败时抛出
         * @throws SfcTaskInterruptedException 接收过程中任务被中断时抛出
         */
        private void receive(ClientHttpResponse response) throws IOException {
            applyResponseFileName(response.getHeaders());
            long totalSize = response.getHeaders().getContentLength();
            SfcTaskProgress progress = context.getExecution().getProgress();
            progress.setTotal(totalSize);
            progress.setLoaded(0L);
            context.log("响应已到达: 文件名=" + fileName
                    + ", 大小=" + (totalSize >= 0 ? totalSize + " 字节" : "未知"));
            log.info("{}下载响应到达: downloadId={}, 状态码={}, 文件名={}, 声明大小={}",
                    LOG_PREFIX, downloadId, response.getStatusCode().value(), fileName,
                    totalSize >= 0 ? totalSize + "字节" : "未知");

            this.tempFile = createTempFile();
            context.log("创建本地临时文件: " + tempFile.toAbsolutePath());
            log.debug("{}已创建本地临时文件: downloadId={}, path={}",
                    LOG_PREFIX, downloadId, tempFile.toAbsolutePath());

            long receiveStartNanos = System.nanoTime();
            try (InputStream body = response.getBody(); OutputStream out = Files.newOutputStream(tempFile)) {
                activeInputStream.set(body);
                copyStream(body, out, progress);
            } catch (IOException e) {
                // 中断回调关闭下载流会让阻塞中的 read 抛 IOException，这里统一还原为「取消」语义
                if (context.isCancelled()) {
                    throw new SfcTaskInterruptedException("下载任务被中断", e);
                }
                throw e;
            } finally {
                activeInputStream.set(null);
            }

            long loaded = progress.getLoaded();
            if (totalSize >= 0 && loaded != totalSize) {
                context.log("[警告] 已下载大小与响应声明大小不一致: 声明=" + totalSize + ", 实际=" + loaded
                        + "，文件可能不完整");
            }
            context.log("文件接收完成: " + loaded + " 字节");
            log.info("{}文件接收完成: downloadId={}, 字节数={}, 耗时={}ms",
                    LOG_PREFIX, downloadId, loaded, elapsedMillis(receiveStartNanos));
        }

        /**
         * 在任务临时目录下创建本次下载的本地临时文件。
         *
         * <p>临时目录由 {@link DownloadContext#getTempDir()} 按任务隔离创建，因此同名文件不会冲突。</p>
         *
         * @return 已创建的空临时文件路径
         * @throws SfcJsonException 文件创建失败时抛出
         */
        private Path createTempFile() {
            Path target = context.getTempDir().resolve(DownloadFileUtils.sanitizeFileName(fileName));
            try {
                return Files.createFile(target);
            } catch (IOException e) {
                throw new SfcJsonException(500, "创建本地临时文件失败: " + e.getMessage());
            }
        }

        /**
         * 从响应头中探测文件名，探测成功时覆盖 URL 解析出的候选文件名。
         *
         * @param headers 响应头
         */
        private void applyResponseFileName(HttpHeaders headers) {
            String responseFileName = extractResponseFileName(headers);
            if (responseFileName == null) {
                context.log("响应头未提供文件名，使用 URL 解析结果: " + fileName);
                return;
            }
            this.fileName = DownloadFileUtils.sanitizeFileName(responseFileName);
            context.log("从响应头探测到文件名: " + fileName);
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
                context.log("解析响应头 Content-Disposition 失败，忽略服务端文件名: " + describe(e));
                return null;
            }
        }

        /**
         * 把响应体逐块复制到临时文件，同时累加进度并检查中断。
         *
         * <p>中断指令到达时取消回调会关闭输入流，使阻塞中的 {@code read} 抛出 {@link IOException}；
         * 每块写入后再调用 {@link DownloadContext#checkInterrupted()} 保证能及时以中断语义退出。</p>
         *
         * @param in       响应输入流
         * @param out      临时文件输出流
         * @param progress 任务进度
         * @throws IOException 读取或写入失败时抛出
         */
        private void copyStream(InputStream in, OutputStream out, SfcTaskProgress progress) throws IOException {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
                progress.addLoaded(len);
                context.checkInterrupted();
            }
        }

        /**
         * 处理中断指令：关闭当前活动的下载流，让阻塞中的读取尽快失败退出。
         */
        private void handleInterrupt() {
            context.log("收到下载中断指令，正在关闭下载流");
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
                    context.log("关闭下载流失败: " + describe(e));
                }
            }
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
            return SfcStringUtils.hasText(message) ? message : e.getClass().getSimpleName();
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
    }
}
