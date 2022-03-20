package com.xiaotao.saltedfishcloud.utils;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class ImageUtilsTest {

    @Test
    void generateThumbnail() throws IOException {
        generate("/defaultAvatar.png");
        generate("/loading.gif");
        try {
            generate("/test.zip");
            fail();
        }catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    private void generate(String classpath) throws IOException {
        final ClassPathResource resource = new ClassPathResource(classpath);
        final String[] names = FileUtils.parseName(resource.getFile().getName());
        final Path outputPath = Paths.get(resource.getFile().getParent() + "/" + names[0] + ".jpg");
        try(
                final InputStream inputStream = resource.getInputStream();
                final OutputStream outputStream = Files.newOutputStream(outputPath);
        ) {
            ImageUtils.generateThumbnail(inputStream, 300, outputStream);
        }
        System.out.println("缩略图已生成: " + outputPath.toUri());
    }
}
