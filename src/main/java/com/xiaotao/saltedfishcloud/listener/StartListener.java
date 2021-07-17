package com.xiaotao.saltedfishcloud.listener;

import lombok.extern.slf4j.Slf4j;
import org.apache.ftpserver.FtpServer;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StartListener {
    @EventListener(ContextClosedEvent.class)
    public void onApplicationEvent(ContextClosedEvent event) throws InterruptedException {
        log.info("[FTP]服务关闭中");
        event.getApplicationContext().getBean(FtpServer.class).stop();
        log.info("[FTP]服务已关闭");

    }
}
