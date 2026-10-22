package com.sfc.demo.task;

import com.sfc.cloud.task.worker.SfcTaskExecution;
import com.sfc.cloud.task.worker.SfcTaskHandler;
import com.sfc.cloud.task.worker.SfcTaskInterruptedException;
import com.sfc.cloud.task.worker.SfcTaskProgress;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.utils.SfcMapperHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * demo 微服务的示例任务处理器：{@code demo-sleep}。
 *
 * <p>用于演示 Worker SDK 的完整链路：core 调度 → 本服务认领 → 输出日志与进度（SDK 周期上报）→
 * 响应中断 → 上报完成/取消。任务参数支持两种形式：</p>
 * <ul>
 *     <li>纯毫秒数：{@code "5000"}，按默认步数（10 步）分步执行；</li>
 *     <li>JSON：{@code {"millis":5000,"steps":20}}，毫秒数被平均拆分为若干步，每步更新一次进度。</li>
 * </ul>
 */
@Slf4j
@Component
public class DemoSleepTaskHandler implements SfcTaskHandler {

    /**
     * 任务类型标识（提交任务时必须使用该值）
     */
    public static final String TASK_TYPE = "demo-sleep";

    /**
     * 未指定毫秒数时的默认耗时
     */
    private static final long DEFAULT_MILLIS = 3000L;

    /**
     * 未指定步数时的默认步数
     */
    private static final int DEFAULT_STEPS = 10;

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public void execute(SfcTaskExecution execution) {
        SleepTaskParams taskParams = parseParams(execution.getParams());
        SfcTaskProgress progress = execution.getProgress();
        progress.setTotal(taskParams.steps());
        progress.setLoaded(0L);
        long singleStepMillis = taskParams.millis() / taskParams.steps();
        long startAt = System.currentTimeMillis();
        execution.log("开始执行 demo-sleep：计划耗时 " + taskParams.millis() + " ms，共 "
                + taskParams.steps() + " 步");

        // 注册中断回调：core 下发中断指令时打断本线程的 sleep，让处理器能尽快退出
        Thread currentThread = Thread.currentThread();
        execution.onInterrupt(currentThread::interrupt);

        try {
            for (int step = 1; step <= taskParams.steps(); step++) {
                execution.checkInterrupted();
                if (singleStepMillis > 0L) {
                    //noinspection BusyWait
                    Thread.sleep(singleStepMillis);
                }
                progress.setLoaded(step);
                execution.log("demo-sleep 进度 " + step + "/" + taskParams.steps()
                        + "（" + (step * 100 / taskParams.steps()) + "%）");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SfcTaskInterruptedException("demo-sleep 任务被中断，已执行 "
                    + (System.currentTimeMillis() - startAt) + " ms", e);
        }

        execution.log("demo-sleep 执行完成，实际耗时 "
                + (System.currentTimeMillis() - startAt) + " ms");
    }

    /**
     * 解析任务参数。
     *
     * @param params 任务参数（毫秒数或 JSON）
     * @return 解析结果
     * @throws SfcJsonException 参数格式非法时抛出
     */
    private static SleepTaskParams parseParams(String params) {
        if (params == null || params.isBlank()) {
            return new SleepTaskParams(DEFAULT_MILLIS, DEFAULT_STEPS);
        }
        String trimmed = params.trim();
        try {
            if (trimmed.startsWith("{")) {
                Map<String, Object> map = SfcMapperHolder.parseJsonToMap(trimmed);
                long millis = Math.max(0L, parseLong(map.get("millis"), DEFAULT_MILLIS));
                int steps = (int) Math.max(1L, parseLong(map.get("steps"), DEFAULT_STEPS));
                return new SleepTaskParams(millis, steps);
            }
            return new SleepTaskParams(Math.max(0L, Long.parseLong(trimmed)), DEFAULT_STEPS);
        } catch (Exception e) {
            log.warn("[demo] 解析 demo-sleep 任务参数失败: {}", params);
            throw new SfcJsonException(400, "无效的 demo-sleep 任务参数: " + params
                    + "（支持毫秒数或 JSON {millis, steps}）");
        }
    }

    /**
     * 把参数值解析为 long，无法识别时使用默认值。
     *
     * @param value        参数值（Number 或字符串）
     * @param defaultValue 默认值
     * @return 解析结果
     */
    private static long parseLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString().trim());
    }

    /**
     * demo-sleep 任务参数。
     *
     * @param millis 总耗时毫秒数
     * @param steps  总步数（决定进度分段）
     */
    private record SleepTaskParams(long millis, int steps) {
    }
}
