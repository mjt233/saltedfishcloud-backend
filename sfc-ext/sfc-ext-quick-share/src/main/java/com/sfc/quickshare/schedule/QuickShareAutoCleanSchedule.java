package com.sfc.quickshare.schedule;

import com.sfc.quickshare.service.QuickShareService;
import com.xiaotao.saltedfishcloud.annotations.ClusterScheduleJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class QuickShareAutoCleanSchedule {
    @Autowired
    private QuickShareService quickShareService;

    @ClusterScheduleJob(value = "quick-share.clean-expired-files", describe = "自动清理过期的快速分享文件")
    @Scheduled(fixedDelay = 60000)
    public void cleanExpiredFiles() throws IOException {
        quickShareService.cleanExpiredFiles();
    }
}
