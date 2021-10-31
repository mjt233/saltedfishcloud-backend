package com.xiaotao.saltedfishcloud.service.ftp;

import com.xiaotao.saltedfishcloud.config.FtpConfig;
import com.xiaotao.saltedfishcloud.service.ftp.ftplet.FtpUploadHandler;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.listener.ListenerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;


/**
 * FTP服务类（实验性功能，目前仅支持公共网盘只读）
 */
@Service
public class FtpService {
    private final DiskFtpUserManager ftpUserManager;
    private final DiskFtpFileSystemFactory ftpFileSystemFactory;
    private final FtpUploadHandler ftpUploadHandler;

    public FtpService(
        FtpConfig ftpConfig, // 仅声明依赖关系，FtpService依赖FtpConfig，否则FtpConfig被调用时可能未被Spring装配
        DiskFtpUserManager ftpUserManager,
        DiskFtpFileSystemFactory ftpFileSystemFactory,
        FtpUploadHandler ftpUploadHandler
    ) {
        this.ftpUserManager = ftpUserManager;
        this.ftpFileSystemFactory = ftpFileSystemFactory;
        this.ftpUploadHandler = ftpUploadHandler;
    }

    @Bean
    public FtpServer getServer() throws FtpException {
        ListenerFactory listenerFactory = new ListenerFactory();

        //  数据连接配置
        DataConnectionConfigurationFactory dataConnectionConfigurationFactory = new DataConnectionConfigurationFactory();
        dataConnectionConfigurationFactory.setPassiveExternalAddress(FtpConfig.PASSIVE_ADDR);
        dataConnectionConfigurationFactory.setPassivePorts(FtpConfig.PASSIVE_PORT);

        //  控制连接配置
        ConnectionConfigFactory connectionConfigFactory = new ConnectionConfigFactory();
        connectionConfigFactory.setMaxAnonymousLogins(10);
        connectionConfigFactory.setMaxLogins(10);

        //  监听配置
        listenerFactory.setPort(FtpConfig.FTP_PORT);
        listenerFactory.setServerAddress("0.0.0.0");
        listenerFactory.setDataConnectionConfiguration(dataConnectionConfigurationFactory.createDataConnectionConfiguration());

        Map<String, Ftplet> ftplets = new HashMap<>();
        ftplets.put("upload", ftpUploadHandler);

        //  服务实例配置
        FtpServerFactory serverFactory = new FtpServerFactory();
        serverFactory.addListener("default", listenerFactory.createListener());
        serverFactory.setUserManager(ftpUserManager);
        serverFactory.setFileSystem(ftpFileSystemFactory);
        serverFactory.setFtplets(ftplets);
        return serverFactory.createServer();
    }
}
