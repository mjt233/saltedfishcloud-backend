package com.sfc.task.receiver;

import com.sfc.task.AsyncTaskConstants;
import com.sfc.task.AsyncTaskReceiver;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.Builder;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 基于redis的简单异步任务队列接收器
 * @deprecated 已改用 {@link DefaultTaskReceiver}。任务改为从数据库中接收，消息队列只用来通知拉取任务，不再用消息队列本身接收完整参数。
 */
@Builder
@Deprecated
public class RedisTaskReceiver implements AsyncTaskReceiver {
    private final RedisTemplate<String, Object> redisTemplate;

    private boolean isInterrupt;

    @Override
    public AsyncTaskRecord get() {
        while (true) {
            if (isInterrupt) {
                return null;
            }
            try {
                Object o = redisTemplate.opsForList().rightPop(AsyncTaskConstants.RedisKey.TASK_QUEUE, 3, TimeUnit.SECONDS);
                if (o == null) {
                    Thread.sleep(100);
                    continue;
                }
                if (o instanceof String) {
                    return MapperHolder.parseJson((String) o, AsyncTaskRecord.class);
                } else if (o instanceof AsyncTaskRecord) {
                    return (AsyncTaskRecord) o;
                } else {
                    throw new IllegalArgumentException("任务反序列化失败");
                }
            } catch (Throwable e) {
                if (!this.isInterrupt) {
                    throw new RuntimeException("任务接受出错:" + e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public void start() {
        isInterrupt = false;
    }

    @Override
    public void interrupt() {
        isInterrupt = true;
    }

    @Override
    public List<AsyncTaskRecord> listQueue() {
        List<Object> range = redisTemplate.opsForList().range(AsyncTaskConstants.RedisKey.TASK_QUEUE, 0, 100);
        if (range == null) {
            return Collections.emptyList();
        } else {
            return range.stream()
                    .map(e -> {
                        if (e instanceof AsyncTaskRecord) {
                            return (AsyncTaskRecord) e;
                        } else if (e instanceof String) {
                            try {
                                return MapperHolder.parseJson((String) e, AsyncTaskRecord.class);
                            } catch (IOException ex) {
                                ex.printStackTrace();
                                return null;
                            }
                        } else {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }
}
