package com.xiaotao.saltedfishcloud.download.model;

import com.sfc.task.prog.ProgressRecord;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DownloadProgressRecord extends ProgressRecord {
    /**
     *  文件名
     */
    private String filename;
}
