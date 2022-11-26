package com.saltedfishcloud.ext.ftp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FTPProperty {
    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 端口
     */
    private Integer port = 21;

    /**
     * 主机名
     */
    private String hostname;

    /**
     * 使用被动模式
     */
    private boolean usePassive = true;

    /**
     * 使用的路径
     */
    private String path;

    /**
     * 启用缩略图支持
     */
    private boolean useThumbnail = false;
}
