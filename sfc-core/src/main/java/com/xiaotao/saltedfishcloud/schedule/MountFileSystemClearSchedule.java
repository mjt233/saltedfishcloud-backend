package com.xiaotao.saltedfishcloud.schedule;

import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.mountpoint.MountPointService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 挂载文件系统连接自动清理定时任务
 */
@Component
public class MountFileSystemClearSchedule {
    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private MountPointService mountPointService;

    /**
     * 每30分钟清理一次挂载文件系统失效的缓存
     */
    @Scheduled(fixedRate = 1000 * 60 * 30)
    public void autoClear() {
        Map<String, List<MountPoint>> mountGroup = mountPointService.listAll().stream().collect(Collectors.groupingBy(MountPoint::getProtocol));
        for (DiskFileSystemFactory factory : diskFileSystemManager.listAllFileSystem()) {
            List<MountPoint> mountPoints = mountGroup.get(factory.getDescribe().getProtocol());
            if (mountPoints == null || mountPoints.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> params = mountPoints.stream().map(MountPoint::getParams)
                    .filter(Objects::nonNull)
                    .map(e -> {
                        try {
                            return MapperHolder.parseJsonToMap(e);
                        } catch (IOException err) {
                            err.printStackTrace();
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            factory.clearCache(params);
        }
    }
}
