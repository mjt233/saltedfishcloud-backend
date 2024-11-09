package com.xiaotao.saltedfishcloud.model.param;

import com.xiaotao.saltedfishcloud.validator.annotations.FileName;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;

@Data
public class NamePair {

    @NotEmpty(message = "files[].source不得为空")
    @FileName
    private String source;

    @NotEmpty(message = "files[].target不得为空")
    @FileName
    private String target;
}
