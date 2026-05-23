package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultFileSystem} 的路由、路径转发与存储记录委托单元测试。
 */
@ExtendWith(MockitoExtension.class)
class DefaultFileSystemTest {
    /**
     * 被测对象。
     */
    @InjectMocks
    private DefaultFileSystem defaultFileSystem;

    /**
     * 文件系统路由解析器模拟对象。
     */
    @Mock
    private DiskFileSystemRouteResolver routeResolver;

    /**
     * 主文件系统执行器模拟对象。
     */
    @Mock
    private DefaultFileSystemMainExecutor mainExecutor;

    /**
     * 文件系统元数据操作器模拟对象。
     */
    @Mock
    private FileSystemMetadataOperator metadataOperator;

    /**
     * 挂载点对应的委托文件系统模拟对象。
     */
    @Mock
    private DiskFileSystem delegateFileSystem;

    /**
     * 验证主文件系统路由会保留原始路径并转发给主执行器。
     */
    @Test
    @DisplayName("主文件系统请求会保留原始路径")
    void getResourceShouldUseOriginalPathOnMainRoute() throws IOException {
        long uid = 1L;
        String path = "/docs";
        String name = "note.txt";
        Resource expected = new ByteArrayResource(new byte[]{1, 2, 3});
        when(routeResolver.matchFileSystem(uid, path)).thenReturn(mainRoute(path));
        when(mainExecutor.getResource(uid, path, name)).thenReturn(expected);

        Resource actual = defaultFileSystem.getResource(uid, path, name);

        assertSame(expected, actual);
        verify(routeResolver).matchFileSystem(uid, path);
        verify(mainExecutor).getResource(uid, path, name);
        verifyNoInteractions(delegateFileSystem, metadataOperator);
    }

    /**
     * 验证挂载点请求会转发到委托文件系统，并使用解析后的路径。
     */
    @Test
    @DisplayName("挂载点资源请求会使用解析后的路径")
    void getResourceShouldUseResolvedPathOnMountedRoute() throws IOException {
        long uid = 1L;
        String path = "/mount/docs";
        String resolvedPath = "/docs";
        String name = "note.txt";
        Resource expected = new ByteArrayResource(new byte[]{4, 5, 6});
        when(routeResolver.matchFileSystem(uid, path))
                .thenReturn(mountRoute("/mount", resolvedPath, FileSystemRouteMode.MOUNT_WITH_DELEGATED_METADATA));
        when(delegateFileSystem.getResource(uid, resolvedPath, name)).thenReturn(expected);

        Resource actual = defaultFileSystem.getResource(uid, path, name);

        assertSame(expected, actual);
        verify(routeResolver).matchFileSystem(uid, path);
        verify(delegateFileSystem).getResource(uid, resolvedPath, name);
        verifyNoInteractions(mainExecutor, metadataOperator);
    }

    /**
     * 验证开启存储记录委托时，挂载点文件列表读取会走主文件系统记录服务。
     */
    @Test
    @DisplayName("开启存储记录委托时文件列表读取会走主记录服务")
    void getUserFileListShouldUseMainExecutorWhenProxyStoreRecordEnabled() throws IOException {
        long uid = 1L;
        String path = "/mount/docs";
        String resolvedPath = "/docs";
        List<String> nameList = List.of("note.txt");
        List<FileInfo> expected = List.of(new FileInfo().setName("note.txt"));
        when(routeResolver.matchFileSystem(uid, path))
                .thenReturn(mountRoute("/mount", resolvedPath, FileSystemRouteMode.MOUNT_WITH_MAIN_METADATA));
        when(mainExecutor.getUserFileList(uid, path, nameList)).thenReturn(expected);

        List<FileInfo> actual = defaultFileSystem.getUserFileList(uid, path, nameList);

        assertSame(expected, actual);
        verify(routeResolver).matchFileSystem(uid, path);
        verify(mainExecutor).getUserFileList(uid, path, nameList);
        verifyNoInteractions(delegateFileSystem, metadataOperator);
    }

    /**
     * 验证关闭存储记录委托时，挂载点文件列表读取不会走主文件系统记录服务。
     */
    @Test
    @DisplayName("关闭存储记录委托时文件列表读取不会走主记录服务")
    void getUserFileListShouldUseDelegateWhenProxyStoreRecordDisabled() throws IOException {
        long uid = 1L;
        String path = "/mount/docs";
        String resolvedPath = "/docs";
        List<String> nameList = List.of("note.txt");
        List<FileInfo> expected = List.of(new FileInfo().setName("note.txt"));
        when(routeResolver.matchFileSystem(uid, path))
                .thenReturn(mountRoute("/mount", resolvedPath, FileSystemRouteMode.MOUNT_WITH_DELEGATED_METADATA));
        when(delegateFileSystem.getUserFileList(uid, resolvedPath, nameList)).thenReturn(expected);

        List<FileInfo> actual = defaultFileSystem.getUserFileList(uid, path, nameList);

        assertSame(expected, actual);
        verify(routeResolver).matchFileSystem(uid, path);
        verify(delegateFileSystem).getUserFileList(uid, resolvedPath, nameList);
        verifyNoInteractions(mainExecutor, metadataOperator);
    }

    /**
     * 验证命中挂载点根路径时无需继续委托即可判断路径存在。
     */
    @Test
    @DisplayName("挂载点根路径存在判断会直接返回 true")
    void existShouldReturnTrueForMountRootWithoutDelegating() throws IOException {
        long uid = 1L;
        String path = "/mount";
        when(routeResolver.matchFileSystem(uid, path))
                .thenReturn(mountRoute("/mount", "", FileSystemRouteMode.MOUNT_WITH_DELEGATED_METADATA));

        boolean actual = defaultFileSystem.exist(uid, path);

        assertTrue(actual);
        verify(routeResolver).matchFileSystem(uid, path);
        verifyNoInteractions(mainExecutor, delegateFileSystem, metadataOperator);
    }

