package com.xiaotao.saltedfishcloud.service.mountpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.dao.jpa.MountPointRepo;
import com.xiaotao.saltedfishcloud.dao.redis.RedisDao;
import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnsupportedFileSystemProtocolException;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
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
    public MountPoint findById(long id) {
        MountPoint mountPoint = mountPointRepo.findById(id).orElse(null);
        if (mountPoint == null) {
            return null;
        }
        String key = "mp_uid:" + mountPoint.getUid() + "_id:" + id;
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.put(key, mountPoint);
        }

        return mountPoint;
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
        keys.addAll(redisDao.scanKeys("mp_uid:" + uid + "*"));

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
    public void saveMountPoint(@Validated MountPoint mountPoint) throws IOException, FileSystemParameterException {
        String protocol = mountPoint.getProtocol();
        if(!fileSystemManager.isSupportedProtocol(protocol)) {
            throw new UnsupportedFileSystemProtocolException(protocol);
        }
        if (mountPoint.getId() == null) {
            createMountPoint(mountPoint);
        } else {
            changeMountPoint(mountPoint);
        }

        clearCache(mountPoint.getUid());
    }

    private void changeMountPoint(MountPoint mountPoint) {
        MountPoint dbObj = mountPointRepo.findById(mountPoint.getId()).orElseThrow(() -> new JsonException(CommonError.RESOURCE_NOT_FOUND));
        if (!Objects.equals(dbObj.getUid(), mountPoint.getUid())) {
            throw new IllegalArgumentException("不能修改uid");
        }
        if (!Objects.equals(dbObj.getNid(), mountPoint.getNid())) {
            throw new IllegalArgumentException("不能修改nid");
        }
        String originName = dbObj.getName();
        if (!Objects.equals(originName, mountPoint.getName())) {
            String path = nodeService.getPathByNode(Math.toIntExact(mountPoint.getUid()), mountPoint.getNid());
            try {
                fileRecordService.rename(Math.toIntExact(mountPoint.getUid()), path, originName, mountPoint.getName());
            } catch (NoSuchFileException e) {
                throw new JsonException(e.getMessage());
            }
        }
        mountPointRepo.save(mountPoint);
    }

    /**
     * 创建挂载点
     * @param mountPoint    待创建的挂载点
     */
    private void createMountPoint(MountPoint mountPoint) throws JsonProcessingException, FileSystemParameterException {
        String path = StringUtils.appendPath(nodeService.getPathByNode(Math.toIntExact(mountPoint.getUid()), mountPoint.getNid()), mountPoint.getName());
        if (!StringUtils.hasText(mountPoint.getNid())) {
            mountPoint.setNid(mountPoint.getUid().toString());
        }
        log.debug("{}创建挂载点:{}", LOG_PREFIX, path);
        if(fileSystemManager.getMainFileSystem().exist(Math.toIntExact(mountPoint.getUid()), path)) {
            throw new JsonException(FileSystemError.FILE_EXIST);
        }
        boolean protocolIsAvailable = fileSystemManager.listPublicFileSystem().stream().anyMatch(e -> e.getDescribe().getProtocol().equals(mountPoint.getProtocol()));
        if (!protocolIsAvailable) {
            throw new JsonException("找不到或无权创建对应的文件系统协议");
        }
        DiskFileSystem targetFileSystem = fileSystemManager.getFileSystem(mountPoint.getProtocol(), MapperHolder.parseJsonToMap(mountPoint.getParams()));
        if (targetFileSystem == null) {
            throw new JsonException("无法挂载目标文件系统");
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
