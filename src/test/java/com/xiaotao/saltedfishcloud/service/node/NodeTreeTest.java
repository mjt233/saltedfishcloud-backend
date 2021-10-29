package com.xiaotao.saltedfishcloud.service.node;

import com.xiaotao.saltedfishcloud.po.NodeInfo;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystemFactory;
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
    private DiskFileSystemFactory fileService;

    @Test
    public void testGetNode() throws IOException {
        String targetPath = "/nodetest/folder2/deepfolder";

        DiskFileSystem fileService = this.fileService.getFileSystem();
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

    @Test
    public void testIterator() {
        var tree = nodeService.getFullTree(0);
        for (NodeInfo nodeInfo : tree) {
            System.out.print(nodeInfo.getName() + " ");
        }
        System.out.println();
        tree.forEach(System.out::println);
    }
}
