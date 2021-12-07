package com.xiaotao.saltedfishcloud.compress;

import com.xiaotao.saltedfishcloud.compress.impl.SequenceCompressFileSystem;
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
    void extractAll() throws IOException, ArchiveException {
        ClassPathResource resource = new ClassPathResource("/testResources/test.zip");
        SequenceCompressFileSystem view = new SequenceCompressFileSystem(resource);
        Path basePath = Paths.get("D:\\test\\");
        view.extractAll(basePath);
    }

    @Test
    void extractOne() throws IOException, ArchiveException {
        ClassPathResource resource = new ClassPathResource("/testResources/test.zip");
        SequenceCompressFileSystem view = new SequenceCompressFileSystem(resource);
        InputStream inputStream = view.getInputStream("avatar.jpg");
        Files.copy(inputStream, Paths.get("D:\\avatar.jpg"), StandardCopyOption.REPLACE_EXISTING);

    }
}
