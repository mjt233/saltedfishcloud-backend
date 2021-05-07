package com.xiaotao.saltedfishcloud.po.param;

import com.xiaotao.saltedfishcloud.validator.custom.ValidPath;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
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
