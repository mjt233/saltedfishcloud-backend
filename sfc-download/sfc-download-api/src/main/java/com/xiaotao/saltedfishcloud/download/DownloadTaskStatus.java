package com.xiaotao.saltedfishcloud.download;

import lombok.ToString;


@ToString
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
