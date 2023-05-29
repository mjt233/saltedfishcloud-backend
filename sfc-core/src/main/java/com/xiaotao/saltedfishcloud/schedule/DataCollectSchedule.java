package com.xiaotao.saltedfishcloud.schedule;

import com.xiaotao.saltedfishcloud.service.manager.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 数据采集定时任务
 */
@Component
public class DataCollectSchedule {
    @Autowired
    private AdminService adminService;

    /**
     * 每5秒采集一次系统信息
     */
    @Scheduled(fixedRate = 5000)
    @Async
    public void collectSystemInfo() {
        adminService.addSystemInfoRecord();
    }
}
