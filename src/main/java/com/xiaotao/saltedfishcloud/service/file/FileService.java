package com.xiaotao.saltedfishcloud.service.file;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.FileDao;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.po.NodeInfo;
import com.xiaotao.saltedfishcloud.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.po.file.FileDCInfo;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.SetUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 对网盘文件的增删改查操作
 */
@Service("fileService")
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class FileService {
    @javax.annotation.Resource
    FileDao fileDao;
    @javax.annotation.Resource
    FileRecordService fileRecordService;
    @javax.annotation.Resource
    StoreService storeService;
    @javax.annotation.Resource
    NodeService nodeService;

    /**
     * 通过文件MD5获取一个存储在系统中的文件<br>
     * @param md5   文件MD5值
     * @return      文件信息，path为本地文件系统中的实际存储文件路径，文件名将被重命名为md5+原文件拓展名
     * @throws NoSuchFileException  没有文件时抛出
     */
    public FileInfo getFileByMD5(String md5) throws NoSuchFileException {
        FileInfo fileInfo = null;
        List<FileInfo> files = fileDao.getFilesByMD5(md5, 1);
        if (files.size() == 0) throw new NoSuchFileException("文件不存在: " + md5);
        fileInfo = files.get(0);
        String path = nodeService.getPathByNode(fileInfo.getUid(), fileInfo.getNode());
        fileInfo.setPath(path + "/" + fileInfo.getName());
        fileInfo.setPath(DiskConfig.getPathHandler().getStorePath(fileInfo.getUid(), path, fileInfo));
        fileInfo.setName(md5 + "." + FileUtils.getSuffix(fileInfo.getName()));
        return fileInfo;
    }

    /**
     * 复制指定用户的文件或目录到指定用户的某个目录下
     * @param uid
     *      原资源的用户ID
     * @param source
     *      要操作的文件所在的网盘目录
     * @param target
     *      复制到的目的地目录
     * @param targetUid
     *      目标用户ID
     * @param sourceName
     *      源文件或目录名
     * @param targetName
     *      目标文件或目录名，
     * @param overwrite
     *      是否覆盖
     */
    public void copy(int uid, String source, String target, int targetUid, String sourceName, String targetName, Boolean overwrite) throws IOException {
        if (PathBuilder.formatPath(source).equals(PathBuilder.formatPath(target)) && sourceName.equals(targetName)) {
            throw new IllegalArgumentException("无法原地复制");
        }
        fileRecordService.copy(uid, source, target, targetUid, sourceName, targetName,overwrite);
        log.debug("Finish DB data copy");
        storeService.copy(uid, source, target, targetUid, sourceName, targetName, overwrite);
        log.debug("Finish local filesystem data copy");
    }

    /**
     * 移动网盘中的文件或目录到指定目录下
     * @param uid       用户ID
     * @param source    要被移动的网盘文件或目录所在目录
     * @param target    要移动到的目标目录
     * @param name      文件名
     * @param overwrite 是否覆盖原文件
     */
    public void move(int uid, String source, String target, String name, boolean overwrite) {
        try {
            target = URLDecoder.decode(target, "UTF-8");
            if (PathBuilder.formatPath(target).equals(PathBuilder.formatPath(source))) {
                throw new IllegalArgumentException("无法原地移动");
            }
            fileRecordService.move(uid, source, target, name, overwrite);
            storeService.move(uid, source, target, name, overwrite);
        } catch (DuplicateKeyException e) {
            throw new HasResultException(409, "目标目录下已存在 " + name + " 暂不支持目录合并或移动覆盖");
        } catch (UnsupportedEncodingException e) {
            throw new HasResultException(400, "不支持的编码（请使用UTF-8）");
        } catch (IllegalArgumentException e) {
            throw new HasResultException(422, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw new HasResultException(404, "资源不存在");
        }
    }

    /**
     * 获取某个用户网盘目录下的所有文件信息
     * 若路径不存在则抛出异常
     * 若路径指向一个文件则返回null
     * 若路径指向一个目录则返回一个集合，数组下标0为目录，1为文件
     * @param uid   用户ID
     * @param path  网盘路径
     * @return      一个List数组，数组下标0为目录，1为文件，或null
     */
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

        NodeInfo nodeId = nodeService.getLastNodeInfoByPath(uid, path);
        return getUserFileListByNodeId(uid, nodeId.getId());
    }

    /**
     * 获取用户所有文件信息<br>
     * 默认正序为根目录优先，倒序为最深级目录优先
     * @param uid   用户ID
     * @param reverse 目录排序倒序
     * @return      文件信息集合，key为目录名，value为该目录下的文件信息列表
     */
    public LinkedHashMap<String, List<FileInfo>> collectFiles(int uid, boolean reverse) {
        LinkedHashMap<String, List<FileInfo>> res = new LinkedHashMap<>();
        List<NodeInfo> nodes = new LinkedList<>();
        //  获取目录结构
        nodes.add(new NodeInfo(null, uid, "root", null));
        nodes.addAll(nodeService.getChildNodes(uid, "root"));

        if (reverse) Collections.reverse(nodes);
        for (NodeInfo node : nodes) {
            String dir = nodeService.getPathByNode(uid, node.getId());
            res.put(dir, fileDao.getFileListByNodeId(uid, node.getId()));
        }
        return res;
    }

    /**
     * 通过节点ID获取节点下的文件信息
     * @param uid       用户ID
     * @param nodeId    节点ID
     * @return          一个List数组，数组下标0为目录，1为文件，或null
     */
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

    /**
     * 通过一个本地路径获取获取该路径下的所有文件列表并区分文件与目录
     * 若路径不存在则抛出异常
     * 若路径指向一个文件则返回null
     * 若路径指向一个目录则返回一个集合，数组下标0为目录，1为文件
     * @param localPath 本地文件夹路径
     * @return 一个List数组，数组下标0为目录，1为文件，或null
     * @throws FileNotFoundException 路径不存在
     */
    public Collection<? extends FileInfo>[] getFileList(String localPath) throws FileNotFoundException {
        File file = new File(localPath);
        return getFileList(file);
    }
    /**
     * 通过一个本地路径获取获取该路径下的所有文件列表并区分文件与目录
     * 若路径不存在则抛出异常
     * 若路径指向一个文件则返回null
     * 若路径指向一个目录则返回一个List数组，数组下标0为目录，1为文件
     * @throws FileNotFoundException 路径不存在
     * @param file 本地文件夹路径
     * @return 一个List数组，数组下标0为目录，1为文件，或null
     */
    @SuppressWarnings("unchecked")
    public Collection<? extends FileInfo>[] getFileList(File file) throws FileNotFoundException {
        if (!file.exists()) {
            throw new FileNotFoundException();
        }
        if (file.isFile()) {
            return null;
        }
        List<FileInfo> dirs = new LinkedList<>();
        List<FileInfo> files = new LinkedList<>();
        try {
            for (File listFile : Objects.requireNonNull(file.listFiles())) {
                if (listFile.isDirectory()) {
                    dirs.add(new FileInfo(listFile));
                } else {
                    files.add(new FileInfo(listFile));
                }
            }
        } catch (NullPointerException e) {
            // do nothing
        }
        return new List[]{dirs, files};
    }



    public List<FileInfo> search(int uid, String key) {
        key = "%" + key.replaceAll("%", "\\%").replaceAll("/s+", "%") + "%";
        return fileDao.search(uid, key);
    }


    /**
     * 保存上传的文件到网盘系统中
     * @param uid         用户ID 0表示公共
     * @param file        接收到的文件对象
     * @param requestPath 请求的文件路径
     * @param md5         请求时传入的文件md5
     * @return 1
     * @throws IOException 本地文件写入失败时抛出
     * @throws HasResultException 文件夹同名时抛出
     */
    public int saveFile(int uid,
                        MultipartFile file,
                        String requestPath,
                        String md5) throws IOException, HasResultException {

        FileInfo fileInfo = new FileInfo(file);
        // 获取上传的文件信息 并看情况计算MD5
        if (md5 != null) {
            fileInfo.setMd5(md5);
        } else {
            fileInfo.updateMd5();
        }

        storeService.store(uid, file.getInputStream(), requestPath, fileInfo);

        int res = fileRecordService.addRecord(uid, file.getOriginalFilename(), fileInfo.getSize(), fileInfo.getMd5(), requestPath);
        if ( res == 0) {
            return fileRecordService.updateFileRecord(uid, file.getOriginalFilename(), requestPath, file.getSize(), fileInfo.getMd5());
        } else {
            return res;
        }
    }

    /**
     * 创建文件夹
     * @param uid 用户ID 0表示公共
     * @param path 请求的路径
     * @param name 文件夹名称
     * @throws NoSuchFileException 当目标目录不存在时抛出
     */
    public void mkdir(int uid, String path, String name) throws HasResultException, NoSuchFileException {
        if ( !storeService.mkdir(uid, path, name) ) {
            throw new HasResultException("在" + path + "创建文件夹失败");
        }
        fileRecordService.mkdir(uid, name, path);
    }

    /**
     * 删除文件
     * @param uid   用户ID 0表示公共
     * @param path  请求路径
     * @param name  文件名列表
     * @throws NoSuchFileException 当目标路径不存在时抛出
     * @return 删除的数量
     */
    public long deleteFile(int uid, String path, List<String> name) throws IOException {
        // 计数删除数
        long res = 0L;
        List<FileInfo> fileInfos = fileRecordService.deleteRecords(uid, path, name);
        res += storeService.delete(uid, path, name);
        if (uid != 0 && DiskConfig.STORE_TYPE == StoreType.UNIQUE && fileInfos.size() > 0) {
            Set<String> all = fileInfos.stream().filter(BasicFileInfo::isFile).map(BasicFileInfo::getMd5).collect(Collectors.toSet());
            if (all.size() == 0) {
                return res;
            }
            Set<String> valid = new HashSet<>(fileDao.getValidFileMD5s(all));
            Set<String> invalid = SetUtils.diff(all, valid);
            for (String md5 : invalid) {
                storeService.delete(md5);
            }
        }
        return res;
    }

    /**
     * 重命名文件或目录
     * @param uid 用户ID 0表示公共
     * @param path 文件所在路径（相对用户网盘目录）
     * @param name 被操作的文件名或文件夹名
     * @throws NoSuchFileException 当目标路径不存在时抛出
     * @param newName 新文件名
     */
    public void rename(int uid, String path, String name, String newName) throws HasResultException, NoSuchFileException {
        fileRecordService.rename(uid, path, name, newName);
        storeService.rename(uid, path, name, newName);
    }

    /**
     * 获取网盘中文件的下载码
     * @param uid 用户ID
     * @param path 文件所在网盘目录
     * @param fileInfo 文件信息
     * @param expr  下载码有效时长（单位：天），若小于0，则无限制
     */
    public String getFileDC(int uid, String path, BasicFileInfo fileInfo, int expr) throws JsonProcessingException {
        Path localPath = Paths.get(DiskConfig.getPathHandler().getStorePath(uid, path, fileInfo));
        if ( !Files.exists(localPath) ){
            throw new HasResultException(404, "文件不存在");
        }
        FileDCInfo info = new FileDCInfo();
        info.setDir(path);
        info.setMd5(fileInfo.getMd5());
        info.setName(fileInfo.getName());
        info.setUid(uid);
        String token = JwtUtils.generateToken(new ObjectMapper().writeValueAsString(info), expr < 0 ? expr : expr*60*60*24);
        return token;
    }

}
