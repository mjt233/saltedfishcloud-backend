package com.xiaotao.saltedfishcloud.service.ftp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.config.ConfigName;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.ftp.core.FtpError;
import com.xiaotao.saltedfishcloud.config.FtpConfig;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;


/**
 * FTP服务类（实验性功能，目前仅支持公共网盘只读）
 * @TODO 增加FTP服务器控制相关的方法
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FtpService implements InitializingBean {
    private final FtpServerFactory ftpServerFactory;
    private final FtpConfig ftpConfig;
    private final ConfigService configService;
    private FtpServer ftpServer;


    @EventListener(ContextClosedEvent.class)
    public void onApplicationEvent(ContextClosedEvent event) {
        stop();
    }

    /**
     * 停止FTP服务
     */
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
     * 加载参数并启动FTP服务
     */
    public void start() throws FtpException {
        if (ftpServer != null && !ftpServer.isStopped()) {
            throw new JsonException(FtpError.FTP_ALREADY_RUNNING);
        }
        log.info("[FTP]==========  FTP服务正在启动  ==========");
        ftpServer = ftpServerFactory.createServer();
        log.info("[FTP]监听地址：{}", ftpConfig.getListenAddr());
        log.info("[FTP]控制端口：{}", ftpConfig.getControlPort());
        log.info("[FTP]被动地址：{}", ftpConfig.getPassiveAddr());
        log.info("[FTP]被动端口：{}", ftpConfig.getPassivePort());
        ftpServer.start();
        log.info("[FTP]==========  FTP服务已启动    ==========");
    }

    /**
     * 重新启动FTP服务
     */
    public void restart() throws FtpException {
        if (ftpServer != null) {
            stop();
        }

        if (ftpConfig.isFtpEnable()) {
            start();
        } else {
            log.info("[FTP]FTP服务处于禁用中，启动已忽略");
        }
    }

    @Override
    public void afterPropertiesSet() throws FtpException {
        // @TODO 非法参数或参数不完整时拒绝参数更新
        // 参数更新时重新加载FTP服务器
        configService.addConfigListener(ConfigName.FTP_CONFIG, e -> {
            try {
                FtpConfig config = MapperHolder.mapper.readValue(e, FtpConfig.class);
                BeanUtils.copyProperties(config, ftpConfig);
                restart();
            } catch (JsonProcessingException | FtpException ex) {
                ex.printStackTrace();
            }
        });
        if (ftpConfig.isFtpEnable()) {
            restart();
        } else {
            log.info("[FTP]FTP服务未启用");
        }
    }
}
