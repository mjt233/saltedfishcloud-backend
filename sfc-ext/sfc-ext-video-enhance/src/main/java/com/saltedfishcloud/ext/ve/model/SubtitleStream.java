package com.saltedfishcloud.ext.ve.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SubtitleStream extends MediaStream {
    /**
     * 标题
     */
    private String title;
}
