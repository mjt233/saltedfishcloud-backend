package com.xiaotao.saltedfishcloud.service.node;

import com.xiaotao.saltedfishcloud.service.user.UserService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
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
        long nid = userService.getUserByUser("xiaotao").getId();
        String node = nodeService.getNodeIdByPath(nid, "/f1");
        log.info(node);
    }
}
