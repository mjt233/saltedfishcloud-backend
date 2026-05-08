package com.sfc.task.prog;

import com.xiaotao.saltedfishcloud.cache.CacheKeyPrefixes;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class ProgressDetectorImpl implements ProgressDetector {
    private final Map<String, ProgressProvider> entityMap = new ConcurrentHashMap<>();
    private final static String LOG_TITLE = "[Progress Detector]";

    @Autowired
    private CacheService cacheService;


    private String getRecordKey(String id) {
        return CacheKeyPrefixes.TASK_PROGRESS + id;
    }

    @Override
    public ProgressRecord getRecord(String id) {
        final Object o = cacheService.get(getRecordKey(id));
        if (o == null) {
            return null;
        } else {
            return (ProgressRecord)o;
        }
    }

    @Override
    public void addObserve(ProgressProvider progressProvider,@NonNull String id) {
        log.debug("{}添加进度速度检测任务：{}", LOG_TITLE, id);
        entityMap.put(id, progressProvider);
    }

    @Override
    public boolean removeObserve(String id) {
        log.debug("{}移除进度速度检测任务：{}", LOG_TITLE, id);
        boolean res = cacheService.delete(getRecordKey(id));
        entityMap.remove(id);
        return res;
    }

    /**
     * 更新速度并移除不再需要检测的实例
     */
    @Scheduled(fixedRate = 1000)
    protected void update() {
        Iterator<Map.Entry<String, ProgressProvider>> iterator = entityMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ProgressProvider> mapEntity = iterator.next();
            String id = mapEntity.getKey();
            ProgressProvider provider = mapEntity.getValue();
            if (provider.isStop()) {
                removeObserve(id);
                iterator.remove();
                continue;
            }
            final ProgressRecord record = provider.getProgressRecord();
            final ProgressRecord lastRecord = getRecord(id);
            if (record != null) {
                final long newLoaded = record.getLoaded() - (lastRecord == null ? 0 : lastRecord.getLoaded());
                final long useTime = System.currentTimeMillis() - record.getLastUpdateTime();
                final long speedPreSecond = (newLoaded / (useTime == 0 ? 1 : useTime))*1000;
                provider.updateSpeed(speedPreSecond);
                if (log.isDebugEnabled()) {
                    log.debug("{}任务进度速度更新{}：{}/s 进度：{}%",
                            LOG_TITLE,
                            id,
                            StringUtils.getFormatSize(speedPreSecond),
                            String.format("%.2f", (provider.getProgressRecord().getLoaded() / (double)provider.getProgressRecord().getTotal()) * 100)
                    );
                }
            }
            cacheService.set(getRecordKey(id), provider.getProgressRecord(), 10, TimeUnit.SECONDS);
        }
    }
}
