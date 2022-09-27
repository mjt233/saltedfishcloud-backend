package com.xiaotao.saltedfishcloud.service.mountpoint;

import com.xiaotao.saltedfishcloud.dao.jpa.MountPointRepo;
import com.xiaotao.saltedfishcloud.exception.UnsupportedFileSystemProtocolException;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MountPointServiceImpl implements MountPointService {
    private final static String LOG_PREFIX = "[挂载点]";
    private final static String CACHE_NAME = "mount_point";
    @Autowired
    private MountPointRepo mountPointRepo;
    @Autowired
    private DiskFileSystemManager fileSystemManager;
    @Autowired
    private NodeService nodeService;

    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'mp_' + #uid")
    public List<MountPoint> findByUid(long uid) {
        return mountPointRepo.findByUid(uid);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CACHE_NAME, key = "'mp_' + #mountPoint.uid"),
            @CacheEvict(cacheNames = CACHE_NAME, key = "'full_mp_' + #mountPoint.uid")
    })
    public void saveMountPoint(MountPoint mountPoint) {
        String protocol = mountPoint.getProtocol();
        if(fileSystemManager.isSupportedProtocol(protocol)) {
            throw new UnsupportedFileSystemProtocolException(protocol);
        }
        mountPointRepo.save(mountPoint);
    }

    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'full_mp_' + #uid")
    public Map<String, MountPoint> findMountPointPathByUid(long uid) {
        Map<String, MountPoint> mountPointMap = findByUid(uid).stream().collect(Collectors.toMap(
                e -> nodeService.getPathByNode(e.getUid().intValue(), e.getNid()),
                Function.identity(),
                (oldVal, newVal) -> {
                    log.warn("{}存在挂载路径冲突：{} 与 {}", LOG_PREFIX, oldVal.getNid(), newVal.getNid());
                    return oldVal;
                }
        ));
        for (Map.Entry<String, MountPoint> entry : mountPointMap.entrySet()) {
            entry.getValue().setPath(entry.getKey());
        }
        return mountPointMap;

    }
}
