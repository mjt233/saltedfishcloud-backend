package com.xiaotao.saltedfishcloud.service.download;

enum TaskStatus {
    DOWNLOADING,
    FAILED,
    FINISH,
    READY,
    CANCEL,
    ERROR
}

public class DownloadTaskStatus {
    public long total;
    public long loaded;
    public String name;
    public String url;
    public long created_at = System.currentTimeMillis();
    public TaskStatus status = TaskStatus.READY;
    public String error;
    public long speed;
}
