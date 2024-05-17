package com.saltedfishcloud.ext.ftpserver.ftplet;

import com.saltedfishcloud.ext.ftpserver.core.DiskFtpUser;
import com.saltedfishcloud.ext.ftpserver.utils.FtpPathInfo;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ftpserver.ftplet.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class FtpUploadHandler extends DefaultFtplet {
    private final static String LOG_PREFIX = "[FtpLet]";

    @Autowired
    private DiskFileSystemManager fileService;

    @Autowired
    private UserDao userDao;

    /**
     * 开始文件上传时获取好用户id与路径信息
     */
    @Override
    public FtpletResult onUploadStart(FtpSession session, FtpRequest request) throws FtpException {
        FtpPathInfo pathInfo = new FtpPathInfo(session.getFileSystemView().getWorkingDirectory().getAbsolutePath() + "/" + request.getArgument());
        User user = session.getUser();
        long uid = 0;
        if (!pathInfo.isPublicArea()) {
            if (user instanceof DiskFtpUser) {
                uid = ((DiskFtpUser) user).getId();
            } else {
                uid = userDao.getUserByUser(user.getName()).getId();
            }
        }
        session.setAttribute("pathInfo", pathInfo);
        session.setAttribute("uid", uid);
        return FtpletResult.DEFAULT;
    }


    /**
     * 完成上传时更新文件表信息
     */
    @Override
    public FtpletResult onUploadEnd(FtpSession session, FtpRequest request) throws IOException {
        log.debug("upload end");

        FtpPathInfo pathInfo = (FtpPathInfo) session.getAttribute("pathInfo");
        long uid = TypeUtils.toLong(session.getAttribute("uid"));
        String tmpDir = System.getProperty("java.io.tmpdir");
        String tag = uid + SecureUtils.getMd5(pathInfo.getFullPath());

        /*
          获取FTP接收的临时文件路径
         */
        Path nativePath = Paths.get(tmpDir + File.separator + tag);
        if (!Files.exists(nativePath)) {
            log.debug("{}不存在对应的临时本地文件", LOG_PREFIX);
            return FtpletResult.DEFAULT;
        }
        FileInfo fileInfo = FileInfo.getLocal(nativePath.toString());
        fileInfo.setName(pathInfo.getName());
        fileService.getMainFileSystem().moveToSaveFile(uid, nativePath, pathInfo.getResourceParent(), fileInfo);
        return FtpletResult.DEFAULT;
    }
}
