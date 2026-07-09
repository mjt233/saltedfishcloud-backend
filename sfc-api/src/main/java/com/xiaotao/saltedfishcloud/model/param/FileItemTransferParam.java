package com.xiaotao.saltedfishcloud.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;

@Schema(description = "文件转移传输操作")
@Data
public class FileItemTransferParam {

    @Schema(description = "源文件/文件夹完整位置路径")
    @NotEmpty
    private String source;

    @Schema(description = "目标文件/文件夹完整位置路径")
    @NotEmpty
    private String target;
}

