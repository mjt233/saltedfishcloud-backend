package com.xiaotao.saltedfishcloud.service.breakpoint.manager.impl;

import com.xiaotao.saltedfishcloud.service.breakpoint.PartParser;
import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskMetadata;
import com.xiaotao.saltedfishcloud.service.breakpoint.exception.TaskNotFoundException;
import com.xiaotao.saltedfishcloud.service.breakpoint.manager.TaskManager;
import com.xiaotao.saltedfishcloud.service.breakpoint.manager.impl.utils.TaskStorePath;
import com.xiaotao.saltedfishcloud.service.breakpoint.merge.MergeInputStream;
import com.xiaotao.saltedfishcloud.service.breakpoint.merge.MultipleFileMergeInputStreamGenerator;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceProvider;
import com.xiaotao.saltedfishcloud.service.file.TempStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 断点续传任务管理器，管理任务的创建，查询，删除和文件块的存储
 */
@Slf4j
public class DefaultTaskManager implements TaskManager {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StoreServiceProvider storeServiceProvider;

    private String getMetaRedisKey(String id) {
        return "xyy::breakpoint::" + id;
    }

    private String getFinishPartKey(String id) {
        return "xyy::breakpoint::finish::" + id;
    }

    /**
     * 创建断点续传任务
     * @param info  任务元数据
     * @return      创建成功后的任务ID
     * @throws IOException 任务数据存储目录无法写入
     */
    @Override
    public String createTask(TaskMetadata info) throws IOException {
        String id = UUID.randomUUID().toString();
        info.setTaskId(id);
        String taskDir = TaskStorePath.getRoot(id);
        storeServiceProvider.getTempStoreService().mkdirs(taskDir);
        redisTemplate.opsForValue().set(getMetaRedisKey(id), info, Duration.ofDays(7));
        return id;
    }

    /**
     * 查询任务信息
     * @param id 任务ID
     * @return 任务信息，若任务不存在则返回Null
     * @throws IOException 目录读取出错
     */
    @Override
    public TaskMetadata queryTask(String id) throws IOException {

        return (TaskMetadata) redisTemplate.opsForValue().get(getMetaRedisKey(id));
    }

    /**
     * 清理指定的任务数据
     * @param id    任务ID
     * @throws IOException 目录不可写或任务不存在
     */
    @Override
    public void clear(String id) throws IOException {
        if(queryTask(id) == null) {
            throw new TaskNotFoundException(id);
        }
        String taskPath = TaskStorePath.getRoot(id);
        storeServiceProvider.getTempStoreService().delete(taskPath);
        redisTemplate.delete(getMetaRedisKey(id));
        redisTemplate.delete(getFinishPartKey(id));
    }

    /**
     * 保存部分的断点续传任务文件片段
     * @param id        任务ID
     * @param part      文件块编号（从1开始）
     * @param stream    文件流
     */
    @Override
    public void save(String id, String part, InputStream stream) throws IOException {
        String root = TaskStorePath.getRoot(id);
        if (queryTask(id) == null) {
            throw new TaskNotFoundException(id);
        }

        final String redisKey = getFinishPartKey(id);
        int[] parts = PartParser.parse(part);
        TaskMetadata taskInfo = queryTask(id);
        for (int i : parts) {
            long size = taskInfo.getPartSize(i);
            final String partFile = TaskStorePath.getPartFile(id, i);
            try(final OutputStream out = storeServiceProvider.getTempStoreService().newOutputStream(partFile)) {
                long l = StreamUtils.copyRange(stream, out, 0, size - 1);
                log.debug("写入断点续传文件块，文件名：{} 编号：{}  大小：{}",taskInfo.getFileName() ,i, l);
                redisTemplate.opsForSet().add(redisKey, i);
            }
        }
        stream.close();
    }

    @Override
    public List<Integer> getFinishPart(String id) throws IOException {
        Set<Object> finish = redisTemplate.opsForSet().members(getFinishPartKey(id));
        if (finish == null) {
            return Collections.emptyList();
        }
        return finish.stream().map(e -> (Integer)e).sorted(Comparator.comparingInt(o -> o)).collect(Collectors.toList());
    }

    @Override
    public boolean isFinish(String id) throws IOException {
        try {
            TaskMetadata taskMetadata = queryTask(id);
            return getFinishPart(id).size() == taskMetadata.getChunkCount();
        } catch (IOException e) {
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
            return false;
        }
    }

    @Override
    public MergeInputStream getMergeInputStream(String id) throws IOException {
        if (!this.isFinish(id)) {
            throw new IllegalStateException("断点续传任务未完成,文件块不完整");
        }


        List<Integer> finishPart = getFinishPart(id);
        Resource[] paths = new Resource[finishPart.size()];
        final TempStoreService tempStoreService = storeServiceProvider.getTempStoreService();
        for (Integer integer : finishPart) {
            paths[integer - 1] =  tempStoreService.getResource(TaskStorePath.getPartFile(id, integer));
        }
        return new MergeInputStream(new MultipleFileMergeInputStreamGenerator(paths));
    }
}
