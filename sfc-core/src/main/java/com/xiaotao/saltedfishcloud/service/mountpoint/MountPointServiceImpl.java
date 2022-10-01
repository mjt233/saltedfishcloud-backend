package com.xiaotao.saltedfishcloud.service.mountpoint;

import com.xiaotao.saltedfishcloud.dao.jpa.MountPointRepo;
import com.xiaotao.saltedfishcloud.dao.redis.RedisDao;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnsupportedFileSystemProtocolException;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MountPointServiceImpl implements MountPointService {
    private final static String LOG_PREFIX = "[挂载点]";
    private final static String CACHE_NAME = "mount_point";
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private MountPointRepo mountPointRepo;
    @Autowired
    private DiskFileSystemManager fileSystemManager;
    @Autowired
    private NodeService nodeService;
    @Autowired
    private FileRecordService fileRecordService;
    @Autowired
    private RedisDao redisDao;


    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'mp_' + #uid")
    public List<MountPoint> findByUid(long uid) {
        return mountPointRepo.findByUid(uid);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CACHE_NAME, key = "'mp_' + #uid"),
            @CacheEvict(cacheNames = CACHE_NAME, key = "'full_mp_' + #uid")
    })
    @Transactional(rollbackFor = Exception.class)
    public void remove(@Validated @UID long uid, long id) {
        Set<String> keys = redisDao.scanKeys("tree_mp" + uid + ":*");

        // 移除缓存
        if (keys != null && !keys.isEmpty()) {
            for (String key : keys) {
                Objects.requireNonNull(cacheManager.getCache(CACHE_NAME)).evict(key);
            }
        }
        MountPoint mountPoint = mountPointRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("不存在该id"));

        String path = nodeService.getPathByNode((int) uid, mountPoint.getNid());
        mountPointRepo.deleteById(id);
        try {
            fileRecordService.deleteRecords((int) uid, path, Collections.singletonList(mountPoint.getName()));
            nodeService.deleteNodes((int) uid, Collections.singletonList(mountPoint.getNid()));
        } catch (NoSuchFileException e) {
            throw new JsonException(e.getMessage());
        }

    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CACHE_NAME, key = "'mp_' + #mountPoint.uid"),
            @CacheEvict(cacheNames = CACHE_NAME, key = "'full_mp_' + #mountPoint.uid"),
            @CacheEvict(cacheNames = CACHE_NAME, key = "'tree_mp_' + #mountPoint.uid + ':' + #mountPoint.path")
    })
    @Transactional(rollbackFor = Exception.class)
    public void saveMountPoint(@Validated MountPoint mountPoint) {
        String protocol = mountPoint.getProtocol();
        if(!fileSystemManager.isSupportedProtocol(protocol)) {
            throw new UnsupportedFileSystemProtocolException(protocol);
        }
        if (mountPoint.getId() == null) {
            log.debug("{}创建挂载点:{}", LOG_PREFIX, mountPoint.getName());
            // 主表保存
            mountPointRepo.save(mountPoint);
            // 文件表保存
            nodeService.addMountPointNode(mountPoint);
            FileInfo fileInfo = new FileInfo(mountPoint.getName(), -1, FileInfo.TYPE_DIR, "", System.currentTimeMillis(), null);
            Date now = new Date();
            fileInfo.setUid(Math.toIntExact(mountPoint.getUid()));
            fileInfo.setNode(mountPoint.getNid());
            fileInfo.setCreatedAt(now);
            fileInfo.setUpdatedAt(now);
            fileInfo.setMd5(mountPoint.getNid());
            fileRecordService.insert(fileInfo);
        } else {
            throw new UnsupportedOperationException("暂时不支持挂载点修改");
        }

    }

    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'full_mp_' + #uid")
    public Map<String, MountPoint> findMountPointPathByUid(long uid) {
        List<MountPoint> mountPointList = findByUid(uid);
        if (mountPointList == null || mountPointList.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, MountPoint> mountPointMap = mountPointList.stream().collect(Collectors.toMap(
                e -> nodeService.getPathByNode(e.getUid().intValue(), e.getNid()),
                Function.identity(),
                (oldVal, newVal) -> {
                    log.warn("{}存在挂载路径冲突：{} 与 {}", LOG_PREFIX, oldVal.getNid(), newVal.getNid());
                    return oldVal;
                }
        ));
        for (Map.Entry<String, MountPoint> entry : mountPointMap.entrySet()) {
            String parentPath = entry.getKey();
            MountPoint mountPoint = entry.getValue();
            mountPoint.setPath(StringUtils.appendPath(parentPath, mountPoint.getName()));
            mountPoint.setParentPath(parentPath);
        }
        return mountPointMap;

    }
}
