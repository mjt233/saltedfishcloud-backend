package com.xiaotao.saltedfishcloud.service.node;

import com.xiaotao.saltedfishcloud.po.NodeInfo;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.nio.file.NoSuchFileException;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
class NodeServiceTest {
    @Resource
    private NodeService nodeService;
    @Resource
    private UserService userService;

    @Test
    void getLastNodeInfoByPath() throws NoSuchFileException {
        int nid = userService.getUserByUser("xiaotao").getId();
        NodeInfo node = nodeService.getLastNodeInfoByPath(nid, "/f1");
        log.info(node.toString());
    }
}
