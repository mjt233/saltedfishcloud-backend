package com.xiaotao.saltedfishcloud.service.ftp.ftplet;

import com.xiaotao.saltedfishcloud.dao.UserDao;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.service.ftp.DiskFtpUser;
import com.xiaotao.saltedfishcloud.service.ftp.utils.FtpPathInfo;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ftpserver.ftplet.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
public class FtpUploadHandler extends DefaultFtplet {
    private final FileService fileService;
    private final UserDao userDao;

    public FtpUploadHandler(FileService fileService, UserDao userDao) {
        this.fileService = fileService;
        this.userDao = userDao;
    }

    /**
     * 开始文件上传时获取好用户id与路径信息
     */
    @Override
    public FtpletResult onUploadStart(FtpSession session, FtpRequest request) throws FtpException, IOException {
        FtpPathInfo pathInfo = new FtpPathInfo(session.getFileSystemView().getWorkingDirectory().getAbsolutePath() + "/" + request.getArgument());
        User user = session.getUser();
        int uid = 0;
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
    public FtpletResult onUploadEnd(FtpSession session, FtpRequest request) throws FtpException, IOException {
        log.debug("upload end");

        FtpPathInfo pathInfo = (FtpPathInfo) session.getAttribute("pathInfo");
        int uid = (int) session.getAttribute("uid");
        String tmpDir = System.getProperty("java.io.tmpdir");
        String tag = uid + SecureUtils.getMd5(pathInfo.getFullPath());

        /*
          获取FTP接收的临时文件路径
         */
        Path nativePath = Paths.get(tmpDir + File.separator + tag);

        FileInfo fileInfo = FileInfo.getLocal(nativePath.toString());
        fileInfo.setName(pathInfo.getName());
        fileService.moveToSaveFile(uid, nativePath, pathInfo.getResourceParent(), fileInfo);
        return FtpletResult.DEFAULT;
    }
}
