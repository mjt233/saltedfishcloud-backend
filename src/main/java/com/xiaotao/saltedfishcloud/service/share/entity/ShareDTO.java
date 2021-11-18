package com.xiaotao.saltedfishcloud.service.share.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
@NoArgsConstructor
public class ShareDTO {
    /**
     * 分享的资源所在目录
     */
    @NotBlank
    private String path;

    /**
     * 文件或目录名
     */
    @NotBlank
    private String name;

    /**
     * 过期时间
     */
    private Date expiredAt;

    /**
     * 分享提取码
     */
    @Length(max = 16)
    private String extractCode;
}
