package com.saltedfishcloud.ext.sftp.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SFTP参数配置
 * todo 支持私钥登录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SFTPProperty {
    private String host;
    @Builder.Default
    private Integer port = 22;
    private String username;
    private String password;
    private String path;
}
