package com.sfc.dm.service.identify.provider;

import com.sfc.dm.model.dto.FileMetadataDefine;
import com.sfc.dm.model.dto.FileTypeInfo;
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
 * 图片文件类型识别提供者，通过ffprobe检测文件格式并提取图片元数据
 */
@Slf4j
public class ImageCheckProvider implements FileTypeCheckProvider {
    private static final String ID = "imageCheckProvider";
    private static final String TYPE_NAME = "图片";
    private static final String TYPE_ID = "image";
    private static final List<String> EXTENSIONS = List.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".tiff", ".tif",
            ".heic", ".heif", ".avif", ".jxl", ".apng", ".svg", ".ico"
    );

    private final FFMpegHelper ffMpegHelper;

    /**
     * @param ffMpegHelper FFMpegHelper实例，用于通过ffprobe检测图片格式和提取元数据
     */
    public ImageCheckProvider(FFMpegHelper ffMpegHelper) {
        this.ffMpegHelper = ffMpegHelper;
    }

    @Override
    public String getId() { return ID; }

    @Override
    public List<FileTypeInfo> getTypeInfoList() {
        return List.of(new FileTypeInfo(TYPE_ID, TYPE_NAME, List.of(
                new FileMetadataDefine("宽度", "width", "图片宽度（像素）", "span"),
                new FileMetadataDefine("高度", "height", "图片高度（像素）", "span"),
                new FileMetadataDefine("编码器", "codec", "图片编码器名称（如mjpeg, png, webp等）", "span"),
                new FileMetadataDefine("封装格式", "containerFormat", "图片封装格式（如image2, mp3等）", "span")
        )));
    }

    @Override
    public List<String> getSupportedFileExtensions() { return EXTENSIONS; }

    @Override
    public FileTypeCheckResultDetail checkFile(File file, boolean extraMetadata) {
        try {
            VideoInfo videoInfo = ffMpegHelper.getVideoInfo(file.getAbsolutePath());
            FormatInfo format = videoInfo.getFormat();
            if (format == null || format.getFormatName() == null) {
                return null;
            }

            // 确认包含视频流（图片被视为单帧视频）
            boolean hasVideo = videoInfo.getStreams().stream()
                    .anyMatch(s -> "video".equals(s.getCodecType()));
            if (!hasVideo) {
                return null;
            }

            // 根据ffprobe返回的formatName和编码器映射扩展名和MIME类型
            String formatName = format.getFormatName();
            String codecName = getVideoCodec(videoInfo);
            String[] extAndMime = mapFormat(formatName, codecName, file.getName());
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
            log.debug("ffprobe图片检测失败: {}", file.getName(), e);
            return null;
        }
    }

    /**
     * 获取视频流的编码器名称
     */
    private String getVideoCodec(VideoInfo videoInfo) {
        return videoInfo.getStreams().stream()
                .filter(s -> "video".equals(s.getCodecType()))
                .map(StreamInfo::getCodecName)
                .findFirst()
                .orElse(null);
    }

    /**
     * 将ffprobe的formatName和编码器映射为扩展名和MIME类型
     *
     * @return [extension, mimetype]，无法识别时返回null
     */
    private String[] mapFormat(String formatName, String codecName, String fileName) {
        // 根据编码器判断图片类型
        if (codecName != null) {
            if ("mjpeg".equals(codecName) || "jpeg".equals(codecName)) {
                return new String[]{".jpg", "image/jpeg"};
            }
            if ("png".equals(codecName)) {
                return new String[]{".png", "image/png"};
            }
            if ("gif".equals(codecName)) {
                return new String[]{".gif", "image/gif"};
            }
            if ("bmp".equals(codecName)) {
                return new String[]{".bmp", "image/bmp"};
            }
            if ("webp".equals(codecName)) {
                return new String[]{".webp", "image/webp"};
            }
            if ("tiff".equals(codecName)) {
                return new String[]{".tiff", "image/tiff"};
            }
            if ("hevc".equals(codecName) && (formatName.contains("heic") || formatName.contains("heif"))) {
                return new String[]{".heic", "image/heic"};
            }
            if ("av1".equals(codecName) && formatName.contains("avif")) {
                return new String[]{".avif", "image/avif"};
            }
            if ("jpegxl".equals(codecName) || "jxl".equals(codecName)) {
                return new String[]{".jxl", "image/jxl"};
            }
            if ("apng".equals(codecName)) {
                return new String[]{".apng", "image/apng"};
            }
        }

        // 根据文件扩展名和格式名称兜底判断
        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
            return new String[]{".jpg", "image/jpeg"};
        }
        if (lowerFileName.endsWith(".png")) {
            return new String[]{".png", "image/png"};
        }
        if (lowerFileName.endsWith(".gif")) {
            return new String[]{".gif", "image/gif"};
        }
        if (lowerFileName.endsWith(".bmp")) {
            return new String[]{".bmp", "image/bmp"};
        }
        if (lowerFileName.endsWith(".webp")) {
            return new String[]{".webp", "image/webp"};
        }
        if (lowerFileName.endsWith(".tiff") || lowerFileName.endsWith(".tif")) {
            return new String[]{".tiff", "image/tiff"};
        }
        if (lowerFileName.endsWith(".heic") || lowerFileName.endsWith(".heif")) {
            return new String[]{".heic", "image/heic"};
        }
        if (lowerFileName.endsWith(".avif")) {
            return new String[]{".avif", "image/avif"};
        }
        if (lowerFileName.endsWith(".jxl")) {
            return new String[]{".jxl", "image/jxl"};
        }
        if (lowerFileName.endsWith(".apng")) {
            return new String[]{".apng", "image/apng"};
        }
        if (lowerFileName.endsWith(".svg")) {
            return new String[]{".svg", "image/svg+xml"};
        }
        if (lowerFileName.endsWith(".ico")) {
            return new String[]{".ico", "image/x-icon"};
        }

        return null;
    }

    /**
     * 通过ffprobe提取图片元数据（编码器、分辨率等）
     */
    private Map<String, String> extractMetadata(VideoInfo videoInfo) {
        Map<String, String> metadata = new HashMap<>();

        // 视频流：编码器、宽高
        for (StreamInfo stream : videoInfo.getStreams()) {
            if ("video".equals(stream.getCodecType())) {
                if (stream.getCodecName() != null) {
                    metadata.put("codec", stream.getCodecName());
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

        // 封装格式
        FormatInfo format = videoInfo.getFormat();
        if (format != null) {
            if (format.getFormatName() != null) {
                metadata.put("containerFormat", format.getFormatName());
            }
        }

        return metadata.isEmpty() ? null : metadata;
    }
}
