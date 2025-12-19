package com.saltedfishcloud.ext.ve.model;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
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
@ConfigPropertyEntity(
        groups = {
                @ConfigPropertiesGroup(name = "基础配置", id="base"),
                @ConfigPropertiesGroup(name = "功能", id="feature")
        }
)
public class VEProperty {
    /**
     * ffmpeg
     */
    @ConfigProperty(value = "ffmpegPath", title = "ffmpeg目录", describe = "ffmpeg可执行程序的所在目录")
    private String ffmpegPath;


    @ConfigProperty(
            value = "enableThumbnail",
            describe = "启用视频缩略图",
            group = "feature",
            defaultValue = "false",
            inputType = "switch"
    )
    private boolean enableThumbnail;

    @ConfigProperty(
            value = "enableThumbnailOnMount",
            describe = "为挂载目录中的视频创建缩略图",
            group = "feature",
            defaultValue = "false",
            inputType = "switch"
    )
    private boolean enableThumbnailOnMount;

    /**
     * 获取ffmpeg目录下的程序可执行路径
     */
    public String getExecutablePathOnFFMpegPATH(String name) {
        if (ffmpegPath == null) {
            throw new IllegalArgumentException("ve.property未配置ffmpeg-path");
        }
        String execFileName = OSInfo.isWindows() ? name + ".exe" : name;
        return Paths.get(ffmpegPath).resolve(execFileName).toString();
    }

    /**
     * 获取ffmpeg的可执行完整路径
     */
    public String getFFMpegExecPath() {
        return getExecutablePathOnFFMpegPATH("ffmpeg");
    }

    /**
     * 获取ffprobe的可执行完整路径
     */
    public String getFFProbeExecPath() {
        return getExecutablePathOnFFMpegPATH("ffprobe");
    }
}
