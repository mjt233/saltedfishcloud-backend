package com.sfc.task.schedule;

import com.sfc.task.AsyncTaskConstants;
import com.sfc.task.AsyncTaskExecutor;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.xiaotao.saltedfishcloud.annotations.ClusterScheduleJob;
import com.xiaotao.saltedfishcloud.dao.redis.RedisDao;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务状态检查器，确保任务执行状态在线。并实现异步任务故障转移。
 */
@Component
@Slf4j
public class AsyncTaskScheduleChecker implements InitializingBean {
    private final static String HOLD_KEY_PREFIX = "async_task_hold_";

    @Autowired
    private AsyncTaskExecutor executor;

    @Autowired
    private AsyncTaskRecordRepo asyncTaskRecordRepo;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private AsyncTaskManager asyncTaskManager;

    @Autowired
    private RedisDao redisDao;

    private final Long nodeId = IdUtil.getId();

    @Override
    public void afterPropertiesSet() throws Exception {
        // 任务开始时打上保持标记
        executor.addTaskStartListener(record -> {
            boolean success = setHold(record.getId());
            log.warn("任务{}-{}启动被忽略，未取得执行权", record.getId(), record.getName());
            if (!success) {
                record.setStatus(AsyncTaskConstants.Status.CANCEL);
            }
        });

        // 任务退出时移除保持标记
        executor.addTaskExitListener(record -> redisTemplate.delete(getHoldKey(record.getId())));
    }

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
            republishTask();
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

        // 重新发布离线和未在队列中的任务
        republishTask();
    }

    /**
     * 重新发布需要再次执行的任务到队列中
     */
    public void republishTask() {
        // 1. 获取离线任务
        List<AsyncTaskRecord> republishTaskList = new ArrayList<>(
                Optional.ofNullable(asyncTaskRecordRepo.listOfflineTask())
                        .orElseGet(Collections::emptyList)
        );

        // 2. 获取未在队列中的任务，若队列为空，则获取数据库中状态为等待中的任务重新发布。
        // 这些任务可能是曾经发布到队列但队列数据丢失了。
        List<AsyncTaskRecord> queue = executor.getReceiver().listQueue();
        if (queue == null || queue.isEmpty()) {
            // 获取半分钟之前创建的任务重新发布到队列
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.SECOND, -30);
            List<AsyncTaskRecord> waitingTasks = asyncTaskRecordRepo.findByStatusAndCreateAt(AsyncTaskConstants.Status.WAITING, calendar.getTime());

            // 获取存在运行标记的任务id，有运行标记的任务暂时不重新发布
            Set<Long> runningKeyIdSet = Optional
                    .ofNullable(redisDao.scanKeys(HOLD_KEY_PREFIX))
                    .orElseGet(Collections::emptySet)
                    .stream()
                    .map(e -> e.substring(HOLD_KEY_PREFIX.length()))
                    .map(Long::parseLong)
                    .collect(Collectors.toSet());

            if (waitingTasks != null && !waitingTasks.isEmpty()) {
                republishTaskList.addAll(waitingTasks.stream()
                        .filter(e -> !runningKeyIdSet.contains(e.getId()))
                        .collect(Collectors.toList())
                );
            }
        }


        if (republishTaskList.isEmpty()) {
            return;
        }

        // 3. 重新发布
        for (AsyncTaskRecord asyncTaskRecord : republishTaskList) {
            asyncTaskRecord.setStatus(AsyncTaskConstants.Status.WAITING);
            try {
                log.info("重新发布任务: {}", asyncTaskRecord.getId());
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
        return HOLD_KEY_PREFIX + taskId;
    }
}
