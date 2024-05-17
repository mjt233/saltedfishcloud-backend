package com.xiaotao.saltedfishcloud.schedule;


import com.xiaotao.saltedfishcloud.service.ClusterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ClusterHoldSchedule {
    @Autowired
    private ClusterService clusterService;

    /**
     * 保持集群节点注册上线
     */
    @Scheduled(fixedRate = 5000)
    public void holdCluster() {
        clusterService.registerSelf();
    }
}
