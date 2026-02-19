package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.dao.jpa.FileInfoRepo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.test.MockFileSystem;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static com.xiaotao.saltedfishcloud.test.MockFileSystem.createDir;
import static com.xiaotao.saltedfishcloud.test.MockFileSystem.createFile;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FileRecordServiceImplTest {
    @Mock
    private FileInfoRepo fileInfoRepo;

    @InjectMocks
    private FileRecordServiceImpl fileRecordService;

    private final MockFileSystem mockFileSystem = new MockFileSystem();

    static String ROOT_ID = "0";


    /**
     * 生成模拟的文件结构列表
     * <pre>
     * .
     * ├── a1
     * │   ├── a1b1
     * │   │   ├── a1b1c1
     * │   │   └── a1b1c2
     * │   └── a1b2
     * │       ├── a1b2c1
     * │       └── a1b2c2
     * ├── a2
     * │   ├── a2b1
     * │   │   ├── a2b1c1
     * │   │   └── a2b1c2
     * │   └── a2b2
     * │       ├── a2b2c1
     * │       └── a2b2c2
     * └── a3
     *     ├── a3b1
     *     │   ├── a3b1c1
     *     │   │   ├── a3b1c1d1
     *     │   │   └── a3b1c1d2
     *     │   └── a3b1c2
     *     └── a3b2
     *         ├── a3b2c1
     *         └── a3b2c2
     * </pre>
     */
    {
        mockFileSystem.registerMockFile(createDir("a1", ROOT_ID)).andThen(a1 -> {
            a1.addSubDir("a1b1").andThen(a1b1 -> {
                a1b1.addSubDir("a1b1c1");
                a1b1.addSubDir("a1b1c2");
            });
            a1.addSubDir("a1b2").andThen(a1b2 -> {
                a1b2.addSubDir("a1b2c1");
                a1b2.addSubDir("a1b2c2");
            });
        });


        mockFileSystem.registerMockFile(createDir("a2", ROOT_ID)).andThen(a2 -> {
            a2.addSubDir("a2b1").andThen(a2b1 -> {
                a2b1.addSubDir("a2b1c1");
                a2b1.addSubDir("a2b1c2");
            });
            a2.addSubDir("a2b2").andThen(a2b2 -> {
                a2b2.addSubDir("a2b2c1");
                a2b2.addSubDir("a2b2c2");
            });
        });

        mockFileSystem.registerMockFile(createDir("a3", ROOT_ID)).andThen(a3 -> {
            a3.addSubDir("a3b1").andThen(a3b1 -> {
                a3b1.addSubDir("a3b1c1").andThen(a3b1c1 -> {
                    a3b1c1.addSubDir("a3b1c1d1");
                    a3b1c1.addSubDir("a3b1c1d2");
                });
                a3b1.addSubDir("a3b1c2");
            });
            a3.addSubDir("a3b2").andThen(a3b2 -> {
                a3b2.addSubDir("a3b2c1");
                a3b2.addSubDir("a3b2c2");
            });
        });
    }

    @Test
    @DisplayName("测试子目录遍历")
    void listChildDirs() {

        Mockito.when(fileInfoRepo.findDirByUidAndNode(Mockito.anyLong(), Mockito.anyString()))
                .thenAnswer(i -> {
                    String node = i.getArgument(1, String.class);
                    return Optional.ofNullable(mockFileSystem.listFiles(node))
                            .map(e -> e.stream()
                                    .filter(FileInfo::isDir)
                                    .toList()
                            )
                            .orElse(null);
                });

        String a3Id = mockFileSystem.getMd5ByName("a3");
        assertEquals(8, fileRecordService.listChildDirs(0, a3Id, -1).size());
        assertEquals(2, fileRecordService.listChildDirs(0, a3Id, 0).size());
        List<FileInfo> r = fileRecordService.listChildDirs(0, a3Id, 1);
        assertTrue(r.stream().anyMatch(e -> e.getName().equals("a3b2c1")));
        assertEquals(6, r.size());
        assertEquals(8, fileRecordService.listChildDirs(0, a3Id, 2).size());
        assertEquals(3, fileRecordService.listChildDirs(0, ROOT_ID, 0).size());
    }

    @Test
    @DisplayName("测试获取所有上级目录")
    void listAllParentByNodeId() {
        Mockito.when(fileInfoRepo.findDirByUidAndMd5(Mockito.anyLong(), Mockito.anyString()))
                .thenAnswer(i -> {
                    String nodeId = i.getArgument(1, String.class);
                    return mockFileSystem.getDirFileInfoByMd5(nodeId);
                });
        Optional<Deque<FileInfo>> res = fileRecordService.listAllParentByNodeId(0, mockFileSystem.getMd5ByName("a1b1c1"));
        assertTrue(res.isPresent());
        Deque<FileInfo> parentList = res.get();
        assertEquals(3, parentList.size());
        assertEquals("a1", parentList.getFirst().getName());
        assertEquals("a1b1c1", parentList.getLast().getName());
        assertFalse(fileRecordService.listAllParentByNodeId(0, mockFileSystem.getMd5ByName("a1b1c11")).isPresent());
        assertFalse(fileRecordService.listAllParentByNodeId(0, SecureUtils.getUUID()).isPresent());
    }

    @Test
    @DisplayName("测试获取所有途径节点")
    void getVisitPathInfo() {
        Mockito.when(fileInfoRepo.findFileInfo(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(i -> {
                    String name = i.getArgument(1, String.class);
                    String parentId = i.getArgument(2, String.class);
                    List<FileInfo> fileInfos = mockFileSystem.listFiles(parentId);
                    if (fileInfos == null) {
                        return null;
                    }
                    return fileInfos.stream().filter(e -> e.getName().equals(name)).findAny().orElse(null);
                });

        // 正常查询路径
        Deque<FileInfo> visitPathInfo = fileRecordService.getVisitPathInfo(0, "/a3/a3b1/a3b1c1/a3b1c1d2");
        assertNotNull(visitPathInfo);
        assertEquals(4, visitPathInfo.size());
        assertArrayEquals(
                new String[]{"a3", "a3b1", "a3b1c1", "a3b1c1d2"},
                visitPathInfo.stream().map(FileInfo::getName).toArray()
        );
        assertArrayEquals(
                new String[]{"/a3", "/a3/a3b1", "/a3/a3b1/a3b1c1", "/a3/a3b1/a3b1c1/a3b1c1d2"},
                visitPathInfo.stream().map(FileInfo::getPath).toArray()
        );

        // 查询不存在路径
        assertNull(fileRecordService.getVisitPathInfo(0, "/a3/a3b1/a3b1c1/a3b1c1d21"));

        // 查根目录
        Deque<FileInfo> rootResult = fileRecordService.getVisitPathInfo(0, "/");
        assertEquals(1, rootResult.size());
        assertEquals("/", rootResult.getFirst().getPath());

        // 路径中间混入文件类型节点
        mockFileSystem.registerMockFile(createFile("a3b1File", mockFileSystem.getMd5ByName("a3b1")));
        assertNull(fileRecordService.getVisitPathInfo(0, "/a3/a3b1/a3b1File/a3b1File111"));
    }
}