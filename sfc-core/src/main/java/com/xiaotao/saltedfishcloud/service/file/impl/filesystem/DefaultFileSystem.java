package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.utils.compress.creator.ArchiveCompressor;
import com.xiaotao.saltedfishcloud.utils.compress.creator.ArchiveResourceEntry;
import com.xiaotao.saltedfishcloud.utils.compress.creator.ZipCompressor;
import com.xiaotao.saltedfishcloud.utils.compress.reader.ArchiveReaderVisitor;
import com.xiaotao.saltedfishcloud.utils.compress.reader.impl.ZipArchiveReader;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.dao.mybatis.FileDao;
import com.xiaotao.saltedfishcloud.model.po.NodeInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.enums.ArchiveError;
import com.xiaotao.saltedfishcloud.enums.ArchiveType;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.service.file.*;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.service.hello.FeatureProvider;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
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
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.ZipException;

@Slf4j
@Component
public class DefaultFileSystem implements DiskFileSystem, ApplicationRunner, FeatureProvider, InitializingBean {
    private final static String LOG_TITLE = "FileSystem";

    @Autowired
    private StoreServiceFactory storeServiceFactory;

    @Autowired
    private FileDao fileDao;

    @Autowired
    private FileRecordService fileRecordService;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private CustomStoreService customStoreService;

    @Autowired
    private FileResourceMd5Resolver md5Resolver;

    @Autowired
    private SysProperties sysProperties;

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
    private static String getStoreLockKey(int uid, String dest) {
        return uid + ":" + dest;
    }

