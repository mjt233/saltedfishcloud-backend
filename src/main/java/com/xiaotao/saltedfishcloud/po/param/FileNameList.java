package com.xiaotao.saltedfishcloud.po.param;

import com.xiaotao.saltedfishcloud.validator.FileName;
import lombok.Data;

import java.util.List;

@Data
public class FileNameList {
    @FileName
    private List<String> fileName;

    public int length() {
        return fileName.size();
    }
}
