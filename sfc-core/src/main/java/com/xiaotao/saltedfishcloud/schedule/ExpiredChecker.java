package com.xiaotao.saltedfishcloud.schedule;

import com.xiaotao.saltedfishcloud.annotations.ClusterScheduleJob;
import com.xiaotao.saltedfishcloud.dao.jpa.CollectionInfoRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredChecker {
    private final CollectionInfoRepo cir;

    @Scheduled(fixedRate = 60000, initialDelay = 10000)
    @ClusterScheduleJob(value = "check_collection_expired", describe = "检查文件收集是否过期和更新过期状态")
    public void loopCheck() {
        try {
            cir.updateState(new Date());
        } catch (Exception e) {
            log.error("文件收集自动过期更新出错", e);
        }
    }
}
