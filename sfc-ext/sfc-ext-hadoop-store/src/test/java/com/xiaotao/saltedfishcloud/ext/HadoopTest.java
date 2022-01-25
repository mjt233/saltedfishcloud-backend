package com.xiaotao.saltedfishcloud.ext;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;


public class HadoopTest {
    @Test
    public void testConnect() throws URISyntaxException, IOException, InterruptedException {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "hdfs://localhost:9000");

        FileSystem fs = FileSystem.get(new URI("hdfs://localhost:9000"), conf, "xiaotao");
        fs.mkdirs(new Path("/test11233"));
        RemoteIterator<LocatedFileStatus> files = fs.listFiles(new Path("/"), true);
        System.out.println("======================");
        while (files.hasNext()) {
            System.out.println("1231231231321");
            System.out.println(files.next().getPath());
        }
        System.out.println("======================");
        fs.close();
    }
}
