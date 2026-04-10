package com.saltedfishcloud.ext.ve.core;

import com.saltedfishcloud.ext.ve.model.ProcessWrap;
import com.saltedfishcloud.ext.ve.model.StreamInfo;
import com.saltedfishcloud.ext.ve.model.VEProperty;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailHandler;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StreamUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class VideoThumbnailHandler implements ThumbnailHandler {
    private final FFMpegHelper ffMpegHelper;
    private final VEProperty veProperty;

    private static final List<String> SUPPORTED_TYPES = List.of(
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "mpeg", "mpg", "m4v", "3gp", "ogv",
            "mts", "m2ts", "vob", "divx", "xvid", "asf", "rm", "rmvb", "f4v", "mxf"
    );

    @Override
    public boolean generate(Resource resource, String type, OutputStream outputStream) throws IOException {
        // 检查是否启用视频缩略图功能
        if (!veProperty.isEnableThumbnail()) {
            log.debug("视频缩略图功能未启用");
            return false;
        }

        // 检查是否启用挂载目录缩略图功能
        if (!resource.isFile() && !veProperty.isEnableThumbnailOnRemote()) {
            log.debug("视频缩略图生成失败：资源不在本机文件系统中且未启用挂载目录缩略图功能");
            return false;
        }

        File videoFile;
        boolean isTempFile = !resource.isFile();

        if (isTempFile) {
            // 保存为临时文件
            log.debug("从流中保存资源到本地为临时文件");
            String tempDir = PathUtils.getTempDirectory();
            videoFile = FileUtils.saveStreamAsLocalTempFile(resource, tempDir);
            log.debug("临时文件已保存，路径：{}", videoFile.getAbsolutePath());
        } else {
            videoFile = resource.getFile();
        }

        try {
            // 检查视频文件是否存在
            if (!videoFile.exists()) {
                log.debug("视频文件不存在：{}", videoFile.getAbsolutePath());
                // 清理临时文件
                if (isTempFile && videoFile.exists()) {
                    boolean deleted = videoFile.delete();
                    log.debug("清理临时文件{}，结果：{}", videoFile.getAbsolutePath(), deleted);
                }
                return false;
            }

            // 获取视频信息
            VideoInfo videoInfo = ffMpegHelper.getVideoInfo(videoFile.getAbsolutePath());
            // 检查是否有内置封面/海报/缩略图资源
            StreamInfo attachedPicStream = findAttachedPicStream(videoInfo);
            if (attachedPicStream != null) {
                // 提取内置封面
                return extractAttachedPic(videoFile.getAbsolutePath(), attachedPicStream.getIndex(), outputStream);
            } else {
                // 生成缩略图（取前30%位置的帧）
                return generateThumbnailFromVideo(videoFile.getAbsolutePath(), videoInfo, outputStream);
            }
        } catch (IOException e) {
            log.error("视频缩略图生成失败：{}", e.getMessage(), e);
            return false;
        } finally {
            // 清理临时文件
            if (isTempFile && videoFile.exists()) {
                log.debug("清理临时文件{}，结果：{}", videoFile.getAbsolutePath(), videoFile.delete());
            }
        }
    }

    /**
     * 查找视频流中是否有内置封面/海报/缩略图资源
     * @param videoInfo 视频信息
     * @return 包含内置封面的流信息，如果没有则返回null
     */
    private StreamInfo findAttachedPicStream(VideoInfo videoInfo) {
        if (videoInfo.getStreams() == null) {
            return null;
        }
        for (StreamInfo stream : videoInfo.getStreams()) {
            Map<String, String> disposition = stream.getDisposition();
            if (disposition != null && "1".equals(disposition.get("attached_pic"))) {
                return stream;
            }
            // 也可以检查codec_type为"video"且tags中有"cover"等标签
            if ("video".equals(stream.getCodecType())) {
                Map<String, String> tags = stream.getTags();
                if (tags != null && (tags.containsKey("cover") || tags.containsKey("poster"))) {
                    return stream;
                }
            }
        }
        return null;
    }

    /**
     * 提取内置封面
     * @param videoFilePath 视频文件路径
     * @param streamIndex 流索引
     * @param outputStream 输出流
     * @return 是否成功
     */
    private boolean extractAttachedPic(String videoFilePath, String streamIndex, OutputStream outputStream) throws IOException {
        // 使用ffmpeg提取内置封面流，输出为jpg格式到输出流
        // 命令：ffmpeg -i input -map 0:streamIndex -c copy -f mjpeg -
        List<String> args = new ArrayList<>();
        args.add("-map");
        args.add("0:" + streamIndex);
        args.add("-c");
        args.add("copy");
        args.add("-f");
        args.add("mjpeg");

        ProcessWrap processWrap = ffMpegHelper.executeFFMpeg(videoFilePath, "-", args);
        try (InputStream is = processWrap.getProcess().getInputStream()) {
            StreamUtils.copyStream(is, outputStream);
            int exitCode = processWrap.getProcess().waitFor();
            return exitCode == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("提取内置封面被中断", e);
            return false;
        }
    }

    /**
     * 生成视频缩略图（取前30%位置的帧）
     * @param videoFilePath 视频文件路径
     * @param videoInfo 视频信息
     * @param outputStream 输出流
     * @return 是否成功
     */
    private boolean generateThumbnailFromVideo(String videoFilePath, VideoInfo videoInfo, OutputStream outputStream) throws IOException {
        // 创建临时文件用于保存缩略图
        Path tempFile = Files.createTempFile("video_thumb_", ".jpg");
        try {
            // 计算目标时间点（前30%位置）
            Double duration = videoInfo.getFormat() != null ? videoInfo.getFormat().getDuration() : null;
            String ssParam = null;
            if (duration != null && duration > 0.1) {
                double targetTime = duration * 0.3;
                ssParam = String.format("%.3f", targetTime); // 保留三位小数
                log.debug("视频时长{}秒，提取前30%位置（{}秒）的帧", duration, ssParam);
            } else {
                log.debug("视频时长无效或太短（{}秒），使用第一帧", duration);
            }

            // 构建ffmpeg参数：取指定时间点的帧，缩放到合适大小
            // 命令：ffmpeg -i input -ss [time] -vframes 1 -vf "scale='min(320,iw)':-1" -q:v 2 -f image2pipe output
            List<String> args = new ArrayList<>();
            if (ssParam != null) {
                args.add("-ss");
                args.add(ssParam);
            }
            args.add("-vframes");
            args.add("1");
            args.add("-vf");
            args.add("scale='min(320,iw)':-1");
            args.add("-q:v");
            args.add("2");
            args.add("-f");
            args.add("image2pipe");

            ProcessWrap processWrap = ffMpegHelper.executeFFMpeg(videoFilePath, tempFile.toString(), args);
            int exitCode = processWrap.getProcess().waitFor();
            if (exitCode != 0) {
                log.error("FFmpeg生成缩略图失败，退出码：{}", exitCode);
                return false;
            }

            // 将临时文件内容写入输出流
            try (InputStream is = Files.newInputStream(tempFile)) {
                StreamUtils.copyStream(is, outputStream);
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("生成缩略图被中断", e);
            return false;
        } finally {
            // 删除临时文件
            Files.deleteIfExists(tempFile);
        }
    }

    @Override
    public List<String> getSupportType() {
        return SUPPORTED_TYPES;
    }
}
