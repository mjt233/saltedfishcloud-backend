package com.xiaotao.saltedfishcloud.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;

@ApiModel(description = "文件转移传输操作")
@Data
public class FileItemTransferParam {

    @ApiModelProperty("源位置")
    @NotEmpty
    private String source;

    @ApiModelProperty("目标位置")
    @NotEmpty
    private String target;
}

