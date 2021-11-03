package com.xiaotao.saltedfishcloud.listener;

import com.xiaotao.saltedfishcloud.init.FileDBSynchronizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ftpserver.FtpServer;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CloseListener {
    private final FileDBSynchronizer synchronizer;
    @EventListener(ContextClosedEvent.class)
    public void onApplicationEvent(ContextClosedEvent event) {
        FtpServer ftpServer = event.getApplicationContext().getBean(FtpServer.class);
        if (!ftpServer.isStopped()) {
            log.info("[FTP]服务关闭中");
            ftpServer.stop();
            log.info("[FTP]服务已关闭");
        }
        synchronizer.stop();
    }
}
