package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.dao.jpa.FileInfoRepo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FileRecordServiceImplTest {
    @Mock
    private FileInfoRepo fileInfoRepo;

    @InjectMocks
    private FileRecordServiceImpl fileRecordService;

    @Test
    void listChildDirs() {
        /* 模拟文件结构
        a1
         a1b1
          a1b1c1
          a1b1c2
         a1b2
          a1b2c1
          a1b2c2
        a2
         a2b1
          a2b1c1
          a2b1c2
         a2b2
          a2b2c1
          a2b2c2
        a3
         a3b1
          a3b1c1
          a3b1c2
         a3b2
          a3b2c1
           a3b1c1d1
           a3b1c1d2
          a3b2c2
        */
        String[] idArray = new String[23];
        for (int i = 0; i < idArray.length; i++) {
            idArray[i] = SecureUtils.getUUID();
        }
        Map<String, List<FileInfo>> virtualDirMap = Stream.of(
                // === a1 组 (索引 0-6) ===
                new FileInfo().setSize(-1L).setName("a1").setMd5(idArray[0]).setNode("0"),
                new FileInfo().setSize(-1L).setName("a1b1").setMd5(idArray[1]).setNode(idArray[0]),
                new FileInfo().setSize(-1L).setName("a1b2").setMd5(idArray[2]).setNode(idArray[0]),
                new FileInfo().setSize(-1L).setName("a1b1c1").setMd5(idArray[3]).setNode(idArray[1]),
                new FileInfo().setSize(-1L).setName("a1b1c2").setMd5(idArray[4]).setNode(idArray[1]),
                new FileInfo().setSize(-1L).setName("a1b2c1").setMd5(idArray[5]).setNode(idArray[2]),
                new FileInfo().setSize(-1L).setName("a1b2c2").setMd5(idArray[6]).setNode(idArray[2]),

                // === a2 组 (索引 7-13) ===
                new FileInfo().setSize(-1L).setName("a2").setMd5(idArray[7]).setNode("0"),
                new FileInfo().setSize(-1L).setName("a2b1").setMd5(idArray[8]).setNode(idArray[7]),
                new FileInfo().setSize(-1L).setName("a2b2").setMd5(idArray[9]).setNode(idArray[7]),
                new FileInfo().setSize(-1L).setName("a2b1c1").setMd5(idArray[10]).setNode(idArray[8]),
                new FileInfo().setSize(-1L).setName("a2b1c2").setMd5(idArray[11]).setNode(idArray[8]),
                new FileInfo().setSize(-1L).setName("a2b2c1").setMd5(idArray[12]).setNode(idArray[9]),
                new FileInfo().setSize(-1L).setName("a2b2c2").setMd5(idArray[13]).setNode(idArray[9]),

                // === a3 组 (索引 14-20) ===
                new FileInfo().setSize(-1L).setName("a3").setMd5(idArray[14]).setNode("0"),
                new FileInfo().setSize(-1L).setName("a3b1").setMd5(idArray[15]).setNode(idArray[14]),
                new FileInfo().setSize(-1L).setName("a3b2").setMd5(idArray[16]).setNode(idArray[14]),
                new FileInfo().setSize(-1L).setName("a3b1c1").setMd5(idArray[17]).setNode(idArray[15]),
                new FileInfo().setSize(-1L).setName("a3b1c2").setMd5(idArray[18]).setNode(idArray[15]),
                new FileInfo().setSize(-1L).setName("a3b2c1").setMd5(idArray[19]).setNode(idArray[16]),
                new FileInfo().setSize(-1L).setName("a3b2c2").setMd5(idArray[20]).setNode(idArray[16]),
                new FileInfo().setSize(-1L).setName("a3b1c1d1").setMd5(idArray[21]).setNode(idArray[19]),
                new FileInfo().setSize(-1L).setName("a3b1c1d2").setMd5(idArray[22]).setNode(idArray[19])
        ).collect(Collectors.groupingBy(FileInfo::getNode));

        Mockito.when(fileInfoRepo.findDirByUidAndNode(Mockito.anyLong(), Mockito.anyString()))
                        .thenAnswer(i -> {
                            String node = i.getArgument(1, String.class);
                            return virtualDirMap.get(node);
                        });

        assertEquals(8, fileRecordService.listChildDirs(0, idArray[14], -1).size());
        assertEquals(2, fileRecordService.listChildDirs(0, idArray[14], 0).size());
        List<FileInfo> r = fileRecordService.listChildDirs(0, idArray[14], 1);
        assertTrue(r.stream().anyMatch(e -> e.getName().equals("a3b2c1")));
        assertEquals(6, r.size());
        assertEquals(8, fileRecordService.listChildDirs(0, idArray[14], 2).size());

        assertEquals(3, fileRecordService.listChildDirs(0, "0", 0).size());
    }
}