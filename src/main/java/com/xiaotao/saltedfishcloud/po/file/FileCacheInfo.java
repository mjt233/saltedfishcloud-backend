package com.xiaotao.saltedfishcloud.po.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileCacheInfo {
    public String name;
    public String path;
    public Long size;
    public String md5;
}
