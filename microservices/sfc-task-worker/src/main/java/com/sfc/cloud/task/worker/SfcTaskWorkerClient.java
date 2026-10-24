package com.sfc.cloud.task.worker;

import com.sfc.cloud.auth.SfcInternalTokenIssuer;
import com.sfc.cloud.auth.client.SfcInternalTokenRequestInitializer;
import com.sfc.cloud.constants.SfcServiceName;
import com.sfc.cloud.task.worker.protocol.TaskAbandonParam;
import com.sfc.cloud.task.worker.protocol.TaskClaimParam;
import com.sfc.cloud.task.worker.protocol.TaskDispatchInfo;
import com.sfc.cloud.task.worker.protocol.TaskFinishReportParam;
import com.sfc.cloud.task.worker.protocol.TaskInterruptCommand;
import com.sfc.cloud.task.worker.protocol.TaskLogReportParam;
import com.sfc.cloud.task.worker.protocol.TaskProgressReportParam;
import com.sfc.cloud.task.worker.protocol.TaskReportStartParam;
import com.sfc.cloud.task.worker.protocol.TaskSubmitParam;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.SfcMapperHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * sfc-core 任务执行者内部协议客户端。
 *
 * <p>基于 {@link RestClient} 实现：发往 sfc-core 的请求会通过
 * {@link SfcInternalTokenRequestInitializer} 在请求头 {@code X-Sfc-Internal-Token} 中携带
 * 每请求新签发的 HS256 内部调用凭证（签发器由消费方的 {@code @EnableSfcInternalAuth} 提供）。</p>
 *
 * <p>sfc-core 的实例由 {@link LoadBalancerClient} 按服务名（{@code sfc.task.worker.core-service-id}，
 * 默认 {@code sfc-core}）从服务发现中选取，多实例默认轮询；因此消费方必须启用服务发现与负载均衡，
 * 本 SDK 不支持直连兜底地址。</p>
 *
 * <p>对外公开的能力：{@link #submit(TaskSubmitParam)}（提交任务）、
 * {@link #requestInterrupt(String, Long)}（core 向指定 worker 实例下发中断指令）、
 * {@link #isAuthConfigured()}（内部鉴权配置自检）。</p>
 */
@Slf4j
public class SfcTaskWorkerClient {

    /**
     * 日志前缀
     */
    private static final String LOG_PREFIX = "[任务Worker]";

    /**
     * 内部接口统一前缀
     */
    private static final String PATH_PREFIX = "/api/internal/task";

    /**
     * 认领任务（长轮询）
     */
    private static final String PATH_CLAIM = PATH_PREFIX + "/claim";

    /**
     * 提交任务
     */
    private static final String PATH_SUBMIT = PATH_PREFIX + "/submit";

    /**
     * 上报任务开始
     */
    private static final String PATH_REPORT_START = PATH_PREFIX + "/report/start";

    /**
     * 上报任务进度
     */
    private static final String PATH_REPORT_PROGRESS = PATH_PREFIX + "/report/progress";

    /**
     * 上报任务日志
     */
    private static final String PATH_REPORT_LOG = PATH_PREFIX + "/report/log";

    /**
     * 上报任务结束
     */
    private static final String PATH_REPORT_FINISH = PATH_PREFIX + "/report/finish";

    /**
     * 上报放弃执行权
     */
    private static final String PATH_REPORT_ABANDON = PATH_PREFIX + "/report/abandon";

    /**
     * 下发中断指令
     */
    private static final String PATH_INTERRUPT = PATH_PREFIX + "/interrupt";

    /**
     * 连接 sfc-core 的超时时间
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * 读取超时的最小秒数（必须大于认领长轮询的最长等待时间）
     */
    private static final long MIN_READ_TIMEOUT_SECONDS = 30L;

    /**
     * 读取超时相对认领等待时间的余量（秒）
     */
    private static final long READ_TIMEOUT_MARGIN_SECONDS = 15L;

    /**
     * 底层 RestClient（不含固定 baseUrl，每次请求按负载均衡选出的实例地址拼装绝对地址）
     */
    private final RestClient restClient;

    /**
     * 负载均衡选路器：按 {@code coreServiceId} 从服务发现中选取 core 实例；消费方未启用服务发现/负载均衡时为 null
     */
    private final LoadBalancerClient loadBalancerClient;

    /**
     * sfc-core 在服务注册中心中的服务名（空白时回退 {@link SfcServiceName#CORE}）
     */
    private final String coreServiceId;

    /**
     * 内部调用凭证签发器；消费方未开启 {@code @EnableSfcInternalAuth} 时为 null
     */
    private final SfcInternalTokenIssuer issuer;

    /**
     * 构造客户端（由自动装配创建）。
     *
     * @param properties           配置项
     * @param loadBalancerProvider 负载均衡选路器提供者；消费方未启用服务发现/负载均衡时为空，
     *                             此时所有发往 sfc-core 的请求都会以 503 失败（Worker 启动自检会提前禁用）
     * @param issuerProvider       内部调用凭证签发器提供者；消费方未开启 {@code @EnableSfcInternalAuth}
     *                             时为空，此时请求不携带凭证
     */
    SfcTaskWorkerClient(SfcTaskWorkerProperties properties,
                        ObjectProvider<LoadBalancerClient> loadBalancerProvider,
                        ObjectProvider<SfcInternalTokenIssuer> issuerProvider) {
        this.loadBalancerClient = loadBalancerProvider == null ? null : loadBalancerProvider.getIfAvailable();
        this.coreServiceId = resolveCoreServiceId(properties);
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(
                MIN_READ_TIMEOUT_SECONDS,
                Math.max(0, properties.getClaimWaitSeconds()) + READ_TIMEOUT_MARGIN_SECONDS)));
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        SfcInternalTokenIssuer resolvedIssuer = issuerProvider == null ? null : issuerProvider.getIfAvailable();
        this.issuer = resolvedIssuer;
        if (resolvedIssuer != null) {
            builder.requestInitializer(new SfcInternalTokenRequestInitializer(resolvedIssuer));
        } else {
            log.warn("{}未找到内部调用凭证签发器（SfcInternalTokenIssuer）：发往 sfc-core 的内部接口请求将不带凭证，"
                    + "请确认消费方已开启 @EnableSfcInternalAuth 并配置 sfc.internal.key（环境变量 SFC_INTERNAL_KEY）",
                    LOG_PREFIX);
        }
        this.restClient = builder.build();
    }

    /**
     * 判断内部接口调用凭证是否可用。
     *
     * <p>只有在找到凭证签发器且共享密钥已配置时才可用；不可用时所有发往 sfc-core 的内部接口请求
     * 都会被鉴权拒绝。</p>
     *
     * @return 可以签发内部调用凭证返回 true
     */
    public boolean isAuthConfigured() {
        return issuer != null && issuer.isConfigured();
    }

    /**
     * 判断是否具备定位 sfc-core 的能力（消费方已提供服务发现与负载均衡选路器）。
     *
     * @return 具备定位能力返回 true
     */
    boolean isCoreLocatable() {
        return loadBalancerClient != null;
    }

    /**
     * 获取 sfc-core 在服务注册中心中的服务名（已解析默认值）。
     *
     * @return sfc-core 的服务名
     */
    String getCoreServiceId() {
        return coreServiceId;
    }

    /**
     * 提交一个异步任务（对外公开能力）。
     *
     * <p>任务以 {@code param.uid()} 指定的用户身份创建，交由 core 的统一异步任务调度。</p>
     *
     * @param param 提交参数
     * @return 新任务id
     * @throws SfcJsonException 参数为空、调用失败或 core 返回错误时抛出
     */
    public Long submit(TaskSubmitParam param) {
        if (param == null) {
            throw new SfcJsonException(400, "提交任务的参数不能为空");
        }
        Long taskId = post(PATH_SUBMIT, param, new ParameterizedTypeReference<SfcJsonResult<Long>>() {
        });
        if (taskId == null) {
            throw new SfcJsonException(500, "sfc-core 未返回新任务id");
        }
        return taskId;
    }

    /**
     * 向指定 worker 实例下发任务中断指令。
     *
     * <p>用于 core 侧按服务发现定位到的实例地址回调微服务执行者：向
     * {@code {baseUrl}/api/internal/task/interrupt} 发起 POST，请求体为 {@link TaskInterruptCommand}。
     * 中断是尽力而为的：失败只记录 WARN 并返回 false，由调用方决定是否重试或直接取消任务。</p>
     *
     * @param baseUrl worker 实例的基础地址，形如 {@code http://192.168.1.10:8090}
     * @param taskId  要中断的任务id
     * @return 中断指令下发成功返回 true；地址或任务id为空、调用失败时返回 false
     */
    public boolean requestInterrupt(String baseUrl, Long taskId) {
        if (!hasText(baseUrl) || taskId == null) {
            log.warn("{}下发任务中断指令失败：worker 地址或任务id为空（baseUrl={}, taskId={}）",
                    LOG_PREFIX, baseUrl, taskId);
            return false;
        }
        String target = normalizeBaseUrl(baseUrl);
        try {
            doPost(target, PATH_INTERRUPT, new TaskInterruptCommand(taskId),
                    new ParameterizedTypeReference<SfcJsonResult<Boolean>>() {
                    });
            log.info("{}已向worker[{}]下发任务[{}]的中断指令", LOG_PREFIX, target, taskId);
            return true;
        } catch (Throwable e) {
            log.warn("{}向worker[{}]下发任务[{}]的中断指令失败: {}",
                    LOG_PREFIX, target, taskId, describe(e));
            return false;
        }
    }

    /**
     * 认领一个任务；无任务可认领时按 core 侧的长轮询等待。
     *
     * @param param 认领参数（含执行者身份、支持的任务类型、是否只认领紧急任务与等待秒数）
     * @return 认领到的任务；无任务时返回 null
     */
    TaskDispatchInfo claim(TaskClaimParam param) {
        return post(PATH_CLAIM, param, new ParameterizedTypeReference<SfcJsonResult<TaskDispatchInfo>>() {
        });
    }

    /**
     * 上报任务开始执行。
     *
     * @param param 上报参数
     * @return core 是否接受（任务已结束或不属于本 worker 时返回 false）
     */
    boolean reportStart(TaskReportStartParam param) {
        Boolean accepted = post(PATH_REPORT_START, param, new ParameterizedTypeReference<SfcJsonResult<Boolean>>() {
        });
        return Boolean.TRUE.equals(accepted);
    }

    /**
     * 上报任务执行进度。
     *
     * @param param 上报参数
     */
    void reportProgress(TaskProgressReportParam param) {
        post(PATH_REPORT_PROGRESS, param, new ParameterizedTypeReference<SfcJsonResult<Object>>() {
        });
    }

    /**
     * 上报任务运行日志。
     *
     * @param param 上报参数
     */
    void reportLog(TaskLogReportParam param) {
        post(PATH_REPORT_LOG, param, new ParameterizedTypeReference<SfcJsonResult<Object>>() {
        });
    }

    /**
     * 上报任务执行结束。
     *
     * @param param 上报参数
     * @return core 是否接受（任务已结束或不属于本 worker 时返回 false）
     */
    boolean reportFinish(TaskFinishReportParam param) {
        Boolean accepted = post(PATH_REPORT_FINISH, param, new ParameterizedTypeReference<SfcJsonResult<Boolean>>() {
        });
        return Boolean.TRUE.equals(accepted);
    }

    /**
     * 上报放弃任务执行权（core 会把任务重置回等待中并重新发布）。
     *
     * @param param 上报参数
     * @return core 是否已把任务重置回等待中
     */
    boolean reportAbandon(TaskAbandonParam param) {
        Boolean accepted = post(PATH_REPORT_ABANDON, param, new ParameterizedTypeReference<SfcJsonResult<Boolean>>() {
        });
        return Boolean.TRUE.equals(accepted);
    }

    /**
     * POST 调用一个内部接口并取出响应信封中的业务数据。
     *
     * <p>每次请求先由负载均衡选路器选取一个 core 实例；请求失败不换实例重试，由调用方兜底：
     * 认领循环每秒重试，开始/结束/放弃上报自带重试，进度与日志随下一个上报周期重发。</p>
     *
     * @param path    接口路径
     * @param body    请求体
     * @param typeRef 响应信封的类型引用
     * @param <T>     业务数据类型
     * @return 业务数据，可能为 null
     * @throws SfcJsonException 无法定位 core、网络失败、HTTP 非 2xx 或信封 code 非 200 时抛出
     */
    private <T> T post(String path, Object body, ParameterizedTypeReference<SfcJsonResult<T>> typeRef) {
        String baseUrl = resolveCoreBaseUrl(path);
        try {
            return doPost(baseUrl, path, body, typeRef);
        } catch (ResourceAccessException e) {
            throw new SfcJsonException(503, "调用 sfc-core 内部接口[" + path + "]失败：无法连接 "
                    + baseUrl + "（" + describe(e) + "）");
        } catch (RestClientResponseException e) {
            throw new SfcJsonException(e.getStatusCode().value(), "调用 sfc-core 内部接口[" + path
                    + "]失败：HTTP " + e.getStatusCode().value() + resolveErrorMessage(e.getResponseBodyAsString()));
        } catch (SfcJsonException e) {
            throw e;
        } catch (Exception e) {
            throw new SfcJsonException(500, "调用 sfc-core 内部接口[" + path + "]失败：" + describe(e));
        }
    }

    /**
     * 通过负载均衡选路器解析本次请求要用的 sfc-core 实例基础地址。
     *
     * @param path 接口路径（仅用于异常信息）
     * @return 实例基础地址，形如 {@code http://192.168.1.10:8087}
     * @throws SfcJsonException 未启用服务发现/负载均衡、服务发现查询失败或没有可用实例时抛出
     */
    private String resolveCoreBaseUrl(String path) {
        if (loadBalancerClient == null) {
            throw new SfcJsonException(503, "调用 sfc-core 内部接口[" + path
                    + "]失败：未启用服务发现/负载均衡，无法定位 sfc-core[" + coreServiceId + "]");
        }
        ServiceInstance instance;
        try {
            instance = loadBalancerClient.choose(coreServiceId);
        } catch (Throwable e) {
            throw new SfcJsonException(503, "调用 sfc-core 内部接口[" + path + "]失败：服务发现查询 sfc-core["
                    + coreServiceId + "]实例失败（" + describe(e) + "）");
        }
        if (instance == null || !hasText(instance.getHost()) || instance.getPort() <= 0) {
            throw new SfcJsonException(503, "调用 sfc-core 内部接口[" + path
                    + "]失败：服务发现中无可用 sfc-core[" + coreServiceId + "]实例");
        }
        String scheme = instance.getScheme();
        if (!hasText(scheme)) {
            scheme = instance.isSecure() ? "https" : "http";
        }
        return scheme.trim() + "://" + instance.getHost().trim() + ":" + instance.getPort();
    }

    /**
     * 向指定基础地址发起一次内部接口调用，并取出响应信封中的业务数据。
     *
     * @param baseUrl 目标实例基础地址（{@code scheme://host:port}）
     * @param path    接口路径
     * @param body    请求体
     * @param typeRef 响应信封的类型引用
     * @param <T>     业务数据类型
     * @return 业务数据，可能为 null
     */
    private <T> T doPost(String baseUrl, String path, Object body,
                         ParameterizedTypeReference<SfcJsonResult<T>> typeRef) {
        SfcJsonResult<T> result = restClient.post()
                .uri(baseUrl + path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(typeRef);
        if (result == null) {
            throw new SfcJsonException(500, "调用 sfc-core 内部接口[" + path + "]失败：响应体为空");
        }
        if (result.getCode() != 200) {
            throw new SfcJsonException(result.getCode(),
                    "调用 sfc-core 内部接口[" + path + "]失败：" + result.getMsg());
        }
        return result.getData();
    }

    /**
     * 从 core 的错误响应体中提取 msg，用于拼装更可读的异常信息。
     *
     * @param responseBody 响应体文本，可能为空
     * @return 形如 "，错误信息: xxx" 的后缀；无法解析时返回空串
     */
    private static String resolveErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            Object parsed = SfcMapperHolder.mapper.readValue(responseBody, SfcJsonResult.class);
            if (parsed instanceof SfcJsonResult<?> result
                    && result.getMsg() != null && !result.getMsg().isBlank()) {
                return "，错误信息: " + result.getMsg();
            }
        } catch (Exception e) {
            log.debug("{}解析 core 错误响应体失败: {}", LOG_PREFIX, e.getMessage());
        }
        return "";
    }

    /**
     * 解析 sfc-core 的服务名，配置为空时使用默认值。
     *
     * @param properties 配置项
     * @return 服务名
     */
    private static String resolveCoreServiceId(SfcTaskWorkerProperties properties) {
        String configured = properties.getCoreServiceId();
        return hasText(configured) ? configured.trim() : SfcServiceName.CORE;
    }

    /**
     * 异常简要描述：优先使用异常消息，消息为空时回退异常类型名。
     *
     * @param e 异常
     * @return 简要描述
     */
    private static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    /**
     * 归一化基础地址：去掉首尾空白与末尾斜杠。
     *
     * @param baseUrl 原始基础地址
     * @return 归一化后的基础地址
     */
    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 判断字符串非 null 且包含非空白字符。
     *
     * @param value 待判断字符串
     * @return 非空白时返回 true
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
