package com.xiaotao.saltedfishcloud.po.param;

import com.xiaotao.saltedfishcloud.validator.custom.FileName;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;

@Data
public class NamePair {

    @NotEmpty(message = "files[].source不得为空")
    @FileName
    private String source;

    @NotEmpty(message = "files[].target不得为空")
    @FileName
    private String target;
}
