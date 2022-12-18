package com.saltedfishcloud.ext.ve.model;

import lombok.Data;

import java.util.Map;

@Data
public class ChapterInfo {
    private String id;

    /**
     * 开始位置
     */
    private Double startTime;

    /**
     * 结束位置
     */
    private Double endTime;

    /**
     * 标题
     */
    private String title;

    private Map<String, String> tags;

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
        if (this.title == null && tags != null) {
            this.title = tags.get("title");
        }
    }
}
