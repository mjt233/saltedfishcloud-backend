package com.xiaotao.saltedfishcloud.model.param;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.Collection;
import java.util.Map;

/**
 * 多文件打包下载创建参数
 */
@Data
@NoArgsConstructor
public class WrapParam {
    /**
     * 文件所在目录路径
     */
    @NotBlank
    private String path;

    /**
     * 需要打包下载的文件名
     */
    @NotEmpty
    private Collection<String> filenames;

    /**
     * 数据区域来源，目前可选share，file，后续此处将拓展<br>
     * share表示从文件分享中创建打包。<br>
     * file表示从用户网盘中创建打包。<br>
     */
    @NotBlank
    private String source;

    /**
     * 数据区域来源资源标识id。
     * share下为分享id，file下为用户id，其他拓展类型以拓展数据id为准。
     */
    @NotBlank
    private String sourceId;

    private Map<String, Object> otherData;
}
