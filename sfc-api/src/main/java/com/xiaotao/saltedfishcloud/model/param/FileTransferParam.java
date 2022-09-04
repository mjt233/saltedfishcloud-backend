package com.xiaotao.saltedfishcloud.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@ApiModel(description = "文件传输参数，可用于从各个位置复制、移动文件")
@Data
public class FileTransferParam {

    @ApiModelProperty("待传输操作的文件列表")
    @NotEmpty
    private List<FileItemTransferParam> files;

    @ApiModelProperty("文件原位置用户id")
    private Long targetUid;

    @ApiModelProperty("复制到的位置用户id")
    private Long sourceUid;
}
