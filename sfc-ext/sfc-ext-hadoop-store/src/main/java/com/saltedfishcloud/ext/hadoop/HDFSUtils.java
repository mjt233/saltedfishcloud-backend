package com.saltedfishcloud.ext.hadoop;

import org.apache.hadoop.fs.FileSystem;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class HDFSUtils {
    private HDFSUtils() {}

    public static FileSystem getFileSystem(HDFSProperties properties) throws URISyntaxException, IOException, InterruptedException {
        final org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        conf.set("fs.defaultFS", properties.getUrl());
        conf.set("hadoop.root.logger", "WARN");
        conf.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");
        return FileSystem.get(new URI(properties.getUrl()), conf, properties.getUser());
    }
}
