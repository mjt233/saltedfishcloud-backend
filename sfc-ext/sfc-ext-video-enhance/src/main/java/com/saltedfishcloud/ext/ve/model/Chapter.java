package com.saltedfishcloud.ext.ve.model;

import lombok.Data;

@Data
public class Chapter {
    /**
     * 编号
     */
    private String no;

    /**
     * 开始位置
     */
    private Long start;

    /**
     * 结束位置
     */
    private Long end;

    /**
     * 标题
     */
    private String title;
}
