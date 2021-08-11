package com.xiaotao.saltedfishcloud.service.breakpoint.merge;

import com.xiaotao.saltedfishcloud.service.breakpoint.manager.impl.DefaultTaskManager;
import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskStatMetadata;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

class MergeTest {
    @Test
    void mergeTest() throws IOException {
        DefaultTaskManager manager = new DefaultTaskManager();
        TaskStatMetadata stat = manager.queryTask("f3852c6c-8f1a-47ba-a4fe-8c99b91d5906");
        MergeInputStream mergeInputStream = stat.getMergeInputStream();
        Files.copy(mergeInputStream, Paths.get("D:\\pack\\test.zip"), StandardCopyOption.REPLACE_EXISTING);
    }
}
