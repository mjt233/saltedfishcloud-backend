package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.sfc.archive.ArchiveManager;
import com.sfc.constant.FeatureName;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.dao.mybatis.FileAnalyseDao;
import com.xiaotao.saltedfishcloud.dao.mybatis.FileDao;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.FileSystemStatus;
import com.xiaotao.saltedfishcloud.model.po.NodeInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.*;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.service.hello.FeatureProvider;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
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
    private static String getStoreLockKey(int uid, String path, String name) {
        return uid + ":" + StringUtils.appendPath(path, name);
    }

    @Override
    public Resource getThumbnail(int uid, String path, String name) throws IOException {
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
    public static String getStoreLockKey(int uid, String dest) {
        return uid + ":" + dest;
    }

    @Override
    public void saveAvatar(int uid, Resource resource) throws IOException {
        customStoreService.saveAvatar(uid, resource);
    }


    @Override
    public Resource getAvatar(int uid) throws IOException {
        final Resource avatar = customStoreService.getAvatar(uid);
        if (avatar == null) {
            return customStoreService.getDefaultAvatar();
        } else {
            return avatar;
        }
    }

    @Override
    public boolean quickSave(int uid, String path, String name, String md5) throws IOException {
        List<FileInfo> files = fileDao.getFilesByMD5(md5, 1);
        if (files.isEmpty()) {
            return false;
        }
        FileInfo fileInfo = files.get(0);
        String filePath = nodeService.getPathByNode(fileInfo.getUid(), fileInfo.getNode());
        Resource resource = getResource(fileInfo.getUid(), filePath, fileInfo.getName());
        if (resource == null) {
            return false;
        }
        RLock lock = redisson.getLock(getStoreLockKey(uid, path, name));
        try {
            lock.lock();
            fileInfo.setName(name);
            saveFile(uid, resource.getInputStream(), path, fileInfo);
        } catch (IOException e) {
            log.trace("错误：{}", e.getMessage());
            return false;
        } finally {
            lock.unlock();
        }
        return true;
    }

    @Override
    public boolean exist(int uid, String path) {
        return fileRecordService.exist(uid, PathUtils.getParentPath(path), PathUtils.getLastNode(path));
    }

    @Override
    public Resource getResource(int uid, String path, String name) throws IOException {
        return storeServiceFactory.getService().getResource(uid, path, name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String mkdirs(int uid, String path) throws IOException {
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
        String nid = fileRecordService.mkdirs(uid, path);
        final StoreService storeService = storeServiceFactory.getService();
        if (!storeService.isUnique()) {
            storeService.mkdir(uid, path, "");
        }
        return nid;
    }

    @Override
    public Resource getResourceByMd5(String md5) throws IOException {
        FileInfo fileInfo;
        List<FileInfo> files = fileDao.getFilesByMD5(md5, 1);
        if (files.size() == 0) throw new NoSuchFileException("文件不存在: " + md5);
        fileInfo = files.get(0);
        String path = nodeService.getPathByNode(fileInfo.getUid(), fileInfo.getNode());
        fileInfo.setPath(path + "/" + fileInfo.getName());
        return getResource(fileInfo.getUid(), path, fileInfo.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copy(int uid, String source, String target, int targetUid, String sourceName, String targetName, Boolean overwrite) throws IOException {
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
    public void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {
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
    public List<FileInfo>[] getUserFileList(int uid, String path) throws IOException {
        String nodeId = nodeService.getNodeIdByPath(uid, path);
        return getUserFileListByNodeId(uid, nodeId);
    }

    @Override
    public LinkedHashMap<String, List<FileInfo>> collectFiles(int uid, boolean reverse) {
        LinkedHashMap<String, List<FileInfo>> res = new LinkedHashMap<>();
        List<NodeInfo> nodes = new LinkedList<>();

        // 根目录使用用户id作为id
        String strId = "" + uid;
        nodes.add(NodeInfo.builder()
                .uid((long) uid)
                .id(strId)
                .build()
        );
        nodes.addAll(nodeService.getChildNodes(uid, strId));

        //  获取目录结构
        if (reverse) Collections.reverse(nodes);
        for (NodeInfo node : nodes) {
            String dir = nodeService.getPathByNode(uid, node.getId());
            res.put(dir, fileDao.getFileListByNodeId(uid, node.getId()));
        }
        return res;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<FileInfo>[] getUserFileListByNodeId(int uid, String nodeId) {
        List<FileInfo> fileList = fileDao.getFileListByNodeId(uid, nodeId);
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
    public List<FileInfo> search(int uid, String key) {
        key = "%" + key.replaceAll("%", "\\%").replaceAll("/s+", "%") + "%";
        return fileDao.search(uid, key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveToSaveFile(int uid, Path nativeFilePath, String path, FileInfo fileInfo) throws IOException {
        RLock lock = redisson.getLock(getStoreLockKey(uid, path, fileInfo.getName()));
        try {
            lock.lock();
            storeServiceFactory.getService().moveToSave(uid, nativeFilePath, path, fileInfo);
            int res = fileRecordService.addRecord(uid, fileInfo.getName(), fileInfo.getSize(), fileInfo.getMd5(), path);
            if ( res == 0) {
                fileRecordService.updateFileRecord(uid, fileInfo.getName(), path, fileInfo.getSize(), fileInfo.getMd5());
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long saveFile(int uid, InputStream stream, String path, FileInfo fileInfo) throws IOException {

        if (fileInfo.getMd5() == null) {
            fileInfo.updateMd5();
        }
        return saveFileWithDelete(uid, stream, path, fileInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long saveFile(int uid, MultipartFile file, String requestPath, String md5) throws IOException {
        FileInfo fileInfo = new FileInfo(file);
        // 获取上传的文件信息 并看情况计算MD5
        if (md5 != null) {
            fileInfo.setMd5(md5);
        } else {
            fileInfo.updateMd5();
        }
        return saveFileWithDelete(uid, file.getInputStream(), requestPath, fileInfo);
    }

    private int saveFileWithDelete(int uid, InputStream file, String path, FileInfo fileInfo) throws IOException {
        String nid;
        // 判断目录是否存在，若不存在则尝试创建
        nid = nodeService.getNodeIdByPathNoEx(uid, path);
        if (nid == null) {
            nid = mkdirs(uid, path);
        }

        // 判断是否存在同名文件，若存在则依据md5判断文件内容是否相同
        // 如果文件内容相同，则不做任何处理
        // 若文件相同，则执行一次删除同名文件
        RLock lock = redisson.getLock(getStoreLockKey(uid, path, fileInfo.getName()));
        try {
            lock.lock();
            FileInfo originInfo = fileDao.getFileInfo(uid, fileInfo.getName(), nid);
            boolean exist = false;
            if (originInfo != null) {
                if (originInfo.getMd5().equals(fileInfo.getMd5())) {
                    return SAVE_NOT_CHANGE;
                } else {
                    deleteFile(uid, path, Collections.singletonList(fileInfo.getName()));
                }
                exist = true;
            }
            storeServiceFactory.getService().store(uid, file, path, fileInfo);
            fileDao.addRecord(uid, fileInfo.getName(), fileInfo.getSize(), fileInfo.getMd5(), nid);
            return exist ? SAVE_COVER : SAVE_NEW_FILE;
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mkdir(int uid, String path, String name) throws IOException {
        final StoreService storeService = storeServiceFactory.getService();
        if ( !storeService.isUnique() && !storeService.mkdir(uid, path, name) ) {
            throw new IOException("在" + path + "创建文件夹失败");
        }
        fileRecordService.mkdir(uid, name, path);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long deleteFile(int uid, String path, List<String> name) throws IOException {
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
                    if (!md5Resolver.hasRef(fileInfo.getMd5())) {
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
    public void rename(int uid, String path, String name, String newName) throws IOException {
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
