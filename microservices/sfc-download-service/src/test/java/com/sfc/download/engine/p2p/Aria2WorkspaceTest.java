package com.sfc.download.engine.p2p;

import com.sfc.download.config.Aria2Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Aria2Workspace} 的工作区归属判断、downloadId 解析与目录映射测试。
 */
class Aria2WorkspaceTest {

    /**
     * 测试用共享目录。
     */
    @TempDir
    Path sharedDir;

    /**
     * 未启用共享目录时：工作区为 JVM 临时目录下的 offline-download，aria2 路径即本服务路径。
     */
    @Test
    void shouldUseJvmTempDirWhenSharedDisabled() {
        Aria2Workspace workspace = new Aria2Workspace(properties("", ""), null);

        Path root = workspace.workspaceRoot();
        assertEquals("offline-download", root.getFileName().toString());
        assertTrue(workspace.isInWorkspace(root.resolve("123-abc")));
        assertFalse(workspace.isInWorkspace(root), "工作区根本身不属于任务目录");
        assertFalse(workspace.isInWorkspace(Path.of("somewhere-else")));
        assertNull(workspace.validate());

        Path aria2Path = Path.of(root.resolve("123-abc").toString());
        assertEquals(aria2Path.toAbsolutePath().normalize(),
                workspace.toLocalDir(aria2Path.toString()).toAbsolutePath().normalize());
    }

    /**
     * 启用共享目录时：aria2 侧路径映射回共享工作区，且工作区外路径被拒绝。
     */
    @Test
    void shouldMapAria2DirIntoSharedWorkspace() throws IOException {
        Aria2Workspace workspace = new Aria2Workspace(
                properties(sharedDir.toString(), "/downloads"), null);
        Path taskDir = Files.createDirectories(
                sharedDir.resolve("offline-download").resolve("123-uuid-abc"));

        assertNull(workspace.validate());
        assertTrue(workspace.isInWorkspace(taskDir));
        assertFalse(workspace.isInWorkspace(sharedDir), "共享根不是任务工作区");
        assertFalse(workspace.isInWorkspace(Path.of("/downloads/offline-download/123-uuid-abc")));

        // aria2 侧目录反向映射到本服务侧
        Path mapped = workspace.toLocalDir("/downloads/offline-download/123-uuid-abc");
        assertNotNull(mapped);
        assertEquals(taskDir.toAbsolutePath().normalize(), mapped);

        // 映射前缀不匹配或越界时返回 null
        assertNull(workspace.toLocalDir("/other/offline-download/123-uuid-abc"));
        assertNull(workspace.toLocalDir("/downloads/offline-download/../outside"));
    }

    /**
     * 任务目录名 {@code <downloadId>-<UUID>} 解析下载任务 id。
     */
    @Test
    void shouldParseDownloadIdFromDirectoryName() {
        Aria2Workspace workspace = new Aria2Workspace(properties("", ""), null);

        assertEquals(1234567890L, workspace.parseDownloadId(Path.of("/x/1234567890-uuid")));
        assertNull(workspace.parseDownloadId(Path.of("/x/task-uuid")), "非数字前缀不可解析");
        assertNull(workspace.parseDownloadId(Path.of("/x/1234567890")), "缺少分隔符时不可解析");
        assertNull(workspace.parseDownloadId(Path.of("/x/")));
        assertNull(workspace.parseDownloadId(null));
    }

    /**
     * 共享目录配置非法时，工作区判断返回 false 而不是抛异常（对账安全边界）。
     */
    @Test
    void shouldTreatInvalidConfigurationAsOutsideWorkspace() {
        Aria2Workspace workspace = new Aria2Workspace(
                properties(Path.of(sharedDir.toString(), "missing").toString(), ""), null);

        assertNotNull(workspace.validate());
        assertFalse(workspace.isInWorkspace(sharedDir));
    }

    /**
     * 构造 aria2 部署期配置。
     *
     * @param shared 共享根目录（本服务侧）
     * @param remote aria2 侧目录
     * @return 配置实例
     */
    private static Aria2Properties properties(String shared, String remote) {
        Aria2Properties properties = new Aria2Properties();
        properties.setSharedDir(shared);
        properties.setRemoteDir(remote);
        properties.setRpcUrl(Aria2Properties.DEFAULT_RPC_URL);
        return properties;
    }
}
