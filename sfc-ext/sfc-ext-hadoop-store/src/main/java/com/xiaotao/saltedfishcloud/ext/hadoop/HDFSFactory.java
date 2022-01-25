package com.xiaotao.saltedfishcloud.ext.hadoop;

import lombok.RequiredArgsConstructor;
import org.apache.hadoop.fs.FileSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class HDFSFactory {

    @Autowired
    private FileSystem fileSystem;

    public FileSystem getFileSystem() throws IOException {
        return fileSystem;
    }
}
