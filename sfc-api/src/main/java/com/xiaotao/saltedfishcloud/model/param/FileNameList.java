package com.xiaotao.saltedfishcloud.model.param;

import com.xiaotao.saltedfishcloud.validator.annotations.FileName;
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
