package com.xiaotao.saltedfishcloud.compress;

import com.xiaotao.saltedfishcloud.compress.reader.impl.ZipArchiveReader;
import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

class ZipCompressFileSystemViewTest {
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
