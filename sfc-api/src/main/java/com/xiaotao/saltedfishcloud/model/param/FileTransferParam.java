package com.xiaotao.saltedfishcloud.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "文件传输参数，可用于从各个位置复制、移动文件")
@Data
public class FileTransferParam {

    @Schema(description = "待传输操作的文件列表")
    @NotEmpty
    private List<FileItemTransferParam> files;

    @Schema(description = "文件原位置用户id")
    private Long targetUid;

    @Schema(description = "复制到的位置用户id")
    private Long sourceUid;
}
