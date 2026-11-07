package com.sfc.archive.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 压缩/解压缩参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ArchiveParam {

    /**
     * 压缩/解压缩格式类型
     */
    private String type;

    /**
     * 文件名编码
     */
    private String encoding;

    /**
     * 压缩/解压缩密码
     */
    private String password;

    /**
     * 其他参数
     */
    private Map<String, Object> otherParams;

}
