package com.xiaotao.saltedfishcloud.model.po.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileDCInfo extends BasicFileInfo{
    private int uid;
    private String dir;
}
