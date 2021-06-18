package com.xiaotao.saltedfishcloud.service.ftp;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.FtpConfig;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.DataConnectionConfigurationFactory;
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
        DataConnectionConfigurationFactory dataConnectionConfigurationFactory = new DataConnectionConfigurationFactory();
        ConnectionConfigFactory connectionConfigFactory = new ConnectionConfigFactory();


        dataConnectionConfigurationFactory.setPassiveAddress(FtpConfig.PASSIVE_ADDR);
        dataConnectionConfigurationFactory.setPassiveExternalAddress(FtpConfig.PASSIVE_ADDR);
        dataConnectionConfigurationFactory.setPassivePorts(FtpConfig.PASSIVE_PORT);

        listenerFactory.setPort(FtpConfig.FTP_PORT);
        listenerFactory.setServerAddress("0.0.0.0");
        listenerFactory.setDataConnectionConfiguration(dataConnectionConfigurationFactory.createDataConnectionConfiguration());

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
