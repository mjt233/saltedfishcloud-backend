package com.xiaotao.saltedfishcloud.service.node;

import com.xiaotao.saltedfishcloud.service.file.FileService;
import lombok.var;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@RunWith(SpringRunner.class)
class NodeTreeTest {
    @Resource
    private NodeService nodeService;
    @Resource
    private FileService fileService;

    @Test
    public void testGetNode() throws IOException {
        String targetPath = "/nodetest/folder2/deepfolder";

        // 初始化环境
        fileService.mkdir(0, "/", "nodetest");
        fileService.mkdir(0, "/nodetest", "folder2");
        fileService.mkdir(0, "/nodetest/folder2", "deepfolder");

        // 获取目标路径的节点ID
        var node = nodeService.getLastNodeInfoByPath(0, targetPath);
        var fullTree = nodeService.getFullTree(0);
        var res = fullTree.getPath(node.getId());

        // 清理环境
        fileService.deleteFile(0, "/", Collections.singletonList("nodetest"));

        System.out.println("从节点树取得的路径：" + res);
        assertEquals(targetPath, res);

    }
}
