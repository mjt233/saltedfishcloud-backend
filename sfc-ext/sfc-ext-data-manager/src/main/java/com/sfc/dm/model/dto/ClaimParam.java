package com.sfc.dm.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 认领请求参数
 */
@Getter
@Setter
public class ClaimParam {
    /**
     * 失效数据记录ID
     */
    @NotNull
    private Long invalidDataId;

    /**
     * 保存的目标网盘id（0=公共网盘，>0=对应用户id的私人网盘）
     */
    @NotNull
    private Long targetUid;

    /**
     * 认领时填写的文件名
     */
    @NotBlank
    private String fileName;

    /**
     * 认领时填写的文件保存目录的路径(不含文件名)
     */
    @NotBlank
    private String savePath;
}
