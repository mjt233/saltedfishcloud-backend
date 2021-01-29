package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.dao.FileDao;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.DirCollection;
import com.xiaotao.saltedfishcloud.po.FileCacheInfo;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathBuilder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service("fileService")
public class FileService {
    @javax.annotation.Resource
    FileDao fileDao;
    @javax.annotation.Resource
    PathMapService pathMapService;
    @javax.annotation.Resource
    FileRecordService fileRecordService;
    @javax.annotation.Resource
    StoreService storeService;

    /**
     * 获取文件列表
     * 若路径不存在则抛出异常
     * 若路径指向一个文件则返回null
     * 若路径指向一个目录则返回一个List数组，数组下标0为目录，1为文件
     * @param localPath 本地文件夹路径
     * @return 一个List数组，数组下标0为目录，1为文件，或null
     * @throws FileNotFoundException 路径不存在
     */
    public List<FileInfo>[] getFileList(String localPath) throws FileNotFoundException {
        File file = new File(localPath);
        return getFileList(file);
    }
    /**
     * 获取文件列表
     * 若路径不存在则抛出异常
     * 若路径指向一个文件则返回null
     * 若路径指向一个目录则返回一个List数组，数组下标0为目录，1为文件
     * @throws FileNotFoundException 路径不存在
     * @param file 本地文件夹路径
     * @return 一个List数组，数组下标0为目录，1为文件，或null
     */
    public List<FileInfo>[] getFileList(File file) throws FileNotFoundException {
        if (!file.exists()) {
            throw new FileNotFoundException();
        }
        if (file.isFile()) {
            return null;
        }
        List<FileInfo> dirs = new LinkedList<>();
        List<FileInfo> files = new LinkedList<>();
        try {
            for (File listFile : file.listFiles()) {
                if (listFile.isDirectory()) {
                    dirs.add(new FileInfo(listFile));
                } else {
                    files.add(new FileInfo(listFile));
                }
            }
        } catch (NullPointerException e) {}
        return new List[]{dirs, files};
    }

    public ResponseEntity<Resource> sendFile(String localFilePath) throws MalformedURLException, UnsupportedEncodingException {
        UrlResource urlResource = new UrlResource(Paths.get(localFilePath).toUri());
        File file = new File(localFilePath);
        String name = file.getName();
        return ResponseEntity.ok()
                .header("Content-Type", FileUtils.getContentType(localFilePath))
                .header("Content-Disposition", "inline;filename="+ URLEncoder.encode(name, "utf-8"))
                .body(urlResource);
    }

    public List<FileCacheInfo> search(String key) {
        key = "%" + key.replaceAll("/s+", "%") + "%";
        return fileDao.search(key);
    }



    /**
     *
     * 更新公共网盘根目录的文件缓存信息
     */
    public void updateCache() {
        DirCollection dirCollection = FileUtils.deepScanDir(DiskConfig.PUBLIC_ROOT);
        final Long[] finishSize = { 0L };
        dirCollection.getFileList().forEach(file -> {
            FileInfo fileInfo = new FileInfo(file);
            Long size = fileInfo.getSize();
            fileInfo.updateMd5();
            String md5 = fileInfo.getMd5();
            String fullPath = fileInfo.getPath().substring(DiskConfig.PUBLIC_ROOT.length());
            int len = fullPath.length() - fileInfo.getName().length() - 1;
            String path = fullPath.substring(0, Math.max(1, len));
            fileDao.addRecord(0, fileInfo.getName(), size, md5, path);
            finishSize[0] += file.length();
            System.out.println(String.format("[%d%% %d/%d %s]",
                    finishSize[0]*100/dirCollection.getSize(),
                    finishSize[0],
                    dirCollection.getSize(),
                    fileInfo.getName()
            ));
        });
        dirCollection.getDirList().forEach(file -> {
            fileDao.addRecord(0, file.getName(), -1L, null, file.getPath().substring(DiskConfig.PUBLIC_ROOT.length()));
        });
    }


    /**
     * 保存上传的文件
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
        // 先保存文件
        int flag = storeService.store(uid, file.getInputStream(), requestPath, fileInfo);

        // 获取上传的文件信息 并看情况计算MD5
        if (md5 != null) {
            fileInfo.setMd5(md5);
        } else {
            fileInfo.updateMd5();
        }


        if(flag == 0) {
            return fileRecordService.addRecord(uid, file.getOriginalFilename(), fileInfo.getSize(), fileInfo.getMd5(), requestPath);
        } else {
            return fileRecordService.updateFileRecord(uid, file.getOriginalFilename(), requestPath, file.getSize(), fileInfo.getMd5());
        }
    }

    /**
     * 创建文件夹
     * @param uid 用户ID 0表示公共
     * @param path 请求的路径
     * @param name 文件夹名称
     */
    public boolean mkdir(int uid, String path, String name) {
        String localFilePath = (uid == 0 ? DiskConfig.PUBLIC_ROOT : DiskConfig.getUserPrivatePath()) + "/" + path + "/" + name;
        File file = new File(localFilePath);
        String pid = SecureUtils.getMd5(path);

        fileDao.addRecord(uid, name, (long) -1, null, pid);
        PathBuilder pb = new PathBuilder();
        pb.append(path).append(name);
        if (file.mkdir()) {
            pathMapService.setRecord(pb.toString());
            return true;
        } else {
            return false;
        }
    }

    /**
     * 删除文件
     * @param uid   用户ID 0表示公共
     * @param path  请求路径
     * @param name  文件名列表
     * @return 删除的数量
     */
    public int deleteFile(int uid, String path, List<String> name) {
        // 计数删除数
        AtomicInteger rec = new AtomicInteger();

        // 本地物理基础路径
        String basePath = (uid == 0 ? DiskConfig.PUBLIC_ROOT : DiskConfig.getUserPrivatePath()) + "/" + path;
        name.forEach(fileName -> {

            // 本地完整路径
            String local = basePath + "/" + fileName;
            File file = new File(local);
            if (file.isDirectory()) {
                DirCollection dirCollection = FileUtils.deepScanDir(local);
                dirCollection.getFileList().forEach(File::delete);
                dirCollection.getDirList().forEach(File::delete);
            }
            file.delete();
        });
        return fileRecordService.deleteRecords(uid, path, name);
    }

//    /**
//     * 移动文件
//     * @param uid 用户ID 0表示公共
//     * @param fromNode 被移动的文件或文件夹所在节点ID
//     * @param name 文件名或文件夹名
//     * @param to 移动目的地完整路径
//     * @return 1
//     */
//    public int move(int uid, String fromNode, String name, String to) {
//        FileInfo fileInfo = fileDao.getFileInfo(uid, name, fromNode);
//        if (fileInfo.isDir()) {
//
//        } else {
//
//        }
//    }
}
