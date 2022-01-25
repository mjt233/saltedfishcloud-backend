package com.xiaotao.saltedfishcloud.ext;

import com.sun.org.apache.regexp.internal.RE;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
public class HadoopTest {
    public FileSystem getFileSystem() throws URISyntaxException, IOException, InterruptedException {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://localhost:9000");

        conf.set("hadoop.root.logger", "INFO");
//        return FileSystem.get(conf);
        return FileSystem.get(new URI("hdfs://localhost:9000"), conf, "xiaotao");
    }

    @Test
    public void testUpload() throws InterruptedException, IOException, URISyntaxException {
        FileSystem fs = getFileSystem();
        fs.mkdirs(new Path("/test"));

        ClassPathResource resource = new ClassPathResource("/test/1.jpg");
        try(
                InputStream in = resource.getInputStream();
                FSDataOutputStream fsDataOutputStream = fs.create(new Path("/test/1.jpg"))
        ) {
            log.info("=================开始上传文件=================");
            StreamUtils.copy(in, fsDataOutputStream);
            log.info("=================上传完成=================");
        }

        final FileStatus[] files = fs.listStatus(new Path("/test/"));
        System.out.println(files.length);
        Assertions.assertTrue(files.length >= 1);
        for (FileStatus file : files) {
            log.info("file: {}", file.getPath());;
        }
        fs.close();
    }


    @Test
    public void testMkdir() throws URISyntaxException, IOException, InterruptedException {
        FileSystem fs = getFileSystem();
        fs.mkdirs(new Path("/123"));
        fs.mkdirs(new Path("/123/123"));
        fs.mkdirs(new Path("/123/345"));
        fs.mkdirs(new Path("/123/777"));
        System.out.println("======================");
        FileStatus[] files = fs.listStatus(new Path("/"));
        for (FileStatus file : files) {
            System.out.println(file.getPath().getName());
        }
        System.out.println("======================");
        fs.close();
    }
}
