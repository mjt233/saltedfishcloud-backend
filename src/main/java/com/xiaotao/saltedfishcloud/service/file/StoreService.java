package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.DirCollection;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.path.PathHandler;
import com.xiaotao.saltedfishcloud.service.file.path.RawPathHandler;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地文件存储服务，用于管理本地文件系统中的文件的创建，复制，删除，移动等操作
 */
@Service
public class StoreService {

    @Resource
    PathHandler pathHandler;

    /**
     * 通过输入流保存一个文件，若outputFile是文件夹 则异常
     * @param inputStream   文件的输入流
     * @param outputFile    保存到的位置
     * @return 若发生覆盖返回1 否则返回0
     * @throws HasResultException 出错
     */
    private int writeFile(InputStream inputStream, File outputFile) throws HasResultException {
        int res = outputFile.exists() ? 1 : 0;
        if (res == 1 && outputFile.isDirectory()) throw new HasResultException("已存在同名文件夹");
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
            byte[] buffer = new byte[8192];
            int len = 0;
            while ( (len = inputStream.read(buffer)) != -1 ) {
                fileOutputStream.write(buffer);
            }
            fileOutputStream.close();
            inputStream.close();
        } catch (IOException e) {
            throw new HasResultException(400, e.getMessage());
        }
        return res;
    }

    /**
     * 向系统保存一个文件
     * @param uid   用户ID 0表示公共
     * @param input 文件输入流（该方法执行完成后会自动关闭流，不需要再次关闭）
     * @param targetDir    保存到的目标网盘目录位置（注意：不是本地真是路径）
     * @param fileInfo 文件信息
     * @return 若发生覆盖返回1 否则返回0
     * @throws HasResultException 存储文件出错
     */
    public int store(int uid, InputStream input, String targetDir, FileInfo fileInfo) throws HasResultException {
        String target = pathHandler.getStorePath(uid, targetDir, fileInfo);
        return writeFile(input, new File(target));
    }

    /**
     * 文件重命名
     * @param uid   用户ID 0表示公共
     * @param path  文件所在路径
     * @param oldName 旧文件名
     * @param newName 新文件名
     */
    public void rename(int uid, String path, String oldName, String newName) throws HasResultException {
        if ( !(pathHandler instanceof RawPathHandler)){
            return;
        }
        String base = FileUtils.getFileStoreRootPath(uid);
        File origin = new File(base + "/" + path + "/" + oldName);
        File dist = new File(base + "/" + path + "/" + newName);
        if (!origin.exists()) {
            throw new HasResultException("原文件不存在");
        }
        if (dist.exists()) {
            throw new HasResultException("文件名冲突");
        }
        if (!origin.renameTo(dist)) {
            throw new HasResultException("移动失败");
        }

    }

    /**
     * 在本地文件系统中创建文件夹
     * @param uid   用户ID
     * @param path  所在路径
     * @param name  文件夹名
     * @return 是否创建成功
     */
    public boolean mkdir(int uid, String path, String name) {
        String localFilePath = FileUtils.getFileStoreRootPath(uid) + "/" + path + "/" + name;
        File file = new File(localFilePath);
        PathBuilder pb = new PathBuilder();
        pb.append(path).append(name);
        return file.mkdir();
    }

    /**
     * 删除本地文件（文件夹会连同所有子文件和目录）
     * @param uid 用户ID
     * @param path 所在路径
     * @param name 文件名
     * @return 删除的文件和文件夹总数
     */
    public long delete(int uid, String path, Collection<String> name) {
        AtomicLong cnt = new AtomicLong();
        // 本地物理基础路径
        String basePath = FileUtils.getFileStoreRootPath(uid)  + "/" + path;
        name.forEach(fileName -> {

            // 本地完整路径
            String local = basePath + "/" + fileName;
            File file = new File(local);
            if (file.isDirectory()) {
                DirCollection dirCollection = FileUtils.deepScanDir(local);
                dirCollection.getFileList().forEach(File::delete);
                dirCollection.getDirList().forEach(File::delete);
                cnt.addAndGet(dirCollection.getItemCount());
            } else {
                cnt.incrementAndGet();
            }
            file.delete();
        });
        return cnt.longValue();
    }

}
