package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.service.file.FileRecordSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 用于同步本地网盘文件的信息到数据库
 */
@Component
@Slf4j
@Order(4)
public class FileDBSynchronizer implements ApplicationRunner, Runnable {
    @Resource
    private FileRecordSyncService syncService;
    @Resource
    private SysProperties sysProperties;

    private Thread thread;

    @Override
    public void run(ApplicationArguments args) {
        Thread thread = new Thread(this);
        thread.start();
        this.thread = thread;
    }

    public void doAction() throws Exception {
        syncService.doSync(User.getPublicUser().getId(), false);
    }

    /**
     * 关闭同步
     */
    public void stop() {
        this.thread.interrupt();
    }

    @Override
    public void run() {
        boolean first = true;
        while (sysProperties.getSync().getInterval() > 0) {
            final int syncDelay = sysProperties.getSync().getInterval();
            try {
                if (first && !sysProperties.getSync().isSyncOnLaunch()) {
                    first = false;
                    Thread.sleep(syncDelay *1000*60);
                }
                log.debug("开始同步文件信息");
                long start = System.currentTimeMillis();
                doAction();
                log.debug("同步完成，任务耗时：{} s", (System.currentTimeMillis() - start)/1000);
                Thread.sleep(syncDelay *1000*60);
            } catch (InterruptedException i) {
                break;
            }catch (Exception e) {
                log.warn("同步出错：{} 本轮同步任务跳过，等待下一轮 ", e.getMessage());
                try {
                    Thread.sleep(syncDelay *1000*60);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
            }
        }
        log.info("[文件系统同步器]同步线程退出");
    }
}
