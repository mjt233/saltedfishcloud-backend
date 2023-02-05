package com.xiaotao.saltedfishcloud.download;

import com.xiaotao.saltedfishcloud.common.prog.ProgressDetector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DownloadTaskBuilderFactory {
    @Autowired
    private ProgressDetector progressDetector;

    public DownloadTaskBuilder getBuilder() {
        return new DownloadTaskBuilder(progressDetector);
    }
}
