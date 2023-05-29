package com.xiaotao.saltedfishcloud.aspect;

import com.xiaotao.saltedfishcloud.annotations.ClusterScheduleJob;
import com.xiaotao.saltedfishcloud.dao.jpa.ScheduleJobRecordRepo;
import com.xiaotao.saltedfishcloud.model.po.ScheduleJobRecord;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 集群模式下的定时任务调度器，用于确保同一个定时任务不会被并行执行和提前执行<br>
 * 缺陷：对于固定间隔的定时任务，原获得执行权的节点会一直获得执行权，一旦这个节点掉线，定时任务会被延期执行直到其他节点获得了执行权。
 */
@Aspect
@Component
@Slf4j
public class ClusterScheduleJobDispatch {
    private final static String LOG_PREFIX = "[定时任务调度器]";
    @Autowired
    private ScheduleJobRecordRepo scheduleJobRecordRepo;

    /**
     * 任务执行日期记录，也作为乐观锁版本号
     */
    private final Map<String, Long> lastExecuteRecord = new ConcurrentHashMap<>();

    @Around("@annotation(clusterScheduleJob)")
    @Async
    public CompletableFuture<Object> checkIsExecutable(ProceedingJoinPoint joinPoint, ClusterScheduleJob clusterScheduleJob) throws Throwable {

        String jobName = clusterScheduleJob.value();
        Long lastDate = lastExecuteRecord.get(jobName);
        log.debug("{}检查定时任务{}在本地的执行记录, 结果为: {}", LOG_PREFIX, jobName, lastDate);

        // 获取本地缓存的上次执行日期，如果能获取到则说明本地触发过该任务的执行，通过乐观锁机制获取执行权即可。
        // 如果获取不到则说明先前未被执行到，需要拉取记录或创建初始记录
        long now = System.currentTimeMillis();
        if (lastDate == null) {
            // 通过拉取数据库 或 创建记录的方式更新本地的执行记录
            while (!fetchNewestExecuteDate(jobName) && !tryCreateJobRecord(jobName, clusterScheduleJob.describe())) {
                log.warn("{}任务{}的执行记录拉取和创建失败，准备重试", LOG_PREFIX, jobName);
                Thread.sleep(500);
            }
            lastDate = lastExecuteRecord.get(jobName);
        }

        // 乐观锁机制检测是否抢占执行权成功
        boolean isSuccess = scheduleJobRecordRepo.updateExecuteDate(jobName, now, lastDate) > 0;

        // 不成功则拉取最新的执行记录更新到本地，结束流程
        if (!isSuccess) {
            log.debug("{}没能获得定时任务{}的执行权, 跳过", LOG_PREFIX, jobName);
            fetchNewestExecuteDate(jobName);
            return CompletableFuture.completedFuture(null);
        } else {
            log.debug("{}成功获得定时任务{}的执行权", LOG_PREFIX, jobName);
        }

        lastExecuteRecord.put(jobName, now);
        return CompletableFuture.completedFuture(joinPoint.proceed(joinPoint.getArgs()));
    }

    /**
     * 拉取数据库最新的执行日期，并更新到本地记录
     */
    private boolean fetchNewestExecuteDate(String jobName) {
        ScheduleJobRecord jobRecord = scheduleJobRecordRepo.getByJobName(jobName);
        if (jobRecord != null) {
            lastExecuteRecord.put(jobRecord.getJobName(), jobRecord.getLastExecuteDate());
            return true;
        } else {
            return false;
        }
    }

    /**
     * 尝试创建一个新的定时任务执行记录，并将其执行日期更新到本地记录
     */
    private boolean tryCreateJobRecord(String jobName, String describe) {
        long now = System.currentTimeMillis();
        ScheduleJobRecord jobRecord = new ScheduleJobRecord();
        jobRecord.setJobName(jobName);
        jobRecord.setJobDescribe(describe);
        jobRecord.setLastExecuteDate(now);
        try {
            scheduleJobRecordRepo.save(jobRecord);
            lastExecuteRecord.put(jobName, now);
            log.debug("{}为定时任务{}创建初始执行记录成功", LOG_PREFIX, jobName);
            return true;
        } catch (DataIntegrityViolationException ignore) {
            log.debug("{}为定时任务{}创建初始执行记录失败", LOG_PREFIX, jobName);
            return false;
        }
    }
}
