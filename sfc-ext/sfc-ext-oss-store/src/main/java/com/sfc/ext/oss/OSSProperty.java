package com.sfc.ext.oss;

import com.sfc.constant.ConfigInputType;
import com.sfc.ext.oss.constants.OSSType;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesGroup;
import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import com.xiaotao.saltedfishcloud.annotations.ConfigSelectOption;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConfigPropertyEntity(
        groups = {
                @ConfigPropertiesGroup(id = "base", name = "基本配置"),
                @ConfigPropertiesGroup(id = "response", name = "文件响应配置")
        }
)
public class OSSProperty {
    /**
     * OSS类型
     *
     * todo: 使用动态获取数据的前端组件代替SELECT类型
     * @see OSSType#S3
     */
    @ConfigProperty(
            required = true,
            defaultValue = OSSType.S3,
            title = "OSS类型",
            inputType = ConfigInputType.SELECT,
            options = {
                    @ConfigSelectOption(title = "Amazon S3", value = OSSType.S3)
            }
    )
    private String type = OSSType.S3;

    @ConfigProperty(required = true, value = "bucket")
    private String bucket;

    @ConfigProperty(required = true, value = "serviceEndPoint")
    private String serviceEndPoint;

    @ConfigProperty(required = true, value = "accessKey")
    private String accessKey;

    @ConfigProperty(required = true, value = "secretKey")
    private String secretKey;

    @ConfigProperty(value = "cdnDomain", title = "CDN地址(选填)", group = "response")
    private String cdnDomain;

    @ConfigProperty(
            value = "urlExpire",
            title = "生成URL有效期",
            inputType = ConfigInputType.SELECT,
            options = {
                    @ConfigSelectOption(title = "1天", value = "1"),
                    @ConfigSelectOption(title = "3天", value = "3"),
                    @ConfigSelectOption(title = "7天", value = "7")
            },
            defaultValue = "1",
            group = "response"
    )
    private String urlExpire;

    @ConfigProperty(
            value = "useUrlRedirect",
            title = "启用URL重定向",
            inputType = ConfigInputType.SWITCH,
            defaultValue = "true",
            group = "response"
    )
    private Boolean useUrlRedirect = Boolean.TRUE;
}
