package com.xiaotao.saltedfishcloud.service.mountpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.dao.jpa.MountPointRepo;
import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnsupportedFileSystemProtocolException;
import com.xiaotao.saltedfishcloud.model.param.MountPointSyncFileRecordParam;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.CrudServiceImpl;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.StorageFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.file.StorageRegistry;
import com.xiaotao.saltedfishcloud.utils.*;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.InputStreamSource;
import org.springframework.data.util.Lazy;
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
public class MountPointServiceImpl extends CrudServiceImpl<MountPoint, MountPointRepo> implements MountPointService {
    private final static String LOG_PREFIX = "[挂载点]";
    private final static String CACHE_NAME = "mount_point";
    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private MountPointRepo mountPointRepo;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private StorageRegistry storageRegistry;

    @Autowired
    private FileRecordService fileRecordService;

    @Override
    public List<MountPoint> listAll() {
        return mountPointRepo.findAll();
    }

    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'mp_' + #uid")
    public List<MountPoint> findByUid(long uid) {
        return mountPointRepo.findByUid(uid);
    }

    @Override
    public MountPoint findById(Long id) {
        MountPoint mountPoint = mountPointRepo.findById(id).orElse(null);
        if (mountPoint == null) {
            return null;
        }
        UIDValidator.validateWithException(mountPoint.getUid(), true);
        return mountPoint;
    }

    @Override
    public List<MountPoint> listByPath(long uid, String path) {
        return fileRecordService.getNodeByPath(uid, path)
                .map(node -> fileRecordService.listChildDirs(uid, node.getMd5(), -1)
                        .stream()
                        .map(FileInfo::getMountId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList())
                )
                .map(mountPointRepo::findByIds)
                .orElseGet(Collections::emptyList);
    }

