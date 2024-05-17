package com.saltedfishcloud.ext.minio;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MinioDirectRawHandlerTest {

    public MinioProperties getProperties() {
        MinioProperties properties = new MinioProperties();
        properties.setBucket("xyy-test");
        properties.setAccessKey("test");
        properties.setSecretKey("test123456");
        return properties;
    }
    public MinioClient getClient() {
        MinioProperties properties = getProperties();
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    @Test
    public void testAll() throws Exception {
        MinioProperties properties = getProperties();
        MinioClient client = getClient();

        MinioDirectRawHandler handler = new MinioDirectRawHandler(client, properties);

        // 测试文件夹创建
        String testDir = "xyy-dir";
        // -- 创建前资源不存在
        assertFalse(handler.exist(testDir));

        handler.mkdir(testDir);
        // -- 创建后资源存在
        assertTrue(handler.exist(testDir));
        // -- 被识别为文件夹
        assertTrue(handler.isEmptyDirectory(testDir));

        // 测试文件夹删除
        handler.delete(testDir);
        // -- 删除后资源不存在
        assertFalse(handler.exist(testDir));

        // 测试文件上传，无报错即成功
        String testFileContent = "Hello Minio!";
        String testFileName = "xyy-new-file.txt";
        String testFileContent2 = "Hello Minio!2";
        String testFileName2 = "xyy-new-file2.txt";
        handler.store(null, testFileName, testFileContent.length(), new ByteArrayInputStream(testFileContent.getBytes(StandardCharsets.UTF_8)));
        try(OutputStream os = handler.newOutputStream(testFileName2)) {
            StreamUtils.copy(testFileContent2.getBytes(StandardCharsets.UTF_8), os);
        }


        // 测试文件下载
        FileInfo fileInfo = handler.getFileInfo(testFileName);
        FileInfo fileInfo2 = handler.getFileInfo(testFileName2);
        // -- 大小要一致
        assertEquals(testFileContent.length(), fileInfo.getSize());
        assertEquals(testFileContent2.length(), fileInfo2.getSize());
        // -- 内容要一致
        try(InputStream is = fileInfo.getStreamSource().getInputStream()) {
            String downloadText = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            assertEquals(downloadText, testFileContent);
        }
        try(InputStream is = fileInfo2.getStreamSource().getInputStream()) {
            String downloadText = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            assertEquals(downloadText, testFileContent2);
        }

        // 测试文件删除
        handler.delete(testFileName);
        assertFalse(handler.exist(testFileName));
        assertTrue(handler.exist(testFileName2));
        handler.delete(testFileName2);
        assertFalse(handler.exist(testFileName));
        assertFalse(handler.exist(testFileName2));


        // 多级目录创建
        String multiLevelDir ="a/b/c/d";
        handler.mkdir(multiLevelDir);
        assertTrue(handler.exist(multiLevelDir));
        assertTrue(handler.exist("a"));

        // 复制目录
        handler.copy("a/b", "a/bb");
        assertTrue(handler.exist("a/bb/c"));
        assertTrue(handler.exist("a/bb/c/d"));
        handler.delete("a/b");
        assertFalse(handler.exist("a/b"));

        // 重命名目录
        handler.rename("a/bb", "new-b");
        assertTrue(handler.exist("a/new-b/c/d"));

        // 移动
        handler.move("a","root-a");
        assertFalse(handler.exist("a"));
        assertTrue(handler.exist("root-a"));
    }
}