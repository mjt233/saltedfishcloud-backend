package com.sfc.task.schedule;

import com.sfc.task.AsyncTaskConstants;
import com.sfc.task.AsyncTaskExecutor;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.xiaotao.saltedfishcloud.annotations.ClusterScheduleJob;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 任务状态保持器，确保任务执行状态在线。并实现异步任务故障转移。
 */
@Component
@Slf4j
public class TaskStatusHolder  {
    @Autowired
    private AsyncTaskExecutor executor;

    @Autowired
    private AsyncTaskRecordRepo asyncTaskRecordRepo;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private AsyncTaskManager asyncTaskManager;

    private final Long nodeId = IdUtil.getId();

    /**
     * 保持任务在线，并剔除离线任务
     */
    @Async
    @Scheduled(fixedRate = 5000)
    public void hold() {
        List<Long> offlineIds = new ArrayList<>();
        for (Long taskId : executor.getRunningTask()) {
            try {
                String key = getHoldKey(taskId);
                if(!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                    setHold(taskId);
                } else {
                    Object holdNodeId = redisTemplate.opsForValue().get(key);
                    // 找不到记录保持的节点id记录 或 保持的节点id与当前不符时，放弃任务
                    if (holdNodeId == null || !Objects.equals(TypeUtils.toLong(holdNodeId), nodeId)) {
                        executor.giveUp(taskId);
                        offlineIds.add(taskId);
                    } else {
                        redisTemplate.expire(key, Duration.ofMinutes(2));
                    }
                }
            } catch (Throwable e) {
                log.error("异步任务状态保持出错: ", e);
            }
        }
        asyncTaskRecordRepo.setTaskOffline(offlineIds);
    }

    /**
     * 半分钟一次，确认运行中的任务存在执行标记
     */
    @Async
    @Scheduled(fixedRate = 30000)
    @ClusterScheduleJob("mark_task_offline")
    public void markTaskOffline() {
        List<AsyncTaskRecord> asyncTaskRecords = asyncTaskRecordRepo.listRunningTask();
        if (asyncTaskRecords == null || asyncTaskRecords.isEmpty()) {

            // 重新发布离线的任务
            republishOfflineTask();
            return;
        }
        List<Long> taskId = new ArrayList<>();
        for (AsyncTaskRecord asyncTaskRecord : asyncTaskRecords) {
            Long recordId = asyncTaskRecord.getId();
            String key = getHoldKey(recordId);
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                taskId.add(recordId);
                log.info("发现运行中的任务{}离线", recordId);
            }
        }

        // 标记任务离线
        if (!taskId.isEmpty()) {
            asyncTaskRecordRepo.setTaskOffline(taskId);
        }

        // 重新发布离线的任务
        republishOfflineTask();
    }

    /**
     * 重新发布离线任务
     */
    public void republishOfflineTask() {
        List<AsyncTaskRecord> asyncTaskRecords = asyncTaskRecordRepo.listOfflineTask();
        if (asyncTaskRecords == null || asyncTaskRecords.isEmpty()) {
            return;
        }
        for (AsyncTaskRecord asyncTaskRecord : asyncTaskRecords) {
            asyncTaskRecord.setStatus(AsyncTaskConstants.Status.WAITING);
            try {
                log.info("重新发布离线任务: {}", asyncTaskRecord.getId());
                asyncTaskManager.submitAsyncTask(asyncTaskRecord);
            } catch (Throwable e) {
                log.error("任务{}重发布失败", asyncTaskRecord.getId(), e);
            }
        }
    }

    private boolean setHold(long taskId) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(getHoldKey(taskId), nodeId, Duration.ofMinutes(2));
        return success != null ? success : false;
    }

    private String getHoldKey(long taskId) {
        return "async_task_hold_" + taskId;
    }
}
