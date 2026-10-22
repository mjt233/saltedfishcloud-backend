package com.sfc.task.worker;

import com.sfc.cloud.auth.SfcInternalTokenIssuer;
import com.sfc.cloud.auth.client.SfcInternalTokenRequestInitializer;
import com.sfc.task.protocol.TaskInterruptCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 微服务 worker 回调客户端。
 * <p>core 需要向执行任务的微服务实例下发控制指令（目前为中断指令），本客户端负责以
 * sfc-auth 内部调用凭证发起该 HTTP 回调。</p>
 */
@Slf4j
@Component
public class TaskWorkerCallbackClient {
    private static final String LOG_PREFIX = "[异步任务Worker回调]";

    /**
     * 中断指令的下发路径
     */
    private static final String INTERRUPT_PATH = "/api/internal/task/interrupt";

    /**
     * 回调请求超时时间
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;

    public TaskWorkerCallbackClient(ObjectProvider<SfcInternalTokenIssuer> issuerProvider) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build());
        requestFactory.setReadTimeout(REQUEST_TIMEOUT);
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        SfcInternalTokenIssuer issuer = issuerProvider.getIfAvailable();
        if (issuer != null) {
            builder.requestInitializer(new SfcInternalTokenRequestInitializer(issuer));
        } else {
            log.warn("{}未找到内部调用凭证签发器，向微服务下发控制指令可能因鉴权失败而被拒绝", LOG_PREFIX);
        }
        this.restClient = builder.build();
    }

    /**
     * 向微服务 worker 下发任务中断指令。
     *
     * @param worker worker 注册信息
     * @param taskId 要中断的任务id
     * @return 下发成功返回 true
     */
    public boolean requestInterrupt(TaskWorkerInfo worker, Long taskId) {
        if (worker == null || !StringUtils.hasText(worker.getCallbackUrl())) {
            return false;
        }
        String baseUrl = worker.getCallbackUrl().endsWith("/")
                ? worker.getCallbackUrl().substring(0, worker.getCallbackUrl().length() - 1)
                : worker.getCallbackUrl();
        try {
            restClient.post()
                    .uri(baseUrl + INTERRUPT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TaskInterruptCommand(taskId))
                    .retrieve()
                    .toBodilessEntity();
            log.info("{}已向worker[{}]下发任务[{}]的中断指令", LOG_PREFIX, worker.getWorkerId(), taskId);
            return true;
        } catch (Exception e) {
            log.warn("{}向worker[{}]下发任务[{}]的中断指令失败: {}",
                    LOG_PREFIX, worker.getWorkerId(), taskId, e.getMessage());
            return false;
        }
    }
}
