package com.xiaotao.saltedfishcloud.schedule;

import com.xiaotao.saltedfishcloud.dao.jpa.CollectionInfoRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExpiredChecker {
    private final CollectionInfoRepo cir;
    @Scheduled(fixedRate = 60000, initialDelay = 10000)
    public void loopCheck() {
        try {
            cir.updateState();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
