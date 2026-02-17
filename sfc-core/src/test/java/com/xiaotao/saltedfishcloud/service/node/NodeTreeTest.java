package com.xiaotao.saltedfishcloud.service.node;

import com.xiaotao.saltedfishcloud.dao.jpa.FileInfoRepo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.impl.filesystem.FileRecordServiceImpl;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeTreeTest {

    @Mock
    private FileInfoRepo fileInfoRepo;

    @InjectMocks
    private FileRecordServiceImpl fileRecordService;

    @Test
    @SuppressWarnings("unchecked")
    public void testGetNode() throws IOException {
        String id1 = SecureUtils.getUUID();
        String id2 = SecureUtils.getUUID();
        String id3 = SecureUtils.getUUID();
         when(fileInfoRepo.findAll(Mockito.any(Specification.class))).thenReturn(List.of(
                 new FileInfo().setNode("").setName("nodeTest").setMd5(id1),
                 new FileInfo().setNode(id1).setName("folder2").setMd5(id2),
                 new FileInfo().setNode(id2).setName("deepFolder").setMd5(id3)
         ));
        NodeTree t = fileRecordService.getFullTree(0);
        int count = 0;
        for (FileInfo fileInfo : t) {
            count++;
        }
        assertEquals(4, count);
        assertEquals("folder2", t.getNode(id2).getName());
        assertEquals("/nodeTest/folder2/deepFolder", t.getPath(id3));
        assertEquals("/", t.getPath("0"));

    }
}
