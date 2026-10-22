package com.sfc.cloud.task.worker;

import com.sfc.cloud.task.worker.protocol.TaskInterruptCommand;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务中断入站端点。
 *
 * <p>sfc-core 在用户中断一个由微服务执行的任务时，会向 worker 注册时上报的
 * {@code callbackUrl} 发起 {@code POST /api/internal/task/interrupt}，请求体为
 * {@link TaskInterruptCommand}。本端点把中断标记写入对应的执行上下文：</p>
 * <ul>
 *     <li>触发任务处理器通过 {@link SfcTaskExecution#onInterrupt(Runnable)} 注册的回调；</li>
 *     <li>使 {@link SfcTaskExecution#checkInterrupted()} 抛出 {@link SfcTaskInterruptedException}。</li>
 * </ul>
 *
 * <p>⚠️ 本端点位于 {@code /api/internal/**} 前缀下，由消费方开启的
 * {@code @EnableSfcInternalAuth}（sfc-auth SDK）负责校验服务间调用凭证。</p>
 *
 * <p>中断是「尽力而为」且幂等的：若任务已在本实例结束（可能刚好完成），或仍处于「已认领、
 * 尚未开始执行」的窗口，本端点都会成功受理——前者不会再影响任何任务，后者会在任务开始执行时
 * 立即生效，避免 core 侧出现过期回调的噪音错误日志。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/task")
public class SfcTaskInterruptController {

    /**
     * 日志前缀
     */
    private static final String LOG_PREFIX = "[任务Worker]";

    /**
     * 运行中任务注册表
     */
    private final SfcTaskWorkerRegistry registry;

    /**
     * 构造中断端点（由自动装配创建）。
     *
     * @param registry 运行中任务注册表
     */
    SfcTaskInterruptController(SfcTaskWorkerRegistry registry) {
        this.registry = registry;
    }

    /**
     * 接收 core 下发的任务中断指令。
     *
     * @param command 中断指令
     * @return 是否已受理（已运行的任务立即生效；尚未开始执行的任务在开始执行时生效）
     * @throws SfcJsonException {@code taskId} 为空时抛出
     */
    @PostMapping("/interrupt")
    public SfcJsonResult<Boolean> interrupt(@RequestBody TaskInterruptCommand command) {
        if (command == null || command.taskId() == null) {
            throw new SfcJsonException(400, "taskId 不能为空");
        }
        boolean accepted = registry.interrupt(command.taskId());
        log.info("{}收到任务[{}]的中断指令，已受理（若任务尚未开始执行，将在开始执行时立即生效）",
                LOG_PREFIX, command.taskId());
        return SfcJsonResult.getInstance(accepted);
    }
}
