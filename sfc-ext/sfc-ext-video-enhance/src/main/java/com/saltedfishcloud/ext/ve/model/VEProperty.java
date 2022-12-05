package com.saltedfishcloud.ext.ve.model;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperties;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesEntity;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesGroup;
import com.xiaotao.saltedfishcloud.utils.OSInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Paths;

/**
 * 视频增强插件配置选项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ConfigPropertiesEntity(
        groups = {
                @ConfigPropertiesGroup(name = "基础配置", id="base"),
                @ConfigPropertiesGroup(name = "功能", id="feature")
        }
)
public class VEProperty {
    /**
     * ffmpeg
     */
    @ConfigProperties(value = "ffmpeg-path", title = "ffmpeg目录", describe = "ffmpeg可执行程序的所在目录")
    private String ffmpegPath;


    @ConfigProperties(
            value = "enable-tThumbnail",
            title = "启用视频缩略图",
            describe = "是否启用视频缩略图总开关",
            group = "feature",
            defaultValue = "false",
            inputType = "checkbox"
    )
    private boolean enableThumbnail;

    @ConfigProperties(
            value = "enable-tThumbnail-on-mount",
            title = "在挂载目录中启用视频缩略图",
            describe = "控制是否为挂载目录中的视频创建缩略图",
            group = "feature",
            defaultValue = "false",
            inputType = "checkbox"
    )
    private boolean enableThumbnailOnMount;

    public String getExecutablePathOnFFMpegPATH(String name) {
        String execFileName = OSInfo.isWindows() ? name + ".exe" : name;
        return Paths.get(ffmpegPath).resolve(execFileName).toString();
    }

    public String getFFMpegExecPath() {
        return getExecutablePathOnFFMpegPATH("ffmpeg");
    }

    public String getFFProbeExecPath() {
        return getExecutablePathOnFFMpegPATH("ffprobe");
    }
}
