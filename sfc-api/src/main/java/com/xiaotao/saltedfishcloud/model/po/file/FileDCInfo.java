package com.xiaotao.saltedfishcloud.model.po.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileDCInfo {
    private long uid;
    private String dir;
    protected String name;
    protected String md5;
    protected Integer type;
    protected long size;
}
