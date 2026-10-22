package com.sfc.demo.controller;

import com.sfc.cloud.task.worker.SfcTaskWorkerClient;
import com.sfc.cloud.task.worker.protocol.TaskSubmitParam;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.SfcMapperHolder;
import com.sfc.demo.task.DemoSleepTaskHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * demo 微服务的异步任务示例控制器。
 *
 * <p>演示微服务如何通过 {@link SfcTaskWorkerClient#submit(TaskSubmitParam)} 把耗时任务交给
 * sfc-core 的统一异步任务系统调度：任务写入 core 的队列后，由任意具备 {@code demo-sleep}
 * 能力的执行者认领执行（本例即本服务的 {@link DemoSleepTaskHandler}），
 * 任务状态/进度/日志/中断统一由 core 管理。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/demo/task")
@RequiredArgsConstructor
public class DemoTaskController {

    /**
     * 提交演示任务的 CPU 开销指数（100 = 1 个 CPU 核心算力；sleep 任务几乎不占 CPU）
     */
    private static final int DEMO_CPU_OVERHEAD = 10;

    /**
     * 未指定步数时的默认值
     */
    private static final int DEFAULT_STEPS = 10;

    /**
     * 任务 Worker 客户端
     */
    private final SfcTaskWorkerClient sfcTaskWorkerClient;

    /**
     * 提交一个 demo-sleep 任务。
     *
     * @param uid    任务归属用户id，默认 0（公共用户）
     * @param millis 任务总耗时毫秒数，默认 3000
     * @param steps  任务进度步数，默认 10
     * @return 新任务id
     * @throws SfcJsonException 参数非法或提交失败时抛出
     */
    @PostMapping("/submit")
    public SfcJsonResult<Long> submit(@RequestParam(value = "uid", required = false, defaultValue = "0") Long uid,
                                      @RequestParam(value = "millis", required = false, defaultValue = "3000") Long millis,
                                      @RequestParam(value = "steps", required = false, defaultValue = "10") Integer steps) {
        if (millis == null || millis < 0L) {
            throw new SfcJsonException(400, "millis 不能为负数");
        }
        int safeSteps = steps == null || steps < 1 ? DEFAULT_STEPS : steps;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("millis", millis);
        params.put("steps", safeSteps);
        TaskSubmitParam submitParam = new TaskSubmitParam(
                "demo-sleep-" + millis + "ms",
                DemoSleepTaskHandler.TASK_TYPE,
                SfcMapperHolder.toJsonNoEx(params),
                uid == null ? 0L : uid,
                DEMO_CPU_OVERHEAD,
                false);
        Long taskId = sfcTaskWorkerClient.submit(submitParam);
        log.info("[demo] 已提交 demo-sleep 任务：taskId={}, uid={}, millis={}, steps={}",
                taskId, uid, millis, safeSteps);
        return SfcJsonResult.getInstance(taskId);
    }
}
