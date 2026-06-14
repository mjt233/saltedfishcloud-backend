package com.sfc.dm.service.identify.provider;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import com.sfc.dm.service.identify.FileTypeCheckProvider;
import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.model.FormatInfo;
import com.saltedfishcloud.ext.ve.model.StreamInfo;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.*;

/**
 * 视频文件类型识别提供者，通过ffprobe检测文件格式并提取视频元数据
 */
@Slf4j
public class VideoCheckProvider implements FileTypeCheckProvider {
    private static final String ID = "videoCheckProvider";
    private static final String TYPE_NAME = "视频";
    private static final String TYPE_ID = "video";
    private static final List<String> EXTENSIONS = List.of(".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv", ".webm", ".m4v", ".ts");

    private final FFMpegHelper ffMpegHelper;

    /**
     * @param ffMpegHelper FFMpegHelper实例，用于通过ffprobe检测视频格式和提取元数据
     */
    public VideoCheckProvider(FFMpegHelper ffMpegHelper) {
        this.ffMpegHelper = ffMpegHelper;
    }

    @Override
    public String getId() { return ID; }

    @Override
    public String getTypeName() { return TYPE_NAME; }

    @Override
    public String getTypeId() { return TYPE_ID; }

    @Override
    public List<String> getSupportedFileExtensions() { return EXTENSIONS; }

    @Override
    public List<FileMetadataDefine> getMetadataDefines() {
        return List.of(
                new FileMetadataDefine("时长", "duration", "视频时长（秒）", "span"),
                new FileMetadataDefine("宽度", "width", "视频宽度（像素）", "span"),
                new FileMetadataDefine("高度", "height", "视频高度（像素）", "span"),
                new FileMetadataDefine("视频编码器", "videoCodec", "视频编码器名称（如h264, hevc）", "span"),
                new FileMetadataDefine("音频编码器", "audioCodec", "音频编码器名称（如aac, mp3）", "span"),
                new FileMetadataDefine("封装格式", "containerFormat", "视频封装格式（如mov,mp4,m4a,3gp,3g2,mj2）", "span")
        );
    }

    @Override
    public FileTypeCheckResultDetail checkFile(File file, boolean extraMetadata) {
        try {
            VideoInfo videoInfo = ffMpegHelper.getVideoInfo(file.getAbsolutePath());
            FormatInfo format = videoInfo.getFormat();
            if (format == null || format.getFormatName() == null) {
                return null;
            }

            // 根据ffprobe返回的formatName映射扩展名和MIME类型
            String formatName = format.getFormatName();
            String[] extAndMime = mapFormat(formatName, videoInfo.getStreams());
            if (extAndMime == null) {
                return null;
            }

            FileTypeCheckResultDetail detail = new FileTypeCheckResultDetail();
            detail.setExtension(extAndMime[0]);
            detail.setMimetype(extAndMime[1]);

            if (extraMetadata) {
                Map<String, String> metadata = extractMetadata(videoInfo);
                if (metadata != null && !metadata.isEmpty()) {
                    detail.setMetadata(metadata);
                }
            }

            return detail;
        } catch (Exception e) {
            log.debug("ffprobe视频检测失败: {}", file.getName(), e);
            return null;
        }
    }

    /**
     * 将ffprobe的formatName映射为扩展名和MIME类型
     *
     * @return [extension, mimetype]，无法识别时返回null
     */
    private String[] mapFormat(String formatName, List<StreamInfo> streams) {
        if (formatName.contains("mp4") || formatName.contains("mov") || formatName.contains("3gp")) {
            return new String[]{".mp4", "video/mp4"};
        }
        if (formatName.contains("avi")) {
            return new String[]{".avi", "video/avi"};
        }
        if (formatName.contains("matroska") || formatName.contains("webm")) {
            // 区分MKV和WebM：WebM仅使用VP8/VP9视频编码
            boolean isWebm = streams.stream()
                    .filter(s -> "video".equals(s.getCodecType()))
                    .anyMatch(s -> "vp8".equals(s.getCodecName()) || "vp9".equals(s.getCodecName()));
            if (isWebm) {
                return new String[]{".webm", "video/webm"};
            }
            return new String[]{".mkv", "video/x-matroska"};
        }
        if (formatName.contains("flv")) {
            return new String[]{".flv", "video/x-flv"};
        }
        if (formatName.contains("asf")) {
            return new String[]{".wmv", "video/x-ms-wmv"};
        }
        return null;
    }

    /**
     * 通过ffprobe提取视频元数据（编码器、封装格式、分辨率、时长等）
     */
    private Map<String, String> extractMetadata(VideoInfo videoInfo) {
        Map<String, String> metadata = new HashMap<>();

        // 视频流：编码器、宽高
        for (StreamInfo stream : videoInfo.getStreams()) {
            if ("video".equals(stream.getCodecType())) {
                if (stream.getCodecName() != null) {
                    metadata.put("videoCodec", stream.getCodecName());
                }
                if (stream.getWidth() != null) {
                    metadata.put("width", String.valueOf(stream.getWidth()));
                }
                if (stream.getHeight() != null) {
                    metadata.put("height", String.valueOf(stream.getHeight()));
                }
                break;
            }
        }

        // 音频流：编码器
        for (StreamInfo stream : videoInfo.getStreams()) {
            if ("audio".equals(stream.getCodecType())) {
                if (stream.getCodecName() != null) {
                    metadata.put("audioCodec", stream.getCodecName());
                }
                break;
            }
        }

        // 封装格式、时长
        FormatInfo format = videoInfo.getFormat();
        if (format != null) {
            if (format.getFormatName() != null) {
                metadata.put("containerFormat", format.getFormatName());
            }
            if (format.getDuration() != null) {
                metadata.put("duration", String.format("%.1f", format.getDuration()));
            }
        }

        return metadata.isEmpty() ? null : metadata;
    }
}
