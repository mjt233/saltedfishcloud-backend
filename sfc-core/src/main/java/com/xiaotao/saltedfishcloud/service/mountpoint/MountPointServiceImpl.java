package com.xiaotao.saltedfishcloud.service.mountpoint;

import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.dao.jpa.MountPointRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnsupportedFileSystemProtocolException;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
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


    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'mp_' + #uid")
    public List<MountPoint> findByUid(long uid) {
        return mountPointRepo.findByUid(uid);
    }

    /**
     * 清空用户的挂载点缓存
     * @param uid   用户id
     */
    private void clearCache(long uid) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            log.warn("{}移除用户{}的缓存失败，无法获取Cache对象", LOG_PREFIX, uid);
            return;
        }
        Set<String> keys = new HashSet<>();
        // 移除用户挂载点列表的缓存
        keys.add("mp_" + uid);
        // 移除挂载路径映射缓存
        keys.add("full_mp_" + uid);
        for (String key : keys) {
            cache.evict(key);
        }
    }

    @Override
    public void batchRemove(long uid, Collection<Long> ids) {
        clearCache(uid);
        mountPointRepo.batchDeleteById(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(@Validated @UID long uid, long id) {
        MountPoint mountPoint = mountPointRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("不存在该id"));

        clearCache(uid);
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
    @Transactional(rollbackFor = Exception.class)
    public void saveMountPoint(@Validated MountPoint mountPoint) {
        clearCache(mountPoint.getUid());
        String protocol = mountPoint.getProtocol();
        if(!fileSystemManager.isSupportedProtocol(protocol)) {
            throw new UnsupportedFileSystemProtocolException(protocol);
        }
        if (mountPoint.getId() == null) {
            String path = StringUtils.appendPath(nodeService.getPathByNode(Math.toIntExact(mountPoint.getUid()), mountPoint.getNid()), mountPoint.getName());

            log.debug("{}创建挂载点:{}", LOG_PREFIX, path);
            if(fileSystemManager.getMainFileSystem().exist(Math.toIntExact(mountPoint.getUid()), path)) {
               throw new JsonException(FileSystemError.FILE_EXIST);
            }

            // 主表保存
            mountPointRepo.save(mountPoint);
            // 文件表保存
            String newNodeId = nodeService.addMountPointNode(mountPoint);
            FileInfo fileInfo = new FileInfo(mountPoint.getName(), -1, FileInfo.TYPE_DIR, "", System.currentTimeMillis(), null);
            Date now = new Date();
            fileInfo.setUid(Math.toIntExact(mountPoint.getUid()));
            fileInfo.setNode(mountPoint.getNid());
            fileInfo.setCreatedAt(now);
            fileInfo.setUpdatedAt(now);
            fileInfo.setMountId(mountPoint.getId());
            fileInfo.setMd5(newNodeId);
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
                e -> StringUtils.appendPath(nodeService.getPathByNode(e.getUid().intValue(), e.getNid()), e.getName()),
                Function.identity(),
                (oldVal, newVal) -> {
                    log.warn("{}存在挂载路径冲突：{} 与 {}", LOG_PREFIX, oldVal.getNid(), newVal.getNid());
                    return oldVal;
                }
        ));
        for (Map.Entry<String, MountPoint> entry : mountPointMap.entrySet()) {
            String fullPath = entry.getKey();
            MountPoint mountPoint = entry.getValue();
            mountPoint.setPath(fullPath);
            mountPoint.setParentPath(PathUtils.getParentPath(fullPath));
        }
        return mountPointMap;

    }
}
