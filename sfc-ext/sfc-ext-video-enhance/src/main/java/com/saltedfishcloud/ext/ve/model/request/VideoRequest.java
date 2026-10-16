package com.saltedfishcloud.ext.ve.model.request;

import com.xiaotao.saltedfishcloud.validator.annotations.FileName;
import com.sfc.cloud.data.validation.SfcUID;
import com.xiaotao.saltedfishcloud.validator.annotations.ValidPath;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

@Data
@Validated
public class VideoRequest {
    @SfcUID
    private Long uid;

    @ValidPath
    private String path;

    @FileName
    private String name;

    /**
     * 流编号
     */
    private String stream;

    /**
     * 字幕格式类型/音视频编码
     */
    private String type;
}
