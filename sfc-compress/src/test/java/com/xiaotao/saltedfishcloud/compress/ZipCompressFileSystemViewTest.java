package com.xiaotao.saltedfishcloud.compress;

import com.xiaotao.saltedfishcloud.compress.creator.ArchiveResourceEntry;
import com.xiaotao.saltedfishcloud.compress.creator.ZipCompressor;
import com.xiaotao.saltedfishcloud.compress.reader.impl.ZipArchiveReader;
import org.apache.commons.compress.archivers.ArchiveException;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

class ZipCompressFileSystemViewTest {
    private final static ClassPathResource INPUT = new ClassPathResource("/testResources/123.txt");
    private final static Path OUTPUT = Paths.get("test_output.zip");
    @Test
    void compress() throws Exception {

        // 压缩文件
        ZipCompressor compressor = new ZipCompressor(Files.newOutputStream(OUTPUT));
        compressor.addFile(new ArchiveResourceEntry("噢/h/h/h/123哈.txt", INPUT.contentLength(), INPUT));
        compressor.close();

        // 解压文件
        ZipArchiveReader reader = new ZipArchiveReader(OUTPUT.toFile());

        // 计算原始MD5
        String originMd5 = DigestUtils.md5DigestAsHex(INPUT.getInputStream());

        // 计算解压后MD5
        String md5 = DigestUtils.md5DigestAsHex(reader.getInputStream("噢/h/h/h/123哈.txt"));

        // 对比
        assertEquals(originMd5, md5);

        // 资源释放
        reader.close();
        Files.deleteIfExists(OUTPUT);
    }

    @Test
    void extractAll() throws Exception {
        ClassPathResource resource = new ClassPathResource("/testResources/test.zip");
        ZipArchiveReader view = new ZipArchiveReader(resource.getFile());
        Path basePath = Paths.get("D:\\test\\");
        view.extractAll(basePath);
    }

    @Test
    void extractOne() throws IOException, ArchiveException {
        ClassPathResource resource = new ClassPathResource("/testResources/test.zip");
        ZipArchiveReader view = new ZipArchiveReader(resource.getFile());
        InputStream inputStream = view.getInputStream("avatar.jpg");
        Files.copy(inputStream, Paths.get("D:\\avatar.jpg"), StandardCopyOption.REPLACE_EXISTING);

    }
}
