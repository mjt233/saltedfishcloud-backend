package com.xiaotao.saltedfishcloud.po.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileDCInfo extends BasicFileInfo{
    private int uid;
    private String dir;
}
