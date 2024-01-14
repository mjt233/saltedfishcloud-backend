package com.xiaotao.saltedfishcloud;

import com.xiaotao.saltedfishcloud.model.po.NodeInfo;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.file.NoSuchFileException;
import java.util.Deque;
import java.util.LinkedList;

@SpringBootTest
@RunWith(SpringRunner.class)
public class SpringTest {
    @Autowired
    private NodeService nodeService;

    @Test
    public void doTest() throws NoSuchFileException {
        Deque<NodeInfo> pathNodeByPath = nodeService.getPathNodeByPath(0, "/");
        for (NodeInfo nodeInfo : pathNodeByPath) {
            System.out.println(nodeInfo);
        }
    }
}
