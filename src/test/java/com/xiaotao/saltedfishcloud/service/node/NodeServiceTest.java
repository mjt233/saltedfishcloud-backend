package com.xiaotao.saltedfishcloud.service.node;

import com.xiaotao.saltedfishcloud.po.NodeInfo;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
class NodeServiceTest {
    @Resource
    private NodeService nodeService;

    @Test
    void getNodeIdByPath() {
        NodeInfo node = nodeService.getNodeIdByPath(0, "/");
        log.info(node.toString());
    }
}