    @Override
    public void clearCache(long uid) {
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
    @Transactional(rollbackFor = Exception.class)
    public void batchRemove(long uid, Collection<Long> ids) {
        List<MountPoint> mountPointList = mountPointRepo.findByIds(ids);
        if (mountPointList == null || mountPointList.isEmpty()) {
            return;
        }
        mountPointList.forEach(m -> UIDValidator.validateWithException(m.getUid(), true));
        mountPointList.forEach(this::clearFileRecord);
        mountPointList.forEach(this::clearFileSystemFactoryCache);
        clearCache(uid);
        mountPointRepo.batchDeleteById(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(@Validated @UID long uid, long id) {
        MountPoint mountPoint = mountPointRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("不存在该id"));
        clearFileSystemFactoryCache(mountPoint);
        clearFileRecord(mountPoint);
        clearCache(uid);
        String path = fileRecordService.getPathByNodeId(uid, mountPoint.getNid()).orElseThrow(() -> new JsonException(FileSystemError.FILE_NOT_FOUND));
        mountPointRepo.deleteById(id);
        try {
            fileRecordService.deleteRecords(uid, path, Collections.singletonList(mountPoint.getName()));
        } catch (NoSuchFileException e) {
            throw new JsonException(e.getMessage());
        }
    }

    /**
     * 清理挂载点文件系统的缓存
     */
    private void clearFileSystemFactoryCache(MountPoint mountPoint) {
        try {
            storageRegistry.getStorageFactory(mountPoint.getProtocol()).clearCache(MapperHolder.parseJsonToMap(mountPoint.getParams()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMountPoint(@Validated MountPoint mountPoint) throws IOException, FileSystemParameterException {
        String protocol = mountPoint.getProtocol();
        if(!storageRegistry.isSupportedProtocol(protocol)) {
            throw new UnsupportedFileSystemProtocolException(protocol);
        }
        if (mountPoint.getId() == null) {
            createMountPoint(mountPoint);
        } else {
            changeMountPoint(mountPoint);
        }

        clearCache(mountPoint.getUid());
        clearFileRecord(mountPoint);
    }

    private void changeMountPoint(MountPoint mountPoint) throws NoSuchFileException {
        MountPoint dbObj = mountPointRepo.findById(mountPoint.getId()).orElseThrow(() -> new JsonException(CommonError.RESOURCE_NOT_FOUND));
        Long uid = dbObj.getUid();
        if (!Objects.equals(uid, mountPoint.getUid())) {
            throw new IllegalArgumentException("不能修改uid");
        }

        // 获取挂载点所在目标路径，并检查下面是否存在路径冲突
        Lazy<Optional<String>> newNodePath = Lazy.of(() -> fileRecordService.getPathByNodeId(uid, mountPoint.getNid()));
        if(fileRecordService.getFileInfo(uid, mountPoint.getNid(), mountPoint.getName()) != null) {
            throw new IllegalArgumentException("路径 " + newNodePath.get().orElseGet(mountPoint::getNid) + " 下已存在同名文件或目录");
        }

        // 所属路径发生变化，先执行目录移动的逻辑
        if (!Objects.equals(mountPoint.getNid(), dbObj.getNid())) {
            String originNodePath = fileRecordService.getPathByNodeId(uid, dbObj.getNid())
                    .orElseThrow(() -> new JsonException(FileSystemError.FILE_NOT_FOUND.getCode(), "原节点丢失"));
            fileRecordService.move(uid, originNodePath, newNodePath.get().orElseThrow(() -> new JsonException(FileSystemError.FILE_NOT_FOUND.getCode(), "移动失败，节点丢失")), dbObj.getName(), true);
        }
        // 清理该挂载点的缓存
        clearFileSystemFactoryCache(dbObj);


        // 文件发生变化，执行重命名逻辑
        String originName = dbObj.getName();
        if (!Objects.equals(originName, mountPoint.getName())) {
            try {
                fileRecordService.rename(uid, newNodePath.get().orElseThrow(() -> new JsonException(FileSystemError.FILE_NOT_FOUND.getCode(), "重命名失败，节点丢失")), originName, mountPoint.getName());
            } catch (NoSuchFileException e) {
                throw new JsonException(e.getMessage());
            }
        }
        mountPointRepo.save(mountPoint);

        // 挂载点的存储记录代理配置发生变化时，清空掉原有存储记录信息
        if(Boolean.TRUE.equals(dbObj.getIsProxyStoreRecord()) != Boolean.TRUE.equals(mountPoint.getIsProxyStoreRecord())) {
            String mountPath = fileRecordService.getPathByNodeId(mountPoint.getUid(), mountPoint.getNid())
                    .orElseThrow(() -> new JsonException(FileSystemError.NODE_NOT_FOUND, mountPoint.getNid() + " " + mountPoint.getPath()));
            fileRecordService.deleteRecords(mountPoint.getUid(), mountPath, List.of(mountPoint.getName()));
        }
    }


    /**
     * 创建挂载点
     * @param mountPoint    待创建的挂载点
     */
    private void createMountPoint(MountPoint mountPoint) throws IOException, FileSystemParameterException {
        String path = StringUtils.appendPath(
                fileRecordService.getPathByNodeId(mountPoint.getUid(), mountPoint.getNid()).orElseThrow(() -> new JsonException(FileSystemError.FILE_NOT_FOUND.getCode(), "目标节点丢失")),
                mountPoint.getName()
        );
        if (!StringUtils.hasText(mountPoint.getNid())) {
            mountPoint.setNid(mountPoint.getUid().toString());
        }
        log.debug("{}创建挂载点:{}", LOG_PREFIX, path);
        if(diskFileSystemManager.getMainFileSystem().exist(mountPoint.getUid(), path)) {
            throw new JsonException(FileSystemError.FILE_EXIST);
        }
        boolean protocolIsAvailable = storageRegistry.listPublicStorageFactory().stream().anyMatch(e -> e.getMetadata().getProtocol().equals(mountPoint.getProtocol()));
        if (!protocolIsAvailable) {
            throw new JsonException("找不到或无权创建对应的文件系统协议");
        }

        // 测试挂载点参数
        Map<String, Object> paramMap = MapperHolder.parseJsonToMap(mountPoint.getParams());
        StorageFactory factory = storageRegistry.getStorageFactory(mountPoint.getProtocol());
        if (factory == null) {
            throw new JsonException("不支持的协议" + mountPoint.getProtocol());
        }
        DiskFileSystem fileSystem = factory.getFileSystem(paramMap);
        factory.testFileSystem(fileSystem);
        try {
            fileSystem.getUserFileList(0, "/");
        } catch (IOException e) {
            log.error("{}挂载测试失败：", LOG_PREFIX, e);
            throw new JsonException("挂载测试失败:" + e);
        }

        // 主表保存
        mountPointRepo.save(mountPoint);
        // 文件表保存
        String newNodeId = SecureUtils.getUUID();
        long now = System.currentTimeMillis();
        FileInfo fileInfo = new FileInfo(mountPoint.getName(), -1, FileInfo.TYPE_DIR, "", now, null);
        fileInfo.setUid(mountPoint.getUid());
        fileInfo.setNode(mountPoint.getNid());
        fileInfo.setMountId(mountPoint.getId());
        fileInfo.setMd5(newNodeId);
        fileInfo.setCtime(now);
        fileInfo.setMtime(now);
        fileRecordService.save(fileInfo);
    }

    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'full_mp_' + #uid")
    public Map<String, MountPoint> findMountPointPathByUid(long uid) {
        List<MountPoint> mountPointList = findByUid(uid);
        if (mountPointList == null || mountPointList.isEmpty()) {
            return Collections.emptyMap();
        }
        // key末尾加个/，防止出现部分前缀误匹配导致的挂载点误匹配，如：/挂载点1 与 /挂载点11，两者前缀相同，访问/挂载点11时容易出现误匹配到/挂载点1
        Map<String, MountPoint> mountPointMap = mountPointList.stream().collect(Collectors.toMap(
                e -> StringUtils.appendPath(
                        fileRecordService.getPathByNodeId(e.getUid(), e.getNid())
                                .orElseThrow(() -> new JsonException(404, "节点信息丢失")),
                        e.getName()
                ) + "/",
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

    /**
     * 获取挂载点本身路径的节点id
     * @param mountPoint    挂载点
     */
    private String getMountPointNodeId(MountPoint mountPoint) {
        String nodePath = getMountPointPath(mountPoint);
        String nodeId = fileRecordService.getNodeIdByPath(mountPoint.getUid(), nodePath).orElse(null);
        if (nodeId == null) {
            log.warn("{}用户{}的挂载点目录 {} 丢失节点记录", LOG_PREFIX, mountPoint.getUid(), nodePath);
        }
        return nodeId;
    }

    /**
     * 获取挂载点本身路径
     * @param mountPoint    挂载点
     */
    private String getMountPointPath(MountPoint mountPoint) {
        if (mountPoint.getPath() != null) {
            return mountPoint.getPath();
        }
        return StringUtils.appendPath(
                fileRecordService.getPathByNodeId(mountPoint.getUid(), mountPoint.getNid())
                        .orElseThrow(() -> new JsonException(FileSystemError.FILE_NOT_FOUND.getCode(), "挂载点丢失")),
                mountPoint.getName()
        );
    }


    /**
     * 清理挂载点下托管的文件记录
     * @param mountPoint    挂载点信息
     */
    private void clearFileRecord(MountPoint mountPoint) {
        String nodeId = getMountPointNodeId(mountPoint);
        if (nodeId == null) {
            return;
        }
        // 找出挂载点下的所有子文件和目录，全部删除
        List<FileInfo> existRecord = fileRecordService.findByUidAndNodeId(mountPoint.getUid(), nodeId, null);
        for (FileInfo fileInfo : existRecord) {
            fileRecordService.deleteFileInfo(fileInfo);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncFileRecord(MountPointSyncFileRecordParam param) throws IOException, FileSystemParameterException {
        Long id = param.getId();
        MountPoint mountPoint = findById(id);
        if(!Boolean.TRUE.equals(mountPoint.getIsProxyStoreRecord())) {
            throw new JsonException(400, "挂载点" + mountPoint.getName() + "未开启委托存储记录，不能进行文件记录同步操作");
        }
        String mountPointPath = Objects.requireNonNull(getMountPointPath(mountPoint), "获取挂载点路径失败");
        log.info("{}同步挂载点文件记录-路径: {}", LOG_PREFIX, mountPointPath);

        // 先删除原有的记录数据
        log.info("{}同步挂载点文件记录-路径: {} 删除原记录...", LOG_PREFIX, mountPointPath);
        clearFileRecord(mountPoint);
        Long uid = mountPoint.getUid();
        log.info("{}同步挂载点文件记录-路径: {} 删除原记录完成", LOG_PREFIX, mountPointPath);

        // 重建记录
        Map<String, Object> params = MapperHolder.parseJsonToMap(mountPoint.getParams());
        DiskFileSystem fileSystem = storageRegistry.getStorage(mountPoint.getProtocol(), params);
        DiskFileSystemUtils.walk(fileSystem, uid, "/", (path, fileList) -> {
            try {
                for (FileInfo fileInfo : fileList) {
                    fileInfo.setIsMount(true);
                    fileInfo.setUid(uid);
                    if (fileInfo.isDir()) {
                        String dirPath = StringUtils.appendPath(mountPointPath, path, fileInfo.getName());
                        log.info("{}同步挂载点文件记录-创建目录: {}", LOG_PREFIX, dirPath);
                        fileRecordService.mkdirs(uid, dirPath, true);
                    } else {
                        String filePath = StringUtils.appendPath(mountPointPath, path);
                        fileInfo.setNode(null);

                        // 计算md5
                        boolean needComputeMd5 = Boolean.TRUE.equals(param.getIsComputeMd5()) && fileInfo.getMd5() == null;
                        if (needComputeMd5) {
                            log.info("{}同步挂载点文件记录-计算文件md5: {}", LOG_PREFIX, filePath);
                            InputStreamSource inputStreamSource = Optional
                                    .ofNullable(fileInfo.getStreamSource())
                                    .orElse(fileSystem.getResource(uid, path, fileInfo.getName()));
                            if (inputStreamSource == null) {
                                log.error("{}同步挂载点文件记录-在用户:{} 路径:{} 下的挂载点资源路径:{} 计算文件md5失败，无法获取到文件流资源", LOG_PREFIX, uid, mountPointPath, filePath);
                                throw new RuntimeException("无法获取挂载文件系统中的文件资源: " + filePath);
                            }
                            fileInfo.setStreamSource(inputStreamSource);
                            fileInfo.updateMd5();
                            log.info("{}同步挂载点文件记录-计算文件md5成功: {} - {}", LOG_PREFIX, filePath, fileInfo.getMd5());
                        }
                        fileRecordService.saveRecord(fileInfo, filePath);
                    }
                }
            } catch (Exception e) {
                log.error("{}同步挂载点记录异常", LOG_PREFIX, e);
                throw new JsonException(500, e.getMessage());
            }
        });
    }

}
