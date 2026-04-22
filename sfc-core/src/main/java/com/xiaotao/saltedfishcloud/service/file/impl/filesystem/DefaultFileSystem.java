package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xiaotao.saltedfishcloud.constant.FeatureName;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.dao.jpa.FileInfoRepo;
import com.xiaotao.saltedfishcloud.dao.jpa.projection.FileInfoSearchResult;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.FileSystemStatus;
import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferCallback;
import com.xiaotao.saltedfishcloud.model.progress.CopyProgressEvent;
import com.xiaotao.saltedfishcloud.model.progress.CopyProgressEventLevel;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import com.xiaotao.saltedfishcloud.model.progress.event.UpdateFileRecordCompleteEvent;
import com.xiaotao.saltedfishcloud.model.progress.event.UpdateFileRecordStartEvent;
import com.xiaotao.saltedfishcloud.service.file.*;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.service.hello.FeatureProvider;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import com.xiaotao.saltedfishcloud.utils.*;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.xiaotao.saltedfishcloud.model.FileSystemStatus.AREA_PRIVATE;
import static com.xiaotao.saltedfishcloud.model.FileSystemStatus.AREA_PUBLIC;

/**
 * 默认的文件系统实现，采用 文件元数据节点记录服务 + 文件内容存储服务 结合的结构
 */
@Slf4j
@Component
public class DefaultFileSystem implements DiskFileSystem, FeatureProvider, InitializingBean {
    private final static String LOG_PREFIX = "[默认文件系统]";

    @Autowired
    private StoreServiceFactory storeServiceFactory;

    @Autowired
    private FileInfoRepo fileInfoRepo;

    @Autowired
    private FileRecordService fileRecordService;

    @Autowired
    private UserCustomStoreService userCustomStoreService;

    @Autowired
    private FileResourceMd5Resolver md5Resolver;

    @Autowired
    private RedissonClient redisson;

    @Autowired
    private ThumbnailService thumbnailService;

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;


    @Override
    public void afterPropertiesSet() throws Exception {
        diskFileSystemManager.setMainFileSystem(this);
    }

    @Override
    public void updateTime(long uid, String path, List<String> names, FileTimeAttribute attribute) throws IOException {
        StoreService storeService = storeServiceFactory.getService();
        FileInfo fileInfo = fileRecordService.getByPath(uid, path).orElse(null);
        if (fileInfo == null || fileInfo.isFile()) {
            log.warn("{} 找不到路径{}:{}对应的节点，无法修改文件日期信息", LOG_PREFIX, uid, path);
            return;
        }
        List<FileInfo> fileInfoList = fileRecordService.findByUidAndNodeId(uid, fileInfo.getNode(), names);
        fileInfoList.forEach(f -> {
            if (attribute.apply(f)) {
                fileRecordService.save(f);
            }
        });
        if (!storeService.isUnique()) {
            storeService.updateTime(uid, path, names, attribute);
        }
    }

    /**
     * 获取写文件时用到分布式锁key
     *
     * @param uid  用户id
     * @param path 文件所在路径
     * @param name 文件名
     */
    private static String getStoreLockKey(long uid, String path, String name) {
        return uid + ":" + StringUtils.appendPath(path, name);
    }

    @Override
    public Resource getThumbnail(long uid, String path, String name) throws IOException {
        String md5 = md5Resolver.getResourceMd5(uid, StringUtils.appendPath(path, name));
        if (md5 != null) {
            return thumbnailService.getThumbnail(md5, FileUtils.getSuffix(name));
        }
        return null;
    }

