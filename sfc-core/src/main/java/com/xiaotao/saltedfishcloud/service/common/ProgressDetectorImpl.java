package com.xiaotao.saltedfishcloud.service.common;

import com.xiaotao.saltedfishcloud.common.prog.ProgressDetector;
import com.xiaotao.saltedfishcloud.common.prog.ProgressProvider;
import com.xiaotao.saltedfishcloud.common.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedList;

@Data
class HandlerEntity {
    private ProgressProvider progressProvider;
    private String id;

    public HandlerEntity(ProgressProvider progressProvider, String id) {
        this.progressProvider = progressProvider;
        this.id = id;
    }
}

@Slf4j
@Component
public class ProgressDetectorImpl implements ProgressDetector {
    private final LinkedList<HandlerEntity> entityList;
    private final static String LOG_TITLE = "[Progress Detector]";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public ProgressDetectorImpl() {
        this.entityList = new LinkedList<>();
    }

    private String getRecordKey(String id) {
        return "xyy::progress::" + id;
    }

    @Override
    public ProgressRecord getRecord(String id) {
        final Object o = redisTemplate.opsForValue().get(getRecordKey(id));
        if (o == null) {
            return null;
        } else {
            return (ProgressRecord)o;
        }
    }

    @Override
    public void addObserve(ProgressProvider progressProvider,@NonNull String id) {
        entityList.add(new HandlerEntity(progressProvider, id));
    }

    /**
     * 更新速度并移除不再需要检测的实例
     */
    @Scheduled(fixedRate = 1000)
    protected void update() {
        synchronized (entityList) {
            final Iterator<HandlerEntity> iterator = entityList.iterator();
            while (iterator.hasNext()) {
                final HandlerEntity entity = iterator.next();
                final ProgressProvider provider = entity.getProgressProvider();
                if (provider.isStop()) {
                    log.debug("{}移除进度速度检测任务：{}", LOG_TITLE, entity.getId());
                    iterator.remove();
                    redisTemplate.delete(entity.getId());
                    continue;
                }
                final ProgressRecord record = provider.getProgressRecord();
                final ProgressRecord lastRecord = getRecord(entity.getId());
                if (record != null) {
                    final long newLoaded = record.getLoaded() - (lastRecord == null ? 0 : lastRecord.getLoaded());
                    final long useTime = System.currentTimeMillis() - record.getLastUpdateTime();
                    final long speedPreMs = newLoaded / useTime;
                    provider.updateSpeed(speedPreMs);
                    if (log.isDebugEnabled()) {
                        log.debug("{}任务进度速度更新{}：{}/s 进度：{}%",
                                LOG_TITLE,
                                entity.getId(),
                                StringUtils.getFormatSize(speedPreMs*1000),
                                String.format("%.2f", (provider.getProgressRecord().getLoaded() / (double)provider.getProgressRecord().getTotal()) * 100)
                        );
                    }
                }
                redisTemplate.opsForValue().set(getRecordKey(entity.getId()), provider.getProgressRecord(), Duration.ofSeconds(10));
            }
        }
    }
}