    @Override
    public void saveAvatar(int uid, Resource resource) throws IOException {
        customStoreService.saveAvatar(uid, resource);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("[{}]Archive File Encoding: {}", LOG_TITLE, sysProperties.getStore().getArchiveEncoding());
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
    public void compressAndWriteOut(int uid, String path, Collection<String> names, ArchiveType type, OutputStream outputStream) throws IOException {
        try(ZipCompressor compressor = new ZipCompressor(outputStream, sysProperties.getStore().getArchiveEncoding())) {
            compress(uid, path, path, names, compressor);
        }
    }

    /**
     * 压缩目录下指定的文件
     * @param uid       用户ID
     * @param root      压缩根
     * @param path      路径
     * @param names     文件名集合
     * @param compressor    压缩器
     */
    private void compress(int uid, String root, String path, Collection<String> names, ArchiveCompressor compressor) throws IOException {
        String curDir = StringUtils.removePrefix(root, path).replaceAll("//+", "").replaceAll("^/+", "");
        for (String name : names) {
            Resource resource = getResource(uid, path, name);
            if (resource == null) {
                compressDir(uid, root, path + "/" + name, compressor, 1);
            } else {
                compressor.addFile(new ArchiveResourceEntry(
                        curDir.length() == 0 ? name : curDir + "/" + name,
                        resource.contentLength(),
                        resource
                ));
            }
        }
    }

    /**
     * 压缩目标文件夹内的所有内容
     * @param uid   用户ID
     * @param root  压缩根路径
     * @param path  要压缩的完整目录路径
     * @param compressor    压缩器
     * @param depth         当前压缩深度
     */
    private void compressDir(int uid, String root, String path, ArchiveCompressor compressor, int depth) throws IOException {
        List<FileInfo>[] list = getUserFileList(uid, path);
        String curPath = StringUtils.removePrefix(root, path).replaceAll("//+", "/").replaceAll("^/+", "");
        compressor.addFile(new ArchiveResourceEntry(
                curPath + "/",
                0,
                null
        ));
        for (FileInfo file : list[1]) {
            compressor.addFile(new ArchiveResourceEntry(
                    curPath + "/" + file.getName(), file.getSize(), getResource(uid, path, file.getName()))
            );
        }

        for (FileInfo file : list[0]) {
            compressor.addFile(new ArchiveResourceEntry(curPath + "/" + file.getName() + "/", 0, null));
            compressDir(uid, root, path + "/" + file.getName(), compressor, depth + 1);
        }
    }

    @Override
    public void compress(int uid, String path, Collection<String> names, String dest, ArchiveType type) throws IOException {
        boolean exist = exist(uid, dest);
        if (exist && getResource(uid, dest, "") == null) {
            throw new JsonException(FileSystemError.RESOURCE_TYPE_NOT_MATCH);
        }
        Path temp = Paths.get(PathUtils.getTempDirectory() + "/temp_zip" + System.currentTimeMillis());
        RLock lock = redisson.getLock(getStoreLockKey(uid, dest));
        lock.lock();
        try(OutputStream output = Files.newOutputStream(temp)) {
            PathBuilder pb = new PathBuilder();
            pb.append(dest);
            compressAndWriteOut(uid, path, names, ArchiveType.ZIP, output);
            final FileInfo fileInfo = FileInfo.getLocal(temp.toString());
            fileInfo.setName(pb.range(1, -1).replace("/", ""));

            if (exist) {
                deleteFile(uid, PathUtils.getParentPath(dest), Collections.singletonList(PathUtils.getLastNode(dest)));
            }
            moveToSaveFile(uid, temp, pb.range(-1), fileInfo);
        } finally {
            Files.deleteIfExists(temp);
            lock.unlock();
        }


    }

    /**
     * @TODO 使用任务队列控制同时进行解压缩的数量
     * @TODO 边解压边计算MD5
     * @TODO 实现实时计算MD5的IO流
     * @TODO 封装代码
     */
    @Override
    public void extractArchive(int uid, String path, String name, String dest) throws IOException {
        if (!name.endsWith(".zip")) {
            throw new JsonException(ArchiveError.ARCHIVE_FORMAT_UNSUPPORTED);
        }
        Resource resource = getResource(uid, path, name);
        if (resource == null) throw new NoSuchFileException(path + "/" + name);


        // 创建临时目录用于存放临时解压的文件
        Path tempBasePath = Paths.get(StringUtils.appendPath(PathUtils.getTempDirectory(), System.currentTimeMillis() + ""));
        Path zipFilePath;
        boolean isDownloadZip = false;
        File zipFile;
        try {
            zipFile = resource.getFile();
            zipFilePath = Paths.get(zipFile.getAbsolutePath());
        } catch (FileNotFoundException ignore) {
            // getFile异常时，需要从Resource的InputStream保存文件到本地
            isDownloadZip = true;
            zipFilePath = Paths.get(StringUtils.appendPath(PathUtils.getTempDirectory(), StringUtils.getRandomString(6) + ".zip"));
            try(
                    final InputStream is = resource.getInputStream();
                    final OutputStream os = Files.newOutputStream(zipFilePath)
            ) {
                log.debug("[解压文件]非本地文件系统存储服务，需要复制文件到本地文件系统：{} ", resource.getFilename());
                log.debug("[解压文件]临时文件：{}", zipFilePath);
                StreamUtils.copy(is, os);
            } catch (IOException e) {
                log.debug("[解压文件]临时文件保存出错");
                Files.deleteIfExists(zipFilePath);
                throw e;
            }
            zipFile = zipFilePath.toFile();
        }

        try(
                ZipArchiveReader fileSystem = new ZipArchiveReader(zipFile)
        ) {

            Files.createDirectories(tempBasePath);

            // 先解压文件到本地，并在网盘中先创建好文件夹
            try(
                    final ArchiveInputStream ignored = fileSystem.walk(((file, stream) -> {
                        Path localTemp = Paths.get(tempBasePath + "/" + file.getPath());
                        if (file.isDirectory()) {
                            log.debug("创建文件夹：{}", localTemp);
                            Files.createDirectories(localTemp);
                            mkdirs(uid, dest + "/" + file.getPath());
                        } else {
                            log.debug("解压文件：{}", localTemp);
                            Files.copy(stream, localTemp);
                        }
                        return ArchiveReaderVisitor.Result.CONTINUE;
                    }))
            ) {
                Files.walkFileTree(tempBasePath, new SimpleFileVisitor<Path>() {
                    final int tempLen = tempBasePath.toString().length();
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String diskPath = dest + "/" + file.getParent().toString().substring(tempLen);
                        moveToSaveFile(uid, file, diskPath, FileInfo.getLocal(file.toString()));
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (ZipException | ArchiveException e) {
            JsonException exception = new JsonException(ArchiveError.ARCHIVE_FORMAT_UNSUPPORTED);
            exception.initCause(e);
            exception.setStackTrace(e.getStackTrace());
            e.printStackTrace();
            throw exception;
        } catch (IOException e) {
            e.printStackTrace();
            throw new JsonException(500, "存储出错或可能存在冲突的文件与文件夹名");
        } catch (Exception e) {
            e.printStackTrace();
            throw new JsonException(e.getMessage());
        }finally {
            // 清理临时解压目录
            // 创建压缩读取器时可能会抛出异常导致临时目录并未创建
            if (Files.exists(tempBasePath)) {
                log.debug("[解压文件]清理临时解压目录：{}", tempBasePath);
                FileUtils.delete(tempBasePath);
            }
            if (isDownloadZip) {
                log.debug("[解压文件]清理临时压缩包：{}", zipFilePath);
                Files.delete(zipFilePath);
            }
            log.debug("[解压文件]文件 {} 解压完成", name);
        }
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
        try {
            nid = nodeService.getNodeIdByPath(uid, path);
        } catch (NoSuchFileException e) {
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

                    // @TODO 使用批量查询和求集合差级操作进行引用判断提高性能
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
        helloService.appendFeatureDetail("archiveType", "zip");
        helloService.appendFeatureDetail("extractArchiveType", "zip");
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }
}