    /**
     * 验证代理挂载创建目录时，会把解析后的路径发给委托文件系统，同时对原始路径维护主元数据。
     */
    @Test
    @DisplayName("代理挂载创建目录时会分别使用委托路径和原始路径")
    void mkdirShouldUseResolvedPathForDelegateAndOriginPathForMetadata() throws IOException {
        long uid = 1L;
        String path = "/mount/docs";
        String resolvedPath = "/docs";
        String name = "child";
        when(routeResolver.matchFileSystem(uid, path))
                .thenReturn(mountRoute("/mount", resolvedPath, FileSystemRouteMode.MOUNT_WITH_MAIN_METADATA));

        defaultFileSystem.mkdir(uid, path, name);

        verify(routeResolver).matchFileSystem(uid, path);
        verify(delegateFileSystem).mkdir(uid, resolvedPath, name);
        verify(metadataOperator).mkdirs(uid, "/mount/docs/child", true);
        verifyNoInteractions(mainExecutor);
    }

    /**
     * 验证关闭存储记录委托时，挂载点创建目录不会同步主文件系统记录。
     */
    @Test
    @DisplayName("关闭存储记录委托时创建目录不会同步主记录服务")
    void mkdirShouldNotSyncMetadataWhenProxyStoreRecordDisabled() throws IOException {
        long uid = 1L;
        String path = "/mount/docs";
        String resolvedPath = "/docs";
        String name = "child";
        when(routeResolver.matchFileSystem(uid, path))
                .thenReturn(mountRoute("/mount", resolvedPath, FileSystemRouteMode.MOUNT_WITH_DELEGATED_METADATA));

        defaultFileSystem.mkdir(uid, path, name);

        verify(routeResolver).matchFileSystem(uid, path);
        verify(delegateFileSystem).mkdir(uid, resolvedPath, name);
        verifyNoInteractions(mainExecutor, metadataOperator);
    }

    /**
     * 验证挂载文件更新时间时，会先按解析后的完整文件路径拆分父目录与文件名再委托执行。
     */
    @Test
    @DisplayName("挂载文件更新时间会按解析后的路径拆分父目录和文件名")
    void updateTimeShouldSplitResolvedMountedPathBeforeDelegating() throws IOException {
        long uid = 1L;
        String path = "/mount/docs";
        String fileName = "note.txt";
        String mountedFilePath = "/mount/docs/note.txt";
        String resolvedFilePath = "/docs/note.txt";
        List<String> names = List.of(fileName);
        FileTimeAttribute attribute = createAttribute();
        when(routeResolver.matchFileSystem(uid, mountedFilePath))
                .thenReturn(mountRoute("/mount", resolvedFilePath, FileSystemRouteMode.MOUNT_WITH_MAIN_METADATA));

        defaultFileSystem.updateTime(uid, path, names, attribute);

        verify(routeResolver).matchFileSystem(uid, mountedFilePath);
        verify(metadataOperator).updateTime(uid, path, names, attribute);
        verify(delegateFileSystem).updateTime(uid, "/docs", names, attribute);
        verifyNoInteractions(mainExecutor);
    }

    /**
     * 验证关闭存储记录委托时，挂载点更新时间不会同步主文件系统记录。
     */
    @Test
    @DisplayName("关闭存储记录委托时更新时间不会同步主记录服务")
    void updateTimeShouldNotSyncMetadataWhenProxyStoreRecordDisabled() throws IOException {
        long uid = 1L;
        String path = "/mount/docs";
        String fileName = "note.txt";
        String mountedFilePath = "/mount/docs/note.txt";
        String resolvedFilePath = "/docs/note.txt";
        List<String> names = List.of(fileName);
        FileTimeAttribute attribute = createAttribute();
        when(routeResolver.matchFileSystem(uid, mountedFilePath))
                .thenReturn(mountRoute("/mount", resolvedFilePath, FileSystemRouteMode.MOUNT_WITH_DELEGATED_METADATA));

        defaultFileSystem.updateTime(uid, path, names, attribute);

        verify(routeResolver).matchFileSystem(uid, mountedFilePath);
        verify(delegateFileSystem).updateTime(uid, "/docs", names, attribute);
        verifyNoInteractions(mainExecutor, metadataOperator);
    }

    /**
     * 创建主文件系统路由上下文。
     *
     * @param path 原始请求路径
     * @return 主文件系统路由上下文
     */
    private static FileSystemRouteContext mainRoute(String path) {
        return new FileSystemRouteContext(null, null, path, FileSystemRouteMode.MAIN);
    }

    /**
     * 创建挂载点路由上下文。
     *
     * @param mountPath 挂载点路径
     * @param resolvedPath 挂载点内部解析后的路径
     * @param routeMode 路由模式
     * @return 挂载点路由上下文
     */
    private FileSystemRouteContext mountRoute(String mountPath, String resolvedPath, FileSystemRouteMode routeMode) {
        MountPoint mountPoint = new MountPoint();
        mountPoint.setPath(mountPath);
        return new FileSystemRouteContext(delegateFileSystem, mountPoint, resolvedPath, routeMode);
    }

    /**
     * 创建用于更新时间测试的时间属性对象。
     *
     * @return 时间属性
     */
    private static FileTimeAttribute createAttribute() {
        FileTimeAttribute attribute = new FileTimeAttribute();
        attribute.setModifyTime(new Date(1L));
        return attribute;
    }
}