    /**
     * 获取写文件时用到分布式锁key
     *
     * @param uid  用户id
     * @param dest 文件所在路径
     */
    public static String getStoreLockKey(long uid, String dest) {
        return uid + ":" + dest;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean quickSave(long uid, String path, String name, String md5) throws IOException {
        List<FileInfo> files = fileRecordService.getFileInfoByMd5(md5, 1);
        if (files.isEmpty()) {
            return false;
        }
        FileInfo existMd5File = ObjectUtils.clone(files.get(0), FileInfo::new);
        String filePath = fileRecordService.getPathByNodeId(existMd5File.getUid(), existMd5File.getNode())
                .orElse(null);
        if (filePath == null) {
            return false;
        }
        Resource resource = getResource(existMd5File.getUid(), filePath, existMd5File.getName());
        if (resource == null) {
            return false;
        }
        try {
            StoreService storeService = storeServiceFactory.getService();
            FileInfo newFile = FileInfo.createFrom(existMd5File, false);
            newFile.setId(null);
            newFile.setUid(uid);
            newFile.setStreamSource(resource);
            newFile.setName(name);
            if (!storeService.isUnique()) {
                saveFile(newFile, path);
            } else {
                fileRecordService.saveRecord(newFile, path);
            }
        } catch (IOException e) {
            log.trace("错误：{}", e.getMessage());
            return false;
        }
        return true;
    }

    @Override
    public boolean exist(long uid, String path) {
        return fileRecordService.exist(uid, PathUtils.getParentPath(path), PathUtils.getLastNode(path));
    }

    @Override
    public Resource getResource(long uid, String path, String name) throws IOException {
        Resource resource = storeServiceFactory.getService().getResource(uid, path, name);
        if (resource == null) {
            return null;
        }
        return ResourceUtils.bindFileInfo(resource, () -> fileRecordService.getFileInfo(uid, path, name));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String mkdirs(long uid, String path) throws IOException {
        PathBuilder pb = new PathBuilder();
        pb.append(path);
        if (pb.getPath().isEmpty()) {
            return uid + "";
        }
        String parent = pb.range(-1);
        String name = pb.range(1, -1);
        if (getResource(uid, parent, name) != null) {
            throw new UnsupportedOperationException("已存在同名文件：" + path);
        }
        String nid = fileRecordService.getAndMkdirs(uid, path, false);
        final StoreService storeService = storeServiceFactory.getService();
        if (!storeService.isUnique()) {
            storeService.mkdir(uid, path, "");
        }
        return nid;
    }

    @Override
    public void copy(SimpleFileTransferParam param, FileTransferCallback callback) throws IOException {
        FileTransferItem transferItem = FileTransferItem.builder()
                .from(param.getSourcePath())
                .to(param.getTargetPath())
                .build();
        if (callback != null) {
            callback.onAdditionalEvent(UpdateFileRecordStartEvent.of());
        }
        fileRecordService.copy(param, callback);
        if (callback != null) {
            callback.onAdditionalEvent(UpdateFileRecordCompleteEvent.of(transferItem));
        }
        StoreService storeService = storeServiceFactory.getService();
        if (!storeService.isUnique()) {
            if (callback != null) {
                callback.onAdditionalEvent(new CopyProgressEvent() {
                    {
                        setMessage("哈希存储模式下，物理存储无需操作，已跳过");
                        setName("Store Skip");
                    }
                    @Override
                    public CopyProgressEventLevel getLevel() {
                        return CopyProgressEventLevel.INFO;
                    }
                });
            }
            storeService.copy(param, callback);
        }
    }

    @Override
    public void move(long uid, String source, String target, String name, boolean overwrite) throws IOException {
        RLock lock = redisson.getLock(getStoreLockKey(uid, target, name));
        try {
            lock.lock();
            target = URLDecoder.decode(target, StandardCharsets.UTF_8);
            fileRecordService.move(uid, source, target, name, overwrite);
            final StoreService storeService = storeServiceFactory.getService();
            if (!storeService.isUnique()) {
                storeService.move(uid, source, target, name, overwrite);
            }
        } catch (DuplicateKeyException e) {
            throw new JsonException(409, "目标目录下已存在 " + name + " 暂不支持目录合并或移动覆盖");
        } catch (UnsupportedEncodingException e) {
            throw new JsonException(400, "不支持的编码（请使用UTF-8）");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<FileInfo>[] getUserFileList(long uid, String path) throws IOException {
        return fileRecordService.getNodeIdByPath(uid, path)
                .map(nodeId -> getUserFileListByNodeId(uid, nodeId))
                .orElseThrow(() -> new JsonException(FileSystemError.FILE_NOT_FOUND));
    }

    @Override
    public List<FileInfo> getUserFileList(long uid, String path, @Nullable Collection<String> nameList) throws IOException {

        return fileRecordService.getNodeIdByPath(uid, path)
                .map(nodeId -> fileRecordService.findByUidAndNodeId(uid, nodeId, nameList))
                .orElseThrow(() -> new JsonException(FileSystemError.FILE_NOT_FOUND, path));
    }

    @Override
    public LinkedHashMap<String, List<FileInfo>> collectFiles(long uid, boolean reverse) {
        LinkedHashMap<String, List<FileInfo>> res = new LinkedHashMap<>();
        List<FileInfo> dirs = new LinkedList<>();

        // 根目录使用用户id作为id
        String strId = "" + uid;
        dirs.add(FileInfo.getRoot(uid));
        dirs.addAll(fileRecordService.listChildDirs(uid, strId, -1));

        //  获取目录结构
        if (reverse) Collections.reverse(dirs);
        for (FileInfo dirInfo : dirs) {
            String dir = fileRecordService.getPathByNodeId(uid, dirInfo.getMd5())
                    .orElseThrow(() -> new JsonException(FileSystemError.FILE_NOT_FOUND.getCode(), "数据移除，节点信息" + dirInfo.getMd5() + "丢失"));
            res.put(dir, fileRecordService.findByUidAndNodeId(uid, dirInfo.getMd5()));
        }
        return res;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<FileInfo>[] getUserFileListByNodeId(long uid, String nodeId) {
        List<FileInfo> fileList = fileRecordService.findByUidAndNodeId(uid, nodeId);
        List<FileInfo> dirs = new LinkedList<>(), files = new LinkedList<>();
        fileList.forEach(file -> {
            if (file.isFile()) {
                file.setType(FileInfo.TYPE_FILE);
                files.add(file);
            } else {
                file.setType(FileInfo.TYPE_DIR);
                dirs.add(file);
            }
        });
        return new List[]{dirs, files};
    }

    @Override
    public CommonPageInfo<FileInfo> search(long uid, String key, Integer page) {
        // todo 重构搜索功能，支持异步任务/遍历式搜索，更丰富的参数控制
        key = key.replace("%", "").replaceAll("\\s+", "%");
        Page<FileInfoSearchResult> searchResult = fileInfoRepo.search(uid, key, PageRequest.of(page, 10));
        CommonPageInfo<FileInfo> pageInfo = new CommonPageInfo<>();
        pageInfo.setTotalPage(searchResult.getTotalPages());
        pageInfo.setTotalCount(searchResult.getTotalElements());
        pageInfo.setContent(searchResult.getContent().stream().map(r -> {
            FileInfo fileInfo = r.getFileInfo();
            fileInfo.setParent(r.getParent());
            return fileInfo;
        }).toList());
        return pageInfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveToSaveFile(long uid, Path nativeFilePath, String path, FileInfo fileInfo) throws IOException {
        RLock lock = redisson.getLock(getStoreLockKey(uid, path, fileInfo.getName()));
        try {
            lock.lock();
            fileInfo.setUid(uid);
            storeServiceFactory.getService().moveToSave(uid, nativeFilePath, path, fileInfo);
            fileRecordService.saveRecord(fileInfo, path);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 按 uid 和 path 分组批量保存文件，减少重复目录查询与单条入库操作。
     *
     * @param fileInfos 待保存文件
     * @param callback 文件传输回调，可为 null
     * @throws IOException 文件操作异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveFiles(List<FileInfo> fileInfos, @Nullable FileTransferCallback callback) throws IOException {
        if (fileInfos == null || fileInfos.isEmpty()) {
            return;
        }
        record SaveKey(Long uid, String path) {}

        LinkedHashMap<SaveKey, List<FileInfo>> groupedFiles = new LinkedHashMap<>();
        for (FileInfo fileInfo : fileInfos) {
            if (fileInfo == null
                    || fileInfo.getUid() == null
                    || fileInfo.getPath() == null
                    || fileInfo.getName() == null
                    || fileInfo.getStreamSource() == null) {
                throw new IllegalArgumentException("batchSaveFiles param invalid: uid/path/name/streamSource must not be null");
            }
            groupedFiles.computeIfAbsent(new SaveKey(fileInfo.getUid(), fileInfo.getPath()), e -> new ArrayList<>()).add(fileInfo);
        }

        StoreService storeService = storeServiceFactory.getService();
        for (Map.Entry<SaveKey, List<FileInfo>> entry : groupedFiles.entrySet()) {
            SaveKey key = entry.getKey();
            List<FileInfo> group = entry.getValue();
            for (FileInfo fileInfo : group) {
                if (callback != null && callback.shouldInterrupt()) {
                    return;
                }
                FileTransferItem item = FileTransferItem.builder()
                        .from(fileInfo.getName())
                        .to(StringUtils.appendPath(key.path(), fileInfo.getName()))
                        .total(fileInfo.getSize())
                        .loaded(0L)
                        .fileInfo(fileInfo)
                        .build();
                if (callback != null) {
                    callback.onFileStart(item);
                }
                LockUtils.execute(getStoreLockKey(key.uid(), key.path(), fileInfo.getName()),
                        () -> doStoreBySource(storeService, fileInfo, key.path()));
                item.setLoaded(item.getTotal());
                if (callback != null) {
                    callback.onFileComplete(item);
                }
            }
            fileRecordService.batchSaveFileInSameDirectory(key.uid(), key.path(), group);
        }
    }

    /**
     * 执行数据写入到存储服务
     */
    private void doStoreBySource(StoreService storeService, FileInfo fileInfo, String savePath) throws IOException {
        storeService.storeByStream(fileInfo, savePath, os -> {
            try (InputStream is = fileInfo.getStreamSource().getInputStream()) {
                return DiskFileSystemUtils.saveFileStream(fileInfo, is, os);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long saveFile(FileInfo file, String savePath) throws IOException {
        saveFileByStream(file, savePath, os -> DiskFileSystemUtils.saveFile(file, os));
        return SAVE_NEW_FILE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveFileByStream(FileInfo file, String savePath, OutputStreamConsumer<OutputStream> streamConsumer) throws IOException {
        // 判断目录是否存在，若不存在则尝试创建
        Long uid = file.getUid();
        StoreService storeService = storeServiceFactory.getService();
        String nid = fileRecordService.getNodeIdByPath(uid, savePath).orElseGet(() -> {
            try {
                return mkdirs(file.getUid(), savePath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        LockUtils.execute(getStoreLockKey(uid, savePath, file.getName()), () -> {
            FileInfo originInfo = fileRecordService.getFileInfoByNode(uid, nid, file.getName());
            // 判断是否存在同名文件，若存在同名文件则先保存为临时文件后，再删除将临时文件重命名为同名文件覆盖
            if (originInfo != null) {
                if (originInfo.isDir()) {
                    throw new IllegalArgumentException("无法使用文件来覆盖文件夹 path: " + savePath + " name:" + file.getName());
                }
                FileInfo tmpFile = new FileInfo();
                BeanUtils.copyProperties(file, tmpFile);
                tmpFile.setName(tmpFile.getName() + "." + IdUtil.getId() + ".tmp");

                storeService.storeByStream(tmpFile, savePath, streamConsumer);
                fileRecordService.saveRecord(tmpFile, savePath);

                deleteFile(uid, savePath, Collections.singletonList(file.getName()));
                rename(file.getUid(), savePath, tmpFile.getName(), file.getName());
            } else {
                storeService.storeByStream(file, savePath, streamConsumer);
                fileRecordService.saveRecord(file, savePath);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mkdir(long uid, String path, String name) throws IOException {
        final StoreService storeService = storeServiceFactory.getService();
        if (!storeService.isUnique() && !storeService.mkdir(uid, path, name)) {
            throw new IOException("在" + path + "创建文件夹失败");
        }
        fileRecordService.mkdirs(uid, StringUtils.appendPath(path, name), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long deleteFile(long uid, String path, List<String> name) throws IOException {
        ArrayList<RLock> locks = new ArrayList<>();
        for (String s : name) {
            RLock lock = redisson.getLock(getStoreLockKey(uid, path, s));
            lock.lock();
            locks.add(lock);
        }
        try {
            // 计数删除数
            long res = 0L;
            List<FileInfo> fileInfos = fileRecordService.deleteRecords(uid, path, name);
            final StoreService storeService = storeServiceFactory.getService();

            // 唯一存储下确认文件无引用后再执行通过md5删除
            if (storeService.isUnique()) {
                Map<String, List<FileInfo>> md5FileGroup = fileInfos.stream().filter(FileInfo::isFile)
                        .collect(Collectors.groupingBy(FileInfo::getMd5));
                Set<String> inRefMd5Set = CollectionUtils.partition(md5FileGroup.keySet(), 500)
                        .stream()
                        .flatMap(md5List -> md5Resolver.checkHasRef(md5List).stream())
                        .collect(Collectors.toSet());
                List<String> toDeleteMd5 = md5FileGroup.keySet()
                        .stream()
                        .filter(s -> !inRefMd5Set.contains(s))
                        .toList();
                if (!toDeleteMd5.isEmpty()) {
                    for (String md5 : toDeleteMd5) {
                        storeService.delete(md5);
                    }
                }
            } else {
                res += storeService.delete(uid, path, name);
            }
            return res;
        } finally {
            for (RLock lock : locks) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(long uid, String path, String name, String newName) throws IOException {
        RLock lock1 = redisson.getLock(getStoreLockKey(uid, path, name));
        RLock lock2 = redisson.getLock(getStoreLockKey(uid, path, newName));
        lock1.lock();
        lock2.lock();
        try {
            fileRecordService.rename(uid, path, name, newName);
            final StoreService storeService = storeServiceFactory.getService();
            if (!storeService.isUnique()) {
                storeService.rename(uid, path, name, newName);
            }
        } finally {
            lock1.unlock();
            lock2.unlock();
        }
    }

    @Override
    public void registerFeature(HelloService helloService) {
        helloService.appendFeatureDetail(FeatureName.ARCHIVE_TYPE, "zip");
        helloService.appendFeatureDetail(FeatureName.EXTRACT_ARCHIVE_TYPE, "zip");
    }

    /**
     * 文件数量和用户使用量大小的统计会查询数据库，文件数量较多时会严重影响性能，因此使用懒加载，只有在用到的时候再去统计
     * todo 考虑使用缓存，完善用户配额统计和限制功能
     */
    @RequiredArgsConstructor
    private static class LazyFileSystemStatus extends FileSystemStatus {
        @JsonIgnore
        private transient final FileInfoRepo fileInfoRepo;

        private boolean isPrivate() {
            return AREA_PRIVATE.equals(this.getArea());
        }

        @Override
        public Long getFileCount() {
            if (super.getFileCount() == null) {
                this.setFileCount(isPrivate() ? fileInfoRepo.getUserFileCount() : fileInfoRepo.getPublicFileCount());
            }
            return super.getFileCount();
        }

        @Override
        public Long getDirCount() {
            if (super.getDirCount() == null) {
                this.setDirCount(isPrivate() ? fileInfoRepo.getUserDirCount() : fileInfoRepo.getPublicDirCount());
            }
            return super.getDirCount();
        }

        @Override
        public Long getSysUsed() {
            if (super.getSysUsed() == null) {
                this.setSysUsed(isPrivate() ? fileInfoRepo.getUserTotalSize() : fileInfoRepo.getPublicTotalSize());
            }
            return super.getSysUsed();
        }
    }

    @Override
    public List<FileSystemStatus> getStatus() {
        List<FileSystemStatus> status = storeServiceFactory.getService().getStatus();
        Map<String, FileSystemStatus> areaMap;
        if (status != null) {
            areaMap = status.stream().collect(Collectors.toMap(FileSystemStatus::getArea, e -> e));
        } else {
            areaMap = new HashMap<>();
        }

        areaMap.putIfAbsent(AREA_PRIVATE, FileSystemStatus.builder().area(AREA_PRIVATE).build());
        areaMap.putIfAbsent(AREA_PUBLIC, FileSystemStatus.builder().area(AREA_PUBLIC).build());

        FileSystemStatus publicStatus = areaMap.get(AREA_PUBLIC);
        LazyFileSystemStatus lazyPublicStatus = new LazyFileSystemStatus(fileInfoRepo);
        BeanUtils.copyProperties(publicStatus, lazyPublicStatus);


        FileSystemStatus privateStatus = areaMap.get(FileSystemStatus.AREA_PRIVATE);
        LazyFileSystemStatus lazyPrivateStatus = new LazyFileSystemStatus(fileInfoRepo);
        BeanUtils.copyProperties(privateStatus, lazyPrivateStatus);

        return Arrays.asList(lazyPublicStatus, lazyPrivateStatus);
    }
}
