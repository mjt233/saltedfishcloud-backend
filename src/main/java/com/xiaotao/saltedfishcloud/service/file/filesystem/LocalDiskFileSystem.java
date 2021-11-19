package com.xiaotao.saltedfishcloud.service.file.filesystem;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.mybatis.FileDao;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.entity.po.NodeInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.file.store.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.SetUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LocalDiskFileSystem implements DiskFileSystem {
    private final StoreServiceFactory storeServiceFactory;
    private final FileDao fileDao;
    private final FileRecordService fileRecordService;
    private final NodeService nodeService;

    @Override
    public boolean exist(int uid, String path) {
        return storeServiceFactory.getService().exist(uid, path);
    }

    @Override
    public Resource getResource(int uid, String path, String name) {
        return storeServiceFactory.getService().getResource(uid, path, name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String mkdirs(int uid, String path) throws IOException {
        String nid = fileRecordService.mkdirs(uid, path);
        storeServiceFactory.getService().mkdir(uid, path, "");
        return nid;
    }

    @Override
    public FileInfo getFileByMD5(String md5) throws NoSuchFileException {
        FileInfo fileInfo;
        List<FileInfo> files = fileDao.getFilesByMD5(md5, 1);
        if (files.size() == 0) throw new NoSuchFileException("文件不存在: " + md5);
        fileInfo = files.get(0);
        String path = nodeService.getPathByNode(fileInfo.getUid(), fileInfo.getNode());
        fileInfo.setPath(path + "/" + fileInfo.getName());
        fileInfo.setPath(DiskConfig.getPathHandler().getStorePath(fileInfo.getUid(), path, fileInfo));
        fileInfo.setName(md5 + "." + FileUtils.getSuffix(fileInfo.getName()));
        return fileInfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copy(int uid, String source, String target, int targetUid, String sourceName, String targetName, Boolean overwrite) throws IOException {
        fileRecordService.copy(uid, source, target, targetUid, sourceName, targetName, overwrite);
        storeServiceFactory.getService().copy(uid, source, target, targetUid, sourceName, targetName, overwrite);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {
        try {
            target = URLDecoder.decode(target, "UTF-8");
            fileRecordService.move(uid, source, target, name, overwrite);
            storeServiceFactory.getService().move(uid, source, target, name, overwrite);
        } catch (DuplicateKeyException e) {
            throw new JsonException(409, "目标目录下已存在 " + name + " 暂不支持目录合并或移动覆盖");
        } catch (UnsupportedEncodingException e) {
            throw new JsonException(400, "不支持的编码（请使用UTF-8）");
        }
    }

    @Override
    public List<FileInfo>[] getUserFileList(int uid, String path) throws IOException {
        if (uid == 0 || DiskConfig.STORE_TYPE == StoreType.RAW) {
            // 初始化用户目录
            String baseLocalPath = DiskConfig.getRawFileStoreRootPath(uid);
            File root = new File(baseLocalPath);
            if (!root.exists()) {
                if (!root.mkdir()) {
                    throw new IOException("目录" + path + "创建失败");
                }
            }
        }

        String nodeId = nodeService.getNodeIdByPath(uid, path);
        return getUserFileListByNodeId(uid, nodeId);
    }

    @Override
    public LinkedHashMap<String, List<FileInfo>> collectFiles(int uid, boolean reverse) {
        LinkedHashMap<String, List<FileInfo>> res = new LinkedHashMap<>();
        List<NodeInfo> nodes = new LinkedList<>();
        //  获取目录结构
        String strId = "" + uid;
        nodes.add(new NodeInfo(null, uid, strId, null));
        nodes.addAll(nodeService.getChildNodes(uid, strId));

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
        storeServiceFactory.getService().moveToSave(uid, nativeFilePath, path, fileInfo);
        int res = fileRecordService.addRecord(uid, fileInfo.getName(), fileInfo.getSize(), fileInfo.getMd5(), path);
        if ( res == 0) {
            fileRecordService.updateFileRecord(uid, fileInfo.getName(), path, fileInfo.getSize(), fileInfo.getMd5());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveFile(int uid, InputStream stream, String path, FileInfo fileInfo) throws IOException {

        if (fileInfo.getMd5() == null) {
            fileInfo.updateMd5();
        }
        return saveFileWithDelete(uid, stream, path, fileInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveFile(int uid, MultipartFile file, String requestPath, String md5) throws IOException {
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
        if (exist) {
            fileDao.updateRecord(uid, fileInfo.getName(), nid, fileInfo.getSize(), fileInfo.getMd5());
        } else {
            fileDao.addRecord(uid, fileInfo.getName(), fileInfo.getSize(), fileInfo.getMd5(), nid);
        }
        return exist ? SAVE_COVER : SAVE_NEW_FILE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mkdir(int uid, String path, String name) throws IOException {
        if ( !storeServiceFactory.getService().mkdir(uid, path, name) ) {
            throw new IOException("在" + path + "创建文件夹失败");
        }
        fileRecordService.mkdir(uid, name, path);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long deleteFile(int uid, String path, List<String> name) throws IOException {
        // 计数删除数
        long res = 0L;
        List<FileInfo> fileInfos = fileRecordService.deleteRecords(uid, path, name);
        res += storeServiceFactory.getService().delete(uid, path, name);

        // 唯一存储模式下删除文件后若文件不再被引用，则在存储仓库中删除
        if (DiskConfig.STORE_TYPE == StoreType.UNIQUE && fileInfos.size() > 0) {
            Set<String> all = fileInfos
                    .stream()
                    .filter(BasicFileInfo::isFile)
                    .map(BasicFileInfo::getMd5)
                    .collect(Collectors.toSet());

            if (all.size() == 0) {
                return res;
            }
            Set<String> valid = new HashSet<>(fileDao.getValidFileMD5s(all));
            Set<String> invalid = SetUtils.diff(all, valid);
            for (String md5 : invalid) {
                storeServiceFactory.getService().delete(md5);
            }
        }
        return res;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(int uid, String path, String name, String newName) throws IOException {
        fileRecordService.rename(uid, path, name, newName);
        storeServiceFactory.getService().rename(uid, path, name, newName);
    }

}
