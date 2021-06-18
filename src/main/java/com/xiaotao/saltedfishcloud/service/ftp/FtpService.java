package com.xiaotao.saltedfishcloud.service.ftp;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.springframework.stereotype.Service;


/**
 * FTP服务类（实验性功能，目前仅支持公共网盘只读）
 */
@Service
public class FtpService {
    public FtpServer getServer() throws FtpException {
        FtpServerFactory serverFactory = new FtpServerFactory();
        ListenerFactory listenerFactory = new ListenerFactory();
        ConnectionConfigFactory connectionConfigFactory = new ConnectionConfigFactory();
        listenerFactory.setPort(DiskConfig.FTP_PORT);
        listenerFactory.setServerAddress("0.0.0.0");

        connectionConfigFactory.setMaxAnonymousLogins(10);
        connectionConfigFactory.setMaxLogins(10);

        serverFactory.addListener("default", listenerFactory.createListener());

        BaseUser defaultUser = new BaseUser();
        defaultUser.setHomeDirectory(DiskConfig.PUBLIC_ROOT);
        defaultUser.setName("anonymous");
        serverFactory.getUserManager().save(defaultUser);
        return serverFactory.createServer();
    }
}
