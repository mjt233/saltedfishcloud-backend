package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.dao.FileDao;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.DirCollection;
import com.xiaotao.saltedfishcloud.po.FileCacheInfo;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service("fileService")
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
    public void mkdir(int uid, String path, String name) throws HasResultException {
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
     * @return 删除的数量
     */
    public long deleteFile(int uid, String path, List<String> name) {
        // 计数删除数
        long res = 0L;
        res += storeService.delete(uid, path, name);
        fileRecordService.deleteRecords(uid, path, name);
        return res;
    }

    /**
     * 移动文件
     * @TODO 待完成，用于移动或重命名文件/文件夹
     * @param uid 用户ID 0表示公共
     * @param from 被移动的文件或文件夹所在目录（相对用户根目录）
     * @param name 被操作的文件名或文件夹名
     * @param to 被移动到的目录（相对用户根目录）
     * @param newName 新文件名
     */
    public void move(int uid, String from, String name, String to, String newName) {
        storeService.move(uid, from, name, to, newName);
//        String originLocalPath =
//        if (fileInfo.isDir()) {
//
//        } else {
//            storeService.move(uid, f);
//        }
    }
}
