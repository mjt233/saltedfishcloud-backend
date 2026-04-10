package com.saltedfishcloud.ext.ve.core;

import com.saltedfishcloud.ext.ve.model.*;

import java.io.IOException;
import java.util.List;

public interface FFMpegHelper {
    /**
     * 执行ffprobe命令
     *
     * @param localFilePath 本地文件路径
     * @return 命令输出内容
     */
    String executeProbe(String localFilePath) throws IOException;

    ProcessWrap executeConvert(String input, String output, EncodeConvertParam param) throws IOException;

    /**
     * 执行原始ffmpeg命令
     * @param input     视频的输入文件
     * @param output    输出文件
     * @param inputArgs 输入文件参数（-i 之前的参数）
     * @param outputArgs 输出文件参数（[-i 输入参数] 之后的参数）
     */
    ProcessWrap executeFFMpeg(String input, String output, List<String> inputArgs, List<String> outputArgs) throws IOException;

    /**
     * 提取视频字幕
     *
     * @param localFile 本地文件路径
     * @param streamNo  字幕流编号
     * @return 字幕文件srt内容
     */
    String extractSubtitle(String localFile, String streamNo) throws IOException;

    /**
     * 提取视频字幕
     *
     * @param localFile 本地文件路径
     * @param streamNo  字幕流编号
     * @return 字幕文件srt内容
     */
    String extractSubtitle(String localFile, String streamNo, String type) throws IOException;

    /**
     * 获取视频信息
     *
     * @param localFilePath 视频本地文件路径
     * @return 视频信息
     */
    VideoInfo getVideoInfo(String localFilePath) throws IOException;

    /**
     * 获取ffmpeg的信息
     */
    FFMpegInfo getFFMpegInfo() throws IOException;

    void setProperty(VEProperty property);

    VEProperty getProperty();
}
