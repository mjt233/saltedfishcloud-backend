package com.xiaotao.saltedfishcloud.service.ftp.listener;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ftpserver.FtpServer;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CloseFtpListener {
    private final FtpServer ftpServer;
    @EventListener(ContextClosedEvent.class)
    public void onApplicationEvent(ContextClosedEvent event) {
        if (!ftpServer.isStopped()) {
            log.info("[FTP]服务关闭中");
            ftpServer.stop();
            log.info("[FTP]服务已关闭");
        }
    }
}
