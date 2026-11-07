package com.sfc.demo.controller;

import com.sfc.cloud.task.worker.SfcTaskWorkerClient;
import com.sfc.cloud.task.worker.protocol.TaskSubmitParam;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.SfcMapperHolder;
import com.sfc.demo.task.DemoSleepTaskHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * demo 微服务的异步任务示例控制器。
 *
 * <p>演示如何通过 {@link SfcTaskWorkerClient#submit(TaskSubmitParam)} 把耗时任务提交给 sfc-core
 * 统一调度（本例由本服务的 {@link DemoSleepTaskHandler} 执行）。</p>
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
     * 提交一个演示任务。
     *
     * @param uid       任务归属用户id，默认 0（公共用户）
     * @param millis    任务总耗时毫秒数，默认 3000
     * @param steps     任务进度步数，默认 10
     * @param immediate 是否要求立即处理（紧急任务），默认 false；为 true 时执行者满载也会优先认领
     * @param isTemp    是否为临时任务，默认 false
     * @param taskType  任务类型，默认 {@code demo-sleep}（须与某个执行者声明的任务类型一致）
     * @param params    直接指定任务参数原文（默认按 {@code {"millis":..,"steps":..}} 构造；
     *                  用于提交 core 侧内置任务类型等需要其它参数格式的联调场景）
     * @return 新任务id
     * @throws SfcJsonException 参数非法或提交失败时抛出
     */
    @PostMapping("/submit")
    public SfcJsonResult<Long> submit(
            @RequestParam(value = "uid", required = false, defaultValue = "0") Long uid,
            @RequestParam(value = "millis", required = false, defaultValue = "3000") Long millis,
            @RequestParam(value = "steps", required = false, defaultValue = "10") Integer steps,
            @RequestParam(value = "immediate", required = false, defaultValue = "false") Boolean immediate,
            @RequestParam(value = "isTemp", required = false, defaultValue = "false") Boolean isTemp,
            @RequestParam(value = "taskType", required = false, defaultValue = DemoSleepTaskHandler.TASK_TYPE)
            String taskType,
            @RequestParam(value = "params", required = false) String params) {
        if (millis == null || millis < 0L) {
            throw new SfcJsonException(400, "millis 不能为负数");
        }
        if (!StringUtils.hasText(taskType)) {
            throw new SfcJsonException(400, "taskType 不能为空");
        }
        int safeSteps = steps == null || steps < 1 ? DEFAULT_STEPS : steps;
        String safeTaskType = taskType.trim();
        String taskParams;
        if (StringUtils.hasText(params)) {
            taskParams = params;
        } else {
            Map<String, Object> paramMap = new LinkedHashMap<>();
            paramMap.put("millis", millis);
            paramMap.put("steps", safeSteps);
            taskParams = SfcMapperHolder.toJsonNoEx(paramMap);
        }
        TaskSubmitParam submitParam = new TaskSubmitParam(
                safeTaskType + "-" + millis + "ms",
                safeTaskType,
                taskParams,
                uid == null ? 0L : uid,
                DEMO_CPU_OVERHEAD,
                Boolean.TRUE.equals(isTemp),
                Boolean.TRUE.equals(immediate));
        Long taskId = sfcTaskWorkerClient.submit(submitParam);
        log.info("[demo] 已提交异步任务：taskId={}, uid={}, taskType={}, millis={}, steps={}, immediate={}, isTemp={}",
                taskId, uid, safeTaskType, millis, safeSteps, immediate, isTemp);
        return SfcJsonResult.getInstance(taskId);
    }
}
