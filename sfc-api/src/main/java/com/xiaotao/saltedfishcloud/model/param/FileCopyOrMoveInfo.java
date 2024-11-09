package com.xiaotao.saltedfishcloud.model.param;

import com.xiaotao.saltedfishcloud.validator.annotations.ValidPath;
import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;


@Data
public class FileCopyOrMoveInfo {

    @NotEmpty(message = "缺少参数target")
    @ValidPath
    private String target;
    private boolean overwrite;

    @Valid
    @NotEmpty(message = "缺少参数files")
    private List<NamePair> files;
}
