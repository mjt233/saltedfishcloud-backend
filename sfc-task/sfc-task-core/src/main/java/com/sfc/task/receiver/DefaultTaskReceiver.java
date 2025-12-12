package com.sfc.task.receiver;

import com.sfc.task.AsyncTaskConstants;
import com.sfc.task.AsyncTaskReceiver;
import com.sfc.task.constants.AsyncTaskMQTopic;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.xiaotao.saltedfishcloud.service.mq.MQService;
import com.xiaotao.saltedfishcloud.utils.db.JpaLambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class DefaultTaskReceiver implements AsyncTaskReceiver {
    private final AsyncTaskRecordRepo asyncTaskRecordRepo;
    private final MQService mqService;

    private final Object monitor = new Object();
    private boolean isInterrupted = true;

    @Override
    public void start() {
        if (!isInterrupted) {
            log.warn("已开始接受任务，已忽略重复调用start");
            return;
        }
        this.isInterrupted = false;
        mqService.subscribeBroadcast(AsyncTaskMQTopic.TASK_PUBLISH, r -> {
            synchronized (monitor) {
                monitor.notify();
            }
        });
    }

    @Override
    public void interrupt() {
        isInterrupted = true;
    }

    @Override
    public AsyncTaskRecord get() {
        while (true) {
            if (isInterrupted) {
                return null;
            }
            AsyncTaskRecord asyncTaskRecord = fetchAndAcceptOne();
            if (asyncTaskRecord != null) {
                return asyncTaskRecord;
            }
            try {
                synchronized (monitor) {
                    monitor.wait(10000);
                }
            } catch (InterruptedException ignored) {
                log.debug("monitor.wait被中断");
            }
        }
    }

    /**
     * 安全的获取并接受一个任务
     * 需要保证并发安全，避免多个接收器获取同一个任务
     */
    protected AsyncTaskRecord fetchAndAcceptOne() {
        AsyncTaskRecord task = asyncTaskRecordRepo.findOne(JpaLambdaQueryWrapper.get(AsyncTaskRecord.class)
                        .eq(AsyncTaskRecord::getStatus, AsyncTaskConstants.Status.WAITING)
                        .orderByAsc(AsyncTaskRecord::getCreateAt)
                        .build())
                .orElse(null);
        if (task == null) {
            return null;
        }

        // 尝试接受任务（原子操作）
        int updated = asyncTaskRecordRepo.updateStatus(
                task.getId(),
                AsyncTaskConstants.Status.RUNNING,
                AsyncTaskConstants.Status.WAITING
        );
        if (updated == 0) {
            log.warn("抓取异步任务竞争失败 {}-{}", task.getName(), task.getId());
            return null;
        } else {
            log.info("成功抓取一个异步任务 {}-{}", task.getName(), task.getId());
            return task;
        }
    }

    @Override
    public List<AsyncTaskRecord> listQueue() {
        return asyncTaskRecordRepo.findAll(JpaLambdaQueryWrapper.get(AsyncTaskRecord.class)
                        .eq(AsyncTaskRecord::getStatus, AsyncTaskConstants.Status.WAITING)
                        .orderByAsc(AsyncTaskRecord::getCreateAt)
                .build());
    }
}
