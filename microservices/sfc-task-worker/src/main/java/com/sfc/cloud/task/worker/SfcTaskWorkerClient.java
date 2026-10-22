package com.sfc.cloud.task.worker;

import com.sfc.cloud.auth.SfcInternalTokenIssuer;
import com.sfc.cloud.auth.client.SfcInternalTokenRequestInitializer;
import com.sfc.cloud.task.worker.protocol.TaskAbandonParam;
import com.sfc.cloud.task.worker.protocol.TaskClaimParam;
import com.sfc.cloud.task.worker.protocol.TaskDispatchInfo;
import com.sfc.cloud.task.worker.protocol.TaskFinishReportParam;
import com.sfc.cloud.task.worker.protocol.TaskLogReportParam;
import com.sfc.cloud.task.worker.protocol.TaskProgressReportParam;
import com.sfc.cloud.task.worker.protocol.TaskReportStartParam;
import com.sfc.cloud.task.worker.protocol.TaskSubmitParam;
import com.sfc.cloud.task.worker.protocol.TaskWorkerRegisterParam;
import com.sfc.cloud.task.worker.protocol.TaskWorkerUnregisterParam;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.SfcMapperHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * sfc-core 任务执行者内部协议客户端。
 *
 * <p>基于 {@link RestClient} 实现，所有出站请求都会通过
 * {@link SfcInternalTokenRequestInitializer} 在请求头 {@code X-Sfc-Internal-Token} 中携带
 * 每请求新签发的 HS256 内部调用凭证（签发器由消费方的 {@code @EnableSfcInternalAuth} 提供）。</p>
 *
 * <p>本类主要由 Worker SDK 内部使用；<b>对外公开的能力是
 * {@link #submit(TaskSubmitParam)}</b>——微服务可以用它把耗时任务交给 core 的统一异步任务系统调度。</p>
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
     * 注册（心跳续约）
     */
    private static final String PATH_WORKER_REGISTER = PATH_PREFIX + "/worker/register";

    /**
     * 注销
     */
    private static final String PATH_WORKER_UNREGISTER = PATH_PREFIX + "/worker/unregister";

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
     * 底层 RestClient
     */
    private final RestClient restClient;

    /**
     * sfc-core 基础地址（已去掉末尾斜杠），用于错误信息
     */
    private final String coreBaseUrl;

    /**
     * 构造客户端（由自动装配创建）。
     *
     * @param properties     配置项
     * @param issuerProvider 内部调用凭证签发器提供者；消费方未开启 {@code @EnableSfcInternalAuth}
     *                       时为空，此时请求不携带凭证（core 侧会拒绝，仅打印告警）
     */
    SfcTaskWorkerClient(SfcTaskWorkerProperties properties, ObjectProvider<SfcInternalTokenIssuer> issuerProvider) {
        this.coreBaseUrl = normalizeBaseUrl(properties.getCoreBaseUrl());
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(
                MIN_READ_TIMEOUT_SECONDS,
                Math.max(0, properties.getClaimWaitSeconds()) + READ_TIMEOUT_MARGIN_SECONDS)));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(coreBaseUrl)
                .requestFactory(requestFactory);
        SfcInternalTokenIssuer issuer = issuerProvider == null ? null : issuerProvider.getIfAvailable();
        if (issuer != null) {
            builder.requestInitializer(new SfcInternalTokenRequestInitializer(issuer));
        } else {
            log.warn("{}未找到内部调用凭证签发器（SfcInternalTokenIssuer）：发往 sfc-core 的内部接口请求将不带凭证，"
                    + "请确认消费方已开启 @EnableSfcInternalAuth 并配置 sfc.internal.key（环境变量 SFC_INTERNAL_KEY）",
                    LOG_PREFIX);
        }
        this.restClient = builder.build();
    }

    /**
     * 提交一个异步任务（对外公开能力）。
     *
     * <p>任务以 {@code param.uid()} 指定的用户身份创建，写入 core 的统一任务队列，由具备该任务类型
     * 能力的执行者（core 节点或任意微服务实例）认领执行。</p>
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
     * 注册或续约当前 worker。
     *
     * @param param 注册参数
     */
    void register(TaskWorkerRegisterParam param) {
        post(PATH_WORKER_REGISTER, param, new ParameterizedTypeReference<SfcJsonResult<Object>>() {
        });
    }

    /**
     * 注销当前 worker。
     *
     * @param workerId worker 标识
     */
    void unregister(String workerId) {
        post(PATH_WORKER_UNREGISTER, new TaskWorkerUnregisterParam(workerId),
                new ParameterizedTypeReference<SfcJsonResult<Object>>() {
                });
    }

    /**
     * 认领一个任务；无任务可认领时按 core 侧的长轮询等待。
     *
     * @param workerId    worker 标识
     * @param types       允许认领的任务类型
     * @param waitSeconds 最多等待秒数
     * @return 认领到的任务；无任务时返回 null
     */
    TaskDispatchInfo claim(String workerId, List<String> types, int waitSeconds) {
        return post(PATH_CLAIM, new TaskClaimParam(workerId, new ArrayList<>(types), waitSeconds),
                new ParameterizedTypeReference<SfcJsonResult<TaskDispatchInfo>>() {
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
     * @param path    接口路径
     * @param body    请求体
     * @param typeRef 响应信封的类型引用
     * @param <T>     业务数据类型
     * @return 业务数据，可能为 null
     * @throws SfcJsonException 网络失败、HTTP 非 2xx 或信封 code 非 200 时抛出
     */
    private <T> T post(String path, Object body, ParameterizedTypeReference<SfcJsonResult<T>> typeRef) {
        SfcJsonResult<T> result;
        try {
            result = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(typeRef);
        } catch (RestClientResponseException e) {
            throw new SfcJsonException(e.getStatusCode().value(), "调用 sfc-core 内部接口[" + path
                    + "]失败：HTTP " + e.getStatusCode().value() + resolveErrorMessage(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            throw new SfcJsonException(503, "调用 sfc-core 内部接口[" + path + "]失败：无法连接 "
                    + coreBaseUrl + "（" + describe(e) + "）");
        } catch (Exception e) {
            throw new SfcJsonException(500, "调用 sfc-core 内部接口[" + path + "]失败：" + describe(e));
        }
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
     * 统一去掉基础地址末尾的斜杠；为空时回退到默认值。
     *
     * @param baseUrl 配置的基础地址
     * @return 归一化后的基础地址
     */
    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? "http://127.0.0.1:8087" : normalized;
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
}
