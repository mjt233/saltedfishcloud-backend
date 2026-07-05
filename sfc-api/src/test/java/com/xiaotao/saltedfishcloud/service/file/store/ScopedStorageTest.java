package com.xiaotao.saltedfishcloud.service.file.store;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScopedStorageTest {

    @Mock
    private Storage delegate;

    @BeforeEach
    void setUp() throws IOException {
        lenient().when(delegate.exist(anyString())).thenReturn(true);
    }

    private ScopedStorage createStorage(String basePath) throws Exception {
        return new ScopedStorage(delegate, basePath);
    }

    @Nested
    @DisplayName("构造器")
    class ConstructorTest {

        @Test
        @DisplayName("null委托抛出NullPointerException")
        @SuppressWarnings("resource")
        void nullDelegate() {
            assertThrows(NullPointerException.class, () -> new ScopedStorage(null, "/"));
        }

        @Test
        @DisplayName("null/空/空白字符串basePath归一化为/")
        void emptyBasePath() throws Exception {
            assertStorageBasePathNormalizedToRoot(null);
            assertStorageBasePathNormalizedToRoot("");
            assertStorageBasePathNormalizedToRoot("   ");
        }

        private void assertStorageBasePathNormalizedToRoot(String input) throws Exception {
            try (ScopedStorage s = createStorage(input)) {
                assertEquals("/", s.getBasePath());
            }
        }

        @Test
        @DisplayName("basePath尾随斜杠被移除")
        void trailingSlashStripped() throws Exception {
            try (ScopedStorage s = createStorage("/mount/")) {
                assertEquals("/mount", s.getBasePath());
            }
        }

        @Test
        @DisplayName("basePath包含..抛IOException")
        @SuppressWarnings("resource")
        void basePathWithDotDot() {
            assertThrows(IOException.class, () -> createStorage("/mount/../etc"));
        }

        @Test
        @DisplayName("basePath不存在抛IOException")
        @SuppressWarnings("resource")
        void nonExistentBasePath() throws IOException {
            doReturn(false).when(delegate).exist("/nonexistent");
            assertThrows(IOException.class, () -> createStorage("/nonexistent"));
        }

        @Test
        @DisplayName("正常basePath构造成功")
        void normalBasePath() throws Exception {
            try (ScopedStorage s = createStorage("/mount/point")) {
                assertEquals("/mount/point", s.getBasePath());
                assertSame(delegate, s.getDelegate());
            }
        }
    }

    @Nested
    @DisplayName("路径拼接")
    class PathResolutionTest {

        @Test
        @DisplayName("basePath=/ 且 path=/ -> delegate收到/")
        void rootBaseRootPath() throws Exception {
            try (ScopedStorage s = createStorage("/")) {
                s.delete("/");
                verify(delegate).delete("/");
            }
        }

        @Test
        @DisplayName("basePath=/ 且 path=/file.txt -> delegate收到/file.txt")
        void rootBaseFilePath() throws Exception {
            try (ScopedStorage s = createStorage("/")) {
                s.delete("/file.txt");
                verify(delegate).delete("/file.txt");
            }
        }

        @Test
        @DisplayName("basePath=/mount 且 path=/ -> delegate收到/mount")
        void mountBaseRootPath() throws Exception {
            try (ScopedStorage s = createStorage("/mount")) {
                s.delete("/");
                verify(delegate).delete("/mount");
            }
        }

        @Test
        @DisplayName("basePath=/mount 且 path=/file.txt -> delegate收到/mount/file.txt")
        void mountBaseFilePath() throws Exception {
            try (ScopedStorage s = createStorage("/mount")) {
                s.delete("/file.txt");
                verify(delegate).delete("/mount/file.txt");
            }
        }

        @Test
        @DisplayName("basePath=/mount/point 且 path=/sub/dir/file.txt -> delegate收到/mount/point/sub/dir/file.txt")
        void deepBaseDeepPath() throws Exception {
            try (ScopedStorage s = createStorage("/mount/point")) {
                s.delete("/sub/dir/file.txt");
                verify(delegate).delete("/mount/point/sub/dir/file.txt");
            }
        }

        @Test
        @DisplayName("basePath尾随斜杠在前置检查中被移除后正常拼接")
        void trailingSlashBasePath() throws Exception {
            try (ScopedStorage s = createStorage("/mount/")) {
                assertEquals("/mount", s.getBasePath());
                s.delete("/file.txt");
                verify(delegate).delete("/mount/file.txt");
            }
        }

        @Test
        @DisplayName("path为null或空映射为/")
        void nullOrEmptyPath() throws Exception {
            try (ScopedStorage s = createStorage("/mount")) {
                s.delete(null);
                s.delete("");
                verify(delegate, times(2)).delete("/mount");
            }
        }
    }

    @Nested
    @DisplayName("反斜杠标准化")
    class BackslashNormalizationTest {

        @Test
        @DisplayName("path中反斜杠转换为正斜杠")
        void backslashToForwardSlash() throws Exception {
            try (ScopedStorage s = createStorage("/mount")) {
                s.delete("\\file.txt");
                verify(delegate).delete("/mount/file.txt");
            }
        }

        @Test
        @DisplayName("path中混合正反斜杠统一为正斜杠")
        void mixedSlashes() throws Exception {
            try (ScopedStorage s = createStorage("/mount")) {
                s.delete("/dir\\sub\\file.txt");
                verify(delegate).delete("/mount/dir/sub/file.txt");
            }
        }

        @Test
        @DisplayName("basePath中反斜杠转换为正斜杠")
        void basePathBackslash() throws Exception {
            try (ScopedStorage s = createStorage("C:\\mount")) {
                s.delete("/file.txt");
                verify(delegate).delete("C:/mount/file.txt");
            }
        }
    }

    @Nested
    @DisplayName("路径越界安全")
    class PathTraversalSecurityTest {

        @Test
        @DisplayName("path包含..抛IOException")
        void pathWithDotDot() throws Exception {
            try (ScopedStorage s = createStorage("/mount")) {
                assertThrows(IOException.class, () -> s.delete("/../etc/passwd"));
            }
        }

        @Test
        @DisplayName("path深层包含..抛IOException")
        void pathWithDeepDotDot() throws Exception {
            try (ScopedStorage s = createStorage("/mount")) {
                assertThrows(IOException.class, () -> s.delete("/dir/../../etc/passwd"));
            }
        }

        @Test
        @DisplayName("path中..出现在中间位置抛IOException")
        void pathWithDotDotInMiddle() throws Exception {
            try (ScopedStorage s = createStorage("/mount")) {
                assertThrows(IOException.class, () -> s.delete("/a/../b"));
            }
        }
    }

    @Nested
    @DisplayName("冗余片段清理")
    class RedundantSegmentCleaningTest {

        @Test
        @DisplayName("path中.片段被过滤")
        void singleDotFiltered() throws Exception {
            try (ScopedStorage s = createStorage("/mount")) {
                s.delete("/./dir/./file.txt");
                verify(delegate).delete("/mount/dir/file.txt");
            }
        }

        @Test
        @DisplayName("path中连续斜杠归一化")
        void doubleSlashNormalized() throws Exception {
            try (ScopedStorage s = createStorage("/mount")) {
                s.delete("/dir///file.txt");
                verify(delegate).delete("/mount/dir/file.txt");
            }
        }

        @Test
        @DisplayName("path=/./映射为根路径下的basePath")
        void singleDotRoot() throws Exception {
            try (ScopedStorage s = createStorage("/mount")) {
                s.delete("/.");
                verify(delegate).delete("/mount");
            }
        }

        @Test
        @DisplayName("path=/.映射为basePath")
        void singleDotAsDirectory() throws Exception {
            try (ScopedStorage s = createStorage("/mount")) {
                s.listFiles("/.");
                verify(delegate).listFiles("/mount");
            }
        }
    }

    @Nested
    @DisplayName("委托方法转发")
    class DelegateMethodForwardingTest {

        @Test
        @DisplayName("mkdir转发解析后的路径")
        void mkdir() throws Exception {
            try (ScopedStorage s = createStorage("/mount")) {
                s.mkdir("/new/dir");
                verify(delegate).mkdir("/mount/new/dir");
            }
        }

        @Test
        @DisplayName("listFiles转发解析后的路径")
        void listFiles() throws Exception {
            when(delegate.listFiles(anyString())).thenReturn(List.of());
            try (ScopedStorage s = createStorage("/mount")) {
                List<FileInfo> result = s.listFiles("/folder");
                verify(delegate).listFiles("/mount/folder");
                assertNotNull(result);
            }
        }

        @Test
        @DisplayName("getFileInfo转发解析后的路径")
        void getFileInfoMethod() throws Exception {
            when(delegate.getFileInfo(anyString())).thenReturn(new FileInfo());
            try (ScopedStorage s = createStorage("/mount")) {
                FileInfo result = s.getFileInfo("/file.txt");
                verify(delegate).getFileInfo("/mount/file.txt");
                assertNotNull(result);
            }
        }

        @Test
        @DisplayName("store转发解析后的路径")
        void store() throws Exception {
            when(delegate.store(any(), anyString(), anyLong(), any())).thenReturn(100L);
            try (ScopedStorage s = createStorage("/mount")) {
                FileInfo info = new FileInfo();
                InputStream is = new ByteArrayInputStream(new byte[0]);
                long result = s.store(info, "/dest/file.bin", 100, is);
                verify(delegate).store(info, "/mount/dest/file.bin", 100, is);
                assertEquals(100L, result);
            }
        }

        @Test
        @DisplayName("copy两个路径都解析")
        void copy() throws Exception {
            when(delegate.copy(anyString(), anyString(), any())).thenReturn(true);
            try (ScopedStorage s = createStorage("/mount")) {
                boolean result = s.copy("/src/file.txt", "/dest/file.txt", null);
                verify(delegate).copy("/mount/src/file.txt", "/mount/dest/file.txt", null);
                assertTrue(result);
            }
        }

        @Test
        @DisplayName("getResource转发解析后的路径")
        void getResource() throws Exception {
            try (ScopedStorage s = createStorage("/mount")) {
                s.getResource("/file.txt");
                verify(delegate).getResource("/mount/file.txt");
            }
        }

        @Test
        @DisplayName("isEmptyDirectory转发解析后的路径")
        void isEmptyDirectory() throws Exception {
            try (ScopedStorage s = createStorage("/mount")) {
                s.isEmptyDirectory("/folder");
                verify(delegate).isEmptyDirectory("/mount/folder");
            }
        }

        @Test
        @DisplayName("newOutputStream转发解析后的路径")
        void newOutputStream() throws Exception {
            when(delegate.newOutputStream(anyString())).thenReturn(new ByteArrayOutputStream());
            try (ScopedStorage s = createStorage("/mount")) {
                OutputStream os = s.newOutputStream("/out.bin");
                verify(delegate).newOutputStream("/mount/out.bin");
                assertNotNull(os);
            }
        }

        @Test
        @DisplayName("rename转发解析后的路径")
        void rename() throws Exception {
            try (ScopedStorage s = createStorage("/mount")) {
                s.rename("/old.txt", "new.txt");
                verify(delegate).rename("/mount/old.txt", "new.txt");
            }
        }
    }
}
