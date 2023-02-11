package com.xiaotao.saltedfishcloud.schedule;

import com.xiaotao.saltedfishcloud.annotations.ClusterScheduleJob;
import com.xiaotao.saltedfishcloud.dao.jpa.CollectionInfoRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExpiredChecker {
    private final CollectionInfoRepo cir;

    @Scheduled(fixedRate = 60000, initialDelay = 10000)
    @ClusterScheduleJob(value = "check_collection_expired", describe = "检查文件收集是否过期和更新过期状态")
    public void loopCheck() {
        try {
            cir.updateState();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
