package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.constant.FeatureName;
import com.xiaotao.saltedfishcloud.dao.mybatis.FileAnalyseDao;
import com.xiaotao.saltedfishcloud.dao.mybatis.FileDao;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.FileSystemStatus;
import com.xiaotao.saltedfishcloud.model.po.NodeInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.*;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.service.hello.FeatureProvider;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.*;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.xiaotao.saltedfishcloud.model.FileSystemStatus.AREA_PRIVATE;
import static com.xiaotao.saltedfishcloud.model.FileSystemStatus.AREA_PUBLIC;

@Slf4j
@Component
public class DefaultFileSystem implements DiskFileSystem, FeatureProvider, InitializingBean {

    @Autowired
    private StoreServiceFactory storeServiceFactory;

    @Autowired
    private FileDao fileDao;

    @Autowired
    private FileAnalyseDao fileAnalyseDao;

    @Autowired
    private FileRecordService fileRecordService;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private CustomStoreService customStoreService;

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

    /**
     * 获取写文件时用到分布式锁key
     * @param uid   用户id
     * @param path  文件所在路径
     * @param name  文件名
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
     * @param uid   用户id
     * @param dest  文件所在路径
     */
    public static String getStoreLockKey(long uid, String dest) {
        return uid + ":" + dest;
    }

    @Override
    public void saveAvatar(long uid, Resource resource) throws IOException {
        customStoreService.saveAvatar(uid, resource);
    }


    @Override
    public Resource getAvatar(long uid) throws IOException {
        final Resource avatar = customStoreService.getAvatar(uid);
        if (avatar == null) {
            return customStoreService.getDefaultAvatar();
        } else {
            return avatar;
        }
    }

    @Override
    public boolean quickSave(long uid, String path, String name, String md5) throws IOException {
        List<FileInfo> files = fileRecordService.getFileInfoByMd5(md5, 1);
        if (files.isEmpty()) {
            return false;
        }
        FileInfo existMd5File = files.get(0);
        String filePath = nodeService.getPathByNode(existMd5File.getUid(), existMd5File.getNode());
        Resource resource = getResource(existMd5File.getUid(), filePath, existMd5File.getName());
        if (resource == null) {
            return false;
        }
        try {
            StoreService storeService = storeServiceFactory.getService();
            if (storeService.isUnique()) {
                fileRecordService.copy(existMd5File.getUid(), filePath, path, uid, existMd5File.getName(), name, true);
            } else {
                fileRecordService.copy(existMd5File.getUid(), filePath, path, uid, existMd5File.getName(), name, true);
                existMd5File.setName(name);
                FileInfo newFile = FileInfo.createFrom(existMd5File, false);
                newFile.setUid(uid);
                newFile.setStreamSource(resource);
                saveFile(newFile, path);
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
    @Transactional(rollbackFor = Exception.class)
    public void copy(long uid, String source, String target, long targetUid, String sourceName, String targetName, Boolean overwrite) throws IOException {
        RLock lock = redisson.getLock(getStoreLockKey(uid, target, targetName));
        try {
            lock.lock();
            fileRecordService.copy(uid, source, target, targetUid, sourceName, targetName, overwrite);
            final StoreService service = storeServiceFactory.getService();
            if (!service.isUnique()) {
                service.copy(uid, source, target, targetUid, sourceName, targetName, overwrite);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        String nodeId = nodeService.getNodeIdByPath(uid, path);
        return getUserFileListByNodeId(uid, nodeId);
    }

    @Override
    public List<FileInfo> getUserFileList(long uid, String path,@Nullable Collection<String> nameList) throws IOException {
        String nodeId = nodeService.getNodeIdByPath(uid, path);
        return fileRecordService.findByUidAndNodeId(uid, nodeId, nameList);
    }

    @Override
    public LinkedHashMap<String, List<FileInfo>> collectFiles(long uid, boolean reverse) {
        LinkedHashMap<String, List<FileInfo>> res = new LinkedHashMap<>();
        List<NodeInfo> nodes = new LinkedList<>();

        // 根目录使用用户id作为id
        String strId = "" + uid;
        nodes.add(NodeInfo.builder()
                .uid(uid)
                .id(strId)
                .build()
        );
        nodes.addAll(nodeService.getChildNodes(uid, strId));

        //  获取目录结构
        if (reverse) Collections.reverse(nodes);
        for (NodeInfo node : nodes) {
            String dir = nodeService.getPathByNode(uid, node.getId());
            res.put(dir, fileRecordService.findByUidAndNodeId(uid, node.getId()));
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
    public List<FileInfo> search(long uid, String key) {
        key = "%" + key.replaceAll("%", "\\%").replaceAll("/s+", "%") + "%";
        return fileDao.search(uid, key);
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
        String nid = Optional.ofNullable(nodeService.getNodeIdByPathNoEx(uid, savePath)).orElseGet(() -> {
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
        if ( !storeService.isUnique() && !storeService.mkdir(uid, path, name) ) {
            throw new IOException("在" + path + "创建文件夹失败");
        }
        fileRecordService.mkdir(uid, name, path);
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
                for (FileInfo fileInfo : fileInfos) {

                    // todo 使用批量查询和求集合差级操作进行引用判断提高性能
                    if (fileInfo.getMd5() != null && !md5Resolver.hasRef(fileInfo.getMd5())) {
                        storeService.delete(fileInfo.getMd5());
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

    @Override
    public boolean equals(Object obj) {
        return obj == this;
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
        publicStatus.setFileCount(fileAnalyseDao.getPublicFileCount());
        publicStatus.setDirCount(fileAnalyseDao.getPublicDirCount());
        publicStatus.setSysUsed(fileAnalyseDao.getPublicTotalSize());


        FileSystemStatus privateStatus = areaMap.get(FileSystemStatus.AREA_PRIVATE);
        privateStatus.setFileCount(fileAnalyseDao.getUserFileCount());
        privateStatus.setDirCount(fileAnalyseDao.getUserDirCount());
        privateStatus.setSysUsed(fileAnalyseDao.getUserTotalSize());

        return Arrays.asList(publicStatus, privateStatus);
    }
}
