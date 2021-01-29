package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.path.PathHandler;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.*;

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

}
