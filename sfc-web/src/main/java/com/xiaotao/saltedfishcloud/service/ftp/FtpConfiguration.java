package com.xiaotao.saltedfishcloud.service.ftp;

import com.xiaotao.saltedfishcloud.config.FtpConfig;
import com.xiaotao.saltedfishcloud.service.ftp.core.DiskFtpFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.ftp.core.DiskFtpUserManager;
import com.xiaotao.saltedfishcloud.service.ftp.ftplet.FtpUploadHandler;
import lombok.RequiredArgsConstructor;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.listener.ListenerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class FtpConfiguration {
    private final FtpConfig ftpConfig;
    private final DiskFtpUserManager ftpUserManager;
    private final DiskFtpFileSystemFactory ftpFileSystemFactory;
    private final FtpUploadHandler ftpUploadHandler;

    @Bean
    public FtpServerFactory getServer() {
        ListenerFactory listenerFactory = new ListenerFactory();

        //  数据连接配置
        DataConnectionConfigurationFactory dataConnectionConfigurationFactory = new DataConnectionConfigurationFactory();
        dataConnectionConfigurationFactory.setPassiveExternalAddress(ftpConfig.getPassiveAddr());
        dataConnectionConfigurationFactory.setPassivePorts(ftpConfig.getPassivePort());

        //  控制连接配置
        ConnectionConfigFactory connectionConfigFactory = new ConnectionConfigFactory();
        connectionConfigFactory.setMaxAnonymousLogins(10);
        connectionConfigFactory.setMaxLogins(10);

        //  监听配置
        listenerFactory.setPort(ftpConfig.getControlPort());
        listenerFactory.setServerAddress(ftpConfig.getListenAddr());
        listenerFactory.setDataConnectionConfiguration(dataConnectionConfigurationFactory.createDataConnectionConfiguration());

        Map<String, Ftplet> ftplets = new HashMap<>();
        ftplets.put("upload", ftpUploadHandler);

        //  服务实例配置
        FtpServerFactory serverFactory = new FtpServerFactory();
        serverFactory.addListener("default", listenerFactory.createListener());
        serverFactory.setUserManager(ftpUserManager);
        serverFactory.setFileSystem(ftpFileSystemFactory);
        serverFactory.setFtplets(ftplets);
        return serverFactory;
    }
}
