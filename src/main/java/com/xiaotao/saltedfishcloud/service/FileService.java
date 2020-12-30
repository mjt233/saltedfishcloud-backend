package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.dao.FileDao;
import com.xiaotao.saltedfishcloud.po.DirCollection;
import com.xiaotao.saltedfishcloud.po.FileCacheInfo;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

@Service("fileService")
public class FileService {
    @javax.annotation.Resource
    FileDao fileDao;

    /**
     *
     * @param localPath 本地文件路径
     * @return
     * @throws FileNotFoundException
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
     * @throws FileNotFoundException
     * @param file 本地文件
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
     * 带SQL注入漏洞的文件搜索
     * @param key 关键字
     * @return
     */
    public List<FileCacheInfo> searchWithSqlInject(String key) {
        return fileDao.searchWithSqlInject(key);
    }

    /**
     * 深度搜索遍历目录，取出文件夹下的所有文件和目录
     * @param path
     * @return
     */
    public DirCollection deepScanDir(String path) {
        LinkedList<File> detectedDirs = new LinkedList<>();
        DirCollection res = new DirCollection();
        Arrays.stream(new File(path).listFiles()).forEach(file -> {
            if (file.isDirectory()) detectedDirs.push(file);
            res.addFile(file);
        });
        while (!detectedDirs.isEmpty()) {
            File dir = detectedDirs.getLast();
            detectedDirs.removeLast();
            Arrays.stream(new File(dir.getPath()).listFiles()).forEach(file -> {
                if (file.isDirectory()) detectedDirs.addLast(file);
                res.addFile(file);
            });
        }
        return res;
    }

    /**
     *
     * 更新公共网盘根目录的文件缓存信息
     */
    public void updateCache() {
        DirCollection dirCollection = deepScanDir(DiskConfig.PUBLIC_ROOT);
        final Long[] finishSize = { 0l };
        dirCollection.getFileList().forEach(file -> {
            FileInfo fileInfo = new FileInfo(file);
            Long size = -1l;
            String md5 = "";
            if(fileInfo.getType() == FileInfo.TYPE_FILE) {
                size = fileInfo.getSize();
                md5 = fileInfo.computeMd5();
                finishSize[0] += size;
            }
            fileDao.addCache(fileInfo.getName(), fileInfo.getPath().substring(DiskConfig.PUBLIC_ROOT.length()), size, md5);
            System.out.println(String.format("[%d%% %d/%d %s]",
                    finishSize[0]*100/dirCollection.getSize(),
                    finishSize[0],
                    dirCollection.getSize(),
                    fileInfo.getName()
            ));
        });
    }

    /**
     * 保存用户上传的文件，文件发生覆盖返回1，否则返回0
     * @param localFilePath
     * @param file
     * @return
     * @throws IOException
     */
    public int saveUploadFile(String localFilePath, MultipartFile file) throws IOException {
        int flag = 0;
        File f = new File(localFilePath);
        if (f.exists()) {
            flag = 1;
            f.delete();
        }
        file.transferTo(f);
        return flag;
    }

    /**
     * 从网盘中删除一个文件
     * @param localFilePath 本地文件路径
     * @return
     */
    public void deleteFile(String localFilePath) {
        File file = new File(localFilePath);
        if (file.isDirectory()) {
            DirCollection dirCollection = deepScanDir(localFilePath);
            dirCollection.getDirList().addLast(file);
            dirCollection.getFileList().forEach(File::delete);
            dirCollection.getDirList().forEach(File::delete);
        } else {
            file.delete();
        }
    }
}
