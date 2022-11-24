package com.xiaotao.saltedfishcloud.service.ftp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.service.config.SysConfigName;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.ftp.core.DiskFtpFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.ftp.core.DiskFtpUserManager;
import com.xiaotao.saltedfishcloud.service.ftp.ftplet.FtpUploadHandler;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ftpserver.*;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.listener.ListenerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * FTP服务类（实验性功能）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FtpServiceImpl implements ApplicationRunner, InitializingBean ,FtpService {
    private final SysProperties sysProperties;
    private final DiskFtpUserManager ftpUserManager;
    private final DiskFtpFileSystemFactory ftpFileSystemFactory;
    private final FtpUploadHandler ftpUploadHandler;
    private final ConfigService configService;
    private FtpServer ftpServer;
    private SysProperties.Ftp ftpProperties;

    @Override
    public void afterPropertiesSet() throws Exception {
        ftpProperties = sysProperties.getFtp();
    }

    @EventListener(ContextClosedEvent.class)
    public void onApplicationEvent(ContextClosedEvent event) {
        stop();
    }

    @Override
    public boolean isRunning() {
        return ftpServer != null && !ftpServer.isStopped() && !ftpServer.isSuspended();
    }

    /**
     * 停止FTP服务。
     * 若没有正在运行的FTP服务，则会忽略本次操作。
     */
    @Override
    public void stop() {
        if (ftpServer != null && !ftpServer.isStopped()) {
            log.info("[FTP]服务关闭中");
            ftpServer.stop();
            log.info("[FTP]服务已关闭");
            ftpServer = null;
        } else {
            log.warn("[FTP]服务未启动，无需关闭");
        }
    }

    /**
     * 加载参数并启动FTP服务。若服务器已在运行，则会忽略该动作
     */
    @Override
    public void start() throws FtpException {
        if (ftpServer != null && !ftpServer.isStopped()) {
            log.warn("[FTP]服务已经在运行中...");
            return;
        }
        log.info("[FTP]==========  FTP服务正在启动  ==========");
        ftpServer = createServer();
        log.info("[FTP]监听地址：{}", ftpProperties.getListenAddr());
        log.info("[FTP]控制端口：{}", ftpProperties.getControlPort());
        log.info("[FTP]被动地址：{}", ftpProperties.getPassiveAddr());
        log.info("[FTP]被动端口：{}", ftpProperties.getPassivePort());
        ftpServer.start();
        log.info("[FTP]==========  FTP服务已启动    ==========");
    }

    /**
     * 重新启动FTP服务。
     * 如果已有一个运行中的FTP服务则会先将其关闭。
     */
    @Override
    public void restart() throws FtpException {
        if (ftpServer != null) {
            stop();
        }

        if (ftpProperties.isFtpEnable()) {
            start();
        } else {
            log.info("[FTP]FTP服务处于禁用中，启动已忽略");
        }
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        String configRawJson = configService.getConfig(SysConfigName.Common.FTP_PROPERTIES);
        if (configRawJson != null) {
            try {
                final SysProperties.Ftp config = MapperHolder.mapper.readValue(configRawJson, SysProperties.Ftp.class);
                BeanUtils.copyProperties(config, ftpProperties);
                log.info("[FTP]已加载配置");
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        } else {
            configService.setConfig(SysConfigName.Common.FTP_PROPERTIES, MapperHolder.mapper.writeValueAsString(ftpProperties));
            log.info("[FTP]初始化FTP配置");
        }
        // 参数更新时重新加载FTP服务器
        configService.addBeforeSetListener(SysConfigName.Common.FTP_PROPERTIES, e -> {
            SysProperties.Ftp originProperties = new SysProperties.Ftp();
            BeanUtils.copyProperties(ftpProperties, originProperties);
            try {
                SysProperties.Ftp config = MapperHolder.mapper.readValue(e, SysProperties.Ftp.class);
                BeanUtils.copyProperties(config, ftpProperties);
                restart();
            } catch (Exception ex) {
                log.warn("[FTP]配置更新失败，还原后重新启动");
                BeanUtils.copyProperties(originProperties, ftpProperties);
                try {
                    restart();
                } catch (Exception exc) {
                    log.error("[FTP]恢复启动失败", exc);
                    throw new RuntimeException(ex.getMessage(), exc);
                }
                throw new RuntimeException(ex.getMessage(), ex);
            }
        });
        if (ftpProperties.isFtpEnable()) {
            try {
                restart();
            } catch (FtpException | FtpServerConfigurationException e) {
                e.printStackTrace();
                this.ftpServer = null;
                log.error("[FTP]服务启动失败，原因：{}", e.getMessage());
            }
        } else {
            log.info("[FTP]FTP服务未启用");
        }
    }


    /**
     * 创建一个FTP服务器实例
     */
    protected FtpServer createServer() {
        ListenerFactory listenerFactory = new ListenerFactory();

        //  数据连接配置
        DataConnectionConfigurationFactory dataConnectionConfigurationFactory = new DataConnectionConfigurationFactory();
        dataConnectionConfigurationFactory.setPassiveExternalAddress(ftpProperties.getPassiveAddr());
        dataConnectionConfigurationFactory.setPassivePorts(ftpProperties.getPassivePort());

        //  控制连接配置
        ConnectionConfigFactory connectionConfigFactory = new ConnectionConfigFactory();
        connectionConfigFactory.setMaxAnonymousLogins(64);
        connectionConfigFactory.setMaxLogins(64);
        connectionConfigFactory.setMaxThreads(64);

        //  监听配置
        listenerFactory.setPort(ftpProperties.getControlPort());
        listenerFactory.setServerAddress(ftpProperties.getListenAddr());
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
