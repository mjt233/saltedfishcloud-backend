package com.sfc.ext.oss.store;

import com.sfc.ext.oss.OSSProperty;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class S3DirectRawHandlerTest {
    private static OSSProperty ossProperty = null;
    private static S3DirectRawHandler handler;

    static {
        try {
            ossProperty = MapperHolder.mapper.readValue(
                    S3DirectRawHandlerTest.class.getClassLoader().getResourceAsStream("test-oss-property.json"),
                    OSSProperty.class
            );
            handler = new S3DirectRawHandler(ossProperty);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testListFile() throws IOException {
        List<FileInfo> files = handler.listFiles("/");
        for (FileInfo file : files) {
            System.out.println(file.getName() + "\t" + file.getSize() + "\t" + (file.isFile() ? "文件" : "目录"));
        }
    }

    @Test
    public void testGetFile() throws IOException {
        FileInfo fileInfo = handler.getFileInfo("/你干嘛.mp4");
        if (fileInfo != null) {
            Path tempSavePath = PathUtils.getTempPath().resolve("test-oss-download");
            System.out.println("测试下载文件,临时保存到" + tempSavePath);
            try (InputStream inputStream = fileInfo.getStreamSource().getInputStream()) {
                Files.copy(inputStream, tempSavePath);
            } finally {
                System.out.println("删除临时文件" + tempSavePath);
                Files.deleteIfExists(tempSavePath);
            }
        }
    }
}