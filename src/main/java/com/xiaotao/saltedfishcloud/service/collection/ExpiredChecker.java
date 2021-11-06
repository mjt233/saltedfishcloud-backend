package com.xiaotao.saltedfishcloud.service.collection;

import com.xiaotao.saltedfishcloud.dao.jpa.CollectionInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class ExpiredChecker {
    private final CollectionInfoRepository cir;
    @Scheduled(fixedRate = 60000)
    public void loopCheck() {
        try {
            cir.updateState();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
