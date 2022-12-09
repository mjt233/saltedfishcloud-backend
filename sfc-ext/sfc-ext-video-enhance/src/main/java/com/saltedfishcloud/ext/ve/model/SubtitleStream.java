package com.saltedfishcloud.ext.ve.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
public class SubtitleStream extends MediaStream {
    /**
     * 标题
     */
    private String title;

    @Override
    public String toString() {
        return "SubtitleStream(" +
                "title='" + title + '\'' +
                ",remark='" + getRemark() + '\'' +
                ')';
    }
}
