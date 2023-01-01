package com.saltedfishcloud.ext.ftp;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperties;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ConfigPropertiesEntity
public class FTPProperty {
    /**
     * 主机名
     */
    @ConfigProperties(value = "hostname", required = true, title = "主机名")
    private String hostname;

    /**
     * 端口
     */
    @ConfigProperties(value = "port", required = true, title = "端口")
    private Integer port = 21;


    /**
     * 用户名
     */
    @ConfigProperties(value = "username", title = "用户名")
    private String username;

    /**
     * 密码
     */
    @ConfigProperties(value = "password", title = "密码", isMask = true)
    private String password;


    /**
     * 使用的路径
     */
    @ConfigProperties(value = "path", required = true, title = "请求的路径")
    private String path;

    /**
     * 使用被动模式
     */
    @ConfigProperties(value = "usePassive", inputType = "switch", describe = "使用被动模式")
    private Boolean usePassive;

    public Boolean getUsePassive() {
        return usePassive != null && usePassive;
    }


    /**
     * 启用缩略图支持
     */
    @ConfigProperties(value = "useThumbnail", inputType = "switch", describe = "启用缩略图支持")
    private Boolean useThumbnail;

    public Boolean getUseThumbnail() {
        return useThumbnail != null && useThumbnail;
    }

    /**
     * 是否匿名访问
     */
    @ConfigProperties(value = "anonymous", inputType = "switch", describe = "匿名登录")
    private Boolean anonymous;

    public String getUsername() {
        if (anonymous == null || anonymous) {
            return "anonymous";
        } else {
            return username;
        }
    }

    public String getPassword() {
        if (anonymous == null || anonymous) {
            return "anonymous@a.com";
        } else {
            return password;
        }
    }
}
